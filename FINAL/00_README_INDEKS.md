# FINAL: Dokumentasi Lengkap Sistem fAI26

Folder ini adalah **paket dokumentasi akhir** fAI26 per **22 November 2026** (API 0.21.0, migrasi basis data sampai 012, Room versi 7). Isinya mencakup seluruh yang sudah diterapkan (skema, API, HP, web, alur, keamanan, penerapan, pengujian), yang belum selesai, dan **spesifikasi sisi ERP** lengkap dengan panduan agen peramban (Claude di Chrome) untuk memeriksa dan menyiapkan ERP.

> **Sifat dokumen.** Bagian yang dihasilkan otomatis dari database dan skema (dokumen 02 dan 03) dan dari spesifikasi API (dokumen 04) **dibaca dari sumber yang berjalan**, bukan ditulis dari ingatan. Konstanta HP dibaca dari kode. Yang tidak dapat diverifikasi ditandai **[Dugaan]** atau **[belum dicoba]**. **Sumber yang berlaku** tetap berkas di repositori (`fai26-api/`, `erd/`, dst.); isi folder `lampiran/` adalah **salinan tertanggal 22 Nov 2026**.

## 1. Urutan baca

| Anda ingin | Baca |
|---|---|
| Memahami sistem secara cepat | `01_RINGKASAN_SISTEM.md` |
| **Membangun sisi ERP (TCode HVDM)** | `01` (bagian 1–3), `12_ERP_YANG_PERLU_DIBUAT.md`, `02` (bagian 4–5), `07`, lalu `lampiran/cocokkan_*.csv` |
| **Menugaskan agen peramban (Claude di Chrome) di ERP** | `15_PROMPT_UNTUK_CLAUDE_BROWSER.md` (prompt siap tempel), lalu `13_PANDUAN_CLAUDE_BROWSER_ERP.md` (baca `12` bagian 1, 3, 4, 7, 8, 10 lebih dulu) |
| Struktur basis data server | `02_SKEMA_DATABASE_SERVER.md` |
| Basis data HP | `03_DATABASE_HP_ROOM.md` |
| Kontrak API, konfigurasi server | `04_API_REFERENSI.md` |
| Aplikasi HP | `05_APLIKASI_HP.md` |
| Web admin | `06_WEB_ADMIN.md` |
| Alur kerja, status, dan aturan bisnis | `07_ALUR_BISNIS_DAN_ATURAN.md` |
| Memasang di server dan operasi | `08_PENERAPAN_DAN_OPERASI.md` |
| Keamanan dan privasi data lokasi | `09_KEAMANAN_DAN_PRIVASI.md` |
| Pengujian, metrik, daftar bug | `10_PENGUJIAN_KUALITAS_DAN_TEMUAN.md` |
| Apa yang sudah, belum, dan menunggu keputusan | `11_STATUS_KEPUTUSAN_DAN_BELUM_SELESAI.md` |
| Riwayat dan logbook harian | `14_RIWAYAT_PEKERJAAN_DAN_LOGBOOK.md` |

## 2. Daftar dokumen

| Berkas | Isi | Cara dibuat |
|---|---|---|
| `00_README_INDEKS.md` | Indeks ini | Tulisan tangan |
| `01_RINGKASAN_SISTEM.md` | Masalah, solusi, arsitektur, komponen, peran, alur, status, keputusan final, garis waktu | Tulisan tangan dari kode dan dokumen proyek |
| `02_SKEMA_DATABASE_SERVER.md` | 24 tabel (kolom, tipe, kunci, indeks, FK, CHECK), peta relasi, definisi view dan prosedur, role `fai26_hvdm`, permission, role, matriks hak, kode temuan, urutan migrasi, hitungan baris | **Dihasilkan dari katalog database `fai26_dev`** + keterangan tulisan tangan |
| `03_DATABASE_HP_ROOM.md` | 28 entitas Room, riwayat versi 1–7 | **Dihasilkan dari skema Room 7.json** |
| `04_API_REFERENSI.md` | Arsitektur API, konvensi, 64 endpoint, konfigurasi, kontrak inti, riwayat versi | Tabel dan kontrak **diambil dari `API.md`** |
| `05_APLIKASI_HP.md` | Layar, modul Panen dan IPD, AI, GPS, sinkron, keamanan HP, pengujian | Konstanta dibaca dari kode |
| `06_WEB_ADMIN.md` | Menu, hak, fitur tiap halaman, keamanan, build, tes | Dari kode web |
| `07_ALUR_BISNIS_DAN_ATURAN.md` | Alur end-to-end, diagram status, aturan validasi | Dari kode, tes, keputusan |
| `08_PENERAPAN_DAN_OPERASI.md` | Runbook, latihan simulasi, skrip, cadangan, rollback | Dari `DEPLOY.md` dan latihan 22 Nov |
| `09_KEAMANAN_DAN_PRIVASI.md` | Mekanisme keamanan, kebijakan data lokasi, batas | Dari kode dan hari ke-106 |
| `10_PENGUJIAN_KUALITAS_DAN_TEMUAN.md` | Tes, metrik tiga sprint, bug B-01..B-21, temuan terbuka O-02..O-19 | Dari review Sprint 2 dan 3 |
| `11_STATUS_KEPUTUSAN_DAN_BELUM_SELESAI.md` | Selesai, keputusan, menunggu, belum, dependensi, risiko | Dari `PROGRES.md` dan `PERTANYAAN.md` |
| **`12_ERP_YANG_PERLU_DIBUAT.md`** | **Spesifikasi sisi ERP**: paket kerja E-01..E-15, kontrak view, algoritma impor, pemetaan `hvd1m`/`hvd1da`, idempotensi, katalog pesan, skenario uji U-01..U-14, keputusan | Dari panduan serah terima ERP, catatan ERP Ptoni, skema |
| **`13_PANDUAN_CLAUDE_BROWSER_ERP.md`** | **Panduan agen peramban**: aturan keselamatan, tugas T-00..T-08, format laporan | Disusun dari dokumen 12 |
| `14_RIWAYAT_PEKERJAAN_DAN_LOGBOOK.md` | Tahap pengembangan, commit, pemetaan logbook hari 91–109 | Dari `PROGRES.md` dan git |
| **`15_PROMPT_UNTUK_CLAUDE_BROWSER.md`** | **Prompt siap tempel** (dasar + T-00..T-07) untuk agen peramban yang tidak dapat membaca folder | Ringkasan dokumen 13 |

