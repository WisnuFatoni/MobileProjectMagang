package com.example.fai26.panorama;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Log;

import com.example.fai26.KelasModel;
import com.example.fai26.detect.Detection;
import com.example.fai26.ui.KeyframeRenderer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Menyusun satu foto panorama mulus dari frame-frame penyisiran tumpukan, supaya satu gambar bukti memperlihatkan seluruh
 * bagian yang disisir tanpa potongan kasar ("ditempel-tempel") di tengah gambar maupun tangga hitam di pinggiran.
 *
 * Cara kerja:
 * 1. Selama penyisiran, menyimpan frame tunggal terbaik (dengan deteksi terbanyak & terjelas) sebagai cadangan utuh.
 * 2. Saat menyusun panorama dari potongan-potongan (tile):
 *    - Mencocokkan ulang posisi tiap potongan secara langsung terhadap potongan sebelumnya (registrasi pasangan langsung)
 *      dan membuang potongan duplikat yang menumpuk di tempat yang sama.
 *    - Memeriksa kualitas tumpang-tindih: bila kamera hanya diam/miring/maju-mundur di satu tumpukan yang sama
 *      (rentang geser nyata sempit atau galat perspektif 3D tinggi), otomatis memakai 1 foto resolusi penuh terbaik
 *      yang utuh 100% tanpa sambungan.
 *    - Bila benar-benar menyisir tumpukan panjang: menyamakan kecerahan/warna (exposure gain matching), mencari jalur
 *      jahitan berbiaya terendah dengan Dynamic Programming (Optimal Seam Carving) yang meliuk menghindari buah sawit,
 *      dan membaurkan piksel secara halus dengan kurva kosinus langsung di larik piksel ARGB.
 */
public final class PanoramaPemindai {

    private static final String TAG = "PanoramaPemindai";

    /** Lebar frame tegak untuk menaksir pergeseran. Kecil supaya cepat; ketelitiannya sekitar 4 piksel pada foto akhir. */
    static final int LEBAR_TAKSIR = 180;
    /** Lebar tiap potongan yang disimpan (piksel). */
    static final int LEBAR_TILE = 540;
    /** Sisi terpanjang panorama akhir. */
    static final int BATAS_SISI_AKHIR = 2400;
    static final int KUALITAS_TILE = 92;
    static final int KUALITAS_AKHIR = 88;
    /** Galat rata-rata maksimum pada area sambungan (0..255). Di atas ini berarti paralaks/zoom 3D membuat buah terpotong. */
    private static final double BATAS_GALAT_SAMBUNGAN = 25.0;

    public static final class Hasil {
        /** Jumlah potongan yang dipakai: 0 berarti tidak ada foto, 1 satu frame biasa, lebih dari 1 panorama. */
        public final int jumlahTile;
        /** Bila bukan panorama: alasan singkat (untuk pengguna dan catatan), null bila panorama jadi atau tidak ada yang perlu dikatakan. */
        public final String alasan;

        Hasil(int jumlahTile) {
            this(jumlahTile, null);
        }

        Hasil(int jumlahTile, String alasan) {
            this.jumlahTile = jumlahTile;
            this.alasan = alasan;
        }

        public boolean adaFoto() {
            return jumlahTile > 0;
        }

        public boolean panorama() {
            return jumlahTile > 1;
        }
    }

    private final File folder;
    private final List<File> berkasTile = new ArrayList<>();
    private final List<List<Detection>> deteksiPerTile = new ArrayList<>();
    private RancangPanorama rancang;
    private Gray ref;
    /** Geseran per frame terakhir yang sah (piksel frame taksiran): titik awal pencarian frame berikutnya. */
    private int perkiraanDx;
    private int perkiraanDy;
    private int tileW;
    private int tileH;
    private double faktor;
    private int nomor;
    private boolean aktif;
    private boolean rusak;
    /** Alasan terakhir panorama tidak jadi (diisi saat menempel gagal atau tidak layak). */
    private String alasanGagal;

    /** Cadangan frame tunggal terbaik (resolusi penuh, utuh tanpa sambungan) selama sesi penyisiran. */
    private Bitmap mentahTerbaik;
    private int rotasiTerbaik;
    private List<Detection> deteksiTerbaik = Collections.emptyList();
    private float skorTerbaik = -1f;

    public PanoramaPemindai(File folderKerja) {
        this.folder = folderKerja;
    }

    /** Memulai penyisiran baru: membuang potongan yang lama. */
    public synchronized void mulai() {
        hapusTile();
        rancang = null;
        ref = null;
        perkiraanDx = 0;
        perkiraanDy = 0;
        nomor = 0;
        aktif = true;
        rusak = false;
        alasanGagal = null;
        mentahTerbaik = null;
        rotasiTerbaik = 0;
        deteksiTerbaik = Collections.emptyList();
        skorTerbaik = -1f;
    }

    /** Dipanggil untuk setiap frame yang dianalisis selama penyisiran. Tidak pernah melempar galat. */
    public synchronized void tambahFrame(Bitmap mentah, int rotasi, List<Detection> deteksi) {
        if (!aktif || rusak) return;
        try {
            perbaruiFrameTerbaik(mentah, rotasi, deteksi);
            Gray cur = grayTaksir(mentah, rotasi);
            if (rancang == null) {
                rancang = new RancangPanorama(cur.w, cur.h);
                rancang.mulai();
                tulisTile(mentah, rotasi, deteksi);
                faktor = tileW / (double) cur.w;
                ref = cur;
                return;
            }
            PenaksirGeser.Hasil g = PenaksirGeser.taksir(ref, cur, perkiraanDx, perkiraanDy);
            RancangPanorama.Keputusan k = rancang.terima(g.dx, g.dy, g.sah, RancangPanorama.AMBANG_TILE);
            if (k.ambilTile) tulisTile(mentah, rotasi, deteksi);
            if (k.jadikanRef) ref = cur;
            // Geseran frame ini jadi perkiraan frame berikutnya, hanya bila ref ikut diganti (geseran per frame, bukan akumulasi).
            if (g.sah && k.jadikanRef) {
                perkiraanDx = g.dx;
                perkiraanDy = g.dy;
            } else if (!g.sah) {
                perkiraanDx = 0;
                perkiraanDy = 0;
            }
        } catch (Throwable e) {
            // Panorama hanya pelengkap: jangan pernah mengganggu penghitungan. Berhenti dan pakai yang sudah ada.
            Log.e(TAG, "Penyusunan panorama dihentikan", e);
            rusak = true;
        }
    }

