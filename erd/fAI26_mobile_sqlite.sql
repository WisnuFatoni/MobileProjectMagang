-- fAI26: database HP (SQLite). Dihasilkan dari skema yang sama dengan ERD.
-- Waktu disimpan UTC format ISO 8601 (YYYY-MM-DDTHH:MM:SSZ).
PRAGMA foreign_keys = ON;

CREATE TABLE unit (
    unit_id               INTEGER PRIMARY KEY,
    kode                  TEXT NOT NULL UNIQUE,
    nama                  TEXT NOT NULL,
    erp_db                TEXT NOT NULL,
    tahun_cluster         INTEGER NOT NULL,
    aktif                 INTEGER NOT NULL DEFAULT 1,
    dibuat_pada           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
    dibuat_oleh           INTEGER,
    diubah_pada           TEXT,
    diubah_oleh           INTEGER,
    dihapus_pada          TEXT,
    dihapus_oleh          INTEGER
);

CREATE TABLE divisi (
    divisi_id             INTEGER PRIMARY KEY,
    unit_id               INTEGER NOT NULL REFERENCES unit(unit_id),
    erp_oid               INTEGER,
    kode                  TEXT NOT NULL,
    nama                  TEXT NOT NULL,
    asisten_user_id       INTEGER,                       -- asisten divisi (petugas.user_id), diatur admin; dipakai IPD
    aktif                 INTEGER NOT NULL DEFAULT 1,
    dibuat_pada           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
    dibuat_oleh           INTEGER,
    diubah_pada           TEXT,
    diubah_oleh           INTEGER,
    dihapus_pada          TEXT,
    dihapus_oleh          INTEGER
);

CREATE TABLE blok (
    blok_id               INTEGER PRIMARY KEY,
    unit_id               INTEGER NOT NULL REFERENCES unit(unit_id),
    divisi_id             INTEGER REFERENCES divisi(divisi_id),
    erp_oid               INTEGER NOT NULL,
    kode                  TEXT NOT NULL,
    nama                  TEXT,
    luas_ha               REAL,
    tahun_tanam           INTEGER,
    sph                   REAL,
    aktif                 INTEGER NOT NULL DEFAULT 1,
    dibuat_pada           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
    dibuat_oleh           INTEGER,
    diubah_pada           TEXT,
    diubah_oleh           INTEGER,
    dihapus_pada          TEXT,
    dihapus_oleh          INTEGER
);

CREATE UNIQUE INDEX ux_blok_unit_id_kode ON blok (unit_id, kode) WHERE dihapus_pada IS NULL;

CREATE TABLE tph (
    tph_id                INTEGER PRIMARY KEY,
    blok_id               INTEGER NOT NULL REFERENCES blok(blok_id),
    no_tph                TEXT NOT NULL,
    qr_kode               TEXT NOT NULL UNIQUE,
    lat                   REAL,
    lon                   REAL,
    aktif                 INTEGER NOT NULL DEFAULT 1,
    dibuat_pada           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
    dibuat_oleh           INTEGER,
    diubah_pada           TEXT,
    diubah_oleh           INTEGER,
    dihapus_pada          TEXT,
    dihapus_oleh          INTEGER
);

CREATE UNIQUE INDEX ux_tph_blok_id_no_tph ON tph (blok_id, no_tph) WHERE dihapus_pada IS NULL;

CREATE TABLE pemanen (
    pemanen_id            INTEGER PRIMARY KEY,
    unit_id               INTEGER NOT NULL REFERENCES unit(unit_id),
    erp_hrmb_code         INTEGER NOT NULL,
    kode                  TEXT,
    nama                  TEXT NOT NULL,
    aktif                 INTEGER NOT NULL DEFAULT 1,
    dibuat_pada           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
    dibuat_oleh           INTEGER,
    diubah_pada           TEXT,
    diubah_oleh           INTEGER,
    dihapus_pada          TEXT,
    dihapus_oleh          INTEGER
);

