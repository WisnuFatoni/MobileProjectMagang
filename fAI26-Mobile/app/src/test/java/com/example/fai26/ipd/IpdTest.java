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
        h.mandorBlokUsername = "mandor1";
        h.mandorAncakUsername = "mandor2";
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
        assertEquals("mandor2", o.getString("mandor_ancak_username"));
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
    public void aturanEditHanyaSebelumDisetujui() {
        assertTrue(IpdRepository.bisaDiubah("DRAFT"));
        assertTrue(IpdRepository.bisaDiubah("MENUNGGU_SETUJU"));
        assertTrue(IpdRepository.bisaDiubah("DITOLAK"));
        assertTrue(!IpdRepository.bisaDiubah("DISETUJUI"));
    }

    @Test
    public void kelengkapanDiperiksaSebelumKirim() {
        IpdDraf d = new IpdDraf();
        String[] urutan = {"divisi", "blok", "pemanen", "mandor di blok", "mandor di ancak", "pokok"};
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
                    d.mandorBlokUserId = 2L;
                    break;
                case 4:
                    d.mandorAncakUserId = 3L;
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
