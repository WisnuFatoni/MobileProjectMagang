package service

import (
	"context"

	"fai26-api/internal/apperror"
	"fai26-api/internal/auth"
	"fai26-api/internal/model"
)

type MasterRepo interface {
	Lingkup(ctx context.Context, userID int64) (model.LingkupMaster, error)
	Versi(ctx context.Context, unitID int64, l model.LingkupMaster) (string, error)
	Ambil(ctx context.Context, unitID int64, l model.LingkupMaster) (*model.IsiMaster, error)
}

type MasterService struct{ repo MasterRepo }

func NewMasterService(repo MasterRepo) *MasterService { return &MasterService{repo: repo} }

// Ambil mengembalikan master unit milik pengguna. Bila versi yang dibawa HP sama dengan versi server,
// hanya {versi, berubah:false} yang dikirim sehingga HP tidak mengunduh ulang tanpa perlu.
func (s *MasterService) Ambil(ctx context.Context, k *auth.Klaim, versiHP string) (*model.Master, error) {
	lingkup, err := s.repo.Lingkup(ctx, k.UserID)
	if err != nil {
		return nil, apperror.Internal("Gagal membaca lingkup master", err)
	}
	versi, err := s.repo.Versi(ctx, k.UnitID, lingkup)
	if err != nil {
		return nil, apperror.Internal("Gagal membaca versi master", err)
	}
	if versiHP != "" && versiHP == versi {
		return &model.Master{Versi: versi, Berubah: false}, nil
	}
	isi, err := s.repo.Ambil(ctx, k.UnitID, lingkup)
	if err != nil {
		return nil, apperror.Internal("Gagal membaca master", err)
	}
	return &model.Master{
		Versi: versi, Berubah: true,
		Unit: isi.Unit, Divisi: isi.Divisi, Blok: isi.Blok, Tph: isi.Tph, Pemanen: isi.Pemanen, User: isi.User, RolePermission: isi.RolePermission,
	}, nil
}
