package com.example.fai26.panorama;

import java.util.List;

/** Menghitung ukuran kanvas panorama dan letak tiap potongan. Murni hitungan supaya bisa diuji di JVM. */
public final class KanvasPanorama {

    public static final class Tata {
        public final int lebar;
        public final int tinggi;
        /** Skala dari piksel potongan ke piksel panorama akhir (1 bila tidak perlu dikecilkan). */
        public final double skala;
        /** Kiri-atas tiap potongan pada panorama akhir. */
        public final int[][] kiriAtas;
        /** Ukuran tiap potongan pada panorama akhir. */
        public final int lebarTile;
        public final int tinggiTile;

        Tata(int lebar, int tinggi, double skala, int[][] kiriAtas, int lebarTile, int tinggiTile) {
            this.lebar = lebar;
            this.tinggi = tinggi;
            this.skala = skala;
            this.kiriAtas = kiriAtas;
            this.lebarTile = lebarTile;
            this.tinggiTile = tinggiTile;
        }
    }

    private KanvasPanorama() {
    }

    /**
     * @param posisi    kiri-atas potongan pada resolusi taksiran (dari {@link RancangPanorama#posisiTile()})
     * @param faktor    pengali dari resolusi taksiran ke piksel potongan
     * @param lebarTile ukuran potongan (piksel)
     * @param batasSisi sisi terpanjang panorama akhir tidak boleh melebihi ini; bila lebih, dikecilkan
     */
    public static Tata hitung(List<int[]> posisi, double faktor, int lebarTile, int tinggiTile, int batasSisi) {
        if (posisi.isEmpty()) throw new IllegalArgumentException("tidak ada potongan");
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (int[] p : posisi) {
            double x = p[0] * faktor;
            double y = p[1] * faktor;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x + lebarTile);
            maxY = Math.max(maxY, y + tinggiTile);
        }
        double lebarPenuh = maxX - minX;
        double tinggiPenuh = maxY - minY;
        double skala = Math.min(1.0, batasSisi / Math.max(lebarPenuh, tinggiPenuh));
        int[][] kiriAtas = new int[posisi.size()][2];
        for (int i = 0; i < posisi.size(); i++) {
            kiriAtas[i][0] = (int) Math.round((posisi.get(i)[0] * faktor - minX) * skala);
            kiriAtas[i][1] = (int) Math.round((posisi.get(i)[1] * faktor - minY) * skala);
        }
        return new Tata((int) Math.round(lebarPenuh * skala), (int) Math.round(tinggiPenuh * skala), skala, kiriAtas,
                (int) Math.round(lebarTile * skala), (int) Math.round(tinggiTile * skala));
    }
}
