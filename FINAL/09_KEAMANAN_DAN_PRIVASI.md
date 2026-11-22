# 09. Keamanan dan Privasi

Kumpulan seluruh mekanisme keamanan dan perlindungan data yang **sudah diterapkan**, kebijakan data lokasi, dan batas perlindungannya yang jujur. Semua bagian bertanda **[Fakta]** terbukti di kode atau tes; bagian **[Usulan]** menunggu keputusan.

## 1. Autentikasi dan sesi [Fakta]

| Mekanisme | Nilai |
|---|---|
| Hash kata sandi server | **bcrypt** (`DefaultCost`); `password_hash` NVARCHAR(255); `HashDummy` mencegah kebocoran waktu respons apakah username ada |
| Panjang kata sandi minimal | 8 karakter (admin membuat dan mereset) |
| Kunci akun | 5 kata sandi salah berturut-turut: terkunci **15 menit** (423); di HP dan server (terpisah) |
| Token akses | JWT **HS256**, 15 menit (`FAI26_AKSES_MENIT`); kunci `FAI26_JWT_SECRET` ≥ 32 karakter |
| Refresh token | 30 hari, **diganti tiap dipakai** (rotasi); hanya **hash SHA-256** disimpan (`auth_sesi.refresh_token_hash`) |
| Hak akses | Dibaca ulang dari database **di setiap panggilan** (nonaktif langsung berlaku) |
| Identitas | Pencatat, perangkat, `user_id` audit selalu dari token, **bukan isi kiriman** |
| Perangkat | `device_id` UUID dari HP; perangkat nonaktif ditolak (403) dan sesinya dicabut |
| Reset password admin | `wajib_ganti_password = 1`; ganti password sendiri mencabut sesi perangkat lain |
| Login offline di HP | Verifier **PBKDF2-HMAC-SHA256**, 100.000 iterasi, salt 16 byte; berlaku 7 hari |
| Token di HP | Disimpan **terenkripsi** (AES-256-GCM, kunci di Android Keystore); dikecualikan dari cadangan Android |
| Kepemilikan token (B-15) | Token dikaitkan dengan akun aktif; token milik akun lain dibuang saat login offline berpindah akun |

## 2. Perlindungan API [Fakta]

- **Batas laju** per IP: login/refresh 30 per menit, seluruh API 600 per menit (429 + `Retry-After`). `FAI26_PERCAYAI_PROXY=true` hanya di belakang proxy yang menimpa `X-Forwarded-For` (header tidak dapat dipalsukan klien).
- **Batas waktu** 20 detik per permintaan; ukuran: foto ≤ 8 MB (413), audit ≤ 500 baris, isi JSON dibatasi.
- **Validasi unggahan foto:** tanda JPEG, SHA-256, ukuran; foto profil diproses ulang menjadi JPEG persegi 384 px (EXIF dibuang).
- **Galat seragam**, 5xx hanya pesan umum (rincian di log server).
- **SQL hanya di lapisan `repository`** dengan parameter (tanpa penyambungan string dari masukan).
- **CSP** untuk web: `img-src 'self' data: blob: https://tile.openstreetmap.org`; `Referrer-Policy: strict-origin-when-cross-origin`.
- **HTTPS** di reverse proxy (build rilis HP hanya mau HTTPS).

## 3. Basis data [Fakta]

- **Akun API berhak sempit**: `db_datareader` + `db_datawriter`; bukan `sa`, bukan pemilik skema. **Migrasi 004** memberi `DENY UPDATE, DELETE` pada `audit_log` (hanya-tambah). Terbukti pada simulasi: akun API ditolak `UPDATE`/`DELETE` pada `audit_log` dan `CREATE TABLE`.
- **Role `fai26_hvdm`** untuk ERP: hanya `SELECT` pada dua view dan `EXECUTE` pada empat prosedur; tanpa hak tulis tabel; terbukti `UPDATE` langsung ditolak.
- **Hapus lunak** dan kolom audit enam; `audit_log` mencatat aksi penting (BUAT, UBAH, HAPUS, SETUJU, TOLAK, LOGIN, LOGIN_GAGAL, TERIMA, IMPOR, IMPOR_GAGAL, IMPOR_ULANG, RESET_PASSWORD, GANTI_PASSWORD, LIHAT_JALUR, HAPUS_JEJAK_LAMA).
- **Pemisahan lingkungan**: tes integrasi hanya di `fai26_tes`; `fai26_dev` berisi data asli kebun; kredensial hanya di `.env` (tidak di-commit).

## 4. Kebijakan data lokasi (jalur GPS inspeksi) [Fakta kecuali dinyatakan]

Jalur GPS adalah **data lokasi petugas**: ke mana berjalan, kapan, berapa lama. Aturan yang berlaku:

| Pertanyaan | Ketentuan | Keadaan |
|---|---|---|
| Apa yang direkam | Koordinat GPS **hanya selama petugas mencatat inspeksi**; akurasi ≤ 25 m, jarak ≥ 3 m; berhenti otomatis 8 jam; petugas dapat mematikan sakelar | Berlaku |
| Untuk apa | Memastikan inspeksi dilakukan di blok yang dicatat dan membantu asisten menilai. **Bukan** untuk menilai kecepatan kerja atau mengawasi di luar inspeksi | Tertulis; aplikasi tidak merekam di luar layar inspeksi |
| Siapa melihat | Hak `ipd.jejak`: ASISTEN, ASKEP, KTU, ADMIN. **Mandor dan krani tidak** (server mengirim `null`) | Berlaku |
| Pembukaan dicatat | Setiap kali jalur dikirim: audit `LIHAT_JALUR` (pengguna, perangkat, IP, jumlah titik); sekali per 10 menit per pengguna dan inspeksi; **bila pencatatan gagal, jalur tidak dikirim** | Berlaku (API 0.21.0) |
| Lama simpan | `FAI26_JEJAK_SIMPAN_HARI=N`: tiap 6 jam server menghapus titik jalur inspeksi berstatus **bukan MENUNGGU** yang tanggalnya lebih tua dari N hari, dalam satu transaksi dengan audit `HAPUS_JEJAK_LAMA`; data inspeksi, pokok, dan foto tidak tersentuh | Terpasang, **bawaan 0 = mati** (keputusan kebijakan belum diambil) |
| Nilai lama simpan | **Usulan 90 hari** (laporan loses dibahas bulanan; koreksi biasanya muncul dalam 1 sampai 2 bulan) | **[Usulan]** pertanyaan 22-8 |
| Salinan di HP | Jalur lokal dihapus bersama draf, dan **setelah inspeksi DISETUJUI** | Berlaku (belum diuji di ponsel) |
| Pemberitahuan petugas | Keterangan di layar inspeksi dan di atas peta web | Berlaku (belum difoto di HP) |
| Peta gabungan | Setiap jalur yang tampil diaudit; memilih petugas membatasi jalur yang dibuka; maksimal 60 jalur sekali muat | Berlaku |
| Ekspor | Hanya ringkasan jalur (jarak, lama, jumlah titik), bukan koordinat | Berlaku |

**Mengapa penghapusan bawaannya mati:** penghapusan tidak dapat dibatalkan dan angkanya keputusan pembimbing/kebun. Sarana sudah ada; nilai diisi setelah ada keputusan, lalu API dimulai ulang.

## 5. Privasi data lain

- **Foto profil** dan nama akun hanya terlihat pengguna yang berhak; foto tidak memuat EXIF setelah diproses.
- **Nama pemanen** adalah data pegawai; daftar pencocokan master (`lampiran/cocokkan_pemanen.csv`) hanya untuk pencocokan dengan ERP, jangan disebarkan.
- **Foto bukti panen dan IPD** disimpan di folder media server dengan nama berbasis UUID; unduh lewat endpoint yang berhak (`panen.lihat`, `ipd.lihat`).
- **Ubin peta OpenStreetMap**: peramban pengguna meminta ubin ke `tile.openstreetmap.org`; penyedia peta melihat IP dan wilayah yang dibuka. Bila dianggap masalah, sajikan peta dari server sendiri.

## 6. Batas perlindungan yang jujur

1. **Akses langsung ke database tidak melewati aplikasi** sehingga tidak tercatat `LIHAT_JALUR`; perlindungannya ada pada hak akses server dan database.
2. **Cadangan database** menyimpan jalur yang sudah dihapus sampai cadangan diganti; atur umur cadangan.
3. **Audit tidak mencegah**, hanya memungkinkan penelusuran; tidak ada peringatan otomatis untuk pola mencurigakan (mis. satu orang membuka puluhan jalur dalam sehari) (O-17).
4. **Penghapusan otomatis belum dijalankan pada data asli**; pembuktiannya hanya pada database uji, dan belum diaktifkan.
5. **Hapus jalur lokal di HP dan keterangan privasi di layar inspeksi belum diuji dan difoto di ponsel** (O-13).
6. Belum ada persetujuan tertulis petugas atau pemberitahuan resmi perusahaan; setahu penulis, UU Pelindungan Data Pribadi (UU No. 27 Tahun 2022) mengatur tujuan dan lama penyimpanan data pribadi; **perlu dikonsultasikan** dengan pihak berwenang sebelum aturan dianggap final.
7. HP versi debug memakai HTTP ke `localhost` lewat USB; **tidak untuk lapangan**. Rilis wajib HTTPS.
8. Akun ADMIN pertama diberi kata sandi awal oleh pemasang dan **wajib diganti** pada login pertama; kata sandi akun uji (di database uji) tidak boleh ada di server sungguhan.

## 7. Keputusan keamanan yang menunggu

| Butir | Keputusan |
|---|---|
| Lama simpan jalur dan siapa melihat (22-8) | Ptoni dan pembimbing; juga umur cadangan |
| Persetujuan firewall, server sungguhan, HTTPS (22-2, 22-3) | Ptoni |
| Notifikasi dorong (22-6) | Butuh akun Firebase; belum diputuskan |
| Pembaruan pustaka dan pemindaian kerentanan | Belum dijadwalkan |
