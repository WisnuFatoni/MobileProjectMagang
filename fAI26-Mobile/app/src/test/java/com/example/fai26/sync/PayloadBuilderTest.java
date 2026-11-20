package com.example.fai26.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.fai26.db.dao.Hasil;
import com.example.fai26.db.entity.AuditLog;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class PayloadBuilderTest {

    private static Hasil.PanenKirim panen() {
        Hasil.PanenKirim p = new Hasil.PanenKirim();
        p.panenId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
        p.unitKode = "KP_STL";
        p.tanggalPanen = "2026-11-10";
        p.asm3Oid = 101;
        p.blokKode = "A12";
        p.tphKode = "01";
        p.brondolanKg = 12.5;
        p.statusLokasi = "TANPA_GPS";
        p.dibuatPada = "2026-11-10T01:00:00Z";
        p.pencatatUsername = "krani1";
        p.disetujuiUsername = "mandor1";
        p.disetujuiPada = "2026-11-10T03:00:00Z";
        return p;
    }

    private static Hasil.GradingKirim grading(String kode, String kolom, long ai, long fin, long manual) {
        Hasil.GradingKirim g = new Hasil.GradingKirim();
        g.kelasKode = kode;
        g.erpKolom = kolom;
        g.jmlAi = ai;
        g.jmlFinal = fin;
        g.diubahManual = manual;
        return g;
    }

    @Test
    public void bentukSesuaiDokumentasiApi() throws Exception {
        Hasil.PemanenKirim pm = new Hasil.PemanenKirim();
        pm.hrmbCode = 5001;
        pm.jjgAlokasi = 10L;
        JSONObject o = PayloadBuilder.panen(panen(),
                Arrays.asList(grading("ripe", "JJG_RIPE", 7, 9, 1), grading("unripe", null, 1, 1, 0)),
                Collections.singletonList(pm));

        assertEquals("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", o.getString("panen_id"));
        assertEquals("KP_STL", o.getString("unit_kode"));
        assertEquals(101, o.getLong("asm3_oid"));
        assertEquals(12.5, o.getDouble("brondolan_kg"), 0);
        assertEquals("mandor1", o.getString("disetujui_username"));
        assertEquals("krani1", o.getString("pencatat_username"));
        assertFalse(o.getBoolean("gps_mock"));

        JSONArray g = o.getJSONArray("grading");
        assertEquals(2, g.length());
        assertEquals("ripe", g.getJSONObject(0).getString("kelas_kode"));
        assertEquals("JJG_RIPE", g.getJSONObject(0).getString("erp_kolom"));
        assertEquals(9, g.getJSONObject(0).getInt("jml_final"));
        assertTrue(g.getJSONObject(0).getBoolean("diubah_manual"));
        assertFalse(g.getJSONObject(1).has("erp_kolom")); // kosong tidak dikirim
        assertFalse(g.getJSONObject(1).getBoolean("diubah_manual"));

        assertEquals(5001, o.getJSONArray("pemanen").getJSONObject(0).getInt("hrmb_code"));
        assertEquals(10, o.getJSONArray("pemanen").getJSONObject(0).getInt("jjg_alokasi"));
    }

    @Test
    public void kolomKosongTidakIkutDikirim() throws Exception {
        JSONObject o = PayloadBuilder.panen(panen(), Collections.emptyList(), Collections.emptyList());
        for (String k : new String[]{"rotasi", "kondisi_cuaca", "gps_lat", "gps_lon", "gps_akurasi_m", "gps_waktu",
                "jarak_ke_tph_m", "model_versi", "catatan"}) {
            assertFalse(k + " seharusnya tidak ada", o.has(k));
        }
        assertEquals(0, o.getJSONArray("pemanen").length());
    }

    @Test
    public void gpsTerisiIkutDikirim() throws Exception {
        Hasil.PanenKirim p = panen();
        p.gpsLat = -2.000123;
        p.gpsLon = 113.000456;
        p.gpsAkurasiM = 8.0;
        p.gpsWaktu = "2026-11-10T01:00:00Z";
        p.gpsMock = 1;
        p.jarakKeTphM = 22.0;
        p.statusLokasi = "DI_TPH";
        JSONObject o = PayloadBuilder.panen(p, Collections.emptyList(), Collections.emptyList());
        assertEquals(-2.000123, o.getDouble("gps_lat"), 1e-9);
        assertTrue(o.getBoolean("gps_mock"));
        assertEquals("DI_TPH", o.getString("status_lokasi"));
        assertEquals(22.0, o.getDouble("jarak_ke_tph_m"), 0);
    }

    @Test
    public void pemanenTanpaAlokasiDikirimNol() throws Exception {
        Hasil.PemanenKirim pm = new Hasil.PemanenKirim();
        pm.hrmbCode = 7;
        JSONObject o = PayloadBuilder.panen(panen(), Collections.emptyList(), Collections.singletonList(pm));
        assertEquals(0, o.getJSONArray("pemanen").getJSONObject(0).getInt("jjg_alokasi"));
    }

    @Test
    public void auditDibentukDenganKolomOpsional() throws Exception {
        AuditLog a = new AuditLog();
        a.auditId = "ffffffff-ffff-4fff-8fff-ffffffffff01";
        a.tabel = "panen_header";
        a.barisId = "x";
        a.aksi = "BUAT";
        a.nilaiBaru = "{\"status\":\"DRAFT\"}";
        a.waktu = "2026-11-10T01:00:00Z";
        JSONArray arr = PayloadBuilder.audit(Collections.singletonList(a));
        JSONObject o = arr.getJSONObject(0);
        assertEquals("BUAT", o.getString("aksi"));
        assertEquals("{\"status\":\"DRAFT\"}", o.getString("nilai_baru"));
        assertFalse(o.has("nilai_lama"));
        assertFalse(o.has("username"));
    }

    @Test
    public void kirimUlangHanyaDikirimBilaCatatanDitolakDiperbaiki() throws Exception {
        Hasil.PemanenKirim pm = new Hasil.PemanenKirim();
        pm.hrmbCode = 90001;
        pm.jjgAlokasi = 10L;
        java.util.List<Hasil.GradingKirim> g = Collections.singletonList(grading("ripe", "JJG_RIPE", 7, 9, 1));

        Hasil.PanenKirim biasa = panen();
        assertFalse("catatan biasa tidak membawa kirim_ulang", PayloadBuilder.panen(biasa, g, Collections.singletonList(pm)).has("kirim_ulang"));

        Hasil.PanenKirim perbaikan = panen();
        perbaikan.kirimUlang = 1;
        assertTrue(PayloadBuilder.panen(perbaikan, g, Collections.singletonList(pm)).getBoolean("kirim_ulang"));
    }
}
