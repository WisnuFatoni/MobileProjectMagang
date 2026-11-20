package com.example.fai26.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.fai26.db.dao.AkunDao;
import com.example.fai26.db.dao.MasterDao;
import com.example.fai26.db.dao.PanenDao;
import com.example.fai26.db.dao.SyncDao;
import com.example.fai26.db.entity.AppUser;
import com.example.fai26.db.entity.AuditLog;
import com.example.fai26.db.entity.Blok;
import com.example.fai26.db.entity.Deteksi;
import com.example.fai26.db.entity.Device;
import com.example.fai26.db.entity.Divisi;
import com.example.fai26.db.entity.Foto;
import com.example.fai26.db.entity.KelasGrading;
import com.example.fai26.db.entity.MasterSync;
import com.example.fai26.db.entity.ModelVersi;
import com.example.fai26.db.entity.PanenGrading;
import com.example.fai26.db.entity.PanenHeader;
import com.example.fai26.db.entity.PanenPemanen;
import com.example.fai26.db.entity.Pemanen;
import com.example.fai26.db.entity.PeriodeRotasi;
import com.example.fai26.db.entity.Permission;
import com.example.fai26.db.entity.Role;
import com.example.fai26.db.entity.RolePermission;
import com.example.fai26.db.entity.SyncQueue;
import com.example.fai26.db.entity.Tph;
import com.example.fai26.db.entity.Unit;
import com.example.fai26.db.entity.UserRole;

/**
 * Database HP (SQLite lewat Room), 22 tabel sesuai ERD (erd/fAI26_ERD_Database_Mobile.md).
 * Foreign key selalu aktif (Room menyalakannya).
 */
@Database(
        entities = {
                Unit.class, Divisi.class, Blok.class, Tph.class, Pemanen.class, PeriodeRotasi.class,
                KelasGrading.class, ModelVersi.class, Device.class,
                AppUser.class, Role.class, Permission.class, UserRole.class, RolePermission.class,
                PanenHeader.class, PanenPemanen.class, PanenGrading.class, Foto.class, Deteksi.class,
                AuditLog.class, SyncQueue.class, MasterSync.class
        },
        version = 4,
        exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {

    public static final String NAMA = "fai26.db";

    /** Versi 2: kolom verifikasi lokasi GPS di panen_header (ERD 10 Nov 2026). Catatan lama jadi TANPA_GPS. */
    /**
     * Versi 3: enam batasan UNIQUE dari ERD yang sebelumnya tidak terbentuk (username, kode unit, kode role,
     * kode permission, kode kelas, dan qr_kode TPH). Ditemukan oleh tes unduh master: tanpa ini dua akun bisa
     * memakai username yang sama.
     */
    public static final Migration MIGRASI_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `ux_unit_kode` ON `unit` (`kode`)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `ux_tph_qr_kode` ON `tph` (`qr_kode`)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `ux_kelas_grading_kode` ON `kelas_grading` (`kode`)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `ux_app_user_username` ON `app_user` (`username`)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `ux_role_kode` ON `role` (`kode`)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `ux_permission_kode` ON `permission` (`kode`)");
        }
    };

    /**
     * Versi 4 (mandor yang menginput): profil mandor di akun (kode HRMB dan afdeling) dan penanda kirim ulang catatan
     * DITOLAK. Hanya menambah kolom, jadi catatan offline yang belum terkirim tidak hilang.
     */
    public static final Migration MIGRASI_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE app_user ADD COLUMN mandor_hrmb_code INTEGER");
            db.execSQL("ALTER TABLE app_user ADD COLUMN afdeling_kode TEXT");
            db.execSQL("ALTER TABLE panen_header ADD COLUMN kirim_ulang INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static final Migration MIGRASI_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE panen_header ADD COLUMN gps_akurasi_m REAL");
            db.execSQL("ALTER TABLE panen_header ADD COLUMN gps_waktu TEXT");
            db.execSQL("ALTER TABLE panen_header ADD COLUMN gps_mock INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE panen_header ADD COLUMN jarak_ke_tph_m REAL");
            db.execSQL("ALTER TABLE panen_header ADD COLUMN status_lokasi TEXT NOT NULL DEFAULT 'TANPA_GPS'");
        }
    };

    public abstract AkunDao akunDao();

    public abstract MasterDao masterDao();

    public abstract PanenDao panenDao();

    public abstract SyncDao syncDao();

    private static volatile AppDatabase instance;

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, NAMA)
                            .addCallback(callback())
                            .addMigrations(MIGRASI_1_2, MIGRASI_2_3, MIGRASI_3_4)
                            .build();
                }
            }
        }
        return instance;
    }

    /**
     * Dipanggil sekali saat database pertama dibuat: mengubah lima indeks unik menjadi parsial
     * (WHERE dihapus_pada IS NULL) seperti di ERD, lalu mengisi data awal.
     */
    public static Callback callback() {
        return new Callback() {
            @Override
            public void onCreate(@NonNull SupportSQLiteDatabase db) {
                for (String sql : IndeksParsial.SQL) db.execSQL(sql);
                SeedData.isi(db);
            }
        };
    }
}
