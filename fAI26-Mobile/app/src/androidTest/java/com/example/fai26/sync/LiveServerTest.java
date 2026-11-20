package com.example.fai26.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.fai26.auth.AuthService;
import com.example.fai26.auth.LoginService;
import com.example.fai26.db.AppDatabase;
import com.example.fai26.db.PanenRepository;
import com.example.fai26.db.Waktu;
import com.example.fai26.db.dao.Hasil;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.List;

/**
 * Uji ujung-ke-ujung terhadap API sungguhan (fai26-api). Hanya jalan bila argumen liveServer diberikan:
 * ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.liveServer=http://localhost:8080
 * (HP lewat USB: adb reverse tcp:8080 tcp:8080). Tambahkan -Pandroid.testInstrumentationRunnerArguments.tungguStatus=120
 * untuk menunggu ERP mengubah status di server.
 */
@RunWith(AndroidJUnit4.class)
public class LiveServerTest {

    private static final String BERKAS_DB = "live_uji.db";
    private static final String BERKAS_PREF = "fai26_sync_live";

    private String url() {
        String u = InstrumentationRegistry.getArguments().getString("liveServer");
        assumeTrue("liveServer tidak diberikan: tes dilewati", u != null && !u.isEmpty());
        return u;
    }

    private AppDatabase buka(Context ctx) {
        return Room.databaseBuilder(ctx, AppDatabase.class, BERKAS_DB).addCallback(AppDatabase.callback()).build();
    }

