package com.example.fai26.ipd;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.example.fai26.MainActivity;
import com.example.fai26.R;
import com.example.fai26.auth.Sesi;
import com.example.fai26.db.AppDatabase;
import com.example.fai26.db.dao.IpdDao;
import com.example.fai26.db.dao.IpdHasil;
import com.example.fai26.sync.PerubahanMaster;
import com.example.fai26.ui.KepalaHijau;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Beranda fitur IPD (gaya sama dengan Beranda Panen): kepala hijau, ringkasan loses 7 hari terakhir, tombol mulai inspeksi,
 * peringatan (draf dan yang ditolak), loses per kode, dan inspeksi terakhir. Angka dihitung dari inspeksi di HP ini.
 */
public class IpdBerandaFragment extends Fragment {

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final PerubahanMaster.Pendengar pendengar = new PerubahanMaster.Pendengar() {
        @Override
        public void masterBerubah() {
            main.post(IpdBerandaFragment.this::muat);
        }

        @Override
        public void hakBerubah() {
        }
    };

    private KepalaHijau kepala;
    private LinearLayout isi;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final Context c = requireContext();
        ScrollView gulir = new ScrollView(c);
        gulir.setFillViewport(true);
        gulir.setClipToPadding(false);
        gulir.setBackgroundColor(MaterialColors.getColor(gulir, android.R.attr.colorBackground));
        LinearLayout kolom = new LinearLayout(c);
        kolom.setOrientation(LinearLayout.VERTICAL);
        gulir.addView(kolom, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        kepala = new KepalaHijau(c, 76);
        kepala.setelahSegarkan(this::muat);
        kolom.addView(kepala.view(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        isi = new LinearLayout(c);
        isi.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.setMargins(IpdUi.dp(c, 16), -IpdUi.dp(c, 48), IpdUi.dp(c, 16), 0);
        kolom.addView(isi, ilp);

        final float d = getResources().getDisplayMetrics().density;
        ViewCompat.setOnApplyWindowInsetsListener(gulir, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            kepala.atur(bars.top);
            v.setPadding(bars.left, 0, bars.right, bars.bottom + (int) (112 * d));
            return insets;
        });
        return gulir;
    }

    @Override
    public void onStart() {
        super.onStart();
        PerubahanMaster.daftar(pendengar);
    }

    @Override
    public void onStop() {
        super.onStop();
        PerubahanMaster.lepas(pendengar);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (kepala != null) kepala.perbaruiStatus();
        if (!isHidden()) muat();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            if (kepala != null) kepala.perbaruiStatus();
            muat();
        }
    }

    private static final class Data {
        IpdHasil.RekapPekan rekap;
        List<IpdHasil.KodePekan> perKode;
        List<IpdHasil.Riwayat> riwayat;
    }

