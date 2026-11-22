// Package config memuat pengaturan dari variabel lingkungan supaya rahasia tidak masuk ke kode.
package config

import (
	"errors"
	"os"
	"strconv"
	"time"
)

type Config struct {
	Addr        string        // alamat dengar, contoh ":8080"
	DSN         string        // string koneksi SQL Server
	JWTSecret   []byte        // kunci tanda tangan token akses
	MediaDir    string        // folder penyimpanan foto
	WebDir      string        // folder hasil build web admin (dist/); kosong berarti API tidak menyajikan web
	AksesTTL    time.Duration // umur token akses
	RefreshTTL  time.Duration // umur sesi (refresh token)
	MaksGagal   int           // jumlah gagal login sebelum akun dikunci
	DurasiKunci time.Duration // lama akun terkunci

	BatasLoginPerMenit int  // permintaan login dan refresh per menit per alamat IP; 0 berarti tanpa batas
	BatasUmumPerMenit  int  // semua permintaan API per menit per alamat IP; 0 berarti tanpa batas
	JejakSimpanHari    int  // jalur GPS inspeksi yang sudah diperiksa dihapus setelah sekian hari; 0 berarti disimpan tanpa batas
	PercayaiProxy      bool // percayai header X-Forwarded-For (hanya bila berada di belakang reverse proxy sendiri)
}

// Muat membaca .env (bila ada) lalu variabel lingkungan. DSN diambil dari FAI26_DSN, atau dirakit dari DB_*.
func Muat() (Config, error) {
	if err := MuatDotenv(".env"); err != nil {
		return Config{}, err
	}
	c := Config{
		Addr:        env("FAI26_ADDR", ":8080"),
		DSN:         os.Getenv("FAI26_DSN"),
		MediaDir:    env("FAI26_MEDIA_DIR", "media"),
		WebDir:      os.Getenv("FAI26_WEB_DIR"),
		AksesTTL:    15 * time.Minute,
		RefreshTTL:  30 * 24 * time.Hour,
		MaksGagal:   5,
		DurasiKunci: 15 * time.Minute,

		// Bawaan longgar: satu kebun bisa memakai satu alamat IP bersama (NAT operator) untuk banyak HP.
		BatasLoginPerMenit: bilangan("FAI26_BATAS_LOGIN", 30),
		BatasUmumPerMenit:  bilangan("FAI26_BATAS_UMUM", 600),
		JejakSimpanHari:    bilangan("FAI26_JEJAK_SIMPAN_HARI", 0),
		PercayaiProxy:      os.Getenv("FAI26_PERCAYAI_PROXY") == "true",
	}
	if c.DSN == "" {
		c.DSN = dsnDariDB()
	}
	if c.DSN == "" {
		return c, errors.New("database belum diatur: isi DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD di .env (atau FAI26_DSN)")
	}
	rahasia := os.Getenv("FAI26_JWT_SECRET")
	if len(rahasia) < 32 {
		return c, errors.New("FAI26_JWT_SECRET harus diisi minimal 32 karakter")
	}
	c.JWTSecret = []byte(rahasia)
	if v := os.Getenv("FAI26_AKSES_MENIT"); v != "" {
		if n, err := strconv.Atoi(v); err == nil && n > 0 {
			c.AksesTTL = time.Duration(n) * time.Minute
		}
	}
	return c, nil
}

// bilangan membaca bilangan bulat tidak negatif dari variabel lingkungan; salah format memakai nilai bawaan.
func bilangan(k string, bawaan int) int {
	if v := os.Getenv(k); v != "" {
		if n, err := strconv.Atoi(v); err == nil && n >= 0 {
			return n
		}
	}
	return bawaan
}

func env(k, bawaan string) string {
	if v := os.Getenv(k); v != "" {
		return v
	}
	return bawaan
}

// DSN hanya mengembalikan string koneksi database (dipakai perintah yang tidak butuh pengaturan lain, misalnya seed).
func DSN() (string, error) {
	if err := MuatDotenv(".env"); err != nil {
		return "", err
	}
	if v := os.Getenv("FAI26_DSN"); v != "" {
		return v, nil
	}
	if v := dsnDariDB(); v != "" {
		return v, nil
	}
	return "", errors.New("database belum diatur: isi DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD di .env (atau FAI26_DSN)")
}
