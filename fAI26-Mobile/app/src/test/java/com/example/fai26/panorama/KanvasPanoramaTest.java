package com.example.fai26.panorama;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class KanvasPanoramaTest {

    @Test
    public void satuPotonganUkurannyaSamaDenganPotongan() {
        KanvasPanorama.Tata t = KanvasPanorama.hitung(Arrays.asList(new int[]{0, 0}), 3.0, 540, 960, 2400);
        assertEquals(540, t.lebar);
        assertEquals(960, t.tinggi);
        assertEquals(1.0, t.skala, 1e-9);
        assertEquals(0, t.kiriAtas[0][0]);
    }

    @Test
    public void geserKeKananDanBawahMemperluasKanvas() {
        // Resolusi taksiran 180 lebar -> faktor 3 ke piksel potongan 540.
        List<int[]> p = Arrays.asList(new int[]{0, 0}, new int[]{60, 0}, new int[]{60, 110});
        KanvasPanorama.Tata t = KanvasPanorama.hitung(p, 3.0, 540, 960, 5000);
        assertEquals(540 + 180, t.lebar);
        assertEquals(960 + 330, t.tinggi);
        assertEquals(180, t.kiriAtas[1][0]);
        assertEquals(330, t.kiriAtas[2][1]);
    }

    @Test
    public void posisiNegatifDigeserSupayaKanvasMulaiDariNol() {
        // Kamera bergerak ke kiri dan atas: posisi negatif; kiri-atas terkecil harus menjadi (0, 0).
        List<int[]> p = Arrays.asList(new int[]{0, 0}, new int[]{-70, -40});
        KanvasPanorama.Tata t = KanvasPanorama.hitung(p, 3.0, 540, 960, 5000);
        assertEquals(210, t.kiriAtas[0][0]);
        assertEquals(120, t.kiriAtas[0][1]);
        assertEquals(0, t.kiriAtas[1][0]);
        assertEquals(0, t.kiriAtas[1][1]);
        assertEquals(540 + 210, t.lebar);
        assertEquals(960 + 120, t.tinggi);
    }

    @Test
    public void kanvasBesarDikecilkanSampaiBatasSisi() {
        List<int[]> p = Arrays.asList(new int[]{0, 0}, new int[]{0, 600});
        KanvasPanorama.Tata t = KanvasPanorama.hitung(p, 3.0, 540, 960, 2400);
        // Tinggi penuh = 1800 + 960 = 2760 > 2400.
        assertEquals(2400, t.tinggi);
        assertEquals(2400.0 / 2760.0, t.skala, 1e-9);
        assertEquals(Math.round(540 * t.skala), t.lebar);
        assertEquals(Math.round(960 * t.skala), t.tinggiTile);
        assertEquals(Math.round(1800 * t.skala), t.kiriAtas[1][1]);
    }
}
