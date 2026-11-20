package com.example.fai26.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.fai26.BuildConfig;

/**
 * Alamat server, token, dan penanda sinkronisasi. Disimpan di SharedPreferences privat aplikasi.
 * Token akses dan refresh dienkripsi (AES-GCM, kunci di Android Keystore) sebelum disimpan; teks polos tidak pernah
 * ditulis. Bila Keystore gagal, token tidak disimpan (pengguna cukup login online ulang) alih-alih jatuh ke teks polos.
 */
public class PengaturanSync {

    private static final String PREF = "fai26_sync";
    private static final String K_URL = "server_url";
    private static final String TAG = "PengaturanSync";
    private static final String K_AKSES = "akses_enc";
    private static final String K_AKSES_EXP = "akses_exp";
    private static final String K_REFRESH = "refresh_enc";
    /** Kolom lama (sebelum enkripsi): berisi teks polos. Dipindahkan lalu dihapus saat pertama dibuka. */
    private static final String K_AKSES_LAMA = "akses";
    private static final String K_REFRESH_LAMA = "refresh";
    private static final String K_USER = "token_user";
    private static final String K_SEJAK = "status_sejak";
    private static final String K_TERAKHIR = "terakhir_waktu";
    private static final String K_RINGKAS = "terakhir_ringkas";

    private final SharedPreferences p;

    public PengaturanSync(Context c) {
        this(c, PREF);
    }

    /** Dengan nama berkas sendiri (dipakai tes supaya tidak menimpa pengaturan aplikasi sungguhan). */
    public PengaturanSync(Context c, String namaBerkas) {
        this.p = c.getApplicationContext().getSharedPreferences(namaBerkas, Context.MODE_PRIVATE);
        pindahkanTokenLama();
    }

    /** Token versi lama tersimpan sebagai teks polos: dienkripsi, lalu teks polosnya dihapus. */
    private void pindahkanTokenLama() {
        String akses = p.getString(K_AKSES_LAMA, null);
        String refresh = p.getString(K_REFRESH_LAMA, null);
        if (akses == null && refresh == null) return;
        SharedPreferences.Editor e = p.edit().remove(K_AKSES_LAMA).remove(K_REFRESH_LAMA);
        try {
            if (akses != null) e.putString(K_AKSES, PenyimpanRahasia.enkripsi(akses, K_AKSES));
            if (refresh != null) e.putString(K_REFRESH, PenyimpanRahasia.enkripsi(refresh, K_REFRESH));
        } catch (java.security.GeneralSecurityException ex) {
            // Tidak bisa dienkripsi: teks polosnya tetap dihapus, pengguna login online ulang.
            Log.e(TAG, "Keystore tidak tersedia: token lama dibuang", ex);
        }
        e.apply();
    }

    public String serverUrl() {
        return p.getString(K_URL, BuildConfig.DEFAULT_SERVER_URL);
    }

    public void aturServerUrl(String url) {
        p.edit().putString(K_URL, url == null ? "" : url.trim()).apply();
    }

    public boolean serverSudahDiatur() {
        return !serverUrl().trim().isEmpty();
    }

    public void simpanToken(String username, ServerApi.Token t) {
        try {
            p.edit().putString(K_USER, username)
                    .putString(K_AKSES, PenyimpanRahasia.enkripsi(t.akses, K_AKSES))
                    .putLong(K_AKSES_EXP, t.aksesKedaluwarsaMillis)
                    .putString(K_REFRESH, PenyimpanRahasia.enkripsi(t.refresh, K_REFRESH)).apply();
        } catch (java.security.GeneralSecurityException ex) {
            Log.e(TAG, "Keystore tidak tersedia: token tidak disimpan (login online ulang diperlukan)", ex);
            hapusToken();
        }
    }

    public void hapusToken() {
        p.edit().remove(K_USER).remove(K_AKSES).remove(K_AKSES_EXP).remove(K_REFRESH)
                .remove(K_AKSES_LAMA).remove(K_REFRESH_LAMA).apply();
    }

    public String tokenUser() {
        return p.getString(K_USER, null);
    }

    public String akses() {
        return PenyimpanRahasia.dekripsi(p.getString(K_AKSES, null), K_AKSES);
    }

    public long aksesKedaluwarsa() {
        return p.getLong(K_AKSES_EXP, 0L);
    }

    public String refresh() {
        return PenyimpanRahasia.dekripsi(p.getString(K_REFRESH, null), K_REFRESH);
    }

    public boolean adaToken() {
        return refresh() != null;
    }

    /** Waktu (millis) perubahan status terakhir yang sudah diambil dari server. */
    public long statusSejak() {
        return p.getLong(K_SEJAK, 0L);
    }

    public void aturStatusSejak(long millis) {
        p.edit().putLong(K_SEJAK, millis).apply();
    }

    public void catatSinkron(long waktuMillis, String ringkasan) {
        p.edit().putLong(K_TERAKHIR, waktuMillis).putString(K_RINGKAS, ringkasan).apply();
    }

    public long sinkronTerakhir() {
        return p.getLong(K_TERAKHIR, 0L);
    }

    public String ringkasanTerakhir() {
        return p.getString(K_RINGKAS, "");
    }
}
