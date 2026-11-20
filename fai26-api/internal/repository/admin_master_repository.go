package repository

import (
	"context"
	"database/sql"
	"errors"
	"fmt"

	"fai26-api/internal/model"
)

// AdminMasterRepository mengelola master (divisi, blok, TPH, pemanen) satu unit untuk web admin.
// Setiap perubahan mengisi diubah_pada (versi master untuk HP dihitung darinya) dan menulis audit_log.
// Penghapusan selalu lunak.
type AdminMasterRepository struct{ db *sql.DB }

func NewAdminMasterRepository(db *sql.DB) *AdminMasterRepository {
	return &AdminMasterRepository{db: db}
}

func (r *AdminMasterRepository) rows(ctx context.Context, q string, args ...any) ([]model.BarisMaster, error) {
	return (&MasterRepository{db: r.db}).baris(ctx, q, args...)
}

// ---- Daftar --------------------------------------------------------------------------------------

func (r *AdminMasterRepository) DaftarDivisi(ctx context.Context, unitID int64) ([]model.BarisMaster, error) {
	return r.rows(ctx, `
SELECT d.divisi_id, d.kode, d.nama, d.erp_oid, d.aktif,
  (SELECT COUNT(*) FROM dbo.blok b WHERE b.divisi_id = d.divisi_id AND b.dihapus_pada IS NULL) AS jumlah_blok
FROM dbo.divisi d WHERE d.unit_id=@p1 AND d.dihapus_pada IS NULL ORDER BY d.kode`, unitID)
}

func (r *AdminMasterRepository) DaftarBlok(ctx context.Context, unitID int64) ([]model.BarisMaster, error) {
	return r.rows(ctx, `
SELECT b.blok_id, b.divisi_id, v.kode AS divisi_kode, b.erp_oid, b.kode, b.nama, b.luas_ha, b.tahun_tanam, b.sph, b.aktif,
  (SELECT COUNT(*) FROM dbo.tph t WHERE t.blok_id = b.blok_id AND t.dihapus_pada IS NULL) AS jumlah_tph
FROM dbo.blok b LEFT JOIN dbo.divisi v ON v.divisi_id = b.divisi_id
WHERE b.unit_id=@p1 AND b.dihapus_pada IS NULL ORDER BY b.kode`, unitID)
}

// DaftarTph; blokID 0 berarti semua blok.
func (r *AdminMasterRepository) DaftarTph(ctx context.Context, unitID, blokID int64) ([]model.BarisMaster, error) {
	return r.rows(ctx, `
SELECT t.tph_id, t.blok_id, b.kode AS blok_kode, t.no_tph, t.qr_kode, t.lat, t.lon, t.aktif
FROM dbo.tph t JOIN dbo.blok b ON b.blok_id = t.blok_id
WHERE b.unit_id=@p1 AND t.dihapus_pada IS NULL AND b.dihapus_pada IS NULL AND (@p2=0 OR t.blok_id=@p2)
ORDER BY b.kode, t.no_tph`, unitID, blokID)
}

func (r *AdminMasterRepository) DaftarPemanen(ctx context.Context, unitID int64) ([]model.BarisMaster, error) {
	return r.rows(ctx, `
SELECT pemanen_id, erp_hrmb_code, mandor_hrmb_code, kode, nama, aktif FROM dbo.pemanen
WHERE unit_id=@p1 AND dihapus_pada IS NULL ORDER BY nama`, unitID)
}

// ---- Transaksi bersama ---------------------------------------------------------------------------

