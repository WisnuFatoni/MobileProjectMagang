package repository

import (
	"context"
	"database/sql"
	"errors"
)

// FotoProfilRepository menyimpan versi foto profil akun (waktu ubah, milidetik). Berkas fotonya ada di penyimpanan media.
type FotoProfilRepository struct{ db *sql.DB }

func NewFotoProfilRepository(db *sql.DB) *FotoProfilRepository { return &FotoProfilRepository{db: db} }

// Versi membaca versi foto akun. Nil bila akun belum punya foto. ErrNotFound bila akunnya tidak ada.
func (r *FotoProfilRepository) Versi(ctx context.Context, userID int64) (*int64, error) {
	var v sql.NullInt64
	err := r.db.QueryRowContext(ctx, `SELECT foto_profil_versi FROM dbo.app_user WHERE user_id=@p1 AND dihapus_pada IS NULL`, userID).Scan(&v)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrNotFound
	}
	if err != nil {
		return nil, err
	}
	return int64Ptr(v), nil
}

// Atur menetapkan versi foto (nil = foto dihapus) dan mencatat audit, dalam satu transaksi.
func (r *FotoProfilRepository) Atur(ctx context.Context, userID int64, versi *int64) error {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	res, err := tx.ExecContext(ctx, `
UPDATE dbo.app_user SET foto_profil_versi=@p2, diubah_pada=SYSUTCDATETIME(), diubah_oleh=@p1
WHERE user_id=@p1 AND dihapus_pada IS NULL`, userID, ptrAny(versi))
	if err != nil {
		return err
	}
	if n, _ := res.RowsAffected(); n != 1 {
		return ErrNotFound
	}
	aksi := "FOTO_PROFIL_UBAH"
	if versi == nil {
		aksi = "FOTO_PROFIL_HAPUS"
	}
	if err := catat(ctx, tx, "app_user", userID, aksi, userID, "foto profil"); err != nil {
		return err
	}
	return tx.Commit()
}
