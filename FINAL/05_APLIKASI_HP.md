# 05. Aplikasi HP (Android) Lengkap

Proyek `fAI26-Mobile/`, paket `com.example.fai26`, Android native **Java**, minSdk 24, targetSdk 35, compileSdk 36, versi aplikasi 1.0. Nilai konstanta di dokumen ini **dibaca dari kode** pada 22 Nov 2026. Basis data lokal dijelaskan di `03_DATABASE_HP_ROOM.md`; alur bisnis di `07_ALUR_BISNIS_DAN_ATURAN.md`.

## 1. Gambaran

Aplikasi **offline-first**: semua pekerjaan lapangan berjalan tanpa sinyal dan disimpan di Room (SQLite); sinkronisasi ke server terjadi di latar belakang atau lewat tombol. Dua modul pada layar awal (Home): **Panen** dan **IPD**. Kotak yang tampil mengikuti hak `akses.panen` dan `akses.ipd` (diatur admin di halaman Hak Akses web).

## 2. Peta kode

| Paket | Isi | Berkas terbesar |
|---|---|---|
| (akar) | Layar utama: Splash, Login, Main, Home, Beranda, Riwayat, Persetujuan, Akun, Scan QR, Scan, Hasil | `BerandaFragment` 779, `DashboardBeranda` 531, `PersetujuanFragment` 475, `HasilActivity` 456, `ScanActivity` 445, `AkunFragment` 422 |
| `auth` | Login online/offline, hash PBKDF2, sesi, ganti password, foto profil lokal, `SesiToken` | `AuthService` 256 |
| `db`, `db/dao`, `db/entity` | Room: 28 entitas, DAO, repositori | `PanenRepository` 473, `IpdRepository` 237 |
| `detect` | `YoloDetector` (TFLite), `YoloPostprocess`, `Detection` | |
| `tracking` | `BunchTracker`, `Track` (pelacak tandan) | |
| `panorama` | `PanoramaPemindai`, `PenaksirGeser`, `RancangPanorama`, `KanvasPanorama` | `PanoramaPemindai` 1.072 |
| `lokasi` | `PencatatLokasi` (Panen), `JejakService` (IPD), `FilterJejak`, `Lokasi` | |
| `ipd` | Inspeksi IPD: formulir, beranda, riwayat, persetujuan, kompres foto | `IpdInspeksiActivity` 1.458 |
| `sync` | `SyncService`, `HttpServerApi`, `MasterSync`, `PayloadBuilder`, `PengaturanSync`, `SyncWorker`/`SyncJadwal` | `SyncService` 697, `HttpServerApi` 558 |
| `qr` | `QrTph`, `QrDecoder` | |
| `sistem` | `KondisiPerangkat` (baterai, ruang simpan) | |
| `ui` | `GlassNavBar` (menu kaca), `BackdropBlurView`, `PemotongBulatView` (pemotong foto profil), `ZoomImageView`, `LogoSplashView`, `KepalaHijau` | |

Dependensi utama: AppCompat, Material 3, ConstraintLayout, CameraX (core, camera2, lifecycle, view), TensorFlow Lite + GPU, Room, ZXing core, WorkManager. Tes: JUnit, `org.json`, Room testing, WorkManager testing, Espresso.

## 3. Izin Android dan keamanan platform

| Izin | Alasan |
|---|---|
| `INTERNET` | Sinkronisasi ke server |
| `CAMERA` | Pindai tandan, foto bukti, foto inspeksi, foto profil |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | GPS pencatat dan jalur inspeksi (**tidak** meminta izin lokasi latar belakang) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` | Layanan jalur GPS latar depan (`foregroundServiceType="location"`) dengan notifikasi tetap |
| `POST_NOTIFICATIONS` | Notifikasi layanan jalur (Android 13+) |

Token akses dan refresh disimpan **terenkripsi** (AES-256-GCM, kunci di Android Keystore) dan dikecualikan dari cadangan Android (`data_extraction_rules`). Alamat server pada build debug: `http://localhost:8080` (diteruskan lewat `adb reverse`); **build rilis kosong** dan harus diatur dan memakai HTTPS (lihat `08_PENERAPAN_DAN_OPERASI.md`).

