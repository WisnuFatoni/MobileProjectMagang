# 03. Database HP (SQLite lewat Room), Lengkap

Dihasilkan dari **skema Room yang diekspor** (`fAI26-Mobile/app/schemas/com.example.fai26.db.AppDatabase/7.json`), versi database **7**, **28 tabel**. Waktu disimpan sebagai teks UTC ISO 8601 (`YYYY-MM-DDTHH:MM:SSZ`); boolean sebagai 0/1. `CHECK` pada skrip SQL `erd/fAI26_mobile_sqlite.sql` tidak ditegakkan Room (pembatasan nilai ada di kode). ID master (`unit_id`, `blok_id`, ...) sama persis dengan server karena master diunduh apa adanya. Migrasi Room tidak pernah menghapus data (tanpa destructive migration).

## 1. Riwayat versi

Perubahan antarversi dihitung otomatis dengan membandingkan berkas skema tiap versi.

| Versi | Perubahan |
|---|---|
| 1 | Awal: 22 tabel |
| 2 | `panen_header` +`gps_akurasi_m`, +`gps_waktu`, +`gps_mock`, +`jarak_ke_tph_m`, +`status_lokasi` |
| 3 | (tanpa perubahan kolom) |
| 4 | `app_user` +`mandor_hrmb_code`, +`afdeling_kode`; `panen_header` +`kirim_ulang` |
| 5 | `divisi` +`asisten_user_id`; tabel baru `kode_temuan`; tabel baru `ipd_header`; tabel baru `ipd_pokok`; tabel baru `ipd_temuan`; tabel baru `ipd_foto` |
| 6 | `ipd_header` +`mandor_user_id`, +`mandor_hrmb`; `ipd_header` -`mandor_blok_user_id`, -`mandor_blok_hrmb`, -`mandor_ancak_user_id`, -`mandor_ancak_hrmb` |
| 7 | tabel baru `ipd_jejak` |

## 2. Tabel satu per satu

### 2.1 `unit`

Salinan master unit.

| Kolom | Tipe | Wajib | Bawaan |
|---|---|---|---|
| `unit_id` | INTEGER | ya |  |
| `kode` | TEXT | ya |  |
| `nama` | TEXT | ya |  |
| `erp_db` | TEXT | ya |  |
| `tahun_cluster` | INTEGER | ya |  |
| `aktif` | INTEGER | ya |  |
| `dibuat_pada` | TEXT | ya |  |
| `dibuat_oleh` | INTEGER | tidak |  |
| `diubah_pada` | TEXT | tidak |  |
| `diubah_oleh` | INTEGER | tidak |  |
| `dihapus_pada` | TEXT | tidak |  |
| `dihapus_oleh` | INTEGER | tidak |  |

- **Kunci utama:** `unit_id`
- **Indeks unik:** `ux_unit_kode` (kode)

### 2.2 `divisi`

Salinan master divisi (dengan `asisten_user_id` untuk IPD).

| Kolom | Tipe | Wajib | Bawaan |
|---|---|---|---|
| `divisi_id` | INTEGER | ya |  |
| `unit_id` | INTEGER | ya |  |
| `erp_oid` | INTEGER | tidak |  |
| `kode` | TEXT | ya |  |
| `nama` | TEXT | ya |  |
| `asisten_user_id` | INTEGER | tidak |  |
| `aktif` | INTEGER | ya |  |
| `dibuat_pada` | TEXT | ya |  |
| `dibuat_oleh` | INTEGER | tidak |  |
| `diubah_pada` | TEXT | tidak |  |
| `diubah_oleh` | INTEGER | tidak |  |
| `dihapus_pada` | TEXT | tidak |  |
| `dihapus_oleh` | INTEGER | tidak |  |

- **Kunci utama:** `divisi_id`
- **FK:** (unit_id) ke `unit` (unit_id) ON DELETE NO ACTION

### 2.3 `blok`

Salinan master blok (hanya blok afdeling mandor bila akun mandor).

