package repository

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"sort"
	"strings"

	"fai26-api/internal/model"
)

// ErrDilindungi berarti perubahan ditolak karena akan mengunci pengelolaan sistem (misalnya mencabut hak mengelola
// hak akses dari role ADMIN).
var ErrDilindungi = errors.New("perubahan ini dilindungi")

// HakAksesRepository membaca dan mengubah hak (permission) tiap role. Setiap perubahan mengisi diubah_pada (versi
// master untuk HP dihitung darinya), memakai hapus lunak, dan menulis audit_log.
type HakAksesRepository struct{ db *sql.DB }

func NewHakAksesRepository(db *sql.DB) *HakAksesRepository { return &HakAksesRepository{db: db} }

func (r *HakAksesRepository) Daftar(ctx context.Context) (*model.HakAkses, error) {
	out := &model.HakAkses{Role: []model.HakAksesRole{}, Permission: []model.IzinKatalog{}}

	rows, err := r.db.QueryContext(ctx, `
SELECT r.role_id, r.kode, r.nama, r.deskripsi, r.bawaan,
  (SELECT COUNT(*) FROM dbo.user_role ur JOIN dbo.app_user u ON u.user_id = ur.user_id AND u.dihapus_pada IS NULL AND u.aktif = 1
    WHERE ur.role_id = r.role_id AND ur.dihapus_pada IS NULL)
FROM dbo.role r WHERE r.dihapus_pada IS NULL ORDER BY r.role_id`)
	if err != nil {
		return nil, err
	}
	idx := map[int64]int{}
	for rows.Next() {
		var x model.HakAksesRole
		var desk sql.NullString
		if err := rows.Scan(&x.RoleID, &x.Kode, &x.Nama, &desk, &x.Bawaan, &x.Pengguna); err != nil {
			rows.Close()
			return nil, err
		}
		if desk.Valid {
			d := desk.String
			x.Deskripsi = &d
		}
		x.Permission = []string{}
		idx[x.RoleID] = len(out.Role)
		out.Role = append(out.Role, x)
	}
	if err := rows.Err(); err != nil {
		rows.Close()
		return nil, err
	}
	rows.Close()

	pr, err := r.db.QueryContext(ctx, `SELECT kode, modul, nama FROM dbo.permission WHERE dihapus_pada IS NULL ORDER BY modul, kode`)
	if err != nil {
		return nil, err
	}
	for pr.Next() {
		var x model.IzinKatalog
		if err := pr.Scan(&x.Kode, &x.Modul, &x.Nama); err != nil {
			pr.Close()
			return nil, err
		}
		out.Permission = append(out.Permission, x)
	}
	if err := pr.Err(); err != nil {
		pr.Close()
		return nil, err
	}
	pr.Close()

	rp, err := r.db.QueryContext(ctx, `
SELECT rp.role_id, p.kode FROM dbo.role_permission rp
JOIN dbo.permission p ON p.permission_id = rp.permission_id AND p.dihapus_pada IS NULL
WHERE rp.dihapus_pada IS NULL ORDER BY rp.role_id, p.kode`)
	if err != nil {
		return nil, err
	}
	defer rp.Close()
	for rp.Next() {
		var roleID int64
		var kode string
		if err := rp.Scan(&roleID, &kode); err != nil {
			return nil, err
		}
		if i, ok := idx[roleID]; ok {
			out.Role[i].Permission = append(out.Role[i].Permission, kode)
		}
	}
	return out, rp.Err()
}

// Ubah mengganti seluruh hak satu role dengan daftar kode yang diberikan, dalam satu transaksi: hak yang tidak lagi
// dicantumkan dihapus lunak, yang baru ditambahkan (atau dihidupkan lagi bila pernah dicabut).
//   - ErrNotFound bila role tidak ada.
//   - ErrReferensi bila ada kode permission yang tidak dikenal.
//   - ErrDilindungi bila role ADMIN akan kehilangan role.kelola (tidak ada lagi yang bisa mengelola hak akses).
func (r *HakAksesRepository) Ubah(ctx context.Context, aktorID, roleID int64, kode []string) error {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	if err := r.ubahTx(ctx, tx, aktorID, roleID, kode); err != nil {
		return err
	}
	return tx.Commit()
}

