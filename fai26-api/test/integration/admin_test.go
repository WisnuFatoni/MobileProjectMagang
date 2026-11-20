package integration

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"testing"

	"fai26-api/internal/model"
)

const devAdmin = "33333333-3333-4333-8333-333333333333"

type berkas = map[string]any

func (e *lingkunganUji) json(metode, path, token string, badan any, wantKode int) berkas {
	e.t.Helper()
	kode, b := e.kirim(metode, path, token, badan, nil)
	if kode != wantKode {
		e.t.Fatalf("%s %s: harapan %d, dapat %d %s", metode, path, wantKode, kode, b)
	}
	var m berkas
	_ = json.Unmarshal(b, &m)
	return m
}

func (e *lingkunganUji) daftar(path, token string) []berkas {
	e.t.Helper()
	kode, b := e.kirim("GET", path, token, nil, nil)
	if kode != 200 {
		e.t.Fatalf("GET %s: %d %s", path, kode, b)
	}
	var out []berkas
	if err := json.Unmarshal(b, &out); err != nil {
		e.t.Fatal(err)
	}
	return out
}

func idDari(m berkas) int64 { return int64(m["id"].(float64)) }

func cari(daftar []berkas, kolom string, nilai any) berkas {
	for _, d := range daftar {
		if d[kolom] == nilai {
			return d
		}
	}
	return nil
}

func (e *lingkunganUji) bersihkanMasterUji() {
	for _, q := range []string{
		`DELETE FROM dbo.tph WHERE blok_id IN (SELECT blok_id FROM dbo.blok WHERE kode LIKE 'T9%')`,
		`DELETE FROM dbo.blok WHERE kode LIKE 'T9%'`,
		`DELETE FROM dbo.divisi WHERE kode LIKE 'TD%'`,
		`DELETE FROM dbo.pemanen WHERE erp_hrmb_code >= 99990`,
		`DELETE FROM dbo.user_role WHERE user_id IN (SELECT user_id FROM dbo.app_user WHERE username LIKE 'uji.%')`,
		`DELETE FROM dbo.auth_sesi WHERE user_id IN (SELECT user_id FROM dbo.app_user WHERE username LIKE 'uji.%')`,
		`DELETE FROM dbo.app_user WHERE username LIKE 'uji.%'`,
	} {
		if _, err := e.db.Exec(q); err != nil {
			e.t.Logf("bersih-bersih (%s): %v", q, err)
		}
	}
}

func TestAdminHakAkses(t *testing.T) {
	e := siapkanUji(t)
	_, kr := e.login("krani1", "krani123", devKrani)

	// Krani boleh melihat master (master.lihat) tetapi tidak mengubahnya (master.kelola) dan tidak melihat pengguna.
	if kode, _ := e.kirim("GET", "/api/admin/blok", kr.AccessToken, nil, nil); kode != 200 {
		t.Fatalf("krani GET blok: %d", kode)
	}
	if kode, _ := e.kirim("POST", "/api/admin/blok", kr.AccessToken, berkas{"kode": "T90", "erp_oid": 1}, nil); kode != 403 {
		t.Fatalf("krani POST blok harus 403, dapat %d", kode)
	}
	for _, p := range []string{"/api/admin/pengguna", "/api/admin/perangkat", "/api/admin/audit", "/api/admin/ringkasan", "/api/admin/peran"} {
		if kode, _ := e.kirim("GET", p, kr.AccessToken, nil, nil); kode != 403 {
			t.Fatalf("krani GET %s harus 403, dapat %d", p, kode)
		}
	}
	if kode, _ := e.kirim("GET", "/api/admin/blok", "", nil, nil); kode != 401 {
		t.Fatalf("tanpa token harus 401, dapat %d", kode)
	}
}

