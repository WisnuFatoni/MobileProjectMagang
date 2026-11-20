package apperror

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"testing"
)

func TestInternalBatasWaktuMenjadi503(t *testing.T) {
	for _, sebab := range []error{
		context.DeadlineExceeded, context.Canceled, fmt.Errorf("membungkus: %w", context.DeadlineExceeded),
	} {
		e := Internal("Gagal menyimpan sesi", sebab)
		if e.Status != http.StatusServiceUnavailable || e.Message != "Server sedang sibuk. Coba lagi sebentar" {
			t.Errorf("%v -> %d %q", sebab, e.Status, e.Message)
		}
		if !errors.Is(e, sebab) {
			t.Errorf("sebab teknis harus tetap bisa dilacak untuk log")
		}
	}
}

func TestInternalBiasaTetap500DanPesanAman(t *testing.T) {
	e := Internal("Gagal menyimpan sesi", errors.New("detail SQL rahasia"))
	if e.Status != http.StatusInternalServerError || e.Message != "Gagal menyimpan sesi" {
		t.Fatalf("%d %q", e.Status, e.Message)
	}
}

func TestDariMengenaliGalatAplikasiDanMembungkusYangLain(t *testing.T) {
	if Dari(Forbidden("x")).Status != 403 {
		t.Fatal("galat aplikasi harus diteruskan")
	}
	e := Dari(errors.New("acak"))
	if e.Status != 500 || e.Message != "Terjadi kesalahan pada server" {
		t.Fatalf("%d %q", e.Status, e.Message)
	}
}
