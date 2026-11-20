package integration

import (
	"encoding/json"
	"testing"
)

type ringkasanJawab struct {
	Tanggal        string `json:"tanggal"`
	Lingkup        string `json:"lingkup"`
	CatatanHariIni int64  `json:"catatan_hari_ini"`
	JanjangHariIni int64  `json:"janjang_hari_ini"`
	PerKelas       []struct {
		KelasKode string `json:"kelas_kode"`
		Janjang   int64  `json:"janjang"`
	} `json:"per_kelas"`
	PerBlok []struct {
		BlokKode string `json:"blok_kode"`
		Tph      int64  `json:"tph"`
		Catatan  int64  `json:"catatan"`
		Janjang  int64  `json:"janjang"`
	} `json:"per_blok"`
	Tren7Hari []struct {
		Tanggal string `json:"tanggal"`
		Catatan int64  `json:"catatan"`
		Janjang int64  `json:"janjang"`
	} `json:"tren_7_hari"`
	CatatanPerStatus map[string]int64 `json:"catatan_per_status"`
}

// GET /api/panen/ringkasan: data dashboard Beranda HP, dihitung dari fai_panen di server.
func TestRingkasanPanenDashboard(t *testing.T) {
	e := siapkanUji(t)
	_, kr := e.login("krani1", "krani123", devKrani)
	_, md := e.login("mandor1", "mandor123", devLain)

	ambil := func(token, query string) (int, ringkasanJawab) {
		kode, b := e.kirim("GET", "/api/panen/ringkasan"+query, token, nil, nil)
		var r ringkasanJawab
		if kode == 200 {
			if err := json.Unmarshal(b, &r); err != nil {
				t.Fatalf("badan tidak terbaca: %v %s", err, b)
			}
		} else {
			t.Logf("GET ringkasan%s -> %d %s", query, kode, b)
		}
		return kode, r
	}

	// Tanpa token ditolak; tanggal salah ditolak.
	if kode, _ := e.kirim("GET", "/api/panen/ringkasan", "", nil, nil); kode != 401 {
		t.Fatalf("tanpa token harus 401, dapat %d", kode)
	}
	if kode, _ := ambil(kr.AccessToken, "?tanggal=10-11-2026"); kode != 400 {
		t.Fatalf("tanggal salah format harus 400, dapat %d", kode)
	}

	// Belum ada data: semua nol, daftar kosong (bukan null), status lengkap.
	kode, kosong := ambil(kr.AccessToken, "?tanggal=2026-11-10")
	if kode != 200 {
		t.Fatalf("ringkasan kosong harus 200, dapat %d", kode)
	}
	if kosong.CatatanHariIni != 0 || kosong.JanjangHariIni != 0 || kosong.Lingkup != "SENDIRI" {
		t.Fatalf("ringkasan kosong: %+v", kosong)
	}
	_, mentah := e.kirim("GET", "/api/panen/ringkasan?tanggal=2026-11-10", kr.AccessToken, nil, nil)
	for _, kunci := range []string{`"per_kelas":[]`, `"per_blok":[]`, `"tren_7_hari":[]`} {
		if !contains2(string(mentah), kunci) {
			t.Fatalf("daftar kosong harus [] (bukan null): %s tidak ada di %s", kunci, mentah)
		}
	}

	// Krani mengirim dua catatan di tanggal itu (10 janjang tiap catatan) dan satu catatan di hari sebelumnya.
	for i, id := range []string{"dddddddd-dddd-4ddd-8ddd-000000000001", "dddddddd-dddd-4ddd-8ddd-000000000002"} {
		p := panenContoh(id)
		p.TphKode = "0" + string(rune('1'+i))
		if kode, b := e.kirim("POST", "/api/panen", kr.AccessToken, p, nil); kode != 201 {
			t.Fatalf("kirim panen %s: %d %s", id, kode, b)
		}
	}
	kemarin := panenContoh("dddddddd-dddd-4ddd-8ddd-000000000003")
	kemarin.TanggalPanen = "2026-11-09"
	if kode, b := e.kirim("POST", "/api/panen", kr.AccessToken, kemarin, nil); kode != 201 {
		t.Fatalf("kirim panen kemarin: %d %s", kode, b)
	}

	kode, r := ambil(kr.AccessToken, "?tanggal=2026-11-10")
	if kode != 200 {
		t.Fatalf("ringkasan harus 200, dapat %d", kode)
	}
	if r.Tanggal != "2026-11-10" || r.Lingkup != "SENDIRI" || r.CatatanHariIni != 2 || r.JanjangHariIni != 20 {
		t.Fatalf("angka utama: %+v", r)
	}
	kelas := map[string]int64{}
	for _, k := range r.PerKelas {
		kelas[k.KelasKode] = k.Janjang
	}
	if kelas["ripe"] != 18 || kelas["unripe"] != 2 || len(kelas) != 2 {
		t.Fatalf("per kelas: %v", kelas)
	}
	if len(r.PerBlok) != 1 || r.PerBlok[0].BlokKode != "A12" || r.PerBlok[0].Catatan != 2 || r.PerBlok[0].Janjang != 20 || r.PerBlok[0].Tph != 2 {
		t.Fatalf("per blok: %+v", r.PerBlok)
	}
	if len(r.Tren7Hari) != 2 || r.Tren7Hari[0].Tanggal != "2026-11-09" || r.Tren7Hari[0].Janjang != 10 ||
		r.Tren7Hari[1].Tanggal != "2026-11-10" || r.Tren7Hari[1].Janjang != 20 {
		t.Fatalf("tren 7 hari (urut tanggal naik): %+v", r.Tren7Hari)
	}
	if r.CatatanPerStatus["TERKIRIM"] != 3 || r.CatatanPerStatus["DITERIMA"] != 0 || r.CatatanPerStatus["DITOLAK"] != 0 {
		t.Fatalf("status: %v", r.CatatanPerStatus)
	}

	// Status ikut berubah bila ERP menolak/menerima (dihitung ulang dari fai_panen).
	if _, err := e.db.Exec(`UPDATE dbo.fai_panen SET status_erp='DITOLAK', diubah_pada=SYSUTCDATETIME() WHERE id='dddddddd-dddd-4ddd-8ddd-000000000001'`); err != nil {
		t.Fatal(err)
	}
	if _, r2 := ambil(kr.AccessToken, "?tanggal=2026-11-10"); r2.CatatanPerStatus["DITOLAK"] != 1 || r2.CatatanPerStatus["TERKIRIM"] != 2 {
		t.Fatalf("status setelah ditolak ERP: %v", r2.CatatanPerStatus)
	}

	// Mandor (panen.koreksi) melihat seluruh unit: catatan krani ikut terhitung, lingkupnya UNIT.
	if _, rm := ambil(md.AccessToken, "?tanggal=2026-11-10"); rm.Lingkup != "UNIT" || rm.CatatanHariIni != 2 || rm.JanjangHariIni != 20 {
		t.Fatalf("mandor harus melihat catatan se-unit: %+v", rm)
	}

	// Tanggal tanpa data: nol. Tanpa parameter: hari ini menurut WIB (data uji berstempel 2026, jadi kosong atau tidak
	// mengandung catatan uji).
	if _, rk := ambil(kr.AccessToken, "?tanggal=2026-01-01"); rk.CatatanHariIni != 0 || len(rk.PerBlok) != 0 {
		t.Fatalf("tanggal tanpa data: %+v", rk)
	}
	if kode, _ := ambil(kr.AccessToken, ""); kode != 200 {
		t.Fatalf("tanpa parameter harus 200, dapat %d", kode)
	}
}

func contains2(s, sub string) bool {
	for i := 0; i+len(sub) <= len(s); i++ {
		if s[i:i+len(sub)] == sub {
			return true
		}
	}
	return false
}
