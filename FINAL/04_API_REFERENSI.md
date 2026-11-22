# 04. API Referensi (fai26-api 0.21.0)

API Go yang dipakai HP (mengisi `fai26`) dan web admin. **Versi 0.21.0, 64 endpoint**, dokumentasi mesin `lampiran/api/openapi.yaml` dan panduan `lampiran/api/API.md` (keduanya salinan 22 Nov 2026 dari `fai26-api/docs/`; yang di repositori adalah sumber yang berlaku). Tes `TestSemuaRuteTerdokumentasi` dan `TestVersiDokumenSama` menjamin dokumentasi sama dengan rute yang terpasang. Dokumen ini merangkum arsitektur, konvensi, daftar endpoint, kontrak inti, konfigurasi, dan riwayat versi.

## 1. Arsitektur internal

Struktur proyek `fai26-api/`: `cmd/api` (titik masuk; menjalankan `http.Server`, tugas perawatan, dan berhenti rapi pada SIGINT/SIGTERM dalam 10 detik), `cmd/hashpw` (pembuat hash bcrypt untuk akun pertama), `internal/` (lapisan di bawah), `migrations/`, `docs/`, `scripts/`, `test/integration/`.

| Paket | Isi |
|---|---|
| `routes` | Satu tabel rute: metode, alamat, hak yang diminta, pengendali |
| `middleware` | Autentikasi (JWT), batas laju per IP, batas waktu permintaan 20 detik |
| `controller` | Membaca permintaan dan menulis jawaban; **tanpa aturan bisnis dan tanpa SQL** |
| `service` | Aturan bisnis dan validasi (pemeriksaan hak, aturan persetujuan, anti ganda, loses, jalur GPS) |
| `repository` | **Seluruh SQL** (go-mssqldb); transaksi; audit |
| `model` | Struktur data permintaan dan jawaban, validasi masukan |
| `auth` | Token JWT HS256, hash bcrypt, token acak |
| `config` | Variabel lingkungan dan `.env` |
| `apperror`, `response` | Galat seragam `{"error": "pesan"}` dan penulis JSON |
| `storage` | Penyimpanan foto di folder media (`<panen_id>/<foto_id>.jpg`, `profil/<user_id>.jpg`) |
| `webui` | Menyajikan web admin (hasil build) dari alamat yang sama, dengan CSP dan `Referrer-Policy` |
| `app` | Perakitan; `perawatan.go` menjalankan kebijakan penyimpanan jalur GPS tiap 6 jam |

Perjalanan satu permintaan: batas laju IP, batas waktu, autentikasi (token), pemeriksaan hak (dibaca ulang dari database **di setiap panggilan**), controller, service, repository (SQL), jawaban JSON.

## 2. Konvensi umum

- **Format:** JSON UTF-8 (kecuali unggah foto: byte JPEG mentah). Waktu ISO 8601 UTC (`2026-11-10T03:00:00Z`); tanggal `YYYY-MM-DD`.
- **Autentikasi:** `Authorization: Bearer <access_token>`. Token akses **15 menit** (`FAI26_AKSES_MENIT`); refresh token **30 hari**, **diganti setiap dipakai** (rotasi). Identitas pencatat, perangkat, dan `user_id` audit **selalu dari token**, bukan isi kiriman.
- **Hak akses dibaca ulang dari database di setiap panggilan:** akun atau perangkat yang dinonaktifkan langsung tidak bisa dipakai walau token masih berlaku.
- **Idempoten:** `panen_id`, `ipd_id`, `X-Foto-Id`, `audit_id` yang sama tidak menggandakan data.
- **Batas waktu:** 20 detik per permintaan; database lambat dijawab 503 (sementara, aman diulang).
- **Batas laju per IP:** login dan refresh 30 per menit, seluruh API 600 per menit (`FAI26_BATAS_LOGIN`, `FAI26_BATAS_UMUM`; 0 = tanpa batas); melebihi dijawab 429 dengan `Retry-After`.
- **Galat:** `{"error": "pesan"}`; 5xx hanya pesan umum (rincian di log server).

| Kode | Arti |
|---|---|
| 200 / 201 | Berhasil (atau `SUDAH_ADA`) / data baru dibuat |
| 400 | Isi tidak terbaca atau parameter wajib kurang |
| 401 | Token tidak ada, rusak, kedaluwarsa, atau login salah |
| 403 | Akun/perangkat nonaktif, atau tanpa permission |
| 404 | Data tidak ada |
| 409 | Ganda (anti ganda panen: tanggal + unit + blok + TPH + pemanen; nomor baris/pokok IPD ganda; sudah diproses) |
| 413 / 415 | Terlalu besar (foto > 8 MB, audit > 500 baris) / bukan JPEG |
| 422 | Terbaca tetapi tidak lolos aturan (pesan menyebut sebabnya) |
| 423 | Akun terkunci sementara; atau catatan sudah disetujui sehingga terkunci |
| 429 | Terlalu banyak permintaan |
| 500 / 503 | Kesalahan server / database lambat atau tidak terjangkau |

## 3. Alur yang dipakai HP

```
1. POST /api/auth/login            -> access_token, refresh_token, profil
   GET  /api/master?versi=...      -> master bila versinya berubah (blok, TPH, pemanen, akun, kode temuan, hak role)
2. (bekerja offline; titik GPS TPH yang direkam ikut antrean)
   PUT  /api/tph/{id}/koordinat    -> hanya bila server belum punya titik
3. POST /api/panen                 -> 201 DITERIMA atau 200 SUDAH_ADA (status server MENUNGGU atau BARU)
4. POST /api/panen/{id}/media      -> foto, setelah catatan terkirim
5. POST /api/audit/batch           -> audit lokal yang belum terkirim
6. GET  /api/panen?sejak=...       -> status: TERKIRIM -> DITERIMA / DITOLAK (+ alasan)
   Krani: GET /api/panen/menunggu -> POST /api/panen/{id}/setujui | /tolak
   IPD:   POST /api/ipd -> POST /api/ipd/{id}/media (foto) -> GET /api/ipd (status)
          asisten: GET /api/ipd/menunggu -> POST /api/ipd/{id}/setujui | /tolak
   Token habis (401)               -> POST /api/auth/refresh lalu ulangi
```

