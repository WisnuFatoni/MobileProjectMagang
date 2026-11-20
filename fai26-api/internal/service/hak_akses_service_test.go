package service

import (
	"context"
	"errors"
	"net/http"
	"reflect"
	"testing"

	"fai26-api/internal/model"
	"fai26-api/internal/repository"
)

type fakeHakAkses struct {
	err      error
	roleID   int64
	aktor    int64
	kode     []string
	dipanggi int
}

func (f *fakeHakAkses) Daftar(context.Context) (*model.HakAkses, error) {
	if f.err != nil {
		return nil, f.err
	}
	return &model.HakAkses{Role: []model.HakAksesRole{{RoleID: 1, Kode: "KRANI", Permission: []string{"panen.kirim"}}}}, nil
}

func (f *fakeHakAkses) Ubah(_ context.Context, aktorID, roleID int64, kode []string) error {
	f.dipanggi++
	f.aktor, f.roleID, f.kode = aktorID, roleID, kode
	return f.err
}

func TestHakAksesUbahMembersihkanDanMeneruskanDenganIdentitasDariToken(t *testing.T) {
	f := &fakeHakAkses{}
	in := model.HakAksesInput{Permission: []string{" panen.lihat ", "panen.kirim", "panen.lihat"}}
	if err := NewHakAksesService(f).Ubah(context.Background(), klaimKrani, 3, in); err != nil {
		t.Fatal(err)
	}
	if f.roleID != 3 || f.aktor != klaimKrani.UserID {
		t.Fatalf("argumen salah: %+v", f)
	}
	if want := []string{"panen.lihat", "panen.kirim"}; !reflect.DeepEqual(f.kode, want) {
		t.Fatalf("kode harus dibersihkan dan tanpa ganda: %v", f.kode)
	}
}

func TestHakAksesDaftarKosongBoleh(t *testing.T) {
	f := &fakeHakAkses{}
	if err := NewHakAksesService(f).Ubah(context.Background(), klaimKrani, 3, model.HakAksesInput{Permission: []string{}}); err != nil {
		t.Fatalf("role tanpa hak harus boleh: %v", err)
	}
	if len(f.kode) != 0 || f.dipanggi != 1 {
		t.Fatalf("%+v", f)
	}
}

func TestHakAksesInputTidakSahDitolakTanpaMenyentuhDatabase(t *testing.T) {
	banyak := make([]string, maksIzinPerRole+1)
	for i := range banyak {
		banyak[i] = "x" + string(rune('a'+i%26))
	}
	kasus := map[string]struct {
		id     int64
		in     model.HakAksesInput
		status int
	}{
		"id nol":         {0, model.HakAksesInput{Permission: []string{"a"}}, http.StatusBadRequest},
		"id negatif":     {-1, model.HakAksesInput{Permission: []string{"a"}}, http.StatusBadRequest},
		"tanpa kunci":    {1, model.HakAksesInput{}, http.StatusUnprocessableEntity},
		"kode kosong":    {1, model.HakAksesInput{Permission: []string{"a", "  "}}, http.StatusUnprocessableEntity},
		"terlalu banyak": {1, model.HakAksesInput{Permission: banyak}, http.StatusUnprocessableEntity},
	}
	for nama, k := range kasus {
		f := &fakeHakAkses{}
		err := NewHakAksesService(f).Ubah(context.Background(), klaimKrani, k.id, k.in)
		if statusGalat(t, err) != k.status {
			t.Errorf("%s: harus %d, dapat %v", nama, k.status, err)
		}
		if f.dipanggi != 0 {
			t.Errorf("%s: repository tidak boleh dipanggil", nama)
		}
	}
}

func TestHakAksesGalatRepositoryDipetakan(t *testing.T) {
	in := model.HakAksesInput{Permission: []string{"panen.lihat"}}
	kasus := map[error]int{
		repository.ErrNotFound:    http.StatusNotFound,
		repository.ErrReferensi:   http.StatusUnprocessableEntity,
		repository.ErrDilindungi:  http.StatusConflict,
		errors.New("TDS rincian"): http.StatusInternalServerError,
	}
	for e, want := range kasus {
		err := NewHakAksesService(&fakeHakAkses{err: e}).Ubah(context.Background(), klaimKrani, 1, in)
		if got := statusGalat(t, err); got != want {
			t.Errorf("%v: harus %d, dapat %d", e, want, got)
		}
	}
	if _, err := NewHakAksesService(&fakeHakAkses{err: errors.New("x")}).Daftar(context.Background()); statusGalat(t, err) != 500 {
		t.Fatalf("galat baca harus 500: %v", err)
	}
}
