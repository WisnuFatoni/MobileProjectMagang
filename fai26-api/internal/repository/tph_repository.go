package repository

import (
	"context"
	"database/sql"
	"errors"
	"fmt"

	"fai26-api/internal/model"
)

// TphRepository menangani perekaman koordinat TPH dari HP.
type TphRepository struct{ db *sql.DB }

func NewTphRepository(db *sql.DB) *TphRepository { return &TphRepository{db: db} }

// RekamKoordinat mengisi koordinat TPH hanya bila belum ada (titik yang sudah benar tidak boleh tertimpa titik
// asal-asalan; mengubahnya lewat web admin). Atomik: UPDATE bersyarat lalu, bila tidak ada baris berubah,
// membaca titik yang sudah ada. Berhasil mengisi mengubah diubah_pada sehingga versi master berubah dan HP lain
// ikut mengunduh titiknya. TPH harus milik unit pengguna dan belum dihapus, kalau tidak ErrNotFound.
func (r *TphRepository) RekamKoordinat(ctx context.Context, unitID, userID, tphID int64, lat, lon, akurasiM float64) (model.KoordinatHasil, error) {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return model.KoordinatHasil{}, err
	}
	defer tx.Rollback()

	res, err := tx.ExecContext(ctx, `
UPDATE t SET lat=@p3, lon=@p4, diubah_pada=SYSUTCDATETIME(), diubah_oleh=@p5
FROM dbo.tph t JOIN dbo.blok b ON b.blok_id=t.blok_id
WHERE t.tph_id=@p1 AND b.unit_id=@p2 AND t.dihapus_pada IS NULL AND (t.lat IS NULL OR t.lon IS NULL)`,
		tphID, unitID, lat, lon, userID)
	if err != nil {
		return model.KoordinatHasil{}, err
	}
	n, err := res.RowsAffected()
	if err != nil {
		return model.KoordinatHasil{}, err
	}
	if n == 1 {
		ket := fmt.Sprintf("koordinat direkam dari HP: lat=%.6f lon=%.6f akurasi=%.0f m", lat, lon, akurasiM)
		if err := catat(ctx, tx, "tph", tphID, "UBAH", userID, ket); err != nil {
			return model.KoordinatHasil{}, err
		}
		if err := tx.Commit(); err != nil {
			return model.KoordinatHasil{}, err
		}
		return model.KoordinatHasil{Diterapkan: true, Lat: lat, Lon: lon}, nil
	}

	// Tidak ada baris berubah: TPH tidak ada di unit ini, atau sudah punya koordinat lengkap.
	var ada, ada2 sql.NullFloat64
	err = tx.QueryRowContext(ctx, `
SELECT t.lat, t.lon FROM dbo.tph t JOIN dbo.blok b ON b.blok_id=t.blok_id
WHERE t.tph_id=@p1 AND b.unit_id=@p2 AND t.dihapus_pada IS NULL`, tphID, unitID).Scan(&ada, &ada2)
	if errors.Is(err, sql.ErrNoRows) {
		return model.KoordinatHasil{}, ErrNotFound
	}
	if err != nil {
		return model.KoordinatHasil{}, err
	}
	return model.KoordinatHasil{Diterapkan: false, Lat: ada.Float64, Lon: ada2.Float64}, nil
}
