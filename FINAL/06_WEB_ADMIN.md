# 06. Web Admin Lengkap

Proyek `fai26-web/`: React 18 + TypeScript + Vite 6 + Ant Design 5 + React Router 6 + TanStack Query. Dilayani oleh API dari alamat yang sama (`FAI26_WEB_DIR` mengarah ke hasil `npm run build`, folder `dist`), sehingga tanpa CORS. Satu aplikasi satu halaman (SPA); alamat selain `/api/` diarahkan ke `index.html`.

## 1. Menu, alamat, dan hak

Menu mengikuti permission akun (menu yang pasti ditolak disembunyikan; **server tetap memeriksa hak pada setiap panggilan**). Urutan di bawah adalah urutan menu.

| Grup | Halaman | Alamat | Hak yang diminta |
|---|---|---|---|
| Laporan | Dasbor | `/` | `laporan.lihat` |
| Laporan | Catatan Panen | `/panen` | `panen.lihat` |
| Laporan | Persetujuan Tertunda | `/tertunda` | `panen.lihat` (bagian IPD: `ipd.lihat`) |
| Laporan | Produktivitas Pemanen | `/produktivitas` | `laporan.lihat` |
| IPD | Inspeksi IPD | `/ipd` | `ipd.lihat` |
| IPD | Peta Monitoring IPD | `/ipd/peta` | `ipd.jejak` |
| IPD | Laporan Loses IPD | `/ipd/laporan` | `laporan.ipd` |
| Master | Blok | `/blok` | `master.lihat` (ubah: `master.kelola`) |
| Master | TPH dan QR | `/tph` (cetak: `/tph/cetak`) | `master.lihat` |
| Master | Divisi | `/divisi` | `master.lihat` |
| Master | Pemanen | `/pemanen` | `master.lihat` |
| Administrasi | Pengguna | `/pengguna` | `user.kelola` |
| Administrasi | Hak Akses | `/hak-akses` | `role.kelola` |
| Administrasi | Perangkat | `/perangkat` | `device.kelola` |
| Administrasi | Audit Log | `/audit` | `audit.lihat` |
| (akun) | Login; ganti password; foto profil | `/login` | tanpa |

Halaman awal setelah login: halaman pertama yang boleh dibuka akun itu (Dasbor bagi yang punya `laporan.lihat`).

## 2. Fitur per halaman

**Dasbor.** Kartu ringkasan (catatan hari ini, janjang hari ini, perlu perhatian: catatan bertanda lokasi bermasalah, TPH berkoordinat), grafik janjang per hari 7 hari terakhir, status catatan di server (Baru, Dibaca ERP, Diimpor, Ditolak ERP), jumlah perangkat aktif dan pengguna aktif. Pemilih tanggal dan tombol Muat ulang. Data dari `GET /api/admin/ringkasan`.

**Catatan Panen.** Daftar dengan filter: rentang tanggal panen, status ERP, dan pencarian kode blok atau TPH. **Status ERP:** Menunggu krani, Baru, Dibaca ERP, Diimpor, Ditolak ERP. Buka baris untuk **drawer rincian**: header, hasil per kelas (AI vs akhir), pemanen, lokasi (status dan jarak), jejak impor ERP (pesan galat), dan **galeri foto bukti** dengan pembesaran. Data dari `GET /api/admin/panen` dan `/{id}`; foto dari `GET /api/admin/foto/{id}`.

**Persetujuan Tertunda (hari ke-105).** Dua tabel: catatan panen yang menunggu krani dan inspeksi IPD yang menunggu asisten, diurutkan dari yang **paling lama menunggu**. Kartu: menunggu, terlambat, mendekati batas, terlama dan rata-rata. Penanda dengan kata (bukan hanya warna): **Wajar**, **Mendekati batas** (75% dari batas), **Terlambat** (lebih dari batas). Batas wajar dapat dipilih 4, 8, 24, atau 48 jam (bawaan 8). Hitungan per asisten untuk IPD. Muat ulang tiap 1 menit dan lama menunggu bertambah tiap 30 detik. **Tanpa endpoint baru:** memakai `GET /api/admin/panen?status_erp=MENUNGGU` dan `GET /api/admin/ipd?status=MENUNGGU`. Mengurangi (tidak menggantikan) kebutuhan notifikasi dorong.

**Produktivitas Pemanen.** Ranking janjang per pemanen per hari, persen matang, brondolan, jumlah menunggu persetujuan; catatan ditolak tidak dihitung. **Tanpa premi** (tarif, basis, hari kerja milik ERP). Data dari `GET /api/admin/produktivitas?tanggal=`.

