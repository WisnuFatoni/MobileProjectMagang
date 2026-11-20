package com.example.fai26.ipd;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Persetujuan asisten: inspeksi IPD yang dikirim mandor dan ditujukan ke asisten ini. Daftar diambil dari server (butuh sinyal).
 * Tiap kartu memperlihatkan ringkasan loses per kode dan foto bukti; asisten menyetujui (lalu terkunci) atau menolak dengan alasan.
 */
public class IpdPersetujuanFragment extends Fragment {

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService ioFoto = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());

    private LinearLayout daftar;
    private TextView txtJumlah;
    private TextView txtKosong;
    private boolean memuat;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final Context c = requireContext();
        ScrollView gulir = new ScrollView(c);
        gulir.setFillViewport(true);
        gulir.setClipToPadding(false);
        gulir.setBackgroundColor(MaterialColors.getColor(gulir, android.R.attr.colorBackground));
        LinearLayout kolom = new LinearLayout(c);
        kolom.setOrientation(LinearLayout.VERTICAL);
        kolom.setPadding(IpdUi.dp(c, 16), IpdUi.dp(c, 8), IpdUi.dp(c, 16), 0);
        gulir.addView(kolom, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView judul = IpdUi.judul(c, "Persetujuan IPD", 18);
        judul.setGravity(Gravity.CENTER);
        judul.setMinHeight(IpdUi.dp(c, 48));
        kolom.addView(judul, IpdUi.lebarPenuh(c, 0, 4));
        txtJumlah = IpdUi.redup(c, "", 14);
        txtJumlah.setGravity(Gravity.CENTER);
        kolom.addView(txtJumlah, IpdUi.lebarPenuh(c, 0, 12));
        txtKosong = IpdUi.redup(c, "", 15);
        txtKosong.setVisibility(View.GONE);
        txtKosong.setOnClickListener(v -> muat()); // ketuk pesan galat untuk mencoba lagi
        kolom.addView(txtKosong, IpdUi.lebarPenuh(c, 8, 8));
        daftar = new LinearLayout(c);
        daftar.setOrientation(LinearLayout.VERTICAL);
        kolom.addView(daftar, IpdUi.lebarPenuh(c, 0, 0));

        final float d = getResources().getDisplayMetrics().density;
        final int atas = kolom.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(gulir, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            kolom.setPadding(kolom.getPaddingLeft(), atas + bars.top, kolom.getPaddingRight(), 0);
            v.setPadding(bars.left, 0, bars.right, bars.bottom + (int) (112 * d));
            return insets;
        });
        return gulir;
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
            List<ServerApi.IpdMenunggu> hasil = null;
            String galat = null;
            try {
                hasil = server(app).muatIpd();
            } catch (ApiException e) {
                galat = (e.kode == 0 ? "Butuh sinyal untuk memuat daftar persetujuan. " : "") + e.getMessage() + "\nKetuk untuk mencoba lagi.";
            } catch (Exception e) {
                galat = "Gagal memuat: " + e.getMessage() + "\nKetuk untuk mencoba lagi.";
            }
            final List<ServerApi.IpdMenunggu> item = hasil;
            final String pesan = galat;
            main.post(() -> {
                memuat = false;
                if (!isAdded()) return;
                daftar.removeAllViews();
                if (pesan != null) {
                    txtJumlah.setText("Belum bisa dimuat");
                    txtKosong.setText(pesan);
                    txtKosong.setVisibility(View.VISIBLE);
                    return;
                }
                for (ServerApi.IpdMenunggu m : item) daftar.addView(kartu(m));
                txtJumlah.setText(item.isEmpty() ? "Semua sudah diperiksa" : item.size() + " inspeksi menunggu");
                txtKosong.setText("Tidak ada inspeksi yang menunggu persetujuan.");
                txtKosong.setVisibility(item.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private View kartu(ServerApi.IpdMenunggu m) {
        final Context c = requireContext();
        LinearLayout[] isi = new LinearLayout[1];
        MaterialCardView kartu = IpdUi.kartu(c, isi);
        LinearLayout l = isi[0];

        l.addView(IpdUi.judul(c, nilai(m.pemanenNama) + " • Blok " + nilai(m.blokKode), 17));
        StringBuilder meta = new StringBuilder();
        meta.append(IpdUi.tanggal(m.tanggalInspeksi)).append(" • ").append(m.divisiKode);
        if (m.ancakNo != null) meta.append(" • ancak ").append(m.ancakNo);
        meta.append("\nDiinspeksi ").append(m.inspekturNama);
        meta.append("\nMandor blok: ").append(nilai(m.mandorBlokNama)).append(" • mandor ancak: ").append(nilai(m.mandorAncakNama));
        if (m.catatan != null) meta.append("\nCatatan: ").append(m.catatan);
        TextView txtMeta = IpdUi.redup(c, meta, 13);
        txtMeta.setPadding(0, IpdUi.dp(c, 4), 0, IpdUi.dp(c, 8));
        l.addView(txtMeta);

        TextView rg = IpdUi.judul(c, m.pokokDiperiksa + " pokok diperiksa • loses " + IpdUi.persen(m.persenLoses) + " (" + m.pokokLoses + " pokok)", 15);
        l.addView(rg);
        if (m.perKode.isEmpty()) {
            TextView bersih = IpdUi.redup(c, "Tidak ada temuan: semua pokok bersih.", 14);
            bersih.setPadding(0, IpdUi.dp(c, 4), 0, 0);
            l.addView(bersih);
        }
        for (ServerApi.IpdKode k : m.perKode) {
            if (!"LOSES".equals(k.jenis)) continue;
            TextView t = IpdUi.redup(c, k.kode + "  " + k.nama + ": " + k.pokok + " pokok (" + IpdUi.persen(k.persenPokok) + "), jumlah " + k.jumlah, 14);
            t.setPadding(0, IpdUi.dp(c, 4), 0, 0);
            l.addView(t);
        }
        if (m.pokokTemuanLain > 0) {
            TextView lain = IpdUi.judul(c, "Temuan lain: " + m.pokokTemuanLain + " pokok (tidak memengaruhi hasil)", 14);
            lain.setPadding(0, IpdUi.dp(c, 8), 0, 0);
            l.addView(lain);
            for (ServerApi.IpdKode k : m.perKode) {
                if ("LOSES".equals(k.jenis)) continue;
                TextView t = IpdUi.redup(c, k.kode + "  " + k.nama + ": " + k.pokok + " pokok, jumlah " + k.jumlah, 14);
                t.setPadding(0, IpdUi.dp(c, 3), 0, 0);
                l.addView(t);
            }
        }

        if (!m.foto.isEmpty()) {
            TextView ft = IpdUi.judul(c, m.foto.size() + " foto bukti (ketuk untuk memperbesar)", 14);
            ft.setPadding(0, IpdUi.dp(c, 10), 0, IpdUi.dp(c, 4));
            l.addView(ft);
            HorizontalScrollView hs = new HorizontalScrollView(c);
            LinearLayout baris = new LinearLayout(c);
            baris.setOrientation(LinearLayout.HORIZONTAL);
            hs.addView(baris);
            l.addView(hs);
            for (ServerApi.IpdFotoRef f : m.foto) baris.addView(kotakFoto(f));
        }

        LinearLayout tombol = new LinearLayout(c);
        tombol.setOrientation(LinearLayout.HORIZONTAL);
        tombol.setPadding(0, IpdUi.dp(c, 12), 0, 0);
        MaterialButton tolak = IpdUi.tombol(c, "Tolak", false);
        MaterialButton setuju = IpdUi.tombol(c, "Setujui", true);
        tombol.addView(tolak, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        slp.setMarginStart(IpdUi.dp(c, 8));
        tombol.addView(setuju, slp);
        l.addView(tombol);

        setuju.setOnClickListener(v -> new MaterialAlertDialogBuilder(c).setTitle("Setujui inspeksi ini?")
                .setMessage("Setelah disetujui, inspeksi terkunci dan tidak bisa diubah lagi oleh mandor.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Setujui", (d, w) -> aksi(setuju, tolak, () -> server(c.getApplicationContext()).setujuiIpd(m.id), "Disetujui"))
                .show());
        tolak.setOnClickListener(v -> {
            final EditText alasan = new EditText(c);
            alasan.setHint("Alasan (wajib)");
            alasan.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            alasan.setPadding(IpdUi.dp(c, 24), IpdUi.dp(c, 16), IpdUi.dp(c, 24), IpdUi.dp(c, 8));
            new MaterialAlertDialogBuilder(c).setTitle("Tolak inspeksi").setView(alasan)
                    .setNegativeButton("Batal", null)
                    .setPositiveButton("Tolak", (d, w) -> {
                        String a = alasan.getText().toString().trim();
                        if (a.isEmpty()) {
                            Toast.makeText(c, "Alasan wajib diisi", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        aksi(setuju, tolak, () -> server(c.getApplicationContext()).tolakIpd(m.id, a), "Dikembalikan ke inspektur");
                    }).show();
        });
        return kartu;
    }

    private interface Kerja {
        void jalankan() throws ApiException;
    }

    private void aksi(View a, View b, Kerja kerja, String sukses) {
        a.setEnabled(false);
        b.setEnabled(false);
        final Context app = requireContext().getApplicationContext();
        io.execute(() -> {
            String galat = null;
            try {
                kerja.jalankan();
            } catch (ApiException e) {
                galat = e.getMessage();
            } catch (Exception e) {
                galat = "Gagal: " + e.getMessage();
            }
            final String pesan = galat;
            main.post(() -> {
                Toast.makeText(app, pesan == null ? sukses : pesan, Toast.LENGTH_LONG).show();
                if (isAdded()) muat();
            });
        });
    }

    private View kotakFoto(ServerApi.IpdFotoRef f) {
        final Context c = requireContext();
        LinearLayout kotak = new LinearLayout(c);
        kotak.setOrientation(LinearLayout.VERTICAL);
        kotak.setLayoutParams(IpdUi.lp(IpdUi.dp(c, 120), ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 8, 0, c));
        final ImageView img = new ImageView(c);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setBackgroundColor(MaterialColors.getColor(img, com.google.android.material.R.attr.colorSurfaceVariant));
        kotak.addView(img, new LinearLayout.LayoutParams(IpdUi.dp(c, 120), IpdUi.dp(c, 90)));
        TextView ket = IpdUi.redup(c, f.keterangan == null ? "" : f.keterangan, 12);
        ket.setMaxLines(3);
        kotak.addView(ket);

        final Context app = c.getApplicationContext();
        final File berkas = new File(new File(app.getCacheDir(), "foto_ipd_setuju"), f.id + ".jpg");
        ioFoto.execute(() -> {
            Bitmap bmp = null;
            try {
                if (!berkas.exists() || berkas.length() == 0) {
                    //noinspection ResultOfMethodCallIgnored
                    berkas.getParentFile().mkdirs();
                    byte[] data = server(app).fotoIpd(f.id);
                    try (FileOutputStream out = new FileOutputStream(berkas)) {
                        out.write(data);
                    }
                }
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 4;
                bmp = BitmapFactory.decodeFile(berkas.getAbsolutePath(), o);
            } catch (Exception ignored) {
                // foto belum bisa dimuat: kotak kosong
            }
            final Bitmap hasil = bmp;
            main.post(() -> {
                if (!isAdded() || hasil == null) return;
                img.setImageBitmap(hasil);
                img.setOnClickListener(v -> LihatFotoDialog.tampilkan(requireContext(), berkas.getAbsolutePath(), f.keterangan == null ? "Foto bukti" : f.keterangan));
            });
        });
        return kotak;
    }

    private static String nilai(String s) {
        return s == null || s.isEmpty() ? "-" : s;
    }
}
