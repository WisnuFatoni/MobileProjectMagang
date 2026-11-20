package com.example.fai26.sync;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/**
 * Penjadwalan sinkronisasi di latar lewat WorkManager (tetap jalan walau aplikasi ditutup):
 * - berkala: tiap 15 menit (batas terkecil WorkManager), hanya saat ada jaringan;
 * - segera: sekali, secepatnya begitu ada jaringan (setelah login online atau mandor menyetujui). Bila tidak ada
 *   sinyal, ia menunggu sampai sinyal kembali, jadi pengguna tidak perlu menekan tombol.
 */
public final class SyncJadwal {

    public static final String NAMA_BERKALA = "fai26-sync-berkala";
    public static final String NAMA_SEGERA = "fai26-sync-segera";
    static final long PERIODE_MENIT = 15;

    private SyncJadwal() {
    }

    private static Constraints butuhJaringan() {
        return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
    }

    /** Memasang sinkronisasi berkala. Aman dipanggil berulang: yang sudah terpasang dipertahankan. */
    public static void pasangBerkala(Context context) {
        PeriodicWorkRequest r = new PeriodicWorkRequest.Builder(SyncWorker.class, PERIODE_MENIT, TimeUnit.MINUTES)
                .setConstraints(butuhJaringan())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniquePeriodicWork(NAMA_BERKALA, ExistingPeriodicWorkPolicy.KEEP, r);
    }

    /** Sekali jalan secepatnya begitu ada jaringan. Bila sedang berjalan, satu putaran lagi dijalankan sesudahnya. */
    public static void segera(Context context) {
        OneTimeWorkRequest r = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(butuhJaringan())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(NAMA_SEGERA, ExistingWorkPolicy.APPEND_OR_REPLACE, r);
    }

    /** Membatalkan semua jadwal (dipakai tes). */
    public static void batalkan(Context context) {
        WorkManager wm = WorkManager.getInstance(context.getApplicationContext());
        wm.cancelUniqueWork(NAMA_BERKALA);
        wm.cancelUniqueWork(NAMA_SEGERA);
    }
}
