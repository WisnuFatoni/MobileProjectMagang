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
