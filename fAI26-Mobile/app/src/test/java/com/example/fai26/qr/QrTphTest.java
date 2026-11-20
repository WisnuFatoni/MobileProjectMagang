package com.example.fai26.qr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class QrTphTest {

    @Test
    public void qrTphYangBenarDibaca() {
        QrTph.Hasil h = QrTph.baca("FAI26|TPH|1|KP_STL|A12|07");
        assertTrue(h.berhasil());
        assertEquals("KP_STL", h.tph.unit);
        assertEquals("A12", h.tph.blok);
        assertEquals("07", h.tph.noTph);
        assertEquals("FAI26|TPH|1|KP_STL|A12|07", h.tph.kode);
        assertNull(h.galat);
    }

    @Test
    public void spasiDanBarisBaruDiTepiDiabaikan() {
        QrTph.Hasil h = QrTph.baca("  FAI26|TPH|1|KP_STL|A12|07\r\n");
        assertTrue(h.berhasil());
        assertEquals("FAI26|TPH|1|KP_STL|A12|07", h.tph.kode);
    }

    @Test
    public void buatKodeSamaDenganYangDibaca() {
        String kode = QrTph.buatKode("KP_STL", "A13", "02");
        assertEquals("FAI26|TPH|1|KP_STL|A13|02", kode);
        assertEquals(kode, QrTph.baca(kode).tph.kode);
    }

    @Test
    public void qrBukanMilikAplikasiDitolak() {
        for (String s : new String[]{"https://contoh.com/tph/7", "HELLO", "FAI27|TPH|1|U|B|1",
                "FAI26|TPH|1|KP_STL|A12", "FAI26|TPH|1|KP_STL|A12|07|lebih"}) {
            QrTph.Hasil h = QrTph.baca(s);
            assertFalse(s, h.berhasil());
            assertNotNull(h.galat);
            assertTrue(h.galat, h.galat.contains("bukan QR fAI26"));
        }
    }

    @Test
    public void jenisQrLainDitolakDenganPesanJelas() {
        // Misalnya nanti ada QR kartu pemanen.
        QrTph.Hasil h = QrTph.baca("FAI26|PEMANEN|1|KP_STL|A12|07");
        assertFalse(h.berhasil());
        assertTrue(h.galat, h.galat.contains("PEMANEN"));
    }

    @Test
    public void versiBelumDidukungDitolak() {
        QrTph.Hasil h = QrTph.baca("FAI26|TPH|2|KP_STL|A12|07");
        assertFalse(h.berhasil());
        assertTrue(h.galat, h.galat.contains("Perbarui aplikasi"));
    }

    @Test
    public void bagianKosongDitolak() {
        assertFalse(QrTph.baca("FAI26|TPH|1||A12|07").berhasil());
        assertFalse(QrTph.baca("FAI26|TPH|1|KP_STL||07").berhasil());
        assertFalse(QrTph.baca("FAI26|TPH|1|KP_STL|A12|").berhasil());
    }

    @Test
    public void kosongAtauNullDitolak() {
        assertFalse(QrTph.baca(null).berhasil());
        assertFalse(QrTph.baca("   ").berhasil());
    }
}
