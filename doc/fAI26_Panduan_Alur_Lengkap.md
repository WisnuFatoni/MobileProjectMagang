# fAI26: Panduan Alur dari Nol

Aplikasi pencatatan panen lapangan dan grading kematangan TBS, dari TPH sampai masuk ke mySAP365.

Disusun untuk: Wisnu Fatoni (KKP Kebun Aas, Banyuasin)
Tanggal: 4 November 2026
Revisi: TCode tujuan di ERP bukan TCDA (Bab 9.3), dan TPH dipilih lewat scan QR (Bab 5.3).

---

## 0. Cara membaca panduan ini

Panduan ini ditulis untuk orang yang belum pernah ke TPH dan belum paham alur data panen. Baca urut dari atas. Setiap bagian menjawab satu pertanyaan.

Setiap informasi punya label supaya kamu tahu seberapa kuat dasarnya:

| Label | Artinya |
|---|---|
| **[Kerjamu]** | Fakta dari pekerjaan dan dokumen harianmu di mySAP365 (Hari 70 sampai 89). |
| **[Umum]** | Cara kerja yang lazim di kebun sawit. Kebun Aas bisa berbeda, jadi perlu dicocokkan. |
| **[Usulan]** | Rancangan dariku. Kamu dan pembimbing yang memutuskan. |
| **[Cek]** | Hal yang belum diketahui dan harus ditanyakan ke orang yang tepat. |

Daftar isi:

1. Gambaran dalam satu menit
2. Kamus istilah
3. Alur panen di lapangan (cara lama)
4. Masalah di cara lama
5. Alur dengan aplikasi fAI26
6. Soal buah yang tercampur di TPH
7. Data yang dicatat
8. Periode: harian, rotasi, gaji, tahun
9. Dari HP ke mySAP365
10. Data itu dipakai untuk apa
11. Peran dan hak akses
12. Jadwal bangun bulan November
13. Rancangan teknis (tabel dan API)
14. Pertanyaan yang harus dijawab
15. Risiko
16. Cara menjelaskan ke dosen atau pembimbing

---

## 1. Gambaran dalam satu menit

**Masalah.** Di TPH, krani menghitung tandan dan menilai mutunya dengan mata, lalu menulis di kertas. Malamnya data itu diketik ulang ke ERP. Prosesnya lambat, bisa salah hitung, dan penilaian mutunya berbeda antarorang.

**Solusi.** Krani memotret tumpukan tandan memakai HP. AI di HP mengenali tiap tandan dan kematangannya. Hasilnya tersimpan sebagai catatan panen, lalu terkirim ke ERP saat ada sinyal.

**Manfaat.** Data masuk lebih cepat, penilaian mutu seragam, ada foto sebagai bukti, dan laporan produksi (seperti TCRA dan dashboard yang sudah kamu buat) tersedia lebih awal.

Satu kalimat untuk presentasi:

> fAI26 menggantikan pencatatan kertas di TPH dengan foto dan AI, supaya data panen dan mutu buah langsung masuk ke ERP.

---

## 2. Kamus istilah

**[Umum]** kecuali disebut lain.

| Istilah | Arti sederhana |
|---|---|
| **TBS** | Tandan Buah Segar. Hasil panen sawit. |
| **Janjang** | Satu tandan buah. Satuan hitung panen. |
| **Brondolan** | Buah sawit yang lepas dari tandan. Ikut dikumpulkan dan dicatat. |
| **Blok** | Petak kebun. Setiap blok punya kode, misalnya A12. |
| **Afdeling atau divisi** | Kelompok beberapa blok di bawah satu asisten. Kebunmu menyebutnya Divisi 2. |
| **TPH** | Tempat Pengumpulan Hasil. Titik di pinggir jalan blok tempat pemanen menaruh hasil panen. |
| **Pemanen** | Pekerja yang memotong tandan. |
| **Krani buah** | Petugas pencatat. Menghitung janjang dan menilai mutu di TPH. |
| **Mandor panen** | Pengawas kelompok pemanen. |
| **Asisten** | Pimpinan satu divisi. Membaca laporan produksi. |
| **Pengangkut atau transport** | Pekerja dan truk yang membawa buah dari TPH ke pabrik. |
| **PKS** | Pabrik Kelapa Sawit. Tempat TBS diolah. |
| **Timbangan** | Alat menimbang truk di pabrik untuk mengetahui berat TBS. |
| **Sortasi atau grading** | Penilaian kematangan dan mutu buah. |
| **Rotasi panen** | Satu putaran panen di sebuah blok. Blok dipanen berulang dengan jeda tetap, umumnya sekitar seminggu. |
| **Basis, premi, denda** | Aturan upah. Pemanen punya target harian (basis). Kelebihan target diberi premi, dan buah yang mutunya buruk bisa dikenai denda. Angkanya berbeda tiap perusahaan. |
| **Tiket panen** | Dokumen pencatat hasil panen. **[Kerjamu]** TCMA mengenal tiga jenis: TK-001 Krani (Putih), TK-002 Pengangkut (Pink), TK-003 Pemanen (Kuning). |
| **Cluster Unit-Tahun** | **[Kerjamu]** Cara mySAP365 memisahkan data per unit dan per tahun, misalnya KP_STL untuk unit dan 2025 untuk tahun. |

---

## 3. Alur panen di lapangan (cara lama)

**[Umum]** Gambaran satu hari panen. Cocokkan dengan Kebun Aas.

