package com.example.fai26.auth;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Hash password untuk verifier login offline: PBKDF2-HMAC-SHA256.
 * Ditulis sendiri di atas HmacSHA256 karena PBKDF2WithHmacSHA256 baru ada di Android 8 (API 26),
 * sedangkan minSdk aplikasi 24. Cocok dengan implementasi standar (diuji dengan nilai acuan).
 *
 * Bentuk yang disimpan di kolom offline_hash: "&lt;iterasi&gt;$&lt;hash heksadesimal&gt;".
 */
public final class PasswordHasher {

    public static final int ITERASI = 100_000;
    private static final int PANJANG_HASH = 32;
    private static final int PANJANG_SALT = 16;

    private PasswordHasher() {
    }

    public static String buatSalt() {
        byte[] s = new byte[PANJANG_SALT];
        new SecureRandom().nextBytes(s);
        return hex(s);
    }

    /** @return teks siap simpan di offline_hash. */
    public static String buatVerifier(String password, String saltHex) {
        return ITERASI + "$" + hash(password, saltHex, ITERASI);
    }

    /** Membandingkan password dengan verifier tersimpan, dalam waktu yang tidak bergantung isi. */
    public static boolean cocok(String password, String saltHex, String verifier) {
        if (password == null || saltHex == null || verifier == null) return false;
        int sep = verifier.indexOf('$');
        if (sep <= 0) return false;
        int iterasi;
        try {
            iterasi = Integer.parseInt(verifier.substring(0, sep));
        } catch (NumberFormatException e) {
            return false;
        }
        if (iterasi <= 0) return false;
        byte[] dihitung = hash(password, saltHex, iterasi).getBytes(StandardCharsets.US_ASCII);
        byte[] tersimpan = verifier.substring(sep + 1).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(dihitung, tersimpan);
    }

    /** PBKDF2-HMAC-SHA256 satu blok (32 byte), hasil heksadesimal. */
    public static String hash(String password, String saltHex, int iterasi) {
        byte[] salt = dariHex(saltHex);
        byte[] pwd = password.getBytes(StandardCharsets.UTF_8);
        if (pwd.length == 0) throw new IllegalArgumentException("Password kosong");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pwd, "HmacSHA256"));

            mac.update(salt);
            mac.update(new byte[]{0, 0, 0, 1});
            byte[] u = mac.doFinal();
            byte[] t = u.clone();
            for (int i = 1; i < iterasi; i++) {
                u = mac.doFinal(u);
                for (int j = 0; j < t.length; j++) t[j] ^= u[j];
            }
            byte[] hasil = new byte[PANJANG_HASH];
            System.arraycopy(t, 0, hasil, 0, PANJANG_HASH);
            return hex(hasil);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private static byte[] dariHex(String s) {
        if (s.length() % 2 != 0) throw new IllegalArgumentException("Heksadesimal tidak valid");
        byte[] r = new byte[s.length() / 2];
        for (int i = 0; i < r.length; i++) {
            r[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        }
        return r;
    }
}
