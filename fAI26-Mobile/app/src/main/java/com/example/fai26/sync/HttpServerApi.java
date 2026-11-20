package com.example.fai26.sync;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Implementasi ServerApi lewat HttpURLConnection (tanpa pustaka tambahan). */
public class HttpServerApi implements ServerApi {

    private static final int WAKTU_SAMBUNG_MS = 8_000;
    private static final int WAKTU_BACA_MS = 30_000;
    private static final int MAKS_FOTO_BYTE = 8 * 1024 * 1024;

    private final String baseUrl;

    public HttpServerApi(String baseUrl) {
        String b = baseUrl == null ? "" : baseUrl.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        this.baseUrl = b;
    }

    // ---- ServerApi -------------------------------------------------------------------------------

    @Override
    public LoginHasil login(String username, String password, String deviceId, String deviceNama, String osVersi,
                            String appVersi) throws ApiException {
        try {
            JSONObject badan = new JSONObject()
                    .put("username", username).put("password", password).put("device_id", deviceId)
                    .put("device_nama", deviceNama).put("os_versi", osVersi).put("app_versi", appVersi);
            JSONObject j = new JSONObject(panggil("POST", "/api/auth/login", null, "application/json",
                    badan.toString().getBytes(StandardCharsets.UTF_8), null).isi);
            JSONObject profil = j.getJSONObject("profil");
            return new LoginHasil(bacaToken(j), profil.getString("username"), profil.optString("nama", ""),
                    profil.optString("unit_kode", ""));
        } catch (JSONException e) {
            throw new ApiException(0, "Jawaban server tidak dikenali", e);
        }
    }

    @Override
    public Token refresh(String refreshToken) throws ApiException {
        try {
            JSONObject badan = new JSONObject().put("refresh_token", refreshToken);
            JSONObject j = new JSONObject(panggil("POST", "/api/auth/refresh", null, "application/json",
                    badan.toString().getBytes(StandardCharsets.UTF_8), null).isi);
            return bacaToken(j);
        } catch (JSONException e) {
            throw new ApiException(0, "Jawaban server tidak dikenali", e);
        }
    }

    @Override
    public String kirimPanen(String tokenAkses, JSONObject panen) throws ApiException {
        Jawaban r = panggil("POST", "/api/panen", tokenAkses, "application/json",
                panen.toString().getBytes(StandardCharsets.UTF_8), null);
        return statusDari(r.isi);
    }

    @Override
    public String kirimFoto(String tokenAkses, String panenId, String fotoId, String sha256, String jenis,
                            File berkas) throws ApiException {
        byte[] isi;
        try {
            if (berkas.length() > MAKS_FOTO_BYTE) throw new ApiException(413, "Foto lebih dari 8 MB");
            isi = bacaSemua(berkas);
        } catch (IOException e) {
            throw new ApiException(422, "Berkas foto tidak terbaca", e);
        }
        String[][] header = {{"X-Foto-Id", fotoId}, {"X-Sha256", sha256}};
        Jawaban r = panggil("POST", "/api/panen/" + panenId + "/media?jenis=" + jenis, tokenAkses, "image/jpeg",
                isi, header);
        return statusDari(r.isi);
    }

    @Override
    public int kirimAudit(String tokenAkses, JSONArray baris) throws ApiException {
        Jawaban r = panggil("POST", "/api/audit/batch", tokenAkses, "application/json",
                baris.toString().getBytes(StandardCharsets.UTF_8), null);
        try {
            return new JSONObject(r.isi).getInt("diterima");
        } catch (JSONException e) {
            throw new ApiException(0, "Jawaban server tidak dikenali", e);
        }
    }

