package repository

import (
	"context"
	"database/sql"
	"errors"

	"fai26-api/internal/model"
)

type DeviceRepository struct{ db *sql.DB }

func NewDeviceRepository(db *sql.DB) *DeviceRepository { return &DeviceRepository{db: db} }

// Daftarkan mendaftarkan HP baru atau memperbarui yang lama. HP yang dinonaktifkan admin mengembalikan ErrPerangkatNonaktif.
func (r *DeviceRepository) Daftarkan(ctx context.Context, d model.Perangkat, userID int64) error {
	var aktif bool
	err := r.db.QueryRowContext(ctx, `SELECT aktif FROM dbo.device WHERE device_id=@p1`, d.ID).Scan(&aktif)
	switch {
	case errors.Is(err, sql.ErrNoRows):
		_, err = r.db.ExecContext(ctx, `
INSERT INTO dbo.device (device_id, nama, os_versi, app_versi, terakhir_sinkron, dibuat_oleh)
VALUES (@p1, @p2, @p3, @p4, SYSUTCDATETIME(), @p5)`, d.ID, nullStr(d.Nama), nullStr(d.OSVersi), nullStr(d.AppVersi), userID)
		return err
	case err != nil:
		return err
	case !aktif:
		return ErrPerangkatNonaktif
	}
	_, err = r.db.ExecContext(ctx, `
UPDATE dbo.device SET nama=COALESCE(@p2,nama), os_versi=COALESCE(@p3,os_versi), app_versi=COALESCE(@p4,app_versi),
  terakhir_sinkron=SYSUTCDATETIME(), diubah_pada=SYSUTCDATETIME() WHERE device_id=@p1`,
		d.ID, nullStr(d.Nama), nullStr(d.OSVersi), nullStr(d.AppVersi))
	return err
}
