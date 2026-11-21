package com.example.fai26.sync;

import android.content.Context;
import android.util.Log;

import com.example.fai26.auth.FotoProfilLokal;
import com.example.fai26.auth.Sesi;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Sinkronisasi foto profil akun yang sedang login: mengirim foto baru (atau penghapusan) yang tertunda, atau, bila tidak ada yang
 * tertunda, menyamakan HP dengan versi di server (mis. foto diganti dari web). Tidak pernah melempar galat: gagal berarti dicoba
 * lagi pada sinkronisasi berikutnya dan tidak mengganggu sinkronisasi lain.
 */
public final class FotoProfilSync {

    private static final String TAG = "FotoProfilSync";

    private FotoProfilSync() {
    }

    public static void jalankan(Context context, HttpServerApi api, PengaturanSync pref) {
        final Context app = context.getApplicationContext();
        try {
            long uid = Sesi.userId(app);
            if (uid <= 0 || !pref.serverSudahDiatur() || !pref.adaToken()) return;
            PersetujuanServer ps = new PersetujuanServer(api, pref);
            String tunda = FotoProfilLokal.tunda(app, uid);

            if (FotoProfilLokal.TUNDA_UNGGAH.equals(tunda)) {
                final byte[] isi = baca(FotoProfilLokal.berkas(app, uid));
                long versi = ps.dengan(t -> api.unggahFotoProfil(t, isi));
                FotoProfilLokal.setelahTerkirim(app, uid, versi);
                return;
            }
            if (FotoProfilLokal.TUNDA_HAPUS.equals(tunda)) {
                ps.dengan(t -> {
                    api.hapusFotoProfil(t);
                    return null;
                });
                FotoProfilLokal.setelahDihapusDiServer(app, uid);
                return;
            }

            JSONObject saya = ps.dengan(api::profilSaya);
            if (saya.isNull("foto_versi")) {
                if (FotoProfilLokal.ada(app, uid)) FotoProfilLokal.hapusSalinanLokal(app, uid);
                return;
            }
            long versiServer = saya.getLong("foto_versi");
            if (versiServer == FotoProfilLokal.versi(app, uid) && FotoProfilLokal.ada(app, uid)) return;
            byte[] jpeg = ps.dengan(api::unduhFotoProfil);
            FotoProfilLokal.simpanDariServer(app, uid, jpeg, versiServer);
        } catch (Exception e) {
            Log.w(TAG, "Sinkron foto profil ditunda: " + e.getMessage());
        }
    }

    private static byte[] baca(File f) throws IOException {
        byte[] b = new byte[(int) f.length()];
        try (FileInputStream in = new FileInputStream(f)) {
            int n = 0;
            while (n < b.length) {
                int r = in.read(b, n, b.length - n);
                if (r < 0) throw new IOException("Berkas foto terpotong");
                n += r;
            }
        }
        return b;
    }
}
