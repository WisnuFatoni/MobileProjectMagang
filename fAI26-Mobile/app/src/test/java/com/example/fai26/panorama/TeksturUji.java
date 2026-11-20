package com.example.fai26.panorama;

import java.util.Random;

/** Tekstur sintetis bergaya alami (derau nilai berlapis-lapis) untuk menguji penyejajaran frame tanpa foto sungguhan. */
final class TeksturUji {

    final int w;
    final int h;
    final int[] d;

    TeksturUji(int w, int h, long benih) {
        this.w = w;
        this.h = h;
        this.d = new int[w * h];
        double[] hasil = new double[w * h];
        int[] sel = {96, 48, 24, 12, 6};
        double amplitudo = 1.0;
        double total = 0;
        Random acak = new Random(benih);
        for (int sIdx = 0; sIdx < sel.length; sIdx++) {
            int s = sel[sIdx];
            int gw = w / s + 2;
            int gh = h / s + 2;
            double[] grid = new double[gw * gh];
            for (int i = 0; i < grid.length; i++) grid[i] = acak.nextDouble();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    double fx = (double) x / s;
                    double fy = (double) y / s;
                    int gx = (int) fx;
                    int gy = (int) fy;
                    double tx = fx - gx;
                    double ty = fy - gy;
                    double a = grid[gy * gw + gx] * (1 - tx) + grid[gy * gw + gx + 1] * tx;
                    double b = grid[(gy + 1) * gw + gx] * (1 - tx) + grid[(gy + 1) * gw + gx + 1] * tx;
                    hasil[y * w + x] += amplitudo * (a * (1 - ty) + b * ty);
                }
            }
            total += amplitudo;
            amplitudo *= 0.7;
        }
        for (int i = 0; i < d.length; i++) d[i] = (int) Math.round(255 * hasil[i] / total);
    }

    /** Memotong jendela fw x fh yang kiri-atasnya (x0, y0); tambahan kecerahan dan derau acak opsional. */
    Gray potong(int x0, int y0, int fw, int fh, int tambahTerang, int derau, long benih) {
        Random acak = new Random(benih);
        int[] o = new int[fw * fh];
        for (int y = 0; y < fh; y++) {
            for (int x = 0; x < fw; x++) {
                int v = d[(y0 + y) * w + (x0 + x)] + tambahTerang;
                if (derau > 0) v += acak.nextInt(2 * derau + 1) - derau;
                o[y * fw + x] = Math.max(0, Math.min(255, v));
            }
        }
        return new Gray(fw, fh, o);
    }
}
