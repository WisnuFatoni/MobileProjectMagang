package com.example.fai26.panorama;

import java.util.ArrayList;
import java.util.List;

/**
 * Menaksir pergeseran gambar antara dua frame berurutan dengan mencocokkan isinya (selisih mutlak rata-rata) dari
 * resolusi kasar ke halus. Tanpa pustaka luar. Dipakai untuk menyambung frame penyisiran menjadi satu panorama.
 *
 * Konvensi: isi di titik P pada frame A tampak di titik P + (dx, dy) pada frame B. Bila kamera bergeser ke kanan,
 * isi bergerak ke kiri sehingga dx negatif. Titik asal (0,0) frame B berada di (-dx, -dy) pada koordinat frame A.
 */
public final class PenaksirGeser {

    /** Jari-jari pencarian pada resolusi paling kasar (piksel di level itu). */
    static final int JARI_KASAR = 6;
    /** Jari-jari penyempurnaan di tiap level yang lebih halus. */
    static final int JARI_HALUS = 2;
    /** Level paling kasar tidak boleh lebih kecil dari ini pada sisi pendeknya. */
    static final int SISI_MIN_KASAR = 20;
    /** Simpangan baku minimal supaya gambar dianggap punya tekstur. */
    static final double TEKSTUR_MIN = 5.0;
    /** Seberapa jelas pencocokan terbaik dibanding rata-rata kandidat (0..1); di bawah ini ditolak. */
    static final double KEPERCAYAAN_MIN = 0.25;

    public static final class Hasil {
        public final int dx;
        public final int dy;
        /** 0..1: seberapa jelas pencocokan ini dibanding kandidat lain. */
        public final double kepercayaan;
        /** false bila gambar polos, pencocokan samar, atau geserannya di luar jangkauan pencarian. */
        public final boolean sah;

        Hasil(int dx, int dy, double kepercayaan, boolean sah) {
            this.dx = dx;
            this.dy = dy;
            this.kepercayaan = kepercayaan;
            this.sah = sah;
        }

        static Hasil gagal() {
            return new Hasil(0, 0, 0, false);
        }
    }

    private PenaksirGeser() {
    }

    /** Jangkauan geser maksimum (piksel pada resolusi frame yang diberikan) bagi gambar berukuran w x h. */
    public static int jangkauan(int w, int h) {
        int level = 0;
        int sisi = Math.min(w, h);
        while (sisi / 2 >= SISI_MIN_KASAR) {
            sisi /= 2;
            level++;
        }
        return (JARI_KASAR - 1) << level;
    }

    public static Hasil taksir(Gray a, Gray b) {
        if (a.w != b.w || a.h != b.h) throw new IllegalArgumentException("ukuran frame harus sama");
        if (a.simpangan() < TEKSTUR_MIN || b.simpangan() < TEKSTUR_MIN) return Hasil.gagal();

        List<Gray> pa = piramida(a);
        List<Gray> pb = piramida(b);
        int kasar = pa.size() - 1;

        // Pencarian penuh di level paling kasar.
        double terbaik = Double.MAX_VALUE;
        double jumlah = 0;
        int n = 0;
        int bx = 0;
        int by = 0;
        for (int dy = -JARI_KASAR; dy <= JARI_KASAR; dy++) {
            for (int dx = -JARI_KASAR; dx <= JARI_KASAR; dx++) {
                double c = biaya(pa.get(kasar), pb.get(kasar), dx, dy);
                if (c == Double.MAX_VALUE) continue;
                jumlah += c;
                n++;
                if (c < terbaik) {
                    terbaik = c;
                    bx = dx;
                    by = dy;
                }
            }
        }
        if (n == 0 || terbaik == Double.MAX_VALUE) return Hasil.gagal();
        double rata = jumlah / n;
        double kepercayaan = rata <= 1e-9 ? 0 : Math.max(0, Math.min(1, (rata - terbaik) / rata));
        boolean ditepi = Math.abs(bx) == JARI_KASAR || Math.abs(by) == JARI_KASAR;

        // Penyempurnaan ke level yang lebih halus.
        for (int l = kasar - 1; l >= 0; l--) {
            int cx = bx * 2;
            int cy = by * 2;
            double lebihBaik = Double.MAX_VALUE;
            int nx = cx;
            int ny = cy;
            for (int dy = cy - JARI_HALUS; dy <= cy + JARI_HALUS; dy++) {
                for (int dx = cx - JARI_HALUS; dx <= cx + JARI_HALUS; dx++) {
                    double c = biaya(pa.get(l), pb.get(l), dx, dy);
                    if (c < lebihBaik) {
                        lebihBaik = c;
                        nx = dx;
                        ny = dy;
                    }
                }
            }
            bx = nx;
            by = ny;
        }
        boolean sah = kepercayaan >= KEPERCAYAAN_MIN && !ditepi;
        return new Hasil(bx, by, kepercayaan, sah);
    }

    private static List<Gray> piramida(Gray g) {
        List<Gray> p = new ArrayList<>();
        p.add(g);
        Gray t = g;
        while (Math.min(t.w, t.h) / 2 >= SISI_MIN_KASAR) {
            t = t.setengah();
            p.add(t);
        }
        return p;
    }

    /**
     * Selisih mutlak rata-rata pada bagian yang saling tumpang: B(x, y) dibandingkan dengan A(x - dx, y - dy).
     * Mengembalikan Double.MAX_VALUE bila tumpang tindihnya kurang dari setengah luas gambar.
     */
    static double biaya(Gray a, Gray b, int dx, int dy) {
        int x0 = Math.max(0, dx);
        int x1 = Math.min(b.w, a.w + dx);
        int y0 = Math.max(0, dy);
        int y1 = Math.min(b.h, a.h + dy);
        int lebar = x1 - x0;
        int tinggi = y1 - y0;
        long n = (long) lebar * tinggi;
        if (lebar <= 0 || tinggi <= 0 || n * 2 < (long) a.w * a.h) return Double.MAX_VALUE;
        long sumA = 0;
        long sumB = 0;
        for (int y = y0; y < y1; y++) {
            int ib = y * b.w;
            int ia = (y - dy) * a.w - dx;
            for (int x = x0; x < x1; x++) {
                sumB += b.d[ib + x];
                sumA += a.d[ia + x];
            }
        }
        int rataA = (int) ((sumA + n / 2) / n);
        int rataB = (int) ((sumB + n / 2) / n);
        long jumlah = 0;
        for (int y = y0; y < y1; y++) {
            int ib = y * b.w;
            int ia = (y - dy) * a.w - dx;
            for (int x = x0; x < x1; x++) {
                int selisih = (b.d[ib + x] - rataB) - (a.d[ia + x] - rataA);
                jumlah += selisih < 0 ? -selisih : selisih;
            }
        }
        return (double) jumlah / (double) n;
    }
}
