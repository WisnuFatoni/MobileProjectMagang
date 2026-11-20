package com.example.fai26.detect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class YoloPostprocessTest {

    private static final int CH = 10; // 4 kotak + 6 kelas
    private static final int N = 8400;

    /** Menulis satu kandidat ke larik datar berbentuk [channels, anchors]. */
    private static void tulis(float[] out, int anchor, float cx, float cy, float w, float h,
                              int kelas, float skor) {
        out[0 * N + anchor] = cx;
        out[1 * N + anchor] = cy;
        out[2 * N + anchor] = w;
        out[3 * N + anchor] = h;
        out[(4 + kelas) * N + anchor] = skor;
    }

    @Test
    public void kotakPiksel640DipetakanKeFrameTegak() {
        // Frame 720x1280 (tegak). Skala = 640/1280 = 0,5; lebar jadi 360, sisa 280 dibagi dua = 140 tiap sisi.
        float[] out = new float[CH * N];
        // Kotak di tengah frame: pusat (320,320) piksel, 180x180.
        tulis(out, 5, 320f, 320f, 180f, 180f, 3, 0.9f);

        List<Detection> d = YoloPostprocess.decode(out, CH, N, true, 640, 720, 1280, 0.25f, 0.6f, 100);
        assertEquals(1, d.size());
        Detection x = d.get(0);
        assertEquals(3, x.classIndex);
        assertEquals(0.9f, x.confidence, 1e-6f);
        // Sumbu x: isi frame 360 piksel mulai dari 140, kotak 230..410 -> 0,25..0,75.
        assertEquals(0.25f, x.x1, 1e-4f);
        assertEquals(0.75f, x.x2, 1e-4f);
        // Sumbu y: tidak ada padding (isi 640 piksel), kotak 230..410 -> dibagi 640.
        assertEquals(230f / 640f, x.y1, 1e-4f);
        assertEquals(410f / 640f, x.y2, 1e-4f);
    }

    @Test
    public void kotakTernormalisasiDikenaliOtomatis() {
        float[] out = new float[CH * N];
        // Sama dengan uji di atas, tetapi dalam 0..1 (dibagi 640).
        tulis(out, 5, 0.5f, 0.5f, 180f / 640f, 180f / 640f, 3, 0.9f);

        List<Detection> d = YoloPostprocess.decode(out, CH, N, true, 640, 720, 1280, 0.25f, 0.6f, 100);
        assertEquals(1, d.size());
        assertEquals(0.25f, d.get(0).x1, 1e-4f);
        assertEquals(0.75f, d.get(0).x2, 1e-4f);
    }

    @Test
    public void kandidatDiBawahBatasConfidenceDibuang() {
        float[] out = new float[CH * N];
        tulis(out, 1, 320f, 320f, 100f, 100f, 3, 0.10f);
        tulis(out, 2, 100f, 100f, 100f, 100f, 3, 0.80f);
        List<Detection> d = YoloPostprocess.decode(out, CH, N, true, 640, 640, 640, 0.25f, 0.6f, 100);
        assertEquals(1, d.size());
        assertEquals(0.8f, d.get(0).confidence, 1e-6f);
    }

    @Test
    public void nmsMenyisakanSatuKotakUntukObjekYangSamaMeskiKelasBeda() {
        float[] out = new float[CH * N];
        tulis(out, 1, 300f, 300f, 200f, 200f, 3, 0.90f);
        tulis(out, 2, 305f, 302f, 200f, 200f, 4, 0.60f); // objek sama, kelas lain
        tulis(out, 3, 100f, 100f, 80f, 80f, 5, 0.70f);   // objek lain
        List<Detection> d = YoloPostprocess.decode(out, CH, N, true, 640, 640, 640, 0.25f, 0.6f, 100);
        assertEquals(2, d.size());
        assertEquals(3, d.get(0).classIndex);
        assertEquals(5, d.get(1).classIndex);
    }

    @Test
    public void bentukKeluaranTransposAnchorDuluDibacaSama() {
        float[] out = new float[N * CH];
        // Bentuk [anchors, channels]: indeks = anchor * channels + channel.
        int a = 7;
        out[a * CH + 0] = 320f;
        out[a * CH + 1] = 320f;
        out[a * CH + 2] = 100f;
        out[a * CH + 3] = 100f;
        out[a * CH + 4 + 2] = 0.8f;
        List<Detection> d = YoloPostprocess.decode(out, CH, N, false, 640, 640, 640, 0.25f, 0.6f, 100);
        assertEquals(1, d.size());
        assertEquals(2, d.get(0).classIndex);
        assertTrue(d.get(0).x1 > 0.4f && d.get(0).x2 < 0.6f);
    }

    @Test
    public void jumlahDeteksiDibatasiMaxDet() {
        float[] out = new float[CH * N];
        for (int i = 0; i < 50; i++) {
            // Kotak kecil yang tidak saling tumpang tindih, di grid.
            tulis(out, i, 20f + (i % 10) * 60f, 20f + (i / 10) * 60f, 30f, 30f, 3, 0.9f);
        }
        List<Detection> d = YoloPostprocess.decode(out, CH, N, true, 640, 640, 640, 0.25f, 0.6f, 20);
        assertEquals(20, d.size());
    }
}
