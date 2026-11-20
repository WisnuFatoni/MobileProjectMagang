# fAI26: Progres dan Checklist

Terakhir diperbarui: 21 November 2026 (fitur IPD: desain dan API selesai, HP dan web berikutnya; lihat akhir Fase 6 dan RENCANA_IPD.md)

Acuan: `doc/fAI26_Panduan_Alur_Lengkap.md`, `erd/` (acuan yang berlaku untuk basis data), `flow/`.
Langkah rinci tiap fase ada di [LANGKAH.md](LANGKAH.md). Hal yang menunggu jawaban ada di [PERTANYAAN.md](PERTANYAAN.md).

## Cara membaca

| Tanda | Arti |
|---|---|
| [x] | Selesai dan sudah diuji |
| [~] | Sudah dibuat, tetapi belum diuji di HP atau belum lengkap |
| [ ] | Belum dikerjakan |
| [?] | Menunggu jawaban atau keputusan (lihat PERTANYAAN.md) |

## Ringkasan posisi (diperbarui 18 Nov)

Kotak `[ ]` di Fase 0 sampai 3 di bawah sebagian besar **usang** (ditulis sebelum fitur dibuat; fiturnya sudah ada dan diuji, lihat entri `[x]` di Fase 4, 5, 5b, 5c, dan 3b). Yang dipakai untuk memutuskan langkah berikutnya adalah bagian "Langkah berikutnya" di bawah.

| Fase | Isi | Status |
|---|---|---|
| 0 | Model dan prototipe kamera | Jalan di HP; angka akurasi dan thermal belum diuji di kebun |
| 1 | Database lokal SQLite (Room) | Selesai (skema v3, migrasi, repository, audit) |
| 2 | Login dan hak akses | Selesai (offline 7 hari, online, token terenkripsi, ganti password, hak role dari server) |
| 3 | Alur scan TPH lengkap | Selesai secara kode (QR, GPS, persetujuan mandor, panorama); belum diuji di TPH nyata |
| 4 | Sinkronisasi HP ke server | Selesai dan terbukti ujung-ke-ujung dengan HP asli |
| 5 | API Go dan database `fai26` | Selesai di lingkungan dev; produksi (HTTPS, DENY audit) tinggal dipasang sesuai `fai26-api/docs/DEPLOY.md` |
| 5b | Web admin React | Selesai (master, QR, panen, pengguna, hak akses, perangkat, audit) |
| 6 | Sisi ERP (TCode HVDM) | **Belum dimulai (dikerjakan Ptoni)**; jembatan SQL sudah disiapkan (Fase 5c) |
| 7 | Uji lapangan, laporan | Belum |

### Langkah berikutnya (urutan saran)

1. **Sisi ERP (Fase 6, Ptoni, jalur kritis):** TCode HVDM membaca `dbo.v_fai_panen_siap_impor` dan memanggil `sp_fai_impor_ok` atau `sp_fai_impor_gagal` (lihat Fase 5c). Tanpa ini data berhenti di database `fai26`.
2. **Uji lapangan (Fase 7):** daftar uji ada di `DAFTAR_UJI_LAPANGAN.md` bila sudah dibuat; tanpa itu mulai dari: hitungan scan vs hitung manual (tracker sekarang jauh lebih peka, lihat Fase 3b), panorama di TPH nyata, GPS di bawah tajuk, thermal sesi 2 sampai 3 menit, sinkron latar, dan QR cetak terbaca kamera.
3. **Pasang di server sungguhan** mengikuti `fai26-api/docs/DEPLOY.md` (database, login API, HTTPS, `004_audit_log_deny.sql`).
4. **Keputusan yang masih menunggu Ptoni:** (persetujuan beda HP sudah selesai 19 Nov, lewat server), bentuk kode mandor/afdeling di HVD1 (B17), aturan `FCM4CODE`/`ROTASI`/`NOURUT` (B18), pemecahan kelas abnormal dan `HASILHI1`, parameter GPS, istilah kebun.
5. **Belum dibuat:** penguncian data bulan lalu, mengirim koreksi mandor setelah catatan terkirim, peringatan kualitas saat scan (gelap, buram, terlalu cepat), info TPH dari ERP.

**Tenggat:** KKP selesai 15 Desember 2026. Aplikasi hanya diberi waktu 3 minggu (10 sampai sekitar 30 November), sisanya uji, laporan, dan presentasi. Jadwal per minggu ada di PERTANYAAN.md bagian 5.

| Minggu | Target |
|---|---|
| 1 (10 sampai 16 Nov) | Uji scan video di HP, SQLite sesuai ERD, simpan scan ke SQLite, login offline |
| 2 (17 sampai 23 Nov) | Scan QR dan master contoh, layar tinjau dengan persetujuan mandor, antrean sinkron, database `fai26` dan API minimal |
| 3 (24 sampai 30 Nov) | Sinkron HP ke `fai26`, HVDM membaca `fai_panen` (Ptoni), uji TPH nyata |

Urutan bila waktu mepet: pilih blok dan TPH, pindai dan dapat grading, simpan tanpa sinyal. Muncul di mySAP365 menyusul. Dikorbankan lebih dulu: login online, layar admin, klip video, peringatan kondisi. Verifikasi GPS sekarang fitur yang diminta, jadi tidak lagi dikorbankan di awal. Versi paling sederhana (catat titik dan jarak, tanda untuk mandor) masuk minggu 2.

**Platform (10 Nov): pindah ke Android native Java.** Project baru: `fAI26-Mobile/` (Android Studio, Java 11, package `com.example.fai26`). Prototipe Flutter (`sawit_grading/`) sudah diporting dan dibekukan sebagai acuan. Pustaka SQLite di Java: **Room** (pengganti `drift`, lihat Fase 1). Kamera memakai CameraX, inferensi memakai TensorFlow Lite langsung (bukan plugin Ultralytics).

---

## Fase 0. Model dan prototipe kamera

### Port ke Android native Java (`fAI26-Mobile/`, 10 Nov)

- [x] Dependensi: CameraX 1.4.2, TensorFlow Lite 2.16.1 (+ GPU delegate), `compileSdk` 36
- [x] Model `best.tflite` di `app/src/main/assets/models/` (tidak dikompres)
- [x] `YoloDetector`: letterbox 640, masukan float32, GPU dengan cadangan CPU, baca bentuk tensor otomatis
- [x] `YoloPostprocess`: baca keluaran `[1,10,8400]`, NMS agnostik kelas (IoU 0,60), pemetaan balik ke frame
- [x] `BunchTracker`: port dari Dart, sama persis
- [x] Layar: `ScanActivity` (MULAI/SELESAI), `HasilActivity` (koreksi + / −, TPH, pemanen, simpan), `RiwayatActivity`
- [x] Penyimpanan prototipe JSON (`ScanStore`): sudah diganti Room dan dihapus (lihat Fase 1)
- [x] Fakta dari log HP (10 Nov): model menerima input `[1, 3, 640, 640]` float32 (NCHW), keluaran `[1, 10, 8400]`, backend GPU di SM-A528B
- [x] 13 tes unit lolos (tracker 6, pasca-proses YOLO 6, JSON catatan 1), `assembleDebug` dan lint lolos
- [x] **Scan diuji di HP (10 Nov, dilaporkan Ptoni): berjalan.** Angka kecepatan dan keakuratan hitungan belum dicatat
- [x] Simpan dan Riwayat diuji di HP (Ptoni, 10 Nov): catatan tersimpan dengan foto, total, dan status DRAFT
- [x] Kecepatan terukur di SM-A528B (10 Nov): **sekitar 151 ms per frame (GPU)**, setara kira-kira 6 sampai 7 frame per detik. Frame pertama 3,3 detik (GPU disiapkan)
- [x] Ukuran terbaru saat HP lebih dingin (10 Nov, bar atas kamera): total 103 ms per frame = konversi 4 + pra-proses 13 + model 89 + pasca 1 (kira-kira 10 fps). Masih perlu uji sesi 2 sampai 3 menit berturut-turut
- [~] **Kecepatan (10 Nov), per tahap di SM-A528B.** Awal: konversi ~30, pra-proses ~80, model ~85, pasca ~1 ms (total ~195 ms). Setelah optimasi (rotasi masuk ke matriks letterbox tanpa bitmap baru, konversi float massal): saat HP dingin konversi 4, pra-proses 18, model 78, pasca 1 ms, **total sekitar 100 ms (kira-kira 10 fps)**. Waktu per tahap kini tampil di bar atas dan di log tiap 30 frame
- [ ] **HP cepat panas dan melambat:** saat diuji HP berstatus thermal 4 dari 5 (suhu casing 50 °C, prosesor 67 °C, CPU turun ke 1,3 dari 2,4 GHz), sedang diisi daya dan mode hemat baterai menyala. Pada kondisi itu model 123 sampai 152 ms dan pra-proses sampai 123 ms. Perlu diuji ulang saat HP dingin, tidak diisi daya, dan hemat baterai mati, lalu uji sesi scan 2 sampai 3 menit berturut-turut untuk melihat apakah melambat. Di kebun HP akan lebih panas lagi
- [~] **Hitungan dobel (dilaporkan Ptoni, 10 Nov):** tandan yang sudah terhitung terhitung lagi. Perbaikan di `BunchTracker`: (1) batas "hilang" berbasis waktu (3 detik) bukan jumlah frame, supaya tidak tergantung fps; (2) track basi dibuang sebelum pencocokan; (3) dua kotak yang saling tumpang tindih berat (IoU > 0,6) pada tandan yang sama digabung, yang lebih muda dibuang; (4) label ID tracking di layar saat memindai (abu-abu = belum dihitung, hijau dengan centang = sudah dihitung). 11 tes tracker lolos. **Belum diuji di HP.** Batas yang diketahui: tandan yang hilang dari layar lebih dari 3 detik lalu muncul lagi tetap dihitung sebagai tandan baru
- [ ] Uji hitungan dobel di HP dengan label ID: catat apakah ID satu tandan berganti-ganti, satu tandan punya dua ID, atau tandan kembali setelah keluar layar
- [x] Sebelum MULAI, analisis kamera dibatasi sekitar 2,5 frame per detik (hemat baterai dan panas). Saat memindai tetap penuh
- [ ] Ide lanjutan bila masih kurang cepat: ekspor ulang model dengan ukuran masukan lebih kecil (480 atau 416 piksel, bukan melatih ulang, tetapi butuh `best.pt` dan Python dengan Ultralytics, belum ada di komputer ini), jalankan pra-proses di thread terpisah dari model, atau melewati frame bila terlambat
- [ ] Kotak koordinat: modul pasca-proses menebak otomatis apakah keluaran berupa piksel atau 0..1. Pastikan dengan log di HP
- [ ] IoU NMS 0,60 agnostik kelas dipilih saya (Flutter memakai 0,7 per kelas). Bandingkan hasilnya

### Prototipe Flutter (`sawit_grading/`, dibekukan)

