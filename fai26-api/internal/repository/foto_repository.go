package repository

import (
	"context"
	"database/sql"

	"fai26-api/internal/model"
)

type FotoRepository struct{ db *sql.DB }

func NewFotoRepository(db *sql.DB) *FotoRepository { return &FotoRepository{db: db} }

func (r *FotoRepository) Ada(ctx context.Context, fotoID string) (bool, error) {
	var n int
	err := r.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM dbo.fai_foto WHERE id=@p1`, fotoID).Scan(&n)
	return n > 0, err
}

func (r *FotoRepository) Simpan(ctx context.Context, f model.Foto) error {
	_, err := r.db.ExecContext(ctx, `
INSERT INTO dbo.fai_foto (id, panen_id, jenis, nama_file, sha256, dibuat_oleh) VALUES (@p1,@p2,@p3,@p4,@p5,@p6)`,
		f.ID, f.PanenID, f.Jenis, f.NamaFile, f.SHA256, f.UserID)
	return err
}
