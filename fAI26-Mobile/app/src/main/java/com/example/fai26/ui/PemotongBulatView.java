package com.example.fai26.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/**
 * Pemotong foto profil: gambar bisa digeser dengan satu jari dan dizoom dengan dua jari (atau lewat {@link #atur(float)}), dengan
 * lingkaran sebagai jendela. Yang berada di dalam lingkaran itulah yang menjadi foto profil. Gambar selalu menutupi lingkaran penuh.
 */
public final class PemotongBulatView extends View {

    public interface Pendengar {
        /** Skala berubah (0..1 dari terkecil ke terbesar), untuk menyelaraskan penggeser zoom. */
        void zoomBerubah(float posisi);
    }

    private Bitmap gambar;
    private float tx;
    private float ty;
    private float s = 1f;
    private float kiri;
    private float atas;
    private float sisi;
    private boolean siap;
    private float terakhirX;
    private float terakhirY;
    private int pointerAktif = -1;
    private Pendengar pendengar;

    private final Paint catGambar = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint catGelap = new Paint();
    private final Paint catHapus = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint catGaris = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint catPanduan = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix m = new Matrix();
    private final Path jalan = new Path();
    private final ScaleGestureDetector zoom;

    public PemotongBulatView(Context c) {
        super(c);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        catGelap.setColor(0xB3000000);
        catHapus.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        float d = c.getResources().getDisplayMetrics().density;
        catGaris.setStyle(Paint.Style.STROKE);
        catGaris.setStrokeWidth(2f * d);
        catGaris.setColor(Color.WHITE);
        catPanduan.setStyle(Paint.Style.STROKE);
        catPanduan.setStrokeWidth(1f * d);
        catPanduan.setColor(0x55FFFFFF);
        zoom = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector det) {
                if (gambar == null || !siap) return true;
                float[] h = GeometriPotong.zoomDi(tx, ty, s, s * det.getScaleFactor(), det.getFocusX(), det.getFocusY(),
                        gambar.getWidth(), gambar.getHeight(), kiri, atas, sisi);
                tx = h[0];
                ty = h[1];
                s = h[2];
                beritahu();
                invalidate();
                return true;
            }
        });
    }

    public void setPendengar(Pendengar p) {
        pendengar = p;
    }

    /** Mengganti gambar dan menempatkannya menutupi lingkaran di tengah. */
    public void setGambar(Bitmap b) {
        gambar = b;
        mulaiUlang();
        invalidate();
    }

    public Bitmap getGambar() {
        return gambar;
    }

    private void mulaiUlang() {
        if (gambar == null || getWidth() == 0) {
            siap = false;
            return;
        }
        float[] a = GeometriPotong.awal(gambar.getWidth(), gambar.getHeight(), kiri, atas, sisi);
        tx = a[0];
        ty = a[1];
        s = a[2];
        siap = true;
        beritahu();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        sisi = Math.min(w, h) * 0.86f;
        kiri = (w - sisi) / 2f;
        atas = (h - sisi) / 2f;
        mulaiUlang();
    }

    private void beritahu() {
        if (pendengar == null || gambar == null) return;
        float min = GeometriPotong.skalaMin(gambar.getWidth(), gambar.getHeight(), sisi);
        float maks = min * GeometriPotong.ZOOM_MAKS;
        pendengar.zoomBerubah(maks == min ? 0f : (s - min) / (maks - min));
    }

    /** Mengatur zoom lewat penggeser: posisi 0 (paling kecil) sampai 1 (paling besar), dari pusat lingkaran. */
    public void atur(float posisi) {
        if (gambar == null || !siap) return;
        float min = GeometriPotong.skalaMin(gambar.getWidth(), gambar.getHeight(), sisi);
        float sBaru = min + (min * GeometriPotong.ZOOM_MAKS - min) * Math.max(0f, Math.min(1f, posisi));
        float[] h = GeometriPotong.zoomDi(tx, ty, s, sBaru, kiri + sisi / 2f, atas + sisi / 2f,
                gambar.getWidth(), gambar.getHeight(), kiri, atas, sisi);
        tx = h[0];
        ty = h[1];
        s = h[2];
        invalidate();
    }

    /** Memutar gambar 90 derajat searah jarum jam lalu menempatkannya ulang. */
    public void putar() {
        if (gambar == null) return;
        Matrix r = new Matrix();
        r.postRotate(90);
        Bitmap baru = Bitmap.createBitmap(gambar, 0, 0, gambar.getWidth(), gambar.getHeight(), r, true);
        if (baru != gambar) gambar.recycle();
        setGambar(baru);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (gambar == null || !siap) return true;
        zoom.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                pointerAktif = e.getPointerId(0);
                terakhirX = e.getX();
                terakhirY = e.getY();
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_POINTER_UP:
                // Setelah jumlah jari berubah, mulai ulang dari jari yang tersisa supaya gambar tidak melompat.
                int idx = e.getActionMasked() == MotionEvent.ACTION_POINTER_UP ? (e.getActionIndex() == 0 ? 1 : 0) : e.getActionIndex();
                if (idx < e.getPointerCount()) {
                    pointerAktif = e.getPointerId(idx);
                    terakhirX = e.getX(idx);
                    terakhirY = e.getY(idx);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (!zoom.isInProgress()) {
                    int i = e.findPointerIndex(pointerAktif);
                    if (i >= 0) {
                        float[] g = GeometriPotong.jepitGeser(tx + e.getX(i) - terakhirX, ty + e.getY(i) - terakhirY, s,
                                gambar.getWidth(), gambar.getHeight(), kiri, atas, sisi);
                        tx = g[0];
                        ty = g[1];
                        terakhirX = e.getX(i);
                        terakhirY = e.getY(i);
                        invalidate();
                    }
                } else {
                    int i = e.findPointerIndex(pointerAktif);
                    if (i >= 0) {
                        terakhirX = e.getX(i);
                        terakhirY = e.getY(i);
                    }
                }
                break;
            default:
                break;
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas k) {
        k.drawColor(Color.BLACK);
        if (gambar == null || !siap) return;
        m.reset();
        m.postScale(s, s);
        m.postTranslate(tx, ty);
        k.drawBitmap(gambar, m, catGambar);

        // Bagian di luar lingkaran digelapkan; lingkaran dibiarkan terang.
        int lapis = k.saveLayer(0, 0, getWidth(), getHeight(), null);
        k.drawRect(0, 0, getWidth(), getHeight(), catGelap);
        k.drawCircle(kiri + sisi / 2f, atas + sisi / 2f, sisi / 2f, catHapus);
        k.restoreToCount(lapis);
        k.drawCircle(kiri + sisi / 2f, atas + sisi / 2f, sisi / 2f, catGaris);
        // Garis bantu sepertiga (membantu menempatkan wajah di tengah).
        jalan.reset();
        jalan.moveTo(kiri + sisi / 3f, atas);
        jalan.lineTo(kiri + sisi / 3f, atas + sisi);
        jalan.moveTo(kiri + 2 * sisi / 3f, atas);
        jalan.lineTo(kiri + 2 * sisi / 3f, atas + sisi);
        jalan.moveTo(kiri, atas + sisi / 3f);
        jalan.lineTo(kiri + sisi, atas + sisi / 3f);
        jalan.moveTo(kiri, atas + 2 * sisi / 3f);
        jalan.lineTo(kiri + sisi, atas + 2 * sisi / 3f);
        k.save();
        Path lingkaran = new Path();
        lingkaran.addCircle(kiri + sisi / 2f, atas + sisi / 2f, sisi / 2f, Path.Direction.CW);
        k.clipPath(lingkaran);
        k.drawPath(jalan, catPanduan);
        k.restore();
    }

    /** Hasil potongan persegi {@code sisiKeluar} x {@code sisiKeluar} dari bagian gambar yang berada di dalam lingkaran. */
    public Bitmap hasil(int sisiKeluar) {
        float[] d = GeometriPotong.daerahSumber(tx, ty, s, kiri, atas, sisi);
        Bitmap keluar = Bitmap.createBitmap(sisiKeluar, sisiKeluar, Bitmap.Config.ARGB_8888);
        Canvas k = new Canvas(keluar);
        float skalaKeluar = sisiKeluar / d[2];
        Matrix mm = new Matrix();
        mm.postTranslate(-d[0], -d[1]);
        mm.postScale(skalaKeluar, skalaKeluar);
        k.drawColor(Color.BLACK);
        k.drawBitmap(gambar, mm, catGambar);
        return keluar;
    }
}
