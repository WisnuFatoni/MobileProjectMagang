package middleware

import (
	"math"
	"net"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"time"

	"fai26-api/internal/apperror"
	"fai26-api/internal/response"
)

// PembatasLaju membatasi jumlah permintaan per klien (alamat IP) dengan ember token: ember berisi paling banyak
// `perMenit` token, terisi `perMenit` per menit, dan tiap permintaan memakai satu token. Disimpan di memori proses
// (cukup untuk satu server; bila nanti ada beberapa server, pembatasnya perlu dipindah ke reverse proxy).
type PembatasLaju struct {
	mu         sync.Mutex
	kapasitas  float64
	isiPerDet  float64
	ember      map[string]*ember
	sekarang   func() time.Time
	bersihLagi time.Time
}

type ember struct {
	token float64
	waktu time.Time
}

// Ember yang tidak dipakai selama ini dibuang supaya memori tidak membengkak oleh alamat yang hanya muncul sekali.
const umurEmberMenganggur = 10 * time.Minute

// NewPembatasLaju membuat pembatas. perMenit <= 0 berarti tanpa batas (mengembalikan nil; semua metode aman pada nil).
func NewPembatasLaju(perMenit int) *PembatasLaju {
	return newPembatasLaju(perMenit, time.Now)
}

func newPembatasLaju(perMenit int, sekarang func() time.Time) *PembatasLaju {
	if perMenit <= 0 {
		return nil
	}
	return &PembatasLaju{
		kapasitas: float64(perMenit),
		isiPerDet: float64(perMenit) / 60.0,
		ember:     map[string]*ember{},
		sekarang:  sekarang,
	}
}

// Boleh memakai satu token milik kunci. Bila habis, mengembalikan false dan berapa lama sampai token berikutnya ada.
func (p *PembatasLaju) Boleh(kunci string) (bool, time.Duration) {
	if p == nil {
		return true, 0
	}
	p.mu.Lock()
	defer p.mu.Unlock()
	now := p.sekarang()
	p.bersihkan(now)
	e := p.ember[kunci]
	if e == nil {
		e = &ember{token: p.kapasitas, waktu: now}
		p.ember[kunci] = e
	} else if dt := now.Sub(e.waktu).Seconds(); dt > 0 {
		e.token = math.Min(p.kapasitas, e.token+dt*p.isiPerDet)
		e.waktu = now
	}
	if e.token >= 1 {
		e.token--
		return true, 0
	}
	tunggu := time.Duration((1 - e.token) / p.isiPerDet * float64(time.Second))
	return false, tunggu
}

// bersihkan membuang ember yang lama menganggur; paling sering sekali semenit.
func (p *PembatasLaju) bersihkan(now time.Time) {
	if now.Before(p.bersihLagi) {
		return
	}
	p.bersihLagi = now.Add(time.Minute)
	for k, e := range p.ember {
		if now.Sub(e.waktu) > umurEmberMenganggur {
			delete(p.ember, k)
		}
	}
}

// jumlahEmber dipakai tes untuk memastikan pembersihan berjalan.
func (p *PembatasLaju) jumlahEmber() int {
	p.mu.Lock()
	defer p.mu.Unlock()
	return len(p.ember)
}

// KunciKlien mengambil alamat klien. Header X-Forwarded-For hanya dipercaya bila server memang berada di belakang
// reverse proxy sendiri (percayaiProxy): yang dipakai nilai TERAKHIR, yaitu yang ditambahkan proxy kita, karena nilai
// sebelumnya bisa dipalsukan klien.
func KunciKlien(r *http.Request, percayaiProxy bool) string {
	if percayaiProxy {
		if xff := r.Header.Get("X-Forwarded-For"); xff != "" {
			parts := strings.Split(xff, ",")
			if ip := strings.TrimSpace(parts[len(parts)-1]); ip != "" {
				return ip
			}
		}
	}
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		return r.RemoteAddr
	}
	return host
}

// BatasLaju menolak dengan 429 (dan header Retry-After) bila klien melebihi batas. Pembatas nil berarti tanpa batas.
func BatasLaju(p *PembatasLaju, percayaiProxy bool) Middleware {
	return func(next http.Handler) http.Handler {
		if p == nil {
			return next
		}
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			ok, tunggu := p.Boleh(KunciKlien(r, percayaiProxy))
			if !ok {
				detik := int(math.Ceil(tunggu.Seconds()))
				if detik < 1 {
					detik = 1
				}
				w.Header().Set("Retry-After", strconv.Itoa(detik))
				response.Error(w, r, apperror.New(http.StatusTooManyRequests, "Terlalu banyak permintaan. Coba lagi sebentar"))
				return
			}
			next.ServeHTTP(w, r)
		})
	}
}
