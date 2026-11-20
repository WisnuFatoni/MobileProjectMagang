package com.example.fai26.sync;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;

import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.fai26.db.AppDatabase;
import com.example.fai26.db.Waktu;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.function.LongSupplier;

/**
 * Mengunduh master dari server (GET /api/master) dan menerapkannya ke database HP.
 *
 * - Versi dibandingkan dulu: sama berarti tidak ada yang diunduh.
 * - Baris dicocokkan menurut id dari server: yang ada diperbarui, yang belum ada ditambahkan. Baris yang dihapus
 *   lunak di server ikut ditandai (tidak dibuang), supaya catatan lama tetap bisa ditampilkan.
 * - Koordinat TPH yang direkam di HP tidak ditimpa bila server belum punya koordinat.
 * - Akun diperbarui tanpa menyentuh verifier offline, kunci akun, atau hitungan gagal login.
 * - Semuanya dalam satu transaksi: gagal di tengah berarti tidak ada yang berubah.
 */
public class MasterSync {

    public static final String NAMA = "master";

    /** Hasil penerapan master. */
    public static final class Hasil {
        public final boolean berubah;
        public final String versi;
        public final int blok;
        public final int tph;
        public final int pemanen;
        public final int user;

        Hasil(boolean berubah, String versi, int blok, int tph, int pemanen, int user) {
            this.berubah = berubah;
            this.versi = versi;
            this.blok = blok;
            this.tph = tph;
            this.pemanen = pemanen;
            this.user = user;
        }
    }

    // Jenis kolom: L bilangan bulat, D desimal, S teks, B boolean (disimpan 0 atau 1).
    private static final String[][] WAKTU = {{"dibuat_pada", "S"}, {"diubah_pada", "S"}, {"dihapus_pada", "S"}};
    private static final String[][] UNIT = gabung(new String[][]{
            {"unit_id", "L"}, {"kode", "S"}, {"nama", "S"}, {"erp_db", "S"}, {"tahun_cluster", "L"}, {"aktif", "B"}}, WAKTU);
    private static final String[][] DIVISI = gabung(new String[][]{
            {"divisi_id", "L"}, {"unit_id", "L"}, {"erp_oid", "L"}, {"kode", "S"}, {"nama", "S"}, {"aktif", "B"}}, WAKTU);
    private static final String[][] BLOK = gabung(new String[][]{
            {"blok_id", "L"}, {"unit_id", "L"}, {"divisi_id", "L"}, {"erp_oid", "L"}, {"kode", "S"}, {"nama", "S"},
            {"luas_ha", "D"}, {"tahun_tanam", "L"}, {"sph", "D"}, {"aktif", "B"}}, WAKTU);
    private static final String[][] TPH = gabung(new String[][]{
            {"tph_id", "L"}, {"blok_id", "L"}, {"no_tph", "S"}, {"qr_kode", "S"}, {"lat", "D"}, {"lon", "D"}, {"aktif", "B"}}, WAKTU);
    private static final String[][] PEMANEN = gabung(new String[][]{
            {"pemanen_id", "L"}, {"unit_id", "L"}, {"erp_hrmb_code", "L"}, {"kode", "S"}, {"nama", "S"}, {"aktif", "B"}}, WAKTU);

