package com.example.fai26.panorama;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Random;

public class PenaksirGeserTest {

    private static final int FW = 180;
    private static final int FH = 320;
    private final TeksturUji scene = new TeksturUji(900, 1100, 42);

    /** Frame A di (ax, ay); isi di P pada A tampak di P + (dx, dy) pada B, jadi B dipotong dari (ax - dx, ay - dy). */
    private PenaksirGeser.Hasil uji(int dx, int dy, int tambahTerang, int derau) {
        int ax = 300;
        int ay = 400;
        Gray a = scene.potong(ax, ay, FW, FH, 0, derau, 1);
        Gray b = scene.potong(ax - dx, ay - dy, FW, FH, tambahTerang, derau, 2);
        return PenaksirGeser.taksir(a, b);
    }

    @Test
    public void tanpaGeserHasilNol() {
        PenaksirGeser.Hasil h = uji(0, 0, 0, 0);
        assertTrue(h.sah);
        assertEquals(0, h.dx);
        assertEquals(0, h.dy);
    }

    @Test
    public void geserDiketahuiTerpulihkanTepat() {
        int[][] kasus = {{-30, 12}, {25, -18}, {-40, 0}, {0, 42}, {10, 10}, {-3, 2}, {1, 0}, {0, -1}, {37, 37}, {-39, -41}};
        for (int[] k : kasus) {
            PenaksirGeser.Hasil h = uji(k[0], k[1], 0, 0);
            assertTrue("sah untuk " + k[0] + "," + k[1] + " kep=" + h.kepercayaan, h.sah);
            assertEquals("dx untuk " + k[0] + "," + k[1], k[0], h.dx);
            assertEquals("dy untuk " + k[0] + "," + k[1], k[1], h.dy);
        }
    }

    @Test
    public void tahanTerhadapDerauDanPerubahanEksposur() {
        int[][] kasus = {{-30, 12}, {25, -18}, {8, 22}, {-14, -9}};
        for (int[] k : kasus) {
            PenaksirGeser.Hasil h = uji(k[0], k[1], 18, 6);
            assertTrue("sah untuk " + k[0] + "," + k[1] + " kep=" + h.kepercayaan, h.sah);
            assertEquals(k[0], h.dx);
            assertEquals(k[1], h.dy);
        }
    }

    @Test
    public void permukaanPolosDitolak() {
        int[] polos = new int[FW * FH];
        java.util.Arrays.fill(polos, 120);
        Gray a = new Gray(FW, FH, polos);
        assertFalse(PenaksirGeser.taksir(a, a).sah);
    }

    @Test
    public void frameTakBerhubunganDitolak() {
        // Dua derau putih yang saling bebas: tidak ada geseran yang benar.
        Random r = new Random(7);
        int[] x = new int[FW * FH];
        int[] y = new int[FW * FH];
        for (int i = 0; i < x.length; i++) {
            x[i] = r.nextInt(256);
            y[i] = r.nextInt(256);
        }
        assertFalse(PenaksirGeser.taksir(new Gray(FW, FH, x), new Gray(FW, FH, y)).sah);
    }

    @Test
    public void geserDiLuarJangkauanTidakDianggapSah() {
        int batas = PenaksirGeser.jangkauan(FW, FH);
        assertTrue("jangkauan masuk akal: " + batas, batas >= 32);
        // Dua kali jangkauan: pencocokan yang diklaim sah harus benar (kalau salah, harus ditolak).
        PenaksirGeser.Hasil h = uji(-(batas * 2 + 10), 0, 0, 0);
        assertTrue("geseran jauh tidak boleh lolos dengan nilai salah", !h.sah || (h.dx == -(batas * 2 + 10) && h.dy == 0));
    }

    @Test
    public void rotasiSemuaTepatDanKembaliKeAsal() {
        int[] d = {1, 2, 3, 4, 5, 6}; // 3 lebar x 2 tinggi: [1 2 3 / 4 5 6]
        Gray g = new Gray(3, 2, d);
        Gray r90 = g.putar(90); // 2 x 3: [4 1 / 5 2 / 6 3]
        assertEquals(2, r90.w);
        assertEquals(3, r90.h);
        assertArrayEquals(new int[]{4, 1, 5, 2, 6, 3}, r90.d);
        Gray r180 = g.putar(180);
        assertArrayEquals(new int[]{6, 5, 4, 3, 2, 1}, r180.d);
        Gray r270 = g.putar(270); // 2 x 3: [3 6 / 2 5 / 1 4]
        assertArrayEquals(new int[]{3, 6, 2, 5, 1, 4}, r270.d);
        assertArrayEquals(d, g.putar(90).putar(270).d);
        assertArrayEquals(d, g.putar(0).d);
    }

    private static void assertArrayEquals(int[] a, int[] b) {
        org.junit.Assert.assertArrayEquals(a, b);
    }

    @Test
    public void setengahMerataratakanKotakDua() {
        Gray g = new Gray(4, 2, new int[]{0, 4, 10, 10, 8, 4, 10, 10});
        Gray s = g.setengah();
        assertEquals(2, s.w);
        assertEquals(1, s.h);
        assertArrayEquals(new int[]{4, 10}, s.d);
    }
}