func TestAdminMasterAlurLengkap(t *testing.T) {
	e := siapkanUji(t)
	e.bersihkanMasterUji()
	t.Cleanup(e.bersihkanMasterUji)
	_, ad := e.login("admin1", "admin12345", devAdmin)
	tok := ad.AccessToken

	// Versi master sebelum perubahan.
	var versi0 string
	{
		m := e.json("GET", "/api/master", tok, nil, 200)
		versi0, _ = m["versi"].(string)
	}

	// Divisi: buat, ganda, ubah.
	div := idDari(e.json("POST", "/api/admin/divisi", tok, berkas{"kode": "TD1", "nama": "Divisi Uji"}, 201))
	e.json("POST", "/api/admin/divisi", tok, berkas{"kode": "TD1", "nama": "Ganda"}, 409)
	e.json("PUT", fmt.Sprintf("/api/admin/divisi/%d", div), tok, berkas{"kode": "TD1", "nama": "Divisi Uji (ubah)"}, 200)
	if cari(e.daftar("/api/admin/divisi", tok), "kode", "TD1")["nama"] != "Divisi Uji (ubah)" {
		t.Fatal("nama divisi tidak berubah")
	}

	// Blok: buat, ganda, divisi tidak ada.
	blok := idDari(e.json("POST", "/api/admin/blok", tok, berkas{"kode": "T91", "erp_oid": 9991, "divisi_id": div, "luas_ha": 12.5, "tahun_tanam": 2015, "sph": 130}, 201))
	e.json("POST", "/api/admin/blok", tok, berkas{"kode": "T91", "erp_oid": 1}, 409)
	e.json("POST", "/api/admin/blok", tok, berkas{"kode": "T93", "erp_oid": 1, "divisi_id": 999999}, 422)
	e.json("POST", "/api/admin/blok", tok, berkas{"kode": "T9|4", "erp_oid": 1}, 422)

	// TPH: QR dibuat otomatis sesuai spesifikasi.
	tph1 := idDari(e.json("POST", "/api/admin/tph", tok, berkas{"blok_id": blok, "no_tph": "01"}, 201))
	e.json("POST", "/api/admin/tph", tok, berkas{"blok_id": blok, "no_tph": "01"}, 409)
	row := cari(e.daftar(fmt.Sprintf("/api/admin/tph?blok_id=%d", blok), tok), "tph_id", float64(tph1))
	if row["qr_kode"] != "FAI26|TPH|1|KP_STL|T91|01" || row["blok_kode"] != "T91" {
		t.Fatalf("qr_kode: %v", row)
	}

	// Massal: 02..04 berhasil; yang bentrok dengan 01 membatalkan semuanya (atomik).
	m := e.json("POST", "/api/admin/tph/massal", tok, berkas{"blok_id": blok, "dari": 2, "sampai": 4, "lebar": 2}, 201)
	if m["dibuat"] != float64(3) {
		t.Fatalf("massal: %v", m)
	}
	e.json("POST", "/api/admin/tph/massal", tok, berkas{"blok_id": blok, "dari": 4, "sampai": 7, "lebar": 2}, 409)
	if n := len(e.daftar(fmt.Sprintf("/api/admin/tph?blok_id=%d", blok), tok)); n != 4 {
		t.Fatalf("massal yang gagal tidak boleh meninggalkan sebagian: %d TPH", n)
	}
	e.json("POST", "/api/admin/tph/massal", tok, berkas{"blok_id": blok, "dari": 1, "sampai": 999, "lebar": 2}, 422)

	// Koordinat tanpa mengubah QR; ubah nomor mengubah QR.
	r := e.json("PUT", fmt.Sprintf("/api/admin/tph/%d", tph1), tok, berkas{"blok_id": blok, "no_tph": "01", "lat": -2.0, "lon": 113.0}, 200)
	if r["qr_berubah"] != false {
		t.Fatalf("QR tidak boleh berubah: %v", r)
	}
	r = e.json("PUT", fmt.Sprintf("/api/admin/tph/%d", tph1), tok, berkas{"blok_id": blok, "no_tph": "09", "lat": -2.0, "lon": 113.0}, 200)
	if r["qr_berubah"] != true {
		t.Fatalf("QR harus berubah: %v", r)
	}
	e.json("PUT", fmt.Sprintf("/api/admin/tph/%d", tph1), tok, berkas{"blok_id": blok, "no_tph": "02"}, 409)
	e.json("PUT", fmt.Sprintf("/api/admin/tph/%d", tph1), tok, berkas{"blok_id": blok, "no_tph": "09", "lat": -2.0}, 422)

	// Ubah kode blok: semua QR TPH-nya ikut berubah.
	r = e.json("PUT", fmt.Sprintf("/api/admin/blok/%d", blok), tok, berkas{"kode": "T92", "erp_oid": 9991, "divisi_id": div}, 200)
	if r["qr_berubah"] != float64(4) {
		t.Fatalf("empat QR harus berubah: %v", r)
	}
	for _, row := range e.daftar(fmt.Sprintf("/api/admin/tph?blok_id=%d", blok), tok) {
		if q, _ := row["qr_kode"].(string); len(q) < 22 || q[:21] != "FAI26|TPH|1|KP_STL|T9" || row["blok_kode"] != "T92" {
			t.Fatalf("qr tidak mengikuti kode blok baru: %v", row)
		}
	}
	if q := cari(e.daftar(fmt.Sprintf("/api/admin/tph?blok_id=%d", blok), tok), "tph_id", float64(tph1))["qr_kode"]; q != "FAI26|TPH|1|KP_STL|T92|09" {
		t.Fatalf("qr tph1: %v", q)
	}

	// Aturan penghapusan: tidak boleh bila masih ada isinya.
	e.json("DELETE", fmt.Sprintf("/api/admin/blok/%d", blok), tok, nil, 409)
	e.json("DELETE", fmt.Sprintf("/api/admin/divisi/%d", div), tok, nil, 409)
	for _, row := range e.daftar(fmt.Sprintf("/api/admin/tph?blok_id=%d", blok), tok) {
		e.json("DELETE", fmt.Sprintf("/api/admin/tph/%d", int64(row["tph_id"].(float64))), tok, nil, 200)
	}
	e.json("DELETE", fmt.Sprintf("/api/admin/blok/%d", blok), tok, nil, 200)
	e.json("DELETE", fmt.Sprintf("/api/admin/divisi/%d", div), tok, nil, 200)
	e.json("DELETE", fmt.Sprintf("/api/admin/blok/%d", blok), tok, nil, 404)

	// Pemanen.
	pem := idDari(e.json("POST", "/api/admin/pemanen", tok, berkas{"erp_hrmb_code": 99991, "kode": "P-UJI", "nama": "Pemanen Uji"}, 201))
	e.json("POST", "/api/admin/pemanen", tok, berkas{"erp_hrmb_code": 99991, "nama": "Ganda"}, 409)
	e.json("POST", "/api/admin/pemanen", tok, berkas{"nama": "Tanpa kode ERP"}, 422)
	e.json("PUT", fmt.Sprintf("/api/admin/pemanen/%d", pem), tok, berkas{"erp_hrmb_code": 99991, "nama": "Pemanen Uji (ubah)", "aktif": false}, 200)
	if p := cari(e.daftar("/api/admin/pemanen", tok), "erp_hrmb_code", float64(99991)); p["nama"] != "Pemanen Uji (ubah)" || p["aktif"] != false {
		t.Fatalf("pemanen: %v", p)
	}
	e.json("DELETE", fmt.Sprintf("/api/admin/pemanen/%d", pem), tok, nil, 200)

	// Semua perubahan itu mengubah versi master HP, dan baris yang dihapus lunak tetap dikirim ke HP.
	mm := e.json("GET", "/api/master?versi="+versi0, tok, nil, 200)
	if mm["berubah"] != true || mm["versi"] == versi0 {
		t.Fatalf("versi master harus berubah: %v", mm["versi"])
	}
	var hapus bool
	for _, p := range mm["pemanen"].([]any) {
		pm := p.(map[string]any)
		if pm["erp_hrmb_code"] == float64(99991) && pm["dihapus_pada"] != nil {
			hapus = true
		}
	}
	if !hapus {
		t.Fatal("pemanen yang dihapus lunak harus tetap ada di unduhan HP dengan dihapus_pada terisi")
	}

	// Jejak audit: perubahan oleh admin tercatat.
	a := e.json("GET", "/api/admin/audit?tabel=blok&per_halaman=50", tok, nil, 200)
	var aksi = map[string]bool{}
	for _, it := range a["item"].([]any) {
		aksi[it.(map[string]any)["aksi"].(string)] = true
	}
	if !aksi["BUAT"] || !aksi["UBAH"] || !aksi["HAPUS"] {
		t.Fatalf("audit blok harus memuat BUAT, UBAH, HAPUS: %v", aksi)
	}
}