## 4. Login dan akun

- Login **online dulu** bila server diatur; password salah, terkunci, atau nonaktif = ditolak (tidak jatuh ke offline). Jaringan putus = login offline.
- Verifier offline di HP: **PBKDF2-HMAC-SHA256, 100.000 iterasi**, salt acak 16 byte, hash 32 byte. Sukses online memperbarui verifier dan memperpanjang masa login offline **7 hari** (`OFFLINE_HARI`).
- Kunci akun: **5** kali salah (`MAKS_GAGAL`) mengunci **15** menit (`KUNCI_MENIT`), di HP dan di server (terpisah).
- Token akses 15 menit, refresh 30 hari dengan rotasi.
- **Kepemilikan token (perbaikan B-15, 22 Nov):** `PengaturanSync` mencatat **akun aktif** (`aturAkunAktif`) dan membuang token milik akun lain (`tokenBukanMilik`); `LoginService` memanggilnya pada login berhasil (online dan offline); `SesiToken` + `MainActivity` membereskan HP yang telanjur salah. Penyebab aslinya: login offline tidak menghasilkan token sehingga token pengguna sebelumnya dipakai ke server (akibatnya Setujui tercatat atas nama orang lain, 403).
- Ganti password sendiri (hanya online; mencabut sesi di HP lain). Hak akses mengikuti server (hak role di master; cache hak sesi diperbarui tanpa login ulang).
- Foto profil: kamera atau galeri, pemotong bulat (geser, zoom, putar), diproses server menjadi JPEG persegi 384 px; offline-first (`FotoProfilSync`).
- Pengaturan alamat server di layar Akun hanya tampil untuk admin (`master.kelola`).

## 5. Master data

`GET /api/master` dibandingkan dengan `versi` (hash dari jumlah baris dan waktu ubah terbaru). Diterapkan dalam satu transaksi; baris yang dihapus lunak di server ditandai (tidak dibuang); koordinat TPH yang direkam di HP tidak ditimpa kosong. Untuk **akun mandor**, master dibatasi: pemanen hanya yang `mandor_hrmb_code`-nya sama, dan divisi/blok/TPH hanya afdelingnya. Master memuat: unit, divisi, blok, TPH, pemanen, akun + role, **kode temuan IPD**, dan **hak role**. Aturan emas: semua alat yang mengubah master wajib mengisi `diubah_pada`. Pencarian TPH manual dan pemanen di Beranda bisa dilakukan dengan mengetik (`PencarianAdapter`: semua kata harus cocok, urutan bebas, mis. "c7 6").

## 6. Modul Panen

**Layar:** Beranda (dashboard), Riwayat, Persetujuan (hanya pemegang `panen.setujui`, yaitu krani), Akun, Scan QR, Scan (kamera), Hasil. Menu bawah berbentuk kapsul kaca yang bisa digeser (`GlassNavBar`, `BackdropBlurView`).

**Alur mandor:** scan QR TPH (atau pilih manual) → **pilih pemanen (wajib, satu)** → MULAI menyisir tumpukan → SELESAI → layar Hasil: koreksi jumlah per kelas (+/−), brondolan (kg), catatan, tanggal panen (bawaan hari ini, tidak boleh masa depan) → Simpan. Mandor tidak menyetujui sendiri; catatan masuk antrean kirim dan di server berstatus `MENUNGGU` sampai krani menyetujui.

**QR TPH:** isi `FAI26|TPH|1|<kode unit>|<kode blok>|<no tph>` (contoh `FAI26|TPH|1|KP_STL|A12|07`), harus sama persis dengan `tph.qr_kode`. QR hanya berisi kode; data lain dicari di master lokal sehingga terbaca tanpa sinyal. Spesifikasi cetak (koreksi galat H, margin 4 modul) di `lampiran/dokumen_proyek/SPEK_QR_TPH.md`.

**Deteksi dan hitungan:**

