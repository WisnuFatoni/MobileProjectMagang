# Tugas: Prototipe Flutter untuk Grading Kematangan TBS Sawit (Deteksi Kamera)

Catatan ini untuk Claude Code. Baca semuanya, lalu kerjakan di proyek Flutter ini.
Pemilik proyek: Ptoni (mahasiswa TRPL, magang di kebun sawit). Bahasa komunikasi: Indonesia.

## Tujuan

Bikin aplikasi Flutter (Android) yang membuka kamera, mendeteksi tandan buah segar (TBS) sawit,
menggambar kotak di tiap tandan, dan menampilkan kelas kematangannya beserta ringkasan jumlah per kelas.
Ini prototipe untuk tugas KKP. Waktu pengerjaan kurang dari satu bulan, jadi **mulai dari yang paling sederhana dan pastikan jalan dulu**.

## Yang sudah selesai (jangan dikerjakan ulang)

- Model YOLOv8n sudah dilatih (Ultralytics 8.4.198, Google Colab T4) dan diekspor ke format LiteRT/TFLite.
- File model: `best.tflite` (sekitar 12 MB). Cadangan PyTorch: `best.pt`.
  Kalau file `best.tflite` belum ada di proyek, **tanyakan ke Ptoni di mana letaknya**, jangan membuat file palsu.
- Dataset: Roboflow `fine-tuning/palm-fruit-ripeness-detection-f6sac` v1, lisensi CC BY 4.0, 4.160 gambar, task Object Detection.
- Detail model:
  - Input: `[1, 3, 640, 640]`, float32, urutan channel-first (NCHW).
  - Output: `[1, 10, 8400]` (4 angka kotak + 6 skor kelas per 8.400 kandidat). **Tidak ada NMS di dalam model.**
  - Ekspor dilakukan dengan `format="tflite"` yang oleh Ultralytics 8.4.198 dialihkan ke format `litert`, tanpa NMS (output mentah).
- Urutan kelas (indeks 0 sampai 5): `Abnormal`, `Empty Bunch`, `Overripe`, `Ripe`, `Underripe`, `Unripe`.
- Hasil uji di Colab (data test, 416 gambar): mAP50 0,993 dan mAP50-95 0,894.
  **Angka ini terlalu optimis**, karena gambar dataset kemungkinan frame berurutan dari video sehingga data latih dan test mirip.
  Jangan menulis di UI atau laporan bahwa akurasinya "99%" tanpa uji foto kebun sendiri.

## Pendekatan yang dipilih

Pakai plugin resmi **`ultralytics_yolo`** (pub.dev, versi 0.7.0 saat catatan ini ditulis; pakai versi terbaru yang kompatibel).
Plugin ini menyediakan `YOLOView` (kamera langsung dengan kotak digambar native) dan `YOLO` (prediksi satu gambar).
Jangan pakai paket `aicycle_yolo` (fork tidak resmi).

Alasan: plugin sudah menangani praproses gambar, NMS di sisi Android, dan penggambaran kotak.
Dokumentasi plugin menyarankan ekspor `format="litert", nms=None, imgsz=640` untuk model custom Android,
yang hampir sama dengan cara model kita diekspor, jadi kemungkinan besar cocok. **Ini belum terbukti, jadi harus diuji.**

Jalur cadangan kalau plugin tidak mau memuat model: `flutter_litert` (fork dari tflite_flutter untuk LiteRT, runtime native sudah dibundel).
Dengan jalur ini praproses (susunan NCHW, nilai 0 sampai 1, letterbox 640x640 dengan sisa diisi hitam),
pembacaan output `[1, 10, 8400]`, dan NMS harus ditulis sendiri. Gunakan hanya kalau jalur utama gagal, dan **tanyakan dulu ke Ptoni**.

## Langkah kerja

1. **Cek lingkungan.** Jalankan `flutter doctor`. Pastikan ada perangkat Android atau emulator. Laporkan kalau ada masalah.
2. **Cek proyek.** Kalau belum ada proyek Flutter di folder ini, buat dengan `flutter create sawit_grading` (atau nama yang disepakati) dan masuk ke foldernya.
3. **Pasang plugin.** `flutter pub add ultralytics_yolo`.
4. **Pasang model.** Salin `best.tflite` ke `assets/models/best.tflite` dan daftarkan di `pubspec.yaml` bagian `flutter: assets:`.
5. **Pengaturan Android.**
   - Tambahkan izin kamera `android.permission.CAMERA` di `android/app/src/main/AndroidManifest.xml`.
   - Kalau build gagal soal `minSdk`, naikkan sesuai angka yang diminta error.
   - **Baca Installation Guide plugin** (di pub.dev: dokumentasi `ultralytics_yolo` > doc/install.md) atau file README di paket yang terpasang (cek di pub cache) untuk syarat resminya, karena syarat Android belum terverifikasi saat catatan ini ditulis.
