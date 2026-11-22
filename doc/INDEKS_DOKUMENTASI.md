# Indeks dokumentasi fAI26

Dibuat 22 November 2026 (hari istirahat mingguan, setelah Sprint 2). Satu tempat untuk mencari dokumen mana yang menjawab pertanyaan apa,
seberapa baru isinya, dan mana yang hanya catatan sejarah.

**Status:** **Aktual** = dirawat dan sesuai keadaan sekarang. **Rujukan** = benar pada waktunya, jarang berubah. **Sejarah** = catatan rancangan atau
pengerjaan; jangan dipakai sebagai keadaan terkini.

## 1. Mulai dari sini

| Pertanyaan | Buka | Status |
|---|---|---|
| Sekarang sudah sampai mana, dan apa berikutnya? | [`progres/PROGRES.md`](../progres/PROGRES.md) (ringkasan posisi dan langkah berikutnya di bagian atas) | Aktual |
| Apa yang menunggu keputusan Ptoni? | [`progres/PERTANYAAN.md`](../progres/PERTANYAAN.md) (bagian terakhir: Tambahan 22 Nov) | Aktual |
| Apa yang harus diuji di lapangan? | [`progres/DAFTAR_UJI_LAPANGAN.md`](../progres/DAFTAR_UJI_LAPANGAN.md) (bagian A sampai K) | Aktual |
| Bagaimana cara memakai API? | [`fai26-api/docs/API.md`](../fai26-api/docs/API.md) dan [`openapi.yaml`](../fai26-api/docs/openapi.yaml) | Aktual (versi 0.20.0) |

## 2. Dokumen per kelompok

### Panduan dan serah terima

| Dokumen | Isi | Status |
|---|---|---|
| [`doc/fAI26_Panduan_Alur_Lengkap.md`](fAI26_Panduan_Alur_Lengkap.md) | Alur data, rancangan, dan jadwal dari awal; bab 17 dan 18 menambah IPD, jalur GPS, dan persetujuan | Rujukan (bab 1 sampai 16 adalah rancangan awal) |
| [`serah_terima_erp/fAI26_PANDUAN_LENGKAP.md`](../serah_terima_erp/fAI26_PANDUAN_LENGKAP.md) | Panduan serah terima untuk sisi ERP dan pemelihara; bab 21 memuat IPD dan jalur GPS | Aktual |
| [`serah_terima_erp/Perubahan_Mobile_API_fAI26_untuk_HVDM.md`](../serah_terima_erp/Perubahan_Mobile_API_fAI26_untuk_HVDM.md) | Perubahan yang berdampak ke TCode HVDM | Rujukan |
| [`serah_terima_erp/Catatan_HVDM_Kekurangan_Aplikasi_fAI26.md`](../serah_terima_erp/Catatan_HVDM_Kekurangan_Aplikasi_fAI26.md) | Kekurangan aplikasi yang perlu diketahui sisi ERP | Rujukan |

### Perencanaan dan progres (`progres/`)

| Dokumen | Isi | Status |
|---|---|---|
| `PROGRES.md` | Checklist per fase, riwayat pengerjaan, keputusan, ringkasan posisi | Aktual |
| `PERTANYAAN.md` | Jawaban Ptoni dan ERP, butir terbuka, tambahan 22 Nov | Aktual |
| `DAFTAR_UJI_LAPANGAN.md` | Daftar uji kondisi nyata (scan, panorama, GPS, sinkron, IPD, persetujuan, koneksi) | Aktual |
| `LANGKAH.md` | Langkah rinci tiap fase | Sejarah (ditulis sebelum banyak fitur dibuat) |
| `CATATAN_PENGERJAAN.md` | Catatan pengerjaan mandiri Ptoni 12 sampai 17 Nov | Sejarah |
| `RENCANA_IPD.md` | Rancangan awal fitur IPD (sekarang selesai dibangun) | Sejarah |
| `SPEK_QR_TPH.md` | Spesifikasi isi dan ukuran QR TPH | Rujukan |

