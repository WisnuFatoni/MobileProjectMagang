package com.example.fai26;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.fai26.db.Waktu;
import com.example.fai26.db.dao.Hasil;
import com.example.fai26.lokasi.Lokasi;
import com.google.android.material.card.MaterialCardView;

import java.io.File;

/** Membuat satu baris kartu catatan panen (dipakai di Beranda dan Riwayat). */
final class CatatanRowFactory {

    private CatatanRowFactory() {
    }

    static View buat(Context c, Hasil.RiwayatItem r) {
        float d = c.getResources().getDisplayMetrics().density;

        // Kartu putih gaya tema (sudut 20dp, tanpa garis dan bayangan), seperti daftar "Notes" di referensi.
        MaterialCardView kartu = new MaterialCardView(c);
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        klp.setMargins(0, 0, 0, (int) (10 * d));
        kartu.setLayoutParams(klp);

        LinearLayout baris = new LinearLayout(c);
        baris.setOrientation(LinearLayout.HORIZONTAL);
        baris.setGravity(Gravity.CENTER_VERTICAL);
        baris.setPadding((int) (12 * d), (int) (12 * d), (int) (12 * d), (int) (12 * d));

        com.google.android.material.imageview.ShapeableImageView img =
                new com.google.android.material.imageview.ShapeableImageView(c);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setShapeAppearanceModel(img.getShapeAppearanceModel().toBuilder().setAllCornerSizes(14 * d).build());
        img.setBackgroundColor(com.google.android.material.color.MaterialColors.getColor(
                c, com.google.android.material.R.attr.colorPrimaryContainer, 0xFFE3F1E7));
        if (r.fotoPath != null && new File(r.fotoPath).exists()) {
            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inSampleSize = 8;
            Bitmap bmp = BitmapFactory.decodeFile(r.fotoPath, opt);
            if (bmp != null) img.setImageBitmap(bmp);
            final String fPath = r.fotoPath;
            final String fJudul = "Blok " + r.blok + " • TPH " + r.noTph;
            View.OnClickListener buka = v -> com.example.fai26.ui.LihatFotoDialog.tampilkan(c, fPath, fJudul);
            img.setOnClickListener(buka);
            kartu.setOnClickListener(buka);
        }
        baris.addView(img, new LinearLayout.LayoutParams((int) (64 * d), (int) (64 * d)));

        TextView judul = new TextView(c);
        judul.setText("Blok " + r.blok + " • TPH " + r.noTph);
        judul.setTextSize(16);
        judul.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(c, R.font.outfit_semibold));
        judul.setTextColor(com.google.android.material.color.MaterialColors.getColor(
                c, com.google.android.material.R.attr.colorOnSurface, 0xFF1B1D1B));
        TextView rinci = new TextView(c);
        rinci.setText(r.total + " janjang • " + Format.waktu(Waktu.keMillis(r.dibuatPada))
                + (r.pemanen == null ? "" : "\n" + r.pemanen)
                + (Lokasi.perluPerhatian(r.statusLokasi) ? "\nLokasi: " + Lokasi.teks(r.statusLokasi) : "")
                + (r.pesanServer != null && !r.pesanServer.isEmpty() ? "\n" + r.pesanServer : ""));
        rinci.setTextSize(13);
        rinci.setTextColor(com.google.android.material.color.MaterialColors.getColor(
                c, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF646A66));
        rinci.setPadding(0, (int) (2 * d), 0, 0);
        LinearLayout teks = new LinearLayout(c);
        teks.setOrientation(LinearLayout.VERTICAL);
        teks.addView(judul);
        teks.addView(rinci);
        if (com.example.fai26.db.PanenRepository.bisaDiubah(r.status)) {
            // Catatan ditolak (oleh server atau ERP) atau sudah terkirim tetapi belum disetujui krani: ubah isinya lalu
            // kirim ulang dengan panen_id yang sama. Setelah krani menyetujui, tombol ini hilang (catatan terkunci).
            com.google.android.material.button.MaterialButton edit = new com.google.android.material.button.MaterialButton(
                    c, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            edit.setText(com.example.fai26.db.StatusPanen.DITOLAK.equals(r.status) ? "Edit dan kirim ulang" : "Edit sebelum disetujui");
            edit.setAllCaps(false);
            edit.setOnClickListener(v -> {
                android.content.Intent i = new android.content.Intent(c, HasilActivity.class);
                i.putExtra(Ekstra.EXTRA_EDIT_PANEN_ID, r.panenId);
                if (!(c instanceof android.app.Activity)) i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
            });
            LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            elp.topMargin = (int) (8 * d);
            teks.addView(edit, elp);
        }
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginStart((int) (12 * d));
        baris.addView(teks, tlp);

        TextView status = new TextView(c);
        StatusUi.pasang(c, status, r.status);
        baris.addView(status);

        kartu.addView(baris);
        return kartu;
    }
}
