package com.example.fai26.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;

/** Cache hak sesi harus bisa diperbarui dari master baru tanpa login ulang, dan hanya untuk pengguna yang login. */
@RunWith(AndroidJUnit4.class)
public class SesiTest {

    private Context ctx;
    /** Isi mentah preferensi sesi sebelum tes, dikembalikan persis sesudahnya (HP uji mungkin punya sesi login sungguhan). */
    private java.util.Map<String, ?> cadangan;

    private android.content.SharedPreferences pref() {
        return ctx.getSharedPreferences("fai26_sesi", Context.MODE_PRIVATE);
    }

    @Before
    public void siapkan() {
        ctx = ApplicationProvider.getApplicationContext();
        cadangan = new java.util.HashMap<>(pref().getAll());
        Sesi.masuk(ctx, 7, "Krani Uji", "Krani", Arrays.asList("panen.lihat", "panen.buat"));
    }

    @After
    @SuppressWarnings("unchecked")
    public void pulihkan() {
        android.content.SharedPreferences.Editor e = pref().edit().clear();
        for (java.util.Map.Entry<String, ?> k : cadangan.entrySet()) {
            Object v = k.getValue();
            if (v instanceof Long) e.putLong(k.getKey(), (Long) v);
            else if (v instanceof String) e.putString(k.getKey(), (String) v);
            else if (v instanceof Integer) e.putInt(k.getKey(), (Integer) v);
            else if (v instanceof Boolean) e.putBoolean(k.getKey(), (Boolean) v);
            else if (v instanceof java.util.Set) e.putStringSet(k.getKey(), (java.util.Set<String>) v);
        }
        e.commit();
    }

    @Test
    public void perbaruiMenggantiHakDanPeranPenggunaYangLogin() {
        assertTrue(Sesi.punya(ctx, "panen.buat"));

        Sesi.perbarui(ctx, 7, "Mandor", Arrays.asList("panen.lihat", "panen.setujui"));

        assertFalse("hak yang dicabut hilang tanpa login ulang", Sesi.punya(ctx, "panen.buat"));
        assertTrue(Sesi.punya(ctx, "panen.setujui"));
        assertEquals("Mandor", Sesi.peran(ctx));
        assertEquals("Krani Uji", Sesi.nama(ctx));
        assertEquals(7, Sesi.userId(ctx));
    }

    @Test
    public void perbaruiUntukPenggunaLainDiabaikan() {
        Sesi.perbarui(ctx, 99, "Admin", Collections.singletonList("user.kelola"));
        assertFalse(Sesi.punya(ctx, "user.kelola"));
        assertTrue(Sesi.punya(ctx, "panen.buat"));
        assertEquals("Krani", Sesi.peran(ctx));
    }

    @Test
    public void perbaruiTanpaSesiTidakMembuatSesi() {
        Sesi.keluar(ctx);
        Sesi.perbarui(ctx, 7, "Mandor", Collections.singletonList("panen.setujui"));
        assertFalse(Sesi.sudahLogin(ctx));
        assertFalse(Sesi.punya(ctx, "panen.setujui"));
    }
}
