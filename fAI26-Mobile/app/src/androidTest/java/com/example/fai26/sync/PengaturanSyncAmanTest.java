package com.example.fai26.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;

/** Token harus tersimpan terenkripsi (Android Keystore), bukan teks polos, dan gagal dengan aman bila tidak terbuka. */
@RunWith(AndroidJUnit4.class)
public class PengaturanSyncAmanTest {

    private static final String BERKAS = "fai26_sync_aman_uji";
    private static final String REFRESH = "REFRESH-RAHASIA-12345";
    private static final String AKSES = "AKSES-RAHASIA-67890";

    private Context ctx;
    private SharedPreferences mentah;

    @Before
    public void siapkan() {
        ctx = ApplicationProvider.getApplicationContext();
        mentah = ctx.getSharedPreferences(BERKAS, Context.MODE_PRIVATE);
        mentah.edit().clear().commit();
    }

    @After
    public void bersihkan() {
        mentah.edit().clear().commit();
    }

    private PengaturanSync baru() {
        return new PengaturanSync(ctx, BERKAS);
    }

    private static ServerApi.Token token() {
        return new ServerApi.Token(AKSES, 1_800_000_000_000L, REFRESH);
    }

    /** Semua isi berkas preferensi digabung jadi satu teks, untuk memastikan rahasia tidak muncul di mana pun. */
    private String isiMentah() {
        StringBuilder s = new StringBuilder();
        for (Map.Entry<String, ?> e : mentah.getAll().entrySet()) {
            s.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        return s.toString();
    }

    @Test
    public void tokenBolehDibacaKembaliTetapiTidakAdaTeksPolosDiPenyimpanan() {
        PengaturanSync pref = baru();
        pref.simpanToken("krani1", token());

        assertEquals(REFRESH, pref.refresh());
        assertEquals(AKSES, pref.akses());
        assertTrue(pref.adaToken());
        assertEquals("krani1", pref.tokenUser());
        assertEquals(1_800_000_000_000L, pref.aksesKedaluwarsa());

        String mentahan = isiMentah();
        assertFalse("refresh token tidak boleh tersimpan polos", mentahan.contains(REFRESH));
        assertFalse("access token tidak boleh tersimpan polos", mentahan.contains(AKSES));
        assertTrue(mentah.getString("refresh_enc", "").startsWith("v1:"));

        // Tetap terbaca oleh objek baru (proses lain, misalnya WorkManager).
        assertEquals(REFRESH, baru().refresh());
    }

    @Test
    public void tokenLamaTeksPolosDipindahkanLaluDihapus() {
        mentah.edit().putString("refresh", REFRESH).putString("akses", AKSES).putString("token_user", "krani1").commit();

        PengaturanSync pref = baru();

        assertEquals(REFRESH, pref.refresh());
        assertEquals(AKSES, pref.akses());
        assertFalse(mentah.contains("refresh"));
        assertFalse(mentah.contains("akses"));
        assertFalse(isiMentah().contains(REFRESH));
    }

    @Test
    public void sandiBerbedaTiapKaliMeskipTeksSama() throws Exception {
        String a = PenyimpanRahasia.enkripsi("sama", "k");
        String b = PenyimpanRahasia.enkripsi("sama", "k");
        assertNotEquals("IV acak: sandi tidak boleh sama", a, b);
        assertEquals("sama", PenyimpanRahasia.dekripsi(a, "k"));
        assertEquals("sama", PenyimpanRahasia.dekripsi(b, "k"));
    }

    @Test
    public void sandiYangDiubahAtauDitukarKonteksnyaDitolak() throws Exception {
        String sandi = PenyimpanRahasia.enkripsi("rahasia", "refresh_enc");

        assertNull("konteks lain (kolom lain) harus ditolak", PenyimpanRahasia.dekripsi(sandi, "akses_enc"));

        char[] huruf = sandi.toCharArray();
        int i = huruf.length - 3;
        huruf[i] = huruf[i] == 'A' ? 'B' : 'A';
        assertNull("sandi yang diubah harus ditolak", PenyimpanRahasia.dekripsi(new String(huruf), "refresh_enc"));

        assertNull(PenyimpanRahasia.dekripsi(null, "x"));
        assertNull(PenyimpanRahasia.dekripsi("", "x"));
        assertNull(PenyimpanRahasia.dekripsi("teks biasa", "x"));
        assertNull(PenyimpanRahasia.dekripsi("v1:", "x"));
        assertNull(PenyimpanRahasia.dekripsi("v1:###bukan-base64###", "x"));
    }

    @Test
    public void kunciHilangMembuatTokenDianggapTidakAdaTanpaCrashDanBisaLoginUlang() {
        PengaturanSync pref = baru();
        pref.simpanToken("krani1", token());
        assertTrue(pref.adaToken());

        // Meniru data dipulihkan ke HP lain: sandi ada, kunci Keystore tidak.
        PenyimpanRahasia.hapusKunci();

        assertNull(pref.refresh());
        assertNull(pref.akses());
        assertFalse(pref.adaToken());

        // Login online ulang membuat kunci baru dan semuanya normal lagi.
        pref.simpanToken("krani1", new ServerApi.Token("AKSES-BARU", 1_800_000_000_000L, "REFRESH-BARU"));
        assertEquals("REFRESH-BARU", pref.refresh());
        assertEquals("AKSES-BARU", pref.akses());
    }

    @Test
    public void hapusTokenMenghapusSemuaTermasukSandi() {
        PengaturanSync pref = baru();
        pref.simpanToken("krani1", token());
        pref.hapusToken();

        assertFalse(pref.adaToken());
        assertNull(pref.akses());
        assertNull(pref.tokenUser());
        assertFalse(mentah.contains("refresh_enc"));
        assertFalse(mentah.contains("akses_enc"));
    }
}
