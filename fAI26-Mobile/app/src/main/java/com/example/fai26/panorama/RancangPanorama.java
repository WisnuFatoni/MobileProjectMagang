package com.example.fai26.panorama;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Menentukan kapan sebuah frame disimpan sebagai potongan panorama, dari pergeseran antar frame yang ditaksir
 * {@link PenaksirGeser}. Posisi dihitung terhadap frame pertama (0, 0) dalam piksel resolusi taksiran.
 *
 * Frame pembanding (ref) hanya diganti bila sudah bergeser cukup jauh, supaya gerakan lambat yang kurang dari
 * satu piksel per frame tetap terkumpul dan tidak hilang karena pembulatan.
 */
public final class RancangPanorama {

    /** Potongan baru diambil bila posisi sudah bergeser sebesar bagian ini dari lebar atau tinggi frame. */
    public static final double AMBANG_TILE = 0.35;
    /** Ambang lebih kecil untuk frame penutup, supaya ujung sisiran ikut masuk. */
    public static final double AMBANG_AKHIR = 0.08;
    /** Batas potongan per panorama: menjaga ukuran gambar dan beban HP. */
    public static final int MAKS_TILE = 24;
    /** Frame berturut-turut yang boleh gagal disejajarkan sebelum panorama dibekukan. */
    public static final int MAKS_GAGAL_BERTURUT = 12;
    /** Ref diganti bila geseran terhadap ref sudah sebesar ini (piksel). */
    static final int GESER_MIN_GANTI_REF = 3;

    public static final class Keputusan {
        public final boolean ambilTile;
        public final boolean jadikanRef;

        Keputusan(boolean ambilTile, boolean jadikanRef) {
            this.ambilTile = ambilTile;
            this.jadikanRef = jadikanRef;
        }
    }

    private static final Keputusan TIDAK = new Keputusan(false, false);

    private final int lebar;
    private final int tinggi;
    private final List<int[]> tile = new ArrayList<>();
    private int refX;
    private int refY;
    private int gagalBerturut;
    private boolean terbeku;

    /** @param lebar,tinggi ukuran frame pada resolusi taksiran */
    public RancangPanorama(int lebar, int tinggi) {
        this.lebar = lebar;
        this.tinggi = tinggi;
    }

    /** Frame pertama: selalu menjadi potongan pertama di (0, 0) dan frame pembanding. */
    public void mulai() {
        tile.clear();
        tile.add(new int[]{0, 0});
        refX = 0;
        refY = 0;
        gagalBerturut = 0;
        terbeku = false;
    }

    /**
     * Frame berikutnya, dibandingkan dengan frame pembanding.
     *
     * @param dx,dy geseran isi dari ref ke frame ini (lihat {@link PenaksirGeser})
     * @param sah   false bila penyejajaran gagal
     * @param ambang bagian lebar/tinggi frame yang harus ditempuh sejak potongan terakhir
     */
    public Keputusan terima(int dx, int dy, boolean sah, double ambang) {
        if (terbeku || tile.isEmpty()) return TIDAK;
        if (!sah) {
            if (++gagalBerturut > MAKS_GAGAL_BERTURUT) terbeku = true;
            return TIDAK;
        }
        gagalBerturut = 0;
        // Titik asal frame ini pada koordinat panorama: posisi ref dikurangi geseran isi.
        int x = refX - dx;
        int y = refY - dy;
        int[] terakhir = tile.get(tile.size() - 1);
        boolean cukupJauh = Math.abs(x - terakhir[0]) >= ambang * lebar || Math.abs(y - terakhir[1]) >= ambang * tinggi;
        boolean ambil = cukupJauh && tile.size() < MAKS_TILE;
        if (ambil) tile.add(new int[]{x, y});
        boolean gantiRef = ambil || Math.abs(dx) >= GESER_MIN_GANTI_REF || Math.abs(dy) >= GESER_MIN_GANTI_REF;
        if (gantiRef) {
            refX = x;
            refY = y;
        }
        return new Keputusan(ambil, gantiRef);
    }

    public boolean terbeku() {
        return terbeku;
    }

    public int jumlahTile() {
        return tile.size();
    }

    /** Posisi kiri-atas tiap potongan (resolusi taksiran), berurutan menurut waktu. */
    public List<int[]> posisiTile() {
        List<int[]> salinan = new ArrayList<>();
        for (int[] p : tile) salinan.add(p.clone());
        return Collections.unmodifiableList(salinan);
    }
}
