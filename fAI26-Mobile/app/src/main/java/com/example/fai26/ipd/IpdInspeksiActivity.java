package com.example.fai26.ipd;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.fai26.Fai26App;
import com.example.fai26.PencarianAdapter;
import com.example.fai26.R;
import com.example.fai26.auth.Sesi;
import com.example.fai26.db.AppDatabase;
import com.example.fai26.db.IpdRepository;
import com.example.fai26.db.dao.IpdDao;
import com.example.fai26.db.dao.IpdHasil;
import com.example.fai26.db.entity.IpdFoto;
import com.example.fai26.db.entity.KodeTemuan;
import com.example.fai26.sync.ApiException;
import com.example.fai26.sync.HttpServerApi;
import com.example.fai26.sync.PengaturanSync;
import com.example.fai26.sync.PersetujuanServer;
import com.example.fai26.sync.ServerApi;
import com.example.fai26.sync.SyncJadwal;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Satu inspeksi IPD (Inspeksi Panen Detail) dalam tiga langkah: (1) kepala catatan: tanggal, divisi (asisten terisi otomatis),
 * blok, pemanen, mandor; (2) pokok per pokok: tombol besar BERSIH atau pilih kode temuan; (3) ringkasan loses, foto bukti
 * (tanpa batas, dikompres, dengan keterangan), lalu Kirim. Boleh dibuka lagi untuk melanjutkan draf atau mengubah inspeksi yang
 * belum disetujui asisten.
 */
public class IpdInspeksiActivity extends AppCompatActivity {

    public static final String EXTRA_IPD_ID = "ipd_id";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private AppDatabase db;
    private IpdDao dao;
    private IpdRepository repo;
    private IpdDraf draf = new IpdDraf();
    private boolean kotor;
    private String peringatanKunci = "";

    // master
    private List<KodeTemuan> kode = new ArrayList<>();
    private List<IpdHasil.DivisiPilihan> daftarDivisi = new ArrayList<>();
    private List<IpdHasil.PemanenPilihan> daftarPemanen = new ArrayList<>();
    private List<IpdHasil.OrangPilihan> daftarMandor = new ArrayList<>();
    private IpdHasil.DivisiPilihan selDivisi;
    private IpdHasil.BlokPilihan selBlok;
    private IpdHasil.PemanenPilihan selPemanen;
    private IpdHasil.OrangPilihan selMandor;
    /** Blok milik divisi terpilih (dimuat di thread latar: Room tidak boleh dibaca di thread utama). */
    private List<IpdHasil.BlokPilihan> daftarBlok = new ArrayList<>();

    // halaman
    private FrameLayout wadah;
    private final View[] halaman = new View[3];
    private final TextView[] langkahChip = new TextView[3];
    private int langkah;

    // langkah 1
    private TextView btnTanggal;
    private TextView txtAsisten;
    private MaterialAutoCompleteTextView ddBlok;
    private EditText edAncak;

    // langkah 2
    private TextView txtInfo;
    private TextView txtPosisi;
    private EditText edBaris;
    private EditText edPokok;
    private TextView txtRekapMini;
    private MaterialButton btnLanjut;
    private final Map<String, BarisKode> barisKode = new LinkedHashMap<>();
    private com.google.android.material.materialswitch.MaterialSwitch swDuaBaris;
    private EditText edBaris2;
    private LinearLayout kolomBaris2;
    private LinearLayout tabSisi;
    private final MaterialButton[] btnSisi = new MaterialButton[2];
    /** Sisi (baris) yang sedang diisi: 0 = baris pertama, 1 = baris ke-2. */
    private int sisi;
    /** Temuan per sisi untuk pokok yang sedang diisi (kode -> jumlah). */
    @SuppressWarnings("unchecked")
    private final Map<String, Integer>[] sisiTemuan = new Map[]{new LinkedHashMap<String, Integer>(), new LinkedHashMap<String, Integer>()};

    // langkah 3
    private LinearLayout kotakRekap;
    private LinearLayout kotakFoto;
    private EditText edCatatan;
    private File fotoMentah;
    /** Bila tidak null, foto yang baru diambil disimpan langsung dengan keterangan ini (tanpa dialog). */
    private String keteranganFotoOtomatis;
    private MaterialButton btnFotoPokok;
    private ActivityResultLauncher<Uri> ambilFoto;
    private ActivityResultLauncher<String> mintaIzinKamera;

    // ---- Jalur GPS inspeksi ----
    /** Merekam jalur GPS selama mencatat pokok (bisa dimatikan; bawaan mati bila yang diubah inspeksi yang sudah terkirim). */
    private boolean rekamJejak = true;
    private boolean jejakDitentukan = false;
    private com.google.android.material.materialswitch.MaterialSwitch swJejak;
    private TextView txtJejak;
    private ActivityResultLauncher<String[]> mintaIzinJejak;
    private final Runnable pantauJejak = new Runnable() {
        @Override
        public void run() {
            perbaruiTeksJejak();
            main.postDelayed(this, 2000);
        }
    };

    /** Satu baris kode temuan di langkah 2. */
    private static final class BarisKode {
        MaterialButton tombol;
        LinearLayout stepper;
        TextView txtJumlah;
        int jumlah = 1;
        boolean pilih;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Sesi.sudahLogin(this) || !Sesi.punya(this, "ipd.buat")) {
            finish();
            return;
        }
        EdgeToEdge.enable(this);
        db = AppDatabase.getInstance(getApplicationContext());
        dao = db.ipdDao();
        repo = Fai26App.ipdRepository(this);

