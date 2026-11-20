package com.example.fai26.panorama;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.fai26.detect.Detection;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Menguji penyusunan panorama dari simulasi sisiran: frame dipotong dari satu pemandangan besar pada posisi yang
 * diketahui, lalu hasilnya dibandingkan dengan pemandangan aslinya.
 */
@RunWith(AndroidJUnit4.class)
public class PanoramaPemindaiTest {

    /** Frame tegak 720 x 1280 seperti hasil kamera 16:9 pada HP uji. */
    private static final int FW = 720;
    private static final int FH = 1280;

    private Context ctx;
    private File folder;
    private File keluaran;
    private PanoramaPemindai pano;
    private Bitmap scene;

    @Before
    public void siapkan() {
        ctx = ApplicationProvider.getApplicationContext();
        folder = new File(ctx.getCacheDir(), "panorama_uji");
        keluaran = new File(ctx.getCacheDir(), "panorama_uji_keluaran.jpg");
        pano = new PanoramaPemindai(folder);
        scene = buatScene(1700, 3100, 42);
    }

    @After
    public void bersihkan() {
        pano.bersihkan();
        keluaran.delete();
        scene.recycle();
    }

    // ---- bantuan ---------------------------------------------------------------------------------

    /** Derau nilai berlapis (mirip permukaan alami) sebagai pemandangan berwarna. */
    private static Bitmap buatScene(int w, int h, long benih) {
        int[] sel = {160, 80, 40, 20, 10};
        double[][] kanal = new double[3][w * h];
        Random acak = new Random(benih);
        for (int c = 0; c < 3; c++) {
            double amp = 1.0;
            double total = 0;
            for (int s : sel) {
                int gw = w / s + 2;
                int gh = h / s + 2;
                double[] grid = new double[gw * gh];
                for (int i = 0; i < grid.length; i++) grid[i] = acak.nextDouble();
                for (int y = 0; y < h; y++) {
                    double fy = (double) y / s;
                    int gy = (int) fy;
                    double ty = fy - gy;
                    for (int x = 0; x < w; x++) {
                        double fx = (double) x / s;
                        int gx = (int) fx;
                        double tx = fx - gx;
                        double a = grid[gy * gw + gx] * (1 - tx) + grid[gy * gw + gx + 1] * tx;
                        double b = grid[(gy + 1) * gw + gx] * (1 - tx) + grid[(gy + 1) * gw + gx + 1] * tx;
                        kanal[c][y * w + x] += amp * (a * (1 - ty) + b * ty);
                    }
                }
                total += amp;
                amp *= 0.7;
            }
            for (int i = 0; i < w * h; i++) kanal[c][i] /= total;
        }
        int[] px = new int[w * h];
        for (int i = 0; i < px.length; i++) {
            px[i] = Color.rgb((int) (255 * kanal[0][i]), (int) (255 * kanal[1][i]), (int) (255 * kanal[2][i]));
        }
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        b.setPixels(px, 0, w, 0, 0, w, h);
        return b;
    }

    /** Frame mentah dari kamera: pemandangan dipotong di (x, y), lalu diputar 90 derajat berlawanan arah jarum jam. */
    private Bitmap mentah(int x, int y) {
        Bitmap tegak = Bitmap.createBitmap(scene, x, y, FW, FH);
        Matrix m = new Matrix();
        m.postRotate(-90);
        return Bitmap.createBitmap(tegak, 0, 0, FW, FH, m, true); // 1280 x 720, rotasi 90 untuk menegakkan
    }

    private static final List<Detection> TANPA_DETEKSI = Collections.emptyList();

    private static double luma(int p) {
        return 0.30 * Color.red(p) + 0.59 * Color.green(p) + 0.11 * Color.blue(p);
    }

    /** Selisih rata-rata kecerahan antara panorama dan pemandangan asli pada daerah yang sama (diskala 0,75). */
    private double selisihDenganScene(Bitmap hasil, int x0, int y0, int lebarScene, int tinggiScene) {
        Bitmap acuan = Bitmap.createScaledBitmap(Bitmap.createBitmap(scene, x0, y0, lebarScene, tinggiScene),
                hasil.getWidth(), hasil.getHeight(), true);
        double total = 0;
        int n = 0;
        for (int y = 4; y < hasil.getHeight() - 4; y += 5) {
            for (int x = 4; x < hasil.getWidth() - 4; x += 5) {
                int p = hasil.getPixel(x, y);
                // Sudut yang tidak pernah terlihat kamera (latar abu gelap) tidak ikut dibandingkan.
                if (Math.abs(Color.red(p) - 24) < 8 && Math.abs(Color.green(p) - 24) < 8 && Math.abs(Color.blue(p) - 24) < 8) continue;
                total += Math.abs(luma(p) - luma(acuan.getPixel(x, y)));
                n++;
            }
        }
        acuan.recycle();
        assertTrue("harus ada piksel yang dibandingkan", n > 1000);
        return total / n;
    }

