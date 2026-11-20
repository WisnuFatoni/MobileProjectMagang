package service

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"io"
	"strings"

	"fai26-api/internal/apperror"
	"fai26-api/internal/auth"
	"fai26-api/internal/model"
	"fai26-api/internal/repository"
)

// MaksFotoByte adalah batas ukuran satu foto.
const MaksFotoByte = 8 << 20

type MediaRequest struct {
	PanenID string
	FotoID  string
	SHA256  string
	Jenis   string // ASLI atau DETEKSI; kosong dianggap DETEKSI
}

type MediaService struct {
	panen PanenRepo
	foto  FotoRepo
	store FileStore
	akses *AksesService
}

func NewMediaService(panen PanenRepo, foto FotoRepo, store FileStore, akses *AksesService) *MediaService {
	return &MediaService{panen: panen, foto: foto, store: store, akses: akses}
}

// Terima menyimpan satu foto JPEG milik sebuah catatan. Idempoten menurut FotoID.
func (s *MediaService) Terima(ctx context.Context, k *auth.Klaim, req MediaRequest, isi io.Reader) (string, error) {
	req.PanenID = strings.ToLower(req.PanenID)
	req.FotoID = strings.ToLower(req.FotoID)
	req.SHA256 = strings.ToLower(req.SHA256)
	if req.Jenis == "" {
		req.Jenis = "DETEKSI"
	}
	if !model.ReUUID.MatchString(req.PanenID) || !model.ReUUID.MatchString(req.FotoID) || len(req.SHA256) != 64 ||
		(req.Jenis != "ASLI" && req.Jenis != "DETEKSI") {
		return "", apperror.BadRequest("id panen, X-Foto-Id (UUID), X-Sha256 (64 hex), dan jenis (ASLI atau DETEKSI) wajib benar")
	}

	pemilik, err := s.panen.PemilikID(ctx, req.PanenID)
	if errors.Is(err, repository.ErrNotFound) {
		return "", apperror.NotFound("Catatan panen belum ada. Kirim catatan dulu")
	}
	if err != nil {
		return "", apperror.Internal("Gagal membaca catatan", err)
	}
	if pemilik != k.UserID {
		boleh, err := s.akses.Punya(ctx, k.UserID, permKoreksi)
		if err != nil {
			return "", apperror.Internal("Gagal memeriksa hak akses", err)
		}
		if !boleh {
			return "", apperror.Forbidden("Bukan catatan Anda")
		}
	}
	sudah, err := s.foto.Ada(ctx, req.FotoID)
	if err != nil {
		return "", apperror.Internal("Gagal memeriksa foto", err)
	}
	if sudah {
		return StatusSudahAda, nil
	}

	data, err := io.ReadAll(io.LimitReader(isi, MaksFotoByte+1))
	if err != nil || len(data) == 0 {
		return "", apperror.BadRequest("Isi foto kosong atau gagal dibaca")
	}
	if len(data) > MaksFotoByte {
		return "", apperror.TooLarge("Foto melebihi 8 MB")
	}
	h := sha256.Sum256(data)
	if hex.EncodeToString(h[:]) != req.SHA256 {
		return "", apperror.Unprocessable("Checksum tidak cocok. Kirim ulang")
	}
	if len(data) < 3 || data[0] != 0xFF || data[1] != 0xD8 {
		return "", apperror.UnsupportedMedia("Hanya JPEG yang diterima")
	}

	path, err := s.store.Simpan(req.PanenID, req.FotoID+".jpg", data)
	if err != nil {
		return "", apperror.Internal("Gagal menyimpan foto", err)
	}
	if err := s.foto.Simpan(ctx, model.Foto{ID: req.FotoID, PanenID: req.PanenID, Jenis: req.Jenis,
		NamaFile: path, SHA256: req.SHA256, UserID: k.UserID}); err != nil {
		_ = s.store.Hapus(path)
		return "", apperror.Internal("Gagal mencatat foto", err)
	}
	return StatusDiterima, nil
}