    private void perbaruiFrameTerbaik(Bitmap mentah, int rotasi, List<Detection> deteksi) {
        if (mentah == null) return;
        float skor = skorDeteksi(deteksi);
        if (mentahTerbaik == null || skor >= skorTerbaik) {
            mentahTerbaik = mentah;
            rotasiTerbaik = rotasi;
            deteksiTerbaik = deteksi != null ? new ArrayList<>(deteksi) : Collections.emptyList();
            skorTerbaik = skor;
        }
    }

    private static float skorDeteksi(List<Detection> deteksi) {
        if (deteksi == null || deteksi.isEmpty()) return 0f;
        float s = deteksi.size() * 1.0f;
        for (Detection d : deteksi) s += d.confidence;
        return s;
    }

    /**
     * Menutup penyisiran dan menulis foto bukti ke {@code tujuan}.
     *
     * @param mentahAkhir  frame terakhir (boleh null); ikut ditempel bila sudah bergeser dari potongan terakhir
     * @param deteksiAkhir kotak deteksi pada frame terakhir
     */
    public synchronized Hasil selesai(Bitmap mentahAkhir, int rotasi, List<Detection> deteksiAkhir, File tujuan)
            throws IOException {
        aktif = false;
        try {
            perbaruiFrameTerbaik(mentahAkhir, rotasi, deteksiAkhir);
            if (rancang != null && !rusak && !rancang.terbeku() && mentahAkhir != null) {
                Gray cur = grayTaksir(mentahAkhir, rotasi);
                PenaksirGeser.Hasil g = PenaksirGeser.taksir(ref, cur);
                RancangPanorama.Keputusan k = rancang.terima(g.dx, g.dy, g.sah, RancangPanorama.AMBANG_AKHIR);
                if (k.ambilTile) tulisTile(mentahAkhir, rotasi, deteksiAkhir);
            }
        } catch (Throwable e) {
            Log.e(TAG, "Frame penutup tidak ikut ditempel", e);
        }

        int jumlah = rancang == null ? 0 : Math.min(berkasTile.size(), rancang.jumlahTile());
        alasanGagal = null;
        if (jumlah > 1 && !rusakSaatMenempel(tujuan, jumlah)) {
            return new Hasil(jumlah);
        }
        String alasan = alasanGagal;
        if (alasan == null) {
            if (rusak) alasan = "penyusunan panorama berhenti karena galat";
            else if (rancang != null && rancang.terbeku()) alasan = "gerakan terlalu cepat atau gambar kabur, penyejajaran terputus";
            else if (jumlah <= 1) alasan = "kamera hampir tidak bergeser";
        }
        Log.i(TAG, "Panorama tidak dipakai (tile=" + jumlah + "): " + alasan);
        // Pilih frame tunggal terbaik (yang paling banyak/jelas menangkap janjang) bila lebih unggul dari frame terakhir.
        Bitmap pilihMentah = mentahAkhir;
        int pilihRotasi = rotasi;
        List<Detection> pilihDeteksi = deteksiAkhir;
        if (mentahTerbaik != null && (pilihMentah == null || skorDeteksi(deteksiTerbaik) > skorDeteksi(deteksiAkhir))) {
            pilihMentah = mentahTerbaik;
            pilihRotasi = rotasiTerbaik;
            pilihDeteksi = deteksiTerbaik;
        }
        if (pilihMentah != null) {
            Matrix m = new Matrix();
            m.postRotate(pilihRotasi);
            Bitmap tegak = pilihRotasi == 0 ? pilihMentah : Bitmap.createBitmap(pilihMentah, 0, 0,
                    pilihMentah.getWidth(), pilihMentah.getHeight(), m, true);
            KeyframeRenderer.simpan(tegak, pilihDeteksi != null ? pilihDeteksi : Collections.emptyList(), tujuan);
            return new Hasil(1, alasan);
        }
        if (!berkasTile.isEmpty()) {
            salin(berkasTile.get(0), tujuan);
            return new Hasil(1, alasan);
        }
        return new Hasil(0);
    }

    /** Membuang potongan (dipanggil saat layar ditutup atau penyisiran diulang). */
    public synchronized void bersihkan() {
        aktif = false;
        hapusTile();
        rancang = null;
        ref = null;
        mentahTerbaik = null;
        deteksiTerbaik = Collections.emptyList();
        skorTerbaik = -1f;
    }

    // ---- internal ---------------------------------------------------------------------------------