```
PAGI       Apel dan pembagian kerja (mandor membagi hanca atau area kerja)
   |
   v
PEMANEN    Memotong tandan matang, memotong pelepah, mengumpulkan brondolan
   |
   v
TPH        Pemanen membawa hasil ke TPH dan menaruhnya per tumpukan
   |
   v
KRANI      Datang ke TPH: hitung janjang, nilai mutu, catat di tiket atau buku
   |
   v
PENGANGKUT Muat buah dari TPH ke truk, bawa ke pabrik
   |
   v
PKS        Timbang truk, buah masuk sortasi pabrik, lalu diolah
   |
   v
KANTOR     Data hasil panen diketik ke ERP (TCode transaksi panen), laporan dibuat
```

Langkah rinci di TPH:

1. Pemanen menaruh tandan di TPH. Biasanya buah tiap pemanen ditata terpisah, kadang diberi tanda.
2. Krani menghitung janjang tiap pemanen dan mengumpulkan angka brondolan.
3. Krani menilai mutu: ada tandan mentah (dilarang dipanen), terlalu matang, atau tandan kosong.
4. Krani menulis hasilnya: tanggal, blok, nama pemanen, jumlah janjang, brondolan, dan catatan mutu.
5. Pengangkut memuat buah ke truk dan membawa tiket yang menyertainya.

**[Kerjamu]** Ketiga jenis tiket di TCMA (Krani, Pengangkut, Pemanen) mengikuti alur ini: ada tiket yang dipegang krani, ada yang menyertai pengangkutan, dan ada yang terkait pemanen.

**[Cek]** Siapa yang menulis tiket di Kebun Aas, apa saja isinya, dan berapa lembar. Tanyakan ke krani.

---

## 4. Masalah di cara lama

| Masalah | Dampak |
|---|---|
| Hitung janjang manual | Salah hitung, terutama di tumpukan besar. |
| Penilaian mutu pakai mata | Krani A dan krani B bisa menilai berbeda untuk buah yang sama. |
| Data di kertas | Bisa hilang, basah, atau tidak terbaca. |
| Ketik ulang ke ERP | Lambat, dan bisa salah ketik. |
| Tidak ada foto | Sulit membuktikan bila ada selisih atau komplain dari pemanen. |
| Data terlambat | Laporan produksi baru terlihat besok, bukan hari ini. |

fAI26 menjawab keenam masalah ini.

---

## 5. Alur dengan aplikasi fAI26

### 5.1 Alur utama

```
KRANI DI TPH
   |  1. Buka aplikasi, login
   |  2. Scan QR di TPH (blok dan TPH terisi otomatis), pilih pemanen bila perlu
   |  3. Foto tumpukan tandan dengan kamera aplikasi
   v
AI DI HP
   |  4. Menandai tiap tandan, memberi kelas kematangan
   |  5. Menghitung janjang per kelas
   v
KRANI
   |  6. Periksa hasil, koreksi bila AI keliru
   |  7. Isi brondolan, tekan Simpan
   v
PENYIMPANAN DI HP (tetap aman tanpa sinyal)
   |  8. Catatan + foto tersimpan, status: TERTUNDA
   v
SINKRONISASI (otomatis saat ada sinyal)
   |  9. Kirim ke API, status: TERKIRIM
   v
SERVER DAN ERP
      10. Data masuk tabel perantara, lalu terbaca di mySAP365 (Bab 9)
```

### 5.2 Gambaran layar (kasar)

Layar 1. Pilih tempat kerja

```
+----------------------------------+
| fAI26                  [Sinkron] |
|----------------------------------|
| Tanggal panen : 05-11-2026       |
| Unit          : KP_STL           |
|        [ SCAN QR TPH ]            |
| (cadangan: pilih manual)         |
| Blok / TPH    : A12 / TPH-07     |
| Pemanen       : [ (opsional) v ] |
|                                  |
|        [ MULAI FOTO ]            |
|                                  |
| Catatan hari ini: 6  Tertunda: 2 |
+----------------------------------+
```

Layar 2. Kamera dan hasil

```
+----------------------------------+
|  [ foto tumpukan tandan ]        |
|   +-----+  +-----+  +-----+      |
|   |ripe |  |ripe |  |unripe|     |
|   +-----+  +-----+  +-----+      |
|----------------------------------|
| Matang (ripe)         : 88       |
| Mentah (unripe)       : 2        |
| Kurang matang         : 5        |
| Lewat matang          : 3        |
| Tandan kosong         : 1        |
| Abnormal              : 1        |
|   [ Koreksi ]     [ Foto lagi ]  |
+----------------------------------+
```

Layar 3. Konfirmasi dan simpan

```
+----------------------------------+
| Ringkasan                        |
| A12 / TPH-07 / 05-11-2026        |
| Total janjang : 100              |
| Brondolan     : [ 12 ]           |
| Catatan       : [ ........... ]  |
|                                  |
|   [ SIMPAN ]      [ BATAL ]      |
+----------------------------------+
```

Layar 4. Daftar dan status

```
+----------------------------------+
| Hari ini                         |
| 09:14  A12 TPH-07  100  [Terkirim]
| 09:40  A12 TPH-08   84  [Tertunda]
| 10:05  A13 TPH-02   92  [Tertunda]
+----------------------------------+
```

### 5.3 Scan QR di TPH

Bisa. Krani tidak perlu memilih atau mengetik TPH. Alurnya:

```
Krani tiba di TPH  ->  buka aplikasi  ->  tekan SCAN QR TPH
        ->  arahkan kamera ke QR di TPH
        ->  layar info TPH muncul
        ->  tekan MULAI FOTO
```

Contoh layar setelah scan (angka karangan):

```
+----------------------------------+
| TPH-07                           |
| Unit KP_STL  |  Divisi 2         |
| Blok          : A12              |
| Tahun tanam   : 2012             |
| Luas blok     : 28,5 ha          |
| Jumlah pokok  : 3.800            |
| Panen terakhir: 29-10-2026       |
|                                  |
|        [ MULAI FOTO ]            |
+----------------------------------+
```

**Apa isi QR-nya?** Prinsipnya: **QR hanya berisi kode TPH, bukan seluruh datanya.** QR sebenarnya hanya teks. Aplikasi membaca teks itu, lalu mencari kode tersebut di daftar master yang tersimpan di HP, dan dari situ muncul blok, tahun tanam, dan data lainnya.

| Pilihan isi QR | Contoh | Penilaian |
|---|---|---|
| **Kode pendek (disarankan)** | `FAI26\|TPH\|1\|KP_STL\|A12\|07` | QR kecil dan mudah dipindai di lapangan. Data bisa berubah tanpa mencetak ulang QR. Tetap jalan tanpa sinyal karena master ada di HP. |
| Semua data di dalam QR (JSON) | `{"blok":"A12","tt":2012,...}` | Tidak disarankan. Tiap data berubah, QR harus dicetak ulang. QR jadi padat dan sulit dipindai saat kotor atau miring. Data terbuka bagi siapa pun yang memindai. |
| Alamat web (URL) | `https://.../tph/07` | Butuh internet saat scan, padahal sinyal di kebun lemah. |

Arti isi kode pendek `FAI26|TPH|1|KP_STL|A12|07`:

| Bagian | Isi | Fungsi |
|---|---|---|
| FAI26 | Penanda aplikasi | Aplikasi menolak QR lain yang tidak berkaitan. |
| TPH | Jenis QR | Membedakan dengan QR jenis lain, misalnya kartu pemanen nanti. |
| 1 | Versi format | Bila format berubah, QR lama tetap bisa dikenali. |
| KP_STL | Unit | Kunci pencarian di master. |
| A12 | Blok | Kunci pencarian di master. |
| 07 | Nomor TPH | Kunci pencarian di master. |

**Data yang muncul setelah scan** berasal dari daftar master. Kandidatnya:

| Data | Ada di ERP? |
|---|---|
| Unit, divisi, blok, nomor TPH | **[Cek]** |
| Tahun tanam | **[Cek]** Biasanya ada di master blok atau tanaman. Cari tabel dan TCode-nya. |
| Luas dan jumlah pokok | **[Cek]** |
| Koordinat TPH | **[Cek]** Kalau belum ada, bisa dicatat saat memasang QR. |
| Mandor atau kemandoran | **[Cek]** |
| Panen terakhir | Dari catatan aplikasi sendiri, tidak perlu dari ERP. |

**Cara membuat dan memasang:**

1. Siapkan daftar TPH (unit, blok, nomor TPH) dalam satu berkas.
2. Buat skrip kecil (Python atau Golang) yang membaca daftar itu dan menghasilkan gambar QR beserta lembar cetak.
3. Cetak di bahan tahan cuaca: stiker laminasi tahan sinar matahari atau plat. Kertas biasa cepat rusak oleh hujan dan lumpur.
4. Ukuran cukup besar (kira-kira seukuran telapak tangan atau lebih) dan beri tingkat koreksi kesalahan tinggi (level H), supaya tetap terbaca meski agak kotor.
5. Tulis juga kode teks di bawah QR, misalnya "A12 / TPH-07", supaya bisa dibaca manusia.
6. Pasang di tiang atau papan setinggi dada di TPH.

**Cadangan bila QR rusak atau hilang:** aplikasi tetap menyediakan pilihan blok dan TPH manual dari daftar.

**Anti curang (opsional):** aplikasi membandingkan lokasi GPS HP dengan koordinat TPH dan memberi peringatan bila jaraknya jauh. QR bisa difoto orang lain, jadi QR tidak boleh berisi data rahasia.

**Pengembangan lanjut:** pasang QR juga di kartu pemanen. Krani scan QR TPH, lalu scan QR kartu pemanen, dan nama pemanen terisi otomatis tanpa memilih dari daftar. Ini menjawab skenario A dan C di Bab 6.

### 5.4 Peran AI di sini

- AI **menemukan tandan dan menentukan kelasnya**. Hasilnya berupa kotak dan label pada foto.
- AI **tidak tahu siapa pemanennya**. Nama pemanen berasal dari pilihan krani atau dari tiket (Bab 6).
- AI bisa keliru. Karena itu krani selalu bisa mengoreksi, dan koreksi itu disimpan. Koreksi berguna untuk melatih ulang model.
- Brondolan tidak dihitung AI. Krani mengisinya manual.

---

## 6. Soal buah yang tercampur di TPH

Pertanyaanmu: kalau buah dicampur di TPH, apakah nama pemanen harus dimasukkan satu per satu?

Jawabannya bergantung pada cara kebun menata buah. Ada tiga kemungkinan.

