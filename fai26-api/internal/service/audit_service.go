package service

import (
	"context"
	"strings"

	"fai26-api/internal/apperror"
	"fai26-api/internal/auth"
	"fai26-api/internal/model"
)

// MaksBatchAudit adalah batas baris audit per kiriman.
const MaksBatchAudit = 500

type AuditService struct{ audit AuditRepo }

func NewAuditService(audit AuditRepo) *AuditService { return &AuditService{audit: audit} }

// TerimaBatch menyimpan audit_log dari HP. Mengembalikan jumlah yang diterima dan yang dilewati (sudah ada).
func (s *AuditService) TerimaBatch(ctx context.Context, k *auth.Klaim, items []model.AuditInput, ip string) (diterima, dilewati int, err error) {
	if len(items) > MaksBatchAudit {
		return 0, 0, apperror.TooLarge("Maksimal 500 baris per kiriman")
	}
	siap := make([]model.AuditSimpan, 0, len(items))
	for _, a := range items {
		a.AuditID = strings.ToLower(a.AuditID)
		if !model.ReUUID.MatchString(a.AuditID) || a.Tabel == "" || a.BarisID == "" || a.Aksi == "" {
			return 0, 0, apperror.Unprocessable("Baris audit tidak lengkap: " + a.AuditID)
		}
		waktu, perr := model.ParseWaktu(a.Waktu)
		if perr != nil {
			return 0, 0, apperror.Unprocessable("waktu audit: " + perr.Error())
		}
		siap = append(siap, model.AuditSimpan{Input: a, Waktu: waktu})
	}
	n, err := s.audit.TulisBatch(ctx, k.UserID, k.DeviceID, ip, siap)
	if err != nil {
		return 0, 0, apperror.Internal("Gagal menyimpan audit", err)
	}
	return n, len(items) - n, nil
}
