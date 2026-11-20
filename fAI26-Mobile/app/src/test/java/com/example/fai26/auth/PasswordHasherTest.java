package com.example.fai26.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PasswordHasherTest {

    private static final String SALT_HEX = "000102030405060708090a0b0c0d0e0f";

    @Test
    public void cocokDenganNilaiAcuanPbkdf2Hmac_Sha256() {
        // Nilai acuan dihitung dengan hashlib.pbkdf2_hmac('sha256', ...) milik Python.
        // 'salt' = 73616c74, 'NaCl' = 4e61436c.
        assertEquals("55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc",
                PasswordHasher.hash("passwd", "73616c74", 1));
        assertEquals("a18495e3ce61675c4dd12a6ab7f919f2ec4e4ebf1978351eb3d2fb84839ca56e",
                PasswordHasher.hash("password", "4e61436c", 80000));
        assertEquals("50dd1f0f403671be3a155ab019c04909388698eb7864a69cd3bd5195aa5c0dfa",
                PasswordHasher.hash("krani123", SALT_HEX, 1000));
    }

    @Test
    public void verifierBenarDanSalahDikenali() {
        String verifier = "1000$" + PasswordHasher.hash("krani123", SALT_HEX, 1000);
        assertTrue(PasswordHasher.cocok("krani123", SALT_HEX, verifier));
        assertFalse(PasswordHasher.cocok("krani124", SALT_HEX, verifier));
        assertFalse(PasswordHasher.cocok("Krani123", SALT_HEX, verifier));
    }

    @Test
    public void verifierRusakTidakMelempar() {
        assertFalse(PasswordHasher.cocok("x", SALT_HEX, "tanpa-pemisah"));
        assertFalse(PasswordHasher.cocok("x", SALT_HEX, "abc$def"));
        assertFalse(PasswordHasher.cocok("x", SALT_HEX, "0$def"));
        assertFalse(PasswordHasher.cocok(null, SALT_HEX, "1$aa"));
    }

    @Test
    public void garamBerbedaMenghasilkanVerifierBerbeda() {
        String a = PasswordHasher.buatSalt();
        String b = PasswordHasher.buatSalt();
        assertEquals(32, a.length());
        assertNotEquals(a, b);
    }

    @Test(expected = IllegalArgumentException.class)
    public void passwordKosongDitolak() {
        PasswordHasher.hash("", SALT_HEX, 1);
    }
}
