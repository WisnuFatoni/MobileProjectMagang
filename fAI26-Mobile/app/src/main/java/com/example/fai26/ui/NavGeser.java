package com.example.fai26.ui;

/**
 * Hitungan murni untuk menyeret kapsul penanda di menu bawah (dipisah supaya bisa diuji tanpa Android).
 * Semua posisi adalah translasi X kiri kapsul; tab ke-i berada di awal + i * lebarItem.
 */
final class NavGeser {

    private NavGeser() {
    }

    /** Membatasi posisi ke rentang tab pertama sampai tab terakhir. */
    static float batas(float tx, float awal, float lebarItem, int jumlah) {
        float akhir = awal + Math.max(0, jumlah - 1) * lebarItem;
        return Math.max(awal, Math.min(akhir, tx));
    }

    /** Indeks tab yang paling dekat dengan posisi kapsul. */
    static int indeksTerdekat(float tx, float awal, float lebarItem, int jumlah) {
        if (jumlah <= 0 || lebarItem <= 0) return 0;
        int i = Math.round((tx - awal) / lebarItem);
        return Math.max(0, Math.min(jumlah - 1, i));
    }

    /**
     * Skala "lensa": ikon yang berada di bawah kapsul diperbesar, makin jauh makin kecil sampai 1 pada jarak
     * satu tab. Meniru efek pembesaran kaca pada Liquid Glass.
     */
    static float skalaLensa(float pusatKapsul, float pusatTab, float lebarItem, float maks) {
        if (lebarItem <= 0) return 1f;
        float d = Math.abs(pusatTab - pusatKapsul) / lebarItem;
        return 1f + (maks - 1f) * Math.max(0f, 1f - d);
    }

    /** Posisi kapsul tepat di tab ke-indeks. */
    static float posisiTab(int indeks, float awal, float lebarItem) {
        return awal + indeks * lebarItem;
    }
}
