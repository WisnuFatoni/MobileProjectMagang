package com.example.fai26.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Foto profil akun yang tersimpan di HP (satu berkas per akun: files/profil/&lt;user_id&gt;.jpg). Foto dipilih dari kamera atau galeri,
 * dipotong persegi dari tengah, dikecilkan ke 512 px, dan disimpan di HP lebih dulu (jadi langsung tampil walau tanpa sinyal). Pengiriman
 * ke server menyusul lewat sinkronisasi: kolom "tunda" menandai apa yang masih harus dikirim ("unggah" atau "hapus").
 */
public final class FotoProfilLokal {

    public static final int SISI = 512;
    public static final String TUNDA_UNGGAH = "unggah";
    public static final String TUNDA_HAPUS = "hapus";

    private static final String PREF = "fai26_foto_profil";

    private FotoProfilLokal() {
    }

    private static SharedPreferences pref(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static File berkas(Context c, long userId) {
        return new File(new File(c.getApplicationContext().getFilesDir(), "profil"), userId + ".jpg");
    }

    public static boolean ada(Context c, long userId) {
        return userId > 0 && berkas(c, userId).isFile();
    }

    /** Versi foto di HP (sama dengan versi server saat sudah sinkron); 0 bila tidak ada atau belum pernah sinkron. */
    public static long versi(Context c, long userId) {
        return pref(c).getLong("versi_" + userId, 0L);
    }

    /** Pekerjaan yang masih tertunda ke server: "", "unggah", atau "hapus". */
    public static String tunda(Context c, long userId) {
        return pref(c).getString("tunda_" + userId, "");
    }

    // ---- pemrosesan gambar (murni, bisa diuji tanpa Android) --------------------------------------------------

    /** Kotak persegi di tengah gambar w x h: {kiri, atas, sisi}. */
    public static int[] potongTengah(int w, int h) {
        int sisi = Math.min(w, h);
        return new int[]{(w - sisi) / 2, (h - sisi) / 2, sisi};
    }

    /** inSampleSize (pangkat dua) terbesar yang masih menyisakan sisi terpendek >= {@code minimal}. */
    public static int sampel(int w, int h, int minimal) {
        int pendek = Math.min(w, h);
        int s = 1;
        while (pendek / (s * 2) >= minimal) s *= 2;
        return s;
    }

    /** Matriks putar untuk nilai orientasi EXIF (foto kamera sering tersimpan miring dengan penanda ini). */
    public static Matrix matriksExif(int orientasi) {
        Matrix m = new Matrix();
        switch (orientasi) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                m.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                m.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                m.postRotate(270);
                break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                m.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                m.postScale(1, -1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                m.postRotate(90);
                m.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                m.postRotate(270);
                m.postScale(-1, 1);
                break;
            default:
                break;
        }
        return m;
    }

    /** Memotong persegi dari tengah lalu mengecilkan ke {@link #SISI}. */
    public static Bitmap potongDanKecilkan(Bitmap sumber) {
        int[] k = potongTengah(sumber.getWidth(), sumber.getHeight());
        Bitmap potong = Bitmap.createBitmap(sumber, k[0], k[1], k[2], k[2]);
        Bitmap hasil = k[2] == SISI ? potong : Bitmap.createScaledBitmap(potong, SISI, SISI, true);
        if (potong != hasil && potong != sumber) potong.recycle();
        return hasil;
    }

    // ---- simpan, hapus, tandai ---------------------------------------------------------------------------------

    /** Dari kamera atau galeri: diproses, disimpan di HP, dan ditandai untuk dikirim ke server. */
    public static void simpanDariUri(Context c, long userId, Uri uri) throws IOException {
        Context app = c.getApplicationContext();
        int w;
        int h;
        try (InputStream in = app.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Gambar tidak bisa dibuka");
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, o);
            w = o.outWidth;
            h = o.outHeight;
        }
        if (w < 64 || h < 64) throw new IOException("Gambar terlalu kecil");
        int orientasi = ExifInterface.ORIENTATION_NORMAL;
        try (InputStream in = app.getContentResolver().openInputStream(uri)) {
            if (in != null) orientasi = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException ignored) {
            // tanpa EXIF: dianggap tegak
        }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sampel(w, h, SISI * 3 / 2);
        Bitmap mentah;
        try (InputStream in = app.getContentResolver().openInputStream(uri)) {
            mentah = in == null ? null : BitmapFactory.decodeStream(in, null, o);
        }
        if (mentah == null) throw new IOException("Gambar rusak atau tidak didukung");
        Matrix putar = matriksExif(orientasi);
        Bitmap tegak = putar.isIdentity() ? mentah : Bitmap.createBitmap(mentah, 0, 0, mentah.getWidth(), mentah.getHeight(), putar, true);
        if (tegak != mentah) mentah.recycle();
        Bitmap akhir = potongDanKecilkan(tegak);
        if (akhir != tegak) tegak.recycle();
        try {
            ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
            akhir.compress(Bitmap.CompressFormat.JPEG, 88, jpeg);
            tulis(app, userId, jpeg.toByteArray());
        } finally {
            akhir.recycle();
        }
        pref(app).edit().putString("tunda_" + userId, TUNDA_UNGGAH).apply();
    }

    private static void tulis(Context app, long userId, byte[] isi) throws IOException {
        File f = berkas(app, userId);
        File dir = f.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("Folder foto profil tidak bisa dibuat");
        File sementara = new File(dir, userId + ".tmp");
        try (FileOutputStream out = new FileOutputStream(sementara)) {
            out.write(isi);
        }
        if (!sementara.renameTo(f)) throw new IOException("Foto tidak bisa disimpan");
    }

    /** Menghapus foto di HP dan menandai penghapusan di server (dikirim saat ada sinyal). */
    public static void hapus(Context c, long userId) {
        Context app = c.getApplicationContext();
        //noinspection ResultOfMethodCallIgnored
        berkas(app, userId).delete();
        pref(app).edit().putString("tunda_" + userId, TUNDA_HAPUS).putLong("versi_" + userId, 0L).apply();
    }

    /** Foto sudah terkirim ke server: tercatat versinya dan tidak ada yang tertunda. */
    public static void setelahTerkirim(Context c, long userId, long versiServer) {
        pref(c).edit().putString("tunda_" + userId, "").putLong("versi_" + userId, versiServer).apply();
    }

    /** Penghapusan sudah sampai ke server. */
    public static void setelahDihapusDiServer(Context c, long userId) {
        pref(c).edit().putString("tunda_" + userId, "").putLong("versi_" + userId, 0L).apply();
    }

    /** Foto dari server (akun ini diubah di perangkat lain atau web) disalin ke HP. */
    public static void simpanDariServer(Context c, long userId, byte[] jpeg, long versiServer) throws IOException {
        Context app = c.getApplicationContext();
        tulis(app, userId, jpeg);
        pref(app).edit().putLong("versi_" + userId, versiServer).apply();
    }

    /** Server tidak punya foto untuk akun ini lagi (dihapus dari tempat lain): hapus salinan di HP. */
    public static void hapusSalinanLokal(Context c, long userId) {
        //noinspection ResultOfMethodCallIgnored
        berkas(c, userId).delete();
        pref(c).edit().putLong("versi_" + userId, 0L).apply();
    }
}
