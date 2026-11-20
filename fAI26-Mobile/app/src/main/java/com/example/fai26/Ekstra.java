package com.example.fai26;

/** Kunci data antar-layar (Intent extra) dan nilai sumber TPH. */
final class Ekstra {

    static final String EXTRA_TPH_ID = "tph_id";
    static final String EXTRA_BLOK_ID = "blok_id";
    static final String EXTRA_UNIT_ID = "unit_id";
    static final String EXTRA_PEMANEN_ID = "pemanen_id";
    static final String EXTRA_TUJUAN = "tujuan";
    static final String EXTRA_BLOK_KODE = "blok_kode";
    static final String EXTRA_NO_TPH = "no_tph";
    static final String EXTRA_SUMBER = "sumber_tph";
    /** panen_id catatan DITOLAK yang sedang diperbaiki (mode "Edit dan kirim ulang" di HasilActivity). */
    static final String EXTRA_EDIT_PANEN_ID = "edit_panen_id";

    static final String EXTRA_GPS_LAT = "gps_lat";
    static final String EXTRA_GPS_LON = "gps_lon";
    static final String EXTRA_GPS_AKURASI = "gps_akurasi";
    static final String EXTRA_GPS_WAKTU = "gps_waktu";
    static final String EXTRA_GPS_MOCK = "gps_mock";

    static final String SUMBER_QR = "QR";
    static final String SUMBER_MANUAL = "MANUAL";

    private Ekstra() {
    }
}
