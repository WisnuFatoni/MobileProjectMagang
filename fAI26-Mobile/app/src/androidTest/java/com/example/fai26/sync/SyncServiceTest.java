package com.example.fai26.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;

import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.fai26.db.AppDatabase;
import com.example.fai26.db.MasterLoader;
import com.example.fai26.db.PanenRepository;
import com.example.fai26.db.Waktu;
import com.example.fai26.db.dao.Hasil;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class SyncServiceTest {

    /** 10 November 2026 03:00 UTC. */
    private static final long AWAL = 1_794_279_600_000L;

    private Context ctx;
    private AppDatabase room;
    private SupportSQLiteDatabase db;
    private PanenRepository krani;
    private PanenRepository mandor;
    private PengaturanSync pref;
    private FakeServerApi api;
    private SyncService sync;
    private File folderFoto;
    private long jam = AWAL;

    @Before
    public void siapkan() throws Exception {
        // Tes jadwal (SyncJadwalTest) memicu sinkron asli di latar yang memegang kunci statis; tunggu sampai selesai.
        for (int i = 0; i < 600 && SyncService.sedangBerjalan(); i++) Thread.sleep(100);
        ctx = ApplicationProvider.getApplicationContext();
        room = Room.inMemoryDatabaseBuilder(ctx, AppDatabase.class).addCallback(AppDatabase.callback()).build();
        db = room.getOpenHelper().getWritableDatabase();
        MasterLoader.muatBilaKosong(ctx, room);
        db.execSQL("INSERT INTO device (device_id, aktif, dibuat_pada) VALUES ('dev-uji', 1, ?)",
                new Object[]{Waktu.utcSekarang()});
        folderFoto = new File(ctx.getCacheDir(), "foto_sync_uji");
        krani = new PanenRepository(room, folderFoto, "dev-uji", 1L);
        mandor = new PanenRepository(room, folderFoto, "dev-uji", 2L);

        pref = new PengaturanSync(ctx, "fai26_sync_uji");
        pref.aturServerUrl("http://server.uji");
        pref.hapusToken();
        pref.aturStatusSejak(0);
        api = new FakeServerApi();
        api.sekarangMillis = AWAL;
        pref.simpanToken("krani1", new ServerApi.Token(api.tokenSah, AWAL + 15 * 60_000L, "REFRESH-0"));
        sync = new SyncService(room, api, pref, () -> jam);
    }

    @After
    public void bersihkan() {
        room.close();
        pref.hapusToken();
        File[] semua = folderFoto.listFiles();
        if (semua != null) for (File f : semua) f.delete();
        folderFoto.delete();
    }

    // ---- bantuan ---------------------------------------------------------------------------------

    private long angka(String sql) {
        try (Cursor c = db.query(sql)) {
            c.moveToFirst();
            return c.getLong(0);
        }
    }

    private String teks(String sql) {
        try (Cursor c = db.query(sql)) {
            return c.moveToFirst() ? c.getString(0) : null;
        }
    }

    private File fotoPalsu() throws Exception {
        Bitmap b = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888);
        new Canvas(b).drawColor(Color.GREEN);
        File f = new File(ctx.getCacheDir(), "foto_palsu_sync.jpg");
        try (FileOutputStream out = new FileOutputStream(f)) {
            b.compress(Bitmap.CompressFormat.JPEG, 90, out);
        }
        return f;
    }

    /** Pemanen berganti tiap catatan: satu TPH + pemanen + tanggal hanya boleh satu catatan (anti ganda). */
    private long nomorPemanen = 0;

    private PanenRepository.Permintaan permintaan(boolean denganFoto) throws Exception {
        List<Hasil.TphPilihan> tph = room.masterDao().daftarTph();
        PanenRepository.Permintaan r = new PanenRepository.Permintaan();
        r.unitId = tph.get(0).unitId;
        r.blokId = tph.get(0).blokId;
        r.tphId = tph.get(0).tphId;
        r.pemanenId = 1 + (nomorPemanen++ % 5);
        r.tanggalPanen = "2026-11-10";
        r.brondolanKg = 12.5;
        r.catatan = "uji sinkron";
        r.jumlahAi = new int[]{0, 0, 2, 7, 0, 1};
        r.jumlahFinal = new int[]{0, 0, 2, 9, 0, 1};
        if (denganFoto) {
            r.foto = fotoPalsu();
            r.deteksi = new float[]{3, 0.9f, 0.1f, 0.2f, 0.4f, 0.5f};
        }
        return r;
    }

    /** Krani menyimpan, mandor menyetujui. @return panen_id. */
    private String catatDanSetujui(boolean denganFoto) throws Exception {
        String id = krani.simpan(permintaan(denganFoto));
        assertEquals(1, mandor.setujui(Collections.singletonList(id)));
        return id;
    }

    // ---- antrean ---------------------------------------------------------------------------------

    @Test
    public void persetujuanMasukAntrean() throws Exception {
        String id = catatDanSetujui(false);
        assertEquals(1, angka("SELECT COUNT(*) FROM sync_queue WHERE panen_id = '" + id
                + "' AND entitas = 'PANEN' AND status = 'MENUNGGU'"));
    }

    @Test
    public void catatanDraftDikirimTanpaPenyetujuAgarKraniBisaMenyetujuiDariHpLain() throws Exception {
        // Perilaku sebelum 19 Nov: DRAFT tidak dikirim sampai disetujui di HP yang sama. Sekarang DRAFT langsung dikirim,
        // tanpa penyetuju, dan server menyimpannya MENUNGGU persetujuan krani.
        krani.simpan(permintaan(false));
        SyncService.Ringkasan h = sync.jalankan(true);
        assertEquals(1, h.panenTerkirim);
        assertEquals(1, api.panenDikirim.size());
        assertFalse(api.panenDikirim.get(0).has("disetujui_username"));
    }

    @Test
    public void disetujuiTanpaAntreanDimasukkanSaatSinkron() throws Exception {
        String id = catatDanSetujui(false);
        db.execSQL("DELETE FROM sync_queue"); // contoh: disetujui sebelum fitur sinkron ada
        SyncService.Ringkasan h = sync.jalankan(true);
        assertEquals(1, h.panenTerkirim);
        assertEquals("TERKIRIM", teks("SELECT status FROM panen_header WHERE panen_id = '" + id + "'"));
    }

    // ---- kirim -----------------------------------------------------------------------------------

    @Test
    public void kirimCatatanFotoDanAudit() throws Exception {
        String id = catatDanSetujui(true);
        long auditSebelum = angka("SELECT COUNT(*) FROM audit_log WHERE disinkron = 0");
        assertTrue(auditSebelum > 0);

        SyncService.Ringkasan h = sync.jalankan(true);

        assertNull(h.masalah);
        assertEquals(1, h.panenTerkirim);
        assertEquals(1, h.fotoTerkirim);
        assertEquals(auditSebelum, h.auditTerkirim);

        // Isi kiriman sesuai master dan catatan.
        JSONObject p = api.panenDikirim.get(0);
        assertEquals(id, p.getString("panen_id"));
        assertEquals("KP_STL", p.getString("unit_kode"));
        assertEquals("A12", p.getString("blok_kode"));
        assertEquals("mandor1", p.getString("disetujui_username"));
        assertEquals(12.5, p.getDouble("brondolan_kg"), 0);
        assertEquals("TANPA_GPS", p.getString("status_lokasi"));
        assertEquals(6, p.getJSONArray("grading").length());
        boolean adaRipe = false;
        for (int i = 0; i < 6; i++) {
            JSONObject g = p.getJSONArray("grading").getJSONObject(i);
            if (g.getString("kelas_kode").equals("ripe")) {
                adaRipe = true;
                assertEquals("JJG_RIPE", g.getString("erp_kolom"));
                assertEquals(7, g.getInt("jml_ai"));
                assertEquals(9, g.getInt("jml_final"));
                assertTrue(g.getBoolean("diubah_manual"));
            }
        }
        assertTrue(adaRipe);
        assertEquals(12, p.getJSONArray("pemanen").getJSONObject(0).getInt("jjg_alokasi"));

        // Status di HP.
        assertEquals("TERKIRIM", teks("SELECT status FROM panen_header WHERE panen_id = '" + id + "'"));
        assertEquals(0, angka("SELECT COUNT(*) FROM sync_queue WHERE status <> 'SELESAI'"));
        assertEquals("SUDAH", teks("SELECT status_unggah FROM foto"));
        assertEquals(0, angka("SELECT COUNT(*) FROM audit_log WHERE disinkron = 0"));
        assertEquals(1, api.fotoDikirim.size());
        assertTrue(pref.sinkronTerakhir() > 0);
    }

    @Test
    public void putaranKeduaTidakMengirimUlang() throws Exception {
        catatDanSetujui(true);
        sync.jalankan(true);
        int panen = api.panenDikirim.size(), foto = api.fotoDikirim.size(), audit = api.auditDikirim.size();
        SyncService.Ringkasan h = sync.jalankan(true);
        assertEquals(panen, api.panenDikirim.size());
        assertEquals(foto, api.fotoDikirim.size());
        assertEquals(audit, api.auditDikirim.size());
        assertFalse(h.adaYangDikirim());
    }

    @Test
    public void tanpaAlamatServerTidakMelakukanApaPun() throws Exception {
        catatDanSetujui(false);
        pref.aturServerUrl("");
        SyncService.Ringkasan h = sync.jalankan(true);
        assertEquals("Alamat server belum diatur", h.masalah);
        assertTrue(api.panenDikirim.isEmpty());
    }

    // ---- jaringan dan jeda -----------------------------------------------------------------------

    @Test
    public void jaringanPutusMenjadwalkanUlangDenganJeda() throws Exception {
        String id = catatDanSetujui(false);
        api.galatPanen.add(new ApiException(0, "Tidak tersambung ke server"));

        SyncService.Ringkasan h = sync.jalankan(false);
        assertEquals("Tidak tersambung ke server", h.masalah);
        assertEquals("DISETUJUI", teks("SELECT status FROM panen_header WHERE panen_id = '" + id + "'"));
        assertEquals("MENUNGGU", teks("SELECT status FROM sync_queue"));
        assertEquals(1, angka("SELECT percobaan FROM sync_queue"));
        assertEquals(0, h.auditTerkirim); // audit tidak dikirim saat ada masalah

        // Langsung dicoba lagi tanpa paksa: masih dalam jeda 30 detik, tidak dikirim.
        api.panenDikirim.clear();
        SyncService.Ringkasan h2 = sync.jalankan(false);
        assertEquals(0, h2.panenTerkirim);
        assertEquals(1, angka("SELECT percobaan FROM sync_queue"));

        // Setelah jeda lewat, berhasil.
        jam += 31_000L;
        SyncService.Ringkasan h3 = sync.jalankan(false);
        assertEquals(1, h3.panenTerkirim);
        assertEquals("TERKIRIM", teks("SELECT status FROM panen_header WHERE panen_id = '" + id + "'"));
    }

    @Test
    public void tombolKirimSekarangMengabaikanJeda() throws Exception {
        catatDanSetujui(false);
        api.galatPanen.add(new ApiException(503, "Server sibuk"));
        sync.jalankan(false);
        SyncService.Ringkasan h = sync.jalankan(true); // paksa: tanpa menunggu jeda
        assertEquals(1, h.panenTerkirim);
    }

    @Test
    public void gagalTerusSampaiBatasMenjadiGagal() throws Exception {
        catatDanSetujui(false);
        for (int i = 0; i < SyncService.MAKS_PERCOBAAN; i++) api.galatPanen.add(new ApiException(0, "putus"));
        for (int i = 0; i < SyncService.MAKS_PERCOBAAN; i++) sync.jalankan(true);
        assertEquals("GAGAL", teks("SELECT status FROM sync_queue"));
        assertEquals(SyncService.MAKS_PERCOBAAN, angka("SELECT percobaan FROM sync_queue"));
        // Catatan tetap DISETUJUI (bukan ditolak): hanya jaringan yang bermasalah.
        assertEquals("DISETUJUI", teks("SELECT status FROM panen_header"));
        assertEquals(1, SyncService.hitungAntrean(room)[1]);
    }

    // ---- ditolak server --------------------------------------------------------------------------

    @Test
    public void ditolakServerMenandaiCatatanDanBisaDikirimUlang() throws Exception {
        String id = catatDanSetujui(false);
        api.galatPanen.add(new ApiException(422, "Penyetuju tidak berhak menyetujui"));

        SyncService.Ringkasan h = sync.jalankan(true);
        assertEquals(1, h.ditolak);
        assertEquals("DITOLAK", teks("SELECT status FROM panen_header WHERE panen_id = '" + id + "'"));
        assertEquals("Penyetuju tidak berhak menyetujui", teks("SELECT pesan_server FROM panen_header"));
        assertEquals("GAGAL", teks("SELECT status FROM sync_queue"));
        assertEquals(1, room.syncDao().jumlahGagal());

        // Tidak dicoba lagi otomatis.
        assertEquals(0, sync.jalankan(true).panenTerkirim);

        // "Kirim ulang" (setelah penyebab diperbaiki): berhasil dan pesan lama hilang.
        assertEquals(1, SyncService.kirimUlangYangGagal(room));
        SyncService.Ringkasan h2 = sync.jalankan(true);
        assertEquals(1, h2.panenTerkirim);
        assertEquals("TERKIRIM", teks("SELECT status FROM panen_header WHERE panen_id = '" + id + "'"));
        assertNull(teks("SELECT pesan_server FROM panen_header"));
    }

    @Test
    public void penolakanSatuCatatanTidakMenghentikanYangLain() throws Exception {
        String a = catatDanSetujui(false);
        String b = catatDanSetujui(false);
        api.galatPanen.add(new ApiException(422, "isi tidak sah")); // catatan pertama ditolak
        SyncService.Ringkasan h = sync.jalankan(true);
        assertEquals(1, h.ditolak);
        assertEquals(1, h.panenTerkirim);
        assertEquals(2, angka("SELECT COUNT(*) FROM panen_header WHERE status IN ('DITOLAK','TERKIRIM')"));
        assertNotNull(a);
        assertNotNull(b);
    }

    @Test
    public void fotoHilangDitandaiGagalTanpaMembatalkanCatatan() throws Exception {
        String id = catatDanSetujui(true);
        String path = teks("SELECT path_lokal FROM foto");
        assertTrue(new File(path).delete());
        SyncService.Ringkasan h = sync.jalankan(true);
        assertEquals(1, h.panenTerkirim);
        assertEquals(0, h.fotoTerkirim);
        assertEquals("TERKIRIM", teks("SELECT status FROM panen_header WHERE panen_id = '" + id + "'"));
        assertEquals("GAGAL", teks("SELECT status FROM sync_queue WHERE entitas = 'FOTO'"));
    }

    // ---- token -----------------------------------------------------------------------------------

    @Test
    public void tokenKedaluwarsaDiperbaruiDenganRefresh() throws Exception {
        catatDanSetujui(false);
        pref.simpanToken("krani1", new ServerApi.Token("AKSES-1", AWAL - 1, "REFRESH-0")); // sudah lewat
        SyncService.Ringkasan h = sync.jalankan(true);
        assertEquals(1, h.panenTerkirim);
        assertEquals(1, api.jumlahRefresh);
        assertEquals("AKSES-2", pref.akses());
        assertEquals("REFRESH-2", pref.refresh());
    }

    @Test
    public void serverMenolakTokenSekaliLaluDiperbaruiDanDiulang() throws Exception {
        catatDanSetujui(false);
        api.tokenSah = "AKSES-LAIN"; // token di HP dianggap tidak sah oleh server
        SyncService.Ringkasan h = sync.jalankan(true);
        assertEquals(1, h.panenTerkirim);
        assertEquals(1, api.jumlahRefresh);
    }

    @Test
    public void sesiHabisMembuatPerluLoginTanpaMengirimApaPun() throws Exception {
        catatDanSetujui(false);
        pref.simpanToken("krani1", new ServerApi.Token("AKSES-1", AWAL - 1, "REFRESH-0"));
        api.galatRefresh.add(new ApiException(401, "Sesi tidak berlaku. Login ulang"));
        SyncService.Ringkasan h = sync.jalankan(true);
        assertTrue(h.perluLogin);
        assertTrue(api.panenDikirim.isEmpty());
        assertFalse(pref.adaToken()); // token yang sudah mati dihapus
        assertEquals("Sesi server tidak berlaku. Login online ulang", h.masalah);
    }

    @Test
    public void belumPernahLoginOnline() throws Exception {
        catatDanSetujui(false);
        pref.hapusToken();
        SyncService.Ringkasan h = sync.jalankan(true);
        assertTrue(h.perluLogin);
        assertEquals("Belum login online. Login saat ada sinyal agar data bisa dikirim", h.masalah);
        // Catatan tetap aman di antrean.
        assertEquals("MENUNGGU", teks("SELECT status FROM sync_queue"));
    }

    // ---- status dari server ----------------------------------------------------------------------

    @Test
    public void statusDiterimaDanDitolakDariServer() throws Exception {
        String a = catatDanSetujui(false);
        String b = catatDanSetujui(false);
        sync.jalankan(true);
        assertEquals(2, angka("SELECT COUNT(*) FROM panen_header WHERE status = 'TERKIRIM'"));

        long t = AWAL + 60_000L;
        api.status.add(new ServerApi.StatusItem(a, "DITERIMA", null, t));
        api.status.add(new ServerApi.StatusItem(b, "DITOLAK", "Blok tidak ditemukan di ERP", t + 1000));
        jam += 120_000L;
        SyncService.Ringkasan h = sync.jalankan(true);

        assertEquals(2, h.statusDiperbarui);
        assertEquals("DITERIMA", teks("SELECT status FROM panen_header WHERE panen_id = '" + a + "'"));
        assertEquals("DITOLAK", teks("SELECT status FROM panen_header WHERE panen_id = '" + b + "'"));
        assertEquals("Blok tidak ditemukan di ERP", teks("SELECT pesan_server FROM panen_header WHERE panen_id = '" + b + "'"));
        assertEquals(t + 1000, pref.statusSejak());

        // Putaran berikutnya hanya menanyakan yang lebih baru: tidak ada pembaruan lagi.
        assertEquals(0, sync.jalankan(true).statusDiperbarui);
    }

    @Test
    public void statusServerTidakMengubahCatatanYangBelumDikirim() throws Exception {
        String id = catatDanSetujui(false);
        api.galatPanen.add(new ApiException(0, "putus")); // belum sampai ke server
        sync.jalankan(true);
        api.status.add(new ServerApi.StatusItem(id, "DITERIMA", null, AWAL + 1000));
        sync.jalankan(false); // tanpa paksa: masih dalam jeda
        assertEquals("DISETUJUI", teks("SELECT status FROM panen_header WHERE panen_id = '" + id + "'"));
    }

    @Test
    public void galatAuditTidakMenghilangkanAuditDanDicobaLagi() throws Exception {
        catatDanSetujui(false);
        api.galatAudit.add(new ApiException(503, "sibuk"));
        SyncService.Ringkasan h = sync.jalankan(true);
        assertEquals(1, h.panenTerkirim);
        assertNotNull(h.masalah);
        assertTrue(angka("SELECT COUNT(*) FROM audit_log WHERE disinkron = 0") > 0);
        SyncService.Ringkasan h2 = sync.jalankan(true);
        assertTrue(h2.auditTerkirim > 0);
        assertEquals(0, angka("SELECT COUNT(*) FROM audit_log WHERE disinkron = 0"));
    }

    @Test
    public void auditDikirimDenganFormatYangDiterimaServer() throws Exception {
        catatDanSetujui(false);
        sync.jalankan(true);
        JSONArray a = api.auditDikirim.get(0);
        assertTrue(a.length() > 0);
        JSONObject o = a.getJSONObject(0);
        assertTrue(o.getString("audit_id").matches("[0-9a-f-]{36}"));
        assertTrue(o.getString("waktu").endsWith("Z"));
        assertTrue(o.has("tabel") && o.has("baris_id") && o.has("aksi"));
    }

    // ---- siapa yang boleh mengirim ---------------------------------------------------------------

    @Test
    public void catatanMilikOrangLainMenungguPemiliknyaLogin() throws Exception {
        // Catatan dibuat mandor (user 2); yang login online di HP ini krani1 (user 1, bukan pemilik, tanpa koreksi).
        // Mandor yang menginput langsung menyetujui sendiri saat menyimpan (masuk antrean tanpa langkah setujui).
        String id = mandor.simpan(permintaan(false));
        assertEquals(0, mandor.setujui(Collections.singletonList(id)));

        SyncService.Ringkasan h = sync.jalankan(true);

        assertEquals(0, h.panenTerkirim);
        assertEquals(1, h.menungguPemilik);
        assertTrue(api.panenDikirim.isEmpty());
        assertEquals("MENUNGGU", teks("SELECT status FROM sync_queue WHERE entitas = 'PANEN'"));
        assertEquals(0, angka("SELECT percobaan FROM sync_queue")); // bukan kegagalan

        // Setelah pemiliknya (mandor1) login online di HP ini, catatan terkirim atas namanya.
        pref.simpanToken("mandor1", new ServerApi.Token(api.tokenSah, AWAL + 15 * 60_000L, "REFRESH-0"));
        SyncService.Ringkasan h2 = sync.jalankan(true);
        assertEquals(1, h2.panenTerkirim);
        assertEquals("mandor1", api.panenDikirim.get(0).getString("pencatat_username"));
    }

    @Test
    public void mandorBolehMengirimCatatanKraniDenganPencatatAsli() throws Exception {
        catatDanSetujui(false); // dicatat krani1, disetujui mandor1
        pref.simpanToken("mandor1", new ServerApi.Token(api.tokenSah, AWAL + 15 * 60_000L, "REFRESH-0"));

        SyncService.Ringkasan h = sync.jalankan(true);

        assertEquals(1, h.panenTerkirim);
        assertEquals(0, h.menungguPemilik);
        JSONObject p = api.panenDikirim.get(0);
        assertEquals("krani1", p.getString("pencatat_username"));
        assertEquals("mandor1", p.getString("disetujui_username"));
    }

    @Test
    public void tanpaPenggunaDikenaliHanyaMenungguTanpaMengirim() throws Exception {
        catatDanSetujui(false);
        pref.simpanToken("orang_tak_dikenal", new ServerApi.Token(api.tokenSah, AWAL + 15 * 60_000L, "REFRESH-0"));
        SyncService.Ringkasan h = sync.jalankan(true);
        assertEquals(0, h.panenTerkirim);
        assertEquals(1, h.menungguPemilik);
        assertTrue(api.panenDikirim.isEmpty());
    }

    // ---- penanda coba-ulang untuk penjadwal latar ------------------------------------------------

    @Test
    public void jaringanPutusMenandaiBisaDicobaLagiTetapiPenolakanTidak() throws Exception {
        catatDanSetujui(false);
        api.galatPanen.add(new ApiException(0, "putus"));
        assertTrue(sync.jalankan(true).bisaDicobaLagi);

        api.galatPanen.add(new ApiException(422, "isi tidak sah"));
        SyncService.Ringkasan h = sync.jalankan(true);
        assertFalse(h.bisaDicobaLagi); // ditolak server: mencoba lagi tidak membantu
        assertEquals(1, h.ditolak);
    }

    @Test
    public void tidakAdaMasalahTidakMenandaiBisaDicobaLagi() throws Exception {
        catatDanSetujui(false);
        SyncService.Ringkasan h = sync.jalankan(true);
        assertFalse(h.bisaDicobaLagi);
        assertNull(h.masalah);
    }

    // ---- koordinat TPH ---------------------------------------------------------------------------

    private long tphUji() {
        return room.masterDao().daftarTph().get(0).tphId;
    }

    private double desimal(String sql) {
        try (Cursor c = db.query(sql)) {
            c.moveToFirst();
            return c.getDouble(0);
        }
    }

    @Test
    public void koordinatYangDirekamMasukAntreanDanTerkirim() throws Exception {
        long tph = tphUji();
        assertTrue(krani.rekamKoordinatTph(tph, -2.0, 113.0, 6.0));
        assertEquals("MENUNGGU", teks("SELECT status FROM sync_queue WHERE entitas = 'TPH_KOORDINAT'"));
        assertEquals(1, angka("SELECT COUNT(*) FROM sync_queue WHERE entitas = 'TPH_KOORDINAT' AND panen_id IS NULL"));

        SyncService.Ringkasan h = sync.jalankan(true);

        assertEquals(1, h.koordinatTerkirim);
        assertTrue(h.adaYangDikirim());
        assertTrue(h.teks().contains("1 koordinat TPH"));
        assertEquals(1, api.koordinatDikirim.size());
        double[] k = api.koordinatDikirim.get(0);
        assertEquals(tph, (long) k[0]);
        assertEquals(-2.0, k[1], 1e-9);
        assertEquals(113.0, k[2], 1e-9);
        assertEquals(6.0, k[3], 1e-9);
        assertEquals("SELESAI", teks("SELECT status FROM sync_queue WHERE entitas = 'TPH_KOORDINAT'"));
        assertEquals(-2.0, desimal("SELECT lat FROM tph WHERE tph_id = " + tph), 1e-9);

        // Putaran kedua tidak mengirim ulang.
        assertEquals(0, sync.jalankan(true).koordinatTerkirim);
        assertEquals(1, api.koordinatDikirim.size());
    }

    @Test
    public void rekamKoordinatKeduaKaliTidakMenambahAntrean() throws Exception {
        long tph = tphUji();
        assertTrue(krani.rekamKoordinatTph(tph, -2.0, 113.0, 6.0));
        assertFalse(krani.rekamKoordinatTph(tph, -3.0, 114.0, 6.0));
        assertEquals(1, angka("SELECT COUNT(*) FROM sync_queue WHERE entitas = 'TPH_KOORDINAT'"));
    }

    @Test
    public void serverSudahPunyaTitikMakaHpMengikutiServer() throws Exception {
        long tph = tphUji();
        api.koordinatServer.put(tph, new double[]{-5.0, 110.0});
        assertTrue(krani.rekamKoordinatTph(tph, -2.0, 113.0, 6.0));

        SyncService.Ringkasan h = sync.jalankan(true);

        assertEquals(1, h.koordinatTerkirim);
        assertEquals(-5.0, desimal("SELECT lat FROM tph WHERE tph_id = " + tph), 1e-9);
        assertEquals(110.0, desimal("SELECT lon FROM tph WHERE tph_id = " + tph), 1e-9);
        assertEquals("SELESAI", teks("SELECT status FROM sync_queue WHERE entitas = 'TPH_KOORDINAT'"));
    }

    @Test
    public void koordinatMilikOrangLainMenungguPemiliknyaLogin() throws Exception {
        long tph = tphUji();
        assertTrue(mandor.rekamKoordinatTph(tph, -2.0, 113.0, 6.0)); // direkam mandor1 (user 2)

        SyncService.Ringkasan h = sync.jalankan(true); // yang login online krani1 (user 1)

        assertEquals(0, h.koordinatTerkirim);
        assertEquals(1, h.menungguPemilik);
        assertTrue(api.koordinatDikirim.isEmpty());
        assertEquals("MENUNGGU", teks("SELECT status FROM sync_queue WHERE entitas = 'TPH_KOORDINAT'"));
        assertEquals(0, angka("SELECT percobaan FROM sync_queue WHERE entitas = 'TPH_KOORDINAT'"));

        pref.simpanToken("mandor1", new ServerApi.Token(api.tokenSah, AWAL + 15 * 60_000L, "REFRESH-0"));
        assertEquals(1, sync.jalankan(true).koordinatTerkirim);
    }

    @Test
    public void mandorBolehMengirimKoordinatYangDirekamKrani() throws Exception {
        long tph = tphUji();
        assertTrue(krani.rekamKoordinatTph(tph, -2.0, 113.0, 6.0));
        pref.simpanToken("mandor1", new ServerApi.Token(api.tokenSah, AWAL + 15 * 60_000L, "REFRESH-0"));

        SyncService.Ringkasan h = sync.jalankan(true);

        assertEquals(1, h.koordinatTerkirim);
        assertEquals(0, h.menungguPemilik);
    }

    @Test
    public void jaringanPutusKoordinatDicobaLagiDanTitikLokalTetap() throws Exception {
        long tph = tphUji();
        assertTrue(krani.rekamKoordinatTph(tph, -2.0, 113.0, 6.0));
        api.galatKoordinat.add(new ApiException(0, "putus"));

        SyncService.Ringkasan h = sync.jalankan(true);

        assertEquals(0, h.koordinatTerkirim);
        assertTrue(h.bisaDicobaLagi);
        assertEquals("MENUNGGU", teks("SELECT status FROM sync_queue WHERE entitas = 'TPH_KOORDINAT'"));
        assertEquals(1, angka("SELECT percobaan FROM sync_queue WHERE entitas = 'TPH_KOORDINAT'"));
        assertEquals(-2.0, desimal("SELECT lat FROM tph WHERE tph_id = " + tph), 1e-9);

        // Putaran berikutnya (jeda diabaikan) berhasil.
        assertEquals(1, sync.jalankan(true).koordinatTerkirim);
    }

    @Test
    public void koordinatDitolakServerDitandaiGagalDanTitikLokalTetap() throws Exception {
        long tph = tphUji();
        assertTrue(krani.rekamKoordinatTph(tph, -2.0, 113.0, 6.0));
        api.galatKoordinat.add(new ApiException(404, "TPH tidak ditemukan"));

        SyncService.Ringkasan h = sync.jalankan(true);

        assertEquals(1, h.ditolak);
        assertFalse(h.bisaDicobaLagi);
        assertEquals("GAGAL", teks("SELECT status FROM sync_queue WHERE entitas = 'TPH_KOORDINAT'"));
        assertEquals("TPH tidak ditemukan", teks("SELECT error_terakhir FROM sync_queue WHERE entitas = 'TPH_KOORDINAT'"));
        assertEquals(-2.0, desimal("SELECT lat FROM tph WHERE tph_id = " + tph), 1e-9);
    }

    // ---- persetujuan krani lewat server (19 Nov) ---------------------------------------------------

    @Test
    public void catatanTanpaHakSetujuDikirimSebagaiMenungguLaluBerubahSetelahKraniMenyetujui() throws Exception {
        String id = krani.simpan(permintaan(false)); // pengirim tanpa hak setuju
        assertEquals("DRAFT", teks("SELECT status FROM panen_header WHERE panen_id = '" + id + "'"));

        SyncService.Ringkasan h = sync.jalankan(true);
        assertEquals(1, h.panenTerkirim);
        org.json.JSONObject kiriman = api.panenDikirim.get(0);
        assertFalse("tanpa penyetuju", kiriman.has("disetujui_username"));
        assertEquals("MENUNGGU_SETUJU", teks("SELECT status FROM panen_header WHERE panen_id = '" + id + "'"));
        // Menunggu krani bukan lagi "belum terkirim": sudah ada di server.
        assertEquals(0, room.panenDao().jumlahBelumTerkirim(0));

        // Krani menyetujui dari HP-nya: server mengubah status; HP pengirim mengikuti saat sinkron berikutnya.
        api.status.add(new ServerApi.StatusItem(id, "TERKIRIM", null, jam + 1000));
        jam += 5000;
        sync.jalankan(true);
        assertEquals("TERKIRIM", teks("SELECT status FROM panen_header WHERE panen_id = '" + id + "'"));
    }

    @Test
    public void catatanDitolakKraniDiperbaikiDanDikirimUlangSebagaiMenunggu() throws Exception {
        String id = krani.simpan(permintaan(false));
        sync.jalankan(true);
        api.status.add(new ServerApi.StatusItem(id, "DITOLAK", "Ditolak krani: blok salah", jam + 1000));
        jam += 5000;
        sync.jalankan(true);
        assertEquals("DITOLAK", teks("SELECT status FROM panen_header WHERE panen_id = '" + id + "'"));
        assertEquals("Ditolak krani: blok salah", teks("SELECT pesan_server FROM panen_header WHERE panen_id = '" + id + "'"));

        PanenRepository.Permintaan r = permintaan(false);
        r.jumlahFinal = new int[]{0, 0, 0, 5, 0, 0};
        krani.perbaikiDitolak(id, r);
        jam += 5000;
        SyncService.Ringkasan h = sync.jalankan(true);
        assertEquals(1, h.panenTerkirim);
        org.json.JSONObject kiriman = api.panenDikirim.get(api.panenDikirim.size() - 1);
        assertTrue(kiriman.getBoolean("kirim_ulang"));
        assertFalse(kiriman.has("disetujui_username"));
        assertEquals("MENUNGGU_SETUJU", teks("SELECT status FROM panen_header WHERE panen_id = '" + id + "'"));
        assertEquals("pesan galat lama hilang", 0, angka("SELECT COUNT(*) FROM panen_header WHERE panen_id = '" + id + "' AND pesan_server IS NOT NULL"));
    }

    @Test
    public void catatanMenungguKraniBisaDiubahDanDikirimUlangSebagaiMenunggu() throws Exception {
        String id = krani.simpan(permintaan(false));
        sync.jalankan(true);
        assertEquals("MENUNGGU_SETUJU", teks("SELECT status FROM panen_header WHERE panen_id = '" + id + "'"));

        PanenRepository.Permintaan r = permintaan(false);
        r.jumlahFinal = new int[]{0, 0, 0, 7, 0, 0};
        krani.perbaikiDitolak(id, r); // belum disetujui krani: boleh diubah
        jam += 5000;
        SyncService.Ringkasan h = sync.jalankan(true);
        assertEquals(1, h.panenTerkirim);
        assertTrue(api.panenDikirim.get(api.panenDikirim.size() - 1).getBoolean("kirim_ulang"));
        assertEquals("MENUNGGU_SETUJU", teks("SELECT status FROM panen_header WHERE panen_id = '" + id + "'"));
    }

    @Test
    public void perubahanDitolakBilaKraniSudahMenyetujuiServerMenjawab423() throws Exception {
        String id = krani.simpan(permintaan(false));
        sync.jalankan(true);
        PanenRepository.Permintaan r = permintaan(false);
        r.jumlahFinal = new int[]{0, 0, 0, 7, 0, 0};
        krani.perbaikiDitolak(id, r);
        // Krani menyetujui tepat sebelum perubahan sampai: server mengunci catatan.
        api.galatPanen.add(new ApiException(423, "Catatan sudah disetujui krani, tidak bisa diubah lagi"));
        jam += 5000;
        SyncService.Ringkasan h = sync.jalankan(true);
        assertEquals(1, h.terkunci);
        assertEquals(0, h.ditolak);
        // Bukan DITOLAK: versi yang disetujui krani berlaku, penanda kirim ulang dibersihkan, antrean selesai (tidak diulang).
        assertEquals("TERKIRIM", teks("SELECT status FROM panen_header WHERE panen_id = '" + id + "'"));
        assertEquals(0, angka("SELECT COUNT(*) FROM panen_header WHERE panen_id = '" + id + "' AND kirim_ulang = 1"));
        assertEquals(0, angka("SELECT COUNT(*) FROM sync_queue WHERE panen_id = '" + id + "' AND status IN ('MENUNGGU','GAGAL')"));
        assertTrue(h.teks(), h.teks().contains("disetujui krani"));
    }
}
