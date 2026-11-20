package com.example.fai26.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NavGeserTest {

    // Empat tab selebar 100 px, kapsul pertama mulai di x = 10.
    private static final float AWAL = 10f;
    private static final float LEBAR = 100f;

    @Test
    public void posisiTabMengikutiIndeks() {
        assertEquals(10f, NavGeser.posisiTab(0, AWAL, LEBAR), 0f);
        assertEquals(310f, NavGeser.posisiTab(3, AWAL, LEBAR), 0f);
    }

    @Test
    public void batasMenahDiUjungKiriDanKanan() {
        assertEquals(10f, NavGeser.batas(-50f, AWAL, LEBAR, 4), 0f);
        assertEquals(310f, NavGeser.batas(999f, AWAL, LEBAR, 4), 0f);
        assertEquals(150f, NavGeser.batas(150f, AWAL, LEBAR, 4), 0f);
    }

    @Test
    public void terdekatDibulatkanKeTabYangPalingDekat() {
        assertEquals(0, NavGeser.indeksTerdekat(10f, AWAL, LEBAR, 4));
        assertEquals(0, NavGeser.indeksTerdekat(59f, AWAL, LEBAR, 4)); // kurang dari setengah ke tab 1
        assertEquals(1, NavGeser.indeksTerdekat(61f, AWAL, LEBAR, 4));
        assertEquals(2, NavGeser.indeksTerdekat(205f, AWAL, LEBAR, 4));
        assertEquals(3, NavGeser.indeksTerdekat(310f, AWAL, LEBAR, 4));
    }

    @Test
    public void terdekatTidakKeluarRentang() {
        assertEquals(0, NavGeser.indeksTerdekat(-500f, AWAL, LEBAR, 4));
        assertEquals(3, NavGeser.indeksTerdekat(5000f, AWAL, LEBAR, 4));
    }

    @Test
    public void lensaMembesarkanIkonDiBawahKapsul() {
        // Tepat di bawah kapsul: skala maksimum. Setengah tab: setengah jalan. Satu tab atau lebih: 1.
        assertEquals(1.25f, NavGeser.skalaLensa(100f, 100f, LEBAR, 1.25f), 1e-6f);
        assertEquals(1.125f, NavGeser.skalaLensa(100f, 150f, LEBAR, 1.25f), 1e-6f);
        assertEquals(1f, NavGeser.skalaLensa(100f, 200f, LEBAR, 1.25f), 1e-6f);
        assertEquals(1f, NavGeser.skalaLensa(100f, 900f, LEBAR, 1.25f), 1e-6f);
        assertEquals(1f, NavGeser.skalaLensa(100f, 100f, 0f, 1.25f), 1e-6f);
    }

    @Test
    public void masukanAneh() {
        assertEquals(0, NavGeser.indeksTerdekat(100f, AWAL, LEBAR, 0));
        assertEquals(0, NavGeser.indeksTerdekat(100f, AWAL, 0f, 4));
        assertEquals(10f, NavGeser.batas(500f, AWAL, LEBAR, 1), 0f); // satu tab: tidak ada ruang geser
    }
}
