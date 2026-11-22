-- Migrasi 003 (11 Nov 2026): jembatan ke ERP untuk TCode HVDM. Aman dijalankan berulang (CREATE OR ALTER).
-- Perlu SQL Server 2016 SP1 atau lebih baru. Dijalankan di database fai26, oleh pemilik skema (bukan akun API).
--
-- Tujuan: kode ERP (Visual Studio) cukup MEMBACA view dan MEMANGGIL prosedur ini, tidak menulis tabel fai_* langsung.
-- Dengan begitu perpindahan status selalu sah (BARU -> DIBACA -> DIIMPOR atau DITOLAK), selalu menulis fai_impor_log
-- dan audit_log, dan selalu mengisi diubah_pada. Kolom diubah_pada itu WAJIB: HP mengetahui status baru dari
-- GET /api/panen?sejak=..., yang membandingkan COALESCE(diubah_pada, diterima_server).
--
--   Baca     : SELECT * FROM dbo.v_fai_panen_siap_impor        (satu baris per catatan, jumlah per kolom JJG_*)
--              SELECT * FROM dbo.v_fai_panen_pemanen_siap_impor (pemanen per catatan, bila ada)
--   Tandai   : EXEC dbo.sp_fai_tandai_dibaca  @panen_id
--   Berhasil : EXEC dbo.sp_fai_impor_ok       @panen_id, @hvd1m_oid, @hvd1da_oid, @diimpor_oleh
--   Gagal    : EXEC dbo.sp_fai_impor_gagal    @panen_id, @pesan, @diimpor_oleh   (pesan tampil di HP krani)
--   Ulang    : EXEC dbo.sp_fai_ulang_impor    @panen_id, @oleh                    (DITOLAK -> BARU)
--
-- Hal yang BELUM diputuskan dan sengaja tidak dihitung di sini (lihat progres/PROGRES.md, Fase 6):
--   * pemecahan kelas abnormal ke JJG_BUSUK / JJG_SAKIT / JJG_UNKNOWN (sementara semuanya di JJG_UNKNOWN dari HP),
--   * di kolom ERP mana unripe dan underripe dicatat (jjg_tanpa_kolom di bawah),
--   * isi HASILHI1 (janjang atau berat).
SET QUOTED_IDENTIFIER ON;
GO

CREATE OR ALTER VIEW dbo.v_fai_panen_siap_impor AS
SELECT
    f.id                      AS panen_id,
    f.unit_id,
    u.kode                    AS unit_kode,
    f.tanggal_panen,
    f.asm3_oid,
    f.blok_kode,
    f.tph_kode,
    f.rotasi,
    f.kondisi_cuaca,
    f.brondolan_kg,
    f.catatan,
    f.status_erp,
    f.status_lokasi,
    f.gps_lat,
    f.gps_lon,
    f.jarak_ke_tph_m,
    f.model_versi,
    f.diterima_server,
    f.disetujui_pada,
    pencatat.username         AS pencatat_username,
    penyetuju.username        AS disetujui_username,
    -- Jumlah janjang akhir (setelah koreksi) per kolom ERP, sesuai pemetaan di fai_panen_grading.erp_kolom.
    COALESCE(g.jjg_ripe, 0)     AS JJG_RIPE,
    COALESCE(g.jjg_overripe, 0) AS JJG_OVERRIPE,
    COALESCE(g.jjg_empty, 0)    AS JJG_EMPTY,
    COALESCE(g.jjg_busuk, 0)    AS JJG_BUSUK,
    COALESCE(g.jjg_sakit, 0)    AS JJG_SAKIT,
    COALESCE(g.jjg_unknown, 0)  AS JJG_UNKNOWN,
    COALESCE(g.jjg_landak, 0)   AS JJG_LANDAK,
    -- Janjang yang kelasnya belum punya kolom ERP (erp_kolom kosong), misalnya unripe dan underripe. Belum diputuskan.
    COALESCE(g.jjg_tanpa_kolom, 0) AS jjg_tanpa_kolom,
    COALESCE(g.jjg_total, 0)    AS jjg_total,
    -- Jumlah per kelas model, apa adanya (jml_final).
    COALESCE(g.k_ripe, 0)       AS jml_ripe,
    COALESCE(g.k_overripe, 0)   AS jml_overripe,
    COALESCE(g.k_underripe, 0)  AS jml_underripe,
    COALESCE(g.k_unripe, 0)     AS jml_unripe,
    COALESCE(g.k_empty, 0)      AS jml_empty,
    COALESCE(g.k_abnormal, 0)   AS jml_abnormal,
    COALESCE(p.jumlah_pemanen, 0) AS jumlah_pemanen
