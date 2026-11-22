package com.example.fai26.sync;

import java.util.List;
import java.util.function.LongSupplier;

/**
 * Persetujuan krani lewat server: mengambil daftar catatan yang menunggu (dikirim mandor dari HP lain), lalu menyetujui
 * atau menolaknya. Memakai token login online perangkat ini dan memperbaruinya sekali bila kedaluwarsa. Butuh jaringan.
 */
public class PersetujuanServer {

    private static final long SISA_TOKEN_MS = 30_000L;

    /** Satu panggilan ke server dengan token akses (dipakai juga oleh FotoProfilSync). */
    interface Aksi<T> {
        T jalankan(String token) throws ApiException;
    }

    private final ServerApi api;
    private final PengaturanSync pref;
    private final LongSupplier jam;

    public PersetujuanServer(ServerApi api, PengaturanSync pref) {
        this(api, pref, System::currentTimeMillis);
    }

    public PersetujuanServer(ServerApi api, PengaturanSync pref, LongSupplier jam) {
        this.api = api;
        this.pref = pref;
        this.jam = jam;
    }

    public List<ServerApi.Menunggu> muat() throws ApiException {
        return dengan(api::daftarMenunggu);
    }

    /** Foto bukti satu catatan (JPEG), untuk diperiksa sebelum menyetujui. */
    public byte[] foto(String fotoId) throws ApiException {
        return dengan(t -> api.unduhFoto(t, fotoId));
    }

    // ---- IPD (persetujuan asisten) ------------------------------------------------------------------

    public List<ServerApi.IpdMenunggu> muatIpd() throws ApiException {
        return dengan(api::daftarIpdMenunggu);
    }

    /** Foto bukti satu inspeksi IPD (JPEG), untuk diperiksa sebelum menyetujui. */
    public byte[] fotoIpd(String fotoId) throws ApiException {
        return dengan(t -> api.unduhFotoIpd(t, fotoId));
    }

    public void setujuiIpd(String ipdId) throws ApiException {
        dengan(t -> {
            api.setujuiIpd(t, ipdId);
            return null;
        });
    }

    public void tolakIpd(String ipdId, String alasan) throws ApiException {
        dengan(t -> {
            api.tolakIpd(t, ipdId, alasan);
            return null;
        });
    }

    /** Peringatan inspeksi ulang (7 hari) dari server; melempar ApiException bila offline (pemanggil memakai data HP). */
    public List<ServerApi.IpdTerakhirItem> inspeksiTerakhir(long hrmb, long asm3Oid, String tanggal, String kecuali) throws ApiException {
        return dengan(t -> api.inspeksiTerakhir(t, hrmb, asm3Oid, tanggal, kecuali));
    }

    public void setujui(String panenId) throws ApiException {
        dengan(t -> {
            api.setujuiPanen(t, panenId);
            return null;
        });
    }

    public void tolak(String panenId, String alasan) throws ApiException {
        dengan(t -> {
            api.tolakPanen(t, panenId, alasan);
            return null;
        });
    }

    <T> T dengan(Aksi<T> aksi) throws ApiException {
        if (!pref.serverSudahDiatur()) throw new ApiException(0, "Alamat server belum diatur");
        if (!pref.adaToken()) throw new ApiException(401, "Belum login online untuk akun ini. Keluar, lalu login lagi saat ada sinyal");
        String token = tokenValid();
        try {
            return aksi.jalankan(token);
        } catch (ApiException e) {
            if (!e.tidakLogin()) throw e;
            return aksi.jalankan(perbaruiToken()); // token ditolak (kedaluwarsa): perbarui sekali lalu ulangi
        }
    }

    private String tokenValid() throws ApiException {
        String akses = pref.akses();
        if (akses != null && pref.aksesKedaluwarsa() > jam.getAsLong() + SISA_TOKEN_MS) return akses;
        return perbaruiToken();
    }

    private String perbaruiToken() throws ApiException {
        String refresh = pref.refresh();
        if (refresh == null) throw new ApiException(401, "Belum login online");
        try {
            ServerApi.Token t = api.refresh(refresh);
            pref.simpanToken(pref.tokenUser(), t);
            return t.akses;
        } catch (ApiException e) {
            if (e.tidakLogin()) pref.hapusToken();
            throw e;
        }
    }
}
