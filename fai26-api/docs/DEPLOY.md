# Runbook penerapan fai26 di server

Untuk satu server Windows yang menjalankan API Go dan web admin, dengan SQL Server di server yang sama atau di server lain.
Semua kredensial diisi sendiri di server (`.env`); jangan dibagikan atau di-commit.

**Status dokumen (22 Nov 2026):** seluruh langkah database, konfigurasi, menjalankan API, pemeriksaan, cadangan, dan
pemeriksaan ulang migrasi sudah **dilatih dari nol pada simulasi lokal** (database kosong baru, akun API berhak sempit,
program hasil build dijalankan dari folder terpisah). Yang belum pernah dicoba ditandai **[belum dicoba]**; bagian 11
merangkum semuanya. Simulasi ini bukan server sungguhan: belum ada uji di server kebun, dengan SQL Server milik kebun,
HTTPS, atau HP.

## 1. Yang harus disiapkan

| Kebutuhan | Keterangan | Siapa |
|---|---|---|
| Server Windows | Ruang disk untuk database, folder foto (`FAI26_MEDIA_DIR`), dan cadangan | Admin server |
| SQL Server | Terakhir diuji: SQL Server 2025. Izin membuat database di server sandbox masih perlu dikonfirmasi | DBA |
| Akun DBA | Untuk membuat database, login API, dan menjalankan migrasi (bukan akun yang dipakai API) | DBA |
| Mesin build | Go dan Node.js (cukup di laptop pengembang; server hanya menerima hasil build) | Pengembang |
| Nama domain dan sertifikat | Untuk HTTPS (bagian 7); HP versi rilis hanya mau berbicara HTTPS | Admin jaringan |
| Keputusan kebijakan jalur GPS | Lama simpan jalur GPS petugas (bagian 9) | Pembimbing/kebun |

Pada simulasi, seluruh langkah dari database kosong sampai pemeriksaan lulus dikerjakan dalam satu sesi kerja; waktu
sebenarnya di server kebun akan lebih lama karena menunggu persetujuan akses.

## 2. Urutan kerja ringkas

| No | Langkah | Bukti selesai |
|---|---|---|
| 1 | Buat database `fai26` (bagian 3.1) | Database ada |
| 2 | Jalankan skema dasar lalu migrasi berurutan (3.2, 3.3) | Semua perintah berkode keluar 0 |
| 3 | Buat login API berhak sempit dan jalankan migrasi 004 (3.4) | `UPDATE audit_log` ditolak |
| 4 | Buat unit dan akun ADMIN pertama (3.5) | Satu baris hasil dengan role `ADMIN,AUDITOR` |
| 5 | Isi `.env` (bagian 4) | `periksa-server.ps1` bagian Konfigurasi hijau |
| 6 | Build dan salin ke server (bagian 5) | `fai26-api.exe`, `dist`, `.env` ada di folder pemasangan |
| 7 | Jalankan API (bagian 6) | `GET /api/health` menjawab 200 |
| 8 | Jalankan `scripts\periksa-server.ps1` (bagian 8) | Hasil: siap, kode keluar 0 |
| 9 | Pasang HTTPS (bagian 7) dan uji dari HP | Login HP berhasil |

## 3. Database

Perintah di bawah memakai `sqlcmd`; `-I` wajib (indeks terfilter butuh `QUOTED_IDENTIFIER ON`) dan `-b` membuat
`sqlcmd` berhenti dengan kode keluar bukan nol bila ada galat. Semua berkas dijalankan pada database `fai26`, kecuali
3.1.

### 3.1 Membuat database

```
sqlcmd -S <server> -U <akun_dba> -I -b -Q "CREATE DATABASE fai26"
```

### 3.2 Skema dasar

```
sqlcmd -S <server> -d fai26 -U <akun_dba> -I -b -i erd\fAI26_server_sqlserver.sql
```

Membuat 14 tabel, role, dan permission awal pada database kosong. Skema dasar **tidak** membuat unit (kebun) dan akun
apa pun; keduanya dibuat di 3.5.

