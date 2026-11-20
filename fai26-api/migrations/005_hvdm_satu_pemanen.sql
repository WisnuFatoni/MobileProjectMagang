-- Migrasi 005 (18 Nov 2026): satu TPH = satu pemanen, ditambah mandor dan afdeling untuk header HVDM. Aman dijalankan berulang.
-- Jalankan SETELAH 002 dan SEBELUM atau sesudah 003 (view dibuat ulang di sini dengan CREATE OR ALTER, jadi 005 menggantikan
-- versi view di 003). Gunakan sqlcmd -I.
--
-- Latar belakang (temuan 18 Nov dari data HVD1 kp_le2026 dan jawaban rekan kerja Ptoni): di PT ini satu TPH hanya untuk
-- SATU pemanen (buah tidak bercampur antar pemanen). Header HVD1 dikelompokkan per mandor + afdeling + tanggal, dan satu
-- baris detail per pemanen. Karena itu setiap catatan fAI26 sekarang membawa tepat satu pemanen, dan server menyimpan
-- (sebagai salinan saat catatan diterima) afdeling dan mandor-nya.
SET QUOTED_IDENTIFIER ON;
GO

-- Master pemanen: kode HRMB mandor kelompoknya (diisi admin di web). [Dugaan] mandor di ERP diidentifikasi dengan kode HRMB.
IF COL_LENGTH('dbo.pemanen', 'mandor_hrmb_code') IS NULL
    ALTER TABLE dbo.pemanen ADD mandor_hrmb_code INT NULL;
GO

-- Salinan pada catatan panen, diisi server saat catatan diterima (supaya perubahan master tidak mengubah catatan lama):
--   afdeling_kode / afdeling_erp_oid : dari blok (erp_oid = asm3_oid) -> divisi.kode / divisi.erp_oid
--   mandor_hrmb_code                 : dari master pemanen (erp_hrmb_code = pemanen pada catatan) -> mandor_hrmb_code
-- NULL berarti master belum lengkap; HVDM sebaiknya menolak dengan pesan yang jelas (sp_fai_impor_gagal).
IF COL_LENGTH('dbo.fai_panen', 'afdeling_kode') IS NULL
    ALTER TABLE dbo.fai_panen ADD afdeling_kode NVARCHAR(20) NULL;
GO
IF COL_LENGTH('dbo.fai_panen', 'afdeling_erp_oid') IS NULL
    ALTER TABLE dbo.fai_panen ADD afdeling_erp_oid INT NULL;
GO
IF COL_LENGTH('dbo.fai_panen', 'mandor_hrmb_code') IS NULL
    ALTER TABLE dbo.fai_panen ADD mandor_hrmb_code INT NULL;
GO

-- View baca untuk HVDM (menggantikan versi di 003): ditambah afdeling, mandor, dan pemanen tunggal.
CREATE OR ALTER VIEW dbo.v_fai_panen_siap_impor AS
SELECT
    f.id                      AS panen_id,
    f.unit_id,
    u.kode                    AS unit_kode,
    u.erp_db                  AS unit_erp_db,
    f.tanggal_panen,
    f.asm3_oid,
    f.blok_kode,
    f.tph_kode,
    f.afdeling_kode,
    f.afdeling_erp_oid,
    f.mandor_hrmb_code,
    pm.hrmb_code              AS pemanen_hrmb_code,   -- satu TPH = satu pemanen
    pm.jjg_alokasi            AS pemanen_jjg,         -- sama dengan jjg_total (seluruh janjang dikreditkan ke pemanen ini)
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