    /** @return true bila menempel gagal atau tidak layak (pemanggil jatuh ke foto satu frame terbaik yang utuh). */
    private boolean rusakSaatMenempel(File tujuan, int jumlah) {
        try {
            List<int[]> posisiAwal = rancang.posisiTile().subList(0, jumlah);
            KanvasPanorama.Tata tAwal = KanvasPanorama.hitung(posisiAwal, faktor, tileW, tileH, BATAS_SISI_AKHIR);
            int tw = tAwal.lebarTile;
            int th = tAwal.tinggiTile;
            if (tw <= 0 || th <= 0) return true;

            // 1. Baca semua potongan ke larik piksel ARGB berukuran tw x th dan abu-abu skala 1/4 untuk registrasi langsung.
            int[][] pikselTile = new int[jumlah][tw * th];
            int sw = Math.max(16, tw / 4);
            int sh = Math.max(16, th / 4);
            Gray[] grayKecil = new Gray[jumlah];

            for (int i = 0; i < jumlah; i++) {
                Bitmap decoded = BitmapFactory.decodeFile(berkasTile.get(i).getAbsolutePath());
                if (decoded == null) throw new IOException("potongan tidak terbaca: " + berkasTile.get(i).getName());
                try {
                    Bitmap sesuai = (decoded.getWidth() == tw && decoded.getHeight() == th)
                            ? decoded
                            : Bitmap.createScaledBitmap(decoded, tw, th, true);
                    sesuai.getPixels(pikselTile[i], 0, tw, 0, 0, tw, th);
                    Bitmap kecil = Bitmap.createScaledBitmap(sesuai, sw, sh, true);
                    int[] pxKecil = new int[sw * sh];
                    kecil.getPixels(pxKecil, 0, sw, 0, 0, sw, sh);
                    if (kecil != sesuai) kecil.recycle();
                    if (sesuai != decoded) sesuai.recycle();
                    grayKecil[i] = Gray.dariArgb(pxKecil, sw, sh);
                } finally {
                    decoded.recycle();
                }
            }

            // 2. Registrasi pasangan langsung (Direct Pairwise Alignment) + saring potongan duplikat yang menumpuk.
            List<Integer> indeksPakai = new ArrayList<>();
            List<int[]> posisiTepat = new ArrayList<>();
            indeksPakai.add(0);
            posisiTepat.add(new int[]{0, 0});

            double skalaKecilX = tw / (double) sw;
            double skalaKecilY = th / (double) sh;
            int minGeserPx = Math.max(16, (int) Math.round(Math.min(tw, th) * 0.16));

            for (int i = 1; i < jumlah; i++) {
                int idxSebelumnya = indeksPakai.get(indeksPakai.size() - 1);
                int[] posSebelumnya = posisiTepat.get(posisiTepat.size() - 1);

                // Taksiran awal pergeseran pusat tile i relatif terhadap tile idxSebelumnya dalam skala sw x sh
                int taksirShiftX = (int) Math.round((tAwal.kiriAtas[i][0] - tAwal.kiriAtas[idxSebelumnya][0]) / skalaKecilX);
                int taksirShiftY = (int) Math.round((tAwal.kiriAtas[i][1] - tAwal.kiriAtas[idxSebelumnya][1]) / skalaKecilY);

                // Cari pencocokan langsung terbaik di sekitar taksiran awal MAUPUN di sekitar (0,0) (bila kamera sebenarnya diam)
                HasilPasangan cocok = cocokkanPasanganLangsung(grayKecil[idxSebelumnya], grayKecil[i], taksirShiftX, taksirShiftY);
                if (cocok.galat > BATAS_GALAT_SAMBUNGAN) {
                    Log.w(TAG, "Sambungan tile " + idxSebelumnya + "->" + i + " ditolak karena galat perspektif tinggi: " + cocok.galat);
                    alasanGagal = "pandangan 3D tumpukan terlalu berbeda antar frame (terlalu dekat atau bergerak miring)";
                    return true;
                }

                int shiftPxX = (int) Math.round(cocok.shiftX * skalaKecilX);
                int shiftPxY = (int) Math.round(cocok.shiftY * skalaKecilY);

                // Bila kamera nyaris tidak bergeser dari potongan terakhir yang disimpan, lewati potongan duplikat ini
                // kecuali ini potongan terakhir dan belum ada potongan lain.
                if (Math.hypot(shiftPxX, shiftPxY) < minGeserPx) {
                    continue;
                }

                indeksPakai.add(i);
                posisiTepat.add(new int[]{posSebelumnya[0] + shiftPxX, posSebelumnya[1] + shiftPxY});
            }

            if (indeksPakai.size() < 2) {
                Log.i(TAG, "Semua potongan berada di posisi tumpukan yang sama; memakai 1 frame penuh terbaik");
                alasanGagal = "kamera hampir tidak bergeser";
                return true;
            }

            // Normalisasi koordinat agar kiri-atas minimum bernilai (0, 0)
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
            for (int[] p : posisiTepat) {
                if (p[0] < minX) minX = p[0];
                if (p[1] < minY) minY = p[1];
                if (p[0] > maxX) maxX = p[0];
                if (p[1] > maxY) maxY = p[1];
            }
            int spanX = maxX - minX;
            int spanY = maxY - minY;
            if (spanX < (int) (tw * 0.20f) && spanY < (int) (th * 0.20f)) {
                Log.i(TAG, "Rentang geser panorama terlalu kecil (" + spanX + "x" + spanY + "); memakai 1 frame penuh terbaik");
                alasanGagal = "rentang sisiran terlalu pendek";
                return true;
            }

            int jumlahPakai = indeksPakai.size();
            int[][] kiriAtas = new int[jumlahPakai][2];
            for (int k = 0; k < jumlahPakai; k++) {
                kiriAtas[k][0] = posisiTepat.get(k)[0] - minX;
                kiriAtas[k][1] = posisiTepat.get(k)[1] - minY;
            }
            int kw = (maxX - minX) + tw;
            int kh = (maxY - minY) + th;

            // 3. Jahit potongan ke dalam larik piksel kanvas menggunakan:
            //    - Penyeimbang kecerahan/warna (RGB Gain Matching)
            //    - Dynamic Programming Optimal Seam Carving (jalur jahit meliuk menghindari janjang sawit)
            //    - Pembauran kosinus (Cosine Feather Blending) di sepanjang jalur jahitan
            int[] kanvasPx = new int[kw * kh];
            boolean[] terisi = new boolean[kw * kh];

            // Salin potongan pertama secara utuh
            int[] tile0 = pikselTile[indeksPakai.get(0)];
            int tx0 = kiriAtas[0][0];
            int ty0 = kiriAtas[0][1];
            for (int y = 0; y < th; y++) {
                int dstOff = (ty0 + y) * kw + tx0;
                int srcOff = y * tw;
                System.arraycopy(tile0, srcOff, kanvasPx, dstOff, tw);
                Arrays.fill(terisi, dstOff, dstOff + tw, true);
            }

            for (int k = 1; k < jumlahPakai; k++) {
                int idxTile = indeksPakai.get(k);
                int[] tileBaru = pikselTile[idxTile];
                int tx = kiriAtas[k][0];
                int ty = kiriAtas[k][1];
                int dxRel = tx - kiriAtas[k - 1][0];
                int dyRel = ty - kiriAtas[k - 1][1];
                jahitPotonganOptimal(kanvasPx, terisi, kw, kh, tileBaru, tw, th, tx, ty, dxRel, dyRel);
            }

            // 4. Bila penyisiran dominan mendatar, pangkas bagian atas & bawah ke area tumpang-tindih bersama
            //    supaya kanvas berbentuk persegi panjang bersih tanpa tangga.
            int overlapTop = 0;
            int overlapBottom = kh;
            for (int k = 0; k < jumlahPakai; k++) {
                overlapTop = Math.max(overlapTop, kiriAtas[k][1]);
                overlapBottom = Math.min(overlapBottom, kiriAtas[k][1] + th);
            }
            int cropTop = 0;
            int finalH = kh;
            if (spanX > spanY && (overlapBottom - overlapTop) >= (int) (th * 0.55f)) {
                cropTop = overlapTop;
                finalH = overlapBottom - overlapTop;
            }

            // Bila masih ada sudut piksel yang belum terisi (misal pada penyisiran diagonal 2D),
            // isi dengan ekstrapolasi piksel pinggir terdekat agar tidak ada blok segitiga hitam.
            isiSudutKosong(kanvasPx, terisi, kw, kh, cropTop, cropTop + finalH);

            Bitmap kanvasBersih = Bitmap.createBitmap(kw, finalH, Bitmap.Config.ARGB_8888);
            try {
                kanvasBersih.setPixels(kanvasPx, cropTop * kw, kw, 0, 0, kw, finalH);

                // 5. Proyeksikan deteksi ke koordinat panorama global + Non-Maximum Suppression (NMS)
                List<KotakGlobal> kandidat = new ArrayList<>();
                for (int k = 0; k < jumlahPakai; k++) {
                    int idxAsli = indeksPakai.get(k);
                    if (idxAsli >= deteksiPerTile.size()) continue;
                    List<Detection> dets = deteksiPerTile.get(idxAsli);
                    if (dets == null || dets.isEmpty()) continue;
                    int tx = kiriAtas[k][0];
                    int ty = kiriAtas[k][1] - cropTop;
                    for (Detection d : dets) {
                        float gx1 = tx + d.x1 * tw;
                        float gy1 = ty + d.y1 * th;
                        float gx2 = tx + d.x2 * tw;
                        float gy2 = ty + d.y2 * th;

                        if (gx2 <= 0 || gx1 >= kw || gy2 <= 0 || gy1 >= finalH) continue;

                        gx1 = Math.max(0, gx1);
                        gy1 = Math.max(0, gy1);
                        gx2 = Math.min(kw, gx2);
                        gy2 = Math.min(finalH, gy2);

                        float bw = gx2 - gx1;
                        float bh = gy2 - gy1;
                        float ow = (d.x2 - d.x1) * tw;
                        float oh = (d.y2 - d.y1) * th;
                        if (bw * bh < ow * oh * 0.35f) continue;

                        kandidat.add(new KotakGlobal(d.classIndex, d.confidence, gx1, gy1, gx2, gy2));
                    }
                }

                Collections.sort(kandidat, (a, b) -> Float.compare(b.conf, a.conf));
                List<KotakGlobal> janjangUtuh = new ArrayList<>();
                for (KotakGlobal kg : kandidat) {
                    boolean duplikat = false;
                    for (KotakGlobal su : janjangUtuh) {
                        if (hitungIou(kg, su) > 0.35f) {
                            duplikat = true;
                            break;
                        }
                    }
                    if (!duplikat) janjangUtuh.add(kg);
                }
                Collections.sort(janjangUtuh, (a, b) -> Float.compare(a.x1, b.x1));

                Canvas renderCanvas = new Canvas(kanvasBersih);
                Paint kotak = new Paint(Paint.ANTI_ALIAS_FLAG);
                kotak.setStyle(Paint.Style.STROKE);
                kotak.setStrokeWidth(Math.max(3.5f, kw / 450f));

                Paint latar = new Paint(Paint.ANTI_ALIAS_FLAG);
                Paint teks = new Paint(Paint.ANTI_ALIAS_FLAG);
                teks.setTextSize(Math.max(16f, kw / 70f));
                teks.setFakeBoldText(true);

                int[] hitungPerKelas = new int[KelasModel.JUMLAH];
                for (int j = 0; j < janjangUtuh.size(); j++) {
                    KotakGlobal kg = janjangUtuh.get(j);
                    hitungPerKelas[kg.classIndex]++;
                    int no = j + 1;
                    int warna = KelasModel.WARNA[kg.classIndex];
                    kotak.setColor(warna);
                    renderCanvas.drawRect(kg.x1, kg.y1, kg.x2, kg.y2, kotak);

                    String label = "#" + no + " " + KelasModel.TAMPIL[kg.classIndex] + " " + Math.round(kg.conf * 100) + "%";
                    float thTeks = teks.getTextSize();
                    float twTeks = teks.measureText(label);
                    latar.setColor(warna);
                    float badgeTop = Math.max(0, kg.y1 - thTeks - 8);
                    renderCanvas.drawRoundRect(new RectF(kg.x1, badgeTop, kg.x1 + twTeks + 14, badgeTop + thTeks + 6), 6, 6, latar);
                    teks.setColor(isColorDark(warna) ? 0xFFFFFFFF : 0xFF000000);
                    renderCanvas.drawText(label, kg.x1 + 7, badgeTop + thTeks, teks);
                }

                if (!janjangUtuh.isEmpty()) {
                    Paint bannerBg = new Paint(Paint.ANTI_ALIAS_FLAG);
                    bannerBg.setColor(0xCC000000);
                    Paint bannerJudul = new Paint(Paint.ANTI_ALIAS_FLAG);
                    bannerJudul.setColor(0xFF4ADE80);
                    bannerJudul.setTextSize(Math.max(22f, kw / 45f));
                    bannerJudul.setFakeBoldText(true);

                    Paint bannerSub = new Paint(Paint.ANTI_ALIAS_FLAG);
                    bannerSub.setColor(0xFFFFFFFF);
                    bannerSub.setTextSize(Math.max(14f, kw / 65f));

                    StringBuilder sb = new StringBuilder();
                    for (int k = 0; k < KelasModel.JUMLAH; k++) {
                        if (hitungPerKelas[k] > 0) {
                            if (sb.length() > 0) sb.append(" • ");
                            sb.append(KelasModel.TAMPIL[k]).append(": ").append(hitungPerKelas[k]);
                        }
                    }
                    String titleStr = "HASIL SCAN TPH: " + janjangUtuh.size() + " JANJANG";
                    String subStr = sb.toString();

                    float pad = Math.max(16f, kw / 100f);
                    float bW = Math.max(bannerJudul.measureText(titleStr), bannerSub.measureText(subStr)) + pad * 2;
                    float bH = bannerJudul.getTextSize() + bannerSub.getTextSize() + pad * 2 + 8;
                    RectF bRect = new RectF(pad, pad, pad + bW, pad + bH);
                    renderCanvas.drawRoundRect(bRect, 12, 12, bannerBg);

                    renderCanvas.drawText(titleStr, pad + pad, pad + pad + bannerJudul.getTextSize() - 4, bannerJudul);
                    renderCanvas.drawText(subStr, pad + pad, pad + pad + bannerJudul.getTextSize() + bannerSub.getTextSize() + 4, bannerSub);
                }

                try (FileOutputStream out = new FileOutputStream(tujuan)) {
                    if (!kanvasBersih.compress(Bitmap.CompressFormat.JPEG, KUALITAS_AKHIR, out)) {
                        throw new IOException("gagal menulis JPEG");
                    }
                }
            } finally {
                if (!kanvasBersih.isRecycled()) kanvasBersih.recycle();
            }
            return false;
        } catch (Throwable e) {
            Log.e(TAG, "Menempel panorama gagal, memakai foto satu frame", e);
            return true;
        }
    }

