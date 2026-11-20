package integration

import (
	"encoding/json"
	"testing"

	"fai26-api/internal/model"
)

// GET /api/admin/produktivitas: ranking produktivitas pemanen satu hari (hanya membaca, tanpa premi).
func TestProduktivitasPemanen(t *testing.T) {
	e := siapkanUji(t)
	_, kr := e.login("krani1", "krani123", devKrani)
	_, ad := e.login("admin1", "admin12345", devAdmin)

	ambil := func(token, query string) (int, model.ProduktivitasHarian, string) {
		kode, b := e.kirim("GET", "/api/admin/produktivitas"+query, token, nil, nil)
		var r model.ProduktivitasHarian
		if kode == 200 {
			if err := json.Unmarshal(b, &r); err != nil {
				t.Fatalf("badan tidak terbaca: %v %s", err, b)
			}
		}
		return kode, r, string(b)
	}

	// Hak dan format: tanpa token 401, akun tanpa laporan.lihat 403, tanggal salah 422.
	if kode, _ := e.kirim("GET", "/api/admin/produktivitas", "", nil, nil); kode != 401 {
		t.Fatalf("tanpa token harus 401, dapat %d", kode)
	}
	if kode, _, _ := ambil(kr.AccessToken, "?tanggal=2026-11-10"); kode != 403 {
		t.Fatalf("krani tanpa laporan.lihat harus 403, dapat %d", kode)
	}
	if kode, _, _ := ambil(ad.AccessToken, "?tanggal=10-11-2026"); kode != 422 {
		t.Fatalf("tanggal salah format harus 422, dapat %d", kode)
	}

	// Tanpa catatan: daftar [] (bukan null) dan angka nol.
	kode, kosong, mentah := ambil(ad.AccessToken, "?tanggal=2026-11-10")
	if kode != 200 || kosong.JumlahPemanen != 0 || kosong.TotalJanjang != 0 || !contains2(mentah, `"pemanen":[]`) {
		t.Fatalf("kosong: %d %s", kode, mentah)
	}

	// Pemanen 90001: dua catatan (masing-masing 9 matang + 1 mentah, brondolan 12,5 kg).
	// Pemanen 90002: satu catatan 15 janjang (6 matang + 9 mentah, brondolan 4 kg).
	kirim := func(id, tph string, pemanen int64, ripe, unripe int, brondolan float64) {
		p := panenContoh(id)
		p.TphKode = tph
		p.BrondolanKg = brondolan
		p.Grading = []model.GradingInput{
			{KelasKode: "ripe", ErpKolom: "JJG_RIPE", JmlAI: ripe, JmlFinal: ripe},
			{KelasKode: "unripe", JmlAI: unripe, JmlFinal: unripe},
		}
		p.Pemanen = []model.PemanenInput{{HrmbCode: int(pemanen), JjgAlokasi: ripe + unripe}}
		if kode, b := e.kirim("POST", "/api/panen", kr.AccessToken, p, nil); kode != 201 {
			t.Fatalf("kirim panen %s: %d %s", id, kode, b)
		}
	}
	kirim("eeeeeeee-eeee-4eee-8eee-000000000001", "01", 90001, 9, 1, 12.5)
	kirim("eeeeeeee-eeee-4eee-8eee-000000000002", "02", 90001, 9, 1, 12.5)
	kirim("eeeeeeee-eeee-4eee-8eee-000000000003", "03", 90002, 6, 9, 4)

	// Catatan hari lain tidak ikut terhitung.
	kemarin := panenContoh("eeeeeeee-eeee-4eee-8eee-000000000004")
	kemarin.TanggalPanen = "2026-11-09"
	if kode, b := e.kirim("POST", "/api/panen", kr.AccessToken, kemarin, nil); kode != 201 {
		t.Fatalf("kirim panen kemarin: %d %s", kode, b)
	}

	kode, r, mentah := ambil(ad.AccessToken, "?tanggal=2026-11-10")
	if kode != 200 {
		t.Fatalf("produktivitas harus 200, dapat %d %s", kode, mentah)
	}
	if r.Tanggal != "2026-11-10" || r.JumlahPemanen != 2 || r.TotalJanjang != 35 || r.RataJanjang != 17.5 || len(r.Pemanen) != 2 {
		t.Fatalf("ringkasan: %+v", r)
	}
	a, b := r.Pemanen[0], r.Pemanen[1]
	if a.Peringkat != 1 || a.HrmbCode != 90001 || a.Nama != "Pemanen Contoh 1" || a.Catatan != 2 || a.Janjang != 20 ||
		a.JanjangMatang != 18 || a.PersenMatang != 90 || a.BrondolanKg != 25 || a.Menunggu != 0 {
		t.Fatalf("peringkat 1: %+v", a)
	}
	if b.Peringkat != 2 || b.HrmbCode != 90002 || b.Catatan != 1 || b.Janjang != 15 || b.PersenMatang != 40 || b.BrondolanKg != 4 {
		t.Fatalf("peringkat 2: %+v", b)
	}

	// Catatan yang ditolak ERP tidak dihitung; catatan menunggu persetujuan krani dihitung dan ditandai.
	if _, err := e.db.Exec(`UPDATE dbo.fai_panen SET status_erp='DITOLAK', diubah_pada=SYSUTCDATETIME() WHERE id='eeeeeeee-eeee-4eee-8eee-000000000002'`); err != nil {
		t.Fatal(err)
	}
	if _, err := e.db.Exec(`UPDATE dbo.fai_panen SET status_erp='MENUNGGU', diubah_pada=SYSUTCDATETIME() WHERE id='eeeeeeee-eeee-4eee-8eee-000000000003'`); err != nil {
		t.Fatal(err)
	}
	_, r2, _ := ambil(ad.AccessToken, "?tanggal=2026-11-10")
	if r2.JumlahPemanen != 2 || r2.TotalJanjang != 25 {
		t.Fatalf("setelah penolakan: %+v", r2)
	}
	// Pemanen 90002 (15 janjang, menunggu persetujuan) kini di atas 90001 (10 janjang, satu catatan tersisa).
	if r2.Pemanen[0].HrmbCode != 90002 || r2.Pemanen[0].Peringkat != 1 || r2.Pemanen[0].Janjang != 15 || r2.Pemanen[0].Menunggu != 1 ||
		r2.Pemanen[1].HrmbCode != 90001 || r2.Pemanen[1].Peringkat != 2 || r2.Pemanen[1].Janjang != 10 || r2.Pemanen[1].Catatan != 1 {
		t.Fatalf("setelah penolakan dan menunggu: %+v", r2.Pemanen)
	}
}
