-- 01 SKEMA LENGKAP fai26 (skema dasar + migrasi 002, 003, 005 sampai 012)
-- Dibuat 22 Nov 2026 dari database pengembangan fai26_dev (API 0.21.0, migrasi sampai 012).
-- Untuk DATABASE KOSONG, dijalankan SEKALI. Skema dasar membuat tabel tanpa pemeriksaan "bila belum ada" (menjalankannya dua kali gagal);
-- migrasi 002 dan seterusnya sendiri aman diulang.
-- Migrasi 004 (audit_log hanya-tambah) TIDAK ada di sini: lihat 05_audit_log_hanya_tambah.sql.
-- Jalankan dengan: sqlcmd -S <server> -d <database> -U <akun> -I -b -f 65001 -i <berkas>


-- ===== SKEMA DASAR (erd/fAI26_server_sqlserver.sql) =====
-- fAI26: database server (SQL Server). Dihasilkan dari skema yang sama dengan ERD.
-- Waktu disimpan UTC (DATETIME2). Sudah dijalankan di SQL Server 2025; tetap jalankan di database uji dulu.

-- Indeks terfilter (WHERE ...) mensyaratkan opsi ini. sqlcmd mematikannya secara bawaan.
SET QUOTED_IDENTIFIER ON;
GO

CREATE TABLE dbo.unit (
    unit_id               INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    kode                  NVARCHAR(20) NOT NULL UNIQUE,
    nama                  NVARCHAR(100) NOT NULL,
    erp_db                NVARCHAR(50) NOT NULL,
    tahun_cluster         INT NOT NULL,
    aktif                 BIT NOT NULL DEFAULT 1,
    dibuat_pada           DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    dibuat_oleh           INT,
    diubah_pada           DATETIME2,
    diubah_oleh           INT,
    dihapus_pada          DATETIME2,
    dihapus_oleh          INT
);

CREATE TABLE dbo.device (
    device_id             UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    nama                  NVARCHAR(60),
    os_versi              NVARCHAR(30),
    app_versi             NVARCHAR(20),
    aktif                 BIT NOT NULL DEFAULT 1,
    terakhir_sinkron      DATETIME2,
    dibuat_pada           DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    dibuat_oleh           INT,
    diubah_pada           DATETIME2,
    diubah_oleh           INT,
    dihapus_pada          DATETIME2,
    dihapus_oleh          INT
);

-- Master yang diunduh HP (GET /api/master). Isinya salinan dari ERP atau diisi admin; HP menerimanya apa adanya.
CREATE TABLE dbo.divisi (
    divisi_id             INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    unit_id               INT NOT NULL,
    erp_oid               INT,
    kode                  NVARCHAR(20) NOT NULL,
    nama                  NVARCHAR(100) NOT NULL,
    aktif                 BIT NOT NULL DEFAULT 1,
    dibuat_pada           DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    dibuat_oleh           INT,
    diubah_pada           DATETIME2,
    diubah_oleh           INT,
    dihapus_pada          DATETIME2,
    dihapus_oleh          INT,
    CONSTRAINT fk_divisi_unit_id FOREIGN KEY (unit_id) REFERENCES dbo.unit(unit_id)
);

CREATE TABLE dbo.blok (
    blok_id               INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    unit_id               INT NOT NULL,
    divisi_id             INT,
    erp_oid               BIGINT NOT NULL,
    kode                  NVARCHAR(20) NOT NULL,
    nama                  NVARCHAR(100),
    luas_ha               FLOAT,
    tahun_tanam           INT,
    sph                   FLOAT,
    aktif                 BIT NOT NULL DEFAULT 1,
    dibuat_pada           DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    dibuat_oleh           INT,
    diubah_pada           DATETIME2,
    diubah_oleh           INT,
    dihapus_pada          DATETIME2,
    dihapus_oleh          INT,
    CONSTRAINT fk_blok_unit_id FOREIGN KEY (unit_id) REFERENCES dbo.unit(unit_id),
    CONSTRAINT fk_blok_divisi_id FOREIGN KEY (divisi_id) REFERENCES dbo.divisi(divisi_id)
);

CREATE UNIQUE INDEX ux_blok_unit_id_kode ON dbo.blok (unit_id, kode) WHERE dihapus_pada IS NULL;