### 3.3 Migrasi berurutan

Jalankan **satu per satu menurut urutan ini** (urutan di dokumen lama berhenti di 008, padahal ada sampai 012):

| Urutan | Berkas | Isi |
|---|---|---|
| 1 | `migrations/002_master.sql` | Tabel master (divisi, blok, TPH, pemanen) |
| 2 | `migrations/003_jembatan_erp.sql` | View dan prosedur untuk HVDM (ERP) |
| 3 | `migrations/005_hvdm_satu_pemanen.sql` | Kolom mandor dan afdeling, view HVDM satu pemanen |
| 4 | `migrations/006_peran_hvdm.sql` | Peran `fai26_hvdm` untuk login ERP (hanya baca dua view dan jalankan prosedur `sp_fai_*`) |
| 5 | `migrations/007_mandor_input.sql` | Akun mandor, `pemanen_hrmb_code`, indeks anti ganda |
| 6 | `migrations/008_menunggu_persetujuan.sql` | Status `MENUNGGU` (persetujuan krani lewat server) |
| 7 | `migrations/009_ipd.sql` | Modul IPD (inspeksi panen detail) |
| 8 | `migrations/010_satu_kemandoran.sql` | IPD memakai satu kemandoran. **Menghapus dua kolom lama** di `fai_ipd`; cadangkan dulu bila sudah ada data IPD |
| 9 | `migrations/011_foto_profil.sql` | Foto profil akun |
| 10 | `migrations/012_jejak_ipd.sql` | Jalur GPS inspeksi IPD |

Nomor 004 sengaja tidak ada di urutan ini: dijalankan sesudah login API dibuat (3.4). Semua migrasi aman diulang: pada
simulasi, seluruh migrasi dijalankan dua kali berturut-turut tanpa galat dan tanpa mengubah data. Bila migrasi 007
berhenti dengan pesan tentang catatan ganda lama, hapus lunak yang berlebih lalu ulangi.

Contoh satu perintah (ulangi untuk tiap berkas):

```
sqlcmd -S <server> -d fai26 -U <akun_dba> -I -b -i migrations\002_master.sql
```

### 3.4 Login API berhak sempit, lalu migrasi 004

API tidak boleh memakai `sa` atau akun pemilik skema. Buat login khusus yang hanya boleh membaca dan menulis data:

```sql
-- pada master
CREATE LOGIN fai26_api WITH PASSWORD = '<kata sandi panjang acak>', CHECK_POLICY = ON;
-- pada database fai26
CREATE USER fai26_api FOR LOGIN fai26_api;
ALTER ROLE db_datareader ADD MEMBER fai26_api;
ALTER ROLE db_datawriter ADD MEMBER fai26_api;
```

Lalu jalankan `migrations/004_audit_log_deny.sql` setelah mengganti `[api_fai26]` di berkas itu dengan nama user API
(`[fai26_api]` pada contoh ini). Migrasi ini membuat `audit_log` hanya bisa ditambah oleh akun API. Bukti (pada simulasi,
dijalankan sebagai akun API): `UPDATE` dan `DELETE` pada `audit_log` ditolak dengan "permission was denied", dan
`CREATE TABLE` ditolak. Akun ERP untuk HVDM ditambahkan ke peran `fai26_hvdm` (perintahnya ada di komentar
`006_peran_hvdm.sql`); jangan beri akun ERP hak tulis pada tabel.

### 3.5 Unit dan akun ADMIN pertama

Karena belum ada akun, akun ADMIN pertama dibuat lewat SQL dengan hash bcrypt. Hash dibuat oleh program `hashpw` yang
memakai fungsi yang sama dengan API (jadi pasti cocok saat login), tanpa mengirim kata sandi ke situs lain:

```
go build -o hashpw.exe .\cmd\hashpw
hashpw.exe                              (ketik kata sandi minimal 8 karakter lalu Enter; hash tercetak)
```