| Kolom | Tipe | Wajib | Bawaan |
|---|---|---|---|
| `blok_id` | INTEGER | ya |  |
| `unit_id` | INTEGER | ya |  |
| `divisi_id` | INTEGER | tidak |  |
| `erp_oid` | INTEGER | ya |  |
| `kode` | TEXT | ya |  |
| `nama` | TEXT | tidak |  |
| `luas_ha` | REAL | tidak |  |
| `tahun_tanam` | INTEGER | tidak |  |
| `sph` | REAL | tidak |  |
| `aktif` | INTEGER | ya |  |
| `dibuat_pada` | TEXT | ya |  |
| `dibuat_oleh` | INTEGER | tidak |  |
| `diubah_pada` | TEXT | tidak |  |
| `diubah_oleh` | INTEGER | tidak |  |
| `dihapus_pada` | TEXT | tidak |  |
| `dihapus_oleh` | INTEGER | tidak |  |

- **Kunci utama:** `blok_id`
- **Indeks unik:** `ux_blok_unit_id_kode` (unit_id, kode)
- **FK:** (unit_id) ke `unit` (unit_id) ON DELETE NO ACTION
- **FK:** (divisi_id) ke `divisi` (divisi_id) ON DELETE NO ACTION

### 2.4 `tph`

Salinan master TPH; `lat/lon` boleh diisi HP (rekam koordinat).

| Kolom | Tipe | Wajib | Bawaan |
|---|---|---|---|
| `tph_id` | INTEGER | ya |  |
| `blok_id` | INTEGER | ya |  |
| `no_tph` | TEXT | ya |  |
| `qr_kode` | TEXT | ya |  |
| `lat` | REAL | tidak |  |
| `lon` | REAL | tidak |  |
| `aktif` | INTEGER | ya |  |
| `dibuat_pada` | TEXT | ya |  |
| `dibuat_oleh` | INTEGER | tidak |  |
| `diubah_pada` | TEXT | tidak |  |
| `diubah_oleh` | INTEGER | tidak |  |
| `dihapus_pada` | TEXT | tidak |  |
| `dihapus_oleh` | INTEGER | tidak |  |

- **Kunci utama:** `tph_id`
- **Indeks unik:** `ux_tph_qr_kode` (qr_kode)
- **Indeks unik:** `ux_tph_blok_id_no_tph` (blok_id, no_tph)
- **FK:** (blok_id) ke `blok` (blok_id) ON DELETE NO ACTION

### 2.5 `pemanen`

Salinan master pemanen (hanya pemanen milik mandor bila akun mandor).

| Kolom | Tipe | Wajib | Bawaan |
|---|---|---|---|
| `pemanen_id` | INTEGER | ya |  |
| `unit_id` | INTEGER | ya |  |
| `erp_hrmb_code` | INTEGER | ya |  |
| `kode` | TEXT | tidak |  |
| `nama` | TEXT | ya |  |
| `aktif` | INTEGER | ya |  |
| `dibuat_pada` | TEXT | ya |  |
| `dibuat_oleh` | INTEGER | tidak |  |
| `diubah_pada` | TEXT | tidak |  |
| `diubah_oleh` | INTEGER | tidak |  |
| `dihapus_pada` | TEXT | tidak |  |
| `dihapus_oleh` | INTEGER | tidak |  |

- **Kunci utama:** `pemanen_id`
- **FK:** (unit_id) ke `unit` (unit_id) ON DELETE NO ACTION

### 2.6 `periode_rotasi`

Periode rotasi; belum dipakai alur sekarang.

| Kolom | Tipe | Wajib | Bawaan |
|---|---|---|---|
| `periode_id` | INTEGER | ya |  |
| `unit_id` | INTEGER | ya |  |
| `rotasi_no` | INTEGER | ya |  |
| `tgl_mulai` | TEXT | ya |  |
| `tgl_selesai` | TEXT | ya |  |
| `dibuat_pada` | TEXT | ya |  |
| `dibuat_oleh` | INTEGER | tidak |  |
| `diubah_pada` | TEXT | tidak |  |
| `diubah_oleh` | INTEGER | tidak |  |
| `dihapus_pada` | TEXT | tidak |  |
| `dihapus_oleh` | INTEGER | tidak |  |

- **Kunci utama:** `periode_id`
- **FK:** (unit_id) ke `unit` (unit_id) ON DELETE NO ACTION

### 2.7 `kelas_grading`

Enam kelas model, `label_model` = nomor keluaran model, `erp_kolom` = kolom tujuan ERP.

