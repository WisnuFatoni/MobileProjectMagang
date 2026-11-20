package com.example.fai26.sync;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Server tiruan untuk menguji SyncService dan LoginService tanpa jaringan. */
class FakeServerApi implements ServerApi {

    // Catatan panggilan
    final List<JSONObject> panenDikirim = new ArrayList<>();
    final List<String> fotoDikirim = new ArrayList<>(); // fotoId
    final List<JSONArray> auditDikirim = new ArrayList<>();
    /** Tiap elemen {tphId, lat, lon, akurasiM}. */
    final List<double[]> koordinatDikirim = new ArrayList<>();
    /** Titik yang sudah dimiliki server per tph_id {lat, lon}; server tiruan tidak menimpanya (seperti server asli). */
    final java.util.Map<Long, double[]> koordinatServer = new java.util.HashMap<>();
    final List<String> tokenDipakai = new ArrayList<>();
    int jumlahRefresh = 0;
    int jumlahLogin = 0;

    // Perilaku: galat yang dilempar berurutan (satu per panggilan) sebelum kembali normal.
    final Deque<ApiException> galatPanen = new ArrayDeque<>();
    final Deque<ApiException> galatFoto = new ArrayDeque<>();
    final Deque<ApiException> galatAudit = new ArrayDeque<>();
    final Deque<ApiException> galatKoordinat = new ArrayDeque<>();
    final Deque<ApiException> galatGantiPassword = new ArrayDeque<>();
    /** Tiap elemen {tokenDipakai, passwordLama, passwordBaru}. */
    final List<String[]> gantiPasswordDikirim = new ArrayList<>();
    final Deque<ApiException> galatStatus = new ArrayDeque<>();
    final Deque<ApiException> galatRefresh = new ArrayDeque<>();
    ApiException galatLogin;

    /** Token yang dianggap sah oleh server tiruan. Panggilan dengan token lain dijawab 401. */
    String tokenSah = "AKSES-1";
    int nomorToken = 1;
    long umurTokenMs = 15 * 60_000L;
    long sekarangMillis = 0;

    List<StatusItem> status = new ArrayList<>();
    MasterPaket masterJawab;
    int jumlahMaster = 0;
    String versiMasterDikirim;
    final Deque<ApiException> galatMaster = new ArrayDeque<>();
    String usernameLogin = "krani1";

    // Persetujuan krani lewat server
    List<Menunggu> menunggu = new ArrayList<>();
    final List<String> disetujui = new ArrayList<>();
    /** Tiap elemen {panenId, alasan}. */
    final List<String[]> ditolak = new ArrayList<>();
    final Deque<ApiException> galatMenunggu = new ArrayDeque<>();

    private void periksa(String token) throws ApiException {
        tokenDipakai.add(token);
        if (!tokenSah.equals(token)) throw new ApiException(401, "Token tidak sah atau kedaluwarsa");
    }

    private static void lemparBila(Deque<ApiException> d) throws ApiException {
        ApiException e = d.poll();
        if (e != null) throw e;
    }

    @Override
    public LoginHasil login(String username, String password, String deviceId, String deviceNama, String osVersi,
                            String appVersi) throws ApiException {
        jumlahLogin++;
        if (galatLogin != null) throw galatLogin;
        return new LoginHasil(new Token(tokenSah, sekarangMillis + umurTokenMs, "REFRESH-1"), username, "Nama", "KP_STL");
    }

    @Override
    public Token refresh(String refreshToken) throws ApiException {
        jumlahRefresh++;
        lemparBila(galatRefresh);
        nomorToken++;
        tokenSah = "AKSES-" + nomorToken;
        return new Token(tokenSah, sekarangMillis + umurTokenMs, "REFRESH-" + nomorToken);
    }

    @Override
    public String kirimPanen(String tokenAkses, JSONObject panen) throws ApiException {
        periksa(tokenAkses);
        lemparBila(galatPanen);
        panenDikirim.add(panen);
        return "DITERIMA";
    }

    @Override
    public String kirimFoto(String tokenAkses, String panenId, String fotoId, String sha256, String jenis, File berkas)
            throws ApiException {
        periksa(tokenAkses);
        lemparBila(galatFoto);
        fotoDikirim.add(fotoId);
        return "DITERIMA";
    }

    @Override
    public int kirimAudit(String tokenAkses, JSONArray baris) throws ApiException {
        periksa(tokenAkses);
        lemparBila(galatAudit);
        auditDikirim.add(baris);
        return baris.length();
    }

    @Override
    public MasterPaket master(String tokenAkses, String versiLokal) throws ApiException {
        periksa(tokenAkses);
        lemparBila(galatMaster);
        jumlahMaster++;
        versiMasterDikirim = versiLokal;
        if (masterJawab == null) return new MasterPaket("m-kosong", false, null);
        // Meniru server: versi sama berarti tidak berubah.
        if (versiLokal != null && versiLokal.equals(masterJawab.versi)) return new MasterPaket(masterJawab.versi, false, null);
        return masterJawab;
    }

    @Override
    public List<Menunggu> daftarMenunggu(String tokenAkses) throws ApiException {
        periksa(tokenAkses);
        lemparBila(galatMenunggu);
        return new ArrayList<>(menunggu);
    }

    @Override
    public void setujuiPanen(String tokenAkses, String panenId) throws ApiException {
        periksa(tokenAkses);
        lemparBila(galatMenunggu);
        disetujui.add(panenId);
    }

    @Override
    public void tolakPanen(String tokenAkses, String panenId, String alasan) throws ApiException {
        periksa(tokenAkses);
        lemparBila(galatMenunggu);
        ditolak.add(new String[]{panenId, alasan});
    }

    @Override
    public List<StatusItem> statusSejak(String tokenAkses, long sejakMillis) throws ApiException {
        periksa(tokenAkses);
        lemparBila(galatStatus);
        List<StatusItem> out = new ArrayList<>();
        for (StatusItem s : status) if (s.diubahMillis > sejakMillis) out.add(s);
        return out;
    }

    @Override
    public KoordinatHasil kirimKoordinatTph(String tokenAkses, long tphId, double lat, double lon, double akurasiM)
            throws ApiException {
        periksa(tokenAkses);
        lemparBila(galatKoordinat);
        koordinatDikirim.add(new double[]{tphId, lat, lon, akurasiM});
        double[] ada = koordinatServer.get(tphId);
        if (ada != null) return new KoordinatHasil(false, ada[0], ada[1]);
        koordinatServer.put(tphId, new double[]{lat, lon});
        return new KoordinatHasil(true, lat, lon);
    }

    @Override
    public void gantiPassword(String tokenAkses, String passwordLama, String passwordBaru) throws ApiException {
        periksa(tokenAkses);
        lemparBila(galatGantiPassword);
        gantiPasswordDikirim.add(new String[]{tokenAkses, passwordLama, passwordBaru});
    }
}