CREATE TABLE dbo.tph (
    tph_id                INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    blok_id               INT NOT NULL,
    no_tph                NVARCHAR(10) NOT NULL,
    qr_kode               NVARCHAR(100) NOT NULL UNIQUE,
    lat                   FLOAT,
    lon                   FLOAT,
    aktif                 BIT NOT NULL DEFAULT 1,
    dibuat_pada           DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    dibuat_oleh           INT,
    diubah_pada           DATETIME2,
    diubah_oleh           INT,
    dihapus_pada          DATETIME2,
    dihapus_oleh          INT,
    CONSTRAINT fk_tph_blok_id FOREIGN KEY (blok_id) REFERENCES dbo.blok(blok_id)
);

CREATE UNIQUE INDEX ux_tph_blok_id_no_tph ON dbo.tph (blok_id, no_tph) WHERE dihapus_pada IS NULL;

CREATE TABLE dbo.pemanen (
    pemanen_id            INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    unit_id               INT NOT NULL,
    erp_hrmb_code         INT NOT NULL,
    mandor_hrmb_code      INT,
    kode                  NVARCHAR(20),
    nama                  NVARCHAR(100) NOT NULL,
    aktif                 BIT NOT NULL DEFAULT 1,
    dibuat_pada           DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    dibuat_oleh           INT,
    diubah_pada           DATETIME2,
    diubah_oleh           INT,
    dihapus_pada          DATETIME2,
    dihapus_oleh          INT,
    CONSTRAINT fk_pemanen_unit_id FOREIGN KEY (unit_id) REFERENCES dbo.unit(unit_id)
);

CREATE TABLE dbo.app_user (
    user_id               INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    unit_id               INT NOT NULL,
    erp_user_oid          INT,
    mandor_hrmb_code      INT,
    afdeling_kode         NVARCHAR(20),
    username              NVARCHAR(50) NOT NULL UNIQUE,
    nama                  NVARCHAR(100) NOT NULL,
    aktif                 BIT NOT NULL DEFAULT 1,
    gagal_login           INT NOT NULL DEFAULT 0,
    terkunci_sampai       DATETIME2,
    login_terakhir        DATETIME2,
    password_hash         NVARCHAR(255) NOT NULL,
    wajib_ganti_password  BIT NOT NULL DEFAULT 0,
    dibuat_pada           DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    dibuat_oleh           INT,
    diubah_pada           DATETIME2,
    diubah_oleh           INT,
    dihapus_pada          DATETIME2,
    dihapus_oleh          INT,
    CONSTRAINT fk_app_user_unit_id FOREIGN KEY (unit_id) REFERENCES dbo.unit(unit_id)
);

CREATE TABLE dbo.role (
    role_id               INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    kode                  NVARCHAR(30) NOT NULL UNIQUE,
    nama                  NVARCHAR(60) NOT NULL,
    deskripsi             NVARCHAR(200),
    bawaan                BIT NOT NULL DEFAULT 0,
    dibuat_pada           DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    dibuat_oleh           INT,
    diubah_pada           DATETIME2,
    diubah_oleh           INT,
    dihapus_pada          DATETIME2,
    dihapus_oleh          INT
);

CREATE TABLE dbo.permission (
    permission_id         INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    kode                  NVARCHAR(50) NOT NULL UNIQUE,
    modul                 NVARCHAR(30) NOT NULL,
    nama                  NVARCHAR(100) NOT NULL,
    dibuat_pada           DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    dibuat_oleh           INT,
    diubah_pada           DATETIME2,
    diubah_oleh           INT,
    dihapus_pada          DATETIME2,
    dihapus_oleh          INT
);

CREATE TABLE dbo.user_role (
    ur_id                 INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    user_id               INT NOT NULL,
    role_id               INT NOT NULL,
    berlaku_mulai         DATE,
    berlaku_sampai        DATE,
    dibuat_pada           DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    dibuat_oleh           INT,
    diubah_pada           DATETIME2,
    diubah_oleh           INT,
    dihapus_pada          DATETIME2,
    dihapus_oleh          INT,
    CONSTRAINT fk_user_role_user_id FOREIGN KEY (user_id) REFERENCES dbo.app_user(user_id),
    CONSTRAINT fk_user_role_role_id FOREIGN KEY (role_id) REFERENCES dbo.role(role_id)
);

CREATE UNIQUE INDEX ux_user_role_user_id_role_id ON dbo.user_role (user_id, role_id) WHERE dihapus_pada IS NULL;

