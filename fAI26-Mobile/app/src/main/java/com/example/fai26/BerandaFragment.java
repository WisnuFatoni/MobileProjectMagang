package com.example.fai26;

import android.Manifest;
import android.app.Activity;
import android.location.Location;
import android.content.Intent;
import android.view.LayoutInflater;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.fai26.auth.Sesi;
import com.example.fai26.db.AppDatabase;
import com.example.fai26.db.Waktu;
import com.example.fai26.db.dao.Hasil;
import com.example.fai26.sync.PerubahanMaster;
import com.example.fai26.lokasi.PencatatLokasi;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Beranda setelah login: ringkasan hari ini, pilih TPH dan pemanen, mulai scan, dan catatan terbaru.
 * Kamera hanya dibuka dari sini.
 */
public class BerandaFragment extends Fragment {

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private final List<Hasil.TphPilihan> daftarTph = new ArrayList<>();
    private final List<Hasil.PemanenPilihan> daftarPemanen = new ArrayList<>();
    private Hasil.TphPilihan tphTerpilih;

    /** Master baru turun dari server (sinkron latar atau login online): muat ulang daftar TPH dan pemanen. */
    private final PerubahanMaster.Pendengar pendengarMaster = new PerubahanMaster.Pendengar() {
        @Override
        public void masterBerubah() {
            if (isAdded() && Sesi.sudahLogin(requireContext()) && Sesi.punya(requireContext(), "panen.buat")) muatPilihan();
        }

        @Override
        public void hakBerubah() {
            if (isAdded() && Sesi.sudahLogin(requireContext())) terapkanHak();
        }
    };
    private Hasil.PemanenPilihan pemanenTerpilih;
    private String sumberTph = Ekstra.SUMBER_MANUAL;

    private View grupBelumPilih;
    private View grupManual;
    private View grupTerpilih;
    private TextView txtTphTerpilih;
    private TextView txtSumber;
    private TextView txtPetunjukKartu;
    private LinearLayout daftarDetail;
    private View boxPeringatanHariIni;
    private TextView txtPeringatanHariIni;

