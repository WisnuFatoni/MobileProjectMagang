// Package middleware berisi pembungkus handler HTTP: autentikasi, izin, pemulihan panic, dan log.
package middleware

import (
	"context"
	"log"
	"net/http"
	"strings"
	"time"

	"fai26-api/internal/apperror"
	"fai26-api/internal/auth"
	"fai26-api/internal/response"
)

// Akses adalah yang dibutuhkan middleware untuk memeriksa akun dan hak akses.
type Akses interface {
	Aktif(ctx context.Context, userID int64) (bool, error)
	Punya(ctx context.Context, userID int64, permission string) (bool, error)
}

// Middleware membungkus sebuah handler.
type Middleware func(http.Handler) http.Handler

// Rantai menerapkan middleware dari yang paling luar (pertama) ke yang paling dalam.
func Rantai(h http.Handler, mws ...Middleware) http.Handler {
	for i := len(mws) - 1; i >= 0; i-- {
		h = mws[i](h)
	}
	return h
}

// Autentikasi memastikan token sah dan akun masih aktif, lalu menaruh klaimnya di konteks.
func Autentikasi(tokens *auth.TokenManager, akses Akses) Middleware {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			h := r.Header.Get("Authorization")
			if !strings.HasPrefix(h, "Bearer ") {
				response.Error(w, r, apperror.Unauthorized("Token tidak ada"))
				return
			}
			k, err := tokens.Periksa(strings.TrimPrefix(h, "Bearer "))
			if err != nil {
				response.Error(w, r, apperror.Unauthorized("Token tidak sah atau kedaluwarsa"))
				return
			}
			aktif, err := akses.Aktif(r.Context(), k.UserID)
			if err != nil {
				response.Error(w, r, apperror.Internal("Gagal memeriksa akun", err))
				return
			}
			if !aktif {
				response.Error(w, r, apperror.Forbidden("Akun tidak aktif"))
				return
			}
			next.ServeHTTP(w, r.WithContext(auth.DenganKlaim(r.Context(), k)))
		})
	}
}

// Izin mensyaratkan permission tertentu. Harus berada setelah Autentikasi.
func Izin(akses Akses, permission string) Middleware {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			k := auth.KlaimDari(r.Context())
			if k == nil {
				response.Error(w, r, apperror.Unauthorized("Token tidak ada"))
				return
			}
			ada, err := akses.Punya(r.Context(), k.UserID, permission)
			if err != nil {
				response.Error(w, r, apperror.Internal("Gagal memeriksa hak akses", err))
				return
			}
			if !ada {
				response.Error(w, r, apperror.Forbidden("Tidak berhak ("+permission+")"))
				return
			}
			next.ServeHTTP(w, r)
		})
	}
}

// Pulih mengubah panic menjadi jawaban 500 supaya server tidak mati.
func Pulih() Middleware {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			defer func() {
				if p := recover(); p != nil {
					log.Printf("PANIC %s %s: %v", r.Method, r.URL.Path, p)
					response.Error(w, r, apperror.Internal("Terjadi kesalahan pada server", nil))
				}
			}()
			next.ServeHTTP(w, r)
		})
	}
}

// BatasWaktu memberi setiap permintaan batas waktu. Operasi database memakai konteks permintaan, jadi bila
// database tersendat, permintaan dibatalkan dan dijawab 503 sebelum HP menyerah menunggu.
func BatasWaktu(d time.Duration) Middleware {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			ctx, batal := context.WithTimeout(r.Context(), d)
			defer batal()
			next.ServeHTTP(w, r.WithContext(ctx))
		})
	}
}

type perekamStatus struct {
	http.ResponseWriter
	kode int
}

func (p *perekamStatus) WriteHeader(k int) {
	p.kode = k
	p.ResponseWriter.WriteHeader(k)
}

// Catat menulis satu baris log per permintaan: metode, path, status, lama.
func Catat() Middleware {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			mulai := time.Now()
			p := &perekamStatus{ResponseWriter: w, kode: http.StatusOK}
			next.ServeHTTP(p, r)
			log.Printf("%s %s %d %s", r.Method, r.URL.Path, p.kode, time.Since(mulai).Round(time.Millisecond))
		})
	}
}
