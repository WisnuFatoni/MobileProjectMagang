package com.example.fai26.ipd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.fai26.db.IpdRepository;
import com.example.fai26.db.dao.IpdHasil;
import com.example.fai26.sync.PayloadBuilder;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Tes murni (tanpa Android) untuk modul IPD: rekap loses, bentuk kiriman, aturan edit, dan ukuran foto. */
public class IpdTest {

    private static IpdDraf.Pokok pokok(int baris, int no, Object... kodeJumlah) {
        IpdDraf.Pokok p = new IpdDraf.Pokok(baris, no);
        for (int i = 0; i < kodeJumlah.length; i += 2) p.temuan.put((String) kodeJumlah[i], (Integer) kodeJumlah[i + 1]);
        return p;
    }

    @Test
    public void rekapMenghitungPokokBertemuanDanPersen() {
        List<IpdDraf.Pokok> daftar = Arrays.asList(
                pokok(11, 1), pokok(11, 2, "B1", 6, "S", 1), pokok(11, 3), pokok(10, 1, "B1", 5), pokok(10, 2, "A", 1));
        IpdRekap r = IpdRekap.hitung(daftar);
        assertEquals(5, r.pokokDiperiksa);
        assertEquals(3, r.pokokBertemuan);
        assertEquals(13, r.totalTemuan);
        assertEquals(60.0, r.persenBertemuan(), 0.0001);
        assertEquals(2, (int) r.pokokPerKode.get("B1"));
        assertEquals(11, (int) r.jumlahPerKode.get("B1"));
        assertEquals(40.0, r.persenKode("B1"), 0.0001);
        assertEquals(0.0, r.persenKode("C"), 0.0001);
    }

    @Test
    public void rekapMemisahkanLosesDariTemuanLain() {
        // Loses = buah dan brondolan; pelepah (C, R) adalah temuan lain yang tidak memengaruhi produksi.
        java.util.Map<String, String> jenis = new java.util.HashMap<>();
        jenis.put("A", "LOSES");
        jenis.put("B1", "LOSES");
        jenis.put("C", "TEMUAN");
        List<IpdDraf.Pokok> daftar = Arrays.asList(
                pokok(11, 1), pokok(11, 2, "B1", 6), pokok(11, 3, "C", 2), pokok(10, 1, "A", 1, "C", 1), pokok(10, 2));
        IpdRekap r = IpdRekap.hitung(daftar, jenis);
        assertEquals(5, r.pokokDiperiksa);
        assertEquals("pokok dengan temuan apa pun", 3, r.pokokBertemuan);
        assertEquals("loses: B1 dan A", 2, r.pokokLoses);
        assertEquals("temuan lain: C pada dua pokok", 2, r.pokokTemuanLain);
        assertEquals(7, r.totalLoses);
        assertEquals(3, r.totalTemuanLain);
        assertEquals(40.0, r.persenLoses(), 0.0001);
        assertEquals(40.0, r.persenTemuanLain(), 0.0001);
        // Pokok yang hanya berisi pelepah tidak dihitung sebagai loses.
        IpdRekap hanyaLain = IpdRekap.hitung(Arrays.asList(pokok(1, 1, "C", 1)), jenis);
        assertEquals(0, hanyaLain.pokokLoses);
        assertEquals(1, hanyaLain.pokokTemuanLain);
        assertEquals(0.0, hanyaLain.persenLoses(), 0.0001);
    }

    @Test
    public void rekapKosongTidakMembagiNol() {
        IpdRekap r = IpdRekap.hitung(new ArrayList<>());
        assertEquals(0, r.pokokDiperiksa);
        assertEquals(0.0, r.persenBertemuan(), 0.0001);
        assertEquals(16.7, IpdRekap.persen(1, 6), 0.0001);
    }

    @Test
    public void bentukKirimanSesuaiDokumentasiApi() throws Exception {
        IpdHasil.HeaderKirim h = new IpdHasil.HeaderKirim();
        h.ipdId = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
        h.unitKode = "KP_STL";
        h.tanggalInspeksi = "2026-11-20";
        h.divisiKode = "DIV2";
        h.asm3Oid = 1012;
        h.blokKode = "A12";
        h.pemanenHrmb = 90001;
        h.ancakNo = "7";
        h.mandorUsername = "mandor2";
        h.dibuatPada = "2026-11-20T03:00:00Z";
        IpdHasil.PokokKirim p1 = new IpdHasil.PokokKirim();
        p1.pokokId = 1;
        p1.noBaris = 11;
        p1.noPokok = 1;
        IpdHasil.PokokKirim p2 = new IpdHasil.PokokKirim();
        p2.pokokId = 2;
        p2.noBaris = 11;
        p2.noPokok = 2;
        IpdHasil.TemuanKirim t = new IpdHasil.TemuanKirim();
        t.pokokId = 2;
        t.kode = "B1";
        t.jumlah = 6;
        JSONObject o = PayloadBuilder.ipd(h, Arrays.asList(p1, p2), Arrays.asList(t));
        assertEquals("DIV2", o.getString("divisi_kode"));
        assertEquals(1012, o.getLong("asm3_oid"));
        assertEquals("mandor2", o.getString("mandor_username"));
        assertTrue("tanpa kirim_ulang bila bukan perubahan", !o.has("kirim_ulang"));
        JSONArray pk = o.getJSONArray("pokok");
        assertEquals(2, pk.length());
        assertEquals(0, pk.getJSONObject(0).getJSONArray("temuan").length());
        assertEquals("B1", pk.getJSONObject(1).getJSONArray("temuan").getJSONObject(0).getString("kode"));
        assertEquals(6, pk.getJSONObject(1).getJSONArray("temuan").getJSONObject(0).getLong("jumlah"));

        h.kirimUlang = 1;
        assertTrue(PayloadBuilder.ipd(h, Arrays.asList(p1), new ArrayList<>()).getBoolean("kirim_ulang"));
    }

