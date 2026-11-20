package com.example.fai26.sync;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Pemberitahuan bahwa master (TPH, blok, pemanen, akun) di HP baru saja berubah karena diunduh dari server, supaya
 * layar yang menampilkannya (misalnya daftar TPH di Beranda) memuat ulang tanpa harus dibuka ulang. Pendengar
 * dipanggil di thread utama. Daftarkan di onStart dan lepas di onStop agar tidak bocor.
 */
public final class PerubahanMaster {

    public interface Pendengar {
        void masterBerubah();

        /** Hak akses pengguna yang sedang login baru saja diperbarui dari master (cache sesi sudah segar). */
        default void hakBerubah() {
        }
    }

    private static final CopyOnWriteArraySet<Pendengar> PENDENGAR = new CopyOnWriteArraySet<>();
    private static final Handler UTAMA = new Handler(Looper.getMainLooper());

    private PerubahanMaster() {
    }

    public static void daftar(Pendengar p) {
        PENDENGAR.add(p);
    }

    public static void lepas(Pendengar p) {
        PENDENGAR.remove(p);
    }

    /** Dipanggil setelah cache hak sesi diperbarui. Aman dari thread mana pun. */
    public static void umumkanHak() {
        UTAMA.post(() -> {
            for (Pendengar p : PENDENGAR) p.hakBerubah();
        });
    }

    /** Dipanggil setelah master baru tersimpan (transaksi sudah selesai). Aman dari thread mana pun. */
    public static void umumkan() {
        UTAMA.post(() -> {
            for (Pendengar p : PENDENGAR) p.masterBerubah();
        });
    }
}
