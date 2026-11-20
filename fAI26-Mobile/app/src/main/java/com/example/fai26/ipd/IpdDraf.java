package com.example.fai26.ipd;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Isi satu inspeksi IPD yang sedang diisi di HP (belum tentu tersimpan). Satu inspeksi = satu pemanen (satu ancak). */
public final class IpdDraf {

    /** Satu pokok yang diperiksa. Temuan kosong berarti pokok bersih. */
    public static final class Pokok {
        public int baris;
        public int pokok;
        /** kode temuan -> jumlah (urutan pilih dipertahankan). */
        public final Map<String, Integer> temuan = new LinkedHashMap<>();

        public Pokok(int baris, int pokok) {
            this.baris = baris;
            this.pokok = pokok;
        }
    }

    public String ipdId;
    public String tanggal;
    public long divisiId;
    public long blokId;
    public long pemanenId;
    public String ancakNo;
    public Long mandorBlokUserId;
    public Long mandorAncakUserId;
    public String catatan;
    /** Status tersimpan (null bila inspeksi baru). */
    public String statusLama;
    public final List<Pokok> pokok = new ArrayList<>();

    /** Inspeksi yang sudah pernah terkirim ke server dan sekarang diubah: dikirim ulang dengan kirim_ulang. */
    public boolean sudahTerkirim() {
        return "MENUNGGU_SETUJU".equals(statusLama) || "DITOLAK".equals(statusLama);
    }
}