    // ---- tes -------------------------------------------------------------------------------------

    @Test
    public void sisirVertikalMenjadiSatuPanoramaYangCocokDenganPemandangan() throws Exception {
        int x = 100;
        int langkah = 30; // piksel foto per frame (7,5 piksel pada resolusi taksiran)
        int frame = 40;
        pano.mulai();
        long totalMs = 0;
        for (int i = 0; i < frame; i++) {
            Bitmap m = mentah(x, i * langkah);
            long t0 = System.nanoTime();
            pano.tambahFrame(m, 90, TANPA_DETEKSI);
            totalMs += (System.nanoTime() - t0) / 1_000_000;
            m.recycle();
        }
        Log.i("PANO_UJI", "rata-rata tambahFrame " + (totalMs / frame) + " ms");
        int yAkhir = (frame - 1) * langkah;
        Bitmap akhir = mentah(x, yAkhir);
        PanoramaPemindai.Hasil h = pano.selesai(akhir, 90, TANPA_DETEKSI, keluaran);

        assertTrue("harus jadi panorama, potongan=" + h.jumlahTile, h.panorama());
        assertTrue("potongan cukup banyak: " + h.jumlahTile, h.jumlahTile >= 4);
        Bitmap hasil = BitmapFactory.decodeFile(keluaran.getAbsolutePath());
        assertNotNull(hasil);
        // Lebar satu frame (540); tinggi = (1280 + 1170) x 0,75 = 1837,5; toleransi beberapa piksel dari ketelitian taksiran.
        assertEquals(540, hasil.getWidth());
        assertEquals(1838, hasil.getHeight(), 24);
        double selisih = selisihDenganScene(hasil, x, 0, FW, FH + yAkhir);
        Log.i("PANO_UJI", "vertikal: potongan=" + h.jumlahTile + " ukuran=" + hasil.getWidth() + "x" + hasil.getHeight()
                + " selisih=" + selisih);
        assertTrue("panorama harus cocok dengan pemandangan (selisih rata-rata " + selisih + ")", selisih < 10);
        assertTrue("waktu tambahFrame wajar: " + (totalMs / frame) + " ms", totalMs / frame < 120);
        hasil.recycle();
        akhir.recycle();
    }

    @Test
    public void sisirDiagonalMemperluasKeDuaArah() throws Exception {
        pano.mulai();
        int n = 36;
        for (int i = 0; i < n; i++) {
            Bitmap m = mentah(100 + i * 20, 50 + i * 24);
            pano.tambahFrame(m, 90, TANPA_DETEKSI);
            m.recycle();
        }
        Bitmap akhir = mentah(100 + (n - 1) * 20, 50 + (n - 1) * 24);
        PanoramaPemindai.Hasil h = pano.selesai(akhir, 90, TANPA_DETEKSI, keluaran);

        assertTrue(h.panorama());
        Bitmap hasil = BitmapFactory.decodeFile(keluaran.getAbsolutePath());
        // Lebar (720 + 700) x 0,75 = 1065; tinggi (1280 + 840) x 0,75 = 1590.
        assertEquals(1065, hasil.getWidth(), 24);
        assertEquals(1590, hasil.getHeight(), 24);
        double selisih = selisihDenganScene(hasil, 100, 50, FW + (n - 1) * 20, FH + (n - 1) * 24);
        Log.i("PANO_UJI", "diagonal: potongan=" + h.jumlahTile + " ukuran=" + hasil.getWidth() + "x" + hasil.getHeight()
                + " selisih=" + selisih);
        assertTrue("selisih " + selisih, selisih < 12);
        hasil.recycle();
        akhir.recycle();
    }