        ambilFoto = registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
            if (ok && fotoMentah != null) prosesFoto(fotoMentah);
            else {
                keteranganFotoOtomatis = null;
                if (fotoMentah != null) //noinspection ResultOfMethodCallIgnored
                    fotoMentah.delete();
            }
        });
        mintaIzinJejak = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), hasil -> {
            if (com.example.fai26.lokasi.PencatatLokasi.adaIzin(this)) {
                mulaiJejak();
            } else {
                rekamJejak = false;
                if (swJejak != null) swJejak.setChecked(false);
                Toast.makeText(this, "Izin lokasi diperlukan untuk merekam jalur; inspeksi tetap bisa dicatat tanpa jalur", Toast.LENGTH_LONG).show();
            }
        });
        mintaIzinKamera = registerForActivityResult(new ActivityResultContracts.RequestPermission(), ok -> {
            if (ok) mulaiAmbilFoto();
            else Toast.makeText(this, "Izin kamera diperlukan untuk mengambil foto", Toast.LENGTH_LONG).show();
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                keluar();
            }
        });

        final String idAda = getIntent().getStringExtra(EXTRA_IPD_ID);
        io.execute(() -> {
            kode = dao.kodeAktif();
            daftarDivisi = dao.divisiPilihan();
            daftarPemanen = dao.pemanenPilihan();
            daftarMandor = dao.orangBerperan("MANDOR");
            final IpdDraf ada = idAda == null ? null : repo.muat(idAda);
            if (ada != null) daftarBlok = dao.blokPilihan(ada.divisiId);
            main.post(() -> mulai(idAda, ada));
        });
    }

    private void mulai(String idAda, IpdDraf ada) {
        if (isFinishing()) return;
        if (idAda != null) {
            if (ada == null || !IpdRepository.bisaDiubah(ada.statusLama)) {
                Toast.makeText(this, "Inspeksi ini tidak bisa diubah lagi (sudah disetujui asisten)", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            draf = ada;
        } else {
            draf.tanggal = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        }
        if (kode.isEmpty() || daftarDivisi.isEmpty()) {
            Toast.makeText(this, "Master IPD belum ada di HP. Sambungkan ke server lalu sinkronkan dulu.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        for (IpdHasil.DivisiPilihan x : daftarDivisi) if (x.divisiId == draf.divisiId) selDivisi = x;
        if (selDivisi != null) {
            for (IpdHasil.BlokPilihan x : daftarBlok) if (x.blokId == draf.blokId) selBlok = x;
        }
        for (IpdHasil.PemanenPilihan x : daftarPemanen) if (x.pemanenId == draf.pemanenId) selPemanen = x;
        for (IpdHasil.OrangPilihan x : daftarMandor) {
            if (draf.mandorUserId != null && x.userId == draf.mandorUserId) selMandor = x;
        }
        bangunLayar();
        kepalaLengkap(false); // mengisi teks info pemanen bila inspeksi dimuat dari draf
        inisialisasiSlot();
        tampilkanLangkah(draf.pokok.isEmpty() ? 0 : 2);
    }

    // ---- Kerangka layar ----------------------------------------------------------------------------

    private void bangunLayar() {
        final Context c = this;
        LinearLayout akar = new LinearLayout(c);
        akar.setOrientation(LinearLayout.VERTICAL);
        akar.setBackgroundColor(MaterialColors.getColor(akar, android.R.attr.colorBackground));

        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(IpdUi.dp(c, 8), IpdUi.dp(c, 4), IpdUi.dp(c, 16), IpdUi.dp(c, 4));
        MaterialButton kembali = IpdUi.tombol(c, "‹", false);
        kembali.setMinimumHeight(IpdUi.dp(c, 48));
        kembali.setMinWidth(IpdUi.dp(c, 48));
        kembali.setPadding(0, 0, 0, 0);
        kembali.setTextSize(24);
        kembali.setOnClickListener(v -> keluar());
        bar.addView(kembali, new LinearLayout.LayoutParams(IpdUi.dp(c, 52), IpdUi.dp(c, 48)));
        TextView judul = IpdUi.judul(c, draf.statusLama == null ? "Inspeksi baru" : "Ubah inspeksi", 18);
        LinearLayout.LayoutParams jlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        jlp.setMarginStart(IpdUi.dp(c, 8));
        bar.addView(judul, jlp);
        akar.addView(bar);

        LinearLayout langkahBar = new LinearLayout(c);
        langkahBar.setOrientation(LinearLayout.HORIZONTAL);
        langkahBar.setPadding(IpdUi.dp(c, 16), 0, IpdUi.dp(c, 16), IpdUi.dp(c, 8));
        String[] nama = {"1  Kepala", "2  Pokok", "3  Kirim"};
        for (int i = 0; i < 3; i++) {
            final int n = i;
            TextView t = IpdUi.teks(c, nama[i], 14, true, com.google.android.material.R.attr.colorOnSurface);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(IpdUi.dp(c, 44));
            t.setOnClickListener(v -> pindahLangkah(n));
            langkahChip[i] = t;
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            p.setMargins(IpdUi.dp(c, 3), 0, IpdUi.dp(c, 3), 0);
            langkahBar.addView(t, p);
        }
        akar.addView(langkahBar);

        wadah = new FrameLayout(c);
        akar.addView(wadah, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        halaman[0] = bangunKepala();
        halaman[1] = bangunPokok();
        halaman[2] = bangunKirim();
        for (View h : halaman) wadah.addView(h, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(akar);
        ViewCompat.setOnApplyWindowInsetsListener(akar, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(akar);
    }

    private void tampilkanLangkah(int n) {
        langkah = n;
        for (int i = 0; i < 3; i++) {
            halaman[i].setVisibility(i == n ? View.VISIBLE : View.GONE);
            boolean aktif = i == n;
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setCornerRadius(IpdUi.dp(this, 14));
            g.setColor(ContextCompat.getColor(this, aktif ? R.color.brand_primary : R.color.brand_primary_container));
            langkahChip[i].setBackground(g);
            langkahChip[i].setTextColor(aktif ? 0xFFFFFFFF : ContextCompat.getColor(this, R.color.brand_on_primary_container));
        }
        if (n == 2) segarkanRingkasan();
        if (n == 1) {
            segarkanFoto();
            mulaiJejak();
            main.removeCallbacks(pantauJejak);
            main.post(pantauJejak);
        } else {
            main.removeCallbacks(pantauJejak);
        }
    }

    // ---- Jalur GPS --------------------------------------------------------------------------------------

    /** Memulai perekam jalur untuk inspeksi ini bila diizinkan: minta izin lokasi dulu bila belum ada. */
    private void mulaiJejak() {
        if (!jejakDitentukan) {
            jejakDitentukan = true;
            rekamJejak = draf.statusLama == null || IpdRepository.DRAFT.equals(draf.statusLama);
            if (swJejak != null) swJejak.setChecked(rekamJejak);
        }
        if (!rekamJejak) return;
        if (draf.ipdId == null) draf.ipdId = UUID.randomUUID().toString();
        if (!com.example.fai26.lokasi.PencatatLokasi.adaIzin(this)) {
            mintaIzinJejak.launch(android.os.Build.VERSION.SDK_INT >= 33
                    ? new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS}
                    : new String[]{Manifest.permission.ACCESS_FINE_LOCATION});
            return;
        }
        android.location.LocationManager lm = (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm != null && !lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
            Toast.makeText(this, "GPS di HP mati: nyalakan GPS supaya jalur terekam", Toast.LENGTH_LONG).show();
        }
        if (!draf.ipdId.equals(com.example.fai26.lokasi.JejakService.ipdAktif())) {
            com.example.fai26.lokasi.JejakService.mulai(this, draf.ipdId);
        }
    }

    private void perbaruiTeksJejak() {
        if (txtJejak == null) return;
        if (!rekamJejak) {
            txtJejak.setText("Jalur GPS tidak direkam");
            return;
        }
        boolean aktif = draf.ipdId != null && draf.ipdId.equals(com.example.fai26.lokasi.JejakService.ipdAktif());
        if (!aktif) {
            txtJejak.setText("Jalur GPS belum aktif");
            return;
        }
        int n = com.example.fai26.lokasi.JejakService.jumlahSesi();
        float ak = com.example.fai26.lokasi.JejakService.akurasiTerakhir();
        txtJejak.setText(n == 0 ? "Jalur GPS aktif • menunggu sinyal GPS…"
                : "Jalur GPS aktif • " + n + " titik" + (ak > 0 ? String.format(java.util.Locale.US, " • akurasi ±%.0f m", ak) : ""));
    }

    /** Inspeksi baru yang dibuang tanpa disimpan: titik jalurnya ikut dibuang (yang sudah tersimpan sebagai draf tidak disentuh). */
    private void buangJejakBaru() {
        final String id = draf.ipdId;
        if (id == null) return;
        io.execute(() -> {
            if (dao.header(id) == null) dao.hapusJejak(id);
        });
    }

    private void pindahLangkah(int n) {
        if (n == langkah) return;
        if (n > 0 && !kepalaLengkap(true)) return;
        if (langkah == 0 && n > 0) {
            periksaInspeksiUlang(() -> tampilkanLangkah(n));
            return;
        }
        if (langkah == 1) simpanSlotBila();
        tampilkanLangkah(n);
    }

    // ---- Langkah 1: kepala -------------------------------------------------------------------------

    private LinearLayout kolomGulir(ScrollView[] gulir) {
        ScrollView g = new ScrollView(this);
        g.setFillViewport(false);
        LinearLayout k = new LinearLayout(this);
        k.setOrientation(LinearLayout.VERTICAL);
        k.setPadding(IpdUi.dp(this, 16), IpdUi.dp(this, 4), IpdUi.dp(this, 16), IpdUi.dp(this, 24));
        g.addView(k, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        gulir[0] = g;
        return k;
    }

    private <T> MaterialAutoCompleteTextView dropdown(LinearLayout ke, String label, List<T> data, T terpilih, java.util.function.Consumer<T> saatPilih) {
        return dropdown(ke, label, data, terpilih, saatPilih, false);
    }

    /** bisaKetik true: daftar panjang yang dicari dengan mengetik (pemanen); false: hanya dipilih dari daftar tanpa papan ketik. */
    private <T> MaterialAutoCompleteTextView dropdown(LinearLayout ke, String label, List<T> data, T terpilih, java.util.function.Consumer<T> saatPilih, boolean bisaKetik) {
        TextInputLayout til = new TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle);
        til.setHint(label);
        MaterialAutoCompleteTextView ac = new MaterialAutoCompleteTextView(til.getContext());
        ac.setInputType(bisaKetik ? (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) : InputType.TYPE_NULL);
        ac.setTextSize(17);
        ac.setThreshold(1);
        ac.setAdapter(new PencarianAdapter<>(this, data));
        if (terpilih != null) ac.setText(String.valueOf(terpilih), false);
        ac.setOnClickListener(v -> ac.showDropDown());
        // Daftar yang bisa diketik: bila teks diubah tanpa memilih item, pilihan lama dianggap batal (tidak boleh tertinggal diam-diam).
        final Object[] saatIni = {terpilih};
        if (bisaKetik) {
            ac.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void afterTextChanged(android.text.Editable s) {
                    if (saatIni[0] != null && !s.toString().equals(String.valueOf(saatIni[0]))) {
                        saatIni[0] = null;
                        saatPilih.accept(null);
                        kotor = true;
                    }
                }
            });
        }
        ac.setOnItemClickListener((p, v, pos, id) -> {
            @SuppressWarnings("unchecked") T item = (T) p.getItemAtPosition(pos);
            saatIni[0] = item;
            saatPilih.accept(item);
            kotor = true;
            ac.clearFocus();
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(ac.getWindowToken(), 0);
        });
        til.addView(ac, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ke.addView(til, IpdUi.lebarPenuh(this, 4, 8));
        return ac;
    }

    private View bangunKepala() {
        final Context c = this;
        ScrollView[] g = new ScrollView[1];
        LinearLayout k = kolomGulir(g);

        k.addView(IpdUi.redup(c, "Satu catatan untuk satu pemanen (satu ancak). Pindah ancak berarti catatan baru.", 14), IpdUi.lebarPenuh(c, 0, 8));

        TextView etiket = IpdUi.judul(c, "Tanggal inspeksi", 14);
        k.addView(etiket, IpdUi.lebarPenuh(c, 4, 4));
        btnTanggal = IpdUi.teks(c, IpdUi.tanggal(draf.tanggal), 17, true, com.google.android.material.R.attr.colorOnSurface);
        btnTanggal.setMinHeight(IpdUi.dp(c, 56));
        btnTanggal.setGravity(Gravity.CENTER_VERTICAL);
        btnTanggal.setPadding(IpdUi.dp(c, 14), 0, IpdUi.dp(c, 14), 0);
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setStroke(IpdUi.dp(c, 1), MaterialColors.getColor(btnTanggal, com.google.android.material.R.attr.colorOutline));
        gd.setCornerRadius(IpdUi.dp(c, 12));
        btnTanggal.setBackground(gd);
        btnTanggal.setOnClickListener(v -> pilihTanggal());
        k.addView(btnTanggal, IpdUi.lebarPenuh(c, 0, 12));

        dropdown(k, "Divisi", daftarDivisi, selDivisi, d -> {
            selDivisi = d;
            selBlok = null;
            ddBlok.setText("", false);
            tampilkanAsisten();
            io.execute(() -> {
                final List<IpdHasil.BlokPilihan> blok = dao.blokPilihan(d.divisiId);
                main.post(() -> {
                    daftarBlok = blok;
                    ddBlok.setAdapter(new PencarianAdapter<>(c, blok));
                });
            });
        });
        txtAsisten = IpdUi.redup(c, "", 14);
        txtAsisten.setPadding(IpdUi.dp(c, 4), 0, 0, IpdUi.dp(c, 8));
        k.addView(txtAsisten);
        tampilkanAsisten();

        ddBlok = dropdown(k, "Blok (ketik untuk mencari)", daftarBlok, selBlok, b -> selBlok = b, true);
        dropdown(k, "Pemanen (ketik untuk mencari)", daftarPemanen, selPemanen, p -> selPemanen = p, true);

        TextInputLayout tilAncak = new TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        tilAncak.setHint("Nomor ancak (opsional)");
        edAncak = new TextInputEditText(tilAncak.getContext());
        edAncak.setTextSize(17);
        edAncak.setSingleLine(true);
        edAncak.setText(draf.ancakNo == null ? "" : draf.ancakNo);
        tilAncak.addView(edAncak);
        k.addView(tilAncak, IpdUi.lebarPenuh(c, 4, 8));

        dropdown(k, "Kemandoran (ketik untuk mencari)", daftarMandor, selMandor, m -> selMandor = m, true);
        if (daftarMandor.isEmpty()) {
            k.addView(IpdUi.teks(c, "Daftar mandor belum ada di HP. Sinkronkan dulu saat ada sinyal.", 13, true, androidx.appcompat.R.attr.colorError));
        }

        MaterialButton lanjut = IpdUi.tombol(c, "Lanjut: catat pokok", true);
        lanjut.setOnClickListener(v -> pindahLangkah(1));
        k.addView(lanjut, IpdUi.lebarPenuh(c, 16, 0));
        return g[0];
    }

    private void tampilkanAsisten() {
        if (selDivisi == null) txtAsisten.setText("Asisten terisi otomatis setelah divisi dipilih.");
        else if (selDivisi.asistenNama == null) txtAsisten.setText("Asisten divisi ini belum diatur (admin mengaturnya di web). Inspeksi tetap bisa dikirim.");
        else txtAsisten.setText("Asisten penyetuju: " + selDivisi.asistenNama + " (otomatis)");
    }

    private void pilihTanggal() {
        Calendar cal = Calendar.getInstance();
        try {
            cal.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(draf.tanggal));
        } catch (Exception ignored) {
            // pakai hari ini
        }
        new DatePickerDialog(this, (v, y, m, d) -> {
            draf.tanggal = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d);
            btnTanggal.setText(IpdUi.tanggal(draf.tanggal));
            kotor = true;
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    /** Memeriksa kepala catatan dan menyalinnya ke draf. */
    private boolean kepalaLengkap(boolean pesan) {
        String kurang = null;
        if (selDivisi == null) kurang = "Pilih divisi dulu";
        else if (selBlok == null) kurang = "Pilih blok dulu";
        else if (selPemanen == null) kurang = "Pilih pemanen dulu";
        else if (selMandor == null) kurang = "Pilih kemandoran (mandor) dulu";
        if (kurang != null) {
            if (pesan) Toast.makeText(this, kurang, Toast.LENGTH_SHORT).show();
            return false;
        }
        draf.divisiId = selDivisi.divisiId;
        draf.blokId = selBlok.blokId;
        draf.pemanenId = selPemanen.pemanenId;
        draf.mandorUserId = selMandor.userId;
        draf.ancakNo = edAncak.getText().toString().trim();
        txtInfo.setText(selPemanen.nama + " • Blok " + selBlok.kode + " • " + selDivisi.nama);
        return true;
    }

    /**
     * Peringatan inspeksi ulang (bukan larangan): pemanen + blok yang sama pernah diinspeksi dalam 7 hari terakhir. Memakai data
     * server bila ada sinyal, dan data HP sendiri sebagai cadangan.
     */
    private void periksaInspeksiUlang(Runnable lanjut) {
        final String kunci = selPemanen.pemanenId + "|" + selBlok.blokId + "|" + draf.tanggal;
        if (kunci.equals(peringatanKunci)) {
            lanjut.run();
            return;
        }
        final String idIni = draf.ipdId == null ? "" : draf.ipdId;
        final Context app = getApplicationContext();
        final long hrmb = selPemanen.hrmbCode;
        final long oid = selBlok.erpOid;
        final long pemanenId = selPemanen.pemanenId;
        final long blokId = selBlok.blokId;
        final String tanggal = draf.tanggal;
        io.execute(() -> {
            List<String> baris = new ArrayList<>();
            try {
                PengaturanSync pref = new PengaturanSync(app);
                PersetujuanServer s = new PersetujuanServer(new HttpServerApi(pref.serverUrl()), pref);
                for (ServerApi.IpdTerakhirItem t : s.inspeksiTerakhir(hrmb, oid, tanggal, idIni.isEmpty() ? null : idIni)) {
                    baris.add(IpdUi.tanggal(t.tanggalInspeksi) + " oleh " + t.inspekturNama + " (" + IpdUi.labelStatus(statusHp(t.status)) + ")");
                }
            } catch (Exception e) {
                // offline: pakai data di HP
                String sampai = tanggal;
                String dari = tambahHari(tanggal, -7);
                for (IpdHasil.Terakhir t : dao.terakhirLokal(pemanenId, blokId, dari, sampai, idIni)) {
                    baris.add(IpdUi.tanggal(t.tanggalInspeksi) + " oleh " + t.inspektur + " (" + IpdUi.labelStatus(t.status) + ")");
                }
            }
            main.post(() -> {
                if (isFinishing()) return;
                peringatanKunci = kunci;
                if (baris.isEmpty()) {
                    lanjut.run();
                    return;
                }
                StringBuilder sb = new StringBuilder();
                sb.append("Pemanen ").append(selPemanen.nama).append(" di blok ").append(selBlok.kode).append(" sudah diinspeksi dalam 7 hari terakhir:\n");
                for (String b : baris) sb.append("\n• ").append(b);
                sb.append("\n\nInspeksi ulang tetap boleh. Lanjutkan?");
                new MaterialAlertDialogBuilder(this).setTitle("Sudah pernah diinspeksi").setMessage(sb)
                        .setNegativeButton("Batal", (d, w) -> peringatanKunci = "")
                        .setPositiveButton("Lanjutkan", (d, w) -> lanjut.run()).show();
            });
        });
    }

    private static String statusHp(String statusServer) {
        if ("DISETUJUI".equals(statusServer)) return "DISETUJUI";
        if ("DITOLAK".equals(statusServer)) return "DITOLAK";
        return "MENUNGGU_SETUJU";
    }

    private static String tambahHari(String yyyyMmDd, int hari) {
        try {
            Calendar c = Calendar.getInstance();
            c.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(yyyyMmDd));
            c.add(Calendar.DAY_OF_MONTH, hari);
            return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
        } catch (Exception e) {
            return yyyyMmDd;
        }
    }

    // ---- Langkah 2: pokok per pokok (satu atau dua baris sekali jalan) -----------------------------

    private LinearLayout stepper(EditText nilai, int min, int maks, Runnable saatUbah) {
        final Context c = this;
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        MaterialButton kurang = IpdUi.tombol(c, "−", false);
        MaterialButton tambah = IpdUi.tombol(c, "+", false);
        for (MaterialButton x : new MaterialButton[]{kurang, tambah}) {
            x.setMinWidth(0);
            x.setMinimumWidth(0);
            x.setPadding(0, 0, 0, 0);
        }
        nilai.setInputType(InputType.TYPE_CLASS_NUMBER);
        nilai.setGravity(Gravity.CENTER);
        nilai.setTextSize(22);
        nilai.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(c, R.font.outfit_semibold));
        nilai.setSelectAllOnFocus(true);
        l.addView(kurang, new LinearLayout.LayoutParams(IpdUi.dp(c, 48), IpdUi.dp(c, 52)));
        l.addView(nilai, new LinearLayout.LayoutParams(0, IpdUi.dp(c, 52), 1f));
        l.addView(tambah, new LinearLayout.LayoutParams(IpdUi.dp(c, 48), IpdUi.dp(c, 52)));
        kurang.setOnClickListener(v -> {
            nilai.setText(String.valueOf(Math.max(min, angka(nilai, min) - 1)));
            saatUbah.run();
        });
        tambah.setOnClickListener(v -> {
            nilai.setText(String.valueOf(Math.min(maks, angka(nilai, min) + 1)));
            saatUbah.run();
        });
        nilai.setOnEditorActionListener((v, a, e) -> {
            saatUbah.run();
            return false;
        });
        return l;
    }

    private static int angka(EditText e, int bawaan) {
        try {
            return Integer.parseInt(e.getText().toString().trim());
        } catch (NumberFormatException ex) {
            return bawaan;
        }
    }

    private View bangunPokok() {
        final Context c = this;
        LinearLayout halamanKolom = new LinearLayout(c);
        halamanKolom.setOrientation(LinearLayout.VERTICAL);

        ScrollView[] g = new ScrollView[1];
        LinearLayout k = kolomGulir(g);
        halamanKolom.addView(g[0], new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        txtInfo = IpdUi.judul(c, "", 15);
        k.addView(txtInfo, IpdUi.lebarPenuh(c, 0, 8));

        // Jalur GPS: direkam tiap 3 meter selama mencatat pokok; terlihat jelas aktif atau tidaknya.
        swJejak = new com.google.android.material.materialswitch.MaterialSwitch(c);
        swJejak.setText("Rekam jalur GPS");
        swJejak.setTextSize(15);
        swJejak.setChecked(rekamJejak);
        swJejak.setOnCheckedChangeListener((v, ok) -> {
            rekamJejak = ok;
            jejakDitentukan = true;
            if (ok) mulaiJejak();
            else com.example.fai26.lokasi.JejakService.berhenti(this);
            perbaruiTeksJejak();
        });
        k.addView(swJejak, IpdUi.lebarPenuh(c, 0, 0));
        txtJejak = IpdUi.redup(c, "", 12);
        txtJejak.setPadding(IpdUi.dp(c, 2), 0, 0, IpdUi.dp(c, 8));
        k.addView(txtJejak);

        LinearLayout[] kartuIsi = new LinearLayout[1];
        MaterialCardView kartu = IpdUi.kartu(c, kartuIsi);
        LinearLayout kl = kartuIsi[0];

        // Satu lorong biasanya memeriksa dua baris sekaligus (mis. baris 120 dan 121): satu langkah mencatat pokok yang sama di keduanya.
        swDuaBaris = new com.google.android.material.materialswitch.MaterialSwitch(c);
        swDuaBaris.setText("Periksa 2 baris sekaligus");
        swDuaBaris.setTextSize(16);
        swDuaBaris.setChecked(true);
        swDuaBaris.setOnCheckedChangeListener((v, ok) -> {
            kolomBaris2.setVisibility(ok ? View.VISIBLE : View.GONE);
            tabSisi.setVisibility(ok ? View.VISIBLE : View.GONE);
            if (!ok) simpanUiKeSisi();
            sisi = 0;
            muatSlot();
        });
        kl.addView(swDuaBaris);

        LinearLayout baris3 = new LinearLayout(c);
        baris3.setOrientation(LinearLayout.HORIZONTAL);
        baris3.setPadding(0, IpdUi.dp(c, 8), 0, 0);
        edBaris = new EditText(c);
        edBaris2 = new EditText(c);
        edPokok = new EditText(c);
        LinearLayout colA = new LinearLayout(c);
        colA.setOrientation(LinearLayout.VERTICAL);
        colA.addView(IpdUi.redup(c, "Baris", 13));
        colA.addView(stepper(edBaris, 1, 9999, this::barisBerubah));
        kolomBaris2 = new LinearLayout(c);
        kolomBaris2.setOrientation(LinearLayout.VERTICAL);
        kolomBaris2.addView(IpdUi.redup(c, "Baris ke-2", 13));
        kolomBaris2.addView(stepper(edBaris2, 1, 9999, this::baris2Berubah));
        LinearLayout colP = new LinearLayout(c);
        colP.setOrientation(LinearLayout.VERTICAL);
        colP.addView(IpdUi.redup(c, "No. pokok", 13));
        colP.addView(stepper(edPokok, 1, 9999, this::muatSlot));
        baris3.addView(colA, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l2.setMarginStart(IpdUi.dp(c, 12));
        baris3.addView(kolomBaris2, l2);
        kl.addView(baris3);
        // Nomor pokok di baris sendiri supaya tiga pengatur angka tidak berdesakan.
        colP.setPadding(0, IpdUi.dp(c, 10), 0, 0);
        kl.addView(colP);
        txtPosisi = IpdUi.redup(c, "", 13);
        txtPosisi.setPadding(0, IpdUi.dp(c, 8), 0, 0);
        kl.addView(txtPosisi);
        // Foto diambil langsung saat jalan memeriksa; keterangannya otomatis menyebut baris dan pokok yang sedang dicatat.
        btnFotoPokok = IpdUi.tombol(c, "Ambil foto pokok ini", false);
        btnFotoPokok.setOnClickListener(v -> tambahFotoPokok());
        kl.addView(btnFotoPokok, IpdUi.lebarPenuh(c, 8, 0));
        k.addView(kartu);

        // Pilihan sisi: temuan dicatat per baris; baris yang tidak diberi pilihan dianggap bersih.
        tabSisi = new LinearLayout(c);
        tabSisi.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 2; i++) {
            final int n = i;
            MaterialButton t = IpdUi.tombol(c, "", false);
            t.setOnClickListener(v -> gantiSisi(n));
            btnSisi[i] = t;
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            p.setMargins(IpdUi.dp(c, 2), 0, IpdUi.dp(c, 2), 0);
            tabSisi.addView(t, p);
        }
        k.addView(tabSisi, IpdUi.lebarPenuh(c, 4, 8));

        // Kode dikelompokkan: loses (buah dan brondolan) dan temuan lain (pelepah, tidak memengaruhi produksi).
        boolean judulLoses = false;
        boolean judulLain = false;
        for (KodeTemuan kd : kode) {
            boolean lain = "PELEPAH".equals(kd.kelompok);
            if (!lain && !judulLoses) {
                k.addView(IpdUi.judul(c, "Loses (memengaruhi hasil panen)", 14), IpdUi.lebarPenuh(c, 4, 6));
                judulLoses = true;
            }
            if (lain && !judulLain) {
                TextView j = IpdUi.judul(c, "Temuan lain (tidak memengaruhi hasil)", 14);
                k.addView(j, IpdUi.lebarPenuh(c, 12, 6));
                judulLain = true;
            }
            k.addView(buatBarisKode(kd));
        }

        txtRekapMini = IpdUi.redup(c, "", 14);
        k.addView(txtRekapMini, IpdUi.lebarPenuh(c, 8, 0));

        // Bilah tombol tetap di bawah.
        LinearLayout bawah = new LinearLayout(c);
        bawah.setOrientation(LinearLayout.VERTICAL);
        bawah.setPadding(IpdUi.dp(c, 16), IpdUi.dp(c, 8), IpdUi.dp(c, 16), IpdUi.dp(c, 8));
        bawah.setBackgroundColor(MaterialColors.getColor(bawah, com.google.android.material.R.attr.colorSurface));
        btnLanjut = IpdUi.tombol(c, "BERSIH, pokok berikutnya", true);
        btnLanjut.setMinimumHeight(IpdUi.dp(c, 64));
        btnLanjut.setTextSize(18);
        btnLanjut.setOnClickListener(v -> simpanSlot(true));
        bawah.addView(btnLanjut, IpdUi.lebarPenuh(c, 0, 8));
        LinearLayout baris2 = new LinearLayout(c);
        baris2.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton sebelum = IpdUi.tombol(c, "‹ Mundur", false);
        MaterialButton hapus = IpdUi.tombol(c, "Hapus", false);
        MaterialButton selesai = IpdUi.tombol(c, "Selesai ›", false);
        sebelum.setOnClickListener(v -> sebelumnya());
        hapus.setOnClickListener(v -> hapusPokok());
        selesai.setOnClickListener(v -> {
            simpanSlotBila();
            if (draf.pokok.isEmpty()) {
                Toast.makeText(this, "Catat minimal satu pokok", Toast.LENGTH_SHORT).show();
                return;
            }
            tampilkanLangkah(2);
        });
        for (MaterialButton b : new MaterialButton[]{sebelum, hapus, selesai}) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            p.setMargins(IpdUi.dp(c, 2), 0, IpdUi.dp(c, 2), 0);
            baris2.addView(b, p);
        }
        bawah.addView(baris2);
        halamanKolom.addView(bawah, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return halamanKolom;
    }

    private View buatBarisKode(KodeTemuan kd) {
        final Context c = this;
        final BarisKode b = new BarisKode();
        LinearLayout baris = new LinearLayout(c);
        baris.setOrientation(LinearLayout.HORIZONTAL);
        baris.setGravity(Gravity.CENTER_VERTICAL);
        b.tombol = IpdUi.tombol(c, kd.kode + "   " + kd.nama, false);
        b.tombol.setCheckable(true);
        b.tombol.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.tombol.setTextSize(15);
        b.tombol.setMinimumHeight(IpdUi.dp(c, 52));
        b.tombol.addOnCheckedChangeListener((t, ok) -> {
            b.pilih = ok;
            b.stepper.setVisibility(ok ? View.VISIBLE : View.GONE);
            t.setBackgroundTintList(ColorStateList.valueOf(ok ? ContextCompat.getColor(c, R.color.brand_primary_container) : 0x00000000));
            t.setStrokeWidth(IpdUi.dp(c, ok ? 2 : 1));
            if (!ok) b.jumlah = 1;
            b.txtJumlah.setText(String.valueOf(b.jumlah));
            perbaruiTombolLanjut();
        });
        baris.addView(b.tombol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        b.stepper = new LinearLayout(c);
        b.stepper.setOrientation(LinearLayout.HORIZONTAL);
        b.stepper.setGravity(Gravity.CENTER_VERTICAL);
        b.stepper.setVisibility(View.GONE);
        MaterialButton kurang = IpdUi.tombol(c, "−", false);
        MaterialButton tambah = IpdUi.tombol(c, "+", false);
        for (MaterialButton x : new MaterialButton[]{kurang, tambah}) {
            x.setMinWidth(0);
            x.setMinimumWidth(0);
            x.setPadding(0, 0, 0, 0);
            x.setMinimumHeight(IpdUi.dp(c, 48));
        }
        b.txtJumlah = IpdUi.judul(c, "1", 18);
        b.txtJumlah.setGravity(Gravity.CENTER);
        kurang.setOnClickListener(v -> {
            b.jumlah = Math.max(1, b.jumlah - 1);
            b.txtJumlah.setText(String.valueOf(b.jumlah));
        });
        tambah.setOnClickListener(v -> {
            b.jumlah = Math.min(MAKS_JUMLAH, b.jumlah + 1);
            b.txtJumlah.setText(String.valueOf(b.jumlah));
        });
        b.stepper.addView(kurang, new LinearLayout.LayoutParams(IpdUi.dp(c, 44), IpdUi.dp(c, 48)));
        b.stepper.addView(b.txtJumlah, new LinearLayout.LayoutParams(IpdUi.dp(c, 40), ViewGroup.LayoutParams.WRAP_CONTENT));
        b.stepper.addView(tambah, new LinearLayout.LayoutParams(IpdUi.dp(c, 44), IpdUi.dp(c, 48)));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.setMarginStart(IpdUi.dp(c, 6));
        baris.addView(b.stepper, slp);
        baris.setLayoutParams(IpdUi.lebarPenuh(c, 0, 6));
        barisKode.put(kd.kode, b);
        return baris;
    }

    /** Batas jumlah per temuan (sama dengan server). */
    private static final int MAKS_JUMLAH = 9999;

    private boolean dua() {
        return swDuaBaris != null && swDuaBaris.isChecked();
    }

    private boolean adaPilihanUi() {
        for (BarisKode b : barisKode.values()) if (b.pilih) return true;
        return false;
    }

    private void perbaruiTombolLanjut() {
        if (btnLanjut == null) return;
        boolean ada = adaPilihanUi() || (dua() && !sisiTemuan[1 - sisi].isEmpty());
        btnLanjut.setText(ada ? "SIMPAN TEMUAN, pokok berikutnya" : "BERSIH, pokok berikutnya");
        perbaruiLabelSisi();
    }

    private void perbaruiLabelSisi() {
        if (btnSisi[0] == null) return;
        int[] n = {sisiTemuan[0].size(), sisiTemuan[1].size()};
        int aktif = 0;
        for (BarisKode b : barisKode.values()) if (b.pilih) aktif++;
        n[sisi] = aktif; // sisi aktif dibaca langsung dari layar
        int[] nomor = {angka(edBaris, 1), angka(edBaris2, 2)};
        for (int i = 0; i < 2; i++) {
            btnSisi[i].setText("Baris " + nomor[i] + (n[i] > 0 ? "  •  " + n[i] + " temuan" : "  •  bersih"));
            boolean a = i == sisi;
            btnSisi[i].setBackgroundTintList(ColorStateList.valueOf(a ? ContextCompat.getColor(this, R.color.brand_primary) : 0x00000000));
            btnSisi[i].setTextColor(a ? 0xFFFFFFFF : ContextCompat.getColor(this, R.color.brand_primary));
        }
    }

    private void setCentang(Map<String, Integer> temuan) {
        for (Map.Entry<String, BarisKode> e : barisKode.entrySet()) {
            BarisKode b = e.getValue();
            Integer j = temuan == null ? null : temuan.get(e.getKey());
            b.jumlah = j == null ? 1 : j;
            b.tombol.setChecked(j != null);
            b.txtJumlah.setText(String.valueOf(b.jumlah));
        }
        perbaruiTombolLanjut();
    }

    /** Menyalin pilihan di layar ke penampung sisi yang sedang aktif. */
    private void simpanUiKeSisi() {
        sisiTemuan[sisi].clear();
        for (Map.Entry<String, BarisKode> e : barisKode.entrySet()) if (e.getValue().pilih) sisiTemuan[sisi].put(e.getKey(), e.getValue().jumlah);
    }

    private void gantiSisi(int n) {
        if (n == sisi) return;
        simpanUiKeSisi();
        sisi = n;
        setCentang(sisiTemuan[n]);
    }

    private IpdDraf.Pokok cariPokok(int baris, int pokok) {
        for (IpdDraf.Pokok p : draf.pokok) if (p.baris == baris && p.pokok == pokok) return p;
        return null;
    }

    private int pokokBerikut(int... baris) {
        int maks = 0;
        for (IpdDraf.Pokok p : draf.pokok) for (int b : baris) if (p.baris == b) maks = Math.max(maks, p.pokok);
        return maks + 1;
    }

    private int[] barisAktif() {
        return dua() ? new int[]{angka(edBaris, 1), angka(edBaris2, 2)} : new int[]{angka(edBaris, 1)};
    }

    /** Memuat isian slot (baris + nomor pokok) dari catatan yang sudah ada; yang belum ada dianggap baru. */
    private void muatSlot() {
        int pokok = Math.max(1, angka(edPokok, 1));
        int[] baris = barisAktif();
        sisiTemuan[0].clear();
        sisiTemuan[1].clear();
        boolean ada = false;
        for (int i = 0; i < baris.length; i++) {
            IpdDraf.Pokok p = cariPokok(baris[i], pokok);
            if (p != null) {
                ada = true;
                sisiTemuan[i].putAll(p.temuan);
            }
        }
        if (sisi >= baris.length) sisi = 0;
        setCentang(sisiTemuan[sisi]);
        txtPosisi.setText(ada ? "Pokok ini sudah dicatat: mengubah isinya" : "Pokok baru");
        perbaruiRekapMini();
    }

    /** Mengisi baris dan nomor pokok awal: lanjutan dari catatan terakhir, atau baris 1 dan 2 bila masih kosong. */
    private void inisialisasiSlot() {
        int a = draf.pokok.isEmpty() ? 1 : draf.pokok.get(draf.pokok.size() - 1).baris;
        // Bila baris terakhir adalah yang kedua dari sepasang, mulai dari pasangannya.
        if (!draf.pokok.isEmpty() && a > 1 && cariPokok(a - 1, draf.pokok.get(draf.pokok.size() - 1).pokok) != null) a = a - 1;
        edBaris.setText(String.valueOf(a));
        edBaris2.setText(String.valueOf(a + 1));
        edPokok.setText(String.valueOf(pokokBerikut(a, a + 1)));
        sisi = 0;
        muatSlot();
    }

    /** Baris diubah: baris ke-2 mengikuti (baris + 1) dan pokok kembali ke pokok berikutnya yang belum dicatat di lorong itu. */
    private void barisBerubah() {
        int a = angka(edBaris, 1);
        edBaris2.setText(String.valueOf(a + 1));
        edPokok.setText(String.valueOf(dua() ? pokokBerikut(a, a + 1) : pokokBerikut(a)));
        sisi = 0;
        muatSlot();
    }

    /** Baris ke-2 diubah sendiri (pasangan bukan baris + 1): pokok menyesuaikan dengan yang sudah dicatat di kedua baris. */
    private void baris2Berubah() {
        edPokok.setText(String.valueOf(pokokBerikut(angka(edBaris, 1), angka(edBaris2, 2))));
        sisi = 0;
        muatSlot();
    }

    private void perbaruiRekapMini() {
        IpdRekap r = IpdRekap.hitung(draf.pokok, jenisKode());
        txtRekapMini.setText("Sudah dicatat: " + r.pokokDiperiksa + " pokok • loses " + IpdUi.persen(r.persenLoses()) + " • temuan lain " + IpdUi.persen(r.persenTemuanLain()));
    }

    /** kode temuan -> jenis (LOSES atau TEMUAN), menurut kelompoknya. */
    private Map<String, String> jenisKode() {
        Map<String, String> m = new HashMap<>();
        for (KodeTemuan k : kode) m.put(k.kode, "PELEPAH".equals(k.kelompok) ? "TEMUAN" : "LOSES");
        return m;
    }

    /** Menyimpan pokok pada slot saat ini (kedua baris bila 2 baris). @return true bila berhasil. */
    private boolean simpanSlot(boolean maju) {
        simpanUiKeSisi();
        int pokok = angka(edPokok, 0);
        int[] baris = barisAktif();
        if (pokok < 1 || baris[0] < 1 || (baris.length > 1 && baris[1] < 1)) {
            Toast.makeText(this, "Nomor baris dan pokok minimal 1", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (baris.length > 1 && baris[0] == baris[1]) {
            Toast.makeText(this, "Kedua baris tidak boleh sama", Toast.LENGTH_SHORT).show();
            return false;
        }
        for (int i = 0; i < baris.length; i++) {
            IpdDraf.Pokok p = cariPokok(baris[i], pokok);
            if (p == null) {
                p = new IpdDraf.Pokok(baris[i], pokok);
                draf.pokok.add(p);
            }
            p.temuan.clear();
            p.temuan.putAll(sisiTemuan[i]);
        }
        kotor = true;
        if (maju) {
            edPokok.setText(String.valueOf(pokok + 1));
            sisi = 0;
            muatSlot();
            autoSimpan();
        } else {
            perbaruiRekapMini();
        }
        return true;
    }

    /** Menyimpan slot saat pindah langkah, hanya bila ada isinya atau sudah pernah dicatat. */
    private void simpanSlotBila() {
        simpanUiKeSisi();
        boolean isi = !sisiTemuan[0].isEmpty() || !sisiTemuan[1].isEmpty();
        int pokok = angka(edPokok, 0);
        boolean sudah = false;
        for (int b : barisAktif()) if (cariPokok(b, pokok) != null) sudah = true;
        if (isi || sudah) simpanSlot(false);
    }

    private void sebelumnya() {
        int pokok = angka(edPokok, 1);
        if (pokok <= 1) {
            Toast.makeText(this, "Ini pokok pertama", Toast.LENGTH_SHORT).show();
            return;
        }
        simpanSlotBila();
        edPokok.setText(String.valueOf(pokok - 1));
        sisi = 0;
        muatSlot();
    }

    private void hapusPokok() {
        int pokok = angka(edPokok, 1);
        boolean ada = false;
        for (int b : barisAktif()) {
            IpdDraf.Pokok p = cariPokok(b, pokok);
            if (p != null) {
                draf.pokok.remove(p);
                ada = true;
            }
        }
        if (!ada) {
            Toast.makeText(this, "Pokok ini belum dicatat", Toast.LENGTH_SHORT).show();
            return;
        }
        kotor = true;
        muatSlot();
        autoSimpan();
    }

    /** Draf baru disimpan otomatis tiap pokok supaya isian tidak hilang bila aplikasi tertutup. Perubahan atas inspeksi yang sudah terkirim tidak. */
    private void autoSimpan() {
        if (draf.statusLama != null && !IpdRepository.DRAFT.equals(draf.statusLama)) return;
        if (!kepalaLengkap(false)) return;
        if (draf.ipdId == null) draf.ipdId = UUID.randomUUID().toString();
        final IpdDraf salinan = salin(draf);
        io.execute(() -> {
            try {
                repo.simpanDraf(salinan);
                draf.statusLama = IpdRepository.DRAFT;
            } catch (RuntimeException ignored) {
                // gagal diam-diam: tombol Simpan draf tetap melaporkan galat
            }
        });
    }

    private static IpdDraf salin(IpdDraf d) {
        IpdDraf x = new IpdDraf();
        x.ipdId = d.ipdId;
        x.tanggal = d.tanggal;
        x.divisiId = d.divisiId;
        x.blokId = d.blokId;
        x.pemanenId = d.pemanenId;
        x.ancakNo = d.ancakNo;
        x.mandorUserId = d.mandorUserId;
        x.catatan = d.catatan;
        x.statusLama = d.statusLama;
        for (IpdDraf.Pokok p : d.pokok) {
            IpdDraf.Pokok n = new IpdDraf.Pokok(p.baris, p.pokok);
            n.temuan.putAll(p.temuan);
            x.pokok.add(n);
        }
        return x;
    }

    // ---- Langkah 3: ringkasan, foto, kirim ---------------------------------------------------------

    private View bangunKirim() {
        final Context c = this;
        ScrollView[] g = new ScrollView[1];
        LinearLayout k = kolomGulir(g);

        k.addView(IpdUi.judul(c, "Ringkasan loses", 16), IpdUi.lebarPenuh(c, 0, 6));
        kotakRekap = new LinearLayout(c);
        kotakRekap.setOrientation(LinearLayout.VERTICAL);
        k.addView(kotakRekap);

        k.addView(IpdUi.judul(c, "Foto bukti (tanpa batas jumlah)", 16), IpdUi.lebarPenuh(c, 8, 4));
        k.addView(IpdUi.redup(c, "Foto dikompres saat disimpan dan dihapus dari HP setelah terkirim ke server.", 13), IpdUi.lebarPenuh(c, 0, 6));
        kotakFoto = new LinearLayout(c);
        kotakFoto.setOrientation(LinearLayout.VERTICAL);
        k.addView(kotakFoto);
        MaterialButton tambah = IpdUi.tombol(c, "Tambah foto", false);
        tambah.setOnClickListener(v -> tambahFoto());
        k.addView(tambah, IpdUi.lebarPenuh(c, 4, 12));

        TextInputLayout til = new TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        til.setHint("Catatan inspeksi (opsional)");
        edCatatan = new TextInputEditText(til.getContext());
        edCatatan.setTextSize(16);
        edCatatan.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        edCatatan.setText(draf.catatan == null ? "" : draf.catatan);
        til.addView(edCatatan);
        k.addView(til, IpdUi.lebarPenuh(c, 0, 12));

        MaterialButton kirim = IpdUi.tombol(c, "Kirim", true);
        kirim.setMinimumHeight(IpdUi.dp(c, 64));
        kirim.setTextSize(18);
        kirim.setOnClickListener(v -> kirim(kirim));
        k.addView(kirim, IpdUi.lebarPenuh(c, 0, 8));
        MaterialButton simpan = IpdUi.tombol(c, "Simpan sebagai draf", false);
        simpan.setOnClickListener(v -> simpanDraf(false));
        k.addView(simpan, IpdUi.lebarPenuh(c, 0, 0));
        return g[0];
    }

    private void segarkanRingkasan() {
        final Context c = this;
        kotakRekap.removeAllViews();
        Map<String, String> jenis = jenisKode();
        IpdRekap r = IpdRekap.hitung(draf.pokok, jenis);
        LinearLayout[] isi = new LinearLayout[1];
        MaterialCardView kartu = IpdUi.kartu(c, isi);
        isi[0].addView(IpdUi.redup(c, r.pokokDiperiksa + " pokok diperiksa", 14));
        TextView persen = IpdUi.judul(c, "Loses: " + IpdUi.persen(r.persenLoses()), 24);
        persen.setPadding(0, IpdUi.dp(c, 2), 0, 0);
        isi[0].addView(persen);
        isi[0].addView(IpdUi.redup(c, r.pokokLoses + " pokok kehilangan hasil (buah atau brondolan) • " + r.totalLoses + " temuan", 13));
        boolean ada = false;
        for (KodeTemuan kd : kode) {
            if (!"LOSES".equals(jenis.get(kd.kode))) continue;
            Integer jml = r.jumlahPerKode.get(kd.kode);
            if (jml == null) continue;
            ada = true;
            TextView t = IpdUi.redup(c, kd.kode + "  " + kd.nama + ": " + r.pokokPerKode.get(kd.kode) + " pokok (" + IpdUi.persen(r.persenKode(kd.kode)) + "), jumlah " + jml, 14);
            t.setPadding(0, IpdUi.dp(c, 3), 0, 0);
            isi[0].addView(t);
        }
        if (!ada) isi[0].addView(IpdUi.redup(c, "Tidak ada loses.", 14));
        kotakRekap.addView(kartu);

        LinearLayout[] isi2 = new LinearLayout[1];
        MaterialCardView kartu2 = IpdUi.kartu(c, isi2);
        isi2[0].addView(IpdUi.judul(c, "Temuan lain: " + r.pokokTemuanLain + " pokok (" + IpdUi.persen(r.persenTemuanLain()) + ")", 15));
        isi2[0].addView(IpdUi.redup(c, "Tidak memengaruhi hasil panen, tidak dihitung sebagai loses.", 12));
        boolean ada2 = false;
        for (KodeTemuan kd : kode) {
            if (!"TEMUAN".equals(jenis.get(kd.kode))) continue;
            Integer jml = r.jumlahPerKode.get(kd.kode);
            if (jml == null) continue;
            ada2 = true;
            TextView t = IpdUi.redup(c, kd.kode + "  " + kd.nama + ": " + r.pokokPerKode.get(kd.kode) + " pokok, jumlah " + jml, 14);
            t.setPadding(0, IpdUi.dp(c, 3), 0, 0);
            isi2[0].addView(t);
        }
        if (!ada2) isi2[0].addView(IpdUi.redup(c, "Tidak ada temuan lain.", 14));
        kotakRekap.addView(kartu2);
        segarkanFoto();
    }

    private void segarkanFoto() {
        if (draf.ipdId == null) {
            if (kotakFoto != null) kotakFoto.removeAllViews();
            if (btnFotoPokok != null) btnFotoPokok.setText("Ambil foto pokok ini");
            return;
        }
        final String id = draf.ipdId;
        io.execute(() -> {
            List<IpdFoto> foto = dao.foto(id);
            main.post(() -> {
                if (isFinishing()) return;
                if (kotakFoto != null) {
                    kotakFoto.removeAllViews();
                    for (IpdFoto f : foto) kotakFoto.addView(barisFoto(f));
                }
                if (btnFotoPokok != null) btnFotoPokok.setText("Ambil foto pokok ini  •  " + foto.size() + " foto tersimpan");
            });
        });
    }

    private View barisFoto(IpdFoto f) {
        final Context c = this;
        LinearLayout[] isi = new LinearLayout[1];
        MaterialCardView kartu = IpdUi.kartu(c, isi);
        LinearLayout baris = new LinearLayout(c);
        baris.setOrientation(LinearLayout.HORIZONTAL);
        baris.setGravity(Gravity.CENTER_VERTICAL);
        ImageView img = new ImageView(c);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setBackgroundColor(MaterialColors.getColor(img, com.google.android.material.R.attr.colorSurfaceVariant));
        if (f.pathLokal != null) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 8;
            Bitmap bmp = BitmapFactory.decodeFile(f.pathLokal, o);
            if (bmp != null) img.setImageBitmap(bmp);
        }
        baris.addView(img, new LinearLayout.LayoutParams(IpdUi.dp(c, 72), IpdUi.dp(c, 72)));
        LinearLayout teks = new LinearLayout(c);
        teks.setOrientation(LinearLayout.VERTICAL);
        TextView ket = IpdUi.judul(c, f.keterangan == null || f.keterangan.isEmpty() ? "Tanpa keterangan (ketuk untuk mengisi)" : f.keterangan, 14);
        ket.setOnClickListener(v -> ubahKeterangan(f));
        teks.addView(ket);
        boolean terkirim = f.pathLokal == null;
        TextView st = IpdUi.redup(c, terkirim ? "Sudah terkirim ke server" : (f.ukuranByte == null ? "" : (f.ukuranByte / 1024) + " KB • belum terkirim"), 12);
        teks.addView(st);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginStart(IpdUi.dp(c, 12));
        baris.addView(teks, tlp);
        if (!terkirim) {
            MaterialButton hapus = IpdUi.tombol(c, "Hapus", false);
            hapus.setOnClickListener(v -> io.execute(() -> {
                repo.hapusFoto(f.fotoId);
                main.post(this::segarkanFoto);
            }));
            baris.addView(hapus);
        }
        isi[0].addView(baris);
        return kartu;
    }

    private void ubahKeterangan(IpdFoto f) {
        final EditText e = new EditText(this);
        e.setText(f.keterangan == null ? "" : f.keterangan);
        e.setHint("Keterangan foto");
        e.setPadding(IpdUi.dp(this, 24), IpdUi.dp(this, 16), IpdUi.dp(this, 24), IpdUi.dp(this, 8));
        new MaterialAlertDialogBuilder(this).setTitle("Keterangan foto").setView(e).setNegativeButton("Batal", null)
                .setPositiveButton("Simpan", (d, w) -> io.execute(() -> {
                    dao.ubahKeteranganFoto(f.fotoId, e.getText().toString().trim(), com.example.fai26.db.Waktu.utcSekarang());
                    main.post(this::segarkanFoto);
                })).show();
    }

    // ---- Foto ------------------------------------------------------------------------------------

    /** Foto saat memeriksa: keterangan otomatis memuat nomor baris dan pokok yang sedang dicatat. */
    private void tambahFotoPokok() {
        int a = angka(edBaris, 1);
        int p = angka(edPokok, 1);
        keteranganFotoOtomatis = dua() ? "Baris " + a + " dan " + angka(edBaris2, a + 1) + ", pokok " + p : "Baris " + a + ", pokok " + p;
        tambahFoto();
    }

    private void tambahFoto() {
        if (!kepalaLengkap(true)) {
            keteranganFotoOtomatis = null;
            return;
        }
        // Inspeksi harus sudah tersimpan (draf) sebelum foto bisa ditautkan.
        if (draf.ipdId == null) draf.ipdId = UUID.randomUUID().toString();
        final IpdDraf salinan = salin(draf);
        salinan.ipdId = draf.ipdId;
        io.execute(() -> {
            try {
                if (dao.header(salinan.ipdId) == null) repo.simpanDraf(salinan);
                draf.statusLama = draf.statusLama == null ? IpdRepository.DRAFT : draf.statusLama;
                main.post(() -> {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) mulaiAmbilFoto();
                    else mintaIzinKamera.launch(Manifest.permission.CAMERA);
                });
            } catch (RuntimeException e) {
                main.post(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void mulaiAmbilFoto() {
        try {
            File dir = new File(getCacheDir(), "foto_ipd_mentah");
            if (!dir.exists() && !dir.mkdirs()) throw new java.io.IOException("folder");
            fotoMentah = new File(dir, UUID.randomUUID() + ".jpg");
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fotoipd", fotoMentah);
            ambilFoto.launch(uri);
        } catch (Exception e) {
            Toast.makeText(this, "Kamera tidak bisa dibuka: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** Foto dari kamera dikecilkan dan dimampatkan, lalu diberi keterangan. */
    private void prosesFoto(File mentah) {
        final String ipdId = draf.ipdId;
        final String ketOtomatis = keteranganFotoOtomatis;
        keteranganFotoOtomatis = null;
        final File tujuan = new File(new File(getFilesDir(), "foto_ipd"), UUID.randomUUID() + ".jpg");
        io.execute(() -> {
            try {
                FotoKompres.Hasil h = FotoKompres.kompres(mentah, tujuan);
                //noinspection ResultOfMethodCallIgnored
                mentah.delete();
                if (ketOtomatis != null) {
                    repo.tambahFoto(ipdId, tujuan, h, ketOtomatis);
                    kotor = true;
                    main.post(() -> {
                        Toast.makeText(this, "Foto tersimpan: " + ketOtomatis, Toast.LENGTH_SHORT).show();
                        segarkanFoto();
                    });
                } else {
                    main.post(() -> mintaKeteranganFoto(ipdId, tujuan, h));
                }
            } catch (Exception e) {
                //noinspection ResultOfMethodCallIgnored
                mentah.delete();
                main.post(() -> Toast.makeText(this, "Foto gagal diproses: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void mintaKeteranganFoto(String ipdId, File berkas, FotoKompres.Hasil h) {
        final EditText e = new EditText(this);
        e.setHint("Keterangan foto (mis. buah tinggal di baris 11)");
        e.setPadding(IpdUi.dp(this, 24), IpdUi.dp(this, 16), IpdUi.dp(this, 24), IpdUi.dp(this, 8));
        final Runnable simpan = () -> io.execute(() -> {
            repo.tambahFoto(ipdId, berkas, h, e.getText().toString());
            kotor = true;
            main.post(this::segarkanFoto);
        });
        new MaterialAlertDialogBuilder(this).setTitle("Keterangan foto").setView(e).setCancelable(false)
                .setPositiveButton("Simpan", (d, w) -> simpan.run())
                .setNegativeButton("Tanpa keterangan", (d, w) -> simpan.run())
                .show();
    }

    // ---- Simpan, kirim, keluar ----------------------------------------------------------------------------

    private boolean salinKeDraf() {
        if (!kepalaLengkap(true)) {
            tampilkanLangkah(0);
            return false;
        }
        if (langkah == 1) simpanSlotBila();
        draf.catatan = edCatatan.getText().toString().trim();
        if (draf.ipdId == null) draf.ipdId = UUID.randomUUID().toString();
        return true;
    }

    private void simpanDraf(boolean keluarSetelah) {
        if (!salinKeDraf()) return;
        final IpdDraf salinan = salin(draf);
        io.execute(() -> {
            try {
                repo.simpanDraf(salinan);
                draf.ipdId = salinan.ipdId;
                draf.statusLama = IpdRepository.DRAFT;
                kotor = false;
                main.post(() -> {
                    Toast.makeText(this, "Draf tersimpan di HP", Toast.LENGTH_SHORT).show();
                    if (keluarSetelah) finish();
                });
            } catch (RuntimeException e) {
                main.post(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void kirim(View tombol) {
        if (!salinKeDraf()) return;
        final IpdDraf salinan = salin(draf);
        try {
            IpdRepository.pastikanLengkap(salinan);
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        new MaterialAlertDialogBuilder(this).setTitle("Kirim inspeksi?")
                .setMessage("Dengan mengirim, kamu menyatakan inspeksi ini valid. Asisten akan menyetujuinya; sebelum disetujui masih bisa kamu ubah dari Riwayat.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Kirim", (d, w) -> {
                    tombol.setEnabled(false);
                    final Context app = getApplicationContext();
                    io.execute(() -> {
                        try {
                            repo.kirim(salinan);
                            kotor = false;
                            SyncJadwal.segera(app);
                            main.post(() -> {
                                Toast.makeText(app, "Tersimpan. Terkirim begitu ada sinyal, lalu menunggu persetujuan asisten", Toast.LENGTH_LONG).show();
                                finish();
                            });
                        } catch (RuntimeException e) {
                            main.post(() -> {
                                tombol.setEnabled(true);
                                Toast.makeText(app, e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                        }
                    });
                }).show();
    }

    private void keluar() {
        if (!kotor) {
            finish();
            return;
        }
        new MaterialAlertDialogBuilder(this).setTitle("Keluar dari inspeksi?")
                .setMessage("Ada isian yang belum disimpan.")
                .setNeutralButton("Tetap di sini", null)
                .setNegativeButton("Keluar tanpa menyimpan", (d, w) -> {
                    buangJejakBaru();
                    finish();
                })
                .setPositiveButton("Simpan draf", (d, w) -> simpanDraf(true)).show();
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacks(pantauJejak);
        if (isFinishing()) com.example.fai26.lokasi.JejakService.berhenti(this);
        super.onDestroy();
    }
}