## 4. Daftar endpoint (64)

| Method | Alamat | Perlu login | Permission | Fungsi |
|---|---|---|---|---|
| GET | `/api/health` | tidak | - | Cek server dan database |
| POST | `/api/auth/login` | tidak | - | Login dan daftarkan perangkat |
| POST | `/api/auth/refresh` | tidak | - | Tukar refresh token |
| POST | `/api/auth/logout` | ya | - | Cabut sesi perangkat |
| GET | `/api/auth/me` | ya | - | Profil dan permission terbaru |
| POST | `/api/auth/password` | ya | - | Ganti password sendiri |
| PUT | `/api/auth/foto` | ya | - | Ganti foto profil sendiri |
| GET | `/api/auth/foto` | ya | - | Baca foto profil sendiri |
| DELETE | `/api/auth/foto` | ya | - | Hapus foto profil sendiri |
| POST | `/api/panen` | ya | `panen.kirim` | Kirim satu catatan panen |
| GET | `/api/panen` | ya | `panen.lihat` | Status catatan (Terkirim, Diterima, Ditolak) |
| GET | `/api/panen/ringkasan` | ya | `panen.lihat` | Ringkasan dashboard HP (produksi hari ini, kelas, blok, tren 7 hari) |
| GET | `/api/panen/menunggu` | ya | `panen.setujui` | Catatan di unit yang menunggu persetujuan krani |
| POST | `/api/panen/{id}/setujui` | ya | `panen.setujui` | Setujui satu catatan menunggu (siap ditarik ERP) |
| POST | `/api/panen/{id}/tolak` | ya | `panen.setujui` | Tolak satu catatan menunggu dengan alasan |
| POST | `/api/panen/{id}/media` | ya | `panen.kirim` | Unggah foto JPEG |
| POST | `/api/ipd` | ya | `ipd.kirim` | Kirim satu inspeksi panen detail (IPD) |
| GET | `/api/ipd` | ya | `ipd.buat` | Status inspeksi milik sendiri |
| GET | `/api/ipd/inspeksi-terakhir` | ya | `ipd.buat` | Peringatan: pemanen + blok ini pernah diinspeksi 7 hari terakhir |
| GET | `/api/ipd/menunggu` | ya | `ipd.setujui` | Inspeksi yang menunggu persetujuan asisten ini |
| POST | `/api/ipd/{id}/setujui` | ya | `ipd.setujui` | Setujui inspeksi (setelah itu terkunci) |
| POST | `/api/ipd/{id}/tolak` | ya | `ipd.setujui` | Tolak inspeksi dengan alasan |
| POST | `/api/ipd/{id}/media` | ya | `ipd.kirim` | Unggah satu foto bukti (tanpa batas jumlah) |
| GET | `/api/ipd/foto/{id}` | ya | `ipd.lihat` | Unduh foto bukti inspeksi |
| POST | `/api/audit/batch` | ya | - | Kirim audit_log dari HP |
| GET | `/api/master` | ya | `master.lihat` | Unduh master unit (blok, TPH, pemanen, akun) |
| PUT | `/api/tph/{id}/koordinat` | ya | `panen.kirim` | Rekam koordinat TPH dari HP (hanya bila belum ada) |
| GET | `/api/admin/divisi` | ya | `master.lihat` | Daftar divisi |
| POST | `/api/admin/divisi` | ya | `master.kelola` | Buat divisi |
| PUT | `/api/admin/divisi/{id}` | ya | `master.kelola` | Ubah divisi |
| DELETE | `/api/admin/divisi/{id}` | ya | `master.kelola` | Hapus divisi (lunak) |
| GET | `/api/admin/blok` | ya | `master.lihat` | Daftar blok |
| POST | `/api/admin/blok` | ya | `master.kelola` | Buat blok |
| PUT | `/api/admin/blok/{id}` | ya | `master.kelola` | Ubah blok |
| DELETE | `/api/admin/blok/{id}` | ya | `master.kelola` | Hapus blok (lunak) |
| GET | `/api/admin/tph` | ya | `master.lihat` | Daftar TPH |
| POST | `/api/admin/tph` | ya | `master.kelola` | Buat TPH |
| POST | `/api/admin/tph/massal` | ya | `master.kelola` | Buat TPH berurutan |
| PUT | `/api/admin/tph/{id}` | ya | `master.kelola` | Ubah TPH |
| DELETE | `/api/admin/tph/{id}` | ya | `master.kelola` | Hapus TPH (lunak) |
| GET | `/api/admin/pemanen` | ya | `master.lihat` | Daftar pemanen |
| POST | `/api/admin/pemanen` | ya | `master.kelola` | Buat pemanen |
| PUT | `/api/admin/pemanen/{id}` | ya | `master.kelola` | Ubah pemanen |
| DELETE | `/api/admin/pemanen/{id}` | ya | `master.kelola` | Hapus pemanen (lunak) |
| GET | `/api/admin/peran` | ya | `user.kelola` | Daftar role |
| GET | `/api/admin/hak-akses` | ya | `role.kelola` | Daftar role beserta hak dan katalog permission |
| POST | `/api/admin/hak-akses` | ya | `role.kelola` | Buat role baru beserta hak awalnya |
| PUT | `/api/admin/hak-akses/{id}` | ya | `role.kelola` | Ganti seluruh hak satu role (dan, bila dikirim, nama atau keterangannya) |
| DELETE | `/api/admin/hak-akses/{id}` | ya | `role.kelola` | Hapus role buatan admin yang tidak dipegang akun aktif |
| GET | `/api/admin/pengguna` | ya | `user.kelola` | Daftar pengguna |
| POST | `/api/admin/pengguna` | ya | `user.kelola` | Buat pengguna |
| PUT | `/api/admin/pengguna/{id}` | ya | `user.kelola` | Ubah pengguna |
| POST | `/api/admin/pengguna/{id}/reset-password` | ya | `user.kelola` | Reset password pengguna |
| GET | `/api/admin/perangkat` | ya | `device.kelola` | Daftar perangkat |
| PUT | `/api/admin/perangkat/{id}` | ya | `device.kelola` | Aktifkan atau nonaktifkan perangkat |
| GET | `/api/admin/ringkasan` | ya | `laporan.lihat` | Ringkasan dasbor |
| GET | `/api/admin/produktivitas` | ya | `laporan.lihat` | Ranking produktivitas pemanen satu hari (janjang, mutu, brondolan; tanpa premi) |
| GET | `/api/admin/panen` | ya | `panen.lihat` | Daftar catatan panen |
| GET | `/api/admin/panen/{id}` | ya | `panen.lihat` | Detail catatan panen |
| GET | `/api/admin/foto/{id}` | ya | `panen.lihat` | Unduh foto catatan |
| GET | `/api/admin/ipd` | ya | `ipd.lihat` | Daftar inspeksi (web) |
| GET | `/api/admin/ipd/laporan` | ya | `laporan.ipd` | Laporan loses dari inspeksi yang disetujui |
| GET | `/api/admin/ipd/{id}` | ya | `ipd.lihat` | Rincian satu inspeksi (semua pokok dan temuan) |
| GET | `/api/admin/audit` | ya | `audit.lihat` | Cari audit log |