// ubahTx mengganti hak satu role di dalam transaksi yang sudah dibuka pemanggil (tanpa commit).
func (r *HakAksesRepository) ubahTx(ctx context.Context, tx *sql.Tx, aktorID, roleID int64, kode []string) error {
	var roleKode string
	if err := tx.QueryRowContext(ctx, `SELECT kode FROM dbo.role WHERE role_id=@p1 AND dihapus_pada IS NULL`, roleID).Scan(&roleKode); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return ErrNotFound
		}
		return err
	}
	if roleKode == "ADMIN" && !berisi(kode, "role.kelola") {
		return ErrDilindungi
	}

	// Katalog permission yang sah.
	pid := map[string]int64{}
	rows, err := tx.QueryContext(ctx, `SELECT permission_id, kode FROM dbo.permission WHERE dihapus_pada IS NULL`)
	if err != nil {
		return err
	}
	for rows.Next() {
		var id int64
		var k string
		if err := rows.Scan(&id, &k); err != nil {
			rows.Close()
			return err
		}
		pid[k] = id
	}
	if err := rows.Err(); err != nil {
		rows.Close()
		return err
	}
	rows.Close()
	target := map[int64]string{}
	for _, k := range kode {
		id, ok := pid[k]
		if !ok {
			return ErrReferensi
		}
		target[id] = k
	}

	// Keadaan sekarang: permission_id yang pernah ada, dan yang masih aktif.
	aktif := map[int64]bool{}
	ada := map[int64]bool{}
	cur, err := tx.QueryContext(ctx, `SELECT permission_id, CASE WHEN dihapus_pada IS NULL THEN 1 ELSE 0 END FROM dbo.role_permission WHERE role_id=@p1`, roleID)
	if err != nil {
		return err
	}
	for cur.Next() {
		var id int64
		var a int
		if err := cur.Scan(&id, &a); err != nil {
			cur.Close()
			return err
		}
		ada[id] = true
		if a == 1 {
			aktif[id] = true
		}
	}
	if err := cur.Err(); err != nil {
		cur.Close()
		return err
	}
	cur.Close()

	var tambah, cabut []string
	for id, k := range target {
		switch {
		case aktif[id]:
			// tetap
		case ada[id]:
			if _, err := tx.ExecContext(ctx, `UPDATE dbo.role_permission SET dihapus_pada=NULL, dihapus_oleh=NULL, diubah_pada=SYSUTCDATETIME(), diubah_oleh=@p3 WHERE role_id=@p1 AND permission_id=@p2`, roleID, id, aktorID); err != nil {
				return err
			}
			tambah = append(tambah, k)
		default:
			if _, err := tx.ExecContext(ctx, `INSERT INTO dbo.role_permission (role_id, permission_id, dibuat_oleh) VALUES (@p1,@p2,@p3)`, roleID, id, aktorID); err != nil {
				return err
			}
			tambah = append(tambah, k)
		}
	}
	nama := map[int64]string{}
	for k, id := range pid {
		nama[id] = k
	}
	for id := range aktif {
		if _, masih := target[id]; masih {
			continue
		}
		if _, err := tx.ExecContext(ctx, `UPDATE dbo.role_permission SET dihapus_pada=SYSUTCDATETIME(), dihapus_oleh=@p3, diubah_pada=SYSUTCDATETIME(), diubah_oleh=@p3 WHERE role_id=@p1 AND permission_id=@p2 AND dihapus_pada IS NULL`, roleID, id, aktorID); err != nil {
			return err
		}
		cabut = append(cabut, nama[id])
	}
	if len(tambah) == 0 && len(cabut) == 0 {
		return nil // tidak ada yang berubah: tidak menulis audit dan tidak mengubah versi master
	}
	sort.Strings(tambah)
	sort.Strings(cabut)
	ket := fmt.Sprintf("%s: +[%s] -[%s]", roleKode, strings.Join(tambah, ","), strings.Join(cabut, ","))
	if len(ket) > 300 {
		ket = ket[:297] + "..."
	}
	return catat(ctx, tx, "role_permission", roleID, "UBAH", aktorID, ket)
}

