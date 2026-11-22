package com.example.fai26;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.example.fai26.auth.Sesi;
import com.example.fai26.db.Waktu;
import com.example.fai26.lokasi.Lokasi;
import com.example.fai26.sync.ApiException;
import com.example.fai26.sync.HttpServerApi;
import com.example.fai26.sync.PengaturanSync;
import com.example.fai26.sync.PersetujuanServer;
import com.example.fai26.sync.ServerApi;
import com.example.fai26.ui.LihatFotoDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Persetujuan krani lewat server: daftar catatan yang dikirim mandor dari HP-nya masing-masing dan menunggu disetujui.
 * Hanya muncul bagi pengguna yang memegang panen.setujui. Daftar diambil dari server (butuh sinyal), jadi krani tidak perlu
 * memegang HP mandor. Setiap kartu memperlihatkan isi catatan supaya diperiksa sebelum disetujui atau ditolak (dengan alasan).
 */
public class PersetujuanFragment extends Fragment {

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    /** Unduhan foto dipisah dari aksi setujui/tolak supaya tombol tidak menunggu foto selesai diunduh. */
    private final ExecutorService ioFoto = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());

    private LinearLayout daftar;
    private TextView txtJumlah;
    private TextView txtKosong;
    private MaterialButton btnSemua;
    private List<ServerApi.Menunggu> terkini = new ArrayList<>();
    private boolean memuat;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View raiz = inflater.inflate(R.layout.fragment_persetujuan, container, false);
        daftar = raiz.findViewById(R.id.daftar);
        txtJumlah = raiz.findViewById(R.id.txtJumlah);
        txtKosong = raiz.findViewById(R.id.txtKosong);
        btnSemua = raiz.findViewById(R.id.btnSetujuiSemua);

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

        btnSemua.setOnClickListener(v -> {
            final List<String> ids = new ArrayList<>();
            for (ServerApi.Menunggu m : terkini) ids.add(m.id);
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Setujui semua " + ids.size() + " catatan?")
                    .setMessage("Semua catatan di daftar ini akan disetujui, dikunci, dan siap ditarik ERP. Pastikan jumlah, lokasi, dan fotonya sudah diperiksa.")
                    .setNegativeButton("Batal", null)
                    .setPositiveButton("Setujui semua", (dlg, w) -> setujui(ids))
                    .show();
        });
        txtKosong.setOnClickListener(v -> muat()); // ketuk pesan galat untuk mencoba lagi
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

    private PersetujuanServer server(Context app) {
        PengaturanSync pref = new PengaturanSync(app);
        return new PersetujuanServer(new HttpServerApi(pref.serverUrl()), pref);
    }

    private void muat() {
        if (!isAdded() || memuat) return;
        memuat = true;
        final Context app = requireContext().getApplicationContext();
        txtJumlah.setText("Memuat dari server...");
        io.execute(() -> {
            List<ServerApi.Menunggu> hasil = null;
            String galat = null;
            try {
                hasil = server(app).muat();
            } catch (ApiException e) {
                galat = e.kode == 0
                        ? "Butuh sinyal untuk memuat daftar persetujuan. " + e.getMessage() + "\nKetuk untuk mencoba lagi."
                        : e.getMessage() + "\nKetuk untuk mencoba lagi.";
            } catch (Exception e) {
                galat = "Gagal memuat: " + e.getMessage() + "\nKetuk untuk mencoba lagi.";
            }
            final List<ServerApi.Menunggu> item = hasil;
            final String pesan = galat;
            main.post(() -> {
                memuat = false;
                if (!isAdded()) return;
                if (pesan != null) {
                    tampilkanGalat(pesan);
                } else {
                    tampilkan(item);
                }
            });
        });
    }

    private void tampilkanGalat(String pesan) {
        terkini = new ArrayList<>();
        daftar.removeAllViews();
        btnSemua.setVisibility(View.GONE);
        txtJumlah.setText("Belum bisa dimuat");
        txtKosong.setText(pesan);
        txtKosong.setVisibility(View.VISIBLE);
    }

    private void tampilkan(List<ServerApi.Menunggu> item) {
        terkini = item;
        daftar.removeAllViews();
        for (ServerApi.Menunggu m : item) daftar.addView(kartu(m));
        bersihkanCacheFoto(item);
        txtJumlah.setText(item.isEmpty() ? "Semua sudah diperiksa" : item.size() + " catatan");
        txtKosong.setText("Tidak ada catatan yang menunggu persetujuan.");
        txtKosong.setVisibility(item.isEmpty() ? View.VISIBLE : View.GONE);
        btnSemua.setVisibility(item.size() > 1 ? View.VISIBLE : View.GONE);
        btnSemua.setText("Setujui semua (" + item.size() + ")");
    }

    private View kartu(ServerApi.Menunggu m) {
        final Context c = requireContext();
        final float d = getResources().getDisplayMetrics().density;

        MaterialCardView kartu = new MaterialCardView(c);
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        klp.setMargins(0, 0, 0, (int) (12 * d));
        kartu.setLayoutParams(klp);

        LinearLayout isi = new LinearLayout(c);
        isi.setOrientation(LinearLayout.VERTICAL);
        isi.setPadding((int) (18 * d), (int) (16 * d), (int) (18 * d), (int) (16 * d));

        TextView judul = new TextView(c);
        judul.setText("Blok " + nilai(m.blokKode) + " • TPH " + nilai(m.tphKode) + "  —  " + m.totalJanjang + " janjang");
        judul.setTextSize(17);
        judul.setTypeface(ResourcesCompat.getFont(c, R.font.outfit_semibold));
        isi.addView(judul);

        StringBuilder meta = new StringBuilder();
        meta.append("Dicatat ").append(m.pencatatNama).append(" • ").append(Format.waktu(m.dibuatMillis));
        meta.append("\nTanggal panen ").append(m.tanggalPanen);
        if (m.pemanenNama != null) meta.append("\nPemanen: ").append(m.pemanenNama);
        TextView txtMeta = new TextView(c);
        txtMeta.setText(meta);
        txtMeta.setTextSize(13);
        txtMeta.setPadding(0, (int) (4 * d), 0, (int) (8 * d));
        isi.addView(txtMeta);

        // Tanda lokasi: hanya muncul bila ada yang perlu diperhatikan.
        if (Lokasi.perluPerhatian(m.statusLokasi) || m.gpsMock) {
            StringBuilder t = new StringBuilder();
            if (Lokasi.perluPerhatian(m.statusLokasi)) {
                t.append("Lokasi: ").append(Lokasi.teks(m.statusLokasi));
                if (m.jarakKeTphM != null) t.append(" (").append(Math.round(m.jarakKeTphM)).append(" m dari TPH)");
            }
            if (m.gpsMock) {
                if (t.length() > 0) t.append("\n");
                t.append("Terdeteksi lokasi palsu");
            }
            TextView txtLokasi = new TextView(c);
            txtLokasi.setText(t);
            txtLokasi.setTextSize(14);
            txtLokasi.setTypeface(ResourcesCompat.getFont(c, R.font.outfit_semibold));
            txtLokasi.setTextColor(MaterialColors.getColor(txtLokasi, androidx.appcompat.R.attr.colorError));
            txtLokasi.setPadding(0, 0, 0, (int) (8 * d));
            isi.addView(txtLokasi);
        }

        // Rincian per kelas, brondolan, dan catatan.
        StringBuilder r = new StringBuilder();
        for (Map.Entry<String, Long> e : m.grading.entrySet()) {
            if (r.length() > 0) r.append("   ");
            r.append(namaKelas(e.getKey())).append(' ').append(e.getValue());
        }
        if (m.brondolanKg > 0) {
            if (r.length() > 0) r.append("\n");
            r.append("Brondolan ").append(Format.desimal(m.brondolanKg)).append(" kg");
        }
        if (m.catatan != null && !m.catatan.trim().isEmpty()) {
            if (r.length() > 0) r.append("\n");
            r.append("Catatan: ").append(m.catatan.trim());
        }
        if (r.length() > 0) {
            TextView txtRinci = new TextView(c);
            txtRinci.setText(r);
            txtRinci.setTextSize(14);
            txtRinci.setPadding(0, 0, 0, (int) (12 * d));
            isi.addView(txtRinci);
        }

        isi.addView(bagianFoto(m));

        LinearLayout tombol = new LinearLayout(c);
        tombol.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton tolak = new MaterialButton(c, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        tolak.setText("Tolak");
        tolak.setOnClickListener(v -> dialogTolak(m));
        MaterialButton setuju = new MaterialButton(c);
        setuju.setText("Setujui");
        setuju.setOnClickListener(v -> new MaterialAlertDialogBuilder(c)
                .setTitle("Setujui catatan ini?")
                .setMessage("Blok " + nilai(m.blokKode) + " • TPH " + nilai(m.tphKode) + ", " + m.totalJanjang + " janjang dari "
                        + m.pencatatNama + ". Setelah disetujui catatan dikunci dan siap ditarik ERP.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Setujui", (dlg, w) -> {
                    v.setEnabled(false);
                    List<String> satu = new ArrayList<>();
                    satu.add(m.id);
                    setujui(satu);
                })
                .show());
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, (int) (52 * d), 1f);
        l1.setMarginEnd((int) (6 * d));
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, (int) (52 * d), 1f);
        l2.setMarginStart((int) (6 * d));
        tombol.addView(tolak, l1);
        tombol.addView(setuju, l2);
        isi.addView(tombol, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        kartu.addView(isi);
        return kartu;
    }

    // ---- foto bukti -------------------------------------------------------------------------------

    private File folderFoto(Context c) {
        File f = new File(c.getCacheDir(), "foto_persetujuan");
        if (!f.exists() && !f.mkdirs()) return c.getCacheDir();
        return f;
    }

    /** Foto bukti catatan, supaya krani memeriksa buktinya sebelum menyetujui. Diunduh dari server saat kartu tampil. */
    private View bagianFoto(ServerApi.Menunggu m) {
        final Context c = requireContext();
        final float d = getResources().getDisplayMetrics().density;
        LinearLayout wadah = new LinearLayout(c);
        wadah.setOrientation(LinearLayout.VERTICAL);
        wadah.setPadding(0, 0, 0, (int) (12 * d));

        final TextView status = new TextView(c);
        status.setTextSize(13);
        status.setTextColor(MaterialColors.getColor(status, com.google.android.material.R.attr.colorOnSurfaceVariant));

        final ImageView img = new ImageView(c);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setContentDescription("Foto bukti catatan");
        img.setBackgroundColor(MaterialColors.getColor(img, com.google.android.material.R.attr.colorSurfaceVariant));
        img.setVisibility(View.GONE);
        wadah.addView(img, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (200 * d)));
        wadah.addView(status);

        if (m.fotoId == null) {
            status.setText("Foto belum terkirim dari HP pencatat.");
        } else {
            muatFoto(m.fotoId, img, status);
        }
        return wadah;
    }

    private void muatFoto(final String fotoId, final ImageView img, final TextView status) {
        final Context app = requireContext().getApplicationContext();
        final File berkas = new File(folderFoto(app), fotoId + ".jpg");
        status.setText("Memuat foto...");
        status.setOnClickListener(null);
        ioFoto.execute(() -> {
            String galat = null;
            Bitmap bmp = null;
            try {
                if (!berkas.exists() || berkas.length() == 0) {
                    byte[] data = server(app).foto(fotoId);
                    try (FileOutputStream out = new FileOutputStream(berkas)) {
                        out.write(data);
                    }
                }
                BitmapFactory.Options batas = new BitmapFactory.Options();
                batas.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(berkas.getAbsolutePath(), batas);
                int sampel = 1;
                while (batas.outWidth / (sampel * 2) >= 1200) sampel *= 2; // cukup untuk kartu; versi penuh dibuka lewat ketukan
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = sampel;
                bmp = BitmapFactory.decodeFile(berkas.getAbsolutePath(), o);
                if (bmp == null) {
                    // berkas rusak: hapus supaya percobaan berikutnya mengunduh ulang
                    //noinspection ResultOfMethodCallIgnored
                    berkas.delete();
                    galat = "Foto tidak bisa dibaca.";
                }
            } catch (ApiException e) {
                galat = e.kode == 404 ? "Foto tidak ditemukan di server." : "Foto belum bisa dimuat: " + e.getMessage();
            } catch (Exception e) {
                galat = "Foto belum bisa dimuat: " + e.getMessage();
            }
            final Bitmap hasil = bmp;
            final String pesan = galat;
            main.post(() -> {
                if (!isAdded() || !img.isAttachedToWindow()) return;
                if (hasil != null) {
                    img.setImageBitmap(hasil);
                    img.setVisibility(View.VISIBLE);
                    img.setOnClickListener(v -> LihatFotoDialog.tampilkan(requireContext(), berkas.getAbsolutePath(), "Foto bukti"));
                    status.setText("Ketuk foto untuk memperbesar.");
                } else {
                    status.setText(pesan + " Ketuk untuk mencoba lagi.");
                    status.setOnClickListener(v -> muatFoto(fotoId, img, status));
                }
            });
        });
    }

    /** Menghapus foto sementara yang catatannya sudah tidak ada di daftar (sudah disetujui atau ditolak). */
    private void bersihkanCacheFoto(List<ServerApi.Menunggu> item) {
        final Context app = requireContext().getApplicationContext();
        final java.util.Set<String> dipakai = new java.util.HashSet<>();
        for (ServerApi.Menunggu m : item) if (m.fotoId != null) dipakai.add(m.fotoId + ".jpg");
        ioFoto.execute(() -> {
            File[] ada = folderFoto(app).listFiles();
            if (ada == null) return;
            for (File f : ada) {
                //noinspection ResultOfMethodCallIgnored
                if (f.isFile() && !dipakai.contains(f.getName())) f.delete();
            }
        });
    }

    private static String nilai(String s) {
        return s == null || s.isEmpty() ? "-" : s;
    }

    /** Nama tampilan kelas dari kode di server (ripe, unripe, ...). */
    static String namaKelas(String kode) {
        String[] kodeServer = {"abnormal", "empty", "overripe", "ripe", "underripe", "unripe"};
        for (int i = 0; i < kodeServer.length && i < KelasModel.TAMPIL.length; i++) {
            if (kodeServer[i].equalsIgnoreCase(kode)) return KelasModel.TAMPIL[i];
        }
        return kode;
    }

    private void dialogTolak(ServerApi.Menunggu m) {
        final Context c = requireContext();
        final EditText alasan = new EditText(c);
        alasan.setHint("Alasan (misalnya: blok salah)");
        alasan.setSingleLine(false);
        alasan.setMinLines(2);
        alasan.setMaxLines(4);
        alasan.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(300)});
        int p = (int) (20 * getResources().getDisplayMetrics().density);
        alasan.setPadding(p, p / 2, p, p / 2);
        new MaterialAlertDialogBuilder(c)
                .setTitle("Tolak catatan?")
                .setMessage("Catatan dikembalikan ke " + m.pencatatNama + " beserta alasannya, lalu bisa diperbaiki dan dikirim ulang.")
                .setView(alasan)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Tolak", (dlg, w) -> {
                    String teks = String.valueOf(alasan.getText()).trim();
                    if (teks.isEmpty()) {
                        Toast.makeText(c, "Alasan wajib diisi", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    tolak(m.id, teks);
                })
                .show();
    }

    private void tolak(String id, String alasan) {
        final Context app = requireContext().getApplicationContext();
        io.execute(() -> {
            String pesan;
            try {
                server(app).tolak(id, alasan);
                pesan = "Catatan ditolak dan dikembalikan";
            } catch (ApiException e) {
                pesan = e.kode == 409 ? "Catatan itu sudah diproses di tempat lain" : "Gagal menolak: " + e.getMessage();
            }
            selesai(app, pesan);
        });
    }

    private void setujui(List<String> ids) {
        final Context app = requireContext().getApplicationContext();
        if (!Sesi.punya(app, "panen.setujui")) return;
        io.execute(() -> {
            int ok = 0;
            int sudah = 0;
            String galat = null;
            PersetujuanServer ps = server(app);
            for (String id : ids) {
                try {
                    ps.setujui(id);
                    ok++;
                } catch (ApiException e) {
                    if (e.kode == 409) {
                        sudah++; // sudah diproses orang lain: tidak dihitung sebagai galat
                    } else {
                        galat = e.getMessage();
                        break; // jaringan/server bermasalah: berhenti, sisanya akan gagal juga
                    }
                }
            }
            StringBuilder p = new StringBuilder();
            if (ok > 0) p.append(ok).append(" catatan disetujui");
            if (sudah > 0) p.append(p.length() > 0 ? ", " : "").append(sudah).append(" sudah diproses di tempat lain");
            if (galat != null) p.append(p.length() > 0 ? ". " : "").append("Gagal: ").append(galat);
            if (p.length() == 0) p.append("Tidak ada yang berubah");
            selesai(app, p.toString());
        });
    }

    private void selesai(Context app, String pesan) {
        main.post(() -> {
            if (!isAdded()) return;
            Toast.makeText(app, pesan, Toast.LENGTH_SHORT).show();
            muat();
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        ioFoto.shutdown();
        io.shutdown();
    }
}
