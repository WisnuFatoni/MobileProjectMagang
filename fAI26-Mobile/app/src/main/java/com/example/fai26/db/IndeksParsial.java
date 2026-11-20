package com.example.fai26.db;

/** Dihasilkan dari erd/fAI26_mobile_sqlite.sql. Jangan diubah tangan. */
final class IndeksParsial {

    private IndeksParsial() {
    }

    static final String[] SQL = {
        "DROP INDEX ux_blok_unit_id_kode",
        "CREATE UNIQUE INDEX ux_blok_unit_id_kode ON blok (unit_id, kode) WHERE dihapus_pada IS NULL",
        "DROP INDEX ux_tph_blok_id_no_tph",
        "CREATE UNIQUE INDEX ux_tph_blok_id_no_tph ON tph (blok_id, no_tph) WHERE dihapus_pada IS NULL",
        "DROP INDEX ux_user_role_user_id_role_id",
        "CREATE UNIQUE INDEX ux_user_role_user_id_role_id ON user_role (user_id, role_id) WHERE dihapus_pada IS NULL",
        "DROP INDEX ux_role_permission_role_id_permission_id",
        "CREATE UNIQUE INDEX ux_role_permission_role_id_permission_id ON role_permission (role_id, permission_id) WHERE dihapus_pada IS NULL",
        "DROP INDEX ux_panen_grading_panen_id_kelas_id",
        "CREATE UNIQUE INDEX ux_panen_grading_panen_id_kelas_id ON panen_grading (panen_id, kelas_id) WHERE dihapus_pada IS NULL",
    };
}
