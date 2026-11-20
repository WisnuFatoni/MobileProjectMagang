package com.example.fai26.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/** Persetujuan krani lewat server: daftar, setujui, tolak, dan perilaku token (tanpa jaringan sungguhan). */
@RunWith(AndroidJUnit4.class)
public class PersetujuanServerTest {

    private static final long AWAL = 1_794_366_000_000L;

    private PengaturanSync pref;
    private FakeServerApi api;
    private PersetujuanServer ps;
    private long jam = AWAL;

    @Before
    public void siapkan() {
        Context ctx = ApplicationProvider.getApplicationContext();
        pref = new PengaturanSync(ctx, "fai26_persetujuan_uji");
        pref.aturServerUrl("http://server.uji");
        pref.hapusToken();
        api = new FakeServerApi();
        api.sekarangMillis = AWAL;
        pref.simpanToken("rony", new ServerApi.Token(api.tokenSah, AWAL + 15 * 60_000L, "REFRESH-0"));
        ps = new PersetujuanServer(api, pref, () -> jam);
    }

    @After
    public void bersihkan() {
        pref.hapusToken();
    }

    private static ServerApi.Menunggu contoh(String id) {
        ServerApi.Menunggu m = new ServerApi.Menunggu();
        m.id = id;
        m.blokKode = "A12";
        m.tphKode = "01";
        m.totalJanjang = 10;
        m.pencatatNama = "Royyy";
        m.grading.put("ripe", 9L);
        return m;
    }

    @Test
    public void memuatSetujuiDanTolakMemakaiTokenLogin() throws Exception {
        api.menunggu.add(contoh("a1"));
        api.menunggu.add(contoh("a2"));
        List<ServerApi.Menunggu> daftar = ps.muat();
        assertEquals(2, daftar.size());
        assertEquals(9L, (long) daftar.get(0).grading.get("ripe"));

        ps.setujui("a1");
        ps.tolak("a2", "blok salah");
        assertEquals("a1", api.disetujui.get(0));
        assertEquals("a2", api.ditolak.get(0)[0]);
        assertEquals("blok salah", api.ditolak.get(0)[1]);
        assertEquals("AKSES-1", api.tokenDipakai.get(0));
    }

    @Test
    public void tokenKedaluwarsaDiperbaruiSekaliLaluDiulang() throws Exception {
        api.tokenSah = "AKSES-BARU"; // server tidak lagi mengenali token yang tersimpan di HP
        api.menunggu.add(contoh("a1"));
        assertEquals(1, ps.muat().size());
        assertEquals("refresh dipakai sekali", 1, api.jumlahRefresh);
    }

    @Test
    public void tanpaLoginOnlineAtauServerBelumDiaturDijawabGalatJelas() {
        pref.hapusToken();
        try {
            ps.muat();
            fail("tanpa token harus gagal");
        } catch (ApiException e) {
            assertEquals(401, e.kode);
            assertTrue(e.getMessage(), e.getMessage().contains("login online"));
        }
    }

    @Test
    public void galatDariServerDiteruskanTermasukSudahDiprosesOrangLain() {
        api.galatMenunggu.add(new ApiException(409, "Catatan sudah diproses (status BARU)"));
        try {
            ps.setujui("a1");
            fail("409 harus diteruskan");
        } catch (ApiException e) {
            assertEquals(409, e.kode);
        }
    }
}
