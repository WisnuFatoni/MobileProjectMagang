# 12. Yang Perlu Dibuat di ERP (mySAP365): TCode HVDM dan Pendukungnya

Dokumen ini adalah **spesifikasi lengkap sisi ERP**. Bagian aplikasi (HP, API, database `fai26`, web admin) sudah jadi; yang belum ada dan menjadi jalur kritis adalah **TCode HVDM** di mySAP365 yang menarik catatan panen dari `fai26` dan membuat dokumen panen ERP. Untuk agen yang bekerja lewat peramban (Claude di Chrome), langkah operasionalnya ada di `13_PANDUAN_CLAUDE_BROWSER_ERP.md`; dokumen ini adalah **apa yang harus ada dan mengapa**, dokumen 13 adalah **bagaimana memeriksanya dan mengisinya lewat layar**.

Label yang dipakai di seluruh dokumen ini:

| Label | Arti |
|---|---|
| **[Fakta]** | Diambil dari kode, skema, atau database yang berjalan dan sudah diuji. Boleh dipegang. |
| **[Keputusan]** | Sudah diputuskan Ptoni. Jangan diubah tanpa bertanya. |
| **[Usulan]** | Rancangan dari pihak fAI26 yang belum diputuskan. Boleh dipakai sebagai bawaan, tetapi tandai di hasil kerja. |
| **[Dugaan]** | Kesimpulan dari bukti tidak langsung tentang ERP. **Wajib dicocokkan dengan ERP nyata** sebelum dipakai. |
| **[Belum diputuskan]** | Masih terbuka. Jangan mengarang; tanyakan ke Ptoni atau pembimbing. |

---

## 1. Posisi ERP dalam sistem

```
 HP mandor/krani (Android, offline-first)     Server fAI26 (Go + SQL Server)              mySAP365 (ERP)
 +--------------------------------+           +-------------------------------+           +----------------------------+
 | kamera + AI, catat, foto, GPS  |  HTTPS    | API REST (64 endpoint)        |           |  TCode HVDM  <-- BELUM ADA |
 | Room (SQLite) sendiri          | --------> | validasi, simpan ke fai_*     |  BACA     |  membaca view              |
 | persetujuan krani di HP krani  | <-------- | database "fai26"              | <-------- |  v_fai_panen_siap_impor    |
 |                                |  status   |  tabel fai_panen, ...         |  TULIS    |  memanggil prosedur        |
 +--------------------------------+           +-------------------------------+  lewat    |  sp_fai_impor_ok / _gagal  |
                                                         ^                      prosedur   |  -> hvd1m, hvd1da          |
                                                         |                                 +----------------------------+
                                            Web admin (React): master, QR, panen, IPD, pengguna, hak akses, audit
```

**Prinsip yang tidak boleh dilanggar [Keputusan]:**

1. HP **tidak pernah** menyentuh database ERP (kredensial tidak boleh ada di HP).
2. **Tidak ada API resmi mySAP365.** ERP mengambil langsung dari database `fai26`; API milik kita hanya supaya HP bisa mengisi `fai26`.
3. Tabel `fai_*` **tidak ditulis langsung** oleh kode ERP. Perpindahan status hanya lewat prosedur `sp_fai_*`.
4. Semua waktu di `fai26` disimpan **UTC**. Konversi ke WIB hanya untuk tampilan.
5. Data **tidak dihapus permanen** di `fai26` (hapus lunak).
6. ID catatan (`panen_id`) dibuat di HP (UUID). Kirim ulang aman: tidak menggandakan data.
7. ERP tidak memakai foreign key; relasi dijaga aplikasi (XPO) di sisi ERP.

**Apa yang sudah disediakan fAI26 untuk ERP [Fakta]:** view `dbo.v_fai_panen_siap_impor` (satu baris per catatan), view `dbo.v_fai_panen_pemanen_siap_impor` (kompatibilitas), empat prosedur `sp_fai_*`, role database `fai26_hvdm` (hak sempit), tabel log `fai_impor_log`, dan jejak `audit_log`. Definisi lengkap ada di `02_SKEMA_DATABASE_SERVER.md` bagian 4 dan 5.

**Yang harus dibuat di sisi ERP:** semua yang tercantum di bagian 3 (paket kerja E-01 sampai E-15).

---

## 2. Lingkungan ERP yang diketahui

**[Fakta dari catatan Ptoni, 10 dan 18 Nov 2026]**

| Hal | Isi |
|---|---|
| Produk | mySAP365 |
| Transaksi yang dikenal | TCMA, TCDA (tiket panen; simpan dokumen baru di TCDA pernah gagal `ConstraintViolationException` pada insert ke `dbo.tcdam`), TCRA (laporan), **HVD1** (harvest harian) |
| TCode tujuan | **HVDM**, salinan HVD1 yang sumber datanya view `fai26` (bukan input manual krani). "HVD1" di dokumen lama dibaca HVDM |
| Pengembangan | Visual Studio (XPO/EF); dipublikasikan lewat **DevAgro Hub** |
| Pemisahan data | per **cluster Unit-Tahun**: database seperti `kp_stl2025`, `kp_le2026`; master di `sap365pub_kp` |
| Lingkungan uji | **CWE** (server uji). **Jangan uji ke produksi.** `kp_le2026` punya data nyata (dipakai membaca pola); `kp_stl2025` hampir kosong |
| Foreign key | ERP tidak memakai foreign key |

**Tabel ERP yang relevan (hanya kolom penting) [Fakta]:**

```
hvd1m  (header)   OID PK, NOBUKTI (dibuat ERP), TGLBUKTI, KETMASTER, PERIODEM, PERIODEY, REC_CREATDATE, REC_CREATBY
hvd1da (baris)    OID PK, hvd1m FK, NOURUT, HRMBCODE (pemanen), ASM3CODE (blok), ROTASI, HASILHI1,
                  JJG_RIPE, JJG_OVERRIPE, JJG_EMPTY, JJG_BUSUK, JJG_SAKIT, JJG_UNKNOWN, JJG_LANDAK,
                  BJR, KONDISI_CUACA, TGLBUKTI, KETDETAIL, FCM4CODE, HVM2CODE, NOTIKETPANEN, NOSPB,
                  harvest_employee_id, REC_*
asm3   (blok)     OID PK, CODE (kode blok), NAME_ID, LUASBLOK, BLNTANAM, SPH
```

Struktur DDL lengkap ERP **tidak ada** di dokumen proyek; lihat database ERP langsung. Kolom lain yang ada tetapi belum dipetakan harus dicek.

**Fakta dari data HVD1 di `kp_le2026` (dibaca Ptoni):**

