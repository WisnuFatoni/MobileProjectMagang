package com.example.fai26.sync;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Mengenkripsi rahasia (token) sebelum disimpan di SharedPreferences. Kuncinya AES-256 di Android Keystore: tidak bisa
 * dibaca keluar dari perangkat, jadi berkas preferensi yang disalin (cadangan, root, adb) hanya berisi sandi yang tidak
 * bisa dibuka di HP lain.
 *
 * Format sandi: "v1:" + Base64(IV 12 byte + isi terenkripsi + tag GCM). {@code konteks} (misalnya "refresh") ikut
 * diautentikasi, sehingga sandi satu kolom tidak bisa ditukar ke kolom lain.
 */
final class PenyimpanRahasia {

    private static final String TAG = "PenyimpanRahasia";
    private static final String PROVIDER = "AndroidKeyStore";
    static final String ALIAS = "fai26_token_v1";
    private static final String TRANSFORMASI = "AES/GCM/NoPadding";
    private static final String AWALAN = "v1:";
    private static final int PANJANG_IV = 12;
    private static final int PANJANG_TAG_BIT = 128;

    private PenyimpanRahasia() {
    }

    /** @throws GeneralSecurityException bila Keystore tidak tersedia; pemanggil tidak boleh menyimpan teks polos sebagai gantinya. */
    static String enkripsi(String teks, String konteks) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance(TRANSFORMASI);
        c.init(Cipher.ENCRYPT_MODE, kunci());
        c.updateAAD(konteks.getBytes(StandardCharsets.UTF_8));
        byte[] iv = c.getIV();
        byte[] sandi = c.doFinal(teks.getBytes(StandardCharsets.UTF_8));
        byte[] gabung = new byte[iv.length + sandi.length];
        System.arraycopy(iv, 0, gabung, 0, iv.length);
        System.arraycopy(sandi, 0, gabung, iv.length, sandi.length);
        return AWALAN + Base64.encodeToString(gabung, Base64.NO_WRAP);
    }

    /**
     * @return teks asli, atau null bila sandi rusak, diubah, milik kolom lain, atau kuncinya sudah tidak ada
     * (misalnya data dipulihkan ke HP lain). Null berarti "anggap tidak ada token": pengguna login online ulang.
     */
    static String dekripsi(String sandi, String konteks) {
        if (sandi == null || !sandi.startsWith(AWALAN)) return null;
        try {
            byte[] gabung = Base64.decode(sandi.substring(AWALAN.length()), Base64.NO_WRAP);
            if (gabung.length <= PANJANG_IV) return null;
            Cipher c = Cipher.getInstance(TRANSFORMASI);
            c.init(Cipher.DECRYPT_MODE, kunci(),
                    new GCMParameterSpec(PANJANG_TAG_BIT, Arrays.copyOfRange(gabung, 0, PANJANG_IV)));
            c.updateAAD(konteks.getBytes(StandardCharsets.UTF_8));
            return new String(c.doFinal(gabung, PANJANG_IV, gabung.length - PANJANG_IV), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            Log.w(TAG, "Sandi tidak bisa dibuka (" + e.getClass().getSimpleName() + "): token dianggap tidak ada");
            return null;
        }
    }

    private static synchronized SecretKey kunci() throws GeneralSecurityException {
        try {
            KeyStore ks = KeyStore.getInstance(PROVIDER);
            ks.load(null);
            if (!ks.containsAlias(ALIAS)) {
                KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER);
                g.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build());
                return g.generateKey();
            }
            return (SecretKey) ks.getKey(ALIAS, null);
        } catch (java.io.IOException e) {
            throw new GeneralSecurityException("Keystore tidak bisa dibuka", e);
        }
    }

    /** Dipakai tes untuk meniru kunci yang hilang (misalnya data dipulihkan ke HP lain). */
    static void hapusKunci() {
        try {
            KeyStore ks = KeyStore.getInstance(PROVIDER);
            ks.load(null);
            ks.deleteEntry(ALIAS);
        } catch (Exception e) {
            Log.w(TAG, "Kunci tidak bisa dihapus", e);
        }
    }
}