FROM dbo.fai_panen f
JOIN dbo.unit u ON u.unit_id = f.unit_id
LEFT JOIN dbo.app_user pencatat  ON pencatat.user_id  = f.dibuat_oleh
LEFT JOIN dbo.app_user penyetuju ON penyetuju.user_id = f.disetujui_oleh
OUTER APPLY (
    SELECT
        SUM(CASE WHEN x.erp_kolom = 'JJG_RIPE'    THEN x.jml_final ELSE 0 END) AS jjg_ripe,
        SUM(CASE WHEN x.erp_kolom = 'JJG_OVERRIPE' THEN x.jml_final ELSE 0 END) AS jjg_overripe,
        SUM(CASE WHEN x.erp_kolom = 'JJG_EMPTY'   THEN x.jml_final ELSE 0 END) AS jjg_empty,
        SUM(CASE WHEN x.erp_kolom = 'JJG_BUSUK'   THEN x.jml_final ELSE 0 END) AS jjg_busuk,
        SUM(CASE WHEN x.erp_kolom = 'JJG_SAKIT'   THEN x.jml_final ELSE 0 END) AS jjg_sakit,
        SUM(CASE WHEN x.erp_kolom = 'JJG_UNKNOWN' THEN x.jml_final ELSE 0 END) AS jjg_unknown,
        SUM(CASE WHEN x.erp_kolom = 'JJG_LANDAK'  THEN x.jml_final ELSE 0 END) AS jjg_landak,
        SUM(CASE WHEN x.erp_kolom IS NULL OR x.erp_kolom = '' THEN x.jml_final ELSE 0 END) AS jjg_tanpa_kolom,
        SUM(x.jml_final) AS jjg_total,
        SUM(CASE WHEN x.kelas_kode = 'ripe'      THEN x.jml_final ELSE 0 END) AS k_ripe,
        SUM(CASE WHEN x.kelas_kode = 'overripe'  THEN x.jml_final ELSE 0 END) AS k_overripe,
        SUM(CASE WHEN x.kelas_kode = 'underripe' THEN x.jml_final ELSE 0 END) AS k_underripe,
        SUM(CASE WHEN x.kelas_kode = 'unripe'    THEN x.jml_final ELSE 0 END) AS k_unripe,
        SUM(CASE WHEN x.kelas_kode = 'empty'     THEN x.jml_final ELSE 0 END) AS k_empty,
        SUM(CASE WHEN x.kelas_kode = 'abnormal'  THEN x.jml_final ELSE 0 END) AS k_abnormal
    FROM dbo.fai_panen_grading x
    WHERE x.panen_id = f.id AND x.dihapus_pada IS NULL
) g
OUTER APPLY (
    SELECT COUNT(*) AS jumlah_pemanen FROM dbo.fai_panen_pemanen y WHERE y.panen_id = f.id AND y.dihapus_pada IS NULL
) p
WHERE f.dihapus_pada IS NULL AND f.status_erp IN ('BARU', 'DIBACA');
GO

CREATE OR ALTER VIEW dbo.v_fai_panen_pemanen_siap_impor AS
SELECT pm.panen_id, pm.hrmb_code, pm.jjg_alokasi
FROM dbo.fai_panen_pemanen pm
JOIN dbo.fai_panen f ON f.id = pm.panen_id
WHERE pm.dihapus_pada IS NULL AND f.dihapus_pada IS NULL AND f.status_erp IN ('BARU', 'DIBACA');
GO

-- BARU -> DIBACA. Mengembalikan jumlah baris yang berubah (0 bila status bukan BARU: aman dipanggil ulang).
CREATE OR ALTER PROCEDURE dbo.sp_fai_tandai_dibaca @panen_id UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;
    UPDATE dbo.fai_panen SET status_erp = 'DIBACA', diubah_pada = SYSUTCDATETIME()
    WHERE id = @panen_id AND status_erp = 'BARU' AND dihapus_pada IS NULL;
    SELECT @@ROWCOUNT AS berubah;
END;
GO

