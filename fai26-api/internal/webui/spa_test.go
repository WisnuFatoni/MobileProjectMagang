package webui

import (
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func siapkanDist(t *testing.T) string {
	t.Helper()
	dir := t.TempDir()
	tulis := func(rel, isi string) {
		p := filepath.Join(dir, filepath.FromSlash(rel))
		if err := os.MkdirAll(filepath.Dir(p), 0o755); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(p, []byte(isi), 0o644); err != nil {
			t.Fatal(err)
		}
	}
	tulis("index.html", "<!doctype html><title>fAI26 Admin</title><div id=root></div>")
	tulis("favicon.svg", "<svg/>")
	tulis("assets/app-abc123.js", "console.log('app')")
	return dir
}

func ambil(h http.Handler, metode, url string) *httptest.ResponseRecorder {
	w := httptest.NewRecorder()
	h.ServeHTTP(w, httptest.NewRequest(metode, url, nil))
	return w
}

func TestRuteReactDijawabIndexHtml(t *testing.T) {
	h := Handler(siapkanDist(t))
	for _, url := range []string{"/", "/tph", "/tph/cetak", "/login", "/panen?status=BARU"} {
		w := ambil(h, "GET", url)
		if w.Code != 200 || !strings.Contains(w.Body.String(), "fAI26 Admin") {
			t.Errorf("%s: kode %d, isi %q", url, w.Code, w.Body.String())
		}
		if cc := w.Header().Get("Cache-Control"); cc != "no-cache" {
			t.Errorf("%s: index.html harus no-cache, dapat %q", url, cc)
		}
	}
}

func TestAssetBerhashDisimpanLama(t *testing.T) {
	h := Handler(siapkanDist(t))
	w := ambil(h, "GET", "/assets/app-abc123.js")
	if w.Code != 200 || !strings.Contains(w.Body.String(), "console.log") {
		t.Fatalf("asset tidak terlayani: %d", w.Code)
	}
	if cc := w.Header().Get("Cache-Control"); !strings.Contains(cc, "immutable") {
		t.Errorf("asset berhash harus immutable, dapat %q", cc)
	}
}

func TestBerkasTakAdaDijawab404BukanHtml(t *testing.T) {
	h := Handler(siapkanDist(t))
	for _, url := range []string{"/assets/hilang-xyz.js", "/hilang.png", "/assets/"} {
		w := ambil(h, "GET", url)
		if w.Code != 404 {
			t.Errorf("%s: harus 404, dapat %d", url, w.Code)
		}
		if strings.Contains(w.Body.String(), "fAI26 Admin") {
			t.Errorf("%s: 404 tidak boleh berisi index.html", url)
		}
	}
}

func TestFaviconTidakImmutable(t *testing.T) {
	w := ambil(Handler(siapkanDist(t)), "GET", "/favicon.svg")
	if w.Code != 200 || w.Header().Get("Cache-Control") != "no-cache" {
		t.Errorf("favicon: kode %d, cache %q", w.Code, w.Header().Get("Cache-Control"))
	}
}

func TestHeaderKeamananSelaluAda(t *testing.T) {
	w := ambil(Handler(siapkanDist(t)), "GET", "/tph")
	for k, mau := range map[string]string{
		"X-Content-Type-Options": "nosniff",
		"X-Frame-Options":        "DENY",
		"Referrer-Policy":        "same-origin",
	} {
		if got := w.Header().Get(k); got != mau {
			t.Errorf("%s = %q, mau %q", k, got, mau)
		}
	}
	if c := w.Header().Get("Content-Security-Policy"); !strings.Contains(c, "frame-ancestors 'none'") || strings.Contains(c, "script-src 'self' 'unsafe") {
		t.Errorf("CSP tidak sesuai: %q", c)
	}
}

func TestPathTraversalTidakKeluarDariFolder(t *testing.T) {
	induk := t.TempDir()
	if err := os.WriteFile(filepath.Join(induk, "rahasia.txt"), []byte("RAHASIA"), 0o644); err != nil {
		t.Fatal(err)
	}
	dir := filepath.Join(induk, "dist")
	if err := os.MkdirAll(dir, 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(dir, "index.html"), []byte("INDEX"), 0o644); err != nil {
		t.Fatal(err)
	}
	h := Handler(dir)
	for _, url := range []string{"/../rahasia.txt", "/assets/../../rahasia.txt", "/%2e%2e/rahasia.txt", "/..%2frahasia.txt"} {
		w := ambil(h, "GET", url)
		if strings.Contains(w.Body.String(), "RAHASIA") {
			t.Errorf("%s: berkas di luar folder bocor", url)
		}
	}
}

func TestMetodeSelainGetDitolak(t *testing.T) {
	h := Handler(siapkanDist(t))
	for _, m := range []string{"POST", "PUT", "DELETE"} {
		if w := ambil(h, m, "/tph"); w.Code != http.StatusMethodNotAllowed {
			t.Errorf("%s: harus 405, dapat %d", m, w.Code)
		}
	}
	if w := ambil(h, "HEAD", "/tph"); w.Code != 200 {
		t.Errorf("HEAD harus 200, dapat %d", w.Code)
	}
}