CREATE TABLE periode_rotasi (
    periode_id            INTEGER PRIMARY KEY,
    unit_id               INTEGER NOT NULL REFERENCES unit(unit_id),
    rotasi_no             INTEGER NOT NULL,
    tgl_mulai             TEXT NOT NULL,
    tgl_selesai           TEXT NOT NULL,
    dibuat_pada           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
    dibuat_oleh           INTEGER,
    diubah_pada           TEXT,
    diubah_oleh           INTEGER,
    dihapus_pada          TEXT,
    dihapus_oleh          INTEGER
);

CREATE TABLE kelas_grading (
    kelas_id              INTEGER PRIMARY KEY,
    kode                  TEXT NOT NULL UNIQUE,
    nama                  TEXT NOT NULL,
    label_model           INTEGER NOT NULL,
    erp_kolom             TEXT,
    boleh_dipanen         INTEGER NOT NULL DEFAULT 0,
    urutan                INTEGER NOT NULL DEFAULT 0,
    dibuat_pada           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
    dibuat_oleh           INTEGER,
    diubah_pada           TEXT,
    diubah_oleh           INTEGER,
    dihapus_pada          TEXT,
    dihapus_oleh          INTEGER
);

CREATE TABLE model_versi (
    model_id              INTEGER PRIMARY KEY,
    nama                  TEXT NOT NULL,
    versi                 TEXT NOT NULL,
    file_hash             TEXT,
    tgl_rilis             TEXT,
    aktif                 INTEGER NOT NULL DEFAULT 0,
    dibuat_pada           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
    dibuat_oleh           INTEGER,
    diubah_pada           TEXT,
    diubah_oleh           INTEGER,
    dihapus_pada          TEXT,
    dihapus_oleh          INTEGER
);

CREATE TABLE device (
    device_id             TEXT PRIMARY KEY,
    nama                  TEXT,
    os_versi              TEXT,
    app_versi             TEXT,
    aktif                 INTEGER NOT NULL DEFAULT 1,
    terakhir_sinkron      TEXT,
    dibuat_pada           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
    dibuat_oleh           INTEGER,
    diubah_pada           TEXT,
    diubah_oleh           INTEGER,
    dihapus_pada          TEXT,
    dihapus_oleh          INTEGER
);

CREATE TABLE app_user (
    user_id               INTEGER PRIMARY KEY,
    unit_id               INTEGER NOT NULL REFERENCES unit(unit_id),
    erp_user_oid          INTEGER,
    mandor_hrmb_code      INTEGER,            -- profil mandor (versi DB 4): kode HRMB di ERP
    afdeling_kode         TEXT,               -- afdeling mandor (= divisi.kode)
    username              TEXT NOT NULL UNIQUE,
    nama                  TEXT NOT NULL,
    aktif                 INTEGER NOT NULL DEFAULT 1,
    gagal_login           INTEGER NOT NULL DEFAULT 0,
    terkunci_sampai       TEXT,
    login_terakhir        TEXT,
    offline_hash          TEXT,
    offline_salt          TEXT,
    offline_berlaku_sampai TEXT,
    dibuat_pada           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
    dibuat_oleh           INTEGER,
    diubah_pada           TEXT,
    diubah_oleh           INTEGER,
    dihapus_pada          TEXT,
    dihapus_oleh          INTEGER
);

CREATE TABLE role (
    role_id               INTEGER PRIMARY KEY,
    kode                  TEXT NOT NULL UNIQUE,
    nama                  TEXT NOT NULL,
    deskripsi             TEXT,
    bawaan                INTEGER NOT NULL DEFAULT 0,
    dibuat_pada           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
    dibuat_oleh           INTEGER,
    diubah_pada           TEXT,
    diubah_oleh           INTEGER,
    dihapus_pada          TEXT,
    dihapus_oleh          INTEGER
);