| Kolom | Tipe | Wajib | Bawaan |
|---|---|---|---|
| `kelas_id` | INTEGER | ya |  |
| `kode` | TEXT | ya |  |
| `nama` | TEXT | ya |  |
| `label_model` | INTEGER | ya |  |
| `erp_kolom` | TEXT | tidak |  |
| `boleh_dipanen` | INTEGER | ya |  |
| `urutan` | INTEGER | ya |  |
| `dibuat_pada` | TEXT | ya |  |
| `dibuat_oleh` | INTEGER | tidak |  |
| `diubah_pada` | TEXT | tidak |  |
| `diubah_oleh` | INTEGER | tidak |  |
| `dihapus_pada` | TEXT | tidak |  |
| `dihapus_oleh` | INTEGER | tidak |  |

- **Kunci utama:** `kelas_id`
- **Indeks unik:** `ux_kelas_grading_kode` (kode)

### 2.8 `model_versi`

Versi model AI yang dipakai dan hash berkasnya.

| Kolom | Tipe | Wajib | Bawaan |
|---|---|---|---|
| `model_id` | INTEGER | ya |  |
| `nama` | TEXT | ya |  |
| `versi` | TEXT | ya |  |
| `file_hash` | TEXT | tidak |  |
| `tgl_rilis` | TEXT | tidak |  |
| `aktif` | INTEGER | ya |  |
| `dibuat_pada` | TEXT | ya |  |
| `dibuat_oleh` | INTEGER | tidak |  |
| `diubah_pada` | TEXT | tidak |  |
| `diubah_oleh` | INTEGER | tidak |  |
| `dihapus_pada` | TEXT | tidak |  |
| `dihapus_oleh` | INTEGER | tidak |  |

- **Kunci utama:** `model_id`

### 2.9 `device`

Identitas HP ini (UUID).

| Kolom | Tipe | Wajib | Bawaan |
|---|---|---|---|
| `device_id` | TEXT | ya |  |
| `nama` | TEXT | tidak |  |
| `os_versi` | TEXT | tidak |  |
| `app_versi` | TEXT | tidak |  |
| `aktif` | INTEGER | ya |  |
| `terakhir_sinkron` | TEXT | tidak |  |
| `dibuat_pada` | TEXT | ya |  |
| `dibuat_oleh` | INTEGER | tidak |  |
| `diubah_pada` | TEXT | tidak |  |
| `diubah_oleh` | INTEGER | tidak |  |
| `dihapus_pada` | TEXT | tidak |  |
| `dihapus_oleh` | INTEGER | tidak |  |

- **Kunci utama:** `device_id`

### 2.10 `app_user`

Akun di HP, termasuk verifier login offline (PBKDF2) dan profil mandor.

| Kolom | Tipe | Wajib | Bawaan |
|---|---|---|---|
| `user_id` | INTEGER | ya |  |
| `unit_id` | INTEGER | ya |  |
| `erp_user_oid` | INTEGER | tidak |  |
| `mandor_hrmb_code` | INTEGER | tidak |  |
| `afdeling_kode` | TEXT | tidak |  |
| `username` | TEXT | ya |  |
| `nama` | TEXT | ya |  |
| `aktif` | INTEGER | ya |  |
| `gagal_login` | INTEGER | ya |  |
| `terkunci_sampai` | TEXT | tidak |  |
| `login_terakhir` | TEXT | tidak |  |
| `offline_hash` | TEXT | tidak |  |
| `offline_salt` | TEXT | tidak |  |
| `offline_berlaku_sampai` | TEXT | tidak |  |
| `dibuat_pada` | TEXT | ya |  |
| `dibuat_oleh` | INTEGER | tidak |  |
| `diubah_pada` | TEXT | tidak |  |
| `diubah_oleh` | INTEGER | tidak |  |
| `dihapus_pada` | TEXT | tidak |  |
| `dihapus_oleh` | INTEGER | tidak |  |

- **Kunci utama:** `user_id`
- **Indeks unik:** `ux_app_user_username` (username)
- **FK:** (unit_id) ke `unit` (unit_id) ON DELETE NO ACTION

### 2.11 `role`

Role lokal (mengikuti server).

| Kolom | Tipe | Wajib | Bawaan |
|---|---|---|---|
| `role_id` | INTEGER | ya |  |
| `kode` | TEXT | ya |  |
| `nama` | TEXT | ya |  |
| `deskripsi` | TEXT | tidak |  |
| `bawaan` | INTEGER | ya |  |
| `dibuat_pada` | TEXT | ya |  |
| `dibuat_oleh` | INTEGER | tidak |  |
| `diubah_pada` | TEXT | tidak |  |
| `diubah_oleh` | INTEGER | tidak |  |
| `dihapus_pada` | TEXT | tidak |  |
| `dihapus_oleh` | INTEGER | tidak |  |