6. **Tulis layar uji.** Buat `lib/main.dart` berisi satu layar dengan `YOLOView` memakai `assets/models/best.tflite`,
   dan teks ringkasan di bawah yang menghitung jumlah deteksi per kelas dari `onResult`.
   Kerangka kode (API di bawah hanya sebagian yang terkonfirmasi, lihat bagian "Yang belum pasti"):

   ```dart
   import 'package:flutter/material.dart';
   import 'package:ultralytics_yolo/ultralytics_yolo.dart';

   void main() => runApp(const MaterialApp(home: UjiGrading()));

   class UjiGrading extends StatefulWidget {
     const UjiGrading({super.key});
     @override
     State<UjiGrading> createState() => _UjiGradingState();
   }

   class _UjiGradingState extends State<UjiGrading> {
     String ringkasan = 'Arahkan kamera ke TBS';

     @override
     Widget build(BuildContext context) {
       return Scaffold(
         body: Stack(
           children: [
             YOLOView(
               modelPath: 'assets/models/best.tflite',
               onResult: (results) {
                 final hitung = <String, int>{};
                 for (final r in results) {
                   hitung[r.className] = (hitung[r.className] ?? 0) + 1;
                 }
                 setState(() => ringkasan =
                     hitung.isEmpty ? 'Tidak ada tandan' : hitung.toString());
               },
             ),
             Positioned(
               left: 12, right: 12, bottom: 32,
               child: Container(
                 padding: const EdgeInsets.all(12),
                 color: Colors.black54,
                 child: Text(ringkasan,
                     style: const TextStyle(color: Colors.white, fontSize: 16)),
               ),
             ),
           ],
         ),
       );
     }
   }
   ```

7. **Build dan jalankan.** `flutter run` di perangkat Android. Perbaiki error build satu per satu.
8. **Laporkan hasil** (lihat bagian "Yang harus dilaporkan").

## Yang belum pasti (verifikasi dulu, jangan percaya buta)

- Nama dan tanda tangan API persisnya. Yang terkonfirmasi dari dokumentasi 0.7.0:
  `YOLOView(modelPath:, controller:, onResult:)`, `onResult` memberi daftar hasil dengan field `className` dan `confidence`,
  `YOLO(modelPath:, task: YOLOTask.detect)` lalu `await yolo.loadModel()` dan `await yolo.predict(imageBytes)`,
  serta `controller.switchModel(path, YOLOTask.detect)`.
  **Cocokkan kode dengan API di paket yang benar-benar terpasang** (baca source atau dokumentasi versi yang terpasang) sebelum menulis lebih banyak.
- Apakah `YOLOView` menerima parameter `task:`. Kalau model gagal dimuat karena tugas tidak terdeteksi, coba tambahkan `task: YOLOTask.detect` bila parameternya ada.
- Apakah plugin membaca metadata (nama kelas) dari file `best.tflite` hasil ekspor LiteRT ini.
  Kalau label muncul sebagai angka 0 sampai 5, petakan sendiri memakai urutan kelas di atas, dan laporkan.
- Apakah file ekspor LiteRT (input NCHW) kompatibel dengan plugin. Kalau error saat memuat model, **jangan menebak**.
  Salin pesan error persisnya ke Ptoni dan usulkan opsi: ekspor ulang dengan pengaturan lain, atau pindah ke `flutter_litert`.

## Batasan dan aturan

- Fokus Android dulu. File `.tflite` hanya untuk Android. iOS butuh model Core ML, jangan dikerjakan sekarang.
- Jangan menaruh API key, token, atau kredensial apa pun di kode atau di file ini.
- Jangan mengubah atau melatih ulang model di tugas ini. Kalau hasilnya jelek, laporkan dan biarkan Ptoni memutuskan.
- Jangan menambah fitur di luar layar uji (login, database, rekap panen, dsb.) sebelum prototipe ini terbukti jalan.
- Lisensi: plugin `ultralytics_yolo` berlisensi AGPL-3.0 atau lisensi Enterprise berbayar. Untuk tugas KKP aman, tapi ingatkan Ptoni
  untuk menanyakan soal lisensi bila aplikasi nanti dipakai perusahaan.
- Dataset CC BY 4.0: sumber harus dicantumkan di laporan (Roboflow `fine-tuning/palm-fruit-ripeness-detection-f6sac`).
- Kalau ada langkah yang butuh keputusan Ptoni (nama proyek, jalur file model, perangkat uji), tanyakan, jangan mengarang.

## Yang harus dilaporkan setelah selesai

1. Apakah model berhasil dimuat, atau pesan error persisnya.
2. Label yang muncul berupa nama atau angka.
3. Apakah kotak muncul dan pas di tandan.
4. Perkiraan kelancaran (lancar, patah-patah, atau berat) dan tipe HP yang dipakai.
5. Versi `ultralytics_yolo` dan Flutter yang terpasang, serta perubahan yang dibuat ke file proyek.

## Langkah setelah prototipe jalan (jangan dikerjakan sekarang, hanya untuk konteks)

- Uji dengan 20 sampai 30 foto TBS dari kebun sungguhan (belum pernah dilihat model) untuk mengukur akurasi yang jujur.
- Kumpulkan 100 sampai 300 foto kebun, anotasi di Roboflow, lalu latih ulang model.
- Cocokkan enam kelas model dengan istilah grading di kebun (matang, mentah, busuk) bersama pembimbing.
- Sambungkan hasil deteksi ke fitur rekap panen.