- 99.400 header untuk 218 tanggal: header **bukan** satu per tanggal.
- Nomor bukti berformat `urut/HVD1/MDR.PNN.AFD.7/kp_le/JUL/31`, memuat kode mandor dan afdeling.
- 97.086 header punya 1 detail, 2.312 punya 2 detail, 1 punya 3 detail.
- Hanya `JJG_OVERRIPE` yang terisi di kolom grading; `JJG_RIPE`, `BUSUK`, `SAKIT`, `EMPTY`, `UNKNOWN`, `LANDAK` kosong.
- `ROTASI` selalu 1; `NOURUT` 1 atau 2; `FCM4CODE` hanya dua nilai (OID 889 dan 9148).
- `HRMBCODE` terisi 100% (83 pemanen berbeda), `NOTIKETPANEN` terisi, **tidak ada kolom TPH**: HVD1 mencatat satu baris per pemanen per tiket panen.
- `NOSPB` terisi 3% (**jangan dipakai**); `BJR` belum terisi (**jangan dipakai**); `harvest_employee_id` terisi hampir semua baris (lihat keputusan B7).
- `HVM2CODE` kosong semua tahun 2026; tabel `hvm2` kosong. `KONDISI_CUACA` hanya ada di `kp_stl2025` dan kosong.
- `hvd1db` kosong; tidak ada kolom untuk janjang mentah/kurang matang.
- `FCM4CODE`: akun biaya dari master `fcm4` (database `sap365pub_kp`), isinya "TM-Upah Panen", **dua versi masa berlaku**: OID 889 (sampai 29-07-2026) dan OID 9148 (mulai 01-07-2026), tumpang tindih Juli 2026.

**[Dugaan]** Kolom berakhiran `CODE` di ERP (`FCM4CODE`, `HRMBCODE`, `ASM3CODE`) menyimpan **OID** tabel master terkait (terbukti untuk `FCM4CODE`: nilainya OID 889/9148). Karena itu `asm3_oid` dipetakan ke `ASM3CODE` dan `hrmb_code` ke `HRMBCODE`. **Cocokkan dengan data nyata `hvd1da` sebelum menulis.**

---

## 3. Paket kerja ERP

Prioritas: **P0** menghalangi segalanya, **P1** wajib sebelum uji dengan data nyata, **P2** sebelum dipakai harian, **P3** pelengkap. "Pemilik" menunjukkan siapa yang paling mungkin mengerjakan: **Ptoni/DBA/tim ERP** (membutuhkan akses penulisan di ERP atau server) atau **agen peramban** (hanya membaca layar, mencatat, dan mengisi hasil ke berkas).

| ID | Paket kerja | Prioritas | Pemilik | Hasil yang diminta |
|---|---|---|---|---|
| E-01 | Login SQL untuk HVDM dan uji koneksi ke `fai26` | P0 | Ptoni/DBA | Login anggota `fai26_hvdm`; bukti SELECT view berhasil dan UPDATE tabel ditolak |
| E-02 | Pencocokan master: OID blok, kode HRMB pemanen, OID afdeling, kode HRMB mandor | P0 | Agen peramban + Ptoni | Empat berkas CSV `lampiran/cocokkan_*.csv` terisi kolom "ASLI" |
| E-03 | Tabel cluster tahun 2026 untuk unit yang dipakai | P0 | Tim ERP | Database/tabel cluster 2026 ada dan terisi master |
| E-04 | Keputusan pemetaan (B3, B4, B6, B7, B8, B17, B18) | P0 | Ptoni + pembimbing | Tabel keputusan bagian 6 terisi |
| E-05 | TCode HVDM: salinan HVD1 + layar "Tarik dari fAI26" | P1 | Ptoni (Visual Studio) | Layar sesuai bagian 5 |
| E-06 | Logika pengimporan per catatan | P1 | Ptoni | Algoritma bagian 7 berjalan dan lolos skenario uji bagian 11 |
| E-07 | Pengelompokan header (mandor + afdeling + tanggal) dan nomor bukti | P1 | Ptoni | Sesuai pola HVD1 yang diamati (T-02 di dokumen 13) |
| E-08 | Pemetaan kolom `hvd1da` dan `hvd1m` | P1 | Ptoni | Tabel bagian 8 terimplementasi |
| E-09 | Pemanggilan prosedur dan idempotensi lintas database | P1 | Ptoni | Bagian 9: tidak ada dokumen ganda walau `sp_fai_impor_ok` gagal dipanggil |
| E-10 | Katalog pesan penolakan | P1 | Ptoni | Bagian 10 diterapkan persis |
| E-11 | Penguncian periode gaji | P2 | Ptoni + ERP | Penolakan dengan pesan jelas bila periode tertutup |
| E-12 | Koreksi setelah impor (belum dirancang) | P2 | Ptoni + pembimbing | Keputusan dan rancangan |
| E-13 | Hak akses TCode HVDM di ERP (siapa boleh menarik) | P2 | Tim ERP | Otorisasi mengikuti padanan `impor.erp` |
| E-14 | Pengujian penuh di CWE | P1 | Agen peramban + Ptoni | Laporan T-07 (dokumen 13) |
| E-15 | Publikasi lewat DevAgro Hub dan rilis bertahap | P2 | Ptoni | HVDM tersedia untuk unit pertama |

### E-01. Login SQL dan koneksi

- Buat **login SQL khusus HVDM** dan jadikan anggota role database `fai26_hvdm`. Perintah (DBA, di database `fai26`): `ALTER ROLE fai26_hvdm ADD MEMBER [nama_login_hvdm];`
- Role itu **hanya** memegang `SELECT` pada kedua view dan `EXECUTE` pada empat prosedur `sp_fai_*`. **Jangan** beri hak lain, terutama hak tulis tabel.
- Connection string disimpan di konfigurasi server ERP, **bukan** di kode dan bukan di dokumen. Jangan menyalin kredensial ke berkas apa pun di repositori.
- Bukti penerimaan (jalankan dengan login itu): `SELECT TOP 1 * FROM fai26.dbo.v_fai_panen_siap_impor` berhasil; `UPDATE fai26.dbo.fai_panen SET status_erp = status_erp WHERE 1=0` **ditolak** (permission denied); `EXEC fai26.dbo.sp_fai_tandai_dibaca @panen_id = '00000000-0000-0000-0000-000000000000'` mengembalikan `berubah = 0` tanpa galat.
- Server `fai26` untuk uji ada di CWE; database lokal pengembangan Ptoni (`fai26_dev`) hanya untuk laptop dan **tidak terjangkau dari ERP**. Alamat server dan koordinasi akses: Ptoni/DBA.

### E-02. Pencocokan master

Master di `fai26` saat ini memakai nilai **sementara** untuk kunci ke ERP. Nilai asli harus diambil dari ERP dan dimasukkan lewat web admin fAI26. Sebelum itu selesai, **HVDM menolak semua catatan** (bagian 7 langkah 3), karena `asm3_oid` dan `hrmb_code` palsu tidak akan ada di ERP.

| Data | Di fAI26 | Kunci ERP | Keadaan sekarang | Berkas |
|---|---|---|---|---|
| Blok (118) | `blok.erp_oid` | `asm3.OID` | **sementara** 900001 sampai 900118 | `lampiran/cocokkan_blok.csv` |
| Pemanen (61) | `pemanen.erp_hrmb_code` | `hrmb` (`HRMBCODE`) | **sementara** 800001 sampai 800061 | `lampiran/cocokkan_pemanen.csv` |
| Mandor per pemanen | `pemanen.mandor_hrmb_code` | kode HRMB mandor | **kosong** | kolom di CSV pemanen |
| Afdeling (DIV1, DIV2) | `divisi.erp_oid` | OID afdeling | **kosong** | `lampiran/cocokkan_divisi.csv` |
| Unit | `unit.erp_db`, `unit.tahun_cluster` | nama database ERP | `kp_stl2025` / 2026 (**tidak konsisten**: database 2025 dengan tahun cluster 2026) | `lampiran/cocokkan_unit.csv` |
| Akun mandor | `app_user.mandor_hrmb_code`, `afdeling_kode` | kode HRMB mandor, kode afdeling | diisi admin saat membuat akun mandor | dibuat di web admin |

