# 01. Ringkasan Sistem fAI26

Dokumen ini adalah gambaran utuh sistem fAI26 per **22 November 2026** (API 0.21.0, migrasi basis data sampai 012, Room versi 7). Rincian tiap bagian ada di dokumen bernomor berikutnya; dokumen ini cukup dibaca lebih dulu untuk memahami sistem tanpa membuka kode.

## 1. Masalah dan solusi

**Masalah.** Di kebun sawit, krani atau mandor menghitung tandan buah (janjang) dan menilai kematangannya dengan mata di TPH (Tempat Pengumpulan Hasil), menulis di kertas, lalu mengetik ulang ke ERP. Prosesnya lambat, rawan salah, dan penilaian mutu berbeda antarorang. Selain itu pemeriksaan kehilangan hasil (loses) di lapangan dicatat manual dan sulit diverifikasi.

**Solusi (fAI26).** Aplikasi Android untuk dua pekerjaan lapangan, ditambah server perantara dan web admin:

1. **Panen (pencatatan dan grading TBS).** Mandor menyisir tumpukan tandan dengan kamera HP. Model AI (YOLOv8n, 6 kelas kematangan) di HP mengenali tandan dan kelasnya; pelacak mencegah tandan dihitung dua kali. Hasil, satu foto bukti panorama, dan lokasi GPS disimpan **offline** di HP lalu dikirim ke server. Krani memeriksa dan menyetujui dari HP-nya. Catatan yang disetujui ditarik ERP (TCode HVDM, **belum dibuat**) lewat view di database `fai26`.
2. **IPD (Inspeksi Panen Detail).** Inspektur (mandor atau asisten) memeriksa pokok demi pokok dan mencatat temuan berkode (A, G, S, M1 sampai M4, B1, B2, C, R). Sistem menghitung persen loses, menyimpan foto bukti dan jalur GPS inspektur, dan asisten divisi menyetujui. Laporan loses dan ekspor ada di web. IPD **berdiri sendiri** (belum terhubung ke ERP).

## 2. Arsitektur

```
 HP (Android, Java)                   Server (Go + SQL Server)                    ERP mySAP365
 +-------------------------+          +-------------------------------+           +----------------------+
 | Panen: kamera + AI      |  HTTPS   | API REST (64 endpoint)        |           | TCode HVDM           |
 | IPD: formulir pokok,    | -------> | JWT, bcrypt, batas laju       |  baca     | (BELUM DIBUAT)       |
 |   foto, jalur GPS       |          | database "fai26":             | <-------- | v_fai_panen_siap_    |
 | Room (SQLite) offline   | <------- |  master, akun, fai_panen,     |  tulis    |  impor + sp_fai_*    |
 | WorkManager sinkron     |  status  |  fai_ipd, audit_log           |  lewat    | -> hvd1m, hvd1da     |
 +-------------------------+          | folder media (foto)           | prosedur  +----------------------+
                                      | web admin (React) di alamat   |
                                      |  yang sama                    |
                                      +-------------------------------+
```

**Prinsip:** (1) HP tidak pernah menyentuh database ERP; (2) tidak ada API resmi mySAP365, ERP membaca database `fai26`; (3) tabel `fai_*` tidak ditulis ERP langsung, hanya lewat prosedur `sp_fai_*`; (4) semua waktu disimpan UTC; (5) data tidak dihapus permanen (hapus lunak); (6) `panen_id` dan `ipd_id` dibuat di HP (UUID) sehingga kirim ulang aman.

## 3. Komponen dan teknologi

