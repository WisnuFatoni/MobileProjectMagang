package com.example.fai26.auth;

import com.example.fai26.sync.ApiException;
import com.example.fai26.sync.MasterSync;
import com.example.fai26.sync.PengaturanSync;
import com.example.fai26.sync.ServerApi;

/**
 * Alur login: coba server dulu (login online) bila alamat server sudah diatur; bila server tidak terjangkau
 * (jaringan putus atau server bermasalah) jatuh ke login offline.
 *
 * Bila server menolak (password salah, akun terkunci atau tidak aktif), login DITOLAK dan tidak jatuh ke offline:
 * password yang sudah diganti di server tidak boleh tetap berlaku lewat verifier lama di HP.
 */
public class LoginService {

    private final AuthService auth;
    private final ServerApi api; // null bila alamat server belum diatur
    private final PengaturanSync pref;
    private final MasterSync master; // boleh null
    private final String deviceId;
    private final String deviceNama;
    private final String osVersi;
    private final String appVersi;

    public LoginService(AuthService auth, ServerApi api, PengaturanSync pref, MasterSync master, String deviceId,
                        String deviceNama, String osVersi, String appVersi) {
        this.auth = auth;
        this.master = master;
        this.api = api;
        this.pref = pref;
        this.deviceId = deviceId;
        this.deviceNama = deviceNama;
        this.osVersi = osVersi;
        this.appVersi = appVersi;
    }

    /** Hasil login beserta cara masuknya (untuk pesan ke pengguna). */
    public static final class Masuk {
        public final AuthService.Hasil hasil;
        public final boolean online;

        Masuk(AuthService.Hasil hasil, boolean online) {
            this.hasil = hasil;
            this.online = online;
        }
    }

    public Masuk masuk(String username, String password) {
        if (api != null && pref.serverSudahDiatur()) {
            try {
                ServerApi.LoginHasil lh = api.login(username, password, deviceId, deviceNama, osVersi, appVersi);
                // Akun belum ada di HP (HP baru atau akun baru): unduh master dulu, baru terapkan login.
                if (master != null && !auth.akunAda(username)) {
                    try {
                        master.ambil(api, lh.token.akses);
                    } catch (ApiException | RuntimeException e) {
                        // dilaporkan sebagai AKUN_BELUM_ADA di bawah
                    }
                }
                AuthService.Hasil h = auth.terapkanLoginOnline(username, password);
                if (h.status == AuthService.Status.BERHASIL) {
                    pref.simpanToken(username, lh.token);
                    pref.aturAkunAktif(username);
                }
                return new Masuk(h, true);
            } catch (ApiException e) {
                AuthService.Hasil ditolak = dariPenolakan(e);
                if (ditolak != null) {
                    auth.catatDitolakServer(username, e.getMessage());
                    return new Masuk(ditolak, true);
                }
                // jaringan putus atau server bermasalah: lanjut offline
            }
        }
        AuthService.Hasil offline = auth.loginOffline(username, password);
        // Login offline tidak menghasilkan token: token online milik pengguna sebelumnya harus dibuang, bukan dipakai atas nama akun ini.
        if (offline.status == AuthService.Status.BERHASIL) pref.aturAkunAktif(username);
        return new Masuk(offline, false);
    }

    /** Menerjemahkan penolakan server menjadi hasil login, atau null bila bukan penolakan (jaringan/server rusak). */
    static AuthService.Hasil dariPenolakan(ApiException e) {
        switch (e.kode) {
            case 400:
            case 401:
                return new AuthService.Hasil(AuthService.Status.SALAH, 0, null, 0, 0);
            case 403:
                return new AuthService.Hasil(AuthService.Status.TIDAK_AKTIF, 0, null, 0, 0);
            case 423:
                return new AuthService.Hasil(AuthService.Status.TERKUNCI, 0, null, AuthService.KUNCI_MENIT, 0);
            default:
                return null;
        }
    }
}
