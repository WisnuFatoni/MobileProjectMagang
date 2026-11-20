package com.example.fai26.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * Pekerjaan latar sinkronisasi. Dijalankan WorkManager hanya saat ada jaringan (lihat SyncJadwal).
 * Kegagalan sementara (jaringan atau server bermasalah) dijawab retry supaya dicoba lagi dengan jeda yang makin panjang;
 * selain itu sukses. Jeda coba-ulang per catatan tetap diatur oleh antrean (SyncService), bukan oleh WorkManager.
 */
public class SyncWorker extends Worker {

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        SyncService.Ringkasan h = SyncRunner.jalankanSinkron(getApplicationContext(), false);
        if (h.bisaDicobaLagi && !h.perluLogin) return Result.retry();
        return Result.success();
    }
}
