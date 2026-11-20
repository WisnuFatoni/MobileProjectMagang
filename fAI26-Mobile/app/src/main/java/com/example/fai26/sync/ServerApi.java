package com.example.fai26.sync;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.List;

/**
 * Kontrak ke server fai26-api (lihat fai26-api/docs/API.md). Dipisah sebagai interface supaya mesin
 * sinkronisasi bisa diuji dengan tiruan.
 */
public interface ServerApi {

    final class Token {
        public final String akses;
        public final long aksesKedaluwarsaMillis;
        public final String refresh;

        public Token(String akses, long aksesKedaluwarsaMillis, String refresh) {
            this.akses = akses;
            this.aksesKedaluwarsaMillis = aksesKedaluwarsaMillis;
            this.refresh = refresh;
        }
    }

    final class LoginHasil {
        public final Token token;
        public final String username;
        public final String nama;
        public final String unitKode;

        public LoginHasil(Token token, String username, String nama, String unitKode) {
            this.token = token;
            this.username = username;
            this.nama = nama;
            this.unitKode = unitKode;
        }
    }

    final class StatusItem {
        public final String id;
        /** TERKIRIM, DITERIMA, atau DITOLAK. */
        public final String status;
        public final String pesan;
        public final long diubahMillis;

        public StatusItem(String id, String status, String pesan, long diubahMillis) {
            this.id = id;
            this.status = status;
            this.pesan = pesan;
            this.diubahMillis = diubahMillis;
        }
    }

    /** Jawaban GET /api/master. Bila berubah false, isi null. */
    final class MasterPaket {
        public final String versi;
        public final boolean berubah;
        public final JSONObject isi;

        public MasterPaket(String versi, boolean berubah, JSONObject isi) {
            this.versi = versi;
            this.berubah = berubah;
            this.isi = isi;
        }
    }

    /** Jawaban PUT /api/tph/{id}/koordinat. Bila diterapkan false, lat dan lon adalah titik milik server. */
    final class KoordinatHasil {
        public final boolean diterapkan;
        public final double lat;
        public final double lon;

        public KoordinatHasil(boolean diterapkan, double lat, double lon) {
            this.diterapkan = diterapkan;
            this.lat = lat;
            this.lon = lon;
        }
    }

    /** Satu catatan yang menunggu persetujuan krani (GET /api/panen/menunggu). */
    final class Menunggu {
        public String id;
        public String tanggalPanen;
        public String blokKode;
        public String tphKode;
        public String pencatatNama;
        public String pemanenNama;
        public long totalJanjang;
        public double brondolanKg;
        public String catatan;
        public String statusLokasi;
        public Double jarakKeTphM;
        public boolean gpsMock;
        public long dibuatMillis;
        /** Id foto bukti di server (hasil deteksi bila ada), atau null bila foto belum terkirim. */
        public String fotoId;
        /** Janjang per kode kelas server (ripe, unripe, ...), hanya yang lebih dari nol. */
        public final java.util.Map<String, Long> grading = new java.util.LinkedHashMap<>();
    }

    LoginHasil login(String username, String password, String deviceId, String deviceNama, String osVersi,
                     String appVersi) throws ApiException;

    Token refresh(String refreshToken) throws ApiException;

    /** @return "DITERIMA" (baru) atau "SUDAH_ADA" (sudah pernah diterima). */
    String kirimPanen(String tokenAkses, JSONObject panen) throws ApiException;

    /** @return "DITERIMA" atau "SUDAH_ADA". */
    String kirimFoto(String tokenAkses, String panenId, String fotoId, String sha256, String jenis, File berkas)
            throws ApiException;

    /** @return jumlah baris yang diterima (yang sudah ada dilewati server). */
    int kirimAudit(String tokenAkses, JSONArray baris) throws ApiException;

    List<StatusItem> statusSejak(String tokenAkses, long sejakMillis) throws ApiException;

    /** Master unit pengguna. @param versiLokal versi master yang tersimpan di HP (kosong bila belum punya). */
    MasterPaket master(String tokenAkses, String versiLokal) throws ApiException;

    /**
     * Mengirim titik GPS TPH yang direkam di lokasi. Server hanya mengisi bila TPH belum punya koordinat; bila sudah
     * punya, titik server dikembalikan dan HP wajib mengikutinya.
     */
    KoordinatHasil kirimKoordinatTph(String tokenAkses, long tphId, double lat, double lon, double akurasiM)
            throws ApiException;

    /**
     * Mengganti password akun yang sedang login (POST /api/auth/password). Server mencabut sesi di perangkat lain;
     * sesi perangkat ini tetap berlaku. Password lama yang salah dijawab 422.
     */
    void gantiPassword(String tokenAkses, String passwordLama, String passwordBaru) throws ApiException;

    /**
     * Mengambil ringkasan produksi real-time dari server (GET /api/panen/ringkasan?tanggal=YYYY-MM-DD).
     */
    default JSONObject ringkasanPanen(String tokenAkses, String tanggal) throws ApiException {
        throw new ApiException(0, "Tidak didukung");
    }

    /** Catatan di unit yang menunggu persetujuan (perlu hak panen.setujui). */
    /** Mengunduh foto bukti satu catatan (JPEG) untuk diperiksa krani. */
    default byte[] unduhFoto(String tokenAkses, String fotoId) throws ApiException {
        throw new ApiException(0, "Belum didukung");
    }

    default List<Menunggu> daftarMenunggu(String tokenAkses) throws ApiException {
        throw new ApiException(0, "Tidak didukung");
    }

    /** Menyetujui satu catatan menunggu. 409 bila sudah diproses orang lain. */
    default void setujuiPanen(String tokenAkses, String panenId) throws ApiException {
        throw new ApiException(0, "Tidak didukung");
    }

    /** Menolak satu catatan menunggu dengan alasan (tampil di HP pencatat). */
    default void tolakPanen(String tokenAkses, String panenId, String alasan) throws ApiException {
        throw new ApiException(0, "Tidak didukung");
    }
}
