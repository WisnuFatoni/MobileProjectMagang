package com.example.fai26;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.example.fai26.auth.Sesi;
import com.example.fai26.ipd.IpdUi;
import com.example.fai26.ui.KepalaHijau;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

/**
 * Layar awal setelah login: kepala hijau dan dua kotak fitur berdampingan (Panen dan IPD) yang menimpa kepala. Kotak yang tampil
 * mengikuti hak akses (akses.panen, akses.ipd) yang diatur admin di web, jadi siapa boleh membuka apa bisa diubah tanpa mengubah
 * aplikasi.
 */
public class HomeFragment extends Fragment {

    private KepalaHijau kepala;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final android.content.Context c = requireContext();
        // Akar: latar polos, tanda air fAI26 di belakang, lalu isi yang bisa digulir di atasnya.
        FrameLayout akar = new FrameLayout(c);
        akar.setBackgroundColor(MaterialColors.getColor(akar, android.R.attr.colorBackground));
        akar.addView(tandaAir(c)); // memakai parameter letaknya sendiri (di tengah area kosong)

        ScrollView gulir = new ScrollView(c);
        gulir.setFillViewport(true);
        gulir.setClipToPadding(false);
        akar.addView(gulir, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout kolom = new LinearLayout(c);
        kolom.setOrientation(LinearLayout.VERTICAL);
        gulir.addView(kolom, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        kepala = new KepalaHijau(c, 76);
        kolom.addView(kepala.view(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Kotak fitur berdampingan, menimpa kepala seperti kartu ringkasan di Beranda Panen.
        LinearLayout baris = new LinearLayout(c);
        baris.setOrientation(LinearLayout.HORIZONTAL);
        baris.setBaselineAligned(false);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.setMargins(IpdUi.dp(c, 16), -IpdUi.dp(c, 48), IpdUi.dp(c, 16), 0);
        kolom.addView(baris, blp);

        boolean panen = Sesi.bolehPanen(c);
        boolean ipd = Sesi.bolehIpd(c);
        if (panen) {
            baris.addView(kotak(R.drawable.ic_nav_panen, 0xFF22A663, 0xFF12612F, "Panen", "Input data TBS", 0xFF15803D, MainActivity.MODE_PANEN),
                    jarakKotak(c, ipd, true));
        }
        if (ipd) {
            baris.addView(kotak(R.drawable.ic_nav_ipd, 0xFFF5A524, 0xFFD97706, "IPD", "Inspeksi panen detail", 0xFFC2410C, MainActivity.MODE_IPD),
                    jarakKotak(c, panen, false));
        }
        if (!panen && !ipd) {
            TextView kosong = IpdUi.redup(c, "Akun ini belum diberi akses ke fitur mana pun. Hubungi admin untuk mengatur hak akses.", 15);
            kosong.setPadding(IpdUi.dp(c, 8), IpdUi.dp(c, 8), IpdUi.dp(c, 8), 0);
            baris.addView(kosong);
        }

        final float d = getResources().getDisplayMetrics().density;
        ViewCompat.setOnApplyWindowInsetsListener(gulir, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            kepala.atur(bars.top);
            // Ruang di bawah supaya isi terakhir bisa digulir di atas menu kaca.
            v.setPadding(bars.left, 0, bars.right, bars.bottom + (int) (112 * d));
            return insets;
        });
        return akar;
    }

    /**
     * Tanda air fAI26: logo besar dan tulisan fAI26 yang sangat samar di area kosong layar awal, supaya tidak terlihat hampa.
     * Tidak bisa disentuh dan tidak dibaca pembaca layar (hiasan). Muncul pelan saat layar dibuka.
     */
    private View tandaAir(android.content.Context c) {
        LinearLayout kolom = new LinearLayout(c);
        kolom.setOrientation(LinearLayout.VERTICAL);
        kolom.setGravity(Gravity.CENTER_HORIZONTAL);
        kolom.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        kolom.setLayoutParams(lp);
        kolom.setTranslationY(IpdUi.dp(c, 96));

        ImageView logo = new ImageView(c);
        logo.setImageResource(R.drawable.ic_logo);
        kolom.addView(logo, new LinearLayout.LayoutParams(IpdUi.dp(c, 210), IpdUi.dp(c, 210)));

        TextView nama = new TextView(c);
        nama.setText("fAI26");
        nama.setTextSize(40);
        nama.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(c, R.font.outfit_semibold));
        nama.setTextColor(0xFF12612F);
        nama.setGravity(Gravity.CENTER_HORIZONTAL);
        nama.setPadding(0, IpdUi.dp(c, 6), 0, 0);
        kolom.addView(nama);

        TextView ket = new TextView(c);
        ket.setText("Grading TBS kelapa sawit");
        ket.setTextSize(14);
        ket.setTextColor(0xFF6B7280);
        ket.setGravity(Gravity.CENTER_HORIZONTAL);
        kolom.addView(ket);

        kolom.setAlpha(0f);
        kolom.animate().alpha(0.11f).setDuration(1100).setStartDelay(150).start();
        return kolom;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (kepala != null) kepala.perbaruiStatus();
    }

    /** Jarak antar kotak: bila ada dua kotak, masing-masing menyisakan 6 dp di sisi yang berhadapan. */
    private static LinearLayout.LayoutParams jarakKotak(android.content.Context c, boolean adaPasangan, boolean kiri) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        int sisi = adaPasangan ? IpdUi.dp(c, 6) : 0;
        lp.setMargins(kiri ? 0 : sisi, 0, kiri ? sisi : 0, 0);
        return lp;
    }

