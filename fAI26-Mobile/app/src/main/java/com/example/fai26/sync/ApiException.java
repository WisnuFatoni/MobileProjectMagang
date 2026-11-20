package com.example.fai26.sync;

/** Galat saat memanggil server. kode 0 berarti tidak sampai ke server (jaringan putus, waktu habis, alamat salah). */
public class ApiException extends Exception {

    public final int kode;

    public ApiException(int kode, String pesan, Throwable sebab) {
        super(pesan, sebab);
        this.kode = kode;
    }

    public ApiException(int kode, String pesan) {
        this(kode, pesan, null);
    }

    /**
     * Layak dicoba lagi nanti: tidak sampai ke server, server sedang bermasalah (5xx), waktu permintaan habis (408),
     * atau kena batas laju (429, server meminta menunggu sebentar). Penolakan lain berarti isinya yang salah.
     */
    public boolean bisaDicobaLagi() {
        return kode == 0 || kode >= 500 || kode == 408 || kode == 429;
    }

    /** Token tidak ada atau tidak berlaku. */
    public boolean tidakLogin() {
        return kode == 401;
    }
}