    private static String[][] gabung(String[][] a, String[][] b) {
        String[][] out = new String[a.length + b.length][];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private final AppDatabase db;
    private final LongSupplier jam;

    public MasterSync(AppDatabase db) {
        this(db, System::currentTimeMillis);
    }

    public MasterSync(AppDatabase db, LongSupplier jam) {
        this.db = db;
        this.jam = jam;
    }

    /** Versi master yang tersimpan di HP, atau string kosong bila belum pernah mengunduh. */
    public String versiLokal() {
        try (Cursor c = db.getOpenHelper().getReadableDatabase().query(
                "SELECT versi_server FROM master_sync WHERE nama_master = '" + NAMA
                        + "' ORDER BY sinkron_terakhir DESC LIMIT 1")) {
            return c.moveToFirst() && !c.isNull(0) ? c.getString(0) : "";
        }
    }

    /** Waktu (millis) master terakhir diunduh, atau 0. */
    public long sinkronTerakhir() {
        try (Cursor c = db.getOpenHelper().getReadableDatabase().query(
                "SELECT sinkron_terakhir FROM master_sync WHERE nama_master = '" + NAMA
                        + "' ORDER BY sinkron_terakhir DESC LIMIT 1")) {
            return c.moveToFirst() && !c.isNull(0) ? Waktu.keMillis(c.getString(0)) : 0L;
        }
    }

    /**
     * Menanyakan versi ke server dan menerapkan master bila berubah.
     *
     * @throws ApiException bila server tidak terjangkau atau menolak, atau master bentrok dengan data di HP.
     */
    public Hasil ambil(ServerApi api, String tokenAkses) throws ApiException {
        String lokal = versiLokal();
        ServerApi.MasterPaket paket = api.master(tokenAkses, lokal);
        if (!paket.berubah || paket.isi == null) return new Hasil(false, paket.versi, 0, 0, 0, 0);
        try {
            return terapkan(paket.isi);
        } catch (JSONException | SQLiteConstraintException e) {
            // Isi tidak bisa dibaca, atau bentrok (misalnya username yang sama dengan id berbeda dari data contoh).
            throw new ApiException(422, "Master dari server tidak bisa diterapkan: " + e.getMessage(), e);
        }
    }

    /** Menerapkan isi JSON master ke database. Dipakai juga oleh tes. */
    Hasil terapkan(JSONObject m) throws JSONException {
        final String sekarang = Waktu.utc(jam.getAsLong());
        final int[] n = new int[4];
        try {
            db.runInTransaction(() -> {
                try {
                    SupportSQLiteDatabase sdb = db.getOpenHelper().getWritableDatabase();
                    JSONObject unit = m.optJSONObject("unit");
                    if (unit != null) upsert(sdb, "unit", "unit_id", UNIT, unit, sekarang);
                    upsertSemua(sdb, "divisi", "divisi_id", DIVISI, m.optJSONArray("divisi"), sekarang);
                    n[0] = upsertSemua(sdb, "blok", "blok_id", BLOK, m.optJSONArray("blok"), sekarang);
                    n[1] = upsertSemua(sdb, "tph", "tph_id", TPH, m.optJSONArray("tph"), sekarang);
                    n[2] = upsertSemua(sdb, "pemanen", "pemanen_id", PEMANEN, m.optJSONArray("pemanen"), sekarang);
                    if (unit != null) {
                        // Paket lengkap = potret master untuk akun ini (akun mandor hanya menerima pemanen miliknya dan
                        // blok/TPH afdelingnya; daftar kosong tidak dikirim server). Baris yang tidak ada di potret disembunyikan
                        // (hapus lunak), bukan dibuang: catatan lama tetap bisa ditampilkan, dan baris itu hidup lagi bila
                        // akun lain (atau pembatasan yang berubah) membawanya kembali.
                        sembunyikanYangTidakAda(sdb, "divisi", "divisi_id", m.optJSONArray("divisi"), sekarang);
                        sembunyikanYangTidakAda(sdb, "blok", "blok_id", m.optJSONArray("blok"), sekarang);
                        sembunyikanYangTidakAda(sdb, "tph", "tph_id", m.optJSONArray("tph"), sekarang);
                        sembunyikanYangTidakAda(sdb, "pemanen", "pemanen_id", m.optJSONArray("pemanen"), sekarang);
                    }
                    n[3] = terapkanUser(sdb, m.optJSONArray("user"), sekarang);
                    terapkanHakRole(sdb, m.optJSONArray("role_permission"), sekarang);
                    catatVersi(sdb, m.getString("versi"), unit == null ? firstUnit(sdb) : unit.getLong("unit_id"), sekarang);
                } catch (JSONException e) {
                    throw new IllegalStateException(e); // membatalkan transaksi
                }
            });
        } catch (IllegalStateException e) {
            if (e.getCause() instanceof JSONException) throw (JSONException) e.getCause();
            throw e;
        }
        PerubahanMaster.umumkan(); // setelah transaksi selesai: layar yang menampilkan master boleh memuat ulang
        return new Hasil(true, m.getString("versi"), n[0], n[1], n[2], n[3]);
    }

    /** Menandai (hapus lunak) baris tabel yang id-nya tidak ada di daftar dari server; daftar kosong atau tidak ada = semua. */
    private static void sembunyikanYangTidakAda(SupportSQLiteDatabase sdb, String tabel, String kunci, JSONArray a,
                                                String sekarang) throws JSONException {
        StringBuilder ids = new StringBuilder();
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                if (i > 0) ids.append(',');
                ids.append(a.getJSONObject(i).getLong(kunci)); // bilangan bulat: aman disisipkan langsung
            }
        }
        String syarat = ids.length() == 0 ? "" : " AND " + kunci + " NOT IN (" + ids + ")";
        sdb.execSQL("UPDATE " + tabel + " SET dihapus_pada = ?, diubah_pada = ? WHERE dihapus_pada IS NULL" + syarat,
                new Object[]{sekarang, sekarang});
    }

    private static long firstUnit(SupportSQLiteDatabase sdb) {
        try (Cursor c = sdb.query("SELECT unit_id FROM unit ORDER BY unit_id LIMIT 1")) {
            return c.moveToFirst() ? c.getLong(0) : 0L;
        }
    }

    private static int upsertSemua(SupportSQLiteDatabase sdb, String tabel, String kunci, String[][] kolom,
                                   JSONArray a, String sekarang) throws JSONException {
        if (a == null) return 0;
        for (int i = 0; i < a.length(); i++) upsert(sdb, tabel, kunci, kolom, a.getJSONObject(i), sekarang);
        return a.length();
    }

    /** Memperbarui baris menurut id, atau menambahkannya bila belum ada. */
    private static void upsert(SupportSQLiteDatabase sdb, String tabel, String kunci, String[][] kolom,
                               JSONObject o, String sekarang) throws JSONException {
        ContentValues v = new ContentValues();
        for (String[] k : kolom) {
            String nama = k[0];
            if (!o.has(nama)) continue;
            if (o.isNull(nama)) {
                // dibuat_pada wajib ada; koordinat TPH yang direkam di HP tidak ditimpa dengan kosong.
                if (nama.equals("dibuat_pada")) v.put(nama, sekarang);
                else if (tabel.equals("tph") && (nama.equals("lat") || nama.equals("lon"))) continue;
                else v.putNull(nama);
                continue;
            }
            switch (k[1]) {
                case "L":
                    v.put(nama, o.getLong(nama));
                    break;
                case "D":
                    v.put(nama, o.getDouble(nama));
                    break;
                case "B":
                    v.put(nama, o.getBoolean(nama) ? 1 : 0);
                    break;
                default:
                    v.put(nama, o.getString(nama));
                    break;
            }
        }
        long id = o.getLong(kunci);
        int diubah = sdb.update(tabel, SQLiteDatabase.CONFLICT_NONE, v, kunci + " = ?", new Object[]{id});
        if (diubah == 0) {
            if (!v.containsKey("dibuat_pada")) v.put("dibuat_pada", sekarang);
            sdb.insert(tabel, SQLiteDatabase.CONFLICT_NONE, v);
        }
    }

    private static int terapkanUser(SupportSQLiteDatabase sdb, JSONArray a, String sekarang) throws JSONException {
        if (a == null) return 0;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            long id = o.getLong("user_id");
            boolean dihapus = !o.isNull("dihapus_pada");
            ContentValues v = new ContentValues();
            v.put("unit_id", o.getLong("unit_id"));
            v.put("username", o.getString("username"));
            v.put("nama", o.getString("nama"));
            v.put("aktif", o.getBoolean("aktif") && !dihapus ? 1 : 0);
            if (o.isNull("erp_user_oid")) v.putNull("erp_user_oid");
            else v.put("erp_user_oid", o.getLong("erp_user_oid"));
            // Profil mandor (akun mandor): kode HRMB dan afdeling, kosong untuk akun lain.
            if (o.isNull("mandor_hrmb_code")) v.putNull("mandor_hrmb_code");
            else v.put("mandor_hrmb_code", o.getLong("mandor_hrmb_code"));
            if (o.isNull("afdeling_kode")) v.putNull("afdeling_kode");
            else v.put("afdeling_kode", o.getString("afdeling_kode"));
            if (dihapus) v.put("dihapus_pada", o.getString("dihapus_pada"));
            else v.putNull("dihapus_pada");
            int diubah = sdb.update("app_user", SQLiteDatabase.CONFLICT_NONE, v, "user_id = ?", new Object[]{id});
            if (diubah == 0) {
                v.put("user_id", id);
                v.put("gagal_login", 0); // tabel buatan Room tidak punya DEFAULT, kolom ini NOT NULL
                v.put("dibuat_pada", sekarang);
                sdb.insert("app_user", SQLiteDatabase.CONFLICT_NONE, v);
            }

            // Role: diganti sesuai server. Kode yang tidak dikenal di HP dilewati.
            sdb.execSQL("DELETE FROM user_role WHERE user_id = ?", new Object[]{id});
            JSONArray role = o.optJSONArray("role");
            if (role != null && !dihapus) {
                for (int r = 0; r < role.length(); r++) {
                    long roleId = -1;
                    try (Cursor c = sdb.query("SELECT role_id FROM role WHERE kode = ? AND dihapus_pada IS NULL",
                            new Object[]{role.getString(r)})) {
                        if (c.moveToFirst()) roleId = c.getLong(0);
                    }
                    if (roleId < 0) continue;
                    long urId;
                    try (Cursor c = sdb.query("SELECT COALESCE(MAX(ur_id), 0) + 1 FROM user_role")) {
                        c.moveToFirst();
                        urId = c.getLong(0);
                    }
                    sdb.execSQL("INSERT INTO user_role (ur_id, user_id, role_id, dibuat_pada) VALUES (?, ?, ?, ?)",
                            new Object[]{urId, id, roleId, sekarang});
                }
            }
        }
        return a.length();
    }

    /**
     * Mengganti seluruh hak role di HP dengan yang dari server. Kunci yang tidak ada atau daftar kosong berarti "jangan
     * ubah" (daftar kosong tidak mungkin dikirim server yang benar, dan menghapus semua hak akan mengunci semua
     * pengguna). Kode role atau permission yang tidak dikenal HP dilewati.
     */
    private static void terapkanHakRole(SupportSQLiteDatabase sdb, JSONArray a, String sekarang) throws JSONException {
        if (a == null || a.length() == 0) return;
        sdb.execSQL("DELETE FROM role_permission");
        long rpId = 0;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            long roleId = idDariKode(sdb, "role", "role_id", o.getString("role"));
            JSONArray izin = o.optJSONArray("permission");
            if (roleId < 0 || izin == null) continue;
            for (int k = 0; k < izin.length(); k++) {
                long permId = idDariKode(sdb, "permission", "permission_id", izin.getString(k));
                if (permId < 0) continue;
                sdb.execSQL("INSERT OR IGNORE INTO role_permission (rp_id, role_id, permission_id, dibuat_pada) "
                        + "VALUES (?, ?, ?, ?)", new Object[]{++rpId, roleId, permId, sekarang});
            }
        }
    }

    private static long idDariKode(SupportSQLiteDatabase sdb, String tabel, String kolomId, String kode) {
        try (Cursor c = sdb.query("SELECT " + kolomId + " FROM " + tabel + " WHERE kode = ? AND dihapus_pada IS NULL",
                new Object[]{kode})) {
            return c.moveToFirst() ? c.getLong(0) : -1L;
        }
    }

    private static void catatVersi(SupportSQLiteDatabase sdb, String versi, long unitId, String sekarang) {
        sdb.execSQL("DELETE FROM master_sync WHERE nama_master = ?", new Object[]{NAMA});
        sdb.execSQL("INSERT INTO master_sync (nama_master, unit_id, versi_server, sinkron_terakhir, dibuat_pada) "
                + "VALUES (?, ?, ?, ?, ?)", new Object[]{NAMA, unitId, versi, sekarang, sekarang});
    }
}