### Basis data (`erd/`)

| Dokumen | Isi | Status |
|---|---|---|
| `fAI26_ERD_Database_Mobile.md` | Penjelasan skema; bab 13 memuat IPD dan jalur GPS | Aktual |
| `fAI26_mobile_sqlite.sql` | Skema SQLite HP (sampai tabel `ipd_jejak`, Room v7) | Aktual |
| `fAI26_server_sqlserver.sql` | Skema SQL Server (sampai `fai_ipd_jejak`, migrasi 012) | Aktual |
| `fAI26_ERD_mobile.mmd`, `fAI26_ERD_server_akses.mmd`, `fAI26_ERD_server_erp.mmd` | Diagram ER (mermaid) | Aktual |

### Diagram alur (`flow/`, mermaid)

| Berkas | Isi |
|---|---|
| `fAI26_H90_1_arsitektur.mmd` sampai `fAI26_H90_4_alur_pindai_tph.mmd` | Arsitektur, modul ERP, komunikasi, alur pindai TPH (Hari 90) |
| `fAI26_IPD_1_alur_inspeksi.mmd`, `fAI26_IPD_2_navigasi_hp.mmd` | Alur inspeksi IPD dan navigasi HP |
| `fAI26_GPS_1_jalur_inspeksi.mmd` | Perekaman, pengiriman, dan tampilan jalur GPS (baru 22 Nov) |
| `fAI26_PERSETUJUAN_1_alur.mmd` | Alur persetujuan panen dan IPD beserta status (baru 22 Nov) |

Diagram baru ditulis dengan sintaks mermaid standar tetapi belum dirender; bila ada galat tampilan, periksa di editor mermaid.

### API dan web

| Dokumen | Isi | Status |
|---|---|---|
| `fai26-api/README.md` | Menjalankan, konfigurasi, migrasi, tes | Aktual |
| `fai26-api/docs/API.md` | Dokumentasi tertulis semua endpoint dan riwayat perubahan | Aktual |
| `fai26-api/docs/openapi.yaml` | Spesifikasi mesin (beberapa respons masih objek bebas) | Aktual |
| `fai26-api/docs/DEPLOY.md` | Pemasangan di server sungguhan (layanan, reverse proxy, HTTPS) | Aktual, belum dijalankan |
| `fai26-web/README.md` | Menjalankan dan membangun web admin; halaman dan fitur | Aktual |

### Tugas harian (`TUGAS/`, tidak masuk git)

Dokumen Word hari 91 sampai 103 (dashboard, transaksi panen, premi, review Sprint 1, mobile inspeksi, Vision AI, GPS tracking,
dokumentasi API, mobile approval, review Sprint 2, istirahat mingguan). Beberapa berkas sudah diganti nama oleh Ptoni; jangan ditimpa atau dibuat ulang.

## 3. Aturan merawat dokumentasi

1. **Setiap endpoint atau perubahan bentuk data** wajib memperbarui `openapi.yaml`, `API.md`, dan riwayat perubahannya. Tes `TestSemuaRuteTerdokumentasi` gagal bila lupa.
2. **Setiap perubahan skema** memperbarui `erd/` (SQL dan mermaid) dan bab 13 `fAI26_ERD_Database_Mobile.md`, dan menambah migrasi di `fai26-api/migrations/`.
3. **Setelah setiap fitur selesai:** perbarui `progres/PROGRES.md` (entri di Fase 6 dan baris tanggal teratas) dan, bila ada keputusan baru, `PERTANYAAN.md`.
4. **Fitur yang butuh uji kondisi nyata** ditambahkan ke `DAFTAR_UJI_LAPANGAN.md`.
5. **Dokumen yang sudah tidak berlaku** diberi tanda **Sejarah** di indeks ini dan di kepala berkasnya, bukan dihapus.
6. Contoh isi pada dokumentasi tidak boleh memuat password atau data pribadi asli.
