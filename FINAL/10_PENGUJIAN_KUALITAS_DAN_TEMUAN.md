# 10. Pengujian, Kualitas, dan Daftar Temuan

## 1. Ringkasan pengujian (22 Nov 2026)

| Lapisan | Jumlah | Jenis | Status |
|---|---|---|---|
| API (Go) unit dan dokumentasi | 139 fungsi tes (34 berkas) | `go test ./internal/...`, termasuk `TestSemuaRuteTerdokumentasi` dan `TestVersiDokumenSama` | Lulus (dijalankan 22 Nov) |
| API integrasi | **25 tes** | `go test ./test/integration` pada database `fai26_tes` | Lulus |
| Web | **135 tes** (26 berkas) | vitest: hitungan murni dan komponen; `tsc`, `eslint` | Lulus dan bersih |
| HP JVM | **139 tes** | JUnit (`gradlew testDebugUnitTest`) | Lulus (terakhir dijalankan 22 Nov, hari 106) |
| HP instrumented | **159 tes** | Room, sinkron, login, dsb. di perangkat | **Dikompilasi, belum dijalankan seluruhnya di ponsel** |
| Pemeriksaan statis | | `go vet`, `gofmt`, `tsc`, `eslint` | Bersih |
| Pemeriksaan di peramban/HP | | Tangkapan layar dan uji manual di HP asli (SM-A528B) dan Chrome | Dilakukan tiap fitur; bukti di logbook |
| **Uji lapangan** (TPH, GPS jalan kaki, baterai, koneksi) | | Daftar di `lampiran/dokumen_proyek/DAFTAR_UJI_LAPANGAN.md` | **Belum dilakukan** |

Catatan: jumlah tes adalah **hitungan fungsi/anotasi tes pada kode**, bukan jumlah yang dijalankan pada satu putaran.

### 1.1 Tes integrasi API (25)

Setiap tes menyiapkan akun contoh dan master pada `fai26_tes` dan **menghapus isi tabel** `fai_*`, `auth_sesi`, `audit_log`, `device` (karena itu menolak berjalan di database yang namanya tidak berakhiran `_tes`).

`TestAdminHakAkses`, `TestAdminMasterAlurLengkap`, `TestAdminPengguna`, `TestAdminPerangkat`, `TestAdminCatatanFotoRingkasanDanAudit`, `TestLoginDanSesi`, `TestKunciAkunDanPerangkatNonaktif`, `TestTerimaPanenIdempotenDanValidasi`, `TestTanpaPermissionDitolak`, `TestMediaStatusDanAudit`, `TestMasterHakRole`, `TestMasterUnit`, `TestFotoProfilAlurPenuh`, `TestGantiPasswordSendiri`, `TestHakAksesPengelolaan`, `TestHakAksesBuatDanHapusRole`, `TestHvdmSatuPemananDanMandorAfdeling`, `TestIpdAlurPenuh` (termasuk audit `LIHAT_JALUR`, pembersihan jalur lama), `TestJembatanErpAlurPenuh` (HP kirim → ERP tolak dengan alasan → HP melihat alasan → ulang → impor OK → DITERIMA), `TestBatasLajuLoginDanUmum`, `TestMandorMenginputAlurPenuh`, `TestPersetujuanLewatServer`, `TestProduktivitasPemanen`, `TestRingkasanPanenDashboard`, `TestTphRekamKoordinat`.

Aturan penting: tes integrasi dijalankan dengan `FAI26_TEST_DSN` berbentuk `key=value` (`server=...;port=...;user id=...;password=...;database=fai26_tes;encrypt=disable;TrustServerCertificate=true`); bentuk URL gagal pada kata sandi berkarakter khusus dan tes **dilewati diam-diam** (pernah terjadi: semua tes SKIPPED sampai bentuknya diperbaiki).

## 2. Metrik ukuran: tiga sprint

Pengukuran memakai berkas yang dilacak git (`git ls-files`); jumlah tes adalah hitungan fungsi/anotasi.

| Ukuran | Akhir Sprint 1 (hari 96) | Akhir Sprint 2 (hari 102) | Akhir Sprint 3 (hari 109) |
|---|---|---|---|
| Kode produksi HP (Java) | 11.659 baris | 19.839 (122 berkas) | **19.943 (123 berkas)** |
| Kode produksi API (Go) | 5.735 | 9.003 (60) | **9.168 (62)** |
| Kode produksi web (TS/TSX) | 3.339 | 6.206 (70) | **7.022 (75)** |
| Total kode produksi | 20.733 (193) | 35.048 (252) | **36.133 (260)** |
| Baris tes | 10.080 | 14.336 | **14.956** (HP 6.656; API 6.599; web 1.701) |
| Jumlah tes | 435 | 532 (Go 136, JVM 134, instr. 157, web 105) | **572** (Go 139, JVM 139, instr. 159, web 135) |
| Versi API | 0.13.0 | 0.20.0 (64 endpoint) | **0.21.0 (64 endpoint)** |
| Migrasi | sampai 008 | sampai 012 | sampai 012 |