-- Impor berhasil: BARU atau DIBACA -> DIIMPOR, menulis fai_impor_log (OK) dan audit_log (IMPOR), dalam satu transaksi.
CREATE OR ALTER PROCEDURE dbo.sp_fai_impor_ok
    @panen_id UNIQUEIDENTIFIER, @hvd1m_oid BIGINT, @hvd1da_oid BIGINT, @diimpor_oleh INT = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRAN;
    DECLARE @lama NVARCHAR(12);
    SELECT @lama = status_erp FROM dbo.fai_panen WITH (UPDLOCK, HOLDLOCK) WHERE id = @panen_id AND dihapus_pada IS NULL;
    IF @lama IS NULL THROW 50001, 'Catatan panen tidak ditemukan.', 1;
    IF @lama NOT IN ('BARU', 'DIBACA') THROW 50002, 'Catatan panen sudah diproses (bukan BARU atau DIBACA).', 1;

    UPDATE dbo.fai_panen SET status_erp = 'DIIMPOR', diubah_pada = SYSUTCDATETIME(), diubah_oleh = @diimpor_oleh
    WHERE id = @panen_id;
    INSERT INTO dbo.fai_impor_log (panen_id, waktu_impor, hvd1m_oid, hvd1da_oid, hasil, diimpor_oleh, dibuat_oleh)
    VALUES (@panen_id, SYSUTCDATETIME(), @hvd1m_oid, @hvd1da_oid, 'OK', @diimpor_oleh, @diimpor_oleh);
    INSERT INTO dbo.audit_log (audit_id, tabel, baris_id, aksi, nilai_lama, nilai_baru, user_id, waktu, waktu_server, keterangan)
    VALUES (NEWID(), 'fai_panen', CONVERT(NVARCHAR(50), @panen_id), 'IMPOR', @lama, 'DIIMPOR', @diimpor_oleh,
            SYSUTCDATETIME(), SYSUTCDATETIME(),
            LEFT(CONCAT('hvd1m=', @hvd1m_oid, ' hvd1da=', @hvd1da_oid), 300));
    COMMIT;
    SELECT 1 AS berubah;
END;
GO

-- Impor gagal: BARU atau DIBACA -> DITOLAK, @pesan tampil di HP krani. Satu transaksi dengan log dan audit.
CREATE OR ALTER PROCEDURE dbo.sp_fai_impor_gagal
    @panen_id UNIQUEIDENTIFIER, @pesan NVARCHAR(500), @diimpor_oleh INT = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    IF @pesan IS NULL OR LTRIM(RTRIM(@pesan)) = N'' THROW 50003, 'Pesan alasan penolakan wajib diisi.', 1;
    BEGIN TRAN;
    DECLARE @lama NVARCHAR(12);
    SELECT @lama = status_erp FROM dbo.fai_panen WITH (UPDLOCK, HOLDLOCK) WHERE id = @panen_id AND dihapus_pada IS NULL;
    IF @lama IS NULL THROW 50001, 'Catatan panen tidak ditemukan.', 1;
    IF @lama NOT IN ('BARU', 'DIBACA') THROW 50002, 'Catatan panen sudah diproses (bukan BARU atau DIBACA).', 1;

    UPDATE dbo.fai_panen SET status_erp = 'DITOLAK', diubah_pada = SYSUTCDATETIME(), diubah_oleh = @diimpor_oleh
    WHERE id = @panen_id;
    INSERT INTO dbo.fai_impor_log (panen_id, waktu_impor, hasil, pesan_error, diimpor_oleh, dibuat_oleh)
    VALUES (@panen_id, SYSUTCDATETIME(), 'GAGAL', LEFT(@pesan, 500), @diimpor_oleh, @diimpor_oleh);
    INSERT INTO dbo.audit_log (audit_id, tabel, baris_id, aksi, nilai_lama, nilai_baru, user_id, waktu, waktu_server, keterangan)
    VALUES (NEWID(), 'fai_panen', CONVERT(NVARCHAR(50), @panen_id), 'IMPOR_GAGAL', @lama, 'DITOLAK', @diimpor_oleh,
            SYSUTCDATETIME(), SYSUTCDATETIME(), LEFT(@pesan, 300));
    COMMIT;
    SELECT 1 AS berubah;
END;
GO

-- DITOLAK -> BARU, supaya catatan yang ditolak karena data ERP belum lengkap bisa diimpor lagi setelah diperbaiki.
-- Riwayat fai_impor_log tetap (hanya ditambah). Status BARU dilihat HP sebagai TERKIRIM lagi.
CREATE OR ALTER PROCEDURE dbo.sp_fai_ulang_impor @panen_id UNIQUEIDENTIFIER, @oleh INT = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRAN;
    UPDATE dbo.fai_panen SET status_erp = 'BARU', diubah_pada = SYSUTCDATETIME(), diubah_oleh = @oleh
    WHERE id = @panen_id AND status_erp = 'DITOLAK' AND dihapus_pada IS NULL;
    DECLARE @n INT = @@ROWCOUNT;
    IF @n = 1
        INSERT INTO dbo.audit_log (audit_id, tabel, baris_id, aksi, nilai_lama, nilai_baru, user_id, waktu, waktu_server)
        VALUES (NEWID(), 'fai_panen', CONVERT(NVARCHAR(50), @panen_id), 'IMPOR_ULANG', 'DITOLAK', 'BARU', @oleh,
                SYSUTCDATETIME(), SYSUTCDATETIME());
    COMMIT;
    SELECT @n AS berubah;
END;
GO