Aturan pencocokan: blok dicocokkan menurut **kode blok**; pemanen menurut **nama** (atau nomor induk bila ada) lalu **dikonfirmasi manusia**. Pencocokan nama pemanen rawan salah (nama kembar, ejaan beda): **jangan menebak**; tandai "ragu" dan serahkan ke Ptoni. Detail prosedur ada di dokumen 13 (T-03). Setelah CSV terisi, admin fAI26 memperbarui nilai lewat web admin (halaman Blok, Pemanen, Divisi). Mengganti `erp_oid` blok tidak mengubah catatan lama (nilai disalin saat diterima); catatan yang sudah ada dengan OID sementara akan ditolak HVDM dan harus dikirim ulang atau dibersihkan.

Catatan penting: **sumber master nyata belum dipastikan (B8).** Tabel `asm3` di `kp_stl2025` kosong, sedangkan `kp_le2026` punya 153 blok. Perlu diputuskan unit mana yang dipakai pertama dan dari database mana nilai asli diambil.

### E-03. Tabel cluster 2026

Menurut pembimbing (10 Nov), "tabel cluster 2026 belum ada, nanti dibuat". Ini tugas tim ERP. Tahun cluster ditentukan dari **`tanggal_panen`** (bukan tanggal kirim atau impor; 31 Des dan 1 Jan jatuh ke tahun berbeda). Bila cluster tahun itu belum ada, HVDM **menolak** dengan pesan jelas (bagian 10, pesan P-05), tidak crash.

### E-04. Keputusan pemetaan

Lihat bagian 6 (tabel keputusan lengkap dengan usulan bawaan).

### E-05. Layar TCode HVDM

**Dasar:** salinan HVD1. Yang berbeda: sumber data bukan input manual, tetapi view `fai26`. Tombol dan elemen yang diminta [Usulan]:

1. **Filter:** unit (menentukan database cluster), rentang tanggal panen (`@dari`, `@sampai`), opsional afdeling dan mandor, opsional status (baru dan siap saja; lihat catatan di bawah).
2. **Tombol "Tarik dari fAI26"** (nama persis dari rencana): membaca view dan mengisi grid pratinjau, **tanpa menulis apa pun**.
3. **Grid pratinjau**, satu baris per catatan, dengan kolom: tanggal panen, afdeling, mandor (HRMB), blok (`blok_kode`), TPH (`tph_kode`), pemanen (HRMB), janjang total, rincian `JJG_*`, `jjg_tanpa_kolom`, brondolan (kg), pencatat, penyetuju, status lokasi, waktu diterima, dan kolom **"Hasil validasi"** (OK atau pesan penolakan bagian 10) yang dihitung **sebelum** impor. Catatan dengan validasi gagal tampil merah.
4. **Tombol "Proses impor"** untuk baris terpilih atau semua baris OK. Hanya pengguna dengan otorisasi HVDM (E-13).
5. **Ringkasan hasil** setelah proses: jumlah OK, jumlah ditolak (dengan pesan), nomor bukti dokumen yang dibuat atau dipakai.
6. Layar **tidak boleh** menawarkan edit nilai janjang. Koreksi dilakukan di fAI26 (mandor edit lalu kirim ulang) atau lewat ERP sebagai koreksi resmi (E-12).

Catatan status: view hanya memuat `BARU` dan `DIBACA`. Catatan `MENUNGGU` (menunggu krani) **tidak tampil**; itu disengaja (belum disetujui). Catatan `DITOLAK` oleh ERP tidak tampil sampai diperbaiki di HP (kembali `BARU`) atau dikembalikan lewat `sp_fai_ulang_impor`.

### E-06. Logika pengimporan

Lihat bagian 7 (algoritma lengkap) dan bagian 10 (pesan).

### E-07 dan E-08. Header dan pemetaan

Lihat bagian 8.

### E-09. Idempotensi lintas database

Lihat bagian 9. Ini bagian yang paling mudah salah.

### E-10 sampai E-13

Lihat bagian 10 (pesan), bagian 12 (periode gaji dan koreksi), dan catatan otorisasi: peran fAI26 `ASISTEN` memegang `impor.erp` (hak menarik) sebagai padanan; di ERP, otorisasi mengikuti mekanisme ERP sendiri.

### E-14 dan E-15

Lihat bagian 11 (skenario uji) dan bagian 13 (rilis).

---

## 4. Kontrak data dari fAI26 (yang dibaca ERP)

### 4.1 Satu catatan = satu TPH = satu pemanen = satu baris `hvd1da` [Keputusan 18 Nov]

- Rekan Ptoni di perusahaan memastikan **satu TPH hanya untuk satu pemanen**. Setiap catatan membawa **tepat satu pemanen** dan **seluruh janjang** dikreditkan ke dia. Tidak ada pembagian janjang antar pemanen.
- Satu mandor mengirim banyak catatan sehari (satu per TPH). **HVDM yang mengelompokkan** catatan menjadi dokumen (header), bukan aplikasi.
- Server menolak catatan dengan total janjang nol, jadi catatan yang sampai ke ERP selalu punya paling sedikit satu janjang.
- Kombinasi **tanggal + unit + blok (asm3_oid) + TPH + pemanen** hanya boleh satu catatan (indeks unik `ux_fai_panen_tph_pemanen`; API menjawab 409 untuk yang ganda).

### 4.2 Alur status

| Di server (`fai_panen.status_erp`) | Siapa yang mengubah | Arti | Terlihat HVDM? |
|---|---|---|---|
| `MENUNGGU` | API saat mandor mengirim tanpa penyetuju | Menunggu krani menyetujui di HP krani | **Tidak** |
| `BARU` | API saat krani menyetujui (atau mandor menyetujui sendiri) | Siap ditarik ERP | Ya |
| `DIBACA` | `sp_fai_tandai_dibaca` | ERP sedang memproses (opsional) | Ya |
| `DIIMPOR` | `sp_fai_impor_ok` | Sudah menjadi dokumen ERP | Tidak |
| `DITOLAK` | Krani menolak, atau `sp_fai_impor_gagal` | Ditolak; alasan di `fai_impor_log.pesan_error` | Tidak |

Pemetaan yang dilihat HP: `MENUNGGU` menjadi "Menunggu krani"; `BARU` dan `DIBACA` menjadi `TERKIRIM`; `DIIMPOR` menjadi `DITERIMA`; `DITOLAK` menjadi `DITOLAK` dengan alasan. Catatan yang `DITERIMA` **tidak boleh diubah** di HP.

### 4.3 Kolom view `dbo.v_fai_panen_siap_impor`

Satu baris per `fai_panen` dengan `status_erp IN ('BARU','DIBACA')` dan `dihapus_pada IS NULL`. Tipe di bawah adalah tipe **sebenarnya** dari katalog database.

