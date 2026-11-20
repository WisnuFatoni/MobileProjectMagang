# Spesifikasi QR TPH

Dokumen ini untuk siapa pun yang membuat QR TPH: skrip `tools/buat_qr_tph.py` sekarang, dan **web admin** nanti. Aplikasi mobile membaca QR sesuai spesifikasi ini.

Terakhir diperbarui: 10 November 2026.

## Prinsip

QR **hanya berisi kode TPH**, bukan seluruh datanya. Aplikasi mencari kode itu di master lokal HP (kolom `tph.qr_kode`), lalu blok, tahun tanam, koordinat, dan data lain diambil dari sana. Akibatnya:

- QR tetap terbaca tanpa sinyal, karena master ada di HP.
- Data TPH boleh berubah tanpa mencetak ulang QR.
- QR kecil dan mudah dipindai walau kotor atau miring.
- QR bisa difoto orang lain, jadi **jangan menaruh data rahasia** di QR. Keaslian lokasi dijaga oleh GPS (lihat PROGRES.md, verifikasi lokasi).

## Format isi

```
FAI26|TPH|1|<kode unit>|<kode blok>|<no tph>
```

Contoh: `FAI26|TPH|1|KP_STL|A12|07`

| Bagian | Isi | Aturan |
|---|---|---|
| `FAI26` | Penanda aplikasi | Persis `FAI26` (huruf besar). QR lain ditolak. |
| `TPH` | Jenis QR | Persis `TPH`. Jenis lain (misalnya kartu pemanen nanti) ditolak dengan pesan khusus. |
| `1` | Versi format | Saat ini `1`. Versi lain ditolak dengan pesan "perbarui aplikasi". |
| kode unit | Contoh `KP_STL` | Tidak boleh kosong, tidak boleh mengandung `\|`. |
| kode blok | Contoh `A12` | Tidak boleh kosong, tidak boleh mengandung `\|`. |
| no tph | Contoh `07` | Tidak boleh kosong, tidak boleh mengandung `\|`. Tulis dengan angka nol di depan secara konsisten. |

Teks di QR **harus sama persis** dengan nilai kolom `tph.qr_kode` di master. Perbandingan peka huruf besar-kecil. Spasi atau baris baru di ujung diabaikan.

Satu `qr_kode` hanya boleh dimiliki satu TPH (unik). Aplikasi menolak TPH yang `aktif = 0` atau sudah dihapus lunak.

## Cara mencetak dan memasang

1. **Level koreksi kesalahan H** (tertinggi), supaya tetap terbaca walau sebagian kotor atau tergores.
2. **Zona tenang (margin) minimal 4 modul** di sekeliling QR.
3. **Ukuran:** sisi QR minimal sekitar 12 cm (seukuran telapak tangan atau lebih). Makin besar makin mudah dipindai dari jarak 50 sampai 100 cm.
4. **Kontras tinggi:** hitam di atas putih. Jangan memakai warna pudar atau latar bermotif. Aplikasi juga bisa membaca QR terbalik (putih di atas hitam), tetapi cetakan standar lebih andal.
5. **Bahan tahan cuaca:** stiker laminasi anti-UV atau plat. Kertas biasa cepat rusak oleh hujan dan lumpur. Jangan memakai bahan yang sangat mengkilap karena memantulkan cahaya.
6. **Teks manusia di bawah QR:** tulis juga `Blok A12 / TPH 07` supaya bisa dibaca orang dan dipakai bila QR rusak (krani memilih TPH manual).
7. **Pemasangan:** di tiang atau papan setinggi dada, menghadap ke arah krani berdiri, tidak terkena sinar matahari langsung dari belakang.

## Kebutuhan web admin (pencatatan)

Web admin nanti perlu:

- Mengelola master TPH (unit, blok, nomor TPH, koordinat, aktif atau tidak) di database `fai26`.
- **Membuat `qr_kode` otomatis** dari unit, blok, dan nomor TPH dengan format di atas, dan menjamin keunikannya.
- Membuat QR (PNG atau PDF) satu per TPH dan lembar cetak per blok, dengan teks manusia di bawahnya. Referensi implementasi: `tools/buat_qr_tph.py`.
- **Mencetak ulang tanpa mengubah isi.** Bila QR hilang, cetak ulang dengan teks yang sama. Mengubah blok atau nomor TPH berarti `qr_kode` berubah dan QR lama tidak berlaku.
- Mengisi koordinat TPH (`lat`, `lon`), dipakai untuk verifikasi lokasi GPS. Bisa diisi dari HP saat QR dipasang.
- Master dari web admin sampai ke HP lewat sinkronisasi master (Fase 4), sehingga TPH baru baru bisa discan setelah HP memperbarui master.

## Perilaku aplikasi

| Keadaan | Pesan ke krani |
|---|---|
| QR bukan milik fAI26 | "Ini bukan QR fAI26. Arahkan ke QR yang dipasang di TPH." |
| QR fAI26 jenis lain | "QR ini bukan QR TPH (jenis: ...)." |
| Versi format tidak dikenal | "Versi QR ... belum didukung. Perbarui aplikasi." |
| QR benar tetapi TPH tidak ada di master HP | "TPH ... tidak ada di data HP ini. Perbarui data master atau pilih TPH manual." |
| QR rusak atau tak terbaca | Tidak ada pesan, aplikasi terus mencoba. Krani bisa menyalakan senter atau memilih TPH manual. |

Pemilihan TPH **manual** dicatat sebagai `sumber_tph = MANUAL` di `audit_log` catatan panen (QR: `QR`), supaya terlihat catatan mana yang tidak memakai QR.

## Cara menguji tanpa TPH

1. Jalankan `python tools/buat_qr_tph.py` (butuh `pip install qrcode pillow`). Hasilnya di folder `qr_contoh/`.
2. Buka salah satu PNG (misalnya `TPH_A12_01.png`) di layar komputer.
3. Di aplikasi, tekan **SCAN QR TPH** dan arahkan kamera ke layar komputer.
