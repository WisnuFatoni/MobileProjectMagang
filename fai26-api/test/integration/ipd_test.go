package integration

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"net/url"
	"testing"

	"fai26-api/internal/model"
	"fai26-api/internal/repository"
)

// Perangkat uji IPD (satu HP per akun).
const (
	devIpdMandor  = "aaaa0001-0000-4000-8000-000000000001"
	devIpdAsisten = "aaaa0002-0000-4000-8000-000000000002"
	devIpdAsist2  = "aaaa0003-0000-4000-8000-000000000003"
	devIpdMandor2 = "aaaa0004-0000-4000-8000-000000000004"
	devIpdAskep   = "aaaa0005-0000-4000-8000-000000000005"
	devIpdAdmin   = "aaaa0006-0000-4000-8000-000000000006"
)

func ipdContoh(id string) model.IpdInput {
	return model.IpdInput{
		IpdID: id, UnitKode: "KP_STL", TanggalInspeksi: "2026-11-20", DivisiKode: "DIV2", Asm3Oid: 1012, BlokKode: "A12",
		PemanenHrmbCode: 90001, AncakNo: "7", MandorUsername: "uji.mandor2",
		Pokok: []model.IpdPokokInput{
			{NoBaris: 11, NoPokok: 1},
			{NoBaris: 11, NoPokok: 2, DicatatPada: "2026-11-20T03:04:10Z", Temuan: []model.IpdTemuanInput{{Kode: "B1", Jumlah: 6}, {Kode: "S"}}},
			{NoBaris: 11, NoPokok: 3},
			{NoBaris: 10, NoPokok: 1, Temuan: []model.IpdTemuanInput{{Kode: "B1", Jumlah: 4}}},
		},
		// Jalur GPS: tiga titik berjarak sekitar 55 m, selang 2 menit.
		Jejak: []model.IpdTitikInput{
			{T: "2026-11-20T03:00:00Z", Lat: -2.7000, Lon: 104.3000, Akurasi: ptrF(8)},
			{T: "2026-11-20T03:02:00Z", Lat: -2.6995, Lon: 104.3000, Akurasi: ptrF(6.5)},
			{T: "2026-11-20T03:04:00Z", Lat: -2.6990, Lon: 104.3000},
		},
	}
}

func ptrF(v float64) *float64 { return &v }

func bersihkanIpd(e *lingkunganUji) {
	for _, q := range []string{
		`UPDATE dbo.divisi SET asisten_user_id=NULL`,
		`DELETE FROM dbo.fai_ipd_foto`, `DELETE FROM dbo.fai_ipd_jejak`, `DELETE FROM dbo.fai_ipd_temuan`, `DELETE FROM dbo.fai_ipd_pokok`, `DELETE FROM dbo.fai_ipd`,
	} {
		if _, err := e.db.Exec(q); err != nil {
			e.t.Logf("bersih-bersih IPD (%s): %v", q, err)
		}
	}
}