CREATE TABLE dbo.role_permission (
    rp_id                 INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    role_id               INT NOT NULL,
    permission_id         INT NOT NULL,
    dibuat_pada           DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    dibuat_oleh           INT,
    diubah_pada           DATETIME2,
    diubah_oleh           INT,
    dihapus_pada          DATETIME2,
    dihapus_oleh          INT,
    CONSTRAINT fk_role_permission_role_id FOREIGN KEY (role_id) REFERENCES dbo.role(role_id),
    CONSTRAINT fk_role_permission_permission_id FOREIGN KEY (permission_id) REFERENCES dbo.permission(permission_id)
);

CREATE UNIQUE INDEX ux_role_permission_role_id_permission_id ON dbo.role_permission (role_id, permission_id) WHERE dihapus_pada IS NULL;

CREATE TABLE dbo.auth_sesi (
    sesi_id               UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    user_id               INT NOT NULL,
    device_id             UNIQUEIDENTIFIER NOT NULL,
    refresh_token_hash    NVARCHAR(128) NOT NULL,
    kedaluwarsa_pada      DATETIME2 NOT NULL,
    dicabut_pada          DATETIME2,
    ip                    NVARCHAR(45),
    dibuat_pada           DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    dibuat_oleh           INT,
    diubah_pada           DATETIME2,
    diubah_oleh           INT,
    CONSTRAINT fk_auth_sesi_user_id FOREIGN KEY (user_id) REFERENCES dbo.app_user(user_id),
    CONSTRAINT fk_auth_sesi_device_id FOREIGN KEY (device_id) REFERENCES dbo.device(device_id)
);

CREATE INDEX ix_auth_sesi_user_id ON dbo.auth_sesi (user_id);

CREATE TABLE dbo.audit_log (
    audit_id              UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    tabel                 NVARCHAR(50) NOT NULL,
    baris_id              NVARCHAR(50) NOT NULL,
    aksi                  NVARCHAR(20) NOT NULL,
    nilai_lama            NVARCHAR(MAX),
    nilai_baru            NVARCHAR(MAX),
    user_id               INT,
    username              NVARCHAR(50),
    device_id             UNIQUEIDENTIFIER,
    waktu                 DATETIME2 NOT NULL,
    waktu_server          DATETIME2,
    ip                    NVARCHAR(45),
    keterangan            NVARCHAR(300)
);

CREATE INDEX ix_audit_log_tabel_baris_id ON dbo.audit_log (tabel, baris_id);
CREATE INDEX ix_audit_log_user_id_waktu ON dbo.audit_log (user_id, waktu);

CREATE TABLE dbo.fai_panen (
    id                    UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    unit_id               INT NOT NULL,
    device_id             UNIQUEIDENTIFIER NOT NULL,
    tanggal_panen         DATE NOT NULL,
    asm3_oid              BIGINT NOT NULL,
    blok_kode             NVARCHAR(20),
    tph_kode              NVARCHAR(30),
    afdeling_kode         NVARCHAR(20),
    afdeling_erp_oid      INT,
    mandor_hrmb_code      INT,
    pemanen_hrmb_code     INT,
    rotasi                INT,
    kondisi_cuaca         INT,
    brondolan_kg          FLOAT,
    gps_lat               DECIMAL(9,6),
    gps_lon               DECIMAL(9,6),
    gps_akurasi_m         FLOAT,
    gps_waktu             DATETIME2,
    gps_mock              BIT NOT NULL DEFAULT 0,
    jarak_ke_tph_m        FLOAT,
    status_lokasi         NVARCHAR(20) NOT NULL DEFAULT 'TANPA_GPS',
    model_versi           NVARCHAR(50),
    catatan               NVARCHAR(500),
    diterima_server       DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    status_erp            NVARCHAR(12) NOT NULL DEFAULT 'BARU',
    disetujui_oleh        INT,
    disetujui_pada        DATETIME2,
    dibuat_pada           DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    dibuat_oleh           INT,
    diubah_pada           DATETIME2,
    diubah_oleh           INT,
    dihapus_pada          DATETIME2,
    dihapus_oleh          INT,
    CONSTRAINT fk_fai_panen_unit_id FOREIGN KEY (unit_id) REFERENCES dbo.unit(unit_id),
    CONSTRAINT fk_fai_panen_device_id FOREIGN KEY (device_id) REFERENCES dbo.device(device_id),
    CONSTRAINT ck_fai_panen_status_erp CHECK (status_erp IN ('MENUNGGU','BARU','DIBACA','DIIMPOR','DITOLAK'))
);

