-- Migrasi 009 (21 Nov 2026): modul IPD (Inspeksi Panen Detail). Aman dijalankan berulang. Jalankan SETELAH 008, dengan sqlcmd -I.
--
-- Isi: tabel ipd_kode_temuan, fai_ipd, fai_ipd_pokok, fai_ipd_temuan, fai_ipd_foto; 11 kode temuan; role ASKEP dan KTU
-- (hanya melihat); permission akses.panen, akses.ipd, ipd.* dan laporan.ipd. Tabel panen TIDAK diubah.
--
-- Alur: inspektur (mandor atau asisten) mengisi pokok per pokok di HP, menekan kirim (otomatis valid), server menyimpan
-- MENUNGGU, asisten (ipd.setujui) menyetujui atau menolak. Belum ada jembatan ke ERP (TCode belum ditentukan).
SET QUOTED_IDENTIFIER ON;
GO

-- Modul IPD (Inspeksi Panen Detail), 21 Nov 2026. Satu catatan = satu pemanen (satu ancak).
IF OBJECT_ID('dbo.ipd_kode_temuan', 'U') IS NULL
CREATE TABLE dbo.ipd_kode_temuan (
    kode_id               INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    kode                  NVARCHAR(10) NOT NULL UNIQUE,    -- A, G, S, M1, M2, M3, M4, B1, B2, C, R
    nama                  NVARCHAR(100) NOT NULL,
    kelompok              NVARCHAR(10) NOT NULL,
    urut                  INT NOT NULL DEFAULT 0,
    aktif                 BIT NOT NULL DEFAULT 1,
    dibuat_pada           DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    dibuat_oleh           INT,
    diubah_pada           DATETIME2,
    diubah_oleh           INT,
    dihapus_pada          DATETIME2,
    dihapus_oleh          INT,
    CONSTRAINT ck_ipd_kode_temuan_kelompok CHECK (kelompok IN ('BUAH','BRONDOLAN','PELEPAH'))
);
GO

-- Asisten tiap divisi (Divisi 1 = Ferdi, Divisi 2 = Ginting menurut Ptoni; diisi admin lewat web, bisa diganti kapan saja).
IF COL_LENGTH('dbo.divisi', 'asisten_user_id') IS NULL
    ALTER TABLE dbo.divisi ADD asisten_user_id INT NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'fk_divisi_asisten_user_id')
    ALTER TABLE dbo.divisi ADD CONSTRAINT fk_divisi_asisten_user_id FOREIGN KEY (asisten_user_id) REFERENCES dbo.app_user(user_id);
GO

IF OBJECT_ID('dbo.fai_ipd', 'U') IS NULL
CREATE TABLE dbo.fai_ipd (
    id                    UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,    -- sama dengan ipd_id di HP
    unit_id               INT NOT NULL,
    device_id             UNIQUEIDENTIFIER NOT NULL,
    tanggal_inspeksi      DATE NOT NULL,
    asm3_oid              BIGINT NOT NULL,                 -- OID blok di ERP (disalin dari master saat diterima)
    blok_kode             NVARCHAR(20),
    divisi_id             INT NOT NULL,                    -- dipilih di awal inspeksi
    asisten_user_id       INT,                             -- asisten divisi saat dikirim = penyetuju; NULL bila divisi belum punya asisten
    pemanen_hrmb_code     INT NOT NULL,                    -- satu catatan = satu pemanen
    ancak_no              NVARCHAR(30),
    mandor_blok_user_id   INT,                             -- akun berperan MANDOR
    mandor_blok_hrmb      INT,                             -- salinan kode HRMB saat dipilih
    mandor_ancak_user_id  INT,
    mandor_ancak_hrmb     INT,
    catatan               NVARCHAR(500),
    status                NVARCHAR(12) NOT NULL DEFAULT 'MENUNGGU',   -- MENUNGGU asisten, DISETUJUI, DITOLAK
    pesan_tolak           NVARCHAR(300),
    diterima_server       DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    divalidasi_oleh       INT,                             -- inspektur (valid otomatis saat kirim)
    divalidasi_pada       DATETIME2,
    disetujui_oleh        INT,                             -- asisten
    disetujui_pada        DATETIME2,
    dibuat_pada           DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    dibuat_oleh           INT,
    diubah_pada           DATETIME2,
    diubah_oleh           INT,
    dihapus_pada          DATETIME2,
    dihapus_oleh          INT,            -- dibuat_oleh = yang menginspeksi
    CONSTRAINT fk_fai_ipd_unit_id FOREIGN KEY (unit_id) REFERENCES dbo.unit(unit_id),
    CONSTRAINT fk_fai_ipd_device_id FOREIGN KEY (device_id) REFERENCES dbo.device(device_id),
    CONSTRAINT fk_fai_ipd_divisi_id FOREIGN KEY (divisi_id) REFERENCES dbo.divisi(divisi_id),
    CONSTRAINT fk_fai_ipd_asisten FOREIGN KEY (asisten_user_id) REFERENCES dbo.app_user(user_id),
    CONSTRAINT fk_fai_ipd_mandor_blok FOREIGN KEY (mandor_blok_user_id) REFERENCES dbo.app_user(user_id),
    CONSTRAINT fk_fai_ipd_mandor_ancak FOREIGN KEY (mandor_ancak_user_id) REFERENCES dbo.app_user(user_id),
    CONSTRAINT ck_fai_ipd_status CHECK (status IN ('MENUNGGU','DISETUJUI','DITOLAK'))
);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_fai_ipd_unit_id_tanggal_inspeksi')
    CREATE INDEX ix_fai_ipd_unit_id_tanggal_inspeksi ON dbo.fai_ipd (unit_id, tanggal_inspeksi);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_fai_ipd_status')
    CREATE INDEX ix_fai_ipd_status ON dbo.fai_ipd (status, tanggal_inspeksi);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_fai_ipd_asisten_status')
    CREATE INDEX ix_fai_ipd_asisten_status ON dbo.fai_ipd (asisten_user_id, status);
