package com.example.fai26.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Aturan isian ganti password (harus sama dengan aturan server: 8 sampai 72 karakter, beda dari yang lama). */
public class GantiPasswordServiceTest {

    @Test
    public void isianSahLolos() {
        assertNull(GantiPasswordService.periksa("lama", "PasswordBaru#2026", "PasswordBaru#2026"));
        assertNull("tepat 8 karakter", GantiPasswordService.periksa("lama", "12345678", "12345678"));
        assertNull("tepat 72 karakter", GantiPasswordService.periksa("lama", repeat('a', 72), repeat('a', 72)));
    }

    @Test
    public void passwordLamaKosongDitolak() {
        assertEquals("Isi password sekarang", GantiPasswordService.periksa("", "PasswordBaru#2026", "PasswordBaru#2026"));
        assertEquals("Isi password sekarang", GantiPasswordService.periksa(null, "PasswordBaru#2026", "PasswordBaru#2026"));
    }

    @Test
    public void panjangPasswordBaruDibatasi() {
        assertNotNull(GantiPasswordService.periksa("lama", "1234567", "1234567"));
        assertNotNull(GantiPasswordService.periksa("lama", null, null));
        assertNotNull(GantiPasswordService.periksa("lama", repeat('a', 73), repeat('a', 73)));
    }

    @Test
    public void harusBerbedaDariLamaDanUlanganHarusSama() {
        assertEquals("Password baru harus berbeda dari password sekarang",
                GantiPasswordService.periksa("PasswordSama1", "PasswordSama1", "PasswordSama1"));
        assertEquals("Ulangan password baru tidak sama",
                GantiPasswordService.periksa("lama", "PasswordBaru#2026", "PasswordBaru#2027"));
    }

    private static String repeat(char c, int n) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < n; i++) s.append(c);
        return s.toString();
    }
}
