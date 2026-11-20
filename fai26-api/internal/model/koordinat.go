package model

import (
	"errors"
	"math"
)

// BatasAkurasiKoordinatM adalah akurasi GPS terburuk yang diterima untuk merekam koordinat TPH (HP memakai 30 m;
// server sedikit lebih longgar supaya selisih pembulatan tidak menolak titik yang sah).
const BatasAkurasiKoordinatM = 50

// KoordinatInput adalah isi PUT /api/tph/{id}/koordinat. Pointer supaya kolom yang tidak dikirim bisa dikenali.
type KoordinatInput struct {
	Lat      *float64 `json:"lat"`
	Lon      *float64 `json:"lon"`
	AkurasiM *float64 `json:"akurasi_m"`
}

// Periksa memastikan titik masuk akal. Titik (0,0) ditolak karena itu nilai bawaan perangkat bila GPS belum dapat.
func (k KoordinatInput) Periksa() error {
	if k.Lat == nil || k.Lon == nil || k.AkurasiM == nil {
		return errors.New("lat, lon, dan akurasi_m wajib diisi")
	}
	lat, lon, ak := *k.Lat, *k.Lon, *k.AkurasiM
	for _, v := range []float64{lat, lon, ak} {
		if math.IsNaN(v) || math.IsInf(v, 0) {
			return errors.New("koordinat tidak valid")
		}
	}
	if lat < -90 || lat > 90 || lon < -180 || lon > 180 {
		return errors.New("lat harus -90 sampai 90 dan lon harus -180 sampai 180")
	}
	if lat == 0 && lon == 0 {
		return errors.New("titik (0, 0) bukan lokasi TPH; GPS kemungkinan belum mendapat titik")
	}
	if ak < 0 || ak > BatasAkurasiKoordinatM {
		return errors.New("akurasi GPS harus 0 sampai 50 meter; coba lagi di tempat terbuka")
	}
	return nil
}

// KoordinatHasil adalah jawaban perekaman. Diterapkan false berarti TPH sudah punya koordinat (milik server
// dikembalikan, dan HP wajib memakainya).
type KoordinatHasil struct {
	Diterapkan bool    `json:"diterapkan"`
	Lat        float64 `json:"lat"`
	Lon        float64 `json:"lon"`
}
