package com.example.fai26.auth;

import com.example.fai26.db.AppDatabase;
import com.example.fai26.db.Waktu;
import com.example.fai26.db.dao.AkunDao;
import com.example.fai26.db.entity.AppUser;
import com.example.fai26.db.entity.AuditLog;

import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Login offline sesuai ERD Bab 4.4: akun diperiksa dari database HP (verifier lokal),
 * akun terkunci 15 menit setelah 5 kali salah, dan login hanya berlaku sampai
 * offline_berlaku_sampai. Setiap percobaan masuk ke audit_log.
 */
public class AuthService {

    public static final int MAKS_GAGAL = 5;
    public static final long KUNCI_MENIT = 15;
    public static final long OFFLINE_HARI = 7;

    public enum Status {
        BERHASIL,
        /** Username atau password salah (sengaja tidak dibedakan). */
        SALAH,
        TERKUNCI,
        TIDAK_AKTIF,
        /** Masa login offline habis atau akun belum pernah login online. */
        PERLU_ONLINE,
        /** Server menerima login, tetapi akun belum ada di master HP (unduh master belum tersedia). */
        AKUN_BELUM_ADA
    }

    public static final class Hasil {
        public final Status status;
        public final long userId;
        public final String nama;
        public final long menitKunci;
        public final int sisaPercobaan;

        Hasil(Status status, long userId, String nama, long menitKunci, int sisaPercobaan) {
            this.status = status;
            this.userId = userId;
            this.nama = nama;
            this.menitKunci = menitKunci;
            this.sisaPercobaan = sisaPercobaan;
        }
    }

    private final AppDatabase db;
    private final String deviceId;
    private final LongSupplier jam;

    public AuthService(AppDatabase db, String deviceId) {
        this(db, deviceId, System::currentTimeMillis);
    }

    public AuthService(AppDatabase db, String deviceId, LongSupplier jam) {
        this.db = db;
        this.deviceId = deviceId;
        this.jam = jam;
    }

    public Hasil loginOffline(String username, String password) {
        final long sekarang = jam.getAsLong();
        final String waktu = Waktu.utc(sekarang);
        final AkunDao dao = db.akunDao();
        final Hasil[] hasil = new Hasil[1];

        db.runInTransaction(() -> {
            AppUser u = username == null || username.isEmpty() ? null : dao.cari(username);
            if (u == null || password == null || password.isEmpty()) {
                dao.insertAudit(audit(null, username, "LOGIN_GAGAL", waktu, "OFFLINE: akun tidak dikenal"));
                hasil[0] = new Hasil(Status.SALAH, 0, null, 0, 0);
                return;
            }
            if (u.aktif == 0) {
                dao.insertAudit(audit(u, username, "LOGIN_GAGAL", waktu, "OFFLINE: akun tidak aktif"));
                hasil[0] = new Hasil(Status.TIDAK_AKTIF, u.userId, u.nama, 0, 0);
                return;
            }
            if (u.terkunciSampai != null && Waktu.keMillis(u.terkunciSampai) > sekarang) {
                long menit = (Waktu.keMillis(u.terkunciSampai) - sekarang + 59_999) / 60_000;
                dao.insertAudit(audit(u, username, "LOGIN_GAGAL", waktu, "OFFLINE: akun terkunci"));
                hasil[0] = new Hasil(Status.TERKUNCI, u.userId, u.nama, menit, 0);
                return;
            }
            if (u.offlineHash == null || u.offlineSalt == null || u.offlineBerlakuSampai == null
                    || Waktu.keMillis(u.offlineBerlakuSampai) <= sekarang) {
                dao.insertAudit(audit(u, username, "LOGIN_GAGAL", waktu,
                        "OFFLINE: masa login offline habis"));
                hasil[0] = new Hasil(Status.PERLU_ONLINE, u.userId, u.nama, 0, 0);
                return;
            }

            if (PasswordHasher.cocok(password, u.offlineSalt, u.offlineHash)) {
                u.gagalLogin = 0;
                u.terkunciSampai = null;
                u.loginTerakhir = waktu;
                u.diubahPada = waktu;
                u.diubahOleh = u.userId;
                dao.ubah(u);
                dao.insertAudit(audit(u, username, "LOGIN", waktu, "OFFLINE"));
                hasil[0] = new Hasil(Status.BERHASIL, u.userId, u.nama, 0, MAKS_GAGAL);
                return;
            }

            u.gagalLogin++;
            u.diubahPada = waktu;
            if (u.gagalLogin >= MAKS_GAGAL) {
                u.terkunciSampai = Waktu.utc(sekarang + KUNCI_MENIT * 60_000);
                u.gagalLogin = 0; // setelah kunci habis, hitungan mulai dari awal
                dao.ubah(u);
                dao.insertAudit(audit(u, username, "LOGIN_GAGAL", waktu,
                        "OFFLINE: salah " + MAKS_GAGAL + " kali, akun dikunci " + KUNCI_MENIT + " menit"));
                hasil[0] = new Hasil(Status.TERKUNCI, u.userId, u.nama, KUNCI_MENIT, 0);
            } else {
                dao.ubah(u);
                dao.insertAudit(audit(u, username, "LOGIN_GAGAL", waktu, "OFFLINE: password salah"));
                hasil[0] = new Hasil(Status.SALAH, u.userId, u.nama, 0,
                        (int) (MAKS_GAGAL - u.gagalLogin));
            }
        });
        return hasil[0];
    }

