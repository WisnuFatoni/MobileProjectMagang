package com.example.fai26.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.example.fai26.R;

import java.io.File;
import java.util.concurrent.Executors;

/**
 * Dialog layar penuh untuk melihat foto panorama TBS secara utuh,
 * lengkap dengan fitur cubit-zoom (pinch-zoom), geser menyisir panorama, dan ketuk-ganda.
 */
public final class LihatFotoDialog {

    private LihatFotoDialog() {
    }

    public static void tampilkan(Context context, String pathFoto, String judul) {
        if (context == null || pathFoto == null || pathFoto.trim().isEmpty()) {
            if (context != null) Toast.makeText(context, "Foto tidak ditemukan", Toast.LENGTH_SHORT).show();
            return;
        }

        File berkas = new File(pathFoto);
        if (!berkas.exists()) {
            Toast.makeText(context, "Berkas foto belum ada di perangkat", Toast.LENGTH_SHORT).show();
            return;
        }

        Dialog dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_lihat_foto);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }

        TextView txtJudul = dialog.findViewById(R.id.txtJudul);
        if (judul != null && !judul.trim().isEmpty()) {
            txtJudul.setText(judul);
        }

        ZoomImageView imgZoom = dialog.findViewById(R.id.imgZoom);
        ImageButton btnTutup = dialog.findViewById(R.id.btnTutup);
        btnTutup.setOnClickListener(v -> dialog.dismiss());

        dialog.findViewById(R.id.btnReset).setOnClickListener(v -> imgZoom.resetZoom());

        // Putar memutar FOTO 90 derajat searah jarum jam (bukan layar aplikasi). Mengubah orientasi aplikasi membuat
        // halaman dibuat ulang sehingga dialog hilang dan kembali ke Beranda.
        final Bitmap[] asli = new Bitmap[1];
        final Bitmap[] tampil = new Bitmap[1];
        final int[] sudut = new int[1];
        View btnPutar = dialog.findViewById(R.id.btnPutar);
        if (btnPutar != null) {
            btnPutar.setOnClickListener(v -> {
                if (asli[0] == null) return;
                sudut[0] = (sudut[0] + 90) % 360;
                Bitmap lama = tampil[0];
                Bitmap baru = asli[0];
                if (sudut[0] != 0) {
                    try {
                        android.graphics.Matrix m = new android.graphics.Matrix();
                        m.postRotate(sudut[0]);
                        baru = Bitmap.createBitmap(asli[0], 0, 0, asli[0].getWidth(), asli[0].getHeight(), m, true);
                    } catch (OutOfMemoryError e) {
                        Toast.makeText(context, "Memori tidak cukup untuk memutar foto", Toast.LENGTH_SHORT).show();
                        sudut[0] = (sudut[0] + 270) % 360;
                        return;
                    }
                }
                tampil[0] = baru;
                imgZoom.setImageBitmap(baru);
                if (lama != null && lama != asli[0]) lama.recycle();
            });
        }

        // Muat bitmap di background agar UI tidak nge-lag jika file resolusi tinggi
        Handler mainHandler = new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // Tentukan bounds dulu
                BitmapFactory.Options optBounds = new BitmapFactory.Options();
                optBounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(berkas.getAbsolutePath(), optBounds);

                BitmapFactory.Options opt = new BitmapFactory.Options();
                opt.inSampleSize = 1;
                // Bila sangat besar (>4500px), perkecil sedikit
                if (optBounds.outWidth > 4500 || optBounds.outHeight > 4500) {
                    opt.inSampleSize = 2;
                }
                Bitmap bmp = BitmapFactory.decodeFile(berkas.getAbsolutePath(), opt);

                mainHandler.post(() -> {
                    if (dialog.isShowing() && bmp != null) {
                        asli[0] = bmp;
                        tampil[0] = bmp;
                        imgZoom.setImageBitmap(bmp);
                    }
                });
            } catch (Throwable e) {
                mainHandler.post(() ->
                        Toast.makeText(context, "Gagal memuat foto: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
            }
        });

        dialog.show();
    }
}