| # | Kolom | Tipe | Null | Isi dan catatan |
|---|---|---|---|---|
| 1 | `panen_id` | uniqueidentifier | tidak | kunci catatan; **kunci idempotensi** |
| 2 | `unit_id` | int | tidak | id internal unit (bukan kunci ERP) |
| 3 | `unit_kode` | nvarchar(20) | tidak | mis. `KP_STL` |
| 4 | `unit` | nvarchar(20) | tidak | sama dengan `unit_kode` (sejak migrasi 007) |
| 5 | `unit_erp_db` | nvarchar(50) | tidak | nama database ERP unit (`unit.erp_db`) |
| 6 | `tanggal_panen` | date | tidak | tanggal panen; **menentukan tahun cluster** |
| 7 | `asm3_oid` | bigint | tidak | OID blok di ERP → `ASM3CODE` |
| 8 | `blok_kode` | nvarchar(20) | ya | kode blok (teks bantu) |
| 9 | `tph_kode` | nvarchar(30) | ya | **hanya nomor TPH**, bukan QR; tidak ada kolom TPH di `hvd1da` |
| 10 | `afdeling_kode` | nvarchar(20) | ya | salinan saat diterima, dari akun pencatat |
| 11 | `afdeling_erp_oid` | int | ya | salinan `divisi.erp_oid` (OID afdeling ERP) |
| 12 | `mandor_hrmb_code` | int | ya | kode HRMB mandor |
| 13 | `pemanen_hrmb_code` | int | ya | pemanen tunggal → `HRMBCODE` |
| 14 | `pemanen_jjg` | int | ya | janjang yang dikreditkan = `jjg_total` |
| 15 | `waktu_panen` | datetime2 | tidak | waktu catatan dibuat di HP (UTC) |
| 16 | `rotasi` | int | ya | **selalu NULL dari HP** |
| 17 | `kondisi_cuaca` | int | ya | **selalu NULL dari HP** |
| 18 | `brondolan_kg` | float | ya | kilogram; **tidak ada kolom ERP** |
| 19 | `catatan` | nvarchar(500) | ya | catatan bebas pencatat |
| 20 | `status_erp` | nvarchar(12) | tidak | `BARU` atau `DIBACA` |
| 21 | `status_lokasi` | nvarchar(20) | tidak | `DI_TPH`, `DI_LUAR`, `AKURASI_RENDAH`, `TANPA_GPS`, `TPH_TANPA_KOORDINAT` (hanya menandai) |
| 22 | `gps_lat` | decimal(9,6) | ya | lokasi pencatat |
| 23 | `gps_lon` | decimal(9,6) | ya | |
| 24 | `jarak_ke_tph_m` | float | ya | |
| 25 | `model_versi` | nvarchar(50) | ya | model AI yang dipakai |
| 26 | `diterima_server` | datetime2 | tidak | |
| 27 | `disetujui_pada` | datetime2 | ya | |
| 28 | `pencatat_username` | nvarchar(50) | ya | **mandor** pencatat |
| 29 | `disetujui_username` | nvarchar(50) | ya | **krani** penyetuju |
| 30 | `JJG_RIPE` | int | ya | jumlah `jml_final` kelas ripe |
| 31 | `JJG_OVERRIPE` | int | ya | overripe |
| 32 | `JJG_EMPTY` | int | ya | empty |
| 33 | `JJG_BUSUK` | int | ya | **selalu 0**: model tidak memisah (lihat B4) |
| 34 | `JJG_SAKIT` | int | ya | **selalu 0** |
| 35 | `JJG_UNKNOWN` | int | ya | abnormal (sementara) |
| 36 | `JJG_LANDAK` | int | ya | **selalu 0** |
| 37 | `jjg_tanpa_kolom` | int | ya | unripe + underripe (tidak punya kolom ERP) |
| 38 | `jjg_total` | int | ya | total janjang semua kelas (`jml_final`) |
| 39 | `jml_ripe` | int | ya | per kelas model, apa adanya |
| 40 | `jml_overripe` | int | ya | |
| 41 | `jml_underripe` | int | ya | |
| 42 | `jml_unripe` | int | ya | |
| 43 | `jml_empty` | int | ya | |
| 44 | `jml_abnormal` | int | ya | |
| 45 | `jumlah_pemanen` | int | ya | selalu 1 |

View kedua `dbo.v_fai_panen_pemanen_siap_impor` (`panen_id`, `hrmb_code`, `jjg_alokasi`) hanya untuk kompatibilitas; **cukup pakai kolom `pemanen_hrmb_code` di view utama**. Tabel `fai_panen_pemanen` **deprecated**: HVDM tidak membacanya.

**Yang tidak ada di view dan tidak dikirim HP:** kode mandor selain HRMB, nomor tiket panen, SPB, rotasi, cuaca, foto (foto tetap di server fAI26), jejak persetujuan lengkap.

### 4.4 Prosedur jembatan

| Situasi | Panggilan | Efek |
|---|---|---|
| Mulai memproses (opsional) | `EXEC dbo.sp_fai_tandai_dibaca @panen_id` | `BARU` menjadi `DIBACA`; mengembalikan `berubah` (1 atau 0; 0 aman bila status bukan BARU) |
| Berhasil dibuat di ERP | `EXEC dbo.sp_fai_impor_ok @panen_id, @hvd1m_oid, @hvd1da_oid, @diimpor_oleh` | `BARU`/`DIBACA` menjadi **`DIIMPOR`**; menulis `fai_impor_log` (OK) dan `audit_log` (IMPOR). HP melihat **DITERIMA** |
| Ditolak | `EXEC dbo.sp_fai_impor_gagal @panen_id, @pesan, @diimpor_oleh` | menjadi **`DITOLAK`**; log GAGAL dengan `pesan_error` dan audit `IMPOR_GAGAL`. **`@pesan` wajib dan tampil di HP** |
| Perbaiki lalu coba lagi | `EXEC dbo.sp_fai_ulang_impor @panen_id, @oleh` | `DITOLAK` menjadi `BARU`; riwayat log tetap |

Tipe parameter: `@panen_id UNIQUEIDENTIFIER`, `@hvd1m_oid BIGINT`, `@hvd1da_oid BIGINT`, `@diimpor_oleh INT NULL` (user_id fAI26 bila ada; **boleh NULL**), `@pesan NVARCHAR(500)` (dipotong 500; ke audit 300), `@oleh INT NULL`. Galat yang dilempar: **50001** catatan tidak ditemukan, **50002** sudah diproses (bukan BARU/DIBACA), **50003** pesan kosong. Tanda tangan prosedur **tidak boleh diubah** (ERP bergantung padanya).

**Mengapa harus lewat prosedur:** prosedur mengisi `diubah_pada`. HP mengetahui status baru dari `GET /api/panen?sejak=...` yang membandingkan `COALESCE(diubah_pada, diterima_server)`. Bila status diubah tanpa mengisi `diubah_pada`, **HP tidak akan pernah tahu**. Prosedur juga menjaga perpindahan status yang sah dan menulis log serta audit dalam satu transaksi.

---

## 5. Layar yang harus tersedia untuk operator ERP

Ringkasan fungsi layar HVDM (rincian bagian E-05). Skenario pemakaian harian:

