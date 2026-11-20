package integration

import (
	"database/sql"
	"encoding/json"
	"testing"
	"time"
)

// Jembatan ERP (migrations/003_jembatan_erp.sql): view untuk membaca catatan siap impor dan prosedur untuk
// memindahkan status. Alur nyata: HP mengirim lewat API, ERP memanggil prosedur, status kembali ke HP lewat API.

func panggil(t *testing.T, db *sql.DB, q string, args ...any) (berubah int, err error) {
	t.Helper()
	err = db.QueryRow(q, args...).Scan(&berubah)
	return
}

func statusHP(t *testing.T, e *lingkunganUji, token string) (status, pesan string) {
	t.Helper()
	kode, b := e.kirim("GET", "/api/panen?sejak=2020-01-01T00:00:00Z", token, nil, nil)
	if kode != 200 {
		t.Fatalf("GET /api/panen: %d %s", kode, b)
	}
	var daftar []struct {
		Status string  `json:"status"`
		Pesan  *string `json:"pesan"`
	}
	if err := json.Unmarshal(b, &daftar); err != nil || len(daftar) != 1 {
		t.Fatalf("daftar status: %v %s", err, b)
	}
	if daftar[0].Pesan != nil {
		pesan = *daftar[0].Pesan
	}
	return daftar[0].Status, pesan
}

