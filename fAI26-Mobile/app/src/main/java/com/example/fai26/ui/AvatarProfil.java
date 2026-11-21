package com.example.fai26.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import com.example.fai26.auth.FotoProfilLokal;
import com.example.fai26.auth.Sesi;

/**
 * Memasang foto profil akun yang sedang login pada sebuah avatar bulat. Bila belum punya foto, yang tampil tetap huruf awal nama
 * (tampilan lama). Dibaca dari berkas di HP, jadi tampil juga tanpa sinyal.
 */
public final class AvatarProfil {

    private AvatarProfil() {
    }

    /**
     * @param inisial teks huruf awal (disembunyikan bila ada foto)
     * @param foto    gambar bulat di atas teks (boleh null, maka tidak berbuat apa-apa)
     */
    public static void pasang(Context c, TextView inisial, ImageView foto) {
        if (foto == null) return;
        long uid = Sesi.userId(c);
        Bitmap bmp = null;
        if (FotoProfilLokal.ada(c, uid)) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 2; // 256 px cukup untuk avatar
            bmp = BitmapFactory.decodeFile(FotoProfilLokal.berkas(c, uid).getAbsolutePath(), o);
        }
        if (bmp == null) {
            foto.setImageDrawable(null);
            foto.setVisibility(View.GONE);
            if (inisial != null) inisial.setVisibility(View.VISIBLE);
            return;
        }
        RoundedBitmapDrawable bulat = RoundedBitmapDrawableFactory.create(c.getResources(), bmp);
        bulat.setCircular(true);
        bulat.setAntiAlias(true);
        foto.setImageDrawable(bulat);
        foto.setVisibility(View.VISIBLE);
        if (inisial != null) inisial.setVisibility(View.INVISIBLE);
    }
}
