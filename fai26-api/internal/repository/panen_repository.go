package repository

import (
	"context"
	"database/sql"
	"errors"
	"strings"
	"time"

	mssql "github.com/microsoft/go-mssqldb"

	"fai26-api/internal/model"
)

type PanenRepository struct{ db *sql.DB }

func NewPanenRepository(db *sql.DB) *PanenRepository { return &PanenRepository{db: db} }

func (r *PanenRepository) Ada(ctx context.Context, panenID string) (bool, error) {
	var n int
	err := r.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM dbo.fai_panen WHERE id=@p1`, panenID).Scan(&n)
	return n > 0, err
}

// Cari mengembalikan status ERP dan pencatat catatan dengan panen_id itu, atau nil bila belum ada.
func (r *PanenRepository) Cari(ctx context.Context, panenID string) (*model.CatatanAda, error) {
	var c model.CatatanAda
	err := r.db.QueryRowContext(ctx, `SELECT status_erp, dibuat_oleh FROM dbo.fai_panen WHERE id=@p1 AND dihapus_pada IS NULL`, panenID).
		Scan(&c.StatusErp, &c.PemilikID)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	return &c, err
}

// CekMaster memeriksa blok (erp_oid = asm3_oid), TPH (nomor pada blok itu), dan pemanen (erp_hrmb_code) di master unit.
func (r *PanenRepository) CekMaster(ctx context.Context, unitID, asm3Oid int64, tphKode string, hrmbCode int64) (model.MasterPanen, error) {
	var blok, tph, pemanen int
	var mandor sql.NullInt64
	err := r.db.QueryRowContext(ctx, `
SELECT
 (SELECT COUNT(*) FROM dbo.blok b WHERE b.unit_id=@p1 AND b.erp_oid=@p2 AND b.dihapus_pada IS NULL),
 (SELECT COUNT(*) FROM dbo.tph t JOIN dbo.blok b ON b.blok_id=t.blok_id
    WHERE b.unit_id=@p1 AND b.erp_oid=@p2 AND b.dihapus_pada IS NULL AND t.no_tph=@p3 AND t.dihapus_pada IS NULL),
 (SELECT COUNT(*) FROM dbo.pemanen p WHERE p.unit_id=@p1 AND p.erp_hrmb_code=@p4 AND p.dihapus_pada IS NULL),
 (SELECT TOP 1 p.mandor_hrmb_code FROM dbo.pemanen p WHERE p.unit_id=@p1 AND p.erp_hrmb_code=@p4 AND p.dihapus_pada IS NULL)`,
		unitID, asm3Oid, tphKode, hrmbCode).Scan(&blok, &tph, &pemanen, &mandor)
	if err != nil {
		return model.MasterPanen{}, err
	}
	return model.MasterPanen{BlokAda: blok > 0, TphAda: tph > 0, PemanenAda: pemanen > 0, PemanenMandor: int64Ptr(mandor)}, nil
}

// AdaKombinasi: apakah sudah ada catatan lain (bukan kecualiID) untuk tanggal + unit + blok + TPH + pemanen yang sama.
func (r *PanenRepository) AdaKombinasi(ctx context.Context, tanggal string, unitID, asm3Oid int64, tphKode string, hrmbCode int64, kecualiID string) (bool, error) {
	var n int
	err := r.db.QueryRowContext(ctx, `
SELECT COUNT(*) FROM dbo.fai_panen
WHERE tanggal_panen=@p1 AND unit_id=@p2 AND asm3_oid=@p3 AND tph_kode=@p4 AND pemanen_hrmb_code=@p5 AND dihapus_pada IS NULL AND id<>@p6`,
		tanggal, unitID, asm3Oid, tphKode, hrmbCode, kecualiID).Scan(&n)
	return n > 0, err
}

// adaPelanggaranUnik: galat SQL Server 2601/2627 (indeks atau kendala unik dilanggar).
func adaPelanggaranUnik(err error) bool {
	var e mssql.Error
	return errors.As(err, &e) && (e.Number == 2601 || e.Number == 2627)
}

// PemilikID mengembalikan pencatat (dibuat_oleh) sebuah catatan, atau ErrNotFound.
func (r *PanenRepository) PemilikID(ctx context.Context, panenID string) (int64, error) {
	var id int64
	err := r.db.QueryRowContext(ctx, `SELECT dibuat_oleh FROM dbo.fai_panen WHERE id=@p1`, panenID).Scan(&id)
	if errors.Is(err, sql.ErrNoRows) {
		return 0, ErrNotFound
	}
	return id, err
}

// Simpan menulis catatan, grading, pemanen, dan audit TERIMA dalam satu transaksi.
func (r *PanenRepository) Simpan(ctx context.Context, p model.PanenSimpan) error {
	in := p.Input
	var gpsWaktu any
	if p.GpsWaktu != nil {
		gpsWaktu = *p.GpsWaktu
	}
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()

	pemanenHrmb := in.Pemanen[0].HrmbCode // Validasi menjamin tepat satu
	// Dengan penyetuju: siap ditarik ERP (BARU). Tanpa penyetuju (mandor mengirim): MENUNGGU persetujuan krani.
	statusAwal := "BARU"
	var penyetuju, disetujuiPada any = p.PenyetujuID, p.DisetujuiPada
	if p.Menunggu {
		statusAwal, penyetuju, disetujuiPada = "MENUNGGU", nil, nil
	}
	if p.Ganti {
		// Catatan DITOLAK (diperbaiki) atau MENUNGGU (diubah sebelum krani menyetujui) dikirim ulang: isi diganti, status ikut
		// hasil penentuan penyetuju (MENUNGGU atau BARU), riwayat impor tetap ada.
		res, e := tx.ExecContext(ctx, `
UPDATE dbo.fai_panen SET device_id=@p2, tanggal_panen=@p3, asm3_oid=@p4, blok_kode=@p5, tph_kode=@p6, rotasi=@p7, kondisi_cuaca=@p8,
  brondolan_kg=@p9, gps_lat=@p10, gps_lon=@p11, gps_akurasi_m=@p12, gps_waktu=@p13, gps_mock=@p14, jarak_ke_tph_m=@p15,
  status_lokasi=@p16, model_versi=@p17, catatan=@p18, status_erp=@p23, disetujui_oleh=@p19, disetujui_pada=@p20,
  pemanen_hrmb_code=@p21, afdeling_kode=NULL, afdeling_erp_oid=NULL, mandor_hrmb_code=NULL,
  diubah_pada=SYSUTCDATETIME(), diubah_oleh=@p22
WHERE id=@p1 AND status_erp IN ('DITOLAK','MENUNGGU') AND dihapus_pada IS NULL`,
			in.PanenID, p.DeviceID, in.TanggalPanen, in.Asm3Oid, nullStr(in.BlokKode), nullStr(in.TphKode),
			ptrAny(in.Rotasi), ptrAny(in.KondisiCuaca), in.BrondolanKg, ptrAny(in.GpsLat), ptrAny(in.GpsLon),
			ptrAny(in.GpsAkurasiM), gpsWaktu, in.GpsMock, ptrAny(in.JarakKeTphM), in.StatusLokasi,
			nullStr(in.ModelVersi), nullStr(in.Catatan), penyetuju, disetujuiPada, pemanenHrmb, p.UserID, statusAwal)
		if e != nil {
			if adaPelanggaranUnik(e) {
				return ErrDuplikat
			}
			return e
		}
		if n, _ := res.RowsAffected(); n != 1 {
			return ErrNotFound // statusnya berubah di tengah jalan (sudah disetujui krani)
		}
		for _, tabel := range []string{"fai_panen_grading", "fai_panen_pemanen"} {
			if _, err = tx.ExecContext(ctx, `UPDATE dbo.`+tabel+` SET dihapus_pada=SYSUTCDATETIME(), dihapus_oleh=@p2 WHERE panen_id=@p1 AND dihapus_pada IS NULL`,
				in.PanenID, p.UserID); err != nil {
				return err
			}
		}
	} else if _, err = tx.ExecContext(ctx, `
INSERT INTO dbo.fai_panen (id, unit_id, device_id, tanggal_panen, asm3_oid, blok_kode, tph_kode, rotasi, kondisi_cuaca,
  brondolan_kg, gps_lat, gps_lon, gps_akurasi_m, gps_waktu, gps_mock, jarak_ke_tph_m, status_lokasi, model_versi, catatan,
  status_erp, disetujui_oleh, disetujui_pada, dibuat_pada, dibuat_oleh, pemanen_hrmb_code)
VALUES (@p1,@p2,@p3,@p4,@p5,@p6,@p7,@p8,@p9,@p10,@p11,@p12,@p13,@p14,@p15,@p16,@p17,@p18,@p19,@p25,@p20,@p21,@p22,@p23,@p24)`,
		in.PanenID, p.UnitID, p.DeviceID, in.TanggalPanen, in.Asm3Oid, nullStr(in.BlokKode), nullStr(in.TphKode),
		ptrAny(in.Rotasi), ptrAny(in.KondisiCuaca), in.BrondolanKg, ptrAny(in.GpsLat), ptrAny(in.GpsLon),
		ptrAny(in.GpsAkurasiM), gpsWaktu, in.GpsMock, ptrAny(in.JarakKeTphM), in.StatusLokasi,
		nullStr(in.ModelVersi), nullStr(in.Catatan), penyetuju, disetujuiPada, p.DibuatPada, p.PencatatID, pemanenHrmb, statusAwal); err != nil {
		if adaPelanggaranUnik(err) {
			return ErrDuplikat
		}
		return err
	}
	for _, g := range in.Grading {
		if _, err = tx.ExecContext(ctx, `
INSERT INTO dbo.fai_panen_grading (panen_id, kelas_kode, erp_kolom, jml_ai, jml_final, diubah_manual, dibuat_oleh)
VALUES (@p1,@p2,@p3,@p4,@p5,@p6,@p7)`,
			in.PanenID, g.KelasKode, nullStr(g.ErpKolom), g.JmlAI, g.JmlFinal, g.DiubahManual, p.PencatatID); err != nil {
			return err
		}
	}
	for _, pm := range in.Pemanen {
		if _, err = tx.ExecContext(ctx, `
INSERT INTO dbo.fai_panen_pemanen (panen_id, hrmb_code, jjg_alokasi, dibuat_oleh) VALUES (@p1,@p2,@p3,@p4)`,
			in.PanenID, pm.HrmbCode, pm.JjgAlokasi, p.PencatatID); err != nil {
			return err
		}
	}
	// Salinan afdeling dan mandor untuk header HVDM, diambil dari master saat catatan diterima (perubahan master di
	// kemudian hari tidak mengubah catatan lama). Master belum lengkap berarti NULL; HVDM yang memutuskan menolaknya.
	// Afdeling: dari akun pencatat bila terisi (dicocokkan ke divisi untuk OID-nya), kalau tidak dari divisi blok.
	if p.AfdelingKode != "" {
		_, err = tx.ExecContext(ctx, `
UPDATE f SET f.afdeling_kode = @p2, f.afdeling_erp_oid = (SELECT TOP 1 d.erp_oid FROM dbo.divisi d WHERE d.unit_id = f.unit_id AND d.kode = @p2 AND d.dihapus_pada IS NULL)
FROM dbo.fai_panen f WHERE f.id = @p1`, in.PanenID, p.AfdelingKode)
	} else {
		_, err = tx.ExecContext(ctx, `
UPDATE f SET f.afdeling_kode = d.kode, f.afdeling_erp_oid = d.erp_oid
FROM dbo.fai_panen f
JOIN dbo.blok b ON b.unit_id = f.unit_id AND b.erp_oid = f.asm3_oid AND b.dihapus_pada IS NULL
JOIN dbo.divisi d ON d.divisi_id = b.divisi_id
WHERE f.id = @p1`, in.PanenID)
	}
	if err != nil {
		return err
	}
	// Mandor: dari akun pencatat bila terisi, kalau tidak dari master pemanen.
	if p.MandorHrmbCode != nil {
		_, err = tx.ExecContext(ctx, `UPDATE dbo.fai_panen SET mandor_hrmb_code = @p2 WHERE id = @p1`, in.PanenID, *p.MandorHrmbCode)
	} else {
		_, err = tx.ExecContext(ctx, `
UPDATE f SET f.mandor_hrmb_code = pm.mandor_hrmb_code
FROM dbo.fai_panen f
JOIN dbo.pemanen pm ON pm.unit_id = f.unit_id AND pm.erp_hrmb_code = @p2 AND pm.dihapus_pada IS NULL
WHERE f.id = @p1`, in.PanenID, pemanenHrmb)
	}
	if err != nil {
		return err
	}
	uid := p.UserID
	aksi := "TERIMA"
	if p.Ganti {
		aksi = "TERIMA_ULANG"
	}
	if err = insertAudit(ctx, tx, model.AuditBaris{
		Tabel: "fai_panen", BarisID: in.PanenID, Aksi: aksi, UserID: &uid, DeviceID: p.DeviceID, IP: p.IP,
	}); err != nil {
		return err
	}
	return tx.Commit()
}

// DaftarStatus mengembalikan status catatan di unit tertentu sejak waktu tertentu. Bila semua false, hanya milik userID.
func (r *PanenRepository) DaftarStatus(ctx context.Context, unitID, userID int64, semua bool, sejak time.Time) ([]model.StatusPanen, error) {
	rows, err := r.db.QueryContext(ctx, `
SELECT CONVERT(NVARCHAR(36), f.id), f.status_erp, COALESCE(f.diubah_pada, f.diterima_server),
  (SELECT TOP 1 l.pesan_error FROM dbo.fai_impor_log l WHERE l.panen_id = f.id ORDER BY l.id DESC)
FROM dbo.fai_panen f
WHERE f.dihapus_pada IS NULL AND f.unit_id=@p1 AND (@p2=1 OR f.dibuat_oleh=@p3)
  AND COALESCE(f.diubah_pada, f.diterima_server) > @p4
ORDER BY COALESCE(f.diubah_pada, f.diterima_server)`, unitID, semua, userID, sejak)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []model.StatusPanen{}
	for rows.Next() {
		var s model.StatusPanen
		if err := rows.Scan(&s.ID, &s.StatusErp, &s.Diubah, &s.Pesan); err != nil {
			return nil, err
		}
		s.ID = strings.ToLower(s.ID)
		s.Status = model.StatusUntukHP(s.StatusErp)
		if s.StatusErp != "DITOLAK" {
			s.Pesan = nil // pesan galat lama tidak relevan lagi (mis. setelah diperbaiki dan dikirim ulang)
		}
		out = append(out, s)
	}
	return out, rows.Err()
}

// Ringkasan menghitung data dashboard HP untuk satu hari. Bila semua false, hanya catatan milik userID.
func (r *PanenRepository) Ringkasan(ctx context.Context, unitID, userID int64, semua bool, hariIni string) (*model.RingkasanPanen, error) {
	out := &model.RingkasanPanen{
		Tanggal:          hariIni,
		PerKelas:         []model.KelasJumlah{},
		PerBlok:          []model.BlokRekap{},
		Tren7Hari:        []model.TrenHarian{},
		CatatanPerStatus: map[string]int64{"TERKIRIM": 0, "DITERIMA": 0, "DITOLAK": 0},
	}
	const lingkup = `f.unit_id=@p1 AND f.dihapus_pada IS NULL AND (@p2=1 OR f.dibuat_oleh=@p3)`

	if err := r.db.QueryRowContext(ctx, `
SELECT COUNT(DISTINCT f.id), COALESCE(SUM(g.jml_final), 0)
FROM dbo.fai_panen f LEFT JOIN dbo.fai_panen_grading g ON g.panen_id=f.id AND g.dihapus_pada IS NULL
WHERE `+lingkup+` AND f.tanggal_panen=@p4`,
		unitID, semua, userID, hariIni).Scan(&out.CatatanHariIni, &out.JanjangHariIni); err != nil {
		return nil, err
	}

	kr, err := r.db.QueryContext(ctx, `
SELECT g.kelas_kode, COALESCE(SUM(g.jml_final),0)
FROM dbo.fai_panen f JOIN dbo.fai_panen_grading g ON g.panen_id=f.id AND g.dihapus_pada IS NULL
WHERE `+lingkup+` AND f.tanggal_panen=@p4
GROUP BY g.kelas_kode ORDER BY SUM(g.jml_final) DESC`, unitID, semua, userID, hariIni)
	if err != nil {
		return nil, err
	}
	for kr.Next() {
		var k model.KelasJumlah
		if err := kr.Scan(&k.KelasKode, &k.Janjang); err != nil {
			kr.Close()
			return nil, err
		}
		out.PerKelas = append(out.PerKelas, k)
	}
	kr.Close()

	br, err := r.db.QueryContext(ctx, `
SELECT TOP 5 COALESCE(f.blok_kode,'-'), COUNT(DISTINCT f.tph_kode), COUNT(DISTINCT f.id), COALESCE(SUM(g.jml_final),0)
FROM dbo.fai_panen f LEFT JOIN dbo.fai_panen_grading g ON g.panen_id=f.id AND g.dihapus_pada IS NULL
WHERE `+lingkup+` AND f.tanggal_panen=@p4
GROUP BY COALESCE(f.blok_kode,'-') ORDER BY COALESCE(SUM(g.jml_final),0) DESC`, unitID, semua, userID, hariIni)
	if err != nil {
		return nil, err
	}
	for br.Next() {
		var b model.BlokRekap
		if err := br.Scan(&b.BlokKode, &b.Tph, &b.Catatan, &b.Janjang); err != nil {
			br.Close()
			return nil, err
		}
		out.PerBlok = append(out.PerBlok, b)
	}
	br.Close()

	tr, err := r.db.QueryContext(ctx, `
SELECT CONVERT(NVARCHAR(10), f.tanggal_panen, 23), COUNT(DISTINCT f.id), COALESCE(SUM(g.jml_final),0)
FROM dbo.fai_panen f LEFT JOIN dbo.fai_panen_grading g ON g.panen_id=f.id AND g.dihapus_pada IS NULL
WHERE `+lingkup+` AND f.tanggal_panen > DATEADD(DAY,-7,CAST(@p4 AS DATE)) AND f.tanggal_panen <= CAST(@p4 AS DATE)
GROUP BY f.tanggal_panen ORDER BY f.tanggal_panen`, unitID, semua, userID, hariIni)
	if err != nil {
		return nil, err
	}
	for tr.Next() {
		var t model.TrenHarian
		if err := tr.Scan(&t.Tanggal, &t.Catatan, &t.Janjang); err != nil {
			tr.Close()
			return nil, err
		}
		out.Tren7Hari = append(out.Tren7Hari, t)
	}
	tr.Close()

	sr, err := r.db.QueryContext(ctx, `
SELECT f.status_erp, COUNT(*) FROM dbo.fai_panen f WHERE `+lingkup+` GROUP BY f.status_erp`, unitID, semua, userID)
	if err != nil {
		return nil, err
	}
	defer sr.Close()
	for sr.Next() {
		var s string
		var n int64
		if err := sr.Scan(&s, &n); err != nil {
			return nil, err
		}
		out.CatatanPerStatus[model.StatusUntukHP(s)] += n
	}
	return out, sr.Err()
}

// DaftarMenunggu mengembalikan catatan unit yang menunggu persetujuan, terlama dulu.
func (r *PanenRepository) DaftarMenunggu(ctx context.Context, unitID int64) ([]model.PanenMenunggu, error) {
	rows, err := r.db.QueryContext(ctx, `
SELECT CONVERT(NVARCHAR(36), f.id), CONVERT(NVARCHAR(10), f.tanggal_panen, 23), f.blok_kode, f.tph_kode, f.afdeling_kode,
  COALESCE(u.username, ''), COALESCE(u.nama, ''), pm.nama, f.pemanen_hrmb_code, f.brondolan_kg, f.catatan, f.status_lokasi,
  f.jarak_ke_tph_m, f.gps_mock, f.dibuat_pada,
  (SELECT TOP 1 CONVERT(NVARCHAR(36), o.id) FROM dbo.fai_foto o
    WHERE o.panen_id = f.id AND o.dihapus_pada IS NULL
    ORDER BY CASE o.jenis WHEN 'DETEKSI' THEN 0 ELSE 1 END, o.dibuat_pada)
FROM dbo.fai_panen f
LEFT JOIN dbo.app_user u ON u.user_id = f.dibuat_oleh
LEFT JOIN dbo.pemanen pm ON pm.unit_id = f.unit_id AND pm.erp_hrmb_code = f.pemanen_hrmb_code AND pm.dihapus_pada IS NULL
WHERE f.unit_id=@p1 AND f.status_erp='MENUNGGU' AND f.dihapus_pada IS NULL
ORDER BY f.dibuat_pada, f.diterima_server`, unitID)
	if err != nil {
		return nil, err
	}
	out := []model.PanenMenunggu{}
	idx := map[string]int{}
	for rows.Next() {
		var x model.PanenMenunggu
		var blok, tph, afd, pemanen, catatan, foto sql.NullString
		var hrmb sql.NullInt64
		var jarak sql.NullFloat64
		if err := rows.Scan(&x.ID, &x.TanggalPanen, &blok, &tph, &afd, &x.Pencatat, &x.PencatatNama, &pemanen, &hrmb,
			&x.BrondolanKg, &catatan, &x.StatusLokasi, &jarak, &x.GpsMock, &x.DibuatPada, &foto); err != nil {
			rows.Close()
			return nil, err
		}
		x.ID = strings.ToLower(x.ID)
		x.BlokKode, x.TphKode, x.AfdelingKode, x.PemanenNama, x.Catatan = strPtr(blok), strPtr(tph), strPtr(afd), strPtr(pemanen), strPtr(catatan)
		x.PemanenHrmb = int64Ptr(hrmb)
		if foto.Valid {
			v := strings.ToLower(foto.String)
			x.FotoID = &v
		}
		if jarak.Valid {
			v := jarak.Float64
			x.JarakKeTphM = &v
		}
		x.Grading = []model.KelasJumlah{}
		idx[x.ID] = len(out)
		out = append(out, x)
	}
	if err := rows.Err(); err != nil {
		rows.Close()
		return nil, err
	}
	rows.Close()
	if len(out) == 0 {
		return out, nil
	}
	gr, err := r.db.QueryContext(ctx, `
SELECT CONVERT(NVARCHAR(36), g.panen_id), g.kelas_kode, g.jml_final
FROM dbo.fai_panen_grading g JOIN dbo.fai_panen f ON f.id = g.panen_id
WHERE f.unit_id=@p1 AND f.status_erp='MENUNGGU' AND f.dihapus_pada IS NULL AND g.dihapus_pada IS NULL AND g.jml_final > 0
ORDER BY g.panen_id, g.id`, unitID)
	if err != nil {
		return nil, err
	}
	defer gr.Close()
	for gr.Next() {
		var id, kelas string
		var n int64
		if err := gr.Scan(&id, &kelas, &n); err != nil {
			return nil, err
		}
		if i, ok := idx[strings.ToLower(id)]; ok {
			out[i].Grading = append(out[i].Grading, model.KelasJumlah{KelasKode: kelas, Janjang: n})
			out[i].TotalJanjang += n
		}
	}
	return out, gr.Err()
}

// ubahMenunggu memindahkan catatan MENUNGGU di unit itu ke status baru dalam satu transaksi (dengan audit). Mengembalikan
// ErrNotFound bila catatan tidak ada, ErrStatusBerubah (dan status sekarang) bila bukan lagi MENUNGGU.
func (r *PanenRepository) ubahMenunggu(ctx context.Context, panenID string, unitID, userID int64, statusBaru, aksi, keterangan, deviceID, ip string) (string, error) {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return "", err
	}
	defer tx.Rollback()
	setuju := ""
	if statusBaru == "BARU" {
		setuju = ", disetujui_oleh=@p3, disetujui_pada=SYSUTCDATETIME()"
	}
	res, err := tx.ExecContext(ctx, `
UPDATE dbo.fai_panen SET status_erp=@p4`+setuju+`, diubah_pada=SYSUTCDATETIME(), diubah_oleh=@p3
WHERE id=@p1 AND unit_id=@p2 AND status_erp='MENUNGGU' AND dihapus_pada IS NULL`, panenID, unitID, userID, statusBaru)
	if err != nil {
		return "", err
	}
	if n, _ := res.RowsAffected(); n != 1 {
		var sekarang string
		e := tx.QueryRowContext(ctx, `SELECT status_erp FROM dbo.fai_panen WHERE id=@p1 AND unit_id=@p2 AND dihapus_pada IS NULL`, panenID, unitID).Scan(&sekarang)
		if errors.Is(e, sql.ErrNoRows) {
			return "", ErrNotFound
		}
		if e != nil {
			return "", e
		}
		return sekarang, ErrStatusBerubah
	}
	if statusBaru == "DITOLAK" {
		if _, err := tx.ExecContext(ctx, `
INSERT INTO dbo.fai_impor_log (panen_id, waktu_impor, hasil, pesan_error, diimpor_oleh) VALUES (@p1, SYSUTCDATETIME(), 'GAGAL', @p2, @p3)`,
			panenID, keterangan, userID); err != nil {
			return "", err
		}
	}
	uid := userID
	if err := insertAudit(ctx, tx, model.AuditBaris{Tabel: "fai_panen", BarisID: panenID, Aksi: aksi, UserID: &uid, DeviceID: deviceID, IP: ip, Keterangan: keterangan}); err != nil {
		return "", err
	}
	return statusBaru, tx.Commit()
}

// Setujui: MENUNGGU -> BARU (siap ditarik ERP), tercatat atas nama penyetuju.
func (r *PanenRepository) Setujui(ctx context.Context, panenID string, unitID, userID int64, deviceID, ip string) (string, error) {
	return r.ubahMenunggu(ctx, panenID, unitID, userID, "BARU", "SETUJU", "", deviceID, ip)
}

// Tolak: MENUNGGU -> DITOLAK, alasan disimpan sebagai pesan galat terakhir (tampil di HP pencatat).
func (r *PanenRepository) Tolak(ctx context.Context, panenID string, unitID, userID int64, alasan, deviceID, ip string) (string, error) {
	return r.ubahMenunggu(ctx, panenID, unitID, userID, "DITOLAK", "TOLAK", alasan, deviceID, ip)
}