| Komponen | Folder | Teknologi | Ukuran (22 Nov 2026) |
|---|---|---|---|
| Aplikasi HP | `fAI26-Mobile/` | Android native **Java** (`com.example.fai26`), minSdk 24, targetSdk 35, compileSdk 36, **Room** (SQLite, skema v7), **CameraX**, **TensorFlow Lite** (GPU delegate dengan cadangan CPU), **WorkManager**, ZXing (QR), `HttpURLConnection` + `org.json`, Material 3 | 19.943 baris produksi (123 berkas) |
| Server API | `fai26-api/` | **Go 1.26**, `net/http` ServeMux, `go-mssqldb`, JWT HS256, bcrypt, SQL Server (diuji di 2025) | 9.168 baris produksi (62 berkas) |
| Web admin | `fai26-web/` | React 18, TypeScript, Vite 6, Ant Design 5, React Router 6, TanStack Query, Leaflet, exceljs, jsPDF, vitest | 7.022 baris produksi (75 berkas) |
| Basis data | `erd/`, `fai26-api/migrations/` | SQL Server, database `fai26`; 24 tabel server, 28 tabel Room | migrasi 002 sampai 012 |
| Model AI | `best.pt`, `best.tflite` | YOLOv8n, masukan `[1,3,640,640]`, 6 kelas, dataset Roboflow `palm-fruit-ripeness-detection-f6sac` v1 (CC BY 4.0), **tidak dilatih ulang** | |
| Dokumentasi | `fai26-api/docs`, `erd`, `flow`, `doc`, `progres`, `serah_terima_erp` | Markdown, OpenAPI 3, Mermaid | |

Total kode produksi **36.133 baris (260 berkas)**; baris tes 14.956; **572 tes** (Go 139, JVM 139, instrumented HP 159, web 135).

## 4. Aktor dan peran

| Peran fAI26 | Pekerjaan | Perangkat |
|---|---|---|
| **MANDOR** | Menginput catatan panen (HP mandor); inspektur IPD | HP |
| **KRANI** | Memvalidasi dan menyetujui catatan mandor dari HP-nya (`panen.setujui`) | HP |
| **ASISTEN** | Menyetujui inspeksi IPD divisinya; memantau; memegang `impor.erp` | HP dan web |
| **ASKEP, KTU** | Hanya melihat: dasbor, catatan panen, produktivitas, IPD, laporan loses, jalur GPS | web |
| **ADMIN** | Master, pengguna, hak akses, perangkat | web |
| **AUDITOR** | Membaca audit log | web |
| Peran buatan admin | Hak diatur dari halaman Hak Akses | web/HP |

Matriks lengkap role × permission (26 permission) ada di `02_SKEMA_DATABASE_SERVER.md` bagian 6.

## 5. Alur ringkas

**Panen:** admin mengisi master (divisi, blok dengan `erp_oid`, TPH dengan QR, pemanen dengan `erp_hrmb_code`) → mandor login online sekali (master turun) → di TPH: scan QR, pilih satu pemanen, MULAI/SELESAI menyisir, koreksi hasil, Simpan → kirim (status server `MENUNGGU`) → krani menyetujui di HP-nya (`BARU`) atau menolak dengan alasan → HVDM menarik `BARU`, membuat `hvd1m`/`hvd1da`, melapor `DIIMPOR` atau `DITOLAK` → HP menampilkan Diterima atau Ditolak beserta alasan.

**IPD:** inspektur memilih tanggal, divisi, blok, pemanen, kemandoran → mencatat pokok demi pokok (jalur GPS terekam) → foto bukti → Kirim → status `MENUNGGU` → asisten divisi menyetujui (terkunci) atau menolak → laporan loses di web (hanya yang disetujui).

Rincian di `07_ALUR_BISNIS_DAN_ATURAN.md`.

## 6. Status per area (22 Nov 2026)

