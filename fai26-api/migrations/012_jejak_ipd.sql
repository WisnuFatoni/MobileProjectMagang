-- fAI26: jalur GPS inspeksi IPD. Saat inspeksi berjalan HP mencatat titik GPS (tiap 3 meter, akurasi baik saja); titik-titik itu
-- ikut terkirim bersama inspeksi dan ditampilkan di web sebagai garis di peta, lengkap dengan penanda tiap pokok. Tiap pokok juga
-- menyimpan waktu dicatatnya (dicatat_pada) supaya bisa dipasangkan dengan titik jalur terdekat.
-- Jalankan setelah 011_foto_profil.sql. Aman dijalankan berulang.

IF COL_LENGTH('dbo.fai_ipd_pokok', 'dicatat_pada') IS NULL
    ALTER TABLE dbo.fai_ipd_pokok ADD dicatat_pada DATETIME2 NULL;
GO

IF OBJECT_ID('dbo.fai_ipd_jejak', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.fai_ipd_jejak (
        jejak_id   BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        ipd_id     UNIQUEIDENTIFIER NOT NULL,
        urut       INT NOT NULL,                 -- urutan titik dalam satu inspeksi (0, 1, 2, ...)
        waktu      DATETIME2 NOT NULL,           -- waktu titik diambil (UTC)
        lat        DECIMAL(9,6) NOT NULL,
        lon        DECIMAL(9,6) NOT NULL,
        akurasi_m  DECIMAL(6,1) NULL,
        CONSTRAINT fk_fai_ipd_jejak_ipd FOREIGN KEY (ipd_id) REFERENCES dbo.fai_ipd(id),
        CONSTRAINT ux_fai_ipd_jejak_ipd_urut UNIQUE (ipd_id, urut)
    );
END
GO

-- Hak melihat jalur: asisten, askep, KTU, dan admin (mandor tidak, sesuai keputusan Ptoni).
INSERT INTO dbo.permission (kode, modul, nama)
SELECT 'ipd.jejak', 'IPD', N'Melihat jalur GPS inspeksi di peta'
WHERE NOT EXISTS (SELECT 1 FROM dbo.permission WHERE kode = 'ipd.jejak');
GO

INSERT INTO dbo.role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM (VALUES ('ADMIN'), ('ASISTEN'), ('ASKEP'), ('KTU')) AS x (kode)
JOIN dbo.role r ON r.kode = x.kode AND r.dihapus_pada IS NULL
JOIN dbo.permission p ON p.kode = 'ipd.jejak'
WHERE NOT EXISTS (SELECT 1 FROM dbo.role_permission rp WHERE rp.role_id = r.role_id AND rp.permission_id = p.permission_id);
GO