func (r *AdminMasterRepository) tx(ctx context.Context, fn func(tx *sql.Tx) error) error {
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

func ada(ctx context.Context, tx *sql.Tx, q string, args ...any) (bool, error) {
	var n int
	if err := tx.QueryRowContext(ctx, q, args...).Scan(&n); err != nil {
		return false, err
	}
	return n > 0, nil
}

func catat(ctx context.Context, tx *sql.Tx, tabel string, id int64, aksi string, userID int64, ket string) error {
	return insertAudit(ctx, tx, model.AuditBaris{Tabel: tabel, BarisID: fmt.Sprint(id), Aksi: aksi, UserID: &userID, Keterangan: ket})
}

func aktifAtauBawaan(p *bool) bool { return p == nil || *p }

// ---- Divisi --------------------------------------------------------------------------------------

func (r *AdminMasterRepository) BuatDivisi(ctx context.Context, unitID, userID int64, in model.DivisiInput) (int64, error) {
	var id int64
	err := r.tx(ctx, func(tx *sql.Tx) error {
		if dup, err := ada(ctx, tx, `SELECT COUNT(*) FROM dbo.divisi WHERE unit_id=@p1 AND kode=@p2 AND dihapus_pada IS NULL`, unitID, in.Kode); err != nil || dup {
			return pilihGalat(err, ErrDuplikat)
		}
		if err := tx.QueryRowContext(ctx, `
INSERT INTO dbo.divisi (unit_id, erp_oid, kode, nama, aktif, dibuat_oleh) OUTPUT INSERTED.divisi_id VALUES (@p1,@p2,@p3,@p4,@p5,@p6)`,
			unitID, ptrAny(in.ErpOid), in.Kode, in.Nama, aktifAtauBawaan(in.Aktif), userID).Scan(&id); err != nil {
			return err
		}
		return catat(ctx, tx, "divisi", id, "BUAT", userID, in.Kode)
	})
	return id, err
}

func (r *AdminMasterRepository) UbahDivisi(ctx context.Context, unitID, userID, id int64, in model.DivisiInput) error {
	return r.tx(ctx, func(tx *sql.Tx) error {
		if ok, err := ada(ctx, tx, `SELECT COUNT(*) FROM dbo.divisi WHERE divisi_id=@p1 AND unit_id=@p2 AND dihapus_pada IS NULL`, id, unitID); err != nil || !ok {
			return pilihGalat(err, ErrNotFound)
		}
		if dup, err := ada(ctx, tx, `SELECT COUNT(*) FROM dbo.divisi WHERE unit_id=@p1 AND kode=@p2 AND dihapus_pada IS NULL AND divisi_id<>@p3`, unitID, in.Kode, id); err != nil || dup {
			return pilihGalat(err, ErrDuplikat)
		}
		if _, err := tx.ExecContext(ctx, `
UPDATE dbo.divisi SET kode=@p2, nama=@p3, erp_oid=@p4, aktif=COALESCE(@p5, aktif), diubah_pada=SYSUTCDATETIME(), diubah_oleh=@p6 WHERE divisi_id=@p1`,
			id, in.Kode, in.Nama, ptrAny(in.ErpOid), ptrAny(in.Aktif), userID); err != nil {
			return err
		}
		return catat(ctx, tx, "divisi", id, "UBAH", userID, in.Kode)
	})
}

func (r *AdminMasterRepository) HapusDivisi(ctx context.Context, unitID, userID, id int64) error {
	return r.tx(ctx, func(tx *sql.Tx) error {
		if ok, err := ada(ctx, tx, `SELECT COUNT(*) FROM dbo.divisi WHERE divisi_id=@p1 AND unit_id=@p2 AND dihapus_pada IS NULL`, id, unitID); err != nil || !ok {
			return pilihGalat(err, ErrNotFound)
		}
		if dipakai, err := ada(ctx, tx, `SELECT COUNT(*) FROM dbo.blok WHERE divisi_id=@p1 AND dihapus_pada IS NULL`, id); err != nil || dipakai {
			return pilihGalat(err, ErrMasihDipakai)
		}
		if _, err := tx.ExecContext(ctx, `UPDATE dbo.divisi SET aktif=0, dihapus_pada=SYSUTCDATETIME(), dihapus_oleh=@p2, diubah_pada=SYSUTCDATETIME(), diubah_oleh=@p2 WHERE divisi_id=@p1`, id, userID); err != nil {
			return err
		}
		return catat(ctx, tx, "divisi", id, "HAPUS", userID, "")
	})
}

// ---- Blok ----------------------------------------------------------------------------------------

func (r *AdminMasterRepository) BuatBlok(ctx context.Context, unitID, userID int64, in model.BlokInput) (int64, error) {
	var id int64
	err := r.tx(ctx, func(tx *sql.Tx) error {
		if err := periksaDivisi(ctx, tx, unitID, in.DivisiID); err != nil {
			return err
		}
		if dup, err := ada(ctx, tx, `SELECT COUNT(*) FROM dbo.blok WHERE unit_id=@p1 AND kode=@p2 AND dihapus_pada IS NULL`, unitID, in.Kode); err != nil || dup {
			return pilihGalat(err, ErrDuplikat)
		}
		if err := tx.QueryRowContext(ctx, `
INSERT INTO dbo.blok (unit_id, divisi_id, erp_oid, kode, nama, luas_ha, tahun_tanam, sph, aktif, dibuat_oleh)
OUTPUT INSERTED.blok_id VALUES (@p1,@p2,@p3,@p4,@p5,@p6,@p7,@p8,@p9,@p10)`,
			unitID, ptrAny(in.DivisiID), in.ErpOid, in.Kode, ptrAny(in.Nama), ptrAny(in.LuasHa), ptrAny(in.TahunTanam), ptrAny(in.Sph),
			aktifAtauBawaan(in.Aktif), userID).Scan(&id); err != nil {
			return err
		}
		return catat(ctx, tx, "blok", id, "BUAT", userID, in.Kode)
	})
	return id, err
}

func periksaDivisi(ctx context.Context, tx *sql.Tx, unitID int64, divisiID *int64) error {
	if divisiID == nil {
		return nil
	}
	ok, err := ada(ctx, tx, `SELECT COUNT(*) FROM dbo.divisi WHERE divisi_id=@p1 AND unit_id=@p2 AND dihapus_pada IS NULL`, *divisiID, unitID)
	return pilihGalat(err, ErrReferensi, ok)
}

// UbahBlok. Bila kode blok berubah, qr_kode semua TPH-nya ikut dibuat ulang (isi QR memuat kode blok);
// jumlahQrBerubah memberi tahu pemanggil berapa QR yang tidak berlaku lagi.
func (r *AdminMasterRepository) UbahBlok(ctx context.Context, unitID, userID, id int64, in model.BlokInput) (jumlahQrBerubah int, err error) {
	err = r.tx(ctx, func(tx *sql.Tx) error {
		var kodeLama, unitKode string
		if e := tx.QueryRowContext(ctx, `SELECT b.kode, u.kode FROM dbo.blok b JOIN dbo.unit u ON u.unit_id=b.unit_id WHERE b.blok_id=@p1 AND b.unit_id=@p2 AND b.dihapus_pada IS NULL`, id, unitID).Scan(&kodeLama, &unitKode); e != nil {
			if errors.Is(e, sql.ErrNoRows) {
				return ErrNotFound
			}
			return e
		}
		if e := periksaDivisi(ctx, tx, unitID, in.DivisiID); e != nil {
			return e
		}
		if dup, e := ada(ctx, tx, `SELECT COUNT(*) FROM dbo.blok WHERE unit_id=@p1 AND kode=@p2 AND dihapus_pada IS NULL AND blok_id<>@p3`, unitID, in.Kode, id); e != nil || dup {
			return pilihGalat(e, ErrDuplikat)
		}
		if _, e := tx.ExecContext(ctx, `
UPDATE dbo.blok SET divisi_id=@p2, erp_oid=@p3, kode=@p4, nama=@p5, luas_ha=@p6, tahun_tanam=@p7, sph=@p8, aktif=COALESCE(@p9, aktif),
  diubah_pada=SYSUTCDATETIME(), diubah_oleh=@p10 WHERE blok_id=@p1`,
			id, ptrAny(in.DivisiID), in.ErpOid, in.Kode, ptrAny(in.Nama), ptrAny(in.LuasHa), ptrAny(in.TahunTanam), ptrAny(in.Sph),
			ptrAny(in.Aktif), userID); e != nil {
			return e
		}
		if kodeLama != in.Kode {
			res, e := tx.ExecContext(ctx, `
UPDATE dbo.tph SET qr_kode = N'FAI26|TPH|1|' + @p2 + N'|' + @p3 + N'|' + no_tph, diubah_pada=SYSUTCDATETIME(), diubah_oleh=@p4
WHERE blok_id=@p1 AND dihapus_pada IS NULL`, id, unitKode, in.Kode, userID)
			if e != nil {
				return e
			}
			n, _ := res.RowsAffected()
			jumlahQrBerubah = int(n)
		}
		return catat(ctx, tx, "blok", id, "UBAH", userID, fmt.Sprintf("%s (QR berubah: %d)", in.Kode, jumlahQrBerubah))
	})
	return jumlahQrBerubah, err
}

func (r *AdminMasterRepository) HapusBlok(ctx context.Context, unitID, userID, id int64) error {
	return r.tx(ctx, func(tx *sql.Tx) error {
		if ok, err := ada(ctx, tx, `SELECT COUNT(*) FROM dbo.blok WHERE blok_id=@p1 AND unit_id=@p2 AND dihapus_pada IS NULL`, id, unitID); err != nil || !ok {
			return pilihGalat(err, ErrNotFound)
		}
		if dipakai, err := ada(ctx, tx, `SELECT COUNT(*) FROM dbo.tph WHERE blok_id=@p1 AND dihapus_pada IS NULL`, id); err != nil || dipakai {
			return pilihGalat(err, ErrMasihDipakai)
		}
		if _, err := tx.ExecContext(ctx, `UPDATE dbo.blok SET aktif=0, dihapus_pada=SYSUTCDATETIME(), dihapus_oleh=@p2, diubah_pada=SYSUTCDATETIME(), diubah_oleh=@p2 WHERE blok_id=@p1`, id, userID); err != nil {
			return err
		}
		return catat(ctx, tx, "blok", id, "HAPUS", userID, "")
	})
}

// ---- TPH -----------------------------------------------------------------------------------------

// QrKode membentuk isi QR sesuai progres/SPEK_QR_TPH.md: FAI26|TPH|1|<unit>|<blok>|<no tph>.
func QrKode(unitKode, blokKode, noTph string) string {
	return "FAI26|TPH|1|" + unitKode + "|" + blokKode + "|" + noTph
}

func (r *AdminMasterRepository) BuatTph(ctx context.Context, unitID, userID int64, in model.TphInput) (int64, error) {
	var id int64
	err := r.tx(ctx, func(tx *sql.Tx) error {
		var err error
		id, err = buatTphDalam(ctx, tx, unitID, userID, in)
		return err
	})
	return id, err
}

func buatTphDalam(ctx context.Context, tx *sql.Tx, unitID, userID int64, in model.TphInput) (int64, error) {
	var blokKode, unitKode string
	if err := tx.QueryRowContext(ctx, `SELECT b.kode, u.kode FROM dbo.blok b JOIN dbo.unit u ON u.unit_id=b.unit_id WHERE b.blok_id=@p1 AND b.unit_id=@p2 AND b.dihapus_pada IS NULL`, in.BlokID, unitID).Scan(&blokKode, &unitKode); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return 0, ErrReferensi
		}
		return 0, err
	}
	qr := QrKode(unitKode, blokKode, in.NoTph)
	if dup, err := ada(ctx, tx, `SELECT COUNT(*) FROM dbo.tph WHERE qr_kode=@p1`, qr); err != nil || dup {
		return 0, pilihGalat(err, ErrDuplikat)
	}
	var id int64
	if err := tx.QueryRowContext(ctx, `
INSERT INTO dbo.tph (blok_id, no_tph, qr_kode, lat, lon, aktif, dibuat_oleh) OUTPUT INSERTED.tph_id VALUES (@p1,@p2,@p3,@p4,@p5,@p6,@p7)`,
		in.BlokID, in.NoTph, qr, ptrAny(in.Lat), ptrAny(in.Lon), aktifAtauBawaan(in.Aktif), userID).Scan(&id); err != nil {
		return 0, err
	}
	return id, catat(ctx, tx, "tph", id, "BUAT", userID, qr)
}