**Catatan hak:** `ipd.jejak` (jalur GPS) tidak tercantum di kolom Permission karena bukan syarat memanggil `GET /api/admin/ipd/{id}` (syaratnya `ipd.lihat`); `ipd.jejak` hanya menentukan apakah isi `jejak` dikirim (selain itu `null`), dan **setiap pengiriman jalur dicatat di audit `LIHAT_JALUR`**.

## 5. Konfigurasi server (variabel lingkungan atau `.env`)

| Variabel | Bawaan | Keterangan |
|---|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | - | Koneksi SQL Server (atau satu `FAI26_DSN`). **Rahasia: jangan di-commit** |
| `DB_ENCRYPT`, `DB_TRUST_CERT` | `true`, `false` | Enkripsi sambungan; `DB_TRUST_CERT=true` hanya bila sertifikat tak dipercaya |
| `FAI26_JWT_SECRET` | - | Wajib, minimal 32 karakter acak |
| `FAI26_AKSES_MENIT` | 15 | Umur token akses |
| `FAI26_ADDR` | `:8080` | Alamat dengar; produksi: `127.0.0.1:8080` di belakang reverse proxy |
| `FAI26_MEDIA_DIR` | `media` | Folder foto; **ikut dicadangkan bersama database** |
| `FAI26_WEB_DIR` | kosong | Folder hasil build web; bila diisi API menyajikan web dari alamat yang sama |
| `FAI26_BATAS_LOGIN`, `FAI26_BATAS_UMUM` | 30, 600 | Batas laju per IP per menit |
| `FAI26_PERCAYAI_PROXY` | `false` | `true` hanya di belakang reverse proxy yang menimpa `X-Forwarded-For` |
| `FAI26_JEJAK_SIMPAN_HARI` | 0 | Hapus otomatis jalur GPS inspeksi yang sudah diperiksa dan lebih tua dari N hari; 0 = tanpa batas |

## 6. Kontrak inti (salinan dari API.md, 22 Nov 2026)

Bagian ini menyalin kontrak endpoint yang paling menentukan. Rincian semua endpoint lain ada di `lampiran/api/API.md`.

### POST /api/auth/login

Tanpa login. Akun terkunci 15 menit setelah 5 kali password salah. Perangkat baru otomatis didaftarkan.

**Body**

| Field | Tipe | Wajib | Keterangan |
|---|---|---|---|
| `username` | string | ya | |
| `password` | string | ya | |
| `device_id` | UUID | ya | Dibuat aplikasi sekali saat pertama dipasang |
| `device_nama` | string | tidak | Contoh `Samsung A52` |
| `os_versi` | string | tidak | Contoh `Android 14` |
| `app_versi` | string | tidak | |

```json
{ "username": "krani1", "password": "krani123", "device_id": "11111111-1111-4111-8111-111111111111", "device_nama": "Samsung A52" }
```

**200**

```json
{
  "access_token": "eyJhbGciOi...",
  "access_expires": "2026-11-10T08:15:00Z",
  "refresh_token": "kPz1...",
  "profil": {
    "user_id": 1, "unit_id": 1, "unit_kode": "KP_STL", "username": "krani1", "nama": "Krani Contoh",
    "role": ["KRANI"],
    "permission": ["grading.ubah", "master.lihat", "panen.buat", "panen.kirim", "panen.lihat", "panen.ubah"],
    "wajib_ganti_password": false,
    "mandor_hrmb_code": null, "afdeling_kode": null
  }
}
```

`mandor_hrmb_code` dan `afdeling_kode` terisi untuk akun **mandor** (diisi admin), contoh `7001` dan `"DIV2"`; `null` untuk akun lain. `unit_kode` adalah unit akun. Server memakai nilai dari akun ini (bukan dari isi kiriman) sebagai mandor dan afdeling catatan.

