package com.example.fai26.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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
 * Kepala hijau berlengkung yang dipakai Home dan Beranda IPD: sapaan, nama, peran dan tanggal, avatar, dan pil status koneksi dengan
 * tombol Segarkan. Tampilannya dari layout XML yang SAMA dengan Beranda Panen ({@code view_kepala_hijau.xml}), jadi ukuran dan jarak
 * hurufnya identik di semua layar dan tidak terlihat "melompat" saat pindah dari Home ke Panen.
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
        akar = (LinearLayout) LayoutInflater.from(c).inflate(R.layout.view_kepala_hijau, null, false);
        padAtas = akar.getPaddingTop();
        akar.setPadding(akar.getPaddingLeft(), padAtas, akar.getPaddingRight(), dp(c, bawahDp));

        ((TextView) akar.findViewById(R.id.txtSalam)).setText(salam());
        ((TextView) akar.findViewById(R.id.txtNama)).setText(Sesi.nama(c));
        String tgl = new SimpleDateFormat("EEEE, d MMMM yyyy", new Locale("id", "ID")).format(new Date());
        ((TextView) akar.findViewById(R.id.txtPeranTanggal)).setText((Sesi.peran(c).isEmpty() ? "" : Sesi.peran(c) + " • ") + tgl);
        ((TextView) akar.findViewById(R.id.txtAvatar)).setText(inisial(Sesi.nama(c)));

        titik = akar.findViewById(R.id.dotStatus);
        txtStatus = akar.findViewById(R.id.txtStatusKoneksi);
        txtSegarkan = akar.findViewById(R.id.txtSegarkanHeader);
        akar.findViewById(R.id.pillStatusKoneksi).setOnClickListener(v -> segarkan());
        akar.findViewById(R.id.btnSegarkanHeader).setOnClickListener(v -> segarkan());
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
