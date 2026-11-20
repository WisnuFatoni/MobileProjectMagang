package com.example.fai26.lokasi;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

/**
 * Mengumpulkan titik GPS hanya selama antara mulai() dan berhenti() (selama sesi scan, tidak melacak
 * terus-menerus). Menyimpan titik terbaik: akurasi terkecil, dan titik yang lebih dari 2 menit dianggap basi.
 */
public class PencatatLokasi implements LocationListener {

    private static final long USIA_MAKS_MS = 2 * 60 * 1000L;

    private final Context context;
    private final LocationManager lm;
    private Location terbaik;
    private boolean berjalan;

    public PencatatLokasi(Context context) {
        this.context = context.getApplicationContext();
        this.lm = (LocationManager) this.context.getSystemService(Context.LOCATION_SERVICE);
    }

    public static boolean adaIzin(Context c) {
        return ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** @return false bila izin belum ada atau tidak ada penyedia lokasi yang menyala. */
    @SuppressLint("MissingPermission")
    public boolean mulai() {
        if (berjalan) return true;
        if (lm == null || !adaIzin(context)) return false;
        boolean ada = false;
        for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            try {
                if (!lm.isProviderEnabled(p)) continue;
                Location terakhir = lm.getLastKnownLocation(p);
                if (terakhir != null) onLocationChanged(terakhir);
                lm.requestLocationUpdates(p, 1000L, 0f, this, Looper.getMainLooper());
                ada = true;
            } catch (SecurityException | IllegalArgumentException e) {
                // penyedia tidak tersedia: dilewati
            }
        }
        berjalan = ada;
        return ada;
    }

    public void berhenti() {
        if (lm != null) lm.removeUpdates(this);
        berjalan = false;
    }

    /** Titik terbaik yang belum basi, atau null. */
    public synchronized Location terbaik() {
        if (terbaik == null) return null;
        long usia = System.currentTimeMillis() - terbaik.getTime();
        return usia > USIA_MAKS_MS ? null : terbaik;
    }

    public static boolean lokasiPalsu(Location l) {
        return Build.VERSION.SDK_INT >= 31 ? l.isMock() : l.isFromMockProvider();
    }

    @Override
    public synchronized void onLocationChanged(@NonNull Location l) {
        boolean basi = terbaik == null || System.currentTimeMillis() - terbaik.getTime() > USIA_MAKS_MS;
        boolean lebihAkurat = terbaik != null && l.hasAccuracy()
                && (!terbaik.hasAccuracy() || l.getAccuracy() <= terbaik.getAccuracy());
        if (basi || lebihAkurat) terbaik = new Location(l);
    }

    @Override
    public void onProviderEnabled(@NonNull String provider) {
    }

    @Override
    public void onProviderDisabled(@NonNull String provider) {
    }
}
