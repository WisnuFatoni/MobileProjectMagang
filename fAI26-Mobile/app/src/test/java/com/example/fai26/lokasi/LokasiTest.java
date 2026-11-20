package com.example.fai26.lokasi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LokasiTest {

    // Titik acuan sekitar Kalimantan Tengah. 0,001 derajat lintang kira-kira 111 meter.
    private static final double LAT = -2.0, LON = 113.0;

    @Test
    public void jarakNolUntukTitikSama() {
        assertEquals(0, Lokasi.jarakMeter(LAT, LON, LAT, LON), 1e-6);
    }

    @Test
    public void jarakSeperseribuDerajatLintangSekitar111Meter() {
        assertEquals(111.2, Lokasi.jarakMeter(LAT, LON, LAT + 0.001, LON), 0.5);
    }

    @Test
    public void jarakBujurMengecilMenjauhiKhatulistiwa() {
        double dekatKhatulistiwa = Lokasi.jarakMeter(0, 100, 0, 100.001);
        double jauh = Lokasi.jarakMeter(60, 100, 60, 100.001);
        assertTrue(jauh < dekatKhatulistiwa * 0.6);
    }

    @Test
    public void tanpaTitikGpsTanpaGps() {
        assertEquals(Lokasi.TANPA_GPS, Lokasi.status(LAT, LON, null, null, null));
        assertEquals(Lokasi.TANPA_GPS, Lokasi.status(null, null, null, null, null));
    }

    @Test
    public void tphTanpaKoordinat() {
        assertEquals(Lokasi.TPH_TANPA_KOORDINAT, Lokasi.status(null, null, LAT, LON, 8.0));
    }

    @Test
    public void dalamRadiusDanAkurat() {
        assertEquals(Lokasi.DI_TPH, Lokasi.status(LAT, LON, LAT + 0.0002, LON, 10.0)); // sekitar 22 m
    }

    @Test
    public void diLuarRadiusDanAkurat() {
        assertEquals(Lokasi.DI_LUAR, Lokasi.status(LAT, LON, LAT + 0.001, LON, 10.0)); // sekitar 111 m
    }

    @Test
    public void akurasiBurukTapiPastiDiLuarTetapDiLuar() {
        // 111 m dikurangi akurasi 40 m masih 71 m, lebih dari radius 50 m.
        assertEquals(Lokasi.DI_LUAR, Lokasi.status(LAT, LON, LAT + 0.001, LON, 40.0));
    }

    @Test
    public void akurasiBurukDanBisaDiDalamTidakBisaDinilai() {
        assertEquals(Lokasi.AKURASI_RENDAH, Lokasi.status(LAT, LON, LAT + 0.0002, LON, 45.0));
        assertEquals(Lokasi.AKURASI_RENDAH, Lokasi.status(LAT, LON, LAT + 0.0002, LON, null));
    }

    @Test
    public void perhatianMandorHanyaUntukSelainDiTph() {
        assertFalse(Lokasi.perluPerhatian(Lokasi.DI_TPH));
        assertTrue(Lokasi.perluPerhatian(Lokasi.DI_LUAR));
        assertTrue(Lokasi.perluPerhatian(Lokasi.TANPA_GPS));
    }
}
