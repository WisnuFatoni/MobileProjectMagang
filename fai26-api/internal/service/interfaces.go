// Package service berisi aturan bisnis. Service tidak menulis SQL (itu urusan repository) dan tidak
// mengenal HTTP (itu urusan controller). Ketergantungannya berupa interface supaya bisa diuji dengan tiruan.
package service

import (
	"context"
	"time"

	"fai26-api/internal/model"
)

type UserRepo interface {
	CariByUsername(ctx context.Context, username string) (*model.User, error)
	Profil(ctx context.Context, userID int64) (*model.Profil, error)
	Aktif(ctx context.Context, userID int64) (bool, error)
	IDDariUsername(ctx context.Context, username string) (int64, error)
	CatatGagal(ctx context.Context, userID int64, gagal, kunciDetik int) error
	CatatLoginSukses(ctx context.Context, userID int64) error
	Permission(ctx context.Context, userID int64) ([]string, error)
	Role(ctx context.Context, userID int64) ([]string, error)
	// GantiPassword mengganti hash, menghapus kewajiban ganti password, membuka kunci, dan mencabut sesi di semua
	// perangkat KECUALI perangkat yang sedang dipakai.
	GantiPassword(ctx context.Context, userID int64, hash, deviceIDSaatIni string) error
}

type DeviceRepo interface {
	Daftarkan(ctx context.Context, d model.Perangkat, userID int64) error
}

type SessionRepo interface {
	Buat(ctx context.Context, userID int64, deviceID, hashRefresh string, ttl time.Duration, ip string) error
	CariAktif(ctx context.Context, hashRefresh string) (*model.Sesi, error)
	Rotasi(ctx context.Context, sesiID, hashBaru string, ttl time.Duration) error
	Cabut(ctx context.Context, userID int64, deviceID string) error
}

type AuditRepo interface {
	Tulis(ctx context.Context, a model.AuditBaris) error
	TulisBatch(ctx context.Context, userID int64, deviceID, ip string, items []model.AuditSimpan) (int, error)
}

type PanenRepo interface {
	Ada(ctx context.Context, panenID string) (bool, error)
	// Cari mengembalikan status dan pemilik catatan, atau nil bila belum ada.
	Cari(ctx context.Context, panenID string) (*model.CatatanAda, error)
	PemilikID(ctx context.Context, panenID string) (int64, error)
	// CekMaster memeriksa blok (asm3_oid), TPH (nomor) dan pemanen (HRMBCODE) terhadap master unit.
	CekMaster(ctx context.Context, unitID, asm3Oid int64, tphKode string, hrmbCode int64) (model.MasterPanen, error)
	// AdaKombinasi: sudah ada catatan lain (bukan panenID itu) untuk tanggal + unit + blok + TPH + pemanen yang sama.
	AdaKombinasi(ctx context.Context, tanggal string, unitID, asm3Oid int64, tphKode string, hrmbCode int64, kecualiID string) (bool, error)
	Simpan(ctx context.Context, p model.PanenSimpan) error
	DaftarMenunggu(ctx context.Context, unitID int64) ([]model.PanenMenunggu, error)
	// Setujui dan Tolak memindahkan catatan MENUNGGU (ErrNotFound bila tidak ada; ErrStatusBerubah bila sudah diproses).
	Setujui(ctx context.Context, panenID string, unitID, userID int64, deviceID, ip string) (string, error)
	Tolak(ctx context.Context, panenID string, unitID, userID int64, alasan, deviceID, ip string) (string, error)
	DaftarStatus(ctx context.Context, unitID, userID int64, semua bool, sejak time.Time) ([]model.StatusPanen, error)
	Ringkasan(ctx context.Context, unitID, userID int64, semua bool, hariIni string) (*model.RingkasanPanen, error)
}

type UnitRepo interface {
	IDDariKode(ctx context.Context, kode string) (int64, error)
}

type FotoRepo interface {
	Ada(ctx context.Context, fotoID string) (bool, error)
	Simpan(ctx context.Context, f model.Foto) error
}

type FileStore interface {
	Simpan(folder, nama string, isi []byte) (string, error)
	Hapus(path string) error
}
