package repository

import (
	"context"
	"database/sql"
	"errors"
)

type UnitRepository struct{ db *sql.DB }

func NewUnitRepository(db *sql.DB) *UnitRepository { return &UnitRepository{db: db} }

// IDDariKode mengembalikan unit_id unit aktif, atau ErrNotFound.
func (r *UnitRepository) IDDariKode(ctx context.Context, kode string) (int64, error) {
	var id int64
	err := r.db.QueryRowContext(ctx,
		`SELECT unit_id FROM dbo.unit WHERE kode=@p1 AND aktif=1 AND dihapus_pada IS NULL`, kode).Scan(&id)
	if errors.Is(err, sql.ErrNoRows) {
		return 0, ErrNotFound
	}
	return id, err
}
