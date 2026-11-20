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
	dibuat   *model.RoleInput
	profil   [2]*string
	dihapus  int64
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

func (f *fakeHakAkses) Buat(_ context.Context, aktorID int64, in model.RoleInput) (int64, error) {
	f.dipanggi++
	f.aktor, f.dibuat = aktorID, &in
	return 9, f.err
}

func (f *fakeHakAkses) UbahProfil(_ context.Context, _, _ int64, nama, deskripsi *string) error {
	f.dipanggi++
	f.profil = [2]*string{nama, deskripsi}
	return f.err
}

func (f *fakeHakAkses) Hapus(_ context.Context, _, roleID int64) error {
	f.dipanggi++
	f.dihapus = roleID
	return f.err
}

func TestHakAksesBuatRoleBaruMerapikanKodeDanMemeriksaBentuk(t *testing.T) {
	f := &fakeHakAkses{}
	id, err := NewHakAksesService(f).Buat(context.Background(), klaimKrani, model.RoleInput{
		Kode: " kepala_kebun ", Nama: " Kepala Kebun ", Permission: []string{"panen.lihat", " panen.lihat"}})
	if err != nil || id != 9 {
		t.Fatalf("buat: %d %v", id, err)
	}
	if f.dibuat.Kode != "KEPALA_KEBUN" || f.dibuat.Nama != "Kepala Kebun" || !reflect.DeepEqual(f.dibuat.Permission, []string{"panen.lihat"}) {
		t.Fatalf("isi dikirim ke repository: %+v", f.dibuat)
	}
	// Tanpa daftar hak: dianggap kosong, bukan galat.
	if _, err := NewHakAksesService(&fakeHakAkses{}).Buat(context.Background(), klaimKrani, model.RoleInput{Kode: "TAMU", Nama: "Tamu"}); err != nil {
		t.Fatalf("role tanpa hak boleh: %v", err)
	}
	salah := map[string]model.RoleInput{
		"kode berspasi":       {Kode: "KEPALA KEBUN", Nama: "Kepala"},
		"kode diawali angka":  {Kode: "1ABC", Nama: "Kepala"},
		"kode terlalu pendek": {Kode: "A", Nama: "Kepala"},
		"nama kosong":         {Kode: "TAMU", Nama: " "},
		"deskripsi panjang":   {Kode: "TAMU", Nama: "Tamu", Deskripsi: string(make([]rune, 201))},
	}
	for nama, in := range salah {
		g := &fakeHakAkses{}
		if _, err := NewHakAksesService(g).Buat(context.Background(), klaimKrani, in); statusGalat(t, err) != http.StatusUnprocessableEntity || g.dipanggi != 0 {
			t.Errorf("%s: harus 422 tanpa menyentuh database: %v", nama, err)
		}
	}
}

func TestHakAksesBuatDanHapusGalatDipetakan(t *testing.T) {
	kasus := map[error]int{repository.ErrDuplikat: http.StatusConflict, repository.ErrMasihDipakai: http.StatusConflict,
		repository.ErrDilindungi: http.StatusConflict, repository.ErrNotFound: http.StatusNotFound}
	for e, want := range kasus {
		if _, err := NewHakAksesService(&fakeHakAkses{err: e}).Buat(context.Background(), klaimKrani, model.RoleInput{Kode: "TAMU", Nama: "Tamu"}); e != repository.ErrMasihDipakai && e != repository.ErrNotFound && statusGalat(t, err) != want {
			t.Errorf("buat %v: harus %d", e, want)
		}
		if err := NewHakAksesService(&fakeHakAkses{err: e}).Hapus(context.Background(), klaimKrani, 4); statusGalat(t, err) != want {
			t.Errorf("hapus %v: harus %d, dapat %v", e, want, err)
		}
	}
	if err := NewHakAksesService(&fakeHakAkses{}).Hapus(context.Background(), klaimKrani, 0); statusGalat(t, err) != http.StatusBadRequest {
		t.Errorf("id nol harus 400: %v", err)
	}
}

func TestHakAksesUbahJugaMengubahNamaBilaDiberikan(t *testing.T) {
	f := &fakeHakAkses{}
	n := " Kepala Kebun "
	if err := NewHakAksesService(f).Ubah(context.Background(), klaimKrani, 3, model.HakAksesInput{Permission: []string{}, Nama: &n}); err != nil {
		t.Fatal(err)
	}
	if f.profil[0] == nil || *f.profil[0] != "Kepala Kebun" || f.profil[1] != nil {
		t.Fatalf("profil: %+v", f.profil)
	}
	kosong := " "
	if err := NewHakAksesService(&fakeHakAkses{}).Ubah(context.Background(), klaimKrani, 3, model.HakAksesInput{Permission: []string{}, Nama: &kosong}); statusGalat(t, err) != http.StatusUnprocessableEntity {
		t.Fatalf("nama kosong harus 422: %v", err)
	}
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
