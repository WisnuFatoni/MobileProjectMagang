package routes

import (
	"os"
	"regexp"
	"sort"
	"strings"
	"testing"
)

// Dokumentasi API wajib selalu sama dengan rute yang benar-benar terpasang. Tes ini gagal bila ada rute yang
// belum didokumentasikan (docs/openapi.yaml dan docs/API.md), atau dokumen menyebut rute yang sudah tidak ada.

const (
	berkasOpenAPI = "../../docs/openapi.yaml"
	berkasPanduan = "../../docs/API.md"
)

var (
	rePath   = regexp.MustCompile(`^  (/api/\S+):\s*$`)
	reMethod = regexp.MustCompile(`^    (get|post|put|patch|delete):\s*$`)
)

// rutePadaOpenAPI membaca pasangan "METHOD /path" dari bagian paths: tanpa pustaka YAML.
func rutePadaOpenAPI(t *testing.T) []string {
	t.Helper()
	b, err := os.ReadFile(berkasOpenAPI)
	if err != nil {
		t.Fatalf("docs/openapi.yaml tidak terbaca: %v", err)
	}
	var out []string
	dalamPaths := false
	path := ""
	for _, baris := range strings.Split(strings.ReplaceAll(string(b), "\r\n", "\n"), "\n") {
		if strings.HasPrefix(baris, "paths:") {
			dalamPaths = true
			continue
		}
		if !dalamPaths {
			continue
		}
		if m := rePath.FindStringSubmatch(baris); m != nil {
			path = m[1]
			continue
		}
		if m := reMethod.FindStringSubmatch(baris); m != nil && path != "" {
			out = append(out, strings.ToUpper(m[1])+" "+path)
		}
	}
	sort.Strings(out)
	return out
}

func TestSemuaRuteTerdokumentasi(t *testing.T) {
	terpasang := Pola()
	sort.Strings(terpasang)
	didoc := rutePadaOpenAPI(t)

	set := func(s []string) map[string]bool {
		m := map[string]bool{}
		for _, x := range s {
			m[x] = true
		}
		return m
	}
	adaDiDoc, adaDiRoute := set(didoc), set(terpasang)

	for _, r := range terpasang {
		if !adaDiDoc[r] {
			t.Errorf("rute %q belum ada di docs/openapi.yaml: tambahkan dokumentasinya", r)
		}
	}
	for _, r := range didoc {
		if !adaDiRoute[r] {
			t.Errorf("docs/openapi.yaml menyebut %q yang tidak ada di routes.go: hapus atau perbaiki", r)
		}
	}

	panduan, err := os.ReadFile(berkasPanduan)
	if err != nil {
		t.Fatalf("docs/API.md tidak terbaca: %v", err)
	}
	teksPanduan := strings.ReplaceAll(string(panduan), "\r\n", "\n") // berkas bisa tersimpan dengan CRLF di Windows
	for _, r := range terpasang {
		// Judul bagian: "## POST /api/panen/{id}/media"
		if !strings.Contains(teksPanduan, "## "+r+"\n") {
			t.Errorf("rute %q belum punya bagian \"## %s\" di docs/API.md", r, r)
		}
	}
}

func TestVersiDokumenSama(t *testing.T) {
	yaml, err := os.ReadFile(berkasOpenAPI)
	if err != nil {
		t.Fatal(err)
	}
	panduan, err := os.ReadFile(berkasPanduan)
	if err != nil {
		t.Fatal(err)
	}
	m := regexp.MustCompile(`(?m)^  version: (\S+)`).FindStringSubmatch(string(yaml))
	if m == nil {
		t.Fatal("info.version tidak ditemukan di openapi.yaml")
	}
	if !strings.Contains(string(panduan), "Versi "+m[1]) {
		t.Errorf("docs/API.md tidak menyebut \"Versi %s\" (sama dengan openapi.yaml)", m[1])
	}
	if !strings.Contains(string(panduan), "| "+m[1]+" |") {
		t.Errorf("Riwayat perubahan di docs/API.md belum punya baris untuk versi %s", m[1])
	}
}
