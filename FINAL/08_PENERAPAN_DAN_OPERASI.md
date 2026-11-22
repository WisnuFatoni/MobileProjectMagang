# 08. Penerapan di Server dan Operasi

Ringkasan **runbook penerapan** fAI26 beserta hasil latihannya. Panduan langkah rinci yang berlaku ada di `fai26-api/docs/DEPLOY.md` (salinan: `lampiran/api/DEPLOY.md`); skrip pendukung di `lampiran/skrip/`. Runbook ini **dilatih dari nol pada simulasi lokal** (22 Nov 2026), **belum** dijalankan di server sungguhan.

## 1. Status

| Bagian | Status |
|---|---|
| Database dari nol: skema dasar + migrasi 002 sampai 012 | **Dicoba** (simulasi), semua kode keluar 0; seluruh migrasi **aman diulang** |
| Login API berhak sempit dan migrasi 004 | **Dicoba**: `UPDATE`/`DELETE` pada `audit_log` dan `CREATE TABLE` ditolak; seluruh endpoint tetap jalan |
| Akun ADMIN pertama (`hashpw` + `awal_unit_admin.sql`) | **Dicoba** |
| Menjalankan program dari folder terpisah dengan `.env` | **Dicoba** (port lain, tanpa HTTPS) |
| Skrip pemeriksa `periksa-server.ps1` | **Dicoba** pada tiga keadaan (benar, satu migrasi hilang, konfigurasi salah) |
| Cadangan dan pemulihan ke nama database lain | **Dicoba**; isi sama |
| Layanan Windows (NSSM) | **Belum dicoba** |
| Reverse proxy, HTTPS, firewall | **Belum dicoba** (butuh persetujuan perubahan sistem) |
| SQL Server milik kebun, pemulihan ke server lain, `RESTORE ... WITH REPLACE` | **Belum dicoba** |
| Migrasi pada database berisi data nyata (migrasi 010 menghapus kolom) | **Belum dicoba** |
| Akun ERP untuk role `fai26_hvdm`, jadwal cadangan otomatis, uji beban | **Belum** |

## 2. Yang disiapkan

| Kebutuhan | Keterangan |
|---|---|
| Server Windows | Ruang untuk database, folder foto (`FAI26_MEDIA_DIR`), dan cadangan |
| SQL Server | Terakhir diuji: SQL Server 2025. Izin membuat database di server sandbox masih perlu dikonfirmasi |
| Akun DBA | Membuat database, login API, menjalankan migrasi (bukan akun yang dipakai API) |
| Mesin build | Go dan Node.js (cukup di laptop pengembang) |
| Nama domain dan sertifikat | HTTPS wajib untuk HP versi rilis |
| Keputusan lama simpan jalur GPS | Lihat `09_KEAMANAN_DAN_PRIVASI.md` |

## 3. Urutan kerja

| No | Langkah | Bukti selesai |
|---|---|---|
| 1 | Buat database `fai26` | Database ada |
| 2 | `erd/fAI26_server_sqlserver.sql` lalu migrasi **002, 003, 005, 006, 007, 008, 009, 010, 011, 012** berurutan (`sqlcmd -I -b`) | Semua kode keluar 0 |
| 3 | Buat login API (hanya `db_datareader` dan `db_datawriter`, **bukan** `sa`), lalu migrasi **004** dengan nama user itu | `UPDATE audit_log` ditolak |
| 4 | Unit dan akun ADMIN pertama: `hashpw` + `scripts/awal_unit_admin.sql` | Satu baris hasil dengan role `ADMIN,AUDITOR` |
| 5 | Isi `.env` (lihat `04_API_REFERENSI.md` bagian 5) | Bagian Konfigurasi pemeriksa hijau |
| 6 | Build web (`npm ci && npm run build`) dan API (`go build -o fai26-api.exe ./cmd/api`); salin `fai26-api.exe`, `.env`, `dist`, `scripts/` ke server | Berkas ada di folder pemasangan |
| 7 | Jalankan API dari folder pemasangan | `GET /api/health` 200 |
| 8 | `scripts\periksa-server.ps1 -Folder <folder>` | Hasil: siap, kode keluar 0 |
| 9 | Pasang HTTPS (reverse proxy) dan uji dari HP | Login HP berhasil |