    @Test
    public void kameraDiamHanyaMenghasilkanFotoSatuFrameResolusiPenuh() throws Exception {
        pano.mulai();
        for (int i = 0; i < 30; i++) {
            Bitmap m = mentah(100, 100);
            pano.tambahFrame(m, 90, TANPA_DETEKSI);
            m.recycle();
        }
        Bitmap akhir = mentah(100, 100);
        PanoramaPemindai.Hasil h = pano.selesai(akhir, 90, TANPA_DETEKSI, keluaran);

        assertEquals(1, h.jumlahTile);
        assertFalse(h.panorama());
        Bitmap hasil = BitmapFactory.decodeFile(keluaran.getAbsolutePath());
        assertEquals("seperti sebelum ada panorama: frame tegak penuh", FW, hasil.getWidth());
        assertEquals(FH, hasil.getHeight());
        hasil.recycle();
        akhir.recycle();
    }

    @Test
    public void tanpaFrameSamaSekaliTidakAdaFoto() throws Exception {
        pano.mulai();
        PanoramaPemindai.Hasil h = pano.selesai(null, 90, TANPA_DETEKSI, keluaran);
        assertFalse(h.adaFoto());
        assertFalse(keluaran.exists());
    }

    @Test
    public void kameraTerlaluCepatTetapMenghasilkanFotoSah() throws Exception {
        // Lompat jauh antar frame (di luar jangkauan penyejajaran): panorama tidak mungkin, tetapi foto harus ada.
        pano.mulai();
        int[][] lokasi = {{0, 0}, {600, 1000}, {50, 1400}, {700, 100}, {300, 1500}, {10, 700}};
        for (int[] l : lokasi) {
            Bitmap m = mentah(l[0], l[1]);
            pano.tambahFrame(m, 90, TANPA_DETEKSI);
            m.recycle();
        }
        Bitmap akhir = mentah(400, 400);
        PanoramaPemindai.Hasil h = pano.selesai(akhir, 90, TANPA_DETEKSI, keluaran);
        assertTrue(h.adaFoto());
        Bitmap hasil = BitmapFactory.decodeFile(keluaran.getAbsolutePath());
        assertNotNull("hasil harus gambar yang bisa dibuka", hasil);
        hasil.recycle();
        akhir.recycle();
    }

    @Test
    public void kotakDeteksiTergambarPadaPotonganDanIkutDiPanorama() throws Exception {
        List<Detection> kotak = new ArrayList<>();
        kotak.add(new Detection(0.3f, 0.4f, 0.7f, 0.6f, 0.9f, 3)); // kelas 3 (Ripe)
        pano.mulai();
        for (int i = 0; i < 20; i++) {
            Bitmap m = mentah(100, i * 40);
            pano.tambahFrame(m, 90, kotak);
            m.recycle();
        }
        Bitmap akhir = mentah(100, 19 * 40);
        PanoramaPemindai.Hasil h = pano.selesai(akhir, 90, kotak, keluaran);
        assertTrue(h.panorama());
        Bitmap hasil = BitmapFactory.decodeFile(keluaran.getAbsolutePath());
        Bitmap acuan = Bitmap.createScaledBitmap(Bitmap.createBitmap(scene, 100, 0, FW, FH + 19 * 40),
                hasil.getWidth(), hasil.getHeight(), true);
        // Warna kotak kelas 3 harus tampak di panorama (piksel yang warnanya dekat dengan warna kotak).
        int warna = com.example.fai26.KelasModel.WARNA[3];
        int dekat = 0;
        for (int y = 0; y < hasil.getHeight(); y++) {
            for (int x = 0; x < hasil.getWidth(); x++) {
                int p = hasil.getPixel(x, y);
                if (Math.abs(Color.red(p) - Color.red(warna)) < 45 && Math.abs(Color.green(p) - Color.green(warna)) < 45
                        && Math.abs(Color.blue(p) - Color.blue(warna)) < 45) {
                    dekat++;
                }
            }
        }
        Log.i("PANO_UJI", "piksel berwarna kotak: " + dekat);
        assertTrue("kotak deteksi harus tampak di panorama (piksel warna kotak=" + dekat + ")", dekat > 300);
        hasil.recycle();
        acuan.recycle();
        akhir.recycle();
    }

    @Test
    public void mulaiUlangMembuangPotonganLamaDanBersihkanMenghapusBerkas() throws Exception {
        pano.mulai();
        for (int i = 0; i < 12; i++) {
            Bitmap m = mentah(100, i * 60);
            pano.tambahFrame(m, 90, TANPA_DETEKSI);
            m.recycle();
        }
        File[] sebelum = folder.listFiles();
        assertNotNull(sebelum);
        assertTrue(sebelum.length >= 2);
        pano.mulai();
        File[] sesudah = folder.listFiles();
        assertTrue(sesudah == null || sesudah.length == 0);
        pano.bersihkan();
    }
}