- **Kunci utama:** `role_id`
- **Indeks unik:** `ux_role_kode` (kode)

### 2.12 `permission`

Permission lokal.

| Kolom | Tipe | Wajib | Bawaan |
|---|---|---|---|
| `permission_id` | INTEGER | ya |  |
| `kode` | TEXT | ya |  |
| `modul` | TEXT | ya |  |
| `nama` | TEXT | ya |  |
| `dibuat_pada` | TEXT | ya |  |
| `dibuat_oleh` | INTEGER | tidak |  |
| `diubah_pada` | TEXT | tidak |  |
| `diubah_oleh` | INTEGER | tidak |  |
| `dihapus_pada` | TEXT | tidak |  |
| `dihapus_oleh` | INTEGER | tidak |  |

- **Kunci utama:** `permission_id`
- **Indeks unik:** `ux_permission_kode` (kode)

### 2.13 `user_role`

Role akun.

| Kolom | Tipe | Wajib | Bawaan |
|---|---|---|---|
| `ur_id` | INTEGER | ya |  |
| `user_id` | INTEGER | ya |  |
| `role_id` | INTEGER | ya |  |
| `berlaku_mulai` | TEXT | tidak |  |
| `berlaku_sampai` | TEXT | tidak |  |
| `dibuat_pada` | TEXT | ya |  |
| `dibuat_oleh` | INTEGER | tidak |  |
| `diubah_pada` | TEXT | tidak |  |
| `diubah_oleh` | INTEGER | tidak |  |
| `dihapus_pada` | TEXT | tidak |  |
| `dihapus_oleh` | INTEGER | tidak |  |

- **Kunci utama:** `ur_id`
- **Indeks unik:** `ux_user_role_user_id_role_id` (user_id, role_id)
- **FK:** (user_id) ke `app_user` (user_id) ON DELETE NO ACTION
- **FK:** (role_id) ke `role` (role_id) ON DELETE NO ACTION

### 2.14 `role_permission`

Hak role (mengikuti server lewat master).

| Kolom | Tipe | Wajib | Bawaan |
|---|---|---|---|
| `rp_id` | INTEGER | ya |  |
| `role_id` | INTEGER | ya |  |
| `permission_id` | INTEGER | ya |  |
| `dibuat_pada` | TEXT | ya |  |
| `dibuat_oleh` | INTEGER | tidak |  |
| `diubah_pada` | TEXT | tidak |  |
| `diubah_oleh` | INTEGER | tidak |  |
| `dihapus_pada` | TEXT | tidak |  |
| `dihapus_oleh` | INTEGER | tidak |  |

- **Kunci utama:** `rp_id`
- **Indeks unik:** `ux_role_permission_role_id_permission_id` (role_id, permission_id)
- **FK:** (role_id) ke `role` (role_id) ON DELETE NO ACTION
- **FK:** (permission_id) ke `permission` (permission_id) ON DELETE NO ACTION

### 2.15 `panen_header`

Satu catatan panen di HP dengan status siklus HP (DRAFT, DISETUJUI, SIAP_KIRIM, MENUNGGU_SETUJU, TERKIRIM, DITERIMA, DITOLAK) dan `pesan_server`.

| Kolom | Tipe | Wajib | Bawaan |
|---|---|---|---|
| `panen_id` | TEXT | ya |  |
| `unit_id` | INTEGER | ya |  |
| `blok_id` | INTEGER | ya |  |
| `tph_id` | INTEGER | ya |  |
| `user_id` | INTEGER | ya |  |
| `device_id` | TEXT | ya |  |
| `model_id` | INTEGER | tidak |  |
| `periode_id` | INTEGER | tidak |  |
| `tanggal_panen` | TEXT | ya |  |
| `rotasi_no` | INTEGER | tidak |  |
| `kondisi_cuaca` | INTEGER | tidak |  |
| `brondolan_kg` | REAL | ya |  |
| `gps_lat` | REAL | tidak |  |
| `gps_lon` | REAL | tidak |  |
| `gps_akurasi_m` | REAL | tidak |  |
| `gps_waktu` | TEXT | tidak |  |
| `gps_mock` | INTEGER | ya |  |
| `jarak_ke_tph_m` | REAL | tidak |  |
| `status_lokasi` | TEXT | ya |  |
| `catatan` | TEXT | tidak |  |
| `status` | TEXT | ya |  |
| `dikirim_pada` | TEXT | tidak |  |
| `pesan_server` | TEXT | tidak |  |
| `disetujui_oleh` | INTEGER | tidak |  |
| `disetujui_pada` | TEXT | tidak |  |
| `kirim_ulang` | INTEGER | ya | `0` |
| `dibuat_pada` | TEXT | ya |  |
| `dibuat_oleh` | INTEGER | tidak |  |
| `diubah_pada` | TEXT | tidak |  |
| `diubah_oleh` | INTEGER | tidak |  |
| `dihapus_pada` | TEXT | tidak |  |
| `dihapus_oleh` | INTEGER | tidak |  |

