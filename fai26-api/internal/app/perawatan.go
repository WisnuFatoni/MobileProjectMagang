package app

import (
	"context"
	"database/sql"
	"log"
	"time"

	"fai26-api/internal/config"
	"fai26-api/internal/repository"
	"fai26-api/internal/service"
	"fai26-api/internal/storage"
)

// selangPerawatan adalah jarak antar pemeriksaan kebijakan penyimpanan. Pembersihan bersifat idempoten, jadi aman diulang
// dan aman bila server sering dimatikan (tidak bergantung pada jam tertentu).
const selangPerawatan = 6 * time.Hour

// JalankanPerawatan menjalankan tugas latar sampai ctx dibatalkan: saat ini hanya kebijakan penyimpanan jalur GPS
// (FAI26_JEJAK_SIMPAN_HARI). Bila kebijakan tidak aktif (0), fungsi langsung kembali dan tidak ada yang dihapus.
func JalankanPerawatan(ctx context.Context, cfg config.Config, db *sql.DB) {
	if cfg.JejakSimpanHari <= 0 {
		log.Printf("perawatan: jalur GPS disimpan tanpa batas (FAI26_JEJAK_SIMPAN_HARI=0)")
		return
	}
	ipds := repository.NewIpdRepository(db)
	svc := service.NewIpdService(ipds, repository.NewUnitRepository(db), service.NewAksesService(repository.NewUserRepository(db)), storage.NewFileStorage(cfg.MediaDir))
	log.Printf("perawatan: jalur GPS inspeksi yang sudah diperiksa dihapus setelah %d hari", cfg.JejakSimpanHari)
	for {
		h, err := svc.BersihkanJejak(ctx, cfg.JejakSimpanHari)
		switch {
		case err != nil:
			log.Printf("perawatan: pembersihan jalur GPS gagal: %v", err)
		case h.Titik > 0:
			log.Printf("perawatan: %d titik jalur GPS dari %d inspeksi dihapus", h.Titik, h.Inspeksi)
		}
		select {
		case <-ctx.Done():
			return
		case <-time.After(selangPerawatan):
		}
	}
}
