package com.example.fai26.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.fai26.R;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu bawah bergaya kaca (terinspirasi Liquid Glass iOS): kapsul melayang dengan latar buram dari isi
 * layar di belakangnya, lapisan warna tipis, kilau di tepi atas, garis tepi bergradien, dan kapsul
 * pilihan yang meluncur membal ke tab yang dipilih.
 *
 * Android tidak punya material kaca bawaan dengan pembiasan seperti iOS 26, jadi ini pendekatan:
 * kabur + warna lebih hidup + kilau + gerak.
 */
public class GlassNavBar extends FrameLayout {

    public interface Listener {
        void onTabDipilih(int indeks);
    }

    public static final class Tab {
        final int ikon;
        final String label;

        public Tab(int ikon, String label) {
            this.ikon = ikon;
            this.label = label;
        }
    }

    private final float dp;
    private final BackdropBlurView latar;
    private final View indikator;
    private final LapisanKaca lapisan;
    private final LinearLayout baris;
    private final Paint garisTepi = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint garisLuar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF kotakTepi = new RectF();
    private final List<View> item = new ArrayList<>();
    private final List<ImageView> ikonItem = new ArrayList<>();
    private final List<TextView> labelItem = new ArrayList<>();

    private Listener listener;
    private int terpilih = 0;

    // Seret kapsul penanda (seperti iOS): kapsul mengikuti jari, lalu menempel ke tab terdekat saat dilepas.
    private final int slopSentuh;
    private boolean menyeret = false;
    private float downX;
    private float downY;
    private float selisihPusat;
    private int tabSementara = 0;
    private ValueAnimator animasiIndikator;

    private final ViewTreeObserver.OnScrollChangedListener tanganiGulir = this::segarkanLatar;
    private final ViewTreeObserver.OnGlobalLayoutListener tanganiLayout = this::segarkanLatar;

    public GlassNavBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
        slopSentuh = ViewConfiguration.get(context).getScaledTouchSlop();
        setWillNotDraw(false);