    @Test
    public void loginKirimCatatanFotoAuditDanStatus() throws Exception {
        String server = url();
        Context ctx = ApplicationProvider.getApplicationContext();
        ctx.deleteDatabase(BERKAS_DB);
        AppDatabase room = buka(ctx);
        try {
            // HP baru: TANPA master contoh. Semua master (blok, TPH, pemanen, akun) datang dari server.
            room.getOpenHelper().getWritableDatabase().execSQL(
                    "INSERT INTO device (device_id, aktif, dibuat_pada) VALUES ('dev-live', 1, ?)",
                    new Object[]{Waktu.utcSekarang()});
            AuthService auth = new AuthService(room, "dev-live");

            PengaturanSync pref = new PengaturanSync(ctx, BERKAS_PREF);
            pref.aturServerUrl(server);
            pref.hapusToken();
            pref.aturStatusSejak(0);

            // 1. Login online sungguhan ke server (krani1/krani123 dari seed server).
            LoginService.Masuk m = new LoginService(auth, new HttpServerApi(server), pref, new MasterSync(room),
                    "11111111-1111-4111-8111-aaaaaaaaaaaa", "HP uji live", "Android 14", "1.0").masuk("krani1", "krani123");
            assertTrue("login online", m.online);
            assertEquals(AuthService.Status.BERHASIL, m.hasil.status);
            assertTrue(pref.adaToken());
            // Master turun dari server saat login: blok, TPH, pemanen, dan akun (mandor1 ikut, dengan rolenya).
            assertEquals(2, count(room, "blok"));
            assertEquals(5, count(room, "tph"));
            assertEquals(5, count(room, "pemanen"));
            assertTrue(new MasterSync(room).versiLokal().startsWith("m-"));
            assertTrue(auth.permission(2).contains("panen.setujui"));

            // 2. Krani mencatat dengan foto, mandor menyetujui.
            File foto = new File(ctx.getCacheDir(), "foto_live.jpg");
            Bitmap b = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888);
            new Canvas(b).drawColor(Color.GREEN);
            try (FileOutputStream out = new FileOutputStream(foto)) {
                b.compress(Bitmap.CompressFormat.JPEG, 90, out);
            }
            List<Hasil.TphPilihan> tph = room.masterDao().daftarTph();
            PanenRepository.Permintaan r = new PanenRepository.Permintaan();
            r.unitId = tph.get(0).unitId;
            r.blokId = tph.get(0).blokId;
            r.tphId = tph.get(0).tphId;
            r.pemanenId = 1L;
            r.tanggalPanen = Waktu.tanggalLokalHariIni();
            r.brondolanKg = 12.5;
            r.catatan = "uji ujung-ke-ujung";
            r.jumlahAi = new int[]{0, 1, 2, 7, 0, 1};
            r.jumlahFinal = new int[]{0, 1, 2, 9, 0, 1};
            r.foto = foto;
            r.deteksi = new float[]{3, 0.9f, 0.1f, 0.2f, 0.4f, 0.5f};
            File folder = new File(ctx.getCacheDir(), "foto_live");
            String id = new PanenRepository(room, folder, "dev-live", 1L).simpan(r);
            assertEquals(1, new PanenRepository(room, folder, "dev-live", 2L).setujui(Collections.singletonList(id)));

            // 3. Kirim ke API sungguhan.
            SyncService.Ringkasan h = new SyncService(room, new HttpServerApi(server), pref).jalankan(true);
            assertNull("masalah: " + h.masalah, h.masalah);
            assertEquals(1, h.panenTerkirim);
            assertEquals(1, h.fotoTerkirim);
            assertTrue("audit terkirim", h.auditTerkirim > 0);

            try (Cursor c = room.getOpenHelper().getReadableDatabase()
                    .query("SELECT status FROM panen_header WHERE panen_id = '" + id + "'")) {
                c.moveToFirst();
                assertEquals("TERKIRIM", c.getString(0));
            }
            // 3b. Krani merekam titik GPS sebuah TPH yang belum punya koordinat; titiknya harus sampai ke server.
            //     (Server tidak menimpa titik yang sudah ada, jadi tiap TPH hanya bisa dipakai sekali untuk uji ini.)
            long tphKosong = -1;
            try (Cursor c = room.getOpenHelper().getReadableDatabase()
                    .query("SELECT tph_id FROM tph WHERE lat IS NULL AND lon IS NULL ORDER BY tph_id LIMIT 1")) {
                if (c.moveToFirst()) tphKosong = c.getLong(0);
            }
            if (tphKosong > 0) {
                PanenRepository krani = new PanenRepository(room, folder, "dev-live", 1L);
                assertTrue(krani.rekamKoordinatTph(tphKosong, 3.123456, 101.654321, 8.0));
                SyncService.Ringkasan hk = new SyncService(room, new HttpServerApi(server), pref).jalankan(true);
                assertNull("masalah koordinat: " + hk.masalah, hk.masalah);
                assertEquals(1, hk.koordinatTerkirim);
                android.util.Log.i("LIVE_UJI", "koordinat tph_id=" + tphKosong);
            } else {
                android.util.Log.w("LIVE_UJI", "semua TPH sudah punya koordinat: langkah koordinat dilewati");
            }

            // 4. Bila diminta (argumen tungguStatus=detik), tunggu sampai ERP mengubah status di server dari luar tes,
            //    lalu pastikan status dan alasan penolakan masuk ke catatan di HP.
            String tunggu = InstrumentationRegistry.getArguments().getString("tungguStatus");
            if (tunggu != null) {
                android.util.Log.i("LIVE_UJI", "panen_id=" + id + " menunggu perubahan status di server");
                long batas = System.currentTimeMillis() + Long.parseLong(tunggu) * 1000L;
                SyncService layanan = new SyncService(room, new HttpServerApi(server), pref);
                int diperbarui = 0;
                while (diperbarui == 0 && System.currentTimeMillis() < batas) {
                    Thread.sleep(2000);
                    diperbarui += layanan.jalankan(true).statusDiperbarui;
                }
                assertEquals("status diperbarui dari server", 1, diperbarui);
                try (Cursor c = room.getOpenHelper().getReadableDatabase()
                        .query("SELECT status, pesan_server FROM panen_header WHERE panen_id = '" + id + "'")) {
                    c.moveToFirst();
                    assertEquals("DITOLAK", c.getString(0));
                    assertEquals("Blok tidak ditemukan di ERP", c.getString(1));
                }
            }
            // panen_id dicatat di logcat agar bisa diperiksa langsung di database server.
            android.util.Log.i("LIVE_UJI", "panen_id=" + id);
        } finally {
            room.close();
        }
    }

    private static long count(AppDatabase room, String tabel) {
        try (Cursor c = room.getOpenHelper().getReadableDatabase().query("SELECT COUNT(*) FROM " + tabel)) {
            c.moveToFirst();
            return c.getLong(0);
        }
    }
}
