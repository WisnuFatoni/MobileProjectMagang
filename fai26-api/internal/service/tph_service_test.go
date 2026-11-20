package service

import (
	"context"
	"errors"
	"math"
	"net/http"
	"testing"

	"fai26-api/internal/apperror"
	"fai26-api/internal/model"
	"fai26-api/internal/repository"
)

type fakeTph struct {
	hasil               model.KoordinatHasil
	err                 error
	dipanggil           int
	unit, user, tph     int64
	lat, lon, akurasiMt float64
}

func (f *fakeTph) RekamKoordinat(_ context.Context, unitID, userID, tphID int64, lat, lon, akurasiM float64) (model.KoordinatHasil, error) {
	f.dipanggil++
	f.unit, f.user, f.tph, f.lat, f.lon, f.akurasiMt = unitID, userID, tphID, lat, lon, akurasiM
	return f.hasil, f.err
}

func p(v float64) *float64 { return &v }

func statusGalat(t *testing.T, err error) int {
	t.Helper()
	var ae *apperror.Error
	if !errors.As(err, &ae) {
		t.Fatalf("galat bukan apperror: %v", err)
	}
	return ae.Status
}

func TestKoordinatDisimpanDenganIdentitasDariToken(t *testing.T) {
	f := &fakeTph{hasil: model.KoordinatHasil{Diterapkan: true, Lat: 3.1, Lon: 101.6}}
	out, err := NewTphService(f).RekamKoordinat(context.Background(), klaimKrani, 7, model.KoordinatInput{Lat: p(3.1), Lon: p(101.6), AkurasiM: p(8)})
	if err != nil || !out.Diterapkan {
		t.Fatalf("%+v %v", out, err)
	}
	if f.unit != klaimKrani.UnitID || f.user != klaimKrani.UserID || f.tph != 7 || f.lat != 3.1 || f.lon != 101.6 || f.akurasiMt != 8 {
		t.Fatalf("argumen ke repository salah: %+v", f)
	}
}

func TestKoordinatSudahAdaMengembalikanTitikServer(t *testing.T) {
	f := &fakeTph{hasil: model.KoordinatHasil{Diterapkan: false, Lat: 3.2, Lon: 101.7}}
	out, err := NewTphService(f).RekamKoordinat(context.Background(), klaimKrani, 7, model.KoordinatInput{Lat: p(3.1), Lon: p(101.6), AkurasiM: p(8)})
	if err != nil {
		t.Fatal(err)
	}
	if out.Diterapkan || out.Lat != 3.2 || out.Lon != 101.7 {
		t.Fatalf("HP harus menerima titik server: %+v", out)
	}
}

func TestKoordinatTidakValidDitolakTanpaMenyentuhDatabase(t *testing.T) {
	kasus := map[string]model.KoordinatInput{
		"lat kosong":        {Lon: p(101), AkurasiM: p(5)},
		"lon kosong":        {Lat: p(3), AkurasiM: p(5)},
		"akurasi kosong":    {Lat: p(3), Lon: p(101)},
		"lat di luar":       {Lat: p(91), Lon: p(101), AkurasiM: p(5)},
		"lon di luar":       {Lat: p(3), Lon: p(181), AkurasiM: p(5)},
		"titik nol":         {Lat: p(0), Lon: p(0), AkurasiM: p(5)},
		"akurasi buruk":     {Lat: p(3), Lon: p(101), AkurasiM: p(51)},
		"akurasi negatif":   {Lat: p(3), Lon: p(101), AkurasiM: p(-1)},
		"lat bukan angka":   {Lat: p(nan()), Lon: p(101), AkurasiM: p(5)},
		"lon tak terhingga": {Lat: p(3), Lon: p(inf()), AkurasiM: p(5)},
	}
	for nama, in := range kasus {
		f := &fakeTph{}
		_, err := NewTphService(f).RekamKoordinat(context.Background(), klaimKrani, 7, in)
		if statusGalat(t, err) != http.StatusUnprocessableEntity {
			t.Errorf("%s: harus 422, dapat %v", nama, err)
		}
		if f.dipanggil != 0 {
			t.Errorf("%s: repository tidak boleh dipanggil", nama)
		}
	}
}

func TestKoordinatBatasAkurasiDiterima(t *testing.T) {
	f := &fakeTph{hasil: model.KoordinatHasil{Diterapkan: true}}
	for _, ak := range []float64{0, 30, 50} {
		if _, err := NewTphService(f).RekamKoordinat(context.Background(), klaimKrani, 1, model.KoordinatInput{Lat: p(3), Lon: p(101), AkurasiM: p(ak)}); err != nil {
			t.Errorf("akurasi %v harus diterima: %v", ak, err)
		}
	}
}

func TestKoordinatIdTidakValid(t *testing.T) {
	f := &fakeTph{}
	for _, id := range []int64{0, -5} {
		_, err := NewTphService(f).RekamKoordinat(context.Background(), klaimKrani, id, model.KoordinatInput{Lat: p(3), Lon: p(101), AkurasiM: p(5)})
		if statusGalat(t, err) != http.StatusBadRequest {
			t.Errorf("id %d harus 400: %v", id, err)
		}
	}
}

func TestKoordinatTphTidakAda404DanGaluDatabaseTidakBocor(t *testing.T) {
	in := model.KoordinatInput{Lat: p(3), Lon: p(101), AkurasiM: p(5)}
	_, err := NewTphService(&fakeTph{err: repository.ErrNotFound}).RekamKoordinat(context.Background(), klaimKrani, 9, in)
	if statusGalat(t, err) != http.StatusNotFound {
		t.Fatalf("harus 404: %v", err)
	}
	_, err = NewTphService(&fakeTph{err: errors.New("Invalid TDS stream: detail internal")}).RekamKoordinat(context.Background(), klaimKrani, 9, in)
	ae := apperror.Dari(err)
	if ae.Status != http.StatusInternalServerError || ae.Message == "" || ae.Message == "Invalid TDS stream: detail internal" {
		t.Fatalf("galat database harus 500 dengan pesan umum: %+v", ae)
	}
}

func nan() float64 { return math.NaN() }
func inf() float64 { return math.Inf(1) }
