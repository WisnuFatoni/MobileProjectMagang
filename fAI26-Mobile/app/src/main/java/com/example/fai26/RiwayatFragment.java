package com.example.fai26;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.example.fai26.auth.Sesi;
import com.example.fai26.db.AppDatabase;
import com.example.fai26.db.dao.Hasil;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Semua catatan panen di HP. Krani hanya melihat miliknya, mandor dan asisten melihat semua. */
public class RiwayatFragment extends Fragment {

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private LinearLayout daftar;
    private TextView txtJumlah;
    private TextView txtKosong;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View raiz = inflater.inflate(R.layout.fragment_riwayat, container, false);
        daftar = raiz.findViewById(R.id.daftar);
        txtJumlah = raiz.findViewById(R.id.txtJumlah);
        txtKosong = raiz.findViewById(R.id.txtKosong);

        View header = raiz.findViewById(R.id.header);
        final int paddingAtas = header.getPaddingTop();
        final float d = getResources().getDisplayMetrics().density;
        ViewCompat.setOnApplyWindowInsetsListener(raiz, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            header.setPadding(header.getPaddingLeft(), paddingAtas + bars.top,
                    header.getPaddingRight(), header.getPaddingBottom());
            // Ruang di bawah supaya isi terakhir bisa digulir di atas menu kaca.
            v.setPadding(bars.left, 0, bars.right, bars.bottom + (int) (112 * d));
            return insets;
        });
        return raiz;
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
        final long pemilik = Sesi.filterPemilik(requireContext());
        final android.content.Context app = requireContext().getApplicationContext();
        io.execute(() -> {
            try {
                Fai26App.tungguSiap();
                List<Hasil.RiwayatItem> hasil = AppDatabase.getInstance(app).panenDao().riwayat(pemilik, 200);
                main.post(() -> {
                    if (isAdded()) tampilkan(hasil);
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (!isAdded()) return;
                    txtKosong.setText("Gagal membaca data: " + e.getMessage());
                    txtKosong.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void tampilkan(List<Hasil.RiwayatItem> hasil) {
        daftar.removeAllViews();
        for (Hasil.RiwayatItem r : hasil) daftar.addView(CatatanRowFactory.buat(requireContext(), r));
        txtJumlah.setText(hasil.isEmpty() ? "Belum ada catatan" : hasil.size() + " catatan");
        txtKosong.setText("Belum ada catatan.");
        txtKosong.setVisibility(hasil.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }
}