Pertumbuhan Sprint 2 ≈ 14,3 ribu baris (membangun modul IPD, GPS, persetujuan); Sprint 3 ≈ 1,1 ribu baris dan +40 tes (memperkuat, mengamankan, memantau).

## 3. Daftar bug yang sudah diperbaiki

Tingkat: **Tinggi** (data salah, keamanan, fitur utama tidak berfungsi), **Sedang** (fitur terganggu dengan jalan keluar), **Rendah** (kosmetik/kasus langka).

| ID | Modul | Temuan | Penyebab dan perbaikan | Tingkat |
|---|---|---|---|---|
| B-01 | API, HP | Dashboard krani selalu 0 | Ringkasan hanya menghitung catatan yang diinput akun itu; kini lingkup UNIT untuk pemegang `panen.setujui`/`panen.koreksi` | Tinggi |
| B-02 | HP | Tombol Putar foto membawa kembali ke Beranda | Tombol memutar orientasi aplikasi sehingga layar dibuat ulang | Sedang |
| B-03 | HP | Kolom pemanen tidak bisa diketik langsung | Teks asli "Pilih pemanen..." menempel di depan ketikan | Sedang |
| B-04 | Web | Menu "Laporan Loses IPD" tidak menyala | Penentu menu aktif hanya melihat segmen alamat pertama | Rendah |
| B-05 | Web | Detail catatan panen berantakan | Jumlah kolom mengikuti lebar layar | Sedang |
| B-06 | Web | Ekspor berantakan, tanpa pemilihan periode | Diganti satu lembar per tanggal dan dialog periode | Sedang |
| B-07 | HP | Kepala hijau Home dan Panen berbeda ukuran | Disatukan memakai layout bersama | Rendah |
| B-08 | HP | Pelacak panorama gagal mengikuti sisiran cepat | Pencarian geseran terlalu sempit; pelebaran sederhana lolos pencocokan salah (tes keselamatan gagal); diganti pencarian dua jendela | Sedang |
| B-09 | API | `openapi.yaml` rusak | Titik dua dalam deskripsi YAML; kini dikutip dan divalidasi | Sedang |
| B-10 | Web | Galat kompilasi karena dua berkas hanya beda huruf besar | `FotoProfil.tsx` vs `fotoProfil.ts` bertabrakan di Windows; diganti nama | Rendah |
| B-11 | Tes | Pembersih tes integrasi tidak menghapus tabel jalur | Kunci asing dari `fai_ipd_jejak` menghalangi penghapusan inspeksi; pembersih menghapus jalur lebih dulu | Sedang |
| B-12 | Server | Peta jalur tampil abu-abu | CSP hanya mengizinkan gambar dari alamat sendiri sehingga ubin OSM diblokir; kini hanya host ubin yang diizinkan | Tinggi |
| B-13 | HP | Semua penanda pokok menumpuk di satu titik jalur | Salin draf menyetel ulang waktu pokok ke saat simpan; kini waktu dipertahankan | Tinggi |
| B-14 | HP | Catatan panen milik akun lain berubah DITOLAK | Asisten (hak koreksi tanpa hak kirim) ikut mengirim antrean orang lain, server 403; kini hanya pemegang `panen.kirim` yang mengirim | Tinggi |
| B-15 | HP, Keamanan | Login offline sebagai pengguna lain tidak mengganti token online (dulu O-01) | Token tidak dikaitkan dengan akun; kini akun aktif dicatat dan token milik akun lain dibuang | Tinggi |
| B-16 | HP | Setujui panen tanpa konfirmasi (dulu O-06) | Dialog konfirmasi pada Setujui dan Setujui semua | Sedang |
| B-17 | API | Filter `status_erp=MENUNGGU` dijawab 422 | Nilai tidak ada di daftar yang diterima padahal catatan itu tampil; ditambahkan (API 0.20.1) | Sedang |
| B-18 | Web | Filter status Catatan Panen tidak memuat "Menunggu krani" | Tipe status tidak mencantumkan MENUNGGU | Rendah |
| B-19 | API | Baris audit `LIHAT_JALUR` menampilkan nomor akun | Nama pengguna tidak diisi; kini diisi dari tabel akun | Rendah |
| B-20 | Alat | Hash kata sandi dari PowerShell 5.1 salah (login ADMIN pertama ditolak) | PowerShell menyisipkan BOM; `hashpw` membuangnya | Tinggi |
| B-21 | Alat | Pemeriksa melaporkan GAGAL untuk migrasi 003 dan 006 yang terpasang | Akun API berhak sempit memang tidak melihat prosedur dan peran; kini PERINGATAN dengan petunjuk periksa sebagai DBA | Rendah |
| K-1..K-9 | Dokumen | Sembilan kekurangan panduan pemasangan lama | Diperbaiki pada runbook (lihat `08_PENERAPAN_DAN_OPERASI.md` bagian 4) | Sedang |

