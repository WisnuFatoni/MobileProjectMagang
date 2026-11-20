package com.example.fai26;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class Format {

    private Format() {
    }

    private static final Locale ID = new Locale("id", "ID");

    /** Bilangan bulat dengan pemisah ribuan titik: 3790 menjadi "3.790". */
    static String angka(long nilai) {
        return java.text.NumberFormat.getIntegerInstance(ID).format(nilai);
    }

    /** Desimal dengan koma, satu angka di belakang koma bila perlu: 28.5 menjadi "28,5", 30.0 menjadi "30". */
    static String desimal(double nilai) {
        java.text.NumberFormat f = java.text.NumberFormat.getNumberInstance(ID);
        f.setMaximumFractionDigits(1);
        f.setMinimumFractionDigits(0);
        return f.format(nilai);
    }

    /** "2026-11-10" menjadi "10/11/2026". Teks lain dikembalikan apa adanya. */
    static String tanggal(String yyyyMmDd) {
        if (yyyyMmDd == null || yyyyMmDd.length() != 10 || yyyyMmDd.charAt(4) != '-') return yyyyMmDd;
        return yyyyMmDd.substring(8, 10) + "/" + yyyyMmDd.substring(5, 7) + "/" + yyyyMmDd.substring(0, 4);
    }

    static String waktu(long millis) {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("id", "ID")).format(new Date(millis));
    }
}