**Galat:** 400 (field wajib kurang atau `device_id` bukan UUID) · 401 `Username atau password salah` ·
403 `Akun tidak aktif` / `Perangkat ini dinonaktifkan. Hubungi admin` · 423 `Akun terkunci sementara. Coba lagi nanti`.

### POST /api/panen

Perlu login, permission `panen.kirim`. Mengirim **satu** catatan panen yang **sudah disetujui mandor**.

**Body**

| Field | Tipe | Wajib | Keterangan |
|---|---|---|---|
| `panen_id` | UUID | ya | Dibuat HP; kunci idempotensi |
| `unit_kode` | string | ya | Harus sama dengan unit akun, kalau tidak 403 |
| `tanggal_panen` | `YYYY-MM-DD` | ya | |
| `asm3_oid` | integer > 0 | ya | OID blok di ERP; blok harus ada di master unit |
| `blok_kode` | string | tidak | |
| `tph_kode` | string | **ya** | Nomor TPH pada blok itu; harus ada di master |
| `rotasi`, `kondisi_cuaca` | integer | tidak | |
| `brondolan_kg` | number ≥ 0 | tidak | |
| `gps_lat`, `gps_lon`, `gps_akurasi_m`, `jarak_ke_tph_m` | number | tidak | Boleh kosong bila tanpa GPS |
| `gps_waktu` | date-time | tidak | |
| `gps_mock` | boolean | tidak | Android menandai lokasi palsu |
| `status_lokasi` | enum | ya | `DI_TPH`, `DI_LUAR`, `AKURASI_RENDAH`, `TANPA_GPS`, `TPH_TANPA_KOORDINAT` |
| `model_versi`, `catatan` | string | tidak | |
| `dibuat_pada` | date-time | tidak | Bila kosong dipakai jam server |
| `pencatat_username` | string | tidak | Pencatat catatan. Kosong berarti pengirim sendiri. Bila berbeda dari pengirim (mandor yang login di HP krani), pengirim harus punya `panen.koreksi`, kalau tidak 403 `Tidak berhak mengirim catatan milik orang lain`; username tak dikenal 422 |
| `disetujui_username` | string | **tidak** | Mandor penyetuju (harus punya `panen.setujui`). **Kosong** berarti pengirim sendiri yang menyetujui: sah bila pengirim memegang `panen.setujui` (mandor); pengirim tanpa hak itu (mandor) **tidak ditolak**: catatan disimpan berstatus `MENUNGGU` (belum terlihat HVDM) sampai krani menyetujuinya lewat `POST /api/panen/{id}/setujui`. Bila diisi, `disetujui_pada` wajib ikut |
| `disetujui_pada` | date-time | tidak | Wajib bersama `disetujui_username`; bila kosong dipakai jam server |
| `kirim_ulang` | boolean | tidak | `true` untuk mengubah catatan yang sudah terkirim, dengan `panen_id` yang sama: isinya diganti (201 `DITERIMA`). Berlaku selama catatan masih **MENUNGGU** persetujuan krani (status tetap `MENUNGGU`) atau **DITOLAK** setelah diperbaiki (kembali `MENUNGGU`, atau `BARU` bila pengirim memegang `panen.setujui`). Setelah krani menyetujui (`BARU`, `DIBACA`, `DIIMPOR`) catatan **terkunci**: dijawab 423. Tanpa `kirim_ulang` catatan yang sudah ada dijawab 200 `SUDAH_ADA` tanpa perubahan. Hanya oleh pencatat (atau pemegang `panen.koreksi`) |
| `grading[]` | array | ya, minimal 1 | Lihat di bawah |
| `pemanen[]` | array | **ya, tepat satu** | `{hrmb_code, jjg_alokasi}`. Satu TPH hanya untuk satu pemanen: `hrmb_code` > 0 dan `jjg_alokasi` harus sama dengan total `jml_final`. Pemanen harus ada di master unit; untuk **akun mandor**, harus pemanen miliknya (`mandor_hrmb_code` pemanen = mandor akun) |

`grading[]`: `kelas_kode` (`unripe`, `underripe`, `ripe`, `overripe`, `empty`, `abnormal`; tidak boleh ganda),
`erp_kolom` (contoh `JJG_RIPE`, boleh kosong), `jml_ai` ≥ 0, `jml_final` ≥ 0, `diubah_manual`.
Total `jml_final` semua kelas tidak boleh nol.

```json
{
  "panen_id": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
  "unit_kode": "KP_STL", "tanggal_panen": "2026-11-10", "asm3_oid": 101, "blok_kode": "A12", "tph_kode": "01",
  "brondolan_kg": 12.5,
  "gps_lat": -2.000123, "gps_lon": 113.000456, "gps_akurasi_m": 8, "gps_waktu": "2026-11-10T01:00:00Z",
  "gps_mock": false, "jarak_ke_tph_m": 22, "status_lokasi": "DI_TPH",
  "model_versi": "best 1", "catatan": "tumpukan dekat parit",
  "dibuat_pada": "2026-11-10T01:00:00Z",
  "disetujui_username": "mandor1", "disetujui_pada": "2026-11-10T03:00:00Z",
  "grading": [
    { "kelas_kode": "ripe", "erp_kolom": "JJG_RIPE", "jml_ai": 7, "jml_final": 9, "diubah_manual": true },
    { "kelas_kode": "unripe", "jml_ai": 1, "jml_final": 1, "diubah_manual": false }
  ],
  "pemanen": [ { "hrmb_code": 5001, "jjg_alokasi": 10 } ]
}
```

Saat catatan diterima, server mengisi **mandor** (`mandor_hrmb_code`) dan **afdeling** (`afdeling_kode`, `afdeling_erp_oid`) untuk header HVDM dari **akun pencatat** (bukan dari isi kiriman, supaya tidak bisa dipalsukan). Bila akun belum punya kode itu (krani), mandor diambil dari master pemanen dan afdeling dari divisi blok. Salinan ini tidak berubah walau master diubah belakangan.