- [x] Model YOLOv8n dilatih dan diekspor ke LiteRT (`best.tflite`, sekitar 12 MB; cadangan `best.pt`)
- [x] Dataset dicatat: Roboflow `fine-tuning/palm-fruit-ripeness-detection-f6sac` v1, CC BY 4.0
- [x] Cek lingkungan Flutter (3.35.5), proyek `sawit_grading` dibuat (Android saja)
- [x] Plugin `ultralytics_yolo` 0.7.1 terpasang
- [x] Model ditaruh di `assets/models/best.tflite` dan didaftarkan di `pubspec.yaml`
- [x] Izin kamera, `minSdk 24`, plugin Kotlin 2.4.21, `kotlin.incremental=false` (build berhasil)
- [x] Model berhasil dimuat di HP (Samsung SM-A528B), jalan di GPU, label tampil sebagai nama
- [x] Kotak deteksi muncul di HP (diuji dengan foto TBS di layar laptop, bukan TBS sungguhan)
- [~] Layar uji awal: foto, halaman hasil, koreksi + / −, simpan JSON, riwayat (foto dan halaman hasil sudah dicoba di HP; Simpan dan Riwayat belum dicoba di HP)
- [~] Mode scan video: tombol MULAI dan SELESAI, tracker ByteTrack-lite (`lib/tracking/bunch_tracker.dart`), 5 tes unit lolos, **belum diuji di HP** karena HP terputus
- [ ] Uji mode scan video di HP dan setel parameter (lihat Fase 7)
- [ ] Uji mode pesawat (offline) untuk membuktikan semuanya jalan tanpa internet
- [ ] Terima lisensi Android SDK (`flutter doctor --android-licenses`), keputusan Ptoni
- [ ] Rapikan peringatan NDK: tambah `ndkVersion = "28.2.13676358"` di `android/app/build.gradle.kts`
- [ ] Ukur kelancaran (lancar, patah-patah, atau berat) di HP dan catat

Catatan: mAP50 0,993 dari Colab terlalu optimis (frame dataset mirip antar data latih dan test). Jangan ditulis "akurasi 99%" tanpa uji foto kebun sendiri.

## Fase 1. Database lokal SQLite (sesuai ERD)

Acuan: `erd/fAI26_mobile_sqlite.sql` (22 tabel).

- [x] Tambah **Room** 2.7.2 (SQLite, query bertipe, migrasi otomatis; padanan `drift` di Java)
- [x] Skema 22 tabel sebagai entitas Room, dihasilkan otomatis dari `erd/fAI26_mobile_sqlite.sql` (`db/entity/`), skema diekspor ke `app/schemas/`
- [x] Lima indeks unik parsial (`WHERE dihapus_pada IS NULL`) dibuat ulang saat database dibuat (Room tidak bisa menyatakannya)
- [x] Data awal: 6 kelas (dengan `label_model` sesuai urutan keluaran model), 5 role, 17 permission (16 dari ERD + `panen.setujui`), `model_versi` (hash SHA-256 model)
- [x] Kolom persetujuan mandor `disetujui_oleh`, `disetujui_pada` dan status `DISETUJUI`
- [x] 8 tes di HP lolos: 22 tabel, validasi skema Room, kelas sesuai model, role dan permission, foreign key, indeks parsial, hapus berantai, kolom persetujuan dan login offline
- [x] DAO (`MasterDao`, `PanenDao`) dan `PanenRepository`: simpan scan dalam satu transaksi (header, 6 baris grading, pemanen, foto, kotak deteksi, `audit_log` BUAT), foto disalin dan di-hash SHA-256, foto dihapus lagi bila transaksi gagal
- [x] Data master contoh (`assets/master_contoh.json`: 1 unit, 2 blok, 5 TPH dengan QR, 5 pemanen, 2 akun) dimuat sekali saat database kosong
- [x] Identitas perangkat (UUID, dibuat saat pertama dijalankan) tercatat di tabel `device`
- [x] Simpan dan Riwayat sudah memakai Room. Penyimpanan JSON dihapus
- [x] 5 tes repository di HP lolos (total 14 tes di HP, 6 + 6 tes unit JVM)
- [x] Database nyata di HP diperiksa: master termuat, perangkat tercatat, indeks parsial ada
- [ ] Uji Simpan lewat layar di HP (belum bisa, adb terputus). Tolong dicoba: scan, SELESAI, pilih TPH, Simpan, lihat Riwayat
- [ ] Hapus lunak, ubah, dan audit UBAH atau HAPUS di repository (baru BUAT)
- [ ] Pembuatan `sync_queue` saat simpan (menunggu Fase 4)
- [ ] Migrasi: skema versi 1. Perubahan skema berikutnya (misalnya kolom GPS, B17) lewat migrasi Room
- ~~Buat skema 22 tabel dari skrip SQL~~ (selesai di atas)
- [ ] Tambah status `DISETUJUI` dan kolom `disetujui_oleh`, `disetujui_pada` di `panen_header` (mandor perlu menyetujui; juga di staging server)
- [ ] Tambah kolom verifikasi lokasi di `panen_header` dan `fai_panen` (usulan, ERD belum memilikinya): `gps_akurasi_m`, `gps_waktu`, `gps_mock`, `jarak_ke_tph_m`, `status_lokasi`. `gps_lat` dan `gps_lon` sudah ada di ERD
- [ ] `brondolan_kg` diisi dalam kilogram, jumlah tandan dalam janjang
- [ ] Isi data awal: `kelas_grading` (6 kelas), `role`, `permission`, `role_permission`, `model_versi`
- [ ] Lapisan repository: isi kolom audit otomatis, hapus lunak, filter `dihapus_pada IS NULL`
- [ ] Tulis `audit_log` dalam satu transaksi dengan perubahan datanya
- [ ] Data master contoh (unit, divisi, blok, TPH, pemanen, periode) berupa berkas yang diisi sendiri
- [ ] Pindahkan alur simpan scan dari JSON ke `panen_header`, `panen_grading`, `foto`
- [ ] Hapus penyimpanan JSON prototipe setelah pindah
- [ ] Tes unit repository dan migrasi

## Fase 2. Login dan hak akses

- [x] Layar login (`LoginActivity`, jadi layar pembuka). Dibuat 10 Nov, **belum dicoba di layar HP** karena disk C: penuh saat mau memasang
- [x] Akun contoh untuk pengembangan: krani1 / krani123 dan mandor1 / mandor123 (password awal ada di `master_contoh.json`, ikut di APK: **hanya untuk uji, hapus sebelum dipakai nyata**)
- [x] Login offline: verifier PBKDF2-HMAC-SHA256 (100.000 iterasi, garam acak), kunci 5 kali gagal selama 15 menit, masa berlaku 7 hari (build debug 365 hari)
- [x] Hak akses: permission dimuat dari role saat login. Tombol Riwayat disembunyikan tanpa `panen.lihat`, MULAI dinonaktifkan tanpa `panen.buat`
- [x] Tombol Keluar. Repository menolak bila belum login (`user_id` dari sesi, bukan lagi akun tetap)
- [x] `LOGIN`, `LOGIN_GAGAL` (termasuk username yang tidak dikenal) tercatat di `audit_log` dengan keterangan OFFLINE
- [x] 11 tes login di HP dan 5 tes hash di JVM lolos (total 25 tes di HP, 17 tes JVM)
- [x] **Tampilan login didesain ulang (10 Nov):** kepala gradien hijau tua dengan logo tandan sawit, kartu form melayang, ikon di tiap isian, tombol MASUK 60 dp dengan indikator memuat, kotak pesan galat dengan ikon dan getar singkat, penanda "Bisa dipakai tanpa sinyal", dukungan autofill dan tombol Done di keyboard. Tema Material 3 hijau kebun dan emas panen berlaku untuk seluruh aplikasi, dengan versi terang dan gelap (`colors.xml`). Tampilan terang sudah dilihat di HP. Keadaan galat, loading, dan mode gelap belum dilihat langsung
- [x] Uji layar login di HP (10 Nov): password salah menampilkan "Sisa percobaan: 4", password benar masuk ke layar kamera dengan tombol Riwayat dan Keluar
- [ ] Simpan token di penyimpanan aman (menunggu login online)
- [ ] Login online ke API, perpanjangan masa berlaku, logout paksa bila akun dinonaktifkan
- [ ] Layar ganti password, wajib ganti password pertama
- [ ] Login online ke API (menunggu Fase 5)

## Fase 3. Alur scan TPH lengkap