// BuatTphMassal membuat TPH berurutan; semua atau tidak sama sekali. Mengembalikan id yang dibuat.
func (r *AdminMasterRepository) BuatTphMassal(ctx context.Context, unitID, userID int64, in model.TphMassalInput) ([]int64, error) {
	var ids []int64
	err := r.tx(ctx, func(tx *sql.Tx) error {
		for n := in.Dari; n <= in.Sampai; n++ {
			id, err := buatTphDalam(ctx, tx, unitID, userID, model.TphInput{BlokID: in.BlokID, NoTph: fmt.Sprintf("%0*d", in.Lebar, n)})
			if err != nil {
				return err
			}
			ids = append(ids, id)
		}
		return nil
	})
	return ids, err
}

// UbahTph. Mengubah blok atau nomor membuat qr_kode baru (QR lama tidak berlaku); qrBerubah memberitahu pemanggil.
func (r *AdminMasterRepository) UbahTph(ctx context.Context, unitID, userID, id int64, in model.TphInput) (qrBerubah bool, err error) {
	err = r.tx(ctx, func(tx *sql.Tx) error {
		var qrLama string
		if e := tx.QueryRowContext(ctx, `SELECT t.qr_kode FROM dbo.tph t JOIN dbo.blok b ON b.blok_id=t.blok_id WHERE t.tph_id=@p1 AND b.unit_id=@p2 AND t.dihapus_pada IS NULL`, id, unitID).Scan(&qrLama); e != nil {
			if errors.Is(e, sql.ErrNoRows) {
				return ErrNotFound
			}
			return e
		}
		var blokKode, unitKode string
		if e := tx.QueryRowContext(ctx, `SELECT b.kode, u.kode FROM dbo.blok b JOIN dbo.unit u ON u.unit_id=b.unit_id WHERE b.blok_id=@p1 AND b.unit_id=@p2 AND b.dihapus_pada IS NULL`, in.BlokID, unitID).Scan(&blokKode, &unitKode); e != nil {
			if errors.Is(e, sql.ErrNoRows) {
				return ErrReferensi
			}
			return e
		}
		qr := QrKode(unitKode, blokKode, in.NoTph)
		if dup, e := ada(ctx, tx, `SELECT COUNT(*) FROM dbo.tph WHERE qr_kode=@p1 AND tph_id<>@p2`, qr, id); e != nil || dup {
			return pilihGalat(e, ErrDuplikat)
		}
		if _, e := tx.ExecContext(ctx, `
UPDATE dbo.tph SET blok_id=@p2, no_tph=@p3, qr_kode=@p4, lat=@p5, lon=@p6, aktif=COALESCE(@p7, aktif), diubah_pada=SYSUTCDATETIME(), diubah_oleh=@p8 WHERE tph_id=@p1`,
			id, in.BlokID, in.NoTph, qr, ptrAny(in.Lat), ptrAny(in.Lon), ptrAny(in.Aktif), userID); e != nil {
			return e
		}
		qrBerubah = qr != qrLama
		return catat(ctx, tx, "tph", id, "UBAH", userID, qr)
	})
	return qrBerubah, err
}

