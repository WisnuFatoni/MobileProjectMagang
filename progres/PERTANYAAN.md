# fAI26: Pertanyaan dan Keputusan

Pasangan dari [PROGRES.md](PROGRES.md). Terakhir diperbarui: 22 November 2026 (ditambah bagian "Tambahan 22 Nov" di akhir).

Tanda: **[Jawab]** dijawab Ptoni, **[ERP]** hasil pembacaan database ERP (dibawa Ptoni), **[Usulan]** rancangan dari saya, **[Buka]** belum selesai.

---

## 1. Jawaban Ptoni (10 Nov)

### Untuk krani atau mandor

| # | Pertanyaan | Jawaban | Dampak ke aplikasi |
|---|---|---|---|
| 1 | Buah pemanen dipisah atau dicampur? | **[Jawab]** Digabung | Skenario B (Bab 6 panduan). Satu scan mewakili satu TPH. Lihat butir terbuka B1. |
| 2 | Apa yang dicatat? | **[Jawab]** 6 kelas hasil AI, nama pemanen, jumlah per **janjang** (bukan kilo), brondolan per **kilo**. Sisanya Ptoni minta saya cari praktik terbaik (lihat bagian 3). | `brondolan_kg` (sudah ada di ERD) berisi kilogram. Janjang dihitung per tandan. |
| 3 | Kriteria matang, mentah, lewat matang | **[Jawab]** Samakan dengan dataset YOLO | Pakai 6 kelas model apa adanya. Tidak ada pemetaan baru. |
| 4 | Ada sinyal di TPH? | **[Jawab]** Kemungkinan tidak ada. Login offline **1 minggu** | Offline-first wajib. `offline_berlaku_sampai` = 7 hari (sesuai usulan ERD). |
| 5 | HP apa? | **[Jawab]** HP standar, bukan sangat bagus dan bukan sangat jelek | Target kelas menengah (contoh uji: SM-A528B). Beban inferensi dan baterai harus wajar. |

### Untuk pembimbing PT LAT

| # | Pertanyaan | Jawaban | Dampak |
|---|---|---|---|
| 6 | API resmi ke mySAP365? | **[Jawab]** Tidak ada. mySAP365 akan **mengambil langsung dari database** | Sesuai flow: HVDM membaca tabel `fai_*` di database `fai26`. API milik kita tetap perlu supaya HP bisa mengisi `fai26`. Konfirmasi di B2. |
| 7 | Boleh bikin database `fai26`? | **[Jawab]** Boleh | Fase 5 bisa jalan. |
| 8 | Master blok, TPH, pemanen boleh dibaca? | **[Jawab]** Boleh, "menyesuaikan" | Tabel pastinya tetap perlu dicari (lihat bagian 2 nomor 7). |
| 9 | Rotasi dan tutup gaji | **[Jawab]** Tutup gaji sekitar tanggal 1 sampai 4 awal bulan. Jumlah hari rotasi belum dijawab | Penguncian periode: data bulan lalu terkunci setelah tanggal tutup. Rotasi masih [Buka] (B3). |
| 10 | Tabel cluster tahun 2026 | **[Jawab]** Belum ada, nanti dibuat | Tugas di sisi ERP, bukan aplikasi. |
| 11 | Lisensi AGPL-3.0 | **[Jawab]** Boleh | Catat di laporan bahwa pembimbing mengizinkan. Bila nanti pindah ke native tanpa plugin, perlu dicek ulang, lihat B5. |
| 12 | Kumpulkan foto kebun untuk latih ulang? | **[Jawab]** Boleh, tapi Ptoni memilih **pakai dataset yang ada saja** | Tidak ada pelatihan ulang. Risiko akurasi di TPH tetap, lihat bagian 4. |
| 13 | TCode tujuan | **[Jawab]** **HVDM** | Nama TCode dianggap final. Dokumen HVD1 di panduan dibaca sebagai HVDM. |
| 14 | Pasang QR di TPH | **[Jawab]** Boleh memasang dan mencetak, pemasang menyesuaikan | Fitur scan QR bisa dibangun. |
| 15 | Mandor perlu menyetujui? | **[Jawab]** Perlu | Tambah status `DISETUJUI`, kolom `disetujui_oleh` dan `disetujui_pada` (ERD Bab 11 butir 12). Mandor jadi langkah sebelum kirim. |
| 16 | Siapa admin aplikasi? | **[Jawab]** Orang IT atau krani administrator, belum tahu | Siapkan layar admin sederhana atau seed akun. Keputusan siapa menyusul. |
| 17 | Tanggal selesai KKP | **[Jawab]** 15 Desember 2026, tetapi aplikasi hanya diberi waktu **3 minggu** | Jadwal dipadatkan, lihat bagian 5. |