// Alur penuh IPD: inspektur (mandor) mengirim, asisten divisi menyetujui, laporan loses di web, aturan edit, ganti asisten divisi,
// peringatan inspeksi ulang, foto banyak dengan keterangan, dan hak akses (ASKEP hanya melihat).
func TestIpdAlurPenuh(t *testing.T) {
	e := siapkanUji(t)
	if hitung(t, e.db, `SELECT COUNT(*) FROM sys.tables WHERE name='fai_ipd'`) == 0 || hitung(t, e.db, `SELECT COL_LENGTH('dbo.divisi','asisten_user_id')`) == 0 {
		t.Skip("migrasi 009 (IPD) belum dijalankan di database uji")
	}
	bersihkanIpd(e)
	e.bersihkanMasterUji()
	t.Cleanup(func() { bersihkanIpd(e); e.bersihkanMasterUji() })

	_, ad := e.login("admin1", "admin12345", devIpdAdmin)
	buat := func(user, role string) int64 {
		return idDari(e.json("POST", "/api/admin/pengguna", ad.AccessToken, berkas{"username": user, "nama": "Uji " + user, "password": "sandiAwal123", "role": []string{role}}, 201))
	}
	asisten1, asisten2 := buat("uji.asisten1", "ASISTEN"), buat("uji.asisten2", "ASISTEN")
	mandor2 := buat("uji.mandor2", "MANDOR")
	buat("uji.askep", "ASKEP")

	// ---- Asisten divisi: hanya akun ASISTEN aktif di unit itu yang boleh dipilih ----
	idMandor1 := int64(0)
	for _, u := range e.daftar("/api/admin/pengguna", ad.AccessToken) {
		if u["username"] == "mandor1" {
			idMandor1 = int64(u["user_id"].(float64))
		}
	}
	divisi := func(asisten any, want int) berkas {
		return e.json("PUT", "/api/admin/divisi/1", ad.AccessToken, berkas{"kode": "DIV2", "nama": "Divisi 2 (contoh)", "asisten_user_id": asisten}, want)
	}
	divisi(idMandor1, 422) // mandor bukan asisten
	divisi(999999, 422)    // tidak ada
	divisi(asisten1, 200)  // sah
	if d := cari(e.daftar("/api/admin/divisi", ad.AccessToken), "kode", "DIV2"); d == nil || d["asisten_user_id"] != float64(asisten1) || d["asisten_nama"] != "Uji uji.asisten1" {
		t.Fatalf("daftar divisi harus memuat asisten: %v", d)
	}

	_, kr := e.login("krani1", "krani123", devKrani)
	_, md := e.login("mandor1", "mandor123", devIpdMandor)
	_, as1 := e.login("uji.asisten1", "sandiAwal123", devIpdAsisten)
	_, as2 := e.login("uji.asisten2", "sandiAwal123", devIpdAsist2)
	_, md2 := e.login("uji.mandor2", "sandiAwal123", devIpdMandor2)
	_, ak := e.login("uji.askep", "sandiAwal123", devIpdAskep)

	// ---- Master: kode temuan dan asisten per divisi sampai ke HP ----
	m := e.json("GET", "/api/master", md.AccessToken, nil, 200)
	if kt, _ := m["kode_temuan"].([]any); len(kt) != 11 || kt[0].(map[string]any)["kode"] != "A" || kt[10].(map[string]any)["kode"] != "R" {
		t.Fatalf("master harus memuat 11 kode temuan berurutan: %v", m["kode_temuan"])
	}
	var asistenDiMaster any
	for _, d := range m["divisi"].([]any) {
		if d.(map[string]any)["kode"] == "DIV2" {
			asistenDiMaster = d.(map[string]any)["asisten_user_id"]
		}
	}
	if asistenDiMaster != float64(asisten1) {
		t.Fatalf("divisi di master harus membawa asisten_user_id: %v", asistenDiMaster)
	}

	// ---- Hak akses kirim ----
	id := "bbbb0001-0000-4000-8000-000000000001"
	if kode, b := e.kirim("POST", "/api/ipd", kr.AccessToken, ipdContoh(id), nil); kode != 403 {
		t.Fatalf("krani tanpa ipd.kirim harus 403: %d %s", kode, b)
	}
	if kode, _ := e.kirim("POST", "/api/ipd", "", ipdContoh(id), nil); kode != 401 {
		t.Fatalf("tanpa token harus 401: %d", kode)
	}

	// ---- Validasi master ----
	tolak := func(ubah func(*model.IpdInput), pesan string) {
		in := ipdContoh("bbbb00ff-0000-4000-8000-0000000000ff")
		ubah(&in)
		if kode, b := e.kirim("POST", "/api/ipd", md.AccessToken, in, nil); kode != 422 {
			t.Fatalf("%s: harapan 422, dapat %d %s", pesan, kode, b)
		}
	}
	tolak(func(in *model.IpdInput) { in.DivisiKode = "TIDAK_ADA" }, "divisi")
	tolak(func(in *model.IpdInput) { in.Asm3Oid = 99999 }, "blok")
	tolak(func(in *model.IpdInput) { in.PemanenHrmbCode = 12345 }, "pemanen")
	tolak(func(in *model.IpdInput) { in.Pokok[1].Temuan[0].Kode = "Z9" }, "kode temuan")
	tolak(func(in *model.IpdInput) { in.MandorUsername = "krani1" }, "mandor bukan MANDOR")
	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.fai_ipd`) != 0 {
		t.Fatal("kiriman yang ditolak tidak boleh tersimpan")
	}

	// ---- Kirim oleh mandor: MENUNGGU asisten divisi ----
	if kode, b := e.kirim("POST", "/api/ipd", md.AccessToken, ipdContoh(id), nil); kode != 201 {
		t.Fatalf("mandor kirim: %d %s", kode, b)
	}
	var st string
	var asisten, mandorDb int64
	if err := e.db.QueryRow(`SELECT status, asisten_user_id, mandor_user_id FROM dbo.fai_ipd WHERE id=@p1`, id).Scan(&st, &asisten, &mandorDb); err != nil {
		t.Fatal(err)
	}
	if st != "MENUNGGU" || asisten != asisten1 || mandorDb != mandor2 {
		t.Fatalf("status/asisten/mandor: %s %d %d", st, asisten, mandorDb)
	}
	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.fai_ipd_pokok WHERE ipd_id=@p1 AND dihapus_pada IS NULL`, id) != 4 ||
		hitung(t, e.db, `SELECT COUNT(*) FROM dbo.fai_ipd_temuan t JOIN dbo.fai_ipd_pokok p ON p.id=t.pokok_id WHERE p.ipd_id=@p1`, id) != 3 {
		t.Fatal("4 pokok dan 3 temuan harus tersimpan")
	}
	if kode, b := e.kirim("POST", "/api/ipd", md.AccessToken, ipdContoh(id), nil); kode != 200 {
		t.Fatalf("percobaan ulang tanpa kirim_ulang harus 200 SUDAH_ADA: %d %s", kode, b)
	}
	statusHP := func(token, id string) string {
		kode, b := e.kirim("GET", "/api/ipd", token, nil, nil)
		if kode != 200 {
			t.Fatalf("status: %d %s", kode, b)
		}
		var l []model.IpdStatus
		_ = json.Unmarshal(b, &l)
		for _, x := range l {
			if x.ID == id {
				return x.Status + "|" + func() string {
					if x.Pesan != nil {
						return *x.Pesan
					}
					return ""
				}()
			}
		}
		return ""
	}
	if s := statusHP(md.AccessToken, id); s != "MENUNGGU_SETUJU|" {
		t.Fatalf("status di HP mandor: %q", s)
	}

	// ---- Mandor boleh mengubah selama belum disetujui ----
	in := ipdContoh(id)
	in.KirimUlang = true
	in.Pokok = append(in.Pokok, model.IpdPokokInput{NoBaris: 10, NoPokok: 2, Temuan: []model.IpdTemuanInput{{Kode: "A"}}})
	in.Pokok[3].Temuan[0].Jumlah = 5
	if kode, b := e.kirim("POST", "/api/ipd", md.AccessToken, in, nil); kode != 201 {
		t.Fatalf("ubah saat MENUNGGU: %d %s", kode, b)
	}
	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.fai_ipd_pokok WHERE ipd_id=@p1 AND dihapus_pada IS NULL`, id) != 5 ||
		hitung(t, e.db, `SELECT COUNT(*) FROM dbo.fai_ipd_pokok WHERE ipd_id=@p1 AND dihapus_pada IS NOT NULL`, id) != 4 ||
		hitung(t, e.db, `SELECT COUNT(*) FROM dbo.audit_log WHERE baris_id=@p1 AND aksi='TERIMA_ULANG'`, id) != 1 {
		t.Fatal("pokok lama harus terhapus lunak, 5 pokok baru aktif, audit TERIMA_ULANG tercatat")
	}
	// Orang lain tanpa ipd.kelola tidak boleh mengubah catatan itu.
	if kode, _ := e.kirim("POST", "/api/ipd", md2.AccessToken, in, nil); kode != 403 {
		t.Fatalf("bukan pemilik harus 403: %d", kode)
	}

	// ---- Peringatan inspeksi ulang (7 hari) ----
	q := "/api/ipd/inspeksi-terakhir?pemanen_hrmb_code=90001&asm3_oid=1012&tanggal=2026-11-24"
	var w model.IpdInspeksiTerakhir
	if kode, b := e.kirim("GET", q, md.AccessToken, nil, nil); kode != 200 || json.Unmarshal(b, &w) != nil || !w.Ada || w.JendelaHari != 7 || len(w.Inspeksi) != 1 || w.Inspeksi[0].Inspektur != "mandor1" {
		t.Fatalf("seminggu kemudian harus ada peringatan: %d %s", kode, b)
	}
	if _, b := e.kirim("GET", "/api/ipd/inspeksi-terakhir?pemanen_hrmb_code=90001&asm3_oid=1012&tanggal=2026-11-28", md.AccessToken, nil, nil); !bytes.Contains(b, []byte(`"ada":false`)) {
		t.Fatalf("8 hari kemudian tidak ada peringatan: %s", b)
	}
	if _, b := e.kirim("GET", q+"&kecuali="+id, md.AccessToken, nil, nil); !bytes.Contains(b, []byte(`"ada":false`)) {
		t.Fatalf("inspeksi yang sedang diedit dikecualikan: %s", b)
	}
	if _, b := e.kirim("GET", "/api/ipd/inspeksi-terakhir?pemanen_hrmb_code=90002&asm3_oid=1012&tanggal=2026-11-24", md.AccessToken, nil, nil); !bytes.Contains(b, []byte(`"ada":false`)) {
		t.Fatalf("pemanen lain tidak diperingatkan: %s", b)
	}

	// ---- Foto: banyak, dengan keterangan; terunggah ke server ----
	jpeg := append([]byte{0xFF, 0xD8, 0xFF, 0xE0}, bytes.Repeat([]byte{0x42}, 300)...)
	h := sha256.Sum256(jpeg)
	var fotoIDs []string
	for i := 0; i < 12; i++ { // lebih dari 10: tidak ada batas jumlah
		fid := fmt.Sprintf("eeee0001-0000-4000-8000-%012x", i+1)
		fotoIDs = append(fotoIDs, fid)
		path := "/api/ipd/" + id + "/media?urut=" + fmt.Sprint(i) + "&keterangan=" + url.QueryEscape(fmt.Sprintf("foto %d: buah tinggal", i+1))
		if kode, b := e.kirim("POST", path, md.AccessToken, jpeg, map[string]string{"X-Foto-Id": fid, "X-Sha256": hex.EncodeToString(h[:])}); kode != 201 {
			t.Fatalf("foto %d: %d %s", i, kode, b)
		}
	}
	if kode, _ := e.kirim("POST", "/api/ipd/"+id+"/media", md.AccessToken, jpeg, map[string]string{"X-Foto-Id": fotoIDs[0], "X-Sha256": hex.EncodeToString(h[:])}); kode != 200 {
		t.Fatalf("foto sama harus 200 SUDAH_ADA: %d", kode)
	}
	if kode, _ := e.kirim("POST", "/api/ipd/"+id+"/media", md2.AccessToken, jpeg, map[string]string{"X-Foto-Id": "eeee0001-0000-4000-8000-0000000000ff", "X-Sha256": hex.EncodeToString(h[:])}); kode != 403 {
		t.Fatalf("foto oleh bukan pemilik harus 403: %d", kode)
	}

	// ---- Persetujuan: hanya asisten yang tercatat ----
	if kode, _ := e.kirim("GET", "/api/ipd/menunggu", md.AccessToken, nil, nil); kode != 403 {
		t.Fatalf("mandor tanpa ipd.setujui harus 403: %d", kode)
	}
	if daftar := e.daftar("/api/ipd/menunggu", as2.AccessToken); len(daftar) != 0 {
		t.Fatalf("asisten divisi lain tidak melihat catatan itu: %v", daftar)
	}
	e.json("POST", "/api/ipd/"+id+"/setujui", as2.AccessToken, nil, 403)
	baris := cari(e.daftar("/api/ipd/menunggu", as1.AccessToken), "id", id)
	if baris == nil || baris["divisi_kode"] != "DIV2" || baris["pemanen_nama"] != "Pemanen Contoh 1" || baris["inspektur"] != "mandor1" ||
		baris["mandor_nama"] != "Uji uji.mandor2" || baris["asisten_nama"] != "Uji uji.asisten1" {
		t.Fatalf("baris menunggu: %v", baris)
	}
	rg := baris["ringkasan"].(map[string]any)
	// 5 pokok diperiksa; 3 bertemuan (pokok 2 baris 11, pokok 1 baris 10, pokok 2 baris 10); total temuan: B1 6+5, S 1, A 1 = 13.
	if rg["pokok_diperiksa"] != float64(5) || rg["pokok_bertemuan"] != float64(3) || rg["persen_pokok_bertemuan"] != float64(60) || rg["total_temuan"] != float64(13) {
		t.Fatalf("ringkasan: %v", rg)
	}
	if pk, _ := rg["per_kode"].([]any); len(pk) != 11 {
		t.Fatalf("per_kode memuat 11 kode: %v", rg["per_kode"])
	}
	if f, _ := baris["foto"].([]any); len(f) != 12 || f[0].(map[string]any)["keterangan"] != "foto 1: buah tinggal" {
		t.Fatalf("foto di kartu: %v", baris["foto"])
	}
	if kode, isi := e.kirim("GET", "/api/ipd/foto/"+fotoIDs[0], as1.AccessToken, nil, nil); kode != 200 || !bytes.Equal(isi, jpeg) {
		t.Fatalf("asisten harus bisa mengunduh foto: %d (%d byte)", kode, len(isi))
	}
	if kode, _ := e.kirim("GET", "/api/ipd/foto/"+fotoIDs[0], kr.AccessToken, nil, nil); kode != 403 {
		t.Fatalf("krani tanpa ipd.lihat tidak boleh melihat foto IPD: %d", kode)
	}

	// Laporan belum menghitung catatan yang belum disetujui.
	laporan := func(token, query string) model.IpdLaporan {
		kode, b := e.kirim("GET", "/api/admin/ipd/laporan"+query, token, nil, nil)
		if kode != 200 {
			t.Fatalf("laporan %s: %d %s", query, kode, b)
		}
		var l model.IpdLaporan
		if err := json.Unmarshal(b, &l); err != nil {
			t.Fatal(err)
		}
		return l
	}
	if l := laporan(ad.AccessToken, "?dari=2026-11-01&sampai=2026-11-30"); l.Inspeksi != 0 || l.Ringkasan.PokokDiperiksa != 0 {
		t.Fatalf("MENUNGGU belum boleh dihitung: %+v", l)
	}

	// Tolak lalu perbaiki lalu ganti asisten divisi: catatan MENUNGGU ikut pindah.
	e.json("POST", "/api/ipd/"+id+"/tolak", as1.AccessToken, berkas{"alasan": "  "}, 422)
	e.json("POST", "/api/ipd/"+id+"/tolak", as1.AccessToken, berkas{"alasan": "Baris 10 belum lengkap"}, 200)
	if s := statusHP(md.AccessToken, id); s != "DITOLAK|Baris 10 belum lengkap" {
		t.Fatalf("mandor melihat penolakan: %q", s)
	}
	e.json("POST", "/api/ipd/"+id+"/setujui", as1.AccessToken, nil, 409)
	in.Pokok = append(in.Pokok, model.IpdPokokInput{NoBaris: 10, NoPokok: 3})
	if kode, b := e.kirim("POST", "/api/ipd", md.AccessToken, in, nil); kode != 201 {
		t.Fatalf("perbaikan DITOLAK: %d %s", kode, b)
	}
	if s := statusHP(md.AccessToken, id); s != "MENUNGGU_SETUJU|" {
		t.Fatalf("setelah diperbaiki kembali menunggu tanpa pesan lama: %q", s)
	}
	divisi(asisten2, 200) // asisten diganti (mis. asisten lama berhenti)
	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.fai_ipd WHERE id=@p1 AND asisten_user_id=@p2`, id, asisten2) != 1 {
		t.Fatal("catatan MENUNGGU harus pindah ke asisten baru")
	}
	e.json("POST", "/api/ipd/"+id+"/setujui", as1.AccessToken, nil, 403)
	if len(e.daftar("/api/ipd/menunggu", as1.AccessToken)) != 0 || cari(e.daftar("/api/ipd/menunggu", as2.AccessToken), "id", id) == nil {
		t.Fatal("antrean persetujuan harus pindah ke asisten baru")
	}

	// ---- Disetujui: terkunci ----
	e.json("POST", "/api/ipd/"+id+"/setujui", as2.AccessToken, nil, 200)
	e.json("POST", "/api/ipd/"+id+"/setujui", as2.AccessToken, nil, 409)
	if s := statusHP(md.AccessToken, id); s != "DISETUJUI|" {
		t.Fatalf("status setelah disetujui: %q", s)
	}
	in.Pokok[0].Temuan = []model.IpdTemuanInput{{Kode: "C"}}
	if kode, b := e.kirim("POST", "/api/ipd", md.AccessToken, in, nil); kode != 423 {
		t.Fatalf("catatan disetujui harus terkunci (423): %d %s", kode, b)
	}
	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.fai_ipd_temuan t JOIN dbo.fai_ipd_pokok p ON p.id=t.pokok_id JOIN dbo.ipd_kode_temuan k ON k.kode_id=t.kode_id
