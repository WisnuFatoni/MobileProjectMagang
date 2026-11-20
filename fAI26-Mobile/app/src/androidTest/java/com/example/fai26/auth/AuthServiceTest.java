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
import com.example.fai26.db.entity.AppUser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class AuthServiceTest {

    private static final long MENIT = 60_000L;
    private static final long HARI = 24 * 60 * MENIT;
    /** 10 November 2026 03:00 UTC. */
    private static final long AWAL = 1_794_279_600_000L;

    private AppDatabase room;
    private SupportSQLiteDatabase db;
    private AuthService auth;
    private long jam = AWAL;

    @Before
    public void siapkan() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        room = Room.inMemoryDatabaseBuilder(ctx, AppDatabase.class)
                .addCallback(AppDatabase.callback())
                .build();
        db = room.getOpenHelper().getWritableDatabase();
        MasterLoader.muatBilaKosong(ctx, room);
        db.execSQL("INSERT INTO device (device_id, aktif, dibuat_pada) VALUES ('dev-uji', 1, '2026-11-10T00:00:00Z')");
        auth = new AuthService(room, "dev-uji", () -> jam);
        // krani1 (user 1) dan mandor1 (user 2) berlaku 7 hari dari AWAL.
        auth.aturPassword(1, "krani123", AWAL + 7 * HARI);
        auth.aturPassword(2, "mandor123", AWAL + 7 * HARI);
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

    @Test
    public void passwordBenarBerhasilDanTercatat() {
        AuthService.Hasil h = auth.loginOffline("krani1", "krani123");
        assertEquals(AuthService.Status.BERHASIL, h.status);
        assertEquals(1, h.userId);
        assertEquals("Krani Contoh", h.nama);
        assertEquals(1, angka("SELECT COUNT(*) FROM audit_log WHERE aksi = 'LOGIN' AND user_id = 1 "
                + "AND keterangan = 'OFFLINE' AND device_id = 'dev-uji'"));
        AppUser u = room.akunDao().cariId(1);
        assertNotNull(u.loginTerakhir);
        assertEquals(0, u.gagalLogin);
    }

    @Test
    public void passwordTidakDisimpanPolos() {
        AppUser u = room.akunDao().cariId(1);
        assertNotNull(u.offlineSalt);
        assertTrue(u.offlineHash.startsWith(PasswordHasher.ITERASI + "$"));
        assertFalse(u.offlineHash.contains("krani123"));
        assertEquals(0, angka("SELECT COUNT(*) FROM app_user WHERE offline_hash LIKE '%krani123%'"));
    }

    @Test
    public void passwordSalahMengurangiSisaPercobaan() {
        AuthService.Hasil h = auth.loginOffline("krani1", "salah");
        assertEquals(AuthService.Status.SALAH, h.status);
        assertEquals(4, h.sisaPercobaan);
        assertEquals(1, room.akunDao().cariId(1).gagalLogin);
        assertEquals(1, angka("SELECT COUNT(*) FROM audit_log WHERE aksi = 'LOGIN_GAGAL' AND user_id = 1"));
    }

    @Test
    public void limaKaliSalahMengunciAkunLimaBelasMenit() {
        for (int i = 0; i < 4; i++) {
            assertEquals(AuthService.Status.SALAH, auth.loginOffline("krani1", "x" + i).status);
        }
        AuthService.Hasil kunci = auth.loginOffline("krani1", "x4");
        assertEquals(AuthService.Status.TERKUNCI, kunci.status);
        assertEquals(15, kunci.menitKunci);

        // Saat terkunci, password benar pun ditolak.
        jam += 14 * MENIT;
        AuthService.Hasil masihKunci = auth.loginOffline("krani1", "krani123");
        assertEquals(AuthService.Status.TERKUNCI, masihKunci.status);
        assertEquals(1, masihKunci.menitKunci);

        // Setelah 15 menit boleh lagi.
        jam += 1 * MENIT + 1;
        assertEquals(AuthService.Status.BERHASIL, auth.loginOffline("krani1", "krani123").status);
        assertNull(room.akunDao().cariId(1).terkunciSampai);
    }

    @Test
    public void kunciHanyaBerlakuUntukAkunYangSalahDicoba() {
        for (int i = 0; i < 5; i++) auth.loginOffline("krani1", "x");
        assertEquals(AuthService.Status.BERHASIL, auth.loginOffline("mandor1", "mandor123").status);
    }

    @Test
    public void berhasilMengembalikanHitunganGagal() {
        auth.loginOffline("krani1", "x");
        auth.loginOffline("krani1", "x");
        assertEquals(AuthService.Status.BERHASIL, auth.loginOffline("krani1", "krani123").status);
        assertEquals(0, room.akunDao().cariId(1).gagalLogin);
        // Hitungan mulai dari awal lagi: 5 kali salah baru mengunci.
        for (int i = 0; i < 4; i++) auth.loginOffline("krani1", "x");
        assertEquals(AuthService.Status.BERHASIL, auth.loginOffline("krani1", "krani123").status);
    }

    @Test
    public void masaLoginOfflineHabisMemintaOnline() {
        jam = AWAL + 7 * HARI + MENIT; // lewat 7 hari
        AuthService.Hasil h = auth.loginOffline("krani1", "krani123");
        assertEquals(AuthService.Status.PERLU_ONLINE, h.status);
        assertFalse(auth.masihBerlaku(1));
    }

    @Test
    public void akunTidakAktifDitolak() {
        db.execSQL("UPDATE app_user SET aktif = 0 WHERE user_id = 1");
        assertEquals(AuthService.Status.TIDAK_AKTIF, auth.loginOffline("krani1", "krani123").status);
        assertFalse(auth.masihBerlaku(1));
    }

    @Test
    public void usernameTakDikenalDicatatTanpaMembocorkan() {
        AuthService.Hasil h = auth.loginOffline("hantu", "apa-saja");
        assertEquals(AuthService.Status.SALAH, h.status);
        assertEquals(1, angka("SELECT COUNT(*) FROM audit_log WHERE aksi = 'LOGIN_GAGAL' "
                + "AND user_id IS NULL AND username = 'hantu'"));
        assertEquals(AuthService.Status.SALAH, auth.loginOffline("", "").status);
    }

    @Test
    public void akunBelumPernahLoginOnlineMemintaOnline() {
        db.execSQL("UPDATE app_user SET offline_hash = NULL, offline_salt = NULL, "
                + "offline_berlaku_sampai = NULL WHERE user_id = 1");
        assertEquals(AuthService.Status.PERLU_ONLINE, auth.loginOffline("krani1", "krani123").status);
    }

    @Test
    public void permissionKraniDanMandorBerbeda() {
        List<String> krani = auth.permission(1);
        List<String> mandor = auth.permission(2);
        assertTrue(krani.contains("panen.buat"));
        assertFalse(krani.contains("panen.setujui"));
        assertFalse(krani.contains("user.kelola"));
        assertTrue(mandor.contains("panen.buat"));
        assertTrue(mandor.contains("panen.setujui"));
        assertTrue(mandor.contains("laporan.lihat"));
        assertFalse(krani.contains("laporan.lihat"));
    }
    @Test
    public void namaRoleUntukBeranda() {
        assertEquals("Krani", auth.namaRole(1));
        assertEquals("Mandor", auth.namaRole(2));
        assertEquals("", auth.namaRole(999));
    }
}
