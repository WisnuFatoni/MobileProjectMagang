package com.example.fai26.ipd;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.example.fai26.Fai26App;
import com.example.fai26.auth.Sesi;
import com.example.fai26.db.AppDatabase;
import com.example.fai26.db.dao.IpdHasil;
import com.example.fai26.sync.PerubahanMaster;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Semua inspeksi IPD yang dibuat di HP ini: draf, menunggu asisten, disetujui, ditolak. Selama belum disetujui asisten
 * inspeksi boleh diubah dan dikirim ulang; yang sudah disetujui terkunci.
 */
public class IpdRiwayatFragment extends Fragment {

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final PerubahanMaster.Pendengar pendengar = new PerubahanMaster.Pendengar() {
        @Override
        public void masterBerubah() {
            main.post(IpdRiwayatFragment.this::muat);
        }

        @Override
        public void hakBerubah() {
        }
    };

    private LinearLayout daftar;
    private TextView txtJumlah;
    private TextView txtKosong;

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

        TextView judul = IpdUi.judul(c, "Riwayat IPD", 18);
        judul.setGravity(android.view.Gravity.CENTER);
        judul.setMinHeight(IpdUi.dp(c, 48));
        kolom.addView(judul, IpdUi.lebarPenuh(c, 0, 4));
        txtJumlah = IpdUi.redup(c, "", 14);
        txtJumlah.setGravity(android.view.Gravity.CENTER);
        kolom.addView(txtJumlah, IpdUi.lebarPenuh(c, 0, 12));
        txtKosong = IpdUi.redup(c, "Belum ada inspeksi. Buka tab Mulai untuk membuat inspeksi pertama.", 15);
        txtKosong.setVisibility(View.GONE);
        kolom.addView(txtKosong, IpdUi.lebarPenuh(c, 8, 8));
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
            List<IpdHasil.Riwayat> data = AppDatabase.getInstance(app).ipdDao().riwayat(Sesi.userId(app), 200);
            main.post(() -> {
                if (!isAdded()) return;
                tampilkan(data);
            });
        });
    }

    private void tampilkan(List<IpdHasil.Riwayat> data) {
        final Context c = requireContext();
        daftar.removeAllViews();
        IpdKartu.Aksi aksi = new IpdKartu.Aksi() {
            @Override
            public void edit(IpdHasil.Riwayat r) {
                startActivity(new Intent(c, IpdInspeksiActivity.class).putExtra(IpdInspeksiActivity.EXTRA_IPD_ID, r.ipdId));
            }

            @Override
            public void hapusDraf(IpdHasil.Riwayat r) {
                new MaterialAlertDialogBuilder(c).setTitle("Hapus draf?")
                        .setMessage("Draf inspeksi " + r.pemanen + " (" + IpdUi.tanggal(r.tanggalInspeksi) + ") dan fotonya akan dihapus dari HP.")
                        .setNegativeButton("Batal", null)
                        .setPositiveButton("Hapus", (d, w) -> io.execute(() -> {
                            Fai26App.ipdRepository(c).hapusDraf(r.ipdId);
                            main.post(IpdRiwayatFragment.this::muat);
                        })).show();
            }
        };
        int menunggu = 0;
        int disetujui = 0;
        for (IpdHasil.Riwayat r : data) {
            daftar.addView(IpdKartu.buat(c, r, aksi));
            if ("MENUNGGU_SETUJU".equals(r.status)) menunggu++;
            if ("DISETUJUI".equals(r.status)) disetujui++;
        }
        txtJumlah.setText(data.isEmpty() ? "" : data.size() + " inspeksi • " + menunggu + " menunggu asisten • " + disetujui + " disetujui");
        txtKosong.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
