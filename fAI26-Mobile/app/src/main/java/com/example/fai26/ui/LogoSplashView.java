package com.example.fai26.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.core.content.res.ResourcesCompat;

import com.example.fai26.R;

/**
 * Animasi pembuka yang tenang: logo resmi fAI26 (LOGO/LogoApp.png) tersingkap dari kiri ke kanan dengan tepi lembut sambil naik
 * sedikit, kilau tipis lewat sekali, garis aksen hijau menarik keluar dari tengah, lalu keterangan muncul. Seluruhnya digambar dari
 * SATU animator (satu waktu t dalam milidetik) supaya semua gerak sinkron dan mulus.
 */
public final class LogoSplashView extends View {

    /** Panjang animasi sampai semuanya utuh (milidetik). */
    public static final long DURASI_MS = 2100;

    private static final int WARNA_LATAR = 0xFFFFFFFF;
    private static final int WARNA_LATAR_BAWAH = 0xFFF4F7EE;
    private static final int WARNA_AKSEN = 0xFF6A9B25;
    private static final int WARNA_AKSEN_TUA = 0xFF0A3626;
    private static final int WARNA_KET = 0xFF5B6B60;

    private final Bitmap logo;
    private final Paint catLatar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint catLogo = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint catMasker = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint catKilau = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint catGaris = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint catKet = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tujuan = new RectF();
    private final float kerapatan;

    private ValueAnimator animator;
    private float t; // milidetik sejak mulai
    private Runnable saatSelesai;

    public LogoSplashView(Context c) {
        super(c);
        kerapatan = c.getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        logo = BitmapFactory.decodeResource(c.getResources(), R.drawable.logo_fai26_warna, o);
        catMasker.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        catKilau.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
        catGaris.setStyle(Paint.Style.STROKE);
        catGaris.setStrokeCap(Paint.Cap.ROUND);
        catKet.setTypeface(ResourcesCompat.getFont(c, R.font.outfit));
        catKet.setColor(WARNA_KET);
        catKet.setTextAlign(Paint.Align.CENTER);
        catKet.setTextSize(14.5f * kerapatan);
        catKet.setLetterSpacing(0.12f);
    }

    /** Mulai memutar. {@code selesai} dipanggil sekali setelah gerak utama selesai (di thread utama). */
    public void mulai(Runnable selesai) {
        saatSelesai = selesai;
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(0f, DURASI_MS);
        animator.setDuration(DURASI_MS);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            t = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator a) {
                Runnable r = saatSelesai;
                saatSelesai = null;
                if (r != null) r.run();
            }
        });
        animator.start();
    }

    /** Langsung ke keadaan akhir (animasi dimatikan di pengaturan perangkat). */
    public void langsungAkhir(Runnable selesai) {
        t = DURASI_MS;
        invalidate();
        if (selesai != null) post(selesai);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        saatSelesai = null;
        if (animator != null) animator.cancel();
    }

    // ---- fungsi pelunak ----------------------------------------------------------------------------------

    private static float klem(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private float maju(float mulai, float lama) {
        return klem((t - mulai) / lama);
    }

    private static float keluarKubik(float x) {
        float y = 1 - x;
        return 1 - y * y * y;
    }

    private static float masukKeluar(float x) {
        return x < 0.5f ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2;
    }

    // ---- gambar ------------------------------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas k) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0 || logo == null) return;

        // Latar putih ke krem-hijau yang sangat tipis di bawah.
        catLatar.setShader(new LinearGradient(0, 0, 0, h, WARNA_LATAR, WARNA_LATAR_BAWAH, Shader.TileMode.CLAMP));
        k.drawRect(0, 0, w, h, catLatar);

        float lebar = w * 0.64f;
        float tinggi = lebar * logo.getHeight() / logo.getWidth();
        float pusatY = h * 0.45f;

        // Logo: muncul dan naik pelan.
        float pm = keluarKubik(maju(150, 900));
        float skala = 0.955f + 0.045f * pm;
        float naik = (1 - pm) * 16 * kerapatan;
        float cx = w / 2f;
        float cy = pusatY + naik;
        tujuan.set(cx - lebar * skala / 2f, cy - tinggi * skala / 2f, cx + lebar * skala / 2f, cy + tinggi * skala / 2f);

        // Singkap kiri ke kanan dengan tepi lembut (masker gradien DST_IN pada lapisan sementara).
        float sapu = masukKeluar(maju(150, 1000));
        float bulu = lebar * 0.28f;
        float tepi = tujuan.left - bulu + (lebar * skala + 2 * bulu) * sapu;
        int lapis = k.saveLayer(tujuan.left - 4, tujuan.top - 4, tujuan.right + 4, tujuan.bottom + 4, null);
        catLogo.setAlpha((int) (255 * klem(pm * 2.2f)));
        k.drawBitmap(logo, null, tujuan, catLogo);

        // Kilau tipis sekali lewat, hanya di atas huruf (SRC_ATOP menjaga bentuk logo).
        float kil = masukKeluar(maju(1100, 800));
        if (kil > 0 && kil < 1) {
            float x = tujuan.left - lebar * 0.2f + (lebar * 1.4f) * kil;
            catKilau.setShader(new LinearGradient(x - lebar * 0.07f, 0, x + lebar * 0.07f, 0,
                    new int[]{0x00FFFFFF, 0x80FFFFFF, 0x00FFFFFF}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            k.drawRect(x - lebar * 0.07f, tujuan.top, x + lebar * 0.07f, tujuan.bottom, catKilau);
            catKilau.setShader(null);
        }

        catMasker.setShader(new LinearGradient(tepi - bulu, 0, tepi, 0, 0xFF000000, 0x00000000, Shader.TileMode.CLAMP));
        k.drawRect(tujuan.left - 4, tujuan.top - 4, tujuan.right + 4, tujuan.bottom + 4, catMasker);
        catMasker.setShader(null);
        k.restoreToCount(lapis);

        // Garis aksen tipis yang menarik keluar dari tengah.
        float pg = keluarKubik(maju(1000, 600));
        if (pg > 0) {
            float y = tujuan.bottom + 30 * kerapatan;
            float setengah = lebar * 0.22f * pg;
            catGaris.setStrokeWidth(2.4f * kerapatan);
            catGaris.setShader(new LinearGradient(cx - setengah, 0, cx + setengah, 0, WARNA_AKSEN_TUA, WARNA_AKSEN, Shader.TileMode.CLAMP));
            k.drawLine(cx - setengah, y, cx + setengah, y, catGaris);
            catGaris.setShader(null);
        }

        // Keterangan.
        float pk = keluarKubik(maju(1300, 650));
        if (pk > 0) {
            float y = tujuan.bottom + 62 * kerapatan;
            catKet.setAlpha((int) (255 * pk));
            k.drawText("GRADING TBS KELAPA SAWIT", cx, y + (1 - pk) * 10 * kerapatan, catKet);
        }
    }
}