CREATE TABLE permission (
    permission_id         INTEGER PRIMARY KEY,
    kode                  TEXT NOT NULL UNIQUE,
    modul                 TEXT NOT NULL,
    nama                  TEXT NOT NULL,
    dibuat_pada           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
    dibuat_oleh           INTEGER,
    diubah_pada           TEXT,
    diubah_oleh           INTEGER,
    dihapus_pada          TEXT,
    dihapus_oleh          INTEGER
);

CREATE TABLE user_role (
    ur_id                 INTEGER PRIMARY KEY,
    user_id               INTEGER NOT NULL REFERENCES app_user(user_id),
    role_id               INTEGER NOT NULL REFERENCES role(role_id),
    berlaku_mulai         TEXT,
    berlaku_sampai        TEXT,
    dibuat_pada           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
    dibuat_oleh           INTEGER,
    diubah_pada           TEXT,
    diubah_oleh           INTEGER,
    dihapus_pada          TEXT,
    dihapus_oleh          INTEGER
);

CREATE UNIQUE INDEX ux_user_role_user_id_role_id ON user_role (user_id, role_id) WHERE dihapus_pada IS NULL;

CREATE TABLE role_permission (
    rp_id                 INTEGER PRIMARY KEY,
    role_id               INTEGER NOT NULL REFERENCES role(role_id),
    permission_id         INTEGER NOT NULL REFERENCES permission(permission_id),
    dibuat_pada           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
    dibuat_oleh           INTEGER,
    diubah_pada           TEXT,
    diubah_oleh           INTEGER,
    dihapus_pada          TEXT,
    dihapus_oleh          INTEGER
);

CREATE UNIQUE INDEX ux_role_permission_role_id_permission_id ON role_permission (role_id, permission_id) WHERE dihapus_pada IS NULL;

CREATE TABLE panen_header (
    panen_id              TEXT PRIMARY KEY,
    unit_id               INTEGER NOT NULL REFERENCES unit(unit_id),
    blok_id               INTEGER NOT NULL REFERENCES blok(blok_id),
    tph_id                INTEGER NOT NULL REFERENCES tph(tph_id),
    user_id               INTEGER NOT NULL REFERENCES app_user(user_id),
    device_id             TEXT NOT NULL REFERENCES device(device_id),
    model_id              INTEGER REFERENCES model_versi(model_id),
    periode_id            INTEGER REFERENCES periode_rotasi(periode_id),
    tanggal_panen         TEXT NOT NULL,
    rotasi_no             INTEGER,
    kondisi_cuaca         INTEGER,
    brondolan_kg          REAL NOT NULL DEFAULT 0,
    gps_lat               REAL,
    gps_lon               REAL,
    gps_akurasi_m         REAL,
    gps_waktu             TEXT,
    gps_mock              INTEGER NOT NULL DEFAULT 0,
    jarak_ke_tph_m        REAL,
    status_lokasi         TEXT NOT NULL DEFAULT 'TANPA_GPS' CHECK (status_lokasi IN ('DI_TPH','DI_LUAR','AKURASI_RENDAH','TANPA_GPS','TPH_TANPA_KOORDINAT')),
    catatan               TEXT,
    status                TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','DISETUJUI','SIAP_KIRIM','TERKIRIM','DITERIMA','DITOLAK')),
    dikirim_pada          TEXT,
    pesan_server          TEXT,
    disetujui_oleh        INTEGER,
    disetujui_pada        TEXT,
    kirim_ulang           INTEGER NOT NULL DEFAULT 0,   -- 1 = catatan DITOLAK yang diperbaiki dan dikirim ulang (versi DB 4)
    dibuat_pada           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
    dibuat_oleh           INTEGER,
    diubah_pada           TEXT,
    diubah_oleh           INTEGER,
    dihapus_pada          TEXT,
    dihapus_oleh          INTEGER
);

