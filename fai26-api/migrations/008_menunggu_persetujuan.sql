-- Migrasi 008 (19 Nov 2026): catatan MENUNGGU persetujuan krani. Aman dijalankan berulang. Jalankan SETELAH 007, dengan sqlcmd -I.
--
-- Alur: mandor mengirim catatan dari HP-nya tanpa persetujuan; server menyimpannya dengan status_erp = 'MENUNGGU'
-- (tidak muncul di view HVDM). Krani (pemegang panen.setujui) melihat daftarnya dari HP-nya sendiri dan menyetujui
-- (MENUNGGU -> BARU, siap ditarik ERP) atau menolak (MENUNGGU -> DITOLAK dengan alasan di fai_impor_log).
SET QUOTED_IDENTIFIER ON;
GO

IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'ck_fai_panen_status_erp' AND definition NOT LIKE '%MENUNGGU%')
BEGIN
    ALTER TABLE dbo.fai_panen DROP CONSTRAINT ck_fai_panen_status_erp;
    ALTER TABLE dbo.fai_panen ADD CONSTRAINT ck_fai_panen_status_erp
        CHECK (status_erp IN ('MENUNGGU','BARU','DIBACA','DIIMPOR','DITOLAK'));
END
GO
