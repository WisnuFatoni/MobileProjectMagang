# Daftar uji lapangan fAI26

Dibuat 18 Nov 2026. Semua fitur di bawah sudah lolos tes otomatis; yang belum pernah dicoba adalah **kondisi nyata**
(TPH sungguhan, tajuk pohon, sinyal buruk, HP panas). Catat hasilnya di kolom kanan supaya bisa dibandingkan dan masuk laporan.

Persiapan: server jalan dan HP bisa login online sekali (master turun), QR TPH sudah dicetak dari web admin
(`/tph`, Cetak QR, ukuran Besar), dan alamat server terisi di layar Akun. Catat tanggal, TPH, dan jenis HP.

## A. Scan dan akurasi hitungan (paling penting)

| # | Uji | Cara | Catat |
|---|---|---|---|
| A1 | Hitungan vs manual | Di 5 sampai 10 TPH dengan jumlah janjang beragam (sedikit, sedang, 20+): hitung manual dulu, lalu scan | Jumlah manual, jumlah scan, selisih per TPH; apakah scan lebih sering kelebihan atau kekurangan |
| A2 | Kotak palsu | Perhatikan hasil scan: kotak pada daun, tanah, atau karung | Berapa kotak palsu per scan |
| A3 | Hitungan dobel | Sisir pelan, lalu sisir lagi bolak-balik | Apakah satu tandan terhitung dua kali; apakah tandan yang keluar layar lalu kembali terhitung ulang |
| A4 | Kecepatan sisir | Sisir pelan, sedang, cepat | Pada kecepatan apa hitungan mulai kehilangan tandan |
| A5 | Cahaya | Pagi, siang terik, teduh, mendung | Apakah akurasi turun saat gelap atau silau |
| A6 | Kelas | Bandingkan label kelas (Matang, Mengkal, dst.) dengan penilaian mandor | Berapa dari 20 tandan yang kelasnya beda |

Catatan: ambang deteksi dilonggarkan (0,17) untuk tumpukan padat; A1 dan A2 menentukan apakah itu tepat atau perlu dinaikkan lagi.

## B. Foto bukti panorama

| # | Uji | Catat |
|---|---|---|
| B1 | Sisir satu tumpukan kecil (muat satu layar): hasilnya harus satu foto utuh | Utuh atau tidak |
| B2 | Sisir tumpukan memanjang (kiri ke kanan, lalu naik): apakah tersambung tanpa sambungan jelas | Foto hasil; ada tandan terbelah atau ganda |
| B3 | Goyang tangan wajar, maju-mundur: apakah tetap sah | Hasil jatuh ke foto satu frame atau panorama |
| B4 | Nomor janjang `#1 #2` di foto cocok dengan total di layar | Cocok atau tidak |
| B5 | Zoom dan putar layar di penampil foto | Terbaca jelas |
| B6 | Ukuran file foto | KB per foto (kirim dan cek di server) |

## C. GPS dan QR

| # | Uji | Catat |
|---|---|---|
| C1 | QR cetak terbaca kamera HP di TPH (jarak, silau, kotor, basah) | Jarak terjauh yang terbaca; kertas atau stiker mana yang tahan |
| C2 | Rekam koordinat TPH pertama kali di tempat terbuka lalu di bawah tajuk | Akurasi (m) dan lama sampai titik pertama |
| C3 | Status lokasi saat menyimpan: DI_TPH, DI_LUAR, AKURASI_RENDAH, TANPA_GPS | Apakah radius 50 m dan akurasi 30 m pas, atau terlalu ketat/longgar |
| C4 | Titik TPH yang direkam di HP A muncul di web admin dan di HP B setelah sinkron | Ya atau tidak, berapa lama |

## D. Sinkronisasi dan offline

| # | Uji | Catat |
|---|---|---|
| D1 | Mode pesawat: login offline, scan, simpan, setujui mandor | Semua jalan tanpa sinyal |
| D2 | Nyalakan sinyal: catatan, foto, audit terkirim otomatis tanpa menekan tombol | Waktu sampai terkirim |
| D3 | Sinyal putus di tengah kirim | Lanjut sendiri setelah sinyal kembali; tidak ada data ganda |
| D4 | Mandor menyetujui catatan krani (login mandor di HP krani) | Alur sesuai harapan di lapangan |
| D5 | Status Ditolak dari ERP muncul di Riwayat HP dengan alasannya | (butuh sisi ERP) |
| D6 | Login offline sampai 7 hari, lalu hari ke-8 | Ditolak dengan pesan jelas |

## E. Ketahanan HP

| # | Uji | Catat |
|---|---|---|
| E1 | Sesi scan 2 sampai 3 menit berturut-turut, 5 sesi | Suhu, apakah makin lambat (ms per frame di bar atas) |
| E2 | HP dilepas dari charger, hemat baterai mati, di bawah terik | Thermal dan baterai per 10 scan |
| E3 | Baterai di bawah 15%, ruang simpan menipis | Peringatan muncul di Beranda |

## F. Akun dan hak akses

| # | Uji | Catat |
|---|---|---|
| F1 | Ganti password dari HP (online) lalu login di HP lain | HP lain diminta login ulang; password lama ditolak |
| F2 | Admin reset password dari web: pengguna wajib ganti saat login web | Dialog wajib muncul |
| F3 | Ubah hak role di web (Hak Akses), HP krani sinkron | Hak berubah di HP tanpa login ulang |
| F4 | Layar ganti password di HP | Tampilan terbaca di bawah matahari; keyboard tidak menutup tombol |

## G. Web admin

| # | Uji | Catat |
|---|---|---|
| G1 | Buka web admin di layar HP (lebar sempit) | Tabel dan menu terbaca; belum pernah dilihat di lebar HP |
| G2 | Cetak QR 3 ukuran di kertas sungguhan, margin pencetak 100% | Ukuran sesuai SPEK_QR_TPH.md |

## Setelah uji

Masukkan angka A1 sampai A6 ke bab hasil laporan (jangan menulis "akurasi 99%": mAP50 0,993 dari Colab terlalu optimis,
lihat PROGRES.md Fase 0). Bila ada bug, tulis langkah mengulangnya dan ID catatan (`panen_id`) supaya bisa dilacak di
`Catatan Panen` pada web admin dan di `audit_log`.
