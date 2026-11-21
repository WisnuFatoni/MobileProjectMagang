package com.example.fai26;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.fai26.auth.FotoProfilLokal;
import com.example.fai26.auth.Sesi;
import com.example.fai26.ipd.IpdUi;
import com.example.fai26.ui.PemotongBulatView;
import com.google.android.material.button.MaterialButton;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pemotong foto profil: setelah memilih dari kamera atau galeri, foto tampil dengan jendela lingkaran. Geser dengan satu jari, zoom
 * dengan dua jari atau penggeser, putar 90 derajat bila miring, lalu Simpan. Foto di dalam lingkaran itulah yang menjadi foto profil.
 * Hasilnya disimpan di HP (512 px) dan dikirim ke server lewat sinkronisasi.
 */
public class FotoProfilCropActivity extends AppCompatActivity {

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private PemotongBulatView pemotong;
    private SeekBar penggeser;
    private MaterialButton btnSimpan;
    private MaterialButton btnPutar;
    private TextView txtStatus;
    private boolean sibuk = true;
    private boolean sinkronPenggeser = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().getDecorView().setBackgroundColor(Color.BLACK); // area bilah status ikut hitam, bukan warna jendela tema
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(false);

        final android.content.Context c = this;
        LinearLayout akar = new LinearLayout(c);
        akar.setOrientation(LinearLayout.VERTICAL);
        akar.setBackgroundColor(Color.BLACK);

        TextView judul = new TextView(c);
        judul.setText("Atur foto profil");
        judul.setTextColor(Color.WHITE);
        judul.setTextSize(20);
        judul.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(c, R.font.outfit_semibold));
        judul.setPadding(IpdUi.dp(c, 20), IpdUi.dp(c, 16), IpdUi.dp(c, 20), IpdUi.dp(c, 2));
        akar.addView(judul);
        txtStatus = new TextView(c);
        txtStatus.setText("Menyiapkan foto…");
        txtStatus.setTextColor(0xFFB8C4BC);
        txtStatus.setTextSize(13);
        txtStatus.setPadding(IpdUi.dp(c, 20), 0, IpdUi.dp(c, 20), IpdUi.dp(c, 8));
        akar.addView(txtStatus);

        pemotong = new PemotongBulatView(c);
        pemotong.setContentDescription("Area pemotong foto profil: geser dan zoom foto");
        akar.addView(pemotong, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Penggeser zoom (cadangan bila pinch sulit) dan tombol putar.
        LinearLayout barisZoom = new LinearLayout(c);
        barisZoom.setOrientation(LinearLayout.HORIZONTAL);
        barisZoom.setGravity(Gravity.CENTER_VERTICAL);
        barisZoom.setPadding(IpdUi.dp(c, 20), IpdUi.dp(c, 8), IpdUi.dp(c, 12), 0);
        TextView kecil = new TextView(c);
        kecil.setText("−");
        kecil.setTextColor(Color.WHITE);
        kecil.setTextSize(22);
        barisZoom.addView(kecil);
        penggeser = new SeekBar(c);
        penggeser.setMax(1000);
        penggeser.setContentDescription("Zoom foto");
        barisZoom.addView(penggeser, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView besar = new TextView(c);
        besar.setText("+");
        besar.setTextColor(Color.WHITE);
        besar.setTextSize(22);
        barisZoom.addView(besar);
        akar.addView(barisZoom);

        LinearLayout tombol = new LinearLayout(c);
        tombol.setOrientation(LinearLayout.HORIZONTAL);
        tombol.setPadding(IpdUi.dp(c, 16), IpdUi.dp(c, 10), IpdUi.dp(c, 16), IpdUi.dp(c, 14));
        MaterialButton batal = IpdUi.tombol(c, "Batal", false);
        btnPutar = IpdUi.tombol(c, "Putar", false);
        btnSimpan = IpdUi.tombol(c, "Simpan", true);
        for (MaterialButton b : new MaterialButton[]{batal, btnPutar}) b.setTextColor(Color.WHITE);
        for (MaterialButton b : new MaterialButton[]{batal, btnPutar, btnSimpan}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(IpdUi.dp(c, 4), 0, IpdUi.dp(c, 4), 0);
            tombol.addView(b, lp);
        }
        akar.addView(tombol);
        setContentView(akar);

        final int padAtas = akar.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(akar, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, padAtas + bars.top, bars.right, bars.bottom);
            return insets;
        });

        pemotong.setPendengar(posisi -> {
            sinkronPenggeser = true;
            penggeser.setProgress(Math.round(posisi * penggeser.getMax()));
            sinkronPenggeser = false;
        });
        penggeser.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean dariPengguna) {
                if (dariPengguna && !sinkronPenggeser) pemotong.atur(p / (float) s.getMax());
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        batal.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        btnPutar.setOnClickListener(v -> {
            if (!sibuk) pemotong.putar();
        });
        btnSimpan.setOnClickListener(v -> simpan());
        atur(false);

        final Uri uri = getIntent().getData();
        if (uri == null) {
            Toast.makeText(this, "Foto tidak ditemukan", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        io.execute(() -> {
            try {
                // Resolusi cukup untuk dipotong halus (sisi pendek minimal 1024), tidak berat di memori.
                final Bitmap b = FotoProfilLokal.decodeTegak(this, uri, 1024);
                main.post(() -> {
                    if (isFinishing()) return;
                    pemotong.setGambar(b);
                    txtStatus.setText("Geser dan zoom sampai pas di dalam lingkaran");
                    atur(true);
                });
            } catch (Exception e) {
                main.post(() -> {
                    Toast.makeText(this, "Foto tidak bisa dibuka: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    setResult(RESULT_CANCELED);
                    finish();
                });
            }
        });
    }

    private void atur(boolean boleh) {
        sibuk = !boleh;
        btnSimpan.setEnabled(boleh);
        btnPutar.setEnabled(boleh);
        penggeser.setEnabled(boleh);
    }

    private void simpan() {
        if (sibuk) return;
        atur(false);
        txtStatus.setText("Menyimpan…");
        final android.content.Context app = getApplicationContext();
        final long uid = Sesi.userId(app);
        final Bitmap hasil = pemotong.hasil(FotoProfilLokal.SISI);
        io.execute(() -> {
            String galat = null;
            try {
                FotoProfilLokal.simpanBitmap(app, uid, hasil);
            } catch (Exception e) {
                galat = e.getMessage();
            } finally {
                hasil.recycle();
            }
            final String g = galat;
            main.post(() -> {
                if (g != null) {
                    Toast.makeText(app, "Foto gagal disimpan: " + g, Toast.LENGTH_LONG).show();
                    txtStatus.setText("Geser dan zoom sampai pas di dalam lingkaran");
                    atur(true);
                    return;
                }
                setResult(RESULT_OK);
                finish();
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }
}