CREATE INDEX ix_panen_header_tanggal_panen_unit_id ON panen_header (tanggal_panen, unit_id);
CREATE INDEX ix_panen_header_status ON panen_header (status);
CREATE INDEX ix_panen_header_tph_id_tanggal_panen ON panen_header (tph_id, tanggal_panen);

CREATE TABLE panen_pemanen (
    pp_id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    panen_id              TEXT NOT NULL REFERENCES panen_header(panen_id) ON DELETE CASCADE,
    pemanen_id            INTEGER REFERENCES pemanen(pemanen_id),
    jjg_alokasi           INTEGER,
    catatan               TEXT,
    dibuat_pada           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
    dibuat_oleh           INTEGER,
    diubah_pada           TEXT,
    diubah_oleh           INTEGER,
    dihapus_pada          TEXT,
    dihapus_oleh          INTEGER
);

CREATE TABLE panen_grading (
    grading_id            INTEGER PRIMARY KEY AUTOINCREMENT,
    panen_id              TEXT NOT NULL REFERENCES panen_header(panen_id) ON DELETE CASCADE,
    kelas_id              INTEGER NOT NULL REFERENCES kelas_grading(kelas_id),
    jml_ai                INTEGER NOT NULL DEFAULT 0,
    jml_final             INTEGER NOT NULL DEFAULT 0,
    diubah_manual         INTEGER NOT NULL DEFAULT 0,
    dibuat_pada           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
    dibuat_oleh           INTEGER,
    diubah_pada           TEXT,
    diubah_oleh           INTEGER,
    dihapus_pada          TEXT,
    dihapus_oleh          INTEGER
);

CREATE UNIQUE INDEX ux_panen_grading_panen_id_kelas_id ON panen_grading (panen_id, kelas_id) WHERE dihapus_pada IS NULL;

CREATE TABLE foto (
    foto_id               TEXT PRIMARY KEY,
    panen_id              TEXT NOT NULL REFERENCES panen_header(panen_id) ON DELETE CASCADE,
    jenis                 TEXT NOT NULL CHECK (jenis IN ('ASLI','DETEKSI')),
    path_lokal            TEXT NOT NULL,
    sha256                TEXT,
    lebar                 INTEGER,
    tinggi                INTEGER,
    status_unggah         TEXT NOT NULL DEFAULT 'BELUM',
    dibuat_pada           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
    dibuat_oleh           INTEGER,
    diubah_pada           TEXT,
    diubah_oleh           INTEGER,
    dihapus_pada          TEXT,
    dihapus_oleh          INTEGER
);

CREATE INDEX ix_foto_panen_id ON foto (panen_id);

CREATE TABLE deteksi (
    deteksi_id            INTEGER PRIMARY KEY AUTOINCREMENT,
    foto_id               TEXT NOT NULL REFERENCES foto(foto_id) ON DELETE CASCADE,
    kelas_id              INTEGER NOT NULL REFERENCES kelas_grading(kelas_id),
    confidence            REAL NOT NULL,
    x                     REAL,
    y                     REAL,
    w                     REAL,
    h                     REAL,
    dibuat_pada           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
    dibuat_oleh           INTEGER,
    diubah_pada           TEXT,
    diubah_oleh           INTEGER
);

CREATE TABLE audit_log (
    audit_id              TEXT PRIMARY KEY,
    tabel                 TEXT NOT NULL,
    baris_id              TEXT NOT NULL,
    aksi                  TEXT NOT NULL,
    nilai_lama            TEXT,
    nilai_baru            TEXT,
    user_id               INTEGER,
    username              TEXT,
    device_id             TEXT,
    waktu                 TEXT NOT NULL,
    disinkron             INTEGER NOT NULL DEFAULT 0,
    keterangan            TEXT
);

CREATE INDEX ix_audit_log_tabel_baris_id ON audit_log (tabel, baris_id);
CREATE INDEX ix_audit_log_user_id_waktu ON audit_log (user_id, waktu);