**Anti ganda:** hanya boleh ada satu catatan untuk kombinasi `tanggal_panen` + unit + `asm3_oid` + `tph_kode` + pemanen. Kirim ulang `panen_id` yang sama dijawab 200 `SUDAH_ADA`; kombinasi yang sama dengan `panen_id` berbeda dijawab **409** `TPH ini sudah dicatat untuk pemanen dan tanggal yang sama` (HP jangan mengulangnya otomatis).

**Jawaban**

- **201** `{"status": "DITERIMA", "id": "..."}`: catatan baru tersimpan, status ERP awal `BARU`, atau `MENUNGGU` bila pengirim tidak punya hak setuju dan tidak membawa penyetuju (mandor); krani yang menyetujuinya.
- **200** `{"status": "SUDAH_ADA", "id": "..."}`: sudah pernah diterima; tidak ada yang berubah. HP boleh menandainya terkirim.

**Galat 422 (pesan menyebut penyebabnya):** `Penyetuju tidak dikenal: ...` ·
`Blok tidak ada di master` · `TPH ... tidak ada di master blok itu` · `Pemanen ... tidak ada di master` · `Pemanen ... bukan pemanen milik mandor yang login` · `tph_kode wajib diisi` ·
`Penyetuju tidak berhak menyetujui` · `unit_kode tidak dikenal: ...` · `status_lokasi tidak dikenal` ·
`kelas_kode tidak dikenal/ganda` · `jumlah janjang tidak boleh negatif` · `total janjang nol` ·
`pemanen wajib diisi tepat satu (satu TPH hanya untuk satu pemanen)` · `hrmb_code pemanen wajib diisi` ·
`jjg_alokasi pemanen harus sama dengan total janjang` ·
`tanggal_panen harus YYYY-MM-DD` · `panen_id harus UUID` · waktu salah format.
**Lainnya:** 400 JSON rusak · 403 unit berbeda dengan unit akun, tanpa permission, atau `kirim_ulang` oleh orang lain · 409 kombinasi ganda · 423 `Catatan sudah disetujui krani, tidak bisa diubah lagi` (`kirim_ulang` pada catatan yang sudah disetujui; HP menandainya terkunci dan tidak mengirim ulang).

### GET /api/panen

Perlu login, permission `panen.lihat`. Mengembalikan catatan milik pengguna; pemegang `panen.koreksi`
(mandor, asisten) melihat semua catatan di unitnya.

**Query** `sejak` (opsional, ISO 8601): hanya yang berubah setelah waktu itu.

**200**

```json
[
  { "id": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", "status": "DITOLAK", "status_erp": "DITOLAK",
    "pesan": "Blok tidak ditemukan di ERP", "diubah": "2026-11-10T04:00:00Z" }
]
```

Pemetaan `status_erp` → `status` untuk HP: `BARU`/`DIBACA` → `TERKIRIM`, `DIIMPOR` → `DITERIMA`, `DITOLAK` → `DITOLAK`.
`pesan` hanya ada bila ERP menulis alasan penolakan. `status` bisa juga `MENUNGGU_SETUJU` (tersimpan di server, menunggu krani menyetujui). **Galat:** 400 `sejak` salah format.

### GET /api/panen/menunggu

Perlu login, permission `panen.setujui` (krani). Daftar catatan **di unit pengguna** yang berstatus `MENUNGGU`, terlama dulu: dikirim mandor dari HP-nya,
belum disetujui. Krani memeriksanya lalu memanggil `setujui` atau `tolak`.

**200**

```json
[{
  "id": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", "tanggal_panen": "2026-11-19", "blok_kode": "A12", "tph_kode": "01", "afdeling_kode": "DIV1",
  "pencatat": "royyy", "pencatat_nama": "Royyy", "pemanen_nama": "Pemanen Satu", "pemanen_hrmb_code": 90001,
  "total_janjang": 10, "brondolan_kg": 12.5, "catatan": null, "status_lokasi": "DI_TPH", "jarak_ke_tph_m": 22, "gps_mock": false,
  "dibuat_pada": "2026-11-19T01:00:00Z", "grading": [{ "kelas_kode": "ripe", "janjang": 9 }, { "kelas_kode": "unripe", "janjang": 1 }],
  "foto_id": "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
}]
```

`grading` hanya memuat kelas bernilai lebih dari nol.

`foto_id` adalah foto bukti untuk diperiksa krani sebelum menyetujui: foto hasil deteksi bila ada, kalau tidak foto asli. Nilainya `null` selama foto belum terkirim (foto dikirim HP terpisah, sesudah data angka). Fotonya diunduh dengan `GET /api/admin/foto/{id}` (permission `panen.lihat`, yang juga dimiliki krani).

### POST /api/panen/{id}/setujui

Perlu login, permission `panen.setujui`. Tanpa body. Catatan `MENUNGGU` di unit pengguna menjadi `BARU` (siap ditarik HVDM), tercatat atas nama penyetuju
(`disetujui_oleh`, `disetujui_pada`) dan di `audit_log` (`SETUJU`). HP pencatat melihat statusnya berubah dari "menunggu krani" menjadi `TERKIRIM`.

**200** `{"status": "DISETUJUI"}` · **Galat:** 404 catatan tidak ada di unit ini · 409 `Catatan sudah diproses (status ...)` (sudah disetujui, ditolak, atau diimpor) · 422 `id` bukan UUID.

### POST /api/panen/{id}/tolak

Perlu login, permission `panen.setujui`. Mengembalikan catatan `MENUNGGU` ke pencatat. Catatan menjadi `DITOLAK` dan alasannya tampil di HP pencatat
sebagai `Ditolak krani: <alasan>`; pencatat bisa memperbaikinya lalu mengirim ulang (`kirim_ulang=true`, `panen_id` sama), dan catatan kembali `MENUNGGU`.