| Skenario | Kondisi di TPH | Cara pencatatan di aplikasi |
|---|---|---|
| **A. Dipisah per pemanen** | Tiap pemanen punya tumpukan sendiri, sering diberi tanda atau tiket. | Foto per tumpukan, pilih nama pemanen dari daftar. Tidak perlu mengetik. |
| **B. Tercampur** | Semua buah menumpuk jadi satu. | Catat per TPH atau per blok. Pembagian ke pemanen mengikuti aturan kebun (misalnya rata per kelompok). |
| **C. Campuran** | Sebagian terpisah, sebagian tercampur. | Catat per TPH dulu, nama pemanen diisi hanya bila bisa dibedakan. |

**[Usulan]** Buat pencatatan yang fleksibel:

- Wajib: tanggal, blok atau TPH, dan hasil foto.
- Opsional: nama pemanen, dipilih dari daftar (bukan diketik).
- Dengan begitu aplikasi jalan di kebun mana pun, apa pun SOP-nya.

Catatan teknis: menghitung tandan dari tumpukan besar kurang akurat karena tandan saling menutupi. Foto per tumpukan kecil lebih akurat. Bila tumpukan besar, bagi jadi beberapa foto dan jumlahkan.

**[Cek]** Tanyakan ke krani atau mandor:

1. Buah tiap pemanen dipisah atau dicampur?
2. Upah pemanen dihitung per orang atau per kelompok?
3. Bagaimana pemanen tahu hasilnya sudah dicatat benar?

---

## 7. Data yang dicatat

### 7.1 Isi satu catatan panen

**[Usulan]** Satu catatan mewakili satu foto atau satu tumpukan di satu TPH.

| Kolom | Contoh | Keterangan |
|---|---|---|
| id | 7f3c9a1e-... | Kode unik dibuat di HP. Mencegah data ganda (Bab 9.4). |
| tanggal_panen | 2026-11-05 | Tanggal buah dipanen, **bukan** tanggal input. |
| unit | KP_STL | Dari pilihan unit. |
| blok | A12 | Dari daftar master. |
| tph | TPH-07 | Dari daftar master. |
| pemanen_id | P-014 atau kosong | Opsional (Bab 6). |
| krani_id | K-003 | Pengguna yang login. |
| jml_unripe | 2 | Mentah. |
| jml_underripe | 5 | Kurang matang. |
| jml_ripe | 88 | Matang. |
| jml_overripe | 3 | Lewat matang. |
| jml_empty | 1 | Tandan kosong. |
| jml_abnormal | 1 | Abnormal. |
| brondolan | 12 | Diisi krani. Satuan mengikuti TCode panen tujuan. **[Cek]** |
| foto | 2 berkas | Foto asli dan foto berkotak. |
| gps | -2.95, 104.7 | Lokasi saat memotret. Opsional. |
| dibuat_di_hp | 2026-11-05 09:14 | Waktu catatan dibuat. |
| status | TERTUNDA | Lihat siklus status di Bab 8.5. |

Enam kelas di atas mengikuti dataset publik yang kamu pakai. **[Cek]** Kebun mungkin hanya mengenal tiga kelas (mentah, matang, lewat matang). Samakan dengan kriteria kebun sebelum dipakai.

### 7.2 Contoh isi satu catatan (JSON)

Angka di bawah ini **karangan** untuk ilustrasi.

```json
{
  "id": "7f3c9a1e-5b2d-4e8a-9c11-2f6d0a7b3e45",
  "tanggal_panen": "2026-11-05",
  "unit": "KP_STL",
  "blok": "A12",
  "tph": "TPH-07",
  "pemanen_id": "P-014",
  "krani_id": "K-003",
  "janjang": {
    "unripe": 2,
    "underripe": 5,
    "ripe": 88,
    "overripe": 3,
    "empty_bunch": 1,
    "abnormal": 1
  },
  "brondolan": 12,
  "foto": ["foto_asli_001.jpg", "foto_deteksi_001.jpg"],
  "gps": { "lat": -2.95, "lon": 104.7 },
  "dibuat_di_hp": "2026-11-05T09:14:00+07:00",
  "status": "TERTUNDA"
}
```

### 7.3 Aturan pemeriksaan data

Aplikasi menolak atau memberi peringatan bila:

- tanggal panen lebih dari hari ini,
- blok atau TPH tidak ada di daftar master,
- total janjang nol tetapi ada foto (kemungkinan model gagal mendeteksi),
- tingkat keyakinan AI rendah pada banyak tandan (sarankan foto ulang),
- tanggal panen jatuh pada periode yang sudah dikunci (Bab 8.5).

---

## 8. Periode: harian, rotasi, gaji, tahun

Kata "periode" punya beberapa arti. Semuanya memengaruhi aplikasi.

### 8.1 Periode harian

- Satu hari panen punya satu **tanggal panen**.
- Kunci semua data adalah tanggal panen. Bukan tanggal data diketik atau dikirim.
- Contoh: krani memotret hari Senin tetapi baru dapat sinyal hari Selasa. Catatan tetap bertanggal Senin. Karena itu aplikasi menyimpan `tanggal_panen` dan `dibuat_di_hp` sebagai dua kolom berbeda.

### 8.2 Periode rotasi panen

**[Umum]**

- Satu blok dipanen berulang. Jedanya umumnya sekitar 7 hari, tetapi Kebun Aas bisa berbeda.
- Rekap yang berguna: janjang per blok per rotasi, dan tanggal panen terakhir tiap blok.
- **[Usulan]** Fitur rekap per blok menampilkan "panen terakhir" supaya mandor tahu blok mana yang sudah waktunya dipanen.
- **[Cek]** Berapa hari rotasi di Divisi 2.

