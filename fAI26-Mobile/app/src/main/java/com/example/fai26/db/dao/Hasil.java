package com.example.fai26.db.dao;

/** POJO hasil query (bukan tabel). Nama kolom query harus sama dengan nama field. */
public final class Hasil {

    private Hasil() {
    }

    /** Satu TPH untuk dipilih krani. */
    public static class TphPilihan {
        public long tphId;
        public long blokId;
        public long unitId;
        public String blok;
        public String noTph;
        public String qrKode;

        @Override
        public String toString() {
            return "Blok " + blok + " • TPH " + noTph;
        }
    }

    public static class PemanenPilihan {
        public long pemanenId;
        public String nama;
        public String kode;

        @Override
        public String toString() {
            return nama;
        }
    }

    /** Pemetaan nomor kelas keluaran model ke kelas_id di database. */
    public static class KelasLabel {
        public long kelasId;
        public long label;
    }

    /** Isi satu catatan panen untuk dikirim ke server (lihat fai26-api/docs/API.md, POST /api/panen). */
    public static class PanenKirim {
        public String panenId;
        public String unitKode;
        public String tanggalPanen;
        public long asm3Oid;
        public String blokKode;
        public String tphKode;
        public Long rotasi;
        public Long kondisiCuaca;
        public double brondolanKg;
        public Double gpsLat;
        public Double gpsLon;
        public Double gpsAkurasiM;
        public String gpsWaktu;
        public long gpsMock;
        public Double jarakKeTphM;
        public String statusLokasi;
        public String modelVersi;
        public String catatan;
        public String dibuatPada;
        public String pencatatUsername;
        public String disetujuiUsername;
        public String disetujuiPada;
        /** 1 bila ini pengiriman ulang catatan DITOLAK yang sudah diperbaiki. */
        public long kirimUlang;
    }

    public static class GradingKirim {
        public String kelasKode;
        public String erpKolom;
        public long jmlAi;
        public long jmlFinal;
        public long diubahManual;
    }

    public static class PemanenKirim {
        public long hrmbCode;
        public Long jjgAlokasi;
    }

    public static class FotoKirim {
        public String fotoId;
        public String panenId;
        public String jenis;
        public String pathLokal;
        public String sha256;
    }

    /** Titik koordinat TPH di master (bisa kosong). */
    public static class Koordinat {
        public Double lat;
        public Double lon;
    }

    /** Satu baris riwayat catatan panen. */
    public static class RiwayatItem {
        public String panenId;
        public String dibuatPada;
        public String status;
        public String blok;
        public String noTph;
        public String pemanen;
        public String fotoPath;
        /** Total janjang akhir (setelah koreksi). */
        public long total;
        public String statusLokasi;
        public Double jarakKeTphM;
        /** Alasan penolakan dari server, bila ada. */
        public String pesanServer;
    }

    /** Satu catatan yang menunggu persetujuan mandor. */
    public static class PersetujuanItem {
        public String panenId;
        public String dibuatPada;
        public String blok;
        public String noTph;
        public String krani;
        public String pemanen;
        public double brondolanKg;
        public String catatan;
        public long total;
        public String statusLokasi;
        public Double jarakKeTphM;
        public long gpsMock;
    }

    /** Informasi TPH yang ditampilkan setelah QR terbaca, supaya krani memastikan TPH-nya benar. */
    public static class TphDetail {
        public String unitKode;
        public String unitNama;
        public String divisiNama;
        public String blokKode;
        public String blokNama;
        public Double luasHa;
        public Long tahunTanam;
        public Double sph;
        /** Tanggal panen terakhir pada TPH ini menurut catatan di HP (YYYY-MM-DD), null bila belum ada. */
        public String panenTerakhir;
        public long catatanHariIni;
        public Double lat;
        public Double lon;
    }

    /** Ringkasan satu hari untuk beranda. */
    public static class RingkasanHari {
        public long catatan;
        public long janjang;
    }

    /** Jumlah janjang satu kelas (label model) pada satu hari. */
    public static class KelasJumlah {
        public long label;
        public long jumlah;
    }

    /** Rekap satu blok pada satu hari. */
    public static class BlokRekap {
        public String blok;
        public long jumlahTph;
        public long catatan;
        public long janjang;
    }

    /** Jumlah janjang pada satu tanggal (YYYY-MM-DD). */
    public static class HariJumlah {
        public String tanggal;
        public long janjang;
    }

    /** Catatan lain dengan kombinasi tanggal + TPH + pemanen yang sama (untuk mencegah ganda di HP). */
    public static class KombinasiAda {
        public String panenId;
        public String status;
    }

    /** Satu kelas pada catatan yang sedang diedit: hitungan kamera dan hitungan akhir. */
    public static class GradingEdit {
        public long kelasId;
        public long label;
        public long jmlAi;
        public long jmlFinal;
    }

    /** Jumlah akhir per kelas untuk satu catatan. */
    public static class GradingBaris {
        public String panenId;
        public long label;
        public long jumlah;
    }
}
