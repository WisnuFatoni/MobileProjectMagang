package repository

import (
	"context"
	"crypto/sha256"
	"database/sql"
	"encoding/hex"
	"errors"
	"fmt"
	"strings"
	"time"

	"fai26-api/internal/model"
)

type MasterRepository struct{ db *sql.DB }

// Pembatas master per akun mandor (@p2 = kode HRMB mandor, @p3 = kode afdeling; NULL berarti tanpa batas).
const (
	filterBlok  = "(@p3 IS NULL OR divisi_id IN (SELECT divisi_id FROM dbo.divisi WHERE unit_id=@p1 AND kode=@p3))"
	filterBlokB = "(@p3 IS NULL OR b.divisi_id IN (SELECT divisi_id FROM dbo.divisi WHERE unit_id=@p1 AND kode=@p3))"
)

// Lingkup membaca batas master sebuah akun dari app_user (akun tanpa kode mandor/afdeling tidak dibatasi).
func (r *MasterRepository) Lingkup(ctx context.Context, userID int64) (model.LingkupMaster, error) {
	var mandor sql.NullInt64
	var afdeling sql.NullString
	err := r.db.QueryRowContext(ctx, `SELECT mandor_hrmb_code, afdeling_kode FROM dbo.app_user WHERE user_id=@p1`, userID).Scan(&mandor, &afdeling)
	if errors.Is(err, sql.ErrNoRows) {
		return model.LingkupMaster{}, nil
	}
	return model.LingkupMaster{MandorHrmb: int64Ptr(mandor), Afdeling: afdeling.String}, err
}

func NewMasterRepository(db *sql.DB) *MasterRepository { return &MasterRepository{db: db} }

// Versi menghitung versi master satu unit dari isi tabelnya: jumlah baris dan waktu perubahan terbaru
// (dihapus, diubah, atau dibuat) tiap tabel. Hasilnya berubah bila ada baris ditambah, diubah, atau dihapus
// lunak, dengan syarat setiap perubahan mengisi diubah_pada (aturan semua alat yang mengubah master).
func (r *MasterRepository) Versi(ctx context.Context, unitID int64, l model.LingkupMaster) (string, error) {
	mandor, afdeling := ptrAny(l.MandorHrmb), nullStr(l.Afdeling)
	rows, err := r.db.QueryContext(ctx, `
SELECT 'unit', COUNT(*), CONVERT(NVARCHAR(30), MAX(COALESCE(dihapus_pada, diubah_pada, dibuat_pada)), 126) FROM dbo.unit WHERE unit_id=@p1
UNION ALL SELECT 'divisi', COUNT(*), CONVERT(NVARCHAR(30), MAX(COALESCE(dihapus_pada, diubah_pada, dibuat_pada)), 126) FROM dbo.divisi WHERE unit_id=@p1 AND (@p3 IS NULL OR kode=@p3)
UNION ALL SELECT 'blok', COUNT(*), CONVERT(NVARCHAR(30), MAX(COALESCE(dihapus_pada, diubah_pada, dibuat_pada)), 126) FROM dbo.blok WHERE unit_id=@p1 AND `+filterBlok+`
UNION ALL SELECT 'tph', COUNT(*), CONVERT(NVARCHAR(30), MAX(COALESCE(t.dihapus_pada, t.diubah_pada, t.dibuat_pada)), 126)
  FROM dbo.tph t JOIN dbo.blok b ON b.blok_id = t.blok_id WHERE b.unit_id=@p1 AND `+filterBlokB+`
UNION ALL SELECT 'pemanen', COUNT(*), CONVERT(NVARCHAR(30), MAX(COALESCE(dihapus_pada, diubah_pada, dibuat_pada)), 126) FROM dbo.pemanen WHERE unit_id=@p1 AND (@p2 IS NULL OR mandor_hrmb_code=@p2)
UNION ALL SELECT 'user', COUNT(*), CONVERT(NVARCHAR(30), MAX(COALESCE(dihapus_pada, diubah_pada, dibuat_pada)), 126) FROM dbo.app_user WHERE unit_id=@p1
UNION ALL SELECT 'user_role', COUNT(*), CONVERT(NVARCHAR(30), MAX(COALESCE(ur.dihapus_pada, ur.diubah_pada, ur.dibuat_pada)), 126)
  FROM dbo.user_role ur JOIN dbo.app_user u ON u.user_id = ur.user_id WHERE u.unit_id=@p1
UNION ALL SELECT 'role', COUNT(*), CONVERT(NVARCHAR(30), MAX(COALESCE(dihapus_pada, diubah_pada, dibuat_pada)), 126) FROM dbo.role
UNION ALL SELECT 'permission', COUNT(*), CONVERT(NVARCHAR(30), MAX(COALESCE(dihapus_pada, diubah_pada, dibuat_pada)), 126) FROM dbo.permission
UNION ALL SELECT 'role_permission', COUNT(*), CONVERT(NVARCHAR(30), MAX(COALESCE(dihapus_pada, diubah_pada, dibuat_pada)), 126) FROM dbo.role_permission`, unitID, mandor, afdeling)
	if err != nil {
		return "", err
	}
	defer rows.Close()
	var b strings.Builder
	fmt.Fprintf(&b, "lingkup:%v:%s|", mandor, l.Afdeling)
	for rows.Next() {
		var nama string
		var n int
		var terbaru sql.NullString
		if err := rows.Scan(&nama, &n, &terbaru); err != nil {
			return "", err
		}
		fmt.Fprintf(&b, "%s:%d:%s|", nama, n, terbaru.String)
	}
	if err := rows.Err(); err != nil {
		return "", err
	}
	h := sha256.Sum256([]byte(b.String()))
	return "m-" + hex.EncodeToString(h[:8]), nil
}