-- Peringatan "ancak ini sudah pernah diinspeksi": inspeksi terakhir pemanen + blok yang sama.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_fai_ipd_pemanen_blok_tanggal')
    CREATE INDEX ix_fai_ipd_pemanen_blok_tanggal ON dbo.fai_ipd (pemanen_hrmb_code, asm3_oid, tanggal_inspeksi);
GO

IF OBJECT_ID('dbo.fai_ipd_pokok', 'U') IS NULL
CREATE TABLE dbo.fai_ipd_pokok (
    id                    BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    ipd_id                UNIQUEIDENTIFIER NOT NULL,
    no_baris              INT NOT NULL,
    no_pokok              INT NOT NULL,
    dibuat_pada           DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    dibuat_oleh           INT,
    diubah_pada           DATETIME2,
    diubah_oleh           INT,
    dihapus_pada          DATETIME2,
    dihapus_oleh          INT,
    CONSTRAINT fk_fai_ipd_pokok_ipd_id FOREIGN KEY (ipd_id) REFERENCES dbo.fai_ipd(id) ON DELETE CASCADE,
    CONSTRAINT ck_fai_ipd_pokok_angka CHECK (no_baris > 0 AND no_pokok > 0)
);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ux_fai_ipd_pokok_ipd_id_baris_pokok')
    CREATE UNIQUE INDEX ux_fai_ipd_pokok_ipd_id_baris_pokok ON dbo.fai_ipd_pokok (ipd_id, no_baris, no_pokok) WHERE dihapus_pada IS NULL;
GO

IF OBJECT_ID('dbo.fai_ipd_temuan', 'U') IS NULL
CREATE TABLE dbo.fai_ipd_temuan (
    id                    BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    pokok_id              BIGINT NOT NULL,
    kode_id               INT NOT NULL,
    jumlah                INT NOT NULL DEFAULT 1,
    catatan               NVARCHAR(200),
    dibuat_pada           DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    dibuat_oleh           INT,
    diubah_pada           DATETIME2,
    diubah_oleh           INT,
    dihapus_pada          DATETIME2,
    dihapus_oleh          INT,
    CONSTRAINT fk_fai_ipd_temuan_pokok_id FOREIGN KEY (pokok_id) REFERENCES dbo.fai_ipd_pokok(id) ON DELETE CASCADE,
    CONSTRAINT fk_fai_ipd_temuan_kode_id FOREIGN KEY (kode_id) REFERENCES dbo.ipd_kode_temuan(kode_id),
    CONSTRAINT ck_fai_ipd_temuan_jumlah CHECK (jumlah > 0)
);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ux_fai_ipd_temuan_pokok_id_kode_id')
    CREATE UNIQUE INDEX ux_fai_ipd_temuan_pokok_id_kode_id ON dbo.fai_ipd_temuan (pokok_id, kode_id) WHERE dihapus_pada IS NULL;
GO

IF OBJECT_ID('dbo.fai_ipd_foto', 'U') IS NULL
CREATE TABLE dbo.fai_ipd_foto (
    id                    UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    ipd_id                UNIQUEIDENTIFIER NOT NULL,
    keterangan            NVARCHAR(200),
    urut                  INT NOT NULL DEFAULT 0,
    nama_file             NVARCHAR(200) NOT NULL,
    sha256                NVARCHAR(64),
    dibuat_pada           DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    dibuat_oleh           INT,
    diubah_pada           DATETIME2,
    diubah_oleh           INT,
    dihapus_pada          DATETIME2,
    dihapus_oleh          INT,
    CONSTRAINT fk_fai_ipd_foto_ipd_id FOREIGN KEY (ipd_id) REFERENCES dbo.fai_ipd(id) ON DELETE CASCADE
);
GO