func TestAdminPengguna(t *testing.T) {
	e := siapkanUji(t)
	e.bersihkanMasterUji()
	t.Cleanup(e.bersihkanMasterUji)
	_, ad := e.login("admin1", "admin12345", devAdmin)
	tok := ad.AccessToken

	roles := e.daftar("/api/admin/peran", tok)
	if cari(roles, "kode", "MANDOR") == nil || len(roles) != 5 {
		t.Fatalf("role: %v", roles)
	}

	id := idDari(e.json("POST", "/api/admin/pengguna", tok, berkas{"username": "uji.satu", "nama": "Uji Satu", "password": "sandiAwal123", "role": []string{"KRANI"}}, 201))
	e.json("POST", "/api/admin/pengguna", tok, berkas{"username": "uji.satu", "nama": "Ganda", "password": "sandiAwal123", "role": []string{"KRANI"}}, 409)
	e.json("POST", "/api/admin/pengguna", tok, berkas{"username": "uji.dua", "nama": "x", "password": "pendek", "role": []string{"KRANI"}}, 422)
	e.json("POST", "/api/admin/pengguna", tok, berkas{"username": "uji.dua", "nama": "x", "password": "sandiAwal123", "role": []string{"TIDAK_ADA"}}, 422)
	e.json("POST", "/api/admin/pengguna", tok, berkas{"username": "Uji Dua", "nama": "x", "password": "sandiAwal123", "role": []string{"KRANI"}}, 422)

	// Pengguna baru bisa login dan wajib ganti password.
	_, baru := e.login("uji.satu", "sandiAwal123", devLain)
	if baru.AccessToken == "" || !baru.Profil.WajibGanti || len(baru.Profil.Role) != 1 || baru.Profil.Role[0] != "KRANI" {
		t.Fatalf("login pengguna baru: %+v", baru.Profil)
	}

	// Ubah role: izin berubah langsung (dibaca ulang tiap panggilan).
	e.json("PUT", fmt.Sprintf("/api/admin/pengguna/%d", id), tok, berkas{"nama": "Uji Satu", "role": []string{"MANDOR"}}, 200)
	if kode, _ := e.kirim("GET", "/api/admin/blok", baru.AccessToken, nil, nil); kode != 200 {
		t.Fatalf("mandor punya master.lihat: %d", kode)
	}
	u := cari(e.daftar("/api/admin/pengguna", tok), "username", "uji.satu")
	if u == nil || fmt.Sprint(u["role"]) != "[MANDOR]" {
		t.Fatalf("daftar pengguna: %v", u)
	}

	// Reset password mencabut sesi lama; password lama tidak berlaku lagi.
	e.json("POST", fmt.Sprintf("/api/admin/pengguna/%d/reset-password", id), tok, berkas{"password": "sandiBaru456"}, 200)
	if kode, _ := e.kirim("POST", "/api/auth/refresh", "", berkas{"refresh_token": baru.RefreshToken}, nil); kode != 401 {
		t.Fatalf("sesi lama harus dicabut, dapat %d", kode)
	}
	if kode, _ := e.login("uji.satu", "sandiAwal123", devLain); kode != 401 {
		t.Fatalf("password lama harus ditolak, dapat %d", kode)
	}
	if kode, _ := e.login("uji.satu", "sandiBaru456", devLain); kode != 200 {
		t.Fatalf("password baru harus berlaku, dapat %d", kode)
	}
	e.json("POST", fmt.Sprintf("/api/admin/pengguna/%d/reset-password", id), tok, berkas{"password": "pendek"}, 422)
	e.json("POST", "/api/admin/pengguna/999999/reset-password", tok, berkas{"password": "sandiBaru456"}, 404)

	// Nonaktifkan: tidak bisa login; tidak boleh menonaktifkan diri sendiri.
	e.json("PUT", fmt.Sprintf("/api/admin/pengguna/%d", id), tok, berkas{"nama": "Uji Satu", "aktif": false, "role": []string{"KRANI"}}, 200)
	if kode, _ := e.login("uji.satu", "sandiBaru456", devLain); kode != 403 {
		t.Fatalf("akun nonaktif harus 403, dapat %d", kode)
	}
	e.json("PUT", fmt.Sprintf("/api/admin/pengguna/%d", ad.Profil.UserID), tok, berkas{"nama": "Admin", "aktif": false, "role": []string{"ADMIN"}}, 422)
	e.json("PUT", "/api/admin/pengguna/999999", tok, berkas{"nama": "x", "role": []string{"KRANI"}}, 404)
}