---

## 2. Jawaban ERP (hasil baca database, dibawa Ptoni)

| # | Pertanyaan | Jawaban | Keyakinan | Tindak lanjut |
|---|---|---|---|---|
| 1a | Arti `FCM4CODE` | Akun biaya dari master `fcm4` (database `sap365pub_kp`), isinya "TM-Upah Panen", terisi di semua baris. Ada dua versi masa berlaku: OID 889 (sampai 29-07-2026) dan OID 9148 (mulai 01-07-2026). HVDM sebaiknya memilih versi yang berlaku pada **tanggal panen**, bukan nilai tetap | Tinggi | Dikerjakan di HVDM (Fase 6). |
| 1b | Arti `HVM2CODE` | Kosong semua tahun 2026. Tabel `hvm2` kosong (0 baris) | Sedang | Isi NULL. |
| 2 | Janjang mentah dan kurang matang | Tidak ada kolom di `hvd1da`, `hvd1db` kosong. Tetap di staging `fai26` | Tinggi | Tetap di `fai_panen_grading`. `erp_kolom` NULL. |
| 3 | Pecah kelas abnormal | Kolom `JJG_*` jarang terpakai. Usulan: busuk ke `JJG_BUSUK`, sakit ke `JJG_SAKIT`, sisanya `JJG_UNKNOWN`. Model perlu memisah kelas abnormal dulu | Sedang (usulan) | **Bentrok** dengan keputusan tanpa latih ulang. Lihat B4. |
| 4 | Isi `HASILHI1` | Jumlah janjang, bilangan bulat, rata-rata 10,3, maksimum 923 | Sedang | Janjang dipakai, bukan kilo. Cara menjumlah kelas masih [Buka] (B6). |
| 5 | Kode `KONDISI_CUACA` | Kolom hanya ada di `kp_stl2025` dan kosong. Tidak ada master cuaca | Tinggi | Tidak ada kode untuk dipakai. Kolom cuaca di aplikasi dibuat opsional, dikirim NULL, atau dihapus dari form. |
| 6 | Pembagian hasil per pemanen | HVD1 mencatat satu baris per pemanen per tiket panen (`HRMBCODE` terisi 100%, 83 pemanen berbeda, `NOTIKETPANEN` terisi). Tidak ada kolom TPH | Sedang | Lihat B1: buah digabung tetapi tiket per pemanen. |
| 7 | `divisi` tabel master tersendiri? | Belum ketemu. `asm1` (16 baris) adalah kategori area dan `asm2` (5 baris) jenis topografi, bukan divisi | Rendah | Divisi di aplikasi diisi dari master contoh sampai sumbernya ketemu. |
| 8 | Kolom yang sudah terpakai | Di `kp_le2026`: `NOSPB` terisi 3%, `harvest_employee_id` terisi hampir semua baris, `BJR` ada tetapi belum terisi | Tinggi | `NOSPB` dan `BJR` jangan dipakai. `harvest_employee_id`: lihat B7. |

---

## 3. Data yang dicatat: pembanding praktik di lapangan

Ptoni meminta saya mencari apa yang lazim dicatat di perusahaan besar. Dari pencarian saya (sebagian besar jurnal dan skripsi, **bukan SOP resmi perusahaan**), yang sering muncul:

