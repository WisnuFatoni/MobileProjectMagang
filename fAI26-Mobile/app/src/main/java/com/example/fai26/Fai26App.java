package com.example.fai26;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import com.example.fai26.auth.AuthService;
import com.example.fai26.auth.LoginService;
import com.example.fai26.sync.HttpServerApi;
import com.example.fai26.sync.MasterSync;
import com.example.fai26.sync.PengaturanSync;
import com.example.fai26.sync.ServerApi;
import com.example.fai26.auth.Sesi;
import com.example.fai26.db.AppDatabase;
import com.example.fai26.db.PanenRepository;
import com.example.fai26.db.Waktu;
import com.example.fai26.db.entity.Device;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

/** Menyiapkan database, data master contoh, dan identitas perangkat saat aplikasi dibuka. */
public class Fai26App extends Application {

    private static final String TAG = "Fai26App";
    private static final String PREF = "fai26";
    private static final String KEY_DEVICE = "device_id";

    private static final CountDownLatch SIAP = new CountDownLatch(1);

    @Override
    public void onCreate() {
        super.onCreate();
        // Sinkronisasi berkala di latar (hanya saat ada jaringan). Aman dipanggil tiap aplikasi dibuka.
        com.example.fai26.sync.SyncJadwal.pasangBerkala(this);
        // Master baru bisa mengubah hak role: cache hak pengguna yang sedang login ikut diperbarui tanpa login ulang.
        final java.util.concurrent.ExecutorService segarkan = Executors.newSingleThreadExecutor();
        com.example.fai26.sync.PerubahanMaster.daftar(() -> segarkan.execute(() -> segarkanHakSesi(this)));
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(this);
                // Tidak ada data atau akun contoh di aplikasi: master dan akun datang dari server saat login online pertama.
                db.masterDao().insertDeviceJikaBelumAda(deviceSaatIni());
            } catch (Throwable e) {
                Log.e(TAG, "Inisialisasi gagal", e);
            } finally {
                SIAP.countDown();
            }
        });
    }

    /** Membaca ulang permission pengguna yang login dari database dan memperbarui cache sesinya. */
    static void segarkanHakSesi(Context context) {
        try {
            Context app = context.getApplicationContext();
            long user = Sesi.userId(app);
            if (user <= 0) return;
            AuthService auth = auth(app);
            Sesi.perbarui(app, user, auth.namaRole(user), auth.permission(user));
            com.example.fai26.sync.PerubahanMaster.umumkanHak();
        } catch (Throwable e) {
            Log.e(TAG, "Hak sesi tidak bisa diperbarui", e);
        }
    }

    /** Menunggu inisialisasi selesai. Panggil dari thread latar, bukan thread utama. */
    public static void tungguSiap() throws InterruptedException {
        SIAP.await();
    }

    /** Identitas perangkat, dibuat sekali saat aplikasi pertama dijalankan. */
    public static String deviceId(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREF, MODE_PRIVATE);
        String id = p.getString(KEY_DEVICE, null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            p.edit().putString(KEY_DEVICE, id).apply();
        }
        return id;
    }

    /** Repository atas nama pengguna yang sedang login. Melempar bila belum login. */
    public static PanenRepository repository(Context context) {
        Context app = context.getApplicationContext();
        long user = Sesi.userId(app);
        if (user <= 0) throw new IllegalStateException("Belum login");
        return new PanenRepository(AppDatabase.getInstance(app),
                new File(app.getFilesDir(), "foto_tph"), deviceId(app), user);
    }

    /** Repository IPD (Inspeksi Panen Detail) atas nama pengguna yang sedang login. Melempar bila belum login. */
    public static com.example.fai26.db.IpdRepository ipdRepository(Context context) {
        Context app = context.getApplicationContext();
        long user = Sesi.userId(app);
        if (user <= 0) throw new IllegalStateException("Belum login");
        return new com.example.fai26.db.IpdRepository(AppDatabase.getInstance(app), user, deviceId(app));
    }

    public static AuthService auth(Context context) {
        Context app = context.getApplicationContext();
        return new AuthService(AppDatabase.getInstance(app), deviceId(app));
    }

    /** Alur login (online dulu bila server diatur, lalu offline). */
    public static LoginService loginService(Context context) {
        Context app = context.getApplicationContext();
        PengaturanSync pref = new PengaturanSync(app);
        ServerApi api = pref.serverSudahDiatur() ? new HttpServerApi(pref.serverUrl()) : null;
        String versi = "1.0";
        try {
            versi = app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            // pakai bawaan
        }
        return new LoginService(auth(app), api, pref, new MasterSync(AppDatabase.getInstance(app)), deviceId(app), Build.MANUFACTURER + " " + Build.MODEL,
                "Android " + Build.VERSION.RELEASE, versi);
    }

    private Device deviceSaatIni() {
        Device d = new Device();
        d.deviceId = deviceId(this);
        d.nama = Build.MODEL;
        d.osVersi = Build.VERSION.RELEASE;
        d.appVersi = "1.0";
        d.dibuatPada = Waktu.utcSekarang();
        return d;
    }
}
