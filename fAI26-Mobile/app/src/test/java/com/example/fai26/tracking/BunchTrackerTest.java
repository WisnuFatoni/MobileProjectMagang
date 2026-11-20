package com.example.fai26.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.fai26.detect.Detection;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class BunchTrackerTest {

    /** Jam palsu: tiap frame berselang 100 ms (10 fps) kecuali diatur lain. */
    private static final class Jam {
        long ms = 1_000_000L;
        long maju(long langkah) {
            ms += langkah;
            return ms;
        }
    }

    private static Detection kotak(float cx, float cy, float w, float h, float conf, int kelas) {
        return new Detection(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, conf, kelas);
    }

    private static void frame(BunchTracker t, Jam jam, List<Detection> dets) {
        t.update(dets, jam.maju(100));
    }

    private static List<Detection> satu(float cx, float cy, float w, float conf, int kelas) {
        return Collections.singletonList(kotak(cx, cy, w, w, conf, kelas));
    }

    /**
     * Tiga janjang diam di dunia, kamera bergeser ke kanan sehingga di layar
     * mereka bergeser ke kiri. Muncul bertahap lalu keluar layar.
     */
    private static List<Detection> sapuan(int f, float jitter, Random rnd) {
        float[][] dunia = {{0.9f, 0.5f, 0}, {1.3f, 0.3f, 3}, {1.7f, 0.6f, 3}};
        List<Detection> dets = new ArrayList<>();
        for (float[] j : dunia) {
            float x = j[0] - f * 0.02f; // kamera bergerak 0,02 per frame
            if (x < -0.1f || x > 1.1f) continue;
            float noise = rnd == null ? 0f : (rnd.nextFloat() - 0.5f) * jitter;
            dets.add(kotak(x + noise, j[1] + noise, 0.25f, 0.25f, 0.8f, (int) j[2]));
        }
        return dets;
    }

    @Test
    public void menyisirTigaJanjangYangBergerakDihitungTepatTiga() {
        BunchTracker t = new BunchTracker(6);
        Jam jam = new Jam();
        Random rnd = new Random(1);
        for (int f = 0; f < 120; f++) frame(t, jam, sapuan(f, 0.01f, rnd));
        assertEquals(3, t.total());
        assertEquals(1, t.counts()[0]);
        assertEquals(2, t.counts()[3]);
    }

    @Test
    public void deteksiSesaatDuaFrameTidakDihitung() {
        BunchTracker t = new BunchTracker(6);
        Jam jam = new Jam();
        for (int f = 0; f < 3; f++) frame(t, jam, satu(0.5f, 0.5f, 0.2f, 0.9f, 2));
        for (int f = 0; f < 60; f++) frame(t, jam, Collections.<Detection>emptyList());
        assertEquals(0, t.total());
    }

    @Test
    public void janjangDiamYangKelasnyaSempatBerubahDihitungSekali() {
        BunchTracker t = new BunchTracker(6);
        Jam jam = new Jam();
        for (int f = 0; f < 30; f++) {
            int kelas = f % 7 == 0 ? 4 : 3; // sesekali salah kelas
            frame(t, jam, satu(0.45f, 0.45f, 0.3f, 0.7f, kelas));
        }
        assertEquals(1, t.total());
        assertEquals(1, t.counts()[3]);
    }

    @Test
    public void janjangYangHilangDuaDetikLaluMunculLagiTidakDobel() {
        BunchTracker t = new BunchTracker(6);
        Jam jam = new Jam();
        for (int f = 0; f < 10; f++) frame(t, jam, satu(0.45f, 0.45f, 0.3f, 0.8f, 3));
        for (int f = 0; f < 20; f++) frame(t, jam, Collections.<Detection>emptyList()); // 2 detik
        for (int f = 0; f < 10; f++) frame(t, jam, satu(0.45f, 0.45f, 0.3f, 0.8f, 3));
        assertEquals(1, t.total());
    }

    @Test
    public void janjangYangHilangLebihDariBatasWaktuDihitungLagi() {
        // Batas yang diketahui: setelah lebih dari 3 detik tak terlihat, tandan dianggap baru.
        BunchTracker t = new BunchTracker(6);
        Jam jam = new Jam();
        for (int f = 0; f < 10; f++) frame(t, jam, satu(0.45f, 0.45f, 0.3f, 0.8f, 3));
        jam.maju(4000);
        for (int f = 0; f < 10; f++) frame(t, jam, satu(0.45f, 0.45f, 0.3f, 0.8f, 3));
        assertEquals(2, t.total());
    }

    @Test
    public void batasHilangBerdasarkanWaktuBukanJumlahFrame() {
        // Pada 4 fps (250 ms per frame) 8 frame hilang = 2 detik: masih tandan yang sama.
        BunchTracker t = new BunchTracker(6);
        Jam jam = new Jam();
        for (int f = 0; f < 10; f++) t.update(satu(0.45f, 0.45f, 0.3f, 0.8f, 3), jam.maju(250));
        for (int f = 0; f < 8; f++) t.update(Collections.<Detection>emptyList(), jam.maju(250));
        for (int f = 0; f < 10; f++) t.update(satu(0.45f, 0.45f, 0.3f, 0.8f, 3), jam.maju(250));
        assertEquals(1, t.total());
    }

    @Test
    public void duaKotakPadaTandanYangSamaDihitungSatu() {
        BunchTracker t = new BunchTracker(6);
        Jam jam = new Jam();
        List<Detection> ganda = new ArrayList<>();
        ganda.add(kotak(0.45f, 0.45f, 0.30f, 0.30f, 0.85f, 3));
        ganda.add(kotak(0.47f, 0.46f, 0.30f, 0.30f, 0.60f, 3)); // IoU sekitar 0,8
        for (int f = 0; f < 15; f++) frame(t, jam, ganda);
        assertEquals(1, t.total());
        assertEquals(1, t.terlihat().size());
    }

    @Test
    public void duaTandanBerdampinganTetapDihitungDua() {
        BunchTracker t = new BunchTracker(6);
        Jam jam = new Jam();
        List<Detection> dua = new ArrayList<>();
        dua.add(kotak(0.30f, 0.45f, 0.25f, 0.25f, 0.8f, 3));
        dua.add(kotak(0.60f, 0.45f, 0.25f, 0.25f, 0.8f, 3));
        for (int f = 0; f < 15; f++) frame(t, jam, dua);
        assertEquals(2, t.total());
    }

    @Test
    public void infoTerlihatMenandaiYangSudahDihitung() {
        BunchTracker t = new BunchTracker(6);
        Jam jam = new Jam();
        frame(t, jam, satu(0.45f, 0.45f, 0.3f, 0.8f, 3));
        assertEquals(1, t.terlihat().size());
        assertTrue(!t.terlihat().get(0).counted);
        for (int f = 0; f < 6; f++) frame(t, jam, satu(0.45f, 0.45f, 0.3f, 0.8f, 3));
        assertTrue(t.terlihat().get(0).counted);
    }

    @Test
    public void resetMengosongkanHitungan() {
        BunchTracker t = new BunchTracker(6);
        Jam jam = new Jam();
        for (int f = 0; f < 10; f++) frame(t, jam, satu(0.45f, 0.45f, 0.3f, 0.8f, 3));
        assertEquals(1, t.total());
        t.reset();
        assertEquals(0, t.total());
    }

    @Test
    public void kotakKecilDiBawahBatasUkuranTidakDihitung() {
        BunchTracker t = new BunchTracker(6); // minArea 0,03
        Jam jam = new Jam();
        for (int f = 0; f < 20; f++) frame(t, jam, satu(0.5f, 0.5f, 0.1f, 0.9f, 3)); // luas 0,01
        assertEquals(0, t.total());
    }
}