-- Kode temuan IPD (urutan sesuai form lapangan).
INSERT INTO dbo.ipd_kode_temuan (kode, nama, kelompok, urut)
SELECT x.kode, x.nama, x.kelompok, x.urut
FROM (VALUES
    ('A',  N'Potong buah mentah',               'BUAH',      1),
    ('G',  N'Tangkai panjang',                  'BUAH',      2),
    ('S',  N'Buah masak tidak dipanen',         'BUAH',      3),
    ('M1', N'Buah diperam',                     'BUAH',      4),
    ('M2', N'Buah dipanen tidak dibawa ke TPH', 'BUAH',      5),
    ('M3', N'Buah matahari',                    'BUAH',      6),
    ('M4', N'Buah tidak disusun di TPH',        'BUAH',      7),
    ('B1', N'Brondolan tak dikutip > 3 butir',  'BRONDOLAN', 8),
    ('B2', N'Brondolan dibuang ke gawangan',    'BRONDOLAN', 9),
    ('C',  N'Pelepah sengkleh tidak dipotong',  'PELEPAH',   10),
    ('R',  N'Pelepah tidak disusun di gawangan','PELEPAH',   11)
) AS x (kode, nama, kelompok, urut)
WHERE NOT EXISTS (SELECT 1 FROM dbo.ipd_kode_temuan k WHERE k.kode = x.kode);
GO

-- Role baru: ASKEP (Asisten Kepala) dan KTU, hanya melihat.
INSERT INTO dbo.role (kode, nama, deskripsi, bawaan)
SELECT x.kode, x.nama, x.deskripsi, 1
FROM (VALUES
    ('ASKEP', N'Askep', N'Melihat dashboard, catatan panen, produktivitas, dan laporan IPD (tanpa mengubah)'),
    ('KTU',   N'KTU',   N'Melihat dashboard, catatan panen, produktivitas, dan laporan IPD (tanpa mengubah)')
) AS x (kode, nama, deskripsi)
WHERE NOT EXISTS (SELECT 1 FROM dbo.role r WHERE r.kode = x.kode);
GO

-- Permission baru. akses.panen dan akses.ipd menentukan kotak mana yang muncul di layar awal HP.
INSERT INTO dbo.permission (kode, modul, nama)
SELECT x.kode, x.modul, x.nama
FROM (VALUES
    ('akses.panen',  'Akses HP', N'Membuka fitur Panen di HP'),
    ('akses.ipd',    'Akses HP', N'Membuka fitur IPD di HP'),
    ('ipd.buat',     'IPD',      N'Membuat inspeksi panen detail'),
    ('ipd.kirim',    'IPD',      N'Mengirim inspeksi (otomatis menyatakan valid)'),
    ('ipd.setujui',  'IPD',      N'Menyetujui inspeksi yang dikirim mandor'),
    ('ipd.lihat',    'IPD',      N'Melihat daftar inspeksi di dashboard web'),
    ('laporan.ipd',  'Laporan',  N'Melihat laporan loses IPD'),
    ('ipd.kelola',   'IPD',      N'Mengelola kode temuan IPD')
) AS x (kode, modul, nama)
WHERE NOT EXISTS (SELECT 1 FROM dbo.permission p WHERE p.kode = x.kode);
GO

-- Hak bawaan role. Peran lama tetap memegang Panen (akses.panen) supaya akun yang ada tidak kehilangan fitur.
INSERT INTO dbo.role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM (VALUES
    ('KRANI',  'akses.panen'),
    ('MANDOR', 'akses.panen'),
    ('MANDOR', 'akses.ipd'),
    ('MANDOR', 'ipd.buat'),
    ('MANDOR', 'ipd.kirim'),
    ('MANDOR', 'ipd.lihat'),
    ('ASISTEN','akses.panen'),
    ('ASISTEN','akses.ipd'),
    ('ASISTEN','ipd.buat'),
    ('ASISTEN','ipd.kirim'),
    ('ASISTEN','ipd.setujui'),
    ('ASISTEN','ipd.lihat'),
    ('ASISTEN','laporan.ipd'),
    ('ADMIN',  'ipd.lihat'),
    ('ADMIN',  'laporan.ipd'),
    ('ADMIN',  'ipd.kelola'),
    ('AUDITOR','ipd.lihat'),
    ('AUDITOR','laporan.ipd'),
    ('ASKEP',  'panen.lihat'),
    ('ASKEP',  'laporan.lihat'),
    ('ASKEP',  'master.lihat'),
    ('ASKEP',  'ipd.lihat'),
    ('ASKEP',  'laporan.ipd'),
    ('KTU',    'panen.lihat'),
    ('KTU',    'laporan.lihat'),
    ('KTU',    'master.lihat'),
    ('KTU',    'ipd.lihat'),
    ('KTU',    'laporan.ipd')
) AS x (role_kode, perm_kode)
JOIN dbo.role r ON r.kode = x.role_kode
JOIN dbo.permission p ON p.kode = x.perm_kode
WHERE NOT EXISTS (SELECT 1 FROM dbo.role_permission rp WHERE rp.role_id = r.role_id AND rp.permission_id = p.permission_id AND rp.dihapus_pada IS NULL);
GO
