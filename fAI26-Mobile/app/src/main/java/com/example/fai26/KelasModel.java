package com.example.fai26;

/**
 * Enam kelas model, urutannya sama dengan indeks 0 sampai 5 pada keluaran model.
 * Jangan diubah kecuali model dilatih ulang.
 */
public final class KelasModel {

    public static final String[] NAMA = {
            "Abnormal", "Empty Bunch", "Overripe", "Ripe", "Underripe", "Unripe"
    };

    /** Istilah tampilan di kebun. Perlu dicocokkan dengan pembimbing. */
    public static final String[] TAMPIL = {
            "Abnormal", "Tandan Kosong", "Lewat Matang", "Matang", "Kurang Matang", "Mentah"
    };

    /** Warna kotak per kelas (ARGB). */
    public static final int[] WARNA = {
            0xFFE91E63, 0xFF9E9E9E, 0xFFFF9800, 0xFF4CAF50, 0xFFFFEB3B, 0xFF00BCD4
    };

    public static final int JUMLAH = NAMA.length;

    private KelasModel() {
    }
}
