-- Migrasi 006 (18 Nov 2026): peran database khusus HVDM (sisi ERP) dengan hak sesempit mungkin. Aman dijalankan berulang.
-- Jalankan SETELAH 003 dan 005 (objek yang diberi hak harus sudah ada). Gunakan sqlcmd -I.
--
-- Prinsip (butir 10 catatan asisten ERP): HVDM hanya MEMBACA dua view dan MEMANGGIL empat prosedur jembatan. Ia tidak
-- diberi hak tulis pada tabel mana pun; prosedur dan view dimiliki dbo sehingga berjalan dengan hak pemiliknya
-- (ownership chaining) tanpa membuka tabel fai_* ke login ERP.
--
-- Setelah migrasi ini, DBA menambahkan login ERP ke peran (sekali, di server sungguhan):
--   CREATE USER [login_hvdm] FOR LOGIN [login_hvdm];
--   ALTER ROLE fai26_hvdm ADD MEMBER [login_hvdm];
SET QUOTED_IDENTIFIER ON;
GO

IF DATABASE_PRINCIPAL_ID('fai26_hvdm') IS NULL
    CREATE ROLE fai26_hvdm AUTHORIZATION dbo;
GO

GRANT SELECT ON OBJECT::dbo.v_fai_panen_siap_impor          TO fai26_hvdm;
GRANT SELECT ON OBJECT::dbo.v_fai_panen_pemanen_siap_impor  TO fai26_hvdm;
GRANT EXECUTE ON OBJECT::dbo.sp_fai_tandai_dibaca           TO fai26_hvdm;
GRANT EXECUTE ON OBJECT::dbo.sp_fai_impor_ok                TO fai26_hvdm;
GRANT EXECUTE ON OBJECT::dbo.sp_fai_impor_gagal             TO fai26_hvdm;
GRANT EXECUTE ON OBJECT::dbo.sp_fai_ulang_impor             TO fai26_hvdm;
GO

-- Pemeriksaan (jalankan sebagai anggota peran; yang pertama HARUS berhasil, sisanya HARUS gagal "permission was denied"):
--   SELECT TOP 1 * FROM dbo.v_fai_panen_siap_impor;
--   SELECT TOP 1 * FROM dbo.fai_panen;
--   UPDATE dbo.fai_panen SET status_erp = 'DIIMPOR' WHERE 1 = 0;
--   DELETE FROM dbo.audit_log WHERE 1 = 0;
