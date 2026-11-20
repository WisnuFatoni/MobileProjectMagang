package com.example.fai26.ipd;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import com.example.fai26.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

import java.text.NumberFormat;
import java.util.Locale;

/** Pembangun tampilan kecil untuk layar IPD (dibuat lewat kode, gayanya mengikuti layar Panen). */
public final class IpdUi {

    private static final Locale ID = new Locale("id", "ID");

    private IpdUi() {
    }

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static TextView teks(Context c, CharSequence isi, float sp, boolean tebal, int atribut) {
        TextView t = new TextView(c);
        t.setText(isi);
        t.setTextSize(sp);
        if (tebal) t.setTypeface(ResourcesCompat.getFont(c, R.font.outfit_semibold));
        t.setTextColor(MaterialColors.getColor(t, atribut));
        return t;
    }

    public static TextView judul(Context c, CharSequence isi, float sp) {
        return teks(c, isi, sp, true, com.google.android.material.R.attr.colorOnSurface);
    }

    public static TextView redup(Context c, CharSequence isi, float sp) {
        return teks(c, isi, sp, false, com.google.android.material.R.attr.colorOnSurfaceVariant);
    }

    public static LinearLayout.LayoutParams lp(int w, int h, int kiri, int atas, int kanan, int bawah, Context c) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(c, kiri), dp(c, atas), dp(c, kanan), dp(c, bawah));
        return p;
    }

    public static LinearLayout.LayoutParams lebarPenuh(Context c, int atas, int bawah) {
        return lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, atas, 0, bawah, c);
    }

    /** Kartu putih bergaris tipis (gaya Widget.Fai26.Card) berisi LinearLayout vertikal; isinya dikembalikan lewat {@code isi[0]}. */
    public static MaterialCardView kartu(Context c, LinearLayout[] isi) {
        MaterialCardView k = new MaterialCardView(c);
        k.setLayoutParams(lebarPenuh(c, 0, 12));
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        k.addView(l);
        isi[0] = l;
        return k;
    }

    /** Tombol besar mudah ditekan di lapangan (tinggi 56 dp). */
    public static MaterialButton tombol(Context c, String label, boolean utama) {
        MaterialButton b = new MaterialButton(c, null,
                utama ? com.google.android.material.R.attr.materialButtonStyle : com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setMinimumHeight(dp(c, 56));
        b.setTypeface(ResourcesCompat.getFont(c, R.font.outfit_semibold));
        return b;
    }

    public static View jarak(Context c, int tinggiDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, tinggiDp)));
        return v;
    }

    public static TextView lencana(Context c, String isi, int warnaLatar, int warnaTeks) {
        TextView t = new TextView(c);
        t.setText(isi);
        t.setTextSize(12);
        t.setTypeface(ResourcesCompat.getFont(c, R.font.outfit_semibold));
        t.setTextColor(warnaTeks);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4));
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(warnaLatar);
        g.setCornerRadius(dp(c, 12));
        t.setBackground(g);
        return t;
    }

    /** Warna lencana status IPD: (latar, teks). */
    public static int[] warnaStatus(Context c, String status) {
        switch (status == null ? "" : status) {
            case "DISETUJUI":
                return new int[]{0xFFDDF3E4, 0xFF0B3D1E};
            case "DITOLAK":
                return new int[]{0xFFFCE8E6, 0xFF7A1410};
            case "MENUNGGU_SETUJU":
                return new int[]{0xFFFEF3C7, 0xFF5B3B03};
            default: // DRAFT
                return new int[]{0xFFE6E8E5, 0xFF3A3F3C};
        }
    }

    public static String labelStatus(String status) {
        switch (status == null ? "" : status) {
            case "DISETUJUI":
                return "Disetujui";
            case "DITOLAK":
                return "Ditolak";
            case "MENUNGGU_SETUJU":
                return "Menunggu asisten";
            default:
                return "Draf";
        }
    }

    public static String persen(double v) {
        NumberFormat f = NumberFormat.getNumberInstance(ID);
        f.setMaximumFractionDigits(1);
        f.setMinimumFractionDigits(0);
        return f.format(v) + "%";
    }

    /** "2026-11-20" menjadi "20 Nov 2026". */
    public static String tanggal(String yyyyMmDd) {
        try {
            java.util.Date d = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(yyyyMmDd);
            return new java.text.SimpleDateFormat("d MMM yyyy", ID).format(d);
        } catch (Exception e) {
            return yyyyMmDd;
        }
    }
}
