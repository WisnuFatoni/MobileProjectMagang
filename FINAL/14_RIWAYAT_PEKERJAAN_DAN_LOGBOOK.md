# 14. Riwayat Pekerjaan dan Logbook Harian

Ringkasan pekerjaan dari tahap rancangan sampai 22 November 2026, dan **pemetaan laporan harian (logbook)** ke bagian sistem. Penomoran "Hari ke-N" adalah nomor tugas harian KKP; **tanggal pengerjaan nyata** diambil dari riwayat commit (repositori lokal, tidak di-push). Laporan Word ada di folder `TUGAS/` (Ptoni mengganti nama beberapa berkas; **jangan ditimpa**).

## 1. Tahap pengembangan (garis besar)

| Periode | Pekerjaan | Hasil |
|---|---|---|
| 4–10 Nov | Rancangan: ERD, alur, keputusan; prototipe pemindaian; database dan API awal | ERD v2.x, skema server, API 0.1.x |
| 10–17 Nov | Pindah ke Android native Java (Room); login offline; master; QR; pindai + pelacak; panorama; GPS; sinkron; dashboard; web admin; hak akses | API 0.2–0.9 |
| 18 Nov | Satu TPH satu pemanen; mandor yang menginput; jembatan ERP (view, prosedur, role) | API 0.10–0.11; migrasi 005–007 |
| 19 Nov | Persetujuan krani lewat server; master asli; produktivitas | API 0.12–0.13; migrasi 008 |
| 20 Nov | Aturan edit; modul IPD tahap 1–5; loses; satu kemandoran; ekspor | API 0.14–0.18; migrasi 009–010 |
| 21 Nov | Foto profil; ekspor rinci; jalur GPS inspeksi; peta diperbesar | API 0.19–0.20; migrasi 011–012; Room v7 |
| 22 Nov | O-01/O-06; dasbor tertunda; keamanan data lokasi; runbook; peta gabungan; review | API 0.20.1–0.21.0 |

## 2. Commit (repositori lokal)

Riwayat git dimulai 20 Nov (titik awal `0060531`: panen, API 0.15.0, web). Total 27 commit sampai 22 Nov (termasuk titik awal). Sprint 2 = 17 commit setelah titik awal (sampai `703b501`); Sprint 3 = 7 commit sesudahnya (`1349f99`, `8203057`, `5805692`, `41c6b23`, `960580c`, `7370d38`, `ab023fc`) ditambah dua commit catatan hari 109. Tidak pernah di-push ke repositori tim (alamat dan hak akses belum ada).

## 3. Logbook harian dan pemetaannya

| Hari | Tema | Isi nyata pada aplikasi | Bagian FINAL |
|---|---|---|---|
| 91 | Dashboard Mobile | Dashboard HP (Beranda); halaman login, tema, efek kaca menu | 05 |
| 92 | Dashboard Kebun | Dashboard web, kualitas buah, rincian kelas; seluruh halaman web terdokumentasi | 06 |
| 93 | Transaksi Panen | Alur transaksi, validasi berlapis, master ERP, integrasi ke ERP lewat jembatan | 05, 07, 12 |
| 94 | Premi Panen | **Fitur baru:** `GET /api/admin/produktivitas` (API 0.13.0) dan halaman Produktivitas Pemanen; tanpa premi (milik ERP) | 04, 06 |
| 95 | Review Sprint 1 | Evaluasi, kendala teknis, rencana Sprint 2 | 10 |
| 96 | Evaluasi Sprint 1 | Kualitas kode terukur (193 berkas, 20.733 baris, 435 tes); ide GPS dan Vision AI | 10 |
| 97 | Mobile Inspeksi Kebun | IPD (inspeksi blok); jalur GPS (inspeksi jalan); infrastruktur belum dibuat | 05, 07 |
| 98 | Vision AI Mobile | Foto bukti pindai dan IPD; GPS per catatan Panen; galeri sebagian | 05 |
| 99 | GPS Tracking | `JejakService`, jalur per inspeksi, peta jalur; peta gabungan belum (kini ada, hari 108) | 05, 06, 09 |
| 100 | Dokumentasi API | Dokumentasi API 64 endpoint dihasilkan dari OpenAPI + panduan | 04 |
| 101 | Mobile Approval | Dua alur persetujuan (krani untuk panen, asisten untuk IPD); notifikasi dorong belum ada | 07 |
| 102 | Review Sprint 2 | Evaluasi, bug B-01..B-14, O-01..O-12, 17 perbaikan Sprint 3 | 10, 11 |
| 103 | Istirahat Mingguan | Dokumentasi dirapikan: indeks, daftar uji lapangan H–K, diagram alur | 11 |
| 104 | Kickoff Sprint 3 | **O-01 (token login offline) dan O-06 (konfirmasi Setujui) diperbaiki** di HP | 05, 09, 10 |
| 105 | Dasbor Persetujuan Tertunda | Halaman web `/tertunda` tanpa endpoint baru; API 0.20.1; B-17, B-18 | 06 |
| 106 | Keamanan dan privasi data lokasi | Audit `LIHAT_JALUR`; penyimpanan jalur dapat diatur; HP hapus jalur setelah disetujui; API 0.21.0 | 09, 04 |
| 107 | Runbook penerapan server | Dilatih dari nol di simulasi; 9 kekurangan panduan diperbaiki; skrip pemeriksa, data awal, hash | 08 |
| 108 | Peta monitoring gabungan | Halaman `/ipd/peta` tanpa endpoint baru | 06 |
| 109 | Review Sprint 3 | 2 selesai, 4 sebagian, 11 belum dari 17 butir; sasaran "bawa ke kebun" belum tercapai | 10, 11 |

Catatan: untuk tugas yang brief-nya tidak sesuai aplikasi (mis. hari 92, 100, 104, 105, 109), Ptoni memilih menyesuaikan dengan yang sudah dikerjakan, bukan mengikuti brief. Laporan **hanya memuat yang sudah dibuat di aplikasi** (tanpa mockup rancangan), dengan tangkapan layar HP/web utuh, memakai istilah **kebun**.

## 4. Berkas dokumen proyek

| Berkas | Isi |
|---|---|
| `progres/PROGRES.md` | Status dan checklist per fase serta entri tiap hari (sangat rinci) |
| `progres/PERTANYAAN.md` | Jawaban Ptoni, temuan ERP, butir terbuka B-xx dan 22-x |
| `progres/DAFTAR_UJI_LAPANGAN.md` | Daftar uji A–K |
| `progres/RENCANA_IPD.md` | Rencana dan putaran IPD |
| `progres/SPEK_QR_TPH.md` | Spesifikasi cetak QR |
| `progres/CATATAN_PENGERJAAN.md`, `LANGKAH.md` | Catatan awal pengerjaan |
| `doc/fAI26_Panduan_Alur_Lengkap.md` | Alur lapangan (sebagian usang, mis. siklus status lama) |
| `doc/INDEKS_DOKUMENTASI.md` | Indeks dokumen proyek |
| `erd/` | ERD dan skema SQL (acuan basis data) |
| `flow/` | Diagram Mermaid |
| `serah_terima_erp/` | Panduan lengkap untuk pihak ERP (18–21 Nov; **sebagian usang**: jumlah rute dan versi API lebih lama) dan dua catatan dari asisten ERP |

Salinan beberapa di antaranya ada di `lampiran/`.
