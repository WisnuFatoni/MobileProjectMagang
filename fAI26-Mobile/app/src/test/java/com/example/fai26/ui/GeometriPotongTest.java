package com.example.fai26.ui;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Hitungan murni pemotong foto profil: gambar harus selalu menutupi kotak, zoom bertumpu pada titik jari. */
public class GeometriPotongTest {

    private static final float KIRI = 40f;
    private static final float ATAS = 200f;
    private static final float SISI = 400f;

    @Test
    public void awalMenutupiKotakDanDiTengah() {
        // Lanskap 1600 x 1000: skala = 400/1000 = 0,4; lebar 640; kelebihan 240 dibagi dua.
        float[] a = GeometriPotong.awal(1600, 1000, KIRI, ATAS, SISI);
        assertEquals(0.4f, a[2], 1e-4f);
        assertEquals(KIRI - 120f, a[0], 1e-3f);
        assertEquals(ATAS, a[1], 1e-3f);
    }

    @Test
    public void geserDijepitSupayaTidakAdaCelah() {
        float s = GeometriPotong.skalaMin(1600, 1000, SISI); // 0,4
        // Terlalu ke kanan: tepi kiri gambar tidak boleh lewat kiri kotak.
        float[] g = GeometriPotong.jepitGeser(500f, 0f, s, 1600, 1000, KIRI, ATAS, SISI);
        assertEquals(KIRI, g[0], 1e-3f);
        // Terlalu ke kiri: tepi kanan gambar (tx + 640) tidak boleh di bawah tepi kanan kotak (440).
        g = GeometriPotong.jepitGeser(-900f, 0f, s, 1600, 1000, KIRI, ATAS, SISI);
        assertEquals(KIRI + SISI - 640f, g[0], 1e-3f);
        // Tinggi pas persis: tidak ada ruang geser vertikal.
        assertEquals(ATAS, g[1], 1e-3f);
    }

    @Test
    public void zoomTidakKurangDariMinDanTidakLebihDariMaks() {
        float min = GeometriPotong.skalaMin(1600, 1000, SISI);
        assertEquals(min, GeometriPotong.jepitSkala(0.01f, 1600, 1000, SISI), 1e-6f);
        assertEquals(min * GeometriPotong.ZOOM_MAKS, GeometriPotong.jepitSkala(99f, 1600, 1000, SISI), 1e-6f);
    }

    @Test
    public void zoomMenjagaTitikDiBawahJariTetapDiTempat() {
        float[] a = GeometriPotong.awal(2000, 2000, KIRI, ATAS, SISI); // skala 0,2
        float px = KIRI + 300f;
        float py = ATAS + 120f;
        // Titik gambar di bawah jari sebelum zoom:
        float gx = (px - a[0]) / a[2];
        float gy = (py - a[1]) / a[2];
        float[] z = GeometriPotong.zoomDi(a[0], a[1], a[2], a[2] * 2f, px, py, 2000, 2000, KIRI, ATAS, SISI);
        assertEquals(0.4f, z[2], 1e-5f);
        // Setelah zoom, titik gambar yang sama harus masih (kira-kira) di bawah jari, kecuali bila dijepit oleh tepi.
        float gx2 = (px - z[0]) / z[2];
        float gy2 = (py - z[1]) / z[2];
        assertTrue(Math.abs(gx - gx2) < 1.5f || z[0] == KIRI || z[0] == KIRI + SISI - 2000f * z[2]);
        assertTrue(Math.abs(gy - gy2) < 1.5f || z[1] == ATAS || z[1] == ATAS + SISI - 2000f * z[2]);
        // Dan kotak tetap tertutup penuh.
        assertTrue(z[0] <= KIRI + 1e-3f && z[0] + 2000f * z[2] >= KIRI + SISI - 1e-3f);
        assertTrue(z[1] <= ATAS + 1e-3f && z[1] + 2000f * z[2] >= ATAS + SISI - 1e-3f);
    }

    @Test
    public void daerahSumberSesuaiYangTerlihat() {
        float[] a = GeometriPotong.awal(1600, 1000, KIRI, ATAS, SISI); // skala 0,4, tx = -80
        float[] d = GeometriPotong.daerahSumber(a[0], a[1], a[2], KIRI, ATAS, SISI);
        // Dari piksel gambar x=300 selebar 1000 (400 / 0,4), mulai y=0.
        assertArrayEquals(new float[]{300f, 0f, 1000f}, d, 1e-2f);
    }
}
