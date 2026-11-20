package com.example.fai26.db;

/**
 * Siklus status catatan panen. Skrip SQLite di ERD membatasi nilai ini dengan CHECK,
 * tetapi Room tidak bisa menyatakan CHECK, jadi pembatasan dilakukan di kode.
 */
public final class StatusPanen {

    public static final String DRAFT = "DRAFT";
    /** Mandor sudah menyetujui (permintaan Ptoni, 10 Nov; belum ada di ERD awal). */
    public static final String DISETUJUI = "DISETUJUI";
    public static final String SIAP_KIRIM = "SIAP_KIRIM";
    public static final String TERKIRIM = "TERKIRIM";
    public static final String DITERIMA = "DITERIMA";
    public static final String DITOLAK = "DITOLAK";
    /** Sudah di server tetapi belum disetujui krani (catatan mandor). Kembali TERKIRIM setelah krani menyetujui. */
    public static final String MENUNGGU_SETUJU = "MENUNGGU_SETUJU";

    private static final String[] SEMUA = {DRAFT, DISETUJUI, SIAP_KIRIM, TERKIRIM, DITERIMA, DITOLAK, MENUNGGU_SETUJU};

    private StatusPanen() {
    }

    public static boolean valid(String status) {
        for (String s : SEMUA) if (s.equals(status)) return true;
        return false;
    }
}
