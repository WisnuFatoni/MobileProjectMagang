package com.example.fai26.sistem;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.StatFs;

import java.util.ArrayList;
import java.util.List;

/**
 * Peringatan kondisi HP sebelum scan: baterai hampir habis atau ruang simpan menipis membuat sesi scan terputus di
 * tengah jalan atau catatan dan foto gagal tersimpan. Hanya memperingatkan, tidak memblokir.
 */
public final class KondisiPerangkat {

    /** Di bawah ini (dan tidak sedang diisi daya) baterai dianggap rendah. */
    public static final int BATERAI_RENDAH_PERSEN = 15;
    /** Ruang bebas di bawah ini (MB) dianggap menipis; foto bukti sekitar 0,3 MB dan database kecil. */
    public static final long RUANG_MENIPIS_MB = 300;
    /** Di bawah ini (MB) dianggap hampir penuh. */
    public static final long RUANG_KRITIS_MB = 50;

    private KondisiPerangkat() {
    }

    /**
     * @param persenBaterai 0..100, atau negatif bila tidak diketahui
     * @param mengisi       sedang diisi daya
     * @param bebasByte     ruang penyimpanan bebas, atau negatif bila tidak diketahui
     * @return daftar peringatan untuk pengguna (kosong bila semuanya aman)
     */
    public static List<String> peringatan(int persenBaterai, boolean mengisi, long bebasByte) {
        List<String> p = new ArrayList<>();
        if (persenBaterai >= 0 && persenBaterai <= BATERAI_RENDAH_PERSEN && !mengisi) {
            p.add("Baterai tinggal " + persenBaterai + "%. Isi daya dulu supaya scan tidak terputus.");
        }
        if (bebasByte >= 0) {
            long mb = bebasByte / (1024L * 1024L);
            if (mb < RUANG_KRITIS_MB) {
                p.add("Ruang penyimpanan hampir penuh (" + mb + " MB). Catatan dan foto bisa gagal tersimpan; hapus berkas yang tidak perlu.");
            } else if (mb < RUANG_MENIPIS_MB) {
                p.add("Ruang penyimpanan menipis (" + mb + " MB). Sebaiknya hapus berkas yang tidak perlu.");
            }
        }
        return p;
    }

    /** Membaca kondisi HP sekarang. Gagal membaca berarti tidak ada peringatan (tidak menghalangi kerja). */
    public static List<String> baca(Context context) {
        int persen = -1;
        boolean mengisi = false;
        long bebas = -1;
        try {
            Intent b = context.getApplicationContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (b != null) {
                int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int skala = b.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && skala > 0) persen = Math.round(level * 100f / skala);
                int status = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                mengisi = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
            }
        } catch (RuntimeException ignored) {
            // baterai tidak terbaca
        }
        try {
            StatFs s = new StatFs(context.getFilesDir().getAbsolutePath());
            bebas = s.getAvailableBytes();
        } catch (RuntimeException ignored) {
            // ruang tidak terbaca
        }
        return peringatan(persen, mengisi, bebas);
    }
}
