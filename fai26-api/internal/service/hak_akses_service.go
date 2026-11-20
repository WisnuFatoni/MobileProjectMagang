package service

import (
	"context"
	"errors"
	"strings"

	"fai26-api/internal/apperror"
	"fai26-api/internal/auth"
	"fai26-api/internal/model"
	"fai26-api/internal/repository"
)

type HakAksesRepo interface {
	Daftar(ctx context.Context) (*model.HakAkses, error)
	Ubah(ctx context.Context, aktorID, roleID int64, kode []string) error
}

type HakAksesService struct{ repo HakAksesRepo }

func NewHakAksesService(repo HakAksesRepo) *HakAksesService { return &HakAksesService{repo: repo} }

const maksIzinPerRole = 200

func (s *HakAksesService) Daftar(ctx context.Context) (*model.HakAkses, error) {
	o, err := s.repo.Daftar(ctx)
	if err != nil {
		return nil, apperror.Internal("Gagal membaca hak akses", err)
	}
	return o, nil
}

// Ubah mengganti seluruh hak satu role. Daftar boleh kosong (role tanpa hak). Kode ganda dilebur, spasi dibuang.
func (s *HakAksesService) Ubah(ctx context.Context, k *auth.Klaim, roleID int64, in model.HakAksesInput) error {
	if roleID <= 0 {
		return apperror.BadRequest("id role tidak valid")
	}
	if in.Permission == nil {
		return apperror.Unprocessable("permission wajib diisi (boleh daftar kosong)")
	}
	if len(in.Permission) > maksIzinPerRole {
		return apperror.Unprocessable("terlalu banyak permission")
	}
	lihat := map[string]bool{}
	bersih := make([]string, 0, len(in.Permission))
	for _, p := range in.Permission {
		p = strings.TrimSpace(p)
		if p == "" {
			return apperror.Unprocessable("kode permission tidak boleh kosong")
		}
		if !lihat[p] {
			lihat[p] = true
			bersih = append(bersih, p)
		}
	}
	err := s.repo.Ubah(ctx, k.UserID, roleID, bersih)
	switch {
	case err == nil:
		return nil
	case errors.Is(err, repository.ErrNotFound):
		return apperror.NotFound("Role tidak ditemukan")
	case errors.Is(err, repository.ErrReferensi):
		return apperror.Unprocessable("Ada kode permission yang tidak dikenal")
	case errors.Is(err, repository.ErrDilindungi):
		return apperror.Conflict("Hak role.kelola tidak boleh dicabut dari role ADMIN: tidak akan ada lagi yang bisa mengelola hak akses")
	default:
		return apperror.Internal("Gagal mengubah hak akses", err)
	}
}
