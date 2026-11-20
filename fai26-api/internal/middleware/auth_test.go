package middleware

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"fai26-api/internal/auth"
)

type aksesTiruan struct {
	aktif map[int64]bool
	izin  map[int64]map[string]bool
}

func (a aksesTiruan) Aktif(_ context.Context, id int64) (bool, error) { return a.aktif[id], nil }
func (a aksesTiruan) Punya(_ context.Context, id int64, p string) (bool, error) {
	return a.izin[id][p], nil
}

func siapkan(t *testing.T) (http.Handler, *auth.TokenManager) {
	t.Helper()
	tokens := auth.NewTokenManager([]byte("rahasia-uji-yang-cukup-panjang-123456"), time.Minute)
	akses := aksesTiruan{
		aktif: map[int64]bool{1: true, 2: false, 3: true},
		izin:  map[int64]map[string]bool{1: {"panen.kirim": true}},
	}
	tujuan := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		k := auth.KlaimDari(r.Context())
		if k == nil {
			t.Error("klaim tidak ada di konteks")
			return
		}
		w.WriteHeader(http.StatusNoContent)
	})
	h := Rantai(tujuan, Pulih(), Autentikasi(tokens, akses), Izin(akses, "panen.kirim"))
	return h, tokens
}

func panggil(h http.Handler, token string) int {
	r := httptest.NewRequest("GET", "/x", nil)
	if token != "" {
		r.Header.Set("Authorization", "Bearer "+token)
	}
	w := httptest.NewRecorder()
	h.ServeHTTP(w, r)
	return w.Code
}

func TestAutentikasiDanIzin(t *testing.T) {
	h, tokens := siapkan(t)
	sah, _, _ := tokens.Buat(1, 1, "dev")
	nonaktif, _, _ := tokens.Buat(2, 1, "dev")
	tanpaIzin, _, _ := tokens.Buat(3, 1, "dev")
	lain := auth.NewTokenManager([]byte("kunci-lain-yang-juga-cukup-panjang-99999"), time.Minute)
	palsu, _, _ := lain.Buat(1, 1, "dev")
	basi, _, _ := auth.NewTokenManager([]byte("rahasia-uji-yang-cukup-panjang-123456"), -time.Minute).Buat(1, 1, "dev")

	kasus := []struct {
		nama, token string
		kode        int
	}{
		{"tanpa token", "", 401},
		{"token rusak", sah + "x", 401},
		{"ditandatangani kunci lain", palsu, 401},
		{"kedaluwarsa", basi, 401},
		{"akun nonaktif", nonaktif, 403},
		{"akun aktif tanpa permission", tanpaIzin, 403},
		{"sah dan berizin", sah, 204},
	}
	for _, k := range kasus {
		if got := panggil(h, k.token); got != k.kode {
			t.Errorf("%s: harapan %d, dapat %d", k.nama, k.kode, got)
		}
	}
}

func TestPulihMengubahPanicMenjadi500(t *testing.T) {
	h := Rantai(http.HandlerFunc(func(http.ResponseWriter, *http.Request) { panic("meledak") }), Pulih())
	if got := panggil(h, ""); got != 500 {
		t.Fatalf("harapan 500, dapat %d", got)
	}
}

func TestBatasWaktuMemasangTenggatPadaKonteks(t *testing.T) {
	var ada bool
	h := Rantai(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, ada = r.Context().Deadline()
		w.WriteHeader(http.StatusNoContent)
	}), BatasWaktu(time.Second))
	if got := panggil(h, ""); got != 204 || !ada {
		t.Fatalf("kode=%d adaTenggat=%v", got, ada)
	}
}