- [x] Kamera dengan deteksi langsung
- [~] Scan video dengan tracker (belum diuji di HP)
- [x] **Beranda (10 Nov), sesuai Layar 1 dan Layar 4 di panduan:** setelah login masuk ke Beranda, bukan langsung kamera. Isinya salam dan nama, peran dan tanggal, tiga kartu ringkasan (catatan hari ini, janjang hari ini, belum terkirim), kartu Scan TPH (pilih TPH dan pemanen opsional, TPH terakhir diingat), tombol MULAI SCAN, dan tiga catatan terbaru dengan lencana status. Krani hanya melihat catatannya sendiri, mandor dan asisten (punya `panen.koreksi`) melihat semua. Kamera hanya bisa dibuka dari Beranda, tujuan TPH tampil di bar atas kamera. Setelah Simpan kembali ke Beranda, setelah Ulangi tetap di kamera. 29 tes di HP lolos (termasuk 4 tes query beranda). Dicoba di HP: login, Beranda, pilih TPH, buka kamera
- [x] Pilih TPH dan pemanen di layar hasil tetap ada (sudah terisi dari Beranda, bisa dikoreksi), plus isian brondolan (kg) dan catatan. Tanggal panen otomatis hari ini
- [x] **Pembaruan Visual & UX Beranda (12 Nov):** Menghilangkan kesan "polos" pada Beranda. (1) Logo fAI26 mini di kanan atas header; (2) Pill status lingkungan kerja interaktif di header (indikator titik hijau/oranye + teks "Online • Terhubung ke Server" atau "Mode Offline • Tersimpan di HP", bisa diklik untuk detail); (3) Ikon bertema pada 3 kartu ringkasan (dokumen panen, tandan sawit oranye, awan sinkron); (4) Banner tips lapangan pemindaian TBS AI dengan latar emas panen (`kartuTips`); (5) Mengganti teks kosong dengan kartu panduan visual 3 langkah pengerjaan di TPH saat belum ada transaksi (`boxEmptyState`). Berhasil diuji kompilasi dan dipasang langsung ke HP uji via USB. Rincian ada di `progres/CATATAN_PENGERJAAN.md`.
- [x] **Scan QR TPH (10 Nov):** alur sekarang Beranda → SCAN QR TPH → TPH dikenali → (pilih pemanen) → MULAI SCAN TBS. Pembaca QR memakai ZXing core (offline). Layar pemindai punya bingkai pembidik, senter, getar saat berhasil, dan pesan galat yang jelas (QR bukan fAI26, jenis lain, versi tak didukung, TPH tidak ada di master). "QR rusak? Pilih TPH manual" tetap ada sebagai cadangan dan dicatat `sumber_tph = MANUAL` di `audit_log` (QR dicatat `QR`). Memori "TPH terakhir" dihapus supaya krani selalu scan QR. 15 tes pembaca QR lolos di JVM (QR miring 90 derajat, terbalik, kontras rendah, bernoise, tanpa QR). Layar QR dan Beranda baru terlihat di HP. **Pembacaan QR dari kamera nyata belum dicoba**: perlu QR di depan kamera (lihat spesifikasi)
- [x] **Pembacaan QR dari kamera nyata berhasil (Ptoni, 10 Nov):** layar menampilkan "Dikenali dari QR"
- [x] **Info TPH setelah QR terbaca (10 Nov), sesuai Layar setelah scan di panduan:** unit, divisi, blok, tahun tanam beserta umur, luas blok, perkiraan jumlah pokok (luas x SPH, ditandai "≈"), dan panen terakhir pada TPH itu menurut catatan di HP. Bila hari ini sudah ada catatan di TPH yang sama, muncul peringatan supaya krani tidak mencatat tumpukan yang sama dua kali. Info yang masih kosong di master (tahun tanam, luas, SPH) disembunyikan. 30 tes di HP dan tes pemformat angka lolos
- [ ] Info yang belum ada: jumlah pokok pasti (master hanya punya SPH), koordinat TPH, mandor atau kemandoran, panen terakhir dari ERP (sekarang hanya dari catatan di HP). Menunggu master asli dari ERP (B8)
- [x] QR contoh untuk uji: `tools/buat_qr_tph.py` menghasilkan `qr_contoh/*.png` dan `lembar_qr.png` dari master contoh
- [x] Spesifikasi QR untuk pembuat QR dan web admin: [SPEK_QR_TPH.md](SPEK_QR_TPH.md)
- [ ] Uji scan QR dari kamera: buka `qr_contoh/TPH_A12_01.png` di layar komputer, arahkan kamera HP
- [ ] **Web admin** (permintaan Ptoni): kelola master TPH, buat `qr_kode` otomatis, cetak QR per TPH dan per blok, isi koordinat. Kebutuhannya ada di SPEK_QR_TPH.md. Bergantung pada API dan database `fai26` (Fase 5)
- [x] **Menu bawah bergaya kaca (10 Nov, permintaan Ptoni):** satu `MainActivity` dengan tiga layar (Beranda, Riwayat, Akun) yang tetap hidup saat pindah tab, jadi pilihan TPH di Beranda tidak hilang. Menu berbentuk kapsul melayang dengan latar yang benar-benar dikaburkan dari isi layar yang bergulir di belakangnya (`BackdropBlurView`, kabur kotak pada bitmap kecil, jalan di semua versi Android, 3 tes unit), warna lebih hidup, kilau di tepi atas, garis tepi bergradien plus garis tipis gelap, bayangan lembut, dan kapsul pilihan yang meluncur membal. Versi terang dan gelap. Tombol Kembali dari Riwayat atau Akun menuju Beranda dulu. **Catatan jujur:** ini pendekatan terhadap Liquid Glass iOS (kabur, kilau, gerak), bukan pembiasan cahaya asli iOS 26 yang tidak ada di Android. Tampilan awal kapsul sudah terlihat di HP; efek kaca di atas isi bergulir, bayangan, mode gelap, dan perpindahan tab belum dilihat langsung
- [x] **Layar Akun:** avatar inisial, nama, peran, username, masa berlaku login offline, jumlah belum terkirim, perangkat dan ID perangkat, versi model AI, versi aplikasi, dan tombol KELUAR (dipindah dari Beranda). **Layar Riwayat** kini memakai kartu yang sama dengan Beranda
- [x] **Tab Persetujuan (10 Nov):** muncul di menu bawah hanya untuk pengguna yang memegang `panen.setujui` (mandor); tab lain tetap. Isinya daftar catatan DRAFT dengan rincian per kelas, brondolan, catatan, pemanen, dan nama krani, tombol SETUJUI per catatan dan SETUJUI SEMUA. Persetujuan mengubah status menjadi DISETUJUI, mengisi `disetujui_oleh` dan `disetujui_pada`, dan menulis `audit_log` (aksi SETUJU, nilai lama DRAFT) dalam satu transaksi; pengguna tanpa permission ditolak di repository (bukan hanya disembunyikan di UI). Tes baru di HP lolos (31 dari 31)
- [ ] Persetujuan: mandor mengoreksi jumlah sebelum setuju, menolak dengan alasan (status DITOLAK dipakai untuk penolakan server, perlu keputusan), kartu "Menunggu persetujuan" di Beranda mandor, lencana jumlah di tab
- [ ] **Keputusan terbuka persetujuan:** catatan tersimpan di HP masing-masing krani. Persetujuan sekarang hanya melihat catatan yang ada di HP tempat mandor login (mandor login di HP krani, atau HP bersama). Agar mandor menyetujui dari HP sendiri, catatan krani harus lebih dulu sampai ke server lewat sinkronisasi, lalu persetujuan dilakukan terhadap data server (Fase 4 sampai 5)
- [~] **Kapsul menu bisa digeser (10 Nov, permintaan Ptoni):** geser mendatar di menu bawah, kapsul penanda mengikuti jari (membesar sedikit saat dipegang), getaran halus tiap melewati tab, label dan ikon ikut menyala sesuai tab yang dilewati, dan saat dilepas menempel ke tab terdekat dengan gerak membal lalu layar berpindah. Mengetuk tab tetap bekerja. Hitungan geser punya 5 tes unit (lolos); terpasang di HP tetapi **belum dicoba langsung di layar** (menunggu Ptoni)
- [ ] Memperluas gaya kaca ke komponen lain (kartu ringkasan di kepala, kotak dialog) bila Ptoni suka hasilnya
- [ ] Scan QR TPH (format `FAI26|TPH|1|KP_STL|A12|07`), cari di master lokal
- [ ] Cadangan pilih blok dan TPH manual bila QR rusak
- [ ] Peringatan kondisi: cahaya, baterai, ruang simpan
- [ ] Peringatan saat scan: terlalu cepat, gelap, buram
- [ ] Keyframe terbaik per tandan (sekarang baru satu foto penutup)
- [ ] Layar tinjau: koreksi `jml_final`, tandai `diubah_manual`, isi brondolan dan catatan
- [ ] Opsi pindai ulang (sesi lama ditandai batal)
- [ ] Validasi (Bab 7.3): tanggal, master, total nol, confidence rendah
- [ ] Siklus status: DRAFT, DISETUJUI (mandor), SIAP_KIRIM, TERKIRIM, DITERIMA, DITOLAK
- [x] Layar persetujuan mandor (lokal di HP, lihat catatan di atas)
- [ ] Penguncian data bulan lalu setelah tanggal tutup gaji (sekitar tanggal 1 sampai 4)
- [ ] Daftar catatan hari ini dengan status
- [ ] Ganti istilah tampilan ke istilah kebun (setelah dicocokkan dengan pembimbing)
- [?] Pembagian janjang ke pemanen: buah digabung tetapi tiket ERP per pemanen (B1)
- [~] **Verifikasi lokasi GPS** (permintaan Ptoni, 10 Nov): memastikan pencatat benar-benar ada di TPH. Kode dan tes selesai (35 tes di HP lolos, 10 tes JVM baru untuk hitungan); belum diuji di TPH nyata
  - [x] Izin lokasi (`ACCESS_FINE_LOCATION` dan `COARSE`) diminta saat layar scan dibuka; bila ditolak, scan tetap jalan dan lokasi dicatat TANPA_GPS
  - [x] Titik GPS hanya dikumpulkan selama layar scan terlihat (mulai di onStart, berhenti di onStop), tidak melacak di belakang layar. Titik terbaik (akurasi terkecil, maksimal 2 menit) dibawa ke layar hasil saat SELESAI
  - [x] Disimpan: lat, lon, akurasi, waktu titik, penanda lokasi palsu (`isMock`), jarak ke TPH, dan status lokasi. Kolom baru ditambahkan ke ERD (sqlite, sql server, md, mmd) dan Room v2 dengan migrasi dari v1 (diuji, data lama aman)
  - [x] Jarak haversine ke `tph.lat`, `tph.lon`; status DI_TPH, DI_LUAR, AKURASI_RENDAH, TANPA_GPS, TPH_TANPA_KOORDINAT (di luar radius meski akurasi buruk tetap DI_LUAR bila pasti di luar)
  - [x] Setelah simpan, krani diberi tahu bila lokasi perlu perhatian; tanda tampil di Riwayat dan di layar Persetujuan mandor (termasuk jarak dan "terdeteksi lokasi palsu")
  - [x] Layar Beranda: tombol "Rekam koordinat TPH ini" untuk TPH yang belum punya koordinat (berdiri di TPH, mencari titik sampai akurasi 12 m atau 30 detik, ditolak bila akurasi lebih buruk dari 30 m, hanya mengisi yang masih kosong, tercatat di audit_log)
  - [ ] Koordinat TPH dari ERP atau web admin, dan mengubah titik yang sudah ada (belum ada selain lewat rekam pertama)
  - [ ] Peringatan ke krani sebelum menyimpan (sekarang pemberitahuan setelah simpan)
  - [?] Nilai yang dipakai sementara: radius 50 m, akurasi maksimum 30 m, hanya menandai (tidak memblokir), tanpa GPS tetap boleh simpan (usulan saya untuk B12 sampai B14, menunggu konfirmasi Ptoni)

## Fase 4. Sinkronisasi HP ke server

Dikerjakan 10 Nov 2026. Terbukti ujung-ke-ujung dengan HP asli ke API Go yang jalan di laptop (database `fai26_dev`): login online, kirim catatan, foto, dan audit, lalu status Ditolak dari ERP (disimulasikan di database server) sampai ke Riwayat beserta alasannya. Uji: 75 tes JVM dan 64 tes di HP lolos (1 dilewati: uji live butuh server).