| Hal | Nilai |
|---|---|
| Model | `models/best.tflite`, YOLOv8n, 6 kelas, masukan 640×640 |
| Ambang | `CONF` 0,17 (longgar untuk tumpukan padat), `IOU` 0,60, maksimal 300 deteksi |
| Delegasi | GPU dengan cadangan CPU; sekitar 100 ms per frame di SM-A528B |
| Urutan kelas keluaran | `0 abnormal, 1 empty, 2 overripe, 3 ripe, 4 underripe, 5 unripe` |
| Pelacak | `BunchTracker` (mirip ByteTrack): ID tiap tandan, hitung hanya yang stabil beberapa frame, gabung kotak ganda (3 frame, 3,5 detik, IoU 0,55) |
| Sifat | **Estimasi**: tandan tertutup tidak terlihat; mandor wajib bisa mengoreksi; selisih `jml_ai` dan `jml_final` tercatat |

**Foto bukti = satu panorama.** Selama menyisir, HP menaksir geser antar frame (`PenaksirGeser`), menyimpan potongan, lalu menyambungnya menjadi satu JPEG (kotak deteksi bernomor `#1 #2 ...` dan banner ringkasan). Bila sapuan hanya melihat satu tumpukan atau penyejajaran gagal, hasilnya satu foto frame penuh. Penampil layar penuh dengan zoom. **Aturan produk: satu foto bukti per pindai.**

**GPS pencatat (`PencatatLokasi`):** titik terbaik selama sesi pindai (usia maksimum 2 menit). Status lokasi: `DI_TPH` (dalam **50 m**, `RADIUS_M`), `DI_LUAR`, `AKURASI_RENDAH` (akurasi lebih buruk dari **30 m**, `AKURASI_MAKS_M`), `TANPA_GPS`, `TPH_TANPA_KOORDINAT`. **Hanya menandai, tidak memblokir.** Koordinat TPH direkam saat pertama kali dipakai (hanya bila TPH belum punya titik), dikirim lewat `PUT /api/tph/{id}/koordinat`; server tidak menimpa titik yang ada.

**Validasi berlapis:** HP menolak simpan tanpa pemanen; memeriksa kombinasi tanggal + blok + TPH + pemanen yang sudah ada (peringatan sebelum pindai, penolakan saat simpan); server mengulang dan menegakkan (422/409).

**Riwayat:** status dari server (Menunggu krani, Terkirim, Diterima, Ditolak + alasan). Catatan **ditolak** dapat diedit lalu dikirim ulang dengan `panen_id` sama (`kirim_ulang`). Catatan **menunggu krani** juga boleh diedit ("Edit sebelum disetujui") sampai krani menyetujui; sesudah itu terkunci (server 423, HP menandai).

**Persetujuan krani (layar Persetujuan):** daftar dari server (`GET /api/panen/menunggu`; butuh sinyal): judul, pencatat dan waktu, pemanen, hasil per kelas, foto bukti. Aksi: **Setujui** (dengan **dialog konfirmasi**, perbaikan O-06 / B-16), **Tolak** (alasan wajib, maksimal 300 karakter), **Setujui semua** (dengan konfirmasi). Pesan galat dibedakan: belum login online untuk akun ini ("Keluar, lalu login lagi saat ada sinyal"), tanpa hak, dsb.

**Beranda:** ringkasan hari ini (catatan, janjang, belum terkirim); dashboard dari server (`GET /api/panen/ringkasan`: per kelas, per blok, tren 7 hari, per status; digabung dengan catatan lokal yang belum terkirim; cache bila offline); status online/offline; peringatan baterai rendah (<15%) dan ruang simpan menipis (<300 MB).

## 7. Modul IPD (Inspeksi Panen Detail)

Menu bawah bertingkat: di Home hanya Home dan Akun; di IPD: Home, **Mulai**, **Riwayat**, **Persetujuan** (bila `ipd.setujui`), Akun.

