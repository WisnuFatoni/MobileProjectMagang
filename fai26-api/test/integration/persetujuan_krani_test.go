package integration

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"testing"
)

// Persetujuan lewat server (19 Nov): pengirim tanpa hak setuju (di tes ini krani1) mengirim catatan MENUNGGU; pemegang
// panen.setujui (di tes ini mandor1) melihatnya dari akun/HP lain, lalu menyetujui atau menolaknya.
func TestPersetujuanLewatServer(t *testing.T) {
	e := siapkanUji(t)
	if hitung(t, e.db, `SELECT COUNT(*) FROM sys.check_constraints WHERE name='ck_fai_panen_status_erp' AND definition LIKE '%MENUNGGU%'`) == 0 {
		t.Skip("migrasi 008 belum dijalankan di database uji")
	}
	_, pengirim := e.login("krani1", "krani123", devKrani)
	_, penyetuju := e.login("mandor1", "mandor123", devLain)
	if e.json("GET", "/api/panen/menunggu", pengirim.AccessToken, nil, 403); false {
		return
	}

	id1 := "99999999-9999-4999-8999-000000000001"
	p1 := panenContoh(id1)
	p1.DisetujuiUsername, p1.DisetujuiPada = "", ""
	if kode, b := e.kirim("POST", "/api/panen", pengirim.AccessToken, p1, nil); kode != 201 {
		t.Fatalf("kirim menunggu: %d %s", kode, b)
	}
	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.fai_panen WHERE id=@p1 AND status_erp='MENUNGGU' AND disetujui_oleh IS NULL`, id1) != 1 {
		t.Fatal("harus MENUNGGU tanpa penyetuju")
	}
	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.v_fai_panen_siap_impor WHERE panen_id=@p1`, id1) != 0 {
		t.Fatal("catatan MENUNGGU tidak boleh terlihat oleh HVDM")
	}

	// Pengirim melihat status MENUNGGU_SETUJU di HP-nya.
	status := func(token, id string) (string, string) {
		kode, b := e.kirim("GET", "/api/panen", token, nil, nil)
		if kode != 200 {
			t.Fatalf("daftar status: %d %s", kode, b)
		}
		var l []struct {
			ID, Status string
			Pesan      *string
		}
		_ = json.Unmarshal(b, &l)
		for _, x := range l {
			if x.ID == id {
				pesan := ""
				if x.Pesan != nil {
					pesan = *x.Pesan
				}
				return x.Status, pesan
			}
		}
		return "", ""
	}
	if st, _ := status(pengirim.AccessToken, id1); st != "MENUNGGU_SETUJU" {
		t.Fatalf("status di HP pengirim harus MENUNGGU_SETUJU: %q", st)
	}

	// Penyetuju (akun/HP lain) melihat dan menyetujui.
	daftar := e.daftar("/api/panen/menunggu", penyetuju.AccessToken)
	baris := cari(daftar, "id", id1)
	if baris == nil || baris["total_janjang"] != float64(10) || baris["pencatat"] != "krani1" || baris["pemanen_nama"] != "Pemanen Contoh 1" {
		t.Fatalf("baris menunggu: %v", baris)
	}
	if g, _ := baris["grading"].([]any); len(g) != 2 {
		t.Fatalf("grading hanya kelas bernilai: %v", baris["grading"])
	}
	// Foto bukti untuk krani: belum ada foto berarti foto_id null; setelah foto terkirim, foto_id terisi dan fotonya bisa diunduh.
	if baris["foto_id"] != nil {
		t.Fatalf("sebelum foto terkirim foto_id harus null: %v", baris["foto_id"])
	}
	fotoID := "efefefef-efef-4fef-8fef-efefefefef01"
	jpeg := append([]byte{0xFF, 0xD8, 0xFF, 0xE0}, bytes.Repeat([]byte{0x42}, 300)...)
	h := sha256.Sum256(jpeg)
	if kode, b := e.kirim("POST", "/api/panen/"+id1+"/media", pengirim.AccessToken, jpeg, map[string]string{"X-Foto-Id": fotoID, "X-Sha256": hex.EncodeToString(h[:])}); kode != 201 {
		t.Fatalf("kirim foto: %d %s", kode, b)
	}
	if b := cari(e.daftar("/api/panen/menunggu", penyetuju.AccessToken), "id", id1); b == nil || b["foto_id"] != fotoID {
		t.Fatalf("foto_id harus terisi setelah foto terkirim: %v", b)
	}
	if kode, isi := e.kirim("GET", "/api/admin/foto/"+fotoID, penyetuju.AccessToken, nil, nil); kode != 200 || !bytes.Equal(isi, jpeg) {
		t.Fatalf("penyetuju harus bisa mengunduh foto: %d (%d byte)", kode, len(isi))
	}
	e.json("POST", "/api/panen/"+id1+"/setujui", penyetuju.AccessToken, nil, 200)
	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.v_fai_panen_siap_impor WHERE panen_id=@p1 AND status_erp='BARU' AND disetujui_username='mandor1'`, id1) != 1 {
		t.Fatal("setelah disetujui harus BARU dan terlihat HVDM atas nama penyetuju")
	}
	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.audit_log WHERE baris_id=@p1 AND aksi='SETUJU'`, id1) != 1 {
		t.Fatal("audit SETUJU tercatat")
	}
	e.json("POST", "/api/panen/"+id1+"/setujui", penyetuju.AccessToken, nil, 409)
	e.json("POST", "/api/panen/"+id1+"/tolak", penyetuju.AccessToken, berkas{"alasan": "terlambat"}, 409)
	if st, _ := status(pengirim.AccessToken, id1); st != "TERKIRIM" {
		t.Fatalf("setelah disetujui HP pengirim melihat TERKIRIM: %q", st)
	}
	e.json("POST", "/api/panen/99999999-9999-4999-8999-0000000000ff/setujui", penyetuju.AccessToken, nil, 404)
	e.json("POST", "/api/panen/bukan-uuid/setujui", penyetuju.AccessToken, nil, 422)
	e.json("POST", "/api/panen/"+id1+"/setujui", pengirim.AccessToken, nil, 403) // pengirim tidak punya hak setuju

	// Penolakan, perbaikan, dan kirim ulang.
	id2 := "99999999-9999-4999-8999-000000000002"
	p2 := panenContoh(id2)
	p2.DisetujuiUsername, p2.DisetujuiPada = "", ""
	p2.TphKode = "02"
	if kode, b := e.kirim("POST", "/api/panen", pengirim.AccessToken, p2, nil); kode != 201 {
		t.Fatalf("kirim kedua: %d %s", kode, b)
	}
	e.json("POST", "/api/panen/"+id2+"/tolak", penyetuju.AccessToken, berkas{"alasan": "  "}, 422)
	e.json("POST", "/api/panen/"+id2+"/tolak", penyetuju.AccessToken, berkas{"alasan": "Blok salah"}, 200)
	if st, pesan := status(pengirim.AccessToken, id2); st != "DITOLAK" || pesan != "Ditolak krani: Blok salah" {
		t.Fatalf("pengirim harus melihat DITOLAK dengan alasan: %q %q", st, pesan)
	}
	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.fai_impor_log WHERE panen_id=@p1 AND hasil='GAGAL' AND pesan_error=N'Ditolak krani: Blok salah'`, id2) != 1 {
		t.Fatal("alasan tolak tercatat di fai_impor_log")
	}
	if len(e.daftar("/api/panen/menunggu", penyetuju.AccessToken)) != 0 {
		t.Fatal("tidak ada lagi yang menunggu")
	}
	// Pengirim memperbaiki lalu kirim ulang: kembali MENUNGGU, pesan lama hilang.
	p2.KirimUlang = true
	p2.Grading[0].JmlFinal = 8
	p2.Pemanen[0].JjgAlokasi = 9
	if kode, b := e.kirim("POST", "/api/panen", pengirim.AccessToken, p2, nil); kode != 201 {
		t.Fatalf("kirim ulang: %d %s", kode, b)
	}
	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.fai_panen WHERE id=@p1 AND status_erp='MENUNGGU' AND disetujui_oleh IS NULL`, id2) != 1 {
		t.Fatal("setelah diperbaiki harus MENUNGGU lagi")
	}
	if st, pesan := status(pengirim.AccessToken, id2); st != "MENUNGGU_SETUJU" || pesan != "" {
		t.Fatalf("status setelah kirim ulang: %q pesan=%q", st, pesan)
	}
	if b := cari(e.daftar("/api/panen/menunggu", penyetuju.AccessToken), "id", id2); b == nil || b["total_janjang"] != float64(9) {
		t.Fatalf("isi perbaikan harus terlihat: %v", b)
	}

	// Aturan edit: selama belum disetujui krani, pencatat boleh mengubah catatan yang sudah terkirim (tetap MENUNGGU, isi diganti).
	p2.Grading[0].JmlFinal = 6
	p2.Pemanen[0].JjgAlokasi = 7
	if kode, b := e.kirim("POST", "/api/panen", pengirim.AccessToken, p2, nil); kode != 201 {
		t.Fatalf("ubah catatan MENUNGGU harus 201: %d %s", kode, b)
	}
	if b := cari(e.daftar("/api/panen/menunggu", penyetuju.AccessToken), "id", id2); b == nil || b["total_janjang"] != float64(7) {
		t.Fatalf("isi hasil ubah harus terlihat krani: %v", b)
	}
	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.fai_panen WHERE id=@p1 AND status_erp='MENUNGGU'`, id2) != 1 {
		t.Fatal("setelah diubah catatan tetap MENUNGGU")
	}
	// Setelah disetujui krani, catatan terkunci: mengubahnya dijawab 423 dan isinya tidak berubah.
	e.json("POST", "/api/panen/"+id2+"/setujui", penyetuju.AccessToken, nil, 200)
	p2.Grading[0].JmlFinal = 1
	p2.Pemanen[0].JjgAlokasi = 2
	if kode, b := e.kirim("POST", "/api/panen", pengirim.AccessToken, p2, nil); kode != 423 {
		t.Fatalf("catatan yang sudah disetujui harus terkunci (423): %d %s", kode, b)
	}
	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.v_fai_panen_siap_impor WHERE panen_id=@p1 AND jjg_total=7`, id2) != 1 {
		t.Fatal("isi catatan terkunci tidak boleh berubah")
	}
	// Catatan lain yang sudah disetujui sejak awal (id1) juga terkunci.
	p1.KirimUlang = true
	if kode, b := e.kirim("POST", "/api/panen", pengirim.AccessToken, p1, nil); kode != 423 {
		t.Fatalf("id1 harus terkunci (423): %d %s", kode, b)
	}
}
