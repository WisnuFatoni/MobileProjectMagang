package com.example.fai26.qr;

/**
 * Isi QR di TPH: {@code FAI26|TPH|<versi>|<unit>|<blok>|<no tph>}, contoh {@code FAI26|TPH|1|KP_STL|A12|07}.
 * QR hanya berisi kode, bukan seluruh data TPH. Aplikasi mencari kode ini di master lokal
 * (kolom tph.qr_kode), jadi QR tetap terbaca tanpa sinyal dan data TPH bisa berubah tanpa mencetak ulang QR.
 *
 * Spesifikasi lengkap untuk pembuat QR (web admin) ada di progres/SPEK_QR_TPH.md.
 */
public final class QrTph {

    public static final String PENANDA = "FAI26";
    public static final String JENIS = "TPH";
    public static final String VERSI = "1";

    public final String unit;
    public final String blok;
    public final String noTph;
    /** Teks QR yang sudah dibersihkan, persis seperti yang disimpan di tph.qr_kode. */
    public final String kode;

    private QrTph(String unit, String blok, String noTph) {
        this.unit = unit;
        this.blok = blok;
        this.noTph = noTph;
        this.kode = buatKode(unit, blok, noTph);
    }

    public static String buatKode(String unit, String blok, String noTph) {
        return PENANDA + "|" + JENIS + "|" + VERSI + "|" + unit + "|" + blok + "|" + noTph;
    }

    /** Hasil membaca teks QR: berhasil (tph terisi) atau gagal (galat berisi pesan untuk krani). */
    public static final class Hasil {
        public final QrTph tph;
        public final String galat;

        private Hasil(QrTph tph, String galat) {
            this.tph = tph;
            this.galat = galat;
        }

        public boolean berhasil() {
            return tph != null;
        }
    }

    public static Hasil baca(String teks) {
        if (teks == null || teks.trim().isEmpty()) return gagal("QR kosong.");
        String t = teks.trim();
        String[] p = t.split("\\|", -1);
        if (p.length != 6 || !PENANDA.equals(p[0])) {
            return gagal("Ini bukan QR fAI26. Arahkan ke QR yang dipasang di TPH.");
        }
        if (!JENIS.equals(p[1])) {
            return gagal("QR ini bukan QR TPH (jenis: " + p[1] + ").");
        }
        if (!VERSI.equals(p[2])) {
            return gagal("Versi QR " + p[2] + " belum didukung. Perbarui aplikasi.");
        }
        if (p[3].isEmpty() || p[4].isEmpty() || p[5].isEmpty()) {
            return gagal("QR TPH tidak lengkap.");
        }
        return new Hasil(new QrTph(p[3], p[4], p[5]), null);
    }

    private static Hasil gagal(String pesan) {
        return new Hasil(null, pesan);
    }
}