- [x] **Login online** (`auth/LoginService`): server dicoba dulu bila alamatnya diatur; sukses memperbarui verifier offline dengan password terbaru dan memperpanjang login offline 7 hari, serta menyimpan token. Server menolak (password salah, terkunci, nonaktif) berarti ditolak dan **tidak** jatuh ke offline (password yang sudah diganti di server tidak boleh tetap berlaku lewat verifier lama). Jaringan putus atau server rusak jatuh ke login offline
- [x] Antrean `sync_queue` (PANEN dan FOTO): catatan masuk antrean saat mandor menyetujui; yang disetujui sebelum fitur ini ada ikut masuk saat sinkron
- [x] Kirim dari yang paling lama: catatan dulu, lalu fotonya di putaran yang sama. UUID mencegah data dobel (jawaban `SUDAH_ADA` dianggap berhasil)
- [x] Coba ulang bertahap: jaringan putus atau server bermasalah dicoba lagi dengan jeda 30 detik yang berlipat sampai 30 menit; setelah 8 kali gagal ditandai GAGAL. Ditolak server (isi tidak sah) langsung GAGAL, catatan ditandai Ditolak dengan alasannya. Tombol **Kirim sekarang** mengabaikan jeda, tombol **Kirim ulang yang gagal** mengembalikan yang GAGAL ke antrean
- [x] Audit log dikirim berkelompok (200 per kiriman) dan ditandai terkirim hanya bila diterima
- [x] Status Diterima atau Ditolak diambil dari server (`GET /api/panen?sejak=`) dan dicatat di catatan; alasan penolakan tampil di Riwayat
- [x] Token: akses 15 menit diperbarui otomatis dengan refresh token; ditolak sekali lalu diperbarui dan diulang; sesi habis berarti "Login online ulang"
- [x] Layar Akun: kartu Sinkronisasi (alamat server, jumlah menunggu dan gagal, waktu sinkron terakhir, tombol Kirim sekarang dan Kirim ulang). Sinkron jalan diam-diam setelah login online dan setelah mandor menyetujui
- [x] Build uji memakai `http://localhost:8080` lewat `adb reverse tcp:8080 tcp:8080` (kabel USB); build rilis hanya HTTPS dan alamat server dikosongkan
- [x] Bug yang ditemukan tes dan sudah diperbaiki: foto tidak ikut terkirim di putaran yang sama dengan catatannya; penghitung "status diperbarui" ikut menghitung baris yang tidak berubah; kode kelas `empty` di HP tidak cocok dengan `emptybunch` di server (server disamakan, dokumen API 0.1.1)
- [x] **Sinkron otomatis di latar (11 Nov, WorkManager 2.9.1):** berkala tiap 15 menit (batas terkecil WorkManager) hanya saat ada jaringan, ditambah sekali jalan secepatnya begitu ada jaringan setelah login online atau setelah mandor menyetujui (bila offline menunggu sampai sinyal kembali, tanpa menekan tombol). Kegagalan sementara dijawab retry (jeda makin panjang), penolakan server tidak. Tombol Kirim sekarang tetap ada. Terbukti: pekerjaan benar-benar dijalankan WorkManager di HP berjaringan (selesai dalam sekitar 1 detik)
- [x] **Aturan pemilik catatan (11 Nov):** HP hanya mengirim catatan milik pengguna yang login online, atau semua catatan bila ia pemegang `panen.koreksi` (mandor); catatan orang lain menunggu pemiliknya (atau mandor) login online, bukan ditandai gagal. Payload membawa `pencatat_username`, dan server (dokumen API 0.3.0) menerimanya hanya dari pengirim itu sendiri atau pemegang `panen.koreksi`, sehingga pencatat tercatat benar (terbukti di database: `dibuat_oleh` = krani, bukan mandor yang mengirim). Sebelumnya pencatat selalu pemilik token
- [x] Foto dikompres sebelum dikirim: **tidak perlu dibuat (11 Nov).** Foto bukti adalah frame analisis kamera yang digambari kotak lalu disimpan JPEG kualitas 85 (`KeyframeRenderer`), bukan foto beresolusi penuh, jadi ukurannya sekitar ratusan KB, jauh di bawah batas 8 MB. Ukuran belum diukur dari foto lapangan; ukur saat uji lapangan dan buka lagi bila ternyata besar
- [x] **Unduh master dari server (11 Nov):** `GET /api/master` (dokumen API 0.2.0) mengirim unit, divisi, blok, TPH, pemanen, dan akun beserta rolenya untuk unit pengguna; HP membandingkan `versi_server` di `master_sync` supaya tidak mengunduh ulang. Versi dihitung server dari isi tabel (jumlah baris dan waktu ubah terbaru), jadi semua alat yang mengubah master wajib mengisi `diubah_pada`. Server mendapat tabel `divisi`, `blok`, `tph`, `pemanen` (ERD 2.3, migrasi `fai26-api/migrations/002_master.sql`).
  - [x] Penerapan di HP dalam satu transaksi (gagal di tengah berarti tidak ada yang berubah): baris dicocokkan menurut id (ada diperbarui, belum ada ditambah), baris yang dihapus lunak di server ditandai bukan dibuang, koordinat TPH yang direkam di HP tidak ditimpa kosong dari server, akun diperbarui tanpa menyentuh verifier offline dan kunci akun, role diganti sesuai server
  - [x] Login online pada HP baru: akun belum ada di HP, jadi master diunduh dulu lalu login diterapkan. Pada pengiriman berikutnya master diperbarui dulu (gagal di sini tidak menghentikan pengiriman catatan)
  - [x] Build rilis mulai kosong (tanpa master dan akun contoh, tanpa password di APK); master contoh hanya di build uji
  - [x] Terbukti ujung-ke-ujung dengan HP asli: HP tanpa master apa pun, login online ke API, master turun (2 blok, 5 TPH, 5 pemanen, akun mandor dengan hak setujui), lalu catat, setujui, kirim catatan, foto, audit, dan status. 77 tes di HP dan 75 tes JVM lolos; API: semua tes unit dan 6 tes integrasi lolos
  - [x] **Cacat skema HP ditemukan dan diperbaiki:** enam batasan UNIQUE dari ERD (username, kode unit, kode role, kode permission, kode kelas, qr_kode TPH) tidak pernah terbentuk di database HP karena generator entitas melewatkan UNIQUE inline. Generator diperbaiki, skema HP naik ke versi 3 dengan migrasi dan tes migrasi (kode unit ganda kini ditolak)
  - [x] **HP mengirim koordinat TPH ke server (11 Nov, dokumen API 0.5.0):** `PUT /api/tph/{id}/koordinat` (permission `panen.kirim`) mengisi koordinat **hanya bila TPH belum punya**; bila sudah punya, server mengembalikan titiknya (`diterapkan: false`) dan HP mengikutinya, jadi titik yang sudah benar tidak tertimpa. Di HP titik masuk antrean sinkron (`TPH_KOORDINAT`, dalam transaksi yang sama dengan perekamannya) sehingga ikut backoff, coba ulang, dan aturan pemilik (pemilik atau mandor yang mengirim). Berhasil mengubah versi master server sehingga HP lain mengunduh titik itu, dan tercatat di audit. Terbukti dengan HP asli ke API dan SQL Server: titik tersimpan di database server. 12 tes integrasi API, 78 tes JVM, 94 tes di HP lolos (1 dilewati: uji live). Belum ada: titik yang direkam di HP sebelum fitur ini ada tidak dikirim otomatis (hanya berlaku untuk HP uji)
  - [x] **Hak akses per role dari server (11 Nov, dokumen API 0.6.0):** `GET /api/master` menambah `role_permission` (hak semua role) dan versi master ikut berubah bila hak role berubah. HP mengganti seluruh hak role-nya dengan isi itu (role yang tidak tercantum berarti tanpa hak; kode tak dikenal dilewati; kunci tidak ada atau daftar kosong diabaikan supaya tidak mengunci semua pengguna), lalu cache hak pengguna yang sedang login diperbarui tanpa login ulang (`Sesi.perbarui`) dan kartu scan di Beranda menyesuaikan. Penegakan di repository HP memang selalu membaca database, jadi sudah segar. Belum: tab Persetujuan di `MainActivity` hanya dicek saat aplikasi dibuka (hak baru tampil setelah dibuka ulang); belum ada layar admin untuk mengubah hak role (sekarang lewat SQL di server)
  - [x] **Daftar TPH di Beranda menyegarkan diri (11 Nov):** `PerubahanMaster` mengumumkan master baru (login online maupun sinkron latar); Beranda memuat ulang daftar TPH dan pemanen sambil mempertahankan pilihan krani (TPH yang dipilih, pemanen), dan bila TPH terpilih sudah dihapus di server, pilihan dikosongkan dengan pesan
  - [x] Alat admin untuk mengisi master di server: web admin `fai26-web/` (lihat Fase 5b). Master tidak perlu lagi diisi lewat SQL
- [x] **Token dienkripsi dengan Android Keystore (11 Nov):** token akses dan refresh disimpan sebagai sandi AES-256-GCM (kunci di Keystore, tidak bisa dibawa keluar HP, IV acak, nama kolom ikut diautentikasi) dan tidak pernah sebagai teks polos. Token versi lama (teks polos) dipindahkan lalu dihapus saat aplikasi dibuka. Sandi rusak, diubah, atau kuncinya hilang (misalnya dipulihkan ke HP lain) dianggap "tidak ada token": pengguna login online ulang, tanpa crash. Bila Keystore sendiri gagal, token tidak disimpan (tidak jatuh ke teks polos). Preferensi sinkron juga dikeluarkan dari cadangan cloud dan transfer perangkat (`backup_rules.xml`, `data_extraction_rules.xml`). 6 tes baru di HP; total 100 tes di HP dan 78 tes JVM lolos
- [x] **Cadangan otomatis Android dibiarkan menyala (keputusan Ptoni, 11 Nov):** database Room (verifier password offline, catatan, audit) tetap ikut dicadangkan ke cloud Google pemilik HP; hanya preferensi sinkron (token) yang dikecualikan. Risiko yang diketahui dan diterima: pemulihan ke HP lain ikut membawa `device_id`, sehingga dua HP bisa memakai identitas perangkat yang sama
- [x] **Foto bukti scan jadi satu panorama (11 Nov, permintaan Ptoni):** sebelumnya hanya satu frame (saat Selesai ditekan) yang disimpan, padahal hitungan berasal dari penyisiran seluruh tumpukan. Percobaan pertama (banyak foto tambahan) salah arah dan dibatalkan: yang diminta SATU gambar panorama. Kini HP menaksir pergeseran kamera antar frame dari gambarnya sendiri (`panorama/PenaksirGeser`, pencocokan kasar ke halus tanpa pustaka luar, sekitar 15 ms per frame di HP uji), menyimpan frame sebagai potongan tiap kamera bergeser sekitar sepertiga layar (kotak deteksi sudah tergambar di tiap potongan), lalu menempelnya menjadi satu JPEG (`PanoramaPemindai`; potongan baru menutupi yang lama; sisi terpanjang maks. 2400 px; sekitar 280 KB pada uji; maks. 24 potongan). Gagal dengan aman: bila kamera nyaris diam, bergerak terlalu cepat, atau permukaannya polos sehingga tidak bisa disejajarkan, hasilnya tetap foto satu frame resolusi penuh seperti sebelumnya, dan kegagalan panorama tidak pernah mengganggu penghitungan. Pada panorama, kotak deteksi tidak disimpan sebagai baris `deteksi` (koordinatnya bukan lagi milik satu frame dan buah yang sama bisa muncul di beberapa potongan); kotak tetap terlihat di gambar. Bukti: simulasi sisiran pada pemandangan yang diketahui di HP asli: sisiran vertikal menghasilkan panorama dengan selisih rata-rata 0,8 dari 255 terhadap pemandangan asli, ukuran tepat, diagonal dan kamera diam dan kamera terlalu cepat juga lolos; foto kebun sungguhan (stok) disisir dengan guncangan tangan tersambung mulus menjadi satu gambar. Tes: 14 JVM (penaksir, perancang potongan, kanvas) dan 7 di HP. **Belum dicoba dengan scan kamera sungguhan di TPH:** keterbatasan yang diketahui: tumpukan polos berwarna seragam atau kamera diputar atau digeser cepat membuat panorama berhenti menyambung (jatuh ke foto satu frame atau panorama sebagian), dan sisi gambar bisa bergerigi gelap bila tangan bergeser ke samping
- [ ] Pengiriman perubahan setelah catatan terkirim (koreksi mandor sesudah kirim) belum ada
- [?] **Masalah lingkungan laptop (bukan kode):** SQL Server lokal sesekali macet 20 sampai 60 detik pada koneksi baru. Terukur 11 Nov: RAM kosong tinggal sekitar 1,1 GB dari 15,7 GB (Android Studio sendiri sekitar 650 MB), dan proses `sqlservr` hanya memegang 76 sampai 91 MB di RAM padahal memakai sekitar 611 MB, artinya sisanya dibuang ke disk dan harus dibaca ulang tiap kali ada permintaan setelah diam. Disk C: juga hampir penuh. Meredam sementara dengan query ringan berkala; obat sebenarnya menutup aplikasi berat atau menambah RAM. Server sungguhan tidak terkena ini. API sudah diberi batas 20 detik per permintaan (jawab 503 bila database lambat), koneksi menganggur dibuang, dan HP mencoba lagi nanti

## Fase 5. API backend dan database server