CREATE TABLE sync_queue (
    queue_id              INTEGER PRIMARY KEY AUTOINCREMENT,
    panen_id              TEXT REFERENCES panen_header(panen_id) ON DELETE CASCADE,
    entitas               TEXT NOT NULL CHECK (entitas IN ('PANEN','FOTO','AUDIT')),
    aksi                  TEXT NOT NULL DEFAULT 'KIRIM',
    payload_json          TEXT,
    percobaan             INTEGER NOT NULL DEFAULT 0,
    status                TEXT NOT NULL DEFAULT 'MENUNGGU' CHECK (status IN ('MENUNGGU','JALAN','GAGAL','SELESAI')),
    error_terakhir        TEXT,
    coba_terakhir         TEXT,
    dibuat_pada           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
    dibuat_oleh           INTEGER,
    diubah_pada           TEXT,
    diubah_oleh           INTEGER
);

CREATE INDEX ix_sync_queue_status_dibuat_pada ON sync_queue (status, dibuat_pada);

CREATE TABLE master_sync (
    nama_master           TEXT NOT NULL,
    unit_id               INTEGER NOT NULL REFERENCES unit(unit_id),
    versi_server          TEXT,
    sinkron_terakhir      TEXT,
    dibuat_pada           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
    dibuat_oleh           INTEGER,
    diubah_pada           TEXT,
    diubah_oleh           INTEGER,
    PRIMARY KEY (nama_master, unit_id)
);

-- Data awal kelas grading.
-- label_model = nomor kelas pada keluaran model best.tflite (urutan: 0 Abnormal, 1 Empty Bunch,
-- 2 Overripe, 3 Ripe, 4 Underripe, 5 Unripe). kelas_id tetap berurutan dari paling muda ke paling tua.
-- erp_kolom abnormal (JJG_UNKNOWN) masih sementara, menunggu keputusan pembimbing.
INSERT INTO kelas_grading (kelas_id, kode, nama, label_model, erp_kolom, boleh_dipanen, urutan) VALUES
    (1, 'unripe', 'Unripe', 5, NULL, 0, 1),
    (2, 'underripe', 'Under-ripe', 4, NULL, 0, 2),
    (3, 'ripe', 'Ripe', 3, 'JJG_RIPE', 1, 3),
    (4, 'overripe', 'Overripe', 2, 'JJG_OVERRIPE', 1, 4),
    (5, 'empty', 'Empty bunch', 1, 'JJG_EMPTY', 0, 5),
    (6, 'abnormal', 'Abnormal', 0, 'JJG_UNKNOWN', 0, 6);

-- =====================================================================================================
-- Modul IPD (Inspeksi Panen Detail), versi DB HP 5 (21 Nov 2026). Satu catatan IPD = satu pemanen (satu ancak).
-- Pokok yang diperiksa dicatat semuanya (termasuk yang bersih) supaya persen loses bisa dihitung.
-- =====================================================================================================

CREATE TABLE kode_temuan (
    kode_id               INTEGER PRIMARY KEY AUTOINCREMENT,
    kode                  TEXT NOT NULL UNIQUE,          -- A, G, S, M1, M2, M3, M4, B1, B2, C, R
    nama                  TEXT NOT NULL,
    kelompok              TEXT NOT NULL CHECK (kelompok IN ('BUAH','BRONDOLAN','PELEPAH')),
    urut                  INTEGER NOT NULL DEFAULT 0,
    aktif                 INTEGER NOT NULL DEFAULT 1,
    dibuat_pada           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
    dibuat_oleh           INTEGER,
    diubah_pada           TEXT,
    diubah_oleh           INTEGER,
    dihapus_pada          TEXT,
    dihapus_oleh          INTEGER
);