    @Override
    public List<StatusItem> statusSejak(String tokenAkses, long sejakMillis) throws ApiException {
        Jawaban r = panggil("GET", "/api/panen?sejak=" + iso(sejakMillis), tokenAkses, null, null, null);
        try {
            JSONArray a = new JSONArray(r.isi);
            List<StatusItem> out = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new StatusItem(o.getString("id"), o.getString("status"),
                        o.isNull("pesan") ? null : o.optString("pesan", null), parseIso(o.getString("diubah"))));
            }
            return out;
        } catch (JSONException | ParseException e) {
            throw new ApiException(0, "Jawaban server tidak dikenali", e);
        }
    }

    @Override
    public MasterPaket master(String tokenAkses, String versiLokal) throws ApiException {
        String q = versiLokal == null || versiLokal.isEmpty() ? "" : "?versi=" + java.net.URLEncoder.encode(versiLokal);
        Jawaban r = panggil("GET", "/api/master" + q, tokenAkses, null, null, null);
        try {
            JSONObject j = new JSONObject(r.isi);
            boolean berubah = j.optBoolean("berubah", false);
            return new MasterPaket(j.getString("versi"), berubah, berubah ? j : null);
        } catch (JSONException e) {
            throw new ApiException(0, "Jawaban server tidak dikenali", e);
        }
    }

    @Override
    public KoordinatHasil kirimKoordinatTph(String tokenAkses, long tphId, double lat, double lon, double akurasiM)
            throws ApiException {
        try {
            JSONObject badan = new JSONObject().put("lat", lat).put("lon", lon).put("akurasi_m", akurasiM);
            Jawaban r = panggil("PUT", "/api/tph/" + tphId + "/koordinat", tokenAkses, "application/json",
                    badan.toString().getBytes(StandardCharsets.UTF_8), null);
            JSONObject j = new JSONObject(r.isi);
            return new KoordinatHasil(j.getBoolean("diterapkan"), j.getDouble("lat"), j.getDouble("lon"));
        } catch (JSONException e) {
            throw new ApiException(0, "Jawaban server tidak dikenali", e);
        }
    }

    @Override
    public void gantiPassword(String tokenAkses, String passwordLama, String passwordBaru) throws ApiException {
        try {
            JSONObject badan = new JSONObject().put("password_lama", passwordLama).put("password_baru", passwordBaru);
            panggil("POST", "/api/auth/password", tokenAkses, "application/json",
                    badan.toString().getBytes(StandardCharsets.UTF_8), null);
        } catch (JSONException e) {
            throw new ApiException(0, "Isi permintaan tidak bisa dibentuk", e);
        }
    }

    @Override
    public JSONObject ringkasanPanen(String tokenAkses, String tanggal) throws ApiException {
        String q = (tanggal == null || tanggal.isEmpty()) ? "" : "?tanggal=" + java.net.URLEncoder.encode(tanggal);
        Jawaban r = panggil("GET", "/api/panen/ringkasan" + q, tokenAkses, null, null, null);
        try {
            return new JSONObject(r.isi);
        } catch (JSONException e) {
            throw new ApiException(0, "Jawaban server tidak dikenali", e);
        }
    }

    @Override
    public List<Menunggu> daftarMenunggu(String tokenAkses) throws ApiException {
        Jawaban r = panggil("GET", "/api/panen/menunggu", tokenAkses, null, null, null);
        try {
            JSONArray a = new JSONArray(r.isi);
            List<Menunggu> out = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Menunggu m = new Menunggu();
                m.id = o.getString("id");
                m.tanggalPanen = o.optString("tanggal_panen", "");
                m.blokKode = o.isNull("blok_kode") ? null : o.optString("blok_kode", null);
                m.tphKode = o.isNull("tph_kode") ? null : o.optString("tph_kode", null);
                m.pencatatNama = o.optString("pencatat_nama", o.optString("pencatat", ""));
                m.pemanenNama = o.isNull("pemanen_nama") ? null : o.optString("pemanen_nama", null);
                m.totalJanjang = o.optLong("total_janjang", 0);
                m.brondolanKg = o.optDouble("brondolan_kg", 0);
                m.catatan = o.isNull("catatan") ? null : o.optString("catatan", null);
                m.statusLokasi = o.optString("status_lokasi", "TANPA_GPS");
                m.jarakKeTphM = o.isNull("jarak_ke_tph_m") ? null : o.optDouble("jarak_ke_tph_m");
                m.gpsMock = o.optBoolean("gps_mock", false);
                m.dibuatMillis = parseIso(o.getString("dibuat_pada"));
                m.fotoId = o.isNull("foto_id") ? null : o.optString("foto_id", null);
                JSONArray g = o.optJSONArray("grading");
                if (g != null) {
                    for (int k = 0; k < g.length(); k++) {
                        JSONObject x = g.getJSONObject(k);
                        m.grading.put(x.getString("kelas_kode"), x.getLong("janjang"));
                    }
                }
                out.add(m);
            }
            return out;
        } catch (JSONException | ParseException e) {
            throw new ApiException(0, "Jawaban server tidak dikenali", e);
        }
    }

    /** Batas ukuran foto yang diunduh untuk diperiksa krani (foto panorama dikompres, jauh di bawah ini). */
    private static final int BATAS_FOTO_BYTE = 8 * 1024 * 1024;

    @Override
    public byte[] unduhFoto(String tokenAkses, String fotoId) throws ApiException {
        return unduhBerkas(tokenAkses, "/api/admin/foto/", fotoId);
    }

    @Override
    public byte[] unduhFotoIpd(String tokenAkses, String fotoId) throws ApiException {
        return unduhBerkas(tokenAkses, "/api/ipd/foto/", fotoId);
    }

    private byte[] unduhBerkas(String tokenAkses, String awalan, String fotoId) throws ApiException {
        if (baseUrl.isEmpty()) throw new ApiException(0, "Alamat server belum diatur");
        if (fotoId == null || !fotoId.matches("[0-9a-fA-F-]{36}")) throw new ApiException(422, "Id foto tidak sah");
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(baseUrl + awalan + fotoId).openConnection();
            c.setConnectTimeout(WAKTU_SAMBUNG_MS);
            c.setReadTimeout(WAKTU_BACA_MS);
            c.setRequestMethod("GET");
            if (tokenAkses != null) c.setRequestProperty("Authorization", "Bearer " + tokenAkses);
            int kode = c.getResponseCode();
            if (kode >= 400) {
                InputStream err = c.getErrorStream();
                throw new ApiException(kode, pesanGalat(err == null ? "" : bacaTeks(err), kode));
            }
            java.io.ByteArrayOutputStream hasil = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            try (InputStream in = c.getInputStream()) {
                int n;
                while ((n = in.read(buf)) > 0) {
                    hasil.write(buf, 0, n);
                    if (hasil.size() > BATAS_FOTO_BYTE) throw new ApiException(0, "Foto terlalu besar");
                }
            }
            return hasil.toByteArray();
        } catch (IOException e) {
            throw new ApiException(0, "Tidak tersambung ke server", e);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    // ---- IPD (Inspeksi Panen Detail) -----------------------------------------------------------------

    @Override
    public String kirimIpd(String tokenAkses, JSONObject ipd) throws ApiException {
        Jawaban r = panggil("POST", "/api/ipd", tokenAkses, "application/json", ipd.toString().getBytes(StandardCharsets.UTF_8), null);
        return statusDari(r.isi);
    }

    @Override
    public String kirimFotoIpd(String tokenAkses, String ipdId, String fotoId, String sha256, String keterangan, int urut,
                               File berkas) throws ApiException {
        byte[] isi;
        try {
            if (berkas.length() > MAKS_FOTO_BYTE) throw new ApiException(413, "Foto lebih dari 8 MB");
            isi = bacaSemua(berkas);
        } catch (IOException e) {
            throw new ApiException(422, "Berkas foto tidak terbaca", e);
        }
        String q = "?urut=" + urut + (keterangan == null || keterangan.isEmpty() ? "" : "&keterangan=" + java.net.URLEncoder.encode(keterangan));
        String[][] header = {{"X-Foto-Id", fotoId}, {"X-Sha256", sha256}};
        Jawaban r = panggil("POST", "/api/ipd/" + ipdId + "/media" + q, tokenAkses, "image/jpeg", isi, header);
        return statusDari(r.isi);
    }

    @Override
    public List<StatusItem> statusIpdSejak(String tokenAkses, long sejakMillis) throws ApiException {
        Jawaban r = panggil("GET", "/api/ipd?sejak=" + iso(sejakMillis), tokenAkses, null, null, null);
        try {
            JSONArray a = new JSONArray(r.isi);
            List<StatusItem> out = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new StatusItem(o.getString("id"), o.getString("status"),
                        o.isNull("pesan") ? null : o.optString("pesan", null), parseIso(o.getString("diubah"))));
            }
            return out;
        } catch (JSONException | ParseException e) {
            throw new ApiException(0, "Jawaban server tidak dikenali", e);
        }
    }

    @Override
    public List<IpdMenunggu> daftarIpdMenunggu(String tokenAkses) throws ApiException {
        Jawaban r = panggil("GET", "/api/ipd/menunggu", tokenAkses, null, null, null);
        try {
            JSONArray a = new JSONArray(r.isi);
            List<IpdMenunggu> out = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                IpdMenunggu m = new IpdMenunggu();
                m.id = o.getString("id");
                m.tanggalInspeksi = o.optString("tanggal_inspeksi", "");
                m.divisiKode = o.optString("divisi_kode", "");
                m.blokKode = o.isNull("blok_kode") ? null : o.optString("blok_kode", null);
                m.pemanenNama = o.isNull("pemanen_nama") ? null : o.optString("pemanen_nama", null);
                m.ancakNo = o.isNull("ancak_no") ? null : o.optString("ancak_no", null);
                m.inspekturNama = o.optString("inspektur_nama", o.optString("inspektur", ""));
                m.mandorBlokNama = o.isNull("mandor_blok_nama") ? null : o.optString("mandor_blok_nama", null);
                m.mandorAncakNama = o.isNull("mandor_ancak_nama") ? null : o.optString("mandor_ancak_nama", null);
                m.catatan = o.isNull("catatan") ? null : o.optString("catatan", null);
                m.dibuatMillis = parseIso(o.getString("dibuat_pada"));
                JSONObject rg = o.optJSONObject("ringkasan");
                if (rg != null) {
                    m.pokokDiperiksa = rg.optLong("pokok_diperiksa", 0);
                    m.pokokBertemuan = rg.optLong("pokok_bertemuan", 0);
                    m.persenBertemuan = rg.optDouble("persen_pokok_bertemuan", 0);
                    m.totalTemuan = rg.optLong("total_temuan", 0);
                    m.pokokLoses = rg.optLong("pokok_loses", m.pokokBertemuan);
                    m.persenLoses = rg.optDouble("persen_pokok_loses", m.persenBertemuan);
                    m.pokokTemuanLain = rg.optLong("pokok_temuan_lain", 0);
                    m.persenTemuanLain = rg.optDouble("persen_pokok_temuan_lain", 0);
                    JSONArray pk = rg.optJSONArray("per_kode");
                    for (int k = 0; pk != null && k < pk.length(); k++) {
                        JSONObject x = pk.getJSONObject(k);
                        if (x.optLong("jumlah", 0) <= 0) continue;
                        IpdKode kd = new IpdKode();
                        kd.kode = x.getString("kode");
                        kd.nama = x.optString("nama", kd.kode);
                        kd.jenis = x.optString("jenis", "LOSES");
                        kd.pokok = x.optLong("pokok", 0);
                        kd.jumlah = x.optLong("jumlah", 0);
                        kd.persenPokok = x.optDouble("persen_pokok", 0);
                        m.perKode.add(kd);
                    }
                }
                JSONArray ft = o.optJSONArray("foto");
                for (int k = 0; ft != null && k < ft.length(); k++) {
                    JSONObject x = ft.getJSONObject(k);
                    IpdFotoRef f = new IpdFotoRef();
                    f.id = x.getString("id");
                    f.keterangan = x.isNull("keterangan") ? null : x.optString("keterangan", null);
                    m.foto.add(f);
                }
                out.add(m);
            }
            return out;
        } catch (JSONException | ParseException e) {
            throw new ApiException(0, "Jawaban server tidak dikenali", e);
        }
    }

    @Override
    public void setujuiIpd(String tokenAkses, String ipdId) throws ApiException {
        panggil("POST", "/api/ipd/" + ipdId + "/setujui", tokenAkses, null, null, null);
    }

    @Override
    public void tolakIpd(String tokenAkses, String ipdId, String alasan) throws ApiException {
        try {
            JSONObject badan = new JSONObject().put("alasan", alasan);
            panggil("POST", "/api/ipd/" + ipdId + "/tolak", tokenAkses, "application/json",
                    badan.toString().getBytes(StandardCharsets.UTF_8), null);
        } catch (JSONException e) {
            throw new ApiException(0, "Isi permintaan tidak bisa dibentuk", e);
        }
    }

    @Override
    public List<IpdTerakhirItem> inspeksiTerakhir(String tokenAkses, long hrmb, long asm3Oid, String tanggal, String kecuali)
            throws ApiException {
        String q = "?pemanen_hrmb_code=" + hrmb + "&asm3_oid=" + asm3Oid
                + (tanggal == null || tanggal.isEmpty() ? "" : "&tanggal=" + java.net.URLEncoder.encode(tanggal))
                + (kecuali == null || kecuali.isEmpty() ? "" : "&kecuali=" + java.net.URLEncoder.encode(kecuali));
        Jawaban r = panggil("GET", "/api/ipd/inspeksi-terakhir" + q, tokenAkses, null, null, null);
        try {
            JSONArray a = new JSONObject(r.isi).optJSONArray("inspeksi");
            List<IpdTerakhirItem> out = new ArrayList<>();
            for (int i = 0; a != null && i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                IpdTerakhirItem t = new IpdTerakhirItem();
                t.id = o.getString("id");
                t.tanggalInspeksi = o.optString("tanggal_inspeksi", "");
                t.inspekturNama = o.optString("inspektur_nama", o.optString("inspektur", ""));
                t.status = o.optString("status", "");
                out.add(t);
            }
            return out;
        } catch (JSONException e) {
            throw new ApiException(0, "Jawaban server tidak dikenali", e);
        }
    }

    @Override
    public void setujuiPanen(String tokenAkses, String panenId) throws ApiException {
        panggil("POST", "/api/panen/" + panenId + "/setujui", tokenAkses, null, null, null);
    }

    @Override
    public void tolakPanen(String tokenAkses, String panenId, String alasan) throws ApiException {
        try {
            JSONObject badan = new JSONObject().put("alasan", alasan);
            panggil("POST", "/api/panen/" + panenId + "/tolak", tokenAkses, "application/json",
                    badan.toString().getBytes(StandardCharsets.UTF_8), null);
        } catch (JSONException e) {
            throw new ApiException(0, "Isi permintaan tidak bisa dibentuk", e);
        }
    }

    // ---- HTTP ------------------------------------------------------------------------------------

    private static final class Jawaban {
        final int kode;
        final String isi;

        Jawaban(int kode, String isi) {
            this.kode = kode;
            this.isi = isi;
        }
    }

    private Jawaban panggil(String metode, String path, String token, String tipe, byte[] badan, String[][] header)
            throws ApiException {
        if (baseUrl.isEmpty()) throw new ApiException(0, "Alamat server belum diatur");
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(baseUrl + path).openConnection();
            c.setConnectTimeout(WAKTU_SAMBUNG_MS);
            c.setReadTimeout(WAKTU_BACA_MS);
            c.setRequestMethod(metode);
            c.setRequestProperty("Accept", "application/json");
            if (token != null) c.setRequestProperty("Authorization", "Bearer " + token);
            if (header != null) for (String[] h : header) c.setRequestProperty(h[0], h[1]);
            if (badan != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", tipe);
                c.setFixedLengthStreamingMode(badan.length);
                try (OutputStream out = c.getOutputStream()) {
                    out.write(badan);
                }
            }
            int kode = c.getResponseCode();
            InputStream in = kode >= 400 ? c.getErrorStream() : c.getInputStream();
            String isi = in == null ? "" : bacaTeks(in);
            if (kode >= 400) throw new ApiException(kode, pesanGalat(isi, kode));
            return new Jawaban(kode, isi);
        } catch (IOException e) {
            throw new ApiException(0, "Tidak tersambung ke server", e);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String pesanGalat(String isi, int kode) {
        try {
            String p = new JSONObject(isi).optString("error", "");
            if (!p.isEmpty()) return p;
        } catch (JSONException ignored) {
            // isi bukan JSON
        }
        return "Server menjawab " + kode;
    }

    private static String statusDari(String isi) throws ApiException {
        try {
            return new JSONObject(isi).getString("status");
        } catch (JSONException e) {
            throw new ApiException(0, "Jawaban server tidak dikenali", e);
        }
    }

    private static Token bacaToken(JSONObject j) throws JSONException, ApiException {
        try {
            return new Token(j.getString("access_token"), parseIso(j.getString("access_expires")),
                    j.getString("refresh_token"));
        } catch (ParseException e) {
            throw new ApiException(0, "Waktu dari server tidak dikenali", e);
        }
    }

    private static String bacaTeks(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toString("UTF-8");
    }

    private static byte[] bacaSemua(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    // ---- Waktu ISO 8601 (UTC) --------------------------------------------------------------------

    /** Membaca waktu dari server, contoh 2026-11-10T08:15:00.1234567Z; pecahan detik dibulatkan ke bawah ke milidetik. */
    static long parseIso(String s) throws ParseException {
        String dasar = s.length() >= 19 ? s.substring(0, 19) : s;
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date d = f.parse(dasar);
        if (d == null) throw new ParseException(s, 0);
        long ms = 0;
        int titik = s.indexOf('.');
        if (titik == 19) {
            int akhir = titik + 1;
            while (akhir < s.length() && Character.isDigit(s.charAt(akhir))) akhir++;
            String pecahan = (s.substring(titik + 1, akhir) + "000").substring(0, 3);
            ms = Long.parseLong(pecahan);
        }
        return d.getTime() + ms;
    }

    static String iso(long millis) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(millis));
    }
}