Bug nonteknis yang ditemukan saat membuat tangkapan layar: notifikasi pribadi dari aplikasi lain tertangkap di tangkapan layar HP (dihapus dan diulang).

## 4. Temuan yang masih terbuka (akhir Sprint 3)

| ID | Modul | Temuan | Keterangan | Tingkat |
|---|---|---|---|---|
| O-02 | HP | HP hanya mencapai server lewat kabel USB (`adb reverse`) | Butuh jaringan lokal atau HTTPS | Tinggi |
| O-03 | AI | Deteksi TBS pada TPH: 5 tandan hanya 1 terdeteksi | Penampang tandan tampaknya tidak ada pada data latih; butuh foto lapangan | Tinggi |
| O-04 | AI, HP | Panorama saat kamera menunduk belum terbukti | | Sedang |
| O-05 | Data | Koordinat TPH belum terisi (0 dari 2.487) | Status lokasi tak bermakna | Sedang |
| O-07 | Server | Notifikasi dorong belum ada | Sebagian dikurangi dasbor Persetujuan Tertunda | Sedang |
| O-08 | Dokumen | Banyak respons `openapi.yaml` masih objek bebas | | Rendah |
| O-09 | Data | Waktu pokok inspeksi lama salah | Penanda peta inspeksi lama tidak akurat | Rendah |
| O-10 | Web | Esc tidak menutup peta besar setelah peta diklik | | Rendah |
| O-11 | Web | Kode temuan IPD belum bisa dikelola dari web | | Rendah |
| O-12 | GPS | Kinerja GPS di kebun belum teruji | Akurasi, baterai, layar mati | Sedang |
| O-13 | HP | Hapus jalur lokal setelah DISETUJUI dan keterangan privasi di layar inspeksi belum diuji dan difoto | HP uji login krani; tes instrumented belum dijalankan | Sedang |
| O-14 | Kebijakan | Lama simpan jalur belum diputuskan | Penghapusan otomatis mati; juga umur cadangan | Sedang |
| O-15 | Penerapan | Layanan Windows, HTTPS, firewall, SQL Server kebun, pemulihan ke server lain, migrasi pada data nyata belum dicoba | Butuh server sungguhan dan persetujuan | Tinggi |
| O-16 | Web | Peta gabungan belum diuji pada beban besar dan data asli beberapa petugas | Hingga 60 jalur sekali muat | Rendah |
| O-17 | Keamanan | Audit tidak mendeteksi pola mencurigakan; akses langsung database tidak tercatat | Perlindungan ada pada hak akses server | Sedang |
| **O-18** | **ERP** | **TCode HVDM belum dibuat** | Jalur kritis; `12_ERP_YANG_PERLU_DIBUAT.md` | **Tinggi** |
| O-19 | Data | `erp_oid` blok dan `erp_hrmb_code` pemanen masih nilai sementara; `divisi.erp_oid` dan `mandor_hrmb_code` kosong | Tanpa nilai asli semua impor ditolak (E-02) | Tinggi |

## 5. Pola yang dipelajari dari bug

1. **Perbedaan lingkungan uji dan lapangan.** Banyak bug baru terlihat saat dipakai di ponsel/peramban sungguhan (B-12, B-13, B-14, B-15), bukan lewat tes otomatis. Tes harus ditambah dengan uji tangkapan layar dan uji di perangkat.
2. **Identitas pengguna.** B-14 dan B-15 sama-sama berasal dari asumsi bahwa akun yang tampil di layar adalah akun yang dipakai ke server. "Siapa yang bertindak" perlu satu sumber kebenaran: token.
3. **Konfigurasi keamanan bisa menghalangi fitur sah** (B-12): kebijakan ketat harus disertai uji tampilan.
4. **Perubahan aman dijaga tes keselamatan** (B-08).
5. **Alat bantu pun perlu diuji dari nol** (B-20, B-21).
6. **Kegagalan yang tampak sebagai masalah kata sandi dapat berasal dari lapisan lain**; periksa tiap lapisan terpisah.
7. **Bug lama muncul lagi pada fitur baru** bila fondasinya dipakai ulang tanpa diperiksa (B-17, B-18).
8. **Tes yang dilewati diam-diam menyesatkan** (tes integrasi SKIPPED karena bentuk DSN): pastikan tes benar-benar berjalan.

## 6. Cara menjalankan semua pemeriksaan

```
# API
cd fai26-api
go vet ./cmd/... ./internal/... ./test/...
go test ./internal/... -count=1
# integrasi (HANYA database uji; FAI26_TEST_DSN bentuk key=value)
go test ./test/integration/ -count=1

# Web
cd fai26-web
npx tsc -b && npx eslint src && npx vitest run

# HP
cd fAI26-Mobile
gradlew testDebugUnitTest        # JVM
gradlew --stop                   # bebaskan RAM setelah selesai
# instrumented (di perangkat): gradlew connectedDebugAndroidTest (belum dijalankan seluruhnya)
```
