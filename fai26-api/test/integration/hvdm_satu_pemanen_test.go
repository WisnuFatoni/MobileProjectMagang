package integration

import (
	"database/sql"
	"fai26-api/internal/model"
	"testing"
)

// Satu TPH = satu pemanen (keputusan 18 Nov), dan header HVDM butuh afdeling dan mandor: server menyalinnya dari master
// saat catatan diterima, dan view HVDM menampilkannya.
func TestHvdmSatuPemananDanMandorAfdeling(t *testing.T) {
	e := siapkanUji(t)
	if hitung(t, e.db, `SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID('dbo.fai_panen') AND name='mandor_hrmb_code'`) == 0 {
		t.Skip("migrasi 005 belum dijalankan di database uji")
	}
	_, ad := e.login("admin1", "admin12345", devAdmin)
	_, kr := e.login("krani1", "krani123", devKrani)
	t.Cleanup(func() {
		_, _ = e.db.Exec(`UPDATE dbo.pemanen SET mandor_hrmb_code=NULL WHERE pemanen_id=1`)
		_, _ = e.db.Exec(`UPDATE dbo.divisi SET erp_oid=NULL WHERE divisi_id=1`)
	})

	// Admin mengisi master: kode ERP afdeling (divisi) dan kode HRMB mandor pada pemanen.
	e.json("PUT", "/api/admin/divisi/1", ad.AccessToken, berkas{"kode": "DIV2", "nama": "Divisi 2 (contoh)", "erp_oid": 7}, 200)
	e.json("PUT", "/api/admin/pemanen/1", ad.AccessToken, berkas{"erp_hrmb_code": 90001, "mandor_hrmb_code": 7001, "kode": "P-001", "nama": "Pemanen Contoh 1"}, 200)
	if p := cari(e.daftar("/api/admin/pemanen", ad.AccessToken), "erp_hrmb_code", float64(90001)); p == nil || p["mandor_hrmb_code"] != float64(7001) {
		t.Fatalf("daftar pemanen harus memuat mandor_hrmb_code: %v", p)
	}

	// Catatan dengan blok A12 (asm3_oid 1012) dan pemanen 90001 (seluruh 10 janjang).
	id := "eeeeeeee-eeee-4eee-8eee-000000000001"
	p := panenContoh(id)
	p.Asm3Oid = 1012
	p.Pemanen[0].HrmbCode = 90001
	if kode, b := e.kirim("POST", "/api/panen", kr.AccessToken, p, nil); kode != 201 {
		t.Fatalf("kirim: %d %s", kode, b)
	}
	var afd sql.NullString
	var afdOid, mandor sql.NullInt64
	if err := e.db.QueryRow(`SELECT afdeling_kode, afdeling_erp_oid, mandor_hrmb_code FROM dbo.fai_panen WHERE id=@p1`, id).Scan(&afd, &afdOid, &mandor); err != nil {
		t.Fatal(err)
	}
	if afd.String != "DIV2" || afdOid.Int64 != 7 || mandor.Int64 != 7001 {
		t.Fatalf("salinan afdeling/mandor: %+v %+v %+v", afd, afdOid, mandor)
	}
	// View untuk HVDM membawa kolom itu dan pemanen tunggal.
	var vAfd string
	var vMandor, vPemanen, vJjg, vTotal int
	if err := e.db.QueryRow(`SELECT afdeling_kode, mandor_hrmb_code, pemanen_hrmb_code, pemanen_jjg, jjg_total FROM dbo.v_fai_panen_siap_impor WHERE panen_id=@p1`, id).
		Scan(&vAfd, &vMandor, &vPemanen, &vJjg, &vTotal); err != nil {
		t.Fatal(err)
	}
	if vAfd != "DIV2" || vMandor != 7001 || vPemanen != 90001 || vJjg != 10 || vTotal != 10 {
		t.Fatalf("view: %s %d %d %d %d", vAfd, vMandor, vPemanen, vJjg, vTotal)
	}

	// Master berubah belakangan: catatan lama tidak ikut berubah (salinan saat diterima).
	e.json("PUT", "/api/admin/pemanen/1", ad.AccessToken, berkas{"erp_hrmb_code": 90001, "mandor_hrmb_code": 7002, "kode": "P-001", "nama": "Pemanen Contoh 1"}, 200)
	if hitung(t, e.db, `SELECT mandor_hrmb_code FROM dbo.fai_panen WHERE id=@p1`, id) != 7001 {
		t.Fatal("catatan lama tidak boleh berubah saat master pemanen diubah")
	}

	// Master tidak lengkap sekarang ditolak (422) dan tidak tersimpan: blok, TPH, dan pemanen harus ada di master.
	for nama, ubah := range map[string]func(*model.PanenInput){
		"31": func(q *model.PanenInput) { q.Asm3Oid = 999 },
		"32": func(q *model.PanenInput) { q.TphKode = "99" },
		"33": func(q *model.PanenInput) { q.Pemanen[0].HrmbCode = 123456 },
	} {
		q := panenContoh("eeeeeeee-eeee-4eee-8eee-0000000000" + nama)
		ubah(&q)
		q.Pemanen[0].JjgAlokasi = 10
		if kode, b := e.kirim("POST", "/api/panen", kr.AccessToken, q, nil); kode != 422 {
			t.Errorf("master tidak lengkap %s: harus 422, dapat %d %s", nama, kode, b)
		}
	}

	// Aturan satu pemanen ditegakkan server.
	tolak := func(nama string, ubah func(*model.PanenInput)) {
		q := panenContoh("eeeeeeee-eeee-4eee-8eee-0000000000" + nama)
		ubah(&q)
		if kode, b := e.kirim("POST", "/api/panen", kr.AccessToken, q, nil); kode != 422 {
			t.Errorf("%s: harus 422, dapat %d %s", nama, kode, b)
		}
	}
	tolak("21", func(q *model.PanenInput) { q.Pemanen = nil })
	tolak("22", func(q *model.PanenInput) { q.Pemanen[0].JjgAlokasi = 4 })
	tolak("23", func(q *model.PanenInput) { q.Pemanen = append(q.Pemanen, q.Pemanen[0]) })
	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.fai_panen WHERE id LIKE 'eeeeeeee-eeee-4eee-8eee-0000000000[2-3][1-3]'`) != 0 {
		t.Fatal("catatan yang ditolak tidak boleh tersimpan")
	}
}