- **Kunci utama:** `panen_id`
- **Indeks:** `ix_panen_header_tanggal_panen_unit_id` (tanggal_panen, unit_id)
- **Indeks:** `ix_panen_header_status` (status)
- **Indeks:** `ix_panen_header_tph_id_tanggal_panen` (tph_id, tanggal_panen)
- **FK:** (unit_id) ke `unit` (unit_id) ON DELETE NO ACTION
- **FK:** (blok_id) ke `blok` (blok_id) ON DELETE NO ACTION
- **FK:** (tph_id) ke `tph` (tph_id) ON DELETE NO ACTION
- **FK:** (user_id) ke `app_user` (user_id) ON DELETE NO ACTION
- **FK:** (device_id) ke `device` (device_id) ON DELETE NO ACTION
- **FK:** (model_id) ke `model_versi` (model_id) ON DELETE NO ACTION
- **FK:** (periode_id) ke `periode_rotasi` (periode_id) ON DELETE NO ACTION

### 2.16 `panen_pemanen`

Pemanen catatan (satu baris; pembagian janjang dihapus sejak 18 Nov).

| Kolom | Tipe | Wajib | Bawaan |
|---|---|---|---|
| `pp_id` | INTEGER | ya |  |
| `panen_id` | TEXT | ya |  |
| `pemanen_id` | INTEGER | tidak |  |
| `jjg_alokasi` | INTEGER | tidak |  |
| `catatan` | TEXT | tidak |  |
| `dibuat_pada` | TEXT | ya |  |
| `dibuat_oleh` | INTEGER | tidak |  |
| `diubah_pada` | TEXT | tidak |  |
| `diubah_oleh` | INTEGER | tidak |  |
| `dihapus_pada` | TEXT | tidak |  |
| `dihapus_oleh` | INTEGER | tidak |  |

- **Kunci utama:** `pp_id` (otomatis)
- **FK:** (panen_id) ke `panen_header` (panen_id) ON DELETE CASCADE
- **FK:** (pemanen_id) ke `pemanen` (pemanen_id) ON DELETE NO ACTION

### 2.17 `panen_grading`

Hitungan per kelas: `jml_ai` dan `jml_final`.

| Kolom | Tipe | Wajib | Bawaan |
|---|---|---|---|
| `grading_id` | INTEGER | ya |  |
| `panen_id` | TEXT | ya |  |
| `kelas_id` | INTEGER | ya |  |
| `jml_ai` | INTEGER | ya |  |
| `jml_final` | INTEGER | ya |  |
| `diubah_manual` | INTEGER | ya |  |
| `dibuat_pada` | TEXT | ya |  |
| `dibuat_oleh` | INTEGER | tidak |  |
| `diubah_pada` | TEXT | tidak |  |
| `diubah_oleh` | INTEGER | tidak |  |
| `dihapus_pada` | TEXT | tidak |  |
| `dihapus_oleh` | INTEGER | tidak |  |

- **Kunci utama:** `grading_id` (otomatis)
- **Indeks unik:** `ux_panen_grading_panen_id_kelas_id` (panen_id, kelas_id)
- **FK:** (panen_id) ke `panen_header` (panen_id) ON DELETE CASCADE
- **FK:** (kelas_id) ke `kelas_grading` (kelas_id) ON DELETE NO ACTION

### 2.18 `foto`

Foto bukti catatan panen (panorama) dengan status unggah.

