package repository

import (
	"context"
	"database/sql"

	"fai26-api/internal/model"
)

type AuditRepository struct{ db *sql.DB }

func NewAuditRepository(db *sql.DB) *AuditRepository { return &AuditRepository{db: db} }

type execer interface {
	ExecContext(ctx context.Context, query string, args ...any) (sql.Result, error)
}

// insertAudit menulis satu baris audit_log (tabel ini hanya boleh INSERT) memakai db atau transaksi.
func insertAudit(ctx context.Context, e execer, a model.AuditBaris) error {
	var uid any
	if a.UserID != nil {
		uid = *a.UserID
	}
	_, err := e.ExecContext(ctx, `
INSERT INTO dbo.audit_log (audit_id, tabel, baris_id, aksi, user_id, username, device_id, waktu, waktu_server, ip, keterangan)
VALUES (NEWID(), @p1, @p2, @p3, @p4, @p5, @p6, SYSUTCDATETIME(), SYSUTCDATETIME(), @p7, @p8)`,
		a.Tabel, a.BarisID, a.Aksi, uid, nullStr(a.Username), nullStr(a.DeviceID), nullStr(a.IP), nullStr(a.Keterangan))
	return err
}

// Tulis menambah satu baris audit milik server.
func (r *AuditRepository) Tulis(ctx context.Context, a model.AuditBaris) error {
	return insertAudit(ctx, r.db, a)
}

// TulisBatch menyimpan audit dari HP dalam satu transaksi. Baris yang audit_id-nya sudah ada dilewati.
// user_id dan device_id dari token, bukan dari isi kiriman.
func (r *AuditRepository) TulisBatch(ctx context.Context, userID int64, deviceID, ip string, items []model.AuditSimpan) (int, error) {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return 0, err
	}
	defer tx.Rollback()
	diterima := 0
	for _, it := range items {
		a := it.Input
		res, err := tx.ExecContext(ctx, `
INSERT INTO dbo.audit_log (audit_id, tabel, baris_id, aksi, nilai_lama, nilai_baru, user_id, username, device_id, waktu, waktu_server, ip, keterangan)
SELECT @p1,@p2,@p3,@p4,@p5,@p6,@p7,@p8,@p9,@p10,SYSUTCDATETIME(),@p11,@p12
WHERE NOT EXISTS (SELECT 1 FROM dbo.audit_log WHERE audit_id=@p1)`,
			a.AuditID, a.Tabel, a.BarisID, a.Aksi, ptrAny(a.NilaiLama), ptrAny(a.NilaiBaru), userID, nullStr(a.Username), deviceID,
			it.Waktu, nullStr(ip), nullStr(a.Keterangan))
		if err != nil {
			return 0, err
		}
		if n, _ := res.RowsAffected(); n > 0 {
			diterima++
		}
	}
	return diterima, tx.Commit()
}
