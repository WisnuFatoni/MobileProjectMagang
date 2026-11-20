package com.example.fai26.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.database.Cursor;

import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.fai26.auth.AuthService;
import com.example.fai26.db.AppDatabase;
import com.example.fai26.db.MasterLoader;
import com.example.fai26.db.dao.Hasil;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class MasterSyncTest {

    /** 11 November 2026 03:00 UTC. */
    private static final long AWAL = 1_794_366_000_000L;

    private Context ctx;
    private AppDatabase room;
    private SupportSQLiteDatabase db;
    private MasterSync master;

    @Before
    public void siapkan() {
        ctx = ApplicationProvider.getApplicationContext();
        room = Room.inMemoryDatabaseBuilder(ctx, AppDatabase.class).addCallback(AppDatabase.callback()).build();
        db = room.getOpenHelper().getWritableDatabase();
        master = new MasterSync(room, () -> AWAL);
    }

    @After
    public void bersihkan() {
        room.close();
    }

    private long angka(String sql) {
        try (Cursor c = db.query(sql)) {
            c.moveToFirst();
            return c.getLong(0);
        }
    }

    private String teks(String sql) {
        try (Cursor c = db.query(sql)) {
            return c.moveToFirst() && !c.isNull(0) ? c.getString(0) : null;
        }
    }

    /** Paket master seperti jawaban server (lihat fai26-api/docs/API.md). */
    private static JSONObject paket(String versi) throws Exception {
        return new JSONObject("{"
                + "\"versi\":\"" + versi + "\",\"berubah\":true,"
                + "\"unit\":{\"unit_id\":1,\"kode\":\"KP_STL\",\"nama\":\"Kebun Server\",\"erp_db\":\"kp_stl2025\",\"tahun_cluster\":2026,"
                + "\"aktif\":true,\"dibuat_pada\":\"2026-11-10T07:00:00Z\",\"diubah_pada\":null,\"dihapus_pada\":null},"
                + "\"divisi\":[{\"divisi_id\":1,\"unit_id\":1,\"erp_oid\":null,\"kode\":\"DIV2\",\"nama\":\"Divisi 2\",\"aktif\":true,"
                + "\"dibuat_pada\":\"2026-11-10T07:00:00Z\",\"diubah_pada\":null,\"dihapus_pada\":null}],"
                + "\"blok\":[{\"blok_id\":1,\"unit_id\":1,\"divisi_id\":1,\"erp_oid\":1012,\"kode\":\"A12\",\"nama\":null,\"luas_ha\":28.5,"
                + "\"tahun_tanam\":2012,\"sph\":133.0,\"aktif\":true,\"dibuat_pada\":\"2026-11-10T07:00:00Z\",\"diubah_pada\":null,\"dihapus_pada\":null}],"
                + "\"tph\":[{\"tph_id\":1,\"blok_id\":1,\"no_tph\":\"01\",\"qr_kode\":\"FAI26|TPH|1|KP_STL|A12|01\",\"lat\":null,\"lon\":null,"
                + "\"aktif\":true,\"dibuat_pada\":\"2026-11-10T07:00:00Z\",\"diubah_pada\":null,\"dihapus_pada\":null},"
                + "{\"tph_id\":2,\"blok_id\":1,\"no_tph\":\"02\",\"qr_kode\":\"FAI26|TPH|1|KP_STL|A12|02\",\"lat\":-2.0,\"lon\":113.0,"
                + "\"aktif\":true,\"dibuat_pada\":\"2026-11-10T07:00:00Z\",\"diubah_pada\":null,\"dihapus_pada\":null}],"
                + "\"pemanen\":[{\"pemanen_id\":1,\"unit_id\":1,\"erp_hrmb_code\":90001,\"kode\":\"P-001\",\"nama\":\"Pemanen Satu\",\"aktif\":true,"
                + "\"dibuat_pada\":\"2026-11-10T07:00:00Z\",\"diubah_pada\":null,\"dihapus_pada\":null}],"
                + "\"user\":[{\"user_id\":1,\"unit_id\":1,\"username\":\"krani1\",\"nama\":\"Krani Satu\",\"aktif\":true,\"erp_user_oid\":null,"
                + "\"role\":[\"KRANI\"],\"dihapus_pada\":null},"
                + "{\"user_id\":2,\"unit_id\":1,\"username\":\"mandor1\",\"nama\":\"Mandor Satu\",\"aktif\":true,\"erp_user_oid\":null,"
                + "\"role\":[\"MANDOR\"],\"dihapus_pada\":null}]}");
    }

    private ServerApi.MasterPaket paketServer(String versi) throws Exception {
        JSONObject j = paket(versi);
        return new ServerApi.MasterPaket(versi, true, j);
    }

    // ---- penerapan -------------------------------------------------------------------------------

    @Test
    public void hpBaruTanpaMasterMenerimaSemuanya() throws Exception {
        assertEquals("", master.versiLokal());
        FakeServerApi api = new FakeServerApi();
        api.masterJawab = paketServer("m-1");

        MasterSync.Hasil h = master.ambil(api, api.tokenSah);

        assertTrue(h.berubah);
        assertEquals("m-1", master.versiLokal());
        assertEquals("", api.versiMasterDikirim);
        assertEquals(1, angka("SELECT COUNT(*) FROM unit"));
        assertEquals(1, angka("SELECT COUNT(*) FROM divisi"));
        assertEquals(1, h.blok);
        assertEquals(2, h.tph);
        assertEquals(1, h.pemanen);
        assertEquals(2, h.user);
        assertEquals("Kebun Server", teks("SELECT nama FROM unit WHERE unit_id = 1"));
        assertEquals(1012, angka("SELECT erp_oid FROM blok WHERE kode = 'A12'"));
        assertTrue(master.sinkronTerakhir() > 0);

        // Data langsung bisa dipakai aplikasi: daftar TPH dan pencarian dari QR.
        List<Hasil.TphPilihan> tph = room.masterDao().daftarTph();
        assertEquals(2, tph.size());
        assertEquals("A12", tph.get(0).blok);
        assertNotNull(room.masterDao().tphDariQr("FAI26|TPH|1|KP_STL|A12|02"));
        assertEquals(1, room.masterDao().daftarPemanen().size());
    }

    @Test
    public void akunDariServerBisaLoginOnlineDanPunyaPermission() throws Exception {
        FakeServerApi api = new FakeServerApi();
        api.masterJawab = paketServer("m-1");
        master.ambil(api, api.tokenSah);

        AuthService auth = new AuthService(room, "dev-uji", () -> AWAL);
        assertTrue(auth.akunAda("mandor1"));
        AuthService.Hasil h = auth.terapkanLoginOnline("mandor1", "rahasia123");
        assertEquals(AuthService.Status.BERHASIL, h.status);
        assertEquals(2, h.userId);
        assertTrue(auth.permission(2).contains("panen.setujui"));
        assertFalse(auth.permission(1).contains("panen.setujui")); // krani tidak
        assertEquals(AuthService.Status.BERHASIL, auth.loginOffline("mandor1", "rahasia123").status);
    }

    @Test
    public void versiSamaTidakMenerapkanApaPun() throws Exception {
        FakeServerApi api = new FakeServerApi();
        api.masterJawab = paketServer("m-1");
        master.ambil(api, api.tokenSah);
        db.execSQL("UPDATE blok SET kode = 'DIUBAH-LOKAL' WHERE blok_id = 1");

        MasterSync.Hasil h = master.ambil(api, api.tokenSah);

        assertFalse(h.berubah);
        assertEquals("m-1", api.versiMasterDikirim);
        assertEquals("DIUBAH-LOKAL", teks("SELECT kode FROM blok WHERE blok_id = 1")); // tidak ditimpa
    }

    @Test
    public void masterBaruMemperbaruiYangAdaDanMenambahYangBaru() throws Exception {
        FakeServerApi api = new FakeServerApi();
        api.masterJawab = paketServer("m-1");
        master.ambil(api, api.tokenSah);

        JSONObject j = paket("m-2");
        j.getJSONObject("unit").put("nama", "Kebun Server Baru");
        j.getJSONArray("pemanen").getJSONObject(0).put("nama", "Pemanen Satu (ubah)");
        j.getJSONArray("pemanen").put(new JSONObject("{\"pemanen_id\":2,\"unit_id\":1,\"erp_hrmb_code\":90002,\"kode\":\"P-002\","
                + "\"nama\":\"Pemanen Dua\",\"aktif\":true,\"dibuat_pada\":\"2026-11-11T01:00:00Z\",\"diubah_pada\":null,\"dihapus_pada\":null}"));
        api.masterJawab = new ServerApi.MasterPaket("m-2", true, j);
        master.ambil(api, api.tokenSah);

        assertEquals("m-2", master.versiLokal());
        assertEquals(1, angka("SELECT COUNT(*) FROM master_sync")); // baris versi diganti, bukan bertambah
        assertEquals("Kebun Server Baru", teks("SELECT nama FROM unit WHERE unit_id = 1"));
        assertEquals(2, angka("SELECT COUNT(*) FROM pemanen"));
        assertEquals("Pemanen Satu (ubah)", teks("SELECT nama FROM pemanen WHERE pemanen_id = 1"));
    }

    // ---- hak role dari server ----------------------------------------------------------------------

    private long hakRole(String role) {
        return angka("SELECT COUNT(*) FROM role_permission rp JOIN role r ON r.role_id = rp.role_id WHERE r.kode = '" + role + "'");
    }

    private boolean punyaHak(String role, String izin) {
        return angka("SELECT COUNT(*) FROM role_permission rp JOIN role r ON r.role_id = rp.role_id "
                + "JOIN permission p ON p.permission_id = rp.permission_id WHERE r.kode = '" + role + "' AND p.kode = '" + izin + "'") > 0;
    }

    private void terapkanHak(String versi, String hakJson) throws Exception {
        JSONObject j = paket(versi);
        if (hakJson != null) j.put("role_permission", new org.json.JSONArray(hakJson));
        FakeServerApi api = new FakeServerApi();
        api.masterJawab = new ServerApi.MasterPaket(versi, true, j);
        master.ambil(api, api.tokenSah);
    }

    @Test
    public void hakRoleDariServerMenggantikanSemuaHakDiHp() throws Exception {
        // Data awal HP: KRANI memegang beberapa hak, ADMIN juga.
        assertTrue(hakRole("KRANI") > 1);
        long adminSebelum = hakRole("ADMIN");

        terapkanHak("m-1", "[{\"role\":\"KRANI\",\"permission\":[\"panen.lihat\",\"panen.buat\"]},"
                + "{\"role\":\"MANDOR\",\"permission\":[\"panen.setujui\"]}]");

        assertEquals(2, hakRole("KRANI"));
        assertTrue(punyaHak("KRANI", "panen.buat"));
        assertFalse("hak yang dicabut server hilang", punyaHak("KRANI", "panen.kirim"));
        assertEquals(1, hakRole("MANDOR"));
        assertEquals("role yang tidak tercantum berarti tanpa hak", 0, hakRole("ADMIN"));
        assertTrue(adminSebelum >= 0);
    }

    @Test
    public void tanpaKunciHakRoleHpTidakMengubahHak() throws Exception {
        long sebelum = angka("SELECT COUNT(*) FROM role_permission");
        assertTrue(sebelum > 0);
        terapkanHak("m-1", null);
        assertEquals(sebelum, angka("SELECT COUNT(*) FROM role_permission"));
    }

    @Test
    public void daftarHakKosongDiabaikanSupayaTidakMengunciSemuaPengguna() throws Exception {
        long sebelum = angka("SELECT COUNT(*) FROM role_permission");
        terapkanHak("m-1", "[]");
        assertEquals(sebelum, angka("SELECT COUNT(*) FROM role_permission"));
    }

    @Test
    public void roleDanPermissionTakDikenalDilewatiTanpaGalat() throws Exception {
        terapkanHak("m-1", "[{\"role\":\"ROLE_BARU_DI_SERVER\",\"permission\":[\"panen.lihat\"]},"
                + "{\"role\":\"KRANI\",\"permission\":[\"panen.lihat\",\"izin.baru.di.server\",\"panen.lihat\"]}]");
        assertEquals("hanya permission yang dikenal, tanpa ganda", 1, hakRole("KRANI"));
        assertEquals(0, angka("SELECT COUNT(*) FROM role WHERE kode = 'ROLE_BARU_DI_SERVER'"));
    }

    @Test
    public void hakBaruMengubahPermissionPenggunaYangDipakaiAplikasi() throws Exception {
        terapkanHak("m-1", "[{\"role\":\"KRANI\",\"permission\":[\"panen.lihat\"]}]");
        // krani1 (user 1) diberi role KRANI oleh paket di atas; permission efektifnya mengikuti hak role dari server.
        java.util.List<String> izin = room.akunDao().permission(1, "2026-11-11");
        assertEquals(java.util.Collections.singletonList("panen.lihat"), izin);
    }

    // ---- pemberitahuan perubahan master ----------------------------------------------------------

    @Test
    public void masterBaruDiumumkanKePendengarTetapiVersiSamaTidak() throws Exception {
        final java.util.concurrent.atomic.AtomicInteger dipanggil = new java.util.concurrent.atomic.AtomicInteger();
        final boolean[] diThreadUtama = {true};
        PerubahanMaster.Pendengar p = () -> {
            diThreadUtama[0] &= android.os.Looper.myLooper() == android.os.Looper.getMainLooper();
            dipanggil.incrementAndGet();
        };
        PerubahanMaster.daftar(p);
        try {
            FakeServerApi api = new FakeServerApi();
            api.masterJawab = paketServer("m-1");
            master.ambil(api, api.tokenSah);
            tungguThreadUtama();
            assertEquals("master baru harus diumumkan satu kali", 1, dipanggil.get());
            assertTrue("pendengar dipanggil di thread utama", diThreadUtama[0]);

            master.ambil(api, api.tokenSah); // versi sama: tidak berubah
            tungguThreadUtama();
            assertEquals("versi sama tidak boleh diumumkan", 1, dipanggil.get());
        } finally {
            PerubahanMaster.lepas(p);
        }

        // Setelah dilepas, tidak dipanggil lagi.
        FakeServerApi api2 = new FakeServerApi();
        JSONObject j = paket("m-2");
        api2.masterJawab = new ServerApi.MasterPaket("m-2", true, j);
        master.ambil(api2, api2.tokenSah);
        tungguThreadUtama();
        assertEquals(1, dipanggil.get());
    }

    /** Menunggu semua pekerjaan yang sudah diantrekan ke thread utama selesai. */
    private static void tungguThreadUtama() throws Exception {
        final java.util.concurrent.CountDownLatch l = new java.util.concurrent.CountDownLatch(1);
        new android.os.Handler(android.os.Looper.getMainLooper()).post(l::countDown);
        assertTrue(l.await(5, java.util.concurrent.TimeUnit.SECONDS));
    }

    // ---- penghapusan lunak, koordinat, akun ------------------------------------------------------

    @Test
    public void barisDihapusDiServerDitandaiBukanDibuang() throws Exception {
        FakeServerApi api = new FakeServerApi();
        api.masterJawab = paketServer("m-1");
        master.ambil(api, api.tokenSah);

        JSONObject j = paket("m-2");
        JSONObject tph2 = j.getJSONArray("tph").getJSONObject(1);
        tph2.put("aktif", false).put("dihapus_pada", "2026-11-11T02:00:00Z").put("diubah_pada", "2026-11-11T02:00:00Z");
        api.masterJawab = new ServerApi.MasterPaket("m-2", true, j);
        master.ambil(api, api.tokenSah);

        assertEquals(2, angka("SELECT COUNT(*) FROM tph")); // masih ada, untuk catatan lama
        assertEquals("2026-11-11T02:00:00Z", teks("SELECT dihapus_pada FROM tph WHERE tph_id = 2"));
        assertEquals(1, room.masterDao().daftarTph().size()); // tidak muncul lagi di pilihan
    }

    @Test
    public void koordinatTphDariHpTidakDitimpaKosongDariServer() throws Exception {
        FakeServerApi api = new FakeServerApi();
        api.masterJawab = paketServer("m-1");
        master.ambil(api, api.tokenSah);

        // Krani merekam koordinat TPH 1 di HP; server belum punya (lat/lon null).
        db.execSQL("UPDATE tph SET lat = -2.5, lon = 113.5 WHERE tph_id = 1");
        JSONObject j = paket("m-2");
        j.getJSONArray("tph").getJSONObject(0).put("nama_tidak_dipakai", "x");
        api.masterJawab = new ServerApi.MasterPaket("m-2", true, j);
        master.ambil(api, api.tokenSah);

        assertEquals(-2.5, Double.parseDouble(teks("SELECT lat FROM tph WHERE tph_id = 1")), 1e-9);

        // Bila server sudah punya koordinat, server yang menang (TPH 2 di paket: -2.0, 113.0).
        assertEquals(-2.0, Double.parseDouble(teks("SELECT lat FROM tph WHERE tph_id = 2")), 1e-9);
    }

    @Test
    public void akunDiperbaruiTanpaMenyentuhVerifierOffline() throws Exception {
        FakeServerApi api = new FakeServerApi();
        api.masterJawab = paketServer("m-1");
        master.ambil(api, api.tokenSah);
        AuthService auth = new AuthService(room, "dev-uji", () -> AWAL);
        auth.terapkanLoginOnline("krani1", "rahasiaKrani");
        String hashSebelum = teks("SELECT offline_hash FROM app_user WHERE user_id = 1");
        assertNotNull(hashSebelum);

        JSONObject j = paket("m-2");
        j.getJSONArray("user").getJSONObject(0).put("nama", "Krani Satu (ubah)");
        api.masterJawab = new ServerApi.MasterPaket("m-2", true, j);
        master.ambil(api, api.tokenSah);

        assertEquals("Krani Satu (ubah)", teks("SELECT nama FROM app_user WHERE user_id = 1"));
        assertEquals(hashSebelum, teks("SELECT offline_hash FROM app_user WHERE user_id = 1"));
        assertEquals(AuthService.Status.BERHASIL, auth.loginOffline("krani1", "rahasiaKrani").status);
    }

    @Test
    public void akunDinonaktifkanDiServerLangsungTidakBisaLogin() throws Exception {
        FakeServerApi api = new FakeServerApi();
        api.masterJawab = paketServer("m-1");
        master.ambil(api, api.tokenSah);
        AuthService auth = new AuthService(room, "dev-uji", () -> AWAL);
        auth.terapkanLoginOnline("krani1", "rahasiaKrani");

        JSONObject j = paket("m-2");
        j.getJSONArray("user").getJSONObject(0).put("aktif", false);
        api.masterJawab = new ServerApi.MasterPaket("m-2", true, j);
        master.ambil(api, api.tokenSah);

        assertEquals(AuthService.Status.TIDAK_AKTIF, auth.loginOffline("krani1", "rahasiaKrani").status);
        assertFalse(auth.masihBerlaku(1));
    }

    @Test
    public void perubahanRoleDariServerBerlaku() throws Exception {
        FakeServerApi api = new FakeServerApi();
        api.masterJawab = paketServer("m-1");
        master.ambil(api, api.tokenSah);
        AuthService auth = new AuthService(room, "dev-uji", () -> AWAL);
        assertFalse(auth.permission(1).contains("panen.setujui"));

        JSONObject j = paket("m-2");
        j.getJSONArray("user").getJSONObject(0).put("role", new org.json.JSONArray("[\"MANDOR\"]"));
        api.masterJawab = new ServerApi.MasterPaket("m-2", true, j);
        master.ambil(api, api.tokenSah);

        assertTrue(auth.permission(1).contains("panen.setujui"));
        assertEquals(1, angka("SELECT COUNT(*) FROM user_role WHERE user_id = 1"));
    }

    // ---- data yang sudah ada dan kegagalan -------------------------------------------------------

    @Test
    public void masterServerMemperbaruiMasterContohDenganIdSama() throws Exception {
        MasterLoader.muatBilaKosong(ctx, room); // master contoh (id 1..): sama dengan seed server pengembangan
        assertEquals(5, angka("SELECT COUNT(*) FROM tph"));
        FakeServerApi api = new FakeServerApi();
        api.masterJawab = paketServer("m-1");
        master.ambil(api, api.tokenSah);

        assertEquals("Kebun Server", teks("SELECT nama FROM unit WHERE unit_id = 1"));
        assertEquals("Pemanen Satu", teks("SELECT nama FROM pemanen WHERE pemanen_id = 1"));
        assertEquals(5, angka("SELECT COUNT(*) FROM tph")); // yang tidak ada di paket tetap ada
        assertEquals(2, angka("SELECT COUNT(*) FROM app_user"));
        assertEquals(2, angka("SELECT COUNT(*) FROM user_role"));
    }

    @Test
    public void bentrokDenganDataHpMembatalkanSemuanya() throws Exception {
        MasterLoader.muatBilaKosong(ctx, room);
        FakeServerApi api = new FakeServerApi();
        JSONObject j = paket("m-x");
        // Username krani1 sudah dipakai user_id 1 di HP; server mengirimnya dengan id lain.
        j.getJSONArray("user").getJSONObject(0).put("user_id", 99);
        api.masterJawab = new ServerApi.MasterPaket("m-x", true, j);
        String namaUnitSebelum = teks("SELECT nama FROM unit WHERE unit_id = 1");

        try {
            master.ambil(api, api.tokenSah);
            fail("seharusnya gagal karena bentrok");
        } catch (ApiException e) {
            assertEquals(422, e.kode);
            assertTrue(e.getMessage().startsWith("Master dari server tidak bisa diterapkan"));
        }
        // Transaksi dibatalkan: tidak ada yang berubah, versi tidak tercatat.
        assertEquals(namaUnitSebelum, teks("SELECT nama FROM unit WHERE unit_id = 1"));
        assertEquals("", master.versiLokal());
        assertNull(teks("SELECT versi_server FROM master_sync"));
    }

    @Test
    public void galatJaringanTidakMengubahData() throws Exception {
        FakeServerApi api = new FakeServerApi();
        api.galatMaster.add(new ApiException(0, "Tidak tersambung ke server"));
        try {
            master.ambil(api, api.tokenSah);
            fail("seharusnya gagal");
        } catch (ApiException e) {
            assertTrue(e.bisaDicobaLagi());
        }
        assertEquals(0, angka("SELECT COUNT(*) FROM blok"));
    }

    // ---- master per mandor (18 Nov) ----------------------------------------------------------------

    @Test
    public void profilMandorDariServerDisimpanDiAkun() throws Exception {
        JSONObject j = paket("m-1");
        j.getJSONArray("user").getJSONObject(1).put("mandor_hrmb_code", 7001).put("afdeling_kode", "DIV2");
        FakeServerApi api = new FakeServerApi();
        api.masterJawab = new ServerApi.MasterPaket("m-1", true, j);
        master.ambil(api, api.tokenSah);

        assertEquals(7001, angka("SELECT mandor_hrmb_code FROM app_user WHERE username = 'mandor1'"));
        assertEquals("DIV2", teks("SELECT afdeling_kode FROM app_user WHERE username = 'mandor1'"));
        assertNull(teks("SELECT afdeling_kode FROM app_user WHERE username = 'krani1'"));
        assertEquals(0, angka("SELECT COUNT(*) FROM app_user WHERE username = 'krani1' AND mandor_hrmb_code IS NOT NULL"));
    }

    @Test
    public void masterMandorMenyembunyikanYangBukanMilikknyaDanMenghidupkanLagiBilaKembali() throws Exception {
        FakeServerApi api = new FakeServerApi();
        api.masterJawab = paketServer("m-1"); // krani: 1 pemanen, 2 TPH
        master.ambil(api, api.tokenSah);
        assertEquals(1, room.masterDao().daftarPemanen().size());
        assertEquals(2, room.masterDao().daftarTph().size());

        // Mandor menerima potret lain: pemanen 2 saja, TPH 1 saja; pemanen 1 dan TPH 2 tidak dikirim.
        JSONObject j = paket("m-mandor");
        j.put("pemanen", new org.json.JSONArray().put(new JSONObject("{\"pemanen_id\":2,\"unit_id\":1,\"erp_hrmb_code\":90002,"
                + "\"kode\":\"P-002\",\"nama\":\"Pemanen Dua\",\"aktif\":true,\"dibuat_pada\":\"2026-11-10T07:00:00Z\","
                + "\"diubah_pada\":null,\"dihapus_pada\":null}")));
        j.put("tph", new org.json.JSONArray().put(j.getJSONArray("tph").getJSONObject(0)));
        api.masterJawab = new ServerApi.MasterPaket("m-mandor", true, j);
        master.ambil(api, api.tokenSah);

        List<Hasil.PemanenPilihan> pem = room.masterDao().daftarPemanen();
        assertEquals(1, pem.size());
        assertEquals(2, pem.get(0).pemanenId);
        assertEquals(1, room.masterDao().daftarTph().size());
        assertEquals("baris tetap ada (hapus lunak), bukan dibuang", 2, angka("SELECT COUNT(*) FROM pemanen"));
        assertEquals(2, angka("SELECT COUNT(*) FROM tph"));

        // Mandor tanpa satu pun pemanen: kunci "pemanen" tidak dikirim server (daftar kosong), semuanya tersembunyi.
        JSONObject kosong = paket("m-kosong");
        kosong.remove("pemanen");
        api.masterJawab = new ServerApi.MasterPaket("m-kosong", true, kosong);
        master.ambil(api, api.tokenSah);
        assertEquals(0, room.masterDao().daftarPemanen().size());

        // Akun yang menerima seluruh unit (krani) mengembalikan semuanya.
        api.masterJawab = paketServer("m-penuh");
        master.ambil(api, api.tokenSah);
        assertEquals(1, room.masterDao().daftarPemanen().size());
        assertEquals(90001, angka("SELECT erp_hrmb_code FROM pemanen WHERE pemanen_id = 1"));
        assertEquals(2, room.masterDao().daftarTph().size());
    }
}
