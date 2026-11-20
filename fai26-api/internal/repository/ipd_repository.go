package repository

import (
	"context"
	"database/sql"
	"errors"
	"strconv"
	"strings"
	"time"

	"fai26-api/internal/model"
)

// ErrBukanPenyetuju: catatan MENUNGGU tetapi ditujukan ke asisten lain.
var ErrBukanPenyetuju = errors.New("catatan ditujukan ke asisten lain")

type IpdRepository struct{ db *sql.DB }

func NewIpdRepository(db *sql.DB) *IpdRepository { return &IpdRepository{db: db} }

// ---- Pemeriksaan sebelum simpan ------------------------------------------------------------------

// Cari mengembalikan status dan pemilik catatan, atau nil bila belum ada.
func (r *IpdRepository) Cari(ctx context.Context, id string) (*model.IpdAda, error) {
	var a model.IpdAda
	err := r.db.QueryRowContext(ctx, `SELECT status, dibuat_oleh FROM dbo.fai_ipd WHERE id=@p1 AND dihapus_pada IS NULL`, id).Scan(&a.Status, &a.PemilikID)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	return &a, err
}

// CekMaster memeriksa divisi (kode), blok (asm3_oid) dan pemanen (HRMBCODE) terhadap master unit.
func (r *IpdRepository) CekMaster(ctx context.Context, unitID int64, divisiKode string, asm3Oid, hrmb int64) (model.IpdMaster, error) {
	var m model.IpdMaster
	var asisten sql.NullInt64
	err := r.db.QueryRowContext(ctx, `SELECT divisi_id, asisten_user_id FROM dbo.divisi WHERE unit_id=@p1 AND kode=@p2 AND dihapus_pada IS NULL AND aktif=1`,
		unitID, divisiKode).Scan(&m.DivisiID, &asisten)
	switch {
	case err == nil:
		m.DivisiAda = true
		m.AsistenID = int64Ptr(asisten)
	case !errors.Is(err, sql.ErrNoRows):
		return m, err
	}
	var blokDivisi sql.NullInt64
	err = r.db.QueryRowContext(ctx, `SELECT TOP 1 divisi_id FROM dbo.blok WHERE unit_id=@p1 AND erp_oid=@p2 AND dihapus_pada IS NULL`, unitID, asm3Oid).Scan(&blokDivisi)
	switch {
	case err == nil:
		m.BlokAda = true
		m.BlokDalamDivisi = m.DivisiAda && blokDivisi.Valid && blokDivisi.Int64 == m.DivisiID
	case !errors.Is(err, sql.ErrNoRows):
		return m, err
	}
	var n int
	if err = r.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM dbo.pemanen WHERE unit_id=@p1 AND erp_hrmb_code=@p2 AND dihapus_pada IS NULL`, unitID, hrmb).Scan(&n); err != nil {
		return m, err
	}
	m.PemanenAda = n > 0
	return m, nil
}

// KodeAktif mengembalikan kode temuan yang aktif: kode -> kode_id.
func (r *IpdRepository) KodeAktif(ctx context.Context) (map[string]int64, error) {
	rows, err := r.db.QueryContext(ctx, `SELECT kode, kode_id FROM dbo.ipd_kode_temuan WHERE aktif=1 AND dihapus_pada IS NULL`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := map[string]int64{}
	for rows.Next() {
		var k string
		var id int64
		if err := rows.Scan(&k, &id); err != nil {
			return nil, err
		}
		out[strings.ToUpper(k)] = id
	}
	return out, rows.Err()
}

// KodeMaster mengembalikan semua kode temuan yang aktif, berurutan seperti di form.
func (r *IpdRepository) KodeMaster(ctx context.Context) ([]model.IpdKodeMaster, error) {
	rows, err := r.db.QueryContext(ctx, `SELECT kode, nama, kelompok, urut FROM dbo.ipd_kode_temuan WHERE aktif=1 AND dihapus_pada IS NULL ORDER BY urut, kode_id`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []model.IpdKodeMaster
	for rows.Next() {
		var k model.IpdKodeMaster
		if err := rows.Scan(&k.Kode, &k.Nama, &k.Kelompok, &k.Urut); err != nil {
			return nil, err
		}
		out = append(out, k)
	}
	return out, rows.Err()
}

// Mandor mencari akun aktif di unit itu yang berperan MANDOR. ErrNotFound bila tidak ada.
func (r *IpdRepository) Mandor(ctx context.Context, unitID int64, username string) (model.IpdOrang, error) {
	var o model.IpdOrang
	var hrmb sql.NullInt64
	err := r.db.QueryRowContext(ctx, `
SELECT u.user_id, u.mandor_hrmb_code FROM dbo.app_user u
WHERE u.unit_id=@p1 AND u.username=@p2 AND u.aktif=1 AND u.dihapus_pada IS NULL
  AND EXISTS (SELECT 1 FROM dbo.user_role ur JOIN dbo.role r ON r.role_id = ur.role_id
              WHERE ur.user_id = u.user_id AND ur.dihapus_pada IS NULL AND r.kode='MANDOR' AND r.dihapus_pada IS NULL)`,
		unitID, username).Scan(&o.UserID, &hrmb)
	if errors.Is(err, sql.ErrNoRows) {
		return o, ErrNotFound
	}
	o.Hrmb = int64Ptr(hrmb)
	return o, err
}

// ---- Simpan --------------------------------------------------------------------------------------

const (
	chunkPokok  = 400 // 4 parameter per baris, batas driver 2100
	chunkTemuan = 300 // 5 parameter per baris
)

// Simpan menulis catatan, pokok, temuan, dan audit dalam satu transaksi. Ganti=true memperbarui catatan MENUNGGU atau
// DITOLAK yang sudah ada (ErrNotFound bila statusnya sudah berubah, mis. krani/asisten sudah menyetujui).
func (r *IpdRepository) Simpan(ctx context.Context, p model.IpdSimpan) error {
	in := p.Input
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()

	status := "DISETUJUI"
	var disetujuiOleh, disetujuiPada any = p.UserID, time.Now().UTC()
	if p.Menunggu {
		status, disetujuiOleh, disetujuiPada = "MENUNGGU", nil, nil
	}
	var asisten any
	if p.AsistenID != nil {
		asisten = *p.AsistenID
	}
	var hrmbBlok, hrmbAncak any
	if p.MandorBlok.Hrmb != nil {
		hrmbBlok = *p.MandorBlok.Hrmb
	}
	if p.MandorAncak.Hrmb != nil {
		hrmbAncak = *p.MandorAncak.Hrmb
	}
	validPada := time.Now().UTC()

	if p.Ganti {
		res, e := tx.ExecContext(ctx, `
UPDATE dbo.fai_ipd SET device_id=@p2, tanggal_inspeksi=@p3, asm3_oid=@p4, blok_kode=@p5, divisi_id=@p6, asisten_user_id=@p7,
  pemanen_hrmb_code=@p8, ancak_no=@p9, mandor_blok_user_id=@p10, mandor_blok_hrmb=@p11, mandor_ancak_user_id=@p12, mandor_ancak_hrmb=@p13,
  catatan=@p14, status=@p15, pesan_tolak=NULL, divalidasi_oleh=@p16, divalidasi_pada=@p17, disetujui_oleh=@p18, disetujui_pada=@p19,
  diubah_pada=SYSUTCDATETIME(), diubah_oleh=@p16
WHERE id=@p1 AND status IN ('MENUNGGU','DITOLAK') AND dihapus_pada IS NULL`,
			in.IpdID, p.DeviceID, in.TanggalInspeksi, in.Asm3Oid, nullStr(in.BlokKode), p.DivisiID, asisten,
			in.PemanenHrmbCode, nullStr(in.AncakNo), p.MandorBlok.UserID, hrmbBlok, p.MandorAncak.UserID, hrmbAncak,
			nullStr(in.Catatan), status, p.UserID, validPada, disetujuiOleh, disetujuiPada)
		if e != nil {
			return e
		}
		if n, _ := res.RowsAffected(); n != 1 {
			return ErrNotFound // statusnya berubah di tengah jalan (sudah disetujui)
		}
		if _, e = tx.ExecContext(ctx, `
UPDATE t SET t.dihapus_pada=SYSUTCDATETIME(), t.dihapus_oleh=@p2 FROM dbo.fai_ipd_temuan t
JOIN dbo.fai_ipd_pokok k ON k.id = t.pokok_id WHERE k.ipd_id=@p1 AND t.dihapus_pada IS NULL`, in.IpdID, p.UserID); e != nil {
			return e
		}
		if _, e = tx.ExecContext(ctx, `UPDATE dbo.fai_ipd_pokok SET dihapus_pada=SYSUTCDATETIME(), dihapus_oleh=@p2 WHERE ipd_id=@p1 AND dihapus_pada IS NULL`,
			in.IpdID, p.UserID); e != nil {
			return e
		}
	} else if _, err = tx.ExecContext(ctx, `
INSERT INTO dbo.fai_ipd (id, unit_id, device_id, tanggal_inspeksi, asm3_oid, blok_kode, divisi_id, asisten_user_id, pemanen_hrmb_code, ancak_no,
  mandor_blok_user_id, mandor_blok_hrmb, mandor_ancak_user_id, mandor_ancak_hrmb, catatan, status, divalidasi_oleh, divalidasi_pada,
  disetujui_oleh, disetujui_pada, dibuat_pada, dibuat_oleh)
VALUES (@p1,@p2,@p3,@p4,@p5,@p6,@p7,@p8,@p9,@p10,@p11,@p12,@p13,@p14,@p15,@p16,@p17,@p18,@p19,@p20,@p21,@p22)`,
		in.IpdID, p.UnitID, p.DeviceID, in.TanggalInspeksi, in.Asm3Oid, nullStr(in.BlokKode), p.DivisiID, asisten, in.PemanenHrmbCode, nullStr(in.AncakNo),
		p.MandorBlok.UserID, hrmbBlok, p.MandorAncak.UserID, hrmbAncak, nullStr(in.Catatan), status, p.UserID, validPada,
		disetujuiOleh, disetujuiPada, p.DibuatPada, p.UserID); err != nil {
		return err
	}

	// Pokok: sekaligus per kelompok baris, lalu id-nya dibaca kembali untuk menautkan temuan.
	for a := 0; a < len(in.Pokok); a += chunkPokok {
		z := min(a+chunkPokok, len(in.Pokok))
		var sb strings.Builder
		sb.WriteString("INSERT INTO dbo.fai_ipd_pokok (ipd_id, no_baris, no_pokok, dibuat_oleh) VALUES ")
		args := make([]any, 0, (z-a)*4)
		for i := a; i < z; i++ {
			if i > a {
				sb.WriteByte(',')
			}
			n := len(args)
			sb.WriteString("(@p" + strconv.Itoa(n+1) + ",@p" + strconv.Itoa(n+2) + ",@p" + strconv.Itoa(n+3) + ",@p" + strconv.Itoa(n+4) + ")")
			args = append(args, in.IpdID, in.Pokok[i].NoBaris, in.Pokok[i].NoPokok, p.UserID)
		}
		if _, err = tx.ExecContext(ctx, sb.String(), args...); err != nil {
			if adaPelanggaranUnik(err) {
				return ErrDuplikat
			}
			return err
		}
	}
	idPokok := map[[2]int]int64{}
	rows, err := tx.QueryContext(ctx, `SELECT id, no_baris, no_pokok FROM dbo.fai_ipd_pokok WHERE ipd_id=@p1 AND dihapus_pada IS NULL`, in.IpdID)
	if err != nil {
		return err
	}
	for rows.Next() {
		var id int64
		var b, n int
		if err = rows.Scan(&id, &b, &n); err != nil {
			rows.Close()
			return err
		}
		idPokok[[2]int{b, n}] = id
	}
	if err = rows.Err(); err != nil {
		rows.Close()
		return err
	}
	rows.Close()

	type temuanBaris struct {
		pokok, kode int64
		jumlah      int
		catatan     any
	}
	var semua []temuanBaris
	for _, pk := range in.Pokok {
		for _, t := range pk.Temuan {
			semua = append(semua, temuanBaris{idPokok[[2]int{pk.NoBaris, pk.NoPokok}], p.KodeID[t.Kode], t.Jumlah, nullStr(t.Catatan)})
		}
	}
	for a := 0; a < len(semua); a += chunkTemuan {
		z := min(a+chunkTemuan, len(semua))
		var sb strings.Builder
		sb.WriteString("INSERT INTO dbo.fai_ipd_temuan (pokok_id, kode_id, jumlah, catatan, dibuat_oleh) VALUES ")
		args := make([]any, 0, (z-a)*5)
		for i := a; i < z; i++ {
			if i > a {
				sb.WriteByte(',')
			}
			n := len(args)
			sb.WriteString("(@p" + strconv.Itoa(n+1) + ",@p" + strconv.Itoa(n+2) + ",@p" + strconv.Itoa(n+3) + ",@p" + strconv.Itoa(n+4) + ",@p" + strconv.Itoa(n+5) + ")")
			args = append(args, semua[i].pokok, semua[i].kode, semua[i].jumlah, semua[i].catatan, p.UserID)
		}
		if _, err = tx.ExecContext(ctx, sb.String(), args...); err != nil {
			return err
		}
	}

	uid := p.UserID
	aksi := "TERIMA"
	if p.Ganti {
		aksi = "TERIMA_ULANG"
	}
	if err = insertAudit(ctx, tx, model.AuditBaris{Tabel: "fai_ipd", BarisID: in.IpdID, Aksi: aksi, UserID: &uid, DeviceID: p.DeviceID, IP: p.IP}); err != nil {
		return err
	}
	if !p.Menunggu {
		if err = insertAudit(ctx, tx, model.AuditBaris{Tabel: "fai_ipd", BarisID: in.IpdID, Aksi: "SETUJU", UserID: &uid, DeviceID: p.DeviceID, IP: p.IP,
			Keterangan: "inspektur memegang ipd.setujui"}); err != nil {
			return err
		}
	}
	return tx.Commit()
}

// ---- Status untuk HP pengirim -----------------------------------------------------------------

func (r *IpdRepository) DaftarStatus(ctx context.Context, unitID, userID int64, sejak time.Time) ([]model.IpdStatus, error) {
	rows, err := r.db.QueryContext(ctx, `
SELECT CONVERT(NVARCHAR(36), id), status, pesan_tolak, COALESCE(diubah_pada, diterima_server)
FROM dbo.fai_ipd WHERE dihapus_pada IS NULL AND unit_id=@p1 AND dibuat_oleh=@p2 AND COALESCE(diubah_pada, diterima_server) > @p3
ORDER BY COALESCE(diubah_pada, diterima_server)`, unitID, userID, sejak)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []model.IpdStatus{}
	for rows.Next() {
		var s model.IpdStatus
		var pesan sql.NullString
		if err := rows.Scan(&s.ID, &s.StatusServer, &pesan, &s.Diubah); err != nil {
			return nil, err
		}
		s.ID = strings.ToLower(s.ID)
		s.Status = model.IpdStatusUntukHP(s.StatusServer)
		if pesan.Valid {
			v := pesan.String
			s.Pesan = &v
		}
		out = append(out, s)
	}
	return out, rows.Err()
}

// ---- Daftar dan rincian -------------------------------------------------------------------------

const dasarIpd = `
SELECT CONVERT(NVARCHAR(36), f.id), CONVERT(NVARCHAR(10), f.tanggal_inspeksi, 23), dv.kode, f.blok_kode, f.pemanen_hrmb_code, pm.nama, f.ancak_no,
  ui.username, ui.nama, mb.nama, ma.nama, asn.nama, f.status, f.pesan_tolak, f.catatan, f.dibuat_pada, f.disetujui_pada
FROM dbo.fai_ipd f
JOIN dbo.divisi dv ON dv.divisi_id = f.divisi_id
JOIN dbo.app_user ui ON ui.user_id = f.dibuat_oleh
LEFT JOIN dbo.app_user mb ON mb.user_id = f.mandor_blok_user_id
LEFT JOIN dbo.app_user ma ON ma.user_id = f.mandor_ancak_user_id
LEFT JOIN dbo.app_user asn ON asn.user_id = f.asisten_user_id
OUTER APPLY (SELECT TOP 1 p.nama FROM dbo.pemanen p WHERE p.unit_id = f.unit_id AND p.erp_hrmb_code = f.pemanen_hrmb_code AND p.dihapus_pada IS NULL ORDER BY p.pemanen_id) pm
`

func (r *IpdRepository) bacaBaris(ctx context.Context, q string, args ...any) ([]model.IpdBaris, error) {
	rows, err := r.db.QueryContext(ctx, q, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []model.IpdBaris{}
	for rows.Next() {
		var b model.IpdBaris
		var blok, pemanen, ancak, mb, ma, asn, tolak, catatan sql.NullString
		var disetujui sql.NullTime
		if err := rows.Scan(&b.ID, &b.TanggalInspeksi, &b.DivisiKode, &blok, &b.PemanenHrmbCode, &pemanen, &ancak, &b.Inspektur, &b.InspekturNama,
			&mb, &ma, &asn, &b.Status, &tolak, &catatan, &b.DibuatPada, &disetujui); err != nil {
			return nil, err
		}
		b.ID = strings.ToLower(b.ID)
		b.BlokKode, b.PemanenNama, b.AncakNo = strPtr(blok), strPtr(pemanen), strPtr(ancak)
		b.MandorBlokNama, b.MandorAncakNama, b.AsistenNama = strPtr(mb), strPtr(ma), strPtr(asn)
		b.PesanTolak, b.Catatan = strPtr(tolak), strPtr(catatan)
		if disetujui.Valid {
			t := disetujui.Time
			b.DisetujuiPada = &t
		}
		out = append(out, b)
	}
	return out, rows.Err()
}

// DaftarMenunggu: inspeksi MENUNGGU yang ditujukan ke asisten itu (atau divisinya belum punya asisten), terlama dulu.
func (r *IpdRepository) DaftarMenunggu(ctx context.Context, unitID, userID int64) ([]model.IpdBaris, error) {
	return r.bacaBaris(ctx, dasarIpd+`
WHERE f.dihapus_pada IS NULL AND f.unit_id=@p1 AND f.status='MENUNGGU' AND (f.asisten_user_id=@p2 OR f.asisten_user_id IS NULL)
ORDER BY f.dibuat_pada`, unitID, userID)
}

// DaftarAdmin: daftar inspeksi untuk web dengan penyaring dan halaman.
func (r *IpdRepository) DaftarAdmin(ctx context.Context, unitID int64, f model.FilterIpd) (*model.HalamanIpd, error) {
	var w strings.Builder
	args := []any{unitID}
	w.WriteString("f.dihapus_pada IS NULL AND f.unit_id=@p1")
	tambah := func(cond string, v any) {
		args = append(args, v)
		w.WriteString(" AND " + strings.Replace(cond, "?", "@p"+strconv.Itoa(len(args)), 1))
	}
	if f.Dari != "" {
		tambah("f.tanggal_inspeksi >= ?", f.Dari)
	}
	if f.Sampai != "" {
		tambah("f.tanggal_inspeksi <= ?", f.Sampai)
	}
	if f.Status != "" {
		tambah("f.status = ?", f.Status)
	}
	if f.DivisiKode != "" {
		tambah("dv.kode = ?", f.DivisiKode)
	}
	if f.BlokKode != "" {
		tambah("f.blok_kode = ?", f.BlokKode)
	}
	if f.PemanenHrmb > 0 {
		tambah("f.pemanen_hrmb_code = ?", f.PemanenHrmb)
	}
	var total int64
	if err := r.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM dbo.fai_ipd f JOIN dbo.divisi dv ON dv.divisi_id=f.divisi_id WHERE `+w.String(), args...).Scan(&total); err != nil {
		return nil, err
	}
	args = append(args, (f.Halaman-1)*f.PerHal, f.PerHal)
	data, err := r.bacaBaris(ctx, dasarIpd+"WHERE "+w.String()+" ORDER BY f.tanggal_inspeksi DESC, f.dibuat_pada DESC OFFSET @p"+
		strconv.Itoa(len(args)-1)+" ROWS FETCH NEXT @p"+strconv.Itoa(len(args))+" ROWS ONLY", args...)
	if err != nil {
		return nil, err
	}
	return &model.HalamanIpd{Item: data, Total: total, Halaman: f.Halaman, PerHal: f.PerHal}, nil
}