### 8.3 Periode gaji dan premi

**[Umum]**

- Upah dan premi pemanen biasanya dihitung per bulan, dengan tanggal tutup (cut-off) yang tetap, misalnya tanggal 20 atau akhir bulan.
- Setelah tanggal tutup, data periode itu dikunci supaya angka gaji tidak berubah.
- Koreksi sesudah penguncian harus lewat prosedur khusus, bukan lewat edit biasa.

**[Cek]** Tanggal tutup di Kebun Aas, dan siapa yang berhak membuka kunci.

### 8.4 Periode pembukuan ERP

**[Kerjamu]**

- mySAP365 memisahkan data per **cluster Unit-Tahun**. Contoh yang pernah kamu lihat: KP_STL dan tahun 2025 pada TCDA.
- Pada laporan Hari 88, cluster 2026 dan 2027 belum punya tabel di sandbox.
- Akibatnya, data yang dikirim aplikasi harus mengikuti tanggal panen untuk menentukan tahun clusternya. Panen tanggal 31 Desember dan 1 Januari jatuh ke tahun berbeda.

**[Cek]** Apakah tabel cluster tahun berjalan sudah tersedia, dan siapa yang membuatnya.

### 8.5 Siklus status satu catatan

**[Usulan]**

```
DRAFT  ->  FINAL  ->  DIPERIKSA  ->  TERKIRIM  ->  MASUK ERP  ->  TERKUNCI
(krani     (krani      (mandor        (sudah di      (dibaca       (periode
mengisi)   menekan     mengecek,      server)        ERP)          ditutup)
           Simpan)     boleh koreksi)
```

| Status | Siapa yang boleh mengubah |
|---|---|
| DRAFT | Krani. |
| FINAL | Krani, sampai akhir hari yang sama. |
| DIPERIKSA | Mandor. Perubahan tercatat. |
| TERKIRIM dan MASUK ERP | Tidak bisa diubah dari aplikasi. Koreksi dibuat sebagai catatan koreksi baru. |
| TERKUNCI | Tidak ada. Mengikuti penutupan periode gaji. |

### 8.6 Ringkasan semua periode

| Periode | Kegunaan | Pengaruh ke aplikasi |
|---|---|---|
| Harian | Tanggal panen | Kunci data. |
| Rotasi (sekitar 7 hari) | Jadwal dan rekap per blok | Fitur "panen terakhir". |
| Bulanan (gaji) | Hitung upah, premi, denda | Penguncian data. |
| Tahunan (cluster ERP) | Penyimpanan di ERP | Tahun cluster dari tanggal panen. |

---

## 9. Dari HP ke mySAP365

### 9.1 Mengapa tidak langsung ke database ERP

- Kredensial database akan tersimpan di HP. Satu HP hilang bisa membuka seluruh data.
- ERP punya aturan dan constraint sendiri. **[Kerjamu]** Pada Hari 83, simpan dokumen baru di TCDA gagal dengan `ConstraintViolationException` pada insert ke `dbo.tcdam`. Menulis langsung ke tabel itu berisiko merusak data atau tidak terbaca ERP.
- Jaringan kebun sering tidak stabil. Aplikasi harus tetap bekerja tanpa sinyal.

### 9.2 Arsitektur tiga lapis

```
+-----------------+      +---------------------+      +------------------+
| HP (fAI26)      | ---> | API perantara       | ---> | Tabel perantara  |
| kamera + catat  |      | (Golang)            |      | (staging)        |
| simpan lokal    |      | login, validasi     |      |                  |
+-----------------+      +---------------------+      +--------+---------+
                                                               |
                                                               v
                                                    +----------------------+
                                                    | mySAP365             |
                                                    | TCode baru membaca   |
                                                    | tabel perantara      |
                                                    +----------------------+
```

### 9.3 Tiga cara sampai ke ERP

| Cara | Isi | Risiko | Saran |
|---|---|---|---|
| **1. Lihat di ERP** | Buat TCode atau laporan baru (mirip TCRA) yang membaca tabel perantara. Data HP tampil di mySAP365 tanpa menyentuh TCDA. | Rendah. | **Lakukan dulu.** Cukup untuk demo November. |
| **2. Import ke TCode transaksi panen resmi** | Dari TCode tadi, tombol "Import" membuat dokumen panen lewat logika simpan ERP. TCode tujuannya **bukan TCDA** (TCDA adalah transaksi tiket). TCode panen yang tepat harus dicari dulu. **[Cek]** | Sedang. Butuh izin, dan perlu memahami tabel serta aturan TCode tujuan. | Hanya bila pembimbing setuju. |
| **3. Tulis langsung ke tabel transaksi ERP** | API menulis langsung ke tabel ERP. | Tinggi. Contoh bahaya: simpan dokumen TCDA pernah gagal pada `dbo.tcdam` karena constraint. | **Jangan.** |

**Mencari TCode tujuan.** Dari catatan kerjamu, keluarga TCode berawalan HV (misalnya HVD dan HVD2) kemungkinan terkait transaksi panen, dan assembly SU0W juga berada di namespace HV. Itu baru dugaan. Cara memastikan: buka SAP1, saring kolom TCode Module dengan HV, lalu lihat nama tiap TCode dan fungsinya. Tanyakan juga ke pembimbing, "Transaksi mana yang mencatat hasil panen per pemanen?" Selama TCode tujuan belum jelas, tetap pakai **cara 1** dengan TCode baru milikmu sendiri.

