package com.example.fai26;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import android.location.Location;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.fai26.auth.Sesi;
import com.example.fai26.db.Waktu;
import com.example.fai26.lokasi.PencatatLokasi;
import com.example.fai26.detect.Detection;
import com.example.fai26.detect.YoloDetector;
import com.example.fai26.panorama.PanoramaPemindai;
import com.example.fai26.tracking.BunchTracker;
import com.example.fai26.ui.OverlayView;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Layar kamera. Krani menekan MULAI, menyisir tumpukan TBS pelan-pelan, lalu menekan SELESAI.
 * Tiap janjang dihitung sekali oleh {@link BunchTracker}.
 */
public class ScanActivity extends AppCompatActivity {

    private static final String TAG = "ScanActivity";
    static final String EXTRA_JUMLAH = "jumlah";
    static final String EXTRA_FOTO = "foto";
    static final String EXTRA_DETEKSI = "deteksi";

    private PreviewView previewView;
    private OverlayView overlay;
    private TextView txtStatus;
    private TextView txtTotal;
    private TextView txtRincian;
    private View panelHitung;
    private Button btnAksi;

    private final BunchTracker tracker = new BunchTracker(
            KelasModel.JUMLAH, 0.18f, 0.22f, 0.40f, 3, 3500L, 0.005f, 0.55f);
    /** Menyusun satu foto panorama dari seluruh penyisiran (diisi di onCreate: butuh folder cache). */
    private PanoramaPemindai panorama;
    private final ExecutorService analisis = Executors.newSingleThreadExecutor();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private YoloDetector detector;
    private volatile boolean memindai = false;
    private volatile boolean siap = false;
    private boolean selesaiMenunggu = false;
    /** Hak membuat catatan panen (permission panen.buat). Tanpa ini scan tidak bisa dimulai. */
    private boolean izinBuat = false;
    private long tphId = -1;
    private long blokId = -1;
    private long unitId = -1;
    private long pemanenId = 0;
    private String tujuan;
    private String sumberTph;

