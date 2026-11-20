package com.example.fai26.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BackdropBlurViewTest {

    private static int abuAbu(int v) {
        return 0xFF000000 | (v << 16) | (v << 8) | v;
    }

    private static int merah(int p) {
        return (p >> 16) & 0xFF;
    }

    @Test
    public void gambarPolosTidakBerubahSetelahDikaburkan() {
        int w = 20;
        int h = 10;
        int[] a = new int[w * h];
        int[] b = new int[w * h];
        java.util.Arrays.fill(a, abuAbu(120));
        BackdropBlurView.kaburHorizontal(a, b, w, h, 3);
        BackdropBlurView.kaburVertikal(b, a, w, h, 3);
        for (int p : a) assertEquals(120, merah(p));
    }

    @Test
    public void tepiTajamMenjadiLandaiTanpaMengubahRataRata() {
        int w = 30;
        int h = 6;
        int[] a = new int[w * h];
        int[] b = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) a[y * w + x] = abuAbu(x < w / 2 ? 0 : 240);
        }
        BackdropBlurView.kaburHorizontal(a, b, w, h, 3);
        // Di dekat batas, nilainya antara 0 dan 240 (landai); jauh dari batas tetap sama.
        int dekatBatas = merah(b[w / 2]);
        assertTrue(dekatBatas > 0 && dekatBatas < 240);
        assertEquals(0, merah(b[2]));
        assertEquals(240, merah(b[w - 3]));
        // Naik monoton melintasi batas.
        for (int x = 1; x < w; x++) assertTrue(merah(b[x]) >= merah(b[x - 1]));
    }

    @Test
    public void kaburVertikalBekerjaSamaDenganHorizontal() {
        int w = 6;
        int h = 30;
        int[] a = new int[w * h];
        int[] b = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) a[y * w + x] = abuAbu(y < h / 2 ? 0 : 240);
        }
        BackdropBlurView.kaburVertikal(a, b, w, h, 3);
        int dekatBatas = merah(b[(h / 2) * w]);
        assertTrue(dekatBatas > 0 && dekatBatas < 240);
        assertEquals(0, merah(b[2 * w]));
        assertEquals(240, merah(b[(h - 3) * w]));
    }
}
