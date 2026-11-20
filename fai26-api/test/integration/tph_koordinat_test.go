package integration

import (
	"fmt"
	"testing"
)

// HP merekam koordinat TPH: hanya bila belum ada, idempoten, mengubah versi master, tercatat di audit log.
func TestTphRekamKoordinat(t *testing.T) {
	e := siapkanUji(t)
	e.bersihkanMasterUji()
	t.Cleanup(e.bersihkanMasterUji)
	_, ad := e.login("admin1", "admin12345", devAdmin)
	_, kr := e.login("krani1", "krani123", devKrani)

	blok := idDari(e.json("POST", "/api/admin/blok", ad.AccessToken, berkas{"kode": "T92", "erp_oid": 9992}, 201))
	tph := idDari(e.json("POST", "/api/admin/tph", ad.AccessToken, berkas{"blok_id": blok, "no_tph": "01"}, 201))
	url := fmt.Sprintf("/api/tph/%d/koordinat", tph)
	titik := berkas{"lat": 3.123456, "lon": 101.654321, "akurasi_m": 8.5}

	// Tanpa token ditolak.
	if kode, _ := e.kirim("PUT", url, "", titik, nil); kode != 401 {
		t.Fatalf("tanpa token harus 401, dapat %d", kode)
	}

	versi0, _ := e.json("GET", "/api/master", kr.AccessToken, nil, 200)["versi"].(string)

	// Pertama kali: tersimpan.
	h := e.json("PUT", url, kr.AccessToken, titik, 200)
	if h["diterapkan"] != true || h["lat"] != 3.123456 || h["lon"] != 101.654321 {
		t.Fatalf("pertama: %v", h)
	}

	// Terlihat di web admin dan membuat versi master berubah, sehingga HP lain mengunduhnya.
	row := cari(e.daftar(fmt.Sprintf("/api/admin/tph?blok_id=%d", blok), ad.AccessToken), "tph_id", float64(tph))
	if row["lat"] != 3.123456 || row["lon"] != 101.654321 {
		t.Fatalf("admin tidak melihat koordinat: %v", row)
	}
	m := e.json("GET", "/api/master?versi="+versi0, kr.AccessToken, nil, 200)
	if m["berubah"] != true || m["versi"] == versi0 {
		t.Fatalf("versi master harus berubah setelah koordinat direkam: %v", m["versi"])
	}

	// Titik yang sama dikirim lagi (HP mengulang): aman, tidak diterapkan, titik tetap.
	h = e.json("PUT", url, kr.AccessToken, titik, 200)
	if h["diterapkan"] != false || h["lat"] != 3.123456 {
		t.Fatalf("ulang: %v", h)
	}

	// Titik lain tidak menimpa; HP diberi titik server.
	h = e.json("PUT", url, kr.AccessToken, berkas{"lat": -1.5, "lon": 100.5, "akurasi_m": 3}, 200)
	if h["diterapkan"] != false || h["lat"] != 3.123456 || h["lon"] != 101.654321 {
		t.Fatalf("tidak boleh menimpa: %v", h)
	}
	row = cari(e.daftar(fmt.Sprintf("/api/admin/tph?blok_id=%d", blok), ad.AccessToken), "tph_id", float64(tph))
	if row["lat"] != 3.123456 {
		t.Fatalf("koordinat tertimpa: %v", row)
	}

	// Tercatat tepat satu kali di audit log, atas nama krani.
	var n int
	if err := e.db.QueryRow(`SELECT COUNT(*) FROM dbo.audit_log WHERE tabel='tph' AND baris_id=@p1 AND aksi='UBAH' AND keterangan LIKE 'koordinat direkam dari HP%' AND user_id=(SELECT user_id FROM dbo.app_user WHERE username='krani1')`,
		fmt.Sprint(tph)).Scan(&n); err != nil {
		t.Fatal(err)
	}
	if n != 1 {
		t.Fatalf("audit koordinat harus 1 baris, dapat %d", n)
	}

	// Validasi.
	for nama, badan := range map[string]berkas{
		"lat di luar":    {"lat": 91.0, "lon": 101.0, "akurasi_m": 5},
		"lon di luar":    {"lat": 3.0, "lon": 181.0, "akurasi_m": 5},
		"titik nol":      {"lat": 0.0, "lon": 0.0, "akurasi_m": 5},
		"akurasi buruk":  {"lat": 3.0, "lon": 101.0, "akurasi_m": 80},
		"akurasi kosong": {"lat": 3.0, "lon": 101.0},
	} {
		if kode, b := e.kirim("PUT", url, kr.AccessToken, badan, nil); kode != 422 {
			t.Errorf("%s: harus 422, dapat %d %s", nama, kode, b)
		}
	}

	// TPH yang tidak ada, atau sudah dihapus lunak: 404.
	if kode, _ := e.kirim("PUT", "/api/tph/99999999/koordinat", kr.AccessToken, titik, nil); kode != 404 {
		t.Fatalf("TPH tidak ada harus 404, dapat %d", kode)
	}
	tph2 := idDari(e.json("POST", "/api/admin/tph", ad.AccessToken, berkas{"blok_id": blok, "no_tph": "02"}, 201))
	if kode, _ := e.kirim("DELETE", fmt.Sprintf("/api/admin/tph/%d", tph2), ad.AccessToken, nil, nil); kode != 200 {
		t.Fatalf("hapus tph2: %d", kode)
	}
	if kode, _ := e.kirim("PUT", fmt.Sprintf("/api/tph/%d/koordinat", tph2), kr.AccessToken, titik, nil); kode != 404 {
		t.Fatalf("TPH terhapus harus 404, dapat %d", kode)
	}
}
