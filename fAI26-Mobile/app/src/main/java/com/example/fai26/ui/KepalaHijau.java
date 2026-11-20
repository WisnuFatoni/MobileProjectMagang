package com.example.fai26.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.example.fai26.Fai26App;
import com.example.fai26.R;
import com.example.fai26.auth.Sesi;
import com.example.fai26.sync.PengaturanSync;
import com.example.fai26.sync.SyncRunner;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Kepala hijau berlengkung yang dipakai Home dan Beranda IPD (gaya sama dengan Beranda Panen): sapaan, nama, peran dan tanggal,
 * avatar, dan pil status koneksi dengan tombol Segarkan. Dibangun lewat kode supaya bisa dipakai di banyak layar.
 */
public final class KepalaHijau {

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private final Context konteks;
    private final LinearLayout akar;
    private final ImageView titik;
    private final TextView txtStatus;
    private final TextView txtSegarkan;
    private final int padAtas;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Runnable setelahSegarkan;

    private static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** @param bawahDp ruang di bawah kepala untuk kartu yang menimpanya. */
    public KepalaHijau(Context c, int bawahDp) {
        konteks = c;
        akar = new LinearLayout(c);
        akar.setOrientation(LinearLayout.VERTICAL);
        akar.setBackgroundResource(R.drawable.bg_header);
        padAtas = dp(c, 20);
        akar.setPadding(dp(c, 24), padAtas, dp(c, 20), dp(c, bawahDp));

        LinearLayout baris = new LinearLayout(c);
        baris.setOrientation(LinearLayout.HORIZONTAL);
        baris.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout teks = new LinearLayout(c);
        teks.setOrientation(LinearLayout.VERTICAL);
        TextView salam = new TextView(c);
        salam.setText(salam());
        salam.setTextSize(15);
        salam.setTextColor(ContextCompat.getColor(c, R.color.header_on_muted));
        teks.addView(salam);
        TextView nama = new TextView(c);
        nama.setText(Sesi.nama(c));
        nama.setTextSize(24);
        nama.setMaxLines(1);
        nama.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nama.setTypeface(ResourcesCompat.getFont(c, R.font.outfit_semibold));
        nama.setTextColor(ContextCompat.getColor(c, R.color.header_on));
        nama.setPadding(0, dp(c, 2), 0, 0);
        teks.addView(nama);
        TextView peran = new TextView(c);
        String tgl = new SimpleDateFormat("EEEE, d MMMM yyyy", new Locale("id", "ID")).format(new Date());
        peran.setText((Sesi.peran(c).isEmpty() ? "" : Sesi.peran(c) + " • ") + tgl);
        peran.setTextSize(13);
        peran.setTextColor(ContextCompat.getColor(c, R.color.header_on_muted));
        peran.setPadding(0, dp(c, 2), 0, 0);
        teks.addView(peran);
        baris.addView(teks, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView avatar = new TextView(c);
        avatar.setBackgroundResource(R.drawable.bg_avatar);
        avatar.setGravity(Gravity.CENTER);
        avatar.setText(inisial(Sesi.nama(c)));
        avatar.setTextSize(17);
        avatar.setTypeface(ResourcesCompat.getFont(c, R.font.outfit_semibold));
        avatar.setTextColor(ContextCompat.getColor(c, R.color.brand_primary));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(c, 48), dp(c, 48));
        alp.setMarginStart(dp(c, 12));
        baris.addView(avatar, alp);
        akar.addView(baris);

        LinearLayout pil = new LinearLayout(c);
        pil.setOrientation(LinearLayout.HORIZONTAL);
        pil.setGravity(Gravity.CENTER_VERTICAL);
        pil.setBackgroundResource(R.drawable.bg_field_header);
        pil.setPadding(dp(c, 18), 0, dp(c, 4), 0);
        titik = new ImageView(c);
        titik.setImageResource(R.drawable.ic_wifi_online);
        pil.addView(titik, new LinearLayout.LayoutParams(dp(c, 10), dp(c, 10)));
        txtStatus = new TextView(c);
        txtStatus.setTextSize(14);
        txtStatus.setMaxLines(1);
        txtStatus.setEllipsize(android.text.TextUtils.TruncateAt.END);
        txtStatus.setTextColor(ContextCompat.getColor(c, R.color.header_on));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginStart(dp(c, 10));
        pil.addView(txtStatus, tlp);
        LinearLayout segarkan = new LinearLayout(c);
        segarkan.setOrientation(LinearLayout.HORIZONTAL);
        segarkan.setGravity(Gravity.CENTER_VERTICAL);
        segarkan.setPadding(dp(c, 10), 0, dp(c, 14), 0);
        ImageView ik = new ImageView(c);
        ik.setImageResource(R.drawable.ic_segarkan);
        ik.setColorFilter(ContextCompat.getColor(c, R.color.header_on));
        segarkan.addView(ik, new LinearLayout.LayoutParams(dp(c, 18), dp(c, 18)));
        txtSegarkan = new TextView(c);
        txtSegarkan.setText("Segarkan");
        txtSegarkan.setTextSize(13);
        txtSegarkan.setTypeface(ResourcesCompat.getFont(c, R.font.outfit_medium));
        txtSegarkan.setTextColor(ContextCompat.getColor(c, R.color.header_on));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.setMarginStart(dp(c, 6));
        segarkan.addView(txtSegarkan, slp);
        segarkan.setOnClickListener(v -> segarkan());
        pil.addView(segarkan, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(c, 48)));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 52));
        plp.topMargin = dp(c, 20);
        akar.addView(pil, plp);
        perbaruiStatus();
    }

    public View view() {
        return akar;
    }

    /** Dipanggil setelah sinkron selesai (untuk memuat ulang isi layar). */
    public void setelahSegarkan(Runnable r) {
        setelahSegarkan = r;
    }

    /** Menyesuaikan ruang atas dengan tinggi bilah status. */
    public void atur(int atasPx) {
        akar.setPadding(akar.getPaddingLeft(), padAtas + atasPx, akar.getPaddingRight(), akar.getPaddingBottom());
    }

    public void perbaruiStatus() {
        if (new PengaturanSync(konteks).adaToken()) {
            titik.setImageResource(R.drawable.ic_wifi_online);
            txtStatus.setText("Online • Terhubung ke Server");
        } else {
            titik.setImageResource(R.drawable.ic_wifi_offline);
            txtStatus.setText("Mode Offline • Tersimpan di HP");
        }
    }

    private void segarkan() {
        final Context app = konteks.getApplicationContext();
        txtSegarkan.setText("Menyinkronkan...");
        IO.execute(() -> {
            String info;
            try {
                Fai26App.tungguSiap();
                info = SyncRunner.jalankanSinkron(app, true).teks();
            } catch (Exception e) {
                info = "Gagal menyegarkan: " + e.getMessage();
            }
            final String pesan = info;
            main.post(() -> {
                txtSegarkan.setText("Segarkan");
                perbaruiStatus();
                Toast.makeText(app, pesan, Toast.LENGTH_SHORT).show();
                if (setelahSegarkan != null) setelahSegarkan.run();
            });
        });
    }

    private static String salam() {
        int jam = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (jam < 11) return "Selamat pagi";
        if (jam < 15) return "Selamat siang";
        if (jam < 18) return "Selamat sore";
        return "Selamat malam";
    }

    private static String inisial(String nama) {
        if (nama == null) return "";
        StringBuilder b = new StringBuilder();
        for (String kata : nama.trim().split("\\s+")) {
            if (!kata.isEmpty() && b.length() < 2) b.append(Character.toUpperCase(kata.charAt(0)));
        }
        return b.toString();
    }
}