| Data | Ada di rancangan kita? | Catatan |
|---|---|---|
| Tanggal, blok, TPH, pemanen, krani | Ya | Inti pencatatan. |
| Janjang per kelas mutu | Ya (6 kelas) | Contoh laporan mutu TBS memakai matang, kurang matang, mentah, terlalu matang, busuk, kosong, abnormal. |
| Brondolan dalam kg | Ya (`brondolan_kg`) | Sama dengan yang Ptoni sebut. |
| Foto dan GPS saat mencatat | Ya (foto, `gps_lat`, `gps_lon`) | Studi e-FACT (Kalimantan Selatan) mencatat langsung di TPH dengan GPS dan foto. |
| Tangkai panjang | **Tidak** | Sering masuk kriteria mutu panen. Bukan kelas model, bisa jadi hitungan manual krani. |
| Busuk dan sakit | Sebagian | Model hanya punya satu kelas abnormal. |
| Nomor tiket panen, SPB, kendaraan | **Tidak** | Dipakai untuk menghubungkan TPH ke angkutan dan pabrik. Di ERP ada `NOTIKETPANEN` dan `NOSPB`. |
| Tikus, kondisi lain | Tidak | Jarang, bisa masuk kolom catatan. |

**[Usulan]** Untuk 3 minggu ini jangan tambah kolom baru. Cukup yang sudah ada di ERD, ditambah satu isian catatan. Tangkai panjang dan nomor tiket ditanyakan ke pembimbing dulu, karena menambah form dan skema.