    private static final class HasilPasangan {
        final int shiftX;
        final int shiftY;
        final double galat;

        HasilPasangan(int shiftX, int shiftY, double galat) {
            this.shiftX = shiftX;
            this.shiftY = shiftY;
            this.galat = galat;
        }
    }

    /**
     * Mencocokkan langsung dua potongan berurutan pada resolusi sw x sh.
     * shiftX, shiftY adalah posisi kiri-atas potongan B relatif terhadap potongan A.
     */
    private static HasilPasangan cocokkanPasanganLangsung(Gray a, Gray b, int taksirShiftX, int taksirShiftY) {
        double terbaik = Double.MAX_VALUE;
        int bestX = taksirShiftX;
        int bestY = taksirShiftY;
        int radius = Math.max(12, Math.min(a.w, a.h) / 5);

        // 1. Telusuri di sekitar taksiran awal
        for (int sy = taksirShiftY - radius; sy <= taksirShiftY + radius; sy += 2) {
            for (int sx = taksirShiftX - radius; sx <= taksirShiftX + radius; sx += 2) {
                double c = PenaksirGeser.biaya(a, b, -sx, -sy);
                if (c < terbaik) {
                    terbaik = c;
                    bestX = sx;
                    bestY = sy;
                }
            }
        }
        // 2. Telusuri juga di sekitar (0, 0) untuk mendeteksi bila kamera sebenarnya diam di depan tumpukan
        int rDiam = Math.max(8, radius / 2);
        for (int sy = -rDiam; sy <= rDiam; sy += 2) {
            for (int sx = -rDiam; sx <= rDiam; sx += 2) {
                double c = PenaksirGeser.biaya(a, b, -sx, -sy);
                if (c < terbaik) {
                    terbaik = c;
                    bestX = sx;
                    bestY = sy;
                }
            }
        }
        // 3. Penyempurnaan 1 piksel di sekitar kandidat terbaik
        int cx = bestX, cy = bestY;
        for (int sy = cy - 2; sy <= cy + 2; sy++) {
            for (int sx = cx - 2; sx <= cx + 2; sx++) {
                double c = PenaksirGeser.biaya(a, b, -sx, -sy);
                if (c < terbaik) {
                    terbaik = c;
                    bestX = sx;
                    bestY = sy;
                }
            }
        }
        return new HasilPasangan(bestX, bestY, terbaik);
    }

