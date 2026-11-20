package repository

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"strings"

	"fai26-api/internal/model"
)

// AdminRepository: pengguna, perangkat, ringkasan, serta catatan panen dan audit (hanya baca) untuk web admin.
type AdminRepository struct{ db *sql.DB }

func NewAdminRepository(db *sql.DB) *AdminRepository { return &AdminRepository{db: db} }

func (r *AdminRepository) tx(ctx context.Context, fn func(tx *sql.Tx) error) error {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	if err := fn(tx); err != nil {
		return err
	}
	return tx.Commit()
}

// ---- Role ----------------------------------------------------------------------------------------

func (r *AdminRepository) DaftarRole(ctx context.Context) ([]model.RoleAdmin, error) {
	rows, err := r.db.QueryContext(ctx, `SELECT role_id, kode, nama, COALESCE(deskripsi, N'') FROM dbo.role WHERE dihapus_pada IS NULL ORDER BY role_id`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []model.RoleAdmin{}
	for rows.Next() {
		var x model.RoleAdmin
		if err := rows.Scan(&x.RoleID, &x.Kode, &x.Nama, &x.Deskripsi); err != nil {
			return nil, err
		}
		out = append(out, x)
	}
	return out, rows.Err()
}

// ---- Pengguna ------------------------------------------------------------------------------------

func (r *AdminRepository) DaftarPengguna(ctx context.Context, unitID int64) ([]model.PenggunaAdmin, error) {
	rows, err := r.db.QueryContext(ctx, `
SELECT user_id, username, nama, aktif, login_terakhir,
  CASE WHEN terkunci_sampai > SYSUTCDATETIME() THEN 1 ELSE 0 END, wajib_ganti_password, mandor_hrmb_code, afdeling_kode
FROM dbo.app_user WHERE unit_id=@p1 AND dihapus_pada IS NULL ORDER BY username`, unitID)
	if err != nil {
		return nil, err
	}
	out := []model.PenggunaAdmin{}
	idx := map[int64]int{}
	for rows.Next() {
		var u model.PenggunaAdmin
		var login sql.NullTime
		var kunci int
		var mandor sql.NullInt64
		var afdeling sql.NullString
		if err := rows.Scan(&u.UserID, &u.Username, &u.Nama, &u.Aktif, &login, &kunci, &u.WajibGanti, &mandor, &afdeling); err != nil {
			rows.Close()
			return nil, err
		}
		u.MandorHrmbCode, u.AfdelingKode = int64Ptr(mandor), strPtr(afdeling)
		if login.Valid {
			t := login.Time.UTC()
			u.LoginTerakhir = &t
		}
		u.Terkunci = kunci == 1
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
SELECT ur.user_id, r.kode FROM dbo.user_role ur JOIN dbo.role r ON r.role_id=ur.role_id
JOIN dbo.app_user u ON u.user_id=ur.user_id
WHERE u.unit_id=@p1 AND ur.dihapus_pada IS NULL ORDER BY ur.user_id, r.role_id`, unitID)
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

// aturRole mengganti role seorang pengguna: yang tidak ada di daftar dihapus lunak, yang baru ditambahkan.
func aturRole(ctx context.Context, tx *sql.Tx, userID, aktorID int64, kodeRole []string) error {
	ids := map[int64]bool{}
	for _, k := range kodeRole {
		var id int64
		if err := tx.QueryRowContext(ctx, `SELECT role_id FROM dbo.role WHERE kode=@p1 AND dihapus_pada IS NULL`, k).Scan(&id); err != nil {
			if errors.Is(err, sql.ErrNoRows) {
				return ErrReferensi
			}
			return err
		}
		ids[id] = true
	}
	rows, err := tx.QueryContext(ctx, `SELECT role_id FROM dbo.user_role WHERE user_id=@p1 AND dihapus_pada IS NULL`, userID)
	if err != nil {
		return err
	}
	ada := map[int64]bool{}
	for rows.Next() {
		var id int64
		if err := rows.Scan(&id); err != nil {
			rows.Close()
			return err
		}
		ada[id] = true
	}
	rows.Close()
	for id := range ada {
		if !ids[id] {
			if _, err := tx.ExecContext(ctx, `UPDATE dbo.user_role SET dihapus_pada=SYSUTCDATETIME(), dihapus_oleh=@p3, diubah_pada=SYSUTCDATETIME(), diubah_oleh=@p3 WHERE user_id=@p1 AND role_id=@p2 AND dihapus_pada IS NULL`, userID, id, aktorID); err != nil {
				return err
			}
		}
	}
	for id := range ids {
		if !ada[id] {
			if _, err := tx.ExecContext(ctx, `INSERT INTO dbo.user_role (user_id, role_id, dibuat_oleh) VALUES (@p1,@p2,@p3)`, userID, id, aktorID); err != nil {
				return err
			}
		}
	}
	return nil
}

func (r *AdminRepository) BuatPengguna(ctx context.Context, unitID, aktorID int64, in model.PenggunaInput, hash string) (int64, error) {
	var id int64
	err := r.tx(ctx, func(tx *sql.Tx) error {
		var n int
		if err := tx.QueryRowContext(ctx, `SELECT COUNT(*) FROM dbo.app_user WHERE username=@p1`, in.Username).Scan(&n); err != nil {
			return err
		}
		if n > 0 {
			return ErrDuplikat
		}
		if err := tx.QueryRowContext(ctx, `
INSERT INTO dbo.app_user (unit_id, username, nama, aktif, password_hash, wajib_ganti_password, dibuat_oleh, mandor_hrmb_code, afdeling_kode)
OUTPUT INSERTED.user_id VALUES (@p1,@p2,@p3,@p4,@p5,1,@p6,@p7,@p8)`, unitID, in.Username, in.Nama, aktifAtauBawaan(in.Aktif), hash, aktorID,
			ptrAny(in.MandorHrmbCode), nullStr(teksAtauKosong(in.AfdelingKode))).Scan(&id); err != nil {
			return err
		}
		if err := aturRole(ctx, tx, id, aktorID, in.Role); err != nil {
			return err
		}
		return catat(ctx, tx, "app_user", id, "BUAT", aktorID, in.Username)
	})
	return id, err
}

func (r *AdminRepository) UbahPengguna(ctx context.Context, unitID, aktorID, id int64, in model.PenggunaInput) error {
	return r.tx(ctx, func(tx *sql.Tx) error {
		var n int
		if err := tx.QueryRowContext(ctx, `SELECT COUNT(*) FROM dbo.app_user WHERE user_id=@p1 AND unit_id=@p2 AND dihapus_pada IS NULL`, id, unitID).Scan(&n); err != nil {
			return err
		}
		if n == 0 {
			return ErrNotFound
		}
		if _, err := tx.ExecContext(ctx, `UPDATE dbo.app_user SET nama=@p2, aktif=COALESCE(@p3, aktif), mandor_hrmb_code=@p5, afdeling_kode=@p6,
  diubah_pada=SYSUTCDATETIME(), diubah_oleh=@p4 WHERE user_id=@p1`, id, in.Nama, ptrAny(in.Aktif), aktorID,
			ptrAny(in.MandorHrmbCode), nullStr(teksAtauKosong(in.AfdelingKode))); err != nil {
			return err
		}
		if in.Aktif != nil && !*in.Aktif {
			if _, err := tx.ExecContext(ctx, `UPDATE dbo.auth_sesi SET dicabut_pada=SYSUTCDATETIME() WHERE user_id=@p1 AND dicabut_pada IS NULL`, id); err != nil {
				return err
			}
		}
		if err := aturRole(ctx, tx, id, aktorID, in.Role); err != nil {
			return err
		}
		return catat(ctx, tx, "app_user", id, "UBAH", aktorID, strings.Join(in.Role, ","))
	})
}

// ResetPassword mengganti hash, mewajibkan ganti password, membuka kunci, dan mencabut semua sesi pengguna.
func (r *AdminRepository) ResetPassword(ctx context.Context, unitID, aktorID, id int64, hash string) error {
	return r.tx(ctx, func(tx *sql.Tx) error {
		res, err := tx.ExecContext(ctx, `
UPDATE dbo.app_user SET password_hash=@p3, wajib_ganti_password=1, gagal_login=0, terkunci_sampai=NULL, diubah_pada=SYSUTCDATETIME(), diubah_oleh=@p4
WHERE user_id=@p1 AND unit_id=@p2 AND dihapus_pada IS NULL`, id, unitID, hash, aktorID)
		if err != nil {
			return err
		}
		if n, _ := res.RowsAffected(); n == 0 {
			return ErrNotFound
		}
		if _, err := tx.ExecContext(ctx, `UPDATE dbo.auth_sesi SET dicabut_pada=SYSUTCDATETIME() WHERE user_id=@p1 AND dicabut_pada IS NULL`, id); err != nil {
			return err
		}
		return catat(ctx, tx, "app_user", id, "RESET_PASSWORD", aktorID, "")
	})
}

// ---- Perangkat -----------------------------------------------------------------------------------

func (r *AdminRepository) DaftarPerangkat(ctx context.Context) ([]model.PerangkatAdmin, error) {
	rows, err := r.db.QueryContext(ctx, `
SELECT CONVERT(NVARCHAR(36), device_id), nama, os_versi, app_versi, aktif, terakhir_sinkron FROM dbo.device
WHERE dihapus_pada IS NULL ORDER BY COALESCE(terakhir_sinkron, dibuat_pada) DESC`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []model.PerangkatAdmin{}
	for rows.Next() {
		var d model.PerangkatAdmin
		var nama, os, app sql.NullString
		var sinkron sql.NullTime
		if err := rows.Scan(&d.DeviceID, &nama, &os, &app, &d.Aktif, &sinkron); err != nil {
			return nil, err
		}
		d.DeviceID = strings.ToLower(d.DeviceID)
		d.Nama, d.OsVersi, d.AppVersi = strPtr(nama), strPtr(os), strPtr(app)
		if sinkron.Valid {
			t := sinkron.Time.UTC()
			d.TerakhirSinkron = &t
		}
		out = append(out, d)
	}
	return out, rows.Err()
}

func strPtr(n sql.NullString) *string {
	if !n.Valid {
		return nil
	}
	return &n.String
}

// UbahPerangkat mengaktifkan atau menonaktifkan HP. Menonaktifkan juga mencabut semua sesinya.
func (r *AdminRepository) UbahPerangkat(ctx context.Context, aktorID int64, deviceID string, aktif bool) error {
	return r.tx(ctx, func(tx *sql.Tx) error {
		res, err := tx.ExecContext(ctx, `UPDATE dbo.device SET aktif=@p2, diubah_pada=SYSUTCDATETIME(), diubah_oleh=@p3 WHERE device_id=@p1 AND dihapus_pada IS NULL`, deviceID, aktif, aktorID)
		if err != nil {
			return err
		}
		if n, _ := res.RowsAffected(); n == 0 {
			return ErrNotFound
		}
		if !aktif {
			if _, err := tx.ExecContext(ctx, `UPDATE dbo.auth_sesi SET dicabut_pada=SYSUTCDATETIME() WHERE device_id=@p1 AND dicabut_pada IS NULL`, deviceID); err != nil {
				return err
			}
		}
		return insertAudit(ctx, tx, model.AuditBaris{Tabel: "device", BarisID: deviceID, Aksi: map[bool]string{true: "AKTIFKAN", false: "NONAKTIFKAN"}[aktif], UserID: &aktorID})
	})
}

// ---- Ringkasan -----------------------------------------------------------------------------------

func (r *AdminRepository) Ringkasan(ctx context.Context, unitID int64, hariIni string) (*model.RingkasanAdmin, error) {
	out := &model.RingkasanAdmin{CatatanPerStatus: map[string]int64{"BARU": 0, "DIBACA": 0, "DIIMPOR": 0, "DITOLAK": 0}, Tren7Hari: []model.TrenHarian{}}
	scalar := func(q string, dst *int64, args ...any) error { return r.db.QueryRowContext(ctx, q, args...).Scan(dst) }

	if err := scalar(`SELECT COUNT(*) FROM dbo.fai_panen WHERE unit_id=@p1 AND tanggal_panen=@p2 AND dihapus_pada IS NULL`, &out.CatatanHariIni, unitID, hariIni); err != nil {
		return nil, err
	}
	if err := scalar(`SELECT COALESCE(SUM(g.jml_final),0) FROM dbo.fai_panen_grading g JOIN dbo.fai_panen f ON f.id=g.panen_id WHERE f.unit_id=@p1 AND f.tanggal_panen=@p2 AND f.dihapus_pada IS NULL AND g.dihapus_pada IS NULL`, &out.JanjangHariIni, unitID, hariIni); err != nil {
		return nil, err
	}
	if err := scalar(`SELECT COUNT(*) FROM dbo.fai_panen WHERE unit_id=@p1 AND dihapus_pada IS NULL AND (status_lokasi IN ('DI_LUAR','AKURASI_RENDAH','TANPA_GPS') OR gps_mock=1) AND status_erp IN ('BARU','DIBACA')`, &out.PerluPerhatian, unitID); err != nil {
		return nil, err
	}
	if err := scalar(`SELECT COUNT(*) FROM dbo.device WHERE aktif=1 AND dihapus_pada IS NULL`, &out.PerangkatAktif); err != nil {
		return nil, err
	}
	if err := scalar(`SELECT COUNT(*) FROM dbo.app_user WHERE unit_id=@p1 AND aktif=1 AND dihapus_pada IS NULL`, &out.PenggunaAktif, unitID); err != nil {
		return nil, err
	}
	if err := scalar(`SELECT COUNT(*) FROM dbo.tph t JOIN dbo.blok b ON b.blok_id=t.blok_id WHERE b.unit_id=@p1 AND t.aktif=1 AND t.dihapus_pada IS NULL`, &out.TphAktif, unitID); err != nil {
		return nil, err
	}
	if err := scalar(`SELECT COUNT(*) FROM dbo.tph t JOIN dbo.blok b ON b.blok_id=t.blok_id WHERE b.unit_id=@p1 AND t.aktif=1 AND t.dihapus_pada IS NULL AND (t.lat IS NULL OR t.lon IS NULL)`, &out.TphTanpaKoordinat, unitID); err != nil {
		return nil, err
	}

	rows, err := r.db.QueryContext(ctx, `SELECT status_erp, COUNT(*) FROM dbo.fai_panen WHERE unit_id=@p1 AND dihapus_pada IS NULL GROUP BY status_erp`, unitID)
	if err != nil {
		return nil, err
	}
	for rows.Next() {
		var s string
		var n int64
		if err := rows.Scan(&s, &n); err != nil {
			rows.Close()
			return nil, err
		}
		out.CatatanPerStatus[s] = n
	}
	rows.Close()

	tr, err := r.db.QueryContext(ctx, `
SELECT CONVERT(NVARCHAR(10), f.tanggal_panen, 23), COUNT(DISTINCT f.id), COALESCE(SUM(g.jml_final), 0)
FROM dbo.fai_panen f LEFT JOIN dbo.fai_panen_grading g ON g.panen_id=f.id AND g.dihapus_pada IS NULL
WHERE f.unit_id=@p1 AND f.dihapus_pada IS NULL AND f.tanggal_panen > DATEADD(DAY, -7, CAST(@p2 AS DATE)) AND f.tanggal_panen <= CAST(@p2 AS DATE)
GROUP BY f.tanggal_panen ORDER BY f.tanggal_panen`, unitID, hariIni)
	if err != nil {
		return nil, err
	}
	defer tr.Close()
	for tr.Next() {
		var t model.TrenHarian
		if err := tr.Scan(&t.Tanggal, &t.Catatan, &t.Janjang); err != nil {
			return nil, err
		}
		out.Tren7Hari = append(out.Tren7Hari, t)
	}
	return out, tr.Err()
}

// ---- Catatan panen -------------------------------------------------------------------------------

func (r *AdminRepository) DaftarPanen(ctx context.Context, unitID int64, f model.FilterPanen) (*model.HalamanPanen, error) {
	where := []string{"f.unit_id=@p1", "f.dihapus_pada IS NULL"}
	args := []any{unitID}
	tambah := func(kondisi string, nilai any) {
		args = append(args, nilai)
		where = append(where, strings.ReplaceAll(kondisi, "?", fmt.Sprintf("@p%d", len(args))))
	}
	if f.Dari != "" {
		tambah("f.tanggal_panen >= ?", f.Dari)
	}
	if f.Sampai != "" {
		tambah("f.tanggal_panen <= ?", f.Sampai)
	}
	if f.StatusEr != "" {
		tambah("f.status_erp = ?", f.StatusEr)
	}
	if f.Cari != "" {
		args = append(args, "%"+f.Cari+"%")
		p := fmt.Sprintf("@p%d", len(args))
		where = append(where, "(f.blok_kode LIKE "+p+" OR f.tph_kode LIKE "+p+")")
	}
	cond := strings.Join(where, " AND ")

	out := &model.HalamanPanen{Item: []model.PanenRingkas{}, Halaman: f.Halaman, PerHal: f.PerHal}
	if err := r.db.QueryRowContext(ctx, "SELECT COUNT(*) FROM dbo.fai_panen f WHERE "+cond, args...).Scan(&out.Total); err != nil {
		return nil, err
	}
	args = append(args, (f.Halaman-1)*f.PerHal, f.PerHal)
	q := fmt.Sprintf(`
SELECT CONVERT(NVARCHAR(36), f.id), CONVERT(NVARCHAR(10), f.tanggal_panen, 23), f.blok_kode, f.tph_kode,
  (SELECT COALESCE(SUM(g.jml_final),0) FROM dbo.fai_panen_grading g WHERE g.panen_id=f.id AND g.dihapus_pada IS NULL),
  COALESCE(f.brondolan_kg,0), f.status_erp, f.status_lokasi, f.jarak_ke_tph_m, f.gps_mock,
  p.nama, s.nama, f.diterima_server
FROM dbo.fai_panen f
LEFT JOIN dbo.app_user p ON p.user_id = f.dibuat_oleh
LEFT JOIN dbo.app_user s ON s.user_id = f.disetujui_oleh
WHERE %s ORDER BY f.tanggal_panen DESC, f.diterima_server DESC OFFSET @p%d ROWS FETCH NEXT @p%d ROWS ONLY`, cond, len(args)-1, len(args))
	rows, err := r.db.QueryContext(ctx, q, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	for rows.Next() {
		var x model.PanenRingkas
		var blok, tph, pencatat, penyetuju sql.NullString
		var jarak sql.NullFloat64
		if err := rows.Scan(&x.ID, &x.TanggalPanen, &blok, &tph, &x.TotalJanjang, &x.BrondolanKg, &x.StatusErp, &x.StatusLokasi, &jarak, &x.GpsMock, &pencatat, &penyetuju, &x.DiterimaServer); err != nil {
			return nil, err
		}
		x.ID = strings.ToLower(x.ID)
		x.BlokKode, x.TphKode, x.Pencatat, x.Penyetuju = strPtr(blok), strPtr(tph), strPtr(pencatat), strPtr(penyetuju)
		if jarak.Valid {
			x.JarakKeTphM = &jarak.Float64
		}
		x.DiterimaServer = x.DiterimaServer.UTC()
		out.Item = append(out.Item, x)
	}
	return out, rows.Err()
}

func (r *AdminRepository) DetailPanen(ctx context.Context, unitID int64, id string) (*model.PanenDetail, error) {
	d := &model.PanenDetail{Grading: []model.GradingAdmin{}, Pemanen: []model.PemanenPanenAdmin{}, Foto: []model.FotoAdmin{}, ImporLog: []model.ImporLogAdmin{}}
	var blok, tph, pencatat, penyetuju, modelV, catatan sql.NullString
	var jarak, lat, lon, akurasi sql.NullFloat64
	var rotasi, cuaca sql.NullInt64
	var setuju sql.NullTime
	err := r.db.QueryRowContext(ctx, `
SELECT CONVERT(NVARCHAR(36), f.id), CONVERT(NVARCHAR(10), f.tanggal_panen, 23), f.blok_kode, f.tph_kode,
  (SELECT COALESCE(SUM(g.jml_final),0) FROM dbo.fai_panen_grading g WHERE g.panen_id=f.id AND g.dihapus_pada IS NULL),
  COALESCE(f.brondolan_kg,0), f.status_erp, f.status_lokasi, f.jarak_ke_tph_m, f.gps_mock, p.nama, s.nama, f.diterima_server,
  f.rotasi, f.kondisi_cuaca, f.asm3_oid, f.gps_lat, f.gps_lon, f.gps_akurasi_m, f.model_versi, f.catatan, f.dibuat_pada, f.disetujui_pada,
  CONVERT(NVARCHAR(36), f.device_id)
FROM dbo.fai_panen f LEFT JOIN dbo.app_user p ON p.user_id=f.dibuat_oleh LEFT JOIN dbo.app_user s ON s.user_id=f.disetujui_oleh
WHERE f.id=@p1 AND f.unit_id=@p2 AND f.dihapus_pada IS NULL`, id, unitID).Scan(
		&d.ID, &d.TanggalPanen, &blok, &tph, &d.TotalJanjang, &d.BrondolanKg, &d.StatusErp, &d.StatusLokasi, &jarak, &d.GpsMock,
		&pencatat, &penyetuju, &d.DiterimaServer, &rotasi, &cuaca, &d.Asm3Oid, &lat, &lon, &akurasi, &modelV, &catatan, &d.DibuatPada, &setuju, &d.DeviceID)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrNotFound
	}
	if err != nil {
		return nil, err
	}
	d.ID = strings.ToLower(d.ID)
	d.DeviceID = strings.ToLower(d.DeviceID)
	d.BlokKode, d.TphKode, d.Pencatat, d.Penyetuju = strPtr(blok), strPtr(tph), strPtr(pencatat), strPtr(penyetuju)
	d.ModelVersi, d.Catatan = strPtr(modelV), strPtr(catatan)
	d.DiterimaServer = d.DiterimaServer.UTC()
	d.DibuatPada = d.DibuatPada.UTC()
	if jarak.Valid {
		d.JarakKeTphM = &jarak.Float64
	}
	if lat.Valid {
		d.GpsLat = &lat.Float64
	}
	if lon.Valid {
		d.GpsLon = &lon.Float64
	}
	if akurasi.Valid {
		d.GpsAkurasiM = &akurasi.Float64
	}
	if rotasi.Valid {
		d.Rotasi = &rotasi.Int64
	}
	if cuaca.Valid {
		d.KondisiCuaca = &cuaca.Int64
	}
	if setuju.Valid {
		t := setuju.Time.UTC()
		d.DisetujuiPada = &t
	}

	gr, err := r.db.QueryContext(ctx, `SELECT kelas_kode, erp_kolom, jml_ai, jml_final, diubah_manual FROM dbo.fai_panen_grading WHERE panen_id=@p1 AND dihapus_pada IS NULL ORDER BY id`, id)
	if err != nil {
		return nil, err
	}
	for gr.Next() {
		var g model.GradingAdmin
		var kolom sql.NullString
		if err := gr.Scan(&g.KelasKode, &kolom, &g.JmlAI, &g.JmlFinal, &g.Manual); err != nil {
			gr.Close()
			return nil, err
		}
		g.ErpKolom = strPtr(kolom)
		d.Grading = append(d.Grading, g)
	}
	gr.Close()

	pm, err := r.db.QueryContext(ctx, `SELECT p.hrmb_code, k.nama, p.jjg_alokasi FROM dbo.fai_panen_pemanen p
LEFT JOIN dbo.pemanen k ON k.erp_hrmb_code=p.hrmb_code AND k.unit_id=@p2 AND k.dihapus_pada IS NULL
WHERE p.panen_id=@p1 AND p.dihapus_pada IS NULL ORDER BY p.id`, id, unitID)
	if err != nil {
		return nil, err
	}
	for pm.Next() {
		var x model.PemanenPanenAdmin
		var hrmb, jjg sql.NullInt64
		var nama sql.NullString
		if err := pm.Scan(&hrmb, &nama, &jjg); err != nil {
			pm.Close()
			return nil, err
		}
		if hrmb.Valid {
			x.HrmbCode = &hrmb.Int64
		}
		if jjg.Valid {
			x.JjgAlokasi = &jjg.Int64
		}
		x.Nama = strPtr(nama)
		d.Pemanen = append(d.Pemanen, x)
	}
	pm.Close()

	fo, err := r.db.QueryContext(ctx, `SELECT CONVERT(NVARCHAR(36), id), jenis FROM dbo.fai_foto WHERE panen_id=@p1 AND dihapus_pada IS NULL ORDER BY dibuat_pada`, id)
	if err != nil {
		return nil, err
	}
	for fo.Next() {
		var x model.FotoAdmin
		if err := fo.Scan(&x.ID, &x.Jenis); err != nil {
			fo.Close()
			return nil, err
		}
		x.ID = strings.ToLower(x.ID)
		d.Foto = append(d.Foto, x)
	}
	fo.Close()

	lg, err := r.db.QueryContext(ctx, `SELECT waktu_impor, hasil, pesan_error FROM dbo.fai_impor_log WHERE panen_id=@p1 ORDER BY id`, id)
	if err != nil {
		return nil, err
	}
	defer lg.Close()
	for lg.Next() {
		var x model.ImporLogAdmin
		var pesan sql.NullString
		if err := lg.Scan(&x.Waktu, &x.Hasil, &pesan); err != nil {
			return nil, err
		}
		x.Waktu = x.Waktu.UTC()
		x.Pesan = strPtr(pesan)
		d.ImporLog = append(d.ImporLog, x)
	}
	return d, lg.Err()
}

// PathFoto mengembalikan path berkas foto, bila foto itu milik catatan di unit tersebut.
func (r *AdminRepository) PathFoto(ctx context.Context, unitID int64, fotoID string) (string, error) {
	var path string
	err := r.db.QueryRowContext(ctx, `
SELECT o.nama_file FROM dbo.fai_foto o JOIN dbo.fai_panen f ON f.id=o.panen_id
WHERE o.id=@p1 AND f.unit_id=@p2 AND o.dihapus_pada IS NULL`, fotoID, unitID).Scan(&path)
	if errors.Is(err, sql.ErrNoRows) {
		return "", ErrNotFound
	}
	return path, err
}

// ---- Audit ---------------------------------------------------------------------------------------

func (r *AdminRepository) DaftarAudit(ctx context.Context, f model.FilterAudit) (*model.HalamanAudit, error) {
	where := []string{"1=1"}
	var args []any
	tambah := func(kondisi string, nilai any) {
		args = append(args, nilai)
		where = append(where, strings.ReplaceAll(kondisi, "?", fmt.Sprintf("@p%d", len(args))))
	}
	if f.Tabel != "" {
		tambah("a.tabel = ?", f.Tabel)
	}
	if f.Aksi != "" {
		tambah("a.aksi = ?", f.Aksi)
	}
	if f.Dari != "" {
		tambah("a.waktu >= ?", f.Dari)
	}
	if f.Sampai != "" {
		tambah("a.waktu < DATEADD(DAY, 1, CAST(? AS DATE))", f.Sampai)
	}
	if f.Cari != "" {
		args = append(args, "%"+f.Cari+"%")
		p := fmt.Sprintf("@p%d", len(args))
		where = append(where, "(a.username LIKE "+p+" OR a.baris_id LIKE "+p+" OR a.keterangan LIKE "+p+")")
	}
	cond := strings.Join(where, " AND ")
	out := &model.HalamanAudit{Item: []model.AuditAdmin{}, Halaman: f.Halaman, PerHal: f.PerHal}
	if err := r.db.QueryRowContext(ctx, "SELECT COUNT(*) FROM dbo.audit_log a WHERE "+cond, args...).Scan(&out.Total); err != nil {
		return nil, err
	}
	args = append(args, (f.Halaman-1)*f.PerHal, f.PerHal)
	rows, err := r.db.QueryContext(ctx, fmt.Sprintf(`
SELECT CONVERT(NVARCHAR(36), a.audit_id), a.tabel, a.baris_id, a.aksi, a.nilai_lama, a.nilai_baru, a.user_id, a.username,
  CONVERT(NVARCHAR(36), a.device_id), a.waktu, a.waktu_server, a.ip, a.keterangan
FROM dbo.audit_log a WHERE %s ORDER BY a.waktu DESC, a.audit_id OFFSET @p%d ROWS FETCH NEXT @p%d ROWS ONLY`, cond, len(args)-1, len(args)), args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	for rows.Next() {
		var x model.AuditAdmin
		var lama, baru, uname, dev, ip, ket sql.NullString
		var uid sql.NullInt64
		var ws sql.NullTime
		if err := rows.Scan(&x.AuditID, &x.Tabel, &x.BarisID, &x.Aksi, &lama, &baru, &uid, &uname, &dev, &x.Waktu, &ws, &ip, &ket); err != nil {
			return nil, err
		}
		x.AuditID = strings.ToLower(x.AuditID)
		x.NilaiLama, x.NilaiBaru, x.Username, x.Ip, x.Keterangan = strPtr(lama), strPtr(baru), strPtr(uname), strPtr(ip), strPtr(ket)
		if dev.Valid {
			s := strings.ToLower(dev.String)
			x.DeviceID = &s
		}
		if uid.Valid {
			x.UserID = &uid.Int64
		}
		x.Waktu = x.Waktu.UTC()
		if ws.Valid {
			t := ws.Time.UTC()
			x.WaktuServer = &t
		}
		out.Item = append(out.Item, x)
	}
	return out, rows.Err()
}

// ---- Produktivitas pemanen -----------------------------------------------------------------------

// Produktivitas mengelompokkan catatan satu hari per pemanen (kode ERP), terbanyak janjang dulu. Catatan yang
// ditolak tidak dihitung. Nama dicari di master pemanen; peringkat dan persentase disusun oleh service.
func (r *AdminRepository) Produktivitas(ctx context.Context, unitID int64, tanggal string) ([]model.ProduktivitasPemanen, error) {
	rows, err := r.db.QueryContext(ctx, `
WITH p AS (
  SELECT f.id, f.pemanen_hrmb_code AS hrmb, COALESCE(f.brondolan_kg, 0) AS brondolan, f.status_erp,
         COALESCE(SUM(g.jml_final), 0) AS jjg,
         COALESCE(SUM(CASE WHEN LOWER(g.kelas_kode) = 'ripe' THEN g.jml_final ELSE 0 END), 0) AS matang
  FROM dbo.fai_panen f
  LEFT JOIN dbo.fai_panen_grading g ON g.panen_id = f.id AND g.dihapus_pada IS NULL
  WHERE f.unit_id = @p1 AND f.tanggal_panen = @p2 AND f.dihapus_pada IS NULL
    AND f.status_erp <> 'DITOLAK' AND f.pemanen_hrmb_code IS NOT NULL
  GROUP BY f.id, f.pemanen_hrmb_code, f.brondolan_kg, f.status_erp)
SELECT p.hrmb, COALESCE(MAX(m.nama), N''), COUNT(*), SUM(p.jjg), SUM(p.matang), SUM(p.brondolan),
       SUM(CASE WHEN p.status_erp = 'MENUNGGU' THEN 1 ELSE 0 END)
FROM p
OUTER APPLY (SELECT TOP 1 pm.nama FROM dbo.pemanen pm
             WHERE pm.unit_id = @p1 AND pm.erp_hrmb_code = p.hrmb AND pm.dihapus_pada IS NULL
             ORDER BY pm.pemanen_id) m
GROUP BY p.hrmb
ORDER BY SUM(p.jjg) DESC, COALESCE(MAX(m.nama), N''), p.hrmb`, unitID, tanggal)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []model.ProduktivitasPemanen{}
	for rows.Next() {
		var x model.ProduktivitasPemanen
		if err := rows.Scan(&x.HrmbCode, &x.Nama, &x.Catatan, &x.Janjang, &x.JanjangMatang, &x.BrondolanKg, &x.Menunggu); err != nil {
			return nil, err
		}
		out = append(out, x)
	}
	return out, rows.Err()
}
