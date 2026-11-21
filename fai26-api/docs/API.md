# Dokumentasi API fai26-api

Versi 0.19.0 · terakhir diperbarui 21 Nov 2026 · spesifikasi mesin: [`openapi.yaml`](openapi.yaml)
(bisa dibuka di Swagger Editor, Postman, atau Insomnia).

> **Aturan tim:** setiap endpoint baru atau perubahan bentuk data **wajib** memperbarui `openapi.yaml`, dokumen ini,
> dan bagian [Riwayat perubahan](#riwayat-perubahan) di bawah. Tes `TestSemuaRuteTerdokumentasi` gagal bila ada
> rute yang belum tercatat di keduanya.

## Daftar endpoint

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

## Aturan umum

- **Format:** JSON UTF-8. Kecuali unggah foto (byte JPEG mentah).
- **Waktu:** ISO 8601 UTC, contoh `2026-11-10T03:00:00Z`. Tanggal panen: `YYYY-MM-DD`.
- **Autentikasi:** header `Authorization: Bearer <access_token>`. Token akses berlaku 15 menit; perbaharui dengan
  `/api/auth/refresh`. Refresh token berlaku 30 hari dan **diganti setiap dipakai**.
- **Hak akses** dibaca ulang dari database di setiap panggilan, jadi akun atau perangkat yang dinonaktifkan langsung
  tidak bisa dipakai walau token masih berlaku.
- **Identitas dari token:** pencatat (`dibuat_oleh`), perangkat, dan `user_id` audit selalu diambil dari token,
  bukan dari isi kiriman.
- **Idempoten:** mengirim ulang `panen_id`, `X-Foto-Id`, atau `audit_id` yang sama tidak menggandakan data.
- **Batas waktu:** setiap permintaan dibatasi 20 detik. Bila database lambat, jawabannya 503 (sementara); HP mencoba lagi nanti.
- **Batas laju:** per alamat IP, login dan refresh maksimal 30 permintaan per menit dan seluruh API 600 per menit
  (bawaan; diatur server lewat `FAI26_BATAS_LOGIN` dan `FAI26_BATAS_UMUM`, 0 berarti tanpa batas). Melebihi batas dijawab
  429 dengan header `Retry-After` (detik). Aman diulang: HP menganggap 429 sebagai kegagalan sementara.
- **Galat:** selalu `{"error": "pesan"}`. Kesalahan server (5xx) hanya berisi pesan umum; rincian ada di log server.

### Kode status

| Kode | Arti |
|---|---|
| 200 | Berhasil (atau sudah pernah diterima: `SUDAH_ADA`) |
| 201 | Data baru dibuat |
| 400 | Isi permintaan tidak bisa dibaca atau parameter wajib kurang |
| 401 | Token tidak ada, rusak, kedaluwarsa, atau login salah |
| 403 | Akun/perangkat nonaktif, atau tidak punya permission |
| 404 | Data tidak ada (misalnya foto untuk catatan yang belum dikirim) |
| 413 | Terlalu besar (foto > 8 MB, audit > 500 baris) |
| 415 | Bukan JPEG |
| 422 | Isi terbaca tetapi tidak lolos aturan (lihat pesan) |
| 423 | Akun terkunci sementara |
| 429 | Terlalu banyak permintaan dari alamat ini (batas laju); tunggu sesuai header `Retry-After` |
| 500 | Kesalahan server |
| 503 | Database lambat atau tidak terjangkau (sementara; aman diulang) |

## Alur yang dipakai HP

```
1. POST /api/auth/login            → simpan access_token, refresh_token, profil
   GET  /api/master?versi=...      → unduh master bila versinya berubah (blok, TPH, pemanen, akun)
2. (bekerja offline, mandor menyetujui di HP; titik GPS TPH yang direkam di HP ikut antrean)
   PUT  /api/tph/{id}/koordinat    → kirim titik TPH yang direkam di lokasi (hanya bila server belum punya)
3. POST /api/panen                 → catatan disetujui; 201 DITERIMA atau 200 SUDAH_ADA
4. POST /api/panen/{id}/media      → foto, setelah catatan terkirim
5. POST /api/audit/batch           → audit_log yang belum terkirim
6. GET  /api/panen?sejak=...       → perbarui status: TERKIRIM → DITERIMA / DITOLAK
   Bila token akses habis (401)    → POST /api/auth/refresh, lalu ulangi
```

---

## GET /api/health

Tanpa login. **200** `{"status": "ok"}` · **503** `{"error": "Database tidak terjangkau"}`.

## POST /api/auth/login

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

## POST /api/auth/refresh

Tanpa login. **Body** `{"refresh_token": "..."}`. **200** `{access_token, access_expires, refresh_token}`;
simpan `refresh_token` yang baru, yang lama langsung tidak berlaku.
**Galat:** 400 token kosong · 401 `Sesi tidak berlaku. Login ulang` (dicabut, kedaluwarsa, atau akun/perangkat nonaktif).

## POST /api/auth/logout

Perlu login. Tanpa body. Mencabut sesi perangkat ini. **200** `{"status": "keluar"}`.

## GET /api/auth/me

Perlu login. **200** objek `profil` seperti pada login, dengan role dan permission terbaru dari database.

## PUT /api/auth/foto

Perlu login (tanpa permission khusus). Mengganti **foto profil milik sendiri**. Isi permintaan adalah berkas gambar mentah (`Content-Type: image/jpeg` atau `image/png`), maksimal 6 MB, minimal 64 x 64 piksel.
Server memotongnya persegi dari tengah, mengecilkannya ke 384 x 384, dan menyimpan ulang sebagai JPEG (metadata foto asli seperti lokasi tidak ikut tersimpan). Tercatat di audit log.

**200** `{"foto_versi": 1790000000000}` (waktu ubah, milidetik). **Galat:** 401 · 422 bukan JPEG/PNG, rusak, terlalu kecil, kosong, atau lebih dari 6 MB.

## GET /api/auth/foto

Perlu login. Mengembalikan foto profil sendiri (JPEG 384 x 384). Header `ETag` berisi versi foto; kirim `If-None-Match` untuk mendapat **304** bila belum berubah. **404** bila belum punya foto.
`foto_versi` juga ada di objek profil (`/api/auth/login`, `/api/auth/me`), jadi klien cukup membandingkan versi lalu mengunduh bila berbeda.

## DELETE /api/auth/foto

Perlu login. Menghapus foto profil sendiri. **200** `{"status": "OK"}`; aman dipanggil berulang.

## POST /api/auth/password

Perlu login (tanpa permission khusus). Mengganti password milik sendiri.

```json
{ "password_lama": "krani123", "password_baru": "PasswordBaru#2026" }
```

**200** `{"status": "OK"}`. Setelah berhasil: kewajiban ganti password (`wajib_ganti_password`) dihapus, hitungan gagal direset,
dan sesi di **semua perangkat lain** dicabut (perangkat yang dipakai tetap masuk; HP lain harus login ulang).

- Password baru 8 sampai 72 karakter dan harus berbeda dari yang lama.
- Password lama salah dijawab **422** (bukan 401, supaya klien tidak mengira token-nya habis) dan dihitung seperti gagal login:
  lima kali berturut-turut mengunci akun 15 menit (423).
- Tercatat di audit log (`GANTI_PASSWORD`, `GANTI_PASSWORD_GAGAL`).

**Galat:** 400 `password_lama` kosong · 401 tanpa token · 403 akun nonaktif · 422 password lama salah atau password baru tidak memenuhi aturan · 423 akun terkunci · 429 batas laju.

## POST /api/panen

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

## GET /api/panen

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

## GET /api/panen/menunggu

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

## POST /api/panen/{id}/setujui

Perlu login, permission `panen.setujui`. Tanpa body. Catatan `MENUNGGU` di unit pengguna menjadi `BARU` (siap ditarik HVDM), tercatat atas nama penyetuju
(`disetujui_oleh`, `disetujui_pada`) dan di `audit_log` (`SETUJU`). HP pencatat melihat statusnya berubah dari "menunggu krani" menjadi `TERKIRIM`.

**200** `{"status": "DISETUJUI"}` · **Galat:** 404 catatan tidak ada di unit ini · 409 `Catatan sudah diproses (status ...)` (sudah disetujui, ditolak, atau diimpor) · 422 `id` bukan UUID.

## POST /api/panen/{id}/tolak

Perlu login, permission `panen.setujui`. Mengembalikan catatan `MENUNGGU` ke pencatat. Catatan menjadi `DITOLAK` dan alasannya tampil di HP pencatat
sebagai `Ditolak krani: <alasan>`; pencatat bisa memperbaikinya lalu mengirim ulang (`kirim_ulang=true`, `panen_id` sama), dan catatan kembali `MENUNGGU`.

**Body** `{"alasan": "Blok salah"}` (wajib, maksimal 300 karakter).

**200** `{"status": "DITOLAK"}` · **Galat:** 404 · 409 (bukan lagi `MENUNGGU`) · 422 alasan kosong atau terlalu panjang.

## GET /api/panen/ringkasan

Perlu login, permission `panen.lihat`. Mengembalikan ringkasan produksi nyata di database server untuk dashboard Beranda HP:
catatan dan janjang hari ini, rincian per kelas kematangan, 5 blok teratas hari ini, tren 7 hari terakhir, dan jumlah catatan per status.
Pengguna biasa (`lingkup: "SENDIRI"`) melihat catatannya sendiri; pemegang `panen.koreksi` (mandor, asisten) **atau `panen.setujui` (krani, yang memeriksa catatan mandor)** (`lingkup: "UNIT"`) melihat seluruh catatan di unitnya.

**Query** `tanggal` (opsional, `YYYY-MM-DD`): tanggal sasaran; kosong berarti hari ini menurut WIB.

**200**

```json
{
  "tanggal": "2026-11-17",
  "lingkup": "UNIT",
  "catatan_hari_ini": 8,
  "janjang_hari_ini": 164,
  "per_kelas": [
    { "kelas_kode": "ripe", "janjang": 140 },
    { "kelas_kode": "underripe", "janjang": 16 },
    { "kelas_kode": "overripe", "janjang": 8 }
  ],
  "per_blok": [
    { "blok_kode": "A12", "tph": 4, "catatan": 5, "janjang": 110 }
  ],
  "tren_7_hari": [
    { "tanggal": "2026-11-17", "catatan": 8, "janjang": 164 }
  ],
  "catatan_per_status": { "TERKIRIM": 2, "DITERIMA": 6, "DITOLAK": 0 }
}
```

**Galat:** 400 `tanggal` salah format.

## POST /api/panen/{id}/media

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

## POST /api/audit/batch

Perlu login. Kirim `audit_log` dari HP, maksimal 500 baris. **Body** berupa array:

```json
[
  { "audit_id": "ffffffff-ffff-4fff-8fff-ffffffffff01", "tabel": "panen_header",
    "baris_id": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", "aksi": "BUAT",
    "nilai_lama": null, "nilai_baru": "{\"status\":\"DRAFT\"}", "username": "krani1",
    "waktu": "2026-11-10T01:00:00Z", "keterangan": "" }
]
```

**200** `{"diterima": 1, "dilewati": 0}`. Baris dengan `audit_id` yang sudah ada dilewati. `user_id` dan `device_id` dari token.
**Galat:** 413 lebih dari 500 baris · 422 baris tidak lengkap, `audit_id` bukan UUID, atau waktu salah format.

## GET /api/master

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

## PUT /api/tph/{id}/koordinat

Perlu login, permission `panen.kirim`. HP mengirim titik GPS TPH yang direkam di lokasi. Server **hanya mengisi bila
TPH belum punya koordinat**, supaya titik yang sudah benar tidak tertimpa titik asal-asalan (mengubah titik yang sudah
ada lewat web admin, `PUT /api/admin/tph/{id}`). `{id}` adalah `tph_id` dari `GET /api/master`.

**Body**

```json
{ "lat": 3.123456, "lon": 101.654321, "akurasi_m": 8.5 }
```

**200** `{"diterapkan": true, "lat": 3.123456, "lon": 101.654321}` bila titik disimpan.
Bila TPH sudah punya koordinat: `{"diterapkan": false, "lat": ..., "lon": ...}` berisi titik **milik server**, dan HP
wajib memakainya (menimpa titiknya sendiri). Mengirim titik yang sama dua kali aman (hasilnya `diterapkan: false`).

- Berhasil mengisi mengubah `diubah_pada` TPH, jadi versi master berubah dan HP lain mengunduh titik itu. Tercatat di audit log.
- TPH harus milik unit akun dan belum dihapus.

**Galat:** 401 tanpa token · 403 tanpa `panen.kirim` · 404 TPH tidak ada atau bukan milik unit · 422 `lat` di luar -90..90,
`lon` di luar -180..180, titik tepat (0, 0), `akurasi_m` kosong atau di atas 50, atau ada kolom yang tidak dikirim.

## IPD: Inspeksi Panen Detail

IPD mencatat **loses** (kehilangan hasil) di titik panen. Satu catatan = satu pemanen (satu ancak): inspektur memeriksa pokok per pokok (nomor baris + nomor pokok)
dan mencatat temuan dengan kode **A, G, S, M1, M2, M3, M4, B1, B2, C, R** (master `kode_temuan` di `GET /api/master`). Pokok yang bersih juga dicatat supaya persen loses
bisa dihitung. Alur: inspektur mengirim (otomatis valid) → status `MENUNGGU` → **asisten divisi yang tercatat** menyetujui atau menolak. Pengirim yang memegang
`ipd.setujui` (asisten) langsung `DISETUJUI`. Setelah `DISETUJUI` catatan **terkunci**. Selama `MENUNGGU` (atau `DITOLAK`) pengirim boleh mengubahnya dengan `kirim_ulang=true`.
Skema: `erd/fAI26_ERD_Database_Mobile.md` bab 13.

Asisten tiap divisi diatur admin (`PUT /api/admin/divisi/{id}`, `asisten_user_id`); saat diganti, inspeksi divisi itu yang masih `MENUNGGU` pindah ke asisten baru. Bila divisi
belum punya asisten, semua pemegang `ipd.setujui` di unit itu melihatnya.

## POST /api/ipd

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
| `pokok` | array | ya | 1 sampai 3000 pokok, `no_baris` dan `no_pokok` 1 sampai 9999, tidak boleh ganda |

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

## GET /api/ipd

Perlu login, permission `ipd.buat`. Status inspeksi **milik pengguna** sejak waktu tertentu (untuk HP pengirim).

**Query** `sejak` (opsional, ISO 8601 UTC).

**200**

```json
[{ "id": "bbbb0001-0000-4000-8000-000000000001", "status": "MENUNGGU_SETUJU", "status_server": "MENUNGGU", "pesan": null, "diubah": "2026-11-20T03:00:00Z" }]
```

Pemetaan `status_server` → `status` untuk HP: `MENUNGGU` → `MENUNGGU_SETUJU`, `DISETUJUI` → `DISETUJUI`, `DITOLAK` → `DITOLAK` (`pesan` berisi alasan asisten).

## GET /api/ipd/inspeksi-terakhir

Perlu login, permission `ipd.buat`. **Peringatan, bukan larangan:** adakah inspeksi (selain yang `DITOLAK`) untuk pemanen + blok yang sama dalam **7 hari** sampai `tanggal`.
Inspeksi ulang tetap boleh; HP menampilkan tanggal, inspektur, dan statusnya lalu inspektur boleh lanjut.

**Query:** `pemanen_hrmb_code` (wajib), `asm3_oid` (wajib), `tanggal` (`YYYY-MM-DD`, kosong = hari ini WIB), `kecuali` (UUID inspeksi yang sedang diedit, dikecualikan).

**200**

```json
{ "jendela_hari": 7, "ada": true, "inspeksi": [{ "id": "...", "tanggal_inspeksi": "2026-11-13", "inspektur": "mandor1", "inspektur_nama": "Mandor Satu", "status": "DISETUJUI" }] }
```

**Galat:** 400 parameter wajib kosong, tanggal salah, atau `kecuali` bukan UUID.

## GET /api/ipd/menunggu

Perlu login, permission `ipd.setujui` (asisten). Inspeksi `MENUNGGU` yang **ditujukan ke pengguna** (asisten yang tercatat), atau yang divisinya belum punya asisten, terlama dulu.

**200**

```json
[{
  "id": "bbbb0001-0000-4000-8000-000000000001", "tanggal_inspeksi": "2026-11-20", "divisi_kode": "DIV2", "blok_kode": "A12",
  "pemanen_hrmb_code": 90001, "pemanen_nama": "Pemanen Contoh 1", "ancak_no": "7", "inspektur": "mandor1", "inspektur_nama": "Mandor Contoh",
  "mandor_nama": "Mandor Contoh", "asisten_nama": "Asisten Satu", "status": "MENUNGGU",
  "pesan_tolak": null, "catatan": null, "dibuat_pada": "2026-11-20T03:00:00Z", "disetujui_pada": null,
  "ringkasan": { "pokok_diperiksa": 5, "pokok_bertemuan": 3, "persen_pokok_bertemuan": 60, "total_temuan": 13,
                 "per_kode": [{ "kode": "A", "nama": "Potong buah mentah", "kelompok": "BUAH", "pokok": 1, "jumlah": 1, "persen_pokok": 20 }] },
  "foto": [{ "id": "eeee0001-...", "keterangan": "buah tinggal di baris 11", "urut": 0 }]
}]
```

`ringkasan.per_kode` memuat semua kode aktif (nol bila tidak ada temuan), tiap kode dengan `jenis`. `persen_*` = pokok dibagi pokok diperiksa (1 desimal). Foto diunduh dengan `GET /api/ipd/foto/{id}`.

**Loses dan temuan lain.** Hanya temuan yang berkaitan dengan buah dan brondolan (kelompok `BUAH` dan `BRONDOLAN`: A, G, S, M1, M2, M3, M4, B1, B2) yang dihitung sebagai **loses** (kehilangan hasil, `jenis: "LOSES"`). Kerusakan atau pekerjaan yang tidak memengaruhi produksi (kelompok `PELEPAH`: C pelepah sengkleh, R pelepah tidak disusun) adalah **temuan lain** (`jenis: "TEMUAN"`) dan tidak masuk loses. Ringkasan memuat keduanya terpisah: `pokok_loses`, `persen_pokok_loses`, `total_loses`, `pokok_temuan_lain`, `persen_pokok_temuan_lain`, `total_temuan_lain`; `pokok_bertemuan` dan `total_temuan` tetap berarti temuan apa pun.

## POST /api/ipd/{id}/setujui

Perlu login, permission `ipd.setujui`. Tanpa body. Inspeksi `MENUNGGU` yang ditujukan ke pengguna menjadi `DISETUJUI` (tercatat `disetujui_oleh`, `disetujui_pada`, audit `SETUJU`) dan **terkunci**.

**200** `{"status": "DISETUJUI"}` · **Galat:** 403 `Inspeksi ini ditujukan ke asisten lain` · 404 · 409 `Inspeksi sudah diproses (status ...)` · 422 `id` bukan UUID.

## POST /api/ipd/{id}/tolak

Perlu login, permission `ipd.setujui`. Mengembalikan inspeksi `MENUNGGU` ke inspektur: status `DITOLAK`, alasan tampil di HP-nya (`pesan`), lalu dapat diperbaiki dan dikirim ulang (`kirim_ulang=true`).

**Body** `{"alasan": "Baris 10 belum lengkap"}` (wajib, maksimal 300 karakter).

**200** `{"status": "DITOLAK"}` · **Galat:** 403 · 404 · 409 · 422 alasan kosong atau terlalu panjang.

## POST /api/ipd/{id}/media

Perlu login, permission `ipd.kirim`. Unggah satu foto JPEG bukti inspeksi; tidak ada batas jumlah foto per inspeksi. Body mentah JPEG (maksimal 8 MB), header `X-Foto-Id` (UUID) dan `X-Sha256` (64 hex),
query `keterangan` (opsional, maksimal 200 karakter) dan `urut` (opsional, 0 sampai 9999). Idempoten menurut `X-Foto-Id`. Hanya pemilik inspeksi (atau pemegang `ipd.kelola`). HP boleh menghapus
berkas fotonya setelah jawaban 201 atau 200.

**201** `{"status": "DITERIMA", "id": "..."}` · **200** `SUDAH_ADA` · **Galat:** 400 header atau parameter salah · 403 bukan inspeksi Anda · 404 inspeksi belum dikirim · 413 lebih dari 8 MB · 415 bukan JPEG · 422 checksum tidak cocok.

## GET /api/ipd/foto/{id}

Perlu login, permission `ipd.lihat`. Mengembalikan byte JPEG foto bukti inspeksi di unit pengguna (token di header `Authorization`).

**200** byte JPEG. **Galat:** 404 foto atau berkasnya tidak ada · 422 id bukan UUID.

## Web admin (`/api/admin/*`)

Dipakai web admin (folder `fai26-web/`). Semua perlu login dan permission yang tertulis; hasilnya selalu terbatas pada **unit milik akun**. Penulisan master (`POST`, `PUT`, `DELETE`) mengisi `diubah_pada` sehingga versi master untuk HP ikut berubah, menulis `audit_log`, dan penghapusan selalu lunak. `PUT` menggantikan semua kolom yang bisa diubah: kolom yang tidak dikirim menjadi kosong (kecuali `aktif`). Kode galat khusus: **409** bentrok (kode ganda atau masih dipakai), **422** masukan tidak sah.

## GET /api/admin/divisi

**Daftar divisi.** Permission `master.lihat`. Divisi unit pengguna yang belum dihapus, beserta `jumlah_blok` dan asistennya (`asisten_user_id`, `asisten_nama`; null bila belum diatur).

**200**

```json
[{ "divisi_id": 1, "kode": "DIV2", "nama": "Divisi 2", "erp_oid": null, "aktif": true, "asisten_user_id": 7, "asisten_nama": "Asisten Satu", "jumlah_blok": 2 }]
```

## POST /api/admin/divisi

**Buat divisi.** Permission `master.kelola`. Kode unik per unit.

**Body:** kode (wajib, ≤20, tanpa `|`), nama (wajib, ≤100), erp_oid (opsional), aktif (opsional), **asisten_user_id** (opsional; akun aktif berperan ASISTEN di unit ini, null berarti belum diatur)

```json
{ "kode": "DIV2", "nama": "Divisi 2", "erp_oid": null, "aktif": true, "asisten_user_id": 7 }
```

**201**

```json
{ "id": 3 }
```

**Galat:** 409 kode sudah dipakai · 422 kode kosong atau berisi `|`, atau asisten tidak sah

## PUT /api/admin/divisi/{id}

**Ubah divisi.** Permission `master.kelola`. Menggantikan semua kolom yang bisa diubah (`aktif` kosong berarti tidak diubah).

**Body:** kode (wajib, ≤20, tanpa `|`), nama (wajib, ≤100), erp_oid (opsional), aktif (opsional), **asisten_user_id** (opsional; akun aktif berperan ASISTEN di unit ini, null berarti belum diatur)

```json
{ "kode": "DIV2", "nama": "Divisi 2", "erp_oid": null, "aktif": true, "asisten_user_id": 7 }
```

**200**

```json
{ "status": "OK" }
```

**Asisten divisi** dipakai IPD untuk mengisi asisten otomatis dan menentukan siapa yang menyetujui. Saat `asisten_user_id` diganti (atau dikosongkan), inspeksi IPD divisi ini yang masih `MENUNGGU` ikut pindah ke asisten baru. Seperti kolom lain, PUT menggantikan semuanya: `asisten_user_id` yang tidak dikirim berarti dikosongkan.

**Galat:** 404 tidak ada · 409 kode sudah dipakai · 422 (termasuk `asisten harus akun aktif berperan ASISTEN di unit ini`)

## DELETE /api/admin/divisi/{id}

**Hapus divisi (lunak).** Permission `master.kelola`. Ditolak bila masih ada blok di dalamnya.

**200**

```json
{ "status": "OK" }
```

**Galat:** 404 · 409 masih dipakai blok

## GET /api/admin/blok

**Daftar blok.** Permission `master.lihat`. Blok beserta `divisi_kode` dan `jumlah_tph`.

**200**

```json
[{ "blok_id": 1, "divisi_id": 1, "divisi_kode": "DIV2", "erp_oid": 1012, "kode": "A12", "nama": null, "luas_ha": 28.5, "tahun_tanam": 2012, "sph": 133, "aktif": true, "jumlah_tph": 3 }]
```

## POST /api/admin/blok

**Buat blok.** Permission `master.kelola`. `erp_oid` (OID blok di ERP) wajib. Kode unik per unit dan tanpa karakter `|`.

**Body:** kode (wajib, ≤20, tanpa `|`), erp_oid (wajib, > 0), divisi_id, nama, luas_ha ≥ 0, tahun_tanam 1900–2100, sph ≥ 0, aktif (semua opsional)

```json
{ "kode": "A12", "erp_oid": 1012, "divisi_id": 1, "luas_ha": 28.5, "tahun_tanam": 2012, "sph": 133 }
```

**201**

```json
{ "id": 3 }
```

**Galat:** 409 kode sudah dipakai · 422 divisi tidak ada atau masukan tidak sah

## PUT /api/admin/blok/{id}

**Ubah blok.** Permission `master.kelola`. Bila `kode` berubah, `qr_kode` semua TPH di blok ini ikut dibuat ulang (isi QR memuat kode blok) dan **QR lama tidak berlaku lagi**. Jawaban memberi `qr_berubah`, jumlah QR yang berubah.

**Body:** kode (wajib, ≤20, tanpa `|`), erp_oid (wajib, > 0), divisi_id, nama, luas_ha ≥ 0, tahun_tanam 1900–2100, sph ≥ 0, aktif (semua opsional)

```json
{ "kode": "A12", "erp_oid": 1012, "divisi_id": 1, "luas_ha": 28.5, "tahun_tanam": 2012, "sph": 133 }
```

**200**

```json
{ "status": "OK", "qr_berubah": 4 }
```

**Galat:** 404 · 409 · 422

## DELETE /api/admin/blok/{id}

**Hapus blok (lunak).** Permission `master.kelola`. Ditolak bila masih ada TPH di dalamnya.

**200**

```json
{ "status": "OK" }
```

**Galat:** 404 · 409 masih ada TPH

## GET /api/admin/tph

**Daftar TPH.** Permission `master.lihat`. Query `blok_id` (opsional) menyaring satu blok. Berisi `qr_kode`, `lat`, `lon`.

**200**

```json
[{ "tph_id": 1, "blok_id": 1, "blok_kode": "A12", "no_tph": "01", "qr_kode": "FAI26|TPH|1|KP_STL|A12|01", "lat": null, "lon": null, "aktif": true }]
```

## POST /api/admin/tph

**Buat TPH.** Permission `master.kelola`. `qr_kode` dibuat server: `FAI26|TPH|1|<unit>|<blok>|<no>` (lihat `progres/SPEK_QR_TPH.md`). `lat` dan `lon` harus diisi bersamaan atau dikosongkan bersamaan.

**Body:** blok_id (wajib), no_tph (wajib, ≤10, tanpa `|`), lat dan lon (bersamaan atau kosong bersamaan), aktif (opsional)

```json
{ "blok_id": 1, "no_tph": "01", "lat": -2.0, "lon": 113.0 }
```

**201**

```json
{ "id": 9 }
```

**Galat:** 409 QR yang sama sudah ada · 422 blok tidak ada, nomor berisi `|`, atau koordinat di luar rentang

## POST /api/admin/tph/massal

**Buat TPH berurutan.** Permission `master.kelola`. Membuat nomor `dari` sampai `sampai` (maksimal 500) dengan lebar nol di depan, contoh 1 sampai 20 lebar 2 menjadi `01` sampai `20`. **Semua atau tidak sama sekali**: satu bentrok membatalkan semuanya.

**Body:** blok_id, dari (≥1), sampai (≥ dari, selisih ≤ 500), lebar (1–4, cukup untuk nomor terbesar)

```json
{ "blok_id": 1, "dari": 1, "sampai": 20, "lebar": 2 }
```

**201**

```json
{ "dibuat": 3, "id": [10, 11, 12] }
```

**Galat:** 409 ada nomor yang sudah ada (tidak ada yang dibuat) · 422 rentang atau lebar tidak sah

## PUT /api/admin/tph/{id}

**Ubah TPH.** Permission `master.kelola`. Mengganti blok atau nomor membuat `qr_kode` baru dan QR lama tidak berlaku (`qr_berubah` true). Mengubah koordinat atau `aktif` saja tidak mengubah QR. Koordinat yang tidak dikirim menjadi kosong.

**Body:** blok_id (wajib), no_tph (wajib, ≤10, tanpa `|`), lat dan lon (bersamaan atau kosong bersamaan), aktif (opsional)

```json
{ "blok_id": 1, "no_tph": "01", "lat": -2.0, "lon": 113.0 }
```

**200**

```json
{ "status": "OK", "qr_berubah": false }
```

**Galat:** 404 · 409 · 422

## DELETE /api/admin/tph/{id}

**Hapus TPH (lunak).** Permission `master.kelola`. Baris tetap ada untuk catatan lama dan HP menandainya terhapus.

**200**

```json
{ "status": "OK" }
```

**Galat:** 404

## GET /api/admin/pemanen

**Daftar pemanen.** Permission `master.lihat`. Pemanen unit pengguna yang belum dihapus.

**200**

```json
[{ "pemanen_id": 1, "erp_hrmb_code": 90001, "mandor_hrmb_code": 7001, "kode": "P-001", "nama": "Pemanen Contoh 1", "aktif": true }]
```

## POST /api/admin/pemanen

**Buat pemanen.** Permission `master.kelola`. `erp_hrmb_code` (kode di ERP) wajib dan unik per unit.

**Body:** erp_hrmb_code (wajib, > 0), nama (wajib, ≤100), mandor_hrmb_code (opsional, > 0; kode HRMB mandor kelompok, untuk header HVDM), kode (opsional, ≤20), aktif (opsional)

```json
{ "erp_hrmb_code": 90001, "mandor_hrmb_code": 7001, "kode": "P-001", "nama": "Pemanen Contoh 1" }
```

**201**

```json
{ "id": 6 }
```

**Galat:** 409 kode ERP sudah dipakai · 422

## PUT /api/admin/pemanen/{id}

**Ubah pemanen.** Permission `master.kelola`. 

**Body:** erp_hrmb_code (wajib, > 0), nama (wajib, ≤100), mandor_hrmb_code (opsional, > 0; kode HRMB mandor kelompok, untuk header HVDM), kode (opsional, ≤20), aktif (opsional)

```json
{ "erp_hrmb_code": 90001, "mandor_hrmb_code": 7001, "kode": "P-001", "nama": "Pemanen Contoh 1" }
```

**200**

```json
{ "status": "OK" }
```

**Galat:** 404 · 409 · 422

## DELETE /api/admin/pemanen/{id}

**Hapus pemanen (lunak).** Permission `master.kelola`. 

**200**

```json
{ "status": "OK" }
```

**Galat:** 404

## GET /api/admin/peran

**Daftar role.** Permission `user.kelola`. Role yang bisa diberikan ke pengguna.

**200**

```json
[{ "role_id": 1, "kode": "KRANI", "nama": "Krani", "deskripsi": "Mencatat dan memotret di TPH" }]
```

## GET /api/admin/hak-akses

Permission `role.kelola`. Seluruh role beserta kode permission yang dipegangnya, dan katalog semua permission (untuk layar
pengelolaan hak akses).

```json
{
  "role": [ { "role_id": 1, "kode": "KRANI", "nama": "Krani", "deskripsi": "Mencatat dan memotret di TPH", "bawaan": true, "pengguna": 3,
              "permission": ["panen.buat", "panen.kirim", "panen.lihat"] } ],
  "permission": [ { "kode": "panen.setujui", "modul": "Panen", "nama": "Menyetujui catatan panen krani" } ]
}
```

## POST /api/admin/hak-akses

**Membuat role baru.** Permission `role.kelola`. Admin bisa membuat role sendiri (mis. `KEPALA_KEBUN`) lalu menentukan hak-nya; role itu bisa
dipasang ke akun lewat halaman Pengguna dan ikut terunduh ke HP lewat `GET /api/master` (hak `akses.panen`/`akses.ipd` menentukan kotak di layar awal HP).

```json
{ "kode": "kepala_kebun", "nama": "Kepala Kebun", "deskripsi": "Melihat laporan", "permission": ["laporan.lihat", "ipd.lihat", "laporan.ipd"] }
```

| Field | Aturan |
|---|---|
| `kode` | wajib; 2 sampai 30 karakter, huruf besar, angka, garis bawah, diawali huruf (huruf kecil dijadikan besar); tidak bisa diubah dan tidak bisa dipakai ulang |
| `nama` | wajib; 2 sampai 60 karakter |
| `deskripsi` | opsional; maksimal 200 karakter |
| `permission` | opsional; hak awal (kode dari katalog) |

**201** `{"id": 8}`. Dibuat dalam satu transaksi (gagal berarti tidak ada role setengah jadi) dan tercatat di audit log.
**Galat:** 403 tanpa `role.kelola` · 409 kode sudah dipakai · 422 bentuk salah atau kode permission tidak dikenal.

## DELETE /api/admin/hak-akses/{id}

**Menghapus role buatan admin.** Permission `role.kelola`. Hapus lunak role dan pasangan haknya; tercatat di audit log. **200** `{"status": "OK"}`.
**Galat:** 404 role tidak ada · 409 role bawaan sistem, atau masih dipegang akun aktif (pindahkan akun itu ke role lain dulu).

## PUT /api/admin/hak-akses/{id}

Permission `role.kelola`. Mengganti **seluruh** hak role dengan daftar yang dikirim (daftar kosong berarti tanpa hak). `nama` dan `deskripsi`
boleh ikut dikirim untuk mengganti nama dan keterangan role (kode tetap).

```json
{ "permission": ["panen.lihat", "panen.buat", "panen.kirim", "laporan.lihat"], "nama": "Krani Panen" }
```

**200** `{"status": "OK"}`. Hak yang tidak dicantumkan dihapus lunak; yang baru ditambahkan atau dihidupkan lagi.

- Berlaku **langsung** di server (hak dibaca ulang tiap panggilan). HP mengikuti lewat `GET /api/master` (`role_permission`):
  versi master berubah, dan HP memperbarui hak pengguna yang sedang login tanpa login ulang.
- Tercatat di audit log (`role_permission`, `UBAH`, keterangan rincian tambah dan cabut). Tanpa perubahan nyata: 200 tanpa
  mengubah versi master atau menulis audit.
- **Pengaman:** role `ADMIN` tidak boleh kehilangan `role.kelola` (409), supaya tidak ada yang terkunci dari pengelolaan hak akses.

**Galat:** 403 tanpa `role.kelola` · 404 role tidak ada · 409 mencabut `role.kelola` dari ADMIN · 422 `permission` tidak dikirim
atau ada kode yang tidak dikenal.

## GET /api/admin/pengguna

**Daftar pengguna.** Permission `user.kelola`. Pengguna unit beserta role, login terakhir, dan status kunci.

**200**

```json
[{ "user_id": 1, "username": "krani1", "nama": "Krani Contoh", "aktif": true, "role": ["KRANI"], "login_terakhir": "2026-11-11T03:00:00Z", "terkunci": false, "wajib_ganti_password": false, "mandor_hrmb_code": null, "afdeling_kode": null }]
```

## POST /api/admin/pengguna

**Buat pengguna.** Permission `user.kelola`. Username 3 sampai 50 karakter (huruf kecil, angka, titik, garis bawah, strip). Password minimal 8 karakter dan disimpan sebagai hash bcrypt. Pengguna baru **wajib ganti password**.

**Body:** username (hanya saat membuat), nama (wajib), password (hanya saat membuat, ≥8), role (1–5 kode role), aktif (opsional), mandor_hrmb_code (opsional, > 0; kode HRMB mandor, diisi untuk akun mandor), afdeling_kode (opsional, ≤20; = `divisi.kode` afdeling mandor)

```json
{ "username": "krani2", "nama": "Krani Dua", "password": "sandiAwal123", "role": ["KRANI"] }
```

**201**

```json
{ "id": 5 }
```

**Galat:** 409 username sudah dipakai · 422 masukan tidak sah atau role tidak ada

## PUT /api/admin/pengguna/{id}

**Ubah pengguna.** Permission `user.kelola`. Mengubah nama, status aktif, role (diganti sesuai daftar), serta `mandor_hrmb_code` dan `afdeling_kode` (nilai yang tidak dikirim dikosongkan). Menonaktifkan mencabut semua sesinya. Tidak bisa menonaktifkan akun sendiri. Password tidak diubah di sini.

**Body:** username (hanya saat membuat), nama (wajib), password (hanya saat membuat, ≥8), role (1–5 kode role), aktif (opsional), mandor_hrmb_code (opsional, > 0; kode HRMB mandor, diisi untuk akun mandor), afdeling_kode (opsional, ≤20; = `divisi.kode` afdeling mandor)

```json
{ "username": "krani2", "nama": "Krani Dua", "password": "sandiAwal123", "role": ["KRANI"] }
```

**200**

```json
{ "status": "OK" }
```

**Galat:** 404 · 422

## POST /api/admin/pengguna/{id}/reset-password

**Reset password pengguna.** Permission `user.kelola`. Mengganti password, mewajibkan ganti pada login berikutnya, membuka kunci akun, dan **mencabut semua sesi** pengguna.

**Body:** password (8–72 karakter)

```json
{ "password": "sandiBaru456" }
```

**200**

```json
{ "status": "OK" }
```

**Galat:** 404 · 422 password kurang dari 8 atau lebih dari 72 karakter

## GET /api/admin/perangkat

**Daftar perangkat.** Permission `device.kelola`. Semua HP (dan browser web admin) yang pernah login, terbaru dulu.

**200**

```json
[{ "device_id": "11111111-1111-4111-8111-111111111111", "nama": "Samsung A52", "os_versi": "Android 14", "app_versi": "1.0", "aktif": true, "terakhir_sinkron": "2026-11-11T03:00:00Z" }]
```

## PUT /api/admin/perangkat/{id}

**Aktifkan atau nonaktifkan perangkat.** Permission `device.kelola`. Menonaktifkan juga mencabut semua sesi perangkat itu dan menolak login berikutnya. Perangkat yang sedang dipakai sendiri tidak bisa dinonaktifkan.

**Body:** aktif (boolean)

```json
{ "aktif": false }
```

**200**

```json
{ "status": "OK" }
```

**Galat:** 404 · 422 id bukan UUID atau perangkat sendiri

## GET /api/admin/ringkasan

**Ringkasan dasbor.** Permission `laporan.lihat`. Query `tanggal` (opsional, `YYYY-MM-DD`; bawaan hari ini menurut WIB). Berisi hitungan hari itu, catatan per status ERP, catatan yang perlu perhatian (lokasi di luar TPH, GPS lemah atau mati, lokasi palsu, dan belum diimpor), jumlah perangkat dan pengguna aktif, TPH tanpa koordinat, dan tren 7 hari.

**200**

```json
{ "catatan_hari_ini": 12, "janjang_hari_ini": 480, "catatan_per_status": { "BARU": 10, "DIBACA": 0, "DIIMPOR": 2, "DITOLAK": 0 }, "perlu_perhatian": 3, "perangkat_aktif": 4, "pengguna_aktif": 9, "tph_tanpa_koordinat": 17, "tph_aktif": 120, "tren_7_hari": [{ "tanggal": "2026-11-10", "catatan": 12, "janjang": 480 }] }
```

**Galat:** 422 tanggal salah format

## GET /api/admin/produktivitas

**Ranking produktivitas pemanen satu hari.** Permission `laporan.lihat`. Query `tanggal` (opsional, `YYYY-MM-DD`; bawaan hari ini menurut WIB). Mengelompokkan catatan hari itu per pemanen (kode ERP `HRMBCODE`) dan mengurutkannya dari janjang terbanyak. Janjang yang sama berarti peringkat yang sama (1, 1, 3). Catatan berstatus ditolak tidak dihitung; catatan yang masih menunggu persetujuan krani dihitung dan jumlahnya ditunjukkan di `menunggu_persetujuan`. `persen_matang` dan `rata_janjang` dibulatkan satu desimal.

Endpoint ini **hanya membaca** catatan yang sudah ada. **Premi (rupiah) tidak dihitung**: tarif dan basis premi dimiliki ERP, dan fAI26 tidak menyimpannya.

**200**

```json
{ "tanggal": "2026-11-10", "jumlah_pemanen": 2, "total_janjang": 50, "rata_janjang": 25,
  "pemanen": [
    { "peringkat": 1, "hrmb_code": 90001, "nama": "ALFARIZI", "catatan": 3, "janjang": 30, "janjang_matang": 27, "persen_matang": 90, "brondolan_kg": 37.5, "menunggu_persetujuan": 0 },
    { "peringkat": 2, "hrmb_code": 90002, "nama": "ALPA", "catatan": 2, "janjang": 20, "janjang_matang": 10, "persen_matang": 50, "brondolan_kg": 12.5, "menunggu_persetujuan": 1 }
  ] }
```

Tanpa catatan pada tanggal itu, `pemanen` berupa `[]` dan semua angka nol.

**Galat:** 422 tanggal salah format

## GET /api/admin/panen

**Daftar catatan panen.** Permission `panen.lihat`. Query: `dari`, `sampai` (`YYYY-MM-DD`), `status_erp` (`BARU`, `DIBACA`, `DIIMPOR`, `DITOLAK`), `cari` (kode blok atau TPH), `halaman` (mulai 1), `per_halaman` (bawaan 25, maksimal 100). Terbaru dulu.

**200**

```json
{ "item": [{ "id": "…", "tanggal_panen": "2026-11-10", "blok_kode": "A12", "tph_kode": "01", "total_janjang": 10, "brondolan_kg": 12.5, "status_erp": "BARU", "status_lokasi": "DI_TPH", "jarak_ke_tph_m": 22, "gps_mock": false, "pencatat": "Krani Contoh", "penyetuju": "Mandor Contoh", "diterima_server": "2026-11-10T03:05:00Z" }], "total": 1, "halaman": 1, "per_halaman": 25 }
```

**Galat:** 422 filter salah format

## GET /api/admin/panen/{id}

**Detail catatan panen.** Permission `panen.lihat`. Satu catatan lengkap: GPS, grading per kelas (hitungan AI dan akhir), pemanen (nama dicocokkan dari master), daftar foto (`id` dipakai di `GET /api/admin/foto/{id}`), dan log impor ERP.

**200**

```json
{ "id": "…", "tanggal_panen": "2026-11-10", "total_janjang": 10, "status_erp": "BARU", "asm3_oid": 1012, "gps_lat": -2.0, "gps_lon": 113.0, "grading": [{ "kelas_kode": "ripe", "erp_kolom": "JJG_RIPE", "jml_ai": 7, "jml_final": 9, "diubah_manual": true }], "pemanen": [{ "hrmb_code": 90001, "nama": "Pemanen Contoh 1", "jjg_alokasi": 10 }], "foto": [{ "id": "…", "jenis": "DETEKSI" }], "impor_log": [], "…": "…" }
```

**Galat:** 404 · 422 id bukan UUID

## GET /api/admin/foto/{id}

**Unduh foto catatan.** Permission `panen.lihat`. Mengembalikan byte JPEG (`Content-Type: image/jpeg`) dengan token di header `Authorization`. Karena itu browser memuatnya dengan `fetch` lalu membuat blob, bukan tag gambar biasa. Hanya foto catatan di unit pengguna.

**200** byte JPEG.

**Galat:** 404 foto atau berkasnya tidak ada · 422 id bukan UUID

## GET /api/admin/ipd

**Daftar inspeksi (IPD).** Permission `ipd.lihat`. Unit pengguna, terbaru dulu, dengan ringkasan loses dan foto tiap baris (bentuk baris sama dengan `GET /api/ipd/menunggu`).

**Query:** `dari`, `sampai` (`YYYY-MM-DD`), `status` (`MENUNGGU`, `DISETUJUI`, `DITOLAK`), `divisi_kode`, `blok_kode`, `pemanen_hrmb_code`, `halaman` (mulai 1), `per_halaman` (bawaan 25, maksimal 100).

**200**

```json
{ "item": [ { "id": "…", "status": "DISETUJUI", "ringkasan": { "pokok_diperiksa": 9 } } ], "total": 3, "halaman": 1, "per_halaman": 25 }
```

**Galat:** 422 tanggal salah atau status tidak dikenal

## GET /api/admin/ipd/laporan

**Laporan loses.** Permission `laporan.ipd`. Dihitung dari inspeksi **berstatus `DISETUJUI`** saja. `dari` dan `sampai` kosong berarti 30 hari terakhir (WIB).

**Query:** `dari`, `sampai`, `divisi_kode`, `blok_kode`, `pemanen_hrmb_code`.

**Rumus:** loses = pokok yang memuat paling sedikit satu temuan buah atau brondolan dibagi pokok diperiksa (`persen_pokok_loses`); temuan lain (pelepah) dihitung terpisah (`persen_pokok_temuan_lain`); `persen_pokok_bertemuan` = pokok dengan temuan apa pun dibagi pokok diperiksa; `persen_pokok` per kode = pokok yang memuat kode itu dibagi pokok diperiksa; `jumlah` = penjumlahan jumlah temuan.
Semua persen dibulatkan 1 desimal. `per_kelompok` selalu BUAH, BRONDOLAN, PELEPAH. `per_pemanen`, `per_blok` diurutkan dari `persen_pokok_loses` tertinggi; `per_divisi` dan `per_tanggal` menurut nama/tanggal.

**200**

```json
{
  "dari": "2026-11-01", "sampai": "2026-11-30", "inspeksi": 2, "pemanen": 2,
  "ringkasan": { "pokok_diperiksa": 9, "pokok_bertemuan": 4, "persen_pokok_bertemuan": 44.4, "total_temuan": 14, "per_kode": [{ "kode": "B1", "nama": "Brondolan tak dikutip > 3 butir", "kelompok": "BRONDOLAN", "pokok": 2, "jumlah": 11, "persen_pokok": 22.2 }] },
  "per_kelompok": [{ "kelompok": "BUAH", "pokok": 2, "jumlah": 2, "persen_pokok": 22.2 }],
  "per_pemanen": [{ "kunci": "90001", "nama": "Pemanen Contoh 1", "inspeksi": 1, "pokok_diperiksa": 6, "pokok_bertemuan": 3, "persen_pokok_bertemuan": 50, "total_temuan": 13, "temuan_per_kode": { "B1": 11, "S": 1, "A": 1 } }],
  "per_blok": [], "per_divisi": [], "per_tanggal": []
}
```

**Galat:** 422 tanggal salah, atau `dari` sesudah `sampai`

## GET /api/admin/ipd/{id}

**Rincian satu inspeksi.** Permission `ipd.lihat`. Seperti satu baris daftar, ditambah `pokok`: semua pokok yang diperiksa berurutan (baris lalu pokok), tiap pokok dengan `temuan` (kosong bila bersih).

**200**

```json
{ "id": "…", "status": "DISETUJUI", "ringkasan": { "pokok_diperiksa": 6 }, "foto": [],
  "pokok": [{ "no_baris": 10, "no_pokok": 1, "temuan": [{ "kode": "B1", "jumlah": 5, "catatan": null }] }, { "no_baris": 11, "no_pokok": 1, "temuan": [] }] }
```

**Galat:** 404 · 422 id bukan UUID

## GET /api/admin/audit

**Cari audit log.** Permission `audit.lihat`. Query: `tabel`, `aksi`, `dari`, `sampai` (`YYYY-MM-DD`), `cari` (username, id baris, atau keterangan), `halaman`, `per_halaman`. Terbaru dulu. Catatan: role ADMIN tidak punya `audit.lihat` (hanya AUDITOR); beri admin kedua role bila perlu.

**200**

```json
{ "item": [{ "audit_id": "…", "tabel": "blok", "baris_id": "3", "aksi": "UBAH", "username": null, "user_id": 30, "waktu": "2026-11-11T03:00:00Z", "keterangan": "A12 (QR berubah: 4)", "…": "…" }], "total": 1, "halaman": 1, "per_halaman": 25 }
```

**Galat:** 422 filter salah format

---

## Riwayat perubahan

| Tanggal | Versi | Perubahan |
|---|---|---|
| 21 Nov 2026 | 0.19.0 | **Foto profil akun.** Endpoint baru `PUT /api/auth/foto`, `GET /api/auth/foto`, `DELETE /api/auth/foto` (tiap akun mengelola fotonya sendiri; diproses server menjadi JPEG persegi 384 px). Objek profil (`login`, `me`) menambah `foto_versi`. Perlu migrasi `011_foto_profil.sql`. Penambahan saja: tidak ada yang berubah untuk klien lama. |
| 20 Nov 2026 | 0.18.0 | **IPD: satu kemandoran. Role buatan admin.** (1) `POST /api/ipd`: `mandor_blok_username` dan `mandor_ancak_username` diganti satu field `mandor_username` (kemandoran); respons IPD memuat `mandor_nama` (menggantikan `mandor_blok_nama` dan `mandor_ancak_nama`). **Tidak kompatibel dengan HP versi lama**; perlu migrasi `010_satu_kemandoran.sql` dan HP terbaru (Room v6). (2) Endpoint baru `POST /api/admin/hak-akses` (buat role) dan `DELETE /api/admin/hak-akses/{id}` (hapus role buatan admin yang tidak dipegang akun aktif); `PUT /api/admin/hak-akses/{id}` menerima `nama` dan `deskripsi`; `GET /api/admin/hak-akses` menambah `deskripsi` dan `pengguna` per role. |
| 21 Nov 2026 | 0.17.0 | **IPD: loses dipisah dari temuan lain.** Temuan buah dan brondolan = loses; pelepah (C, R) = temuan lain yang tidak memengaruhi produksi. `IpdKodeAgg` menambah `jenis`; `ringkasan` menambah `pokok_loses`, `persen_pokok_loses`, `total_loses`, `pokok_temuan_lain`, `persen_pokok_temuan_lain`, `total_temuan_lain`; baris laporan (`per_pemanen`, `per_blok`, `per_divisi`, `per_tanggal`) menambah kolom yang sama dan `per_pemanen`/`per_blok` kini diurutkan menurut persen loses. Penambahan saja: field lama tidak berubah. |
| 21 Nov 2026 | 0.16.0 | **Modul IPD (Inspeksi Panen Detail).** Endpoint baru: `POST /api/ipd`, `GET /api/ipd`, `GET /api/ipd/inspeksi-terakhir`, `GET /api/ipd/menunggu`, `POST /api/ipd/{id}/setujui`, `POST /api/ipd/{id}/tolak`, `POST /api/ipd/{id}/media`, `GET /api/ipd/foto/{id}`, `GET /api/admin/ipd`, `GET /api/admin/ipd/laporan`, `GET /api/admin/ipd/{id}`. `GET /api/master` menambah `kode_temuan` dan `divisi[].asisten_user_id`. `GET/POST/PUT /api/admin/divisi` menambah `asisten_user_id` (asisten divisi; `GET` juga `asisten_nama`). Permission baru: `akses.panen`, `akses.ipd`, `ipd.buat`, `ipd.kirim`, `ipd.setujui`, `ipd.lihat`, `laporan.ipd`, `ipd.kelola`; role baru ASKEP dan KTU (hanya melihat). Perlu migrasi `009_ipd.sql`. Penambahan saja: endpoint dan kolom lama tidak berubah. |
| 20 Nov 2026 | 0.15.0 | `POST /api/panen` dengan `kirim_ulang=true`: pencatat kini boleh mengubah catatan yang masih `MENUNGGU` persetujuan krani (isi diganti, status tetap `MENUNGGU`), tidak hanya yang `DITOLAK`. Setelah krani menyetujui catatan terkunci dan `kirim_ulang` dijawab **423** (sebelumnya 200 `SUDAH_ADA` tanpa perubahan). Tanpa `kirim_ulang` perilaku tidak berubah. Respons baru 423 saja. |
| 20 Nov 2026 | 0.14.1 | `GET /api/panen/ringkasan`: pemegang `panen.setujui` (krani) kini juga `lingkup: "UNIT"`. Sebelumnya hanya pemegang `panen.koreksi`, sehingga dashboard krani selalu 0 padahal catatan yang ia periksa dikirim mandor. Bentuk data tidak berubah. |
| 20 Nov 2026 | 0.14.0 | `GET /api/panen/menunggu`: setiap catatan kini memuat `foto_id` (foto bukti untuk diperiksa krani, `null` bila foto belum terkirim). Penambahan kolom saja, tidak ada yang berubah pada kolom lain. |
| 19 Nov 2026 | 0.13.0 | Endpoint baru `GET /api/admin/produktivitas`: ranking produktivitas pemanen satu hari untuk halaman web Produktivitas Pemanen (hanya membaca, permission `laporan.lihat`; tanpa perhitungan premi). Tidak ada perubahan pada endpoint lain. |
| 19 Nov 2026 | 0.12.0 | **Persetujuan krani lewat server.** `POST /api/panen` dari pengirim tanpa hak `panen.setujui` (mandor) kini disimpan berstatus `MENUNGGU` (tidak lagi 422); endpoint baru `GET /api/panen/menunggu`, `POST /api/panen/{id}/setujui` dan `POST /api/panen/{id}/tolak` (permission `panen.setujui`) supaya krani menyetujui dari HP-nya sendiri. Status HP baru `MENUNGGU_SETUJU`. Migrasi `008_menunggu_persetujuan.sql` (nilai `MENUNGGU` pada `status_erp`) |
| 18 Nov 2026 | 0.11.0 | **Mandor yang menginput.** Akun punya `mandor_hrmb_code` dan `afdeling_kode` (admin pengguna; migrasi `007_mandor_input.sql`); login mengembalikannya di `profil`. `GET /api/master` untuk akun mandor dibatasi ke pemanen miliknya dan blok/TPH afdelingnya. `POST /api/panen`: persetujuan terpisah tidak lagi wajib (mandor pengirim menyetujui sendiri), `tph_kode` wajib, blok/TPH/pemanen harus ada di master dan pemanen milik mandor akun, anti ganda 409, field `kirim_ulang` untuk mengirim ulang catatan DITOLAK dengan `panen_id` sama; mandor dan afdeling catatan diambil dari akun. Kolom baru `fai_panen.pemanen_hrmb_code` (tabel `fai_panen_pemanen` deprecated), view `v_fai_panen_siap_impor` menambah `unit` dan `waktu_panen` |
| 18 Nov 2026 | 0.10.0 | **Satu TPH = satu pemanen.** `POST /api/panen`: `pemanen[]` kini wajib tepat satu dengan `jjg_alokasi` sama dengan total janjang (sebelumnya opsional; HP versi lama tanpa pemanen akan ditolak 422). Admin pemanen: field baru `mandor_hrmb_code`. Catatan panen menyimpan salinan `afdeling_kode`, `afdeling_erp_oid`, `mandor_hrmb_code`; view `v_fai_panen_siap_impor` menambah kolom itu serta `pemanen_hrmb_code` dan `pemanen_jjg`. Migrasi `migrations/005_hvdm_satu_pemanen.sql` |
| 17 Nov 2026 | 0.9.0 | Endpoint baru `GET /api/panen/ringkasan`: ringkasan dashboard real-time dari database server untuk Beranda HP (produksi harian, rincian per kelas, rekap per blok, tren 7 hari, status catatan) sesuai lingkup hak akses pengguna |
| 11 Nov 2026 | 0.8.0 | Endpoint baru `GET /api/admin/hak-akses` dan `PUT /api/admin/hak-akses/{id}`: mengelola hak (permission) tiap role dari web admin (sebelumnya hanya lewat SQL). Role ADMIN dilindungi agar tidak kehilangan `role.kelola` |
| 11 Nov 2026 | 0.7.0 | Endpoint baru `POST /api/auth/password` (ganti password sendiri; mencabut sesi perangkat lain). Batas laju per alamat IP: login dan refresh 30 per menit, seluruh API 600 per menit (bawaan, dapat diatur); melebihi batas dijawab 429 dengan `Retry-After` |
| 11 Nov 2026 | 0.6.0 | `GET /api/master` menambah `role_permission` (hak semua role) dan versi master kini ikut berubah bila hak role berubah, sehingga HP mengikuti hak akses dari server. Versi lama berubah satu kali (HP mengunduh ulang sekali) |
| 11 Nov 2026 | 0.5.0 | Endpoint baru `PUT /api/tph/{id}/koordinat`: HP mengirim titik GPS TPH yang direkam di lokasi. Hanya mengisi bila TPH belum punya koordinat; bila sudah punya, titik server dikembalikan (`diterapkan: false`) dan HP mengikutinya |
| 11 Nov 2026 | 0.4.0 | Endpoint web admin `/api/admin/*`: master (divisi, blok, TPH termasuk pembuatan massal, pemanen) dengan `qr_kode` otomatis, pengguna dan role, reset password, perangkat, ringkasan dasbor, daftar dan detail catatan panen, unduh foto, dan pencarian audit |
| 11 Nov 2026 | 0.3.0 | `POST /api/panen` menerima `pencatat_username` (opsional): catatan boleh dikirim atas nama pencatat sebenarnya oleh pemegang `panen.koreksi`, sehingga pemilik catatan tidak berubah menjadi pengirim |
| 11 Nov 2026 | 0.2.0 | Endpoint baru `GET /api/master` (unduh master unit: unit, divisi, blok, TPH, pemanen, akun dan role-nya, dengan versi supaya HP tidak mengunduh ulang). Tabel master baru di database server (migrasi `migrations/002_master.sql`) |
| 10 Nov 2026 | 0.1.2 | Setiap permintaan dibatasi 20 detik; bila database lambat server menjawab 503 "Server sedang sibuk" (sebelumnya bisa menggantung sampai HP menyerah) |
| 10 Nov 2026 | 0.1.1 | `kelas_kode` tandan kosong diganti dari `emptybunch` menjadi `empty` (sama dengan kode di HP dan ERD) |
| 10 Nov 2026 | 0.1.0 | Versi awal: health, auth (login, refresh, logout, me), panen (kirim, status, foto), audit batch |

### Cara memperbarui dokumen ini

1. Tambah rute di `internal/routes/routes.go` (tabel `tabel`).
2. Tambah path dan skema di `docs/openapi.yaml`.
3. Tambah bagian endpoint di dokumen ini (judul `## METHOD /path`) dan baris di tabel daftar endpoint.
4. Tambah baris di Riwayat perubahan dan naikkan versi (di sini dan di `info.version` pada `openapi.yaml`).
5. Jalankan `go test ./...`. Tes dokumentasi akan gagal bila langkah 2 atau 3 terlewat.
