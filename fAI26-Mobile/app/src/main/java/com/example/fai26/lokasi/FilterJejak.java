package com.example.fai26.lokasi;

/**
 * Menentukan titik GPS mana yang dicatat dalam jalur inspeksi: akurasinya harus cukup baik ({@link #AKURASI_MAKS_M}) dan jaraknya dari
 * titik yang dicatat sebelumnya minimal {@link #JARAK_MIN_M}. Titik berjarak lebih dekat dari galat GPS hanya menambah gerigi
 * (bukan informasi), jadi dilewati. Murni (tanpa Android) supaya bisa diuji.
 */
public final class FilterJejak {

    /** Titik dengan akurasi lebih buruk dari ini (meter) dibuang. Di bawah tajuk sawit galat 5 sampai 15 m masih wajar. */
    public static final float AKURASI_MAKS_M = 25f;
    /** Jarak minimal dari titik yang dicatat sebelumnya (meter), sesuai keputusan Ptoni: satu titik tiap 3 meter. */
    public static final float JARAK_MIN_M = 3f;

    private boolean ada;
    private double lat;
    private double lon;

    /** Mengisi titik terakhir yang sudah tercatat (mis. saat inspeksi dilanjutkan) supaya jaraknya diukur dari sana. */
    public void atur(double lat, double lon) {
        this.lat = lat;
        this.lon = lon;
        this.ada = true;
    }

    /** @return true bila titik ini harus dicatat (dan dianggap sebagai titik terakhir). */
    public boolean terima(double lat, double lon, Float akurasi) {
        if (Double.isNaN(lat) || Double.isNaN(lon) || (lat == 0 && lon == 0)) return false;
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return false;
        if (akurasi != null && akurasi > AKURASI_MAKS_M) return false;
        if (ada && jarakMeter(this.lat, this.lon, lat, lon) < JARAK_MIN_M) return false;
        atur(lat, lon);
        return true;
    }

    /** Jarak lingkaran besar (haversine) dua koordinat, meter. */
    public static double jarakMeter(double lat1, double lon1, double lat2, double lon2) {
        final double r = 6371000.0;
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = Math.toRadians(lat2 - lat1);
        double dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * r * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }
}
