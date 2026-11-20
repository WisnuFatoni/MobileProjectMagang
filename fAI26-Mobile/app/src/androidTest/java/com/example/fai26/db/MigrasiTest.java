package com.example.fai26.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;

import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

/** Migrasi skema HP dari versi 1 ke versi 2 (kolom verifikasi lokasi) tidak boleh menghilangkan data. */
public class MigrasiTest {

    private static final String BERKAS = "migrasi-uji";

    @Rule
    public MigrationTestHelper helper = new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(), AppDatabase.class,
            java.util.Collections.emptyList(), new FrameworkSQLiteOpenHelperFactory());

    @Test
    public void versi1KeVersi2MempertahankanCatatanLamaSebagaiTanpaGps() throws IOException {
        SupportSQLiteDatabase v1 = helper.createDatabase(BERKAS, 1);
        v1.execSQL("INSERT INTO device (device_id, aktif, dibuat_pada) VALUES ('dev-1', 1, '2026-11-10T00:00:00Z')");
        v1.close();

        SupportSQLiteDatabase v2 = helper.runMigrationsAndValidate(BERKAS, 2, true, AppDatabase.MIGRASI_1_2);
        try (Cursor c = v2.query("PRAGMA table_info(panen_header)")) {
            StringBuilder kolom = new StringBuilder();
            while (c.moveToNext()) kolom.append(c.getString(c.getColumnIndexOrThrow("name"))).append(',');
            for (String k : new String[]{"gps_akurasi_m", "gps_waktu", "gps_mock", "jarak_ke_tph_m", "status_lokasi"}) {
                assertTrue("kolom " + k, kolom.indexOf(k) >= 0);
            }
        }
        // Data yang sudah ada tetap ada.
        try (Cursor c = v2.query("SELECT COUNT(*) FROM device")) {
            c.moveToFirst();
            assertEquals(1, c.getInt(0));
        }
        v2.close();
    }

    @Test
    public void versi2KeVersi3MenambahBatasanUnikDanMenolakDuplikat() throws IOException {
        SupportSQLiteDatabase v2 = helper.createDatabase(BERKAS + "-3", 2);
        v2.execSQL("INSERT INTO unit (unit_id, kode, nama, erp_db, tahun_cluster, aktif, dibuat_pada) "
                + "VALUES (1, 'KP_STL', 'Unit', 'db', 2026, 1, '2026-11-10T00:00:00Z')");
        v2.close();

        SupportSQLiteDatabase v3 = helper.runMigrationsAndValidate(BERKAS + "-3", 3, true,
                AppDatabase.MIGRASI_1_2, AppDatabase.MIGRASI_2_3);
        try (Cursor c = v3.query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name IN "
                + "('ux_unit_kode','ux_tph_qr_kode','ux_kelas_grading_kode','ux_app_user_username','ux_role_kode','ux_permission_kode')")) {
            c.moveToFirst();
            assertEquals(6, c.getInt(0));
        }
        // Data lama tetap ada; kode unit yang sama kini ditolak.
        try (Cursor c = v3.query("SELECT COUNT(*) FROM unit")) {
            c.moveToFirst();
            assertEquals(1, c.getInt(0));
        }
        try {
            v3.execSQL("INSERT INTO unit (unit_id, kode, nama, erp_db, tahun_cluster, aktif, dibuat_pada) "
                    + "VALUES (2, 'KP_STL', 'Duplikat', 'db', 2026, 1, '2026-11-10T00:00:00Z')");
            org.junit.Assert.fail("kode unit ganda seharusnya ditolak");
        } catch (android.database.sqlite.SQLiteConstraintException e) {
            // benar
        }
        v3.close();
    }

    @Test
    public void versi3KeVersi4MenambahProfilMandorDanPenandaKirimUlangTanpaMenghapusData() throws IOException {
        SupportSQLiteDatabase v3 = helper.createDatabase(BERKAS + "-4", 3);
        v3.execSQL("INSERT INTO unit (unit_id, kode, nama, erp_db, tahun_cluster, aktif, dibuat_pada) "
                + "VALUES (1, 'KP_STL', 'Unit', 'db', 2026, 1, '2026-11-10T00:00:00Z')");
        v3.execSQL("INSERT INTO app_user (user_id, unit_id, username, nama, aktif, gagal_login, dibuat_pada) "
                + "VALUES (7, 1, 'mandor1', 'Mandor Satu', 1, 0, '2026-11-10T00:00:00Z')");
        v3.close();

        SupportSQLiteDatabase v4 = helper.runMigrationsAndValidate(BERKAS + "-4", 4, true,
                AppDatabase.MIGRASI_1_2, AppDatabase.MIGRASI_2_3, AppDatabase.MIGRASI_3_4);
        try (Cursor c = v4.query("SELECT username, mandor_hrmb_code, afdeling_kode FROM app_user WHERE user_id = 7")) {
            assertTrue(c.moveToFirst());
            assertEquals("mandor1", c.getString(0));
            assertTrue("akun lama belum punya profil mandor", c.isNull(1) && c.isNull(2));
        }
        v4.execSQL("UPDATE app_user SET mandor_hrmb_code = 7001, afdeling_kode = 'DIV2' WHERE user_id = 7");
        try (Cursor c = v4.query("PRAGMA table_info(panen_header)")) {
            boolean ada = false;
            while (c.moveToNext()) {
                if ("kirim_ulang".equals(c.getString(c.getColumnIndexOrThrow("name")))) {
                    ada = true;
                    assertEquals("NOT NULL", 1, c.getInt(c.getColumnIndexOrThrow("notnull")));
                    assertEquals("0", c.getString(c.getColumnIndexOrThrow("dflt_value")));
                }
            }
            assertTrue("kolom kirim_ulang", ada);
        }
        v4.close();
    }
}