**Body** `{"alasan": "Blok salah"}` (wajib, maksimal 300 karakter).

**200** `{"status": "DITOLAK"}` · **Galat:** 404 · 409 (bukan lagi `MENUNGGU`) · 422 alasan kosong atau terlalu panjang.

### POST /api/panen/{id}/media

Perlu login, permission `panen.kirim`. Unggah satu foto JPEG untuk catatan `{id}` (`panen_id`).
Catatan harus sudah dikirim lebih dulu.

| Bagian | Nilai |
|---|---|
| Header `X-Foto-Id` | UUID foto, dibuat HP (wajib) |
| Header `X-Sha256` | SHA-256 isi foto, 64 heksadesimal (wajib) |
| Query `jenis` | `DETEKSI` (bawaan) atau `ASLI` |
| Header `Content-Type` | `image/jpeg` |
| Body | Byte JPEG mentah, maksimal 8 MB |

**Jawaban:** 201 `{"status": "DITERIMA", "id": "<foto id>"}` · 200 `SUDAH_ADA`.
**Galat:** 400 header/id salah · 403 bukan catatan Anda (kecuali punya `panen.koreksi`) · 404 `Catatan panen belum ada. Kirim catatan dulu` ·
413 lebih dari 8 MB · 415 bukan JPEG · 422 `Checksum tidak cocok. Kirim ulang`.

### GET /api/master

Perlu login, permission `master.lihat`. Mengembalikan master **unit milik akun**. Untuk akun **mandor** (punya `mandor_hrmb_code` dan/atau `afdeling_kode`) isinya dibatasi: `pemanen` hanya yang `mandor_hrmb_code`-nya sama dengan mandor itu, dan `divisi`, `blok`, `tph` hanya yang berada di afdelingnya. Akun tanpa kode itu (krani, admin) menerima seluruh unit. `versi` dihitung dari isi yang diterima akun itu, jadi dua akun dengan lingkup berbeda mendapat versi berbeda. Daftar `user` (untuk login offline) tetap seluruh unit dan kini memuat `mandor_hrmb_code`, `afdeling_kode`. HP mengirim `versi` yang tersimpan;
bila sama dengan versi server, jawabannya hanya `{"versi": "...", "berubah": false}`.

**Query** `versi` (opsional): versi master di HP. Kosong berarti HP belum punya master.

**200**

```json
{
  "versi": "m-3f9a1c0d22b7e4aa",
  "berubah": true,
  "unit": { "unit_id": 1, "kode": "KP_STL", "nama": "Kebun Contoh STL", "erp_db": "kp_stl2025", "tahun_cluster": 2026, "aktif": true,
            "dibuat_pada": "2026-11-10T07:00:00Z", "diubah_pada": null, "dihapus_pada": null },
  "divisi":  [ { "divisi_id": 1, "unit_id": 1, "erp_oid": null, "kode": "DIV2", "nama": "Divisi 2 (contoh)", "asisten_user_id": 7, "aktif": true, "...": "..." } ],
  "blok":    [ { "blok_id": 1, "unit_id": 1, "divisi_id": 1, "erp_oid": 1012, "kode": "A12", "nama": null, "luas_ha": 28.5,
                 "tahun_tanam": 2012, "sph": 133, "aktif": true, "...": "..." } ],
  "tph":     [ { "tph_id": 1, "blok_id": 1, "no_tph": "01", "qr_kode": "FAI26|TPH|1|KP_STL|A12|01", "lat": null, "lon": null,
                 "aktif": true, "...": "..." } ],
  "pemanen": [ { "pemanen_id": 1, "unit_id": 1, "erp_hrmb_code": 90001, "kode": "P-001", "nama": "Pemanen Contoh 1", "aktif": true, "...": "..." } ],
  "user":    [ { "user_id": 1, "unit_id": 1, "username": "krani1", "nama": "Krani Contoh", "aktif": true, "erp_user_oid": null,
                 "role": ["KRANI"], "dihapus_pada": null } ],
  "kode_temuan": [ { "kode_id": 1, "kode": "A", "nama": "Potong buah mentah", "kelompok": "BUAH", "urut": 1, "aktif": true, "...": "..." } ],
  "role_permission": [ { "role": "KRANI", "permission": ["grading.ubah", "master.lihat", "panen.buat", "panen.kirim", "panen.lihat", "panen.ubah"] },
                       { "role": "MANDOR", "permission": ["panen.setujui", "..."] } ]
}
```

- Kunci tiap baris sama dengan nama kolom tabel di HP. Waktu ISO 8601 UTC tanpa pecahan detik, boolean `true`/`false`.
- Baris yang dihapus lunak tetap dikirim dengan `dihapus_pada` terisi; HP menandainya, tidak membuangnya.
- Hash password dan data kunci akun **tidak pernah** dikirim. `role` berupa kode role.
- **Versi** dihitung dari isi tabel (jumlah baris dan waktu ubah terbaru). Semua alat yang mengubah master wajib mengisi `diubah_pada`.
- **Hak role** (`role_permission`): hak SEMUA role, bukan hanya unit akun. HP mengganti seluruh hak role-nya dengan isi ini; role yang tidak tercantum berarti tanpa hak, dan kode role atau permission yang tidak dikenal HP dilewati. Bila kunci ini tidak ada, HP tidak mengubah hak role-nya. Perubahan hak role (termasuk hapus lunak, yang wajib mengisi `diubah_pada`) mengubah `versi` sehingga HP mengunduh ulang.
- **IPD:** `kode_temuan` adalah 11 kode temuan inspeksi (A, G, S, M1, M2, M3, M4, B1, B2, C, R) berurutan (`urut`), `kelompok` BUAH, BRONDOLAN, atau PELEPAH. Pilihan mandor dan asisten untuk IPD diambil dari `user` (akun berperan MANDOR dan ASISTEN); `divisi.asisten_user_id` menunjuk asisten tiap divisi (null bila belum diatur).
- Bila `berubah` false, kolom lain kosong; HP tidak perlu berbuat apa-apa.

