package integration

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"fai26-api/internal/app"
)

func kirimKe(t *testing.T, url, metode, path string, badan any, xff string) (int, http.Header) {
	t.Helper()
	b, _ := json.Marshal(badan)
	req, _ := http.NewRequest(metode, url+path, bytes.NewReader(b))
	req.Header.Set("Content-Type", "application/json")
	if xff != "" {
		req.Header.Set("X-Forwarded-For", xff)
	}
	res, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	defer res.Body.Close()
	return res.StatusCode, res.Header
}

func TestBatasLajuLoginDanUmum(t *testing.T) {
	e := siapkanUji(t)

	// Login dan refresh berbagi satu ember per alamat: 3 per menit.
	cfg := e.cfg
	cfg.BatasLoginPerMenit = 3
	cfg.BatasUmumPerMenit = 0
	srv := httptest.NewServer(app.Bangun(cfg, e.db))
	defer srv.Close()
	salah := map[string]string{"username": "krani1", "password": "salah", "device_id": devKrani}
	for i := 0; i < 3; i++ {
		if kode, _ := kirimKe(t, srv.URL, "POST", "/api/auth/login", salah, ""); kode != 401 {
			t.Fatalf("percobaan %d harus sampai ke login (401), dapat %d", i+1, kode)
		}
	}
	kode, h := kirimKe(t, srv.URL, "POST", "/api/auth/login", salah, "")
	if kode != http.StatusTooManyRequests || h.Get("Retry-After") == "" {
		t.Fatalf("percobaan ke-4 harus 429 dengan Retry-After, dapat %d %q", kode, h.Get("Retry-After"))
	}
	if kode, _ := kirimKe(t, srv.URL, "POST", "/api/auth/refresh", map[string]string{"refresh_token": "x"}, ""); kode != http.StatusTooManyRequests {
		t.Fatalf("refresh berbagi ember dengan login, harus 429, dapat %d", kode)
	}
	// Endpoint lain tidak ikut terkena batas login.
	if kode, _ := kirimKe(t, srv.URL, "GET", "/api/health", nil, ""); kode != 200 {
		t.Fatalf("health tidak dibatasi oleh batas login, dapat %d", kode)
	}

	// Batas umum 4 per menit; X-Forwarded-For dihormati hanya bila proxy dipercaya.
	cfg2 := e.cfg
	cfg2.BatasLoginPerMenit = 0
	cfg2.BatasUmumPerMenit = 4
	cfg2.PercayaiProxy = true
	srv2 := httptest.NewServer(app.Bangun(cfg2, e.db))
	defer srv2.Close()
	for i := 0; i < 4; i++ {
		if kode, _ := kirimKe(t, srv2.URL, "GET", "/api/health", nil, "203.0.113.7"); kode != 200 {
			t.Fatalf("permintaan %d harus lolos, dapat %d", i+1, kode)
		}
	}
	if kode, _ := kirimKe(t, srv2.URL, "GET", "/api/health", nil, "203.0.113.7"); kode != http.StatusTooManyRequests {
		t.Fatalf("permintaan ke-5 dari alamat yang sama harus 429, dapat %d", kode)
	}
	if kode, _ := kirimKe(t, srv2.URL, "GET", "/api/health", nil, "203.0.113.8"); kode != 200 {
		t.Fatalf("alamat klien lain tidak boleh ikut terkena, dapat %d", kode)
	}

	// Tanpa batas (nol): tidak pernah 429.
	cfg3 := e.cfg
	srv3 := httptest.NewServer(app.Bangun(cfg3, e.db))
	defer srv3.Close()
	for i := 0; i < 30; i++ {
		if kode, _ := kirimKe(t, srv3.URL, "GET", "/api/health", nil, ""); kode != 200 {
			t.Fatalf("tanpa batas harus selalu lolos, dapat %d pada ke-%d", kode, i+1)
		}
	}
}