// Baris mengembalikan satu inspeksi di unit itu, atau ErrNotFound.
func (r *IpdRepository) Baris(ctx context.Context, unitID int64, id string) (*model.IpdBaris, error) {
	b, err := r.bacaBaris(ctx, dasarIpd+"WHERE f.dihapus_pada IS NULL AND f.unit_id=@p1 AND f.id=@p2", unitID, id)
	if err != nil {
		return nil, err
	}
	if len(b) == 0 {
		return nil, ErrNotFound
	}
	return &b[0], nil
}

// Pokok mengembalikan semua pokok yang diperiksa pada satu inspeksi beserta temuannya.
func (r *IpdRepository) Pokok(ctx context.Context, id string) ([]model.IpdPokokOut, error) {
	rows, err := r.db.QueryContext(ctx, `
SELECT p.no_baris, p.no_pokok, k.kode, t.jumlah, t.catatan
FROM dbo.fai_ipd_pokok p
LEFT JOIN dbo.fai_ipd_temuan t ON t.pokok_id = p.id AND t.dihapus_pada IS NULL
LEFT JOIN dbo.ipd_kode_temuan k ON k.kode_id = t.kode_id
WHERE p.ipd_id=@p1 AND p.dihapus_pada IS NULL ORDER BY p.no_baris, p.no_pokok, k.urut`, id)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []model.IpdPokokOut{}
	for rows.Next() {
		var b, n int
		var kode, cat sql.NullString
		var jml sql.NullInt64
		if err := rows.Scan(&b, &n, &kode, &jml, &cat); err != nil {
			return nil, err
		}
		if len(out) == 0 || out[len(out)-1].NoBaris != b || out[len(out)-1].NoPokok != n {
			out = append(out, model.IpdPokokOut{NoBaris: b, NoPokok: n, Temuan: []model.IpdTemuanOut{}})
		}
		if kode.Valid {
			last := &out[len(out)-1]
			last.Temuan = append(last.Temuan, model.IpdTemuanOut{Kode: kode.String, Jumlah: jml.Int64, Catatan: strPtr(cat)})
		}
	}
	return out, rows.Err()
}