Lalu jalankan skrip data awal, memakai akun pemilik skema:

```
sqlcmd -S <server> -d fai26 -U <akun_dba> -I -b -i scripts\awal_unit_admin.sql ^
  -v KODE_UNIT="KP_XXX" NAMA_UNIT="Nama Kebun" ERP_DB="nama_db_erp" TAHUN_CLUSTER="2026" ^
     USERNAME="admin" NAMA_ADMIN="Administrator" HASH_SANDI="<hash dari hashpw>"
```

Skrip itu membuat unit dan akun ADMIN (juga AUDITOR, karena `ADMIN` sendiri tidak punya hak membuka audit log), dengan
kewajiban mengganti kata sandi saat login pertama. Aman diulang. Blok, TPH, dan pemanen diisi lewat web admin atau
disalin dari ERP.

## 4. Konfigurasi API (`.env` di folder pemasangan)

Lihat `.env.example`. Yang wajib: `DB_*` (atau `FAI26_DSN`) memakai **akun API** dari 3.4, `FAI26_JWT_SECRET` (acak,
minimal 32 karakter), dan `FAI26_MEDIA_DIR` (folder foto; pastikan ada ruang dan dicadangkan). Contoh untuk produksi:

```
DB_HOST=<server>
DB_PORT=1433
DB_NAME=fai26
DB_USER=fai26_api
DB_PASSWORD=<kata sandi akun API>
DB_ENCRYPT=true
DB_TRUST_CERT=false          # true hanya bila sertifikat SQL Server tidak dipercaya dan Anda tahu risikonya
FAI26_JWT_SECRET=<teks acak minimal 32 karakter>
FAI26_ADDR=127.0.0.1:8080    # hanya mendengar di lokal; reverse proxy yang menghadap luar
FAI26_MEDIA_DIR=C:\fai26\media
FAI26_WEB_DIR=C:\fai26\dist  # hasil `npm run build` (isi dist/), bila web dilayani oleh API
FAI26_PERCAYAI_PROXY=true    # karena di belakang reverse proxy (lihat bagian 7)
FAI26_JEJAK_SIMPAN_HARI=0    # lama simpan jalur GPS (hari); 0 = tanpa batas, lihat bagian 9
```

## 5. Membangun dan menyalin

Di mesin build:

```
cd fai26-web && npm ci && npm run build        # hasilnya fai26-web/dist
cd ../fai26-api && go build -o fai26-api.exe ./cmd/api
```

Salin `fai26-api.exe`, `.env`, dan folder `dist` ke server (mis. `C:\fai26`), bersama `scripts\periksa-server.ps1`.
Program API membaca `.env` dari **folder kerjanya**, jadi jalankan dari folder itu.

## 6. Menjalankan

Percobaan manual (simulasi memakai cara ini): dari folder pemasangan jalankan `fai26-api.exe`. Log awal yang benar
memuat satu baris "perawatan: ..." (keadaan penyimpanan jalur GPS) dan "fai26-api mendengar di <alamat>". Cek
`GET /api/health` (200 berarti server dan database hidup).

### Sebagai layanan Windows [belum dicoba]

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

## 7. HTTPS lewat reverse proxy (wajib untuk HP) [belum dicoba]

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
dan pasang CA itu di HP. Membuka port di firewall untuk HP termasuk perubahan pengaturan server yang perlu persetujuan
pemilik server.

Isi alamat server ini di aplikasi HP (layar Akun) atau pada build rilis (`DEFAULT_SERVER_URL`).

## 8. Pemeriksaan setelah terpasang

### 8.1 Skrip pemeriksaan

```
powershell -ExecutionPolicy Bypass -File scripts\periksa-server.ps1 -Folder C:\fai26
powershell -ExecutionPolicy Bypass -File scripts\periksa-server.ps1 -Folder C:\fai26 -Alamat https://fai26.contoh-kebun.co.id
```