**Langkah 1, kepala:** tanggal, **divisi** (asisten terisi otomatis dari divisi), blok (difilter divisi; bisa dicari dengan mengetik), **pemanen** (satu catatan = satu pemanen/ancak), nomor ancak (opsional), **kemandoran** (satu mandor dipilih atau diketik dari akun berperan MANDOR). Bila pemanen + blok yang sama pernah diinspeksi dalam **7 hari terakhir** (inspeksi tiap Jumat), HP memberi **peringatan** (bukan larangan).

**Langkah 2, pokok demi pokok:** nomor baris dan nomor pokok (naik otomatis), tombol besar **BERSIH** atau pilih kode temuan **A, G, S, M1, M2, M3, M4, B1, B2, C, R** (boleh beberapa, jumlah bawaan 1). Semua pokok yang diperiksa dicatat supaya persen loses bisa dihitung. Bawaan **dua baris sekaligus** (satu lorong, mis. baris 120 dan 121); sakelar bisa dimatikan. Draf tersimpan otomatis tiap pokok. Foto bukti bisa diambil langsung dengan "Ambil foto pokok ini" (keterangan otomatis memuat baris dan pokok).

**Langkah 3, ringkasan dan kirim:** loses langsung terhitung; foto bukti **tanpa batas jumlah**, dikompres (sisi terpanjang 1600 px, JPEG kualitas 80, EXIF dibuang), tiap foto diberi keterangan; setelah terunggah, **berkas di HP dihapus**. **Kirim** otomatis menyatakan inspeksi valid. Status server `MENUNGGU` sampai asisten divisi memutuskan; pengirim pemegang `ipd.setujui` (asisten) langsung `DISETUJUI`.

**Persetujuan asisten (`ipd.setujui`):** ringkasan per kode dan foto; **Setujui** (terkunci) atau **Tolak** dengan alasan.

**Aturan edit:** selama belum disetujui (draf, menunggu asisten, ditolak) inspektur boleh mengubah dari Riwayat dan kirim ulang; setelah disetujui terkunci (server 423, HP menandai disetujui).

**Rumus:** loses = kode buah dan brondolan (A, G, S, M1 sampai M4, B1, B2); persen loses = pokok yang memuat kode loses dibagi pokok diperiksa. Pelepah (C, R) dilaporkan terpisah sebagai **temuan lain**.

### 7.1 Jalur GPS inspeksi (`JejakService`)

| Hal | Nilai |
|---|---|
| Jenis | Layanan **latar depan** (notifikasi tetap "jalur_ipd"), GPS saja, tanpa izin lokasi latar belakang |
| Pembaruan | `requestLocationUpdates(GPS, 1000 ms, 3 m)` |
| Saringan | akurasi **≤ 25 m** (`AKURASI_MAKS_M`) dan jarak **≥ 3 m** dari titik terakhir (`JARAK_MIN_M`) |
| Batas | berhenti otomatis setelah **8 jam**; `START_NOT_STICKY` |
| Sakelar | "Rekam jalur GPS" (bawaan mati bila yang diubah inspeksi yang sudah terkirim) |
| Data | Room `ipd_jejak` (`ipd_id`, waktu, lat, lon, akurasi); ikut terkirim bersama inspeksi (`jejak[]`, maksimal 20.000 titik) dan waktu tiap pokok (`pokok[].dicatat_pada`) |
| Privasi | Keterangan di bawah sakelar: lokasi hanya direkam selama inspeksi; hanya asisten, askep, dan KTU yang dapat melihat jalur; setiap pembukaan tercatat |
| Penghapusan lokal | Bersama draf yang dihapus; dan **setelah inspeksi berstatus DISETUJUI** (`IpdDao.hapusJejakTerkunci`, dipanggil dari `SyncService`) karena terkunci dan tak pernah dikirim ulang |

## 8. Sinkronisasi

