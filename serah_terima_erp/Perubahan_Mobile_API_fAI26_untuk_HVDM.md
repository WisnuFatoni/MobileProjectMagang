# Perubahan fAI26 (Mobile, API, Database) supaya data bisa ditarik HVDM

Untuk Claude di VS Code. Tugasmu: ubah aplikasi Android, API Go, dan database `fai26` sesuai dokumen ini. Baca dulu kode yang ada (struktur proyek, model Room, handler API, migrasi SQL, view) sebelum mengubah apa pun, lalu sesuaikan perubahan dengan gaya kode yang sudah ada. Jangan menebak nama file atau kolom, cek langsung di repo.

Sisi ERP (tcode HVDM di mySAP365) dikerjakan terpisah. Jangan sentuh itu.

## 1. Latar belakang

HVDM adalah salinan tcode HVD1 di ERP. HVDM akan punya tombol "Tarik dari fAI26" yang membaca view di database `fai26`, membuat dokumen panen (header dan detail), lalu melapor balik lewat stored procedure. Mandor menginput dari HP. Data masuk ke database fAI26 saat mandor menekan Kirim, dan baru masuk ERP saat orang ERP menekan Tarik.

## 2. Aturan bisnis baru

1. Satu TPH dikerjakan satu pemanen. Satu catatan panen berisi tepat satu pemanen, dan semua janjang jadi milik pemanen itu.
2. Yang menginput adalah mandor. Akun login aplikasi adalah akun mandor, jadi pencatat otomatis mandor itu.
3. Tidak ada langkah persetujuan terpisah. Mandor menekan Kirim, data langsung siap ditarik ERP.
4. Pembagian janjang ke beberapa pemanen (`jjg_alokasi` per pemanen) dihapus.
5. Satu mandor bisa mengirim banyak catatan sehari (satu per TPH). HVDM yang mengelompokkan jadi dokumen, bukan aplikasi.

## 3. Perubahan database (`fai26`)

Buat migrasi SQL baru berurutan setelah migrasi terakhir yang ada. Jangan ubah migrasi lama.

1. Tabel akun pengguna: tambah `mandor_hrmb_code` (kode HRMB mandor di master ERP), `afdeling_kode`, dan `unit`. Kolom ini diisi admin saat membuat akun mandor.
2. `fai_panen`: tambah `mandor_hrmb_code`, `afdeling_kode`, `pemanen_hrmb_code` (satu nilai), `tph_kode`, `blok_kode`. Kalau sebagian sudah ada, pakai yang ada dan jangan duplikat.
3. `panen_id` tetap UUID yang dibuat di HP. Itu kunci idempotensi.
4. Aturan anti ganda: unique index pada kombinasi tanggal panen + unit + blok + TPH + `pemanen_hrmb_code`. Kirim ulang dengan `panen_id` yang sama harus mengembalikan sukses tanpa membuat baris baru. Kombinasi yang sama dengan `panen_id` berbeda harus ditolak dengan pesan jelas (kode HTTP 409).
5. `fai_panen_pemanen`: tabel ini tidak dipakai lagi untuk catatan baru. Jangan dihapus, tandai deprecated di komentar migrasi.
6. Status: saat Kirim sukses, status di server langsung `BARU` (siap ditarik ERP). Tidak ada status menunggu persetujuan. Pemetaan ke HP tetap: BARU atau DIBACA menjadi TERKIRIM, DIIMPOR menjadi DITERIMA, DITOLAK menjadi DITOLAK.
7. Perbarui `dbo.v_fai_panen_siap_impor` supaya mengeluarkan satu baris per catatan dengan kolom ini: `panen_id`, `tanggal_panen`, `unit`, `afdeling_kode`, `mandor_hrmb_code`, `blok_kode`, `asm3_oid` (kalau sudah dipetakan, kalau belum biarkan NULL), `tph_kode`, `pemanen_hrmb_code`, `jjg_total`, kolom grading yang ada (ripe, overripe, unripe, underripe, abnormal, empty, brondolan), `kondisi_cuaca`, `waktu_panen`, `pencatat_username`. Hanya baris berstatus BARU atau DIBACA yang muncul. `dbo.v_fai_panen_pemanen_siap_impor` boleh dibiarkan atau diubah mengeluarkan satu baris per catatan, pilih yang paling kecil risikonya.
8. Stored procedure yang ada (`sp_fai_tandai_dibaca`, `sp_fai_impor_ok`, `sp_fai_impor_gagal`, `sp_fai_ulang_impor`) jangan diubah tanda tangannya. ERP bergantung pada itu.
9. ERP tidak pernah UPDATE tabel `fai_*` langsung. Jangan tambahkan izin tulis untuk login ERP selain EXECUTE pada procedure di atas dan SELECT pada view.

## 4. Perubahan API (Go)

1. Login mengembalikan profil mandor: `username`, `mandor_hrmb_code`, `afdeling_kode`, `unit`, `nama`.
2. Endpoint master untuk HP (GET, per unit dan afdeling milik mandor yang login):
   - daftar pemanen milik mandor itu (`hrmb_code`, `nama`)
   - daftar blok dan TPH (`blok_kode`, `tph_kode`, `asm3_oid` kalau ada)
   Sumber data master adalah tabel referensi di `fai26` yang diisi admin atau job sinkronisasi dari ERP. Kalau tabelnya belum ada, buat tabel dan endpoint, dan catat di akhir tugas bahwa pengisiannya masih manual.
