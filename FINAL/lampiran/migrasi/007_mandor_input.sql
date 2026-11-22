-- Migrasi 007 (18 Nov 2026): MANDOR yang menginput dari HP. Aman dijalankan berulang. Jalankan SETELAH 005, dengan sqlcmd -I.
--
-- Perubahan aturan bisnis (dokumen asisten ERP, "Perubahan_Mobile_API_fAI26_untuk_HVDM"):
--   * Yang menginput adalah mandor; akun mandor membawa kode HRMB mandor dan afdelingnya (diisi admin).
--   * Satu catatan = satu TPH = satu pemanen, disimpan langsung di fai_panen.pemanen_hrmb_code.
--   * Anti ganda: kombinasi tanggal + unit + blok + TPH + pemanen hanya boleh satu catatan (API menjawab 409).
--   * fai_panen_pemanen DEPRECATED: tidak lagi menjadi sumber data. Tabel tetap ada dan tetap diisi satu baris sebagai
--     salinan kompatibilitas (detail catatan di web admin dan view pemanen lama membacanya). Jangan dibaca HVDM; pakai
--     fai_panen.pemanen_hrmb_code atau kolom pemanen_hrmb_code di v_fai_panen_siap_impor.
SET QUOTED_IDENTIFIER ON;
GO

IF COL_LENGTH('dbo.app_user', 'mandor_hrmb_code') IS NULL
    ALTER TABLE dbo.app_user ADD mandor_hrmb_code INT NULL;   -- kode HRMB mandor pemilik akun (NULL untuk akun non-mandor)
GO
IF COL_LENGTH('dbo.app_user', 'afdeling_kode') IS NULL
    ALTER TABLE dbo.app_user ADD afdeling_kode NVARCHAR(20) NULL;   -- kode divisi/afdeling mandor (= divisi.kode)
GO
IF COL_LENGTH('dbo.fai_panen', 'pemanen_hrmb_code') IS NULL
    ALTER TABLE dbo.fai_panen ADD pemanen_hrmb_code INT NULL;
GO

-- Isi catatan lama dari tabel pemanen lama (satu baris per catatan sejak 005).
UPDATE f SET f.pemanen_hrmb_code = x.hrmb_code
FROM dbo.fai_panen f
CROSS APPLY (SELECT TOP 1 y.hrmb_code FROM dbo.fai_panen_pemanen y WHERE y.panen_id = f.id AND y.dihapus_pada IS NULL ORDER BY y.id) x
WHERE f.pemanen_hrmb_code IS NULL;
GO

-- View HVDM: kolom lama dipertahankan; ditambah unit dan waktu_panen. pemanen_hrmb_code kini dari fai_panen.
CREATE OR ALTER VIEW dbo.v_fai_panen_siap_impor AS
SELECT
    f.id                      AS panen_id,
    f.unit_id,
    u.kode                    AS unit_kode,
    u.kode                    AS unit,
    u.erp_db                  AS unit_erp_db,
    f.tanggal_panen,
    f.asm3_oid,
    f.blok_kode,
    f.tph_kode,
    f.afdeling_kode,
    f.afdeling_erp_oid,
    f.mandor_hrmb_code,
    COALESCE(f.pemanen_hrmb_code, pm.hrmb_code) AS pemanen_hrmb_code,   -- satu TPH = satu pemanen
    pm.jjg_alokasi            AS pemanen_jjg,         -- sama dengan jjg_total (seluruh janjang dikreditkan ke pemanen ini)
    f.dibuat_pada             AS waktu_panen,          -- waktu catatan dibuat di HP (UTC)
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
    COALESCE(g.jjg_ripe, 0)     AS JJG_RIPE,
    COALESCE(g.jjg_overripe, 0) AS JJG_OVERRIPE,
    COALESCE(g.jjg_empty, 0)    AS JJG_EMPTY,
    COALESCE(g.jjg_busuk, 0)    AS JJG_BUSUK,
    COALESCE(g.jjg_sakit, 0)    AS JJG_SAKIT,
    COALESCE(g.jjg_unknown, 0)  AS JJG_UNKNOWN,
    COALESCE(g.jjg_landak, 0)   AS JJG_LANDAK,
    COALESCE(g.jjg_tanpa_kolom, 0) AS jjg_tanpa_kolom,
    COALESCE(g.jjg_total, 0)    AS jjg_total,
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
    SELECT TOP 1 y.hrmb_code, y.jjg_alokasi FROM dbo.fai_panen_pemanen y
    WHERE y.panen_id = f.id AND y.dihapus_pada IS NULL ORDER BY y.id
) pm
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

-- Pemanen tunggal per catatan, kini diturunkan dari fai_panen (bukan dari tabel deprecated).
CREATE OR ALTER VIEW dbo.v_fai_panen_pemanen_siap_impor AS
SELECT f.id AS panen_id, f.pemanen_hrmb_code AS hrmb_code,
       COALESCE((SELECT SUM(g.jml_final) FROM dbo.fai_panen_grading g WHERE g.panen_id = f.id AND g.dihapus_pada IS NULL), 0) AS jjg_alokasi
FROM dbo.fai_panen f
WHERE f.pemanen_hrmb_code IS NOT NULL AND f.dihapus_pada IS NULL AND f.status_erp IN ('BARU', 'DIBACA');
GO

-- Anti ganda. Bila ada kombinasi ganda lama, migrasi berhenti dengan pesan; bereskan datanya (hapus lunak satu catatan) lalu ulangi.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ux_fai_panen_tph_pemanen' AND object_id = OBJECT_ID('dbo.fai_panen'))
BEGIN
    IF EXISTS (
        SELECT 1 FROM dbo.fai_panen
        WHERE dihapus_pada IS NULL AND pemanen_hrmb_code IS NOT NULL AND tph_kode IS NOT NULL
        GROUP BY tanggal_panen, unit_id, asm3_oid, tph_kode, pemanen_hrmb_code HAVING COUNT(*) > 1)
        THROW 50007, 'Ada catatan panen ganda (tanggal + unit + blok + TPH + pemanen sama). Hapus lunak yang berlebih lalu jalankan ulang 007.', 1;
    CREATE UNIQUE INDEX ux_fai_panen_tph_pemanen ON dbo.fai_panen (tanggal_panen, unit_id, asm3_oid, tph_kode, pemanen_hrmb_code)
        WHERE dihapus_pada IS NULL AND pemanen_hrmb_code IS NOT NULL AND tph_kode IS NOT NULL;
END
GO
