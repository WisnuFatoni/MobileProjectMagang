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
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.example.fai26.auth.Sesi;
import com.example.fai26.ipd.IpdUi;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

/**
 * Layar awal setelah login: satu kotak per fitur (Panen, IPD). Kotak yang tampil mengikuti hak akses akun (akses.panen,
 * akses.ipd) yang diatur admin di web, jadi siapa boleh membuka apa bisa diubah tanpa mengubah aplikasi.
 */
public class HomeFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final android.content.Context c = requireContext();
        ScrollView gulir = new ScrollView(c);
        gulir.setFillViewport(true);
        gulir.setClipToPadding(false);
        gulir.setBackgroundColor(MaterialColors.getColor(gulir, android.R.attr.colorBackground));

        LinearLayout kolom = new LinearLayout(c);
        kolom.setOrientation(LinearLayout.VERTICAL);
        gulir.addView(kolom, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Kepala hijau berisi sapaan.
        final LinearLayout kepala = new LinearLayout(c);
        kepala.setOrientation(LinearLayout.VERTICAL);
        kepala.setBackgroundColor(ContextCompat.getColor(c, R.color.header_start));
        final int padH = IpdUi.dp(c, 20);
        final int padAtas = IpdUi.dp(c, 28);
        kepala.setPadding(padH, padAtas, padH, IpdUi.dp(c, 28));
        String nama = Sesi.nama(c);
        TextView sapa = new TextView(c);
        sapa.setText("Halo, " + (nama.isEmpty() ? "pengguna" : nama));
        sapa.setTextSize(24);
        sapa.setTypeface(ResourcesCompat.getFont(c, R.font.outfit_semibold));
        sapa.setTextColor(ContextCompat.getColor(c, R.color.header_on));
        kepala.addView(sapa);
        TextView sub = new TextView(c);
        String peran = Sesi.peran(c);
        sub.setText(peran.isEmpty() ? "Pilih fitur yang ingin dibuka" : peran + " • pilih fitur yang ingin dibuka");
        sub.setTextSize(14);
        sub.setTextColor(ContextCompat.getColor(c, R.color.header_on_muted));
        sub.setPadding(0, IpdUi.dp(c, 4), 0, 0);
        kepala.addView(sub);
        kolom.addView(kepala, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout isi = new LinearLayout(c);
        isi.setOrientation(LinearLayout.VERTICAL);
        isi.setPadding(IpdUi.dp(c, 16), IpdUi.dp(c, 20), IpdUi.dp(c, 16), 0);
        kolom.addView(isi, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        boolean ada = false;
        if (Sesi.bolehPanen(c)) {
            isi.addView(kotak(R.drawable.ic_kartu_panen, 0xFF12612F, "Panen",
                    "Catat panen di TPH: scan QR, hitung tandan otomatis, kirim, riwayat, dan persetujuan krani.", MainActivity.MODE_PANEN));
            ada = true;
        }
        if (Sesi.bolehIpd(c)) {
            isi.addView(kotak(R.drawable.ic_kartu_ipd, 0xFFA16207, "IPD",
                    "Inspeksi Panen Detail: periksa pokok per pokok, catat temuan, dan lihat loses di ancak.", MainActivity.MODE_IPD));
            ada = true;
        }
        if (!ada) {
            TextView kosong = IpdUi.redup(c, "Akun ini belum diberi akses ke fitur mana pun. Hubungi admin untuk mengatur hak akses.", 15);
            kosong.setPadding(IpdUi.dp(c, 4), IpdUi.dp(c, 8), IpdUi.dp(c, 4), 0);
            isi.addView(kosong);
        }

        final float d = getResources().getDisplayMetrics().density;
        ViewCompat.setOnApplyWindowInsetsListener(gulir, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            kepala.setPadding(padH, padAtas + bars.top, padH, kepala.getPaddingBottom());
            // Ruang di bawah supaya isi terakhir bisa digulir di atas menu kaca.
            v.setPadding(bars.left, 0, bars.right, bars.bottom + (int) (112 * d));
            return insets;
        });
        return gulir;
    }

    private View kotak(int ikon, int warna, String judul, String keterangan, String mode) {
        final android.content.Context c = requireContext();
        MaterialCardView kartu = new MaterialCardView(c);
        kartu.setLayoutParams(IpdUi.lebarPenuh(c, 0, 14));
        kartu.setClickable(true);
        kartu.setFocusable(true);
        kartu.setOnClickListener(v -> ((MainActivity) requireActivity()).bukaMode(mode));

        LinearLayout baris = new LinearLayout(c);
        baris.setOrientation(LinearLayout.HORIZONTAL);
        baris.setGravity(Gravity.CENTER_VERTICAL);
        baris.setPadding(IpdUi.dp(c, 18), IpdUi.dp(c, 20), IpdUi.dp(c, 18), IpdUi.dp(c, 20));

        FrameLayout bulat = new FrameLayout(c);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(warna);
        bulat.setBackground(g);
        ImageView gambar = new ImageView(c);
        gambar.setImageResource(ikon);
        bulat.addView(gambar, new FrameLayout.LayoutParams(IpdUi.dp(c, 40), IpdUi.dp(c, 40), Gravity.CENTER));
        baris.addView(bulat, new LinearLayout.LayoutParams(IpdUi.dp(c, 72), IpdUi.dp(c, 72)));

        LinearLayout teks = new LinearLayout(c);
        teks.setOrientation(LinearLayout.VERTICAL);
        teks.addView(IpdUi.judul(c, judul, 22));
        TextView ket = IpdUi.redup(c, keterangan, 14);
        ket.setPadding(0, IpdUi.dp(c, 4), 0, 0);
        teks.addView(ket);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginStart(IpdUi.dp(c, 16));
        baris.addView(teks, tlp);

        kartu.addView(baris);
        return kartu;
    }
}