-- Daftar akun berperan MANDOR dan ASISTEN di unit (master, diunduh dari server). Dipakai memilih mandor dan menampilkan asisten.
CREATE TABLE petugas (
    user_id               INTEGER PRIMARY KEY,           -- sama dengan app_user.user_id di server
    unit_id               INTEGER NOT NULL REFERENCES unit(unit_id),
    nama                  TEXT NOT NULL,
    peran                 TEXT NOT NULL CHECK (peran IN ('MANDOR','ASISTEN')),
    hrmb_code             INTEGER,                       -- kode HRMB mandor (app_user.mandor_hrmb_code), kosong untuk asisten
    afdeling_kode         TEXT,
    aktif                 INTEGER NOT NULL DEFAULT 1,
    dibuat_pada           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
    dibuat_oleh           INTEGER,
    diubah_pada           TEXT,
    diubah_oleh           INTEGER,
    dihapus_pada          TEXT,
    dihapus_oleh          INTEGER
);

CREATE TABLE ipd_header (
    ipd_id                TEXT PRIMARY KEY,              -- UUID dibuat di HP, sama dengan fai_ipd.id di server
    unit_id               INTEGER NOT NULL REFERENCES unit(unit_id),
    blok_id               INTEGER NOT NULL REFERENCES blok(blok_id),
    pemanen_id            INTEGER NOT NULL REFERENCES pemanen(pemanen_id),
    user_id               INTEGER NOT NULL REFERENCES app_user(user_id),   -- yang menginspeksi (biasanya mandor)
    device_id             TEXT NOT NULL REFERENCES device(device_id),
    tanggal_inspeksi      TEXT NOT NULL,                 -- YYYY-MM-DD
    ancak_no              TEXT,                          -- nomor/nama ancak, boleh kosong
    divisi_id             INTEGER NOT NULL REFERENCES divisi(divisi_id),   -- dipilih di awal inspeksi (Divisi 1 atau 2)
    asisten_user_id       INTEGER REFERENCES petugas(user_id),             -- terisi otomatis dari divisi, penyetuju catatan ini
    mandor_blok_user_id   INTEGER REFERENCES petugas(user_id),             -- dipilih dari daftar akun berperan MANDOR
    mandor_blok_hrmb      INTEGER,                       -- salinan kode HRMB mandor saat dipilih (untuk ERP nanti)
    mandor_ancak_user_id  INTEGER REFERENCES petugas(user_id),
    mandor_ancak_hrmb     INTEGER,
    catatan               TEXT,
    status                TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','MENUNGGU_SETUJU','DISETUJUI','DITOLAK')),
    dikirim_pada          TEXT,
    pesan_server          TEXT,                          -- alasan bila ditolak asisten, atau pesan 423
    divalidasi_oleh       INTEGER,                       -- inspektur menyatakan valid saat menekan kirim
    divalidasi_pada       TEXT,
    disetujui_oleh        INTEGER,                       -- asisten (pemegang ipd.setujui)
    disetujui_pada        TEXT,
    kirim_ulang           INTEGER NOT NULL DEFAULT 0,    -- 1 = diubah sebelum disetujui / diperbaiki setelah ditolak
    dibuat_pada           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
    dibuat_oleh           INTEGER,
    diubah_pada           TEXT,
    diubah_oleh           INTEGER,
    dihapus_pada          TEXT,
    dihapus_oleh          INTEGER
);

CREATE INDEX ix_ipd_header_tanggal_inspeksi_unit_id ON ipd_header (tanggal_inspeksi, unit_id);
CREATE INDEX ix_ipd_header_status ON ipd_header (status);
CREATE INDEX ix_ipd_header_pemanen_id_tanggal_inspeksi ON ipd_header (pemanen_id, tanggal_inspeksi);
-- Peringatan "ancak ini sudah pernah diinspeksi": mencari inspeksi terakhir pemanen + blok yang sama.
CREATE INDEX ix_ipd_header_pemanen_id_blok_id_tanggal_inspeksi ON ipd_header (pemanen_id, blok_id, tanggal_inspeksi);

