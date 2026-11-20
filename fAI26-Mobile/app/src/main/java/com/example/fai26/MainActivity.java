package com.example.fai26;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.fai26.auth.Sesi;
import com.example.fai26.ui.GlassNavBar;

import java.util.ArrayList;
import java.util.List;

/**
 * Rumah aplikasi setelah login: tiga layar (Beranda, Riwayat, Akun) dalam satu activity dengan menu bawah
 * bergaya kaca. Layar disimpan hidup dan hanya disembunyikan, jadi pilihan di Beranda tidak hilang saat
 * pindah tab.
 */
public class MainActivity extends AppCompatActivity {

    /** Urutan tab. "persetujuan" hanya ada untuk pengguna yang boleh menyetujui; Riwayat tetap indeks 1. */
    private String[] tag;
    private static final String KEY_TAB = "tab";

    private GlassNavBar nav;
    private int tabAktif = 0;
    /** Apakah tab Persetujuan dibuat saat layar ini dibangun; bila hak berubah, layar dibangun ulang. */
    private boolean bisaSetujuiAwal;

    /** Hak akses diperbarui dari master baru: bangun ulang layar bila tab Persetujuan harus muncul atau hilang. */
    private final com.example.fai26.sync.PerubahanMaster.Pendengar pendengarHak = new com.example.fai26.sync.PerubahanMaster.Pendengar() {
        @Override
        public void masterBerubah() {
        }

        @Override
        public void hakBerubah() {
            if (Sesi.sudahLogin(MainActivity.this) && Sesi.punya(MainActivity.this, "panen.setujui") != bisaSetujuiAwal) {
                // Dimulai ulang (bukan recreate) supaya fragmen dibuat bersih sesuai daftar tab yang baru.
                finish();
                startActivity(getIntent());
                overridePendingTransition(0, 0);
            }
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        com.example.fai26.sync.PerubahanMaster.daftar(pendengarHak);
    }

    @Override
    protected void onStop() {
        super.onStop();
        com.example.fai26.sync.PerubahanMaster.lepas(pendengarHak);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Hanya untuk pengguna yang sudah login.
        if (!Sesi.sudahLogin(this)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        // Beranda berkepala hijau (ikon bilah status putih); disesuaikan lagi tiap ganti layar.
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);

        nav = findViewById(R.id.nav);
        View pembungkus = findViewById(R.id.navPembungkus);
        View kontainer = findViewById(R.id.kontainer);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            float d = getResources().getDisplayMetrics().density;
            // Pembungkus 104 dp: kapsul 68 dp di tengah, sisanya ruang untuk bayangan.
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) pembungkus.getLayoutParams();
            lp.bottomMargin = Math.max(0, bars.bottom - (int) (6 * d));
            lp.leftMargin = bars.left;
            lp.rightMargin = bars.right;
            pembungkus.setLayoutParams(lp);
            return insets;
        });

        final boolean bisaSetujui = Sesi.punya(this, "panen.setujui");
        bisaSetujuiAwal = bisaSetujui;
        tag = bisaSetujui
                ? new String[]{"beranda", "riwayat", "persetujuan", "akun"}
                : new String[]{"beranda", "riwayat", "akun"};
        List<GlassNavBar.Tab> tabs = new ArrayList<>();
        tabs.add(new GlassNavBar.Tab(R.drawable.ic_nav_home, "Beranda"));
        tabs.add(new GlassNavBar.Tab(R.drawable.ic_nav_history, "Riwayat"));
        if (bisaSetujui) tabs.add(new GlassNavBar.Tab(R.drawable.ic_nav_check, "Persetujuan"));
        tabs.add(new GlassNavBar.Tab(R.drawable.ic_nav_person, "Akun"));
        nav.setTabs(tabs);
        nav.setSumber(kontainer);
        nav.setListener(this::tampilkan);
        nav.post(() -> aturBilahStatus(tabAktif));

        FragmentManager fm = getSupportFragmentManager();
        if (savedInstanceState == null) {
            // Semua layar ditambahkan sekaligus; selain Beranda disembunyikan sejak awal.
            FragmentTransaction awal = fm.beginTransaction();
            for (int i = 0; i < tag.length; i++) {
                Fragment f = buatLayar(tag[i]);
                awal.add(R.id.kontainer, f, tag[i]);
                if (i != 0) awal.hide(f);
            }
            awal.commitNow();
        } else {
            tabAktif = Math.min(savedInstanceState.getInt(KEY_TAB, 0), tag.length - 1);
        }
        nav.pilih(tabAktif, false);

        // Tombol kembali dari Riwayat atau Akun menuju Beranda dulu.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (tabAktif != 0) {
                    pilihTab(0);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private static Fragment buatLayar(String nama) {
        switch (nama) {
            case "riwayat":
                return new RiwayatFragment();
            case "persetujuan":
                return new PersetujuanFragment();
            case "akun":
                return new AkunFragment();
            default:
                return new BerandaFragment();
        }
    }

    /** Dipanggil dari layar lain (misalnya "Lihat semua" di Beranda). */
    public void pilihTab(int indeks) {
        nav.pilih(indeks, true);
        tampilkan(indeks);
    }

    /**
     * Ikon bilah status: Beranda dan Akun berkepala hijau (ikon putih); Riwayat dan Persetujuan bertop bar terang (ikon
     * gelap di mode terang). Ditentukan dari nama layar, karena urutan tab berubah sesuai hak akses.
     */
    private void aturBilahStatus(int indeks) {
        boolean modeGelap = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        String nama = tag != null && indeks >= 0 && indeks < tag.length ? tag[indeks] : "beranda";
        boolean latarTerang = "riwayat".equals(nama) || "persetujuan".equals(nama);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(latarTerang && !modeGelap);
    }

    private void tampilkan(int indeks) {
        if (indeks == tabAktif) return;
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction()
                .setReorderingAllowed(true)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        for (int i = 0; i < tag.length; i++) {
            Fragment f = fm.findFragmentByTag(tag[i]);
            if (f == null) continue;
            if (i == indeks) ft.show(f);
            else ft.hide(f);
        }
        ft.commit();
        tabAktif = indeks;
        aturBilahStatus(indeks);
        // Latar buram menangkap ulang setelah layar berganti.
        nav.post(nav::segarkanLatar);
        nav.postDelayed(nav::segarkanLatar, 220);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_TAB, tabAktif);
    }
}
