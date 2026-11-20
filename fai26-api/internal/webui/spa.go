// Package webui menyajikan hasil build web admin (folder dist/) dari alamat yang sama dengan API.
// Satu origin berarti tidak butuh CORS dan token tidak menyeberang domain.
package webui

import (
	"net/http"
	"os"
	"path"
	"path/filepath"
	"strings"
)

// Kebijakan keamanan konten. Ant Design menulis gaya lewat <style> di runtime, maka style-src butuh 'unsafe-inline'.
// Foto bukti dimuat lewat blob:, dan web tidak memuat apa pun dari domain lain.
const csp = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; " +
	"font-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'"

// Handler menyajikan berkas statis dari dir. Alamat yang bukan berkas (rute React Router seperti /tph) dijawab
// index.html supaya muat ulang dan tautan langsung berfungsi. Alamat /api/ tidak pernah sampai ke sini.
func Handler(dir string) http.Handler {
	statis := http.FileServer(http.Dir(dir))
	index := filepath.Join(dir, "index.html")

	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet && r.Method != http.MethodHead {
			w.Header().Set("Allow", "GET, HEAD")
			http.Error(w, "metode tidak diizinkan", http.StatusMethodNotAllowed)
			return
		}
		h := w.Header()
		h.Set("X-Content-Type-Options", "nosniff")
		h.Set("X-Frame-Options", "DENY")
		h.Set("Referrer-Policy", "same-origin")
		h.Set("Content-Security-Policy", csp)

		bersih := path.Clean("/" + r.URL.Path)
		if bersih != "/" && adaBerkas(dir, bersih) {
			// Nama berkas di /assets memuat hash isi, jadi aman disimpan lama. Berkas lain (favicon) diperiksa ulang.
			if strings.HasPrefix(bersih, "/assets/") {
				h.Set("Cache-Control", "public, max-age=31536000, immutable")
			} else {
				h.Set("Cache-Control", "no-cache")
			}
			statis.ServeHTTP(w, r)
			return
		}
		// Berkas di /assets yang tidak ada harus 404 sungguhan (bukan HTML), supaya galat pemuatan skrip jelas.
		if bersih == "/assets" || strings.HasPrefix(bersih, "/assets/") || path.Ext(bersih) != "" {
			http.NotFound(w, r)
			return
		}
		// index.html tidak boleh di-cache: tiap rilis menunjuk ke berkas hash yang baru.
		h.Set("Cache-Control", "no-cache")
		http.ServeFile(w, r, index)
	})
}

func adaBerkas(dir, rel string) bool {
	info, err := os.Stat(filepath.Join(dir, filepath.FromSlash(rel)))
	return err == nil && !info.IsDir()
}
