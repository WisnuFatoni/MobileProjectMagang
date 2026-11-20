package com.example.fai26.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Bingkai pembidik untuk pemindai QR: latar digelapkan, bagian tengah berbentuk persegi dibiarkan terang,
 * dengan empat sudut tebal. {@link #sisi()} dipakai pemindai untuk memotong gambar yang dibaca.
 */
public class ViewfinderView extends View {

    /** Sisi bingkai sebagai bagian dari sisi terpendek layar. */
    public static final float BAGIAN_LAYAR = 0.68f;

    private final Paint scrim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sudut = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path jendela = new Path();
    private final RectF kotak = new RectF();
    private int warnaSudut = 0xFF4ADE80;

    public ViewfinderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float d = getResources().getDisplayMetrics().density;
        scrim.setColor(0x99000000);
        sudut.setStyle(Paint.Style.STROKE);
        sudut.setStrokeWidth(5 * d);
        sudut.setStrokeCap(Paint.Cap.ROUND);
        sudut.setColor(warnaSudut);
    }

    /** Sisi bingkai dalam piksel layar. */
    public float sisi() {
        return Math.min(getWidth(), getHeight()) * BAGIAN_LAYAR;
    }

    /** Warna sudut: hijau normal, merah muda bila QR salah. */
    public void setWarnaSudut(int warna) {
        warnaSudut = warna;
        sudut.setColor(warna);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;
        float d = getResources().getDisplayMetrics().density;
        float s = sisi();
        // Bingkai sedikit di atas tengah supaya tidak tertutup tombol bawah.
        float cx = w / 2f;
        float cy = h * 0.42f;
        kotak.set(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2);

        jendela.reset();
        jendela.addRoundRect(kotak, 20 * d, 20 * d, Path.Direction.CW);
        canvas.save();
        canvas.clipOutPath(jendela);
        canvas.drawRect(0, 0, w, h, scrim);
        canvas.restore();

        // Empat sudut tebal.
        float p = s * 0.16f;
        float r = 20 * d;
        Path garis = new Path();
        // kiri atas
        garis.moveTo(kotak.left, kotak.top + p);
        garis.lineTo(kotak.left, kotak.top + r);
        garis.quadTo(kotak.left, kotak.top, kotak.left + r, kotak.top);
        garis.lineTo(kotak.left + p, kotak.top);
        // kanan atas
        garis.moveTo(kotak.right - p, kotak.top);
        garis.lineTo(kotak.right - r, kotak.top);
        garis.quadTo(kotak.right, kotak.top, kotak.right, kotak.top + r);
        garis.lineTo(kotak.right, kotak.top + p);
        // kanan bawah
        garis.moveTo(kotak.right, kotak.bottom - p);
        garis.lineTo(kotak.right, kotak.bottom - r);
        garis.quadTo(kotak.right, kotak.bottom, kotak.right - r, kotak.bottom);
        garis.lineTo(kotak.right - p, kotak.bottom);
        // kiri bawah
        garis.moveTo(kotak.left + p, kotak.bottom);
        garis.lineTo(kotak.left + r, kotak.bottom);
        garis.quadTo(kotak.left, kotak.bottom, kotak.left, kotak.bottom - r);
        garis.lineTo(kotak.left, kotak.bottom - p);
        canvas.drawPath(garis, sudut);
    }

    /** Batas bawah bingkai (piksel layar), untuk menempatkan teks petunjuk. */
    public float bawahBingkai() {
        return getHeight() * 0.42f + sisi() / 2f;
    }
}