1. Operator ERP (mis. krani administrasi atau asisten) membuka HVDM, memilih unit dan rentang tanggal.
2. Menekan "Tarik dari fAI26". Grid terisi; baris yang tidak lolos validasi tampil dengan alasannya (admin fAI26 atau mandor yang memperbaikinya, bukan operator ERP).
3. Menekan "Proses impor". ERP membuat dokumen; hasil per catatan tampil (nomor bukti atau alasan penolakan).
4. Catatan yang ditolak muncul di HP mandor dengan alasan. Mandor memperbaiki di HP lalu kirim ulang; catatan kembali `BARU` dan muncul lagi di HVDM pada penarikan berikutnya.

---

## 6. Keputusan yang diperlukan (dengan usulan bawaan)

Jangan mengarang jawabannya. Bila diperlukan sebelum keputusan resmi, pakai **usulan bawaan** dan **tandai** di hasil kerja bahwa itu bawaan.

| Kode | Soal | Fakta pendukung | Usulan bawaan | Menghalangi? |
|---|---|---|---|---|
| B3 | Jumlah hari rotasi | Pembimbing belum menjawab | `ROTASI` = NULL bila kolom boleh NULL; bila wajib, pakai 1 seperti data HVD1 (selalu 1) | Tidak |
| B4 | Pemecahan kelas abnormal | Model tidak memisah busuk/sakit/lain; tidak dilatih ulang | Semua abnormal ke `JJG_UNKNOWN` (sudah demikian di view) | Tidak |
| B6 | Isi `HASILHI1` | Data lama: bilangan bulat 0 sampai 923, rata-rata 10,3 (mirip janjang) | **ripe + overripe** (janjang yang dipanen); alternatif: semua janjang. **Konfirmasi ke pembimbing sebelum rilis** | Ya untuk laporan ERP |
| B7 | `harvest_employee_id` | Terisi hampir semua baris di `kp_le2026` | Isi dari master pemanen bila ada kolom padanan; bila tidak diketahui, **jangan tebak: tolak/laporkan** | Mungkin |
| B8 | Sumber master blok/TPH/pemanen | `asm3` di `kp_stl2025` kosong; `kp_le2026` punya 153 blok | Ambil dari ERP unit yang dipilih pertama (lihat E-02) | **Ya** |
| B9 | Perlu dicatat tangkai panjang, nomor tiket, SPB | `NOSPB` terisi 3%, jangan dipakai | Tidak dicatat dulu; `NOTIKETPANEN` dibuat HVDM bila wajib (bentuk: ikuti HVD1) | Mungkin |
| B10 | Siapa admin aplikasi | | Orang IT atau krani administrator | Tidak |
| B17 | Bentuk identitas **mandor** dan **afdeling** di header HVD1; apakah `divisi.erp_oid` = OID afdeling | Server menyimpan keduanya apa adanya; nomor bukti memuat kode mandor dan afdeling (`MDR.PNN.AFD.7`) | **[Dugaan]** kode HRMB mandor dan OID afdeling; **wajib dicocokkan di T-02** | **Ya** |
| B18 | Nilai `ROTASI`, `NOURUT`, `FCM4CODE`, nomor bukti, dan siapa mengisinya | `ROTASI` selalu 1; `NOURUT` 1 atau 2 | **HVDM mengisi sendiri**: `ROTASI`=1, `NOURUT`=urutan baris di header, `FCM4CODE`=versi berlaku pada tanggal panen (bagian 8), nomor bukti dibuat ERP | Tidak |
| A1 | Unripe dan underripe | Tidak ada kolom di `hvd1da`; `hvd1db` kosong | Tetap di staging fAI26; tidak dibuang diam-diam; minta kolom ke tim ERP bila memang dibutuhkan | Tidak |
| A2 | Brondolan (kg), cuaca | Tidak ada kolom | Tetap di staging; cuaca NULL | Tidak |
| A3 | `FCM4CODE` bulan peralihan (Juli 2026) | OID 889 sampai 29-07-2026; OID 9148 mulai 01-07-2026 (tumpang tindih) | Pilih versi yang masa berlakunya mencakup `tanggal_panen`; **bila dua versi berlaku, pilih yang terbaru (9148)** dan minta konfirmasi | Ya (biaya salah akun) |
| A4 | Unit pertama untuk uji | | `kp_le2026` (data nyata untuk pola); jangan produksi | Ya |
| A5 | Mandor boleh mengedit catatan terkirim yang belum ditarik | Sekarang **hanya `DITOLAK`** yang bisa diedit di HP; catatan `MENUNGGU` boleh diedit sebelum krani menyetujui (aturan 20 Nov) | Tidak ada perubahan | Tidak |
| A6 | Penguncian periode gaji | Tutup sekitar tanggal 1 sampai 4 awal bulan | HVDM menolak ke periode tertutup (E-11) | Ya sebelum produksi |

---

## 7. Algoritma pengimporan per catatan (spesifikasi)

**[Usulan]**, sesuaikan dengan struktur nyata ERP. **Satu transaksi ERP per catatan.** Urutan yang dianjurkan:

```
UNTUK SETIAP baris view (urut tanggal_panen, diterima_server):

 1. (opsional)  EXEC fai26.dbo.sp_fai_tandai_dibaca @panen_id

 2. VALIDASI MASTER  (gagal -> sp_fai_impor_gagal, pesan sesuai bagian 10, lanjut ke baris berikutnya)
      a. afdeling_erp_oid, mandor_hrmb_code, pemanen_hrmb_code tidak NULL         (P-01..P-03)
      b. Database cluster untuk tahun(tanggal_panen) pada unit_erp_db ada          (P-05)
      c. Blok dengan OID = asm3_oid ada di asm3 cluster itu                        (P-04)
      d. Pemanen dengan OID/kode = pemanen_hrmb_code ada di hrmb                   (P-06)
      e. Periode gaji tanggal_panen belum ditutup                                  (P-07)
      f. Total janjang > 0 dan jumlah kolom yang dipetakan tidak melebihi jjg_total (P-08)
      g. Belum ada dokumen ERP untuk panen_id ini (cek penanda idempotensi, bagian 9)
         -> bila ada, JANGAN buat lagi: langsung ke langkah 6 dengan OID yang ada

 3. CARI/BUAT HEADER hvd1m untuk kunci (mandor_hrmb_code, afdeling, tanggal_panen, unit)   (bagian 8.1)
      - bila ada -> pakai (NOURUT detail berikutnya = jumlah detail + 1)
      - bila tidak -> buat; NOBUKTI dibuat ERP; KETMASTER menurut pola HVD1

 4. BUAT BARIS hvd1da  (bagian 8.2)
      ASM3CODE=asm3_oid; HRMBCODE=pemanen_hrmb_code; JJG_*=dari view; HASILHI1 sesuai B6;
      FCM4CODE=versi berlaku pada tanggal_panen; ROTASI=1/NULL; HVM2CODE=NULL; KONDISI_CUACA=NULL;
      TGLBUKTI=tanggal_panen; KETDETAIL = "[fAI26:<panen_id>] TPH <tph_kode>; <catatan>; oleh <pencatat_username>"
      JANGAN isi NOSPB dan BJR.
      REC_CREATBY = user ERP yang mengimpor; REC_CREATDATE = saat impor.

 5. COMMIT transaksi ERP.   (jika gagal di sini: rollback, sp_fai_impor_gagal dengan pesan P-09)

 6. EXEC fai26.dbo.sp_fai_impor_ok @panen_id, @hvd1m_oid, @hvd1da_oid, @diimpor_oleh
      - sukses -> selesai
      - galat 50002 (sudah diproses) -> anggap sukses (impor ganda yang aman), catat peringatan
      - galat lain/koneksi putus -> JANGAN buat dokumen lagi; coba ulang HANYA langkah 6 (bagian 9)
```

