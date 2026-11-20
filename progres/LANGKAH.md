# fAI26: Langkah Rinci per Fase

Pasangan dari [PROGRES.md](PROGRES.md). Setiap langkah punya tujuan, cara kerja, dan cara memastikan sudah selesai. Kerjakan berurutan, kecuali disebut lain.

Perintah memakai PowerShell di folder `sawit_grading`, kecuali disebut lain.

---

## Fase 0. Tutup prototipe kamera

### 0.1 Pasang dan uji mode scan video di HP
- **Tujuan:** memastikan tracker menghitung tandan unik di HP, bukan hanya di tes.
- **Cara:**
  1. Sambungkan HP (USB debugging menyala), cek `adb devices`.
  2. `flutter build apk --debug --target-platform android-arm64`, lalu `adb install -r build\app\outputs\flutter-apk\app-debug.apk`.
  3. Buka aplikasi, tekan MULAI, sisir tumpukan atau gambar TBS pelan satu arah, tekan SELESAI.
  4. Coba juga: diam di satu tandan (harus tetap terhitung 1), geser cepat, dan tutup kamera sebentar.
- **Selesai bila:** hitungan naik saat menyisir, tidak melonjak saat kamera diam, dan halaman Hasil menampilkan angka yang sama dengan panel.
- **Catat:** kelancaran (lancar, patah-patah, berat) dan tipe HP.

### 0.2 Uji simpan dan riwayat di HP
- **Cara:** setelah SELESAI, isi TPH dan pemanen, tekan Simpan, buka Riwayat.
- **Selesai bila:** catatan muncul di Riwayat dengan foto, jumlah per kelas, dan status "Belum terkirim". Tutup aplikasi, buka lagi, data masih ada.

### 0.3 Uji offline
- **Cara:** nyalakan mode pesawat, ulangi 0.1 dan 0.2.
- **Selesai bila:** semuanya jalan sama seperti saat online.

### 0.4 Rapikan build
- Jalankan `flutter doctor --android-licenses` (keputusan Ptoni).
- Tambah `ndkVersion = "28.2.13676358"` di blok `android {}` pada `android/app/build.gradle.kts`, lalu build ulang.

---

## Fase 1. Database lokal SQLite

### 1.1 Persiapan paket
- Pakai `drift` (keputusan Ptoni, 10 Nov): tabel dan query bertipe, migrasi terotomatisasi. Tambah `drift`, `drift_flutter`, `drift_dev`, dan `build_runner`. `path_provider` sudah ada.
- Bila nanti pindah ke Android native: padanannya Room. Skema SQL dari ERD sama, hanya lapisan aksesnya yang ditulis ulang.

### 1.2 Skema
- Salin DDL dari `erd/fAI26_mobile_sqlite.sql` ke satu file (misalnya `lib/db/skema.dart` atau berkas asset `.sql`).
- Aktifkan `PRAGMA foreign_keys = ON` saat membuka database.
- Buat mekanisme versi skema (`onCreate`, `onUpgrade`) supaya perubahan ERD nanti bisa dimigrasi.
- **Selesai bila:** database terbentuk dengan 22 tabel dan tes membuktikan semua tabel ada.

### 1.3 Data awal
- `kelas_grading`: 6 baris dengan `label_model` sesuai urutan model (0 Abnormal, 1 Empty Bunch, 2 Overripe, 3 Ripe, 4 Underripe, 5 Unripe). Isi `erp_kolom` sesuai pemetaan ERD Bab 8, kosongkan untuk unripe dan underripe.
- `role` (KRANI, MANDOR, ASISTEN, ADMIN, AUDITOR), `permission`, dan `role_permission` sesuai tabel ERD Bab 4.2.
- `model_versi`: satu baris untuk `best.tflite` (nama, versi, hash file).
- **Catatan:** urutan kelas model harus tetap sama dengan `label_model`. Bila model dilatih ulang, cek ulang.

### 1.4 Repository dan audit
- Satu lapisan repository yang: mengisi `dibuat_*`, `diubah_*`, `dihapus_*`; menyaring `dihapus_pada IS NULL` otomatis; menulis `audit_log` (nilai lama dan baru, hanya kolom yang berubah) dalam transaksi yang sama.
- Waktu disimpan UTC (`YYYY-MM-DDTHH:MM:SSZ`), ditampilkan WIB.
- Hapus tidak pernah memakai `DELETE` pada tabel data.
- **Selesai bila:** tes menunjukkan ubah satu baris menghasilkan satu baris `audit_log` dengan nilai lama dan baru, dan hapus hanya mengisi `dihapus_pada`.