CREATE TABLE ipd_pokok (
    pokok_id              INTEGER PRIMARY KEY AUTOINCREMENT,
    ipd_id                TEXT NOT NULL REFERENCES ipd_header(ipd_id) ON DELETE CASCADE,
    no_baris              INTEGER NOT NULL CHECK (no_baris > 0),
    no_pokok              INTEGER NOT NULL CHECK (no_pokok > 0),
    dibuat_pada           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
    dibuat_oleh           INTEGER,
    diubah_pada           TEXT,
    diubah_oleh           INTEGER,
    dihapus_pada          TEXT,
    dihapus_oleh          INTEGER
);

CREATE UNIQUE INDEX ux_ipd_pokok_ipd_id_baris_pokok ON ipd_pokok (ipd_id, no_baris, no_pokok) WHERE dihapus_pada IS NULL;

CREATE TABLE ipd_temuan (
    temuan_id             INTEGER PRIMARY KEY AUTOINCREMENT,
    pokok_id              INTEGER NOT NULL REFERENCES ipd_pokok(pokok_id) ON DELETE CASCADE,
    kode_id               INTEGER NOT NULL REFERENCES kode_temuan(kode_id),
    jumlah                INTEGER NOT NULL DEFAULT 1 CHECK (jumlah > 0),   -- butir/buah/pelepah, bawaan 1
    catatan               TEXT,
    dibuat_pada           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
    dibuat_oleh           INTEGER,
    diubah_pada           TEXT,
    diubah_oleh           INTEGER,
    dihapus_pada          TEXT,
    dihapus_oleh          INTEGER
);

CREATE UNIQUE INDEX ux_ipd_temuan_pokok_id_kode_id ON ipd_temuan (pokok_id, kode_id) WHERE dihapus_pada IS NULL;

CREATE TABLE ipd_foto (
    foto_id               TEXT PRIMARY KEY,
    ipd_id                TEXT NOT NULL REFERENCES ipd_header(ipd_id) ON DELETE CASCADE,
    keterangan            TEXT,                          -- keterangan foto, diisi inspektur
    urut                  INTEGER NOT NULL DEFAULT 0,
    path_lokal            TEXT,                          -- NULL setelah terkirim ke server: berkas di HP dihapus agar tidak menumpuk
    sha256                TEXT,
    lebar                 INTEGER,
    tinggi                INTEGER,
    ukuran_byte           INTEGER,                       -- ukuran setelah dikompres saat disimpan
    status_unggah         TEXT NOT NULL DEFAULT 'BELUM',
    dibuat_pada           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
    dibuat_oleh           INTEGER,
    diubah_pada           TEXT,
    diubah_oleh           INTEGER,
    dihapus_pada          TEXT,
    dihapus_oleh          INTEGER
);

CREATE INDEX ix_ipd_foto_ipd_id ON ipd_foto (ipd_id);

-- Data awal kode temuan IPD (urutan sesuai form lapangan).
INSERT INTO kode_temuan (kode_id, kode, nama, kelompok, urut) VALUES
    (1, 'A', 'Potong buah mentah', 'BUAH', 1),
    (2, 'G', 'Tangkai panjang', 'BUAH', 2),
    (3, 'S', 'Buah masak tidak dipanen', 'BUAH', 3),
    (4, 'M1', 'Buah diperam', 'BUAH', 4),
    (5, 'M2', 'Buah dipanen tidak dibawa ke TPH', 'BUAH', 5),
    (6, 'M3', 'Buah matahari', 'BUAH', 6),
    (7, 'M4', 'Buah tidak disusun di TPH', 'BUAH', 7),
    (8, 'B1', 'Brondolan tak dikutip > 3 butir', 'BRONDOLAN', 8),
    (9, 'B2', 'Brondolan dibuang ke gawangan', 'BRONDOLAN', 9),
    (10, 'C', 'Pelepah sengkleh tidak dipotong', 'PELEPAH', 10),
    (11, 'R', 'Pelepah tidak disusun di gawangan', 'PELEPAH', 11);