Bila PT LAT punya API resmi mySAP365, itu menggantikan seluruh lapis tengah dan menjadi pilihan terbaik. **[Cek]**

### 9.4 Aturan sinkronisasi (saat sinyal timbul tenggelam)

1. Setiap catatan punya `id` unik yang dibuat di HP. Server menolak `id` yang sudah pernah masuk, jadi pengiriman ulang tidak membuat data ganda.
2. Aplikasi mengirim catatan berstatus TERTUNDA satu per satu, dari yang paling lama.
3. Bila gagal, status tetap TERTUNDA dan aplikasi mencoba lagi nanti. Kegagalan terus-menerus ditandai GAGAL dan diberi tombol "Kirim ulang".
4. Foto dikirim terpisah dari data angka. Data angka dikirim lebih dulu karena kecil, foto menyusul.
5. Foto dikompres sebelum dikirim supaya hemat kuota.
6. Sinkronisasi hanya berjalan bila pengguna sudah login. Token login disimpan aman di HP.

### 9.5 Daftar master (blok, TPH, pemanen)

Aplikasi perlu daftar blok, TPH, dan pemanen supaya pengguna tinggal memilih.

- **[Usulan]** Untuk November, daftar ini berupa berkas contoh yang kamu isi sendiri.
- Tahap lanjut: API menyediakan daftar master yang diambil dari ERP (misalnya TCMA dan master HC), lalu HP menyimpannya.
- **[Cek]** Dari tabel ERP mana daftar blok dan pemanen diambil, dan apakah boleh dibaca.

---

## 10. Data itu dipakai untuk apa

Setelah data ada di ERP, ia dipakai untuk lima hal. **[Umum]** kecuali disebut lain.

| Pemakaian | Siapa | Caranya |
|---|---|---|
| **Laporan produksi harian** | Asisten, manajer | Jumlah janjang per blok dan per hari. **[Kerjamu]** Sama seperti TCRA dan dashboard yang sudah ada. |
| **Hitung upah dan premi** | Bagian gaji | Janjang per pemanen dibanding basis. Janjang matang dihargai penuh, mentah bisa dikenai denda sesuai aturan kebun. |
| **Pengawasan mutu panen** | Mandor, asisten | Persentase mentah, matang, dan lewat matang per pemanen atau per blok. |
| **Pembanding dengan timbangan pabrik** | Asisten, pabrik | Total janjang TPH dibanding berat yang tiba di pabrik, untuk melihat selisih. |
| **Bukti dan penelusuran** | Semua | Foto asli dan hasil deteksi untuk menyelesaikan selisih atau komplain. |

### Contoh rekap (angka karangan)

Satu hari, blok A12, tiga catatan:

| Catatan | Janjang matang | Mentah | Lewat matang | Total |
|---|---|---|---|---|
| TPH-07 | 88 | 2 | 3 | 94 |
| TPH-08 | 70 | 6 | 4 | 80 |
| TPH-09 | 95 | 1 | 2 | 98 |
| **Jumlah blok A12** | **253** | **9** | **9** | **272** |

Persentase mentah blok A12 hari itu: 9 dibagi 272, sekitar 3,3 persen.

Tabel ini sama bentuknya dengan kartu KPI dan grid pada dashboard TCRA yang kamu buat. Bedanya, kolom mutu berasal dari AI.

---

## 11. Peran dan hak akses

**[Usulan]**

| Peran | Bisa melakukan |
|---|---|
| **Krani** | Login, memilih blok dan TPH, memotret, menyimpan, melihat catatan sendiri. |
| **Mandor** | Semua milik krani, ditambah memeriksa dan mengoreksi catatan krani di kelompoknya. |
| **Asisten** | Melihat rekap dan dashboard (baca saja). |
| **Admin sistem** | Mengelola daftar master dan pengguna, melihat status sinkronisasi, membuka kunci periode. |

Aturan dasar: aplikasi memakai login sendiri. Tidak ada kredensial database di HP.

---

## 12. Jadwal bangun bulan November

**[Usulan]** Sesuaikan dengan sisa masa KKP dan arahan pembimbing. **[Cek]** Tanggal selesai KKP.

### Posisi sekarang

| Pekerjaan | Status |
|---|---|
| Model deteksi dan kamera AI | Sudah jalan (menurutmu). **[Cek]** Jalan di HP, atau masih di komputer atau Colab? |
| Hasil pelatihan akhir 50 epoch | Perlu dilaporkan: mAP50, precision, recall. |
| Pencatatan, penyimpanan lokal, sinkronisasi | Belum. |
| Sisi ERP | Belum. |

### Rencana per minggu

| Minggu | Tanggal | Pekerjaan | Hasil yang bisa ditunjukkan |
|---|---|---|---|
| **1** | 2 sampai 8 Nov | Pastikan model berjalan di HP (TFLite). Catat hasil pelatihan akhir. Tanyakan pertanyaan Bab 14 ke krani dan pembimbing. | Video singkat kamera mendeteksi tandan di HP. Jawaban awal SOP TPH. |
| **2** | 9 sampai 15 Nov | Bangun formulir pencatatan, hasil grading masuk ke catatan, simpan lokal. | Aplikasi yang menyimpan catatan panen lengkap di HP tanpa sinyal. |
| **3** | 16 sampai 22 Nov | Bangun API perantara dan tabel staging. Sinkronisasi dari HP. Mulai TCode atau laporan di ERP yang membaca staging. | Data dari HP muncul di server, lalu tampil di mySAP365. |
| **4** | 23 sampai 30 Nov | Uji di TPH nyata bersama krani. Ambil foto kebun untuk memperbaiki model. Susun laporan dan presentasi. | Catatan uji lapangan, daftar perbaikan, laporan November. |