CREATE INDEX ix_fai_panen_status_erp_tanggal_panen ON dbo.fai_panen (status_erp, tanggal_panen);
CREATE INDEX ix_fai_panen_unit_id_tanggal_panen ON dbo.fai_panen (unit_id, tanggal_panen);

CREATE TABLE dbo.fai_panen_grading (
    id                    BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    panen_id              UNIQUEIDENTIFIER NOT NULL,
    kelas_kode            NVARCHAR(20) NOT NULL,
    erp_kolom             NVARCHAR(30),
    jml_ai                INT NOT NULL DEFAULT 0,
    jml_final             INT NOT NULL DEFAULT 0,
    diubah_manual         BIT NOT NULL DEFAULT 0,
    dibuat_pada           DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    dibuat_oleh           INT,
    diubah_pada           DATETIME2,
    diubah_oleh           INT,
    dihapus_pada          DATETIME2,
    dihapus_oleh          INT,
    CONSTRAINT fk_fai_panen_grading_panen_id FOREIGN KEY (panen_id) REFERENCES dbo.fai_panen(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX ux_fai_panen_grading_panen_id_kelas_kode ON dbo.fai_panen_grading (panen_id, kelas_kode) WHERE dihapus_pada IS NULL;

CREATE TABLE dbo.fai_panen_pemanen (
    id                    BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    panen_id              UNIQUEIDENTIFIER NOT NULL,
    hrmb_code             INT,
    jjg_alokasi           INT,
    dibuat_pada           DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    dibuat_oleh           INT,
    diubah_pada           DATETIME2,
    diubah_oleh           INT,
    dihapus_pada          DATETIME2,
    dihapus_oleh          INT,
    CONSTRAINT fk_fai_panen_pemanen_panen_id FOREIGN KEY (panen_id) REFERENCES dbo.fai_panen(id) ON DELETE CASCADE
);

CREATE TABLE dbo.fai_foto (
    id                    UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    panen_id              UNIQUEIDENTIFIER NOT NULL,
    jenis                 NVARCHAR(10) NOT NULL,
    nama_file             NVARCHAR(200) NOT NULL,
    sha256                NVARCHAR(64),
    dibuat_pada           DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    dibuat_oleh           INT,
    diubah_pada           DATETIME2,
    diubah_oleh           INT,
    dihapus_pada          DATETIME2,
    dihapus_oleh          INT,
    CONSTRAINT fk_fai_foto_panen_id FOREIGN KEY (panen_id) REFERENCES dbo.fai_panen(id) ON DELETE CASCADE,
    CONSTRAINT ck_fai_foto_jenis CHECK (jenis IN ('ASLI','DETEKSI'))
);

CREATE TABLE dbo.fai_impor_log (
    id                    BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    panen_id              UNIQUEIDENTIFIER NOT NULL,
    waktu_impor           DATETIME2 NOT NULL,
    hvd1m_oid             BIGINT,
    hvd1da_oid            BIGINT,
    hasil                 NVARCHAR(10) NOT NULL,
    pesan_error           NVARCHAR(500),
    diimpor_oleh          INT,
    dibuat_pada           DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    dibuat_oleh           INT,
    diubah_pada           DATETIME2,
    diubah_oleh           INT,
    CONSTRAINT fk_fai_impor_log_panen_id FOREIGN KEY (panen_id) REFERENCES dbo.fai_panen(id),
    CONSTRAINT ck_fai_impor_log_hasil CHECK (hasil IN ('OK','GAGAL'))
);

CREATE INDEX ix_fai_impor_log_panen_id ON dbo.fai_impor_log (panen_id);

-- Audit log tidak boleh diubah atau dihapus oleh akun API (ganti nama akun sesuai server)
-- DENY UPDATE, DELETE ON dbo.audit_log TO [api_fai26];

-- Role
INSERT INTO dbo.role (kode, nama, deskripsi, bawaan) VALUES
    ('KRANI', 'Krani', 'Mencatat panen di TPH', 1),
    ('MANDOR', 'Mandor', 'Mengawasi dan mengoreksi catatan krani', 1),
    ('ASISTEN', 'Asisten', 'Melihat rekap dan menjalankan impor ke ERP', 1),
    ('ADMIN', 'Admin', 'Mengelola pengguna, master, perangkat, dan model', 1),
    ('AUDITOR', 'Auditor', 'Membaca data dan audit log, tanpa mengubah', 1);

-- Permission
INSERT INTO dbo.permission (kode, modul, nama) VALUES
    ('panen.lihat', 'Panen', 'Melihat catatan panen'),
    ('panen.buat', 'Panen', 'Membuat catatan panen'),
    ('panen.ubah', 'Panen', 'Mengubah catatan panen milik sendiri'),
    ('panen.koreksi', 'Panen', 'Mengoreksi catatan panen orang lain'),
    ('panen.hapus', 'Panen', 'Menghapus lunak catatan panen'),
    ('panen.kirim', 'Panen', 'Mengirim catatan ke server'),
    ('panen.setujui', 'Panen', 'Menyetujui catatan panen krani'),
    ('grading.ubah', 'Panen', 'Mengubah hasil grading (jml_final)'),
    ('master.lihat', 'Master', 'Melihat data master'),
    ('master.kelola', 'Master', 'Mengelola data master di fAI26'),
    ('laporan.lihat', 'Laporan', 'Melihat rekap panen'),
    ('impor.erp', 'ERP', 'Menjalankan impor ke ERP'),
    ('user.kelola', 'Akses', 'Mengelola pengguna'),
    ('role.kelola', 'Akses', 'Mengelola role dan permission'),
    ('device.kelola', 'Akses', 'Mengelola perangkat HP'),
    ('audit.lihat', 'Audit', 'Melihat audit log'),
    ('model.kelola', 'Model', 'Mengelola versi model YOLO');

-- Role dan permission
INSERT INTO dbo.role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM (VALUES
    ('KRANI', 'panen.lihat'),
    ('KRANI', 'panen.buat'),
    ('KRANI', 'panen.ubah'),
    ('KRANI', 'panen.kirim'),
    ('KRANI', 'grading.ubah'),
    ('KRANI', 'master.lihat'),
    ('MANDOR', 'panen.lihat'),
    ('MANDOR', 'panen.buat'),
    ('MANDOR', 'panen.ubah'),
    ('MANDOR', 'panen.kirim'),
    ('MANDOR', 'panen.setujui'),
    ('MANDOR', 'grading.ubah'),
    ('MANDOR', 'master.lihat'),
    ('MANDOR', 'panen.koreksi'),
    ('MANDOR', 'panen.hapus'),
    ('MANDOR', 'laporan.lihat'),
    ('ASISTEN', 'panen.lihat'),
    ('ASISTEN', 'panen.koreksi'),
    ('ASISTEN', 'panen.hapus'),
    ('ASISTEN', 'laporan.lihat'),
    ('ASISTEN', 'master.lihat'),
    ('ASISTEN', 'impor.erp'),
    ('ADMIN', 'panen.lihat'),
    ('ADMIN', 'laporan.lihat'),
    ('ADMIN', 'master.lihat'),
    ('ADMIN', 'master.kelola'),
    ('ADMIN', 'user.kelola'),
    ('ADMIN', 'role.kelola'),
    ('ADMIN', 'device.kelola'),
    ('ADMIN', 'model.kelola'),
    ('AUDITOR', 'panen.lihat'),
    ('AUDITOR', 'laporan.lihat'),
    ('AUDITOR', 'master.lihat'),
    ('AUDITOR', 'audit.lihat')
) AS x (role_kode, perm_kode)
JOIN dbo.role r ON r.kode = x.role_kode
JOIN dbo.permission p ON p.kode = x.perm_kode;

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
    mandor_user_id        INT,                             -- kemandoran: akun berperan MANDOR
    mandor_hrmb           INT,                             -- salinan kode HRMB saat dipilih
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
    CONSTRAINT fk_fai_ipd_mandor FOREIGN KEY (mandor_user_id) REFERENCES dbo.app_user(user_id),
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
    dicatat_pada          DATETIME2 NULL,              -- waktu pokok dicatat di HP (migrasi 012); dipasangkan dengan titik jalur
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

-- Jalur GPS inspeksi (migrasi 012): titik tiap 3 meter selama inspeksi berjalan. Hanya pemegang ipd.jejak yang melihatnya di web.
IF OBJECT_ID('dbo.fai_ipd_jejak', 'U') IS NULL
CREATE TABLE dbo.fai_ipd_jejak (
    jejak_id              BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    ipd_id                UNIQUEIDENTIFIER NOT NULL,
    urut                  INT NOT NULL,                -- urutan titik dalam satu inspeksi (0, 1, 2, ...)
    waktu                 DATETIME2 NOT NULL,          -- waktu titik diambil (UTC)
    lat                   DECIMAL(9,6) NOT NULL,
    lon                   DECIMAL(9,6) NOT NULL,
    akurasi_m             DECIMAL(6,1) NULL,
    CONSTRAINT fk_fai_ipd_jejak_ipd FOREIGN KEY (ipd_id) REFERENCES dbo.fai_ipd(id),
    CONSTRAINT ux_fai_ipd_jejak_ipd_urut UNIQUE (ipd_id, urut)
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
    ('ipd.kelola',   'IPD',      N'Mengelola kode temuan IPD'),
    ('ipd.jejak',    'IPD',      N'Melihat jalur GPS inspeksi di peta')
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
    ('KTU',    'laporan.ipd'),
    ('ADMIN',  'ipd.jejak'),
    ('ASISTEN','ipd.jejak'),
    ('ASKEP',  'ipd.jejak'),
    ('KTU',    'ipd.jejak')
) AS x (role_kode, perm_kode)
JOIN dbo.role r ON r.kode = x.role_kode
JOIN dbo.permission p ON p.kode = x.perm_kode
WHERE NOT EXISTS (SELECT 1 FROM dbo.role_permission rp WHERE rp.role_id = r.role_id AND rp.permission_id = p.permission_id AND rp.dihapus_pada IS NULL);
GO

GO


-- ===== MIGRASI 002_master =====
-- Migrasi 002 (11 Nov 2026): tabel master untuk GET /api/master. Aman dijalankan berulang.
-- Untuk database baru cukup erd/fAI26_server_sqlserver.sql (sudah memuat tabel ini).
SET QUOTED_IDENTIFIER ON;
GO

-- Master yang diunduh HP (GET /api/master). Isinya salinan dari ERP atau diisi admin; HP menerimanya apa adanya.
IF OBJECT_ID('dbo.divisi', 'U') IS NULL
CREATE TABLE dbo.divisi (
    divisi_id             INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    unit_id               INT NOT NULL,
    erp_oid               INT,
    kode                  NVARCHAR(20) NOT NULL,
    nama                  NVARCHAR(100) NOT NULL,
    aktif                 BIT NOT NULL DEFAULT 1,
    dibuat_pada           DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    dibuat_oleh           INT,
    diubah_pada           DATETIME2,
    diubah_oleh           INT,
    dihapus_pada          DATETIME2,
    dihapus_oleh          INT,
    CONSTRAINT fk_divisi_unit_id FOREIGN KEY (unit_id) REFERENCES dbo.unit(unit_id)
);

IF OBJECT_ID('dbo.blok', 'U') IS NULL
CREATE TABLE dbo.blok (
    blok_id               INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    unit_id               INT NOT NULL,
    divisi_id             INT,
    erp_oid               BIGINT NOT NULL,
    kode                  NVARCHAR(20) NOT NULL,
    nama                  NVARCHAR(100),
    luas_ha               FLOAT,
    tahun_tanam           INT,
    sph                   FLOAT,
    aktif                 BIT NOT NULL DEFAULT 1,
    dibuat_pada           DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    dibuat_oleh           INT,
    diubah_pada           DATETIME2,
    diubah_oleh           INT,
    dihapus_pada          DATETIME2,
    dihapus_oleh          INT,
    CONSTRAINT fk_blok_unit_id FOREIGN KEY (unit_id) REFERENCES dbo.unit(unit_id),
    CONSTRAINT fk_blok_divisi_id FOREIGN KEY (divisi_id) REFERENCES dbo.divisi(divisi_id)
);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ux_blok_unit_id_kode')
CREATE UNIQUE INDEX ux_blok_unit_id_kode ON dbo.blok (unit_id, kode) WHERE dihapus_pada IS NULL;

IF OBJECT_ID('dbo.tph', 'U') IS NULL
CREATE TABLE dbo.tph (
    tph_id                INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    blok_id               INT NOT NULL,
    no_tph                NVARCHAR(10) NOT NULL,
    qr_kode               NVARCHAR(100) NOT NULL UNIQUE,
    lat                   FLOAT,
    lon                   FLOAT,
    aktif                 BIT NOT NULL DEFAULT 1,
    dibuat_pada           DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    dibuat_oleh           INT,
    diubah_pada           DATETIME2,
    diubah_oleh           INT,
    dihapus_pada          DATETIME2,
    dihapus_oleh          INT,
    CONSTRAINT fk_tph_blok_id FOREIGN KEY (blok_id) REFERENCES dbo.blok(blok_id)
);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ux_tph_blok_id_no_tph')
CREATE UNIQUE INDEX ux_tph_blok_id_no_tph ON dbo.tph (blok_id, no_tph) WHERE dihapus_pada IS NULL;

IF OBJECT_ID('dbo.pemanen', 'U') IS NULL
CREATE TABLE dbo.pemanen (
    pemanen_id            INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    unit_id               INT NOT NULL,
    erp_hrmb_code         INT NOT NULL,
    kode                  NVARCHAR(20),
    nama                  NVARCHAR(100) NOT NULL,
    aktif                 BIT NOT NULL DEFAULT 1,
    dibuat_pada           DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    dibuat_oleh           INT,
    diubah_pada           DATETIME2,
    diubah_oleh           INT,
    dihapus_pada          DATETIME2,
    dihapus_oleh          INT,
    CONSTRAINT fk_pemanen_unit_id FOREIGN KEY (unit_id) REFERENCES dbo.unit(unit_id)
);


GO


-- ===== MIGRASI 003_jembatan_erp =====
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

GO


-- ===== MIGRASI 005_hvdm_satu_pemanen =====
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

GO


-- ===== MIGRASI 006_peran_hvdm =====
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

GO


-- ===== MIGRASI 007_mandor_input =====
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

GO


-- ===== MIGRASI 008_menunggu_persetujuan =====
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

GO


-- ===== MIGRASI 009_ipd =====
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

GO


-- ===== MIGRASI 010_satu_kemandoran =====
-- fAI26: IPD memakai SATU kemandoran (bukan mandor di blok dan mandor di ancak).
-- Kolom mandor_blok_* diganti namanya menjadi mandor_*, kolom mandor_ancak_* dibuang. Aman dijalankan berulang.
-- Jalankan setelah 009_ipd.sql.

IF COL_LENGTH('dbo.fai_ipd', 'mandor_ancak_user_id') IS NOT NULL
BEGIN
    IF EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'fk_fai_ipd_mandor_ancak')
        ALTER TABLE dbo.fai_ipd DROP CONSTRAINT fk_fai_ipd_mandor_ancak;
    ALTER TABLE dbo.fai_ipd DROP COLUMN mandor_ancak_user_id;
END
GO

IF COL_LENGTH('dbo.fai_ipd', 'mandor_ancak_hrmb') IS NOT NULL
    ALTER TABLE dbo.fai_ipd DROP COLUMN mandor_ancak_hrmb;
GO

IF COL_LENGTH('dbo.fai_ipd', 'mandor_blok_user_id') IS NOT NULL
    EXEC sp_rename 'dbo.fai_ipd.mandor_blok_user_id', 'mandor_user_id', 'COLUMN';
GO

IF COL_LENGTH('dbo.fai_ipd', 'mandor_blok_hrmb') IS NOT NULL
    EXEC sp_rename 'dbo.fai_ipd.mandor_blok_hrmb', 'mandor_hrmb', 'COLUMN';
GO

IF EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'fk_fai_ipd_mandor_blok')
    EXEC sp_rename 'dbo.fk_fai_ipd_mandor_blok', 'fk_fai_ipd_mandor', 'OBJECT';
GO

GO


-- ===== MIGRASI 011_foto_profil =====
-- fAI26: foto profil akun. Berkasnya disimpan di folder media (profil/<user_id>.jpg, persegi 384 px); kolom ini hanya menyimpan
-- versinya (waktu ubah dalam milidetik) supaya HP dan web tahu kapan harus mengunduh ulang. NULL = belum punya foto.
-- Jalankan setelah 010_satu_kemandoran.sql. Aman dijalankan berulang.

IF COL_LENGTH('dbo.app_user', 'foto_profil_versi') IS NULL
    ALTER TABLE dbo.app_user ADD foto_profil_versi BIGINT NULL;
GO

GO


-- ===== MIGRASI 012_jejak_ipd =====
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

GO
