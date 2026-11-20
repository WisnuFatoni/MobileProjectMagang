package com.example.fai26.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import com.google.android.material.color.MaterialColors;

/**
 * Latar buram untuk efek kaca: menggambar isi {@code sumber} (misalnya layar yang sedang bergulir) pada
 * posisi di belakang view ini ke bitmap kecil, mengaburkannya, lalu membentangkannya kembali.
 * Tidak memakai RenderEffect, jadi berlaku di semua versi Android. Bitmapnya sangat kecil sehingga murah.
 * Panggil {@link #segarkan()} setiap isi di belakang berubah (bergulir, berganti layar).
 */
public class BackdropBlurView extends View {

    /** Satu piksel bitmap mewakili sekian piksel layar. */
    private static final int SKALA = 6;
    private static final int RADIUS_KABUR = 3;
    private static final int ULANG_KABUR = 2;

    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Rect tujuan = new Rect();
    private final int[] lokasiSaya = new int[2];
    private final int[] lokasiSumber = new int[2];

    private View sumber;
    private Bitmap kecil;
    private Canvas kanvas;
    private int[] pix;
    private int[] sementara;
    private boolean menunggu = false;

    public BackdropBlurView(Context context, AttributeSet attrs) {
        super(context, attrs);
        // Warna kaca lebih hidup dan sedikit lebih terang, seperti material kaca di iOS.
        ColorMatrix m = new ColorMatrix();
        m.setSaturation(2.0f);
        ColorMatrix terang = new ColorMatrix(new float[]{
                1.08f, 0, 0, 0, 8,
                0, 1.08f, 0, 0, 8,
                0, 0, 1.08f, 0, 8,
                0, 0, 0, 1, 0});
        m.postConcat(terang);
        paint.setColorFilter(new ColorMatrixColorFilter(m));
    }

    public void setSumber(View sumber) {
        this.sumber = sumber;
        segarkan();
    }

    /** Menjadwalkan penangkapan ulang pada frame berikutnya (digabung bila dipanggil berkali-kali). */
    public void segarkan() {
        if (menunggu || sumber == null) return;
        menunggu = true;
        postOnAnimation(this::tangkap);
    }

    private void tangkap() {
        menunggu = false;
        if (sumber == null || getWidth() == 0 || getHeight() == 0 || !isShown()) return;

        int w = Math.max(8, getWidth() / SKALA);
        int h = Math.max(4, getHeight() / SKALA);
        if (kecil == null || kecil.getWidth() != w || kecil.getHeight() != h) {
            kecil = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            kanvas = new Canvas(kecil);
            pix = new int[w * h];
            sementara = new int[w * h];
        }

        getLocationInWindow(lokasiSaya);
        sumber.getLocationInWindow(lokasiSumber);
        float dx = lokasiSaya[0] - lokasiSumber[0];
        float dy = lokasiSaya[1] - lokasiSumber[1];

        kecil.eraseColor(MaterialColors.getColor(this, android.R.attr.colorBackground));
        kanvas.save();
        kanvas.scale(1f / SKALA, 1f / SKALA);
        kanvas.translate(-dx, -dy);
        try {
            sumber.draw(kanvas);
        } catch (RuntimeException e) {
            // Bila ada view yang tidak bisa digambar ke bitmap, pakai latar polos saja.
        }
        kanvas.restore();

        kecil.getPixels(pix, 0, w, 0, 0, w, h);
        for (int i = 0; i < ULANG_KABUR; i++) {
            kaburHorizontal(pix, sementara, w, h, RADIUS_KABUR);
            kaburVertikal(sementara, pix, w, h, RADIUS_KABUR);
        }
        kecil.setPixels(pix, 0, w, 0, 0, w, h);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (kecil == null) return;
        tujuan.set(0, 0, getWidth(), getHeight());
        canvas.drawBitmap(kecil, null, tujuan, paint);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        segarkan();
    }

    // ---- Kabur kotak (box blur) pada bitmap kecil ----

    static void kaburHorizontal(int[] src, int[] dst, int w, int h, int r) {
        int div = 2 * r + 1;
        for (int y = 0; y < h; y++) {
            int baris = y * w;
            int rs = 0, gs = 0, bs = 0;
            for (int k = -r; k <= r; k++) {
                int p = src[baris + Math.max(0, Math.min(w - 1, k))];
                rs += (p >> 16) & 0xFF;
                gs += (p >> 8) & 0xFF;
                bs += p & 0xFF;
            }
            for (int x = 0; x < w; x++) {
                dst[baris + x] = 0xFF000000 | ((rs / div) << 16) | ((gs / div) << 8) | (bs / div);
                int tambah = src[baris + Math.min(w - 1, x + r + 1)];
                int buang = src[baris + Math.max(0, x - r)];
                rs += ((tambah >> 16) & 0xFF) - ((buang >> 16) & 0xFF);
                gs += ((tambah >> 8) & 0xFF) - ((buang >> 8) & 0xFF);
                bs += (tambah & 0xFF) - (buang & 0xFF);
            }
        }
    }

    static void kaburVertikal(int[] src, int[] dst, int w, int h, int r) {
        int div = 2 * r + 1;
        for (int x = 0; x < w; x++) {
            int rs = 0, gs = 0, bs = 0;
            for (int k = -r; k <= r; k++) {
                int p = src[Math.max(0, Math.min(h - 1, k)) * w + x];
                rs += (p >> 16) & 0xFF;
                gs += (p >> 8) & 0xFF;
                bs += p & 0xFF;
            }
            for (int y = 0; y < h; y++) {
                dst[y * w + x] = 0xFF000000 | ((rs / div) << 16) | ((gs / div) << 8) | (bs / div);
                int tambah = src[Math.min(h - 1, y + r + 1) * w + x];
                int buang = src[Math.max(0, y - r) * w + x];
                rs += ((tambah >> 16) & 0xFF) - ((buang >> 16) & 0xFF);
                gs += ((tambah >> 8) & 0xFF) - ((buang >> 8) & 0xFF);
                bs += (tambah & 0xFF) - (buang & 0xFF);
            }
        }
    }
}
