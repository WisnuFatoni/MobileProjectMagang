package repository

import (
	"context"
	"database/sql"
	"errors"
	"time"

	"fai26-api/internal/model"
)

type SessionRepository struct{ db *sql.DB }

func NewSessionRepository(db *sql.DB) *SessionRepository { return &SessionRepository{db: db} }

func (r *SessionRepository) Buat(ctx context.Context, userID int64, deviceID, hashRefresh string, ttl time.Duration, ip string) error {
	_, err := r.db.ExecContext(ctx, `
INSERT INTO dbo.auth_sesi (sesi_id, user_id, device_id, refresh_token_hash, kedaluwarsa_pada, ip, dibuat_oleh)
VALUES (NEWID(), @p1, @p2, @p3, DATEADD(SECOND,@p4,SYSUTCDATETIME()), @p5, @p1)`,
		userID, deviceID, hashRefresh, int(ttl.Seconds()), ip)
	return err
}

// CariAktif mencari sesi dari hash refresh token: belum dicabut, belum kedaluwarsa, akun dan perangkat masih aktif.
func (r *SessionRepository) CariAktif(ctx context.Context, hashRefresh string) (*model.Sesi, error) {
	var s model.Sesi
	err := r.db.QueryRowContext(ctx, `
SELECT CONVERT(NVARCHAR(36), a.sesi_id), a.user_id, u.unit_id, CONVERT(NVARCHAR(36), a.device_id)
FROM dbo.auth_sesi a JOIN dbo.app_user u ON u.user_id = a.user_id
WHERE a.refresh_token_hash=@p1 AND a.dicabut_pada IS NULL AND a.kedaluwarsa_pada > SYSUTCDATETIME()
  AND u.aktif=1 AND u.dihapus_pada IS NULL
  AND EXISTS (SELECT 1 FROM dbo.device d WHERE d.device_id = a.device_id AND d.aktif=1)`, hashRefresh).
		Scan(&s.ID, &s.UserID, &s.UnitID, &s.DeviceID)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrNotFound
	}
	return &s, err
}

// Rotasi mengganti hash refresh token dan memperpanjang masa berlaku sesi.
func (r *SessionRepository) Rotasi(ctx context.Context, sesiID, hashBaru string, ttl time.Duration) error {
	_, err := r.db.ExecContext(ctx, `
UPDATE dbo.auth_sesi SET refresh_token_hash=@p2, kedaluwarsa_pada=DATEADD(SECOND,@p3,SYSUTCDATETIME()),
  diubah_pada=SYSUTCDATETIME() WHERE sesi_id=@p1`, sesiID, hashBaru, int(ttl.Seconds()))
	return err
}

func (r *SessionRepository) Cabut(ctx context.Context, userID int64, deviceID string) error {
	_, err := r.db.ExecContext(ctx, `
UPDATE dbo.auth_sesi SET dicabut_pada=SYSUTCDATETIME()
WHERE user_id=@p1 AND device_id=@p2 AND dicabut_pada IS NULL`, userID, deviceID)
	return err
}
