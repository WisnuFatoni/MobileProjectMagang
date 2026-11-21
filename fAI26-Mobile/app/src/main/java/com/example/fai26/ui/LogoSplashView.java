package com.example.fai26.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.PathParser;

import com.example.fai26.R;

/**
 * Animasi pembuka: tandan sawit logo fAI26 "tumbuh" bulir demi bulir dari bawah, tiga pelepah mekar di atasnya, cincin cahaya
 * menyebar, lalu logo naik sedikit dan tulisan fAI26 muncul. Seluruhnya digambar dari SATU animator (satu waktu t dalam milidetik)
 * sehingga semua gerak selalu sinkron dan mulus, tanpa banyak animator yang saling menunggu.
 */
public final class LogoSplashView extends View {

    /** Panjang animasi sampai logo dan tulisan utuh (milidetik). */
    public static final long DURASI_MS = 2500;

    // Geometri logo (sama dengan res/drawable/ic_logo.xml, kotak 96 x 96).
    private static final float[][] BULIR = {
            {39.5f, 40}, {56.5f, 40}, {31, 55}, {48, 55}, {65, 55}, {39.5f, 70}, {56.5f, 70}, {48, 85}};
    /** Urutan tumbuh bulir: dari ujung bawah naik ke atas. */
    private static final int[] URUTAN = {7, 5, 6, 2, 3, 4, 0, 1};
    private static final float JARI_BULIR = 8f;
    private static final String DAUN_KIRI = "M48,30C40,16 26,12 14,17C25,25 37,30 48,30z";
    private static final String DAUN_KANAN = "M48,30C56,16 70,12 82,17C71,25 59,30 48,30z";
    private static final String DAUN_TENGAH = "M48,9C53,15 53,24 48,31C43,24 43,15 48,9z";

    private static final int WARNA_BULIR = 0xFFC2410C;
    private static final int WARNA_DAUN = 0xFF15803D;
    private static final int WARNA_DAUN_TENGAH = 0xFF22C55E;
    private static final int WARNA_LATAR = 0xFFFFFBF2;
    private static final int WARNA_TEKS = 0xFF12612F;
    private static final int WARNA_TEKS_REDUP = 0xFF6B7280;

    private final Path daunKiri = PathParser.createPathFromPathData(DAUN_KIRI);
    private final Path daunKanan = PathParser.createPathFromPathData(DAUN_KANAN);
    private final Path daunTengah = PathParser.createPathFromPathData(DAUN_TENGAH);
    private final Paint cat = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint catCincin = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint catLatar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint catJudul = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint catKet = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path jalan = new Path();
    private final float kerapatan;

    private ValueAnimator animator;
    private float t; // milidetik sejak mulai
    private Runnable saatSelesai;