    private void muat() {
        if (!isAdded()) return;
        final Context app = requireContext().getApplicationContext();
        io.execute(() -> {
            IpdDao dao = AppDatabase.getInstance(app).ipdDao();
            long user = Sesi.userId(app);
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, -6);
            String dari = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
            Data dt = new Data();
            dt.rekap = dao.rekapSejak(user, dari);
            dt.perKode = dao.perKodeSejak(user, dari);
            dt.riwayat = dao.riwayat(user, 100);
            main.post(() -> {
                if (isAdded()) tampilkan(dt);
            });
        });
    }

    private void tampilkan(Data dt) {
        final Context c = requireContext();
        isi.removeAllViews();

        // Kartu ringkasan 7 hari (menimpa kepala).
        LinearLayout[] a = new LinearLayout[1];
        MaterialCardView hero = IpdUi.kartu(c, a);
        hero.setRadius(IpdUi.dp(c, 24));
        hero.setCardElevation(IpdUi.dp(c, 3));
        LinearLayout l = a[0];
        l.setPadding(IpdUi.dp(c, 20), IpdUi.dp(c, 18), IpdUi.dp(c, 20), IpdUi.dp(c, 18));
        double persenLoses = dt.rekap.pokok > 0 ? Math.round(dt.rekap.pokokLoses * 1000.0 / dt.rekap.pokok) / 10.0 : 0;
        double persenLain = dt.rekap.pokok > 0 ? Math.round(dt.rekap.pokokLain * 1000.0 / dt.rekap.pokok) / 10.0 : 0;
        l.addView(IpdUi.redup(c, "Ringkasan 7 hari terakhir", 14));
        LinearLayout besar = new LinearLayout(c);
        besar.setOrientation(LinearLayout.HORIZONTAL);
        besar.setGravity(Gravity.BOTTOM);
        TextView angka = IpdUi.teks(c, IpdUi.persen(persenLoses), 40, true, com.google.android.material.R.attr.colorOnSurface);
        besar.addView(angka);
        TextView ket = IpdUi.redup(c, "  loses pokok", 15);
        ket.setPadding(0, 0, 0, IpdUi.dp(c, 8));
        besar.addView(ket);
        l.addView(besar);
        l.addView(IpdUi.redup(c, dt.rekap.pokok + " pokok diperiksa • temuan lain " + IpdUi.persen(persenLain) + " (tidak dihitung loses)", 13));
        View garis = new View(c);
        garis.setBackgroundColor(MaterialColors.getColor(garis, com.google.android.material.R.attr.colorOutlineVariant));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, IpdUi.dp(c, 1));
        glp.setMargins(0, IpdUi.dp(c, 14), 0, IpdUi.dp(c, 12));
        l.addView(garis, glp);
        long menunggu = 0;
        long ditolak = 0;
        long draf = 0;
        for (IpdHasil.Riwayat r : dt.riwayat) {
            if ("MENUNGGU_SETUJU".equals(r.status)) menunggu++;
            if ("DITOLAK".equals(r.status)) ditolak++;
            if ("DRAFT".equals(r.status)) draf++;
        }
        LinearLayout tiga = new LinearLayout(c);
        tiga.setOrientation(LinearLayout.HORIZONTAL);
        tiga.addView(statistik(c, "Inspeksi", String.valueOf(dt.rekap.inspeksi)), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tiga.addView(statistik(c, "Menunggu asisten", String.valueOf(menunggu)), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tiga.addView(statistik(c, "Ditolak", String.valueOf(ditolak)), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        l.addView(tiga);
        isi.addView(hero);

        // Mulai inspeksi.
        TextView sub = IpdUi.judul(c, "Mulai inspeksi", 18);
        sub.setPadding(IpdUi.dp(c, 4), IpdUi.dp(c, 8), 0, IpdUi.dp(c, 8));
        isi.addView(sub);
        LinearLayout[] b = new LinearLayout[1];
        MaterialCardView mulaiKartu = IpdUi.kartu(c, b);
        b[0].addView(IpdUi.redup(c, "Periksa pokok per pokok, dua baris sekali jalan. Catat loses dan temuan, lalu kirim ke asisten.", 14));
        MaterialButton mulai = IpdUi.tombol(c, "Mulai inspeksi baru", true);
        mulai.setMinimumHeight(IpdUi.dp(c, 60));
        mulai.setTextSize(17);
        mulai.setEnabled(Sesi.punya(c, "ipd.buat"));
        mulai.setOnClickListener(v -> startActivity(new Intent(c, IpdInspeksiActivity.class)));
        b[0].addView(mulai, IpdUi.lebarPenuh(c, 12, 0));
        isi.addView(mulaiKartu);

        // Peringatan.
        if (ditolak > 0 || draf > 0) {
            StringBuilder s = new StringBuilder();
            if (ditolak > 0) s.append(ditolak).append(" inspeksi ditolak, perlu diperbaiki");
            if (draf > 0) s.append(s.length() > 0 ? " • " : "").append(draf).append(" draf belum dikirim");
            LinearLayout[] w = new LinearLayout[1];
            MaterialCardView peringatan = IpdUi.kartu(c, w);
            peringatan.setClickable(true);
            peringatan.setOnClickListener(v -> ((MainActivity) requireActivity()).pilihLayar("ipd_riwayat"));
            LinearLayout baris = new LinearLayout(c);
            baris.setOrientation(LinearLayout.HORIZONTAL);
            baris.setGravity(Gravity.CENTER_VERTICAL);
            TextView tanda = IpdUi.teks(c, "!", 20, true, com.google.android.material.R.attr.colorOnSurface);
            tanda.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(0xFFFEF3C7);
            tanda.setBackground(g);
            baris.addView(tanda, new LinearLayout.LayoutParams(IpdUi.dp(c, 44), IpdUi.dp(c, 44)));
            TextView t = IpdUi.judul(c, s, 14);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tlp.setMarginStart(IpdUi.dp(c, 12));
            baris.addView(t, tlp);
            baris.addView(IpdUi.teks(c, "Buka ›", 14, true, androidx.appcompat.R.attr.colorPrimary));
            w[0].addView(baris);
            LinearLayout.LayoutParams plp = IpdUi.lebarPenuh(c, 12, 0);
            peringatan.setLayoutParams(plp);
            isi.addView(peringatan);
        }

        // Loses per kode (7 hari).
        TextView sub2 = IpdUi.judul(c, "Loses per kode (7 hari)", 18);
        sub2.setPadding(IpdUi.dp(c, 4), IpdUi.dp(c, 16), 0, IpdUi.dp(c, 8));
        isi.addView(sub2);
        LinearLayout[] k = new LinearLayout[1];
        MaterialCardView kodeKartu = IpdUi.kartu(c, k);
        boolean adaLoses = false;
        double maks = 1;
        for (IpdHasil.KodePekan x : dt.perKode) if (!"PELEPAH".equals(x.kelompok)) maks = Math.max(maks, dt.rekap.pokok > 0 ? x.pokok * 100.0 / dt.rekap.pokok : 0);
        for (IpdHasil.KodePekan x : dt.perKode) {
            if ("PELEPAH".equals(x.kelompok)) continue;
            adaLoses = true;
            double p = dt.rekap.pokok > 0 ? Math.round(x.pokok * 1000.0 / dt.rekap.pokok) / 10.0 : 0;
            k[0].addView(barisKode(c, x.kode + "  " + x.nama, p, maks, x.pokok + " pokok"));
        }
        if (!adaLoses) k[0].addView(IpdUi.redup(c, dt.rekap.pokok == 0 ? "Belum ada inspeksi dalam 7 hari terakhir." : "Tidak ada loses dalam 7 hari terakhir.", 14));
        boolean adaLain = false;
        for (IpdHasil.KodePekan x : dt.perKode) {
            if (!"PELEPAH".equals(x.kelompok)) continue;
            if (!adaLain) {
                TextView lain = IpdUi.judul(c, "Temuan lain (tidak memengaruhi hasil)", 13);
                lain.setPadding(0, IpdUi.dp(c, 10), 0, IpdUi.dp(c, 2));
                k[0].addView(lain);
                adaLain = true;
            }
            k[0].addView(IpdUi.redup(c, x.kode + "  " + x.nama + ": " + x.pokok + " pokok", 13));
        }
        isi.addView(kodeKartu);

        // Inspeksi terakhir.
        TextView sub3 = IpdUi.judul(c, "Inspeksi terakhir", 18);
        sub3.setPadding(IpdUi.dp(c, 4), IpdUi.dp(c, 8), 0, IpdUi.dp(c, 8));
        isi.addView(sub3);
        int n = 0;
        for (IpdHasil.Riwayat r : dt.riwayat) {
            if (n++ >= 5) break;
            isi.addView(IpdKartu.buat(c, r, null));
        }
        if (dt.riwayat.isEmpty()) isi.addView(IpdUi.redup(c, "Belum ada inspeksi.", 14));
    }

    private View statistik(Context c, String label, String nilai) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.addView(IpdUi.redup(c, label, 13));
        TextView v = IpdUi.judul(c, nilai, 24);
        l.addView(v);
        return l;
    }

    /** Satu baris batang loses: label, batang sebanding persen, dan angkanya. */
    private View barisKode(Context c, String label, double persen, double maks, String pokok) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(0, IpdUi.dp(c, 4), 0, IpdUi.dp(c, 4));
        LinearLayout atas = new LinearLayout(c);
        atas.setOrientation(LinearLayout.HORIZONTAL);
        TextView t = IpdUi.judul(c, label, 14);
        atas.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        atas.addView(IpdUi.teks(c, IpdUi.persen(persen) + " • " + pokok, 13, false, com.google.android.material.R.attr.colorOnSurfaceVariant));
        l.addView(atas);
        LinearLayout batang = new LinearLayout(c);
        batang.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable latar = new GradientDrawable();
        latar.setColor(0x14000000);
        latar.setCornerRadius(IpdUi.dp(c, 5));
        batang.setBackground(latar);
        View isiBatang = new View(c);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(IpdUi.dp(c, 5));
        g.setColor(persen >= 30 ? 0xFFB91C1C : persen >= 15 ? 0xFFD97706 : ContextCompat.getColor(c, R.color.brand_primary));
        isiBatang.setBackground(g);
        float bobot = (float) Math.max(persen / maks, 0.03);
        batang.addView(isiBatang, new LinearLayout.LayoutParams(0, IpdUi.dp(c, 10), bobot));
        batang.addView(new View(c), new LinearLayout.LayoutParams(0, IpdUi.dp(c, 10), 1f - bobot));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, IpdUi.dp(c, 10));
        blp.topMargin = IpdUi.dp(c, 4);
        l.addView(batang, blp);
        return l;
    }
}
