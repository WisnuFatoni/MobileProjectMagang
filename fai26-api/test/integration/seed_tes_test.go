package integration

import (
	"context"
	"database/sql"
	"fmt"

	"fai26-api/internal/auth"
)

// seedDev (HANYA untuk tes, di database berakhiran _tes) mengisi satu unit dan pengguna krani1 dan mandor1 (sama dengan master_contoh.json di HP). Aman dijalankan berulang.
func seedDev(ctx context.Context, db *sql.DB) error {
	if _, err := db.ExecContext(ctx, `
IF NOT EXISTS (SELECT 1 FROM dbo.unit WHERE kode='KP_STL')
  INSERT INTO dbo.unit (kode, nama, erp_db, tahun_cluster) VALUES ('KP_STL', 'Kebun Contoh STL', 'kp_stl2025', 2026)`); err != nil {
		return err
	}
	for _, u := range []struct{ user, nama, pass, role string }{
		{"krani1", "Krani Contoh", "krani123", "KRANI"},
		{"mandor1", "Mandor Contoh", "mandor123", "MANDOR"},
		{"admin1", "Admin Contoh", "admin12345", "ADMIN"},
	} {
		h, err := auth.HashPassword(u.pass)
		if err != nil {
			return err
		}
		if _, err := db.ExecContext(ctx, `
IF NOT EXISTS (SELECT 1 FROM dbo.app_user WHERE username=@p1)
BEGIN
  INSERT INTO dbo.app_user (unit_id, username, nama, password_hash)
  VALUES ((SELECT unit_id FROM dbo.unit WHERE kode='KP_STL'), @p1, @p2, @p3);
  INSERT INTO dbo.user_role (user_id, role_id)
  VALUES (SCOPE_IDENTITY(), (SELECT role_id FROM dbo.role WHERE kode=@p4));
END`, u.user, u.nama, h, u.role); err != nil {
			return err
		}
	}
	// Admin contoh juga memegang AUDITOR supaya bisa membuka audit log di web admin (ADMIN sendiri tidak punya audit.lihat).
	if _, err := db.ExecContext(ctx, `
IF NOT EXISTS (SELECT 1 FROM dbo.user_role ur JOIN dbo.app_user u ON u.user_id=ur.user_id JOIN dbo.role r ON r.role_id=ur.role_id
               WHERE u.username='admin1' AND r.kode='AUDITOR' AND ur.dihapus_pada IS NULL)
  INSERT INTO dbo.user_role (user_id, role_id)
  VALUES ((SELECT user_id FROM dbo.app_user WHERE username='admin1'), (SELECT role_id FROM dbo.role WHERE kode='AUDITOR'))`); err != nil {
		return err
	}
	return masterDev(ctx, db)
}

// masterDev mengisi master contoh dengan id yang sama dengan master_contoh.json di HP, sehingga unduhan master
// tidak bentrok dengan data contoh yang sudah ada di HP uji.
func masterDev(ctx context.Context, db *sql.DB) error {
	unit := "(SELECT unit_id FROM dbo.unit WHERE kode='KP_STL')"
	masukkan := func(tabel, kolomId string, id int, kolom string, nilai string) error {
		q := fmt.Sprintf(`IF NOT EXISTS (SELECT 1 FROM dbo.%[1]s WHERE %[2]s=%[3]d)
BEGIN
  SET IDENTITY_INSERT dbo.%[1]s ON;
  INSERT INTO dbo.%[1]s (%[2]s, %[4]s) VALUES (%[3]d, %[5]s);
  SET IDENTITY_INSERT dbo.%[1]s OFF;
END`, tabel, kolomId, id, kolom, nilai)
		_, err := db.ExecContext(ctx, q)
		return err
	}
	if err := masukkan("divisi", "divisi_id", 1, "unit_id, kode, nama", unit+", 'DIV2', 'Divisi 2 (contoh)'"); err != nil {
		return err
	}
	for _, b := range []struct {
		id, oid int
		kode    string
		luas    float64
	}{{1, 1012, "A12", 28.5}, {2, 1013, "A13", 30.0}} {
		if err := masukkan("blok", "blok_id", b.id, "unit_id, divisi_id, erp_oid, kode, luas_ha, tahun_tanam, sph",
			fmt.Sprintf("%s, 1, %d, '%s', %v, 2012, 133", unit, b.oid, b.kode, b.luas)); err != nil {
			return err
		}
	}
	for _, t := range []struct {
		id, blok int
		kodeBlok string
		no       string
	}{{1, 1, "A12", "01"}, {2, 1, "A12", "02"}, {3, 1, "A12", "03"}, {4, 2, "A13", "01"}, {5, 2, "A13", "02"}} {
		if err := masukkan("tph", "tph_id", t.id, "blok_id, no_tph, qr_kode",
			fmt.Sprintf("%d, '%s', 'FAI26|TPH|1|KP_STL|%s|%s'", t.blok, t.no, t.kodeBlok, t.no)); err != nil {
			return err
		}
	}
	for i := 1; i <= 5; i++ {
		if err := masukkan("pemanen", "pemanen_id", i, "unit_id, erp_hrmb_code, kode, nama",
			fmt.Sprintf("%s, %d, 'P-%03d', 'Pemanen Contoh %d'", unit, 90000+i, i, i)); err != nil {
			return err
		}
	}
	return nil
}
