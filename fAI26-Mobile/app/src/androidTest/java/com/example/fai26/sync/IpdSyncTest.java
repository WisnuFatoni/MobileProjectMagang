package com.example.fai26.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
import com.example.fai26.db.IpdRepository;
import com.example.fai26.db.MasterLoader;
import com.example.fai26.db.Waktu;
import com.example.fai26.db.dao.IpdHasil;
import com.example.fai26.ipd.FotoKompres;
import com.example.fai26.ipd.IpdDraf;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

/**
 * Modul IPD di HP: simpan draf, aturan edit sebelum disetujui, antrean sinkron, foto (dikompres, dihapus dari HP setelah terkirim),
 * status dari server, dan 423 saat asisten sudah menyetujui.
 */
@RunWith(AndroidJUnit4.class)
public class IpdSyncTest {

    private static final long AWAL = 1_794_279_600_000L; // 10 Nov 2026 03:00 UTC

    private Context ctx;
    private AppDatabase room;
    private SupportSQLiteDatabase db;
    private IpdRepository repo;
    private PengaturanSync pref;
    private FakeServerApi api;
    private SyncService sync;
    private long jam = AWAL;
    private File folder;

    @Before
    public void siapkan() throws Exception {
        for (int i = 0; i < 600 && SyncService.sedangBerjalan(); i++) Thread.sleep(100);
        ctx = ApplicationProvider.getApplicationContext();
        room = Room.inMemoryDatabaseBuilder(ctx, AppDatabase.class).addCallback(AppDatabase.callback()).build();
        db = room.getOpenHelper().getWritableDatabase();
        MasterLoader.muatBilaKosong(ctx, room);
        db.execSQL("INSERT INTO device (device_id, aktif, dibuat_pada) VALUES ('dev-uji', 1, ?)", new Object[]{Waktu.utcSekarang()});
        String now = Waktu.utcSekarang();
        db.execSQL("INSERT INTO kode_temuan (kode_id, kode, nama, kelompok, urut, aktif, dibuat_pada) VALUES (1, 'A', 'Potong buah mentah', 'BUAH', 1, 1, ?)", new Object[]{now});
        db.execSQL("INSERT INTO kode_temuan (kode_id, kode, nama, kelompok, urut, aktif, dibuat_pada) VALUES (8, 'B1', 'Brondolan tak dikutip', 'BRONDOLAN', 8, 1, ?)", new Object[]{now});
        folder = new File(ctx.getCacheDir(), "foto_ipd_uji");
        repo = new IpdRepository(room, 2L, "dev-uji"); // mandor1

        pref = new PengaturanSync(ctx, "fai26_sync_ipd_uji");
        pref.aturServerUrl("http://server.uji");
        pref.hapusToken();
        pref.aturStatusSejak(0);
        pref.aturStatusIpdSejak(0);
        api = new FakeServerApi();
        api.sekarangMillis = AWAL;
        pref.simpanToken("mandor1", new ServerApi.Token(api.tokenSah, AWAL + 15 * 60_000L, "REFRESH-0"));
        sync = new SyncService(room, api, pref, () -> jam);
    }

