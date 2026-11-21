package com.example.fai26.sync;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.fai26.Fai26App;
import com.example.fai26.db.AppDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Menjalankan SyncService di thread latar dan mengembalikan hasilnya ke thread utama. */
public final class SyncRunner {

    public interface Pendengar {
        void selesai(SyncService.Ringkasan hasil);
    }

    private static final ExecutorService LATAR = Executors.newSingleThreadExecutor();
    private static final Handler UTAMA = new Handler(Looper.getMainLooper());

    private SyncRunner() {
    }

    /**
     * Kirim sekarang. @param paksa mengabaikan jeda coba-ulang. @param pendengar boleh null (jalan diam-diam,
     * misalnya setelah mandor menyetujui catatan).
     */
    public static void jalankan(Context context, boolean paksa, Pendengar pendengar) {
        final Context app = context.getApplicationContext();
        LATAR.execute(() -> {
            SyncService.Ringkasan hasil = jalankanSinkron(app, paksa);
            if (pendengar != null) UTAMA.post(() -> pendengar.selesai(hasil));
        });
    }

    /** Satu putaran sinkronisasi di thread pemanggil (blocking). Dipakai tombol dan WorkManager. */
    public static SyncService.Ringkasan jalankanSinkron(Context context, boolean paksa) {
        final Context app = context.getApplicationContext();
        try {
            Fai26App.tungguSiap();
            PengaturanSync pref = new PengaturanSync(app);
            if (!pref.serverSudahDiatur()) {
                SyncService.Ringkasan r = new SyncService.Ringkasan();
                r.masalah = "Alamat server belum diatur";
                return r;
            }
            HttpServerApi api = new HttpServerApi(pref.serverUrl());
            SyncService.Ringkasan r = new SyncService(AppDatabase.getInstance(app), api, pref).jalankan(paksa);
            // Foto profil menyusul setelah data kerja (tidak menggagalkan sinkronisasi bila gagal).
            FotoProfilSync.jalankan(app, api, pref);
            return r;
        } catch (Exception e) {
            SyncService.Ringkasan r = new SyncService.Ringkasan();
            r.masalah = "Sinkronisasi gagal: " + e.getMessage();
            return r;
        }
    }
}
