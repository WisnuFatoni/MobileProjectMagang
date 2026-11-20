package repository

import (
	"context"
	"database/sql"
	"errors"

	"fai26-api/internal/model"
)

type UserRepository struct{ db *sql.DB }

func NewUserRepository(db *sql.DB) *UserRepository { return &UserRepository{db: db} }

func (r *UserRepository) CariByUsername(ctx context.Context, username string) (*model.User, error) {
	var u model.User
	var terkunci int
	var mandor sql.NullInt64
	var afdeling sql.NullString
	err := r.db.QueryRowContext(ctx, `
SELECT u.user_id, u.unit_id, n.kode, u.username, u.nama, u.password_hash, u.aktif, u.gagal_login,
       CASE WHEN u.terkunci_sampai > SYSUTCDATETIME() THEN 1 ELSE 0 END, u.wajib_ganti_password,
       u.mandor_hrmb_code, u.afdeling_kode
FROM dbo.app_user u JOIN dbo.unit n ON n.unit_id = u.unit_id
WHERE u.username=@p1 AND u.dihapus_pada IS NULL`, username).
		Scan(&u.ID, &u.UnitID, &u.UnitKode, &u.Username, &u.Nama, &u.PasswordHash, &u.Aktif, &u.GagalLogin, &terkunci, &u.WajibGanti, &mandor, &afdeling)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrNotFound
	}
	u.Terkunci = terkunci == 1
	u.MandorHrmbCode, u.AfdelingKode = int64Ptr(mandor), strPtr(afdeling)
	return &u, err
}

// Profil membaca data profil dasar (tanpa role dan permission).
func (r *UserRepository) Profil(ctx context.Context, userID int64) (*model.Profil, error) {
	p := model.Profil{UserID: userID}
	var mandor sql.NullInt64
	var afdeling sql.NullString
	err := r.db.QueryRowContext(ctx, `
SELECT u.unit_id, n.kode, u.username, u.nama, u.wajib_ganti_password, u.mandor_hrmb_code, u.afdeling_kode
FROM dbo.app_user u JOIN dbo.unit n ON n.unit_id = u.unit_id WHERE u.user_id=@p1`, userID).
		Scan(&p.UnitID, &p.UnitKode, &p.Username, &p.Nama, &p.WajibGanti, &mandor, &afdeling)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrNotFound
	}
	p.MandorHrmbCode, p.AfdelingKode = int64Ptr(mandor), strPtr(afdeling)
	return &p, err
}

func (r *UserRepository) Aktif(ctx context.Context, userID int64) (bool, error) {
	var n int
	err := r.db.QueryRowContext(ctx,
		`SELECT COUNT(*) FROM dbo.app_user WHERE user_id=@p1 AND aktif=1 AND dihapus_pada IS NULL`, userID).Scan(&n)
	return n > 0, err
}

// IDDariUsername mengembalikan user_id akun aktif, atau ErrNotFound.
func (r *UserRepository) IDDariUsername(ctx context.Context, username string) (int64, error) {
	var id int64
	err := r.db.QueryRowContext(ctx,
		`SELECT user_id FROM dbo.app_user WHERE username=@p1 AND aktif=1 AND dihapus_pada IS NULL`, username).Scan(&id)
	if errors.Is(err, sql.ErrNoRows) {
		return 0, ErrNotFound
	}
	return id, err
}

// CatatGagal menaikkan hitungan gagal login. Bila kunciDetik > 0 akun dikunci sebanyak itu dan hitungan di-nol-kan.
func (r *UserRepository) CatatGagal(ctx context.Context, userID int64, gagal int, kunciDetik int) error {
	if kunciDetik > 0 {
		_, err := r.db.ExecContext(ctx,
			`UPDATE dbo.app_user SET gagal_login=0, terkunci_sampai=DATEADD(SECOND,@p2,SYSUTCDATETIME()) WHERE user_id=@p1`,
			userID, kunciDetik)
		return err
	}
	_, err := r.db.ExecContext(ctx, `UPDATE dbo.app_user SET gagal_login=@p2 WHERE user_id=@p1`, userID, gagal)
	return err
}

func (r *UserRepository) CatatLoginSukses(ctx context.Context, userID int64) error {
	_, err := r.db.ExecContext(ctx,
		`UPDATE dbo.app_user SET gagal_login=0, terkunci_sampai=NULL, login_terakhir=SYSUTCDATETIME() WHERE user_id=@p1`, userID)
	return err
}

// Permission mengumpulkan permission dari semua role yang masih berlaku hari ini.
func (r *UserRepository) Permission(ctx context.Context, userID int64) ([]string, error) {
	return r.daftar(ctx, `
SELECT DISTINCT p.kode FROM dbo.user_role ur
JOIN dbo.role_permission rp ON rp.role_id = ur.role_id AND rp.dihapus_pada IS NULL
JOIN dbo.permission p ON p.permission_id = rp.permission_id AND p.dihapus_pada IS NULL
WHERE ur.user_id=@p1 AND ur.dihapus_pada IS NULL
  AND (ur.berlaku_mulai IS NULL OR ur.berlaku_mulai <= CAST(SYSUTCDATETIME() AS DATE))
  AND (ur.berlaku_sampai IS NULL OR ur.berlaku_sampai >= CAST(SYSUTCDATETIME() AS DATE))
ORDER BY p.kode`, userID)
}

func (r *UserRepository) Role(ctx context.Context, userID int64) ([]string, error) {
	return r.daftar(ctx, `
SELECT r.kode FROM dbo.user_role ur JOIN dbo.role r ON r.role_id = ur.role_id
WHERE ur.user_id=@p1 AND ur.dihapus_pada IS NULL ORDER BY r.role_id`, userID)
}

func (r *UserRepository) daftar(ctx context.Context, q string, args ...any) ([]string, error) {
	rows, err := r.db.QueryContext(ctx, q, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []string{}
	for rows.Next() {
		var k string
		if err := rows.Scan(&k); err != nil {
			return nil, err
		}
		out = append(out, k)
	}
	return out, rows.Err()
}

// GantiPassword menyimpan hash baru, menghapus wajib_ganti_password, mereset hitungan gagal, dan mencabut sesi di
// perangkat lain (perangkat yang sedang dipakai tetap masuk), semuanya dalam satu transaksi.
func (r *UserRepository) GantiPassword(ctx context.Context, userID int64, hash, deviceIDSaatIni string) error {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	res, err := tx.ExecContext(ctx, `
UPDATE dbo.app_user SET password_hash=@p2, wajib_ganti_password=0, gagal_login=0, terkunci_sampai=NULL,
  diubah_pada=SYSUTCDATETIME(), diubah_oleh=@p1
WHERE user_id=@p1 AND dihapus_pada IS NULL`, userID, hash)
	if err != nil {
		return err
	}
	if n, _ := res.RowsAffected(); n == 0 {
		return ErrNotFound
	}
	if _, err := tx.ExecContext(ctx, `
UPDATE dbo.auth_sesi SET dicabut_pada=SYSUTCDATETIME()
WHERE user_id=@p1 AND dicabut_pada IS NULL AND device_id<>@p2`, userID, deviceIDSaatIni); err != nil {
		return err
	}
	return tx.Commit()
}
