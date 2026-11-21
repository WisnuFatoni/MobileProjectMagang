package com.example.fai26;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.example.fai26.auth.FotoProfilLokal;
import com.example.fai26.auth.GantiPasswordService;
import com.example.fai26.ui.AvatarProfil;
import com.example.fai26.auth.Sesi;
import com.example.fai26.sync.HttpServerApi;
import com.example.fai26.sync.MasterSync;
import com.example.fai26.sync.PengaturanSync;
import com.example.fai26.sync.SyncRunner;
import com.example.fai26.sync.SyncService;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import android.widget.Toast;
import com.example.fai26.db.AppDatabase;
import com.example.fai26.db.Waktu;
import com.example.fai26.db.entity.AppUser;
import com.google.android.material.color.MaterialColors;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Profil pengguna, status login offline, info perangkat, dan tombol keluar. */
public class AkunFragment extends Fragment {

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private LinearLayout daftarInfo;
    private ActivityResultLauncher<Uri> ambilKamera;
    private ActivityResultLauncher<String> pilihGaleri;
    private ActivityResultLauncher<String> mintaIzinKamera;
    private File fotoSementara;
    private ActivityResultLauncher<Intent> pemotong;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pemotong = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), hasil -> {
            if (fotoSementara != null) {
                //noinspection ResultOfMethodCallIgnored
                fotoSementara.delete();
                fotoSementara = null;
            }
            if (hasil.getResultCode() == android.app.Activity.RESULT_OK && isAdded()) {
                final Context app = requireContext().getApplicationContext();
                Toast.makeText(app, "Foto profil disimpan", Toast.LENGTH_SHORT).show();
                muat();
                SyncRunner.jalankan(app, true, null);
            }
        });
        ambilKamera = registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
            if (ok && fotoSementara != null) {
                bukaPemotong(androidx.core.content.FileProvider.getUriForFile(requireContext(),
                        requireContext().getPackageName() + ".fotoipd", fotoSementara));
            } else if (fotoSementara != null) {
                //noinspection ResultOfMethodCallIgnored
                fotoSementara.delete();
            }
        });
        pilihGaleri = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) bukaPemotong(uri);
        });
        mintaIzinKamera = registerForActivityResult(new ActivityResultContracts.RequestPermission(), ok -> {
            if (ok) bukaKamera();
            else Toast.makeText(requireContext(), "Izin kamera diperlukan untuk memotret foto profil", Toast.LENGTH_LONG).show();
        });
    }

    /** Pilihan sumber foto profil: kamera, galeri, atau hapus. */
    private void pilihSumberFoto() {
        final Context c = requireContext();
        final boolean ada = FotoProfilLokal.ada(c, Sesi.userId(c));
        final String[] pilihan = ada ? new String[]{"Ambil foto (kamera)", "Pilih dari galeri", "Hapus foto"} : new String[]{"Ambil foto (kamera)", "Pilih dari galeri"};
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(c)
                .setTitle("Foto profil")
                .setItems(pilihan, (d, i) -> {
                    if (i == 0) {
                        if (androidx.core.content.ContextCompat.checkSelfPermission(c, android.Manifest.permission.CAMERA)
                                == android.content.pm.PackageManager.PERMISSION_GRANTED) bukaKamera();
                        else mintaIzinKamera.launch(android.Manifest.permission.CAMERA);
                    } else if (i == 1) {
                        pilihGaleri.launch("image/*");
                    } else {
                        final long uid = Sesi.userId(c);
                        FotoProfilLokal.hapus(c, uid);
                        muat();
                        SyncRunner.jalankan(c, true, null);
                        Toast.makeText(c, "Foto profil dihapus", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void bukaKamera() {
        try {
            File dir = new File(requireContext().getCacheDir(), "foto_ipd_mentah");
            if (!dir.exists() && !dir.mkdirs()) throw new java.io.IOException("folder");
            fotoSementara = new File(dir, "profil_" + System.currentTimeMillis() + ".jpg");
            Uri uri = androidx.core.content.FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".fotoipd", fotoSementara);
            ambilKamera.launch(uri);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Kamera tidak bisa dibuka: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** Membuka pemotong foto (geser, zoom, putar) untuk gambar yang baru dipilih dari kamera atau galeri. */
    private void bukaPemotong(Uri uri) {
        Intent i = new Intent(requireContext(), FotoProfilCropActivity.class);
        i.setData(uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        pemotong.launch(i);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View raiz = inflater.inflate(R.layout.fragment_akun, container, false);
        daftarInfo = raiz.findViewById(R.id.daftarInfo);

        View header = raiz.findViewById(R.id.header);
        final int paddingAtas = header.getPaddingTop();
        final float d = getResources().getDisplayMetrics().density;
        ViewCompat.setOnApplyWindowInsetsListener(raiz, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            header.setPadding(header.getPaddingLeft(), paddingAtas + bars.top,
                    header.getPaddingRight(), header.getPaddingBottom());
            v.setPadding(bars.left, 0, bars.right, bars.bottom + (int) (112 * d));
            return insets;
        });

        pasangSinkron(raiz);
        raiz.findViewById(R.id.boxAvatar).setOnClickListener(v -> pilihSumberFoto());

        raiz.findViewById(R.id.btnGantiPassword).setOnClickListener(v -> tampilkanGantiPassword());
        raiz.findViewById(R.id.btnKeluar).setOnClickListener(v ->
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Keluar dari akun?")
                        .setMessage("Yakin ingin keluar dari aplikasi? Catatan yang belum terkirim tetap tersimpan di HP.")
                        .setNegativeButton("Batal", null)
                        .setPositiveButton("Keluar", (dlg, w) -> {
                            Sesi.keluar(requireContext());
                            startActivity(new Intent(requireContext(), LoginActivity.class));
                            requireActivity().finish();
                        })
                        .show());
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
        if (!isAdded() || getView() == null) return;
        muatSinkron();
        final Context app = requireContext().getApplicationContext();
        final long userId = Sesi.userId(app);
        final long pemilik = Sesi.filterPemilik(app);
        final String nama = Sesi.nama(app);
        final String peran = Sesi.peran(app);

        TextView txtNama = getView().findViewById(R.id.txtNama);
        TextView txtPeran = getView().findViewById(R.id.txtPeran);
        TextView txtInisial = getView().findViewById(R.id.txtInisial);
        txtNama.setText(nama);
        txtPeran.setText(peran.isEmpty() ? "Pengguna" : peran);
        txtInisial.setText(nama.isEmpty() ? "?" : nama.substring(0, 1).toUpperCase());
        AvatarProfil.pasang(app, txtInisial, getView().findViewById(R.id.imgAvatar));

        io.execute(() -> {
            try {
                Fai26App.tungguSiap();
                AppDatabase db = AppDatabase.getInstance(app);
                AppUser u = db.akunDao().cariId(userId);
                long tertunda = db.panenDao().jumlahBelumTerkirim(pemilik);
                String model = db.panenDao().namaModelAktif();
                String versi = app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionName;
                String berlaku = u == null || u.offlineBerlakuSampai == null ? "-"
                        : Format.waktu(Waktu.keMillis(u.offlineBerlakuSampai));
                String username = u == null ? "-" : u.username;
                String perangkat = Build.MANUFACTURER + " " + Build.MODEL;
                String idPerangkat = Fai26App.deviceId(app);
                main.post(() -> {
                    if (!isAdded() || getView() == null) return;
                    daftarInfo.removeAllViews();
                    baris("Username", username);
                    baris("Login offline sampai", berlaku);
                    baris("Belum terkirim", tertunda + " catatan");
                    baris("Perangkat", perangkat);
                    baris("ID perangkat", idPerangkat.substring(0, Math.min(8, idPerangkat.length())) + "…");
                    baris("Model AI", model == null ? "-" : model);
                    baris("Versi aplikasi", versi);
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (!isAdded()) return;
                    daftarInfo.removeAllViews();
                    baris("Galat", e.getMessage());
                });
            }
        });
    }

    // ---- Ganti password -------------------------------------------------------------------------

    /** Dialog ganti password. Server yang memeriksa password lama; dialog tetap terbuka bila ada yang salah. */
    private void tampilkanGantiPassword() {
        final View isi = getLayoutInflater().inflate(R.layout.dialog_ganti_password, null);
        final TextInputLayout tilLama = isi.findViewById(R.id.tilPwLama);
        final TextInputLayout tilBaru = isi.findViewById(R.id.tilPwBaru);
        final TextInputLayout tilUlang = isi.findViewById(R.id.tilPwUlang);
        final TextInputEditText edtLama = isi.findViewById(R.id.edtPwLama);
        final TextInputEditText edtBaru = isi.findViewById(R.id.edtPwBaru);
        final TextInputEditText edtUlang = isi.findViewById(R.id.edtPwUlang);
        final androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Ganti password")
                .setView(isi)
                .setPositiveButton("Simpan", null) // diganti di bawah supaya tidak menutup sendiri saat ada yang salah
                .setNegativeButton("Batal", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            final String lama = String.valueOf(edtLama.getText());
            final String baru = String.valueOf(edtBaru.getText());
            final String ulang = String.valueOf(edtUlang.getText());
            tilLama.setError(null);
            tilBaru.setError(null);
            tilUlang.setError(null);
            String salah = GantiPasswordService.periksa(lama, baru, ulang);
            if (salah != null) {
                TextInputLayout sasaran = lama.isEmpty() ? tilLama : baru.equals(ulang) || baru.length() < GantiPasswordService.PANJANG_MIN
                        || baru.length() > GantiPasswordService.PANJANG_MAKS || baru.equals(lama) ? tilBaru : tilUlang;
                sasaran.setError(salah);
                return;
            }
            final android.widget.Button tombol = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            tombol.setEnabled(false);
            tombol.setText("MENYIMPAN...");
            final Context app = requireContext().getApplicationContext();
            io.execute(() -> {
                GantiPasswordService.Hasil h;
                try {
                    Fai26App.tungguSiap();
                    PengaturanSync pref = new PengaturanSync(app);
                    h = new GantiPasswordService(Fai26App.auth(app),
                            pref.serverSudahDiatur() ? new HttpServerApi(pref.serverUrl()) : null, pref).ganti(lama, baru, ulang);
                } catch (Exception e) {
                    h = new GantiPasswordService.Hasil(false, "Gagal: " + e.getMessage());
                }
                final GantiPasswordService.Hasil hasil = h;
                main.post(() -> {
                    if (!isAdded()) return;
                    if (hasil.berhasil) {
                        dialog.dismiss();
                        Toast.makeText(requireContext(), hasil.pesan, Toast.LENGTH_LONG).show();
                    } else {
                        tombol.setEnabled(true);
                        tombol.setText("Simpan");
                        tilLama.setError(hasil.pesan);
                    }
                });
            });
        }));
        dialog.show();
    }

    // ---- Sinkronisasi ---------------------------------------------------------------------------

    private void pasangSinkron(View raiz) {
        final PengaturanSync pref = new PengaturanSync(requireContext());
        final TextInputEditText edt = raiz.findViewById(R.id.edtServer);
        final TextInputLayout til = raiz.findViewById(R.id.tilServer);
        edt.setText(pref.serverUrl());

        final View boxServer = raiz.findViewById(R.id.boxServer);
        final MaterialButton lanjutan = raiz.findViewById(R.id.btnServerLanjutan);
        // Alamat server urusan teknis: hanya admin (hak master.kelola) yang melihat dan mengubahnya.
        final boolean admin = com.example.fai26.auth.Sesi.punya(requireContext(), "master.kelola");
        lanjutan.setVisibility(admin ? View.VISIBLE : View.GONE);
        if (!admin) boxServer.setVisibility(View.GONE);
        lanjutan.setOnClickListener(v -> {
            boolean buka = boxServer.getVisibility() != View.VISIBLE;
            boxServer.setVisibility(buka ? View.VISIBLE : View.GONE);
            lanjutan.setText(buka ? "Tutup pengaturan server" : "Pengaturan server (lanjutan)");
        });

        raiz.findViewById(R.id.btnSimpanServer).setOnClickListener(v -> {
            String url = String.valueOf(edt.getText()).trim();
            if (!url.isEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) {
                til.setError("Harus diawali http:// atau https://");
                return;
            }
            til.setError(null);
            pref.aturServerUrl(url);
            Toast.makeText(requireContext(), url.isEmpty() ? "Alamat server dikosongkan" : "Alamat server disimpan",
                    Toast.LENGTH_SHORT).show();
            muatSinkron();
        });

        final MaterialButton kirim = raiz.findViewById(R.id.btnKirimSekarang);
        kirim.setOnClickListener(v -> {
            kirim.setEnabled(false);
            kirim.setText("Mengirim...");
            SyncRunner.jalankan(requireContext(), true, hasil -> {
                if (!isAdded()) return;
                kirim.setEnabled(true);
                kirim.setText("Kirim sekarang");
                Toast.makeText(requireContext(), hasil.teks(), Toast.LENGTH_LONG).show();
                muatSinkron();
            });
        });

        raiz.findViewById(R.id.btnKirimUlang).setOnClickListener(v -> io.execute(() -> {
            int n = SyncService.kirimUlangYangGagal(AppDatabase.getInstance(requireContext().getApplicationContext()));
            main.post(() -> {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), n + " dijadwalkan ulang", Toast.LENGTH_SHORT).show();
                kirim.performClick();
            });
        }));
    }

    /** Memperbarui jumlah antrean dan waktu sinkronisasi terakhir di kartu Sinkronisasi. */
    private void muatSinkron() {
        if (!isAdded() || getView() == null) return;
        final Context app = requireContext().getApplicationContext();
        final PengaturanSync pref = new PengaturanSync(app);
        io.execute(() -> {
            try {
                Fai26App.tungguSiap();
                AppDatabase db = AppDatabase.getInstance(app);
                long[] n = SyncService.hitungAntrean(db);
                String galat = n[1] > 0 ? db.syncDao().galatGagalTerbaru() : null;
                main.post(() -> {
                    if (!isAdded() || getView() == null) return;
                    TextView a = getView().findViewById(R.id.txtSinkronAntrean);
                    TextView w = getView().findViewById(R.id.txtSinkronTerakhir);
                    StringBuilder s = new StringBuilder();
                    s.append(n[0] == 0 && n[1] == 0 ? "Semua sudah terkirim" : n[0] + " menunggu dikirim");
                    if (n[1] > 0) s.append(" • ").append(n[1]).append(" gagal");
                    if (n[2] > 0) s.append(" • ").append(n[2]).append(" audit belum terkirim");
                    a.setText(s);
                    StringBuilder t = new StringBuilder();
                    if (!pref.serverSudahDiatur()) t.append("Alamat server belum diatur (buka Pengaturan server). ");
                    else if (!pref.adaToken()) t.append("Belum login online (login saat ada sinyal). ");
                    if (pref.sinkronTerakhir() > 0) {
                        t.append("Terakhir sinkron: ").append(Format.waktu(pref.sinkronTerakhir()));
                    } else if (pref.serverSudahDiatur() && pref.adaToken()) {
                        t.append("Belum pernah sinkron.");
                    }
                    if (galat != null) t.append("\nGagal: ").append(galat);
                    w.setText(t.toString().trim());
                    getView().findViewById(R.id.btnKirimUlang).setVisibility(n[1] > 0 ? View.VISIBLE : View.GONE);
                });
            } catch (Exception ignored) {
                // kartu tetap tampil dengan isi lama
            }
        });
    }

    private void baris(String label, String nilai) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout b = new LinearLayout(requireContext());
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setPadding(0, (int) (7 * d), 0, (int) (7 * d));

        TextView l = new TextView(requireContext());
        l.setText(label);
        l.setTextSize(14);
        l.setTextColor(MaterialColors.getColor(l, com.google.android.material.R.attr.colorOnSurfaceVariant));
        b.addView(l, new LinearLayout.LayoutParams((int) (150 * d), ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView v = new TextView(requireContext());
        v.setText(nilai);
        v.setTextSize(15);
        v.setTypeface(v.getTypeface(), android.graphics.Typeface.BOLD);
        v.setTextColor(MaterialColors.getColor(v, com.google.android.material.R.attr.colorOnSurface));
        b.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        daftarInfo.addView(b);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }
}
