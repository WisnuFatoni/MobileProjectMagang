package com.example.fai26.sync;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/** Menguji klien HTTP terhadap server tiruan di JVM (tanpa jaringan sungguhan). */
public class HttpServerApiTest {

    private ServerSocket server;
    private Thread penerima;
    private HttpServerApi api;

    // Permintaan terakhir yang diterima server tiruan.
    private volatile String metode;
    private volatile String pathLengkap;
    private volatile String authorization;
    private volatile String fotoId;
    private volatile String sha;
    private volatile String tipeIsi;
    private volatile byte[] isi;

    // Jawaban yang akan diberikan.
    private volatile int kodeJawab = 200;
    private volatile String badanJawab = "{}";

    @Before
    public void mulai() throws IOException {
        server = new ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"));
        penerima = new Thread(() -> {
            while (!server.isClosed()) {
                try (Socket k = server.accept()) {
                    layani(k);
                } catch (IOException e) {
                    // server ditutup
                }
            }
        });
        penerima.setDaemon(true);
        penerima.start();
        api = new HttpServerApi("http://127.0.0.1:" + server.getLocalPort() + "/");
    }

    /** Membaca satu permintaan HTTP/1.1 sederhana lalu menjawab sesuai kodeJawab dan badanJawab. */
    private void layani(Socket k) throws IOException {
        InputStream in = k.getInputStream();
        ByteArrayOutputStream kepala = new ByteArrayOutputStream();
        int sama = 0;
        int c;
        while (sama < 4 && (c = in.read()) >= 0) {
            kepala.write(c);
            sama = (c == '\r' || c == '\n') ? sama + 1 : 0;
            // urutan \r\n\r\n
            byte[] h = kepala.toByteArray();
            int n = h.length;
            if (n >= 4 && h[n - 4] == '\r' && h[n - 3] == '\n' && h[n - 2] == '\r' && h[n - 1] == '\n') break;
        }
        String[] baris = new String(kepala.toByteArray(), StandardCharsets.ISO_8859_1).split("\r\n");
        String[] awal = baris[0].split(" ");
        metode = awal[0];
        pathLengkap = awal[1];
        authorization = null;
        fotoId = null;
        sha = null;
        tipeIsi = null;
        int panjang = 0;
        for (int i = 1; i < baris.length; i++) {
            int t = baris[i].indexOf(':');
            if (t < 0) continue;
            String nama = baris[i].substring(0, t).trim().toLowerCase();
            String nilai = baris[i].substring(t + 1).trim();
            switch (nama) {
                case "authorization": authorization = nilai; break;
                case "x-foto-id": fotoId = nilai; break;
                case "x-sha256": sha = nilai; break;
                case "content-type": tipeIsi = nilai; break;
                case "content-length": panjang = Integer.parseInt(nilai); break;
                default: break;
            }
        }
        byte[] badan = new byte[panjang];
        int baca = 0;
        while (baca < panjang) {
            int n = in.read(badan, baca, panjang - baca);
            if (n < 0) break;
            baca += n;
        }
        isi = badan;
        byte[] jawab = badanJawab.getBytes(StandardCharsets.UTF_8);
        OutputStream out = k.getOutputStream();
        out.write(("HTTP/1.1 " + kodeJawab + " X\r\nContent-Type: application/json\r\nContent-Length: " + jawab.length
                + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
        out.write(jawab);
        out.flush();
    }

    @After
    public void henti() throws IOException {
        server.close();
    }

    private String isiTeks() {
        return new String(isi, StandardCharsets.UTF_8);
    }

    @Test
    public void loginMengirimIdentitasDanMembacaToken() throws Exception {
        badanJawab = "{\"access_token\":\"AKSES\",\"access_expires\":\"2026-11-10T08:15:00.1234567Z\","
                + "\"refresh_token\":\"REFRESH\",\"profil\":{\"username\":\"krani1\",\"nama\":\"Krani\",\"unit_kode\":\"KP_STL\"}}";
        ServerApi.LoginHasil h = api.login("krani1", "krani123", "dev-1", "Samsung", "Android 14", "1.0");

        assertEquals("POST", metode);
        assertEquals("/api/auth/login", pathLengkap);
        assertNull("login tanpa token", authorization);
        JSONObject kirim = new JSONObject(isiTeks());
        assertEquals("krani1", kirim.getString("username"));
        assertEquals("dev-1", kirim.getString("device_id"));
        assertEquals("AKSES", h.token.akses);
        assertEquals("REFRESH", h.token.refresh);
        assertEquals("KP_STL", h.unitKode);
        assertEquals(1_794_298_500_123L, h.token.aksesKedaluwarsaMillis); // 2026-11-10T08:15:00.123Z
    }

    @Test
    public void kirimPanenMemakaiBearerDanMembacaStatus() throws Exception {
        kodeJawab = 201;
        badanJawab = "{\"status\":\"DITERIMA\",\"id\":\"x\"}";
        String s = api.kirimPanen("TOKEN", new JSONObject().put("panen_id", "x"));
        assertEquals("DITERIMA", s);
        assertEquals("/api/panen", pathLengkap);
        assertEquals("Bearer TOKEN", authorization);
        assertEquals("application/json", tipeIsi);

        kodeJawab = 200;
        badanJawab = "{\"status\":\"SUDAH_ADA\",\"id\":\"x\"}";
        assertEquals("SUDAH_ADA", api.kirimPanen("TOKEN", new JSONObject().put("panen_id", "x")));
    }

    @Test
    public void galatServerMembawaKodeDanPesan() {
        kodeJawab = 422;
        badanJawab = "{\"error\":\"catatan belum disetujui mandor\"}";
        try {
            api.kirimPanen("TOKEN", new JSONObject());
            fail("harus gagal");
        } catch (ApiException e) {
            assertEquals(422, e.kode);
            assertEquals("catatan belum disetujui mandor", e.getMessage());
            assertFalse(e.bisaDicobaLagi());
            assertFalse(e.tidakLogin());
        }
    }

    @Test
    public void kodeKlasifikasiGalat() {
        assertTrue(new ApiException(0, "x").bisaDicobaLagi());
        assertTrue(new ApiException(500, "x").bisaDicobaLagi());
        assertTrue(new ApiException(503, "x").bisaDicobaLagi());
        assertTrue("batas laju: dicoba lagi nanti", new ApiException(429, "x").bisaDicobaLagi());
        assertTrue(new ApiException(408, "x").bisaDicobaLagi());
        assertFalse(new ApiException(400, "x").bisaDicobaLagi());
        assertFalse(new ApiException(422, "x").bisaDicobaLagi());
        assertFalse(new ApiException(404, "x").bisaDicobaLagi());
        assertTrue(new ApiException(401, "x").tidakLogin());
    }

    @Test
    public void jawabanBukanJsonTetapMenjadiGalatBerpesan() {
        kodeJawab = 502;
        badanJawab = "<html>Bad gateway</html>";
        try {
            api.kirimAudit("T", new JSONArray());
            fail("harus gagal");
        } catch (ApiException e) {
            assertEquals(502, e.kode);
            assertEquals("Server menjawab 502", e.getMessage());
            assertTrue(e.bisaDicobaLagi());
        }
    }

    @Test
    public void serverTidakTerjangkauMenjadiKodeNol() throws Exception {
        int port = server.getLocalPort();
        server.close();
        try {
            new HttpServerApi("http://127.0.0.1:" + port).refresh("r");
            fail("harus gagal");
        } catch (ApiException e) {
            assertEquals(0, e.kode);
            assertTrue(e.bisaDicobaLagi());
        }
    }

    @Test
    public void alamatKosongDitolakTanpaJaringan() {
        try {
            new HttpServerApi("  ").refresh("r");
            fail("harus gagal");
        } catch (ApiException e) {
            assertEquals(0, e.kode);
            assertEquals("Alamat server belum diatur", e.getMessage());
        }
    }

    @Test
    public void kirimFotoMengirimByteMentahDenganHeaderChecksum() throws Exception {
        File f = File.createTempFile("foto", ".jpg");
        f.deleteOnExit();
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};
        Files.write(f.toPath(), jpeg);
        kodeJawab = 201;
        badanJawab = "{\"status\":\"DITERIMA\",\"id\":\"f1\"}";

        String s = api.kirimFoto("TOKEN", "panen-1", "f1", "abc123", "DETEKSI", f);

        assertEquals("DITERIMA", s);
        assertEquals("/api/panen/panen-1/media?jenis=DETEKSI", pathLengkap);
        assertEquals("image/jpeg", tipeIsi);
        assertEquals("f1", fotoId);
        assertEquals("abc123", sha);
        assertArrayEquals(jpeg, isi);
    }

    @Test
    public void kirimAuditMembacaJumlahDiterima() throws Exception {
        badanJawab = "{\"diterima\":2,\"dilewati\":1}";
        JSONArray a = new JSONArray().put(new JSONObject().put("audit_id", "1")).put(new JSONObject().put("audit_id", "2"));
        assertEquals(2, api.kirimAudit("TOKEN", a));
        assertEquals("/api/audit/batch", pathLengkap);
        assertEquals(2, new JSONArray(isiTeks()).length());
    }

    @Test
    public void kirimKoordinatTphMemakaiPutDanMembacaHasil() throws Exception {
        badanJawab = "{\"diterapkan\":true,\"lat\":-2.123456,\"lon\":113.654321}";
        ServerApi.KoordinatHasil h = api.kirimKoordinatTph("TOKEN", 42L, -2.123456, 113.654321, 8.5);

        assertEquals("PUT", metode);
        assertEquals("/api/tph/42/koordinat", pathLengkap);
        assertEquals("Bearer TOKEN", authorization);
        assertEquals("application/json", tipeIsi);
        JSONObject kirim = new JSONObject(isiTeks());
        assertEquals(-2.123456, kirim.getDouble("lat"), 1e-12);
        assertEquals(113.654321, kirim.getDouble("lon"), 1e-12);
        assertEquals(8.5, kirim.getDouble("akurasi_m"), 1e-12);
        assertTrue(h.diterapkan);
        assertEquals(-2.123456, h.lat, 1e-12);
        assertEquals(113.654321, h.lon, 1e-12);
    }

    @Test
    public void kirimKoordinatTphSudahAdaDiServerMengembalikanTitikServer() throws Exception {
        badanJawab = "{\"diterapkan\":false,\"lat\":-5.0,\"lon\":110.0}";
        ServerApi.KoordinatHasil h = api.kirimKoordinatTph("TOKEN", 1L, -2.0, 113.0, 6.0);
        assertFalse(h.diterapkan);
        assertEquals(-5.0, h.lat, 1e-12);
        assertEquals(110.0, h.lon, 1e-12);
    }

    @Test
    public void kirimKoordinatTphGalatDanJawabanRusak() {
        kodeJawab = 404;
        badanJawab = "{\"error\":\"TPH tidak ditemukan\"}";
        try {
            api.kirimKoordinatTph("TOKEN", 9L, -2.0, 113.0, 6.0);
            fail("harus gagal");
        } catch (ApiException e) {
            assertEquals(404, e.kode);
            assertEquals("TPH tidak ditemukan", e.getMessage());
            assertFalse(e.bisaDicobaLagi());
        }
        kodeJawab = 200;
        badanJawab = "{\"ok\":1}";
        try {
            api.kirimKoordinatTph("TOKEN", 9L, -2.0, 113.0, 6.0);
            fail("jawaban tanpa kolom wajib harus gagal");
        } catch (ApiException e) {
            assertEquals(0, e.kode);
        }
    }

    @Test
    public void statusSejakMembacaDaftarDanPesanKosong() throws Exception {
        badanJawab = "[{\"id\":\"a\",\"status\":\"DITOLAK\",\"status_erp\":\"DITOLAK\",\"pesan\":\"Blok tidak ada\","
                + "\"diubah\":\"2026-11-10T04:00:00Z\"},"
                + "{\"id\":\"b\",\"status\":\"DITERIMA\",\"status_erp\":\"DIIMPOR\",\"diubah\":\"2026-11-10T05:00:00.5Z\"}]";
        List<ServerApi.StatusItem> daftar = api.statusSejak("TOKEN", 0L);

        assertEquals("GET", metode);
        assertEquals("/api/panen?sejak=1970-01-01T00:00:00Z", pathLengkap);
        assertEquals(2, daftar.size());
        assertEquals("DITOLAK", daftar.get(0).status);
        assertEquals("Blok tidak ada", daftar.get(0).pesan);
        assertNull(daftar.get(1).pesan);
        assertEquals(daftar.get(0).diubahMillis + 3_600_500L, daftar.get(1).diubahMillis);
    }

    @Test
    public void fotoBuktiCatatanMenunggu() throws Exception {
        // foto_id null berarti foto belum terkirim dari HP pencatat; bila ada, dipakai untuk mengunduh fotonya.
        badanJawab = "[{\"id\":\"a1\",\"dibuat_pada\":\"2026-11-19T01:00:00Z\",\"foto_id\":null},"
                + "{\"id\":\"a2\",\"dibuat_pada\":\"2026-11-19T01:00:00Z\",\"foto_id\":\"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb\"}]";
        List<ServerApi.Menunggu> l = api.daftarMenunggu("TOKEN");
        assertNull(l.get(0).fotoId);
        assertEquals("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb", l.get(1).fotoId);

        badanJawab = "ISI-FOTO";
        byte[] data = api.unduhFoto("TOKEN", "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
        assertEquals("GET", metode);
        assertEquals("/api/admin/foto/bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb", pathLengkap);
        assertEquals("Bearer TOKEN", authorization);
        assertEquals("ISI-FOTO", new String(data, java.nio.charset.StandardCharsets.UTF_8));

        // Id yang bukan UUID ditolak di HP, tanpa menghubungi server.
        try {
            api.unduhFoto("TOKEN", "../rahasia");
            fail("id foto tidak sah harus ditolak");
        } catch (ApiException e) {
            assertEquals(422, e.kode);
        }

        kodeJawab = 404;
        badanJawab = "{\"error\":\"Foto tidak ditemukan\"}";
        try {
            api.unduhFoto("TOKEN", "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
            fail("404 harus gagal");
        } catch (ApiException e) {
            assertEquals(404, e.kode);
        }
    }

    @Test
    public void waktuIsoDariDanKeServer() throws Exception {
        assertEquals(0L, HttpServerApi.parseIso("1970-01-01T00:00:00Z"));
        assertEquals(1_500L, HttpServerApi.parseIso("1970-01-01T00:00:01.5Z"));
        assertEquals(1_001L, HttpServerApi.parseIso("1970-01-01T00:00:01.0019999Z")); // dibulatkan ke bawah
        assertEquals("1970-01-01T00:00:01Z", HttpServerApi.iso(1_999L));
    }

    @Test
    public void daftarMenungguMembacaIsiCatatanDanSetujuiTolakMemakaiJalurBenar() throws Exception {
        badanJawab = "[{\"id\":\"a1\",\"tanggal_panen\":\"2026-11-19\",\"blok_kode\":\"A12\",\"tph_kode\":\"01\",\"afdeling_kode\":\"DIV1\","
                + "\"pencatat\":\"royyy\",\"pencatat_nama\":\"Royyy\",\"pemanen_nama\":\"Pemanen Satu\",\"pemanen_hrmb_code\":90001,"
                + "\"total_janjang\":10,\"brondolan_kg\":12.5,\"catatan\":null,\"status_lokasi\":\"DI_LUAR\",\"jarak_ke_tph_m\":120.5,"
                + "\"gps_mock\":true,\"dibuat_pada\":\"2026-11-19T01:00:00Z\",\"grading\":[{\"kelas_kode\":\"ripe\",\"janjang\":9},{\"kelas_kode\":\"unripe\",\"janjang\":1}]}]";
        List<ServerApi.Menunggu> l = api.daftarMenunggu("TOKEN");
        assertEquals("GET", metode);
        assertEquals("/api/panen/menunggu", pathLengkap);
        assertEquals("Bearer TOKEN", authorization);
        assertEquals(1, l.size());
        ServerApi.Menunggu m = l.get(0);
        assertEquals("a1", m.id);
        assertEquals("A12", m.blokKode);
        assertEquals("Royyy", m.pencatatNama);
        assertEquals("Pemanen Satu", m.pemanenNama);
        assertEquals(10L, m.totalJanjang);
        assertEquals(12.5, m.brondolanKg, 1e-9);
        assertNull(m.catatan);
        assertEquals(120.5, m.jarakKeTphM, 1e-9);
        assertTrue(m.gpsMock);
        assertEquals(9L, (long) m.grading.get("ripe"));
        assertEquals(1L, (long) m.grading.get("unripe"));

        badanJawab = "{\"status\":\"DISETUJUI\"}";
        api.setujuiPanen("TOKEN", "a1");
        assertEquals("POST", metode);
        assertEquals("/api/panen/a1/setujui", pathLengkap);

        badanJawab = "{\"status\":\"DITOLAK\"}";
        api.tolakPanen("TOKEN", "a1", "blok salah");
        assertEquals("/api/panen/a1/tolak", pathLengkap);
        assertEquals("blok salah", new JSONObject(isiTeks()).getString("alasan"));

        kodeJawab = 409;
        badanJawab = "{\"error\":\"Catatan sudah diproses (status BARU)\"}";
        try {
            api.setujuiPanen("TOKEN", "a1");
            fail("409 harus gagal");
        } catch (ApiException e) {
            assertEquals(409, e.kode);
            assertFalse("409 tidak dicoba ulang otomatis", e.bisaDicobaLagi());
        }
    }

    // ---- IPD (Inspeksi Panen Detail) --------------------------------------------------------------

    @Test
    public void ipdKirimMemakaiJalurBenarDanMembacaStatus() throws Exception {
        badanJawab = "{\"status\":\"DITERIMA\",\"id\":\"x\"}";
        assertEquals("DITERIMA", api.kirimIpd("TOKEN", new JSONObject().put("ipd_id", "x")));
        assertEquals("POST", metode);
        assertEquals("/api/ipd", pathLengkap);
        assertEquals("Bearer TOKEN", authorization);
        assertEquals("application/json", tipeIsi);

        // 423: asisten sudah menyetujui, perubahan tidak diterima. Kode dan pesan sampai ke pemanggil.
        kodeJawab = 423;
        badanJawab = "{\"error\":\"Inspeksi sudah disetujui asisten, tidak bisa diubah lagi\"}";
        try {
            api.kirimIpd("TOKEN", new JSONObject().put("ipd_id", "x"));
            fail("423 harus gagal");
        } catch (ApiException e) {
            assertEquals(423, e.kode);
            assertTrue(e.getMessage(), e.getMessage().contains("disetujui asisten"));
            assertFalse("423 bukan galat sementara: tidak dicoba lagi", e.bisaDicobaLagi());
        }
    }

    @Test
    public void ipdFotoMengirimKeteranganDanUrutLewatQuery() throws Exception {
        File f = File.createTempFile("ipd", ".jpg");
        f.deleteOnExit();
        Files.write(f.toPath(), new byte[]{(byte) 0xFF, (byte) 0xD8, 1, 2, 3});
        badanJawab = "{\"status\":\"DITERIMA\"}";
        api.kirimFotoIpd("TOKEN", "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb", "cccccccc-cccc-4ccc-8ccc-cccccccccccc",
                "a".repeat(64), "buah tinggal & brondolan", 3, f);
        assertEquals("POST", metode);
        assertEquals("/api/ipd/bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb/media?urut=3&keterangan=buah+tinggal+%26+brondolan", pathLengkap);
        assertEquals("cccccccc-cccc-4ccc-8ccc-cccccccccccc", fotoId);
        assertEquals("a".repeat(64), sha);
        assertEquals("image/jpeg", tipeIsi);
        assertEquals(5, isi.length);
    }

    @Test
    public void ipdStatusMenungguDanInspeksiTerakhirDibaca() throws Exception {
        badanJawab = "[{\"id\":\"a1\",\"status\":\"DITOLAK\",\"status_server\":\"DITOLAK\",\"pesan\":\"Baris 10 belum lengkap\",\"diubah\":\"2026-11-20T03:00:00Z\"}]";
        List<ServerApi.StatusItem> st = api.statusIpdSejak("TOKEN", 0);
        assertTrue(pathLengkap.startsWith("/api/ipd?sejak="));
        assertEquals("DITOLAK", st.get(0).status);
        assertEquals("Baris 10 belum lengkap", st.get(0).pesan);

        badanJawab = "[{\"id\":\"i1\",\"tanggal_inspeksi\":\"2026-11-20\",\"divisi_kode\":\"DIV2\",\"blok_kode\":\"A12\",\"pemanen_nama\":\"Budi\","
                + "\"ancak_no\":null,\"inspektur_nama\":\"Mandor Satu\",\"mandor_blok_nama\":\"M1\",\"mandor_ancak_nama\":null,\"catatan\":null,"
                + "\"dibuat_pada\":\"2026-11-20T03:00:00Z\",\"ringkasan\":{\"pokok_diperiksa\":5,\"pokok_bertemuan\":3,\"persen_pokok_bertemuan\":60,\"total_temuan\":13,"
                + "\"per_kode\":[{\"kode\":\"A\",\"nama\":\"Potong buah mentah\",\"pokok\":1,\"jumlah\":1,\"persen_pokok\":20},"
                + "{\"kode\":\"C\",\"nama\":\"Pelepah\",\"pokok\":0,\"jumlah\":0,\"persen_pokok\":0}]},"
                + "\"foto\":[{\"id\":\"f1\",\"keterangan\":\"buah tinggal\",\"urut\":0}]}]";
        List<ServerApi.IpdMenunggu> l = api.daftarIpdMenunggu("TOKEN");
        assertEquals("/api/ipd/menunggu", pathLengkap);
        ServerApi.IpdMenunggu m = l.get(0);
        assertEquals("Budi", m.pemanenNama);
        assertEquals(5, m.pokokDiperiksa);
        assertEquals(60.0, m.persenBertemuan, 0.0001);
        assertEquals("hanya kode yang punya temuan", 1, m.perKode.size());
        assertEquals("A", m.perKode.get(0).kode);
        assertEquals("buah tinggal", m.foto.get(0).keterangan);

        badanJawab = "{\"jendela_hari\":7,\"ada\":true,\"inspeksi\":[{\"id\":\"z\",\"tanggal_inspeksi\":\"2026-11-13\",\"inspektur_nama\":\"Mandor Satu\",\"status\":\"DISETUJUI\"}]}";
        List<ServerApi.IpdTerakhirItem> tr = api.inspeksiTerakhir("TOKEN", 90001, 1012, "2026-11-20", null);
        assertEquals("/api/ipd/inspeksi-terakhir?pemanen_hrmb_code=90001&asm3_oid=1012&tanggal=2026-11-20", pathLengkap);
        assertEquals("Mandor Satu", tr.get(0).inspekturNama);
    }

    @Test
    public void ipdSetujuiTolakDanFotoMemakaiJalurIpd() throws Exception {
        badanJawab = "{\"status\":\"DISETUJUI\"}";
        api.setujuiIpd("TOKEN", "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
        assertEquals("POST", metode);
        assertEquals("/api/ipd/bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb/setujui", pathLengkap);
        api.tolakIpd("TOKEN", "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb", "Blok salah");
        assertEquals("/api/ipd/bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb/tolak", pathLengkap);
        assertTrue(new String(isi, StandardCharsets.UTF_8).contains("Blok salah"));
        badanJawab = "JPEG";
        assertEquals("JPEG", new String(api.unduhFotoIpd("TOKEN", "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"), StandardCharsets.UTF_8));
        assertEquals("/api/ipd/foto/bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb", pathLengkap);
        try {
            api.unduhFotoIpd("TOKEN", "../x");
            fail("id tidak sah");
        } catch (ApiException e) {
            assertEquals(422, e.kode);
        }
    }
}