**Galat:** 401 tanpa token · 403 tanpa permission `master.lihat`.

### POST /api/ipd

Perlu login, permission `ipd.kirim`. Pengirim (inspektur), unit, dan asisten ditentukan server dari token dan divisi, bukan dari isi kiriman. Aman diulang: `ipd_id` yang sudah ada
dan tanpa `kirim_ulang` dijawab 200 `SUDAH_ADA` tanpa perubahan.

| Field | Tipe | Wajib | Keterangan |
|---|---|---|---|
| `ipd_id` | UUID | ya | Dibuat di HP |
| `unit_kode` | string | ya | Harus sama dengan unit akun |
| `tanggal_inspeksi` | `YYYY-MM-DD` | ya | |
| `divisi_kode` | string | ya | Divisi dipilih di awal inspeksi; asisten terisi otomatis dari divisi |
| `asm3_oid` | integer | ya | OID blok di ERP; blok harus milik divisi itu |
| `blok_kode` | string | tidak | Salinan untuk tampilan |
| `pemanen_hrmb_code` | integer | ya | Satu catatan = satu pemanen; harus ada di master |
| `ancak_no` | string | tidak | Maksimal 30 karakter |
| `mandor_username` | string | ya | Kemandoran: satu akun aktif berperan MANDOR |
| `catatan` | string | tidak | Maksimal 500 karakter |
| `dibuat_pada` | ISO 8601 UTC | tidak | |
| `kirim_ulang` | boolean | tidak | `true` mengubah catatan `MENUNGGU` (isi diganti, tetap `MENUNGGU`) atau memperbaiki `DITOLAK` (kembali `MENUNGGU`). Catatan `DISETUJUI` dijawab 423 |
| `pokok` | array | ya | 1 sampai 3000 pokok, `no_baris` dan `no_pokok` 1 sampai 9999, tidak boleh ganda. Tiap pokok boleh memuat `dicatat_pada` (waktu dicatat di HP, ISO 8601 UTC) |
| `jejak` | array | tidak | Jalur GPS inspeksi, maksimal 20000 titik `{"t": "2026-11-20T03:00:00Z", "lat": -2.7, "lon": 104.3, "ak": 6.5}` (`ak` akurasi meter, opsional). HP mencatat tiap 3 meter dan hanya yang akurasinya 25 m atau lebih baik. `kirim_ulang` menggantikan jalur lama seluruhnya. Koordinat harus sah (bukan 0,0) dan waktu valid, kalau tidak 422 |

Tiap `pokok`: `{ "no_baris": 11, "no_pokok": 2, "temuan": [{ "kode": "B1", "jumlah": 6, "catatan": "..." }] }`. `temuan` kosong berarti pokok bersih. `jumlah` kosong dianggap 1 (maksimal 9999),
kode tidak boleh ganda dalam satu pokok.

```json
{
  "ipd_id": "bbbb0001-0000-4000-8000-000000000001", "unit_kode": "KP_STL", "tanggal_inspeksi": "2026-11-20", "divisi_kode": "DIV2",
  "asm3_oid": 1012, "blok_kode": "A12", "pemanen_hrmb_code": 90001, "ancak_no": "7",
  "mandor_username": "mandor1",
  "pokok": [ { "no_baris": 11, "no_pokok": 1, "temuan": [] },
             { "no_baris": 11, "no_pokok": 2, "temuan": [{ "kode": "B1", "jumlah": 6 }, { "kode": "S" }] } ]
}
```

**201** `{"status": "DITERIMA", "id": "..."}` (baru atau diperbarui) · **200** `{"status": "SUDAH_ADA", "id": "..."}`.

**Galat:** 400 JSON rusak · 403 unit berbeda dengan unit akun, tanpa permission, atau `kirim_ulang` oleh orang lain (tanpa `ipd.kelola`) · 409 nomor baris dan pokok ganda ·
**423** `Inspeksi sudah disetujui asisten, tidak bisa diubah lagi` (`kirim_ulang` pada catatan `DISETUJUI`; HP menandainya terkunci dan tidak mengirim ulang) · 422 (pesan menyebut penyebabnya):
`Divisi tidak ada di master` · `Blok tidak ada di master` · `Blok bukan milik divisi ...` · `Pemanen ... tidak ada di master` · `Kode temuan tidak dikenal: ...` ·
`mandor_username bukan akun aktif berperan MANDOR ...` · pokok kosong, ganda, atau di luar batas · jumlah temuan di luar batas · tanggal atau waktu salah format.

### GET /api/admin/ipd/{id}

**Rincian satu inspeksi.** Permission `ipd.lihat`. Seperti satu baris daftar, ditambah `pokok`: semua pokok yang diperiksa berurutan (baris lalu pokok), tiap pokok dengan `temuan` (kosong bila bersih) dan `dicatat_pada` (waktu dicatat di HP, null untuk HP lama).

Bila pemanggil memegang **`ipd.jejak`** (asisten, askep, KTU, admin; mandor tidak) dan HP mengirim jalur GPS, ada `jejak`: `titik` (berurutan menurut waktu), `jarak_m` (jumlah jarak antartitik, haversine), dan `durasi_menit` (titik pertama sampai terakhir). Tanpa hak itu, atau tanpa jalur, `jejak` bernilai `null`.