### 1.5 Data master contoh
- Berkas contoh (JSON atau CSV di `assets/`) berisi unit `KP_STL`, divisi, beberapa blok, TPH dengan `qr_kode`, beberapa pemanen, dan `periode_rotasi`. Isi sendiri sampai master dari ERP tersedia.
- Muat ke SQLite saat pertama kali jalan dan catat di `master_sync`.

### 1.6 Pindahkan alur simpan scan
- Ganti `ScanStore` (JSON) dengan repository: tulis `panen_header` (status DRAFT atau SIAP_KIRIM), satu baris `panen_grading` per kelas (`jml_ai` dari tracker, `jml_final` setelah koreksi, `diubah_manual`), `foto`, dan `panen_pemanen` bila pemanen diisi.
- Riwayat membaca dari SQLite.
- Hapus `scan_store.dart` dan model JSON setelah semuanya pindah.
- **Selesai bila:** simpan lalu buka Riwayat menampilkan data dari SQLite, dan semua tes lolos.

---

## Fase 2. Login dan hak akses

### 2.1 Layar login dan akun lokal
- Layar login (username, password). Untuk pengembangan, seed satu akun KRANI dan satu MANDOR.
- Simpan hash verifikasi lokal (`offline_hash` dengan `offline_salt`), bukan password.

### 2.2 Login offline
- Periksa `aktif`, `terkunci_sampai`, `offline_berlaku_sampai` (7 hari), lalu bandingkan hash.
- Gagal 5 kali mengunci akun 15 menit. Berhasil mengembalikan `gagal_login` ke 0.
- Tulis `audit_log` `LOGIN` atau `LOGIN_GAGAL` dengan keterangan `OFFLINE`.

### 2.3 Hak akses di UI
- Muat permission dari SQLite. Tampilkan atau sembunyikan tombol sesuai tabel ERD Bab 4.2 (misalnya hanya MANDOR dan ASISTEN yang punya `panen.koreksi`).
- Server tetap memeriksa ulang saat API ada (Fase 5).

### 2.4 Login online
- Dikerjakan setelah API tersedia (Fase 5): token di `flutter_secure_storage`, refresh, logout paksa bila akun atau HP dinonaktifkan.

---

## Fase 3. Alur scan TPH lengkap

### 3.1 Layar pilih tempat
- Tanggal panen (bawaan hari ini; tanggal panen bukan tanggal input), unit, blok dan TPH, pemanen opsional dari daftar.

### 3.2 Scan QR TPH
- Baca teks QR, validasi format `FAI26|TPH|1|<unit>|<blok>|<no_tph>`, cari di master lokal lewat `qr_kode`. Tolak QR yang bukan milik aplikasi.
- Cadangan pilih manual dari daftar blok dan TPH.
- Skrip pembuat QR (Python atau Go) beserta lembar cetak: tingkat koreksi H, ada teks di bawah QR. Dikerjakan terpisah dari aplikasi.

### 3.3 Mode scan
- Sudah ada: MULAI, SELESAI, hitungan langsung.
- Tambah: peringatan cahaya, terlalu cepat, buram; pemeriksaan baterai dan ruang simpan sebelum mulai.
- Keyframe: simpan frame terbaik per tandan yang lolos gerbang (confidence tertinggi). Simpan sebagai `foto` dengan kotak di tabel `deteksi`.
- Pertimbangan: simpan keyframe dengan ukuran kecil supaya hemat ruang dan kuota.

