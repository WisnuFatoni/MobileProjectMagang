# Catatan: Clone HVD1 menjadi HVDM dan Kekurangan Aplikasi fAI26

Disusun untuk Ptoni, 18 November 2026. Sumber: fAI26_PANDUAN_LENGKAP.md dan query baca-saja ke kp_le2026 di server CWE.

## 1. Bisa clone HVD1 jadi HVDM?

Bisa, dan itu memang rencana di panduan (bagian 2 dan 12). HVDM adalah salinan HVD1 yang sumber datanya bukan input manual krani, tapi view dari database fai26:

- `dbo.v_fai_panen_siap_impor` (data panen dan grading)
- `dbo.v_fai_panen_pemanen_siap_impor` (pembagian ke pemanen)

Hasil impor dilaporkan balik lewat procedure `sp_fai_tandai_dibaca`, `sp_fai_impor_ok`, `sp_fai_impor_gagal`. HVDM tidak boleh UPDATE tabel fai_* langsung.

Alur HVDM:

1. Tombol "Tarik dari fAI26": baca view, kelompokkan per mandor + afdeling + tanggal.
2. Buat 1 header HVDM per kelompok, 1 detail per pemanen.
3. Tandai dibaca, lalu OK atau gagal lewat procedure.

## 2. Fakta dari data HVD1 (kp_le2026)

- 99.400 header untuk 218 tanggal, jadi header bukan satu per tanggal.
- Nomor bukti berformat `urut/HVD1/MDR.PNN.AFD.7/kp_le/JUL/31`, memuat kode mandor dan afdeling.
- 97.086 header punya 1 detail, 2.312 punya 2 detail, 1 punya 3 detail.
- Hanya JJG_OVERRIPE yang terisi di kolom grading. JJG_RIPE, BUSUK, SAKIT, EMPTY, UNKNOWN, LANDAK kosong.
- ROTASI selalu 1, NOURUT 1 atau 2, FCM4CODE hanya 2 nilai (OID 889 dan 9148).

Belum kuverifikasi: cara persis krani membuat header di layar HVD1 dan isi KETMASTER. Cek sendiri di layar HVD1 sebelum menyalin logikanya.

## 3. Yang kurang di aplikasi (urut prioritas)

| No | Kekurangan | Akibat | Solusi |
|----|-----------|--------|--------|
| 1 | Payload tidak punya kode mandor dan afdeling | HVDM tidak bisa membuat header dan nomor bukti | Tambah `mandor_hrmb_code` dan `afdeling_kode` di aplikasi dan API (atau turunkan dari blok) |
| 2 | Janjang belum tentu terbagi ke pemanen | Detail HVDM butuh HRMBCODE per baris | Aplikasi wajib pilih pemanen dan validasi total `jjg_alokasi` sama dengan total janjang. Putuskan aturan default (B1) |
| 3 | Master blok tidak seragam | `asm3_oid` salah atau kosong, impor gagal | Aplikasi mengunduh daftar blok per unit dari server. Server punya peta `unit` ke `erp_db` dan `tahun_cluster`. kp_le2026 punya 153 blok, kp_stl2025 hampir kosong |
| 4 | Kolom ERP wajib yang tidak ada di aplikasi | Dokumen HVDM tidak lengkap | HVDM isi default: ROTASI=1, NOURUT=1, FCM4CODE sesuai tanggal panen, harvest_employee_id sesuai keputusan B7 |
| 5 | FCM4CODE berubah menurut tanggal | Biaya salah akun di bulan peralihan | OID 889 berlaku sampai 2026-07-29, OID 9148 mulai 2026-07-01 (tumpang tindih Juli). Putuskan aturan pilih |
| 6 | Grading 6 kelas lebih banyak dari kolom ERP | Data hilang kalau dipaksa | Tahap awal: overripe ke JJG_OVERRIPE, sisanya tetap di fai26 atau keterangan. Putuskan B4 (abnormal) dan B6 (HASILHI1 janjang atau berat) |
| 7 | Unripe, underripe, brondolan, KONDISI_CUACA tidak punya kolom | Tidak bisa masuk ERP | Biarkan di fai26 dulu. Minta kolom tambahan ke tim ERP kalau memang dibutuhkan |
| 8 | Cluster 2026 belum ada untuk beberapa unit (kp_stl) | Impor gagal | Uji di kp_le2026 dulu. Cek tabel cluster sebelum aktifkan unit lain |
| 9 | Kunci periode (period lock) | Dokumen ditolak di periode tertutup | HVDM mencatat gagal dengan pesan, lalu aplikasi menampilkan DITOLAK |
| 10 | Server belum deploy | Aplikasi belum bisa konek | HTTPS, login database untuk HVDM (hanya SELECT view dan EXECUTE procedure), DEPLOY.md, 004_audit_log_deny.sql |

## 4. Input aplikasi yang disarankan

Krani isi: tanggal, mandor, afdeling, blok (pilih dari daftar), TPH, pemanen beserta jumlah janjang masing-masing, foto, hasil grading dari model, brondolan.

HVDM isi sendiri: ROTASI, NOURUT, FCM4CODE, nomor bukti, kolom default lain.

## 5. Keputusan yang harus kamu ambil

- B1: janjang dibagi ke pemanen atau dicatat ke satu pemanen
- B4: abnormal dipecah ke BUSUK, SAKIT, UNKNOWN, atau tidak
- B6: HASILHI1 berisi janjang atau berat (data lama berisi bilangan bulat 0 sampai 923, rata-rata 10,3, yang lebih mirip janjang)
- B7: isi harvest_employee_id
- Aturan FCM4CODE di bulan Juli
- Unit mana yang aktif pertama kali

## 6. Aturan keselamatan

- Uji hanya di CWE, jangan ke produksi.
- HVDM tidak menulis ke fai_* langsung.
- Semua akses ERP lewat view dan procedure di atas.