### Definisi selesai untuk prototipe

Prototipe dianggap berhasil bila satu krani bisa:

1. membuka aplikasi dan memilih blok serta TPH,
2. memotret tumpukan dan mendapat hasil grading,
3. menyimpan catatan tanpa sinyal,
4. melihat catatan itu muncul di mySAP365 setelah sinyal tersedia.

Urutan kerja bila waktu mepet: kerjakan poin 1 sampai 3 dulu. Poin 4 menyusul.

---

## 13. Rancangan teknis (tabel dan API)

### 13.1 Tabel perantara (staging)

**[Usulan]** Contoh dalam SQL Server, karena ERP memakai SQL Server. **[Cek]** Apakah boleh dibuat database atau tabel baru di server sandbox.

```sql
CREATE TABLE fai_panen (
    id              UNIQUEIDENTIFIER PRIMARY KEY,
    tanggal_panen   DATE         NOT NULL,
    unit            VARCHAR(20)  NOT NULL,
    blok            VARCHAR(20)  NOT NULL,
    tph             VARCHAR(20)  NULL,
    pemanen_id      VARCHAR(20)  NULL,
    krani_id        VARCHAR(20)  NOT NULL,
    jml_unripe      INT NOT NULL DEFAULT 0,
    jml_underripe   INT NOT NULL DEFAULT 0,
    jml_ripe        INT NOT NULL DEFAULT 0,
    jml_overripe    INT NOT NULL DEFAULT 0,
    jml_empty       INT NOT NULL DEFAULT 0,
    jml_abnormal    INT NOT NULL DEFAULT 0,
    brondolan       INT NOT NULL DEFAULT 0,
    gps_lat         DECIMAL(9,6) NULL,
    gps_lon         DECIMAL(9,6) NULL,
    dibuat_di_hp    DATETIME2    NOT NULL,
    diterima_server DATETIME2    NOT NULL DEFAULT SYSDATETIME(),
    status_erp      VARCHAR(12)  NOT NULL DEFAULT 'BARU'  -- BARU, DIBACA, DIIMPOR
);

CREATE TABLE fai_foto (
    id        UNIQUEIDENTIFIER PRIMARY KEY,
    panen_id  UNIQUEIDENTIFIER NOT NULL REFERENCES fai_panen(id),
    jenis     VARCHAR(10) NOT NULL,   -- ASLI atau DETEKSI
    nama_file VARCHAR(200) NOT NULL
);
```

Tabel ini milikmu sendiri. Ia tidak menyentuh tabel ERP.

### 13.2 Endpoint API

**[Usulan]**

| Method | Alamat | Fungsi |
|---|---|---|
| POST | /api/login | Login, mengembalikan token. |
| GET | /api/master/blok | Daftar blok dan TPH. |
| GET | /api/master/pemanen | Daftar pemanen. |
| POST | /api/panen | Kirim satu catatan panen. Menolak `id` yang sudah ada. |
| POST | /api/panen/{id}/foto | Kirim foto catatan. |
| GET | /api/panen?tanggal=... | Daftar catatan, untuk rekap dan pengecekan. |

### 13.3 Susunan aplikasi di HP

**[Usulan]**

- Flutter dengan plugin `ultralytics_yolo` untuk menjalankan model TFLite.
- Penyimpanan lokal: SQLite, berisi catatan, foto, dan daftar master.
- Lapisan sinkronisasi: antrean catatan TERTUNDA, mengirim saat sinyal tersedia.
- Layar: login, pilih tempat, kamera, ringkasan, daftar catatan.

### 13.4 Sisi ERP

**[Usulan]** Cara 1 di Bab 9.3:

- TCode baru, misalnya laporan "Tiket Panen Mobile", dibangun mirip TCRA.
- Sumber data: tabel `fai_panen`, dibaca lewat EF Core atau query Power Query seperti SU07 yang pernah kamu kerjakan.
- Tampilan: grid dan dashboard seperti TCRA.
- Setelah dibangun di Visual Studio, publish lewat DevAgro Hub.

---

## 14. Pertanyaan yang harus dijawab

Kerjakan Bab 14 pada minggu pertama. Tanpa jawaban, rancangan di atas hanya tebakan yang masuk akal.

### Untuk krani atau mandor (di TPH)

| # | Pertanyaan | Mengapa penting |
|---|---|---|
| 1 | Buah tiap pemanen dipisah atau dicampur di TPH? | Menentukan level pencatatan (Bab 6). |
| 2 | Apa yang ditulis krani di tiket, dan berapa lembar? | Menentukan kolom data (Bab 7). |
| 3 | Bagaimana kriteria buah matang, mentah, dan lewat matang? | Menentukan kelas AI. |
| 4 | Apakah ada sinyal di TPH? Operator apa? | Menentukan seberapa penting mode offline. |
| 5 | Siapa yang membawa HP dan HP apa? | Menentukan spesifikasi perangkat. |

### Untuk pembimbing PT LAT

