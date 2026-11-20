package com.example.fai26.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;

import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class AppDatabaseTest {

    private AppDatabase room;
    private SupportSQLiteDatabase db;

    @Before
    public void buka() {
        Context ctx = ApplicationProvider.getApplicationContext();
        room = Room.inMemoryDatabaseBuilder(ctx, AppDatabase.class)
                .addCallback(AppDatabase.callback())
                .build();
        // Membuka database memicu validasi skema Room terhadap 22 entitas.
        db = room.getOpenHelper().getWritableDatabase();
    }

    @After
    public void tutup() {
        room.close();
    }

    private long angka(String sql) {
        try (Cursor c = db.query(sql)) {
            c.moveToFirst();
            return c.getLong(0);
        }
    }

    private void unitContoh() {
        db.execSQL("INSERT INTO unit (unit_id, kode, nama, erp_db, tahun_cluster, aktif, dibuat_pada) "
                + "VALUES (1, 'KP_STL', 'Contoh', 'kp_stl2026', 2026, 1, '2026-11-10T00:00:00Z')");
    }

    private void blokContoh(int id, String kode, String dihapusPada) {
        db.execSQL("INSERT INTO blok (blok_id, unit_id, erp_oid, kode, aktif, dibuat_pada, dihapus_pada) "
                + "VALUES (?, 1, ?, ?, 1, '2026-11-10T00:00:00Z', ?)",
                new Object[]{id, id, kode, dihapusPada});
    }

    @Test
    public void duaPuluhDuaTabelTerbentuk() {
        List<String> nama = new ArrayList<>();
        try (Cursor c = db.query("SELECT name FROM sqlite_master WHERE type='table' "
                + "AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%' "
                + "AND name NOT LIKE 'room_%'")) {
            while (c.moveToNext()) nama.add(c.getString(0));
        }
        assertEquals(nama.toString(), 22, nama.size());
    }

    @Test
    public void kelasGradingMengikutiUrutanKeluaranModel() {
        String[] kodeUrutanModel = {"abnormal", "empty", "overripe", "ripe", "underripe", "unripe"};
        for (int label = 0; label < 6; label++) {
            try (Cursor c = db.query("SELECT kode FROM kelas_grading WHERE label_model = " + label)) {
                assertTrue(c.moveToFirst());
                assertEquals(kodeUrutanModel[label], c.getString(0));
            }
        }
    }

    @Test
    public void dataAwalRoleDanPermissionSesuaiErd() {
        assertEquals(5, angka("SELECT COUNT(*) FROM role"));
        assertEquals(17, angka("SELECT COUNT(*) FROM permission"));
        assertEquals(1, angka("SELECT COUNT(*) FROM model_versi WHERE aktif = 1"));
        // KRANI memegang panen.buat tetapi tidak panen.setujui; MANDOR memegang keduanya.
        String sql = "SELECT COUNT(*) FROM role_permission rp "
                + "JOIN role r ON r.role_id = rp.role_id "
                + "JOIN permission p ON p.permission_id = rp.permission_id "
                + "WHERE r.kode = '%s' AND p.kode = '%s'";
        assertEquals(1, angka(String.format(sql, "KRANI", "panen.buat")));
        assertEquals(0, angka(String.format(sql, "KRANI", "panen.setujui")));
        assertEquals(1, angka(String.format(sql, "MANDOR", "panen.setujui")));
        assertEquals(0, angka(String.format(sql, "AUDITOR", "panen.buat")));
    }

    @Test
    public void foreignKeyDiberlakukan() {
        try {
            blokContoh(1, "A12", null); // unit 1 belum ada
            fail("Seharusnya ditolak foreign key");
        } catch (SQLiteConstraintException e) {
            // benar
        }
    }

    @Test
    public void indeksUnikParsialMengizinkanKodeSamaSetelahDihapusLunak() {
        unitContoh();
        blokContoh(1, "A12", null);
        try {
            blokContoh(2, "A12", null);
            fail("Kode ganda pada baris aktif seharusnya ditolak");
        } catch (SQLiteConstraintException e) {
            // benar
        }
        db.execSQL("UPDATE blok SET dihapus_pada = '2026-11-10T01:00:00Z' WHERE blok_id = 1");
        blokContoh(3, "A12", null); // boleh: baris lama sudah dihapus lunak
        assertEquals(2, angka("SELECT COUNT(*) FROM blok WHERE kode = 'A12'"));
    }

    @Test
    public void hapusHeaderPanenMenghapusAnakNya() {
        unitContoh();
        blokContoh(1, "A12", null);
        db.execSQL("INSERT INTO tph (tph_id, blok_id, no_tph, qr_kode, aktif, dibuat_pada) "
                + "VALUES (1, 1, '07', 'FAI26|TPH|1|KP_STL|A12|07', 1, '2026-11-10T00:00:00Z')");
        db.execSQL("INSERT INTO app_user (user_id, unit_id, username, nama, aktif, gagal_login, dibuat_pada) "
                + "VALUES (1, 1, 'krani1', 'Krani Satu', 1, 0, '2026-11-10T00:00:00Z')");
        db.execSQL("INSERT INTO device (device_id, aktif, dibuat_pada) "
                + "VALUES ('dev-1', 1, '2026-11-10T00:00:00Z')");
        db.execSQL("INSERT INTO panen_header (panen_id, unit_id, blok_id, tph_id, user_id, device_id, "
                + "tanggal_panen, brondolan_kg, gps_mock, status_lokasi, status, dibuat_pada) "
                + "VALUES ('p1', 1, 1, 1, 1, 'dev-1', '2026-11-10', 0, 0, 'TANPA_GPS', 'DRAFT', "
                + "'2026-11-10T00:00:00Z')");
        db.execSQL("INSERT INTO panen_grading (panen_id, kelas_id, jml_ai, jml_final, diubah_manual, dibuat_pada) "
                + "VALUES ('p1', 3, 4, 5, 1, '2026-11-10T00:00:00Z')");
        assertEquals(1, angka("SELECT COUNT(*) FROM panen_grading"));

        try { // satu baris per kelas per catatan
            db.execSQL("INSERT INTO panen_grading (panen_id, kelas_id, jml_ai, jml_final, diubah_manual, "
                    + "dibuat_pada) VALUES ('p1', 3, 1, 1, 0, '2026-11-10T00:00:00Z')");
            fail("Pasangan panen dan kelas ganda seharusnya ditolak");
        } catch (SQLiteConstraintException e) {
            // benar
        }

        db.execSQL("DELETE FROM panen_header WHERE panen_id = 'p1'");
        assertEquals(0, angka("SELECT COUNT(*) FROM panen_grading"));
    }

    @Test
    public void kolomPersetujuanMandorAda() {
        boolean oleh = false;
        boolean pada = false;
        try (Cursor c = db.query("PRAGMA table_info(panen_header)")) {
            while (c.moveToNext()) {
                String n = c.getString(c.getColumnIndexOrThrow("name"));
                oleh |= n.equals("disetujui_oleh");
                pada |= n.equals("disetujui_pada");
            }
        }
        assertTrue(oleh && pada);
        assertFalse(StatusPanen.valid("ACAK"));
        assertTrue(StatusPanen.valid(StatusPanen.DISETUJUI));
    }

    @Test
    public void kolomLoginOfflineBernamaBenar() {
        // Salah ketik di skrip ERD: 'offline_berlaku_sampaiTEXT' tanpa spasi.
        boolean ada = false;
        try (Cursor c = db.query("PRAGMA table_info(app_user)")) {
            while (c.moveToNext()) {
                ada |= c.getString(c.getColumnIndexOrThrow("name")).equals("offline_berlaku_sampai");
            }
        }
        assertTrue(ada);
    }
}
