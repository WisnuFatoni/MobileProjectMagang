package com.example.fai26;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.fai26.db.AppDatabase;
import com.example.fai26.db.dao.Hasil;
import com.example.fai26.qr.QrDecoder;
import com.example.fai26.qr.QrTph;
import com.example.fai26.ui.ViewfinderView;
import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Memindai QR di TPH. Bila QR dikenali dan TPH-nya ada di master, hasilnya dikembalikan ke beranda
 * (RESULT_OK dengan data TPH). Tombol "pilih manual" mengembalikan RESULT_FIRST_USER.
 */
public class QrActivity extends AppCompatActivity {

    private static final String TAG = "QrActivity";
    private static final long JEDA_BACA_MS = 120;
    private static final long JEDA_PESAN_MS = 2500;

    private final ExecutorService analisis = Executors.newSingleThreadExecutor();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final QrDecoder decoder = new QrDecoder();
    /** Bernilai true selama ada QR yang sedang diperiksa atau activity sudah selesai. */
    private final AtomicBoolean sibuk = new AtomicBoolean(false);

    private PreviewView previewView;
    private ViewfinderView viewfinder;
    private View boxPesan;
    private TextView txtPesan;
    private TextView txtPetunjuk;
    private MaterialButton btnSenter;
    private Camera kamera;
    private boolean senterNyala = false;

    private long terakhirBaca = 0;
    private String teksGalatTerakhir = null;
    private long waktuGalatTerakhir = 0;