### 3.3a Verifikasi lokasi GPS
- **Tujuan:** memastikan pencatat benar-benar berada di TPH saat scan (permintaan Ptoni). QR saja tidak cukup karena QR bisa difoto orang lain.
- **Cara kerja:**
  1. Tambah izin `ACCESS_FINE_LOCATION` di manifest dan minta izin saat pertama kali dipakai. Jelaskan di layar bahwa lokasi hanya dicatat saat scan.
  2. Saat scan QR TPH, ambil satu titik GPS dan tampilkan peringatan dini bila sudah jauh dari TPH.
  3. Saat SELESAI, ambil titik GPS lagi. Titik inilah yang disimpan di `gps_lat` dan `gps_lon`.
  4. Catat juga akurasi dalam meter, waktu titik GPS (dari satelit, bukan dari jam HP), dan penanda lokasi palsu dari Android (`isMocked`).
  5. Hitung jarak ke `tph.lat` dan `tph.lon` (rumus haversine). Hasilnya `jarak_ke_tph_m`.
  6. Tentukan `status_lokasi`: `DI_LOKASI` (jarak dalam radius dan akurasi cukup), `DI_LUAR` (jarak melebihi radius), `AKURASI_RENDAH` (akurasi lebih buruk dari ambang), `TANPA_GPS` (izin ditolak, GPS mati, atau titik tidak didapat), `LOKASI_PALSU` (mock terdeteksi), `TPH_BELUM_BERKOORDINAT` (master TPH belum punya koordinat).
  7. Status ikut tersimpan di catatan dan terkirim ke server. Mandor melihatnya saat menyetujui.
- **Nilai awal [Usulan], disetel saat uji lapangan:** radius 50 meter, akurasi minimal 30 meter. Di bawah tajuk pohon GPS HP bisa meleset puluhan meter.
- **Perilaku bila di luar lokasi [Usulan]:** hanya menandai dan memberi peringatan ke krani, tidak memblokir simpan. Alasannya GPS di kebun tidak selalu andal, jadi pemblokiran bisa menghentikan kerja yang sah. Mandor yang memutuskan saat menyetujui. Keputusan akhir di Ptoni (B13).
- **Tanpa sinyal internet:** GPS memakai satelit, jadi tetap jalan. Titik pertama bisa lama (cold start). Beri waktu tunggu dan tombol "ambil ulang".
- **Koordinat TPH:** `tph.lat` dan `tph.lon` perlu diisi. Cara paling mudah: layar "rekam koordinat" yang dipakai saat memasang QR di TPH. Tanpa koordinat, pemeriksaan jarak tidak bisa jalan.
- **Batas yang harus dipahami:** GPS bisa dipalsukan dengan aplikasi lokasi palsu. Deteksi mock dari Android membantu tetapi tidak sempurna. Fitur ini menyulitkan kecurangan, bukan membuktikan 100%. Foto keyframe tetap bukti pendukung.
- **Privasi:** lokasi hanya diambil selama sesi scan, tidak ada pelacakan terus-menerus. Beri tahu pengguna.
- **Selesai bila:** tes unit rumus jarak dan penentuan status lolos. Uji di HP: di dekat titik terbaca `DI_LOKASI`, di tempat jauh terbaca `DI_LUAR`, GPS dimatikan terbaca `TANPA_GPS`.
- **Paket:** `geolocator` di Flutter. Di Android native: `FusedLocationProviderClient`.

### 3.4 Layar tinjau dan simpan
- Ringkasan per kelas dan galeri keyframe, koreksi `jml_final`, isi brondolan dalam kilogram dan catatan. Jumlah tandan dalam janjang.
- Persetujuan mandor: mandor memeriksa catatan krani, boleh mengoreksi (tercatat di `audit_log`), lalu status menjadi DISETUJUI sebelum SIAP_KIRIM.
- Pindai ulang: sesi lama ditandai batal, kembali ke pilih tempat.
- Validasi sebelum simpan: tanggal tidak lebih dari hari ini, blok dan TPH ada di master, total nol dengan foto (peringatan), banyak deteksi confidence rendah (sarankan ulang), dan status lokasi bukan `DI_LOKASI` (peringatan, lihat 3.3a).

### 3.5 Status dan daftar
- Status `panen_header`: DRAFT, SIAP_KIRIM, TERKIRIM, DITERIMA, DITOLAK. DITERIMA tidak boleh diubah di HP.
- Daftar hari ini dengan status per catatan.

### 3.6 Istilah tampilan
- Ganti nama kelas ke istilah kebun setelah dicocokkan dengan pembimbing. Hanya pemetaan tampilan, model tidak diubah.

---

## Fase 4. Sinkronisasi

### 4.1 Antrean
- Saat Simpan: tambah baris `sync_queue` untuk PANEN, FOTO, dan AUDIT dengan status MENUNGGU.