func (r *AdminMasterRepository) HapusTph(ctx context.Context, unitID, userID, id int64) error {
	return r.tx(ctx, func(tx *sql.Tx) error {
		if ok, err := ada(ctx, tx, `SELECT COUNT(*) FROM dbo.tph t JOIN dbo.blok b ON b.blok_id=t.blok_id WHERE t.tph_id=@p1 AND b.unit_id=@p2 AND t.dihapus_pada IS NULL`, id, unitID); err != nil || !ok {
			return pilihGalat(err, ErrNotFound)
		}
		if _, err := tx.ExecContext(ctx, `UPDATE dbo.tph SET aktif=0, dihapus_pada=SYSUTCDATETIME(), dihapus_oleh=@p2, diubah_pada=SYSUTCDATETIME(), diubah_oleh=@p2 WHERE tph_id=@p1`, id, userID); err != nil {
			return err
		}
		return catat(ctx, tx, "tph", id, "HAPUS", userID, "")
	})
}

// ---- Pemanen -------------------------------------------------------------------------------------

func (r *AdminMasterRepository) BuatPemanen(ctx context.Context, unitID, userID int64, in model.PemanenAdminInput) (int64, error) {
	var id int64
	err := r.tx(ctx, func(tx *sql.Tx) error {
		if dup, err := ada(ctx, tx, `SELECT COUNT(*) FROM dbo.pemanen WHERE unit_id=@p1 AND erp_hrmb_code=@p2 AND dihapus_pada IS NULL`, unitID, in.ErpHrmbCode); err != nil || dup {
			return pilihGalat(err, ErrDuplikat)
		}
		if err := tx.QueryRowContext(ctx, `
INSERT INTO dbo.pemanen (unit_id, erp_hrmb_code, mandor_hrmb_code, kode, nama, aktif, dibuat_oleh) OUTPUT INSERTED.pemanen_id VALUES (@p1,@p2,@p7,@p3,@p4,@p5,@p6)`,
			unitID, in.ErpHrmbCode, ptrAny(in.Kode), in.Nama, aktifAtauBawaan(in.Aktif), userID, ptrAny(in.MandorHrmbCode)).Scan(&id); err != nil {
			return err
		}
		return catat(ctx, tx, "pemanen", id, "BUAT", userID, in.Nama)
	})
	return id, err
}

