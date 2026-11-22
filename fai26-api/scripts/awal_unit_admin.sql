-- Data awal pemasangan: satu unit (kebun) dan akun ADMIN pertama. Aman dijalankan berulang (tidak menimpa yang sudah ada).
-- Jalankan SETELAH skema dan semua migrasi, dengan sqlcmd -I, memakai akun pemilik skema (bukan akun API):
--
--   sqlcmd -S <server> -d <database> -U <akun> -I -b -i scripts\awal_unit_admin.sql ^
--     -v KODE_UNIT="KP_STL" NAMA_UNIT="Kebun Contoh" ERP_DB="nama_db_erp" TAHUN_CLUSTER="2026" ^
--        USERNAME="admin" NAMA_ADMIN="Administrator" HASH_SANDI="<keluaran hashpw>"
--
-- Di PowerShell beri tanda kutip tunggal pada HASH_SANDI (isinya memuat tanda $). Hash dibuat dengan `go run ./cmd/hashpw`
-- (atau hashpw.exe) dan tidak pernah dikirim ke situs lain. Akun ADMIN diberi AUDITOR juga (ADMIN sendiri tidak punya
-- audit.lihat) dan wajib mengganti kata sandi saat login pertama.
SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
GO

IF NOT EXISTS (SELECT 1 FROM dbo.unit WHERE kode = N'$(KODE_UNIT)')
    INSERT INTO dbo.unit (kode, nama, erp_db, tahun_cluster) VALUES (N'$(KODE_UNIT)', N'$(NAMA_UNIT)', N'$(ERP_DB)', $(TAHUN_CLUSTER));
GO

IF NOT EXISTS (SELECT 1 FROM dbo.app_user WHERE username = N'$(USERNAME)')
BEGIN
    INSERT INTO dbo.app_user (unit_id, username, nama, password_hash, wajib_ganti_password)
    VALUES ((SELECT unit_id FROM dbo.unit WHERE kode = N'$(KODE_UNIT)'), N'$(USERNAME)', N'$(NAMA_ADMIN)', N'$(HASH_SANDI)', 1);
    INSERT INTO dbo.user_role (user_id, role_id)
    SELECT SCOPE_IDENTITY(), role_id FROM dbo.role WHERE kode IN (N'ADMIN', N'AUDITOR');
END
GO

SELECT u.username, u.wajib_ganti_password, CAST(STRING_AGG(r.kode, ',') AS NVARCHAR(40)) AS role
FROM dbo.app_user u JOIN dbo.user_role ur ON ur.user_id = u.user_id JOIN dbo.role r ON r.role_id = ur.role_id
WHERE u.username = N'$(USERNAME)' GROUP BY u.username, u.wajib_ganti_password;
GO