| Area | Status | Catatan |
|---|---|---|
| HP: Panen | Selesai, teruji di HP asli | Uji lapangan di TPH sungguhan belum dilakukan |
| HP: IPD | Selesai, teruji di HP asli | Idem |
| HP: jalur GPS IPD | Selesai | Belum diuji jalan kaki di kebun (akurasi di bawah tajuk, baterai, layar mati) |
| API (64 endpoint) | Selesai, dokumentasi 0.21.0 | |
| Web admin | Selesai | 135 tes |
| Basis data | Selesai di dev dan database uji | Migrasi 005 sampai 012 **belum di server sungguhan** |
| Persetujuan krani lewat server | Selesai | Tanpa notifikasi dorong |
| Keamanan data lokasi | Selesai (audit pembukaan, penyimpanan dapat diatur) | Lama simpan belum diputuskan (bawaan tanpa batas) |
| Penerapan server | Runbook dilatih di simulasi lokal | Server sungguhan, HTTPS, layanan Windows belum |
| **ERP (TCode HVDM)** | **Belum dibuat** | Jalur kritis; lihat `12_ERP_YANG_PERLU_DIBUAT.md` |
| Koneksi HP tanpa kabel | Belum | HP baru terhubung lewat USB (`adb reverse`) |
| Koordinat TPH | Belum terisi (0 dari 2.487) | Direkam di lapangan |
| Akurasi AI | Belum memadai pada tumpukan nyata (5 tandan terdeteksi 1) | Perlu foto lapangan; model tidak dilatih ulang (keputusan) |

Daftar lengkap keputusan dan butir terbuka: `11_STATUS_KEPUTUSAN_DAN_BELUM_SELESAI.md`.

## 7. Keputusan produk yang sudah final

| Soal | Hasil |
|---|---|
| Alur data | Offline-first di HP, kirim ke `fai26` lewat API sendiri, ERP membaca database `fai26` |
| TCode tujuan | **HVDM** |
| Pemanen per TPH | **Satu TPH satu pemanen** (18 Nov); pembagian janjang dihapus |
| Siapa menginput | **Mandor** (akun membawa kode HRMB mandor dan afdeling) |
| Persetujuan | **Krani** menyetujui lewat server dari HP-nya (19 Nov); mandor boleh mengedit selama belum disetujui |
| Satuan | Janjang per tandan; brondolan dalam kg |
| Kelas | 6 kelas model apa adanya; tidak dilatih ulang |
| Login | Akun fAI26 sendiri; login pertama wajib online; login offline 7 hari |
| Foto bukti | Satu panorama per pindai (bukan banyak foto, bukan klip video) |
| GPS | Memverifikasi pencatat dan merekam jalur inspeksi; saat ini hanya menandai, tidak memblokir |
| Basis data | ERD adalah acuan; kolom audit enam; hapus lunak |
| Magang | Di **kebun** (laporan memakai istilah kebun, bukan pabrik) |

## 8. Garis waktu pengerjaan

| Tanggal | Tonggak |
|---|---|
| 4 sampai 10 Nov 2026 | Rancangan, ERD, prototipe Flutter lalu pindah ke Android native Java; database `fai26`, API awal |
| 10 sampai 17 Nov | Login offline, master, QR, scan + tracker, panorama, GPS, dashboard, web admin, hak akses |
| 18 Nov | Satu TPH satu pemanen; mandor yang menginput; jembatan ke ERP (view, prosedur, role `fai26_hvdm`) |
| 19 Nov | Persetujuan krani lewat server; master asli (118 blok, 2.487 TPH, 61 pemanen); produktivitas pemanen |
| 20 Nov | Aturan edit sebelum disetujui; modul IPD (API 0.16 sampai 0.18) |
| 21 Nov | Foto profil, ekspor, jalur GPS inspeksi (API 0.20.0, Room v7) |
| 22 Nov | Perbaikan token login offline; dasbor Persetujuan Tertunda; keamanan data lokasi (API 0.21.0); runbook penerapan; Peta Monitoring IPD; review Sprint 3 |

Rincian per hari ada di `14_RIWAYAT_PEKERJAAN_DAN_LOGBOOK.md`.

## 9. Cara membaca dokumen FINAL

Mulai dari `00_README_INDEKS.md`. Untuk orang yang akan **membangun sisi ERP**: baca bagian 1 sampai 3 dokumen ini, lalu `12_ERP_YANG_PERLU_DIBUAT.md`, `02_SKEMA_DATABASE_SERVER.md` bagian 4 dan 5, dan `07_ALUR_BISNIS_DAN_ATURAN.md`. Untuk agen peramban: `13_PANDUAN_CLAUDE_BROWSER_ERP.md`.
