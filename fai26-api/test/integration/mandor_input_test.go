package integration

import (
	"fmt"
	"testing"
)

// Alur baru (18 Nov): mandor yang menginput. Akun mandor membawa kode HRMB dan afdeling; master HP dibatasi ke pemanen
// miliknya; kirim tanpa langkah persetujuan; kombinasi ganda 409; catatan DITOLAK bisa dikirim ulang dengan panen_id yang sama.
func TestMandorMenginputAlurPenuh(t *testing.T) {
	e := siapkanUji(t)
	if hitung(t, e.db, `SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID('dbo.app_user') AND name='mandor_hrmb_code'`) == 0 {
		t.Skip("migrasi 007 belum dijalankan di database uji")
	}
	_, ad := e.login("admin1", "admin12345", devAdmin)
	t.Cleanup(func() {
		_, _ = e.db.Exec(`UPDATE dbo.app_user SET mandor_hrmb_code=NULL, afdeling_kode=NULL WHERE username='mandor1'`)
		_, _ = e.db.Exec(`UPDATE dbo.pemanen SET mandor_hrmb_code=NULL WHERE pemanen_id IN (1,2)`)
		_, _ = e.db.Exec(`UPDATE dbo.divisi SET erp_oid=NULL WHERE divisi_id=1`)
	})

	// Admin: jadikan mandor1 sebagai mandor 7001 di afdeling DIV2; pemanen 90001 miliknya, 90002 milik mandor lain.
	e.json("PUT", "/api/admin/divisi/1", ad.AccessToken, berkas{"kode": "DIV2", "nama": "Divisi 2 (contoh)", "erp_oid": 7}, 200)
	e.json("PUT", "/api/admin/pemanen/1", ad.AccessToken, berkas{"erp_hrmb_code": 90001, "mandor_hrmb_code": 7001, "kode": "P-001", "nama": "Pemanen Contoh 1"}, 200)
	e.json("PUT", "/api/admin/pemanen/2", ad.AccessToken, berkas{"erp_hrmb_code": 90002, "mandor_hrmb_code": 7002, "kode": "P-002", "nama": "Pemanen Contoh 2"}, 200)
	idMandor := 0.0
	for _, u := range e.daftar("/api/admin/pengguna", ad.AccessToken) {
		if u["username"] == "mandor1" {
			idMandor = u["user_id"].(float64)
		}
	}
	e.json("PUT", fmt.Sprintf("/api/admin/pengguna/%d", int64(idMandor)), ad.AccessToken,
		berkas{"nama": "Mandor Contoh", "role": []string{"MANDOR"}, "mandor_hrmb_code": 7001, "afdeling_kode": "DIV2"}, 200)
	if u := cari(e.daftar("/api/admin/pengguna", ad.AccessToken), "username", "mandor1"); u["mandor_hrmb_code"] != float64(7001) || u["afdeling_kode"] != "DIV2" {
		t.Fatalf("daftar pengguna harus memuat mandor dan afdeling: %v", u)
	}
	e.json("PUT", fmt.Sprintf("/api/admin/pengguna/%d", int64(idMandor)), ad.AccessToken,
		berkas{"nama": "Mandor Contoh", "role": []string{"MANDOR"}, "mandor_hrmb_code": -1}, 422)

	// Login mengembalikan profil mandor.
	kode, md := e.login("mandor1", "mandor123", devLain)
	if kode != 200 || md.Profil.MandorHrmbCode == nil || *md.Profil.MandorHrmbCode != 7001 || md.Profil.AfdelingKode == nil || *md.Profil.AfdelingKode != "DIV2" || md.Profil.UnitKode != "KP_STL" {
		t.Fatalf("profil mandor: %d %+v", kode, md.Profil)
	}

	// Master untuk mandor: hanya pemanen miliknya dan blok afdelingnya; krani (tanpa kode mandor) tetap menerima semuanya.
	m := e.json("GET", "/api/master", md.AccessToken, nil, 200)
	pem, _ := m["pemanen"].([]any)
	if len(pem) != 1 || pem[0].(map[string]any)["erp_hrmb_code"] != float64(90001) {
		t.Fatalf("master mandor harus berisi hanya pemanen miliknya: %v", pem)
	}
	if blok, _ := m["blok"].([]any); len(blok) != 2 {
		t.Fatalf("blok afdeling DIV2 harus 2: %v", blok)
	}
	_, kr := e.login("krani1", "krani123", devKrani)
	mk := e.json("GET", "/api/master", kr.AccessToken, nil, 200)
	if pk, _ := mk["pemanen"].([]any); len(pk) != 5 {
		t.Fatalf("krani menerima seluruh pemanen unit: %d", len(pk))
	}
	// Versi master berbeda antar akun, dan versi yang sama menghasilkan berubah=false.
	if m["versi"] == mk["versi"] {
		t.Fatal("versi master mandor dan krani tidak boleh sama (isinya berbeda)")
	}
	if again := e.json("GET", "/api/master?versi="+m["versi"].(string), md.AccessToken, nil, 200); again["berubah"] != false {
		t.Fatalf("versi sama harus berubah=false: %v", again)
	}

	// Mandor mengirim tanpa disetujui_*: langsung 201, mandor menjadi pencatat dan penyetuju, mandor/afdeling dari akun.
	id := "ffffffff-ffff-4fff-8fff-000000000001"
	p := panenContoh(id)
	p.DisetujuiUsername, p.DisetujuiPada = "", ""
	if kode, b := e.kirim("POST", "/api/panen", md.AccessToken, p, nil); kode != 201 {
		t.Fatalf("mandor kirim: %d %s", kode, b)
	}
	var mandor int
	var afd, pencatat, penyetuju string
	var afdOid int
	if err := e.db.QueryRow(`SELECT f.mandor_hrmb_code, f.afdeling_kode, f.afdeling_erp_oid, pc.username, ps.username
FROM dbo.v_fai_panen_siap_impor f JOIN dbo.fai_panen x ON x.id=f.panen_id
JOIN dbo.app_user pc ON pc.user_id=x.dibuat_oleh JOIN dbo.app_user ps ON ps.user_id=x.disetujui_oleh WHERE f.panen_id=@p1`, id).
		Scan(&mandor, &afd, &afdOid, &pencatat, &penyetuju); err != nil {
		t.Fatal(err)
	}
	if mandor != 7001 || afd != "DIV2" || afdOid != 7 || pencatat != "mandor1" || penyetuju != "mandor1" {
		t.Fatalf("atribusi: %d %s %d %s %s", mandor, afd, afdOid, pencatat, penyetuju)
	}
	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.v_fai_panen_siap_impor WHERE panen_id=@p1 AND pemanen_hrmb_code=90001 AND waktu_panen IS NOT NULL AND unit='KP_STL'`, id) != 1 {
		t.Fatal("view harus memuat pemanen_hrmb_code, waktu_panen, dan unit")
	}
	// Pengirim tanpa hak setuju tidak lagi ditolak: catatannya MENUNGGU persetujuan (lihat persetujuan_krani_test.go).
	k := panenContoh("ffffffff-ffff-4fff-8fff-000000000002")
	k.DisetujuiUsername, k.DisetujuiPada = "", ""
	k.TphKode = "03"
	if kode, b := e.kirim("POST", "/api/panen", kr.AccessToken, k, nil); kode != 201 {
		t.Fatalf("pengirim tanpa hak setuju harus 201 (menunggu): %d %s", kode, b)
	}
	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.fai_panen WHERE id='ffffffff-ffff-4fff-8fff-000000000002' AND status_erp='MENUNGGU' AND disetujui_oleh IS NULL`) != 1 {
		t.Fatal("catatan krani harus MENUNGGU tanpa penyetuju")
	}

	// Kirim ulang panen_id yang sama: 200 SUDAH_ADA. Kombinasi sama dengan panen_id berbeda: 409.
	if kode, b := e.kirim("POST", "/api/panen", md.AccessToken, p, nil); kode != 200 {
		t.Fatalf("kirim ulang harus 200: %d %s", kode, b)
	}
	dobel := panenContoh("ffffffff-ffff-4fff-8fff-000000000003")
	dobel.DisetujuiUsername, dobel.DisetujuiPada = "", ""
	if kode, b := e.kirim("POST", "/api/panen", md.AccessToken, dobel, nil); kode != 409 {
		t.Fatalf("kombinasi ganda harus 409: %d %s", kode, b)
	}
	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.fai_panen WHERE id='ffffffff-ffff-4fff-8fff-000000000003'`) != 0 {
		t.Fatal("kombinasi ganda tidak boleh tersimpan")
	}

	// Pemanen milik mandor lain: 422. TPH lain dengan pemanen sendiri: 201.
	lain := panenContoh("ffffffff-ffff-4fff-8fff-000000000004")
	lain.DisetujuiUsername, lain.DisetujuiPada = "", ""
	lain.TphKode = "02"
	lain.Pemanen[0].HrmbCode = 90002
	if kode, b := e.kirim("POST", "/api/panen", md.AccessToken, lain, nil); kode != 422 {
		t.Fatalf("pemanen mandor lain harus 422: %d %s", kode, b)
	}
	lain.Pemanen[0].HrmbCode = 90001
	if kode, b := e.kirim("POST", "/api/panen", md.AccessToken, lain, nil); kode != 201 {
		t.Fatalf("TPH lain, pemanen sendiri harus 201: %d %s", kode, b)
	}

	// ERP menolak catatan pertama; percobaan ulang biasa tidak menghidupkannya, kirim_ulang=true menggantinya.
	if _, err := panggil(t, e.db, `EXEC dbo.sp_fai_impor_gagal @panen_id=@p1, @pesan=N'Blok tidak ditemukan di ERP', @diimpor_oleh=NULL`, id); err != nil {
		t.Fatal(err)
	}
	if kode, b := e.kirim("POST", "/api/panen", md.AccessToken, p, nil); kode != 200 {
		t.Fatalf("percobaan ulang biasa harus 200 SUDAH_ADA: %d %s", kode, b)
	}
	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.fai_panen WHERE id=@p1 AND status_erp='DITOLAK'`, id) != 1 {
		t.Fatal("tanpa kirim_ulang catatan harus tetap DITOLAK")
	}
	p.KirimUlang = true
	p.Grading[0].JmlFinal, p.Grading[1].JmlFinal = 5, 1
	p.Pemanen[0].JjgAlokasi = 6
	if kode, b := e.kirim("POST", "/api/panen", md.AccessToken, p, nil); kode != 201 {
		t.Fatalf("kirim ulang catatan DITOLAK harus 201: %d %s", kode, b)
	}
	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.v_fai_panen_siap_impor WHERE panen_id=@p1 AND status_erp='BARU' AND jjg_total=6 AND JJG_RIPE=5`, id) != 1 {
		t.Fatal("catatan harus BARU lagi dengan isi baru")
	}
	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.fai_panen_grading WHERE panen_id=@p1 AND dihapus_pada IS NULL`, id) != 2 ||
		hitung(t, e.db, `SELECT COUNT(*) FROM dbo.fai_impor_log WHERE panen_id=@p1 AND hasil='GAGAL'`, id) != 1 ||
		hitung(t, e.db, `SELECT COUNT(*) FROM dbo.audit_log WHERE baris_id=@p1 AND aksi='TERIMA_ULANG'`, id) != 1 {
		t.Fatal("grading lama harus terhapus lunak, log penolakan tetap, audit TERIMA_ULANG tercatat")
	}
	// Catatan yang sudah disetujui (BARU) terkunci: kirim_ulang=true dijawab 423, tanpa kirim_ulang tetap 200 SUDAH_ADA.
	if kode, b := e.kirim("POST", "/api/panen", md.AccessToken, p, nil); kode != 423 {
		t.Fatalf("catatan BARU tidak boleh ditimpa: %d %s", kode, b)
	}
	p.KirimUlang = false
	if kode, b := e.kirim("POST", "/api/panen", md.AccessToken, p, nil); kode != 200 {
		t.Fatalf("percobaan ulang biasa tetap 200: %d %s", kode, b)
	}
}
