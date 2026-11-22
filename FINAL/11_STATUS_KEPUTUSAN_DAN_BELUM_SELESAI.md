# 11. Status, Keputusan, dan yang Belum Selesai

Satu tempat untuk melihat **apa yang sudah jadi, apa yang sudah diputuskan, apa yang menunggu keputusan, dan apa yang belum dikerjakan**, per 22 November 2026. Tenggat KKP: 15 Desember 2026.

## 1. Sudah selesai dan terbukti

| Area | Isi | Bukti |
|---|---|---|
| Database `fai26` | 24 tabel, 2 view, 4 prosedur, role `fai26_hvdm`, migrasi 002–012 | `02_SKEMA_DATABASE_SERVER.md`; simulasi pemasangan dari nol |
| API 0.21.0 | 64 endpoint terdokumentasi (OpenAPI + panduan), kontrak teruji | 139 tes unit + 25 tes integrasi |
| HP Panen | Login online/offline, master, QR, pindai AI + pelacak, panorama, GPS, simpan, kirim, riwayat, edit sebelum disetujui, persetujuan krani, dashboard | Uji di HP asli; tes |
| HP IPD | Formulir tiga langkah, 11 kode temuan, dua baris, foto, loses, persetujuan asisten, riwayat, jalur GPS | Uji di HP asli; tes |
| Jembatan ke ERP (sisi fAI26) | View, prosedur, role, log impor, audit | `TestJembatanErpAlurPenuh` |
| Web admin | 15 halaman: dasbor, panen, tertunda, produktivitas, IPD, peta jalur dan peta gabungan, laporan loses, master, QR, pengguna, hak akses, perangkat, audit | 135 tes; diperiksa di peramban |
| Keamanan | JWT + rotasi, bcrypt, batas laju, hak per panggilan, audit hanya-tambah, kepemilikan token, audit pembukaan jalur, penyimpanan jalur dapat diatur | Tes + peramban |
| Penerapan | Runbook + skrip (pemeriksa, data awal, hash) dilatih di simulasi | `08_PENERAPAN_DAN_OPERASI.md` |
| Dokumentasi | API, ERD, panduan alur, serah terima ERP, runbook, FINAL | Folder ini |

## 2. Keputusan yang sudah diambil

| Soal | Hasil | Tanggal |
|---|---|---|
| Cara input | Offline-first, kirim ke `fai26` lewat API sendiri, ERP membaca database | 4–10 Nov |
| Data per pindai | 6 kelas AI, janjang, nama pemanen, brondolan kg, tanggal dan jam, TPH/blok | 4–10 Nov |
| Acuan basis data | ERD | 9 Nov |
| Foto | Satu panorama per pindai (video dengan tracking dari masukan CTO; keyframe untuk bukti) | 9–10 Nov |
| Penyimpanan HP | SQLite (Room), JSON hanya prototipe | 9 Nov |
| Platform | Pindah ke Android native Java (Room) | 10 Nov |
| TCode | HVDM | 10 Nov |
| Latih ulang model | **Tidak**; pakai dataset yang ada | 10 Nov |
| Login offline | 7 hari | 10 Nov |
| Satu TPH satu pemanen | Ya | 18 Nov |
| Mandor yang menginput | Ya; akun membawa kode HRMB mandor dan afdeling | 18 Nov |
| Persetujuan | Krani, lewat server, dari HP berbeda; mandor boleh edit selama belum disetujui | 19–20 Nov |
| Rencana Sprint 3 | Hari 105–109: dasbor tertunda, keamanan data lokasi, runbook, peta gabungan, review | 22 Nov |
| Bawaan retensi jalur GPS | **Mati** sampai ada keputusan kebijakan | 22 Nov |
| Magang | Di kebun (tulisan memakai istilah kebun) | |

## 3. Keputusan yang menunggu

