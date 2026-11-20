package middleware

import (
	"net/http"
	"net/http/httptest"
	"sync"
	"testing"
	"time"
)

type jamUji struct{ t time.Time }

func (j *jamUji) now() time.Time       { return j.t }
func (j *jamUji) maju(d time.Duration) { j.t = j.t.Add(d) }
func baru() *jamUji                    { return &jamUji{t: time.Date(2026, 11, 11, 8, 0, 0, 0, time.UTC)} }

func TestLajuMenghabiskanKapasitasLaluMenolak(t *testing.T) {
	j := baru()
	p := newPembatasLaju(3, j.now)
	for i := 0; i < 3; i++ {
		if ok, _ := p.Boleh("1.1.1.1"); !ok {
			t.Fatalf("permintaan ke-%d harus boleh", i+1)
		}
	}
	ok, tunggu := p.Boleh("1.1.1.1")
	if ok {
		t.Fatal("permintaan ke-4 harus ditolak")
	}
	// 3 per menit = 1 token tiap 20 detik.
	if tunggu < 19*time.Second || tunggu > 21*time.Second {
		t.Fatalf("lama tunggu %v, seharusnya sekitar 20 detik", tunggu)
	}
}

func TestLajuTerisiKembaliSeiringWaktu(t *testing.T) {
	j := baru()
	p := newPembatasLaju(6, j.now) // 1 token per 10 detik
	for i := 0; i < 6; i++ {
		p.Boleh("a")
	}
	if ok, _ := p.Boleh("a"); ok {
		t.Fatal("harus habis")
	}
	j.maju(10 * time.Second)
	if ok, _ := p.Boleh("a"); !ok {
		t.Fatal("setelah 10 detik satu token harus kembali")
	}
	if ok, _ := p.Boleh("a"); ok {
		t.Fatal("hanya satu token yang kembali")
	}
	j.maju(time.Hour)
	for i := 0; i < 6; i++ {
		if ok, _ := p.Boleh("a"); !ok {
			t.Fatalf("setelah lama diam kapasitas penuh kembali, gagal pada ke-%d", i+1)
		}
	}
	if ok, _ := p.Boleh("a"); ok {
		t.Fatal("kapasitas tidak boleh melebihi batas walau diam lama")
	}
}

func TestLajuTiapKlienTerpisah(t *testing.T) {
	p := newPembatasLaju(1, baru().now)
	if ok, _ := p.Boleh("a"); !ok {
		t.Fatal("a pertama")
	}
	if ok, _ := p.Boleh("a"); ok {
		t.Fatal("a kedua harus ditolak")
	}
	if ok, _ := p.Boleh("b"); !ok {
		t.Fatal("klien lain tidak boleh ikut terkena")
	}
}

func TestLajuNilNolBerartiTanpaBatas(t *testing.T) {
	if NewPembatasLaju(0) != nil || NewPembatasLaju(-5) != nil {
		t.Fatal("batas nol atau negatif harus menghasilkan nil")
	}
	var p *PembatasLaju
	for i := 0; i < 1000; i++ {
		if ok, _ := p.Boleh("x"); !ok {
			t.Fatal("nil tidak boleh membatasi")
		}
	}
	h := BatasLaju(nil, false)(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { w.WriteHeader(204) }))
	for i := 0; i < 50; i++ {
		w := httptest.NewRecorder()
		h.ServeHTTP(w, httptest.NewRequest("GET", "/", nil))
		if w.Code != 204 {
			t.Fatalf("tanpa batas harus selalu lolos, dapat %d", w.Code)
		}
	}
}

func TestLajuEmberMenganggurDibuang(t *testing.T) {
	j := baru()
	p := newPembatasLaju(10, j.now)
	for i := 0; i < 500; i++ {
		p.Boleh("ip-" + string(rune('a'+i%26)) + time.Duration(i).String())
	}
	if p.jumlahEmber() != 500 {
		t.Fatalf("500 klien berbeda, dapat %d", p.jumlahEmber())
	}
	j.maju(umurEmberMenganggur + 2*time.Minute)
	p.Boleh("baru")
	if n := p.jumlahEmber(); n != 1 {
		t.Fatalf("ember lama harus dibuang, tersisa %d", n)
	}
}

func TestMiddlewareLajuMenjawab429DenganRetryAfter(t *testing.T) {
	p := newPembatasLaju(2, baru().now)
	h := BatasLaju(p, false)(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { w.WriteHeader(200) }))
	panggil := func() *httptest.ResponseRecorder {
		w := httptest.NewRecorder()
		r := httptest.NewRequest("POST", "/api/auth/login", nil)
		r.RemoteAddr = "10.0.0.5:51234"
		h.ServeHTTP(w, r)
		return w
	}
	panggil()
	panggil()
	w := panggil()
	if w.Code != http.StatusTooManyRequests {
		t.Fatalf("harus 429, dapat %d", w.Code)
	}
	if ra := w.Header().Get("Retry-After"); ra != "30" {
		t.Fatalf("Retry-After = %q, seharusnya 30 (2 per menit)", ra)
	}
	if got := w.Body.String(); got == "" || got[0] != '{' {
		t.Fatalf("badan harus JSON galat: %q", got)
	}
}

func TestKunciKlien(t *testing.T) {
	r := httptest.NewRequest("GET", "/", nil)
	r.RemoteAddr = "192.168.1.10:5555"
	r.Header.Set("X-Forwarded-For", "6.6.6.6, 203.0.113.9")
	if got := KunciKlien(r, false); got != "192.168.1.10" {
		t.Errorf("tanpa proxy harus alamat sambungan, dapat %q", got)
	}
	if got := KunciKlien(r, true); got != "203.0.113.9" {
		t.Errorf("dengan proxy harus nilai TERAKHIR (bukan yang bisa dipalsukan), dapat %q", got)
	}
	r2 := httptest.NewRequest("GET", "/", nil)
	r2.RemoteAddr = "[2001:db8::1]:443"
	if got := KunciKlien(r2, true); got != "2001:db8::1" {
		t.Errorf("IPv6: %q", got)
	}
	r3 := httptest.NewRequest("GET", "/", nil)
	r3.RemoteAddr = "tanpa-port"
	if got := KunciKlien(r3, false); got != "tanpa-port" {
		t.Errorf("alamat tanpa port: %q", got)
	}
}

func TestLajuAmanDipakaiBersamaan(t *testing.T) {
	p := newPembatasLaju(100, baru().now)
	var wg sync.WaitGroup
	var mu sync.Mutex
	boleh := 0
	for g := 0; g < 20; g++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for i := 0; i < 20; i++ {
				if ok, _ := p.Boleh("sama"); ok {
					mu.Lock()
					boleh++
					mu.Unlock()
				}
			}
		}()
	}
	wg.Wait()
	if boleh != 100 {
		t.Fatalf("dari 400 permintaan serentak tepat 100 yang boleh, dapat %d", boleh)
	}
}
