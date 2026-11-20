package com.example.fai26.panorama;

/** Gambar abu-abu kecil (0..255) untuk menaksir pergeseran antar frame. Murni Java supaya bisa diuji di JVM. */
public final class Gray {

    public final int w;
    public final int h;
    public final int[] d;
    /** Rata-rata kecerahan (dibulatkan); dikurangkan saat membandingkan supaya perubahan eksposur tidak mengganggu. */
    public final int rata;

    public Gray(int w, int h, int[] d) {
        if (d.length != w * h) throw new IllegalArgumentException("ukuran data tidak cocok");
        this.w = w;
        this.h = h;
        this.d = d;
        long jumlah = 0;
        for (int v : d) jumlah += v;
        this.rata = d.length == 0 ? 0 : (int) Math.round((double) jumlah / d.length);
    }

    /** Dari piksel ARGB (urutan Bitmap.getPixels): kecerahan = 0,30 R + 0,59 G + 0,11 B. */
    public static Gray dariArgb(int[] argb, int w, int h) {
        int[] g = new int[w * h];
        for (int i = 0; i < g.length; i++) {
            int p = argb[i];
            g[i] = (77 * ((p >> 16) & 0xFF) + 150 * ((p >> 8) & 0xFF) + 29 * (p & 0xFF)) >> 8;
        }
        return new Gray(w, h, g);
    }

    /** Memutar searah jarum jam 0, 90, 180, atau 270 derajat (sama dengan rotationDegrees CameraX untuk menegakkan frame). */
    public Gray putar(int derajat) {
        switch (((derajat % 360) + 360) % 360) {
            case 0:
                return this;
            case 90: {
                int[] o = new int[d.length];
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) o[x * h + (h - 1 - y)] = d[y * w + x];
                return new Gray(h, w, o);
            }
            case 180: {
                int[] o = new int[d.length];
                for (int i = 0; i < d.length; i++) o[d.length - 1 - i] = d[i];
                return new Gray(w, h, o);
            }
            case 270: {
                int[] o = new int[d.length];
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) o[(w - 1 - x) * h + y] = d[y * w + x];
                return new Gray(h, w, o);
            }
            default:
                throw new IllegalArgumentException("rotasi harus kelipatan 90: " + derajat);
        }
    }

    /** Mengecilkan setengah (rata-rata kotak 2x2). */
    public Gray setengah() {
        int nw = w / 2;
        int nh = h / 2;
        int[] o = new int[nw * nh];
        for (int y = 0; y < nh; y++) {
            for (int x = 0; x < nw; x++) {
                int i = (2 * y) * w + 2 * x;
                o[y * nw + x] = (d[i] + d[i + 1] + d[i + w] + d[i + w + 1] + 2) >> 2;
            }
        }
        return new Gray(nw, nh, o);
    }

    /** Simpangan baku kecerahan: kecil berarti permukaan polos tanpa tekstur (tidak bisa disejajarkan). */
    public double simpangan() {
        if (d.length == 0) return 0;
        double s = 0;
        for (int v : d) s += (v - rata) * (double) (v - rata);
        return Math.sqrt(s / d.length);
    }
}
