package com.example.fai26.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;

import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.fai26.db.AppDatabase;
import com.example.fai26.db.MasterLoader;
import com.example.fai26.sync.ApiException;
import com.example.fai26.sync.PengaturanSync;
import com.example.fai26.sync.ServerApi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class LoginServiceTest {

    private static final long HARI = 24L * 60 * 60_000L;
    /** 10 November 2026 03:00 UTC. */
    private static final long AWAL = 1_794_279_600_000L;

    private AppDatabase room;
    private SupportSQLiteDatabase db;
    private AuthService auth;
    private PengaturanSync pref;
    private ApiLogin api;

    /** Server tiruan khusus login. */
    private static class ApiLogin implements ServerApi {
        ApiException galat;
        int panggilan;

        @Override
        public LoginHasil login(String u, String p, String d, String n, String o, String a) throws ApiException {
            panggilan++;
            if (galat != null) throw galat;
            return new LoginHasil(new Token("AKSES", AWAL + 900_000L, "REFRESH"), u, "Nama", "KP_STL");
        }

        @Override
        public Token refresh(String r) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String kirimPanen(String t, JSONObject p) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String kirimFoto(String t, String p, String f, String s, String j, File b) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int kirimAudit(String t, JSONArray b) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<StatusItem> statusSejak(String t, long s) {
            return Collections.emptyList();
        }

        @Override
        public MasterPaket master(String t, String v) {
            throw new UnsupportedOperationException();
        }

        @Override
        public KoordinatHasil kirimKoordinatTph(String t, long id, double lat, double lon, double ak) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void gantiPassword(String t, String lama, String baru) {
            throw new UnsupportedOperationException();
        }
    }

    @Before
    public void siapkan() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        room = Room.inMemoryDatabaseBuilder(ctx, AppDatabase.class).addCallback(AppDatabase.callback()).build();
        db = room.getOpenHelper().getWritableDatabase();
        MasterLoader.muatBilaKosong(ctx, room);
        db.execSQL("INSERT INTO device (device_id, aktif, dibuat_pada) VALUES ('dev-uji', 1, '2026-11-10T00:00:00Z')");
        auth = new AuthService(room, "dev-uji", () -> AWAL);
        auth.aturPassword(1, "krani123", AWAL + 7 * HARI);
        pref = new PengaturanSync(ctx, "fai26_sync_login_uji");
        pref.aturServerUrl("http://server.uji");
        pref.hapusToken();
        api = new ApiLogin();
    }

    @After
    public void bersihkan() {
        pref.hapusToken();
        room.close();
    }

    private LoginService layanan(ServerApi server) {
        return new LoginService(auth, server, pref, null, "dev-uji", "HP uji", "Android 14", "1.0");
    }

    private long angka(String sql) {
        try (Cursor c = db.query(sql)) {
            c.moveToFirst();
            return c.getLong(0);
        }
    }

    @Test
    public void loginOnlineBerhasilMenyimpanTokenDanMemperbaruiVerifierOffline() {
        LoginService.Masuk m = layanan(api).masuk("krani1", "passwordBaru1");

        assertTrue(m.online);
        assertEquals(AuthService.Status.BERHASIL, m.hasil.status);
        assertEquals(1, m.hasil.userId);
        assertEquals("AKSES", pref.akses());
        assertEquals("REFRESH", pref.refresh());
        assertEquals("krani1", pref.tokenUser());
        assertEquals(1, angka("SELECT COUNT(*) FROM audit_log WHERE aksi = 'LOGIN' AND keterangan = 'ONLINE'"));

        // Password yang diterima server kini juga berlaku offline; yang lama tidak.
        assertEquals(AuthService.Status.BERHASIL, auth.loginOffline("krani1", "passwordBaru1").status);
        assertEquals(AuthService.Status.SALAH, auth.loginOffline("krani1", "krani123").status);
    }

    @Test
    public void loginOnlineMemperpanjangMasaLoginOffline() {
        db.execSQL("UPDATE app_user SET offline_berlaku_sampai = '2026-11-10T04:00:00Z' WHERE user_id = 1");
        layanan(api).masuk("krani1", "krani123");
        assertTrue(auth.masihBerlaku(1));
        try (Cursor c = db.query("SELECT offline_berlaku_sampai FROM app_user WHERE user_id = 1")) {
            c.moveToFirst();
            assertEquals("2026-11-17T03:00:00Z", c.getString(0)); // AWAL + 7 hari
        }
    }

    @Test
    public void passwordSalahDiServerDitolakMeskiVerifierOfflineCocok() {
        api.galat = new ApiException(401, "Username atau password salah");
        // "krani123" cocok dengan verifier offline, tetapi server menolak (misalnya password sudah diganti).
        LoginService.Masuk m = layanan(api).masuk("krani1", "krani123");

        assertTrue(m.online);
        assertEquals(AuthService.Status.SALAH, m.hasil.status);
        assertNull(pref.akses());
        assertEquals(1, angka("SELECT COUNT(*) FROM audit_log WHERE aksi = 'LOGIN_GAGAL' AND keterangan LIKE 'ONLINE: ditolak server%'"));
    }

    @Test
    public void akunTerkunciDanNonaktifDiServer() {
        api.galat = new ApiException(423, "Akun terkunci sementara");
        LoginService.Masuk terkunci = layanan(api).masuk("krani1", "krani123");
        assertEquals(AuthService.Status.TERKUNCI, terkunci.hasil.status);
        assertEquals(AuthService.KUNCI_MENIT, terkunci.hasil.menitKunci);

        api.galat = new ApiException(403, "Akun tidak aktif");
        assertEquals(AuthService.Status.TIDAK_AKTIF, layanan(api).masuk("krani1", "krani123").hasil.status);
    }

    @Test
    public void jaringanPutusAtauServerRusakJatuhKeOffline() {
        for (int kode : new int[]{0, 500, 503}) {
            api.galat = new ApiException(kode, "tidak terjangkau");
            LoginService.Masuk m = layanan(api).masuk("krani1", "krani123");
            assertFalse("kode " + kode, m.online);
            assertEquals("kode " + kode, AuthService.Status.BERHASIL, m.hasil.status);
        }
        assertNull(pref.akses()); // tanpa login online tidak ada token
    }

    @Test
    public void loginOfflineSebagaiPenggunaLainMembuangTokenOnlineAkunSebelumnya() {
        // Token online milik mandor1 tertinggal di HP, lalu krani1 login saat server tidak terjangkau.
        pref.simpanToken("mandor1", new ServerApi.Token("AKSES_LAMA", AWAL + 900_000L, "REFRESH_LAMA"));
        api.galat = new ApiException(0, "putus");

        LoginService.Masuk m = layanan(api).masuk("krani1", "krani123");

        assertFalse(m.online);
        assertEquals(AuthService.Status.BERHASIL, m.hasil.status);
        assertNull("token milik mandor1 tidak boleh dipakai atas nama krani1", pref.akses());
        assertNull(pref.refresh());
        assertNull(pref.tokenUser());
        assertEquals("krani1", pref.akunAktif());
    }

    @Test
    public void loginOfflineSebagaiPemilikTokenMempertahankanTokenNya() {
        pref.simpanToken("krani1", new ServerApi.Token("AKSES_SAYA", AWAL + 900_000L, "REFRESH_SAYA"));
        api.galat = new ApiException(0, "putus");

        layanan(api).masuk("krani1", "krani123");

        assertEquals("AKSES_SAYA", pref.akses());
        assertEquals("krani1", pref.tokenUser());
        assertEquals("krani1", pref.akunAktif());
    }

    @Test
    public void jaringanPutusTetapMenolakPasswordSalah() {
        api.galat = new ApiException(0, "putus");
        LoginService.Masuk m = layanan(api).masuk("krani1", "salah");
        assertFalse(m.online);
        assertEquals(AuthService.Status.SALAH, m.hasil.status);
    }

    @Test
    public void tanpaAlamatServerLangsungOffline() {
        pref.aturServerUrl("");
        LoginService.Masuk m = layanan(null).masuk("krani1", "krani123");
        assertFalse(m.online);
        assertEquals(AuthService.Status.BERHASIL, m.hasil.status);
        assertEquals(0, api.panggilan);
    }

    @Test
    public void akunBelumAdaDiMasterHp() {
        LoginService.Masuk m = layanan(api).masuk("orang_baru", "apaSaja123");
        assertTrue(m.online);
        assertEquals(AuthService.Status.AKUN_BELUM_ADA, m.hasil.status);
        assertNull(pref.akses());
        assertNotNull(m.hasil);
    }
}
