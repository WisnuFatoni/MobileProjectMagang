# Rencana fitur IPD (Inspeksi Panen Detail) dan perombakan navigasi

Status: **RENCANA, belum dikerjakan.** Dibuat 20 Nov 2026 dari penjelasan Ptoni; 10 pertanyaan pertama sudah dijawab (bagian 7). Menunggu jawaban pertanyaan tentang arti isi form lapangan sebelum desain data dibuat.

## 1. Tujuan

Mengetahui **loses (kehilangan hasil)** di setiap titik panen: brondolan tidak dikutip, buah tinggal, dan temuan lain. Setelah inspeksi selesai, laporan menunjukkan dari sekian ancak yang diperiksa, berapa loses per jenis temuan, per ancak/pemanen, dan per blok.

## 2. Alur inspeksi (menurut Ptoni)

1. Inspektur memilih **ancak panen** yang diinspeksi: pilih pemanen (nama).
2. Isi **nomor baris** (baris pohon) dan **nomor pokok**.
3. Catat **kriteria temuan** pada pokok itu (boleh lebih dari satu):

| Kode | Temuan | Kelompok (usulan) |
|---|---|---|
| A | Potong buah mentah | Buah |
| G | Tangkai panjang | Buah |
| S | Buah masak tidak dipanen | Buah |
| M1 | Buah diperam | Buah |
| M2 | Buah dipanen tidak dibawa ke TPH | Buah |
| M3 | Buah matahari | Buah |
| M4 | Buah tidak disusun di TPH | Buah |
| B1 | Brondolan tak dikutip > 3 butir | Brondolan |
| B2 | Brondolan dibuang ke gawangan | Brondolan |
| C | Pelepah sengkleh tidak dipotong | Pelepah |
| R | Pelepah tidak disusun di gawangan | Pelepah |

4. Persetujuan hasil inspeksi oleh **Asisten** dan **Mandor panen yang menginspeksi** (detail peran: pertanyaan 4).
5. Laporan loses di dashboard web, dibaca oleh role baru **ASKEP** dan **KTU**.

## 3. Perombakan aplikasi HP

**Layar awal (Home)** setelah login: dua kotak fitur.
- Kotak **Panen**: membuka semua yang sudah ada sekarang (Beranda ringkasan hari ini, mulai di TPH, scan QR, Hasil, Riwayat, Persetujuan). Tidak ada fitur lama yang diubah isinya.
- Kotak **IPD**: membuka fitur baru.

**Menu Liquid Glass bertingkat:**
- Di Home: hanya **Home** dan **Akun**.
- Setelah masuk Panen: Home, **Beranda panen**, **Riwayat**, **Persetujuan** (bila berhak), Akun.
- Setelah masuk IPD: menu IPD (usulan: Mulai inspeksi, Riwayat IPD, Persetujuan IPD bila berhak, Laporan ringkas), Home, Akun.
- Kotak yang tampil mengikuti hak akses akun.

Cara membuatnya: `MainActivity` sekarang memuat satu set tab tetap. Diubah menjadi "mode" (HOME, PANEN, IPD) yang menukar daftar tab `GlassNavBar` dan fragment-nya. Fragment Panen yang ada dipakai ulang tanpa diubah. Fragment Home dan fragment IPD baru ditambahkan.

## 4. Data

Tabel baru (nama sementara, prefiks `ipd_` supaya terpisah dari panen):

- `ipd_kode_temuan`: master 11 kode di atas (kode, nama, kelompok, urut, aktif). Diisi lewat migrasi, bisa dikelola admin.
- `ipd_header`: satu sesi inspeksi (id UUID, unit, blok, tanggal, inspektur, status, penyetuju, waktu, catatan).
- `ipd_ancak`: ancak yang diinspeksi dalam satu sesi (header, pemanen, jumlah pokok diperiksa).
- `ipd_pokok`: pokok yang diperiksa (ancak, no baris, no pokok).
- `ipd_temuan`: temuan pada pokok (pokok, kode temuan, jumlah, catatan, foto opsional).

Status HP: DRAFT, MENUNGGU (menunggu persetujuan), DISETUJUI, DITOLAK, mengikuti pola panen. Offline lebih dulu: Room (skema v5, migrasi dari v4 tanpa menghapus data panen) + antrean sinkron yang sama dengan panen.

Dokumen yang ikut diperbarui: `erd/fAI26_ERD_mobile.mmd`, `erd/fAI26_ERD_server_akses.mmd`, `erd/fAI26_ERD_server_erp.mmd` (bila perlu), `fAI26_ERD_Database_Mobile.md`, `fAI26_mobile_sqlite.sql`, `fAI26_server_sqlserver.sql`, `flow/` (alur IPD baru), `doc/fAI26_Panduan_Alur_Lengkap.md`, `serah_terima_erp/fAI26_PANDUAN_LENGKAP.md`, `docs/API.md`, `docs/openapi.yaml`, `progres/PROGRES.md`.

## 5. Server (API Go + SQL Server)

