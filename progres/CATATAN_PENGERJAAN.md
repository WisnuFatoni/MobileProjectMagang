# Catatan Pengerjaan & Pembaruan fAI26

Dokumen ini mencatat setiap perubahan, penambahan fitur, dan perbaikan tampilan yang dilakukan pada proyek fAI26.

---

## [12 November 2026] Pembaruan Tampilan & UX Beranda Mobile (`fAI26-Mobile`)

### Latar Belakang & Masalah
Saat aplikasi pertama kali dibuka di HP Android, tampilan Beranda terasa sangat "polos" dan kosong:
1. Angka statistik masih `0`, `0`, `0` tanpa ada ikon atau aksen visual pembeda.
2. Saat belum ada transaksi hari ini, bagian catatan terbaru hanya menampilkan teks abu-abu polos (*"Belum ada catatan. Pilih TPH lalu mulai scan."*).
3. Pengguna di lapangan tidak mengetahui apakah HP sedang berada dalam kondisi **Online (terhubung ke server)** atau **Offline (tersimpan lokal)**.
4. Tidak ada panduan ringkas cara kerja maupun tips pemindaian tumpukan TBS bagi krani.

---

### Perubahan yang Dilakukan

#### 1. Header & Indikator Status Lingkungan Lapangan
* **Logo Mini fAI26:** Menambahkan ikon logo tandan sawit mini di sudut kanan atas header dengan bingkai kapsul kaca semi-transparan (`bg_pill_status.xml`).
* **Pill Status Koneksi Real-Time:** 
  * Di bawah nama dan tanggal, ditambahkan badge kapsul dinamis (`pillStatusKoneksi`).
  * 🟢 **Hijau (`ic_wifi_online.xml`):** Menampilkan *"Online • Terhubung ke Server"* jika sesi token aktif ke API.
  * 🟡 **Kuning/Oranye (`ic_wifi_offline.xml`):** Menampilkan *"Mode Offline • Tersimpan di HP"* saat di luar jangkauan sinyal.
  * Badge ini interaktif (dapat diklik) untuk menampilkan dialog/toast informasi URL server dan status kerja offline.

#### 2. Visualisasi Tiga Kartu Ringkasan
Setiap kartu statistik kini memiliki ikon bertema tersendiri di sudut kanan atas angka:
* **Catatan Hari Ini:** Ikon dokumen panen hijau (`ic_stat_panen.xml`).
* **Janjang Hari Ini:** Ikon buah/tandan sawit oranye segar (`ic_stat_janjang.xml`).
* **Belum Terkirim:** Ikon awan sinkronisasi (`ic_stat_sync.xml`).
* Penyesuaian tipografi dan tata letak agar angka dan label tetap proporsional dan mudah dibaca di bawah terik matahari.

#### 3. Banner Tips Lapangan Pindai TBS AI (`kartuTips`)
* Menambahkan kartu tips praktis di antara Kartu Pindai TPH dan Catatan Terbaru.
* Berlatar belakang warna emas panen lembut (`brand_secondary_container`) dengan ikon lampu (`ic_lightbulb.xml`).
* Pesan panduan: *"Tips Pindai AI di TPH: Jaga jarak kamera ~1,5 m & sisir tumpukan perlahan satu arah agar semua tandan terhitung akurat."*

#### 4. Transformasi *Empty State* Menjadi Kartu Panduan Alur Kerja (`boxEmptyState`)
Menggantikan teks kosong polos menjadi kartu edukatif yang memandu alur kerja krani:
* **Header Kartu:** Logo sawit mini + Judul *"Siap Mencatat Panen"* & subjudul *"Belum ada transaksi hari ini"*.
* **3 Langkah Pindai di Lapangan:**
  1. 1️⃣ **Scan QR TPH** pada patok untuk mengunci lokasi.
  2. 2️⃣ **Sisir tumpukan TBS** perlahan dengan kamera AI.
  3. 3️⃣ **Tinjau hasil grading**, koreksi manual, dan simpan.
