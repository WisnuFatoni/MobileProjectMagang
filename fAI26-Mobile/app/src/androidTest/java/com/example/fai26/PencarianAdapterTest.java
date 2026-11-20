package com.example.fai26;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.fai26.db.dao.Hasil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Pencarian TPH/pemanen dengan mengetik: semua kata harus cocok, huruf besar/kecil sama, urutan bebas. */
@RunWith(AndroidJUnit4.class)
public class PencarianAdapterTest {

    private PencarianAdapter<Hasil.TphPilihan> adapter;

    private static Hasil.TphPilihan tph(long id, String blok, String no) {
        Hasil.TphPilihan t = new Hasil.TphPilihan();
        t.tphId = id;
        t.blok = blok;
        t.noTph = no;
        return t;
    }

    @Before
    public void siapkan() {
        Context c = ApplicationProvider.getApplicationContext();
        List<Hasil.TphPilihan> data = new ArrayList<>();
        long id = 1;
        for (String blok : new String[]{"A1", "C7", "C7A", "C16", "D5B"}) {
            for (int n = 1; n <= 70; n++) data.add(tph(id++, blok, String.valueOf(n)));
        }
        adapter = new PencarianAdapter<>(c, data);
    }

    private List<String> cari(String q) throws Exception {
        CountDownLatch selesai = new CountDownLatch(1);
        List<String> hasil = new ArrayList<>();
        // Filter membuat Handler di thread pemanggil: harus dari thread utama.
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                adapter.getFilter().filter(q, jumlah -> {
                    for (int i = 0; i < adapter.getCount(); i++) hasil.add(String.valueOf(adapter.getItem(i)));
                    selesai.countDown();
                }));
        assertTrue("penyaringan selesai", selesai.await(5, TimeUnit.SECONDS));
        return hasil;
    }

    @Test
    public void kosongMenampilkanSemua() throws Exception {
        assertEquals(350, cari("").size());
    }

    @Test
    public void kataBlokDanNomorTphCocokTanpaPeduliHurufDanUrutan() throws Exception {
        List<String> a = cari("c7 6");
        assertTrue(a.contains("Blok C7 • TPH 6"));
        assertTrue(a.contains("Blok C7 • TPH 60"));
        assertTrue("blok C7A juga mengandung c7", a.contains("Blok C7A • TPH 6"));
        assertTrue("blok lain tidak ikut", a.stream().noneMatch(s -> s.contains("A1 ") || s.contains("D5B")));
        assertEquals(a, cari("6 C7"));
        assertEquals(a, cari("  C7   6  "));
    }

    @Test
    public void nomorSajaDanTidakAdaHasil() throws Exception {
        List<String> a = cari("tph 70");
        assertEquals(5, a.size());
        assertEquals(0, cari("zzz").size());
    }
}