    /** Hasil layar QR: Activity.RESULT_OK membawa TPH, Activity.RESULT_FIRST_USER berarti krani memilih manual. */
    private final ActivityResultLauncher<Intent> layarQr =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                Intent d = r.getData();
                if (r.getResultCode() == Activity.RESULT_OK && d != null) {
                    Hasil.TphPilihan t = new Hasil.TphPilihan();
                    t.tphId = d.getLongExtra(Ekstra.EXTRA_TPH_ID, -1);
                    t.blokId = d.getLongExtra(Ekstra.EXTRA_BLOK_ID, -1);
                    t.unitId = d.getLongExtra(Ekstra.EXTRA_UNIT_ID, -1);
                    t.blok = d.getStringExtra(Ekstra.EXTRA_BLOK_KODE);
                    t.noTph = d.getStringExtra(Ekstra.EXTRA_NO_TPH);
                    pilihTph(t, Ekstra.SUMBER_QR);
                } else if (r.getResultCode() == Activity.RESULT_FIRST_USER) {
                    keadaan(Keadaan.MANUAL);
                }
            });

    private enum Keadaan { BELUM, MANUAL, TERPILIH }

    private TextView angkaCatatan;
    private TextView angkaJanjang;
    private TextView angkaTertunda;
    private LinearLayout daftarTerbaru;
    private View boxEmptyState;
    private ImageView dotStatus;
    private TextView txtStatusKoneksi;
    private TextView txtKosong;
    private TextView txtPesanScan;
    private MaterialButton btnMulai;
    private MaterialAutoCompleteTextView pilihTph;
    private MaterialAutoCompleteTextView pilihPemanen;
    private TextInputLayout tilTph;

    private View raiz;

    private <T extends View> T cari(int id) {
        return raiz.findViewById(id);
    }

    /** Menjalankan r di thread utama hanya bila fragment masih terpasang. */
    private void posting(Runnable r) {
        main.post(() -> {
            if (isAdded() && raiz != null) r.run();
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        raiz = inflater.inflate(R.layout.fragment_beranda, container, false);
        iniUi();
        return raiz;
    }

    private void iniUi() {
        View header = cari(R.id.header);
        final int paddingAtas = header.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(cari(R.id.root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            header.setPadding(header.getPaddingLeft(), paddingAtas + bars.top,
                    header.getPaddingRight(), header.getPaddingBottom());
            v.setPadding(bars.left, 0, bars.right, bars.bottom + (int) (112 * getResources().getDisplayMetrics().density));
            return insets;
        });

        angkaCatatan = cari(R.id.angkaCatatan);
        angkaJanjang = cari(R.id.angkaJanjang);
        angkaTertunda = cari(R.id.angkaTertunda);
        daftarTerbaru = cari(R.id.daftarTerbaru);
        boxEmptyState = cari(R.id.boxEmptyState);
        dotStatus = cari(R.id.dotStatus);
        txtStatusKoneksi = cari(R.id.txtStatusKoneksi);
        txtKosong = cari(R.id.txtKosong);
        txtPesanScan = cari(R.id.txtPesanScan);
        btnMulai = cari(R.id.btnMulai);
        pilihTph = cari(R.id.pilihTph);
        pilihPemanen = cari(R.id.pilihPemanen);
        tilTph = cari(R.id.tilTph);
        grupBelumPilih = cari(R.id.grupBelumPilih);
        grupManual = cari(R.id.grupManual);
        grupTerpilih = cari(R.id.grupTerpilih);
        txtTphTerpilih = cari(R.id.txtTphTerpilih);
        txtSumber = cari(R.id.txtSumber);
        txtPetunjukKartu = cari(R.id.txtPetunjukKartu);
        daftarDetail = cari(R.id.daftarDetail);
        boxPeringatanHariIni = cari(R.id.boxPeringatanHariIni);
        txtPeringatanHariIni = cari(R.id.txtPeringatanHariIni);

        ((TextView) cari(R.id.txtSalam)).setText(salam());
        ((TextView) cari(R.id.txtNama)).setText(Sesi.nama(requireContext()));
        TextView avatar = cari(R.id.txtAvatar);
        if (avatar != null) avatar.setText(inisial(Sesi.nama(requireContext())));
        String peran = Sesi.peran(requireContext());
        String tanggal = new SimpleDateFormat("EEEE, d MMMM yyyy", new Locale("id", "ID")).format(new Date());
        ((TextView) cari(R.id.txtPeranTanggal))
                .setText(peran.isEmpty() ? tanggal : peran + " • " + tanggal);

        perbaruiStatusKoneksi();
        View pill = cari(R.id.pillStatusKoneksi);
        if (pill != null) {
            pill.setOnClickListener(v -> segarkanDariServer(true));
        }
        View btnSegarkanHeader = cari(R.id.btnSegarkanHeader);
        if (btnSegarkanHeader != null) {
            btnSegarkanHeader.setOnClickListener(v -> segarkanDariServer(true));
        }

        cari(R.id.btnLihatSemua).setOnClickListener(
                v -> ((MainActivity) requireActivity()).pilihLayar("riwayat"));
        btnMulai.setOnClickListener(v -> mulaiScan());
        cari(R.id.btnScanQr).setOnClickListener(v ->
                layarQr.launch(new Intent(requireContext(), QrActivity.class)));
        cari(R.id.btnManual).setOnClickListener(v -> keadaan(Keadaan.MANUAL));
        cari(R.id.btnKembaliQr).setOnClickListener(v -> keadaan(Keadaan.BELUM));
        cari(R.id.btnGanti).setOnClickListener(v -> {
            tphTerpilih = null;
            keadaan(Keadaan.BELUM);
        });

        // Tanpa hak membuat catatan (panen.buat), kartu scan dinonaktifkan.
        if (!Sesi.punya(requireContext(), "panen.buat")) {
            terapkanHak();
        } else {
            keadaan(Keadaan.BELUM);
            muatPilihan();
        }
    }

    /** Menyesuaikan kartu scan dengan hak akun saat ini; dipanggil juga saat hak berubah dari master baru. */
    private void terapkanHak() {
        boolean boleh = Sesi.punya(requireContext(), "panen.buat");
        cari(R.id.kartuScan).setAlpha(boleh ? 1f : 0.6f);
        cari(R.id.btnScanQr).setEnabled(boleh);
        cari(R.id.btnManual).setEnabled(boleh);
        if (!boleh) {
            tphTerpilih = null;
            keadaan(Keadaan.BELUM);
            tampilkanPesanScan("Akun ini tidak boleh membuat catatan panen.");
        } else if (daftarTph.isEmpty()) {
            keadaan(Keadaan.BELUM);
            muatPilihan();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isHidden() && Sesi.sudahLogin(requireContext())) {
            perbaruiStatusKoneksi();
            muatRingkasan();
            periksaKondisiPerangkat();
        }
    }

    private void perbaruiStatusKoneksi() {
        if (!isAdded() || dotStatus == null || txtStatusKoneksi == null) return;
        com.example.fai26.ui.AvatarProfil.pasang(requireContext(), cari(R.id.txtAvatar), cari(R.id.imgAvatar));
        com.example.fai26.sync.PengaturanSync pref = new com.example.fai26.sync.PengaturanSync(requireContext());
        if (pref.adaToken()) {
            dotStatus.setImageResource(R.drawable.ic_wifi_online);
            txtStatusKoneksi.setText("Online • Terhubung ke Server");
        } else {
            dotStatus.setImageResource(R.drawable.ic_wifi_offline);
            txtStatusKoneksi.setText("Mode Offline • Tersimpan di HP");
        }
    }

    /** Memperingatkan bila baterai rendah atau ruang simpan menipis, di tempat pesan kartu scan. */
    private void periksaKondisiPerangkat() {
        if (!Sesi.punya(requireContext(), "panen.buat")) return;
        java.util.List<String> p = com.example.fai26.sistem.KondisiPerangkat.baca(requireContext());
        if (p.isEmpty()) return;
        // Jangan menimpa pesan lain yang sedang tampil (misalnya akun tidak boleh mencatat).
        if (txtPesanScan.getVisibility() == View.VISIBLE) return;
        tampilkanPesanScan(android.text.TextUtils.join("\n", p));
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && Sesi.sudahLogin(requireContext())) muatRingkasan();
    }

    /** Dua huruf awal nama untuk avatar (mis. "Mandor Contoh" menjadi "MC"). */
    static String inisial(String nama) {
        if (nama == null) return "";
        StringBuilder b = new StringBuilder();
        for (String kata : nama.trim().split("\\s+")) {
            if (!kata.isEmpty() && b.length() < 2) b.append(Character.toUpperCase(kata.charAt(0)));
        }
        return b.toString();
    }

    private String salam() {
        int jam = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (jam < 11) return "Selamat pagi";
        if (jam < 15) return "Selamat siang";
        if (jam < 18) return "Selamat sore";
        return "Selamat malam";
    }

    /** Mengisi pilihan TPH dan pemanen dari master. */
    private void muatPilihan() {
        io.execute(() -> {
            try {
                Fai26App.tungguSiap();
                AppDatabase db = AppDatabase.getInstance(requireContext().getApplicationContext());
                List<Hasil.TphPilihan> tph = db.masterDao().daftarTph();
                List<Hasil.PemanenPilihan> pemanen = new ArrayList<>();
                Hasil.PemanenPilihan kosong = new Hasil.PemanenPilihan();
                kosong.pemanenId = 0;
                kosong.nama = "Pilih pemanen...";
                pemanen.add(kosong);
                pemanen.addAll(db.masterDao().daftarPemanen());
                posting(() -> terapkanPilihan(tph, pemanen));
            } catch (Exception e) {
                posting(() -> tampilkanPesanScan("Gagal memuat daftar TPH: " + e.getMessage()));
            }
        });
    }

    private void terapkanPilihan(List<Hasil.TphPilihan> tph, List<Hasil.PemanenPilihan> pemanen) {
        // Pilihan yang sedang dibuat krani dipertahankan saat daftar dimuat ulang (master berubah di latar).
        final Long pemanenLama = pemanenTerpilih == null ? null : pemanenTerpilih.pemanenId;
        final Hasil.TphPilihan tphLama = tphTerpilih;
        daftarTph.clear();
        daftarTph.addAll(tph);
        daftarPemanen.clear();
        daftarPemanen.addAll(pemanen);

        // Daftar bisa dicari dengan mengetik (ribuan TPH): setiap kata yang diketik harus ada di teks pilihan.
        pilihTph.setAdapter(new PencarianAdapter<>(requireContext(), daftarTph));
        pilihPemanen.setAdapter(new PencarianAdapter<>(requireContext(), daftarPemanen));
        pilihTph.setThreshold(1);
        pilihPemanen.setThreshold(1);
        pilihTph.setOnClickListener(v -> pilihTph.showDropDown());
        pilihPemanen.setOnClickListener(v -> pilihPemanen.showDropDown());
        // Yang dipilih adalah item yang diketuk (bukan nomor urut di daftar penuh: urutan berubah saat disaring).
        pilihTph.setOnItemClickListener((p, v, pos, id) -> {
            tilTph.setError(null);
            Hasil.TphPilihan dipilih = (Hasil.TphPilihan) p.getItemAtPosition(pos);
            pilihTph(dipilih, Ekstra.SUMBER_MANUAL);
            sembunyikanKeyboard(pilihTph);
        });
        pilihPemanen.setOnItemClickListener((p, v, pos, id) -> {
            Hasil.PemanenPilihan dipilih = (Hasil.PemanenPilihan) p.getItemAtPosition(pos);
            pemanenTerpilih = dipilih.pemanenId == 0 ? null : dipilih;
            // Pesan "Pilih pemanen dulu" tidak berlaku lagi setelah pemanen dipilih.
            if (pemanenTerpilih != null) txtPesanScan.setVisibility(View.GONE);
            sembunyikanKeyboard(pilihPemanen);
        });
        pilihPemanen.setOnFocusChangeListener((v, fokus) -> {
            String awal = daftarPemanen.isEmpty() ? "" : daftarPemanen.get(0).toString();
            String teks = String.valueOf(pilihPemanen.getText());
            if (fokus) {
                // "Pilih pemanen..." adalah isi sungguhan kolom: dikosongkan supaya ketikan tidak menempel di depannya.
                // Bila sudah ada pemanen terpilih, teksnya diblok supaya ketikan baru menggantikannya.
                if (teks.equals(awal)) pilihPemanen.setText("", false);
                else pilihPemanen.post(pilihPemanen::selectAll);
            } else if (pemanenTerpilih == null) {
                // Tidak ada pemanen yang dipilih: kembalikan tulisan awal supaya jelas belum ada pemanen.
                pilihPemanen.setText(awal, false);
            }
        });
        // Teks diubah tanpa memilih item: pilihan sebelumnya tidak berlaku lagi (hindari teks dan pilihan yang tidak sama).
        pilihPemanen.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                if (pemanenTerpilih != null && !String.valueOf(s).equals(pemanenTerpilih.toString())) pemanenTerpilih = null;
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });
        Hasil.PemanenPilihan pulih = null;
        if (pemanenLama != null) {
            for (Hasil.PemanenPilihan q : daftarPemanen) if (q.pemanenId == pemanenLama) pulih = q;
        }
        pemanenTerpilih = pulih;
        pilihPemanen.setText((pulih != null ? pulih : daftarPemanen.get(0)).toString(), false);

        if (tphLama != null) {
            Hasil.TphPilihan baru = null;
            for (Hasil.TphPilihan q : daftarTph) if (q.tphId == tphLama.tphId) baru = q;
            if (baru == null) {
                tphTerpilih = null;
                keadaan(Keadaan.BELUM);
                tampilkanPesanScan("TPH yang tadi dipilih sudah tidak ada di master. Pilih lagi.");
                return;
            }
            // Objek baru (koordinat atau nama bisa berubah di server): perbarui tampilan dan detailnya.
            tphTerpilih = baru;
            txtTphTerpilih.setText(baru.toString());
            muatDetail(baru);
        }
        if (daftarTph.isEmpty()) tampilkanPesanScan("Data TPH belum ada. Hubungi admin.");
    }

    private void sembunyikanKeyboard(View v) {
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        v.clearFocus();
    }

    /** TPH sudah ditentukan, lewat QR atau lewat daftar manual. */
    private void pilihTph(Hasil.TphPilihan t, String sumber) {
        tphTerpilih = t;
        sumberTph = sumber;
        pemanenTerpilih = null;
        if (!daftarPemanen.isEmpty()) pilihPemanen.setText(daftarPemanen.get(0).toString(), false);
        txtTphTerpilih.setText(t.toString());
        txtSumber.setText(Ekstra.SUMBER_QR.equals(sumber) ? "Dikenali dari QR" : "Dipilih manual (bukan QR)");
        keadaan(Keadaan.TERPILIH);
        muatDetail(t);
    }

    /** Mengambil informasi TPH dari master lalu menampilkannya. */
    private void muatDetail(Hasil.TphPilihan t) {
        daftarDetail.removeAllViews();
        boxPeringatanHariIni.setVisibility(View.GONE);
        io.execute(() -> {
            try {
                Hasil.TphDetail d = AppDatabase.getInstance(requireContext().getApplicationContext())
                        .masterDao().detailTph(t.tphId, Waktu.tanggalLokalHariIni());
                // Bila pilihan sudah berganti selama menunggu, abaikan hasil ini.
                posting(() -> {
                    if (tphTerpilih == t && d != null) tampilkanDetail(d);
                });
            } catch (Exception e) {
                // Info tambahan saja; krani tetap bisa melanjutkan.
            }
        });
    }

    private void tampilkanDetail(Hasil.TphDetail d) {
        daftarDetail.removeAllViews();
        tambahBaris("Unit", d.unitNama != null ? d.unitKode + " • " + d.unitNama : d.unitKode);
        if (d.divisiNama != null) tambahBaris("Divisi", d.divisiNama);
        tambahBaris("Blok", d.blokNama != null && !d.blokNama.isEmpty()
                ? d.blokKode + " • " + d.blokNama : d.blokKode);
        if (d.tahunTanam != null) {
            int umur = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) - d.tahunTanam.intValue();
            tambahBaris("Tahun tanam", d.tahunTanam + (umur >= 0 ? " (umur " + umur + " tahun)" : ""));
        }
        if (d.luasHa != null) tambahBaris("Luas blok", Format.desimal(d.luasHa) + " ha");
        if (d.sph != null && d.luasHa != null) {
            tambahBaris("Perkiraan pokok", "≈ " + Format.angka(Math.round(d.sph * d.luasHa))
                    + " pokok (" + Format.desimal(d.sph) + " per ha)");
        }
        tambahBaris("Panen terakhir", d.panenTerakhir == null
                ? "Belum ada catatan di HP ini" : Format.tanggal(d.panenTerakhir));
        tambahBaris("Koordinat TPH", d.lat != null && d.lon != null ? "Sudah direkam" : "Belum direkam");
        if (d.lat == null || d.lon == null) tambahTombolRekam();

        if (d.catatanHariIni > 0) {
            txtPeringatanHariIni.setText("Hari ini sudah ada " + d.catatanHariIni
                    + " catatan di TPH ini. Lanjutkan hanya bila ini tumpukan yang berbeda.");
            boxPeringatanHariIni.setVisibility(View.VISIBLE);
        }
    }

    // ---- Rekam koordinat TPH ------------------------------------------------------------------------

    private static final long WAKTU_REKAM_MS = 30_000L;
    private static final double AKURASI_BAGUS_M = 12;

    private final ActivityResultLauncher<String[]> mintaIzinLokasi =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), hasil -> {
                if (PencatatLokasi.adaIzin(requireContext())) mulaiRekam();
                else Toast.makeText(requireContext(), "Izin lokasi ditolak", Toast.LENGTH_LONG).show();
            });

    private PencatatLokasi pencatatRekam;
    private MaterialButton tombolRekam;
    private boolean merekam = false;

    /** Tombol untuk TPH yang belum punya koordinat: krani berdiri di TPH lalu merekam titiknya. */
    private void tambahTombolRekam() {
        float dp = getResources().getDisplayMetrics().density;
        tombolRekam = new MaterialButton(requireContext(), null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        tombolRekam.setText("Rekam koordinat TPH ini");
        tombolRekam.setOnClickListener(v -> {
            if (PencatatLokasi.adaIzin(requireContext())) {
                mulaiRekam();
            } else {
                mintaIzinLokasi.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION});
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (52 * dp));
        lp.topMargin = (int) (8 * dp);
        daftarDetail.addView(tombolRekam, lp);
    }

    private void mulaiRekam() {
        if (merekam || tphTerpilih == null || tombolRekam == null) return;
        if (pencatatRekam == null) pencatatRekam = new PencatatLokasi(requireContext());
        if (!pencatatRekam.mulai()) {
            Toast.makeText(requireContext(), "GPS mati. Nyalakan lokasi di pengaturan HP.", Toast.LENGTH_LONG).show();
            return;
        }
        merekam = true;
        tombolRekam.setEnabled(false);
        final Hasil.TphPilihan sasaran = tphTerpilih;
        final long mulai = System.currentTimeMillis();
        final Runnable[] putaran = new Runnable[1];
        putaran[0] = () -> {
            if (!merekam || !isAdded()) return;
            Location l = pencatatRekam.terbaik();
            long lalu = System.currentTimeMillis() - mulai;
            boolean cukup = l != null && l.hasAccuracy() && l.getAccuracy() <= AKURASI_BAGUS_M;
            if (cukup || lalu >= WAKTU_REKAM_MS) {
                selesaiRekam(sasaran, l);
                return;
            }
            tombolRekam.setText("MENCARI TITIK GPS... " + (l != null && l.hasAccuracy()
                    ? "±" + Math.round(l.getAccuracy()) + " m" : (lalu / 1000) + " dtk"));
            main.postDelayed(putaran[0], 1000);
        };
        putaran[0].run();
    }

    private void selesaiRekam(Hasil.TphPilihan sasaran, Location l) {
        merekam = false;
        pencatatRekam.berhenti();
        final android.content.Context app = requireContext().getApplicationContext();
        if (l == null || !l.hasAccuracy()) {
            tombolRekam.setEnabled(true);
            tombolRekam.setText("Rekam koordinat TPH ini");
            Toast.makeText(app, "Belum dapat titik GPS. Coba di tempat terbuka.", Toast.LENGTH_LONG).show();
            return;
        }
        final double lat = l.getLatitude();
        final double lon = l.getLongitude();
        final double akurasi = l.getAccuracy();
        io.execute(() -> {
            String pesan;
            try {
                boolean ok = Fai26App.repository(app).rekamKoordinatTph(sasaran.tphId, lat, lon, akurasi);
                pesan = ok ? "Koordinat TPH tersimpan (±" + Math.round(akurasi) + " m)"
                        : "TPH ini sudah punya koordinat";
            } catch (Exception e) {
                pesan = e.getMessage();
            }
            final String teks = pesan;
            posting(() -> {
                if (!isAdded()) return;
                Toast.makeText(app, teks, Toast.LENGTH_LONG).show();
                if (tphTerpilih == sasaran) muatDetail(sasaran);
            });
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        PerubahanMaster.daftar(pendengarMaster);
    }

    @Override
    public void onStop() {
        super.onStop();
        PerubahanMaster.lepas(pendengarMaster);
        // Tidak melacak di belakang layar: berhenti saat layar tidak terlihat.
        if (pencatatRekam != null) pencatatRekam.berhenti();
        merekam = false;
        if (tombolRekam != null) {
            tombolRekam.setEnabled(true);
            tombolRekam.setText("Rekam koordinat TPH ini");
        }
    }

    private void tambahBaris(String label, String nilai) {
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout baris = new LinearLayout(requireContext());
        baris.setOrientation(LinearLayout.HORIZONTAL);
        baris.setPadding(0, (int) (5 * dp), 0, (int) (5 * dp));

        TextView l = new TextView(requireContext());
        l.setText(label);
        l.setTextSize(14);
        l.setTextColor(com.google.android.material.color.MaterialColors.getColor(
                l, com.google.android.material.R.attr.colorOnSurfaceVariant));
        baris.addView(l, new LinearLayout.LayoutParams((int) (128 * dp), ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView v = new TextView(requireContext());
        v.setText(nilai);
        v.setTextSize(15);
        v.setTypeface(v.getTypeface(), android.graphics.Typeface.BOLD);
        v.setTextColor(com.google.android.material.color.MaterialColors.getColor(
                v, com.google.android.material.R.attr.colorOnSurface));
        baris.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        daftarDetail.addView(baris);
    }

    private void keadaan(Keadaan k) {
        grupBelumPilih.setVisibility(k == Keadaan.BELUM ? View.VISIBLE : View.GONE);
        grupManual.setVisibility(k == Keadaan.MANUAL ? View.VISIBLE : View.GONE);
        grupTerpilih.setVisibility(k == Keadaan.TERPILIH ? View.VISIBLE : View.GONE);
        txtPetunjukKartu.setText(k == Keadaan.TERPILIH
                ? "Pilih pemanen bila perlu, lalu mulai scan TBS."
                : k == Keadaan.MANUAL
                ? "Pilih TPH dari daftar."
                : "Scan QR di TPH, lalu sisir tumpukan TBS.");
        txtPesanScan.setVisibility(View.GONE);
    }

    private void mulaiScan() {
        if (tphTerpilih == null) {
            keadaan(Keadaan.BELUM);
            return;
        }
        if (pemanenTerpilih == null) {
            // Satu TPH hanya untuk satu pemanen: pemanen harus dipilih sebelum scan.
            tampilkanPesanScan("Pilih pemanen dulu (satu TPH hanya untuk satu pemanen).");
            pilihPemanen.requestFocus();
            return;
        }
        // Peringatan dini: sudah ada catatan hari ini untuk TPH + pemanen yang sama (server akan menolaknya dengan 409).
        final long tphId = tphTerpilih.tphId;
        final long pemanenId = pemanenTerpilih.pemanenId;
        final android.content.Context app = requireContext().getApplicationContext();
        io.execute(() -> {
            Hasil.KombinasiAda dobel = null;
            try {
                Fai26App.tungguSiap();
                dobel = AppDatabase.getInstance(app).panenDao()
                        .kombinasiAda(com.example.fai26.db.Waktu.tanggalLokalHariIni(), tphId, pemanenId, "");
            } catch (Exception ignored) {
                // pemeriksaan gagal: lanjut saja, penyimpanan akan memeriksanya lagi
            }
            final Hasil.KombinasiAda ada = dobel;
            posting(() -> {
                if (ada == null) {
                    luncurkanScan();
                    return;
                }
                boolean ditolak = com.example.fai26.db.StatusPanen.DITOLAK.equals(ada.status);
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("TPH ini sudah dicatat")
                        .setMessage(ditolak
                                ? "Catatan hari ini untuk TPH dan pemanen ini pernah ditolak. Perbaiki lewat Riwayat, pilih Edit dan kirim ulang."
                                : "Hari ini TPH ini sudah dicatat untuk pemanen yang sama. Catatan ganda akan ditolak server.")
                        .setNegativeButton("Batal", null)
                        .setPositiveButton("Tetap scan", (d, w) -> luncurkanScan())
                        .show();
            });
        });
    }

    private void luncurkanScan() {
        if (!isAdded() || tphTerpilih == null || pemanenTerpilih == null) return;
        Intent i = new Intent(requireContext(), ScanActivity.class);
        i.putExtra(Ekstra.EXTRA_TPH_ID, tphTerpilih.tphId);
        i.putExtra(Ekstra.EXTRA_BLOK_ID, tphTerpilih.blokId);
        i.putExtra(Ekstra.EXTRA_UNIT_ID, tphTerpilih.unitId);
        i.putExtra(Ekstra.EXTRA_PEMANEN_ID, pemanenTerpilih.pemanenId);
        i.putExtra(Ekstra.EXTRA_TUJUAN, tphTerpilih.toString());
        i.putExtra(Ekstra.EXTRA_SUMBER, sumberTph);
        startActivity(i);
    }

    private void tampilkanPesanScan(String teks) {
        txtPesanScan.setText(teks);
        txtPesanScan.setVisibility(View.VISIBLE);
    }

    /** Memuat angka ringkasan dan tiga catatan terbaru (lokal instan + tarik data real dari server). */
    private void muatRingkasan() {
        final android.content.Context app = requireContext().getApplicationContext();
        final long pemilik = Sesi.filterPemilik(app);
        io.execute(() -> {
            try {
                Fai26App.tungguSiap();
                AppDatabase db = AppDatabase.getInstance(app);
                List<Hasil.RiwayatItem> terbaru = db.panenDao().riwayat(pemilik, 3);
                final DashboardBeranda.Data dashLokal = DashboardBeranda.muatLokal(app, db, pemilik);
                posting(() -> terapkanRingkasanKeUi(dashLokal, terbaru));

                // Lanjutkan mengambil data real-time langsung dari server bila online
                final DashboardBeranda.Data dashServer = DashboardBeranda.muatDariServer(app, db, pemilik);
                final List<Hasil.RiwayatItem> terbaruSegar = db.panenDao().riwayat(pemilik, 3);
                posting(() -> {
                    perbaruiStatusKoneksi();
                    terapkanRingkasanKeUi(dashServer, terbaruSegar);
                });
            } catch (Exception e) {
                posting(() -> txtKosong.setText("Gagal membaca data: " + e.getMessage()));
            }
        });
    }

    /**
     * Menyinkronkan antrean lokal ke server (kirim panen/foto/GPS, tarik status & master) lalu menarik
     * ringkasan produksi real-time dari database server.
     */
    private void segarkanDariServer(boolean tampilkanToast) {
        if (!isAdded()) return;
        final android.content.Context app = requireContext().getApplicationContext();
        final long pemilik = Sesi.filterPemilik(app);
        TextView txtHeader = cari(R.id.txtSegarkanHeader);
        TextView txtSumber = cari(R.id.txtSumberData);
        com.google.android.material.button.MaterialButton btnDash = cari(R.id.btnSegarkanDashboard);
        if (txtHeader != null) txtHeader.setText("Menyinkronkan...");
        if (txtSumber != null) txtSumber.setText("Menarik data real dari server...");
        if (btnDash != null) {
            btnDash.setEnabled(false);
            btnDash.setText("...");
        }

        io.execute(() -> {
            try {
                Fai26App.tungguSiap();
                com.example.fai26.sync.SyncService.Ringkasan hasilSync =
                        com.example.fai26.sync.SyncRunner.jalankanSinkron(app, true);
                AppDatabase db = AppDatabase.getInstance(app);
                final DashboardBeranda.Data dashServer = DashboardBeranda.muatDariServer(app, db, pemilik);
                final List<Hasil.RiwayatItem> terbaru = db.panenDao().riwayat(pemilik, 3);
                posting(() -> {
                    if (txtHeader != null) txtHeader.setText("Segarkan");
                    if (btnDash != null) {
                        btnDash.setEnabled(true);
                        btnDash.setText("Refresh");
                    }
                    perbaruiStatusKoneksi();
                    terapkanRingkasanKeUi(dashServer, terbaru);
                    if (tampilkanToast) {
                        String info = dashServer.dariServer
                                ? "Data real server berhasil diperbarui (" + hasilSync.teks() + ")"
                                : "Mode offline: menampilkan data lokal HP (" + hasilSync.teks() + ")";
                        Toast.makeText(app, info, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                posting(() -> {
                    if (txtHeader != null) txtHeader.setText("Segarkan");
                    if (btnDash != null) {
                        btnDash.setEnabled(true);
                        btnDash.setText("Refresh");
                    }
                    if (tampilkanToast) {
                        Toast.makeText(app, "Gagal menyegarkan: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void terapkanRingkasanKeUi(DashboardBeranda.Data dash, List<Hasil.RiwayatItem> terbaru) {
        angkaCatatan.setText(String.valueOf(dash.catatanHariIni));
        angkaJanjang.setText(String.valueOf(dash.janjangHariIni));
        angkaTertunda.setText(String.valueOf(dash.tertunda));
        TextView angkaDitolak = cari(R.id.angkaDitolak);
        if (angkaDitolak != null) angkaDitolak.setText(String.valueOf(dash.ditolak));
        tampilkanTerbaru(terbaru);
        DashboardBeranda.tampil(raiz, dash,
                () -> ((MainActivity) requireActivity()).pilihLayar("riwayat"),
                this::muatRingkasan,
                () -> segarkanDariServer(true));
    }

    private void tampilkanTerbaru(List<Hasil.RiwayatItem> terbaru) {
        daftarTerbaru.removeAllViews();
        boolean kosong = terbaru.isEmpty();
        if (boxEmptyState != null) {
            boxEmptyState.setVisibility(kosong ? View.VISIBLE : View.GONE);
        }
        if (!kosong) {
            if (txtKosong != null) txtKosong.setVisibility(View.GONE);
            for (Hasil.RiwayatItem r : terbaru) {
                daftarTerbaru.addView(CatatanRowFactory.buat(requireContext(), r));
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }
}