* Kartu ini otomatis menghilang dan digantikan oleh daftar kartu riwayat begitu krani sudah melakukan pencatatan panen pertama.

---

### Berkas yang Dibuat / Dimodifikasi

1. **Berkas Drawable Baru:**
   * `app/src/main/res/drawable/ic_stat_panen.xml` (Ikon statistik dokumen panen).
   * `app/src/main/res/drawable/ic_stat_janjang.xml` (Ikon statistik tandan sawit).
   * `app/src/main/res/drawable/ic_stat_sync.xml` (Ikon statistik cloud sync).
   * `app/src/main/res/drawable/ic_lightbulb.xml` (Ikon tips bohlam panduan).
   * `app/src/main/res/drawable/ic_wifi_online.xml` (Titik status online hijau).
   * `app/src/main/res/drawable/ic_wifi_offline.xml` (Titik status offline kuning).
   * `app/src/main/res/drawable/bg_pill_status.xml` (Background kapsul status header).
   * `app/src/main/res/drawable/bg_banner_tips.xml` (Background banner tips emas).
   * `app/src/main/res/drawable/bg_empty_guide.xml` (Background logo empty state).

2. **Berkas Layout & Kode:**
   * `app/src/main/res/layout/fragment_beranda.xml` (Tata letak baru header, kartu, banner tips, dan empty state).
   * `app/src/main/java/com/example/fai26/BerandaFragment.java` (Pengendali logika status koneksi real-time, event click pill, dan visibilitas panduan).

---

### Pengujian & Pemasangan
* **Kompilasi:** Berhasil dibangun (`assembleDebug`) tanpa galat menggunakan Android Studio JBR (Java 17).
* **Pemasangan ke HP:** APK hasil kompilasi telah langsung dipasang (*streamed install*) ke perangkat HP Android fisik via kabel USB ADB (`RRCT400144F`).



---

## [12 November 2026] Fitur Penampil Foto Layar Penuh & Zoom Panorama (`LihatFotoDialog`)

### Latar Belakang & Masalah
Foto hasil pemindaian (khususnya foto panorama tumpukan TBS yang memanjang) sebelumnya tampil kecil dan terpotong:
1. Di layar hasil scan (`HasilActivity`), foto dibatasi tinggi `260dp` dengan `centerCrop` sehingga ujung kiri dan kanan terpotong dan tidak bisa diklik.
2. Di Beranda dan Riwayat (`CatatanRowFactory`), foto hanya berupa thumbnail kecil `56x56 dp` yang tidak interaktif.
3. Pengguna tidak bisa memeriksa kejelasan buah atau kotak deteksi AI secara detail karena tidak ada fitur perbesar (zoom) maupun geser (pan).

---

### Solusi & Fitur Baru yang Dibangun

1. **Komponen `ZoomImageView` (`ui/ZoomImageView.java`):**
   * Komponen kustom turunan `AppCompatImageView` asli Android tanpa pustaka pihak ketiga.
   * Mendukung **cubit-zoom (pinch-to-zoom)** dari 1x hingga 5x.
   * Mendukung **geser (pan)** dengan jari secara mulus untuk menyisir seluruh panjang foto panorama.
   * Mendukung **ketuk-ganda (double-tap zoom)** untuk langsung memperbesar 2.5x atau kembali ke ukuran pas layar.
   * Menjaga batas tepian (*boundary clamp*) agar foto tidak hilang keluar layar.

2. **Dialog Layar Penuh Imersif (`ui/LihatFotoDialog.java` & `res/layout/dialog_lihat_foto.xml`):**
   * Tema gelap imersif (*fullscreen dark theme*) bebas gangguan.
   * Top bar dengan tombol Tutup (✕), judul Blok/TPH, dan tombol **RESET** untuk mengembalikan posisi zoom.
   * Indikator petunjuk di bagian bawah: *"Ketuk 2x untuk perbesar detail buah"*.
   * Pemuatan bitmap asinkron di thread latar belakang agar tidak menyebabkan lag saat membuka foto beresolusi tinggi.

