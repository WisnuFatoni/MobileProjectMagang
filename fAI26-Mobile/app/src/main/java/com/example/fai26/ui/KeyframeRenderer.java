package com.example.fai26.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import com.example.fai26.KelasModel;
import com.example.fai26.detect.Detection;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Menggambar kotak deteksi ke frame lalu menyimpannya sebagai JPEG (foto bukti). */
public final class KeyframeRenderer {

    private KeyframeRenderer() {
    }

    public static void simpan(Bitmap frame, List<Detection> deteksi, File tujuan) throws IOException {
        simpan(frame, deteksi, tujuan, 85);
    }

    /** @param kualitas kualitas JPEG 1..100 (potongan panorama memakai lebih tinggi karena dikompres ulang di akhir) */
    public static void simpan(Bitmap frame, List<Detection> deteksi, File tujuan, int kualitas) throws IOException {
        Bitmap hasil = frame.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(hasil);
        int w = hasil.getWidth();
        int h = hasil.getHeight();

        Paint kotak = new Paint(Paint.ANTI_ALIAS_FLAG);
        kotak.setStyle(Paint.Style.STROKE);
        kotak.setStrokeWidth(Math.max(3f, w / 300f));
        Paint latar = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint teks = new Paint(Paint.ANTI_ALIAS_FLAG);
        teks.setColor(0xFF000000);
        teks.setTextSize(Math.max(18f, w / 40f));

        List<Detection> urut = new ArrayList<>(deteksi);
        Collections.sort(urut, (a, b) -> Float.compare(a.x1, b.x1));

        int no = 1;
        int[] totalPerKelas = new int[KelasModel.JUMLAH];
        for (Detection d : urut) {
            totalPerKelas[d.classIndex]++;
            int warna = KelasModel.WARNA[d.classIndex];
            float l = d.x1 * w;
            float t = d.y1 * h;
            kotak.setColor(warna);
            canvas.drawRect(l, t, d.x2 * w, d.y2 * h, kotak);

            String label = "#" + (no++) + " " + KelasModel.TAMPIL[d.classIndex] + " " + Math.round(d.confidence * 100) + "%";
            float th = teks.getTextSize();
            float tw = teks.measureText(label);
            latar.setColor(warna);
            float badgeTop = Math.max(0, t - th - 8);
            canvas.drawRoundRect(new RectF(l, badgeTop, l + tw + 14, badgeTop + th + 6), 6, 6, latar);
            teks.setColor(isColorDark(warna) ? 0xFFFFFFFF : 0xFF000000);
            canvas.drawText(label, l + 7, badgeTop + th, teks);
        }

        if (!urut.isEmpty()) {
            Paint bannerBg = new Paint(Paint.ANTI_ALIAS_FLAG);
            bannerBg.setColor(0xCC000000);
            Paint bannerJudul = new Paint(Paint.ANTI_ALIAS_FLAG);
            bannerJudul.setColor(0xFF4ADE80);
            bannerJudul.setTextSize(Math.max(20f, w / 35f));
            bannerJudul.setFakeBoldText(true);

            Paint bannerSub = new Paint(Paint.ANTI_ALIAS_FLAG);
            bannerSub.setColor(0xFFFFFFFF);
            bannerSub.setTextSize(Math.max(14f, w / 50f));

            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < KelasModel.JUMLAH; k++) {
                if (totalPerKelas[k] > 0) {
                    if (sb.length() > 0) sb.append(" • ");
                    sb.append(KelasModel.TAMPIL[k]).append(": ").append(totalPerKelas[k]);
                }
            }
            String titleStr = "HASIL SCAN TPH: " + urut.size() + " JANJANG";
            String subStr = sb.toString();

            float pad = 16f;
            float bW = Math.max(bannerJudul.measureText(titleStr), bannerSub.measureText(subStr)) + pad * 2;
            float bH = bannerJudul.getTextSize() + bannerSub.getTextSize() + pad * 2 + 8;
            RectF bRect = new RectF(pad, pad, pad + bW, pad + bH);
            canvas.drawRoundRect(bRect, 12, 12, bannerBg);

            canvas.drawText(titleStr, pad + pad, pad + pad + bannerJudul.getTextSize() - 4, bannerJudul);
            canvas.drawText(subStr, pad + pad, pad + pad + bannerJudul.getTextSize() + bannerSub.getTextSize() + 4, bannerSub);
        }

        try (FileOutputStream out = new FileOutputStream(tujuan)) {
            hasil.compress(Bitmap.CompressFormat.JPEG, kualitas, out);
        } finally {
            hasil.recycle();
        }
    }

    private static boolean isColorDark(int color) {
        double darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return darkness >= 0.5;
    }
}
