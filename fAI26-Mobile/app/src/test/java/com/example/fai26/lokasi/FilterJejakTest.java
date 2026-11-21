package com.example.fai26.lokasi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Tes murni penyaring titik jalur GPS: akurasi baik dan jarak minimal 3 meter. */
public class FilterJejakTest {

    /** 0,00001 derajat lintang = sekitar 1,11 meter. */
    private static final double M1 = 0.00001;

    @Test
    public void jarakHaversineSesuaiPerkiraan() {
        // 0,001 derajat lintang = sekitar 111,2 meter.
        assertEquals(111.2, FilterJejak.jarakMeter(-2.700, 104.3, -2.699, 104.3), 0.5);
        assertEquals(0.0, FilterJejak.jarakMeter(-2.7, 104.3, -2.7, 104.3), 1e-9);
    }

    @Test
    public void titikPertamaDiterimaLalu3MeterBerikutnya() {
        FilterJejak f = new FilterJejak();
        assertTrue(f.terima(-2.7, 104.3, 8f));
        assertFalse("1,1 m: terlalu dekat", f.terima(-2.7 + M1, 104.3, 8f));
        assertFalse("2,2 m: masih terlalu dekat", f.terima(-2.7 + 2 * M1, 104.3, 8f));
        assertTrue("3,3 m: cukup jauh", f.terima(-2.7 + 3 * M1, 104.3, 8f));
        // Jarak diukur dari titik yang DICATAT terakhir, bukan dari titik yang dibuang.
        assertFalse(f.terima(-2.7 + 4 * M1, 104.3, 8f));
        assertTrue(f.terima(-2.7 + 6.5 * M1, 104.3, 8f));
    }

    @Test
    public void akurasiBurukDibuangDanAkurasiTakDiketahuiDiterima() {
        FilterJejak f = new FilterJejak();
        assertFalse(f.terima(-2.7, 104.3, 40f));
        assertTrue("batas 25 m masih diterima", f.terima(-2.7, 104.3, 25f));
        FilterJejak g = new FilterJejak();
        assertTrue("tanpa laporan akurasi tetap diterima", g.terima(-2.7, 104.3, null));
    }

    @Test
    public void koordinatTidakSahDitolak() {
        FilterJejak f = new FilterJejak();
        assertFalse(f.terima(0, 0, 5f));
        assertFalse(f.terima(91, 104.3, 5f));
        assertFalse(f.terima(-2.7, 181, 5f));
        assertFalse(f.terima(Double.NaN, 104.3, 5f));
    }

    @Test
    public void inspeksiDilanjutkanMengukurDariTitikTerakhirYangAda() {
        FilterJejak f = new FilterJejak();
        f.atur(-2.7, 104.3);
        assertFalse(f.terima(-2.7 + M1, 104.3, 5f));
        assertTrue(f.terima(-2.7 + 4 * M1, 104.3, 5f));
    }
}
