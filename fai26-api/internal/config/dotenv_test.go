package config

import (
	"net/url"
	"os"
	"path/filepath"
	"testing"
)

func TestParsiBaris(t *testing.T) {
	kasus := []struct {
		in, k, v string
		ok       bool
	}{
		{`DB_HOST=localhost`, "DB_HOST", "localhost", true},
		{`DB_PASSWORD="devlocal#2026"`, "DB_PASSWORD", "devlocal#2026", true}, // # di dalam kutip bukan komentar
		{`  DB_PORT = 1444  `, "DB_PORT", "1444", true},
		{`export A='b c'`, "A", "b c", true},
		{`# komentar`, "", "", false},
		{``, "", "", false},
		{`tanpa-sama-dengan`, "", "", false},
		{"\ufeffBOM=ya", "BOM", "ya", true},
	}
	for _, c := range kasus {
		k, v, ok := parsiBaris(c.in)
		if k != c.k || v != c.v || ok != c.ok {
			t.Errorf("%q -> (%q,%q,%v), harapan (%q,%q,%v)", c.in, k, v, ok, c.k, c.v, c.ok)
		}
	}
}

func TestDotenvTidakMenimpasLingkungan(t *testing.T) {
	dir := t.TempDir()
	p := filepath.Join(dir, ".env")
	if err := os.WriteFile(p, []byte("UJI_A=dari-berkas\nUJI_B=dari-berkas\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	t.Setenv("UJI_A", "dari-lingkungan")
	os.Unsetenv("UJI_B")
	t.Cleanup(func() { os.Unsetenv("UJI_B") })
	if err := MuatDotenv(p); err != nil {
		t.Fatal(err)
	}
	if os.Getenv("UJI_A") != "dari-lingkungan" || os.Getenv("UJI_B") != "dari-berkas" {
		t.Fatalf("A=%q B=%q", os.Getenv("UJI_A"), os.Getenv("UJI_B"))
	}
	if err := MuatDotenv(filepath.Join(dir, "tidak-ada")); err != nil {
		t.Fatalf("berkas tak ada bukan galat: %v", err)
	}
}

func TestDSNDariDBMenyandiKarakterKhusus(t *testing.T) {
	t.Setenv("DB_HOST", "localhost")
	t.Setenv("DB_PORT", "1444")
	t.Setenv("DB_NAME", "fai26_dev")
	t.Setenv("DB_USER", "devlocal")
	t.Setenv("DB_PASSWORD", "p@ss#:/?word")
	t.Setenv("DB_ENCRYPT", "true")
	t.Setenv("DB_TRUST_CERT", "true")
	dsn := dsnDariDB()
	u, err := url.Parse(dsn)
	if err != nil {
		t.Fatal(err)
	}
	pw, _ := u.User.Password()
	if u.Host != "localhost:1444" || u.User.Username() != "devlocal" || pw != "p@ss#:/?word" ||
		u.Query().Get("database") != "fai26_dev" || u.Query().Get("TrustServerCertificate") != "true" {
		t.Fatalf("DSN salah: %s", dsn)
	}
	t.Setenv("DB_NAME", "")
	if dsnDariDB() != "" {
		t.Fatal("tanpa DB_NAME seharusnya kosong")
	}
}