    @Test
    public void jalurGpsDanWaktuPokokIkutDikirimBilaAda() throws Exception {
        IpdHasil.HeaderKirim h = new IpdHasil.HeaderKirim();
        h.ipdId = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
        h.unitKode = "KP_STL";
        h.tanggalInspeksi = "2026-11-20";
        h.divisiKode = "DIV2";
        h.asm3Oid = 1012;
        h.pemanenHrmb = 90001;
        h.mandorUsername = "mandor1";
        h.dibuatPada = "2026-11-20T03:00:00Z";
        IpdHasil.PokokKirim p = new IpdHasil.PokokKirim();
        p.pokokId = 1;
        p.noBaris = 120;
        p.noPokok = 5;
        p.dicatatPada = "2026-11-20T03:04:10Z";
        IpdHasil.TitikKirim t1 = new IpdHasil.TitikKirim();
        t1.waktu = "2026-11-20T03:00:00Z";
        t1.lat = -2.7;
        t1.lon = 104.3;
        t1.akurasi = 8.04;
        IpdHasil.TitikKirim t2 = new IpdHasil.TitikKirim();
        t2.waktu = "2026-11-20T03:00:05Z";
        t2.lat = -2.69997;
        t2.lon = 104.3;

        JSONObject o = PayloadBuilder.ipd(h, Arrays.asList(p), new ArrayList<>(), Arrays.asList(t1, t2));
        assertEquals("2026-11-20T03:04:10Z", o.getJSONArray("pokok").getJSONObject(0).getString("dicatat_pada"));
        JSONArray j = o.getJSONArray("jejak");
        assertEquals(2, j.length());
        assertEquals("2026-11-20T03:00:00Z", j.getJSONObject(0).getString("t"));
        assertEquals(-2.7, j.getJSONObject(0).getDouble("lat"), 1e-9);
        assertEquals(8.0, j.getJSONObject(0).getDouble("ak"), 1e-9);
        assertTrue("akurasi tak diketahui tidak dikirim", !j.getJSONObject(1).has("ak"));

        // Tanpa jalur (HP tanpa GPS atau dimatikan): kunci jejak tidak ada sama sekali.
        assertTrue(!PayloadBuilder.ipd(h, Arrays.asList(p), new ArrayList<>()).has("jejak"));
        assertTrue(!PayloadBuilder.ipd(h, Arrays.asList(p), new ArrayList<>(), new ArrayList<>()).has("jejak"));
    }

    @Test
    public void aturanEditHanyaSebelumDisetujui() {
        assertTrue(IpdRepository.bisaDiubah("DRAFT"));
        assertTrue(IpdRepository.bisaDiubah("MENUNGGU_SETUJU"));
        assertTrue(IpdRepository.bisaDiubah("DITOLAK"));
        assertTrue(!IpdRepository.bisaDiubah("DISETUJUI"));
    }

    @Test
    public void kelengkapanDiperiksaSebelumKirim() {
        IpdDraf d = new IpdDraf();
        String[] urutan = {"divisi", "blok", "pemanen", "mandor", "pokok"};
        int i = 0;
        for (; i < urutan.length; i++) {
            try {
                IpdRepository.pastikanLengkap(d);
                break;
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage(), e.getMessage().toLowerCase().contains(urutan[i].split(" ")[0]));
            }
            switch (i) {
                case 0:
                    d.divisiId = 1;
                    break;
                case 1:
                    d.blokId = 1;
                    break;
                case 2:
                    d.pemanenId = 1;
                    break;
                case 3:
                    d.mandorUserId = 2L;
                    break;
                default:
                    d.pokok.add(new IpdDraf.Pokok(1, 1));
            }
        }
        IpdRepository.pastikanLengkap(d); // lengkap: tidak melempar
    }

    @Test
    public void ukuranFotoDibatasiTanpaMemperbesar() {
        assertEquals(1600, FotoKompres.sisiHasil(4000, 3000, 1600));
        assertEquals(1200, FotoKompres.sisiHasil(1200, 900, 1600));
        // inSampleSize: pangkat dua terbesar yang masih menyisakan sisi terpanjang >= batas.
        assertEquals(1, FotoKompres.sampel(1600, 1200, 1600));
        assertEquals(2, FotoKompres.sampel(4000, 3000, 1600));
        assertEquals(4, FotoKompres.sampel(6500, 4000, 1600));
    }
}
