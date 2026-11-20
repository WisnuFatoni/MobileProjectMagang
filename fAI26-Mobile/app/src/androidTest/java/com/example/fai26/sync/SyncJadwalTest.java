package com.example.fai26.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.ListenableWorker;
import androidx.work.NetworkType;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.TestListenableWorkerBuilder;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/** Penjadwalan sinkronisasi latar lewat WorkManager. */
@RunWith(AndroidJUnit4.class)
public class SyncJadwalTest {

    private Context ctx;

    @Before
    public void siapkan() {
        ctx = ApplicationProvider.getApplicationContext();
        SyncJadwal.batalkan(ctx);
    }

    @After
    public void bersihkan() {
        SyncJadwal.batalkan(ctx);
        // Aplikasi memasang jadwal berkala sendiri saat dibuka; kembalikan supaya aplikasi tetap sinkron.
        SyncJadwal.pasangBerkala(ctx);
    }

    private List<WorkInfo> info(String nama) throws Exception {
        return WorkManager.getInstance(ctx).getWorkInfosForUniqueWork(nama).get();
    }

    @Test
    public void berkalaTerpasangSatuKaliDanButuhJaringan() throws Exception {
        SyncJadwal.pasangBerkala(ctx);
        SyncJadwal.pasangBerkala(ctx); // aman diulang: tidak menggandakan
        List<WorkInfo> daftar = info(SyncJadwal.NAMA_BERKALA);
        assertEquals(1, daftar.size());
        WorkInfo w = daftar.get(0);
        assertTrue(w.getState() == WorkInfo.State.ENQUEUED || w.getState() == WorkInfo.State.RUNNING);
        assertEquals(NetworkType.CONNECTED, w.getConstraints().getRequiredNetworkType());
        assertEquals(15 * 60_000L, w.getPeriodicityInfo().getRepeatIntervalMillis());
    }

    @Test
    public void segeraMenungguJaringanBilaOffline() throws Exception {
        SyncJadwal.segera(ctx);
        List<WorkInfo> daftar = info(SyncJadwal.NAMA_SEGERA);
        assertEquals(1, daftar.size());
        assertNotNull(daftar.get(0));
        assertEquals(NetworkType.CONNECTED, daftar.get(0).getConstraints().getRequiredNetworkType());
    }

    @Test
    public void batalkanMenghapusSemuaJadwal() throws Exception {
        SyncJadwal.pasangBerkala(ctx);
        SyncJadwal.segera(ctx);
        SyncJadwal.batalkan(ctx);
        for (WorkInfo w : info(SyncJadwal.NAMA_BERKALA)) assertEquals(WorkInfo.State.CANCELLED, w.getState());
        // Yang sekali jalan bisa sudah selesai sebelum dibatalkan (HP berjaringan menjalankannya seketika).
        for (WorkInfo w : info(SyncJadwal.NAMA_SEGERA)) {
            assertTrue("state: " + w.getState(),
                    w.getState() == WorkInfo.State.CANCELLED || w.getState() == WorkInfo.State.SUCCEEDED);
        }
    }

    @Test
    public void segeraBenarBenarDijalankanWorkManagerBilaAdaJaringan() throws Exception {
        SyncJadwal.segera(ctx);
        WorkInfo.State akhir = null;
        for (int i = 0; i < 30; i++) {
            List<WorkInfo> daftar = info(SyncJadwal.NAMA_SEGERA);
            akhir = daftar.get(0).getState();
            if (akhir != WorkInfo.State.ENQUEUED && akhir != WorkInfo.State.RUNNING) break;
            Thread.sleep(1000);
        }
        // Tanpa jaringan di HP uji, pekerjaan tetap menunggu (ENQUEUED): tes ini tidak berlaku.
        org.junit.Assume.assumeTrue("HP uji tanpa jaringan", akhir != WorkInfo.State.ENQUEUED);
        // Dengan jaringan: berjalan sampai selesai atau dijadwal ulang (retry), tidak pernah gagal.
        assertTrue("state: " + akhir, akhir == WorkInfo.State.SUCCEEDED || akhir == WorkInfo.State.ENQUEUED
                || akhir == WorkInfo.State.RUNNING);
        assertTrue(akhir != WorkInfo.State.FAILED);
    }

    @Test
    public void workerSelesaiTanpaGagalDanTidakMelemparGalat() throws Exception {
        ListenableWorker w = TestListenableWorkerBuilder.from(ctx, SyncWorker.class).build();
        ListenableWorker.Result r = w.startWork().get();
        // Sukses (tidak ada yang perlu dikirim atau perlu login) atau retry (jaringan): tidak pernah failure.
        assertTrue(r instanceof ListenableWorker.Result.Success || r instanceof ListenableWorker.Result.Retry);
    }
}
