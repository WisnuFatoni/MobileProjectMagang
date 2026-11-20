package com.example.fai26.panorama;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class RancangPanoramaTest {

    private static final int W = 180;
    private static final int H = 320;

    private RancangPanorama baru() {
        RancangPanorama r = new RancangPanorama(W, H);
        r.mulai();
        return r;
    }

    @Test
    public void frameDiamTidakMenambahPotongan() {
        RancangPanorama r = baru();
        for (int i = 0; i < 50; i++) {
            assertFalse(r.terima(0, 0, true, RancangPanorama.AMBANG_TILE).ambilTile);
        }
        assertEquals(1, r.jumlahTile());
    }

    @Test
    public void potonganBaruSetiapBergeserSepertigaLayar() {
        RancangPanorama r = baru();
        // Kamera bergeser ke kanan 10 piksel per frame: isi bergerak ke kiri, dx = -10.
        int ambil = 0;
        for (int i = 0; i < 40; i++) {
            RancangPanorama.Keputusan k = r.terima(-10, 0, true, RancangPanorama.AMBANG_TILE);
            if (k.ambilTile) ambil++;
        }
        // 400 piksel total; ambang 0,35 x 180 = 63 piksel -> potongan tiap 70 piksel (7 frame).
        assertEquals(5, ambil);
        List<int[]> p = r.posisiTile();
        assertEquals(6, p.size());
        assertEquals(0, p.get(0)[0]);
        assertEquals(70, p.get(1)[0]);
        assertEquals(140, p.get(2)[0]);
        for (int[] q : p) assertEquals(0, q[1]);
    }

    @Test
    public void geserLambatSubPikselTetapTerkumpul() {
        // Geser 1 piksel per frame, lebih kecil dari batas ganti ref (3): tetap harus terkumpul menjadi potongan.
        RancangPanorama r = baru();
        int ambil = 0;
        int posisi = 0;
        for (int i = 0; i < 200; i++) {
            posisi += 1;
            // dx relatif terhadap ref yang tidak diganti selama geseran < 3: yang dilaporkan penaksir adalah total.
            int dxTerhadapRef = -(posisi - refPos(r));
            RancangPanorama.Keputusan k = r.terima(dxTerhadapRef, 0, true, RancangPanorama.AMBANG_TILE);
            if (k.ambilTile) ambil++;
        }
        assertTrue("potongan harus terambil walau geser kecil per frame: " + ambil, ambil >= 2);
    }

    /** Posisi ref saat ini, dibaca dari potongan terakhir yang pasti menjadi ref (aproksimasi untuk uji). */
    private static int refPos(RancangPanorama r) {
        List<int[]> p = r.posisiTile();
        return p.get(p.size() - 1)[0];
    }

    @Test
    public void geserDuaDimensiMenghitungKeduaSumbu() {
        RancangPanorama r = baru();
        // Naik: kamera bergeser ke bawah -> isi bergerak ke atas, dy negatif; 120 piksel > 0,35 x 320.
        RancangPanorama.Keputusan k = r.terima(0, -120, true, RancangPanorama.AMBANG_TILE);
        assertTrue(k.ambilTile);
        assertEquals(120, r.posisiTile().get(1)[1]);
    }

    @Test
    public void gagalBerulangMembekukanPanoramaDanMenghentikanPotongan() {
        RancangPanorama r = baru();
        for (int i = 0; i <= RancangPanorama.MAKS_GAGAL_BERTURUT; i++) {
            assertFalse(r.terima(0, 0, false, RancangPanorama.AMBANG_TILE).ambilTile);
        }
        assertTrue(r.terbeku());
        assertFalse("setelah beku tidak ada potongan baru", r.terima(-200, 0, true, RancangPanorama.AMBANG_TILE).ambilTile);
        assertEquals(1, r.jumlahTile());
    }

    @Test
    public void keberhasilanMenyetelUlangHitunganGagal() {
        RancangPanorama r = baru();
        for (int i = 0; i < RancangPanorama.MAKS_GAGAL_BERTURUT; i++) r.terima(0, 0, false, RancangPanorama.AMBANG_TILE);
        r.terima(0, 0, true, RancangPanorama.AMBANG_TILE);
        for (int i = 0; i < RancangPanorama.MAKS_GAGAL_BERTURUT; i++) r.terima(0, 0, false, RancangPanorama.AMBANG_TILE);
        assertFalse(r.terbeku());
    }

    @Test
    public void tidakMelebihiBatasPotongan() {
        RancangPanorama r = baru();
        for (int i = 0; i < 500; i++) r.terima(-70, 0, true, RancangPanorama.AMBANG_TILE);
        assertEquals(RancangPanorama.MAKS_TILE, r.jumlahTile());
    }

    @Test
    public void frameAkhirMemakaiAmbangLebihKecil() {
        RancangPanorama r = baru();
        // 20 piksel (11% lebar): di bawah ambang biasa, di atas ambang penutup.
        assertFalse(r.terima(-20, 0, true, RancangPanorama.AMBANG_TILE).ambilTile);
        assertTrue(r.terima(-20, 0, true, RancangPanorama.AMBANG_AKHIR).ambilTile);
    }
}
