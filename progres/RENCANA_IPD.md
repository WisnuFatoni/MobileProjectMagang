# Rencana fitur IPD (Inspeksi Panen Detail) dan perombakan navigasi

Status (21 Nov 2026): **Tahap 0 (git lokal) dan Tahap 1 (desain data) selesai. Menunggu Ptoni memeriksa desain** sebelum Tahap 2 (API). Foto form lapangan diabaikan atas permintaan Ptoni; desain mengikuti penjelasannya.

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

## 4. Data (desain selesai 21 Nov 2026)

Rinci di `erd/fAI26_ERD_Database_Mobile.md` bab 13. Tabel (HP / server):

- `kode_temuan` / `ipd_kode_temuan`: master 11 kode temuan.
- `ipd_header` / `fai_ipd`: satu inspeksi = satu pemanen; kepala: tanggal, blok, pemanen, inspektur, nomor ancak (opsional), nama mandor blok, nama mandor ancak; status, validasi (inspektur), persetujuan (asisten).
- `ipd_pokok` / `fai_ipd_pokok`: pokok yang diperiksa (nomor baris, nomor pokok), semuanya dicatat termasuk yang bersih.
- `ipd_temuan` / `fai_ipd_temuan`: kode temuan dan jumlah per pokok (boleh beberapa kode).
- `ipd_foto` / `fai_ipd_foto`: foto lebih dari satu, tiap foto punya keterangan dan urutan.

Status HP: DRAFT, MENUNGGU_SETUJU, DISETUJUI, DITOLAK. Status server: MENUNGGU, DISETUJUI, DITOLAK. Offline lebih dulu (Room v5 + antrean sinkron yang sama dengan panen).

Berkas yang sudah diperbarui: `erd/fAI26_ERD_mobile.mmd`, `erd/fAI26_ERD_server_erp.mmd`, `erd/fAI26_mobile_sqlite.sql` (diuji di SQLite), `erd/fAI26_server_sqlserver.sql`, `erd/fAI26_ERD_Database_Mobile.md` (bab 13), `fai26-api/migrations/009_ipd.sql` (sudah dijalankan dan diuji ulang berulang di `fai26_tes`; **belum** dijalankan di `fai26_dev`), `flow/fAI26_IPD_1_alur_inspeksi.mmd`, `flow/fAI26_IPD_2_navigasi_hp.mmd`. Belum diperbarui (tahap akhir): `doc/fAI26_Panduan_Alur_Lengkap.md`, `serah_terima_erp/`, `docs/API.md`, `docs/openapi.yaml`.

## 5. Server (API Go + SQL Server)

- Migrasi `009_ipd.sql`: tabel IPD, seed 11 kode, permission `ipd.*`, role **ASKEP** dan **KTU** (hanya lihat).
- Endpoint (usulan): `GET /api/master` ditambah kode temuan; `POST /api/ipd` (kirim satu sesi, idempoten seperti panen); `GET /api/ipd/menunggu`, `POST /api/ipd/{id}/setujui|tolak`; `GET /api/admin/ipd` (daftar); `GET /api/admin/ipd/laporan` (loses per kode, per kelompok, per pemanen, per blok, per periode).
- Permission baru (sudah ada di migrasi 009): `akses.panen`, `akses.ipd` (menentukan kotak di HP, bisa dipasang ke role mana pun), `ipd.buat`, `ipd.kirim`, `ipd.setujui`, `ipd.lihat`, `laporan.ipd`, `ipd.kelola`. Role baru ASKEP dan KTU (hanya lihat).
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

| Tahap | Isi | Status |
|---|---|---|
| 0 | Git lokal, baseline | **Selesai 21 Nov** (repo di `L:\GRADING_MAGANG`, commit awal "Titik awal sebelum fitur IPD"; `.env`, DataKEBUN, TUGAS, node_modules, build dikecualikan; tidak diunggah ke mana pun) |
| 1 | Desain data: ERD mermaid, SQL SQLite dan SQL Server, migrasi 009, alur di `flow/` | **Selesai 21 Nov, menunggu pemeriksaan Ptoni** |
| 2 | API: endpoint IPD, master kode temuan, persetujuan, laporan loses, tes, dokumentasi | Belum |
| 3 | HP: Home dua kotak, menu bertingkat, Room v5, layar IPD, Riwayat dan Persetujuan IPD, sinkron | Belum |
| 4 | Web: Daftar Inspeksi, Laporan Loses, halaman Hak Akses untuk kotak, tampilan role ASKEP dan KTU | Belum |
| 5 | Dokumentasi akhir, `PROGRES.md`, memori, daftar tes manual | Belum |

Tiap tahap diakhiri pembaruan `PROGRES.md`. Fitur Panen yang sudah ada tidak diubah perilakunya; hanya pintu masuknya yang pindah ke kotak Panen.

## 9. Pertanyaan desain yang masih terbuka (jawaban bawaan dipakai bila tidak dijawab)

1. **Nama mandor**: diketik bebas (dua isian: mandor di blok dan mandor di ancak). Bawaan: ya, diketik bebas; kode HRMB mandor opsional. Atau dipilih dari daftar mandor?
2. **Asisten sebagai inspektur**: bila yang mengirim memegang `ipd.setujui`, catatan langsung DISETUJUI. Bawaan: ya.
3. **Jumlah temuan**: tiap kode bawaan 1 per pokok, boleh diubah (misalnya 5 butir). Bawaan: ya.
4. **Batas foto** per inspeksi. Bawaan: tidak dibatasi (maksimal 10 di HP agar memori aman).
5. **Satu pemanen satu hari dua kali diinspeksi** (dua catatan untuk blok dan tanggal yang sama)? Bawaan: boleh, tidak ada anti ganda.