**Inspeksi IPD.** Daftar inspeksi (tanggal, pemanen, blok dan divisi, inspektur, asisten, pokok, loses, temuan lain, status) dengan filter periode, status, divisi; **drawer rincian**: kepala, ringkasan loses per kode, **Jalur inspeksi** (peta, hanya bila akun memegang `ipd.jejak` dan jalur ada), tabel semua pokok dengan temuannya, foto bukti dengan keterangan. **Ekspor** (Excel `.xlsx` dan PDF) dan **Cetak** dalam dua tingkat: **ringkas** dan **rinci** (arti kode, rumus loses, satu halaman PDF per inspeksi, foto bila dipilih; Excel rinci memuat lembar Inspeksi, Rincian pokok, Foto). Ekspor mengikuti filter dan mengambil semua baris (maksimal 500 inspeksi dan 150 foto per berkas). Ekspor memuat **ringkasan jalur** (jarak, durasi, jumlah titik), bukan koordinat.

**Peta jalur inspeksi** (`JalurPeta`): Leaflet dengan ubin OpenStreetMap; garis jalur yang dihaluskan (rata-rata bergerak 3 titik, hanya untuk gambar), titik **Mulai** dan **Selesai**, penanda tiap pokok di posisi inspektur saat mencatat (merah = loses, kuning = temuan lain, hijau = bersih; pokok tanpa waktu atau lebih dari 10 menit dari titik terdekat dilewati), jarak dan lama berjalan (dihitung server), tombol **Perbesar peta** (jendela besar). Keterangan privasi di atas peta.

**Peta Monitoring IPD (hari ke-108).** Jalur GPS **semua inspeksi pada satu hari** dalam satu peta; tiap petugas berwarna sendiri (palet 10 warna, tetap menurut urutan nama). Penyaring: **tanggal** (tidak boleh melewati hari ini), **divisi**, **petugas** (boleh lebih dari satu). Kartu: inspeksi, ada jalur, petugas, total jarak. Legenda per petugas, peta (klik garis untuk rincian), dan tabel inspeksi dengan status jalur (Ada, Tanpa jalur, Memuat, Gagal dimuat). **Tanpa endpoint baru:** daftar harian dari `GET /api/admin/ipd` lalu rincian tiap inspeksi dari `GET /api/admin/ipd/{id}`; karena itu **setiap jalur yang tampil tercatat di audit `LIHAT_JALUR`**, dan memilih petugas membatasi jalur yang dibuka. Batas **60 jalur** sekali muat; cache 5 menit. Hanya satu hari (belum rentang atau riwayat lintas hari).

**Laporan Loses IPD.** Per kode temuan, per kelompok (buah, brondolan, pelepah), pemanen, blok, divisi, tanggal; hanya inspeksi `DISETUJUI`. Batang loses berwarna (hijau di bawah 15%, jingga 15 sampai 30%, merah 30% ke atas) disertai angka. Ekspor Excel dan PDF.

**Blok, Divisi, Pemanen.** Tabel master dengan tambah/ubah/hapus lunak. Blok: kode (tanpa `|`), `erp_oid`, divisi, luas, tahun tanam, SPH. Divisi: `erp_oid` (OID afdeling di ERP), dan **asisten penyetuju IPD** (dipilih dari akun ASISTEN aktif; saat diganti, inspeksi `MENUNGGU` divisi itu pindah). Pemanen: `erp_hrmb_code`, `mandor_hrmb_code`. **Nilai `erp_oid` dan `erp_hrmb_code` yang saat ini ada adalah sementara dan harus diganti nilai asli dari ERP** (lihat dokumen 12).

**TPH dan QR.** Daftar TPH per blok; tambah satu atau **buat berurutan** (atomik, maksimal 500); `qr_kode` dibuat otomatis (`FAI26|TPH|1|<unit>|<blok>|<no>`); mengubah kode blok atau nomor TPH mengubah QR (QR lama tidak berlaku). **Cetak QR** dalam tiga ukuran (`/tph/cetak`) dengan koreksi galat H dan margin cukup. Kartu "TPH berkoordinat" menunjukkan kemajuan perekaman titik.

**Pengguna.** Buat akun (username huruf kecil `a-z 0-9 . _ -`, 3 sampai 50 karakter; kata sandi awal minimal 8 karakter), ubah nama/role/status, isi **kode HRMB mandor dan afdeling** untuk akun mandor, **reset password** (pengguna wajib menggantinya saat login berikutnya). Penghapusan lunak.

