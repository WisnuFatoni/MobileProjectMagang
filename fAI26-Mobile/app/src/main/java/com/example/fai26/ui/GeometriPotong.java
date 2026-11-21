package com.example.fai26.ui;

/**
 * Hitungan murni (tanpa Android) untuk pemotong foto profil. Gambar digambar dengan skala {@code s} (piksel layar per piksel gambar)
 * dan pojok kiri-atasnya di (tx, ty). Kotak potong adalah persegi {@code sisi} dengan pojok kiri-atas (kiri, atas); gambar tidak
 * boleh menyisakan celah kosong di dalam kotak itu.
 */
public final class GeometriPotong {

    /** Pembesaran maksimum relatif terhadap skala minimum (gambar yang terlalu diperbesar menjadi buram). */
    public static final float ZOOM_MAKS = 5f;

    private GeometriPotong() {
    }

    /** Skala terkecil agar gambar bw x bh menutupi seluruh kotak sisi x sisi. */
    public static float skalaMin(int bw, int bh, float sisi) {
        return Math.max(sisi / bw, sisi / bh);
    }

    public static float jepitSkala(float s, int bw, int bh, float sisi) {
        float min = skalaMin(bw, bh, sisi);
        return Math.max(min, Math.min(min * ZOOM_MAKS, s));
    }

    /** Menjepit posisi supaya kotak potong selalu tertutup gambar. Mengembalikan {tx, ty}. */
    public static float[] jepitGeser(float tx, float ty, float s, int bw, int bh, float kiri, float atas, float sisi) {
        float lebar = bw * s;
        float tinggi = bh * s;
        float x = Math.max(kiri + sisi - lebar, Math.min(kiri, tx));
        float y = Math.max(atas + sisi - tinggi, Math.min(atas, ty));
        return new float[]{x, y};
    }

    /** Posisi awal: gambar menutupi kotak dan berada di tengahnya. Mengembalikan {tx, ty, s}. */
    public static float[] awal(int bw, int bh, float kiri, float atas, float sisi) {
        float s = skalaMin(bw, bh, sisi);
        return new float[]{kiri + (sisi - bw * s) / 2f, atas + (sisi - bh * s) / 2f, s};
    }

    /** Mengubah skala dengan titik (px, py) tetap di tempat (titik di bawah jari). Mengembalikan {tx, ty, s} yang sudah dijepit. */
    public static float[] zoomDi(float tx, float ty, float s, float sBaru, float px, float py,
                                 int bw, int bh, float kiri, float atas, float sisi) {
        float sj = jepitSkala(sBaru, bw, bh, sisi);
        float nx = px - (px - tx) * sj / s;
        float ny = py - (py - ty) * sj / s;
        float[] g = jepitGeser(nx, ny, sj, bw, bh, kiri, atas, sisi);
        return new float[]{g[0], g[1], sj};
    }

    /**
     * Daerah gambar (dalam piksel gambar) yang tampak di kotak potong: {kiri, atas, sisi}. Dipakai untuk memotong hasil akhir
     * pada resolusi penuh.
     */
    public static float[] daerahSumber(float tx, float ty, float s, float kiri, float atas, float sisi) {
        return new float[]{(kiri - tx) / s, (atas - ty) / s, sisi / s};
    }
}