**Migrasi 010 menghapus dua kolom lama** di `fai_ipd`: cadangkan database lebih dulu bila sudah ada data IPD. Migrasi 007 berhenti dengan pesan bila ada catatan ganda lama (hapus lunak yang berlebih lalu ulangi).

## 4. Kekurangan panduan lama yang ditemukan dan diperbaiki (latihan 22 Nov)

| No | Temuan | Perbaikan |
|---|---|---|
| 1 | Urutan migrasi berhenti di 008 | Urutan lengkap sampai 012 |
| 2 | Skema dasar tidak membuat unit | `awal_unit_admin.sql` |
| 3 | Tidak ada cara membuat hash bcrypt | Program `hashpw` memakai fungsi hash yang sama dengan API |
| 4 | Hash dari PowerShell 5.1 salah karena BOM (login ADMIN ditolak) | `hashpw` membuang BOM |
| 5 | ADMIN tidak punya hak membuka Audit Log | Skrip data awal memberi role AUDITOR juga |
| 6 | Migrasi 010 menghapus kolom tanpa peringatan | Peringatan dan perintah cadangan |
| 7 | Cadangan tanpa perintah dan bukti | Perintah dan uji pulihkan ke nama lain |
| 8 | Tidak ada langkah update dan rollback | Bagian khusus |
| 9 | Pemeriksaan hanya manual | `periksa-server.ps1` |

Masalah lain yang ditemui: `sqlcmd -v` dengan nilai berspasi gagal di PowerShell 5.1 (jalankan dari `cmd.exe` atau hindari spasi); jalur ber-`/` dari terminal bash memicu "Access is denied"; migrasi 003 dan 006 tampak hilang bagi akun API berhak sempit karena prosedur dan peran memang tersembunyi (periksa sebagai DBA); akun terkunci 15 menit setelah 5 kata sandi salah (buka dengan `UPDATE dbo.app_user SET gagal_login = 0, terkunci_sampai = NULL WHERE username = '<nama>'`).

## 5. Skrip pendukung (`lampiran/skrip/`)

| Berkas | Fungsi |
|---|---|
| `periksa-server.ps1` | **Hanya membaca**: `.env`, program, folder foto dan web, sambungan database dengan akun API, 11 tahap migrasi, hak akun API (tidak bisa mengubah `audit_log`; bukan sysadmin/db_owner), unit dan akun ADMIN, `GET /api/health`, halaman web. Hasil `[OK]/[PERINGATAN]/[GAGAL]`; kode keluar = jumlah GAGAL. Tidak mencetak kata sandi |
| `awal_unit_admin.sql` | Unit dan akun ADMIN pertama (+AUDITOR; wajib ganti sandi), aman diulang; variabel `-v KODE_UNIT NAMA_UNIT ERP_DB TAHUN_CLUSTER USERNAME NAMA_ADMIN HASH_SANDI` |
| `env.example` | Contoh `.env` |
| `cmd/hashpw` (di repositori) | Membaca kata sandi dari masukan standar, mencetak hash bcrypt (minimal 8 karakter) |

## 6. Menjalankan dan layanan

- Percobaan: jalankan `fai26-api.exe` dari folder pemasangan (membaca `.env` dari folder kerjanya). Log awal memuat baris "perawatan: ..." dan "fai26-api mendengar di <alamat>". Server berhenti rapi pada SIGINT/SIGTERM (menyelesaikan permintaan sampai 10 detik).
- **Layanan Windows (NSSM) [belum dicoba]:** `nssm install fai26-api C:\fai26\fai26-api.exe`, `AppDirectory C:\fai26`, log ke `C:\fai26\log\api.log` dengan rotasi 10 MB, `Start SERVICE_AUTO_START`; akun layanan hanya berhak pada folder itu.
- **HTTPS [belum dicoba]:** API hanya HTTP; sertifikat di reverse proxy (contoh Caddy dan Nginx di DEPLOY.md). `X-Forwarded-For` harus **ditimpa** proxy (`FAI26_PERCAYAI_PROXY=true`); `FAI26_ADDR=127.0.0.1:8080`. HP versi rilis hanya mau HTTPS; isi alamat di layar Akun atau `DEFAULT_SERVER_URL` saat build rilis. Membuka firewall adalah perubahan pengaturan server yang butuh persetujuan pemilik.