- [x] Skrip `erd/fAI26_server_sqlserver.sql` dijalankan di SQL Server 2025 lokal (database uji `fai26_uji`): lolos setelah satu perbaikan (indeks terfilter butuh `SET QUOTED_IDENTIFIER ON`, kini ada di awal skrip). 14 tabel, 3 indeks terfilter, 5 role, 17 permission
- [x] **API Golang `fai26-api/` (10 Nov):** `/api/health`, `/api/auth/login|refresh|logout|me`, `POST /api/panen`, `POST /api/panen/{id}/media`, `GET /api/panen?sejak=`, `POST /api/audit/batch`. Lihat `fai26-api/README.md`. **Disusun ulang (10 Nov) ke struktur standar Go:** `cmd/`, `internal/{controller,service,repository,routes,middleware,auth,model,apperror,response,storage,config,database,app,seed}`, `test/integration`. Tes unit tanpa database (model, service dengan tiruan, middleware) lolos: login, kunci akun 5 kali gagal, perangkat nonaktif, rotasi refresh token, idempoten, validasi, penolakan tanpa persetujuan mandor, foto dengan checksum, audit dobel, galat tidak membocorkan detail. 5 tes integrasi di `test/integration` (opt-in lewat `FAI26_TEST_DSN`, tanpa nilai bawaan) lolos terhadap SQL Server lokal di `localhost:1444`, database `fai26_dev`, memakai kredensial pengembangan yang diberikan Ptoni; server dijalankan sungguhan dan dicoba lewat curl (health dan login). Konfigurasi dibaca dari `.env` (git mengabaikannya)
- [x] JWT (HS256, 15 menit) dengan permission dibaca ulang dari database tiap panggilan, bcrypt, `auth_sesi` dengan refresh token di-hash dan diganti tiap dipakai
- [x] Penyimpanan media: foto JPEG ke folder per catatan, dicek checksum SHA-256 dan tanda JPEG, maks. 8 MB
- [x] Aturan server: catatan wajib disetujui mandor (penyetuju harus memegang `panen.setujui` di server)
- [x] **Dokumentasi API (10 Nov, permintaan Ptoni):** `fai26-api/docs/API.md` (panduan: daftar endpoint, body, contoh, kode galat, alur HP, riwayat perubahan) dan `docs/openapi.yaml` (OpenAPI 3). Dijaga tes: `TestSemuaRuteTerdokumentasi` dan `TestVersiDokumenSama` gagal bila ada rute atau versi yang tidak tercatat di dokumen (sudah dicoba dengan merusak dokumen dan tes memang gagal). **Aturan: tiap endpoint baru wajib memperbarui dokumen ini**
- [x] `GET /api/master` (unduh master, dokumen 0.2.0) dan **API admin `/api/admin/*` (11 Nov, dokumen 0.4.0, 29 rute):** divisi, blok, TPH (termasuk buat berurutan massal atomik maks. 500), pemanen, pengguna (buat, ubah role, reset password yang mencabut sesi), perangkat (nonaktifkan mencabut sesi), ringkasan dasbor, daftar catatan panen dengan filter dan detail, foto, audit log. Izin dicek per rute (`master.lihat/kelola`, `user.kelola`, `device.kelola`, `laporan.lihat`, `panen.lihat`, `audit.lihat`). Penghapusan master selalu lunak; mengubah kode blok atau nomor TPH membuat QR TPH berganti. 11 tes integrasi lolos
- [x] `PUT /api/tph/{id}/koordinat` (dokumen 0.5.0): lihat Fase 4
- [x] Pengguna mengganti password sendiri: `POST /api/auth/password` (dokumen 0.7.0), lihat Fase 5c
- [~] `DENY UPDATE, DELETE` pada `audit_log`: skrip siap di `fai26-api/migrations/004_audit_log_deny.sql`, **diterapkan oleh DBA di server sungguhan** (sengaja tidak di database dev)
- [~] HTTPS, layanan Windows: panduan lengkap di `fai26-api/docs/DEPLOY.md` (reverse proxy, NSSM); pembatasan laju sudah ada (Fase 5c). Tinggal dipasang di server
- [?] SQL Server lokal di laptop ini tidak mendengar di TCP 1433 (TCP mati); tes memakai named pipe. Server sandbox kebun biasanya memakai TCP, jadi DSN-nya berbeda
- [?] Izin membuat database baru di server sandbox

## Fase 5b. Web admin (`fai26-web/`, 11 Nov)

React 18 + TypeScript + Vite + Ant Design 5 + React Router + TanStack Query. Cara menjalankan ada di `fai26-web/README.md`. Terbukti di Chrome dengan API dan data nyata (mode gelap dan terang); `tsc`, ESLint (0 peringatan), build produksi, dan 47 tes (Vitest) lolos.

- [x] Login split-screen, sesi dipulihkan dari refresh token (dirotasi, satu penerbangan), tema terang dan gelap, menu mengikuti hak akses akun
- [x] Dasbor (catatan dan janjang hari ini, perlu perhatian, TPH tanpa koordinat, grafik 7 hari, status ERP, perangkat dan pengguna aktif)
- [x] Master: Blok, TPH dan QR (buat massal berurutan, salin isi QR), Divisi, Pemanen
- [x] **Cetak QR TPH** sebagai SVG murni (koreksi galat H, margin 4 modul sesuai `SPEK_QR_TPH.md`), tiga ukuran lembar; terbukti terbaca pemindai (jsQR) di tes. Belum dicoba dengan kamera HP di kertas sungguhan
- [x] Catatan panen (filter tanggal, status ERP, cari), detail dengan foto, Pengguna (buat, ubah role, reset password), Perangkat (nonaktifkan), Audit log (hanya baca)
- [ ] Tampilan lebar HP belum diverifikasi di browser (jendela uji tidak bisa dipersempit); CSS sudah memakai breakpoint
- [x] **Satu alamat dengan API (11 Nov):** `FAI26_WEB_DIR=<fai26-web/dist>` membuat API menyajikan web (`internal/webui`): `/api/*` ke API, rute React dijawab `index.html`, `/assets` di-cache setahun (berhash), `index.html` no-cache, berkas hilang 404 sungguhan, header keamanan dan CSP ketat, path traversal ditolak. 7 tes baru. Dicoba di Chrome pada satu origin: login, kembali ke halaman tujuan, muat ulang langsung di `/tph` memulihkan sesi
- [x] **Bug build produksi ditemukan dan diperbaiki:** `manualChunks` di Vite membuat build produksi kosong (`Cannot set properties of undefined (setting 'Children')`), padahal dev dan tes normal. Pemecahan manual dihapus; entry kini 849 KB (273 KB gzip), turun dari 1,28 MB. Pelajaran: build produksi harus dicoba di browser, bukan hanya `npm run build`
- [x] **Server gangguan tidak lagi memaksa login ulang:** bila refresh sesi dijawab 5xx atau jaringan putus, web menampilkan "Server belum bisa dihubungi" dengan tombol Coba lagi dan sesi tetap tersimpan; hanya 401/400 yang menghapus sesi. 5 tes baru

## Fase 5c. Tambahan server dan web (11 Nov, Claude)

Semua diuji terhadap SQL Server dev (tes integrasi) dan, untuk web, dilihat di Chrome.

- [x] **Jembatan ERP untuk HVDM** (`fai26-api/migrations/003_jembatan_erp.sql`, aman dijalankan berulang): view `dbo.v_fai_panen_siap_impor` (satu baris per catatan BARU atau DIBACA, jumlah per kolom `JJG_*` dan per kelas, pencatat, penyetuju) dan `dbo.v_fai_panen_pemanen_siap_impor`; prosedur `sp_fai_tandai_dibaca`, `sp_fai_impor_ok(@panen_id, @hvd1m_oid, @hvd1da_oid, @diimpor_oleh)`, `sp_fai_impor_gagal(@panen_id, @pesan, @diimpor_oleh)`, `sp_fai_ulang_impor`. Prosedur menjaga perpindahan status yang sah, menulis `fai_impor_log` dan `audit_log`, dan **mengisi `diubah_pada`** (tanpa itu HP tidak melihat status baru). Alur penuh (HP kirim, ERP tolak, HP melihat alasan, ulang, impor OK, HP melihat DITERIMA) terbukti di `TestJembatanErpAlurPenuh`. Belum diputuskan dan sengaja tidak dihitung: pemecahan abnormal, kolom ERP unripe dan underripe (`jjg_tanpa_kolom`), isi `HASILHI1`
- [x] **Pembatas laju** (middleware, dokumen 0.7.0): login dan refresh 30 per menit, seluruh API 600 per menit per alamat IP (bawaan, `FAI26_BATAS_LOGIN`, `FAI26_BATAS_UMUM`, 0 = tanpa batas); melebihi batas dijawab 429 dengan `Retry-After`; `FAI26_PERCAYAI_PROXY` hanya bila di belakang reverse proxy sendiri. HP menganggap 429 dan 408 sebagai kegagalan sementara (dicoba lagi)
- [x] **Ganti password sendiri** (`POST /api/auth/password`, dokumen 0.7.0): password lama wajib benar (salah berulang mengunci akun, dijawab 422 bukan 401), baru 8 sampai 72 karakter dan berbeda, sesi di perangkat lain dicabut, `wajib_ganti_password` dihapus. Web: menu "Ganti password" (dan dialog wajib setelah reset admin). HP: tombol "GANTI PASSWORD" di layar Akun (hanya online; verifier offline ikut diperbarui). **Tampilan dialog HP belum dilihat di layar**, hanya logikanya yang dites
- [x] **Hak role bisa dikelola dari web** (dokumen 0.8.0): `GET /api/admin/hak-akses`, `PUT /api/admin/hak-akses/{id}` (permission `role.kelola`); ADMIN tidak bisa kehilangan `role.kelola`; berlaku langsung di server dan HP mengikuti lewat master. Halaman web "Hak Akses" berupa matriks role x permission dengan konfirmasi rincian perubahan
- [x] **Peringatan kondisi HP** (baterai di bawah 15% dan tidak diisi, ruang simpan di bawah 300 MB) di Beranda; **tab Persetujuan** mengikuti perubahan hak tanpa buka ulang aplikasi
- [x] **Panduan pemasangan** `fai26-api/docs/DEPLOY.md`
- [x] Tes pada akhir 11 Nov: 125 tes di HP, 106 JVM, 65 web, seluruh tes unit dan integrasi API lolos

## Fase 3b. Pengerjaan mandiri Ptoni (12 sampai 17 Nov, dari `CATATAN_PENGERJAAN.md`) dan hasil pemeriksaan

Ptoni mengerjakan sendiri: Beranda baru (status online/offline, ikon, tips, panduan kosong), penampil foto layar penuh dengan zoom dan putar, perombakan mesin panorama (penaksir geser lokal, jahitan berbiaya minimum, penyeimbang warna, nomor janjang `#1 #2`, banner ringkasan, gerbang kualitas yang jatuh ke satu foto utuh bila sapuan hanya melihat satu tumpukan), pelonggaran ambang deteksi untuk tumpukan padat, dan **dashboard Beranda dari server** (`GET /api/panen/ringkasan`, dokumen 0.9.0).