Rincian penting:

- **Tidak ada pembagian janjang** (satu catatan, satu baris). Jangan menunggu atau membuat baris `hvd1da` lebih dari satu per catatan.
- **`jml_ai` jangan dipakai**: hanya `jml_final` (view sudah memakainya).
- **Unripe/underripe (`jjg_tanpa_kolom`)**: tidak punya kolom ERP. Jangan dibuang diam-diam: tampilkan di grid pratinjau dan di `KETDETAIL` bila diputuskan (A1).
- **Waktu UTC.** Tahun cluster dari `tanggal_panen` (kolom `date`, bukan datetime).
- **Pesan ke HP** ≤ 500 karakter, singkat dan bisa dipahami mandor/krani (bukan jejak tumpukan pengecualian).

---

## 8. Pemetaan ke tabel ERP

### 8.1 Header `hvd1m`

| Kolom ERP | Nilai | Status |
|---|---|---|
| Kunci pengelompokan | (mandor, afdeling, `tanggal_panen`, unit) | **[Dugaan]** sesuai HVD1 (99.400 header untuk 218 tanggal); **verifikasi di T-02** |
| `NOBUKTI` | dibuat ERP | Pola HVD1 `urut/HVD1/MDR.PNN.AFD.7/kp_le/JUL/31` memuat mandor dan afdeling; **arti `MDR.PNN.AFD.7` belum dipastikan (B18)** |
| `TGLBUKTI` | `tanggal_panen` | [Fakta] |
| `KETMASTER` | **belum diverifikasi** | Cek isi di layar HVD1 sebelum menyalin logikanya |
| `PERIODEM`, `PERIODEY` | bulan dan tahun dari `tanggal_panen` | [Dugaan] |
| `REC_CREATDATE`, `REC_CREATBY` | saat impor; user ERP pengimpor | [Fakta] |

### 8.2 Baris `hvd1da`

| Kolom ERP | Sumber di view | Keterangan |
|---|---|---|
| `hvd1m` (FK) | header langkah 3 | |
| `NOURUT` | urutan dalam header | data lama 1 atau 2 (B18) |
| `HRMBCODE` | `pemanen_hrmb_code` | **wajib**, satu pemanen per baris |
| `ASM3CODE` | `asm3_oid` | OID blok |
| `ROTASI` | tidak ada (NULL dari HP) | B3: 1 atau NULL |
| `JJG_RIPE` | `JJG_RIPE` | |
| `JJG_OVERRIPE` | `JJG_OVERRIPE` | satu-satunya kolom grading yang terisi di data lama |
| `JJG_EMPTY` | `JJG_EMPTY` | |
| `JJG_BUSUK`, `JJG_SAKIT` | `JJG_BUSUK`, `JJG_SAKIT` (selalu 0) | B4 |
| `JJG_UNKNOWN` | `JJG_UNKNOWN` (abnormal) | sementara |
| `JJG_LANDAK` | `JJG_LANDAK` (selalu 0) | |
| `HASILHI1` | dihitung | **B6**; usulan ripe + overripe |
| `BJR` | jangan diisi | belum terisi di data lama |
| `KONDISI_CUACA` | NULL | tidak ada master cuaca |
| `TGLBUKTI` | `tanggal_panen` | |
| `KETDETAIL` | catatan + nomor TPH + username pencatat + penanda `[fAI26:<panen_id>]` | **tidak ada kolom TPH di ERP**; penanda dipakai untuk idempotensi |
| `FCM4CODE` | versi `fcm4` yang berlaku pada `tanggal_panen` | A3 |
| `HVM2CODE` | NULL | tabel `hvm2` kosong |
| `NOTIKETPANEN` | **belum diputuskan** (B9) | terisi di data lama; bentuk ikuti HVD1 |
| `NOSPB` | jangan diisi | terisi 3% saja |
| `harvest_employee_id` | **B7** | terisi hampir semua baris di data lama |
| `REC_CREATBY`, `REC_CREATDATE` | user ERP pengimpor, saat impor | |

### 8.3 Pemetaan kelas (HP dan server ke ERP)

| Kelas model | `kelas_grading` | `erp_kolom` | Kolom di view | Nasib di ERP |
|---|---|---|---|---|
| unripe (mentah) | 1 | NULL | `jml_unripe`, `jjg_tanpa_kolom` | tetap di fAI26 |
| underripe (kurang matang) | 2 | NULL | `jml_underripe`, `jjg_tanpa_kolom` | tetap di fAI26 |
| ripe (matang) | 3 | `JJG_RIPE` | `JJG_RIPE` | `hvd1da.JJG_RIPE` |
| overripe (lewat matang) | 4 | `JJG_OVERRIPE` | `JJG_OVERRIPE` | `hvd1da.JJG_OVERRIPE` |
| empty (kosong) | 5 | `JJG_EMPTY` | `JJG_EMPTY` | `hvd1da.JJG_EMPTY` |
| abnormal | 6 | `JJG_UNKNOWN` (sementara) | `JJG_UNKNOWN` | `hvd1da.JJG_UNKNOWN` |

Urutan keluaran model: `0 abnormal, 1 empty, 2 overripe, 3 ripe, 4 underripe, 5 unripe`.

### 8.4 Kunci yang menghubungkan tiga dunia

- **Blok:** `blok.blok_id` (internal fAI26) ≠ `blok.erp_oid` (**kunci ke ERP**: `asm3.OID`). Jangan memakai `blok_id` untuk ERP.
- **Pemanen:** `pemanen.pemanen_id` (internal) ≠ `pemanen.erp_hrmb_code` (**kunci ke ERP**: `hrmb`).
- **Unit/tahun:** `unit.kode` → `unit.erp_db` (nama database ERP) dan `unit.tahun_cluster`. **Tahun cluster diturunkan dari `tanggal_panen`.**
- **User:** `app_user.user_id` (fAI26) ≠ user ERP; kaitan opsional lewat `erp_user_oid`. `REC_CREATBY` diisi user ERP pengimpor.

---

## 9. Idempotensi lintas database (bagian paling rawan)

Database ERP dan `fai26` adalah dua database berbeda; **tidak ada transaksi terdistribusi**. Skenario berbahaya:

1. ERP membuat dokumen (commit) → panggilan `sp_fai_impor_ok` gagal (koneksi putus, timeout, server `fai26` mati).
2. Catatan tetap `BARU` di `fai26` → muncul lagi pada penarikan berikutnya → ERP membuat dokumen **kedua** untuk catatan yang sama. **Janjang ganda masuk ERP.**

**Pengaman yang diminta [Usulan]:**

