# Panduan pemasangan fai26 di server

Untuk satu server Windows (atau Linux) yang menjalankan API Go dan web admin, dengan SQL Server di server yang sama atau
di server lain. Semua kredensial diisi sendiri di server (`.env`); jangan dibagikan atau di-commit.

## 1. Database

1. Buat database `fai26` (izin membuat database di server sandbox masih perlu dikonfirmasi).
2. Jalankan `erd/fAI26_server_sqlserver.sql` pada database kosong (14 tabel, role, dan permission awal). Bila memakai
   `sqlcmd`, tambahkan `-I` (butuh `QUOTED_IDENTIFIER ON` untuk indeks terfilter).
3. Jalankan migrasi berurutan yang belum ada di skrip itu: `migrations/002_master.sql` (aman bila tabelnya sudah ada),
   `migrations/003_jembatan_erp.sql` (view dan prosedur untuk HVDM), dan `migrations/005_hvdm_satu_pemanen.sql`
   (kolom mandor dan afdeling, view HVDM versi satu pemanen; menggantikan view di 003). Semuanya aman diulang.
4. Buat login khusus API (hak `db_datareader`, `db_datawriter`) dan **jangan** memakai `sa`. Setelah semuanya jalan,
   jalankan `migrations/004_audit_log_deny.sql` dengan nama user itu. Lalu jalankan `migrations/006_peran_hvdm.sql`
   (peran `fai26_hvdm`: hanya baca dua view dan jalankan empat prosedur `sp_fai_*`) dan tambahkan login ERP ke peran itu
   (perintahnya ada di komentar berkas); jangan beri login ERP hak tulis pada tabel.
   Jalankan juga `migrations/007_mandor_input.sql` (setelah 005; akun mandor, `pemanen_hrmb_code`, indeks anti ganda, dan view HVDM terbaru). Bila ada
   catatan ganda lama, migrasi berhenti dengan pesan; hapus lunak yang berlebih lalu ulangi. Jalankan juga `migrations/008_menunggu_persetujuan.sql` (status `MENUNGGU`: catatan mandor menunggu disetujui krani lewat server). Urutan lengkap: 002, 003, 005, 006, 007, 008 (004 oleh DBA).
5. Isi data awal: unit, divisi, blok, TPH, dan pemanen lewat web admin atau salin dari ERP. Tidak ada program seed dan tidak ada akun
   contoh: buat akun ADMIN pertama lewat SQL (hash bcrypt), lalu kelola akun lain lewat web admin.

## 2. Konfigurasi API (`.env` di folder kerja API)

Lihat `.env.example`. Yang wajib: `DB_*` (atau `FAI26_DSN`), `FAI26_JWT_SECRET` (acak, minimal 32 karakter),
`FAI26_MEDIA_DIR` (folder foto; pastikan ada ruang dan dicadangkan). Untuk produksi:

```
DB_ENCRYPT=true
DB_TRUST_CERT=false          # true hanya bila sertifikat SQL Server tidak dipercaya dan Anda tahu risikonya
FAI26_ADDR=127.0.0.1:8080    # hanya mendengar di lokal; reverse proxy yang menghadap luar
FAI26_WEB_DIR=C:\fai26\web   # hasil `npm run build` (isi dist/), bila web dilayani oleh API
FAI26_PERCAYAI_PROXY=true    # karena di belakang reverse proxy (lihat bagian 4)
```

## 3. Membangun dan menjalankan

```
cd fai26-web && npm ci && npm run build        # hasilnya fai26-web/dist
cd ../fai26-api && go build -o fai26-api.exe ./cmd/api
```

Salin `fai26-api.exe`, `.env`, dan folder `dist` ke server (mis. `C:\fai26`). Jalankan dari folder itu supaya `.env`
terbaca: `fai26-api.exe`. Cek `GET /api/health` (200 berarti server dan database hidup).

### Sebagai layanan Windows (jalan otomatis, hidup lagi bila mati)

Cara paling sederhana adalah NSSM (https://nssm.cc), dengan akun layanan yang hanya punya hak ke folder `C:\fai26`:

```
nssm install fai26-api C:\fai26\fai26-api.exe
nssm set fai26-api AppDirectory C:\fai26
nssm set fai26-api AppStdout C:\fai26\log\api.log
nssm set fai26-api AppStderr C:\fai26\log\api.log
nssm set fai26-api AppRotateFiles 1
nssm set fai26-api AppRotateBytes 10485760
nssm set fai26-api Start SERVICE_AUTO_START
nssm start fai26-api
```

Server berhenti dengan rapi (menyelesaikan permintaan berjalan sampai 10 detik) saat menerima SIGINT atau SIGTERM.

## 4. HTTPS lewat reverse proxy (wajib untuk HP)

API sendiri hanya melayani HTTP; sertifikat dan HTTPS dipasang di reverse proxy. Build rilis aplikasi hanya mau
berbicara HTTPS. Contoh Caddy (sertifikat otomatis bila server punya nama domain publik):

```
fai26.contoh-kebun.co.id {
    encode gzip
    reverse_proxy 127.0.0.1:8080 {
        header_up X-Forwarded-For {remote_host}
    }
    request_body {
        max_size 12MB
    }
}
```

`header_up X-Forwarded-For {remote_host}` MENIMPA header dari klien (dengan nilai yang dilihat proxy), itu yang dipercaya
oleh `FAI26_PERCAYAI_PROXY=true`. Untuk Nginx pakai `proxy_set_header X-Forwarded-For $remote_addr;` (bukan
`$proxy_add_x_forwarded_for`). Untuk jaringan internal tanpa nama domain publik, pakai sertifikat dari CA perusahaan
dan pasang CA itu di HP.

Isi alamat server ini di aplikasi HP (layar Akun) atau pada build rilis (`DEFAULT_SERVER_URL`).

## 5. Pemeriksaan setelah terpasang

- `GET https://<alamat>/api/health` menjawab 200 dan web admin terbuka di `https://<alamat>/`.
- Login web dengan akun ADMIN; buat blok dan TPH; cetak QR sesuai `progres/SPEK_QR_TPH.md`.
- Login dari HP, master turun (blok, TPH, pemanen), catat satu panen uji, setujui, kirim; cek muncul di
  `SELECT * FROM dbo.v_fai_panen_siap_impor` dan di web admin (Catatan Panen).
- Akun API tidak bisa `UPDATE` atau `DELETE` pada `audit_log` (lihat pemeriksaan di `004_audit_log_deny.sql`).
- Batas laju aktif: 31 login berturut-turut dari satu alamat dijawab 429.

## 6. Cadangan dan pemeliharaan

- Cadangkan database `fai26` dan folder `FAI26_MEDIA_DIR` bersama-sama (foto bukti dirujuk dari database).
- Ganti `FAI26_JWT_SECRET` bila bocor: semua token akses tidak berlaku, pengguna login ulang (sesi refresh di database
  tetap berlaku, tetapi token akses baru ditandatangani dengan kunci baru).
- Memperbarui versi: hentikan layanan, ganti `fai26-api.exe` dan isi `dist`, jalankan migrasi baru (jika ada) lalu mulai
  lagi. Dokumen API (`docs/API.md`) memuat riwayat perubahan dan versinya.
