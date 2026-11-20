package com.example.fai26.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.fai26.auth.AuthService;
import com.example.fai26.auth.GantiPasswordService;
import com.example.fai26.db.AppDatabase;
import com.example.fai26.db.MasterLoader;
import com.example.fai26.db.Waktu;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Ganti password dari HP: server memeriksa password lama, lalu verifier offline ikut diperbarui. */
@RunWith(AndroidJUnit4.class)
public class GantiPasswordServiceTest {

    private static final long AWAL = 1_794_279_600_000L;
    private static final long HARI = 24L * 60 * 60 * 1000;

    private AppDatabase room;
    private AuthService auth;
    private PengaturanSync pref;
    private FakeServerApi api;
    private GantiPasswordService svc;
    private long jam = AWAL;

    @Before
    public void siapkan() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        room = Room.inMemoryDatabaseBuilder(ctx, AppDatabase.class).addCallback(AppDatabase.callback()).build();
        MasterLoader.muatBilaKosong(ctx, room);
        room.getOpenHelper().getWritableDatabase().execSQL(
                "INSERT INTO device (device_id, aktif, dibuat_pada) VALUES ('dev-uji', 1, ?)", new Object[]{Waktu.utcSekarang()});
        auth = new AuthService(room, "dev-uji", () -> jam);
        auth.aturPassword(1, "krani123", AWAL + 7 * HARI);