// Ambil membaca seluruh master satu unit, termasuk baris yang sudah dihapus lunak (HP menandainya, bukan membuangnya).
func (r *MasterRepository) Ambil(ctx context.Context, unitID int64, l model.LingkupMaster) (*model.IsiMaster, error) {
	mandor, afdeling := ptrAny(l.MandorHrmb), nullStr(l.Afdeling)
	const waktu = "dibuat_pada, diubah_pada, dihapus_pada"
	out := &model.IsiMaster{}

	unit, err := r.baris(ctx, "SELECT unit_id, kode, nama, erp_db, tahun_cluster, aktif, "+waktu+" FROM dbo.unit WHERE unit_id=@p1", unitID)
	if err != nil {
		return nil, err
	}
	if len(unit) > 0 {
		out.Unit = unit[0]
	}
	if out.Divisi, err = r.baris(ctx, "SELECT divisi_id, unit_id, erp_oid, kode, nama, aktif, "+waktu+" FROM dbo.divisi WHERE unit_id=@p1 AND (@p3 IS NULL OR kode=@p3) ORDER BY divisi_id", unitID, mandor, afdeling); err != nil {
		return nil, err
	}
	if out.Blok, err = r.baris(ctx, "SELECT blok_id, unit_id, divisi_id, erp_oid, kode, nama, luas_ha, tahun_tanam, sph, aktif, "+waktu+" FROM dbo.blok WHERE unit_id=@p1 AND "+filterBlok+" ORDER BY blok_id", unitID, mandor, afdeling); err != nil {
		return nil, err
	}
	if out.Tph, err = r.baris(ctx, `SELECT t.tph_id, t.blok_id, t.no_tph, t.qr_kode, t.lat, t.lon, t.aktif, t.dibuat_pada, t.diubah_pada, t.dihapus_pada
FROM dbo.tph t JOIN dbo.blok b ON b.blok_id = t.blok_id WHERE b.unit_id=@p1 AND `+filterBlokB+` ORDER BY t.tph_id`, unitID, mandor, afdeling); err != nil {
		return nil, err
	}
	if out.Pemanen, err = r.baris(ctx, "SELECT pemanen_id, unit_id, erp_hrmb_code, kode, nama, aktif, "+waktu+" FROM dbo.pemanen WHERE unit_id=@p1 AND (@p2 IS NULL OR mandor_hrmb_code=@p2) ORDER BY pemanen_id", unitID, mandor, afdeling); err != nil {
		return nil, err
	}
	if out.User, err = r.user(ctx, unitID); err != nil {
		return nil, err
	}
	if out.RolePermission, err = r.rolePermission(ctx); err != nil {
		return nil, err
	}
	return out, nil
}