Hasil pemeriksaan Claude pada 18 Nov (setelah semua perubahan itu):
- Kompilasi aplikasi dan sumber tes, **106 tes JVM dan 125 tes di HP lolos** (termasuk 7 tes panorama).
- **Bug ditemukan dan diperbaiki:** `GET /api/panen/ringkasan` menjawab **500 untuk setiap panggilan** (`SUM` atas subkueri ditolak SQL Server, kesalahan 130); hanya terlihat saat dijalankan terhadap SQL Server karena tes unit memakai tiruan. Kueri diganti `LEFT JOIN`, dan tes integrasi baru `TestRingkasanPanenDashboard` (angka utama, per kelas, per blok, tren urut tanggal, status, cakupan SENDIRI lawan UNIT, tanggal salah 400) lolos. Pelajaran: setiap SQL baru harus dicoba lewat tes integrasi sebelum dianggap selesai
- Kunci JSON yang dibaca `DashboardBeranda` cocok dengan model server.
- **Risiko yang perlu dicek di lapangan, bukan bug:** ambang deteksi dilonggarkan jauh (`YoloDetector.CONF` 0,25 menjadi 0,17; tracker `highConf` 0,18, `minHits` 3, `minArea` 0,005, `mergeIou` 0,55). Itu menaikkan peluang kotak palsu dan hitungan berlebih; bandingkan hasil scan dengan hitung manual di beberapa TPH (Fase 7) sebelum angkanya dipercaya. `PanoramaPemindai` kini sekitar 1000 baris tanpa tes baru untuk jalur jahitan dan gerbang kualitas; hanya 7 tes lama yang mencakupnya
- `DashboardBeranda` dan penampil foto belum punya tes otomatis

## Fase 6. Sisi ERP (dikerjakan Ptoni di Visual Studio)

- [ ] TCode HVDM (salinan HVD1) membaca `fai_panen` berstatus BARU. **Pakai `dbo.v_fai_panen_siap_impor` dan prosedur `sp_fai_*` (Fase 5c), jangan menulis tabel `fai_*` langsung**
- [ ] Pemetaan ke `hvd1m` dan `hvd1da` (tabel pemetaan di ERD Bab 8)
- [ ] Tulis `fai_impor_log` dan `audit_log` IMPOR, ubah `status_erp`
- [ ] Publish lewat DevAgro Hub
- [?] Pemecahan kelas abnormal, tempat unripe dan underripe dicatat, isi `HASILHI1`

### Satu TPH = satu pemanen (18 Nov, Claude) [x] selesai dan diuji

- [x] Migrasi `fai26-api/migrations/005_hvdm_satu_pemanen.sql`: `pemanen.mandor_hrmb_code`, `fai_panen.afdeling_kode/afdeling_erp_oid/mandor_hrmb_code`, view HVDM diperluas (`pemanen_hrmb_code`, `pemanen_jjg`)
- [x] `POST /api/panen`: `pemanen[]` wajib tepat satu, `jjg_alokasi` = total (422 bila tidak); server menyalin afdeling dan mandor dari master saat diterima. API 0.10.0 (`API.md`, `openapi.yaml`)
- [x] Admin pemanen: field `mandor_hrmb_code` (API dan halaman Pemanen di web)
- [x] HP: pemanen wajib (Beranda menolak MULAI tanpa pemanen, layar Hasil menolak simpan, `PanenRepository.simpan` melempar); tes diperbarui
- [x] Migrasi `006_peran_hvdm.sql`: peran `fai26_hvdm` (baca dua view, jalankan empat prosedur, tanpa hak tulis tabel); diuji di database dev
- [x] Perbaikan tes web di Node 26 (`localStorage` global bawaan Node menimpa jsdom; `src/test/setup.ts`)
- [x] Panduan serah-terima ERP diperbarui (bagian 18 menanggapi catatan asisten ERP)
- Verifikasi 18 Nov: 126 tes di HP, 65 tes web, tes unit Go, 19 tes integrasi Go, semua lolos.
- [ ] Migrasi 005 dan 006 belum dijalankan di server sungguhan (DBA/Ptoni); isi master di web: `erp_oid` divisi (afdeling) dan kode mandor tiap pemanen

### Mandor yang menginput (18 Nov, Claude), menanggapi `Perubahan_Mobile_API_fAI26_untuk_HVDM.md`

- [x] Migrasi `007_mandor_input.sql` (diterapkan ke `fai26_dev`): `app_user.mandor_hrmb_code/afdeling_kode`, `fai_panen.pemanen_hrmb_code`, indeks unik anti ganda, view HVDM (`unit`, `waktu_panen`)
- [x] API 0.11.0: profil mandor di login; master per mandor/afdeling; kirim tanpa persetujuan terpisah (mandor menyetujui sendiri); validasi blok/TPH/pemanen milik mandor; 409 ganda; `kirim_ulang` untuk catatan DITOLAK; admin pengguna (mandor, afdeling); web admin halaman Pengguna
- [x] HP (Room v4): profil mandor di akun, master per mandor (baris di luar potret disembunyikan), mandor menyetujui sendiri saat simpan lalu kirim, tanggal panen dapat dipilih, peringatan/penolakan catatan ganda, Riwayat: Edit dan kirim ulang untuk DITOLAK
- [x] Tes: unit Go, 20 tes integrasi Go, 65 tes web, tes JVM HP, dan 137 tes di perangkat HP semuanya lolos (18 Nov). `SyncServiceTest` kini menunggu sinkron latar asli selesai (dipicu `SyncJadwalTest`) supaya tidak bentrok pada suite penuh
- [ ] Belum: kondisi cuaca, sinkron master dari ERP (master masih manual admin), mandor mengedit catatan TERKIRIM, migrasi 005/006/007 di server sungguhan

### Persetujuan krani lewat server + data contoh dihapus (19 Nov, Claude)

- [x] Alur sebenarnya: **mandor menginput, krani memvalidasi dan menyetujui** (HP berbeda). Catatan mandor dikirim tanpa penyetuju dan disimpan server berstatus `MENUNGGU`; krani melihat daftarnya dari HP-nya, lalu **setujui** (jadi `BARU`, siap HVDM) atau **tolak dengan alasan** (jadi `DITOLAK`; mandor memperbaiki lalu kirim ulang, kembali `MENUNGGU`)
- [x] API 0.12.0: `GET /api/panen/menunggu`, `POST /api/panen/{id}/setujui`, `POST /api/panen/{id}/tolak` (permission `panen.setujui`); migrasi `008_menunggu_persetujuan.sql` (diterapkan ke `fai26_dev`)
- [x] HP: catatan tanpa hak setuju langsung antre dan dikirim; status baru "Menunggu krani" (`MENUNGGU_SETUJU`); layar Persetujuan membaca dari server (setujui, tolak dengan alasan, setujui semua; butuh sinyal)
- [x] Semua data/akun contoh dihapus: dari aplikasi HP (`master_contoh.json` dan `MasterLoader` pindah ke sumber tes), dari API (`cmd/seed` dan `internal/seed` pindah ke kode tes), dan dari database dev (diisi akun asli, divisi DIV1 dan DIV2)
- [x] Tes integrasi API kini memakai database terpisah `fai26_tes` (dibuat dari nol memakai skema + migrasi 002..008: uji instalasi bersih) dan **menolak berjalan** di database yang namanya tidak berakhiran `_tes`
- [x] Tes: unit Go, 22 tes integrasi Go (`fai26_tes`), tes JVM HP, dan 91 tes perangkat HP (kelas yang tidak menyentuh sesi login nyata)
- [ ] Belum: foto catatan belum bisa dilihat krani saat menyetujui; mandor/afdeling akun mandor belum diisi (kode HRMB ERP belum ada)

### Master asli dan pencarian TPH (19 Nov, Claude)

- [x] Blok: 118 blok dimasukkan ke `fai26_dev` (80 di DIV1, 38 di DIV2). `erp_oid` masih **sementara** 900001 sampai 900118
- [x] TPH: 2487 TPH dari `DataBLOK+TPH/BLOK+TPH.xlsx`, `no_tph` angka biasa, QR `FAI26|TPH|1|KP_STL|<blok>|<no>`. Dilewati karena mencurigakan: blok `D` dan `A/2`; TPH `SP8`, `14-13`, `27-28`, `5.5`, `46273`, `46148`. Kode ganda (D1, D1A, D2, D3, TESTER) hanya masuk sebagai Divisi 2
- [x] Pemanen: 61 nama unik dari `DataKEBUN/PEMANEN.xlsx` dimasukkan ke `fai26_dev`; `erp_hrmb_code` **sementara** 800001 sampai 800061; `mandor_hrmb_code` kosong, jadi tiap mandor masih melihat semua pemanen. Divisi di Excel tidak tersimpan (tabel tidak punya kolomnya; 11 pemanen tercatat di dua divisi)
- [x] HP: kolom TPH manual ("QR rusak? Pilih TPH manual") dan kolom pemanen di Beranda bisa dicari dengan mengetik (`PencarianAdapter`: semua kata harus cocok, urutan bebas, mis. "c7 6"). Diuji di HP asli dan lewat `PencarianAdapterTest` (3 tes lolos)
- [x] Pengaturan server di Akun hanya tampil untuk admin (`master.kelola`); kartu "Pengiriman data" dirapikan
- [ ] Belum: layar Hasil (ganti TPH setelah scan) masih memakai daftar penuh tanpa pencarian; keputusan TPH/blok mencurigakan di atas; `erp_oid` blok dan kode HRMB pemanen asli dari ERP; kode HRMB/afdeling akun mandor; foto catatan untuk krani; kondisi cuaca; migrasi 005 sampai 008 di server sandbox; catatan "alamat server ditanam saat build, bukan diketik" sebelum rilis; data uji lama di HP (3 catatan ditolak) masih ada

**Status: pengodean dihentikan sementara oleh Ptoni (19 Nov).** Lanjut dari daftar "Belum" di atas.

### Tugas kuliah (KKP) Hari ke-91: dashboard mobile (19 Nov, Claude)

- [x] Ditambah atas permintaan Ptoni: bagian 3.4 Halaman Login (screenshot login asli, Gambar 4), 3.5 Tema Visual, 3.6 Efek Liquid Glass pada Menu Bawah (lapisan kaca, latar buram, kapsul, efek lensa; kode BackdropBlurView dan NavGeser), serta lampiran berkas kode login/kaca. Dokumen kini 19 halaman
- [x] Dokumen Word `TUGAS/Tugas_Hari_91_Dashboard_Mobile.docx` (sebelumnya 14 halaman; Times New Roman hitam, rata kiri-kanan, spasi 1,5, isi 12 pt, judul 16/14/12 pt). Isi: KPI Estate, desain dashboard, metode refresh, tiga dashboard, refleksi logbook, potongan kode. Screenshot utuh ada di `TUGAS/gambar/`
- **Hanya yang sudah dibuat di aplikasi**: tidak ada gambar rancangan/mockup; screenshot HP dipakai utuh tanpa dipotong (3 tangkapan Beranda: atas, tengah, bawah). Versi pertama (18 halaman) berisi mockup AKP/budget dan screenshot terpotong; ditolak Ptoni dan diganti. Aplikasi **tidak diubah**
- Pemetaan tugas ke aplikasi: AKP Mobile = layar Beranda; Produksi TBS Harian = kartu Produksi hari ini + rekap blok + tren 7 hari; Realisasi vs Budget = realisasi vs target harian (0 / 10). AKP sensus buah dan budget bulanan hanya disebut di "Keterbatasan"
- [ ] Belum: Ptoni memeriksa isi (terutama refleksi bagian 6 yang ditulis orang pertama) dan melengkapi program studi di sampul bila diperlukan
- [x] **Hari ke-92** (brief aslinya dashboard pabrik/PKS, tapi Ptoni magang di **KEBUN**): `TUGAS/Tugas_Hari_92_Dashboard_Kebun_Mobile.docx` (28 halaman, format sama). Versi pertama memakai istilah PKS/OER/KER dan ditolak Ptoni; ditulis ulang tanpa satu pun istilah pabrik. Pemetaan: Dashboard Kualitas Buah = Kualitas panen (% matang); Dashboard Rincian Kelas Buah dan Blok = legenda 6 kelas + Rekap per blok; Dashboard Produksi Harian = produksi hari ini + tren 7 hari (HP) dan Dasbor web. Notifikasi pencapaian target = pesan yang memang ada di app/web (tanpa push). File hari ke-91 sudah diganti nama dan diedit Ptoni menjadi `TUGAS/DASHBOARD_MOBILE.docx`: jangan dibuat ulang/ditimpa
- [x] Hari ke-92 dilengkapi atas permintaan Ptoni: bab 6 "Dashboard Web fAI26: Seluruh Fitur" (27 halaman). Screenshot asli utuh semua halaman web (login, dasbor gelap dan terang, catatan panen, blok, TPH dan QR beserta dialog QR, cetak QR, buat berurutan, divisi, pemanen, pengguna dan formulir tambah, hak akses atas dan bawah, perangkat, audit log, menu akun, ganti password) di `TUGAS/gambar/web_*.jpg`, dengan penjelasan fitur tiap halaman. Formulir hanya dibuka, tidak ada data yang disimpan. Sesi web di Chrome Ptoni keluar (logout) saat memotret halaman login
- Catatan kecil web (belum diperbaiki, tidak diminta): kartu TPH tanpa koordinat menampilkan "2,487 / 2.487" (format angka campur); subjudul Catatan Panen masih bertuliskan "disetujui mandor" padahal persetujuan oleh krani