- Migrasi `009_ipd.sql`: tabel IPD, seed 11 kode, permission `ipd.*`, role **ASKEP** dan **KTU** (hanya lihat).
- Endpoint (usulan): `GET /api/master` ditambah kode temuan; `POST /api/ipd` (kirim satu sesi, idempoten seperti panen); `GET /api/ipd/menunggu`, `POST /api/ipd/{id}/setujui|tolak`; `GET /api/admin/ipd` (daftar); `GET /api/admin/ipd/laporan` (loses per kode, per kelompok, per pemanen, per blok, per periode).
- Permission baru: `ipd.buat`, `ipd.kirim`, `ipd.setujui`, `ipd.lihat`, `laporan.ipd`.
- Versi API naik (0.16.0), dokumentasi API wajib diperbarui (ada tes yang memaksanya).
- Tes unit + integrasi di `fai26_tes` (bukan `fai26_dev`).

## 6. Web (React + Ant Design)

- Menu baru grup **IPD**: Daftar Inspeksi, Laporan Loses.
- Laporan: loses per kode temuan (jumlah dan persen), per kelompok (buah, brondolan, pelepah), per pemanen/ancak, per blok, filter tanggal. Grafik batang + tabel, bisa diekspor.
- Role **ASKEP** dan **KTU**: login web, hanya melihat laporan (menu lain tersembunyi sesuai hak).

## 7. Keputusan Ptoni (20 Nov 2026) dan pertanyaan yang masih terbuka

**Sudah diputuskan (jawaban atas 10 pertanyaan pertama):**
1. Pokok yang diperiksa dicatat semuanya, termasuk yang bersih (untuk menghitung persen loses).
2. Satu pokok boleh punya beberapa temuan (misalnya brondolan tidak dikutip, buah tidak dipanen, pelepah sengkleh).
3. Satu catatan = satu pemanen. Pindah ancak berarti mulai catatan baru dari awal. Ada input **nama mandor** pada blok dan ancak.
4. Yang menginput di HP adalah yang menginspeksi (biasanya mandor). Persetujuan dua tingkat: (a) **mandor** yang menginspeksi menyatakan valid saat menekan kirim (otomatis, seperti alur panen), (b) lalu terkirim ke **asisten** untuk disetujui.
5. Kotak Panen dan IPD muncul untuk semua, **diatur lewat hak akses** (dinamis, bisa diubah admin): siapa boleh membuka Panen dan siapa boleh membuka IPD.
6. **Foto per catatan lebih dari satu**, tiap foto bisa diberi keterangan.
7. Tahap ini hanya di fAI26. Nanti diintegrasikan ke ERP (TCode belum diketahui), jadi data dirancang siap diekspor (kode, tanggal, blok, pemanen, mandor tersimpan terstruktur).
8. ASKEP dan KTU boleh melihat dashboard, catatan panen, produktivitas, dan laporan IPD (tanpa mengubah).
9. Git lokal dibuat dulu. 10. Nomor baris dan pokok angka bebas.

**Dari foto form lapangan "Pemeriksaan Mutu Ancak" (Tanggal Panen, Blok, Seksi, Mandor Panen, Mandor 1; kolom per pemanen):**
- Satu pemanen punya beberapa pasang baris (contoh Haruani: baris 11 dan 10, lalu 15 dan 14). Tiap baris punya dua sisi, **Kn** dan **Kr** (dugaan: kanan dan kiri).
- Baris form = nomor pokok 1 sampai 35. Pemeriksaan berhenti di pokok tertentu (contoh 16, 18, 19, 20, 21).
- Di bawah tiap kolom ada pecahan, misalnya 19 di atas 1, 18 di atas 0, 12 di atas 2 (dugaan: jumlah pokok diperiksa di atas, jumlah temuan di bawah).
- Di bawah kolom ada tulisan "0 Butir", "10 Butir", "11 Butir" (dugaan: brondolan yang tidak dikutip, dihitung per pemanen atau per pasang baris).
- Ada tanda "-", "X", dan angka kecil (1, 2) di kotak. Artinya belum pasti.
- Tanda tangan: **Dibuat** Mandor Panen, **Disetujui** Asisten. Ini cocok dengan alur persetujuan di atas.

Pertanyaan terbuka tentang form ini ada di balasan Claude kepada Ptoni (arti tanda, Kn/Kr, pecahan, butir, kode huruf, Seksi, Mandor 1). Jawabannya menentukan bentuk tabel `ipd_*` dan layar input; sampai dijawab, bagian 4 masih sementara.

## 8. Urutan pengerjaan

| Tahap | Isi | Hasil yang bisa dicek |
|---|---|---|
| 0 | Git lokal (bila disetujui), baseline | Titik kembali |
| 1 | Desain data: ERD mermaid, SQL SQLite dan SQL Server, migrasi 009, alur di `flow/` | Dokumen dan skema, dikoreksi Ptoni sebelum kode |
| 2 | API: tabel, endpoint, permission, role ASKEP dan KTU, tes, dokumentasi | Tes lolos di `fai26_tes` |
| 3 | HP: Home dua kotak, menu bertingkat, migrasi Room v5, layar IPD (pilih ancak, input pokok dan temuan, ringkasan, kirim), Riwayat dan Persetujuan IPD, sinkron | Terpasang di HP, dicoba Ptoni |
| 4 | Web: halaman Daftar Inspeksi dan Laporan Loses, tampilan role ASKEP dan KTU | Dicoba di browser |
| 5 | Dokumentasi akhir, `PROGRES.md`, memori, daftar tes manual | Catatan lengkap |

Tiap tahap diakhiri pembaruan `PROGRES.md`. Fitur Panen yang sudah ada tidak diubah perilakunya; hanya pintu masuknya yang pindah ke kotak Panen.