func (r *AdminMasterRepository) UbahPemanen(ctx context.Context, unitID, userID, id int64, in model.PemanenAdminInput) error {
	return r.tx(ctx, func(tx *sql.Tx) error {
		if ok, err := ada(ctx, tx, `SELECT COUNT(*) FROM dbo.pemanen WHERE pemanen_id=@p1 AND unit_id=@p2 AND dihapus_pada IS NULL`, id, unitID); err != nil || !ok {
			return pilihGalat(err, ErrNotFound)
		}
		if dup, err := ada(ctx, tx, `SELECT COUNT(*) FROM dbo.pemanen WHERE unit_id=@p1 AND erp_hrmb_code=@p2 AND dihapus_pada IS NULL AND pemanen_id<>@p3`, unitID, in.ErpHrmbCode, id); err != nil || dup {
			return pilihGalat(err, ErrDuplikat)
		}
		if _, err := tx.ExecContext(ctx, `
UPDATE dbo.pemanen SET erp_hrmb_code=@p2, mandor_hrmb_code=@p7, kode=@p3, nama=@p4, aktif=COALESCE(@p5, aktif), diubah_pada=SYSUTCDATETIME(), diubah_oleh=@p6 WHERE pemanen_id=@p1`,
			id, in.ErpHrmbCode, ptrAny(in.Kode), in.Nama, ptrAny(in.Aktif), userID, ptrAny(in.MandorHrmbCode)); err != nil {
			return err
		}
		return catat(ctx, tx, "pemanen", id, "UBAH", userID, in.Nama)
	})
}

