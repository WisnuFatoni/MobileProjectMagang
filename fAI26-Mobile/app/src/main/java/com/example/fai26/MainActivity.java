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
import com.example.fai26.ipd.IpdBerandaFragment;
import com.example.fai26.ipd.IpdPersetujuanFragment;
import com.example.fai26.ipd.IpdRiwayatFragment;
import com.example.fai26.ui.GlassNavBar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Rumah aplikasi setelah login. Layar awal (Home) menampilkan kotak fitur (Panen dan IPD) menurut hak akses akun. Menu bawah
 * bergaya kaca bertingkat: di Home hanya Home dan Akun; setelah masuk sebuah fitur muncul menu fitur itu (Panen: Beranda,
 * Riwayat, Persetujuan; IPD: Mulai, Riwayat, Persetujuan). Layar disimpan hidup dan hanya disembunyikan, jadi isian di sebuah
 * layar tidak hilang saat pindah tab.
 */
public class MainActivity extends AppCompatActivity {

    public static final String MODE_HOME = "HOME";
    public static final String MODE_PANEN = "PANEN";
    public static final String MODE_IPD = "IPD";

    private static final String KEY_MODE = "mode";
    private static final String KEY_TAB = "tab";
    private static final List<String> SEMUA_LAYAR = Arrays.asList(
            "home", "akun", "beranda", "riwayat", "persetujuan", "ipd_mulai", "ipd_riwayat", "ipd_setuju");

    private GlassNavBar nav;
    private String modeAktif = MODE_HOME;
    /** Tag tab pada mode aktif, berurutan seperti di menu bawah. */
    private List<String> tag = new ArrayList<>();
    private int tabAktif = 0;
    /** Hak yang membentuk menu saat layar dibangun; bila berubah (master baru), layar dibangun ulang. */
    private String hakAwal;

    /** Hak akses diperbarui dari master baru: bangun ulang layar bila kotak atau tab harus muncul atau hilang. */
    private final com.example.fai26.sync.PerubahanMaster.Pendengar pendengarHak = new com.example.fai26.sync.PerubahanMaster.Pendengar() {
        @Override
        public void masterBerubah() {
        }

        @Override
        public void hakBerubah() {
            if (Sesi.sudahLogin(MainActivity.this) && !hakSekarang().equals(hakAwal)) {
                // Dimulai ulang (bukan recreate) supaya fragmen dibuat bersih sesuai daftar tab yang baru.
                finish();
                startActivity(getIntent());
                overridePendingTransition(0, 0);
            }
        }
    };

    private String hakSekarang() {
        return Sesi.bolehPanen(this) + "|" + Sesi.bolehIpd(this) + "|" + Sesi.punya(this, "panen.setujui") + "|"
                + Sesi.punya(this, "ipd.setujui") + "|" + Sesi.punya(this, "ipd.buat");
    }

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
        // HP yang sudah telanjur login offline sebagai pengguna lain: buang token online milik akun sebelumnya.
        com.example.fai26.auth.SesiToken.cocokkanDiLatar(this);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        // Home berkepala hijau (ikon bilah status putih); disesuaikan lagi tiap ganti layar.
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

        hakAwal = hakSekarang();
        nav.setSumber(kontainer);
        nav.setListener(this::tabDipilih);

        String mode = MODE_HOME;
        int tab = 0;
        if (savedInstanceState != null) {
            mode = savedInstanceState.getString(KEY_MODE, MODE_HOME);
            tab = savedInstanceState.getInt(KEY_TAB, 0);
        }
        terapkanMode(mode, tab, false);