    public LogoSplashView(Context c) {
        super(c);
        kerapatan = c.getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        Typeface semibold = ResourcesCompat.getFont(c, R.font.outfit_semibold);
        Typeface biasa = ResourcesCompat.getFont(c, R.font.outfit);
        catJudul.setTypeface(semibold);
        catJudul.setColor(WARNA_TEKS);
        catJudul.setTextAlign(Paint.Align.CENTER);
        catJudul.setTextSize(44 * kerapatan);
        catJudul.setLetterSpacing(0.02f);
        catKet.setTypeface(biasa);
        catKet.setColor(WARNA_TEKS_REDUP);
        catKet.setTextAlign(Paint.Align.CENTER);
        catKet.setTextSize(15 * kerapatan);
        catKet.setLetterSpacing(0.04f);
        catCincin.setStyle(Paint.Style.STROKE);
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

    /** Kemajuan 0..1 sebuah gerak yang mulai pada {@code mulai} dan berlangsung {@code lama} milidetik. */
    private float maju(float mulai, float lama) {
        return klem((t - mulai) / lama);
    }

    private static float keluarKubik(float x) {
        float y = 1 - x;
        return 1 - y * y * y;
    }

    private static float keluarBalik(float x) {
        final float c1 = 1.70158f;
        final float c3 = c1 + 1;
        float y = x - 1;
        return 1 + c3 * y * y * y + c1 * y * y;
    }

    private static float masukKeluar(float x) {
        return x < 0.5f ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2;
    }

    // ---- gambar ------------------------------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas k) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        // Latar krem dengan cahaya hijau lembut di tengah-atas.
        catLatar.setShader(new RadialGradient(w / 2f, h * 0.40f, Math.max(w, h) * 0.75f,
                new int[]{0xFFFFFFFF, WARNA_LATAR, 0xFFF1F6EA}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        k.drawRect(0, 0, w, h, catLatar);

        float ukuran = Math.min(w, h * 0.5f) * 0.46f; // sisi logo (piksel)
        float angkat = masukKeluar(maju(1500, 600));
        float pusatY = h * (0.5f - 0.08f * angkat);   // logo naik pelan saat tulisan muncul
        float kiri = w / 2f - ukuran / 2f;
        float atas = pusatY - ukuran / 2f;
        float skala = ukuran / 96f;

        // Cincin cahaya menyebar dari pusat logo (dua gelombang).
        for (int g = 0; g < 2; g++) {
            float p = maju(1050 + g * 280, 1100);
            if (p > 0 && p < 1) {
                float r = ukuran * (0.45f + 0.95f * keluarKubik(p));
                catCincin.setColor(0xFF22C55E);
                catCincin.setAlpha((int) (70 * (1 - p)));
                catCincin.setStrokeWidth((3.5f - 2f * p) * kerapatan);
                k.drawCircle(w / 2f, pusatY, r, catCincin);
            }
        }

        // Bayangan lembut di bawah logo (muncul bersama bulir).
        float bayang = keluarKubik(maju(100, 1000));
        if (bayang > 0) {
            cat.setShader(new RadialGradient(w / 2f, pusatY + ukuran * 0.52f, ukuran * 0.5f,
                    0x22000000, 0x00000000, Shader.TileMode.CLAMP));
            k.drawOval(w / 2f - ukuran * 0.5f, pusatY + ukuran * 0.52f - ukuran * 0.07f,
                    w / 2f + ukuran * 0.5f, pusatY + ukuran * 0.52f + ukuran * 0.07f, cat);
            cat.setShader(null);
        }

        k.save();
        k.translate(kiri, atas);
        k.scale(skala, skala);

        // Bulir: tumbuh satu per satu dari ujung bawah, memantul sedikit saat sampai.
        cat.setStyle(Paint.Style.FILL);
        cat.setColor(WARNA_BULIR);
        for (int n = 0; n < URUTAN.length; n++) {
            float[] b = BULIR[URUTAN[n]];
            float p = maju(120 + n * 85, 440);
            if (p <= 0) continue;
            float s = keluarBalik(p);
            cat.setAlpha((int) (255 * klem(p * 3)));
            k.drawCircle(b[0], b[1], JARI_BULIR * Math.max(0f, s), cat);
        }
        cat.setAlpha(255);

        // Pelepah samping mekar dari pangkal (48, 30): membesar sambil berputar ke posisi akhir.
        gambarDaun(k, daunKiri, WARNA_DAUN, maju(980, 640), -34f);
        gambarDaun(k, daunKanan, WARNA_DAUN, maju(1040, 640), 34f);
        // Pelepah tengah naik dari pangkal (48, 31).
        float pt = maju(860, 620);
        if (pt > 0) {
            k.save();
            k.scale(0.55f + 0.45f * keluarBalik(pt), 0.1f + 0.9f * keluarKubik(pt), 48f, 31f);
            cat.setColor(WARNA_DAUN_TENGAH);
            cat.setAlpha((int) (255 * klem(pt * 3)));
            k.drawPath(daunTengah, cat);
            cat.setAlpha(255);
            k.restore();
        }

        // Kilau sekali lewat di atas logo setelah utuh.
        float kilau = maju(1350, 650);
        if (kilau > 0 && kilau < 1) {
            float x = -30 + 156 * kilau;
            Paint pk = new Paint(Paint.ANTI_ALIAS_FLAG);
            pk.setShader(new LinearGradient(x - 14, 0, x + 14, 0, new int[]{0x00FFFFFF, 0x66FFFFFF, 0x00FFFFFF},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            jalan.reset();
            jalan.addCircle(48, 55, 44, Path.Direction.CW);
            k.save();
            k.clipPath(jalan);
            k.drawRect(x - 14, 0, x + 14, 96, pk);
            k.restore();
        }
        k.restore();

        // Tulisan: naik dari bawah sambil memudar masuk, lalu keterangan.
        float pj = keluarKubik(maju(1500, 650));
        if (pj > 0) {
            float y = pusatY + ukuran / 2f + 62 * kerapatan;
            catJudul.setAlpha((int) (255 * pj));
            k.drawText("fAI26", w / 2f, y + (1 - pj) * 22 * kerapatan, catJudul);
        }
        float pk2 = keluarKubik(maju(1750, 650));
        if (pk2 > 0) {
            float y = pusatY + ukuran / 2f + 94 * kerapatan;
            catKet.setAlpha((int) (255 * pk2));
            k.drawText("Grading TBS kelapa sawit di TPH", w / 2f, y + (1 - pk2) * 16 * kerapatan, catKet);
        }
    }

    private void gambarDaun(Canvas k, Path daun, int warna, float p, float sudutAwal) {
        if (p <= 0) return;
        float s = keluarBalik(p);
        k.save();
        k.rotate(sudutAwal * (1 - keluarKubik(p)), 48f, 30f);
        k.scale(Math.max(0f, s), Math.max(0f, s), 48f, 30f);
        cat.setColor(warna);
        cat.setAlpha((int) (255 * klem(p * 3)));
        k.drawPath(daun, cat);
        cat.setAlpha(255);
        k.restore();
    }
}