**Pembukaan jalur dicatat.** Setiap kali `jejak` dikirim (hak ada dan jalur ada), satu baris `audit_log` ditulis lebih dulu: `tabel` = `fai_ipd`, `baris_id` = id inspeksi, `aksi` = `LIHAT_JALUR`, `user_id`, `device_id`, `ip`, dan `keterangan` = jumlah titik yang dikirim (mis. `"182 titik"`). Pembukaan ulang inspeksi yang sama oleh pengguna yang sama dalam 10 menit tidak mencatat baris baru (web memuat ulang rincian berkali-kali). Bila pencatatan gagal, permintaan dijawab 500 dan jalur **tidak** dikirim. Baris ini bisa dicari di `GET /api/admin/audit?aksi=LIHAT_JALUR`. Rincian tanpa jalur (mandor, atau inspeksi tanpa jalur) tidak mencatat apa pun.

**Penyimpanan jalur.** Bila server dijalankan dengan `FAI26_JEJAK_SIMPAN_HARI=N` (N > 0), tugas latar memeriksa tiap 6 jam dan menghapus titik jalur inspeksi yang tanggal inspeksinya lebih dari N hari lalu **dan** statusnya bukan `MENUNGGU` (yang menunggu asisten masih bisa dikirim ulang). Hanya titik di `fai_ipd_jejak` yang dihapus; inspeksi, pokok, temuan, dan foto tetap. Tiap pembersihan yang menghapus sesuatu menulis satu baris audit `HAPUS_JEJAK_LAMA` (`tabel` = `fai_ipd_jejak`, `baris_id` = tanggal batas, `keterangan` = `"120 titik dari 3 inspeksi sebelum 2026-08-24"`). Setelah dihapus, `jejak` bernilai `null`. Bawaan `0` berarti jalur disimpan tanpa batas.

**200**

```json
{ "id": "…", "status": "DISETUJUI", "ringkasan": { "pokok_diperiksa": 6 }, "foto": [],
  "pokok": [{ "no_baris": 10, "no_pokok": 1, "dicatat_pada": "2026-11-20T03:04:10Z", "temuan": [{ "kode": "B1", "jumlah": 5, "catatan": null }] },
            { "no_baris": 11, "no_pokok": 1, "dicatat_pada": null, "temuan": [] }],
  "jejak": { "titik": [{ "t": "2026-11-20T03:00:00Z", "lat": -2.7, "lon": 104.3, "ak": 8 }], "jarak_m": 222.4, "durasi_menit": 10 } }
```

**Galat:** 404 · 422 id bukan UUID

### GET /api/admin/audit

**Cari audit log.** Permission `audit.lihat`. Query: `tabel`, `aksi`, `dari`, `sampai` (`YYYY-MM-DD`), `cari` (username, id baris, atau keterangan), `halaman`, `per_halaman`. Terbaru dulu. Catatan: role ADMIN tidak punya `audit.lihat` (hanya AUDITOR); beri admin kedua role bila perlu.

**200**

```json
{ "item": [{ "audit_id": "…", "tabel": "blok", "baris_id": "3", "aksi": "UBAH", "username": null, "user_id": 30, "waktu": "2026-11-11T03:00:00Z", "keterangan": "A12 (QR berubah: 4)", "…": "…" }], "total": 1, "halaman": 1, "per_halaman": 25 }
```

**Galat:** 422 filter salah format

---


## 7. Riwayat versi

| Versi | Tanggal | Ringkasan |
|---|---|---|
| 0.1.0 sampai 0.1.2 | 10 Nov | Health, auth, panen kirim/status/foto, audit batch; `kelas_kode` kosong menjadi `empty`; batas waktu 20 detik |
| 0.2.0 | 11 Nov | `GET /api/master` dan tabel master |
| 0.3.0 | 11 Nov | `pencatat_username` (kirim atas nama pencatat oleh pemegang `panen.koreksi`) |
| 0.4.0 | 11 Nov | Endpoint web admin: master, pengguna, perangkat, ringkasan, panen, foto, audit |
| 0.5.0 | 11 Nov | `PUT /api/tph/{id}/koordinat` |
| 0.6.0 | 11 Nov | `role_permission` di master; versi master ikut hak role |
| 0.7.0 | 11 Nov | Ganti password sendiri; batas laju per IP |
| 0.8.0 | 11 Nov | Hak akses per role dari web |
| 0.9.0 | 17 Nov | `GET /api/panen/ringkasan` (dashboard HP) |
| 0.10.0 | 18 Nov | **Satu TPH satu pemanen** (pemanen wajib tepat satu) |
| 0.11.0 | 18 Nov | **Mandor yang menginput**; profil mandor; master per mandor; 409 anti ganda |
| 0.12.0 | 19 Nov | **Persetujuan krani lewat server** (`MENUNGGU`, setujui/tolak) |
| 0.13.0 | 19 Nov | `GET /api/admin/produktivitas` |
| 0.14.0 / 0.14.1 | 20 Nov | `foto_id` pada daftar menunggu; ringkasan UNIT untuk krani |
| 0.15.0 | 20 Nov | `kirim_ulang` untuk catatan `MENUNGGU` (edit sebelum disetujui) |
| 0.16.0 | 21 Nov | **Modul IPD** (kirim, status, menunggu, setujui/tolak, foto, daftar dan laporan web) |
| 0.17.0 | 21 Nov | Loses dipisah dari temuan lain |
| 0.18.0 | 20 Nov | IPD satu kemandoran; role buatan admin |
| 0.19.0 | 21 Nov | Foto profil akun |
| 0.20.0 | 21 Nov | **Jalur GPS inspeksi IPD** |
| 0.20.1 | 22 Nov | Filter `status_erp=MENUNGGU` diterima |
| 0.21.0 | 22 Nov | Audit `LIHAT_JALUR`; penyimpanan jalur dapat diatur (`FAI26_JEJAK_SIMPAN_HARI`, audit `HAPUS_JEJAK_LAMA`) |