3. **Integrasi di Layar Aplikasi:**
   * **Layar Hasil Scan (`HasilActivity` & `activity_hasil.xml`):**
     * Foto dibingkai dalam kartu elegan dengan mode `fitCenter` agar seluruh panorama terlihat utuh.
     * Ditambahkan badge *"🔍 Ketuk untuk perbesar"*.
     * Mengetuk kartu atau foto akan langsung membuka penampil layar penuh.
   * **Daftar Catatan Beranda & Riwayat (`CatatanRowFactory.java`):**
     * Mengetuk thumbnail foto atau kartu catatan akan membuka penampil foto layar penuh dengan judul Blok dan nomor TPH.

4. **Berkas Baru:**
   * `app/src/main/java/com/example/fai26/ui/ZoomImageView.java`
   * `app/src/main/java/com/example/fai26/ui/LihatFotoDialog.java`
   * `app/src/main/res/layout/dialog_lihat_foto.xml`
   * `app/src/main/res/drawable/ic_close.xml`
   * `app/src/main/res/drawable/ic_zoom_in.xml`

5. **Hasil Pengujian & Pemasangan:**
   * Kompilasi APK debug sukses (`BUILD SUCCESSFUL in 25s`).
   * APK langsung dipasang ke perangkat HP uji (`RRCT400144F`) via USB.


---

## [13 November 2026] Peningkatan Kualitas Panorama: Jahitan Mulus Tanpa Potongan Bergerigi, Kotak Deteksi Bernomor & Pelacakan AI Peka

### Latar Belakang & Masalah
Saat pengujian di lapangan, ditemukan beberapa kendala signifikan pada hasil foto bukti dan deteksi AI:
1. **Foto Terpotong Bergerigi (*Staircase Effect*):** Hasil jahit foto panorama tampak seperti potongan puzzle/kertas yang bertingkat-tingkat dengan latar hitam pekat di bagian atas dan bawah akibat goyangan tangan wajar saat menyisir tumpukan TBS.
2. **Sambungan Kaku & Kotak Terbelah (*Sliced Boxes*):** Potongan frame ditempel saling menimpa secara kaku (*hard seams*). Garis kotak hijau dan label kematangan terbakar di potongan lama terbelah oleh potongan baru berikutnya.
3. **Susah Dihitung di Hasil Jepret:** Kotak deteksi tidak memiliki nomor urut. Atasan dan krani kesulitan mencocokkan jumlah tandan riil dengan angka total.
4. **AI Kadang Tidak Mendeteksi Tandan:** Parameter pelacak (`BunchTracker`) terlalu ketat (`minHits = 5`, `highConf = 0.40f`, `minArea = 0.03f`), sehingga tandan yang tersapu agak cepat atau tandan di sudut langsung diabaikan.
5. **Tampilan di HP Ceper:** Tumpukan TBS memanjang ke samping, tetapi dibuka di HP layar tegak (portrait) sehingga foto tampak pipih di tengah layar.

---

### Perubahan & Solusi yang Diterapkan

#### 1. Penghilangan Undakan Tangga & Pemangkasan Rata (*Clean Rectangular Auto-Crop*)
* Di `PanoramaPemindai.java`, sistem sekarang menghitung batas tumpang-tindih vertikal efektif (`overlapTop` dan `overlapBottom`) di mana seluruh sapuan kamera saling melengkapi.
* Kanvas panorama otomatis dipangkas (*auto-cropped*) ke area persegi panjang penuh. Seluruh latar hitam dan pola bertangga di tepi atas-bawah **hilang 100%**, menghasilkan satu foto persegi panjang solid seperti kamera panorama profesional.

