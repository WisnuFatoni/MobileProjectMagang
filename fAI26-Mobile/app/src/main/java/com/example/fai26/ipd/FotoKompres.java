package com.example.fai26.ipd;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

/**
 * Mengecilkan foto bukti IPD saat disimpan: sisi terpanjang dibatasi dan JPEG dimampatkan, supaya foto yang banyak
 * (tanpa batas jumlah) tidak memenuhi memori HP. Arah foto (EXIF) dipakai lalu dilepas karena piksel sudah diputar.
 */
public final class FotoKompres {

    public static final int SISI_MAKS = 1600;
    public static final int MUTU = 80;

    private FotoKompres() {
    }

    public static final class Hasil {
        public int lebar;
        public int tinggi;
        public long ukuranByte;
        public String sha256;
    }

    /** Sisi terpanjang hasil: asli bila sudah cukup kecil, kalau tidak dikecilkan ke {@code sisiMaks}. */
    public static int sisiHasil(int lebar, int tinggi, int sisiMaks) {
        return Math.min(Math.max(lebar, tinggi), sisiMaks);
    }

    /** Pangkat dua terbesar yang membuat sisi terpanjang tetap tidak kurang dari sisiMaks (inSampleSize untuk decode hemat memori). */
    public static int sampel(int lebar, int tinggi, int sisiMaks) {
        int s = 1;
        int terpanjang = Math.max(lebar, tinggi);
        while (terpanjang / (s * 2) >= sisiMaks) s *= 2;
        return s;
    }

    public static Hasil kompres(File sumber, File tujuan) throws IOException {
        BitmapFactory.Options batas = new BitmapFactory.Options();
        batas.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(sumber.getAbsolutePath(), batas);
        if (batas.outWidth <= 0 || batas.outHeight <= 0) throw new IOException("Foto tidak bisa dibaca");

        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sampel(batas.outWidth, batas.outHeight, SISI_MAKS);
        Bitmap bmp = BitmapFactory.decodeFile(sumber.getAbsolutePath(), o);
        if (bmp == null) throw new IOException("Foto tidak bisa dibaca");

        Bitmap kerja = bmp;
        int putar = 0;
        try {
            int ori = new ExifInterface(sumber.getAbsolutePath()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (ori == ExifInterface.ORIENTATION_ROTATE_90) putar = 90;
            else if (ori == ExifInterface.ORIENTATION_ROTATE_180) putar = 180;
            else if (ori == ExifInterface.ORIENTATION_ROTATE_270) putar = 270;
        } catch (IOException ignored) {
            // tanpa EXIF: dianggap tegak
        }
        float skala = (float) SISI_MAKS / Math.max(bmp.getWidth(), bmp.getHeight());
        if (putar != 0 || skala < 1f) {
            Matrix m = new Matrix();
            if (putar != 0) m.postRotate(putar);
            if (skala < 1f) m.postScale(skala, skala);
            kerja = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
            if (kerja != bmp) bmp.recycle();
        }
        File induk = tujuan.getParentFile();
        if (induk != null && !induk.exists() && !induk.mkdirs()) throw new IOException("Folder foto tidak bisa dibuat");
        try (FileOutputStream out = new FileOutputStream(tujuan)) {
            if (!kerja.compress(Bitmap.CompressFormat.JPEG, MUTU, out)) throw new IOException("Foto gagal dimampatkan");
        }
        Hasil h = new Hasil();
        h.lebar = kerja.getWidth();
        h.tinggi = kerja.getHeight();
        h.ukuranByte = tujuan.length();
        kerja.recycle();
        h.sha256 = sha256(tujuan);
        return h;
    }

    public static String sha256(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
