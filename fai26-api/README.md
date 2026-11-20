# fai26-api

API sinkronisasi untuk aplikasi fAI26: HP krani mengirim catatan panen ke database `fai26` di SQL Server.
HVDM (sisi ERP) membaca tabel `fai_*` dari database yang sama; tidak ada API resmi mySAP365.

## Struktur folder

```
fai26-api/
├── cmd/
│   ├── api/main.go            titik masuk server
│   └── seed/main.go           isi data contoh (hanya pengembangan)
├── docs/                      API.md (panduan) dan openapi.yaml (spesifikasi)
├── internal/
│   ├── app/                   composition root: merakit repository → service → controller → routes
│   ├── routes/                daftar alamat API dan middleware tiap rute
│   ├── middleware/            autentikasi (JWT), izin (permission), pulih dari panic, log
│   ├── controller/            lapisan HTTP: baca input, panggil service, tulis jawaban (tanpa aturan bisnis)
│   ├── service/               aturan bisnis (login, kunci akun, validasi catatan, persetujuan mandor, foto, audit)
│   ├── repository/            semua SQL ke database (satu-satunya tempat SQL)
│   ├── model/                 DTO permintaan/jawaban dan entitas
│   ├── auth/                  JWT, hash password (bcrypt), token acak
│   ├── apperror/              galat aplikasi dengan kode HTTP
│   ├── response/              penulis jawaban JSON seragam
│   ├── storage/               penyimpanan foto di disk
│   ├── config/                pengaturan dari variabel lingkungan
│   ├── database/              pembukaan koneksi SQL Server
│   └── seed/                  data contoh pengembangan
└── test/integration/          tes terhadap SQL Server sungguhan (opt-in)
```

Aliran satu permintaan: `routes` → `middleware` → `controller` → `service` → `repository` → database.
Service bergantung pada interface (didefinisikan di `service/interfaces.go`), sehingga aturan bisnis diuji dengan
tiruan tanpa database.

## Menjalankan

Salin `.env.example` menjadi `.env` lalu isi (berkas `.env` diabaikan git; di server isi sendiri, jangan dibagikan):

```
DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD, DB_ENCRYPT, DB_TRUST_CERT
FAI26_JWT_SECRET   acak, minimal 32 karakter
FAI26_MEDIA_DIR    folder foto
FAI26_WEB_DIR      (opsional) folder fai26-web/dist; bila diisi API juga menyajikan web admin
```

Dengan `FAI26_WEB_DIR`, satu proses melayani semuanya di satu alamat: `/api/*` ke API, selain itu ke web admin
(rute React seperti `/tph` dijawab `index.html`, berkas di `/assets` disimpan lama oleh browser, ada header keamanan
dan CSP). Bangun web dulu dengan `npm run build` di `../fai26-web`. Kode ada di `internal/webui`.

Variabel lingkungan yang sudah diatur menang atas `.env`. Alternatifnya satu string `FAI26_DSN`.

```
go run ./cmd/api
```

Database dibuat dari `erd/fAI26_server_sqlserver.sql`. Bila Go belum punya modul, atur `GOMODCACHE` dan `GOCACHE`
ke folder yang lega (folder `.cache` di sini diabaikan git).

Tidak ada data atau akun contoh di aplikasi maupun API. Akun dan master diisi lewat web admin. Tes integrasi memakai akun contoh milik
tes sendiri (`test/integration/seed_tes_test.go`) dan **hanya berjalan di database berakhiran `_tes`** (pengaman, karena tes menghapus
isi tabel panen, sesi, dan audit). Buat database kosong bernama mis. `fai26_tes`, jalankan `erd/fAI26_server_sqlserver.sql` lalu migrasi
002, 003, 005, 006, 007, 008 (`sqlcmd -I`), dan arahkan `FAI26_TEST_DSN` ke sana. Di database lain tes dilewati.

## Dokumentasi API

- [`docs/API.md`](docs/API.md): panduan lengkap (endpoint, body, contoh, kode galat, alur HP, riwayat perubahan).
- [`docs/openapi.yaml`](docs/openapi.yaml): spesifikasi OpenAPI 3 (buka di Swagger Editor, Postman, atau Insomnia).

**Aturan:** setiap endpoint baru atau perubahan bentuk data wajib memperbarui kedua berkas itu dan Riwayat perubahan.
Tes `TestSemuaRuteTerdokumentasi` dan `TestVersiDokumenSama` gagal bila dokumentasi tertinggal dari `internal/routes/routes.go`.

## Endpoint

| Method | Alamat | Hak | Fungsi |
|---|---|---|---|
| GET | /api/health | - | Cek hidup dan koneksi database |
| POST | /api/auth/login | - | username, password, device_id (UUID) → token akses 15 menit, refresh token, profil, role, permission |
| POST | /api/auth/refresh | - | Tukar refresh token (diganti tiap dipakai) |
| POST | /api/auth/logout | login | Cabut sesi perangkat ini |
| GET | /api/auth/me | login | Profil dan permission terbaru |
| POST | /api/panen | panen.kirim | Terima satu catatan panen (idempoten menurut `panen_id`) |
| GET | /api/panen?sejak=ISO | panen.lihat | Status catatan (TERKIRIM, DITERIMA, DITOLAK + pesan) |
| POST | /api/panen/{id}/media | panen.kirim | Terima foto JPEG (header `X-Foto-Id`, `X-Sha256`) |
| POST | /api/audit/batch | login | Terima audit_log dari HP (maks. 500 baris, duplikat dilewati) |

## Aturan penting

- **Identitas dari token**, bukan dari isi kiriman: `dibuat_oleh`, `device_id`, dan `user_id` audit selalu dari token.
- **Permission dibaca ulang dari database** pada tiap panggilan; akun atau perangkat yang dinonaktifkan langsung tidak berguna.
- **Catatan wajib disetujui mandor** (keputusan Ptoni 10 Nov): server menolak (422) bila `disetujui_username` kosong,
  tidak dikenal, atau tidak memegang `panen.setujui` di server.
- **Idempoten:** kirim ulang `panen_id` atau `X-Foto-Id` yang sama menjawab 200 `SUDAH_ADA` tanpa menggandakan data.
- **Kunci akun:** 5 kali salah → terkunci 15 menit (HTTP 423).
- **Galat server tidak membocorkan detail:** klien hanya menerima pesan umum; sebab teknis masuk log.
- Pemetaan status untuk HP dari `status_erp`: BARU/DIBACA → TERKIRIM, DIIMPOR → DITERIMA, DITOLAK → DITOLAK.

## Tes

```
go test ./...                      tes unit (tanpa database): model, service, middleware
set FAI26_TEST_DSN=...             hidupkan tes integrasi di test/integration
go test ./test/integration -v
```

Tes integrasi **menghapus isi** tabel `fai_*`, `auth_sesi`, `audit_log`, dan `device`: arahkan hanya ke database uji
yang sengaja dibuat untuk itu. Tidak ada nilai bawaan; tanpa `FAI26_TEST_DSN` tes dilewati.

## Belum ada

- Pengguna mengganti password sendiri (reset oleh admin sudah ada di `/api/admin/pengguna/{id}/reset-password`).
- `DENY UPDATE, DELETE` pada `audit_log` untuk akun API (ada di skrip, perlu diterapkan di server sungguhan).
- HTTPS (pasang di belakang reverse proxy) dan pembatasan laju permintaan.