func TestJembatanErpAlurPenuh(t *testing.T) {
	e := siapkanUji(t)
	if hitung(t, e.db, `SELECT COUNT(*) FROM sys.views WHERE name='v_fai_panen_siap_impor'`) == 0 {
		t.Skip("migrasi 003 belum dijalankan di database uji")
	}
	_, kr := e.login("krani1", "krani123", devKrani)
	const id = "cccccccc-cccc-4ccc-8ccc-000000000001"
	if kode, b := e.kirim("POST", "/api/panen", kr.AccessToken, panenContoh(id), nil); kode != 201 {
		t.Fatalf("kirim panen: %d %s", kode, b)
	}

	// 1. View: satu baris dengan jumlah per kolom ERP dan per kelas.
	var (
		unitKode, status, pencatat, penyetuju string
		ripe, tanpaKolom, total, unripe       int
		pemanen                               int
		asm3                                  int64
	)
	err := e.db.QueryRow(`SELECT unit_kode, status_erp, pencatat_username, disetujui_username, JJG_RIPE, jjg_tanpa_kolom,
jjg_total, jml_unripe, jumlah_pemanen, asm3_oid FROM dbo.v_fai_panen_siap_impor WHERE panen_id=@p1`, id).
		Scan(&unitKode, &status, &pencatat, &penyetuju, &ripe, &tanpaKolom, &total, &unripe, &pemanen, &asm3)
	if err != nil {
		t.Fatal("view:", err)
	}
	if unitKode != "KP_STL" || status != "BARU" || pencatat != "krani1" || penyetuju != "mandor1" {
		t.Fatalf("kolom identitas: %s %s %s %s", unitKode, status, pencatat, penyetuju)
	}
	if ripe != 9 || tanpaKolom != 1 || total != 10 || unripe != 1 || pemanen != 1 || asm3 != 1012 {
		t.Fatalf("jumlah: ripe=%d tanpa_kolom=%d total=%d unripe=%d pemanen=%d asm3=%d", ripe, tanpaKolom, total, unripe, pemanen, asm3)
	}
	var hrmb, alokasi int
	if err := e.db.QueryRow(`SELECT hrmb_code, jjg_alokasi FROM dbo.v_fai_panen_pemanen_siap_impor WHERE panen_id=@p1`, id).Scan(&hrmb, &alokasi); err != nil || hrmb != 90001 || alokasi != 10 {
		t.Fatalf("view pemanen: %v %d %d", err, hrmb, alokasi)
	}

	// 2. Tandai dibaca: HP masih melihat TERKIRIM; panggilan kedua tidak mengubah apa-apa.
	if n, err := panggil(t, e.db, `EXEC dbo.sp_fai_tandai_dibaca @panen_id=@p1`, id); err != nil || n != 1 {
		t.Fatalf("tandai dibaca: %d %v", n, err)
	}
	if n, _ := panggil(t, e.db, `EXEC dbo.sp_fai_tandai_dibaca @panen_id=@p1`, id); n != 0 {
		t.Fatalf("tandai dibaca ulang harus 0, dapat %d", n)
	}
	if s, _ := statusHP(t, e, kr.AccessToken); s != "TERKIRIM" {
		t.Fatalf("DIBACA terlihat di HP sebagai %s, seharusnya TERKIRIM", s)
	}
	time.Sleep(20 * time.Millisecond)

	// 3. Gagal: status DITOLAK dan alasannya sampai ke HP; pesan kosong ditolak.
	if _, err := panggil(t, e.db, `EXEC dbo.sp_fai_impor_gagal @panen_id=@p1, @pesan=N'', @diimpor_oleh=NULL`, id); err == nil {
		t.Fatal("pesan kosong harus ditolak")
	}
	if n, err := panggil(t, e.db, `EXEC dbo.sp_fai_impor_gagal @panen_id=@p1, @pesan=N'Blok tidak ditemukan di ERP', @diimpor_oleh=NULL`, id); err != nil || n != 1 {
		t.Fatalf("impor gagal: %d %v", n, err)
	}
	if s, p := statusHP(t, e, kr.AccessToken); s != "DITOLAK" || p != "Blok tidak ditemukan di ERP" {
		t.Fatalf("HP melihat %q / %q", s, p)
	}
	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.v_fai_panen_siap_impor WHERE panen_id=@p1`, id) != 0 {
		t.Fatal("catatan DITOLAK tidak boleh muncul lagi di view siap impor")
	}
	// Catatan yang sudah DITOLAK tidak bisa langsung dinyatakan berhasil.
	if _, err := panggil(t, e.db, `EXEC dbo.sp_fai_impor_ok @panen_id=@p1, @hvd1m_oid=1, @hvd1da_oid=2`, id); err == nil {
		t.Fatal("impor_ok pada catatan DITOLAK harus ditolak")
	}

	// 4. Ulang impor: kembali BARU (HP: TERKIRIM), muncul lagi di view, lalu berhasil.
	if n, _ := panggil(t, e.db, `EXEC dbo.sp_fai_ulang_impor @panen_id=@p1`, id); n != 1 {
		t.Fatalf("ulang impor: %d", n)
	}
	if n, _ := panggil(t, e.db, `EXEC dbo.sp_fai_ulang_impor @panen_id=@p1`, id); n != 0 {
		t.Fatalf("ulang impor pada BARU harus 0, dapat %d", n)
	}
	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.v_fai_panen_siap_impor WHERE panen_id=@p1`, id) != 1 {
		t.Fatal("catatan BARU harus muncul lagi di view")
	}
	if n, err := panggil(t, e.db, `EXEC dbo.sp_fai_impor_ok @panen_id=@p1, @hvd1m_oid=700001, @hvd1da_oid=800001, @diimpor_oleh=NULL`, id); err != nil || n != 1 {
		t.Fatalf("impor ok: %d %v", n, err)
	}
	if s, _ := statusHP(t, e, kr.AccessToken); s != "DITERIMA" {
		t.Fatalf("setelah impor OK HP harus melihat DITERIMA, dapat %s", s)
	}
	if _, err := panggil(t, e.db, `EXEC dbo.sp_fai_impor_ok @panen_id=@p1, @hvd1m_oid=1, @hvd1da_oid=2`, id); err == nil {
		t.Fatal("impor_ok kedua kali harus ditolak (sudah DIIMPOR)")
	}

	// 5. Jejak: dua baris log (GAGAL lalu OK) dan audit IMPOR_GAGAL, IMPOR_ULANG, IMPOR.
	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.fai_impor_log WHERE panen_id=@p1`, id) != 2 ||
		hitung(t, e.db, `SELECT COUNT(*) FROM dbo.fai_impor_log WHERE panen_id=@p1 AND hasil='OK' AND hvd1m_oid=700001 AND hvd1da_oid=800001`, id) != 1 {
		t.Fatal("fai_impor_log harus berisi satu GAGAL dan satu OK")
	}
	for _, aksi := range []string{"IMPOR_GAGAL", "IMPOR_ULANG", "IMPOR"} {
		if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.audit_log WHERE tabel='fai_panen' AND baris_id=@p1 AND aksi=@p2`, id, aksi) != 1 {
			t.Fatalf("audit %s harus tepat 1 baris", aksi)
		}
	}

	// 6. Id yang tidak ada.
	if _, err := panggil(t, e.db, `EXEC dbo.sp_fai_impor_ok @panen_id='99999999-9999-4999-8999-999999999999', @hvd1m_oid=1, @hvd1da_oid=2`); err == nil {
		t.Fatal("catatan tidak ada harus galat")
	}
}