// rolePermission membaca hak semua role yang masih berlaku (role, permission, dan pasangannya belum dihapus).
func (r *MasterRepository) rolePermission(ctx context.Context) ([]model.RoleIzin, error) {
	rows, err := r.db.QueryContext(ctx, `
SELECT r.kode, p.kode FROM dbo.role_permission rp
JOIN dbo.role r ON r.role_id = rp.role_id AND r.dihapus_pada IS NULL
JOIN dbo.permission p ON p.permission_id = rp.permission_id AND p.dihapus_pada IS NULL
WHERE rp.dihapus_pada IS NULL
ORDER BY r.kode, p.kode`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []model.RoleIzin
	for rows.Next() {
		var role, izin string
		if err := rows.Scan(&role, &izin); err != nil {
			return nil, err
		}
		if n := len(out); n == 0 || out[n-1].Role != role {
			out = append(out, model.RoleIzin{Role: role, Permission: []string{}})
		}
		out[len(out)-1].Permission = append(out[len(out)-1].Permission, izin)
	}
	return out, rows.Err()
}

func (r *MasterRepository) user(ctx context.Context, unitID int64) ([]model.MasterUser, error) {
	rows, err := r.db.QueryContext(ctx,
		`SELECT user_id, unit_id, username, nama, aktif, erp_user_oid, dihapus_pada, mandor_hrmb_code, afdeling_kode FROM dbo.app_user WHERE unit_id=@p1 ORDER BY user_id`, unitID)
	if err != nil {
		return nil, err
	}
	var out []model.MasterUser
	idx := map[int64]int{}
	for rows.Next() {
		var u model.MasterUser
		var oid, mandor sql.NullInt64
		var afdeling sql.NullString
		var hapus sql.NullTime
		if err := rows.Scan(&u.UserID, &u.UnitID, &u.Username, &u.Nama, &u.Aktif, &oid, &hapus, &mandor, &afdeling); err != nil {
			rows.Close()
			return nil, err
		}
		if oid.Valid {
			v := oid.Int64
			u.ErpUserOid = &v
		}
		u.MandorHrmbCode, u.AfdelingKode = int64Ptr(mandor), strPtr(afdeling)
		if hapus.Valid {
			s := formatWaktu(hapus.Time)
			u.DihapusPada = &s
		}
		u.Role = []string{}
		idx[u.UserID] = len(out)
		out = append(out, u)
	}
	if err := rows.Err(); err != nil {
		rows.Close()
		return nil, err
	}
	rows.Close()

	rr, err := r.db.QueryContext(ctx, `
SELECT ur.user_id, r.kode FROM dbo.user_role ur
JOIN dbo.role r ON r.role_id = ur.role_id AND r.dihapus_pada IS NULL
JOIN dbo.app_user u ON u.user_id = ur.user_id
WHERE u.unit_id=@p1 AND ur.dihapus_pada IS NULL
  AND (ur.berlaku_mulai IS NULL OR ur.berlaku_mulai <= CAST(SYSUTCDATETIME() AS DATE))
  AND (ur.berlaku_sampai IS NULL OR ur.berlaku_sampai >= CAST(SYSUTCDATETIME() AS DATE))
ORDER BY ur.user_id, r.role_id`, unitID)
	if err != nil {
		return nil, err
	}
	defer rr.Close()
	for rr.Next() {
		var uid int64
		var kode string
		if err := rr.Scan(&uid, &kode); err != nil {
			return nil, err
		}
		if i, ok := idx[uid]; ok {
			out[i].Role = append(out[i].Role, kode)
		}
	}
	return out, rr.Err()
}

// baris menjalankan query dan mengembalikan tiap baris sebagai peta kolom ke nilai JSON-able.
func (r *MasterRepository) baris(ctx context.Context, query string, args ...any) ([]model.BarisMaster, error) {
	rows, err := r.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	kolom, err := rows.Columns()
	if err != nil {
		return nil, err
	}
	var out []model.BarisMaster
	for rows.Next() {
		nilai := make([]any, len(kolom))
		penunjuk := make([]any, len(kolom))
		for i := range nilai {
			penunjuk[i] = &nilai[i]
		}
		if err := rows.Scan(penunjuk...); err != nil {
			return nil, err
		}
		b := make(model.BarisMaster, len(kolom))
		for i, k := range kolom {
			b[k] = jsonable(nilai[i])
		}
		out = append(out, b)
	}
	return out, rows.Err()
}

func formatWaktu(t time.Time) string { return t.UTC().Format("2006-01-02T15:04:05Z") }

func jsonable(v any) any {
	switch x := v.(type) {
	case time.Time:
		return formatWaktu(x)
	case []byte:
		return string(x)
	default:
		return v
	}
}