// Buat membuat role baru (bukan bawaan) beserta hak awalnya dalam satu transaksi.
//   - ErrDuplikat bila kode role sudah dipakai (termasuk role yang pernah dihapus: kodenya tetap tercatat).
//   - ErrReferensi bila ada kode permission yang tidak dikenal.
func (r *HakAksesRepository) Buat(ctx context.Context, aktorID int64, in model.RoleInput) (int64, error) {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return 0, err
	}
	defer tx.Rollback()

	var n int
	if err := tx.QueryRowContext(ctx, `SELECT COUNT(*) FROM dbo.role WHERE kode=@p1`, in.Kode).Scan(&n); err != nil {
		return 0, err
	}
	if n > 0 {
		return 0, ErrDuplikat
	}
	var id int64
	if err := tx.QueryRowContext(ctx, `
INSERT INTO dbo.role (kode, nama, deskripsi, bawaan, dibuat_oleh) OUTPUT INSERTED.role_id VALUES (@p1, @p2, @p3, 0, @p4)`,
		in.Kode, in.Nama, nullStr(in.Deskripsi), aktorID).Scan(&id); err != nil {
		return 0, err
	}
	if err := catat(ctx, tx, "role", id, "TAMBAH", aktorID, in.Kode); err != nil {
		return 0, err
	}
	if err := r.ubahTx(ctx, tx, aktorID, id, in.Permission); err != nil {
		return 0, err
	}
	return id, tx.Commit()
}

// UbahProfil mengganti nama dan/atau deskripsi role (kode tetap). Mengubah diubah_pada supaya HP mengunduh master baru.
func (r *HakAksesRepository) UbahProfil(ctx context.Context, aktorID, roleID int64, nama, deskripsi *string) error {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	res, err := tx.ExecContext(ctx, `
UPDATE dbo.role SET nama = COALESCE(@p2, nama), deskripsi = CASE WHEN @p3 IS NULL THEN deskripsi ELSE NULLIF(@p3, N'') END,
  diubah_pada = SYSUTCDATETIME(), diubah_oleh = @p4
WHERE role_id=@p1 AND dihapus_pada IS NULL`, roleID, ptrAny(nama), ptrAny(deskripsi), aktorID)
	if err != nil {
		return err
	}
	if n, _ := res.RowsAffected(); n != 1 {
		return ErrNotFound
	}
	if err := catat(ctx, tx, "role", roleID, "UBAH", aktorID, "nama/deskripsi"); err != nil {
		return err
	}
	return tx.Commit()
}

// Hapus menghapus lunak role buatan admin beserta pasangan hak-nya.
//   - ErrNotFound bila role tidak ada.
//   - ErrDilindungi bila role bawaan sistem.
//   - ErrMasihDipakai bila masih ada akun aktif yang memegang role itu.
func (r *HakAksesRepository) Hapus(ctx context.Context, aktorID, roleID int64) error {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	var kode string
	var bawaan bool
	if err := tx.QueryRowContext(ctx, `SELECT kode, bawaan FROM dbo.role WHERE role_id=@p1 AND dihapus_pada IS NULL`, roleID).Scan(&kode, &bawaan); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return ErrNotFound
		}
		return err
	}
	if bawaan {
		return ErrDilindungi
	}
	var pakai int
	if err := tx.QueryRowContext(ctx, `
SELECT COUNT(*) FROM dbo.user_role ur JOIN dbo.app_user u ON u.user_id = ur.user_id AND u.dihapus_pada IS NULL
WHERE ur.role_id=@p1 AND ur.dihapus_pada IS NULL`, roleID).Scan(&pakai); err != nil {
		return err
	}
	if pakai > 0 {
		return ErrMasihDipakai
	}
	if _, err := tx.ExecContext(ctx, `
UPDATE dbo.role_permission SET dihapus_pada=SYSUTCDATETIME(), dihapus_oleh=@p2, diubah_pada=SYSUTCDATETIME(), diubah_oleh=@p2
WHERE role_id=@p1 AND dihapus_pada IS NULL`, roleID, aktorID); err != nil {
		return err
	}
	if _, err := tx.ExecContext(ctx, `
UPDATE dbo.role SET dihapus_pada=SYSUTCDATETIME(), dihapus_oleh=@p2, diubah_pada=SYSUTCDATETIME(), diubah_oleh=@p2 WHERE role_id=@p1`, roleID, aktorID); err != nil {
		return err
	}
	if err := catat(ctx, tx, "role", roleID, "HAPUS", aktorID, kode); err != nil {
		return err
	}
	return tx.Commit()
}

func berisi(daftar []string, k string) bool {
	for _, x := range daftar {
		if x == k {
			return true
		}
	}
	return false
}
