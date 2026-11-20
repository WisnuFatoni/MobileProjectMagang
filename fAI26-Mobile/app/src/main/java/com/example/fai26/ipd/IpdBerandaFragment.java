package com.example.fai26.ipd;

import android.content.Context;
import android.content.Intent;
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

import com.example.fai26.R;
import com.example.fai26.auth.Sesi;
import com.example.fai26.db.AppDatabase;
import com.example.fai26.db.dao.IpdHasil;
import com.example.fai26.sync.PerubahanMaster;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Layar utama fitur IPD: tombol besar "Mulai inspeksi baru", ringkasan, dan inspeksi terakhir. */
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

    private TextView txtRingkas;
    private TextView txtKosong;
    private LinearLayout daftar;

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
        kolom.setPadding(IpdUi.dp(c, 16), IpdUi.dp(c, 8), IpdUi.dp(c, 16), 0);
        gulir.addView(kolom, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView judul = IpdUi.judul(c, "IPD • Inspeksi Panen Detail", 18);
        judul.setGravity(Gravity.CENTER);
        judul.setMinHeight(IpdUi.dp(c, 48));
        kolom.addView(judul, IpdUi.lebarPenuh(c, 0, 8));

        // Kartu hijau: ringkasan.
        MaterialCardView hijau = new MaterialCardView(c);
        hijau.setCardBackgroundColor(ContextCompat.getColor(c, R.color.header_start));
        hijau.setLayoutParams(IpdUi.lebarPenuh(c, 0, 14));
        LinearLayout hl = new LinearLayout(c);
        hl.setOrientation(LinearLayout.VERTICAL);
        hl.setPadding(IpdUi.dp(c, 20), IpdUi.dp(c, 18), IpdUi.dp(c, 20), IpdUi.dp(c, 18));
        TextView h1 = new TextView(c);
        h1.setText("Loses di ancak, pokok per pokok");
        h1.setTextSize(14);
        h1.setTextColor(ContextCompat.getColor(c, R.color.header_on_muted));
        hl.addView(h1);
        txtRingkas = new TextView(c);
        txtRingkas.setTextSize(20);
        txtRingkas.setTextColor(ContextCompat.getColor(c, R.color.header_on));
        txtRingkas.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(c, R.font.outfit_semibold));
        txtRingkas.setPadding(0, IpdUi.dp(c, 4), 0, 0);
        hl.addView(txtRingkas);
        hijau.addView(hl);
        kolom.addView(hijau);

        MaterialButton mulai = IpdUi.tombol(c, "Mulai inspeksi baru", true);
        mulai.setMinimumHeight(IpdUi.dp(c, 64));
        mulai.setTextSize(18);
        mulai.setEnabled(Sesi.punya(c, "ipd.buat"));
        mulai.setOnClickListener(v -> startActivity(new Intent(c, IpdInspeksiActivity.class)));
        kolom.addView(mulai, IpdUi.lebarPenuh(c, 0, 16));

        TextView sub = IpdUi.judul(c, "Inspeksi terakhir", 16);
        kolom.addView(sub, IpdUi.lebarPenuh(c, 0, 8));
        txtKosong = IpdUi.redup(c, "Belum ada inspeksi.", 14);
        kolom.addView(txtKosong, IpdUi.lebarPenuh(c, 0, 8));
        daftar = new LinearLayout(c);
        daftar.setOrientation(LinearLayout.VERTICAL);
        kolom.addView(daftar, IpdUi.lebarPenuh(c, 0, 0));

        final float d = getResources().getDisplayMetrics().density;
        final int atas = kolom.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(gulir, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            kolom.setPadding(kolom.getPaddingLeft(), atas + bars.top, kolom.getPaddingRight(), 0);
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
        if (!isHidden()) muat();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) muat();
    }

    private void muat() {
        if (!isAdded()) return;
        final Context app = requireContext().getApplicationContext();
        io.execute(() -> {
            List<IpdHasil.Riwayat> data = AppDatabase.getInstance(app).ipdDao().riwayat(Sesi.userId(app), 100);
            main.post(() -> {
                if (!isAdded()) return;
                tampilkan(data);
            });
        });
    }

    private void tampilkan(List<IpdHasil.Riwayat> data) {
        final Context c = requireContext();
        int draf = 0;
        int menunggu = 0;
        for (IpdHasil.Riwayat r : data) {
            if ("DRAFT".equals(r.status)) draf++;
            if ("MENUNGGU_SETUJU".equals(r.status)) menunggu++;
        }
        txtRingkas.setText(draf + " draf • " + menunggu + " menunggu asisten");
        daftar.removeAllViews();
        int n = 0;
        for (IpdHasil.Riwayat r : data) {
            if (n++ >= 5) break;
            daftar.addView(IpdKartu.buat(c, r, null));
        }
        txtKosong.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