| Kolom | Tipe | Wajib | Bawaan |
|---|---|---|---|
| `foto_id` | TEXT | ya |  |
| `panen_id` | TEXT | ya |  |
| `jenis` | TEXT | ya |  |
| `path_lokal` | TEXT | ya |  |
| `sha256` | TEXT | tidak |  |
| `lebar` | INTEGER | tidak |  |
| `tinggi` | INTEGER | tidak |  |
| `status_unggah` | TEXT | ya |  |
| `dibuat_pada` | TEXT | ya |  |
| `dibuat_oleh` | INTEGER | tidak |  |
| `diubah_pada` | TEXT | tidak |  |
| `diubah_oleh` | INTEGER | tidak |  |
| `dihapus_pada` | TEXT | tidak |  |
| `dihapus_oleh` | INTEGER | tidak |  |

- **Kunci utama:** `foto_id`
- **Indeks:** `ix_foto_panen_id` (panen_id)
- **FK:** (panen_id) ke `panen_header` (panen_id) ON DELETE CASCADE

### 2.19 `deteksi`

Kotak deteksi pada foto (tidak disimpan untuk panorama).

| Kolom | Tipe | Wajib | Bawaan |
|---|---|---|---|
| `deteksi_id` | INTEGER | ya |  |
| `foto_id` | TEXT | ya |  |
| `kelas_id` | INTEGER | ya |  |
| `confidence` | REAL | ya |  |
| `x` | REAL | tidak |  |
| `y` | REAL | tidak |  |
| `w` | REAL | tidak |  |
| `h` | REAL | tidak |  |
| `dibuat_pada` | TEXT | ya |  |
| `dibuat_oleh` | INTEGER | tidak |  |
| `diubah_pada` | TEXT | tidak |  |
| `diubah_oleh` | INTEGER | tidak |  |

- **Kunci utama:** `deteksi_id` (otomatis)
- **FK:** (foto_id) ke `foto` (foto_id) ON DELETE CASCADE
- **FK:** (kelas_id) ke `kelas_grading` (kelas_id) ON DELETE NO ACTION

### 2.20 `audit_log`

Audit lokal; `disinkron` 0/1.

| Kolom | Tipe | Wajib | Bawaan |
|---|---|---|---|
| `audit_id` | TEXT | ya |  |
| `tabel` | TEXT | ya |  |
| `baris_id` | TEXT | ya |  |
| `aksi` | TEXT | ya |  |
| `nilai_lama` | TEXT | tidak |  |
| `nilai_baru` | TEXT | tidak |  |
| `user_id` | INTEGER | tidak |  |
| `username` | TEXT | tidak |  |
| `device_id` | TEXT | tidak |  |
| `waktu` | TEXT | ya |  |
| `disinkron` | INTEGER | ya |  |
| `keterangan` | TEXT | tidak |  |

- **Kunci utama:** `audit_id`
- **Indeks:** `ix_audit_log_tabel_baris_id` (tabel, baris_id)
- **Indeks:** `ix_audit_log_user_id_waktu` (user_id, waktu)

### 2.21 `sync_queue`

Antrean kirim: `PANEN`, `FOTO`, `AUDIT`, `TPH_KOORDINAT`, `IPD`, `IPD_FOTO`; status MENUNGGU/JALAN/GAGAL/SELESAI.

| Kolom | Tipe | Wajib | Bawaan |
|---|---|---|---|
| `queue_id` | INTEGER | ya |  |
| `panen_id` | TEXT | tidak |  |
| `entitas` | TEXT | ya |  |
| `aksi` | TEXT | ya |  |
| `payload_json` | TEXT | tidak |  |
| `percobaan` | INTEGER | ya |  |
| `status` | TEXT | ya |  |
| `error_terakhir` | TEXT | tidak |  |
| `coba_terakhir` | TEXT | tidak |  |
| `dibuat_pada` | TEXT | ya |  |
| `dibuat_oleh` | INTEGER | tidak |  |
| `diubah_pada` | TEXT | tidak |  |
| `diubah_oleh` | INTEGER | tidak |  |

- **Kunci utama:** `queue_id` (otomatis)
- **Indeks:** `ix_sync_queue_status_dibuat_pada` (status, dibuat_pada)
- **FK:** (panen_id) ke `panen_header` (panen_id) ON DELETE CASCADE

### 2.22 `master_sync`

Versi master terakhir yang diterapkan per nama master dan unit.