    /** Satu kotak fitur: ikon di kotak membulat berwarna, judul, dan keterangan pendek. */
    private View kotak(int ikon, int warnaAtas, int warnaBawah, String judul, String keterangan, int warnaKet, String mode) {
        final android.content.Context c = requireContext();
        MaterialCardView kartu = new MaterialCardView(c);
        kartu.setMinimumHeight(IpdUi.dp(c, 104));
        kartu.setRadius(IpdUi.dp(c, 24));
        kartu.setCardElevation(IpdUi.dp(c, 3));
        kartu.setClickable(true);
        kartu.setFocusable(true);
        kartu.setOnClickListener(v -> ((MainActivity) requireActivity()).bukaMode(mode));

        LinearLayout baris = new LinearLayout(c);
        baris.setOrientation(LinearLayout.HORIZONTAL);
        baris.setGravity(Gravity.CENTER_VERTICAL);
        baris.setPadding(IpdUi.dp(c, 12), IpdUi.dp(c, 14), IpdUi.dp(c, 10), IpdUi.dp(c, 14));

        FrameLayout kotakIkon = new FrameLayout(c);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{warnaAtas, warnaBawah});
        g.setCornerRadius(IpdUi.dp(c, 16));
        kotakIkon.setBackground(g);
        ImageView gambar = new ImageView(c);
        gambar.setImageResource(ikon);
        gambar.setColorFilter(0xFFFFFFFF);
        kotakIkon.addView(gambar, new FrameLayout.LayoutParams(IpdUi.dp(c, 28), IpdUi.dp(c, 28), Gravity.CENTER));
        baris.addView(kotakIkon, new LinearLayout.LayoutParams(IpdUi.dp(c, 54), IpdUi.dp(c, 54)));

        LinearLayout teks = new LinearLayout(c);
        teks.setOrientation(LinearLayout.VERTICAL);
        TextView t = IpdUi.judul(c, judul, 17);
        teks.addView(t);
        TextView ket = IpdUi.teks(c, keterangan, 12, true, com.google.android.material.R.attr.colorOnSurface);
        ket.setTextColor(warnaKet);
        ket.setPadding(0, IpdUi.dp(c, 2), 0, 0);
        teks.addView(ket);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginStart(IpdUi.dp(c, 10));
        baris.addView(teks, tlp);
        kartu.addView(baris, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return kartu;
    }
}