## 7. Cadangan, pembaruan, dan rollback

**Cadangan** (database `fai26` dan folder media **bersama-sama**, karena foto dirujuk dari database):

```sql
BACKUP DATABASE fai26 TO DISK = N'<folder>\fai26.bak' WITH CHECKSUM, INIT, COMPRESSION;
RESTORE VERIFYONLY FROM DISK = N'<folder>\fai26.bak' WITH CHECKSUM;
```

Pulihkan ke nama lain untuk membuktikan cadangan sah (dicoba). Pemulihan ke **server lain** membuat user database `fai26_api` yatim: buat login sama lalu `ALTER USER fai26_api WITH LOGIN = fai26_api;` **[belum dicoba]**. Atur umur cadangan, karena cadangan menyimpan jalur GPS yang sudah dihapus.

**Memperbarui versi:** hentikan API → cadangkan database → ganti `fai26-api.exe` dan isi `dist` → jalankan migrasi baru (aman diulang; lihat riwayat perubahan di API.md) → mulai API → `periksa-server.ps1`.

**Kembali ke versi lama:** hentikan API, kembalikan `fai26-api.exe` dan `dist` lama; bila migrasi baru sudah dijalankan, pulihkan database dari cadangan (migrasi tidak punya skrip pembalik). **Simpan satu salinan versi sebelumnya** sebagai syarat rollback.

**Ganti `FAI26_JWT_SECRET`** bila bocor: semua token akses tidak berlaku; pengguna login ulang (sesi refresh di database tetap berlaku, token akses baru memakai kunci baru).

## 8. Pemeriksaan setelah terpasang (manual)

- Web admin terbuka di `https://<alamat>/`; login ADMIN dan ganti kata sandi saat diminta.
- Buat blok dan TPH; cetak QR sesuai `SPEK_QR_TPH.md`.
- Login dari HP; master turun; catat satu panen uji; setujui (krani); cek di `SELECT * FROM dbo.v_fai_panen_siap_impor` dan di web (Catatan Panen).
- Akun API tidak bisa `UPDATE`/`DELETE` pada `audit_log`.
- Batas laju aktif: 31 login berturut-turut dari satu alamat dijawab 429.

## 9. Lingkungan pengembangan (laptop)

- Folder `L:\GRADING_MAGANG\`. API: `go run ./cmd/api` (port 8080, konfigurasi `.env` yang **tidak boleh dibagikan**). Web: `npm run dev` atau dilayani API (`FAI26_WEB_DIR`). HP: Android Studio (JDK 17 untuk Gradle).
- **Database pengembangan** `fai26_dev` (data asli kebun + data uji) dan **database uji terpisah `fai26_tes`**: tes integrasi **hanya** boleh di `fai26_tes` (menghapus isi tabel `fai_*`, `auth_sesi`, `audit_log`, `device`, akun uji) dan **menolak berjalan** di database yang namanya tidak berakhiran `_tes`. `FAI26_TEST_DSN` memakai bentuk `key=value` (bukan URL; sandi berkarakter khusus merusak bentuk URL).
- **Sumber daya laptop terbatas** (RAM rendah, ruang disk C: hampir penuh): jalankan satu alat berat sekali waktu; `gradlew --stop` setelah build; jangan menjalankan banyak pencarian berkas rekursif.
- HP terhubung lewat USB: `adb reverse tcp:8080 tcp:8080` (hilang bila kabel dilepas).
