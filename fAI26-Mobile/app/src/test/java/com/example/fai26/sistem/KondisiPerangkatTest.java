package com.example.fai26.sistem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class KondisiPerangkatTest {

    private static final long GB = 1024L * 1024L * 1024L;
    private static final long MB = 1024L * 1024L;

    @Test
    public void kondisiAmanTanpaPeringatan() {
        assertTrue(KondisiPerangkat.peringatan(80, false, 5 * GB).isEmpty());
        assertTrue(KondisiPerangkat.peringatan(16, false, 300 * MB).isEmpty()); // tepat di batas aman
    }

    @Test
    public void bateraiRendahMemperingatkanKecualiSedangDiisi() {
        List<String> p = KondisiPerangkat.peringatan(12, false, 5 * GB);
        assertEquals(1, p.size());
        assertTrue(p.get(0), p.get(0).contains("12%"));
        assertTrue("sedang diisi tidak perlu peringatan", KondisiPerangkat.peringatan(12, true, 5 * GB).isEmpty());
        assertEquals(1, KondisiPerangkat.peringatan(KondisiPerangkat.BATERAI_RENDAH_PERSEN, false, 5 * GB).size());
        assertEquals(0, KondisiPerangkat.peringatan(KondisiPerangkat.BATERAI_RENDAH_PERSEN + 1, false, 5 * GB).size());
    }

    @Test
    public void ruangMenipisDanKritisBerbedaPesan() {
        List<String> menipis = KondisiPerangkat.peringatan(90, false, 120 * MB);
        assertEquals(1, menipis.size());
        assertTrue(menipis.get(0), menipis.get(0).contains("menipis"));
        List<String> kritis = KondisiPerangkat.peringatan(90, false, 20 * MB);
        assertEquals(1, kritis.size());
        assertTrue(kritis.get(0), kritis.get(0).contains("hampir penuh"));
    }

    @Test
    public void duaMasalahDuaPeringatanDanNilaiTakDiketahuiDiabaikan() {
        assertEquals(2, KondisiPerangkat.peringatan(5, false, 10 * MB).size());
        assertTrue("tidak diketahui tidak boleh dianggap bermasalah", KondisiPerangkat.peringatan(-1, false, -1).isEmpty());
    }
}