- [x] **Hari ke-93** (Mobile Transaksi Panen): `TUGAS/Tugas_Hari_93_Mobile_Transaksi_Panen.docx` (23 halaman, format sama, hanya yang sudah dibuat, screenshot HP dan web asli utuh, tanpa istilah pabrik). Isi: analisis alur transaksi (pelaku, 10 langkah, status HP dan server), form input hasil panen (pilih TPH manual dengan pencarian, pemanen wajib, form hasil), validasi berlapis HP dan server (blok, TPH, pemanen, anti ganda, lokasi), master data ERP (`erp_oid`, `erp_hrmb_code`), integrasi ke ERP lewat jembatan view/prosedur (bukan panggilan langsung ke API mySAP365; ditulis jujur di Keterbatasan), refleksi. Screenshot HP baru di `TUGAS/gambar/hp93_*.png`. File hari ke-91 dan ke-92 diganti nama Ptoni (`DASHBOARD_MOBILE.docx`, `DASHBOARD_PRODUKSI_KEBUN_MOBILE.docx`): jangan ditimpa
- Temuan kecil di aplikasi (belum diperbaiki, tidak diminta): (1) kolom pemanen di Beranda berisi teks asli "Pilih pemanen...", jadi mengetik "alf" menempel di depannya dan daftar tidak muncul sampai teksnya dihapus; (2) pesan merah "Pilih pemanen dulu" tidak hilang setelah pemanen dipilih; (3) tulisan "menunggu persetujuan mandor" masih ada di layar Persetujuan dan toast sesudah Simpan, padahal yang menyetujui krani

- [x] **Hari ke-94** (Mobile Premi Panen): `TUGAS/Tugas_Hari_94_Mobile_Premi_Panen.docx` (14 halaman). Ditulis jujur: fAI26 **tidak punya premi/payroll/tarif/basis/hari kerja**; yang ada = data hasil kerja per pemanen + jembatan ke ERP. **Fitur baru dibuat** (tambahan saja, HP dan fitur lama tidak diubah): API 0.13.0 `GET /api/admin/produktivitas?tanggal=` (laporan.lihat; ranking janjang per pemanen per hari, % matang, brondolan, jumlah menunggu persetujuan; DITOLAK tidak dihitung; tanpa premi) + halaman web "Produktivitas Pemanen" (menu Laporan). Diuji: tes unit service, tes integrasi di `fai26_tes` (suite penuh lolos), tes komponen web; docs API.md/openapi.yaml/PANDUAN_LENGKAP/README diperbarui. Web sudah dibuild ke `dist` dan API dijalankan ulang di laptop. Screenshot halaman baru kosong (belum ada catatan panen di server); untuk versi terisi, input beberapa catatan sungguhan lewat HP lalu ambil ulang screenshot
- [x] **Hari ke-95** (Review Sprint 1): `TUGAS/Tugas_Hari_95_Review_Sprint_1.docx` (19 halaman). Evaluasi dashboard Estate, dashboard produksi kebun, transaksi panen dan premi (capaian, kekurangan, tindak lanjut); bahan presentasi dan alur demo; 12 kendala teknis dan solusi (dari riwayat pengerjaan sebenarnya); dokumentasi yang ada; rencana Sprint 2. Tanpa istilah pabrik. **Belum dilakukan dan ditulis jujur di dokumen:** presentasi (hanya bahan), dan **unggah ke repository tim**: folder proyek belum git (`fai26-api`, `fai26-web`, `fAI26-Mobile` sudah punya `.gitignore`; `.env` API berisi kata sandi DB dan tidak boleh diunggah) dan alamat repository tim belum ada. Butuh dari Ptoni: alamat dan hak akses repository
- [x] **Hari ke-96** (Istirahat dan Evaluasi Sprint 1): `TUGAS/Tugas_Hari_96_Evaluasi_Sprint_1.docx` (14 halaman). Review dashboard (penilaian pribadi dengan alasan), **evaluasi kualitas kode dari pengukuran nyata**: 193 berkas / 20.733 baris kode produksi (HP 11.659, API 5.735, web 3.339), 10.080 baris tes, 435 tes; gofmt, go vet, eslint, tsc bersih; 0 TODO; temuan: 101 teks tertanam di layout Android + strings.xml hanya 1 entri, tulisan "mandor" tertinggal, kolom pemanen "Pilih pemanen...", kelas besar (PanoramaPemindai 944 baris), cakupan tes web rendah (0,21), lint Android belum dijalankan. Ide GPS dan Vision AI (8 ide, belum dibuat; prasyarat: 2.487/2.487 TPH belum punya koordinat), indeks dokumentasi, logbook perkembangan mobile. **Belum:** unggah ke Google Drive tugas (dilakukan Ptoni; folder GD ada di akun Ptoni/pembimbing)
- [x] **20 Nov 2026, perbaikan kekurangan transaksi panen** (diminta Ptoni): (1) **foto bukti untuk krani**: API 0.14.0 `GET /api/panen/menunggu` memuat `foto_id` (foto deteksi bila ada, null bila belum terkirim); kartu Persetujuan di HP mengunduh dan menampilkan foto (ketuk untuk memperbesar, pesan bila belum terkirim, cache dibersihkan); (2) kolom pemanen di Beranda: teks "Pilih pemanen..." dikosongkan saat disentuh sehingga ketikan langsung bekerja, dan pesan merah hilang setelah pemanen dipilih (diuji di HP); (3) tulisan "mandor" diganti "krani" di layar Persetujuan, pesan setelah Simpan (HasilActivity), dan subjudul Catatan Panen web; (4) layar Hasil: TPH kini kolom cari dengan mengetik (`PencarianAdapter`), TPH wajib dipilih. Tes: JVM `HttpServerApiTest` (foto_id dan unduh foto), integrasi `TestPersetujuanLewatServer` (fai26_tes), build dan pasang ke HP berhasil. **Belum terverifikasi di HP:** tampilan foto pada kartu Persetujuan (belum ada catatan MENUNGGU di server) dan pencarian TPH pada layar Hasil untuk catatan baru (butuh scan kamera); keduanya hanya teruji lewat kode dan tes
- [x] **20 Nov 2026, perbaikan setelah uji Ptoni di HP** (API 0.14.1): (1) **dashboard krani 0**: penyebabnya aturan lama, dashboard hanya menghitung catatan yang diinput akun itu sendiri (`lingkup SENDIRI`) dan krani tidak menginput; kini pemegang `panen.setujui` (krani) juga se-unit, di server dan di HP (`Sesi.filterPemilik`), diuji di HP (Rony: 56 janjang, 4 catatan); (2) **bug Putar foto**: tombol memutar orientasi aplikasi sehingga halaman dibuat ulang dan kembali ke Beranda; kini memutar fotonya saja (`LihatFotoDialog`), diuji di HP; (3) **konfirmasi Keluar**: dialog "Yakin ingin keluar dari aplikasi?" sebelum keluar akun, diuji di HP; (4) foto bukti tampil di kartu Persetujuan dan teruji dengan catatan sungguhan dari royyy. Tes: unit service `TestRingkasanLingkupMengikutiHak`, build dan pasang berhasil. Catatan: karena krani kini melihat semua catatan lokal HP (pemilik 0), "Belum terkirim/Ditolak" di dashboard krani ikut menghitung catatan uji akun lain pada HP yang sama
- [x] **20 Nov 2026, aturan edit sebelum disetujui krani** (permintaan Ptoni: "mandor bisa edit yang sudah dikirim asalkan belum di-ACC krani; kalau sudah di-ACC tidak bisa lagi mengubah"). **API 0.15.0**: `POST /api/panen` dengan `kirim_ulang=true` kini berlaku untuk catatan `MENUNGGU` (isi diganti, tetap MENUNGGU) selain `DITOLAK`; catatan yang sudah disetujui (BARU/DIBACA/DIIMPOR) dijawab **423** "Catatan sudah disetujui krani, tidak bisa diubah lagi" (sebelumnya 200 SUDAH_ADA diam-diam); UPDATE di repository dibatasi `status_erp IN ('DITOLAK','MENUNGGU')` sehingga balapan dengan persetujuan krani ikut 423. Tes: unit (TestUbahCatatanMenungguSebelumDisetujui, TestKirimUlangCatatanDitolak diperbarui) + integrasi di `fai26_tes` (PersetujuanLewatServer, MandorMenginputAlurPenuh) lolos; docs API.md/openapi.yaml/changelog diperbarui. **HP**: `PanenRepository.bisaDiubah` (DITOLAK atau MENUNGGU_SETUJU), tombol "Edit sebelum disetujui" di Riwayat untuk "Menunggu krani", layar Hasil mengizinkan mode edit; bila server menjawab 423 `SyncService` menandai catatan TERKIRIM (bukan DITOLAK), membersihkan penanda kirim ulang dan menampilkan "N perubahan tidak diterima (sudah disetujui krani)". Tes instrumented baru ditulis dan dikompilasi (belum dijalankan di perangkat: Ptoni tidak mau suite penuh di HP). Aplikasi terpasang di HP, API dijalankan ulang. **Keterbatasan yang diketahui**: (1) bila krani sedang membuka kartu lalu mandor mengubah isinya, krani menyetujui versi baru tanpa melihat ulang (kartu baru diperbarui saat ditarik ulang); (2) bila perubahan ditolak 423, isi di HP mandor sudah berbeda dari yang disetujui krani (server berlaku, HP tidak mengembalikan isi lama); (3) catatan DRAFT yang belum sempat terkirim belum bisa diedit.
- **Dijelaskan ke Ptoni**: cuaca = kolom `kondisi_cuaca` sudah ada di tabel dan view ERP tetapi kosong, belum diketahui ERP mewajibkan atau tidak (tanya pembimbing); penguncian data bulan lalu = setelah tutup gaji catatan bulan sebelumnya dikunci supaya angka tidak berubah, belum dikerjakan.
- [x] **21 Nov 2026, fitur IPD (Inspeksi Panen Detail): rencana, git lokal, desain data.** Permintaan Ptoni: layar awal dua kotak (Panen, IPD) dengan menu Liquid Glass bertingkat; fitur IPD mencatat loses per titik panen (11 kode temuan A G S M1-M4 B1 B2 C R; satu catatan = satu pemanen, nomor baris, nomor pokok, semua pokok dicatat, foto lebih dari satu dengan keterangan); validasi inspektur otomatis saat kirim lalu persetujuan asisten; kotak diatur lewat hak akses (`akses.panen`, `akses.ipd`); role web baru ASKEP dan KTU (hanya lihat); nanti dihubungkan ke ERP (TCode belum ada). Rencana lengkap: `progres/RENCANA_IPD.md`. **Selesai**: (1) git lokal di `L:\GRADING_MAGANG` (commit "Titik awal sebelum fitur IPD", tanpa .env/DataKEBUN/TUGAS, tidak diunggah); (2) desain data: tabel `kode_temuan`, `ipd_header`, `ipd_pokok`, `ipd_temuan`, `ipd_foto` (HP, SQLite diuji) dan `ipd_kode_temuan`, `fai_ipd`, `fai_ipd_pokok`, `fai_ipd_temuan`, `fai_ipd_foto` (server), migrasi `009_ipd.sql` (role ASKEP/KTU, 8 permission, seed; dijalankan di `fai26_tes` dan idempoten, **belum di `fai26_dev`**), ERD mermaid HP dan server diperbarui, bab 13 di `fAI26_ERD_Database_Mobile.md`, dua diagram di `flow/` (alur inspeksi, navigasi HP). Foto form kertas lapangan diabaikan atas permintaan Ptoni. **Putaran kedua (21 Nov, jawaban Ptoni)**: mandor dipilih dari daftar akun MANDOR (tabel HP `petugas`), awal inspeksi pilih Divisi dan asisten terisi otomatis dari `divisi.asisten_user_id` (diatur admin di web, bisa diganti; inspeksi MENUNGGU ikut pindah), foto tanpa batas (dikompres, berkas HP dihapus setelah terkirim: `ipd_foto.path_lokal` NULL), inspeksi ulang bebas dengan peringatan 7 hari (indeks pemanen+blok+tanggal). Desain, 009_ipd.sql (diuji ulang di `fai26_tes`, belum di dev), ERD, bab 13 dan flow sudah disesuaikan. **Berikutnya**: Ptoni memeriksa desain, lalu Tahap 2 (API: endpoint, persetujuan, laporan loses, tes, dokumentasi), Tahap 3 (HP), Tahap 4 (web), Tahap 5 (dokumentasi akhir).
- [x] **21 Nov 2026, IPD Tahap 2 (API 0.16.0) selesai** setelah Ptoni menyetujui desain ("gaskan"; peringatan inspeksi ulang diubah menjadi **7 hari** karena inspeksi tiap Jumat). Dibuat: model/repository/service/controller IPD; endpoint HP `POST /api/ipd` (idempoten, `kirim_ulang` untuk MENUNGGU/DITOLAK, 423 bila sudah disetujui; pengirim pemegang `ipd.setujui` langsung DISETUJUI), `GET /api/ipd` (status sendiri), `GET /api/ipd/inspeksi-terakhir` (peringatan 7 hari), `GET /api/ipd/menunggu` (hanya yang ditujukan ke asisten itu), `POST /api/ipd/{id}/setujui|tolak`, `POST /api/ipd/{id}/media` (foto tanpa batas, keterangan, urut, sha256), `GET /api/ipd/foto/{id}`; endpoint web `GET /api/admin/ipd`, `/laporan` (loses per kode, kelompok, pemanen, blok, divisi, tanggal; hanya DISETUJUI), `/{id}` (rincian pokok). `GET /api/master` menambah `kode_temuan` dan `divisi[].asisten_user_id`; `GET/POST/PUT /api/admin/divisi` menambah `asisten_user_id` (hanya akun ASISTEN aktif; saat diganti inspeksi MENUNGGU divisi itu pindah ke asisten baru). Mandor blok dan ancak divalidasi sebagai akun MANDOR aktif. Tes: unit baru `ipd_service_test.go` (aturan edit, master 422, validasi bentuk, rumus ringkasan dan laporan, foto) dan integrasi `ipd_test.go` (alur penuh di `fai26_tes`: kirim, ubah, tolak, ganti asisten, setujui, terkunci 423, laporan, ASKEP hanya lihat); seluruh tes unit dan 23 tes integrasi lolos, vet dan gofmt bersih. Dokumentasi: API.md, openapi.yaml (0.16.0, juga memperbaiki cacat YAML lama di deskripsi `disetujui_username` dan menambah skema `TolakInput`), changelog. Migrasi 009 **sudah dijalankan di `fai26_dev`** dan pemetaan divisi dari yang Ptoni sebutkan diisi di dev (DIV1 = Ferdi, DIV2 = Ginting; akun `ferdi` dan `ginting` memang sudah ada berperan ASISTEN). Desain disederhanakan: tabel HP `petugas` dihapus, mandor dan asisten dibaca dari `app_user`+`user_role` yang sudah diunduh master. API dijalankan ulang di laptop. Smoke test dengan login akun asli tidak dijalankan (tidak menyimpan password ke berkas). **Berikutnya**: Tahap 3 (HP: Home dua kotak, menu bertingkat, Room v5, layar IPD), Tahap 4 (web), Tahap 5 (dokumen akhir).