// Mentah menghitung pokok dan temuan per inspeksi (id -> hitungan) untuk daftar id yang diberikan.
func (r *IpdRepository) Mentah(ctx context.Context, ids []string) (map[string]*model.IpdMentah, error) {
	if len(ids) == 0 {
		return map[string]*model.IpdMentah{}, nil
	}
	ph := make([]string, len(ids))
	args := make([]any, len(ids))
	for i, id := range ids {
		ph[i] = "@p" + strconv.Itoa(i+1)
		args[i] = id
	}
	return r.mentah(ctx, "f.id IN ("+strings.Join(ph, ",")+")", args)
}

// mentah menjalankan tiga hitungan (pokok, per kode, per kelompok) untuk inspeksi yang memenuhi syarat where (alias f = fai_ipd).
func (r *IpdRepository) mentah(ctx context.Context, where string, args []any) (map[string]*model.IpdMentah, error) {
	out := map[string]*model.IpdMentah{}
	ambil := func(id string) *model.IpdMentah {
		id = strings.ToLower(id)
		m, ok := out[id]
		if !ok {
			m = &model.IpdMentah{PerKode: map[string]model.IpdHitung{}, PerKelompok: map[string]model.IpdHitung{}}
			out[id] = m
		}
		return m
	}
	rows, err := r.db.QueryContext(ctx, `
SELECT CONVERT(NVARCHAR(36), x.ipd_id), COUNT(*), SUM(x.ada), SUM(x.loses), SUM(x.lain)
FROM (SELECT p.ipd_id, CASE WHEN EXISTS (SELECT 1 FROM dbo.fai_ipd_temuan t WHERE t.pokok_id = p.id AND t.dihapus_pada IS NULL) THEN 1 ELSE 0 END AS ada,
        CASE WHEN EXISTS (SELECT 1 FROM dbo.fai_ipd_temuan t JOIN dbo.ipd_kode_temuan k ON k.kode_id = t.kode_id
                          WHERE t.pokok_id = p.id AND t.dihapus_pada IS NULL AND k.kelompok <> 'PELEPAH') THEN 1 ELSE 0 END AS loses,
        CASE WHEN EXISTS (SELECT 1 FROM dbo.fai_ipd_temuan t JOIN dbo.ipd_kode_temuan k ON k.kode_id = t.kode_id
                          WHERE t.pokok_id = p.id AND t.dihapus_pada IS NULL AND k.kelompok = 'PELEPAH') THEN 1 ELSE 0 END AS lain
      FROM dbo.fai_ipd_pokok p JOIN dbo.fai_ipd f ON f.id = p.ipd_id
      WHERE p.dihapus_pada IS NULL AND `+where+`) x GROUP BY x.ipd_id`, args...)
	if err != nil {
		return nil, err
	}
	for rows.Next() {
		var id string
		var n, b, lo, la int64
		if err := rows.Scan(&id, &n, &b, &lo, &la); err != nil {
			rows.Close()
			return nil, err
		}
		m := ambil(id)
		m.PokokDiperiksa, m.PokokBertemuan, m.PokokLoses, m.PokokLain = n, b, lo, la
	}
	rows.Close()

	rows, err = r.db.QueryContext(ctx, `
SELECT CONVERT(NVARCHAR(36), p.ipd_id), k.kode, k.kelompok, COUNT(*), SUM(t.jumlah)
FROM dbo.fai_ipd_temuan t
JOIN dbo.fai_ipd_pokok p ON p.id = t.pokok_id AND p.dihapus_pada IS NULL
JOIN dbo.fai_ipd f ON f.id = p.ipd_id
JOIN dbo.ipd_kode_temuan k ON k.kode_id = t.kode_id
WHERE t.dihapus_pada IS NULL AND `+where+` GROUP BY p.ipd_id, k.kode, k.kelompok`, args...)
	if err != nil {
		return nil, err
	}
	for rows.Next() {
		var id, kode, kel string
		var n, jml int64
		if err := rows.Scan(&id, &kode, &kel, &n, &jml); err != nil {
			rows.Close()
			return nil, err
		}
		ambil(id).PerKode[kode] = model.IpdHitung{Pokok: n, Jumlah: jml}
		_ = kel
	}
	rows.Close()

	rows, err = r.db.QueryContext(ctx, `
SELECT CONVERT(NVARCHAR(36), p.ipd_id), k.kelompok, COUNT(DISTINCT t.pokok_id), SUM(t.jumlah)
FROM dbo.fai_ipd_temuan t
JOIN dbo.fai_ipd_pokok p ON p.id = t.pokok_id AND p.dihapus_pada IS NULL
JOIN dbo.fai_ipd f ON f.id = p.ipd_id
JOIN dbo.ipd_kode_temuan k ON k.kode_id = t.kode_id
WHERE t.dihapus_pada IS NULL AND `+where+` GROUP BY p.ipd_id, k.kelompok`, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	for rows.Next() {
		var id, kel string
		var n, jml int64
		if err := rows.Scan(&id, &kel, &n, &jml); err != nil {
			return nil, err
		}
		ambil(id).PerKelompok[kel] = model.IpdHitung{Pokok: n, Jumlah: jml}
	}
	return out, rows.Err()
}

// Foto mengembalikan foto bukti per inspeksi (id -> foto), berurutan.
func (r *IpdRepository) Foto(ctx context.Context, ids []string) (map[string][]model.IpdFotoRef, error) {
	out := map[string][]model.IpdFotoRef{}
	if len(ids) == 0 {
		return out, nil
	}
	ph := make([]string, len(ids))
	args := make([]any, len(ids))
	for i, id := range ids {
		ph[i] = "@p" + strconv.Itoa(i+1)
		args[i] = id
	}
	rows, err := r.db.QueryContext(ctx, `
SELECT CONVERT(NVARCHAR(36), ipd_id), CONVERT(NVARCHAR(36), id), keterangan, urut FROM dbo.fai_ipd_foto
WHERE dihapus_pada IS NULL AND ipd_id IN (`+strings.Join(ph, ",")+`) ORDER BY ipd_id, urut, dibuat_pada`, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	for rows.Next() {
		var ipd, id string
		var ket sql.NullString
		var urut int
		if err := rows.Scan(&ipd, &id, &ket, &urut); err != nil {
			return nil, err
		}
		ipd = strings.ToLower(ipd)
		out[ipd] = append(out[ipd], model.IpdFotoRef{ID: strings.ToLower(id), Keterangan: strPtr(ket), Urut: urut})
	}
	return out, rows.Err()
}

// ---- Persetujuan ----------------------------------------------------------------------------------

func (r *IpdRepository) pindah(ctx context.Context, id string, unitID, userID int64, statusBaru, aksi, alasan, deviceID, ip string) (string, error) {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return "", err
	}
	defer tx.Rollback()
	var res sql.Result
	if statusBaru == "DISETUJUI" {
		res, err = tx.ExecContext(ctx, `
UPDATE dbo.fai_ipd SET status='DISETUJUI', disetujui_oleh=@p3, disetujui_pada=SYSUTCDATETIME(), pesan_tolak=NULL, diubah_pada=SYSUTCDATETIME(), diubah_oleh=@p3
WHERE id=@p1 AND unit_id=@p2 AND status='MENUNGGU' AND dihapus_pada IS NULL AND (asisten_user_id=@p3 OR asisten_user_id IS NULL)`, id, unitID, userID)
	} else {
		res, err = tx.ExecContext(ctx, `
UPDATE dbo.fai_ipd SET status='DITOLAK', pesan_tolak=@p4, diubah_pada=SYSUTCDATETIME(), diubah_oleh=@p3
WHERE id=@p1 AND unit_id=@p2 AND status='MENUNGGU' AND dihapus_pada IS NULL AND (asisten_user_id=@p3 OR asisten_user_id IS NULL)`, id, unitID, userID, alasan)
	}
	if err != nil {
		return "", err
	}
	if n, _ := res.RowsAffected(); n != 1 {
		var sekarang string
		var asisten sql.NullInt64
		e := tx.QueryRowContext(ctx, `SELECT status, asisten_user_id FROM dbo.fai_ipd WHERE id=@p1 AND unit_id=@p2 AND dihapus_pada IS NULL`, id, unitID).Scan(&sekarang, &asisten)
		if errors.Is(e, sql.ErrNoRows) {
			return "", ErrNotFound
		}
		if e != nil {
			return "", e
		}
		if sekarang != "MENUNGGU" {
			return sekarang, ErrStatusBerubah
		}
		return sekarang, ErrBukanPenyetuju
	}
	uid := userID
	if err := insertAudit(ctx, tx, model.AuditBaris{Tabel: "fai_ipd", BarisID: id, Aksi: aksi, UserID: &uid, DeviceID: deviceID, IP: ip, Keterangan: alasan}); err != nil {
		return "", err
	}
	return statusBaru, tx.Commit()
}

func (r *IpdRepository) Setujui(ctx context.Context, id string, unitID, userID int64, deviceID, ip string) (string, error) {
	return r.pindah(ctx, id, unitID, userID, "DISETUJUI", "SETUJU", "", deviceID, ip)
}

func (r *IpdRepository) Tolak(ctx context.Context, id string, unitID, userID int64, alasan, deviceID, ip string) (string, error) {
	return r.pindah(ctx, id, unitID, userID, "DITOLAK", "TOLAK", alasan, deviceID, ip)
}

// ---- Peringatan inspeksi ulang ----------------------------------------------------------------------

// Terakhir mengembalikan inspeksi (selain yang DITOLAK) pemanen + blok yang sama pada rentang tanggal, terbaru dulu.
func (r *IpdRepository) Terakhir(ctx context.Context, unitID, hrmb, asm3Oid int64, dari, sampai, kecuali string) ([]model.IpdTerakhir, error) {
	q := `
SELECT TOP 5 CONVERT(NVARCHAR(36), f.id), CONVERT(NVARCHAR(10), f.tanggal_inspeksi, 23), u.username, u.nama, f.status
FROM dbo.fai_ipd f JOIN dbo.app_user u ON u.user_id = f.dibuat_oleh
WHERE f.unit_id=@p1 AND f.pemanen_hrmb_code=@p2 AND f.asm3_oid=@p3 AND f.dihapus_pada IS NULL AND f.status<>'DITOLAK'
  AND f.tanggal_inspeksi >= @p4 AND f.tanggal_inspeksi <= @p5`
	args := []any{unitID, hrmb, asm3Oid, dari, sampai}
	if kecuali != "" {
		q += " AND f.id <> @p6"
		args = append(args, kecuali)
	}
	rows, err := r.db.QueryContext(ctx, q+" ORDER BY f.tanggal_inspeksi DESC, f.dibuat_pada DESC", args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []model.IpdTerakhir{}
	for rows.Next() {
		var t model.IpdTerakhir
		if err := rows.Scan(&t.ID, &t.TanggalInspeksi, &t.Inspektur, &t.InspekturNama, &t.Status); err != nil {
			return nil, err
		}
		t.ID = strings.ToLower(t.ID)
		out = append(out, t)
	}
	return out, rows.Err()
}

// ---- Laporan loses ------------------------------------------------------------------------------

func kondisiLoses(unitID int64, f model.FilterLosesIpd) (string, []any) {
	var w strings.Builder
	args := []any{unitID}
	w.WriteString("f.dihapus_pada IS NULL AND f.unit_id=@p1 AND f.status='DISETUJUI'")
	tambah := func(cond string, v any) {
		args = append(args, v)
		w.WriteString(" AND " + strings.Replace(cond, "?", "@p"+strconv.Itoa(len(args)), 1))
	}
	if f.Dari != "" {
		tambah("f.tanggal_inspeksi >= ?", f.Dari)
	}
	if f.Sampai != "" {
		tambah("f.tanggal_inspeksi <= ?", f.Sampai)
	}
	if f.DivisiKode != "" {
		tambah("f.divisi_id IN (SELECT divisi_id FROM dbo.divisi WHERE unit_id=@p1 AND kode = ?)", f.DivisiKode)
	}
	if f.BlokKode != "" {
		tambah("f.blok_kode = ?", f.BlokKode)
	}
	if f.PemanenHrmb > 0 {
		tambah("f.pemanen_hrmb_code = ?", f.PemanenHrmb)
	}
	return w.String(), args
}

// DasarLoses membaca inspeksi DISETUJUI sesuai penyaring beserta hitungan pokok dan temuannya.
func (r *IpdRepository) DasarLoses(ctx context.Context, unitID int64, f model.FilterLosesIpd) ([]model.IpdDasar, error) {
	where, args := kondisiLoses(unitID, f)
	rows, err := r.db.QueryContext(ctx, `
SELECT CONVERT(NVARCHAR(36), f.id), CONVERT(NVARCHAR(10), f.tanggal_inspeksi, 23), dv.kode, COALESCE(f.blok_kode, ''), f.pemanen_hrmb_code, COALESCE(pm.nama, '')
FROM dbo.fai_ipd f JOIN dbo.divisi dv ON dv.divisi_id = f.divisi_id
OUTER APPLY (SELECT TOP 1 p.nama FROM dbo.pemanen p WHERE p.unit_id = f.unit_id AND p.erp_hrmb_code = f.pemanen_hrmb_code AND p.dihapus_pada IS NULL ORDER BY p.pemanen_id) pm
WHERE `+where+` ORDER BY f.tanggal_inspeksi, f.dibuat_pada`, args...)
	if err != nil {
		return nil, err
	}
	var out []model.IpdDasar
	for rows.Next() {
		var d model.IpdDasar
		if err := rows.Scan(&d.ID, &d.Tanggal, &d.DivisiKode, &d.BlokKode, &d.PemanenHrmb, &d.PemanenNama); err != nil {
			rows.Close()
			return nil, err
		}
		d.ID = strings.ToLower(d.ID)
		out = append(out, d)
	}
	rows.Close()
	if len(out) == 0 {
		return out, nil
	}
	m, err := r.mentah(ctx, where, args)
	if err != nil {
		return nil, err
	}
	for i := range out {
		if x := m[out[i].ID]; x != nil {
			out[i].Mentah = *x
		} else {
			out[i].Mentah = model.IpdMentah{PerKode: map[string]model.IpdHitung{}, PerKelompok: map[string]model.IpdHitung{}}
		}
	}
	return out, nil
}

// ---- Foto ---------------------------------------------------------------------------------------

func (r *IpdRepository) FotoAda(ctx context.Context, fotoID string) (bool, error) {
	var n int
	err := r.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM dbo.fai_ipd_foto WHERE id=@p1`, fotoID).Scan(&n)
	return n > 0, err
}

func (r *IpdRepository) SimpanFoto(ctx context.Context, f model.IpdFoto) error {
	_, err := r.db.ExecContext(ctx, `
INSERT INTO dbo.fai_ipd_foto (id, ipd_id, keterangan, urut, nama_file, sha256, dibuat_oleh) VALUES (@p1,@p2,@p3,@p4,@p5,@p6,@p7)`,
		f.ID, f.IpdID, nullStr(f.Keterangan), f.Urut, f.NamaFile, f.SHA256, f.UserID)
	return err
}

// PathFoto mengembalikan path berkas foto IPD di unit itu, atau ErrNotFound.
func (r *IpdRepository) PathFoto(ctx context.Context, unitID int64, fotoID string) (string, error) {
	var p string
	err := r.db.QueryRowContext(ctx, `
SELECT ft.nama_file FROM dbo.fai_ipd_foto ft JOIN dbo.fai_ipd f ON f.id = ft.ipd_id
WHERE ft.id=@p1 AND f.unit_id=@p2 AND ft.dihapus_pada IS NULL AND f.dihapus_pada IS NULL`, fotoID, unitID).Scan(&p)
	if errors.Is(err, sql.ErrNoRows) {
		return "", ErrNotFound
	}
	return p, err
}

// PemilikID mengembalikan inspektur (dibuat_oleh) sebuah catatan, atau ErrNotFound.
func (r *IpdRepository) PemilikID(ctx context.Context, id string) (int64, error) {
	var u int64
	err := r.db.QueryRowContext(ctx, `SELECT dibuat_oleh FROM dbo.fai_ipd WHERE id=@p1 AND dihapus_pada IS NULL`, id).Scan(&u)
	if errors.Is(err, sql.ErrNoRows) {
		return 0, ErrNotFound
	}
	return u, err
}