### 3.1 Terkait ERP (lihat `12_ERP_YANG_PERLU_DIBUAT.md` bagian 6)

| Kode | Soal | Usulan bawaan |
|---|---|---|
| B3 | Jumlah hari rotasi | NULL/1 |
| B4 | Pemecahan kelas abnormal | Semua ke `JJG_UNKNOWN` |
| B6 | Isi `HASILHI1` | ripe + overripe (konfirmasi) |
| B7 | `harvest_employee_id` | Isi dari master pemanen bila ada; jangan tebak |
| B8 | Sumber master blok/TPH/pemanen (`asm3` di `kp_stl2025` kosong) | Dari ERP unit yang dipilih pertama |
| B9 | Catat tangkai panjang, nomor tiket, SPB | Tidak dulu |
| B10 | Siapa admin aplikasi | IT atau krani administrator |
| B17 | Bentuk identitas mandor dan afdeling di header HVD1 | Dugaan: HRMB mandor dan OID afdeling; **wajib diverifikasi** |
| B18 | `ROTASI`, `NOURUT`, `FCM4CODE`, nomor bukti | HVDM mengisi sendiri |
| A3 | `FCM4CODE` bulan peralihan Juli 2026 | Versi berlaku pada tanggal panen; bila dua berlaku, 9148 |
| A4 | Unit pertama uji | `kp_le2026` |
| A6 | Penguncian periode gaji (tutup tanggal 1–4) | HVDM menolak ke periode tertutup |

### 3.2 Terkait lapangan dan penerapan

| Kode | Butir | Keputusan |
|---|---|---|
| 22-2 | HP tanpa kabel (jaringan lokal/firewall atau reverse proxy HTTPS) | Setuju mengubah firewall? IP mana? |
| 22-3 | Server sungguhan (alamat, akses, domain HTTPS) | Siapa dan kapan |
| 22-4 | Jadwal uji lapangan (scan, GPS jalan kaki, IPD) | Hari, blok/TPH, HP |
| 22-6 | Notifikasi dorong (butuh Firebase) | Dibuat atau cukup penanda? |
| 22-7 | Peta monitoring gabungan dan histori per pengguna | Peta gabungan satu hari sudah ada; histori lintas hari belum |
| 22-8 | Lama simpan jalur GPS dan siapa melihat (usulan 90 hari) | Ptoni dan pembimbing; juga umur cadangan |
| 22-9 | TCode ERP untuk IPD | Pihak ERP |
| 22-10 | Rekam koordinat TPH mulai dari blok mana | Blok prioritas |

### 3.3 Parameter GPS panen (B12–B16) [Usulan, disetel dari uji lapangan]

Radius "di TPH" 50 m dan akurasi minimal 30 m; hanya menandai (tidak memblokir); tanpa GPS tetap boleh disimpan (`TANPA_GPS`); koordinat TPH direkam saat QR dipasang; siapa menindaklanjuti catatan bertanda lokasi belum diputuskan.

## 4. Belum dikerjakan

### 4.1 Prioritas tinggi (menghalangi pemakaian di kebun)

1. **TCode HVDM di ERP** (jalur kritis) dan pencocokan master (E-01 sampai E-15, dokumen 12).
2. **Penggantian nilai sementara** `erp_oid` blok dan `erp_hrmb_code` pemanen; pengisian `divisi.erp_oid`, `pemanen.mandor_hrmb_code`, dan kode mandor pada akun mandor.
3. **Pemasangan di server sungguhan**: migrasi 005–012, HTTPS, layanan Windows, login API berhak sempit, `004` dan `006`, ganti kata sandi akun uji, build rilis.
4. **Koneksi HP tanpa kabel**.
5. **Uji lapangan**: akurasi hitungan, panorama, GPS di bawah tajuk, baterai, thermal, jalur jalan kaki (daftar A–K di `lampiran/dokumen_proyek/DAFTAR_UJI_LAPANGAN.md`).
6. **Rekam koordinat TPH** (0 dari 2.487).
7. **Foto lapangan untuk mengevaluasi/melatih ulang model** (bila keputusan "tidak latih ulang" ditinjau).

