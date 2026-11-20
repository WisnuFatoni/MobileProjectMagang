package com.example.fai26;

import android.app.DatePickerDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.fai26.db.AppDatabase;
import com.example.fai26.db.PanenRepository;
import com.example.fai26.lokasi.Lokasi;
import com.example.fai26.db.Waktu;
import com.example.fai26.db.dao.Hasil;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Hasil scan: koreksi jumlah per kelas, pilih TPH dan pemanen, lalu simpan ke database. */
public class HasilActivity extends AppCompatActivity {

    private final TextView[] txtJumlah = new TextView[KelasModel.JUMLAH];
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private int[] jumlahAi;
    private int[] jumlah;
    private float[] deteksi;
    private File foto;
    private TextView txtTotal;
    /** TPH dicari dengan mengetik (ribuan TPH): semua kata yang diketik harus ada di teks pilihan. */
    private MaterialAutoCompleteTextView pilihTph;
    private TextInputLayout tilTph;
    private Hasil.TphPilihan tphTerpilih;
    private Spinner spnPemanen;
    private TextInputEditText edtBrondolan;
    private TextInputEditText edtCatatan;
    private Button btnSimpan;
    private Button btnUlangi;
    private Button btnTanggal;
    private TextView txtJudul;
    /** Tanggal panen (YYYY-MM-DD), bukan tanggal input: bawaan hari ini, boleh diubah ke hari yang sudah lewat. */
    private String tanggalPanen = Waktu.tanggalLokalHariIni();
    /** Tidak null bila layar ini dipakai memperbaiki catatan DITOLAK (Edit dan kirim ulang). */
    private String editPanenId;
    private final long waktuBuka = System.currentTimeMillis();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_hasil);

        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        int[] awal = getIntent().getIntArrayExtra(ScanActivity.EXTRA_JUMLAH);
        jumlahAi = awal != null && awal.length == KelasModel.JUMLAH
                ? awal.clone() : new int[KelasModel.JUMLAH];
        jumlah = jumlahAi.clone();
        deteksi = getIntent().getFloatArrayExtra(ScanActivity.EXTRA_DETEKSI);
        String fotoPath = getIntent().getStringExtra(ScanActivity.EXTRA_FOTO);
        foto = fotoPath == null ? null : new File(fotoPath);

        editPanenId = getIntent().getStringExtra(Ekstra.EXTRA_EDIT_PANEN_ID);
        tampilkanFoto();

        ((TextView) findViewById(R.id.txtWaktu))
                .setText("Waktu: " + Format.waktu(waktuBuka));
        txtTotal = findViewById(R.id.txtTotal);
        pilihTph = findViewById(R.id.spnTph);
        tilTph = findViewById(R.id.tilTphHasil);
        spnPemanen = findViewById(R.id.spnPemanen);
        edtBrondolan = findViewById(R.id.edtBrondolan);
        edtCatatan = findViewById(R.id.edtCatatan);
        btnSimpan = findViewById(R.id.btnSimpan);
        btnUlangi = findViewById(R.id.btnUlangi);
        btnTanggal = findViewById(R.id.btnTanggal);
        txtJudul = findViewById(R.id.txtJudul);
        btnTanggal.setOnClickListener(v -> pilihTanggal());
        tampilkanTanggal();
        if (editPanenId != null) {
            txtJudul.setText("Edit dan Kirim Ulang");
            btnSimpan.setText("Simpan dan kirim ulang");
            btnUlangi.setText("Batal");
        }

        bangunDaftarKelas((LinearLayout) findViewById(R.id.daftarKelas));
        perbarui();
        muatMaster();

        btnUlangi.setOnClickListener(v -> finish());
        findViewById(R.id.btnKembali).setOnClickListener(v -> finish());
        btnSimpan.setOnClickListener(v -> simpan());
    }

    /** Menampilkan foto bukti (hasil scan baru, atau foto tersimpan pada catatan yang diedit). */
    private void tampilkanFoto() {
        View cardFoto = findViewById(R.id.cardFoto);
        ImageView img = findViewById(R.id.imgFoto);
        if (foto != null && foto.exists()) {
            if (cardFoto != null) cardFoto.setVisibility(View.VISIBLE);
            Bitmap bmp = BitmapFactory.decodeFile(foto.getAbsolutePath());
            if (bmp != null) img.setImageBitmap(bmp);
            View.OnClickListener bukaFoto = v ->
                    com.example.fai26.ui.LihatFotoDialog.tampilkan(this, foto.getAbsolutePath(), "Foto Scan TBS");
            img.setOnClickListener(bukaFoto);
            if (cardFoto != null) cardFoto.setOnClickListener(bukaFoto);
        } else {
            if (cardFoto != null) cardFoto.setVisibility(View.GONE);
            else img.setVisibility(View.GONE);
        }
    }

    private void tampilkanTanggal() {
        boolean hariIni = tanggalPanen.equals(Waktu.tanggalLokalHariIni());
        btnTanggal.setText(hariIni ? "Hari ini • " + tanggalPanen : tanggalPanen);
    }

    /** Memilih tanggal panen (tidak boleh di masa depan). */
    private void pilihTanggal() {
        Calendar c = Calendar.getInstance();
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(tanggalPanen);
            if (d != null) c.setTime(d);
        } catch (ParseException ignored) {
            // tetap memakai hari ini
        }
        DatePickerDialog dlg = new DatePickerDialog(this, (v, y, m, h) -> {
            tanggalPanen = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, h);
            tampilkanTanggal();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dlg.getDatePicker().setMaxDate(System.currentTimeMillis());
        dlg.show();
    }

    /** Mengisi pilihan TPH dan pemanen dari database (di thread latar). */
    private void muatMaster() {
        io.execute(() -> {
            try {
                Fai26App.tungguSiap();
                AppDatabase db = AppDatabase.getInstance(getApplicationContext());
                List<Hasil.TphPilihan> tph = db.masterDao().daftarTph();
                List<Hasil.PemanenPilihan> pemanen = new ArrayList<>();
                Hasil.PemanenPilihan kosong = new Hasil.PemanenPilihan();
                kosong.pemanenId = 0;
                kosong.nama = "Pilih pemanen...";
                pemanen.add(kosong);
                pemanen.addAll(db.masterDao().daftarPemanen());
                // Mode edit: muat catatan yang DITOLAK (TPH-nya dikunci, jadi tampilkan walau sudah tidak ada di master).
                final com.example.fai26.db.entity.PanenHeader hEdit =
                        editPanenId == null ? null : db.panenDao().headerById(editPanenId);
                final List<Hasil.GradingEdit> gEdit = hEdit == null ? null : db.panenDao().gradingEdit(editPanenId);
                final Long pEdit = hEdit == null ? null : db.panenDao().pemanenPanen(editPanenId);
                final String fotoEdit = hEdit == null ? null : db.panenDao().fotoPathPanen(editPanenId);
                if (hEdit != null) {
                    boolean ada = false;
                    for (Hasil.TphPilihan t : tph) if (t.tphId == hEdit.tphId) ada = true;
                    if (!ada) {
                        Hasil.TphPilihan t = db.masterDao().tphPilihanById(hEdit.tphId);
                        if (t != null) tph.add(0, t);
                    }
                }
                main.post(() -> {
                    if (editPanenId != null) {
                        if (hEdit == null || !com.example.fai26.db.PanenRepository.bisaDiubah(hEdit.status)) {
                            Toast.makeText(this, "Catatan ini tidak bisa diedit lagi (sudah disetujui krani)", Toast.LENGTH_LONG).show();
                            finish();
                            return;
                        }
                        tanggalPanen = hEdit.tanggalPanen;
                        tampilkanTanggal();
                        jumlahAi = new int[KelasModel.JUMLAH];
                        jumlah = new int[KelasModel.JUMLAH];
                        for (Hasil.GradingEdit g : gEdit) {
                            if (g.label >= 0 && g.label < KelasModel.JUMLAH) {
                                jumlahAi[(int) g.label] = (int) g.jmlAi;
                                jumlah[(int) g.label] = (int) g.jmlFinal;
                            }
                        }
                        perbarui();
                        if (hEdit.brondolanKg > 0) edtBrondolan.setText(String.valueOf(hEdit.brondolanKg));
                        if (hEdit.catatan != null) edtCatatan.setText(hEdit.catatan);
                        if (fotoEdit != null) {
                            foto = new File(fotoEdit);
                            tampilkanFoto();
                        }
                        if (hEdit.pesanServer != null && !hEdit.pesanServer.isEmpty()) {
                            Toast.makeText(this, "Alasan ditolak: " + hEdit.pesanServer, Toast.LENGTH_LONG).show();
                        }
                    }
                    pilihTph.setAdapter(new PencarianAdapter<>(this, tph));
                    pilihTph.setThreshold(1);
                    pilihTph.setOnClickListener(v -> pilihTph.showDropDown());
                    // Yang dipilih adalah item yang diketuk (urutan berubah saat daftar disaring).
                    pilihTph.setOnItemClickListener((pa, v, pos, id) -> {
                        tphTerpilih = (Hasil.TphPilihan) pa.getItemAtPosition(pos);
                        tilTph.setError(null);
                        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                                getSystemService(INPUT_METHOD_SERVICE);
                        if (imm != null) imm.hideSoftInputFromWindow(pilihTph.getWindowToken(), 0);
                        pilihTph.clearFocus();
                    });
                    // Teks diubah tanpa memilih item: pilihan sebelumnya tidak berlaku lagi.
                    pilihTph.addTextChangedListener(new android.text.TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                        }

                        @Override
                        public void onTextChanged(CharSequence s, int a, int b, int c) {
                            if (tphTerpilih != null && !String.valueOf(s).equals(tphTerpilih.toString())) tphTerpilih = null;
                        }

                        @Override
                        public void afterTextChanged(android.text.Editable s) {
                        }
                    });
                    spnPemanen.setAdapter(new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_dropdown_item, pemanen));
                    // Pilihan dari beranda sudah terisi; krani masih bisa mengoreksinya.
                    long tphDipilih = hEdit != null ? hEdit.tphId : getIntent().getLongExtra(Ekstra.EXTRA_TPH_ID, -1L);
                    for (int i = 0; i < tph.size(); i++) {
                        if (tph.get(i).tphId == tphDipilih) {
                            tphTerpilih = tph.get(i);
                            pilihTph.setText(tphTerpilih.toString(), false);
                        }
                    }
                    if (hEdit != null) { // TPH catatan yang diperbaiki tidak diganti
                        tilTph.setEnabled(false);
                        pilihTph.setEnabled(false);
                    }
                    long pemanenDipilih = hEdit != null ? (pEdit == null ? 0L : pEdit)
                            : getIntent().getLongExtra(Ekstra.EXTRA_PEMANEN_ID, 0L);
                    for (int i = 0; i < pemanen.size(); i++) {
                        if (pemanen.get(i).pemanenId == pemanenDipilih) spnPemanen.setSelection(i);
                    }
                    if (tph.isEmpty()) {
                        Toast.makeText(this, "Data TPH belum ada", Toast.LENGTH_LONG).show();
                    } else {
                        btnSimpan.setEnabled(true);
                    }
                });
            } catch (Exception e) {
                main.post(() -> Toast.makeText(this, "Gagal memuat data TPH: " + e.getMessage(),
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    private void bangunDaftarKelas(LinearLayout wadah) {
        float d = getResources().getDisplayMetrics().density;
        for (int i = 0; i < KelasModel.JUMLAH; i++) {
            final int idx = i;
            LinearLayout baris = new LinearLayout(this);
            baris.setOrientation(LinearLayout.HORIZONTAL);
            baris.setGravity(Gravity.CENTER_VERTICAL);
            baris.setPadding(0, (int) (4 * d), 0, (int) (4 * d));

            LinearLayout nama = new LinearLayout(this);
            nama.setOrientation(LinearLayout.VERTICAL);
            TextView tampil = new TextView(this);
            tampil.setText(KelasModel.TAMPIL[i]);
            tampil.setTextSize(16);
            TextView asli = new TextView(this);
            asli.setText(KelasModel.NAMA[i]);
            asli.setTextSize(12);
            nama.addView(tampil);
            nama.addView(asli);
            baris.addView(nama, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            MaterialButton kurang = tombolKecil("−");
            kurang.setOnClickListener(v -> {
                if (jumlah[idx] > 0) jumlah[idx]--;
                perbarui();
            });
            TextView angka = new TextView(this);
            angka.setTextSize(18);
            angka.setGravity(Gravity.CENTER);
            txtJumlah[i] = angka;
            MaterialButton tambah = tombolKecil("+");
            tambah.setOnClickListener(v -> {
                jumlah[idx]++;
                perbarui();
            });

            baris.addView(kurang);
            baris.addView(angka, new LinearLayout.LayoutParams((int) (40 * d), ViewGroup.LayoutParams.WRAP_CONTENT));
            baris.addView(tambah);
            wadah.addView(baris);
        }
    }

    private MaterialButton tombolKecil(String teks) {
        float d = getResources().getDisplayMetrics().density;
        // Bulat hijau seperti tombol jumlah di referensi: tampak 36dp, area sentuh 48dp.
        MaterialButton b = new MaterialButton(this);
        b.setText(teks);
        b.setTextSize(20);
        b.setInsetTop((int) (6 * d));
        b.setInsetBottom((int) (6 * d));
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setCornerRadius((int) (18 * d));
        b.setLayoutParams(new LinearLayout.LayoutParams((int) (48 * d), (int) (48 * d)));
        return b;
    }

    private void perbarui() {
        int total = 0;
        for (int i = 0; i < KelasModel.JUMLAH; i++) {
            txtJumlah[i].setText(String.valueOf(jumlah[i]));
            total += jumlah[i];
        }
        txtTotal.setText("Jumlah tandan: " + total);
    }

    private void simpan() {
        Hasil.TphPilihan tph = tphTerpilih;
        if (tph == null) {
            tilTph.setError("Pilih TPH dari daftar");
            Toast.makeText(this, "Pilih TPH dulu", Toast.LENGTH_SHORT).show();
            return;
        }
        double brondolan = 0;
        String teksBrondolan = String.valueOf(edtBrondolan.getText()).trim();
        if (!teksBrondolan.isEmpty()) {
            try {
                brondolan = Double.parseDouble(teksBrondolan);
            } catch (NumberFormatException e) {
                edtBrondolan.setError("Angka tidak valid");
                return;
            }
            if (brondolan < 0) {
                edtBrondolan.setError("Tidak boleh negatif");
                return;
            }
        }
        Hasil.PemanenPilihan pemanen = (Hasil.PemanenPilihan) spnPemanen.getSelectedItem();
        // Satu TPH hanya untuk satu pemanen: tanpa pemanen, catatan tidak bisa diimpor ke ERP.
        if (pemanen == null || pemanen.pemanenId == 0) {
            Toast.makeText(this, "Pilih pemanen dulu (satu TPH hanya untuk satu pemanen)", Toast.LENGTH_LONG).show();
            spnPemanen.performClick();
            return;
        }
        String catatan = String.valueOf(edtCatatan.getText()).trim();

        PanenRepository.Permintaan r = new PanenRepository.Permintaan();
        r.unitId = tph.unitId;
        r.blokId = tph.blokId;
        r.tphId = tph.tphId;
        r.pemanenId = pemanen.pemanenId;
        r.tanggalPanen = tanggalPanen;
        r.brondolanKg = brondolan;
        r.catatan = catatan.isEmpty() ? null : catatan;
        r.jumlahAi = jumlahAi.clone();
        r.jumlahFinal = jumlah.clone();
        r.foto = foto;
        r.deteksi = deteksi;
        String sumber = getIntent().getStringExtra(Ekstra.EXTRA_SUMBER);
        r.sumberTph = sumber == null ? "MANUAL" : sumber;
        if (getIntent().hasExtra(Ekstra.EXTRA_GPS_LAT)) {
            r.gpsLat = getIntent().getDoubleExtra(Ekstra.EXTRA_GPS_LAT, 0);
            r.gpsLon = getIntent().getDoubleExtra(Ekstra.EXTRA_GPS_LON, 0);
            if (getIntent().hasExtra(Ekstra.EXTRA_GPS_AKURASI)) {
                r.gpsAkurasiM = getIntent().getDoubleExtra(Ekstra.EXTRA_GPS_AKURASI, 0);
            }
            r.gpsWaktu = getIntent().getStringExtra(Ekstra.EXTRA_GPS_WAKTU);
            r.gpsMock = getIntent().getBooleanExtra(Ekstra.EXTRA_GPS_MOCK, false);
        }

        btnSimpan.setEnabled(false);
        btnUlangi.setEnabled(false);
        io.execute(() -> {
            try {
                if (editPanenId != null) {
                    Fai26App.repository(getApplicationContext()).perbaikiDitolak(editPanenId, r);
                } else {
                    Fai26App.repository(getApplicationContext()).simpan(r);
                }
                main.post(() -> {
                    String lokasiTeks = Lokasi.perluPerhatian(r.statusLokasi)
                            ? "\nLokasi: " + Lokasi.teks(r.statusLokasi) + " (ditandai untuk krani)" : "";
                    String pesan;
                    if (editPanenId != null) {
                        pesan = r.disetujuiOtomatis ? "Perubahan tersimpan. Dikirim ulang begitu ada sinyal"
                                : "Perubahan tersimpan. Dikirim ulang begitu ada sinyal, lalu menunggu persetujuan krani";
                    } else {
                        pesan = r.disetujuiOtomatis ? "Tersimpan dan disetujui. Terkirim begitu ada sinyal" + lokasiTeks
                                : "Tersimpan di HP, menunggu persetujuan krani" + lokasiTeks;
                    }
                    Toast.makeText(this, pesan, Toast.LENGTH_LONG).show();
                    // Mandor yang menginput tidak punya langkah persetujuan lagi: langsung kirim bila ada sinyal.
                    if (r.disetujuiOtomatis) com.example.fai26.sync.SyncJadwal.segera(getApplicationContext());
                    setResult(RESULT_OK);
                    finish();
                });
            } catch (PanenRepository.PanenDuplikatException e) {
                main.post(() -> {
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                            .setTitle("Catatan ganda")
                            .setMessage(e.getMessage())
                            .setPositiveButton("Mengerti", null)
                            .show();
                    btnSimpan.setEnabled(true);
                    btnUlangi.setEnabled(true);
                });
            } catch (Exception e) {
                main.post(() -> {
                    Toast.makeText(this, "Gagal menyimpan: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    btnSimpan.setEnabled(true);
                    btnUlangi.setEnabled(true);
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }
}