    /** Hasil layar hasil: bila catatan tersimpan, kembali ke beranda; bila tidak, scan diulang. */
    private final ActivityResultLauncher<Intent> layarHasil =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() == RESULT_OK) {
                    finish();
                    return;
                }
                selesaiMenunggu = false;
                synchronized (tracker) {
                    tracker.reset();
                }
                memindai = false;
                tampilkanIdle();
            });

    /** Frame terakhir dari kamera (belum diputar) dan rotasinya; diputar hanya saat foto bukti dibuat. */
    private volatile Bitmap frameTerakhir;
    private volatile int rotasiTerakhir = 0;
    private volatile List<Detection> deteksiTerakhir = Collections.emptyList();
    private String pesanError = null;
    private int frameKe = 0;
    private static final long JEDA_IDLE_MS = 400;
    private long terakhirIdle = 0;

    private PencatatLokasi lokasi;
    private boolean sudahMintaLokasi = false;

    /** Izin lokasi bersifat pelengkap: bila ditolak, scan tetap jalan dan lokasi dicatat TANPA_GPS. */
    private final ActivityResultLauncher<String[]> mintaIzinLokasi =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), hasil -> {
                if (PencatatLokasi.adaIzin(this)) lokasi.mulai();
            });

    private final ActivityResultLauncher<String> mintaIzin =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), ok -> {
                if (ok) mulaiKamera();
                else txtStatus.setText("Izin kamera ditolak. Aktifkan di pengaturan aplikasi.");
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Layar ini hanya untuk pengguna yang sudah login.
        if (!Sesi.sudahLogin(this)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        // Kamera hanya dibuka dari beranda dengan TPH yang sudah dipilih.
        tphId = getIntent().getLongExtra(Ekstra.EXTRA_TPH_ID, -1L);
        if (tphId < 0) {
            finish();
            return;
        }
        blokId = getIntent().getLongExtra(Ekstra.EXTRA_BLOK_ID, -1L);
        unitId = getIntent().getLongExtra(Ekstra.EXTRA_UNIT_ID, -1L);
        pemanenId = getIntent().getLongExtra(Ekstra.EXTRA_PEMANEN_ID, 0L);
        tujuan = getIntent().getStringExtra(Ekstra.EXTRA_TUJUAN);
        sumberTph = getIntent().getStringExtra(Ekstra.EXTRA_SUMBER);
        panorama = new PanoramaPemindai(new File(getCacheDir(), "panorama"));
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_scan);

        previewView = findViewById(R.id.previewView);
        overlay = findViewById(R.id.overlay);
        txtStatus = findViewById(R.id.txtStatus);
        txtTotal = findViewById(R.id.txtTotal);
        txtRincian = findViewById(R.id.txtRincian);
        panelHitung = findViewById(R.id.panelHitung);
        btnAksi = findViewById(R.id.btnAksi);

        View topBar = findViewById(R.id.topBar);
        View bottomBar = findViewById(R.id.bottomBar);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int pad = (int) (12 * getResources().getDisplayMetrics().density);
            topBar.setPadding(pad, bars.top + pad, pad, pad);
            int pad16 = (int) (16 * getResources().getDisplayMetrics().density);
            bottomBar.setPadding(pad16, pad16, pad16, bars.bottom + pad16);
            return insets;
        });

        izinBuat = Sesi.punya(this, "panen.buat");
        ((TextView) findViewById(R.id.txtTujuan)).setText(tujuan == null ? "" : tujuan);
        findViewById(R.id.btnTutup).setOnClickListener(v -> finish());
        btnAksi.setOnClickListener(v -> {
            if (memindai) selesai();
            else mulai();
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            mulaiKamera();
        } else {
            mintaIzin.launch(Manifest.permission.CAMERA);
        }
    }

    private void siapkanLokasi() {
        if (lokasi == null) lokasi = new PencatatLokasi(this);
        if (PencatatLokasi.adaIzin(this)) {
            lokasi.mulai();
        } else if (!sudahMintaLokasi) {
            sudahMintaLokasi = true;
            mintaIzinLokasi.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION});
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Titik GPS dikumpulkan hanya selama layar scan terlihat.
        if (lokasi != null && PencatatLokasi.adaIzin(this)) lokasi.mulai();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (lokasi != null) lokasi.berhenti();
    }

    private void mulaiKamera() {
        siapkanLokasi();
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                bindKamera(future.get());
            } catch (ExecutionException | InterruptedException e) {
                txtStatus.setText("Kamera gagal: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindKamera(ProcessCameraProvider provider) {
        // Pratinjau dan analisis memakai rasio yang sama supaya kotak pas di layar.
        ResolutionSelector selector = new ResolutionSelector.Builder()
                .setAspectRatioStrategy(new AspectRatioStrategy(
                        AspectRatio.RATIO_16_9, AspectRatioStrategy.FALLBACK_RULE_AUTO))
                .build();

        Preview preview = new Preview.Builder().setResolutionSelector(selector).build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setResolutionSelector(selector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build();
        analysis.setAnalyzer(analisis, this::analisisFrame);

        provider.unbindAll();
        provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
        txtStatus.setText("Memuat model...");
    }

    private void analisisFrame(androidx.camera.core.ImageProxy proxy) {
        try {
            // Sebelum MULAI, kotak pratinjau cukup diperbarui beberapa kali per detik.
            // Ini menghemat baterai dan menahan panas sehingga HP tidak melambat saat memindai.
            if (!memindai) {
                long sekarang = SystemClock.elapsedRealtime();
                if (sekarang - terakhirIdle < JEDA_IDLE_MS) return;
                terakhirIdle = sekarang;
            }
            if (detector == null) detector = new YoloDetector(this);

            long tc0 = System.nanoTime();
            Bitmap mentah = proxy.toBitmap();
            int rotasi = proxy.getImageInfo().getRotationDegrees();
            float konversiMs = (System.nanoTime() - tc0) / 1e6f;
            boolean tukar = rotasi == 90 || rotasi == 270;
            int fw = tukar ? mentah.getHeight() : mentah.getWidth(); // ukuran frame tegak
            int fh = tukar ? mentah.getWidth() : mentah.getHeight();

            long t0 = SystemClock.elapsedRealtime();
            List<Detection> deteksi = detector.detect(mentah, rotasi);
            long ms = SystemClock.elapsedRealtime() - t0;
            float[] w = detector.waktuMs;
            String rincian = String.format(java.util.Locale.US, "konversi %.0f • pra %.0f • model %.0f • pasca %.0f",
                    konversiMs, w[0], w[1], w[2]);
            if (++frameKe % 30 == 0) {
                Log.i(TAG, "frame " + fw + "x" + fh + " " + rincian + " (" + detector.backendNama() + ")");
            }

            int[] hitung = null;
            List<BunchTracker.Info> labelTrack = Collections.emptyList();
            if (memindai) {
                synchronized (tracker) {
                    tracker.update(deteksi, System.currentTimeMillis());
                    hitung = tracker.counts();
                    labelTrack = tracker.terlihat();
                }
                // Panorama hanya pelengkap bukti: tidak pernah melempar galat dan tidak memengaruhi hitungan.
                panorama.tambahFrame(mentah, rotasi, deteksi);
            }
            frameTerakhir = mentah;
            rotasiTerakhir = rotasi;
            deteksiTerakhir = deteksi;

            int[] h = hitung;
            String backend = detector.backendNama();
            final List<BunchTracker.Info> lt = labelTrack;
            main.post(() -> tampilkan(deteksi, lt, fw, fh, h, ms, backend + " • " + rincian));
        } catch (Throwable e) {
            Log.e(TAG, "Analisis gagal", e);
            String pesan = e.getClass().getSimpleName() + ": " + e.getMessage();
            main.post(() -> {
                pesanError = pesan;
                txtStatus.setText("GAGAL: " + pesan);
                btnAksi.setEnabled(false);
            });
        } finally {
            proxy.close();
        }
    }

    private void tampilkan(List<Detection> deteksi, List<BunchTracker.Info> labelTrack, int fw, int fh,
                           int[] hitung, long ms, String backend) {
        if (pesanError != null) return;
        overlay.set(deteksi, labelTrack, fw, fh);
        if (!siap) {
            siap = true;
            btnAksi.setEnabled(izinBuat && !selesaiMenunggu);
        }
        if (!izinBuat) {
            txtStatus.setText("Akun ini tidak boleh membuat catatan panen");
            return;
        }
        String info = " • " + ms + " ms (" + backend + ")";
        if (memindai) {
            txtStatus.setText("Memindai... gerakkan kamera pelan menyisir tumpukan" + info);
            int total = 0;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < KelasModel.JUMLAH; i++) {
                total += hitung[i];
                if (hitung[i] > 0) {
                    if (sb.length() > 0) sb.append("   ");
                    sb.append(KelasModel.TAMPIL[i]).append(": ").append(hitung[i]);
                }
            }
            txtTotal.setText("Total: " + total + " tandan");
            txtRincian.setText(sb);
        } else {
            txtStatus.setText("Tekan MULAI lalu sisir tumpukan TBS" + info);
        }
    }

    private void tampilkanIdle() {
        panelHitung.setVisibility(View.GONE);
        btnAksi.setText("MULAI");
        btnAksi.setEnabled(siap && izinBuat);
        txtStatus.setText("Tekan MULAI lalu sisir tumpukan TBS");
    }

    private void mulai() {
        synchronized (tracker) {
            tracker.reset();
        }
        panorama.mulai();
        txtTotal.setText("Total: 0 tandan");
        txtRincian.setText("");
        panelHitung.setVisibility(View.VISIBLE);
        btnAksi.setText("SELESAI");
        memindai = true;
    }

    /** Kotak pada foto: per deteksi 6 angka (kelas, confidence, x1, y1, x2, y2). */
    private static float[] kotakKeAngka(List<Detection> deteksi) {
        final float[] kotak = new float[deteksi.size() * 6];
        for (int n = 0; n < deteksi.size(); n++) {
            Detection d = deteksi.get(n);
            kotak[n * 6] = d.classIndex;
            kotak[n * 6 + 1] = d.confidence;
            kotak[n * 6 + 2] = d.x1;
            kotak[n * 6 + 3] = d.y1;
            kotak[n * 6 + 4] = d.x2;
            kotak[n * 6 + 5] = d.y2;
        }
        return kotak;
    }

    private void selesai() {
        if (selesaiMenunggu) return;
        selesaiMenunggu = true;
        memindai = false;
        btnAksi.setEnabled(false);

        final int[] hitung;
        synchronized (tracker) {
            hitung = tracker.counts();
        }
        final Bitmap frameMentah = frameTerakhir;
        final int rotasi = rotasiTerakhir;
        final List<Detection> deteksi = deteksiTerakhir;
        final File foto = new File(getCacheDir(), "keyframe_sementara.jpg");

        // Foto bukti: satu gambar panorama dari seluruh penyisiran (bila kamera bergeser cukup jauh dan bisa
        // disejajarkan), kalau tidak satu frame terakhir beserta kotaknya seperti biasa.
        io.execute(() -> {
            boolean adaFoto = false;
            boolean panoramaJadi = false;
            String alasanTunggal = null;
            try {
                PanoramaPemindai.Hasil hasil = panorama.selesai(frameMentah, rotasi, deteksi, foto);
                adaFoto = hasil.adaFoto();
                panoramaJadi = hasil.panorama();
                alasanTunggal = hasil.alasan;
            } catch (Exception e) {
                Log.e(TAG, "Gagal menyimpan foto", e);
            }
            final boolean fotoOk = adaFoto;
            final String alasanFoto = alasanTunggal;
            // Kotak tiap deteksi sudah tergambar di foto. Pada panorama koordinatnya tidak lagi sama dengan satu frame
            // (dan buah yang sama bisa tampil di beberapa potongan), jadi tidak disimpan sebagai baris deteksi.
            final float[] kotak = panoramaJadi ? new float[0] : kotakKeAngka(deteksi);
            main.post(() -> {
                // Panorama tidak jadi: beri tahu alasannya supaya pemindai tahu apa yang diperbaiki di sisiran berikutnya.
                if (alasanFoto != null) {
                    Toast.makeText(this, "Foto bukti 1 frame, bukan panorama: " + alasanFoto + ". Sisir pelan dan stabil.", Toast.LENGTH_LONG).show();
                }
                Intent i = new Intent(this, HasilActivity.class);
                i.putExtra(EXTRA_JUMLAH, hitung);
                i.putExtra(Ekstra.EXTRA_TPH_ID, tphId);
                i.putExtra(Ekstra.EXTRA_BLOK_ID, blokId);
                i.putExtra(Ekstra.EXTRA_UNIT_ID, unitId);
                i.putExtra(Ekstra.EXTRA_PEMANEN_ID, pemanenId);
                i.putExtra(Ekstra.EXTRA_SUMBER, sumberTph);
                i.putExtra(EXTRA_DETEKSI, kotak);
                Location titik = lokasi == null ? null : lokasi.terbaik();
                if (titik != null) {
                    i.putExtra(Ekstra.EXTRA_GPS_LAT, titik.getLatitude());
                    i.putExtra(Ekstra.EXTRA_GPS_LON, titik.getLongitude());
                    if (titik.hasAccuracy()) i.putExtra(Ekstra.EXTRA_GPS_AKURASI, (double) titik.getAccuracy());
                    i.putExtra(Ekstra.EXTRA_GPS_WAKTU, Waktu.utc(titik.getTime()));
                    i.putExtra(Ekstra.EXTRA_GPS_MOCK, PencatatLokasi.lokasiPalsu(titik));
                }
                if (fotoOk) i.putExtra(EXTRA_FOTO, foto.getAbsolutePath());
                layarHasil.launch(i);
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        analisis.execute(() -> {
            if (detector != null) detector.close();
        });
        analisis.shutdown();
        io.execute(() -> panorama.bersihkan());
        io.shutdown();
    }
}
