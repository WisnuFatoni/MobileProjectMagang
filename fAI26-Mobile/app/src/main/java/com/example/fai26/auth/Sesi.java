package com.example.fai26.auth;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pengguna yang sedang login di HP ini beserta permission-nya. */
public final class Sesi {

    private static final String PREF = "fai26_sesi";
    private static final String KEY_USER = "user_id";
    private static final String KEY_NAMA = "nama";
    private static final String KEY_PERMISSION = "permission";
    private static final String KEY_PERAN = "peran";

    private Sesi() {
    }

    private static SharedPreferences pref(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static void masuk(Context c, long userId, String nama, String peran, List<String> permission) {
        pref(c).edit()
                .putLong(KEY_USER, userId)
                .putString(KEY_NAMA, nama)
                .putString(KEY_PERAN, peran)
                .putStringSet(KEY_PERMISSION, new HashSet<>(permission))
                .apply();
    }

    /**
     * Memperbarui permission dan nama peran pengguna yang sedang login (dari master yang baru diunduh), tanpa login
     * ulang. Tidak berbuat apa-apa bila tidak ada yang login atau {@code userId} bukan yang sedang login.
     */
    public static void perbarui(Context c, long userId, String peran, List<String> permission) {
        if (userId(c) != userId) return;
        pref(c).edit()
                .putString(KEY_PERAN, peran == null ? "" : peran)
                .putStringSet(KEY_PERMISSION, new HashSet<>(permission))
                .apply();
    }

    public static void keluar(Context c) {
        pref(c).edit().clear().apply();
    }

    /** @return user_id, atau -1 bila belum login. */
    public static long userId(Context c) {
        return pref(c).getLong(KEY_USER, -1L);
    }

    public static boolean sudahLogin(Context c) {
        return userId(c) > 0;
    }

    public static String nama(Context c) {
        return pref(c).getString(KEY_NAMA, "");
    }

    public static String peran(Context c) {
        return pref(c).getString(KEY_PERAN, "");
    }

    /**
     * 0 bila pengguna boleh melihat catatan semua orang (punya panen.koreksi), selain itu user_id sendiri.
     * Dipakai sebagai filter pada query beranda dan riwayat.
     */
    public static long filterPemilik(Context c) {
        // Mandor dan asisten (koreksi) serta krani (setujui) melihat seluruh catatan unit; akun lain hanya miliknya sendiri.
        return punya(c, "panen.koreksi") || punya(c, "panen.setujui") ? 0L : userId(c);
    }

    /**
     * Kotak Panen di layar awal. Dikendalikan hak akses.panen; sebelum hak itu sampai ke HP (master belum diperbarui sesudah server
     * ditingkatkan) akun yang punya hak panen tetapi belum punya satu pun hak akses.* tetap dianggap boleh membuka Panen.
     */
    public static boolean bolehPanen(Context c) {
        if (punya(c, "akses.panen")) return true;
        return !punya(c, "akses.ipd") && (punya(c, "panen.lihat") || punya(c, "panen.buat") || punya(c, "panen.setujui"));
    }

    /** Kotak IPD di layar awal (hak akses.ipd). */
    public static boolean bolehIpd(Context c) {
        return punya(c, "akses.ipd");
    }

    public static boolean punya(Context c, String permission) {
        Set<String> s = pref(c).getStringSet(KEY_PERMISSION, null);
        return s != null && s.contains(permission);
    }
}