    /**
     * Menerapkan login yang sudah diterima server: password terbaru menjadi verifier offline dan masa
     * login offline diperpanjang {@link #OFFLINE_HARI} hari. Akun harus sudah ada di master HP.
     */
    public Hasil terapkanLoginOnline(String username) {
        return terapkanLoginOnline(username, null);
    }

    public Hasil terapkanLoginOnline(String username, String password) {
        final long sekarang = jam.getAsLong();
        final String waktu = Waktu.utc(sekarang);
        final AkunDao dao = db.akunDao();
        final Hasil[] hasil = new Hasil[1];
        db.runInTransaction(() -> {
            AppUser u = dao.cari(username);
            if (u == null) {
                dao.insertAudit(audit(null, username, "LOGIN_GAGAL", waktu, "ONLINE: akun belum ada di master HP"));
                hasil[0] = new Hasil(Status.AKUN_BELUM_ADA, 0, null, 0, 0);
                return;
            }
            if (u.aktif == 0) {
                dao.insertAudit(audit(u, username, "LOGIN_GAGAL", waktu, "ONLINE: akun tidak aktif di HP"));
                hasil[0] = new Hasil(Status.TIDAK_AKTIF, u.userId, u.nama, 0, 0);
                return;
            }
            if (password != null) {
                String salt = PasswordHasher.buatSalt();
                u.offlineSalt = salt;
                u.offlineHash = PasswordHasher.buatVerifier(password, salt);
            }
            u.offlineBerlakuSampai = Waktu.utc(sekarang + OFFLINE_HARI * 24L * 60 * 60 * 1000);
            u.gagalLogin = 0;
            u.terkunciSampai = null;
            u.loginTerakhir = waktu;
            u.diubahPada = waktu;
            u.diubahOleh = u.userId;
            dao.ubah(u);
            dao.insertAudit(audit(u, username, "LOGIN", waktu, "ONLINE"));
            hasil[0] = new Hasil(Status.BERHASIL, u.userId, u.nama, 0, MAKS_GAGAL);
        });
        return hasil[0];
    }

    /**
     * Memperbarui verifier offline setelah password diganti DI SERVER (hanya dipanggil setelah server menerimanya).
     * Masa login offline tidak diubah. Tercatat di audit_log.
     *
     * @return false bila akun tidak ada di HP
     */
    public boolean ubahPasswordSetelahServer(String username, String passwordBaru) {
        final String waktu = Waktu.utc(jam.getAsLong());
        final AkunDao dao = db.akunDao();
        final boolean[] ok = {false};
        db.runInTransaction(() -> {
            AppUser u = dao.cari(username);
            if (u == null) return;
            String salt = PasswordHasher.buatSalt();
            u.offlineSalt = salt;
            u.offlineHash = PasswordHasher.buatVerifier(passwordBaru, salt);
            u.gagalLogin = 0;
            u.terkunciSampai = null;
            u.diubahPada = waktu;
            u.diubahOleh = u.userId;
            dao.ubah(u);
            dao.insertAudit(audit(u, username, "GANTI_PASSWORD", waktu, "ONLINE"));
            ok[0] = true;
        });
        return ok[0];
    }

    /** Apakah akun sudah ada di master HP. */
    public boolean akunAda(String username) {
        return username != null && db.akunDao().cari(username) != null;
    }

    /** Server menolak login (password salah, akun terkunci, dan sebagainya): dicatat di audit_log HP. */
    public void catatDitolakServer(String username, String alasan) {
        AppUser u = db.akunDao().cari(username);
        db.akunDao().insertAudit(audit(u, username, "LOGIN_GAGAL", Waktu.utc(jam.getAsLong()),
                "ONLINE: ditolak server (" + alasan + ")"));
    }

    /** Mengatur password akun dan masa berlaku login offline (dipakai saat penyediaan akun). */
    public void aturPassword(long userId, String password, long berlakuSampaiMillis) {
        AkunDao dao = db.akunDao();
        AppUser u = dao.cariId(userId);
        if (u == null) throw new IllegalArgumentException("Akun tidak ada: " + userId);
        String salt = PasswordHasher.buatSalt();
        u.offlineSalt = salt;
        u.offlineHash = PasswordHasher.buatVerifier(password, salt);
        u.offlineBerlakuSampai = Waktu.utc(berlakuSampaiMillis);
        u.gagalLogin = 0;
        u.terkunciSampai = null;
        u.diubahPada = Waktu.utc(jam.getAsLong());
        dao.ubah(u);
    }

    /** Permission akun yang berlaku hari ini. */
    public List<String> permission(long userId) {
        return db.akunDao().permission(userId, Waktu.tanggalLokalHariIni());
    }

    public String namaRole(long userId) {
        String r = db.akunDao().namaRole(userId);
        return r == null ? "" : r;
    }

    /** Apakah akun masih boleh dipakai: aktif, tidak dihapus, dan login offline belum habis. */
    public boolean masihBerlaku(long userId) {
        AppUser u = db.akunDao().cariId(userId);
        return u != null && u.aktif != 0 && u.offlineBerlakuSampai != null
                && Waktu.keMillis(u.offlineBerlakuSampai) > jam.getAsLong();
    }

    private AuditLog audit(AppUser u, String username, String aksi, String waktu, String keterangan) {
        AuditLog a = new AuditLog();
        a.auditId = UUID.randomUUID().toString();
        a.tabel = "app_user";
        a.barisId = u == null ? "-" : String.valueOf(u.userId);
        a.aksi = aksi;
        a.userId = u == null ? null : u.userId;
        a.username = username;
        a.deviceId = deviceId;
        a.waktu = waktu;
        a.keterangan = keterangan;
        return a;
    }
}