    @After
    public void bersihkan() {
        room.close();
        pref.hapusToken();
        File[] semua = folder.listFiles();
        if (semua != null) for (File f : semua) f.delete();
        folder.delete();
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

    private IpdDraf draf() {
        IpdHasil.DivisiPilihan d = room.ipdDao().divisiPilihan().get(0);
        IpdDraf x = new IpdDraf();
        x.tanggal = "2026-11-10";
        x.divisiId = d.divisiId;
        x.blokId = room.ipdDao().blokPilihan(d.divisiId).get(0).blokId;
        x.pemanenId = room.ipdDao().pemanenPilihan().get(0).pemanenId;
        x.mandorUserId = 2L;
        IpdDraf.Pokok p1 = new IpdDraf.Pokok(11, 1);
        IpdDraf.Pokok p2 = new IpdDraf.Pokok(11, 2);
        p2.temuan.put("B1", 6);
        p2.temuan.put("A", 1);
        x.pokok.add(p1);
        x.pokok.add(p2);
        return x;
    }

    private File fotoKecil(String nama) throws Exception {
        Bitmap b = Bitmap.createBitmap(3000, 2000, Bitmap.Config.ARGB_8888);
        new Canvas(b).drawColor(Color.GREEN);
        File f = new File(ctx.getCacheDir(), nama);
        try (FileOutputStream out = new FileOutputStream(f)) {
            b.compress(Bitmap.CompressFormat.JPEG, 95, out);
        }
        return f;
    }

    @Test
    public void simpanDrafLaluMuatKembaliSamaIsinya() {
        IpdDraf d = draf();
        String id = repo.simpanDraf(d);
        assertEquals("DRAFT", teks("SELECT status FROM ipd_header WHERE ipd_id = '" + id + "'"));
        assertEquals(2, angka("SELECT COUNT(*) FROM ipd_pokok WHERE ipd_id = '" + id + "'"));
        assertEquals(2, angka("SELECT COUNT(*) FROM ipd_temuan"));
        IpdDraf m = repo.muat(id);
        assertEquals(2, m.pokok.size());
        assertEquals(6, (int) m.pokok.get(1).temuan.get("B1"));
        assertTrue(m.pokok.get(0).temuan.isEmpty());
        // Disimpan lagi dengan satu pokok: yang lama diganti, tidak bertambah.
        m.pokok.remove(0);
        repo.simpanDraf(m);
        assertEquals(1, angka("SELECT COUNT(*) FROM ipd_pokok WHERE ipd_id = '" + id + "'"));
    }

    @Test
    public void kirimMasukAntreanLaluTerkirimMenungguAsisten() throws Exception {
        String id = repo.kirim(draf());
        assertEquals(1, angka("SELECT COUNT(*) FROM sync_queue WHERE entitas = 'IPD' AND status = 'MENUNGGU'"));
        SyncService.Ringkasan h = sync.jalankan(true);
        assertEquals(1, h.ipdTerkirim);
        assertEquals("MENUNGGU_SETUJU", teks("SELECT status FROM ipd_header WHERE ipd_id = '" + id + "'"));
        assertEquals(1, api.ipdDikirim.size());
        assertFalse(api.ipdDikirim.get(0).has("kirim_ulang"));
        assertEquals(2, api.ipdDikirim.get(0).getJSONArray("pokok").length());
    }

    @Test
    public void fotoDikompresDikirimDenganKeteranganLaluBerkasDihapusDariHp() throws Exception {
        IpdDraf d = draf();
        String id = repo.simpanDraf(d);
        File mentah = fotoKecil("ipd_mentah.jpg");
        File hasil = new File(folder, "f1.jpg");
        FotoKompres.Hasil h = FotoKompres.kompres(mentah, hasil);
        assertTrue("sisi terpanjang dibatasi", Math.max(h.lebar, h.tinggi) <= FotoKompres.SISI_MAKS);
        assertTrue("lebih kecil dari aslinya", hasil.length() < mentah.length());
        repo.tambahFoto(id, hasil, h, "buah tinggal di baris 11");
        repo.kirim(d);

        SyncService.Ringkasan r = sync.jalankan(true);
        assertEquals(1, r.ipdTerkirim);
        assertEquals(1, r.fotoIpdTerkirim);
        assertTrue(api.fotoIpdDikirim.get(0).endsWith("|buah tinggal di baris 11|0"));
        assertFalse("berkas di HP dihapus setelah terkirim", hasil.exists());
        assertNull(teks("SELECT path_lokal FROM ipd_foto WHERE ipd_id = '" + id + "'"));
        assertEquals("SUDAH", teks("SELECT status_unggah FROM ipd_foto WHERE ipd_id = '" + id + "'"));
    }

    @Test
    public void ubahSebelumDisetujuiDikirimUlangSetelahDisetujuiTerkunci() throws Exception {
        IpdDraf d = draf();
        String id = repo.kirim(d);
        sync.jalankan(true); // MENUNGGU_SETUJU

        IpdDraf m = repo.muat(id);
        m.pokok.add(new IpdDraf.Pokok(10, 1));
        repo.kirim(m); // diubah selagi menunggu asisten
        assertEquals(1, angka("SELECT kirim_ulang FROM ipd_header WHERE ipd_id = '" + id + "'"));
        jam += 5000;
        sync.jalankan(true);
        assertTrue(api.ipdDikirim.get(1).getBoolean("kirim_ulang"));
        assertEquals("MENUNGGU_SETUJU", teks("SELECT status FROM ipd_header WHERE ipd_id = '" + id + "'"));
        assertEquals(0, angka("SELECT kirim_ulang FROM ipd_header WHERE ipd_id = '" + id + "'"));

        // Asisten menyetujui: status dari server mengikuti, lalu inspeksi terkunci di HP.
        api.statusIpd.add(new ServerApi.StatusItem(id, "DISETUJUI", null, AWAL + 60_000L));
        jam += 60_000L;
        sync.jalankan(true);
        assertEquals("DISETUJUI", teks("SELECT status FROM ipd_header WHERE ipd_id = '" + id + "'"));
        try {
            repo.simpanDraf(repo.muat(id));
            fail("inspeksi yang disetujui tidak boleh diubah");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("disetujui"));
        }
    }