    /**
     * Menjahit satu potongan {@code tilePx} (ukuran tw x th) ke {@code kanvasPx} (ukuran kw x kh) pada posisi (tx, ty)
     * menggunakan penyeimbang warna RGB, Dynamic Programming Optimal Seam Carving, dan Cosine Feather Blending.
     */
    private static void jahitPotonganOptimal(int[] kanvasPx, boolean[] terisi, int kw, int kh,
                                             int[] tilePx, int tw, int th, int tx, int ty, int dxRel, int dyRel) {
        // 1. Cari kotak pembatas area tumpang-tindih (overlap) dalam koordinat lokal tile [0..tw, 0..th]
        int ox0 = tw, oy0 = th, ox1 = -1, oy1 = -1;
        long sumKR = 0, sumKG = 0, sumKB = 0;
        long sumTR = 0, sumTG = 0, sumTB = 0;
        long cacahOverlap = 0;

        for (int y = 0; y < th; y++) {
            int ky = ty + y;
            if (ky < 0 || ky >= kh) continue;
            int kRow = ky * kw;
            int tRow = y * tw;
            for (int x = 0; x < tw; x++) {
                int kx = tx + x;
                if (kx < 0 || kx >= kw) continue;
                if (terisi[kRow + kx]) {
                    if (x < ox0) ox0 = x;
                    if (x > ox1) ox1 = x;
                    if (y < oy0) oy0 = y;
                    if (y > oy1) oy1 = y;
                    int kp = kanvasPx[kRow + kx];
                    int tp = tilePx[tRow + x];
                    sumKR += (kp >> 16) & 0xFF;
                    sumKG += (kp >> 8) & 0xFF;
                    sumKB += kp & 0xFF;
                    sumTR += (tp >> 16) & 0xFF;
                    sumTG += (tp >> 8) & 0xFF;
                    sumTB += tp & 0xFF;
                    cacahOverlap++;
                }
            }
        }

        // 2. Samakan kecerahan/eksposur RGB pada potongan baru agar tidak belang
        if (cacahOverlap > 100) {
            float gainR = clampGain((float) sumKR / Math.max(1L, sumTR));
            float gainG = clampGain((float) sumKG / Math.max(1L, sumTG));
            float gainB = clampGain((float) sumKB / Math.max(1L, sumTB));
            if (Math.abs(gainR - 1f) > 0.01f || Math.abs(gainG - 1f) > 0.01f || Math.abs(gainB - 1f) > 0.01f) {
                for (int i = 0; i < tilePx.length; i++) {
                    int p = tilePx[i];
                    int r = Math.min(255, Math.round(((p >> 16) & 0xFF) * gainR));
                    int g = Math.min(255, Math.round(((p >> 8) & 0xFF) * gainG));
                    int b = Math.min(255, Math.round((p & 0xFF) * gainB));
                    tilePx[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
                }
            }
        }

        int ow = ox1 - ox0 + 1;
        int oh = oy1 - oy0 + 1;
        if (ow <= 4 || oh <= 4) {
            // Nyaris tidak ada tumpang-tindih: langsung salin
            for (int y = 0; y < th; y++) {
                int ky = ty + y;
                if (ky < 0 || ky >= kh) continue;
                for (int x = 0; x < tw; x++) {
                    int kx = tx + x;
                    if (kx < 0 || kx >= kw) continue;
                    kanvasPx[ky * kw + kx] = tilePx[y * tw + x];
                    terisi[ky * kw + kx] = true;
                }
            }
            return;
        }

        boolean geserMendatar = Math.abs(dxRel) >= Math.abs(dyRel);
        if (geserMendatar) {
            // Dynamic Programming dari atas (oy0) ke bawah (oy1) untuk mencari jalur jahit optimal seamX[y]
            int[] seamX = cariJalurJahitTegak(kanvasPx, terisi, kw, tilePx, tw, tx, ty, ox0, ox1, oy0, oy1);
            boolean tileDiKanan = dxRel >= 0;
            int feather = Math.max(12, Math.min(44, ow / 2));

            for (int y = 0; y < th; y++) {
                int ky = ty + y;
                if (ky < 0 || ky >= kh) continue;
                int kRow = ky * kw;
                int tRow = y * tw;
                int sx = (y >= oy0 && y <= oy1) ? seamX[y - oy0] : (ox0 + ox1) / 2;

                for (int x = 0; x < tw; x++) {
                    int kx = tx + x;
                    if (kx < 0 || kx >= kw) continue;
                    int kIdx = kRow + kx;
                    int tp = tilePx[tRow + x];
                    if (!terisi[kIdx]) {
                        kanvasPx[kIdx] = tp;
                        terisi[kIdx] = true;
                        continue;
                    }
                    // Jarak berarah dari jalur jahit: positif berarti sisi milik tile baru
                    int d = tileDiKanan ? (x - sx) : (sx - x);
                    if (d >= feather) {
                        kanvasPx[kIdx] = tp;
                    } else if (d > -feather) {
                        float t = (d + feather) / (float) (2 * feather);
                        float wBaru = 0.5f - 0.5f * (float) Math.cos(Math.PI * t);
                        kanvasPx[kIdx] = baurPiksel(kanvasPx[kIdx], tp, wBaru);
                    }
                }
            }
        } else {
            // Dynamic Programming dari kiri (ox0) ke kanan (ox1) untuk mencari jalur jahit optimal seamY[x]
            int[] seamY = cariJalurJahitMendatar(kanvasPx, terisi, kw, tilePx, tw, tx, ty, ox0, ox1, oy0, oy1);
            boolean tileDiBawah = dyRel >= 0;
            int feather = Math.max(12, Math.min(44, oh / 2));

            for (int y = 0; y < th; y++) {
                int ky = ty + y;
                if (ky < 0 || ky >= kh) continue;
                int kRow = ky * kw;
                int tRow = y * tw;
                for (int x = 0; x < tw; x++) {
                    int kx = tx + x;
                    if (kx < 0 || kx >= kw) continue;
                    int kIdx = kRow + kx;
                    int tp = tilePx[tRow + x];
                    if (!terisi[kIdx]) {
                        kanvasPx[kIdx] = tp;
                        terisi[kIdx] = true;
                        continue;
                    }
                    int sy = (x >= ox0 && x <= ox1) ? seamY[x - ox0] : (oy0 + oy1) / 2;
                    int d = tileDiBawah ? (y - sy) : (sy - y);
                    if (d >= feather) {
                        kanvasPx[kIdx] = tp;
                    } else if (d > -feather) {
                        float t = (d + feather) / (float) (2 * feather);
                        float wBaru = 0.5f - 0.5f * (float) Math.cos(Math.PI * t);
                        kanvasPx[kIdx] = baurPiksel(kanvasPx[kIdx], tp, wBaru);
                    }
                }
            }
        }
    }

    /**
     * Mencari jalur jahitan vertikal {@code seamX[0..oh-1]} (dalam koordinat lokal tile {@code ox0..ox1})
     * yang meminimalkan selisih warna + gradien tepi sehingga jalur jahit meliuk di celah antar tandan sawit.
     */
    private static int[] cariJalurJahitTegak(int[] kanvasPx, boolean[] terisi, int kw,
                                             int[] tilePx, int tw, int tx, int ty,
                                             int ox0, int ox1, int oy0, int oy1) {
        int ow = ox1 - ox0 + 1;
        int oh = oy1 - oy0 + 1;
        // Batasi pencarian di 70% bagian tengah area overlap agar jahitan tidak menempel tepat di tepi luar potongan
        int margin = Math.max(2, ow / 6);
        int xMin = ox0 + margin;
        int xMax = ox1 - margin;
        if (xMin > xMax) {
            xMin = ox0;
            xMax = ox1;
        }
        int sw = xMax - xMin + 1;
        int[] dpPrev = new int[sw];
        int[] dpCur = new int[sw];
        short[][] arah = new short[oh][sw];

        for (int ry = 0; ry < oh; ry++) {
            int y = oy0 + ry;
            int ky = ty + y;
            int kRow = ky * kw;
            int tRow = y * tw;

            for (int rx = 0; rx < sw; rx++) {
                int x = xMin + rx;
                int kx = tx + x;
                int biayaPiksel = terisi[kRow + kx]
                        ? biayaJahitPiksel(kanvasPx, kw, kx, ky, tilePx, tw, x, y, ox1 - ox0)
                        : 10000;

                if (ry == 0) {
                    dpCur[rx] = biayaPiksel;
                    arah[ry][rx] = 0;
                } else {
                    int bTengah = dpPrev[rx];
                    int bKiri = rx > 0 ? dpPrev[rx - 1] + 12 : Integer.MAX_VALUE / 2;
                    int bKanan = rx + 1 < sw ? dpPrev[rx + 1] + 12 : Integer.MAX_VALUE / 2;
                    if (bTengah <= bKiri && bTengah <= bKanan) {
                        dpCur[rx] = biayaPiksel + bTengah;
                        arah[ry][rx] = 0;
                    } else if (bKiri <= bKanan) {
                        dpCur[rx] = biayaPiksel + bKiri;
                        arah[ry][rx] = -1;
                    } else {
                        dpCur[rx] = biayaPiksel + bKanan;
                        arah[ry][rx] = 1;
                    }
                }
            }
            int[] tmp = dpPrev;
            dpPrev = dpCur;
            dpCur = tmp;
        }

        int bestRx = sw / 2;
        int minTotal = Integer.MAX_VALUE;
        for (int rx = 0; rx < sw; rx++) {
            if (dpPrev[rx] < minTotal) {
                minTotal = dpPrev[rx];
                bestRx = rx;
            }
        }

        int[] seamX = new int[oh];
        int curRx = bestRx;
        for (int ry = oh - 1; ry >= 0; ry--) {
            seamX[ry] = xMin + curRx;
            curRx = Math.max(0, Math.min(sw - 1, curRx + arah[ry][curRx]));
        }
        // Haluskan jalur jahitan (moving average jendela 5) agar belokannya mulus
        haluskanJalur(seamX);
        return seamX;
    }

    /**
     * Mencari jalur jahitan mendatar {@code seamY[0..ow-1]} untuk penyisiran arah vertikal.
     */
    private static int[] cariJalurJahitMendatar(int[] kanvasPx, boolean[] terisi, int kw,
                                                int[] tilePx, int tw, int tx, int ty,
                                                int ox0, int ox1, int oy0, int oy1) {
        int ow = ox1 - ox0 + 1;
        int oh = oy1 - oy0 + 1;
        int margin = Math.max(2, oh / 6);
        int yMin = oy0 + margin;
        int yMax = oy1 - margin;
        if (yMin > yMax) {
            yMin = oy0;
            yMax = oy1;
        }
        int sh = yMax - yMin + 1;
        int[] dpPrev = new int[sh];
        int[] dpCur = new int[sh];
        short[][] arah = new short[ow][sh];

        for (int rx = 0; rx < ow; rx++) {
            int x = ox0 + rx;
            int kx = tx + x;
            for (int ry = 0; ry < sh; ry++) {
                int y = yMin + ry;
                int ky = ty + y;
                int biayaPiksel = terisi[ky * kw + kx]
                        ? biayaJahitPiksel(kanvasPx, kw, kx, ky, tilePx, tw, x, y, oy1 - oy0)
                        : 10000;
                if (rx == 0) {
                    dpCur[ry] = biayaPiksel;
                    arah[rx][ry] = 0;
                } else {
                    int bTengah = dpPrev[ry];
                    int bAtas = ry > 0 ? dpPrev[ry - 1] + 12 : Integer.MAX_VALUE / 2;
                    int bBawah = ry + 1 < sh ? dpPrev[ry + 1] + 12 : Integer.MAX_VALUE / 2;
                    if (bTengah <= bAtas && bTengah <= bBawah) {
                        dpCur[ry] = biayaPiksel + bTengah;
                        arah[rx][ry] = 0;
                    } else if (bAtas <= bBawah) {
                        dpCur[ry] = biayaPiksel + bAtas;
                        arah[rx][ry] = -1;
                    } else {
                        dpCur[ry] = biayaPiksel + bBawah;
                        arah[rx][ry] = 1;
                    }
                }
            }
            int[] tmp = dpPrev;
            dpPrev = dpCur;
            dpCur = tmp;
        }

        int bestRy = sh / 2;
        int minTotal = Integer.MAX_VALUE;
        for (int ry = 0; ry < sh; ry++) {
            if (dpPrev[ry] < minTotal) {
                minTotal = dpPrev[ry];
                bestRy = ry;
            }
        }

        int[] seamY = new int[ow];
        int curRy = bestRy;
        for (int rx = ow - 1; rx >= 0; rx--) {
            seamY[rx] = yMin + curRy;
            curRy = Math.max(0, Math.min(sh - 1, curRy + arah[rx][curRy]));
        }
        haluskanJalur(seamY);
        return seamY;
    }

    private static int biayaJahitPiksel(int[] kanvasPx, int kw, int kx, int ky,
                                        int[] tilePx, int tw, int x, int y, int span) {
        int kp = kanvasPx[ky * kw + kx];
        int tp = tilePx[y * tw + x];
        int dr = Math.abs(((kp >> 16) & 0xFF) - ((tp >> 16) & 0xFF));
        int dg = Math.abs(((kp >> 8) & 0xFF) - ((tp >> 8) & 0xFF));
        int db = Math.abs((kp & 0xFF) - (tp & 0xFF));
        int diffWarna = dr + dg + db;

        // Tambahkan penalti gradien lokal agar jahitan tidak memotong tepi tajam janjang sawit
        int gradT = 0;
        if (x > 0 && x + 1 < tw) {
            int pL = tilePx[y * tw + (x - 1)];
            int pR = tilePx[y * tw + (x + 1)];
            gradT = Math.abs(((pR >> 8) & 0xFF) - ((pL >> 8) & 0xFF));
        }
        return diffWarna + gradT;
    }

    private static void haluskanJalur(int[] s) {
        if (s.length < 5) return;
        int[] salin = s.clone();
        for (int i = 2; i < s.length - 2; i++) {
            s[i] = (salin[i - 2] + salin[i - 1] * 2 + salin[i] * 3 + salin[i + 1] * 2 + salin[i + 2]) / 9;
        }
    }

    private static int baurPiksel(int cLama, int cBaru, float wBaru) {
        float wLama = 1f - wBaru;
        int r = Math.round(((cLama >> 16) & 0xFF) * wLama + ((cBaru >> 16) & 0xFF) * wBaru);
        int g = Math.round(((cLama >> 8) & 0xFF) * wLama + ((cBaru >> 8) & 0xFF) * wBaru);
        int b = Math.round((cLama & 0xFF) * wLama + (cBaru & 0xFF) * wBaru);
        return 0xFF000000 | (clampByte(r) << 16) | (clampByte(g) << 8) | clampByte(b);
    }

    private static int clampByte(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    private static float clampGain(float g) {
        if (Float.isNaN(g) || Float.isInfinite(g)) return 1f;
        return Math.max(0.82f, Math.min(1.22f, g));
    }

    /**
     * Mengisi sudut kosong (bila ada pada penyisiran miring 2D) dengan menyalin piksel terisi terdekat
     * supaya tidak ada blok hitam bertingkat di pinggir foto.
     */
    private static void isiSudutKosong(int[] kanvasPx, boolean[] terisi, int kw, int kh, int yStart, int yEnd) {
        // Sapu per kolom (atas ke bawah, lalu bawah ke atas)
        for (int x = 0; x < kw; x++) {
            int firstY = -1;
            for (int y = yStart; y < yEnd; y++) {
                if (terisi[y * kw + x]) {
                    firstY = y;
                    break;
                }
            }
            if (firstY == -1) continue;
            int topColor = kanvasPx[firstY * kw + x];
            for (int y = yStart; y < firstY; y++) {
                kanvasPx[y * kw + x] = topColor;
                terisi[y * kw + x] = true;
            }
            int lastY = firstY;
            for (int y = yEnd - 1; y > firstY; y--) {
                if (terisi[y * kw + x]) {
                    lastY = y;
                    break;
                }
            }
            int bottomColor = kanvasPx[lastY * kw + x];
            for (int y = lastY + 1; y < yEnd; y++) {
                kanvasPx[y * kw + x] = bottomColor;
                terisi[y * kw + x] = true;
            }
        }
        // Sapu per baris (kiri ke kanan, lalu kanan ke kiri) untuk kolom yang belum terisi
        for (int y = yStart; y < yEnd; y++) {
            int row = y * kw;
            int firstX = -1;
            for (int x = 0; x < kw; x++) {
                if (terisi[row + x]) {
                    firstX = x;
                    break;
                }
            }
            if (firstX == -1) continue;
            int leftColor = kanvasPx[row + firstX];
            for (int x = 0; x < firstX; x++) {
                kanvasPx[row + x] = leftColor;
                terisi[row + x] = true;
            }
            int lastX = firstX;
            for (int x = kw - 1; x > firstX; x--) {
                if (terisi[row + x]) {
                    lastX = x;
                    break;
                }
            }
            int rightColor = kanvasPx[row + lastX];
            for (int x = lastX + 1; x < kw; x++) {
                kanvasPx[row + x] = rightColor;
                terisi[row + x] = true;
            }
        }
    }

    private void tulisTile(Bitmap mentah, int rotasi, List<Detection> deteksi) throws IOException {
        if (!folder.exists() && !folder.mkdirs()) throw new IOException("Gagal membuat folder panorama");
        Bitmap tegak = tegakkan(mentah, rotasi);
        try {
            if (tileW == 0 || berkasTile.isEmpty()) {
                tileW = tegak.getWidth();
                tileH = tegak.getHeight();
            }
            File f = new File(folder, "potongan_" + (nomor++) + ".jpg");
            try (FileOutputStream out = new FileOutputStream(f)) {
                tegak.compress(Bitmap.CompressFormat.JPEG, KUALITAS_TILE, out);
            }
            berkasTile.add(f);
            deteksiPerTile.add(deteksi != null ? new ArrayList<>(deteksi) : Collections.emptyList());
        } finally {
            tegak.recycle();
        }
    }

    private static boolean isColorDark(int color) {
        double darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return darkness >= 0.5;
    }

    private static final class KotakGlobal {
        final int classIndex;
        final float conf;
        final float x1, y1, x2, y2;
        KotakGlobal(int classIndex, float conf, float x1, float y1, float x2, float y2) {
            this.classIndex = classIndex;
            this.conf = conf;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }
    }

    private static float hitungIou(KotakGlobal a, KotakGlobal b) {
        float x1 = Math.max(a.x1, b.x1);
        float y1 = Math.max(a.y1, b.y1);
        float x2 = Math.min(a.x2, b.x2);
        float y2 = Math.min(a.y2, b.y2);
        float inter = Math.max(0, x2 - x1) * Math.max(0, y2 - y1);
        float areaA = (a.x2 - a.x1) * (a.y2 - a.y1);
        float areaB = (b.x2 - b.x1) * (b.y2 - b.y1);
        float union = areaA + areaB - inter;
        return union <= 0 ? 0 : inter / union;
    }

    /** Frame tegak dan dikecilkan ke lebar potongan, dalam satu langkah. */
    private static Bitmap tegakkan(Bitmap mentah, int rotasi) {
        boolean tukar = rotasi == 90 || rotasi == 270;
        int tegakW = tukar ? mentah.getHeight() : mentah.getWidth();
        Matrix m = new Matrix();
        m.postRotate(rotasi);
        float skala = Math.min(1f, LEBAR_TILE / (float) tegakW);
        if (skala < 1f) m.postScale(skala, skala);
        return Bitmap.createBitmap(mentah, 0, 0, mentah.getWidth(), mentah.getHeight(), m, true);
    }

    /** Gambar abu-abu kecil dalam posisi tegak, untuk menaksir pergeseran. */
    private static Gray grayTaksir(Bitmap mentah, int rotasi) {
        boolean tukar = rotasi == 90 || rotasi == 270;
        int tegakW = tukar ? mentah.getHeight() : mentah.getWidth();
        int tegakH = tukar ? mentah.getWidth() : mentah.getHeight();
        int ew = Math.min(LEBAR_TAKSIR, tegakW);
        int eh = Math.max(1, Math.round(ew * tegakH / (float) tegakW));
        int rw = tukar ? eh : ew;
        int rh = tukar ? ew : eh;
        Bitmap kecil = Bitmap.createScaledBitmap(mentah, rw, rh, true);
        int[] px = new int[rw * rh];
        kecil.getPixels(px, 0, rw, 0, 0, rw, rh);
        if (kecil != mentah) kecil.recycle();
        return Gray.dariArgb(px, rw, rh).putar(rotasi);
    }

    private void hapusTile() {
        for (File f : berkasTile) if (!f.delete()) f.deleteOnExit();
        berkasTile.clear();
        deteksiPerTile.clear();
    }

    private static void salin(File dari, File ke) throws IOException {
        try (java.io.InputStream in = new java.io.FileInputStream(dari); FileOutputStream out = new FileOutputStream(ke)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }
}
