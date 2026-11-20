package com.example.fai26.ipd;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ringkasan loses satu inspeksi, dihitung dari pokok yang sudah dicatat. Rumusnya sama dengan laporan server
 * (fai26-api): persen = pokok dibagi pokok diperiksa. Kelas murni (tanpa Android) supaya mudah diuji.
 */
public final class IpdRekap {

    public int pokokDiperiksa;
    public int pokokBertemuan;
    public int totalTemuan;
    /** kode -> jumlah pokok yang memuat kode itu. */
    public final Map<String, Integer> pokokPerKode = new LinkedHashMap<>();
    /** kode -> jumlah temuan. */
    public final Map<String, Integer> jumlahPerKode = new LinkedHashMap<>();

    public static IpdRekap hitung(List<IpdDraf.Pokok> daftar) {
        IpdRekap r = new IpdRekap();
        for (IpdDraf.Pokok p : daftar) {
            r.pokokDiperiksa++;
            if (!p.temuan.isEmpty()) r.pokokBertemuan++;
            for (Map.Entry<String, Integer> e : p.temuan.entrySet()) {
                r.totalTemuan += e.getValue();
                r.pokokPerKode.merge(e.getKey(), 1, Integer::sum);
                r.jumlahPerKode.merge(e.getKey(), e.getValue(), Integer::sum);
            }
        }
        return r;
    }

    /** Persen satu desimal (0 bila belum ada pokok). */
    public static double persen(int bagian, int total) {
        if (total <= 0) return 0;
        return Math.round(bagian * 1000.0 / total) / 10.0;
    }

    public double persenBertemuan() {
        return persen(pokokBertemuan, pokokDiperiksa);
    }

    public double persenKode(String kode) {
        return persen(pokokPerKode.getOrDefault(kode, 0), pokokDiperiksa);
    }
}