### 4.2 Prioritas menengah

8. Notifikasi dorong persetujuan (FCM).
9. Menjalankan seluruh 159 tes instrumented di perangkat dan menjadikannya rutin.
10. Koordinat per foto IPD; galeri foto terpusat.
11. Penguncian data bulan lalu; koreksi mandor setelah catatan terkirim; peringatan kualitas saat pindai; informasi TPH dari ERP.
12. Sinkron master dari ERP ke fAI26 (sekarang manual oleh admin).
13. Cuaca (input dan master) bila ERP menyediakan.
14. Skema respons `openapi.yaml` yang lengkap (O-08).
15. Lama simpan jalur GPS: keputusan lalu aktifkan; uji hapus jalur lokal di HP.

### 4.3 Prioritas rendah

16. Kode temuan IPD dikelola dari web (O-11); Esc menutup peta besar (O-10).
17. Jembatan ERP untuk IPD (menunggu TCode).
18. Histori jalur satu petugas lintas hari; peta gabungan rentang tanggal.
19. Rapikan utang teknis: 101 teks tertanam di layout, kelas besar, cakupan tes web rendah, lint Android.

## 5. Dependensi di luar kode

| Butuh | Dari | Menghalangi |
|---|---|---|
| Akses dan jadwal ke kebun | Ptoni, pembimbing lapangan | Uji lapangan, koordinat TPH, foto model |
| Persetujuan firewall/server/HTTPS dan akses server | Ptoni, DBA, admin jaringan | Koneksi HP tanpa kabel, penerapan |
| Nilai asli OID blok, kode HRMB pemanen/mandor/afdeling | ERP (T-03 dokumen 13) | Semua impor ke ERP |
| Keputusan B-item dan A-item | Ptoni, pembimbing | HVDM |
| Tabel cluster 2026 | Tim ERP | Impor 2026 |
| Akun Firebase | Ptoni | Notifikasi dorong |
| Kebijakan data lokasi | Ptoni, pembimbing | Aktivasi penghapusan jalur |

## 6. Urutan kerja yang disarankan

1. Isi keputusan B6, B7, B17, B18, A3, A4 (dengan verifikasi T-01 sampai T-05 dokumen 13).
2. Selesaikan pencocokan master (E-02) dan muat ke fAI26.
3. Bangun HVDM (E-05–E-10) di lingkungan pengembangan; uji U-01 sampai U-14 di CWE.
4. Pasang server uji dengan runbook; sambungkan HP; uji lapangan terbatas.
5. Putuskan kebijakan data lokasi; aktifkan retensi.
6. Perluas unit dan pasang di server sungguhan.

## 7. Risiko

| Risiko | Dampak | Penanganan |
|---|---|---|
| Uji lapangan bergantung cuaca dan akses | Jadwal bergeser | Jadwalkan lebih awal; daftar uji ringkas |
| Perubahan firewall/server butuh persetujuan | Butir 3–4 tertunda | Minta persetujuan lebih dulu |
| Sisi ERP dikerjakan pihak lain/di Visual Studio | HVDM tidak dapat diselesaikan sepihak | Batas antarmuka jelas (view + prosedur); dokumen 12 |
| Model AI butuh data lapangan | Akurasi tidak membaik | Kumpulkan foto saat uji TPH |
| Sumber daya laptop terbatas | Server lokal/peramban macet | Satu alat berat sekali waktu |
| Impor ganda ke ERP bila `sp_fai_impor_ok` gagal setelah dokumen dibuat | Janjang ganda | Penanda idempotensi (dokumen 12 bagian 9) |
| Nilai master sementara terpakai | Impor salah/ditolak semua | E-02 sebelum uji nyata |
