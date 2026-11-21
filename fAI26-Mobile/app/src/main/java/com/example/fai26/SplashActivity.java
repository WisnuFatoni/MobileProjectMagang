package com.example.fai26;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.example.fai26.auth.Sesi;
import com.example.fai26.ui.LogoSplashView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Layar pembuka: animasi logo fAI26, lalu masuk ke aplikasi. Selama animasi berjalan, sesi login diperiksa di latar belakang; bila
 * masih berlaku langsung ke layar utama (tanpa melewati form login), bila tidak ke layar login.
 */
public class SplashActivity extends AppCompatActivity {

    /** Jeda setelah animasi utama selesai sebelum berpindah, supaya logo utuh sempat terlihat. */
    private static final long TAHAN_MS = 450;

    private static final int BELUM = 0;
    private static final int KE_UTAMA = 1;
    private static final int KE_LOGIN = 2;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicInteger tujuan = new AtomicInteger(BELUM);
    private boolean animasiSelesai = false;
    private boolean pindah = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Latar krem terang: ikon bilah status gelap.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(true);

        LogoSplashView logo = new LogoSplashView(this);
        setContentView(logo, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Periksa sesi sambil animasi berjalan.
        io.execute(() -> {
            int hasil = KE_LOGIN;
            try {
                Fai26App.tungguSiap();
                long user = Sesi.userId(this);
                if (user > 0 && Fai26App.auth(this).masihBerlaku(user)) hasil = KE_UTAMA;
            } catch (Exception e) {
                // gagal memeriksa: tampilkan login, di sana pemeriksaan diulang
            }
            tujuan.set(hasil);
            main.post(this::coba);
        });

        boolean animasiMati = Settings.Global.getFloat(getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
        Runnable selesai = () -> main.postDelayed(() -> {
            animasiSelesai = true;
            coba();
        }, animasiMati ? 150 : TAHAN_MS);
        if (animasiMati) logo.langsungAkhir(selesai);
        else logo.mulai(selesai);
    }

    /** Berpindah hanya bila animasi selesai DAN pemeriksaan sesi sudah ada hasilnya. */
    private void coba() {
        if (pindah || !animasiSelesai || tujuan.get() == BELUM) return;
        pindah = true;
        Intent i = new Intent(this, tujuan.get() == KE_UTAMA ? MainActivity.class : LoginActivity.class);
        startActivity(i);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }
}