Skrip hanya membaca (tidak mengubah apa pun, tidak mencetak kata sandi) dan memeriksa: isi `.env` (kunci cukup
panjang, bukan `sa`, enkripsi, alamat dengar), program, folder foto, web, sambungan database memakai akun API, 11
migrasi (skema dasar sampai 012), hak akun API (tidak bisa mengubah `audit_log`, bukan sysadmin atau `db_owner`),
unit dan akun ADMIN, serta `GET /api/health` dan halaman web. Kode keluar adalah jumlah `[GAGAL]`; 0 berarti siap.
`[PERINGATAN]` tidak menggagalkan.

Dua pemeriksaan (migrasi 003 dan 006) menghasilkan `[PERINGATAN]` pada akun API yang benar: prosedur dan peran sengaja
tidak terlihat oleh akun berhak sempit. Periksa sebagai DBA:

```sql
SELECT COUNT(*) FROM sys.objects WHERE name IN ('v_fai_panen_siap_impor','sp_fai_impor_ok');   -- harus 2
SELECT COUNT(*) FROM sys.database_principals WHERE name = 'fai26_hvdm' AND type = 'R';          -- harus 1
```

Pada simulasi, skrip dicoba pada tiga keadaan: server benar (siap, 6 peringatan wajar), satu migrasi hilang (tepat satu
`[GAGAL]`, 012), dan konfigurasi sengaja salah (6 `[GAGAL]`: program tidak ada, kunci pendek, `sa`, alamat terbuka
bersama proxy dipercaya, web tidak ada, sambungan database gagal).

### 8.2 Pemeriksaan manual

- Web admin terbuka di `https://<alamat>/`; login dengan akun ADMIN dan ganti kata sandi saat diminta.
- Buat blok dan TPH; cetak QR sesuai `progres/SPEK_QR_TPH.md`.
- Login dari HP, master turun (blok, TPH, pemanen), catat satu panen uji, setujui, kirim; cek muncul di
  `SELECT * FROM dbo.v_fai_panen_siap_impor` dan di web admin (Catatan Panen).
- Batas laju aktif: 31 login berturut-turut dari satu alamat dijawab 429.
- Akun terkunci 15 menit setelah 5 kata sandi salah. Membukanya lebih cepat (sebagai DBA):
  `UPDATE dbo.app_user SET gagal_login = 0, terkunci_sampai = NULL WHERE username = '<nama>';`

## 9. Kebijakan data lokasi (jalur GPS)

Jalur GPS inspeksi adalah data lokasi petugas. Pengamannya: setiap pembukaan jalur di web tercatat di audit (`LIHAT_JALUR`),
dan `FAI26_JEJAK_SIMPAN_HARI=N` menghapus otomatis jalur inspeksi yang sudah diperiksa dan lebih tua dari N hari
(audit `HAPUS_JEJAK_LAMA`). Bawaannya 0 (tanpa batas) karena penghapusan tidak bisa dibatalkan dan angkanya keputusan
pembimbing atau kebun (usulan 90 hari, pertanyaan 22-8). Isi nilainya **sebelum** server dipakai penuh, lalu mulai ulang
API. Cadangan database menyimpan jalur yang sudah dihapus sampai cadangan lama diganti, jadi atur juga umur cadangan.

## 10. Cadangan, pemeliharaan, dan kembali ke versi lama

### 10.1 Cadangan dan pemulihan (dicoba pada simulasi)

Cadangkan database `fai26` dan folder `FAI26_MEDIA_DIR` bersama-sama (foto bukti dirujuk dari database). Contoh:

```sql
BACKUP DATABASE fai26 TO DISK = N'<folder cadangan>\fai26.bak' WITH CHECKSUM, INIT, COMPRESSION;
RESTORE VERIFYONLY FROM DISK = N'<folder cadangan>\fai26.bak' WITH CHECKSUM;
```

Untuk membuktikan cadangan bisa dipulihkan, pulihkan ke database dengan nama lain lalu bandingkan isinya:

```sql
RESTORE DATABASE fai26_pulih FROM DISK = N'<folder cadangan>\fai26.bak'
  WITH MOVE N'fai26' TO N'<folder data>\fai26_pulih.mdf', MOVE N'fai26_log' TO N'<folder data>\fai26_pulih.ldf';
```

Pada simulasi, hitungan pengguna, audit, dan permission database asli dan hasil pemulihan sama. Pemulihan ke **server
lain** akan membuat user database `fai26_api` yatim (login-nya belum ada di server itu); buat login dengan nama sama lalu
`ALTER USER fai26_api WITH LOGIN = fai26_api;` [belum dicoba]. Ganti database dengan hasil pemulihan
(`RESTORE ... WITH REPLACE` setelah API dihentikan) juga [belum dicoba].

### 10.2 Memperbarui versi

1. Hentikan API. 2. Cadangkan database (10.1). 3. Ganti `fai26-api.exe` dan isi `dist`. 4. Jalankan migrasi baru
(semua aman diulang; periksa `docs/API.md` bagian riwayat perubahan untuk yang baru). 5. Mulai API. 6. Jalankan
`periksa-server.ps1`. `docs/API.md` memuat versi tiap rilis.

### 10.3 Kembali ke versi lama

Bila versi baru bermasalah: hentikan API, kembalikan `fai26-api.exe` dan `dist` lama, dan bila migrasi baru sudah
dijalankan pulihkan database dari cadangan 10.2 langkah 2 (migrasi tidak punya skrip pembalik; migrasi 010 misalnya
menghapus kolom). Menyimpan satu salinan `fai26-api.exe` dan `dist` versi sebelumnya adalah syarat rollback.

### 10.4 Lain-lain

Ganti `FAI26_JWT_SECRET` bila bocor: semua token akses tidak berlaku, pengguna login ulang (sesi refresh di database
tetap berlaku, tetapi token akses baru ditandatangani dengan kunci baru).

## 11. Masalah yang ditemui saat simulasi dan yang belum dicoba

| Masalah | Penyebab | Penanganan |
|---|---|---|
| Login akun ADMIN pertama ditolak walau kata sandi benar | PowerShell 5.1 menyisipkan tanda BOM di depan teks yang dialirkan lewat pipa, sehingga hash dibuat dari kata sandi plus BOM | `hashpw` kini membuang BOM; tetap, jalankan `hashpw` lalu ketik kata sandinya langsung |
| `sqlcmd -v NAMA="Kebun Contoh"` gagal "Invalid argument" di PowerShell | PowerShell 5.1 memecah argumen berspasi | Jalankan dari `cmd.exe`, atau hindari spasi pada nilai |
| `sqlcmd` "Access is denied" pada berkas | Jalur ber-`/` dari terminal bash | Pakai jalur Windows ber-`\` |
| Migrasi 003 dan 006 tampak hilang oleh skrip pemeriksa | Akun API tidak boleh melihat prosedur dan peran | Periksa sebagai DBA (8.1) |
| Akun terkunci setelah beberapa percobaan salah | Batas 5 kata sandi salah, 15 menit | Lihat 8.2 |
| Akun ADMIN tidak bisa membuka Audit Log | `ADMIN` tidak punya `audit.lihat` | Beri juga role `AUDITOR` (skrip 3.5 sudah melakukannya) |

**Belum dicoba (perlu server sungguhan atau persetujuan perubahan sistem):** layanan Windows dengan NSSM dan akun layanan;
reverse proxy dan HTTPS dengan sertifikat; pembukaan port firewall dan akses dari HP lewat jaringan; SQL Server milik
kebun (versi, kolasi, izin membuat database); pemulihan ke server lain dan pergantian database dengan `REPLACE`;
migrasi pada database yang sudah berisi data nyata (migrasi 010 menghapus kolom); jadwal cadangan otomatis; akun ERP
untuk peran `fai26_hvdm`; dan uji beban.