- Tulis **penanda unik `[fAI26:<panen_id>]`** pada `hvd1da.KETDETAIL` (atau kolom khusus bila ERP menyediakan) saat membuat baris.
- Sebelum membuat dokumen (langkah 2g), **cari penanda itu**. Bila ditemukan, **jangan buat dokumen baru**: ambil OID `hvd1m`/`hvd1da` yang ada dan langsung panggil `sp_fai_impor_ok` (langkah 6).
- Bila `sp_fai_impor_ok` melempar 50002 (sudah diproses), perlakukan sebagai sukses.
- Langkah 6 boleh diulang tanpa batas; langkah 4 tidak boleh diulang untuk `panen_id` yang sama.
- Catat setiap penarikan di log ERP (siapa, kapan, berapa catatan, hasil), selain `fai_impor_log` di sisi fAI26.

Dan sebaliknya (dokumen dibuat tetapi catatan `DITOLAK` oleh `sp_fai_impor_gagal` yang terlanjur dipanggil lebih dulu): urutan di bagian 7 menempatkan `sp_fai_impor_gagal` hanya **sebelum** dokumen dibuat, sehingga tidak terjadi.

---

## 10. Katalog pesan penolakan

Pesan tampil di HP mandor/krani. Singkat, jelas, menyebut **apa yang harus dilengkapi dan oleh siapa**. Gunakan teks berikut (boleh disesuaikan sedikit, ≤ 500 karakter):

| Kode | Kondisi | Pesan ke HP |
|---|---|---|
| P-01 | `afdeling_erp_oid` NULL | Afdeling belum dipetakan ke ERP. Minta admin mengisi OID afdeling pada master divisi di fAI26. |
| P-02 | `mandor_hrmb_code` NULL | Kode mandor belum diisi. Minta admin mengisi kode HRMB mandor pada akun atau master pemanen. |
| P-03 | `pemanen_hrmb_code` NULL | Kode pemanen belum diisi di master. Minta admin melengkapi. |
| P-04 | blok tidak ada di ERP | Blok `<blok_kode>` tidak ditemukan di ERP (OID `<asm3_oid>`). Minta admin mencocokkan master blok dengan ERP. |
| P-05 | cluster tahun tidak ada | Data ERP untuk tahun `<tahun>` unit `<unit>` belum tersedia. Hubungi tim ERP. |
| P-06 | pemanen tidak ada di ERP | Pemanen dengan kode `<hrmb>` tidak ditemukan di ERP. Minta admin mencocokkan master pemanen. |
| P-07 | periode gaji tertutup | Periode `<bulan/tahun>` sudah ditutup. Hubungi asisten/KTU untuk membuka atau dikoreksi lewat ERP. |
| P-08 | total janjang 0 atau tidak konsisten | Jumlah janjang tidak valid (`<jjg_total>`). Periksa dan kirim ulang dari HP. |
| P-09 | gagal menyimpan dokumen ERP | Gagal menyimpan dokumen di ERP: `<alasan singkat>`. Coba lagi nanti; bila berulang hubungi tim ERP. |
| P-10 | FCM4CODE tidak ketemu | Akun biaya (upah panen) untuk tanggal `<tanggal>` tidak ditemukan di ERP. Hubungi tim ERP. |
| P-11 | master mandor tidak ada di ERP | Mandor dengan kode `<hrmb>` tidak ditemukan di ERP. |

Aturan: jangan mengirim jejak tumpukan pengecualian atau nama tabel ke HP; jangan mengulang pesan generik "gagal" tanpa penyebab; satu penolakan = satu pesan yang bisa ditindaklanjuti.

---

## 11. Skenario uji di CWE

Semua uji **hanya di CWE** (bukan produksi). Siapkan data uji dengan mengirim catatan lewat HP/API dari lingkungan uji fAI26, bukan dengan menulis tabel `fai_*` langsung.

| No | Skenario | Langkah | Hasil yang diharapkan |
|---|---|---|---|
| U-01 | Jalur bahagia | Kirim 3 catatan (3 TPH, 3 pemanen berbeda, satu mandor, satu afdeling, satu tanggal); krani menyetujui; di HVDM Tarik lalu Proses | 3 baris `hvd1da` dalam **satu** header; HP menampilkan DITERIMA untuk ketiganya; `fai_impor_log` 3 baris OK |
| U-02 | Pengelompokan header | Kirim catatan dari dua mandor berbeda pada hari yang sama | Dua header berbeda; sesuai pola HVD1 |
| U-03 | Master tidak lengkap | Kirim catatan dengan `afdeling_erp_oid` kosong | Ditolak (P-01); HP menampilkan pesan; tidak ada dokumen ERP |
| U-04 | Blok tidak ada di ERP | Kirim catatan dengan `asm3_oid` palsu | Ditolak (P-04) |
| U-05 | Cluster tidak ada | Tanggal panen di tahun tanpa cluster | Ditolak (P-05) |
| U-06 | Perbaiki lalu ulang | Setelah U-03 diperbaiki (mandor edit dan kirim ulang, atau `sp_fai_ulang_impor`) | Catatan kembali `BARU`, impor sukses, HP DITERIMA |
| U-07 | Idempotensi | Matikan koneksi ke `fai26` tepat sebelum `sp_fai_impor_ok` (atau panggil prosedur gagal-simulasi); tarik lagi | **Tidak** ada dokumen ganda; langkah 6 diulang dengan OID yang ada |
| U-08 | Impor ganda | Panggil `sp_fai_impor_ok` dua kali untuk satu `panen_id` | Panggilan kedua melempar 50002; diperlakukan sebagai sukses; tidak ada dokumen baru |
| U-09 | Catatan MENUNGGU | Kirim dari mandor, **jangan** disetujui krani | Tidak tampil di HVDM |
| U-10 | Periode tertutup | Tanggal panen pada periode ditutup | Ditolak (P-07) |
| U-11 | Dua versi FCM4CODE | Tanggal panen di antara 01-07-2026 dan 29-07-2026 | Versi sesuai keputusan A3 |
| U-12 | Janjang unripe/underripe | Catatan berisi kelas mentah | Janjang tampil di pratinjau; tidak hilang; tidak masuk kolom `JJG_*` |
| U-13 | Hak akses | Pengguna tanpa otorisasi HVDM | Tombol Proses tidak tersedia |
| U-14 | Hak login SQL | Login HVDM mencoba `UPDATE fai_panen` | Ditolak (permission denied) |

Verifikasi dua sisi setiap skenario: (a) di ERP (dokumen, nomor bukti, nilai kolom); (b) di fAI26 (web admin: Catatan Panen, filter status ERP; HP: Riwayat; basis data: `fai_impor_log`, `audit_log` aksi `IMPOR`/`IMPOR_GAGAL`).

---

## 12. Hal yang belum dirancang (sisi ERP dan lintas sistem)