3. Endpoint kirim panen (POST) menerima satu catatan dengan field di bagian 5. Validasi sisi server:
   - `panen_id` UUID valid
   - `pemanen_hrmb_code` wajib, harus termasuk pemanen milik mandor yang login
   - `mandor_hrmb_code` dan `afdeling_kode` diambil dari token atau akun, bukan dari body, supaya tidak bisa dipalsukan
   - `jjg_total` bilangan bulat tidak negatif, dan jumlah kelas grading tidak boleh melebihi `jjg_total`
   - blok dan TPH harus ada di master
   - respons: 201 untuk baru, 200 untuk kirim ulang `panen_id` yang sama, 409 untuk kombinasi ganda, 422 untuk validasi gagal dengan pesan per field
4. Endpoint daftar catatan milik mandor dengan status terkini, supaya HP bisa menampilkan TERKIRIM, DITERIMA, DITOLAK beserta alasan penolakan dari ERP.
5. Hapus atau nonaktifkan endpoint dan logika persetujuan. Kalau panel admin React memakainya, sembunyikan tombolnya dan beri catatan di commit.
6. Catat ke `audit_log` untuk setiap kirim, seperti yang sudah dilakukan sekarang.

## 5. Perubahan aplikasi Android

1. Layar login memakai akun mandor. Simpan profil mandor hasil login di penyimpanan lokal.
2. Unduh master (pemanen milik mandor, blok, TPH) saat login dan tiap kali ada koneksi. Simpan di Room supaya form tetap jalan offline.
3. Form input per TPH, urutan isi:
   1. tanggal (default hari ini)
   2. blok, lalu TPH (pilih dari master, tidak boleh ketik bebas)
   3. satu pemanen (dropdown dari daftar pemanen mandor)
   4. foto dan hasil grading dari model YOLO seperti sekarang
   5. jumlah janjang total dan kelas grading, brondolan, kondisi cuaca
   Kolom mandor, afdeling, dan pencatat tidak ditampilkan sebagai input, diisi otomatis dari profil login.
4. Hapus layar atau langkah persetujuan dan pembagian janjang ke beberapa pemanen.
5. Tombol Kirim: buat `panen_id` UUID sekali saat catatan pertama disimpan, simpan di Room, dan pakai ulang untuk setiap percobaan kirim ulang. Jangan buat UUID baru saat retry.
6. Antrean offline: catatan yang belum terkirim tetap berstatus lokal, dikirim ulang otomatis saat koneksi kembali. Tanggapan 200 atau 201 berarti sukses. Tanggapan 409 ditampilkan ke mandor sebagai "TPH ini sudah dicatat untuk pemanen dan tanggal yang sama", jangan diulang otomatis.
7. Cegah ganda di sisi HP juga: sebelum menyimpan, cek di Room apakah kombinasi tanggal + blok + TPH + pemanen sudah ada, dan beri peringatan.
8. Daftar riwayat menampilkan status dari server: TERKIRIM, DITERIMA, DITOLAK. Untuk DITOLAK tampilkan alasannya, dan beri tombol edit lalu kirim ulang (memakai `panen_id` yang sama, server yang memanggil `sp_fai_ulang_impor`, atau jalur ulang yang sudah ada di kode).
9. Migrasi Room: naikkan versi database dan tulis migrasi, jangan pakai destructive migration supaya catatan offline mandor tidak hilang.

## 6. Kontrak untuk HVDM (jangan ubah tanpa memberi tahu)

HVDM mengandalkan hal berikut:

- view `dbo.v_fai_panen_siap_impor` dengan kolom di bagian 3 poin 7, satu baris per catatan
- `panen_id` unik dan stabil
- `mandor_hrmb_code`, `afdeling_kode`, `tanggal_panen` selalu terisi (HVDM memakainya sebagai kunci header)
- procedure `sp_fai_tandai_dibaca`, `sp_fai_impor_ok`, `sp_fai_impor_gagal` dengan tanda tangan yang sama

## 7. Uji yang harus lulus

1. Mandor login, mengunduh master, mengisi tiga TPH dengan tiga pemanen berbeda, menekan Kirim. Tiga baris BARU muncul di view.
2. Kirim ulang catatan yang sama (jaringan putus lalu pulih) tidak menghasilkan baris ganda.
3. Kombinasi tanggal + TPH + pemanen yang sama dengan `panen_id` berbeda ditolak (409), HP menampilkan pesan.
4. Pemanen yang bukan milik mandor ditolak (422).
5. Setelah `sp_fai_impor_ok` dipanggil manual di database uji, HP menampilkan DITERIMA. Setelah `sp_fai_impor_gagal`, HP menampilkan DITOLAK beserta alasan.
6. Mode offline: catatan tersimpan, terkirim sendiri setelah online, tanpa duplikasi.
7. Migrasi Room dari versi lama tidak menghapus data.

## 8. Yang belum diputuskan (tanyakan ke Ptoni, jangan menebak)

- Sumber master pemanen, blok, dan TPH: diisi manual admin atau disinkronkan dari ERP.
- Apakah mandor boleh mengedit catatan yang sudah TERKIRIM tapi belum DITARIK.
- Unit pertama yang dipakai uji (kp_le2026 punya 153 blok, kp_stl2025 hampir kosong).
- Pengisian `asm3_oid`: dari master di fAI26 atau dicari HVDM dari `blok_kode`.

## 9. Batasan

- Jangan deploy ke produksi. Semua uji di database lokal atau uji.
- Jangan commit rahasia (JWT secret, password database, `.env`).
- Jangan ubah tanda tangan procedure ERP.
- Setelah selesai, tulis ringkasan: file yang berubah, migrasi baru, dan hal dari bagian 8 yang masih terbuka.