    @Test
    public void server423MenandaiDisetujuiBukanDitolak() throws Exception {
        IpdDraf d = draf();
        String id = repo.kirim(d);
        sync.jalankan(true);
        IpdDraf m = repo.muat(id);
        m.pokok.add(new IpdDraf.Pokok(10, 1));
        repo.kirim(m);
        // Asisten menyetujui tepat sebelum perubahan sampai: server menjawab 423.
        api.galatIpd.add(new ApiException(423, "Inspeksi sudah disetujui asisten, tidak bisa diubah lagi"));
        jam += 5000;
        SyncService.Ringkasan h = sync.jalankan(true);
        assertEquals(1, h.terkunci);
        assertEquals(0, h.ditolak);
        assertEquals("DISETUJUI", teks("SELECT status FROM ipd_header WHERE ipd_id = '" + id + "'"));
        assertEquals(0, angka("SELECT kirim_ulang FROM ipd_header WHERE ipd_id = '" + id + "'"));
        assertEquals(0, angka("SELECT COUNT(*) FROM sync_queue WHERE entitas = 'IPD' AND status IN ('MENUNGGU', 'GAGAL')"));
    }

    @Test
    public void ditolakAsistenBisaDiperbaikiDanDikirimUlang() throws Exception {
        IpdDraf d = draf();
        String id = repo.kirim(d);
        sync.jalankan(true);
        api.statusIpd.add(new ServerApi.StatusItem(id, "DITOLAK", "Baris 10 belum lengkap", AWAL + 60_000L));
        jam += 60_000L;
        sync.jalankan(true);
        assertEquals("DITOLAK", teks("SELECT status FROM ipd_header WHERE ipd_id = '" + id + "'"));
        assertEquals("Baris 10 belum lengkap", teks("SELECT pesan_server FROM ipd_header WHERE ipd_id = '" + id + "'"));

        IpdDraf m = repo.muat(id);
        m.pokok.add(new IpdDraf.Pokok(10, 1));
        repo.kirim(m);
        jam += 5000;
        assertEquals(1, sync.jalankan(true).ipdTerkirim);
        assertEquals("MENUNGGU_SETUJU", teks("SELECT status FROM ipd_header WHERE ipd_id = '" + id + "'"));
        assertNull("pesan penolakan lama hilang", teks("SELECT pesan_server FROM ipd_header WHERE ipd_id = '" + id + "'"));
    }

    @Test
    public void kirimTanpaKepalaLengkapDitolakDanDrafBelumTerkirimBisaDihapus() {
        IpdDraf d = draf();
        d.mandorUserId = null;
        try {
            repo.kirim(d);
            fail("kemandoran wajib");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("mandor"));
        }
        d.mandorUserId = 2L;
        String id = repo.simpanDraf(d);
        assertTrue(repo.hapusDraf(id));
        assertEquals(0, angka("SELECT COUNT(*) FROM ipd_header"));
        // Yang sudah terkirim tidak bisa dihapus.
        String id2 = repo.kirim(draf());
        db.execSQL("UPDATE ipd_header SET status = 'MENUNGGU_SETUJU' WHERE ipd_id = ?", new Object[]{id2});
        assertFalse(repo.hapusDraf(id2));
    }
}