### 4.2 Pengirim
- Berjalan saat ada sinyal dan pengguna login. Kirim dari yang paling lama.
- Urutan: `panen_header` dan anak-anaknya, lalu `audit_log`, lalu foto.
- Gagal: tetap MENUNGGU, coba lagi bertahap; gagal terus ditandai GAGAL dengan tombol "Kirim ulang".
- Server menjawab 200 bila `panen_id` sudah ada, jadi kirim ulang aman.

### 4.3 Master turun
- Unduh unit, divisi, blok, TPH, pemanen, periode, dan data akses. Bandingkan `versi_server` di `master_sync`. Master nonaktif tetap disimpan.

### 4.4 Status balik
- `GET /api/panen?sejak=...` untuk mengisi `status` dan `pesan_server`.

---

## Fase 5. API dan database server

### 5.1 Database
- Buat database `fai26` dari `erd/fAI26_server_sqlserver.sql`. Skrip belum pernah dijalankan di SQL Server, jadi uji dan perbaiki dulu.
- `audit_log` hanya boleh INSERT (`DENY UPDATE, DELETE` untuk akun API).

### 5.2 API Golang
- Endpoint sesuai ERD Bab 4.6 dan flow komunikasi: auth, master, panen, media, audit.
- JWT pendek (15 menit) dengan refresh token dalam bentuk hash.
- Pemeriksaan permission di setiap panggilan.
- Master dibaca dari database ERP hanya baca.

### 5.3 Media
- Terima keyframe JPEG. Klip video penuh (bila disimpan) hanya diunggah lewat Wi-Fi.

---

## Fase 6. Sisi ERP

Dikerjakan Ptoni di Visual Studio (tidak bisa dilakukan dari sini).

1. Buat TCode HVDM sebagai salinan HVD1.
2. Baca `fai_panen` berstatus BARU berdasarkan tanggal dan unit.
3. Validasi blok, pemanen, dan rotasi.
4. Simpan ke `hvd1m` dan `hvd1da` lewat kode aplikasi (bukan menulis langsung ke tabel), isi `JJG_*` sesuai pemetaan ERD Bab 8.
5. Tulis `fai_impor_log` dan `audit_log` IMPOR, ubah `status_erp` menjadi DIIMPOR atau DITOLAK.
6. Publish lewat DevAgro Hub.

Tulis juga username fAI26 ke `hvd1da.KETDETAIL` supaya orang ERP bisa melihat krani pencatatnya.

---

## Fase 7. Uji lapangan dan laporan

### 7.0 Uji GPS di TPH
- Di beberapa TPH (terbuka dan di bawah pohon): catat akurasi, waktu titik pertama tanpa sinyal, dan jarak terbaca. Setel radius dan ambang akurasi dari data ini.
- Coba aplikasi lokasi palsu untuk memastikan `LOKASI_PALSU` terdeteksi.

### 7.1 Uji scan di TPH
- Untuk beberapa TPH: hitung manual dulu, lalu scan, catat selisih. Ulangi pada kondisi cahaya berbeda dan tumpukan kecil dan besar.
- Catat tandan yang terhitung dobel (keluar lalu masuk layar) dan yang terlewat (tertutup).

### 7.2 Penyetelan
- Parameter di `BunchTracker`: `highConf` 0,40, `minHits` 5, `minArea` 0,03, `maxLost` 20, `minIouHigh` 0,30, `minIouLow` 0,50. Ubah satu per satu dan bandingkan selisih.

### 7.3 Akurasi jujur
- Foto 20 sampai 30 tandan kebun sungguhan yang belum pernah dilihat model. Laporkan akurasi dari sini, bukan dari mAP Colab.

### 7.4 Perbaikan model: dibatalkan
- Diputuskan tidak melatih ulang (10 Nov). Pembimbing sebenarnya mengizinkan pengumpulan foto kebun, jadi bisa dilakukan setelah KKP.
- Akibatnya akurasi di TPH hanya bisa diketahui dari 7.1 dan 7.3. Laporkan apa adanya.

### 7.5 Laporan
- Hasil pelatihan akhir (mAP50, precision, recall), arsitektur, hasil uji lapangan, batasan.
- Cantumkan sumber dataset (CC BY 4.0) dan catatan lisensi AGPL-3.0 untuk `ultralytics_yolo`.
