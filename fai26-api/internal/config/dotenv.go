package config

import (
	"bufio"
	"net/url"
	"os"
	"strings"
)

// MuatDotenv membaca berkas .env (KEY=VALUE per baris, # untuk komentar, nilai boleh diberi tanda kutip) dan
// mengisi variabel lingkungan yang BELUM diatur. Variabel yang sudah ada di lingkungan menang atas .env.
// Berkas yang tidak ada bukan galat.
func MuatDotenv(path string) error {
	f, err := os.Open(path)
	if os.IsNotExist(err) {
		return nil
	}
	if err != nil {
		return err
	}
	defer f.Close()
	sc := bufio.NewScanner(f)
	for sc.Scan() {
		k, v, ok := parsiBaris(sc.Text())
		if !ok {
			continue
		}
		if _, ada := os.LookupEnv(k); !ada {
			_ = os.Setenv(k, v)
		}
	}
	return sc.Err()
}

func parsiBaris(baris string) (kunci, nilai string, ok bool) {
	baris = strings.TrimSpace(strings.TrimPrefix(strings.TrimSpace(baris), "\ufeff"))
	if baris == "" || strings.HasPrefix(baris, "#") {
		return "", "", false
	}
	baris = strings.TrimPrefix(baris, "export ")
	i := strings.IndexByte(baris, '=')
	if i <= 0 {
		return "", "", false
	}
	kunci = strings.TrimSpace(baris[:i])
	nilai = strings.TrimSpace(baris[i+1:])
	if n := len(nilai); n >= 2 && (nilai[0] == '"' && nilai[n-1] == '"' || nilai[0] == '\'' && nilai[n-1] == '\'') {
		nilai = nilai[1 : n-1]
	}
	return kunci, nilai, true
}

// dsnDariDB merakit string koneksi SQL Server dari DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD,
// DB_ENCRYPT, dan DB_TRUST_CERT. Kosong bila DB_HOST atau DB_NAME tidak diisi.
func dsnDariDB() string {
	host, nama := os.Getenv("DB_HOST"), os.Getenv("DB_NAME")
	if host == "" || nama == "" {
		return ""
	}
	h := host
	if p := os.Getenv("DB_PORT"); p != "" {
		h += ":" + p
	}
	u := url.URL{Scheme: "sqlserver", Host: h}
	if user := os.Getenv("DB_USER"); user != "" {
		u.User = url.UserPassword(user, os.Getenv("DB_PASSWORD"))
	}
	q := url.Values{}
	q.Set("database", nama)
	if v := os.Getenv("DB_ENCRYPT"); v != "" {
		q.Set("encrypt", v)
	}
	if v := os.Getenv("DB_TRUST_CERT"); v != "" {
		q.Set("TrustServerCertificate", v)
	}
	u.RawQuery = q.Encode()
	return u.String()
}