| Kolom | Tipe | Wajib | Bawaan |
|---|---|---|---|
| `nama_master` | TEXT | ya |  |
| `unit_id` | INTEGER | ya |  |
| `versi_server` | TEXT | tidak |  |
| `sinkron_terakhir` | TEXT | tidak |  |
| `dibuat_pada` | TEXT | ya |  |
| `dibuat_oleh` | INTEGER | tidak |  |
| `diubah_pada` | TEXT | tidak |  |
| `diubah_oleh` | INTEGER | tidak |  |

- **Kunci utama:** `nama_master, unit_id`
- **FK:** (unit_id) ke `unit` (unit_id) ON DELETE NO ACTION

### 2.23 `kode_temuan`

Salinan master kode temuan IPD.

| Kolom | Tipe | Wajib | Bawaan |
|---|---|---|---|
| `kode_id` | INTEGER | ya |  |
| `kode` | TEXT | ya |  |
| `nama` | TEXT | ya |  |
| `kelompok` | TEXT | ya |  |
| `urut` | INTEGER | ya |  |
| `aktif` | INTEGER | ya |  |
| `dibuat_pada` | TEXT | ya |  |
| `dibuat_oleh` | INTEGER | tidak |  |
| `diubah_pada` | TEXT | tidak |  |
| `diubah_oleh` | INTEGER | tidak |  |
| `dihapus_pada` | TEXT | tidak |  |
| `dihapus_oleh` | INTEGER | tidak |  |

- **Kunci utama:** `kode_id`
- **Indeks unik:** `ux_kode_temuan_kode` (kode)

### 2.24 `ipd_header`

Satu inspeksi IPD di HP (status DRAFT, MENUNGGU_SETUJU, DISETUJUI, DITOLAK; `kirim_ulang`).

| Kolom | Tipe | Wajib | Bawaan |
|---|---|---|---|
| `ipd_id` | TEXT | ya |  |
| `unit_id` | INTEGER | ya |  |
| `blok_id` | INTEGER | ya |  |
| `pemanen_id` | INTEGER | ya |  |
| `user_id` | INTEGER | ya |  |
| `device_id` | TEXT | ya |  |
| `tanggal_inspeksi` | TEXT | ya |  |
| `ancak_no` | TEXT | tidak |  |
| `divisi_id` | INTEGER | ya |  |
| `asisten_user_id` | INTEGER | tidak |  |
| `mandor_user_id` | INTEGER | tidak |  |
| `mandor_hrmb` | INTEGER | tidak |  |
| `catatan` | TEXT | tidak |  |
| `status` | TEXT | ya |  |
| `dikirim_pada` | TEXT | tidak |  |
| `pesan_server` | TEXT | tidak |  |
| `divalidasi_oleh` | INTEGER | tidak |  |
| `divalidasi_pada` | TEXT | tidak |  |
| `disetujui_oleh` | INTEGER | tidak |  |
| `disetujui_pada` | TEXT | tidak |  |
| `kirim_ulang` | INTEGER | ya |  |
| `dibuat_pada` | TEXT | ya |  |
| `dibuat_oleh` | INTEGER | tidak |  |
| `diubah_pada` | TEXT | tidak |  |
| `diubah_oleh` | INTEGER | tidak |  |
| `dihapus_pada` | TEXT | tidak |  |
| `dihapus_oleh` | INTEGER | tidak |  |

- **Kunci utama:** `ipd_id`
- **Indeks:** `ix_ipd_header_tanggal_inspeksi_unit_id` (tanggal_inspeksi, unit_id)
- **Indeks:** `ix_ipd_header_status` (status)
- **Indeks:** `ix_ipd_header_pemanen_id_blok_id_tanggal_inspeksi` (pemanen_id, blok_id, tanggal_inspeksi)

### 2.25 `ipd_pokok`

Pokok yang diperiksa (baris, pokok, waktu catat).

| Kolom | Tipe | Wajib | Bawaan |
|---|---|---|---|
| `pokok_id` | INTEGER | ya |  |
| `ipd_id` | TEXT | ya |  |
| `no_baris` | INTEGER | ya |  |
| `no_pokok` | INTEGER | ya |  |
| `dibuat_pada` | TEXT | ya |  |
| `dibuat_oleh` | INTEGER | tidak |  |
| `diubah_pada` | TEXT | tidak |  |
| `diubah_oleh` | INTEGER | tidak |  |
| `dihapus_pada` | TEXT | tidak |  |
| `dihapus_oleh` | INTEGER | tidak |  |