Sumber:
- [Jurnal IPB: kehilangan hasil dan pengamatan panen](https://journal.ipb.ac.id/index.php/bulagron/article/download/14928/10992)
- [Contoh pencatatan dan pengamatan mutu di TPH (Instiper)](https://jurnal.instiperjogja.ac.id/index.php/JOM/article/download/2025/1227/15680)
- [HarvestTrack: pencatatan offline di PTPN IV](https://ojs.unimal.ac.id/sisfo/article/view/27375)
- [Pelacakan QR untuk TBS (vendor)](https://alcaidc.com/Public/Solution/PalmOilTracking.aspx)

---

## 4. Keputusan: foto atau klip video, dan menangkap semua tandan

**Keputusan [Usulan] dari saya: simpan keyframe, tidak menyimpan klip video penuh.**

Alasannya:
- Tanpa sinyal, klip video tidak bisa dikirim dan menghabiskan ruang di HP standar. Tiap sesi satu TPH bisa puluhan sampai ratusan MB.
- Yang dibutuhkan sebagai bukti adalah gambar tiap tandan terhitung, bukan videonya. Keyframe JPEG kecil dengan kotak cukup untuk audit dan memperbaiki selisih.
- Klip video bisa ditambah nanti sebagai opsi (hanya diunggah lewat Wi-Fi), bila pembimbing meminta.

**Soal "semua tumpukan yang banyak harus terdeteksi."** Itu bergantung pada cara scan dan koreksi, bukan pada penyimpanan klip:
- Tumpukan besar tidak boleh dianggap bisa dilihat utuh. Tandan yang tertutup tumpukan tidak terlihat kamera, jadi hasil AI adalah estimasi.
- Rancangan yang menjaga akurasi: sisir pelan satu arah, pecah tumpukan besar jadi beberapa sesi scan, koreksi manual wajib tersedia (sudah ada), dan selisih koreksi tercatat (`jml_ai` dibanding `jml_final`) supaya terlihat seberapa sering AI meleset.
- Kita tidak melatih ulang model, jadi keandalan di tumpukan nyata hanya bisa diketahui dari uji di TPH. Tolong jangan menjanjikan angka akurasi sebelum itu.

Keputusan akhir tetap di Ptoni.

---

## 5. Jadwal dipadatkan (3 minggu, sampai sekitar 1 Desember)

Tenggat KKP 15 Desember. Aplikasi diberi 3 minggu mulai 10 November. Sisa waktu setelahnya untuk uji, laporan, dan presentasi.

| Minggu | Tanggal | Target |
|---|---|---|
| 1 | 10 sampai 16 Nov | Uji scan video di HP. Database SQLite sesuai ERD. Simpan scan ke SQLite. Login offline. |
| 2 | 17 sampai 23 Nov | Scan QR TPH dan master contoh. Layar tinjau dengan persetujuan mandor. Antrean sinkron. Database `fai26` dan API minimal. |
| 3 | 24 sampai 30 Nov | Sinkron HP ke `fai26`. HVDM membaca `fai_panen` (dikerjakan Ptoni). Uji di TPH nyata. |
| Sisa | 1 sampai 15 Des | Penyetelan, laporan, presentasi. |

Dikorbankan lebih dulu bila waktu mepet: login online, layar admin, klip video, peringatan kondisi (cahaya dan baterai), geofence.

---

## 6. Butir terbuka

### Perlu keputusan segera

- [x] **B0. Selesai (10 Nov): Ptoni memilih pindah ke Android native Java. Project `fAI26-Mobile`, prototipe sudah diporting. Pustaka SQLite: Room.** Catatan lama di bawah ini hanya sebagai riwayat pertimbangan.
- ~~B0 lama:~~ Platform: Flutter atau pindah ke Android Studio dengan Java. Ptoni menyebut nanti ingin ganti ke Android native dengan Java. Itu mengubah beberapa hal:
  - `drift` hanya untuk Dart. Padanan di Android native adalah **Room** (SQLite, query bertipe, migrasi otomatis).
  - Plugin `ultralytics_yolo` hanya untuk Flutter. Di native, inferensi model harus dibangun sendiri di atas LiteRT atau TFLite, lalu tracker `bunch_tracker.dart` ditulis ulang di Java.
  - Dengan waktu 3 minggu, pindah platform berarti membangun ulang kamera, inferensi, dan tracker. **Rekomendasi saya: selesaikan prototipe dan SQLite di Flutter sampai tenggat KKP, lalu pindah bila memang diperlukan.** Alasan perpindahan sebaiknya jelas (misalnya kebijakan perusahaan atau kebutuhan layanan latar belakang).
  - Skema SQLite dari ERD bisa dipakai di kedua platform, jadi pekerjaan Fase 1 tidak terbuang.
- [ ] **B1. Buah digabung tetapi nama pemanen dicatat dan HVD1 satu baris per pemanen per tiket.** Bagaimana pembagiannya? Pilihan: pembagian manual oleh krani lewat `panen_pemanen.jjg_alokasi`, atau pembagian rata, atau hanya per TPH tanpa pemanen. Tanyakan ke pembimbing.

### Menunggu pembimbing

- [ ] B2. Konfirmasi arti "tidak ada API, mySAP365 mengambil langsung dari database": HVDM membaca `fai26`, dan HP tetap mengirim lewat API milik kita ke `fai26`. Apakah benar, atau HP boleh langsung menulis ke SQL Server? (Menulis langsung dari HP tidak saya sarankan: kredensial di HP dan tanpa sinyal tidak jalan.)
- [ ] B3. Jumlah hari rotasi.
- [ ] B4. Kelas abnormal: karena tidak melatih ulang, usulan sementara semua abnormal masuk `JJG_UNKNOWN`. Atau krani memilih jenis (busuk, sakit, lain) saat tinjau. Keputusan ada di pembimbing.
- [ ] B5. Lisensi: pembimbing mengizinkan. Bila pindah native dan tidak memakai plugin Ultralytics, cek ulang karena bobot model tetap dari Ultralytics.
- [ ] B6. `HASILHI1`: apakah jumlah ripe saja, ripe ditambah overripe, atau semua janjang termasuk mentah.
- [ ] B7. `harvest_employee_id` yang terisi hampir semua baris: apakah ini kunci pemanen yang harus diisi dari master pemanen.
- [ ] B8. Sumber master blok, TPH, pemanen (tabel `asm3` di `kp_stl2025` kosong).
- [ ] B9. Tangkai panjang dan nomor tiket atau SPB perlu dicatat atau tidak.
- [ ] B10. Siapa admin aplikasi.
- [ ] B11. Terima lisensi Android SDK (`flutter doctor --android-licenses`).

### Fitur GPS (verifikasi lokasi pencatat)

- [ ] B12. Radius yang dianggap "di TPH". Usulan awal 50 meter dengan akurasi minimal 30 meter, disetel dari uji lapangan.
- [ ] B13. Bila pencatat di luar radius: hanya ditandai untuk mandor (usulan saya), atau simpan diblokir? Saya sarankan hanya ditandai, karena GPS di bawah pohon bisa meleset dan pemblokiran bisa menghentikan kerja yang sah.
- [ ] B14. Bila GPS mati atau izin ditolak: boleh disimpan dengan status `TANPA_GPS` (usulan), atau wajib menyala?
- [ ] B15. Koordinat TPH: dari mana? Cara termudah, direkam saat QR dipasang di TPH. Perlu ditanyakan apakah ERP sudah punya koordinat TPH atau blok (ERD Bab 4 menandai ini [Cek]).
- [ ] B16. Siapa yang melihat catatan bertanda lokasi dan apa tindak lanjutnya (mandor saat menyetujui, asisten di rekap, atau keduanya).
- [x] B17 (diterapkan 10 Nov dengan persetujuan Ptoni lewat "Lanjut"; ERD v2.2). Tambahan kolom di ERD untuk verifikasi lokasi (`gps_akurasi_m`, `gps_waktu`, `gps_mock`, `jarak_ke_tph_m`, `status_lokasi`) perlu disetujui sebagai perubahan ERD, karena ERD adalah acuan yang berlaku.

### Sudah diputuskan

| Keputusan | Hasil | Tanggal |
|---|---|---|
| Cara input ke sistem | Penyimpanan offline dulu, kirim ke `fai26` lewat API kita, HVDM mengambil dari database | 4 dan 10 Nov |
| Data per scan | 6 kelas AI, jumlah janjang, nama pemanen, brondolan kg, tanggal dan jam, nomor TPH atau blok | 4 dan 10 Nov |
| Acuan basis data | ERD | 9 Nov |
| Foto atau video | Video dengan tracking (masukan CTO) | 9 Nov |
| Penyimpanan | SQLite sesuai ERD, JSON hanya prototipe | 9 Nov |
| TCode | HVDM | 10 Nov |
| Latih ulang model | Tidak, pakai dataset yang ada | 10 Nov |
| Persetujuan mandor | Perlu (status DISETUJUI) | 10 Nov |
| Login offline | 7 hari | 10 Nov |
| Pustaka SQLite | Ptoni pilih `drift`. Berlaku selama Flutter. Bila pindah native, ganti ke Room (lihat B0) | 10 Nov |
| Klip video atau keyframe | Keyframe saja (usulan saya, menunggu persetujuan Ptoni) | 10 Nov |
| Fitur GPS | Masuk aplikasi untuk memantau apakah pencatat benar-benar di lokasi TPH. Parameter (B12 sampai B17) menyusul | 10 Nov |

---

## Tambahan 22 Nov: butir menunggu keputusan Ptoni

Hasil Review Sprint 2 dan persiapan Sprint 3. Butir yang mengubah pengaturan komputer atau server hanya dikerjakan setelah disetujui.

| # | Butir | Keputusan yang dibutuhkan |
|---|---|---|
| 22-1 | Perbaiki token saat login offline sebagai pengguna lain (O-01) | Setuju diperbaiki sekarang? (mengubah alur login HP) |
| 22-2 | HP tanpa kabel: jaringan lokal dan aturan firewall untuk port 8080, atau reverse proxy HTTPS | Setuju mengubah firewall? Alamat IP laptop atau server mana yang dipakai? |
| 22-3 | Server sungguhan: alamat, akses, dan nama domain untuk HTTPS | Siapa yang memberi akses, kapan |
| 22-4 | Jadwal uji lapangan (scan, GPS jalan kaki, IPD) | Hari, blok atau TPH, dan HP yang dipakai |
| 22-5 | Konfirmasi pada tombol Setujui catatan panen | Mau ada dialog konfirmasi? |
| 22-6 | Notifikasi push persetujuan (butuh akun Firebase) | Mau dibuat atau cukup penanda di aplikasi? |
| 22-7 | Peta monitoring gabungan dan histori per pengguna | Mau dibuat? Prioritasnya |
| 22-8 | Kebijakan data lokasi: berapa lama jalur disimpan dan siapa boleh melihat | Keputusan Ptoni dan pembimbing |
| 22-9 | TCode ERP untuk membaca data IPD | Dari pihak ERP; belum ada |
| 22-10 | Rekam koordinat TPH: mulai dari blok mana | Blok prioritas |