func TestAdminPerangkat(t *testing.T) {
	e := siapkanUji(t)
	_, ad := e.login("admin1", "admin12345", devAdmin)
	_, kr := e.login("krani1", "krani123", devKrani)
	tok := ad.AccessToken

	p := cari(e.daftar("/api/admin/perangkat", tok), "device_id", devKrani)
	if p == nil || p["aktif"] != true || p["nama"] != "HP uji" {
		t.Fatalf("perangkat krani: %v", p)
	}

	e.json("PUT", "/api/admin/perangkat/"+devKrani, tok, berkas{"aktif": false}, 200)
	if kode, _ := e.kirim("GET", "/api/auth/me", kr.AccessToken, nil, nil); kode != 403 && kode != 200 {
		t.Fatalf("me: %d", kode)
	}
	if kode, _ := e.kirim("POST", "/api/auth/refresh", "", berkas{"refresh_token": kr.RefreshToken}, nil); kode != 401 {
		t.Fatalf("sesi perangkat nonaktif harus dicabut, dapat %d", kode)
	}
	if kode, _ := e.login("krani1", "krani123", devKrani); kode != 403 {
		t.Fatalf("login dari perangkat nonaktif harus 403, dapat %d", kode)
	}
	e.json("PUT", "/api/admin/perangkat/"+devKrani, tok, berkas{"aktif": true}, 200)
	if kode, _ := e.login("krani1", "krani123", devKrani); kode != 200 {
		t.Fatalf("setelah diaktifkan harus bisa login, dapat %d", kode)
	}
	e.json("PUT", "/api/admin/perangkat/"+devAdmin, tok, berkas{"aktif": false}, 422) // perangkat sendiri
	e.json("PUT", "/api/admin/perangkat/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", tok, berkas{"aktif": false}, 404)
	e.json("PUT", "/api/admin/perangkat/bukan-uuid", tok, berkas{"aktif": false}, 422)
}

