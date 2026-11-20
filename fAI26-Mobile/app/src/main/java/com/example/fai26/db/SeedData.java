package com.example.fai26.db;

import androidx.sqlite.db.SupportSQLiteDatabase;

/** Data awal sistem: kelas grading, role, permission, dan versi model. */
final class SeedData {

    private SeedData() {
    }

    /** SHA-256 berkas assets/models/best.tflite (model yang dilatih 2 November 2026). */
    private static final String HASH_MODEL =
            "cc47a63334755e2ceb8055ec4607bfdcad19a817fcaf58c46069f866d0756107";

    static void isi(SupportSQLiteDatabase db) {
        String now = Waktu.utcSekarang();

        // kelas_id mengikuti ERD. label_model disesuaikan dengan urutan keluaran model
        // (0 Abnormal, 1 Empty Bunch, 2 Overripe, 3 Ripe, 4 Underripe, 5 Unripe).
        // erp_kolom abnormal masih usulan (pembimbing belum memutuskan, lihat B4).
        Object[][] kelas = {
                {1, "unripe", "Unripe", 5, null, 0, 1},
                {2, "underripe", "Under-ripe", 4, null, 0, 2},
                {3, "ripe", "Ripe", 3, "JJG_RIPE", 1, 3},
                {4, "overripe", "Overripe", 2, "JJG_OVERRIPE", 1, 4},
                {5, "empty", "Empty bunch", 1, "JJG_EMPTY", 0, 5},
                {6, "abnormal", "Abnormal", 0, "JJG_UNKNOWN", 0, 6},
        };
        for (Object[] k : kelas) {
            db.execSQL("INSERT INTO kelas_grading (kelas_id, kode, nama, label_model, erp_kolom, "
                            + "boleh_dipanen, urutan, dibuat_pada) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    new Object[]{k[0], k[1], k[2], k[3], k[4], k[5], k[6], now});
        }

        String[][] role = {
                {"KRANI", "Krani", "Mencatat dan memotret di TPH"},
                {"MANDOR", "Mandor", "Memeriksa dan menyetujui catatan krani"},
                {"ASISTEN", "Asisten", "Membaca rekap dan menjalankan impor ke ERP"},
                {"ADMIN", "Admin sistem", "Mengelola pengguna, master, dan perangkat"},
                {"AUDITOR", "Auditor", "Membaca audit log"},
        };
        for (int i = 0; i < role.length; i++) {
            db.execSQL("INSERT INTO role (role_id, kode, nama, deskripsi, bawaan, dibuat_pada) "
                            + "VALUES (?, ?, ?, ?, 1, ?)",
                    new Object[]{i + 1, role[i][0], role[i][1], role[i][2], now});
        }

        // Permission (ERD Bab 4.2) ditambah panen.setujui untuk persetujuan mandor.
        // Kolom: kode, modul, nama, lalu role pemegangnya.
        String[][] perm = {
                {"panen.lihat", "panen", "Melihat catatan panen", "KRANI,MANDOR,ASISTEN,ADMIN,AUDITOR"},
                {"panen.buat", "panen", "Membuat catatan panen", "KRANI,MANDOR"},
                {"panen.ubah", "panen", "Mengubah catatan panen milik sendiri", "KRANI,MANDOR"},
                {"panen.koreksi", "panen", "Mengoreksi catatan panen orang lain", "MANDOR,ASISTEN"},
                {"panen.hapus", "panen", "Menghapus lunak catatan panen", "MANDOR,ASISTEN"},
                {"panen.kirim", "panen", "Mengirim catatan ke server", "KRANI,MANDOR"},
                {"panen.setujui", "panen", "Menyetujui catatan panen krani", "MANDOR"},
                {"grading.ubah", "grading", "Mengubah hasil grading (jml_final)", "KRANI,MANDOR"},
                {"master.lihat", "master", "Melihat data master", "KRANI,MANDOR,ASISTEN,ADMIN,AUDITOR"},
                {"master.kelola", "master", "Mengelola data master di fAI26", "ADMIN"},
                {"laporan.lihat", "laporan", "Melihat rekap panen", "MANDOR,ASISTEN,ADMIN,AUDITOR"},
                {"impor.erp", "impor", "Menjalankan impor ke ERP", "ASISTEN"},
                {"user.kelola", "user", "Mengelola pengguna", "ADMIN"},
                {"role.kelola", "user", "Mengelola role dan permission", "ADMIN"},
                {"device.kelola", "device", "Mengelola perangkat HP", "ADMIN"},
                {"audit.lihat", "audit", "Melihat audit log", "AUDITOR"},
                {"model.kelola", "model", "Mengelola versi model YOLO", "ADMIN"},
        };
        int rpId = 1;
        for (int i = 0; i < perm.length; i++) {
            int permId = i + 1;
            db.execSQL("INSERT INTO permission (permission_id, kode, modul, nama, dibuat_pada) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    new Object[]{permId, perm[i][0], perm[i][1], perm[i][2], now});
            for (String kodeRole : perm[i][3].split(",")) {
                int roleId = 0;
                for (int r = 0; r < role.length; r++) if (role[r][0].equals(kodeRole)) roleId = r + 1;
                db.execSQL("INSERT INTO role_permission (rp_id, role_id, permission_id, dibuat_pada) "
                        + "VALUES (?, ?, ?, ?)", new Object[]{rpId++, roleId, permId, now});
            }
        }

        db.execSQL("INSERT INTO model_versi (model_id, nama, versi, file_hash, tgl_rilis, aktif, "
                        + "dibuat_pada) VALUES (1, 'fAI26 YOLOv8n', '1.0', ?, '2026-11-02', 1, ?)",
                new Object[]{HASH_MODEL, now});
    }
}