        // Bentuk kapsul: memotong semua lapisan di dalamnya.
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), view.getHeight() / 2f);
            }
        });

        // Lapis 1: latar buram.
        latar = new BackdropBlurView(context, null);
        addView(latar, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Lapis 2: warna kaca dan kilau atas.
        lapisan = new LapisanKaca(context);
        addView(lapisan, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // Lapis 3: kapsul pilihan.
        indikator = new View(context);
        GradientDrawable kapsul = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{context.getColor(R.color.glass_indicator_top), context.getColor(R.color.glass_indicator_bottom)});
        kapsul.setStroke((int) Math.max(1, dp), context.getColor(R.color.glass_indicator_stroke));
        kapsul.setCornerRadius(100 * dp);
        indikator.setBackground(kapsul);
        FrameLayout.LayoutParams lpIndikator = new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
        lpIndikator.setMargins(0, (int) (6 * dp), 0, (int) (6 * dp));
        addView(indikator, lpIndikator);

        // Lapis 4: ikon dan label.
        baris = new LinearLayout(context);
        baris.setOrientation(LinearLayout.HORIZONTAL);
        baris.setPadding((int) (8 * dp), 0, (int) (8 * dp), 0);
        addView(baris, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        garisTepi.setStyle(Paint.Style.STROKE);
        garisTepi.setStrokeWidth(1.5f * dp);
        garisLuar.setStyle(Paint.Style.STROKE);
        garisLuar.setStrokeWidth(1f * dp);
        garisLuar.setColor(context.getColor(R.color.glass_hairline));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** @param sumber isi layar yang berada di belakang bilah ini dan menjadi bahan kaburnya. */
    public void setSumber(View sumber) {
        latar.setSumber(sumber);
    }

    public void setTabs(List<Tab> tabs) {
        baris.removeAllViews();
        item.clear();
        ikonItem.clear();
        labelItem.clear();
        for (int i = 0; i < tabs.size(); i++) {
            final int indeks = i;
            Tab t = tabs.get(i);

            LinearLayout satu = new LinearLayout(getContext());
            satu.setOrientation(LinearLayout.VERTICAL);
            satu.setGravity(Gravity.CENTER);
            satu.setContentDescription(t.label);
            satu.setClickable(true);
            satu.setFocusable(true);

            ImageView ikon = new ImageView(getContext());
            ikon.setImageResource(t.ikon);
            ikon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            satu.addView(ikon, new LinearLayout.LayoutParams((int) (26 * dp), (int) (26 * dp)));

            TextView label = new TextView(getContext());
            label.setText(t.label);
            label.setTextSize(11);
            label.setSingleLine(true);
            label.setGravity(Gravity.CENTER);
            label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams lpLabel = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpLabel.topMargin = (int) (2 * dp);
            satu.addView(label, lpLabel);

            // Efek tekan: mengecil sedikit lalu kembali.
            satu.setOnTouchListener((v, e) -> {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(90).start();
                } else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(160)
                            .setInterpolator(new OvershootInterpolator(2f)).start();
                }
                return false;
            });
            satu.setOnClickListener(v -> {
                if (indeks == terpilih) return;
                pilih(indeks, true);
                if (listener != null) listener.onTabDipilih(indeks);
            });

            baris.addView(satu, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            item.add(satu);
            ikonItem.add(ikon);
            labelItem.add(label);
        }
        pilih(Math.min(terpilih, tabs.size() - 1), false);
    }

    /** Menandai tab terpilih (tanpa memanggil listener). */
    public void pilih(int indeks, boolean animasi) {
        int lama = terpilih;
        terpilih = indeks;
        tabSementara = indeks;
        warnai(indeks);
        geserIndikator(animasi && lama != indeks);
        if (animasi && indeks < ikonItem.size()) {
            ikonItem.get(indeks).animate().scaleX(1.18f).scaleY(1.18f).setDuration(120).withEndAction(() ->
                    ikonItem.get(indeks).animate().scaleX(1f).scaleY(1f).setDuration(220)
                            .setInterpolator(new OvershootInterpolator(2.5f)).start()).start();
        }
        segarkanLatar();
    }

    /** Memberi warna tab yang disorot (terpilih atau sedang dilewati kapsul saat diseret). */
    private void warnai(int indeks) {
        for (int i = 0; i < item.size(); i++) {
            boolean aktif = i == indeks;
            int warna = aktif
                    ? MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)
                    : MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant);
            ikonItem.get(i).setColorFilter(warna);
            labelItem.get(i).setTextColor(warna);
            labelItem.get(i).setTypeface(null, aktif ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            item.get(i).setSelected(aktif);
        }
    }

    private float lebarItem() {
        float lebarRuang = getWidth() - baris.getPaddingLeft() - baris.getPaddingRight();
        return item.isEmpty() ? 0 : lebarRuang / item.size();
    }

    /** Posisi X kiri kapsul pada tab pertama. */
    private float awalKapsul() {
        return baris.getPaddingLeft() + 3 * dp;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (item.size() < 2) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                // Geseran mendatar melewati ambang: ambil alih dari tab yang sedang ditekan.
                if (!menyeret && Math.abs(e.getX() - downX) > slopSentuh
                        && Math.abs(e.getX() - downX) > Math.abs(e.getY() - downY)) {
                    mulaiSeret(e);
                    return true;
                }
                break;
            default:
                break;
        }
        return menyeret;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!menyeret) return super.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                ikutiJari(e.getX());
                return true;
            case MotionEvent.ACTION_UP:
                lepasSeret(true);
                return true;
            case MotionEvent.ACTION_CANCEL:
                lepasSeret(false);
                return true;
            default:
                return true;
        }
    }

    private void mulaiSeret(MotionEvent e) {
        menyeret = true;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        if (animasiIndikator != null) animasiIndikator.cancel();
        // Jari menempel di mana pun; kapsul tidak melompat ke bawah jari, tetapi ikut bergerak dari posisinya.
        selisihPusat = indikator.getTranslationX() + indikator.getLayoutParams().width / 2f - e.getX();
        // Kapsul "terangkat": sedikit membesar dan lebih terang selama dipegang.
        indikator.animate().scaleX(1.12f).scaleY(1.18f).setDuration(140)
                .setInterpolator(new OvershootInterpolator(2f)).start();
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    /** Mengatur posisi kapsul dan menggeser kilau diagonal pada kaca mengikutinya. */
    private void aturPosisi(float tx) {
        indikator.setTranslationX(tx);
        if (getWidth() > 0) {
            lapisan.setPosisiKilau((tx + indikator.getLayoutParams().width / 2f) / getWidth());
        }
    }

    /** Efek lensa: ikon dan label di bawah kapsul membesar saat kapsul diseret. */
    private void lensa(float tx) {
        float pusatKapsul = tx + indikator.getLayoutParams().width / 2f;
        float lebar = lebarItem();
        float awal = awalKapsul();
        float setengah = indikator.getLayoutParams().width / 2f;
        for (int i = 0; i < ikonItem.size(); i++) {
            float pusatTab = awal + i * lebar + setengah;
            float s = NavGeser.skalaLensa(pusatKapsul, pusatTab, lebar, 1.25f);
            ikonItem.get(i).setScaleX(s);
            ikonItem.get(i).setScaleY(s);
            float sl = 1f + (s - 1f) * 0.5f;
            labelItem.get(i).setScaleX(sl);
            labelItem.get(i).setScaleY(sl);
        }
    }

    private void ikutiJari(float x) {
        float w = indikator.getLayoutParams().width;
        float awal = awalKapsul();
        float lebar = lebarItem();
        float tx = NavGeser.batas(x + selisihPusat - w / 2f, awal, lebar, item.size());
        aturPosisi(tx);
        lensa(tx);
        int baru = NavGeser.indeksTerdekat(tx, awal, lebar, item.size());
        if (baru != tabSementara) {
            tabSementara = baru;
            warnai(baru);
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
    }

    private void lepasSeret(boolean terapkan) {
        menyeret = false;
        for (int i = 0; i < ikonItem.size(); i++) {
            ikonItem.get(i).animate().scaleX(1f).scaleY(1f).setDuration(200).start();
            labelItem.get(i).animate().scaleX(1f).scaleY(1f).setDuration(200).start();
        }
        indikator.animate().scaleX(1f).scaleY(1f).setDuration(220)
                .setInterpolator(new OvershootInterpolator(2f)).start();
        int tujuan = terapkan ? tabSementara : terpilih;
        if (tujuan != terpilih) {
            pilih(tujuan, true);
            if (listener != null) listener.onTabDipilih(tujuan);
        } else {
            tabSementara = terpilih;
            warnai(terpilih);
            geserIndikator(true); // menempel kembali ke tab terpilih
        }
    }

    private void geserIndikator(boolean animasi) {
        if (item.isEmpty() || getWidth() == 0) return;
        float lebarItem = lebarItem();
        int lebarKapsul = (int) (lebarItem - 6 * dp);
        FrameLayout.LayoutParams lp = (LayoutParams) indikator.getLayoutParams();
        if (lp.width != lebarKapsul) {
            lp.width = lebarKapsul;
            indikator.setLayoutParams(lp);
        }
        float tujuan = baris.getPaddingLeft() + terpilih * lebarItem + 3 * dp;
        if (!animasi) {
            aturPosisi(tujuan);
            return;
        }
        // Meluncur dengan sedikit melampaui lalu kembali, seperti gerak per di iOS.
        if (animasiIndikator != null) animasiIndikator.cancel();
        ValueAnimator a = ValueAnimator.ofFloat(indikator.getTranslationX(), tujuan);
        a.setDuration(420);
        a.setInterpolator(new OvershootInterpolator(1.3f));
        a.addUpdateListener(x -> aturPosisi((float) x.getAnimatedValue()));
        animasiIndikator = a;
        a.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        invalidateOutline();
        post(() -> geserIndikator(false));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ViewTreeObserver o = getViewTreeObserver();
        o.addOnScrollChangedListener(tanganiGulir);
        o.addOnGlobalLayoutListener(tanganiLayout);
    }

    @Override
    protected void onDetachedFromWindow() {
        ViewTreeObserver o = getViewTreeObserver();
        if (o.isAlive()) {
            o.removeOnScrollChangedListener(tanganiGulir);
            o.removeOnGlobalLayoutListener(tanganiLayout);
        }
        super.onDetachedFromWindow();
    }

    /** Memperbarui latar buram. Dipanggil juga dari luar saat layar berganti. */
    public void segarkanLatar() {
        latar.segarkan();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        // Garis tipis gelap di paling luar supaya kapsul tetap terlihat di latar terang.
        float luar = garisLuar.getStrokeWidth() / 2f;
        kotakTepi.set(luar, luar, getWidth() - luar, getHeight() - luar);
        canvas.drawRoundRect(kotakTepi, getHeight() / 2f - luar, getHeight() / 2f - luar, garisLuar);

        // Garis tepi terang: paling cerah di kiri atas, redup di kanan bawah.
        float inset = garisLuar.getStrokeWidth() + garisTepi.getStrokeWidth() / 2f;
        kotakTepi.set(inset, inset, getWidth() - inset, getHeight() - inset);
        int terang = getContext().getColor(R.color.glass_stroke_start);
        int redup = getContext().getColor(R.color.glass_rim_dim);
        garisTepi.setShader(new LinearGradient(0, 0, getWidth(), getHeight(),
                new int[]{terang, redup, redup, getContext().getColor(R.color.glass_stroke_end)},
                new float[]{0f, 0.3f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        float r = getHeight() / 2f - inset;
        canvas.drawRoundRect(kotakTepi, r, r, garisTepi);
    }

    /**
     * Lapisan kaca: warna tipis, kilau di tepi atas, pantulan cahaya lembut dari bawah, dan satu pita kilau
     * diagonal yang bergeser mengikuti kapsul (seperti pantulan cahaya pada permukaan kaca).
     */
    private static final class LapisanKaca extends View {
        private final Paint warna = new Paint();
        private final Paint kilau = new Paint();
        private final Paint pantulan = new Paint();
        private final Paint pita = new Paint();
        private final Path jalur = new Path();
        private final int warnaPita;
        private float posisi = 0.15f;

        LapisanKaca(Context c) {
            super(c);
            warna.setColor(c.getColor(R.color.glass_tint));
            warnaPita = c.getColor(R.color.glass_glare);
        }

        void setPosisiKilau(float frac) {
            posisi = frac;
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            kilau.setShader(new LinearGradient(0, 0, 0, h * 0.5f,
                    getContext().getColor(R.color.glass_sheen), 0x00FFFFFF, Shader.TileMode.CLAMP));
            pantulan.setShader(new LinearGradient(0, h, 0, h * 0.55f,
                    getContext().getColor(R.color.glass_bottom_glow), 0x00FFFFFF, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            canvas.drawRect(0, 0, w, h, warna);
            canvas.drawRect(0, 0, w, h * 0.5f, kilau);
            canvas.drawRect(0, h * 0.55f, w, h, pantulan);

            // Pita kilau miring: jajaran genjang yang pudar ke kedua sisinya.
            float cx = posisi * w;
            float setengah = w * 0.09f;
            float miring = h * 0.45f;
            pita.setShader(new LinearGradient(cx - setengah, 0, cx + setengah, 0,
                    new int[]{0x00FFFFFF, warnaPita, 0x00FFFFFF}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            jalur.reset();
            jalur.moveTo(cx - setengah + miring, 0);
            jalur.lineTo(cx + setengah + miring, 0);
            jalur.lineTo(cx + setengah - miring, h);
            jalur.lineTo(cx - setengah - miring, h);
            jalur.close();
            canvas.drawPath(jalur, pita);
        }
    }
}