## 3. Isi folder `lampiran/`

| Berkas/folder | Isi |
|---|---|
| `cocokkan_unit.csv`, `cocokkan_divisi.csv`, `cocokkan_blok.csv` (118 baris), `cocokkan_pemanen.csv` (61 baris) | **Daftar pencocokan master dengan ERP**; kolom `*_ASLI_*` dikosongkan untuk diisi (tugas T-03 dokumen 13). Dihasilkan dari `fai26_dev`; **nilai kunci ERP di situ masih sementara**. Berisi nama pegawai: hanya untuk pencocokan |
| `api/API.md`, `api/openapi.yaml`, `api/DEPLOY.md` | Panduan API, spesifikasi OpenAPI, runbook penerapan (salinan 22 Nov 2026) |
| `migrasi/` | `fAI26_server_sqlserver.sql` (skema dasar), `002`–`012` (migrasi), `fAI26_mobile_sqlite.sql`, dan `definisi_view_prosedur_dari_database.sql` (definisi view/prosedur sebagaimana ada di database) |
| `skrip/` | `periksa-server.ps1`, `awal_unit_admin.sql`, `env.example` |
| `diagram/` | Diagram Mermaid (arsitektur, modul ERP, komunikasi, alur pindai, IPD, persetujuan, GPS) dan ERD (server akses, server ERP, HP). **Belum dirender** menjadi gambar |
| `serah_terima_erp/` | Panduan lengkap untuk pihak ERP dan dua catatan asisten ERP (18–21 Nov). **Konteks historis, sebagian usang** (jumlah rute, versi API); yang berlaku adalah dokumen 02, 04, dan 12 |
| `dokumen_proyek/` | `PROGRES.md`, `PERTANYAAN.md`, `DAFTAR_UJI_LAPANGAN.md`, `RENCANA_IPD.md`, `SPEK_QR_TPH.md`, `fAI26_ERD_Database_Mobile.md`, `fAI26_Panduan_Alur_Lengkap.md` (sebagian usang), `INDEKS_DOKUMENTASI.md` |

## 4. Aturan yang berlaku untuk siapa pun yang memakai paket ini

1. **Tidak ada kredensial nyata di folder ini** (kata sandi database, kunci JWT, token) dan **jangan menambahkannya**. `.env` hanya ada di server dan tidak di-commit. Yang tampak seperti kata sandi di `API.md`/`openapi.yaml` (mis. `krani123`) hanyalah **contoh akun uji pada database uji**, bukan akun sungguhan.
2. Pengujian hanya di **database uji / CWE**, bukan produksi. Tes integrasi hanya di database berakhiran `_tes`.
3. Tabel `fai_*` **tidak ditulis langsung** oleh ERP; hanya lewat prosedur `sp_fai_*`.
4. Kunci ke ERP adalah `blok.erp_oid` dan `pemanen.erp_hrmb_code`, **bukan** id internal; nilai yang ada sekarang **sementara**.
5. Semua waktu di basis data UTC.
6. Laporan KKP memakai istilah **kebun**, bukan istilah pabrik.
7. Jangan menebak: bila dokumen ini dan kenyataan berbeda, **berhenti dan tanyakan ke Ptoni**.

## 5. Cakupan: apa yang ada dan apa yang tidak

**Tercakup:** seluruh skema (server dan HP), semua endpoint, semua layar HP dan web, alur dan status, aturan validasi, keamanan dan privasi, penerapan (dengan hasil simulasi), pengujian dan metrik, daftar bug dan temuan, keputusan dan butir terbuka, spesifikasi ERP, dan panduan agen peramban.

**Tidak tercakup (sengaja):** isi data nyata kebun (hanya hitungan dan daftar pencocokan master), kredensial, DDL internal ERP (tidak tersedia pada dokumen proyek: harus dibaca dari ERP), pedoman penulisan laporan KKP dan laporan KKP itu sendiri (sudah ada tersendiri), kode sumber (ada di repositori).

**Batas kebenaran dokumen:** bagian tentang ERP bersumber dari catatan dan pembacaan database ERP oleh Ptoni, bukan dari pengamatan langsung layar ERP oleh penulis. Setiap hal berlabel [Dugaan] harus dicocokkan di ERP (tugas T-01 sampai T-05 dokumen 13).

## 6. Cara memperbarui paket ini

- Dokumen 02 dan 03 dihasilkan dari katalog database dan skema Room; bila skema berubah, buat ulang dari sumbernya (jangan ubah tangan).
- Dokumen 04 mengikuti `API.md`; saat API berubah, perbarui `openapi.yaml`, `API.md`, dan riwayat versi, lalu salin ulang.
- Dokumen 12 memuat tabel "Catatan hasil" (bagian 13) untuk diisi selama pengerjaan sisi ERP.
- Catat tanggal pembaruan di bagian atas tiap dokumen yang diubah.