- **Antrean `sync_queue`:** entitas `PANEN`, `FOTO`, `AUDIT`, `TPH_KOORDINAT`, `IPD`, `IPD_FOTO`; dikirim dari yang paling lama.
- **Jadwal:** WorkManager berkala **tiap 15 menit** (`fai26-sync-berkala`) ditambah satu kali segera (`fai26-sync-segera`) dan tombol "Kirim sekarang".
- **Coba ulang:** jaringan putus atau 429/5xx = sementara, jeda **30 detik berlipat sampai 30 menit**, maksimal **8** percobaan lalu `GAGAL`. Penolakan server (isi tidak sah) langsung `GAGAL` dan catatan `DITOLAK` dengan alasan. 409 (ganda) menandai DITOLAK dengan pesan server dan **tidak** diulang otomatis.
- **Hak kirim:** HP hanya mengirim catatan milik pengguna yang login online, kecuali pemegang `panen.koreksi` yang boleh mengirim semua. Hanya akun pemegang `panen.kirim` yang mengirim antrean (perbaikan B-14).
- **Urutan:** catatan, lalu foto, lalu audit (berkelompok maksimal 200 per batch). `panen_id` dibuat sekali saat simpan dan dipakai ulang untuk setiap percobaan.
- **Status balik:** `GET /api/panen?sejak=` dan `GET /api/ipd?sejak=`; HP memperbarui status lokal.

## 9. Tampilan

Tema hijau dengan kepala hijau seragam (`KepalaHijau`), splash dan tanda air memakai logo resmi fAI26 (animasi tenang), menu bawah berbentuk kapsul kaca, dan tombol besar untuk pemakaian di lapangan. Teks dan tampilan memakai Bahasa Indonesia. (Perilaku mode gelap HP dan keterbacaan di bawah sinar matahari belum diverifikasi secara khusus.)

## 10. Pengujian HP

- **Tes unit JVM: 139** (termasuk `TokenMilikAkunTest` 5 tes, `PencarianAdapterTest`, tes kompresi, filter jejak, dsb.). Dijalankan dengan `gradlew testDebugUnitTest` (JDK 17 untuk Gradle).
- **Tes instrumented: 159** (Room, sinkron, login, dsb.): sudah **dikompilasi tetapi belum dijalankan seluruhnya di perangkat** (Ptoni memilih tidak menjalankan seluruh rangkaian di ponsel); kelas yang tidak menyentuh sesi login nyata pernah dijalankan.
- Bukti di HP asli (SM-A528B): pindai, panorama, simpan, kirim, persetujuan krani, IPD, jalur 4 titik berakurasi 4 sampai 8 m di dalam ruangan.
- Perintah penting: `adb reverse tcp:8080 tcp:8080` (menghubungkan HP ke API di laptop; **hilang bila kabel USB dicabut dan dipasang lagi**); `gradlew --stop` setelah build (RAM laptop terbatas).

## 11. Keterbatasan yang diketahui

1. **Belum dipakai di kebun.** Akurasi hitungan di tumpukan nyata, panorama saat kamera menunduk (O-04), akurasi GPS di bawah tajuk sawit, baterai, dan perilaku layanan saat layar mati (O-12) belum terbukti.
2. **Akurasi AI:** uji TPH 5 tandan hanya 1 terdeteksi (O-03); model tidak dilatih ulang.
3. **Koneksi:** HP hanya terhubung lewat USB; belum ada koneksi tanpa kabel/HTTPS (O-02).
4. **Tanpa notifikasi dorong** (O-07): persetujuan dilihat saat tab dibuka; kartu "N catatan ditolak" di Beranda; sinkron 15 menit.
5. **Waktu pokok inspeksi lama** yang dikirim sebelum perbaikan 21 Nov salah (O-09).
6. **Foto IPD tidak menyimpan koordinatnya sendiri** (lokasi lewat jalur dan waktu).
7. **Kondisi cuaca** belum ada input (dikirim NULL; tidak ada master cuaca di ERP).
8. **Kode temuan IPD** hanya dapat diubah lewat basis data (O-11).
9. Teks tertanam di layout (101 teks; `strings.xml` hanya 1 entri) dan beberapa kelas besar (`IpdInspeksiActivity` 1.458 baris, `PanoramaPemindai` 1.072) adalah utang teknis dari evaluasi Sprint 1.
