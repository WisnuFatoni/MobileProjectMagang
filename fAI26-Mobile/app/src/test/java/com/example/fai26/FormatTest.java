package com.example.fai26;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FormatTest {

    @Test
    public void angkaMemakaiTitikSebagaiPemisahRibuan() {
        assertEquals("3.790", Format.angka(3790));
        assertEquals("12", Format.angka(12));
        assertEquals("1.234.567", Format.angka(1234567));
    }

    @Test
    public void desimalMemakaiKoma() {
        assertEquals("28,5", Format.desimal(28.5));
        assertEquals("30", Format.desimal(30.0));
        assertEquals("133", Format.desimal(133));
    }

    @Test
    public void tanggalDiubahKeHariBulanTahun() {
        assertEquals("10/11/2026", Format.tanggal("2026-11-10"));
        assertEquals("kemarin", Format.tanggal("kemarin"));
        assertEquals(null, Format.tanggal(null));
    }
}
