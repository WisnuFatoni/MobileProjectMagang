package com.example.fai26.auth;

import com.example.fai26.sync.ApiException;
import com.example.fai26.sync.PengaturanSync;
import com.example.fai26.sync.ServerApi;

import java.util.function.LongSupplier;

/**
 * Ganti password sendiri dari HP. Password hanya bisa diganti saat online: server yang memeriksa password lama, lalu
 * verifier offline di HP ikut diperbarui. HP tidak pernah mengganti password offline (server akan tidak sama dengan HP).
 */
public class GantiPasswordService {

    public static final int PANJANG_MIN = 8;
    public static final int PANJANG_MAKS = 72;
    private static final long SISA_TOKEN_MS = 30_000L;

    public static final class Hasil {
        public final boolean berhasil;
        public final String pesan;

        public Hasil(boolean berhasil, String pesan) {
            this.berhasil = berhasil;
            this.pesan = pesan;
        }
    }

    private final AuthService auth;
    private final ServerApi api; // null bila alamat server belum diatur
    private final PengaturanSync pref;
    private final LongSupplier jam;

    public GantiPasswordService(AuthService auth, ServerApi api, PengaturanSync pref) {
        this(auth, api, pref, System::currentTimeMillis);
    }

    public GantiPasswordService(AuthService auth, ServerApi api, PengaturanSync pref, LongSupplier jam) {
        this.auth = auth;
        this.api = api;
        this.pref = pref;
        this.jam = jam;
    }

    /** Aturan isian (sama dengan server). @return pesan galat untuk pengguna, atau null bila sah. */
    public static String periksa(String lama, String baru, String ulangi) {
        if (lama == null || lama.isEmpty()) return "Isi password sekarang";
        if (baru == null || baru.length() < PANJANG_MIN) return "Password baru minimal " + PANJANG_MIN + " karakter";
        if (baru.length() > PANJANG_MAKS) return "Password baru maksimal " + PANJANG_MAKS + " karakter";
        if (baru.equals(lama)) return "Password baru harus berbeda dari password sekarang";
        if (!baru.equals(ulangi)) return "Ulangan password baru tidak sama";
        return null;
    }

    /** Blocking: panggil dari thread latar. */
    public Hasil ganti(String lama, String baru, String ulangi) {
        String salah = periksa(lama, baru, ulangi);
        if (salah != null) return new Hasil(false, salah);
        final String username = pref.tokenUser();
        if (api == null || !pref.serverSudahDiatur()) {
            return new Hasil(false, "Alamat server belum diatur. Password hanya bisa diganti saat online.");
        }
        if (username == null || !pref.adaToken()) {
            return new Hasil(false, "Login online dulu (saat ada sinyal), lalu ganti password.");
        }
        try {
            try {
                api.gantiPassword(tokenValid(username), lama, baru);
            } catch (ApiException e) {
                if (!e.tidakLogin()) throw e;
                api.gantiPassword(perbaruiToken(username), lama, baru); // token ditolak: perbarui sekali lalu ulangi
            }
        } catch (ApiException e) {
            if (e.tidakLogin()) {
                pref.hapusToken();
                return new Hasil(false, "Sesi server tidak berlaku. Login online ulang.");
            }
            if (e.kode == 0) return new Hasil(false, "Tidak tersambung ke server. Password hanya bisa diganti saat online.");
            if (e.kode == 429) return new Hasil(false, "Terlalu banyak percobaan. Tunggu sebentar lalu coba lagi.");
            return new Hasil(false, e.getMessage());
        }
        // Server sudah menerima. Bila verifier lokal gagal diperbarui, login online berikutnya yang memperbaikinya.
        boolean lokal = auth.ubahPasswordSetelahServer(username, baru);
        return new Hasil(true, lokal
                ? "Password diganti. HP lain perlu login ulang."
                : "Password diganti di server. Login online ulang untuk memperbarui HP ini.");
    }

    private String tokenValid(String username) throws ApiException {
        String akses = pref.akses();
        if (akses != null && pref.aksesKedaluwarsa() > jam.getAsLong() + SISA_TOKEN_MS) return akses;
        return perbaruiToken(username);
    }

    private String perbaruiToken(String username) throws ApiException {
        String refresh = pref.refresh();
        if (refresh == null) throw new ApiException(401, "Belum login online");
        try {
            ServerApi.Token t = api.refresh(refresh);
            pref.simpanToken(username, t);
            return t.akses;
        } catch (ApiException e) {
            if (e.tidakLogin()) pref.hapusToken();
            throw e;
        }
    }
}