1. **Penguncian periode gaji** (tutup sekitar tanggal 1 sampai 4 awal bulan): fAI26 belum mengunci data bulan lalu; HVDM sebaiknya menolak ke periode tertutup (P-07). Siapa yang boleh membuka kunci belum diputuskan.
2. **Koreksi setelah impor:** catatan `DIIMPOR` terkunci di HP dan tidak dapat diubah dari fAI26. Koreksi harus lewat ERP (dokumen koreksi) atau catatan baru. Mekanismenya belum dirancang.
3. **Sinkronisasi master dari ERP ke fAI26:** master (blok, TPH, pemanen) diisi **manual oleh admin** di web fAI26; belum ada pekerjaan sinkron otomatis. Bila nanti dibuat, jalurnya baca ERP lalu tulis ke master fAI26 lewat prosedur/API admin; **aturan emas: setiap alat yang mengubah master wajib mengisi `diubah_pada`**.
4. **Informasi TPH dari ERP** (jumlah pokok, mandor, panen terakhir): belum ada.
5. **Jembatan IPD ke ERP:** modul IPD (inspeksi panen detail) **berdiri sendiri** di fAI26; TCode ERP-nya belum ditentukan (pertanyaan 22-9). Data sudah tersimpan terstruktur (`asm3_oid`, `pemanen_hrmb_code`, kode HRMB mandor), sehingga jembatan serupa dapat dibuat.
6. **Persetujuan lintas HP:** persetujuan krani berjalan lewat server (dua HP berbeda); mekanisme notifikasi dorong belum ada.

---

## 13. Rilis dan definisi selesai

**Urutan rilis [Usulan]:**

1. E-01 dan E-02 dulu (koneksi dan pencocokan master). Tanpa ini tidak ada impor yang sah.
2. Bangun E-05 sampai E-10 di lingkungan pengembangan; lolos U-01 sampai U-14 di CWE.
3. Uji lapangan terbatas: satu unit, satu afdeling, beberapa mandor, beberapa hari; bandingkan angka ERP dengan catatan kertas.
4. Publikasi lewat DevAgro Hub untuk satu unit; pantau `fai_impor_log` setiap hari.
5. Perluas unit.

**Selesai bila:**

- U-01 sampai U-14 lulus di CWE dengan bukti tertulis.
- Tidak ada dokumen ganda pada pengujian pemutusan koneksi (U-07, U-08).
- Semua nilai sementara `erp_oid` dan `erp_hrmb_code` di fAI26 sudah diganti nilai asli.
- Keputusan B6, B7, B17, B18, A3, A6 tertulis.
- Login SQL HVDM hanya anggota `fai26_hvdm`.
- Dokumen ini diperbarui dengan hasil nyata (bagian "Catatan hasil" di bawah diisi).

### Catatan hasil (diisi saat pengerjaan)

| Tanggal | Paket | Hasil | Oleh | Catatan |
|---|---|---|---|---|
| | | | | |

---

## 14. Larangan dan jebakan (ringkas)

1. **Jangan** `UPDATE`/`INSERT`/`DELETE` langsung ke tabel `fai_*`; pakai prosedur. Bila terpaksa mengubah status, **wajib mengisi `diubah_pada`**, menulis `fai_impor_log` dan `audit_log`.
2. **Jangan** memakai `blok_id`, `pemanen_id`, atau `user_id` internal fAI26 sebagai kunci ERP.
3. **Jangan** menjumlah `jml_ai`; yang sah adalah `jml_final`.
4. **Jangan** mengasumsikan setiap kelas punya kolom ERP (`jjg_tanpa_kolom`).
5. **Jangan** mengubah tanda tangan prosedur `sp_fai_*` atau menambah hak tulis ke role `fai26_hvdm`.
6. **Jangan** menghapus baris di `fai26`; hapus lunak saja. Catatan `DIIMPOR` tidak boleh diubah.
7. **Jangan** menganggap `rotasi` atau `kondisi_cuaca` NULL sebagai kesalahan.
8. **Jangan** menyalin kredensial database ke dokumen, kode, atau pesan.
9. **Jangan** menguji di produksi.
10. **Jangan** menebak pencocokan pemanen/blok; tandai ragu dan tanyakan.
11. Skrip migrasi dijalankan dengan `sqlcmd -I`; view dan prosedur memakai `CREATE OR ALTER` (SQL Server 2016 SP1 ke atas).

---

## 15. Contoh T-SQL (sisi ERP membaca dan melapor)

```sql
-- 1) Bahan: catatan siap diimpor untuk satu unit dan rentang tanggal
SELECT *
FROM fai26.dbo.v_fai_panen_siap_impor
WHERE unit_kode = @unit AND tanggal_panen BETWEEN @dari AND @sampai
ORDER BY tanggal_panen, diterima_server;

-- 2) Tandai dibaca (opsional)
EXEC fai26.dbo.sp_fai_tandai_dibaca @panen_id = 'AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA';

-- 3a) Berhasil (setelah dokumen ERP dibuat; OID hasil insert):
EXEC fai26.dbo.sp_fai_impor_ok
     @panen_id = 'AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA', @hvd1m_oid = 700001, @hvd1da_oid = 800001, @diimpor_oleh = NULL;

-- 3b) Ditolak (pesan wajib, tampil di HP):
EXEC fai26.dbo.sp_fai_impor_gagal
     @panen_id = 'AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA', @pesan = N'Blok tidak ditemukan di ERP', @diimpor_oleh = NULL;

-- 4) Setelah data ERP dibenahi: kembalikan ke BARU
EXEC fai26.dbo.sp_fai_ulang_impor @panen_id = 'AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA', @oleh = NULL;
```

Pemeriksaan hasil dari sisi fAI26 (untuk DBA/uji; membaca saja):

```sql
SELECT id, status_erp, diubah_pada FROM fai26.dbo.fai_panen WHERE id = @panen_id;
SELECT * FROM fai26.dbo.fai_impor_log WHERE panen_id = @panen_id ORDER BY id;
SELECT aksi, nilai_lama, nilai_baru, waktu_server FROM fai26.dbo.audit_log WHERE tabel = 'fai_panen' AND baris_id = CONVERT(NVARCHAR(50), @panen_id);
```

Pencarian master di database ERP (**membaca saja; [Dugaan] nama tabel dan kolom, cocokkan dengan ERP nyata**):

```sql
-- OID blok menurut kode (untuk mengisi lampiran/cocokkan_blok.csv)
SELECT OID, CODE, NAME_ID FROM <database_cluster>.dbo.asm3 WHERE CODE IN ('A1','A1A', ...);
-- Akun biaya upah panen dan masa berlakunya
SELECT OID, <kolom_kode>, <kolom_berlaku_dari>, <kolom_berlaku_sampai> FROM sap365pub_kp.dbo.fcm4 WHERE <kolom_nama> LIKE '%Upah Panen%';
-- Pola header HVD1 (untuk memahami pengelompokan)
SELECT TOP 50 OID, NOBUKTI, TGLBUKTI, KETMASTER FROM <database_cluster>.dbo.hvd1m ORDER BY OID DESC;
```

---

## 16. Daftar acuan di dalam folder FINAL

| Butuh | Lihat |
|---|---|
| Struktur tabel, view, prosedur, hak, kode temuan | `02_SKEMA_DATABASE_SERVER.md` |
| Kontrak API, isi POST yang menjadi baris `fai_*` | `04_API_REFERENSI.md`, `lampiran/api/API.md` |
| Alur dan aturan bisnis | `07_ALUR_BISNIS_DAN_ATURAN.md` |
| Cara memeriksa dan mengisi lewat peramban | `13_PANDUAN_CLAUDE_BROWSER_ERP.md` |
| Status, keputusan, belum selesai | `11_STATUS_KEPUTUSAN_DAN_BELUM_SELESAI.md` |
| Daftar pencocokan master | `lampiran/cocokkan_*.csv` |
| Catatan serah terima lama (konteks) | `lampiran/serah_terima_erp/` |