| # | Pertanyaan | Mengapa penting |
|---|---|---|
| 6 | Apakah ada API atau cara resmi memasukkan data dari aplikasi luar ke mySAP365? | Menentukan cara 1, 2, atau API resmi (Bab 9.3). |
| 7 | Boleh membuat tabel perantara di database sandbox? | Menentukan lokasi staging. |
| 8 | Dari tabel ERP mana daftar blok, TPH, dan pemanen boleh dibaca? | Menentukan daftar master. |
| 9 | Berapa hari rotasi, kapan tanggal tutup gaji? | Menentukan periode (Bab 8). |
| 10 | Apakah tabel cluster tahun 2026 sudah ada? | Menentukan tujuan data di ERP. |
| 11 | Bagaimana ketentuan lisensi bila aplikasi dipakai perusahaan (Ultralytics memakai AGPL-3.0)? | Menentukan boleh tidaknya dipakai di luar tugas. |
| 12 | Bolehkah foto TBS dari kebun dikumpulkan dan diberi label? | Menentukan perbaikan model. |
| 13 | Transaksi (TCode) mana di mySAP365 yang mencatat hasil panen per pemanen? | Menentukan TCode tujuan data (Bab 9.3). |
| 14 | Di tabel atau TCode mana master blok, tahun tanam, luas, dan jumlah pokok, dan apakah boleh dibaca? | Menentukan data yang muncul setelah scan QR (Bab 5.3). |
| 15 | Boleh memasang QR di TPH, dan siapa yang mencetak serta memasangnya? | Menentukan kesiapan di lapangan. |

---

## 15. Risiko

| Risiko | Penanganan |
|---|---|
| Akurasi AI turun di kebun (dataset direkam di pabrik, bukan di TPH). | Ambil foto di TPH, perbaiki model, ukur akurasi terpisah pada foto kebun. |
| Cahaya buruk (siang terik, bayangan, hujan). | Beri petunjuk foto di layar. Uji di berbagai kondisi. |
| Tandan tertutup di tumpukan besar. | Foto tumpukan kecil. Krani bisa koreksi manual. |
| Tidak ada sinyal. | Simpan lokal, sinkron kemudian. |
| Akses ERP belum jelas. | Staging dan cara 1. Hentikan di sana bila izin belum ada. |
| Waktu satu bulan. | Kerjakan poin 1 sampai 3 pada definisi selesai dulu. |
| Krani sulit memakai aplikasi. | Layar sederhana, tombol besar, uji dengan krani nyata. |
| Lisensi dataset dan perangkat lunak. | Periksa lisensi dan tanyakan ke pembimbing. |

---

## 16. Cara menjelaskan ke dosen atau pembimbing

### Versi satu menit

> Di TPH, krani menghitung tandan dan menilai mutu secara manual, lalu mengetik ulang ke ERP. Aplikasi fAI26 mengganti itu dengan foto. AI di HP mengenali tandan dan kematangannya, krani memeriksa hasilnya, dan catatan tersimpan di HP meski tanpa sinyal. Saat sinyal ada, data terkirim ke API, masuk ke tabel perantara, lalu terbaca di mySAP365 lewat TCode baru. Data itu dipakai untuk laporan produksi, pengawasan mutu, dan perhitungan upah.

### Pertanyaan yang mungkin muncul

**Mengapa tidak langsung masuk ke database ERP?**
ERP punya aturan dan constraint sendiri. Pada Hari 83 simpan dokumen di TCDA gagal pada insert ke `dbo.tcdam`. Tabel perantara menjaga data ERP tetap aman.

**Bagaimana kalau AI keliru?**
Krani selalu bisa mengoreksi hasilnya. Koreksi tersimpan dan dipakai untuk memperbaiki model.

**Bagaimana akurasinya?**
Jawab dengan angka hasil pelatihan akhir (mAP50, precision, recall), dan jelaskan bahwa uji pada foto kebun menyusul. Jangan menjanjikan angka sebelum diukur.

**Siapa yang menikmati hasilnya?**
Krani (lebih cepat), mandor dan asisten (laporan lebih awal), bagian gaji (data per pemanen lebih rapi), dan pabrik (pembanding dengan timbangan).

**Kalau buah tercampur di TPH?**
Pencatatan fleksibel: per TPH wajib, nama pemanen opsional (Bab 6).

**Kenapa periode data penting?**
Tanggal panen menentukan rekap harian, rotasi, penguncian gaji, dan cluster tahun di ERP (Bab 8).

---

## Lampiran: yang sudah ada dan yang belum

| Sudah | Belum |
|---|---|
| Pemahaman alur data TCMA, TCDA, TCRA **[Kerjamu]** | Jawaban SOP TPH dari krani dan mandor |
| Dashboard dan laporan custom **[Kerjamu]** | Jalur resmi ke ERP dari pembimbing |
| Model deteksi dan kamera AI (menurutmu) | Hasil akhir pelatihan 50 epoch |
| Pelatihan awal di Colab (Gambar 1 dokumen Hari 89) | Model berjalan di HP, diuji di TPH |
| Rancangan alur, data, dan jadwal (panduan ini) | Aplikasi pencatatan, penyimpanan lokal, sinkronisasi |
| | API, tabel perantara, dan TCode di ERP |

Panduan ini berisi rancangan dan pengetahuan umum, bukan hasil uji. Bagian berlabel **[Umum]** dan **[Cek]** perlu kamu cocokkan dengan kondisi Kebun Aas sebelum dijadikan keputusan.

