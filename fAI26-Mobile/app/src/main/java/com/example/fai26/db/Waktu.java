package com.example.fai26.db;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Semua waktu di database disimpan UTC, format ISO 8601: YYYY-MM-DDTHH:MM:SSZ. */
public final class Waktu {

    private Waktu() {
    }

    public static String utc(long millis) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(millis));
    }

    public static String utcSekarang() {
        return utc(System.currentTimeMillis());
    }

    /** Kebalikan {@link #utc(long)}. Mengembalikan 0 bila teks tidak bisa dibaca. */
    public static long keMillis(String utc) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d = f.parse(utc);
            return d == null ? 0L : d.getTime();
        } catch (java.text.ParseException e) {
            return 0L;
        }
    }

    /** Tanggal lokal hari ini, format YYYY-MM-DD (dipakai untuk tanggal panen). */
    public static String tanggalLokalHariIni() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }
}