**Hak Akses.** Matriks role × permission (26 permission). Ubah hak role; **buat role baru** (kode, nama, keterangan, hak awal dapat disalin dari role lain); ubah nama; **hapus** role buatan admin yang tidak dipegang akun aktif. `ADMIN` tidak boleh kehilangan `role.kelola`. Hak yang dicentang menentukan menu web dan kotak di HP (termasuk `akses.panen`, `akses.ipd`).

**Perangkat.** Daftar HP/peramban yang pernah login; **nonaktifkan** = login ditolak dan sesi dicabut.

**Audit Log.** Hanya baca. Filter tabel, aksi, tanggal, dan cari (username, id baris, keterangan). Aksi diberi warna (BUAT, UBAH, HAPUS, LOGIN, LOGIN_GAGAL, SETUJU, TERIMA, RESET_PASSWORD, AKTIFKAN, NONAKTIFKAN, **LIHAT_JALUR**, **HAPUS_JEJAK_LAMA**). Nilai lama/baru ditampilkan sebagai JSON rapi.

**Akun.** Menu akun: foto profil dengan pemotong (geser, zoom, putar), ganti password (mode wajib setelah reset: tidak bisa ditutup), keluar. Tema terang/gelap dapat diganti.

## 3. Struktur kode

`src/features/<fitur>/` (auth, dashboard, panen, tertunda, produktivitas, ipd, peta, master, qr, pengguna, hakakses, perangkat, audit), `src/api/` (klien, tipe, endpoint), `src/components/` (AppLayout, menu, PageHeader, ErrorState, TombolEkspor, ...), `src/lib/` (izin, format, ekspor, cetak), `src/app/` (router, Halaman penjaga hak). Hitungan murni dipisahkan dari tampilan (mis. `tertunda.ts`, `peta.ts`, `jalur.ts`, `ekspor.ts`) supaya dapat diuji tanpa peramban. Peta dan pustaka berat (`exceljs`, `jspdf`, Leaflet) dimuat hanya saat dipakai (lazy).

## 4. Keamanan dan privasi di web

- **CSP** (`internal/webui/spa.go`): `img-src 'self' data: blob: https://tile.openstreetmap.org` (hanya host ubin peta yang diizinkan); `Referrer-Policy: strict-origin-when-cross-origin`. Bila web diletakkan di belakang proxy dengan CSP sendiri, host ubin harus diizinkan juga.
- Komputer yang membuka peta perlu **internet** (ubin OSM); penyedia peta melihat IP dan wilayah yang dibuka.
- Token disimpan di peramban oleh klien web; sesi dicabut saat perangkat dinonaktifkan.
- Setiap jalur GPS yang dibuka tercatat di audit; pemberitahuan privasi tampil di atas peta.

## 5. Menjalankan dan membangun

```
cd fai26-web
npm ci
npm run dev        # pengembangan (proksi ke API)
npm run build      # hasil di dist/ (disajikan API lewat FAI26_WEB_DIR)
npm test           # vitest (135 tes)
npx tsc -b         # pemeriksaan tipe
npx eslint src     # lint
```

Catatan Node 26: `localStorage` global bawaan menimpa jsdom pada tes; diperbaiki di `src/test/setup.ts`.

## 6. Pengujian

**135 tes web lulus** (26 berkas), `tsc` dan `eslint` bersih (22 Nov 2026). Cakupan: hitungan murni (tertunda, peta, jalur, ekspor, izin, format, pemotong foto, massal QR, rapikan JSON), komponen halaman (Persetujuan Tertunda, Peta Monitoring, menu, login, ganti password), dan tes ekspor berkas Excel/PDF. Pemeriksaan di peramban dilakukan untuk halaman baru (Persetujuan Tertunda, Peta Monitoring, keterangan privasi, audit).

## 7. Keterbatasan

1. Belum dicoba di layar kecil/HP.
2. Peta gabungan: satu hari saja; beban 60 jalur panjang belum diuji; data asli beberapa petugas belum ada (O-16).
3. Esc tidak menutup jendela peta besar setelah peta diklik (O-10).
4. Kode temuan IPD tidak dapat dikelola dari web (O-11).
5. Batas wajar Persetujuan Tertunda dipilih per sesi, belum disimpan per pengguna; polling, bukan notifikasi dorong.
6. Kartu "TPH berkoordinat" menampilkan format angka campur (catatan kecil lama).
