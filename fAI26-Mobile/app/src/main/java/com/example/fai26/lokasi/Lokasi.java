package com.example.fai26.lokasi;

/**
 * Logika verifikasi lokasi pencatat terhadap TPH. Murni hitungan (tanpa Android) supaya bisa diuji di JVM.
 * Hasilnya tanda untuk mandor, bukan pemblokir: GPS di bawah tajuk pohon bisa meleset.
 */
public final class Lokasi {

    public static final String DI_TPH = "DI_TPH";
    public static final String DI_LUAR = "DI_LUAR";
    public static final String AKURASI_RENDAH = "AKURASI_RENDAH";
    public static final String TANPA_GPS = "TANPA_GPS";
    public static final String TPH_TANPA_KOORDINAT = "TPH_TANPA_KOORDINAT";

    /** Nilai awal; disetel dari uji lapangan (PERTANYAAN B12). */
    public static final double RADIUS_M = 50;
    public static final double AKURASI_MAKS_M = 30;

    private static final double R_BUMI_M = 6371008.8;

    private Lokasi() {
    }

    /** Jarak permukaan bumi (haversine) dalam meter. */
    public static double jarakMeter(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = p2 - p1;
        double dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * R_BUMI_M * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    /**
     * Menentukan status lokasi.
     * <ul>
     * <li>Tidak ada titik GPS: TANPA_GPS.</li>
     * <li>TPH belum punya koordinat: TPH_TANPA_KOORDINAT.</li>
     * <li>Jarak dikurangi akurasi sudah melebihi radius: DI_LUAR, meski akurasi buruk (pasti di luar).</li>
     * <li>Akurasi tidak diketahui atau lebih buruk dari batas: AKURASI_RENDAH (tidak bisa dinilai).</li>
     * <li>Selain itu DI_TPH bila jarak dalam radius, DI_LUAR bila tidak.</li>
     * </ul>
     */
    public static String status(Double tphLat, Double tphLon, Double lat, Double lon, Double akurasiM) {
        if (lat == null || lon == null) return TANPA_GPS;
        if (tphLat == null || tphLon == null) return TPH_TANPA_KOORDINAT;
        double jarak = jarakMeter(tphLat, tphLon, lat, lon);
        double akurasi = akurasiM == null ? Double.MAX_VALUE : akurasiM;
        if (jarak - akurasi > RADIUS_M) return DI_LUAR;
        if (akurasi > AKURASI_MAKS_M) return AKURASI_RENDAH;
        return jarak <= RADIUS_M ? DI_TPH : DI_LUAR;
    }

    /** Teks untuk pengguna. */
    public static String teks(String status) {
        if (status == null) return "";
        switch (status) {
            case DI_TPH:
                return "Di TPH";
            case DI_LUAR:
                return "Di luar TPH";
            case AKURASI_RENDAH:
                return "GPS kurang akurat";
            case TANPA_GPS:
                return "Tanpa GPS";
            case TPH_TANPA_KOORDINAT:
                return "TPH belum ada koordinat";
            default:
                return status;
        }
    }

    /** Perlu perhatian mandor (selain DI_TPH). */
    public static boolean perluPerhatian(String status) {
        return status != null && !DI_TPH.equals(status);
    }
}