func TestAdminCatatanFotoRingkasanDanAudit(t *testing.T) {
	e := siapkanUji(t)
	_, kr := e.login("krani1", "krani123", devKrani)
	_, ad := e.login("admin1", "admin12345", devAdmin)
	tok := ad.AccessToken
	id := "abababab-abab-4bab-8bab-abababababab"
	fotoID := "cdcdcdcd-cdcd-4dcd-8dcd-cdcdcdcdcdcd"

	hari := "2026-11-10"
	in := model.PanenInput{
		PanenID: id, UnitKode: "KP_STL", TanggalPanen: hari, Asm3Oid: 1012, BlokKode: "A12", TphKode: "01", BrondolanKg: 12.5,
		StatusLokasi: "DI_LUAR", GpsMock: true, ModelVersi: "best 1", Catatan: "uji admin",
		DisetujuiUsername: "mandor1", DisetujuiPada: "2026-11-10T03:00:00Z", DibuatPada: "2026-11-10T01:00:00Z",
		Grading: []model.GradingInput{{KelasKode: "ripe", ErpKolom: "JJG_RIPE", JmlAI: 7, JmlFinal: 9, DiubahManual: true}, {KelasKode: "unripe", JmlFinal: 1}},
		Pemanen: []model.PemanenInput{{HrmbCode: 90001, JjgAlokasi: 10}},
	}
	e.json("POST", "/api/panen", kr.AccessToken, in, 201)
	jpeg := append([]byte{0xFF, 0xD8, 0xFF, 0xE0}, bytes.Repeat([]byte{0x42}, 300)...)
	h := sha256.Sum256(jpeg)
	kode, b := e.kirim("POST", "/api/panen/"+id+"/media", kr.AccessToken, jpeg, map[string]string{"X-Foto-Id": fotoID, "X-Sha256": hex.EncodeToString(h[:])})
	if kode != 201 {
		t.Fatalf("foto: %d %s", kode, b)
	}

	// Daftar dengan filter.
	hal := e.json("GET", "/api/admin/panen?dari="+hari+"&sampai="+hari+"&status_erp=BARU&cari=A12", tok, nil, 200)
	if hal["total"] != float64(1) {
		t.Fatalf("total: %v", hal)
	}
	it := hal["item"].([]any)[0].(map[string]any)
	if it["id"] != id || it["total_janjang"] != float64(10) || it["pencatat"] != "Krani Contoh" || it["penyetuju"] != "Mandor Contoh" || it["gps_mock"] != true {
		t.Fatalf("baris: %v", it)
	}
	if h2 := e.json("GET", "/api/admin/panen?status_erp=DIIMPOR", tok, nil, 200); h2["total"] != float64(0) {
		t.Fatalf("filter status: %v", h2["total"])
	}
	e.json("GET", "/api/admin/panen?dari=kemarin", tok, nil, 422)
	e.json("GET", "/api/admin/panen?status_erp=SELESAI", tok, nil, 422)

	// Detail beserta foto.
	d := e.json("GET", "/api/admin/panen/"+id, tok, nil, 200)
	if d["asm3_oid"] != float64(1012) || d["catatan"] != "uji admin" || d["status_lokasi"] != "DI_LUAR" {
		t.Fatalf("detail: %v", d)
	}
	if len(d["grading"].([]any)) != 2 || len(d["pemanen"].([]any)) != 1 || len(d["foto"].([]any)) != 1 {
		t.Fatalf("anak detail: %v", d)
	}
	if pm := d["pemanen"].([]any)[0].(map[string]any); pm["nama"] != "Pemanen Contoh 1" {
		t.Fatalf("nama pemanen harus dicocokkan dari master: %v", pm)
	}
	e.json("GET", "/api/admin/panen/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", tok, nil, 404)
	e.json("GET", "/api/admin/panen/bukan-uuid", tok, nil, 422)

	// Foto: byte sama, tipe gambar, dan hanya untuk yang berhak.
	kode, isi := e.kirim("GET", "/api/admin/foto/"+fotoID, tok, nil, nil)
	if kode != 200 || !bytes.Equal(isi, jpeg) {
		t.Fatalf("foto: %d, %d byte", kode, len(isi))
	}
	if kode, _ := e.kirim("GET", "/api/admin/foto/"+fotoID, "", nil, nil); kode != 401 {
		t.Fatalf("foto tanpa token harus 401, dapat %d", kode)
	}
	if kode, _ := e.kirim("GET", "/api/admin/foto/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", tok, nil, nil); kode != 404 {
		t.Fatalf("foto tak ada harus 404, dapat %d", kode)
	}
	if kode, _ := e.kirim("GET", "/api/admin/foto/..%2F..%2Fsecret", tok, nil, nil); kode != 422 && kode != 404 {
		t.Fatalf("path traversal harus ditolak, dapat %d", kode)
	}

	// Ringkasan.
	r := e.json("GET", "/api/admin/ringkasan?tanggal="+hari, tok, nil, 200)
	if r["catatan_hari_ini"] != float64(1) || r["janjang_hari_ini"] != float64(10) || r["perlu_perhatian"] != float64(1) {
		t.Fatalf("ringkasan: %v", r)
	}
	if st := r["catatan_per_status"].(map[string]any); st["BARU"] != float64(1) {
		t.Fatalf("per status: %v", st)
	}
	if tr := r["tren_7_hari"].([]any); len(tr) != 1 {
		t.Fatalf("tren: %v", tr)
	}
	e.json("GET", "/api/admin/ringkasan?tanggal=kemarin", tok, nil, 422)

	// Audit: penerimaan catatan terlihat, dengan filter.
	a := e.json("GET", "/api/admin/audit?tabel=fai_panen&aksi=TERIMA&cari="+id[:8], tok, nil, 200)
	if a["total"] != float64(1) {
		t.Fatalf("audit: %v", a)
	}
	e.json("GET", "/api/admin/audit?dari=besok-lusa", tok, nil, 422)
}
