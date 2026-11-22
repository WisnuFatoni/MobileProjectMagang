package com.example.fai26.auth;

import android.content.Context;

import com.example.fai26.Fai26App;
import com.example.fai26.db.AppDatabase;
import com.example.fai26.db.entity.AppUser;
import com.example.fai26.sync.PengaturanSync;

/**
 * Memastikan token online di HP milik akun yang sedang login. Login offline hanya mengganti akun di layar; tanpa pemeriksaan ini
 * token pengguna sebelumnya tetap dipakai ke server (menyetujui atas nama orang lain, atau ditolak 403 karena beda hak).
 * Pemeriksaan saat login ada di {@link LoginService}; kelas ini membereskan HP yang SUDAH telanjur dalam keadaan itu.
 */
public final class SesiToken {

    private SesiToken() {
    }

    /** Dijalankan di latar (menyentuh basis data). Mencatat akun yang sedang login dan membuang token milik akun lain. */
    public static void cocokkanDiLatar(Context c) {
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                cocokkan(app);
            } catch (Exception e) {
                // pemeriksaan gagal: tidak menghalangi aplikasi dibuka
            }
        }, "sesi-token").start();
    }

    /** Memanggil dari utas latar. @return true bila ada token milik akun lain yang dibuang. */
    static boolean cocokkan(Context app) throws InterruptedException {
        PengaturanSync pref = new PengaturanSync(app);
        long id = Sesi.userId(app);
        if (id <= 0) return false;
        Fai26App.tungguSiap();
        AppUser u = AppDatabase.getInstance(app).akunDao().cariId(id);
        if (u == null) return false;
        return pref.aturAkunAktif(u.username);
    }
}