#### 2. Peleburan Sambungan Halus (*Soft Edge Feathering*)
* Setiap potongan frame berikutnya (`i > 0`) diberikan gradasi transparansi (*alpha feathering* selebar 30–100 px) pada tepi kirinya sebelum digabungkan.
* Tidak ada lagi garis patahan vertikal tajam; tanah, pelepah, dan buah melebur mulus secara alami.

#### 3. Kotak Deteksi Bersih & Penomoran Janjang Otomatis (`#1`, `#2`, `#3`...)
* Potongan foto disimpan murni tanpa coretan kotak deteksi mentah.
* Setelah panorama tersambung utuh, seluruh koordinat deteksi diproyeksikan ke skala panorama dan diproses dengan *Global Non-Maximum Suppression (NMS)* untuk menggabungkan kotak buah yang sama antar-frame.
* Setiap janjang buah sawit yang terdeteksi diberi:
  * **Nomor Urut Janjang:** `#1 Matang 88%`, `#2 Matang 91%`, `#3 Mengkal 70%`, dst., diurutkan rapi dari kiri ke kanan.
  * **Badge Kontras Tinggi:** Warna sesuai kelas (hijau, kuning, oranye, merah) dengan latar membulat dan teks kontras otomatis (putih/hitam).
  * **Watermark Banner Ringkasan (Pojok Kiri Atas):** Menampilkan badge elegan bertuliskan:
    `HASIL SCAN TPH: [X] JANJANG`
    `Matang: A • Mengkal: B • Lewat Matang: C`
* Peningkatan ini juga diterapkan pada foto satu frame di `KeyframeRenderer.java`.

#### 4. Sensitivitas Pelacak AI Ditingkatkan (`BunchTracker.java`)
* Melonggarkan batas `highConf` dari `0.40f` ke `0.30f` agar tandan dengan confidence 30–39% tetap didaftarkan.
* Menurunkan batas `minHits` dari `5` ke `4` frame agar sapuan kamera dengan kecepatan wajar tidak kehilangan tandan.
* Menurunkan batas `minArea` dari `0.03f` ke `0.015f` agar tandan yang sedikit lebih jauh atau tertutup sebagian tetap sah dihitung.

#### 5. Fitur "PUTAR / Rotasi Layar" di Penampil Foto HP (`LihatFotoDialog`)
* Menambahkan tombol **"PUTAR"** (`btnPutar`) dengan ikon rotasi layar (`ic_screen_rotation.xml`) di toolbar dialog foto.
* Pengguna cukup mengetuk tombol ini untuk langsung memutar layar ke mode **Landscape**. Foto panorama memanjang langsung memenuhi seluruh layar HP secara masif dan detail. Orientasi otomatis kembali normal saat dialog ditutup.

---

