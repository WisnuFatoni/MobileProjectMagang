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
