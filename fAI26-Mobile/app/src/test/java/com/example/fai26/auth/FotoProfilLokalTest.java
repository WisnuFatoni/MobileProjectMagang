package com.example.fai26.auth;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Tes murni (tanpa Android) untuk perhitungan potongan dan ukuran foto profil. */
public class FotoProfilLokalTest {

    @Test
    public void potonganPersegiDariTengah() {
        // Lanskap 4000 x 3000: sisi 3000, mulai x = 500.
        assertArrayEquals(new int[]{500, 0, 3000}, FotoProfilLokal.potongTengah(4000, 3000));
        // Potret 1080 x 2400: sisi 1080, mulai y = 660.
        assertArrayEquals(new int[]{0, 660, 1080}, FotoProfilLokal.potongTengah(1080, 2400));
        // Sudah persegi: tidak ada potongan.
        assertArrayEquals(new int[]{0, 0, 512}, FotoProfilLokal.potongTengah(512, 512));
    }

    @Test
    public void sampelMenyisakanSisiPendekCukupBesar() {
        int minimal = FotoProfilLokal.SISI * 3 / 2; // 768
        assertEquals(1, FotoProfilLokal.sampel(1000, 800, minimal));
        assertEquals(2, FotoProfilLokal.sampel(4000, 1600, minimal)); // 1600/2 = 800 >= 768
        assertEquals(4, FotoProfilLokal.sampel(4000, 3200, minimal)); // 3200/4 = 800
        // Tidak pernah menyisakan sisi pendek di bawah minimal.
        for (int w : new int[]{1500, 3024, 4032, 6000}) {
            int s = FotoProfilLokal.sampel(w, w * 3 / 4, minimal);
            assertEquals(true, (w * 3 / 4) / s >= minimal || s == 1);
        }
    }
}