- **Kunci utama:** `pokok_id` (otomatis)
- **Indeks unik:** `ux_ipd_pokok_ipd_id_baris_pokok` (ipd_id, no_baris, no_pokok)
- **FK:** (ipd_id) ke `ipd_header` (ipd_id) ON DELETE CASCADE

### 2.26 `ipd_temuan`

Temuan per pokok: kode dan jumlah.

| Kolom | Tipe | Wajib | Bawaan |
|---|---|---|---|
| `temuan_id` | INTEGER | ya |  |
| `pokok_id` | INTEGER | ya |  |
| `kode_id` | INTEGER | ya |  |
| `jumlah` | INTEGER | ya |  |
| `catatan` | TEXT | tidak |  |
| `dibuat_pada` | TEXT | ya |  |
| `dibuat_oleh` | INTEGER | tidak |  |
| `diubah_pada` | TEXT | tidak |  |
| `diubah_oleh` | INTEGER | tidak |  |
| `dihapus_pada` | TEXT | tidak |  |
| `dihapus_oleh` | INTEGER | tidak |  |

- **Kunci utama:** `temuan_id` (otomatis)
- **Indeks unik:** `ux_ipd_temuan_pokok_id_kode_id` (pokok_id, kode_id)
- **FK:** (pokok_id) ke `ipd_pokok` (pokok_id) ON DELETE CASCADE
- **FK:** (kode_id) ke `kode_temuan` (kode_id) ON DELETE NO ACTION

### 2.27 `ipd_foto`

Foto bukti inspeksi dengan keterangan; berkas HP dihapus setelah terunggah.

| Kolom | Tipe | Wajib | Bawaan |
|---|---|---|---|
| `foto_id` | TEXT | ya |  |
| `ipd_id` | TEXT | ya |  |
| `keterangan` | TEXT | tidak |  |
| `urut` | INTEGER | ya |  |
| `path_lokal` | TEXT | tidak |  |
| `sha256` | TEXT | tidak |  |
| `lebar` | INTEGER | tidak |  |
| `tinggi` | INTEGER | tidak |  |
| `ukuran_byte` | INTEGER | tidak |  |
| `status_unggah` | TEXT | ya |  |
| `dibuat_pada` | TEXT | ya |  |
| `dibuat_oleh` | INTEGER | tidak |  |
| `diubah_pada` | TEXT | tidak |  |
| `diubah_oleh` | INTEGER | tidak |  |
| `dihapus_pada` | TEXT | tidak |  |
| `dihapus_oleh` | INTEGER | tidak |  |

- **Kunci utama:** `foto_id`
- **Indeks:** `ix_ipd_foto_ipd_id` (ipd_id)
- **FK:** (ipd_id) ke `ipd_header` (ipd_id) ON DELETE CASCADE

### 2.28 `ipd_jejak`

Titik jalur GPS inspeksi; dihapus bersama draf, dan setelah inspeksi DISETUJUI.

| Kolom | Tipe | Wajib | Bawaan |
|---|---|---|---|
| `jejak_id` | INTEGER | ya |  |
| `ipd_id` | TEXT | ya |  |
| `waktu` | TEXT | ya |  |
| `lat` | REAL | ya |  |
| `lon` | REAL | ya |  |
| `akurasi` | REAL | tidak |  |

- **Kunci utama:** `jejak_id` (otomatis)
- **Indeks:** `ix_ipd_jejak_ipd_id` (ipd_id)

## 3. Status siklus HP

| Tabel | Kolom | Nilai |
|---|---|---|
| `panen_header` | `status` | `DRAFT` (krani, belum disetujui), `DISETUJUI` (masuk antrean), `SIAP_KIRIM` (tidak dipakai), `MENUNGGU_SETUJU` (terkirim, menunggu krani), `TERKIRIM` (server BARU/DIBACA), `DITERIMA` (ERP DIIMPOR), `DITOLAK` (krani atau ERP, alasan di `pesan_server`) |
| `ipd_header` | `status` | `DRAFT`, `MENUNGGU_SETUJU` (menunggu asisten), `DISETUJUI` (terkunci), `DITOLAK` |
| `sync_queue` | `status` | `MENUNGGU`, `JALAN`, `GAGAL`, `SELESAI` |
| `foto` | `status_unggah` | `BELUM`, `SUDAH` |