## Fase 7. Uji lapangan dan laporan

- [ ] Uji GPS di TPH nyata: seberapa akurat di bawah tajuk pohon, berapa lama titik pertama didapat tanpa sinyal, dan setel radius
- [ ] Uji scan video di TPH nyata, bandingkan dengan hitungan manual di beberapa TPH
- [ ] Setel parameter tracker: confidence 0,40, minimal 5 frame, ukuran 3%, hilang maksimal 20 frame
- [ ] Uji 20 sampai 30 foto TBS kebun (belum pernah dilihat model) untuk akurasi yang jujur
- ~~Kumpulkan 100 sampai 300 foto kebun, anotasi, latih ulang model~~ (dibatalkan 10 Nov: pakai dataset yang ada, tanpa latih ulang)
- [ ] Cocokkan 6 kelas dengan istilah grading kebun (kriteria sudah diputuskan sama dengan dataset)
- [ ] Laporan: hasil pelatihan akhir (mAP50, precision, recall), sumber dataset, catatan lisensi
- [ ] Presentasi

---

## Keputusan yang sudah diambil

| Tanggal | Keputusan |
|---|---|
| 4 Nov | Aplikasi dipakai untuk scan di TPH (hasil panen), bukan di pohon |
| 4 Nov | Input ke sistem lewat API, dengan penyimpanan offline dulu |
| 9 Nov | ERD adalah acuan basis data yang berlaku |
| 9 Nov | Scan memakai video dan tracking (masukan CTO), bukan satu foto, supaya cukup untuk tumpukan besar |
| 9 Nov | Penyimpanan JSON hanya prototipe. Versi sebenarnya memakai SQLite sesuai ERD |
| 10 Nov | TCode tujuan: HVDM |
| 10 Nov | Pindah ke Android native Java (project `fAI26-Mobile`). Pustaka SQLite: Room (pengganti `drift`) |
| 10 Nov | Tidak latih ulang model, pakai dataset yang ada |
| 10 Nov | Hitungan per janjang, brondolan per kilogram (catatan awal "buah pemanen digabung" digantikan keputusan 18 Nov di bawah) |
| 18 Nov | **Satu TPH hanya untuk satu pemanen** (jawaban rekan Ptoni di PT). Pemanen wajib dan tunggal per catatan, seluruh janjang dikreditkan ke dia; HP dan server menolak tanpa pemanen. Header HVD1 per mandor + afdeling + tanggal, jadi server menyimpan salinan afdeling dan mandor |
| 10 Nov | Login offline 7 hari. Mandor perlu menyetujui sebelum kirim |
| 10 Nov | Tidak ada API resmi mySAP365: HVDM membaca langsung database `fai26`. Boleh membuat database `fai26` |
| 10 Nov | Tutup gaji sekitar tanggal 1 sampai 4. Tabel cluster 2026 belum ada, dibuat nanti |
| 10 Nov | Keyframe disimpan, klip video penuh tidak (usulan saya, menunggu persetujuan) |
| 10 Nov | Aplikasi punya fitur GPS untuk memantau apakah pencatat benar-benar ada di lokasi TPH |

## Ketidakkonsistenan dokumen yang perlu diselesaikan

- **ERD diperbaiki 10 Nov 2026 (versi 2.1)** di `erd/fAI26_mobile_sqlite.sql`, `fAI26_server_sqlserver.sql`, `fAI26_ERD_Database_Mobile.md`, `fAI26_ERD_mobile.mmd`, dan `fAI26_ERD_server_erp.mmd`: (1) salah ketik `offline_berlaku_sampaiTEXT`, (2) `label_model` pada data awal `kelas_grading` sekarang mengikuti urutan keluaran model (sebelumnya terbalik), (3) persetujuan mandor (status `DISETUJUI`, kolom `disetujui_oleh` dan `disetujui_pada` di HP dan di staging server, permission `panen.setujui`), (4) `erp_kolom` abnormal sementara `JJG_UNKNOWN`. Skrip SQLite hasil perbaikan sudah dijalankan di SQLite sungguhan. Skrip SQL Server belum diuji di SQL Server.
- **Skrip SQLite vs Room:** `CHECK` dan `DEFAULT` di SQL tidak bisa dinyatakan oleh Room. Pembatasan status dilakukan di kode (`StatusPanen`) dan nilai bawaan diisi oleh repository. Struktur kolom, foreign key, dan indeks tetap sama dengan ERD.
- `fAI26_ERD_Database_Mobile.md` Bab 12 masih menyebut berkas `*_ringkas.mmd` dan gambar `.png` yang tidak ada di folder `erd/`.
- Nama TCode: panduan dan ERD menyebut HVD1, flow menyebut HVDM (salinan HVD1). **Diputuskan HVDM** (10 Nov), baca HVD1 di dokumen sebagai HVDM.
- Nama tabel media: ERD `fai_foto`, flow `fai_media`.
- Staging di panduan Bab 13.1 memakai kolom `jml_*`, ERD memakai satu baris per kelas di `fai_panen_grading`. Pakai ERD.
- Panduan dan ERD merujuk "Bab 13.5" dan file PNG ERD yang tidak ada di folder.

## File proyek penting

| Lokasi | Isi |
|---|---|
| `sawit_grading/lib/pages/scan_page.dart` | Layar kamera, mode scan MULAI dan SELESAI |
| `sawit_grading/lib/pages/hasil_page.dart` | Hasil, koreksi, isi TPH dan pemanen, simpan |
| `sawit_grading/lib/pages/riwayat_page.dart` | Daftar scan tersimpan |
| `sawit_grading/lib/tracking/bunch_tracker.dart` | Tracker penghitung tandan unik |
| `sawit_grading/lib/models/scan_record.dart` | Model data dan daftar kelas (prototipe) |
| `sawit_grading/lib/services/scan_store.dart` | Simpan JSON (prototipe, akan diganti SQLite) |
| `sawit_grading/test/` | Tes unit tracker dan `ScanRecord` |
