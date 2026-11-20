package com.example.fai26.db.dao;

/** POJO hasil query modul IPD (bukan tabel). Nama kolom query harus sama dengan nama field. */
public final class IpdHasil {

    private IpdHasil() {
    }

    /** Satu baris Riwayat IPD. */
    public static class Riwayat {
        public String ipdId;
        public String tanggalInspeksi;
        public String status;
        public String pesanServer;
        public String blok;
        public String pemanen;
        public String divisi;
        public String asisten;
        public long pokok;
        /** Pokok dengan temuan loses (buah atau brondolan) dan dengan temuan lain (pelepah). */
        public long loses;
        public long lain;
        public String dibuatPada;
        public long userId;
    }

    /** Rekap beberapa hari terakhir milik pengguna (untuk Beranda IPD). */
    public static class RekapPekan {
        public long inspeksi;
        public long pokok;
        public long pokokLoses;
        public long pokokLain;
    }

    /** Hitungan satu kode temuan pada rekap beberapa hari terakhir. */
    public static class KodePekan {
        public String kode;
        public String nama;
        public String kelompok;
        public long pokok;
        public long jumlah;
    }

    public static class DivisiPilihan {
        public long divisiId;
        public String kode;
        public String nama;
        public Long asistenUserId;
        public String asistenNama;

        @Override
        public String toString() {
            return nama;
        }
    }

    public static class BlokPilihan {
        public long blokId;
        public String kode;
        public long erpOid;
        public Long divisiId;

        @Override
        public String toString() {
            return "Blok " + kode;
        }
    }

    /** Akun yang bisa dipilih sebagai mandor (atau asisten). */
    public static class OrangPilihan {
        public long userId;
        public String nama;
        public Long hrmbCode;

        @Override
        public String toString() {
            return nama;
        }
    }

    public static class PemanenPilihan {
        public long pemanenId;
        public String nama;
        public long hrmbCode;

        @Override
        public String toString() {
            return nama;
        }
    }

    /** Satu baris pokok beserta satu temuannya (kosong bila pokok bersih), untuk memuat ulang inspeksi. */
    public static class PokokBaris {
        public long noBaris;
        public long noPokok;
        public String kode;
        public Long jumlah;
    }

    /** Kepala catatan IPD untuk dikirim (lihat fai26-api/docs/API.md, POST /api/ipd). */
    public static class HeaderKirim {
        public String ipdId;
        public String unitKode;
        public String tanggalInspeksi;
        public String divisiKode;
        public long asm3Oid;
        public String blokKode;
        public long pemanenHrmb;
        public String ancakNo;
        public String mandorUsername;
        public String catatan;
        public String dibuatPada;
        public long kirimUlang;
        public long userId;
    }

    public static class PokokKirim {
        public long pokokId;
        public long noBaris;
        public long noPokok;
    }

    public static class TemuanKirim {
        public long pokokId;
        public String kode;
        public long jumlah;
        public String catatan;
    }

    public static class FotoKirim {
        public String fotoId;
        public String ipdId;
        public String keterangan;
        public long urut;
        public String pathLokal;
        public String sha256;
    }

    /** Inspeksi sebelumnya pada pemanen + blok yang sama, dari data HP (cadangan peringatan inspeksi ulang saat offline). */
    public static class Terakhir {
        public String ipdId;
        public String tanggalInspeksi;
        public String inspektur;
        public String status;
    }
}
