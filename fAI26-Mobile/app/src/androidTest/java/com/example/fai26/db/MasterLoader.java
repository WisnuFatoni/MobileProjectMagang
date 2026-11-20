package com.example.fai26.db;

import android.content.Context;

import com.example.fai26.auth.AuthService;
import com.example.fai26.db.dao.MasterDao;
import com.example.fai26.db.entity.AppUser;
import com.example.fai26.db.entity.Blok;
import com.example.fai26.db.entity.Divisi;
import com.example.fai26.db.entity.Pemanen;
import com.example.fai26.db.entity.Tph;
import com.example.fai26.db.entity.Unit;
import com.example.fai26.db.entity.UserRole;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * HANYA UNTUK TES: memuat data master contoh (androidTest/assets/master_contoh.json) ke database kosong. Aplikasi sungguhan
 * tidak membawa data atau akun contoh; master dan akun datang dari server saat login online pertama.
 */
public final class MasterLoader {

    private static final String BERKAS = "master_contoh.json";

    private MasterLoader() {
    }

    /** @return true bila data dimuat, false bila database sudah berisi master. */
    public static boolean muatBilaKosong(Context context, AppDatabase db) throws IOException {
        if (db.masterDao().jumlahUnit() > 0) return false;
        final JSONObject j;
        try {
            j = new JSONObject(bacaAset(context));
        } catch (JSONException e) {
            throw new IOException("master_contoh.json tidak valid", e);
        }
        try {
            db.runInTransaction(() -> {
                try {
                    isi(db.masterDao(), j);
                } catch (JSONException e) {
                    throw new IllegalStateException(e);
                }
            });
        } catch (IllegalStateException e) {
            throw new IOException("Isi master_contoh.json salah: " + e.getCause(), e);
        }
        return true;
    }

    /**
     * PENYEDIAAN AKUN CONTOH, hanya untuk pengembangan. Memberi password awal (dari
     * master_contoh.json) kepada akun contoh yang belum punya verifier. Password contoh ikut
     * terpasang di APK, jadi JANGAN dipakai di produksi: hapus bagian "password_awal" dan
     * pakai login online begitu API tersedia.
     * Masa login offline: 365 hari untuk build debug, selain itu 7 hari.
     */
    public static void lengkapiPasswordContoh(Context context, AppDatabase db, String deviceId)
            throws IOException {
        try {
            JSONArray users = new JSONObject(bacaAset(context)).getJSONArray("user");
            boolean debug = (context.getApplicationInfo().flags
                    & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            long hari = debug ? 365 : AuthService.OFFLINE_HARI;
            long berlaku = System.currentTimeMillis() + hari * 24L * 60 * 60 * 1000;
            AuthService auth = new AuthService(db, deviceId);
            for (int i = 0; i < users.length(); i++) {
                JSONObject o = users.getJSONObject(i);
                String pwd = o.optString("password_awal", "");
                if (pwd.isEmpty()) continue;
                com.example.fai26.db.entity.AppUser u = db.akunDao().cariId(o.getLong("user_id"));
                if (u != null && u.offlineHash == null) auth.aturPassword(u.userId, pwd, berlaku);
            }
        } catch (JSONException e) {
            throw new IOException("master_contoh.json tidak valid", e);
        }
    }

    private static void isi(MasterDao dao, JSONObject j) throws JSONException {
        String now = Waktu.utcSekarang();

        JSONObject u = j.getJSONObject("unit");
        Unit unit = new Unit();
        unit.unitId = u.getLong("unit_id");
        unit.kode = u.getString("kode");
        unit.nama = u.getString("nama");
        unit.erpDb = u.getString("erp_db");
        unit.tahunCluster = u.getLong("tahun_cluster");
        unit.dibuatPada = now;
        dao.insertUnit(unit);
        long unitId = unit.unitId;

        List<Divisi> divisi = new ArrayList<>();
        JSONArray ad = j.getJSONArray("divisi");
        for (int i = 0; i < ad.length(); i++) {
            JSONObject o = ad.getJSONObject(i);
            Divisi d = new Divisi();
            d.divisiId = o.getLong("divisi_id");
            d.unitId = unitId;
            d.kode = o.getString("kode");
            d.nama = o.getString("nama");
            d.dibuatPada = now;
            divisi.add(d);
        }
        dao.insertDivisi(divisi);

        List<Blok> blok = new ArrayList<>();
        JSONArray ab = j.getJSONArray("blok");
        for (int i = 0; i < ab.length(); i++) {
            JSONObject o = ab.getJSONObject(i);
            Blok b = new Blok();
            b.blokId = o.getLong("blok_id");
            b.unitId = unitId;
            b.divisiId = o.getLong("divisi_id");
            b.erpOid = o.getLong("erp_oid");
            b.kode = o.getString("kode");
            b.luasHa = o.getDouble("luas_ha");
            b.tahunTanam = o.getLong("tahun_tanam");
            b.sph = o.getDouble("sph");
            b.dibuatPada = now;
            blok.add(b);
        }
        dao.insertBlok(blok);

        // Isi QR mengikuti rancangan: FAI26|TPH|<versi>|<unit>|<blok>|<no tph>.
        List<Tph> tph = new ArrayList<>();
        JSONArray at = j.getJSONArray("tph");
        for (int i = 0; i < at.length(); i++) {
            JSONObject o = at.getJSONObject(i);
            long blokId = o.getLong("blok_id");
            String kodeBlok = null;
            for (Blok b : blok) if (b.blokId == blokId) kodeBlok = b.kode;
            Tph t = new Tph();
            t.tphId = o.getLong("tph_id");
            t.blokId = blokId;
            t.noTph = o.getString("no_tph");
            t.qrKode = "FAI26|TPH|1|" + unit.kode + "|" + kodeBlok + "|" + t.noTph;
            t.dibuatPada = now;
            tph.add(t);
        }
        dao.insertTph(tph);

        List<Pemanen> pemanen = new ArrayList<>();
        JSONArray ap = j.getJSONArray("pemanen");
        for (int i = 0; i < ap.length(); i++) {
            JSONObject o = ap.getJSONObject(i);
            Pemanen p = new Pemanen();
            p.pemanenId = o.getLong("pemanen_id");
            p.unitId = unitId;
            p.erpHrmbCode = o.getLong("erp_hrmb_code");
            p.kode = o.optString("kode", null);
            p.nama = o.getString("nama");
            p.dibuatPada = now;
            pemanen.add(p);
        }
        dao.insertPemanen(pemanen);

        // Akun contoh sampai layar login dan akun dari server tersedia. Belum punya kata sandi.
        JSONArray au = j.getJSONArray("user");
        long urId = 1;
        for (int i = 0; i < au.length(); i++) {
            JSONObject o = au.getJSONObject(i);
            AppUser user = new AppUser();
            user.userId = o.getLong("user_id");
            user.unitId = unitId;
            user.username = o.getString("username");
            user.nama = o.getString("nama");
            user.dibuatPada = now;
            dao.insertUser(user);

            UserRole ur = new UserRole();
            ur.urId = urId++;
            ur.userId = user.userId;
            ur.roleId = "MANDOR".equals(o.getString("role")) ? 2 : 1; // id role dari SeedData
            ur.dibuatPada = now;
            dao.insertUserRole(ur);
        }
    }

    private static String bacaAset(Context context) throws IOException {
        // Aset contoh ada di APK tes, bukan di APK aplikasi.
        try (InputStream in = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(BERKAS);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