func (r *AdminMasterRepository) HapusPemanen(ctx context.Context, unitID, userID, id int64) error {
	return r.tx(ctx, func(tx *sql.Tx) error {
		if ok, err := ada(ctx, tx, `SELECT COUNT(*) FROM dbo.pemanen WHERE pemanen_id=@p1 AND unit_id=@p2 AND dihapus_pada IS NULL`, id, unitID); err != nil || !ok {
			return pilihGalat(err, ErrNotFound)
		}
		if _, err := tx.ExecContext(ctx, `UPDATE dbo.pemanen SET aktif=0, dihapus_pada=SYSUTCDATETIME(), dihapus_oleh=@p2, diubah_pada=SYSUTCDATETIME(), diubah_oleh=@p2 WHERE pemanen_id=@p1`, id, userID); err != nil {
			return err
		}
		return catat(ctx, tx, "pemanen", id, "HAPUS", userID, "")
	})
}

// pilihGalat: bila ada galat database kembalikan itu; bila tidak, kembalikan galat domain (bila kondisi gagal).
// Dua bentuk: pilihGalat(err, domain) untuk kondisi "ada = salah", dan pilihGalat(err, domain, ok) untuk "ok = benar".
func pilihGalat(err error, domain error, ok ...bool) error {
	if err != nil {
		return err
	}
	if len(ok) > 0 && ok[0] {
		return nil
	}
	return domain
}