### Berkas yang Dimodifikasi & Dibuat
1. `fAI26-Mobile/app/src/main/java/com/example/fai26/panorama/PanoramaPemindai.java` (Jahitan mulus, soft feathering, auto-crop tangga hitam, NMS global, nomor janjang urut, watermark banner).
2. `fAI26-Mobile/app/src/main/java/com/example/fai26/tracking/BunchTracker.java` (Peningkatan sensitivitas deteksi tandan).
3. `fAI26-Mobile/app/src/main/java/com/example/fai26/ui/KeyframeRenderer.java` (Penomoran tandan #1, #2... dan watermark banner untuk foto tunggal).
4. `fAI26-Mobile/app/src/main/java/com/example/fai26/ui/LihatFotoDialog.java` (Pengendali rotasi layar landscape/portrait).
5. `fAI26-Mobile/app/src/main/res/layout/dialog_lihat_foto.xml` (Tombol PUTAR di top bar).
6. `fAI26-Mobile/app/src/main/res/drawable/ic_screen_rotation.xml` (Ikon rotasi layar putih).

---

### Status Pengujian
* **Unit Test:** `.\gradlew testDebugUnitTest` sukses 100% (`BUILD SUCCESSFUL in 1m`).
* **Kompilasi APK:** `.\gradlew assembleDebug` sukses (`BUILD SUCCESSFUL in 41s`).
* **Pemasangan ke HP:** APK berhasil dipasang langsung (*Streamed Install Success*) ke perangkat fisik Samsung `RRCT400144F` via ADB USB. Port reverse 8080 aktif.

---

## [14 November 2026] Perombakan Mesin Jahit Panorama (Optimal Seam Carving + Direct Registration) & Sensitivitas Deteksi Tumpukan Padat

### Latar Belakang & Analisis Akar Masalah (*Root Cause Analysis* dari Foto Lapangan)
Berdasarkan bedah piksel langsung pada berkas foto hasil pengujian lapangan yang ditarik dari HP (`03f06240-3f95-44f5-8a9a-e0003faf4974.jpg`), ditemukan 4 akar masalah teknis mengapa bagian dalam foto tampak seperti **"5 gambar ditempel-tempel yang kepotong-potong"** dan mengapa AI melewatkan sejumlah janjang sawit yang jelas terlihat:

1. **Halusinasi Geser (*False Shift Drift*) pada `PenaksirGeser` yang Menggandakan Tumpukan:**
   * Saat krani mengarahkan kamera ke satu tumpukan (~20 janjang) sambil sedikit maju/miring untuk membaca brondolan, `PenaksirGeser.biaya()` sebelumnya mengurangi rata-rata global (`a.rata` dan `b.rata`) alih-alih rata-rata lokal di area tumpang-tindih (*overlap*).
   * Kontras tinggi antara tumpukan TBS yang gelap dan tanah pasir yang terang mengecoh penaksir resolusi kasar (`22x40`), menghasilkan pergeseran palsu `dx = -10` per frame. Akibatnya, `PanoramaPemindai` menempelkan 7 potongan dari **tumpukan yang sama** secara berulang-ulang di posisi `X = 0, 180, 360, 540, 720, 900, 1080` sehingga tandan sawit yang sama terbelah dan berulang 4–5 kali di tengah gambar.
2. **Kegagalan `PorterDuff.Mode.DST_IN` pada Bitmap JPEG & Garis Potong Lurus:**
   * `BitmapFactory.decodeFile()` pada berkas `.jpg` menghasilkan `Bitmap` tanpa kanal alfa (`hasAlpha() == false`). Menggambar gradien transparan dengan `Canvas` di atasnya diabaikan oleh Skia Android, sehingga tiap potongan tetap menimpa potongan sebelumnya dengan garis potong vertikal lurus yang membelah buah sawit.
3. **Tidak Ada Registrasi Langsung Antar-Potongan & Gerbang Kualitas Perspektif 3D:**
   * Potongan (*tile*) ditempel buta mengikuti akumulasi geseran frame kecil tanpa dicocokkan ulang secara langsung (*direct pairwise registration*) antar-potongan. Selain itu, saat kamera bergerak maju-mundur (perubahan skala 3D/paralaks) pada satu tumpukan yang sudah muat dalam 1 frame, sistem tetap memaksa menempel banyak potongan alih-alih memakai 1 foto resolusi penuh terbaik yang utuh.
4. **Ambang Confidence YOLO (`0.25f`) & Luas Minimum Tracker (`0.015f`) Terlalu Tinggi untuk Tumpukan Padat:**
   * Pada layar tegak (*portrait* `720x1280`) yang di-*letterbox* ke input model `640x640`, lebar aktif hanya `360px`. Dalam tumpukan 20+ janjang, ukuran tiap janjang hanya sekitar `70x75px` (`~1.0%` luas layar, di bawah `0.015f`) dengan skor *confidence* `0.17–0.24` (di bawah `0.25f`), sehingga janjang di sisi kanan tumpukan tidak diberi kotak deteksi.

---

### Solusi & Perombakan Arsitektur yang Diterapkan

#### 1. Perbaikan Penaksir Geser Lokal (`panorama/PenaksirGeser.java`)
* Memperbarui `PenaksirGeser.biaya()` agar menghitung rata-rata pencahayaan lokal (`rataA` dan `rataB`) **khusus di dalam jendela tumpang-tindih** `[x0..x1, y0..y1]`.
* Mencegah perubahan komposisi latar (tanah terang vs tumpukan gelap) memicu pergeseran palsu ketika kamera sedang menahan posisi di depan tumpukan.

#### 2. Mesin Jahit Panorama Berbasis Larik Piksel ARGB & *Dynamic Programming Optimal Seam Carving* (`panorama/PanoramaPemindai.java`)
* **Penyimpanan Cadangan Frame Tunggal Terbaik (`mentahTerbaik`):** Selama penyisiran berlangsung, sistem selalu mencatat frame resolusi penuh dengan jumlah dan skor deteksi janjang tertinggi.
* **Registrasi Pasangan Langsung (*Direct Pairwise Tile Alignment*) & Penyaringan Duplikat:**
  * Sebelum menempel potongan, posisi tiap potongan dicocokkan langsung terhadap potongan sebelumnya (`cocokkanPasanganLangsung`), termasuk memeriksa di sekitar `(0, 0)`.
  * Potongan yang tumpang-tindih di tempat yang sama (pergeseran `< 16%` ukuran potongan) otomatis dibuang agar tidak ada tumpukan gambar ganda.
  * **Gerbang Kualitas Otomatis:** Bila seluruh penyisiran ternyata hanya melihat satu tumpukan yang sama (rentang geser bersih `< 20%`) atau galat sambungan akibat zoom/paralaks 3D melebihi ambang (`> 25.0`), sistem otomatis menjatuhkan pilihan ke **1 foto resolusi penuh terbaik (`mentahTerbaik`)** yang 100% utuh, tajam, dan bebas sambungan.
* **Penyeimbang Eksposur & Warna (*RGB Gain Matching*):**
  * Menghitung rasio rata-rata kanal R, G, B pada area tumpang-tindih dan menyamakan eksposur potongan baru (`clamp [0.82, 1.22]`) sehingga tidak ada belang warna antar-potongan.
* **Jalur Jahit Berbiaya Minimum (*Dynamic Programming Optimal Seam Carving*):**
  * Mengimplementasikan `cariJalurJahitTegak` dan `cariJalurJahitMendatar` menggunakan *Dynamic Programming* yang mencari jalur sambungan dengan selisih warna + gradien tepi paling kecil dari atas ke bawah (atau kiri ke kanan).
  * Jalur sambungan **meliuk secara cerdas melalui celah tanah/antar-buah** dan menghindari memotong badan janjang sawit.
* **Pembauran Kosinus Murni di Larik `int[]` ARGB (`jahitPotonganOptimal`) & Pengisian Sudut (`isiSudutKosong`):**
  * Membaurkan piksel di sepanjang jalur jahitan meliuk (`±44px`) menggunakan kurva kosinus langsung di memori `int[]` ARGB (bebas dari bug `hasAlpha() == false` pada JPEG) serta mengisi sudut pinggiran agar bebas tangga hitam.

#### 3. Penyesuaian Kepekaan Deteksi AI & Pelacak Tumpukan Padat (`detect/YoloDetector.java` & `ScanActivity.java`)
* Menurunkan ambang `YoloDetector.CONF` dari `0.25f` menjadi `0.17f` agar janjang sawit berukuran kecil di dalam tumpukan besar (20–30 janjang) tetap terdeteksi.
* Mengonfigurasi `BunchTracker` di `ScanActivity.java` dengan parameter lapangan (`highConf = 0.18f`, `minIouHigh = 0.22f`, `minIouLow = 0.40f`, `minHits = 3`, `minArea = 0.005f`, `mergeIou = 0.55f`) sehingga tandan yang tampak jelas di layar langsung terhitung dan diberi kotak bernomor tanpa mengorbankan ketahanan terhadap derau.

---

### Berkas yang Dimodifikasi
1. `fAI26-Mobile/app/src/main/java/com/example/fai26/panorama/PenaksirGeser.java` (Perhitungan rata-rata pencahayaan lokal pada area tumpang-tindih untuk menghentikan halusinasi pergeseran).
2. `fAI26-Mobile/app/src/main/java/com/example/fai26/panorama/PanoramaPemindai.java` (Cadangan 1 frame utuh terbaik, registrasi pasangan langsung, penyaringan potongan duplikat, gerbang kualitas paralaks 3D, penyeimbang warna RGB, *Dynamic Programming Optimal Seam Carving*, pembauran kosinus `int[]` ARGB, dan pengisian sudut).
3. `fAI26-Mobile/app/src/main/java/com/example/fai26/detect/YoloDetector.java` (Penurunan ambang `CONF` ke `0.17f` untuk tumpukan padat).
4. `fAI26-Mobile/app/src/main/java/com/example/fai26/ScanActivity.java` (Penyesuaian parameter `BunchTracker` untuk tandan padat di lapangan).

---

### Status Pengujian & Pemasangan
* **Unit Test & Build APK:** `.\gradlew testDebugUnitTest assembleDebug` lulus 100% (`BUILD SUCCESSFUL in 53s`).
* **Pemasangan ke HP:** APK terbaru dipasang langsung ke perangkat Samsung `RRCT400144F` via ADB USB beserta konfigurasi `adb reverse tcp:8080 tcp:8080`.




---

## [17 November 2026] Dashboard Beranda Mobile Real-Time Server + Offline Hybrid (Produksi Harian, Target, Rekap Blok, Tren 7 Hari & Refresh Server)

### Latar Belakang
1. Beranda mobile sebelumnya hanya berisi 3 angka (catatan hari ini, janjang hari ini, belum terkirim), kartu mulai scan, dan 3 catatan terbaru, sehingga terasa kosong.
2. Dashboard juga **tidak boleh hanya menghitung dari database lokal HP saja** — aplikasi harus bisa di-*refresh* langsung ke database server (`fai26_dev`) agar menampilkan **data real produksi** (termasuk data yang tercatat di server/perangkat lain dalam satu unit bagi mandor/asisten/admin), sekaligus tetap berfungsi penuh saat di kebun tanpa sinyal (offline).

### Arsitektur & Fitur Baru yang Dibangun

#### 1. Endpoint API Baru di Server (`GET /api/panen/ringkasan` pada `fai26-api` v0.9.0)
* Menambahkan endpoint `GET /api/panen/ringkasan?tanggal=YYYY-MM-DD` (permission `panen.lihat`) yang menghitung langsung dari database utama server (`dbo.fai_panen` & `dbo.fai_panen_grading`):
  * `catatan_hari_ini` dan `janjang_hari_ini`
  * `per_kelas`: jumlah janjang per kelas kematangan (`ripe`, `underripe`, `overripe`, `unripe`, `empty`, `abnormal`)
  * `per_blok`: 5 blok teratas hari ini beserta jumlah TPH, catatan, dan janjang
  * `tren_7_hari`: produksi janjang dan catatan selama 7 hari terakhir
  * `catatan_per_status`: jumlah catatan per status (`TERKIRIM`, `DITERIMA`, `DITOLAK`)
* Otomatis menyesuaikan lingkup hak akses: pengguna biasa (`SENDIRI`) melihat catatannya sendiri, sedangkan pemegang `panen.koreksi` (`UNIT` — mandor, asisten, admin) melihat **data real seluruh unit**.

#### 2. Sinkronisasi & Refresh Real-Time di Beranda Mobile (`DashboardBeranda.java` & `BerandaFragment.java`)
* **Tarik Data Real Server + Gabung Draft Lokal Belum Terkirim:**
  * Saat Beranda dibuka atau tombol **Segarkan / Refresh** ditekan, aplikasi memanggil `GET /api/panen/ringkasan` melalui `HttpServerApi` (dengan perpanjangan token otomatis bila kedaluwarsa).
  * Angka dari server digabungkan secara cerdas dengan catatan lokal di HP yang berstatus belum terkirim (`DRAFT`, `DISETUJUI`, `SIAP_KIRIM`) melalui query `ringkasanHariBelumKirim`, `kelasHariBelumKirim`, `rekapBlokHariBelumKirim`, dan `janjangPerHariBelumKirim` sehingga tidak ada penghitungan ganda maupun catatan baru yang terlewat.
* **Tombol "Segarkan" di Header & Tombol "Refresh" di Kartu Produksi:**
  * Menekan tombol **Segarkan** (di kanan atas header) atau **Refresh** (di kartu Produksi hari ini) akan menjalankan `SyncRunner.jalankanSinkron` (mengirim antrean panen/foto/koordinat ke server + menarik status terbaru & master), lalu menarik ulang data real dari server dan menampilkan indikator `"Data Real Server (Se-Unit / Akun Ini) • Diperbarui HH:mm"`.
  * Bila sedang tanpa sinyal di kebun, aplikasi otomatis memakai cache server terakhir + database lokal HP (`"Data Lokal HP (Offline)"`).
* **5 Komponen Visual Baru di Beranda:**
  1. **Produksi Hari Ini & Target Harian:** Bar progres `janjang / target` (default 200, bisa diubah dengan mengetuk angka target) dan indikator sumber data (`Data Real Server` vs `Data Lokal HP`).
  2. **Kualitas Panen per Kelas:** Persentase matang, bar komposisi warna per kelas, dan rincian jumlah + persentase tiap kelas.
  3. **Rekap per Blok Hari Ini:** 5 blok teratas lengkap dengan jumlah TPH, catatan, janjang, dan bar proporsi.
  4. **Tren 7 Hari Terakhir:** Grafik batang produksi harian selama 7 hari terakhir.
  5. **Kartu Peringatan Tindak Lanjut:** Muncul otomatis bila ada catatan `DITOLAK` server atau belum terkirim.

### Berkas yang Dimodifikasi & Dibuat
* **Backend (`fai26-api`):**
  1. `internal/model/panen.go` (`RingkasanPanen`, `KelasJumlah`, `BlokRekap`)
  2. `internal/repository/panen_repository.go` (`Ringkasan`)
  3. `internal/service/panen_service.go` & `internal/service/interfaces.go` (`Ringkasan`)
  4. `internal/controller/panen_controller.go` & `internal/routes/routes.go` (`GET /api/panen/ringkasan`)
  5. `docs/API.md` & `docs/openapi.yaml` (Spesifikasi endpoint v0.9.0; lulus `go test ./internal/...`)
* **Mobile (`fAI26-Mobile`):**
  1. `app/src/main/java/com/example/fai26/DashboardBeranda.java` (Mesin dashboard hybrid Server Real-Time + Lokal Offline)
  2. `app/src/main/java/com/example/fai26/BerandaFragment.java` (Integrasi auto-refresh server & tombol Segarkan/Refresh)
  3. `app/src/main/java/com/example/fai26/sync/ServerApi.java` & `HttpServerApi.java` (`ringkasanPanen`)
  4. `app/src/main/java/com/example/fai26/db/dao/PanenDao.java` & `Hasil.java` (Query agregasi lokal & belum terkirim)
  5. `app/src/main/res/layout/fragment_beranda.xml` (Tombol Segarkan header, tombol Refresh dashboard, indikator sumber data, dan 4 kartu analitik)
