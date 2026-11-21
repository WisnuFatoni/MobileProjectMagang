package com.example.fai26.lokasi;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.fai26.R;
import com.example.fai26.db.AppDatabase;
import com.example.fai26.db.Waktu;
import com.example.fai26.db.dao.IpdDao;
import com.example.fai26.db.entity.IpdJejak;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Merekam jalur GPS selama inspeksi IPD. Berjalan sebagai layanan latar dengan notifikasi tetap (wajib di Android supaya pelacakan
 * tidak berhenti saat layar dikunci) dan HANYA hidup antara {@link #mulai} dan {@link #berhenti}: dimulai saat inspektur mulai
 * mencatat pokok, dihentikan saat inspeksi ditutup. Titik dicatat tiap 3 meter bila akurasinya baik ({@link FilterJejak}).
 */
public class JejakService extends Service implements LocationListener {

    private static final String TAG = "JejakService";
    private static final String SALURAN = "jejak_ipd";
    private static final int ID_NOTIF = 4126;
    private static final String EKSTRA_IPD = "ipd_id";
    private static final String AKSI_BERHENTI = "com.example.fai26.JEJAK_BERHENTI";
    /** Pengaman: layanan mati sendiri bila lupa dihentikan (inspeksi tidak sampai selama ini). */
    private static final long BATAS_WAKTU_MS = 8L * 60 * 60 * 1000;

    private static final AtomicInteger JUMLAH = new AtomicInteger();
    private static volatile String ipdAktif;
    private static volatile float akurasiTerakhir = -1f;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final FilterJejak filter = new FilterJejak();
    private LocationManager lm;
    private IpdDao dao;
    private String ipdId;
    private boolean berjalan;

    // ---- kendali dari luar -------------------------------------------------------------------------------

    public static void mulai(Context c, String ipdId) {
        Intent i = new Intent(c, JejakService.class).putExtra(EKSTRA_IPD, ipdId);
        ContextCompat.startForegroundService(c, i);
    }

    public static void berhenti(Context c) {
        c.stopService(new Intent(c, JejakService.class));
    }

    /** Id inspeksi yang sedang direkam, atau null. */
    public static String ipdAktif() {
        return ipdAktif;
    }

    /** Jumlah titik yang dicatat sejak layanan mulai (sesi ini). */
    public static int jumlahSesi() {
        return JUMLAH.get();
    }

    /** Akurasi titik terakhir (meter), atau -1 bila belum ada. */
    public static float akurasiTerakhir() {
        return akurasiTerakhir;
    }

    // ---- siklus layanan ----------------------------------------------------------------------------------

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && AKSI_BERHENTI.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        String baru = intent == null ? null : intent.getStringExtra(EKSTRA_IPD);
        if (baru == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        mulaiForeground();
        if (berjalan && baru.equals(ipdId)) return START_NOT_STICKY;
        if (berjalan) hentikanPembaruan();
        ipdId = baru;
        ipdAktif = baru;
        JUMLAH.set(0);
        akurasiTerakhir = -1f;
        dao = AppDatabase.getInstance(getApplicationContext()).ipdDao();
        lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        final String id = baru;
        io.execute(() -> {
            // Inspeksi yang dilanjutkan: ukur jarak dari titik terakhir yang sudah ada.
            IpdJejak terakhir = dao.jejakTerakhir(id);
            if (terakhir != null) filter.atur(terakhir.lat, terakhir.lon);
            main.post(this::mintaPembaruan);
        });
        main.postDelayed(this::stopSelf, BATAS_WAKTU_MS);
        return START_NOT_STICKY;
    }

    @SuppressLint("MissingPermission")
    private void mintaPembaruan() {
        if (lm == null || ipdId == null) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Izin lokasi tidak ada; jalur tidak direkam");
            stopSelf();
            return;
        }
        try {
            // GPS saja: jalur jalan kaki butuh akurasi, bukan perkiraan jaringan. Pembaruan hanya dikirim bila bergeser 3 meter.
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, FilterJejak.JARAK_MIN_M, this, Looper.getMainLooper());
            berjalan = true;
        } catch (SecurityException | IllegalArgumentException e) {
            Log.w(TAG, "GPS tidak tersedia: " + e.getMessage());
            stopSelf();
        }
    }

    private void hentikanPembaruan() {
        if (lm != null) lm.removeUpdates(this);
        berjalan = false;
    }

    @Override
    public void onDestroy() {
        hentikanPembaruan();
        main.removeCallbacksAndMessages(null);
        ipdAktif = null;
        akurasiTerakhir = -1f;
        io.shutdown();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---- titik GPS ---------------------------------------------------------------------------------------

    @Override
    public void onLocationChanged(@NonNull Location l) {
        final Float ak = l.hasAccuracy() ? l.getAccuracy() : null;
        if (ak != null) akurasiTerakhir = ak;
        if (!filter.terima(l.getLatitude(), l.getLongitude(), ak)) return;
        final IpdJejak j = new IpdJejak();
        j.ipdId = ipdId;
        j.waktu = Waktu.utc(l.getTime() > 0 ? l.getTime() : System.currentTimeMillis());
        j.lat = l.getLatitude();
        j.lon = l.getLongitude();
        j.akurasi = ak == null ? null : (double) ak;
        final int n = JUMLAH.incrementAndGet();
        io.execute(() -> dao.insertJejak(j));
        if (n == 1 || n % 10 == 0) perbaruiNotifikasi(n);
    }

    @Override
    public void onProviderEnabled(@NonNull String provider) {
    }

    @Override
    public void onProviderDisabled(@NonNull String provider) {
        Log.w(TAG, "Penyedia lokasi dimatikan: " + provider);
    }

    // ---- notifikasi --------------------------------------------------------------------------------------

    private void mulaiForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null && nm.getNotificationChannel(SALURAN) == null) {
            NotificationChannel ch = new NotificationChannel(SALURAN, "Jalur inspeksi", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Tampil selama jalur GPS inspeksi IPD direkam");
            nm.createNotificationChannel(ch);
        }
        Notification n = bangunNotifikasi(0);
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID_NOTIF, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        else startForeground(ID_NOTIF, n);
    }

    private Notification bangunNotifikasi(int titik) {
        Intent stop = new Intent(this, JejakService.class).setAction(AKSI_BERHENTI);
        PendingIntent pi = PendingIntent.getService(this, 0, stop, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(this, SALURAN)
                .setSmallIcon(R.drawable.ic_nav_ipd)
                .setContentTitle("Merekam jalur inspeksi")
                .setContentText(titik == 0 ? "Menunggu sinyal GPS…" : titik + " titik tercatat")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .addAction(0, "Hentikan", pi)
                .build();
    }

    private void perbaruiNotifikasi(int titik) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(ID_NOTIF, bangunNotifikasi(titik));
    }
}
