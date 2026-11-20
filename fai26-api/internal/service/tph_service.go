package service

import (
	"context"
	"errors"

	"fai26-api/internal/apperror"
	"fai26-api/internal/auth"
	"fai26-api/internal/model"
	"fai26-api/internal/repository"
)

type TphRepo interface {
	RekamKoordinat(ctx context.Context, unitID, userID, tphID int64, lat, lon, akurasiM float64) (model.KoordinatHasil, error)
}

type TphService struct{ repo TphRepo }

func NewTphService(repo TphRepo) *TphService { return &TphService{repo: repo} }

// RekamKoordinat menyimpan titik GPS TPH dari HP bila TPH belum punya koordinat. Bila sudah punya, titik milik
// server dikembalikan apa adanya (Diterapkan=false) dan HP harus mengikutinya, bukan menimpanya.
func (s *TphService) RekamKoordinat(ctx context.Context, k *auth.Klaim, tphID int64, in model.KoordinatInput) (*model.KoordinatHasil, error) {
	if tphID <= 0 {
		return nil, apperror.BadRequest("id TPH tidak valid")
	}
	if err := in.Periksa(); err != nil {
		return nil, apperror.Unprocessable(err.Error())
	}
	hasil, err := s.repo.RekamKoordinat(ctx, k.UnitID, k.UserID, tphID, *in.Lat, *in.Lon, *in.AkurasiM)
	if errors.Is(err, repository.ErrNotFound) {
		return nil, apperror.NotFound("TPH tidak ditemukan")
	}
	if err != nil {
		return nil, apperror.Internal("Gagal merekam koordinat TPH", err)
	}
	return &hasil, nil
}
