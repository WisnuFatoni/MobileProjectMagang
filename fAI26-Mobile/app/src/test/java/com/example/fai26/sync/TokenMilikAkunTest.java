package com.example.fai26.sync;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Aturan kepemilikan token online: token hanya boleh dipakai oleh akun yang memilikinya. Bug asalnya: login offline sebagai
 * pengguna lain tidak mengganti token, sehingga aksi ke server (menyetujui, mengirim) dilakukan atas nama pengguna sebelumnya.
 */
public class TokenMilikAkunTest {

    @Test
    public void tanpaTokenTidakAdaYangDibuang() {
        assertFalse(PengaturanSync.tokenBukanMilik(null, "rony"));
        assertFalse(PengaturanSync.tokenBukanMilik(null, null));
    }

    @Test
    public void tokenMilikAkunAktifDipertahankan() {
        assertFalse(PengaturanSync.tokenBukanMilik("rony", "rony"));
    }

    @Test
    public void huruf_besar_kecil_tidak_dibedakan() {
        assertFalse(PengaturanSync.tokenBukanMilik("Rony", "rony"));
        assertFalse(PengaturanSync.tokenBukanMilik("rony", "RONY"));
    }

    @Test
    public void tokenMilikPenggunaLainDibuang() {
        // Kasus nyata: token royyy (mandor) tertinggal, lalu rony (krani) login offline.
        assertTrue(PengaturanSync.tokenBukanMilik("royyy", "rony"));
    }

    @Test
    public void tanpaAkunAktifTokenTidakDipercaya() {
        assertTrue(PengaturanSync.tokenBukanMilik("royyy", null));
    }
}