        pref = new PengaturanSync(ctx, "fai26_sync_gp_uji");
        pref.aturServerUrl("http://server.uji");
        pref.hapusToken();
        api = new FakeServerApi();
        api.sekarangMillis = AWAL;
        pref.simpanToken("krani1", new ServerApi.Token(api.tokenSah, AWAL + 15 * 60_000L, "REFRESH-0"));
        svc = new GantiPasswordService(auth, api, pref, () -> jam);
    }

    @After
    public void bersihkan() {
        pref.hapusToken();
        room.close();
    }

    private AuthService.Status loginOffline(String password) {
        return auth.loginOffline("krani1", password).status;
    }

    @Test
    public void berhasilMengirimKeServerLaluMemperbaruiVerifierOffline() {
        GantiPasswordService.Hasil h = svc.ganti("krani123", "PasswordBaru#2026", "PasswordBaru#2026");

        assertTrue(h.pesan, h.berhasil);
        assertEquals(1, api.gantiPasswordDikirim.size());
        String[] k = api.gantiPasswordDikirim.get(0);
        assertEquals(api.tokenSah, k[0]);
        assertEquals("krani123", k[1]);
        assertEquals("PasswordBaru#2026", k[2]);
        assertEquals(AuthService.Status.BERHASIL, loginOffline("PasswordBaru#2026"));
        assertEquals("password lama tidak berlaku lagi", AuthService.Status.SALAH, loginOffline("krani123"));
        // Audit lokal tercatat.
        try (android.database.Cursor c = room.getOpenHelper().getReadableDatabase()
                .query("SELECT COUNT(*) FROM audit_log WHERE aksi = 'GANTI_PASSWORD'")) {
            c.moveToFirst();
            assertEquals(1, c.getLong(0));
        }
    }

    @Test
    public void isianTidakSahDitolakTanpaMenghubungiServer() {
        assertFalse(svc.ganti("", "PasswordBaru#2026", "PasswordBaru#2026").berhasil);
        assertFalse(svc.ganti("krani123", "pendek", "pendek").berhasil);
        assertFalse(svc.ganti("krani123", "krani123", "krani123").berhasil);
        assertFalse(svc.ganti("krani123", "PasswordBaru#2026", "BEDA-ulangan-1").berhasil);
        assertTrue(api.gantiPasswordDikirim.isEmpty());
        assertEquals(0, api.tokenDipakai.size());
    }

    @Test
    public void serverMenolakPasswordLamaVerifierLokalTidakBerubah() {
        api.galatGantiPassword.add(new ApiException(422, "Password lama salah"));

        GantiPasswordService.Hasil h = svc.ganti("krani123", "PasswordBaru#2026", "PasswordBaru#2026");

        assertFalse(h.berhasil);
        assertEquals("Password lama salah", h.pesan);
        assertEquals(AuthService.Status.BERHASIL, loginOffline("krani123"));
        assertEquals(AuthService.Status.SALAH, loginOffline("PasswordBaru#2026"));
    }

    @Test
    public void jaringanPutusTidakMengubahApaPunDanPesanJelas() {
        api.galatGantiPassword.add(new ApiException(0, "putus"));
        GantiPasswordService.Hasil h = svc.ganti("krani123", "PasswordBaru#2026", "PasswordBaru#2026");
        assertFalse(h.berhasil);
        assertTrue(h.pesan, h.pesan.contains("online"));
        assertEquals(AuthService.Status.BERHASIL, loginOffline("krani123"));
    }

    @Test
    public void batasLajuDiberitahukanTanpaMengubahPassword() {
        api.galatGantiPassword.add(new ApiException(429, "Terlalu banyak permintaan"));
        GantiPasswordService.Hasil h = svc.ganti("krani123", "PasswordBaru#2026", "PasswordBaru#2026");
        assertFalse(h.berhasil);
        assertTrue(h.pesan, h.pesan.contains("Tunggu"));
        assertEquals(AuthService.Status.BERHASIL, loginOffline("krani123"));
    }

    @Test
    public void tokenKedaluwarsaDiperbaruiDenganRefreshLaluBerhasil() {
        pref.simpanToken("krani1", new ServerApi.Token(api.tokenSah, AWAL - 1, "REFRESH-0")); // sudah lewat
        GantiPasswordService.Hasil h = svc.ganti("krani123", "PasswordBaru#2026", "PasswordBaru#2026");
        assertTrue(h.pesan, h.berhasil);
        assertEquals(1, api.jumlahRefresh);
        assertEquals("AKSES-2", api.gantiPasswordDikirim.get(0)[0]);
    }

    @Test
    public void serverMenolakTokenSekaliLaluDiperbaruiDanDiulang() {
        api.tokenSah = "AKSES-LAIN"; // server tidak mengenali token yang tersimpan -> 401 pada panggilan pertama
        GantiPasswordService.Hasil h = svc.ganti("krani123", "PasswordBaru#2026", "PasswordBaru#2026");
        assertTrue(h.pesan, h.berhasil);
        assertEquals(1, api.jumlahRefresh);
        assertEquals(1, api.gantiPasswordDikirim.size());
    }

    @Test
    public void sesiHabisMenghapusTokenDanMintaLoginOnlineUlang() {
        api.galatRefresh.add(new ApiException(401, "Sesi tidak berlaku"));
        pref.simpanToken("krani1", new ServerApi.Token(api.tokenSah, AWAL - 1, "REFRESH-0"));
        GantiPasswordService.Hasil h = svc.ganti("krani123", "PasswordBaru#2026", "PasswordBaru#2026");
        assertFalse(h.berhasil);
        assertTrue(h.pesan, h.pesan.contains("Login online ulang"));
        assertFalse(pref.adaToken());
    }

    @Test
    public void tanpaLoginOnlineAtauTanpaAlamatServerDitolakDenganPesan() {
        pref.hapusToken();
        GantiPasswordService.Hasil h = svc.ganti("krani123", "PasswordBaru#2026", "PasswordBaru#2026");
        assertFalse(h.berhasil);
        assertTrue(h.pesan, h.pesan.contains("Login online dulu"));

        GantiPasswordService tanpaServer = new GantiPasswordService(auth, null, pref, () -> jam);
        assertFalse(tanpaServer.ganti("krani123", "PasswordBaru#2026", "PasswordBaru#2026").berhasil);
        assertTrue(api.gantiPasswordDikirim.isEmpty());
        assertEquals(AuthService.Status.BERHASIL, loginOffline("krani123"));
    }
}