    private final ActivityResultLauncher<String> mintaIzin =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), ok -> {
                if (ok) mulaiKamera();
                else tampilkanPesan("Izin kamera ditolak. Aktifkan di pengaturan aplikasi.", false);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_qr);

        previewView = findViewById(R.id.previewView);
        viewfinder = findViewById(R.id.viewfinder);
        boxPesan = findViewById(R.id.boxPesan);
        txtPesan = findViewById(R.id.txtPesan);
        txtPetunjuk = findViewById(R.id.txtPetunjuk);
        btnSenter = findViewById(R.id.btnSenter);

        View topBar = findViewById(R.id.topBar);
        View bottomBar = findViewById(R.id.bottomBar);
        View panel = findViewById(R.id.panelPetunjuk);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            float d = getResources().getDisplayMetrics().density;
            topBar.setPadding((int) (12 * d), bars.top + (int) (12 * d), (int) (12 * d), (int) (12 * d));
            bottomBar.setPadding((int) (20 * d), (int) (20 * d), (int) (20 * d), bars.bottom + (int) (20 * d));
            return insets;
        });
        // Petunjuk diletakkan tepat di bawah bingkai pembidik.
        viewfinder.post(() -> {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) panel.getLayoutParams();
            lp.topMargin = (int) viewfinder.bawahBingkai() + (int) (20 * getResources().getDisplayMetrics().density);
            panel.setLayoutParams(lp);
        });

        findViewById(R.id.btnBatal).setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        findViewById(R.id.btnManual).setOnClickListener(v -> {
            setResult(RESULT_FIRST_USER);
            finish();
        });
        btnSenter.setOnClickListener(v -> {
            senterNyala = !senterNyala;
            if (kamera != null) kamera.getCameraControl().enableTorch(senterNyala);
            btnSenter.setText(senterNyala ? "Matikan senter" : "Nyalakan senter");
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            mulaiKamera();
        } else {
            mintaIzin.launch(Manifest.permission.CAMERA);
        }
    }

    private void mulaiKamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                bindKamera(future.get());
            } catch (ExecutionException | InterruptedException e) {
                tampilkanPesan("Kamera gagal: " + e.getMessage(), false);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindKamera(ProcessCameraProvider provider) {
        ResolutionSelector selector = new ResolutionSelector.Builder()
                .setAspectRatioStrategy(new AspectRatioStrategy(
                        AspectRatio.RATIO_16_9, AspectRatioStrategy.FALLBACK_RULE_AUTO))
                .build();
        Preview preview = new Preview.Builder().setResolutionSelector(selector).build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setResolutionSelector(selector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build(); // format bawaan YUV_420_888: bidang pertama adalah luminansi
        analysis.setAnalyzer(analisis, this::periksaFrame);

        provider.unbindAll();
        kamera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
        if (kamera.getCameraInfo().hasFlashUnit()) btnSenter.setVisibility(View.VISIBLE);
    }

    private void periksaFrame(ImageProxy proxy) {
        try {
            long sekarang = SystemClock.elapsedRealtime();
            if (sibuk.get() || sekarang - terakhirBaca < JEDA_BACA_MS) return;
            terakhirBaca = sekarang;

            byte[] y = potongTengah(proxy);
            if (y == null) return;
            int sisi = (int) Math.sqrt(y.length);
            String teks = decoder.baca(y, sisi, sisi);
            if (teks != null) prosesTeks(teks);
        } catch (Throwable e) {
            Log.e(TAG, "Gagal membaca frame", e);
        } finally {
            proxy.close();
        }
    }

    /**
     * Memotong persegi di sekitar bingkai pembidik dari bidang luminansi. Yang dibaca hanya yang terlihat
     * di dalam bingkai, jadi lebih cepat dan krani tahu persis ke mana mengarahkan kamera.
     */
    private byte[] potongTengah(ImageProxy proxy) {
        int vw = previewView.getWidth();
        int vh = previewView.getHeight();
        if (vw == 0 || vh == 0 || viewfinder.getWidth() == 0) return null;

        int bw = proxy.getWidth();
        int bh = proxy.getHeight();
        int rot = proxy.getImageInfo().getRotationDegrees();
        boolean tukar = rot == 90 || rot == 270;
        int fw = tukar ? bh : bw; // ukuran frame tegak
        int fh = tukar ? bw : bh;

        // Pratinjau fillCenter: skala terbesar, tengah dipaskan.
        float skala = Math.max((float) vw / fw, (float) vh / fh);
        float offY = (vh - fh * skala) / 2f;
        float pusatU = fw / 2f;
        float pusatV = (vh * 0.42f - offY) / skala; // bingkai berada di 42% tinggi layar
        int sisi = (int) Math.min(viewfinder.sisi() / skala * 1.15f, Math.min(bw, bh));

        // Dari koordinat frame tegak (u, v) ke koordinat buffer (x, y).
        float cx;
        float cy;
        switch (rot) {
            case 90:
                cx = pusatV;
                cy = bh - 1 - pusatU;
                break;
            case 180:
                cx = bw - 1 - pusatU;
                cy = bh - 1 - pusatV;
                break;
            case 270:
                cx = bw - 1 - pusatV;
                cy = pusatU;
                break;
            default:
                cx = pusatU;
                cy = pusatV;
        }
        int kiri = Math.max(0, Math.min(bw - sisi, Math.round(cx - sisi / 2f)));
        int atas = Math.max(0, Math.min(bh - sisi, Math.round(cy - sisi / 2f)));

        ImageProxy.PlaneProxy bidang = proxy.getPlanes()[0];
        ByteBuffer buf = bidang.getBuffer();
        int rowStride = bidang.getRowStride();
        byte[] hasil = new byte[sisi * sisi];
        for (int j = 0; j < sisi; j++) {
            buf.position((atas + j) * rowStride + kiri);
            buf.get(hasil, j * sisi, sisi);
        }
        return hasil;
    }

    private void prosesTeks(String teks) {
        // Galat yang sama tidak ditampilkan berulang-ulang pada frame berikutnya.
        long sekarang = SystemClock.elapsedRealtime();
        if (teks.equals(teksGalatTerakhir) && sekarang - waktuGalatTerakhir < JEDA_PESAN_MS) return;
        if (!sibuk.compareAndSet(false, true)) return;

        QrTph.Hasil h = QrTph.baca(teks);
        if (!h.berhasil()) {
            teksGalatTerakhir = teks;
            waktuGalatTerakhir = sekarang;
            main.post(() -> {
                tampilkanPesan(h.galat, true);
                sibuk.set(false);
            });
            return;
        }

        io.execute(() -> {
            try {
                Fai26App.tungguSiap();
                Hasil.TphPilihan t = AppDatabase.getInstance(getApplicationContext())
                        .masterDao().tphDariQr(h.tph.kode);
                if (t == null) {
                    teksGalatTerakhir = teks;
                    waktuGalatTerakhir = SystemClock.elapsedRealtime();
                    main.post(() -> {
                        tampilkanPesan("TPH " + h.tph.blok + " / " + h.tph.noTph
                                + " tidak ada di data HP ini. Perbarui data master atau pilih TPH manual.", true);
                        sibuk.set(false);
                    });
                    return;
                }
                main.post(() -> selesai(t));
            } catch (Exception e) {
                main.post(() -> {
                    tampilkanPesan("Gagal memeriksa TPH: " + e.getMessage(), true);
                    sibuk.set(false);
                });
            }
        });
    }

    private void selesai(Hasil.TphPilihan t) {
        previewView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        Intent hasil = new Intent();
        hasil.putExtra(Ekstra.EXTRA_TPH_ID, t.tphId);
        hasil.putExtra(Ekstra.EXTRA_BLOK_ID, t.blokId);
        hasil.putExtra(Ekstra.EXTRA_UNIT_ID, t.unitId);
        hasil.putExtra(Ekstra.EXTRA_BLOK_KODE, t.blok);
        hasil.putExtra(Ekstra.EXTRA_NO_TPH, t.noTph);
        setResult(RESULT_OK, hasil);
        finish();
    }

    /** @param sementara true bila pesan hilang sendiri dan pemindaian berlanjut. */
    private void tampilkanPesan(String teks, boolean sementara) {
        txtPesan.setText(teks);
        boxPesan.setVisibility(View.VISIBLE);
        txtPetunjuk.setVisibility(View.GONE);
        viewfinder.setWarnaSudut(0xFFF2B8B5);
        if (sementara) {
            main.postDelayed(() -> {
                boxPesan.setVisibility(View.GONE);
                txtPetunjuk.setVisibility(View.VISIBLE);
                viewfinder.setWarnaSudut(0xFF4ADE80);
            }, JEDA_PESAN_MS);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        main.removeCallbacksAndMessages(null);
        analisis.shutdown();
        io.shutdown();
    }
}