WHERE p.ipd_id=@p1 AND p.dihapus_pada IS NULL AND t.dihapus_pada IS NULL AND k.kode='C'`, id) != 0 {
		t.Fatal("isi catatan terkunci berubah")
	}
	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.audit_log WHERE baris_id=@p1 AND aksi IN ('TERIMA','TERIMA_ULANG','TOLAK','SETUJU')`, id) != 5 {
		t.Fatal("audit TERIMA, TERIMA_ULANG x2, TOLAK, SETUJU harus tercatat")
	}

	// ---- Asisten sebagai inspektur: langsung DISETUJUI; inspeksi ulang pemanen lain di blok lain ----
	id2 := "bbbb0002-0000-4000-8000-000000000002"
	asisten2Kirim := ipdContoh(id2)
	asisten2Kirim.TanggalInspeksi = "2026-11-27"
	asisten2Kirim.PemanenHrmbCode = 90002
	asisten2Kirim.Pokok = []model.IpdPokokInput{{NoBaris: 3, NoPokok: 1}, {NoBaris: 3, NoPokok: 2}, {NoBaris: 3, NoPokok: 3, Temuan: []model.IpdTemuanInput{{Kode: "C"}}}}
	if kode, b := e.kirim("POST", "/api/ipd", as2.AccessToken, asisten2Kirim, nil); kode != 201 {
		t.Fatalf("asisten kirim: %d %s", kode, b)
	}
	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.fai_ipd WHERE id=@p1 AND status='DISETUJUI' AND disetujui_oleh=@p2 AND divalidasi_oleh=@p2`, id2, asisten2) != 1 {
		t.Fatal("inspeksi asisten harus langsung DISETUJUI")
	}
	// Inspeksi ulang pemanen yang sama di minggu berikutnya dibolehkan (hanya peringatan).
	id3 := "bbbb0003-0000-4000-8000-000000000003"
	ulang := ipdContoh(id3)
	ulang.TanggalInspeksi = "2026-11-27"
	if kode, b := e.kirim("POST", "/api/ipd", md.AccessToken, ulang, nil); kode != 201 {
		t.Fatalf("inspeksi ulang pemanen yang sama harus boleh: %d %s", kode, b)
	}

	// ---- Laporan loses (hanya DISETUJUI): id (5+1 pokok) dan id2 (3 pokok) ----
	l := laporan(ad.AccessToken, "?dari=2026-11-01&sampai=2026-11-30")
	if l.Inspeksi != 2 || l.Pemanen != 2 || l.Ringkasan.PokokDiperiksa != 9 || l.Ringkasan.PokokBertemuan != 4 || l.Ringkasan.TotalTemuan != 14 {
		t.Fatalf("laporan: inspeksi=%d pemanen=%d %+v", l.Inspeksi, l.Pemanen, l.Ringkasan)
	}
	// Loses (buah, brondolan) dipisah dari temuan lain (pelepah C): 3 pokok loses dengan 13 temuan; 1 pokok temuan lain.
	if l.Ringkasan.PokokLoses != 3 || l.Ringkasan.PersenPokokLoses != 33.3 || l.Ringkasan.TotalLoses != 13 ||
		l.Ringkasan.PokokTemuanLain != 1 || l.Ringkasan.PersenPokokTemuanLain != 11.1 || l.Ringkasan.TotalTemuanLain != 1 {
		t.Fatalf("pemisahan loses dan temuan lain: %+v", l.Ringkasan)
	}
	kodeAgg := map[string]model.IpdKodeAgg{}
	for _, k := range l.Ringkasan.PerKode {
		kodeAgg[k.Kode] = k
	}
	if kodeAgg["B1"].Jenis != "LOSES" || kodeAgg["C"].Jenis != "TEMUAN" {
		t.Fatalf("jenis kode: %+v", kodeAgg)
	}
	if kodeAgg["B1"].Pokok != 2 || kodeAgg["B1"].Jumlah != 11 || kodeAgg["C"].Jumlah != 1 || kodeAgg["A"].Jumlah != 1 || kodeAgg["S"].Jumlah != 1 {
		t.Fatalf("per kode: %+v", kodeAgg)
	}
	if len(l.PerKelompok) != 3 || l.PerKelompok[1].Kelompok != "BRONDOLAN" || l.PerKelompok[1].Pokok != 2 || l.PerKelompok[1].Jumlah != 11 {
		t.Fatalf("per kelompok: %+v", l.PerKelompok)
	}
	if len(l.PerPemanen) != 2 || len(l.PerBlok) != 1 || l.PerBlok[0].Kunci != "A12" || len(l.PerTanggal) != 2 {
		t.Fatalf("pengelompokan: %+v", l)
	}
	if k := laporan(ad.AccessToken, "?dari=2026-11-01&sampai=2026-11-30&pemanen_hrmb_code=90002"); k.Inspeksi != 1 || k.Ringkasan.PokokDiperiksa != 3 {
		t.Fatalf("penyaring pemanen: %+v", k)
	}
	if k := laporan(ad.AccessToken, "?dari=2026-11-21&sampai=2026-11-30"); k.Inspeksi != 1 {
		t.Fatalf("penyaring tanggal: %+v", k)
	}
	e.json("GET", "/api/admin/ipd/laporan?dari=2026-13-01", ad.AccessToken, nil, 422)
	e.json("GET", "/api/admin/ipd/laporan?dari=2026-11-30&sampai=2026-11-01", ad.AccessToken, nil, 422)

	// ---- Daftar dan rincian di web ----
	hal := e.json("GET", "/api/admin/ipd?dari=2026-11-01&sampai=2026-11-30", ad.AccessToken, nil, 200)
	if hal["total"] != float64(3) {
		t.Fatalf("daftar admin: %v", hal["total"])
	}
	if h := e.json("GET", "/api/admin/ipd?status=MENUNGGU", ad.AccessToken, nil, 200); h["total"] != float64(1) {
		t.Fatalf("filter status: %v", h["total"])
	}
	e.json("GET", "/api/admin/ipd?status=SALAH", ad.AccessToken, nil, 422)
	det := e.json("GET", "/api/admin/ipd/"+id, ad.AccessToken, nil, 200)
	if pk, _ := det["pokok"].([]any); len(pk) != 6 || pk[0].(map[string]any)["no_baris"] != float64(10) {
		t.Fatalf("rincian harus memuat 6 pokok berurutan baris lalu pokok: %v", det["pokok"])
	}
	// Jalur GPS: admin melihatnya lengkap dengan jarak dan durasi; waktu pokok ikut tersimpan.
	jj, _ := det["jejak"].(map[string]any)
	if tt, _ := jj["titik"].([]any); len(tt) != 3 || jj["durasi_menit"] != float64(4) || jj["jarak_m"].(float64) < 100 || jj["jarak_m"].(float64) > 120 {
		t.Fatalf("jejak admin: %v", det["jejak"])
	}
	var pokokBerwaktu int
	for _, p := range det["pokok"].([]any) {
		if p.(map[string]any)["dicatat_pada"] != nil {
			pokokBerwaktu++
		}
	}
	if pokokBerwaktu != 1 {
		t.Fatalf("hanya satu pokok yang dikirim dengan dicatat_pada: %d", pokokBerwaktu)
	}
	// Mandor boleh melihat rincian (ipd.lihat) tetapi tidak jalurnya (tanpa ipd.jejak).
	if dm := e.json("GET", "/api/admin/ipd/"+id, md.AccessToken, nil, 200); dm["jejak"] != nil {
		t.Fatalf("mandor tidak boleh menerima jejak: %v", dm["jejak"])
	}
	// ASKEP (hanya melihat) memegang ipd.jejak.
	if da := e.json("GET", "/api/admin/ipd/"+id, ak.AccessToken, nil, 200); da["jejak"] == nil {
		t.Fatal("ASKEP harus bisa melihat jejak")
	}
	// Audit: setiap pembukaan jalur tercatat siapa, dari mana, dan berapa titik. Mandor tidak menerima jalur sehingga tidak
	// tercatat; membuka ulang dalam 10 menit oleh orang yang sama tidak menambah baris.
	e.json("GET", "/api/admin/ipd/"+id, ad.AccessToken, nil, 200)
	if n := hitung(t, e.db, `SELECT COUNT(*) FROM dbo.audit_log WHERE aksi='LIHAT_JALUR' AND tabel='fai_ipd' AND baris_id=@p1`, id); n != 2 {
		t.Fatalf("LIHAT_JALUR harus 2 baris (admin dan ASKEP, tanpa duplikat dan tanpa mandor): %d", n)
	}
	var ketLihat, ipLihat, devLihat string
	if err := e.db.QueryRow(`SELECT TOP 1 keterangan, ISNULL(ip,''), ISNULL(device_id,'') FROM dbo.audit_log WHERE aksi='LIHAT_JALUR' AND baris_id=@p1 AND device_id=@p2`, id, devIpdAskep).Scan(&ketLihat, &ipLihat, &devLihat); err != nil {
		t.Fatalf("baris audit ASKEP: %v", err)
	}
	if ketLihat != "3 titik" || ipLihat == "" {
		t.Fatalf("audit harus memuat jumlah titik dan alamat IP: %q %q", ketLihat, ipLihat)
	}
	e.json("GET", "/api/admin/ipd/bbbb0099-0000-4000-8000-000000000099", ad.AccessToken, nil, 404)
	e.json("GET", "/api/admin/ipd/bukan-uuid", ad.AccessToken, nil, 422)

	// ---- ASKEP: hanya melihat ----
	if l := laporan(ak.AccessToken, "?dari=2026-11-01&sampai=2026-11-30"); l.Inspeksi != 2 {
		t.Fatalf("ASKEP harus bisa melihat laporan: %+v", l)
	}
	e.json("GET", "/api/admin/ipd", ak.AccessToken, nil, 200)
	e.json("GET", "/api/admin/panen", ak.AccessToken, nil, 200)
	e.json("GET", "/api/admin/produktivitas", ak.AccessToken, nil, 200)
	if kode, _ := e.kirim("POST", "/api/ipd", ak.AccessToken, ipdContoh("bbbb0004-0000-4000-8000-000000000004"), nil); kode != 403 {
		t.Fatalf("ASKEP tidak boleh mengirim IPD: %d", kode)
	}
	if kode, _ := e.kirim("PUT", "/api/admin/divisi/1", ak.AccessToken, berkas{"kode": "DIV2", "nama": "x"}, nil); kode != 403 {
		t.Fatalf("ASKEP tidak boleh mengubah master: %d", kode)
	}
	if kode, _ := e.kirim("GET", "/api/admin/ipd/laporan", kr.AccessToken, nil, nil); kode != 403 {
		t.Fatalf("krani tidak boleh melihat laporan IPD: %d", kode)
	}

	// ---- Kebijakan penyimpanan jalur GPS ----
	// Yang sudah selesai diperiksa dan lebih tua dari batas dihapus jalurnya; yang masih MENUNGGU tetap (masih bisa dikirim
	// ulang); data inspeksi lain tidak tersentuh; penghapusan tercatat di audit dan tidak berulang bila dijalankan lagi.
	repoIpd := repository.NewIpdRepository(e.db)
	ctxBersih := context.Background()
	titikSelesai := hitung(t, e.db, `SELECT COUNT(*) FROM dbo.fai_ipd_jejak j JOIN dbo.fai_ipd i ON i.id=j.ipd_id WHERE i.status<>'MENUNGGU'`)
	inspeksiSelesai := hitung(t, e.db, `SELECT COUNT(DISTINCT j.ipd_id) FROM dbo.fai_ipd_jejak j JOIN dbo.fai_ipd i ON i.id=j.ipd_id WHERE i.status<>'MENUNGGU'`)
	titikMenunggu := hitung(t, e.db, `SELECT COUNT(*) FROM dbo.fai_ipd_jejak j JOIN dbo.fai_ipd i ON i.id=j.ipd_id WHERE i.status='MENUNGGU'`)
	pokokSebelum := hitung(t, e.db, `SELECT COUNT(*) FROM dbo.fai_ipd_pokok`)
	if titikSelesai == 0 || titikMenunggu == 0 {
		t.Fatalf("bahan uji harus punya jalur selesai (%d) dan menunggu (%d)", titikSelesai, titikMenunggu)
	}
	if h, err := repoIpd.HapusJalurSebelum(ctxBersih, "2020-01-01"); err != nil || h.Titik != 0 || h.Inspeksi != 0 {
		t.Fatalf("batas lama tidak boleh menghapus: %v %+v", err, h)
	}
	if n := hitung(t, e.db, `SELECT COUNT(*) FROM dbo.audit_log WHERE aksi='HAPUS_JEJAK_LAMA'`); n != 0 {
		t.Fatalf("tanpa penghapusan tidak boleh ada audit: %d", n)
	}
	hasil, errBersih := repoIpd.HapusJalurSebelum(ctxBersih, "2099-01-01")
	if errBersih != nil || hasil.Titik != titikSelesai || hasil.Inspeksi != inspeksiSelesai {
		t.Fatalf("hasil pembersihan: %v %+v (harapan %d titik, %d inspeksi)", errBersih, hasil, titikSelesai, inspeksiSelesai)
	}
	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.fai_ipd_jejak j JOIN dbo.fai_ipd i ON i.id=j.ipd_id WHERE i.status<>'MENUNGGU'`) != 0 ||
		hitung(t, e.db, `SELECT COUNT(*) FROM dbo.fai_ipd_jejak j JOIN dbo.fai_ipd i ON i.id=j.ipd_id WHERE i.status='MENUNGGU'`) != titikMenunggu {
		t.Fatal("jalur yang selesai harus hilang, yang menunggu harus tetap")
	}
	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.fai_ipd_pokok`) != pokokSebelum {
		t.Fatal("data pokok inspeksi tidak boleh ikut terhapus")
	}
	var ketBersih string
	if err := e.db.QueryRow(`SELECT keterangan FROM dbo.audit_log WHERE aksi='HAPUS_JEJAK_LAMA'`).Scan(&ketBersih); err != nil ||
		ketBersih != fmt.Sprintf("%d titik dari %d inspeksi sebelum 2099-01-01", titikSelesai, inspeksiSelesai) {
		t.Fatalf("audit pembersihan: %q %v", ketBersih, err)
	}
	if h, err := repoIpd.HapusJalurSebelum(ctxBersih, "2099-01-01"); err != nil || h.Titik != 0 {
		t.Fatalf("ulang harus kosong: %v %+v", err, h)
	}
	if n := hitung(t, e.db, `SELECT COUNT(*) FROM dbo.audit_log WHERE aksi='HAPUS_JEJAK_LAMA'`); n != 1 {
		t.Fatalf("audit tidak boleh bertambah: %d", n)
	}
	// Setelah dihapus, rincian di web tidak lagi memuat jalur (sama seperti inspeksi tanpa jalur).
	if d := e.json("GET", "/api/admin/ipd/"+id, ad.AccessToken, nil, 200); d["jejak"] != nil {
		t.Fatalf("jalur yang sudah dihapus harus null: %v", d["jejak"])
	}
}