        // Tombol kembali: dari layar mana pun menuju layar utama mode itu dulu, lalu ke Home, lalu keluar.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (MODE_HOME.equals(modeAktif)) {
                    if (tabAktif != 0) {
                        pilihIndeks(0);
                        return;
                    }
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                } else if (tabAktif != indeksUtama(modeAktif)) {
                    pilihIndeks(indeksUtama(modeAktif));
                } else {
                    bukaMode(MODE_HOME);
                }
            }
        });
    }

    // ---- Mode dan tab -----------------------------------------------------------------------------

    private List<String> tagMode(String mode) {
        List<String> t = new ArrayList<>();
        t.add("home");
        if (MODE_PANEN.equals(mode)) {
            t.add("beranda");
            t.add("riwayat");
            if (Sesi.punya(this, "panen.setujui")) t.add("persetujuan");
        } else if (MODE_IPD.equals(mode)) {
            t.add("ipd_mulai");
            t.add("ipd_riwayat");
            if (Sesi.punya(this, "ipd.setujui")) t.add("ipd_setuju");
        }
        t.add("akun");
        return t;
    }

    /** Indeks layar utama pada sebuah mode: Home bagi Home, Beranda panen bagi Panen, Mulai bagi IPD. */
    private int indeksUtama(String mode) {
        return MODE_HOME.equals(mode) ? 0 : 1;
    }

    private static String labelTab(String nama) {
        switch (nama) {
            case "home":
                return "Home";
            case "beranda":
                return "Beranda";
            case "riwayat":
            case "ipd_riwayat":
                return "Riwayat";
            case "persetujuan":
            case "ipd_setuju":
                return "Persetujuan";
            case "ipd_mulai":
                return "Beranda";
            default:
                return "Akun";
        }
    }

    private static int ikonTab(String nama) {
        switch (nama) {
            case "home":
                return R.drawable.ic_nav_home;
            case "beranda":
                return R.drawable.ic_nav_panen;
            case "riwayat":
            case "ipd_riwayat":
                return R.drawable.ic_nav_history;
            case "persetujuan":
            case "ipd_setuju":
                return R.drawable.ic_nav_check;
            case "ipd_mulai":
                return R.drawable.ic_nav_ipd;
            default:
                return R.drawable.ic_nav_person;
        }
    }

    /** Membangun menu bawah untuk sebuah mode dan menampilkan tab ke-{@code tab}. */
    private void terapkanMode(String mode, int tab, boolean animasi) {
        if (MODE_PANEN.equals(mode) && !Sesi.bolehPanen(this)) mode = MODE_HOME;
        if (MODE_IPD.equals(mode) && !Sesi.bolehIpd(this)) mode = MODE_HOME;
        modeAktif = mode;
        tag = tagMode(mode);
        tabAktif = Math.max(0, Math.min(tab, tag.size() - 1));
        List<GlassNavBar.Tab> tabs = new ArrayList<>();
        for (String t : tag) tabs.add(new GlassNavBar.Tab(ikonTab(t), labelTab(t)));
        nav.setTabs(tabs);
        nav.pilih(tabAktif, animasi);
        tampilkanLayar(tag.get(tabAktif));
        aturBilahStatus(tag.get(tabAktif));
    }

    /** Dipanggil dari kotak di Home: membuka Panen atau IPD (atau kembali ke Home). */
    public void bukaMode(String mode) {
        terapkanMode(mode, MODE_HOME.equals(mode) ? 0 : 1, true);
    }

    /** Dipanggil dari layar lain (misalnya "Lihat semua" di Beranda panen): pindah ke layar bernama itu pada mode aktif. */
    public void pilihLayar(String nama) {
        int i = tag.indexOf(nama);
        if (i >= 0) pilihIndeks(i);
    }

    private void pilihIndeks(int indeks) {
        nav.pilih(indeks, true);
        tabDipilih(indeks);
    }

    private void tabDipilih(int indeks) {
        if (indeks < 0 || indeks >= tag.size()) return;
        String nama = tag.get(indeks);
        // Tab Home di dalam sebuah fitur kembali ke layar Home.
        if ("home".equals(nama) && !MODE_HOME.equals(modeAktif)) {
            terapkanMode(MODE_HOME, 0, true);
            return;
        }
        if (indeks == tabAktif) return;
        tabAktif = indeks;
        tampilkanLayar(nama);
        aturBilahStatus(nama);
        // Latar buram menangkap ulang setelah layar berganti.
        nav.post(nav::segarkanLatar);
        nav.postDelayed(nav::segarkanLatar, 220);
    }

    private Fragment buatLayar(String nama) {
        switch (nama) {
            case "home":
                return new HomeFragment();
            case "beranda":
                return new BerandaFragment();
            case "riwayat":
                return new RiwayatFragment();
            case "persetujuan":
                return new PersetujuanFragment();
            case "ipd_mulai":
                return new IpdBerandaFragment();
            case "ipd_riwayat":
                return new IpdRiwayatFragment();
            case "ipd_setuju":
                return new IpdPersetujuanFragment();
            default:
                return new AkunFragment();
        }
    }

    /** Menampilkan satu layar (dibuat saat pertama dibuka) dan menyembunyikan yang lain. */
    private void tampilkanLayar(String nama) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction().setReorderingAllowed(true)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        Fragment f = fm.findFragmentByTag(nama);
        if (f == null) {
            f = buatLayar(nama);
            ft.add(R.id.kontainer, f, nama);
        }
        for (String lain : SEMUA_LAYAR) {
            if (lain.equals(nama)) continue;
            Fragment x = fm.findFragmentByTag(lain);
            if (x != null) ft.hide(x);
        }
        ft.show(f);
        ft.commitNowAllowingStateLoss();
    }

    /**
     * Ikon bilah status: Home, Beranda panen, dan Akun berkepala hijau (ikon putih); layar lain bertop bar terang (ikon gelap di
     * mode terang). Ditentukan dari nama layar, karena urutan tab berubah sesuai mode dan hak akses.
     */
    private void aturBilahStatus(String nama) {
        boolean modeGelap = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        boolean latarTerang = !("home".equals(nama) || "beranda".equals(nama) || "akun".equals(nama));
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(latarTerang && !modeGelap);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_MODE, modeAktif);
        outState.putInt(KEY_TAB, tabAktif);
    }
}
