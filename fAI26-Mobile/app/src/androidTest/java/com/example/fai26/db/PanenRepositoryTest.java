package com.example.fai26.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;

import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.fai26.db.dao.Hasil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class PanenRepositoryTest {

    private Context ctx;
    private AppDatabase room;
    private SupportSQLiteDatabase db;
    private PanenRepository repo;
    private File folderFoto;

    @Before
    public void siapkan() throws Exception {
        ctx = ApplicationProvider.getApplicationContext();
        room = Room.inMemoryDatabaseBuilder(ctx, AppDatabase.class)
                .addCallback(AppDatabase.callback())
                .build();
        db = room.getOpenHelper().getWritableDatabase();
        assertTrue(MasterLoader.muatBilaKosong(ctx, room));

        // Perangkat uji dan pengguna contoh (user 1 = krani1) dari master contoh.
        db.execSQL("INSERT INTO device (device_id, aktif, dibuat_pada) VALUES ('dev-uji', 1, ?)",
                new Object[]{Waktu.utcSekarang()});
        folderFoto = new File(ctx.getCacheDir(), "foto_uji");
        repo = new PanenRepository(room, folderFoto, "dev-uji", 1L);
    }

    @After
    public void bersihkan() {
        room.close();
        File[] semua = folderFoto.listFiles();
        if (semua != null) for (File f : semua) f.delete();
        folderFoto.delete();
    }

    private long angka(String sql) {
        try (Cursor c = db.query(sql)) {
            c.moveToFirst();
            return c.getLong(0);
        }
    }

    private File fotoPalsu() throws Exception {
        Bitmap b = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888);
        new Canvas(b).drawColor(Color.GREEN);
        File f = new File(ctx.getCacheDir(), "foto_palsu.jpg");
        try (FileOutputStream out = new FileOutputStream(f)) {
            b.compress(Bitmap.CompressFormat.JPEG, 90, out);
        }
        return f;
    }

    private PanenRepository.Permintaan permintaan() throws Exception {
        List<Hasil.TphPilihan> tph = room.masterDao().daftarTph();
        assertFalse(tph.isEmpty());
        PanenRepository.Permintaan r = new PanenRepository.Permintaan();
        r.unitId = tph.get(0).unitId;
        r.blokId = tph.get(0).blokId;
        r.tphId = tph.get(0).tphId;
        r.pemanenId = 1L;
        r.tanggalPanen = "2026-11-10";
        r.brondolanKg = 12.5;
        r.catatan = "uji";
        // Nomor kelas model: 3 = Ripe, 5 = Unripe, 2 = Overripe.
        r.jumlahAi = new int[]{0, 0, 2, 7, 0, 1};
        r.jumlahFinal = new int[]{0, 0, 2, 9, 0, 1}; // krani menambah 2 Ripe
        r.foto = fotoPalsu();
        // Dua kotak: Ripe dan Unripe.
        r.deteksi = new float[]{3, 0.9f, 0.1f, 0.2f, 0.4f, 0.5f, 5, 0.6f, 0.5f, 0.5f, 0.8f, 0.9f};
        return r;
    }

    @Test
    public void masterContohDimuatSekali() throws Exception {
        assertEquals(5, room.masterDao().daftarTph().size());
        assertEquals(5, room.masterDao().daftarPemanen().size());
        assertFalse(MasterLoader.muatBilaKosong(ctx, room)); // kedua kali tidak memuat lagi
        assertEquals(1, angka("SELECT COUNT(*) FROM unit"));
        // QR mengikuti rancangan FAI26|TPH|1|unit|blok|no.
        Hasil.TphPilihan t = room.masterDao().tphDariQr("FAI26|TPH|1|KP_STL|A12|01");
        assertNotNull(t);
        assertEquals("A12", t.blok);
    }

    @Test
    public void simpanMenulisSemuaTabelDanAudit() throws Exception {
        String id = repo.simpan(permintaan());

        assertEquals(1, angka("SELECT COUNT(*) FROM panen_header WHERE status = 'DRAFT'"));
        assertEquals(6, angka("SELECT COUNT(*) FROM panen_grading WHERE panen_id = '" + id + "'"));
        assertEquals(1, angka("SELECT COUNT(*) FROM panen_pemanen WHERE jjg_alokasi = 12")); // 2+9+1
        assertEquals(1, angka("SELECT COUNT(*) FROM foto WHERE jenis = 'DETEKSI' AND sha256 IS NOT NULL "
                + "AND lebar = 64 AND tinggi = 48"));
        assertEquals(2, angka("SELECT COUNT(*) FROM deteksi"));
        assertEquals(1, angka("SELECT model_id FROM panen_header"));

        // Kelas Ripe (label 3): hitungan AI 7, final 9, ditandai diubah manual.
        try (Cursor c = db.query("SELECT g.jml_ai, g.jml_final, g.diubah_manual FROM panen_grading g "
                + "JOIN kelas_grading k ON k.kelas_id = g.kelas_id WHERE k.label_model = 3")) {
            assertTrue(c.moveToFirst());
            assertEquals(7, c.getInt(0));
            assertEquals(9, c.getInt(1));
            assertEquals(1, c.getInt(2));
        }
        // Kelas Unripe (label 5) tidak diubah.
        assertEquals(0, angka("SELECT g.diubah_manual FROM panen_grading g "
                + "JOIN kelas_grading k ON k.kelas_id = g.kelas_id WHERE k.label_model = 5"));

        // Audit: 1 header + 6 grading + 1 pemanen + 1 foto = 9 baris, semua BUAT dan belum disinkron.
        assertEquals(9, angka("SELECT COUNT(*) FROM audit_log WHERE aksi = 'BUAT' AND disinkron = 0 "
                + "AND user_id = 1 AND device_id = 'dev-uji'"));
        assertEquals(1, angka("SELECT COUNT(*) FROM audit_log WHERE tabel = 'panen_header' "
                + "AND baris_id = '" + id + "' AND nilai_baru LIKE '%2026-11-10%'"));

        // Berkas foto benar-benar tersalin ke folder foto.
        try (Cursor c = db.query("SELECT path_lokal FROM foto")) {
            assertTrue(c.moveToFirst());
            assertTrue(new File(c.getString(0)).exists());
        }
    }

    @Test
    public void riwayatMenampilkanCatatan() throws Exception {
        repo.simpan(permintaan());
        List<Hasil.RiwayatItem> riwayat = room.panenDao().riwayat(0, 50);
        assertEquals(1, riwayat.size());
        Hasil.RiwayatItem r = riwayat.get(0);
        assertEquals("A12", r.blok);
        assertEquals("DRAFT", r.status);
        assertEquals("Pemanen Contoh 1", r.pemanen);
        assertNotNull(r.fotoPath);

        int total = 0;
        for (Hasil.GradingBaris g : room.panenDao().gradingSemua()) total += g.jumlah;
        assertEquals(12, total);
    }

    @Test
    public void tanpaPemanenDitolakKarenaSatuTphSatuPemanen() throws Exception {
        PanenRepository.Permintaan r = permintaan();
        r.pemanenId = null;
        try {
            repo.simpan(r);
            fail("Catatan tanpa pemanen harus ditolak");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Pemanen wajib"));
        }
        r.pemanenId = 0L; // pilihan "Pilih pemanen..." (id 0) juga bukan pemanen
        try {
            repo.simpan(r);
            fail("pemanen_id 0 harus ditolak");
        } catch (IllegalArgumentException e) {
            // benar
        }
        assertEquals("tidak ada yang tersimpan", 0, angka("SELECT COUNT(*) FROM panen_header"));
        assertEquals(0, angka("SELECT COUNT(*) FROM audit_log"));
        File[] sisa = folderFoto.listFiles();
        assertTrue("tidak boleh ada foto yatim", sisa == null || sisa.length == 0);
    }

    @Test
    public void satuPemananMendapatSeluruhJanjang() throws Exception {
        PanenRepository.Permintaan r = permintaan();
        r.jumlahFinal = new int[]{0, 0, 2, 9, 0, 1}; // total 12
        repo.simpan(r);
        assertEquals(1, angka("SELECT COUNT(*) FROM panen_pemanen"));
        assertEquals("seluruh janjang dikreditkan ke satu pemanen", 12, angka("SELECT jjg_alokasi FROM panen_pemanen"));
    }

    @Test
    public void transaksiGagalMembatalkanSemuanyaTermasukFoto() throws Exception {
        PanenRepository.Permintaan r = permintaan();
        r.tphId = 9999; // TPH tidak ada: foreign key menolak header
        try {
            repo.simpan(r);
            fail("Seharusnya gagal");
        } catch (RuntimeException e) {
            // benar
        }
        assertEquals(0, angka("SELECT COUNT(*) FROM panen_header"));
        assertEquals(0, angka("SELECT COUNT(*) FROM audit_log"));
        File[] sisa = folderFoto.listFiles();
        assertTrue("Foto yatim harus dihapus", sisa == null || sisa.length == 0);
    }
    private void setKoordinatTph(long tphId, double lat, double lon) {
        db.execSQL("UPDATE tph SET lat = ?, lon = ? WHERE tph_id = ?", new Object[]{lat, lon, tphId});
    }

    @Test
    public void lokasiTanpaGpsDanTphTanpaKoordinat() throws Exception {
        PanenRepository.Permintaan r = permintaan();
        repo.simpan(r);
        assertEquals("TANPA_GPS", r.statusLokasi);
        assertEquals(1, angka("SELECT COUNT(*) FROM panen_header WHERE status_lokasi = 'TANPA_GPS' "
                + "AND gps_lat IS NULL AND gps_mock = 0"));

        PanenRepository.Permintaan r2 = permintaan();
        r2.pemanenId = 2L; // satu TPH + pemanen + tanggal hanya boleh satu catatan
        r2.gpsLat = -2.0;
        r2.gpsLon = 113.0;
        r2.gpsAkurasiM = 8.0;
        repo.simpan(r2);
        assertEquals("TPH_TANPA_KOORDINAT", r2.statusLokasi); // TPH contoh belum punya koordinat
    }

    @Test
    public void lokasiDiTphDiLuarDanLokasiPalsu() throws Exception {
        PanenRepository.Permintaan dekat = permintaan();
        setKoordinatTph(dekat.tphId, -2.0, 113.0);
        dekat.gpsLat = -2.0 + 0.0002; // sekitar 22 m
        dekat.gpsLon = 113.0;
        dekat.gpsAkurasiM = 6.0;
        dekat.gpsWaktu = "2026-11-10T01:00:00Z";
        repo.simpan(dekat);
        assertEquals("DI_TPH", dekat.statusLokasi);

        PanenRepository.Permintaan jauh = permintaan();
        jauh.pemanenId = 2L;
        jauh.gpsLat = -2.0 + 0.002; // sekitar 222 m
        jauh.gpsLon = 113.0;
        jauh.gpsAkurasiM = 6.0;
        jauh.gpsMock = true;
        repo.simpan(jauh);
        assertEquals("DI_LUAR", jauh.statusLokasi);

        assertEquals(1, angka("SELECT COUNT(*) FROM panen_header WHERE status_lokasi = 'DI_TPH' "
                + "AND jarak_ke_tph_m BETWEEN 15 AND 30 AND gps_akurasi_m = 6 AND gps_waktu IS NOT NULL"));
        assertEquals(1, angka("SELECT COUNT(*) FROM panen_header WHERE status_lokasi = 'DI_LUAR' "
                + "AND gps_mock = 1 AND jarak_ke_tph_m > 200"));
        // Audit mencatat status lokasi.
        assertEquals(1, angka("SELECT COUNT(*) FROM audit_log WHERE tabel = 'panen_header' "
                + "AND nilai_baru LIKE '%\"status_lokasi\":\"DI_LUAR\"%'"));

        List<Hasil.PersetujuanItem> menunggu = room.panenDao().menungguPersetujuan();
        assertEquals(2, menunggu.size());
        boolean adaLuar = false;
        for (Hasil.PersetujuanItem i : menunggu) {
            if ("DI_LUAR".equals(i.statusLokasi)) {
                adaLuar = true;
                assertEquals(1, i.gpsMock);
            }
        }
        assertTrue(adaLuar);
    }

    @Test
    public void rekamKoordinatTphHanyaSekaliDanButuhAkurasiCukup() throws Exception {
        long tph = room.masterDao().daftarTph().get(0).tphId;
        try {
            repo.rekamKoordinatTph(tph, -2.0, 113.0, 40.0);
            fail("Akurasi 40 m harus ditolak");
        } catch (IllegalArgumentException e) {
            // benar
        }
        assertEquals(0, angka("SELECT COUNT(*) FROM tph WHERE lat IS NOT NULL"));

        assertTrue(repo.rekamKoordinatTph(tph, -2.0, 113.0, 6.0));
        assertFalse("Kedua kali tidak menimpa", repo.rekamKoordinatTph(tph, -3.0, 114.0, 6.0));
        assertEquals(1, angka("SELECT COUNT(*) FROM tph WHERE tph_id = " + tph
                + " AND lat = -2.0 AND lon = 113.0 AND diubah_oleh = 1"));
        assertEquals(1, angka("SELECT COUNT(*) FROM audit_log WHERE tabel = 'tph' AND aksi = 'UBAH'"));
        assertEquals(-2.0, room.panenDao().koordinatTph(tph).lat, 1e-9);

        // Catatan berikutnya di TPH itu dinilai terhadap koordinat tersebut.
        PanenRepository.Permintaan r = permintaan();
        r.tphId = tph;
        r.gpsLat = -2.0 + 0.0001;
        r.gpsLon = 113.0;
        r.gpsAkurasiM = 5.0;
        repo.simpan(r);
        assertEquals("DI_TPH", r.statusLokasi);
    }

    @Test
    public void mandorMenyetujuiCatatanDraftDanKraniTidakBerhak() throws Exception {
        String id = repo.simpan(permintaan());
        assertEquals(1, room.panenDao().jumlahMenunggu());
        List<Hasil.PersetujuanItem> menunggu = room.panenDao().menungguPersetujuan();
        assertEquals(1, menunggu.size());
        assertEquals(12, menunggu.get(0).total);
        assertEquals("A12", menunggu.get(0).blok);

        // Krani (user 1) tidak memegang panen.setujui.
        try {
            repo.setujui(java.util.Collections.singletonList(id));
            fail("Krani tidak boleh menyetujui");
        } catch (SecurityException e) {
            // benar
        }
        assertEquals(1, angka("SELECT COUNT(*) FROM panen_header WHERE status = 'DRAFT'"));

        // Mandor (user 2) boleh. Kedua kali tidak mengubah apa pun.
        PanenRepository mandor = new PanenRepository(room, folderFoto, "dev-uji", 2L);
        assertEquals(1, mandor.setujui(java.util.Collections.singletonList(id)));
        assertEquals(0, mandor.setujui(java.util.Collections.singletonList(id)));

        assertEquals(0, room.panenDao().jumlahMenunggu());
        assertEquals(1, angka("SELECT COUNT(*) FROM panen_header WHERE status = 'DISETUJUI' "
                + "AND disetujui_oleh = 2 AND disetujui_pada IS NOT NULL"));
        assertEquals(1, angka("SELECT COUNT(*) FROM audit_log WHERE aksi = 'SETUJU' AND user_id = 2 "
                + "AND baris_id = '" + id + "' AND nilai_lama LIKE '%DRAFT%' AND nilai_baru LIKE '%DISETUJUI%'"));
    }

    @Test
    public void ringkasanBerandaMenghitungCatatanJanjangDanTertunda() throws Exception {
        repo.simpan(permintaan()); // 12 janjang, tanggal 2026-11-10, status DRAFT
        Hasil.RingkasanHari hari = room.panenDao().ringkasanHari("2026-11-10", 0);
        assertEquals(1, hari.catatan);
        assertEquals(12, hari.janjang);

        Hasil.RingkasanHari lain = room.panenDao().ringkasanHari("2026-11-11", 0);
        assertEquals(0, lain.catatan);
        assertEquals(0, lain.janjang);

        assertEquals(1, room.panenDao().jumlahBelumTerkirim(0));
        db.execSQL("UPDATE panen_header SET status = 'TERKIRIM'");
        assertEquals(0, room.panenDao().jumlahBelumTerkirim(0));
    }

    @Test
    public void krani_hanyaMelihatCatatanSendiriSedangPemilikSemuaMelihatSemua() throws Exception {
        repo.simpan(permintaan()); // dicatat user 1 (krani1)
        assertEquals(1, room.panenDao().riwayat(1, 50).size());
        assertEquals(0, room.panenDao().riwayat(2, 50).size()); // mandor1 bukan pencatatnya
        assertEquals(1, room.panenDao().riwayat(0, 50).size()); // 0 = semua
        assertEquals(12, room.panenDao().riwayat(1, 50).get(0).total);
        assertEquals(0, room.panenDao().ringkasanHari("2026-11-10", 2).catatan);
        assertEquals(1, room.panenDao().ringkasanHari("2026-11-10", 1).catatan);
    }

    @Test
    public void riwayatDibatasiJumlahDanUrutanTerbaruDulu() throws Exception {
        for (int i = 0; i < 4; i++) {
            PanenRepository.Permintaan r = permintaan();
            r.pemanenId = (long) (i + 1); // pemanen berbeda: bukan catatan ganda
            repo.simpan(r);
            Thread.sleep(1100); // dibuat_pada berresolusi detik
        }
        List<Hasil.RiwayatItem> tiga = room.panenDao().riwayat(0, 3);
        assertEquals(3, tiga.size());
        assertTrue(tiga.get(0).dibuatPada.compareTo(tiga.get(1).dibuatPada) >= 0);
        assertTrue(tiga.get(1).dibuatPada.compareTo(tiga.get(2).dibuatPada) >= 0);
    }
    @Test
    public void detailTphMenampilkanInfoBlokDanRiwayatPanen() throws Exception {
        long tphId = room.masterDao().tphDariQr("FAI26|TPH|1|KP_STL|A12|01").tphId;

        // Sebelum ada catatan: info master saja, belum ada panen terakhir.
        Hasil.TphDetail awal = room.masterDao().detailTph(tphId, "2026-11-10");
        assertEquals("KP_STL", awal.unitKode);
        assertEquals("Kebun Aas (contoh)", awal.unitNama);
        assertEquals("Divisi 2 (contoh)", awal.divisiNama);
        assertEquals("A12", awal.blokKode);
        assertEquals(2012L, awal.tahunTanam.longValue());
        assertEquals(28.5, awal.luasHa, 1e-9);
        assertEquals(133.0, awal.sph, 1e-9);
        assertNull(awal.panenTerakhir);
        assertEquals(0, awal.catatanHariIni);

        // Setelah satu catatan di TPH ini (permintaan() memakai TPH pertama dari daftar: A12 / 01).
        repo.simpan(permintaan());
        Hasil.TphDetail sesudah = room.masterDao().detailTph(tphId, "2026-11-10");
        assertEquals("2026-11-10", sesudah.panenTerakhir);
        assertEquals(1, sesudah.catatanHariIni);

        // Hari lain: panen terakhir tetap ada, tetapi tidak dihitung sebagai catatan hari itu.
        Hasil.TphDetail hariLain = room.masterDao().detailTph(tphId, "2026-11-11");
        assertEquals("2026-11-10", hariLain.panenTerakhir);
        assertEquals(0, hariLain.catatanHariIni);

        // TPH lain tidak ikut terpengaruh.
        long tphLain = room.masterDao().tphDariQr("FAI26|TPH|1|KP_STL|A13|01").tphId;
        Hasil.TphDetail lain = room.masterDao().detailTph(tphLain, "2026-11-10");
        assertEquals("A13", lain.blokKode);
        assertNull(lain.panenTerakhir);
    }

    // ---- mandor yang menginput (18 Nov) ------------------------------------------------------------

    private PanenRepository mandorRepo() {
        return new PanenRepository(room, folderFoto, "dev-uji", 2L);
    }

    @Test
    public void mandorMenyetujuiSendiriSaatSimpanDanLangsungAntre() throws Exception {
        PanenRepository.Permintaan r = permintaan();
        String id = mandorRepo().simpan(r);

        assertTrue(r.disetujuiOtomatis);
        assertEquals(1, angka("SELECT COUNT(*) FROM panen_header WHERE panen_id = '" + id + "' AND status = 'DISETUJUI' "
                + "AND disetujui_oleh = 2 AND disetujui_pada IS NOT NULL AND user_id = 2"));
        assertEquals("langsung masuk antrean kirim", 1, angka("SELECT COUNT(*) FROM sync_queue WHERE panen_id = '" + id
                + "' AND entitas = 'PANEN' AND status = 'MENUNGGU'"));
        assertEquals(1, angka("SELECT COUNT(*) FROM audit_log WHERE aksi = 'SETUJU' AND baris_id = '" + id + "'"));
        assertEquals("tidak ada yang menunggu persetujuan", 0, room.panenDao().jumlahMenunggu());
    }

    @Test
    public void pengirimTanpaHakSetujuTetapDraftTetapiLangsungAntreDikirimSebagaiMenunggu() throws Exception {
        PanenRepository.Permintaan r = permintaan();
        String id = repo.simpan(r);
        assertFalse(r.disetujuiOtomatis);
        assertEquals(1, angka("SELECT COUNT(*) FROM panen_header WHERE panen_id = '" + id + "' AND status = 'DRAFT' AND disetujui_oleh IS NULL"));
        // Krani menyetujuinya dari HP lain lewat server, jadi catatan langsung antre tanpa menunggu persetujuan di HP ini.
        assertEquals(1, angka("SELECT COUNT(*) FROM sync_queue WHERE panen_id = '" + id + "' AND entitas = 'PANEN' AND status = 'MENUNGGU'"));
    }

    @Test
    public void kombinasiTanggalTphPemanenGandaDitolakTanpaMenyimpanApaPun() throws Exception {
        String pertama = mandorRepo().simpan(permintaan());
        File[] sebelum = folderFoto.listFiles();
        int fotoSebelum = sebelum == null ? 0 : sebelum.length;

        try {
            mandorRepo().simpan(permintaan());
            fail("kombinasi ganda harus ditolak");
        } catch (PanenRepository.PanenDuplikatException e) {
            assertEquals(pertama, e.panenId);
            assertTrue(e.getMessage(), e.getMessage().contains("sudah dicatat"));
        }
        assertEquals(1, angka("SELECT COUNT(*) FROM panen_header"));
        assertEquals(1, angka("SELECT COUNT(*) FROM sync_queue WHERE entitas = 'PANEN'"));
        File[] sesudah = folderFoto.listFiles();
        assertEquals("salinan foto catatan ganda dibuang", fotoSebelum, sesudah == null ? 0 : sesudah.length);

        // Tanggal lain, atau pemanen lain, atau TPH lain: bukan ganda.
        PanenRepository.Permintaan lain = permintaan();
        lain.tanggalPanen = "2026-11-11";
        mandorRepo().simpan(lain);
        PanenRepository.Permintaan pemanenLain = permintaan();
        pemanenLain.pemanenId = 2L;
        mandorRepo().simpan(pemanenLain);
        PanenRepository.Permintaan tphLain = permintaan();
        tphLain.tphId = room.masterDao().daftarTph().get(1).tphId;
        mandorRepo().simpan(tphLain);
        assertEquals(4, angka("SELECT COUNT(*) FROM panen_header"));
    }

    @Test
    public void catatanDitolakYangSamaMemberiPetunjukEditBukanCatatBaru() throws Exception {
        String id = mandorRepo().simpan(permintaan());
        db.execSQL("UPDATE panen_header SET status = 'DITOLAK', pesan_server = 'Blok tidak ditemukan di ERP' WHERE panen_id = ?", new Object[]{id});
        try {
            mandorRepo().simpan(permintaan());
            fail("harus ditolak");
        } catch (PanenRepository.PanenDuplikatException e) {
            assertEquals("DITOLAK", e.status);
            assertTrue(e.getMessage(), e.getMessage().contains("Edit dan kirim ulang"));
        }
    }

    @Test
    public void perbaikiDitolakMenggantiIsiMenandaiKirimUlangDanMengantreLagi() throws Exception {
        String id = mandorRepo().simpan(permintaan());
        // Server atau ERP menolak: antrean lama GAGAL, catatan DITOLAK.
        db.execSQL("UPDATE panen_header SET status = 'DITOLAK', pesan_server = 'Blok tidak ditemukan di ERP' WHERE panen_id = ?", new Object[]{id});
        db.execSQL("UPDATE sync_queue SET status = 'GAGAL' WHERE panen_id = ?", new Object[]{id});

        PanenRepository.Permintaan r = permintaan();
        r.tanggalPanen = "2026-11-11";
        r.pemanenId = 2L;
        r.brondolanKg = 3;
        r.catatan = "diperbaiki";
        r.jumlahFinal = new int[]{0, 0, 1, 5, 0, 0}; // total 6
        mandorRepo().perbaikiDitolak(id, r);

        assertTrue(r.disetujuiOtomatis);
        assertEquals(1, angka("SELECT COUNT(*) FROM panen_header WHERE panen_id = '" + id + "' AND status = 'DISETUJUI' "
                + "AND kirim_ulang = 1 AND pesan_server IS NULL AND tanggal_panen = '2026-11-11' AND brondolan_kg = 3 "
                + "AND catatan = 'diperbaiki' AND disetujui_oleh = 2"));
        assertEquals(6, angka("SELECT SUM(jml_final) FROM panen_grading WHERE panen_id = '" + id + "'"));
        assertEquals("hitungan kamera tidak berubah", 10, angka("SELECT SUM(jml_ai) FROM panen_grading WHERE panen_id = '" + id + "'"));
        assertEquals(1, angka("SELECT COUNT(*) FROM panen_pemanen WHERE panen_id = '" + id + "' AND pemanen_id = 2 AND jjg_alokasi = 6"));
        assertEquals("antrean lama ditutup, satu baris baru menunggu", 1, angka("SELECT COUNT(*) FROM sync_queue WHERE panen_id = '"
                + id + "' AND entitas = 'PANEN' AND status = 'MENUNGGU'"));
        assertEquals(0, angka("SELECT COUNT(*) FROM sync_queue WHERE panen_id = '" + id + "' AND status = 'GAGAL'"));
        assertEquals(1, angka("SELECT COUNT(*) FROM audit_log WHERE aksi = 'UBAH' AND baris_id = '" + id + "' "
                + "AND nilai_lama LIKE '%DITOLAK%' AND nilai_baru LIKE '%kirim_ulang%'"));
        // Muatan kiriman membawa kirim_ulang dan isi baru.
        Hasil.PanenKirim kirim = room.syncDao().panenKirim(id);
        assertEquals(1, kirim.kirimUlang);
        assertEquals("2026-11-11", kirim.tanggalPanen);
    }

    @Test
    public void perbaikiDitolakOlehKraniMengembalikanKeDraftMenungguMandor() throws Exception {
        String id = repo.simpan(permintaan()); // krani (user 1)
        db.execSQL("UPDATE panen_header SET status = 'DITOLAK' WHERE panen_id = ?", new Object[]{id});
        PanenRepository.Permintaan r = permintaan();
        r.jumlahFinal = new int[]{0, 0, 0, 4, 0, 0};
        repo.perbaikiDitolak(id, r);
        assertFalse(r.disetujuiOtomatis);
        assertEquals(1, angka("SELECT COUNT(*) FROM panen_header WHERE panen_id = '" + id + "' AND status = 'DRAFT' "
                + "AND kirim_ulang = 1 AND disetujui_oleh IS NULL"));
        // Tetap antre untuk dikirim ulang sebagai MENUNGGU (krani menyetujuinya dari HP lain); persetujuan di HP ini tidak menggandakan antrean.
        assertEquals(1, angka("SELECT COUNT(*) FROM sync_queue WHERE panen_id = '" + id + "' AND status = 'MENUNGGU'"));
        assertEquals(1, mandorRepo().setujui(java.util.Collections.singletonList(id)));
        assertEquals("tidak ada antrean ganda", 1, angka("SELECT COUNT(*) FROM sync_queue WHERE panen_id = '" + id + "' AND status = 'MENUNGGU'"));
    }

    @Test
    public void catatanMenungguKraniBisaDiubahTetapiYangSudahDisetujuiTerkunci() throws Exception {
        String id = repo.simpan(permintaan()); // krani (user 1) tanpa hak setuju
        db.execSQL("UPDATE panen_header SET status = 'MENUNGGU_SETUJU' WHERE panen_id = ?", new Object[]{id});
        PanenRepository.Permintaan r = permintaan();
        r.jumlahFinal = new int[]{0, 0, 0, 6, 0, 0};
        repo.perbaikiDitolak(id, r);
        assertEquals(1, angka("SELECT COUNT(*) FROM panen_header WHERE panen_id = '" + id + "' AND status = 'DRAFT' AND kirim_ulang = 1"));
        assertEquals(1, angka("SELECT COUNT(*) FROM audit_log WHERE aksi = 'UBAH' AND baris_id = '" + id + "' AND nilai_lama LIKE '%MENUNGGU_SETUJU%'"));
        // Krani sudah menyetujui (status dari server menjadi TERKIRIM): tidak bisa diubah lagi.
        db.execSQL("UPDATE panen_header SET status = 'TERKIRIM', kirim_ulang = 0 WHERE panen_id = ?", new Object[]{id});
        try {
            repo.perbaikiDitolak(id, permintaan());
            fail("catatan yang sudah disetujui krani tidak boleh diubah");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("belum disetujui krani"));
        }
    }

    @Test
    public void perbaikiDitolakMenolakYangBukanDitolakBukanMilikSendiriDanTotalNol() throws Exception {
        String id = mandorRepo().simpan(permintaan());
        try { // belum ditolak
            mandorRepo().perbaikiDitolak(id, permintaan());
            fail("catatan yang tidak ditolak tidak boleh diperbaiki");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("ditolak"));
        }
        db.execSQL("UPDATE panen_header SET status = 'DITOLAK' WHERE panen_id = ?", new Object[]{id});
        try { // krani (user 1) tanpa panen.koreksi, catatan milik mandor
            repo.perbaikiDitolak(id, permintaan());
            fail("catatan milik orang lain tidak boleh diperbaiki");
        } catch (SecurityException e) {
            // benar
        }
        PanenRepository.Permintaan nol = permintaan();
        nol.jumlahFinal = new int[6];
        try {
            mandorRepo().perbaikiDitolak(id, nol);
            fail("total nol");
        } catch (IllegalArgumentException e) {
            // benar
        }
        PanenRepository.Permintaan tanpaPemanen = permintaan();
        tanpaPemanen.pemanenId = null;
        try {
            mandorRepo().perbaikiDitolak(id, tanpaPemanen);
            fail("pemanen wajib");
        } catch (IllegalArgumentException e) {
            // benar
        }
        assertEquals("tidak ada yang berubah", 1, angka("SELECT COUNT(*) FROM panen_header WHERE panen_id = '" + id + "' AND status = 'DITOLAK' AND kirim_ulang = 0"));
    }

    @Test
    public void perbaikiDitolakBentrokDenganCatatanLainDitolak() throws Exception {
        String a = mandorRepo().simpan(permintaan());
        PanenRepository.Permintaan lain = permintaan();
        lain.tanggalPanen = "2026-11-11";
        mandorRepo().simpan(lain);
        db.execSQL("UPDATE panen_header SET status = 'DITOLAK' WHERE panen_id = ?", new Object[]{a});
        PanenRepository.Permintaan r = permintaan();
        r.tanggalPanen = "2026-11-11"; // menabrak catatan kedua (TPH dan pemanen sama)
        try {
            mandorRepo().perbaikiDitolak(a, r);
            fail("bentrok");
        } catch (PanenRepository.PanenDuplikatException e) {
            // benar
        }
        assertEquals("2026-11-10", room.panenDao().headerById(a).tanggalPanen);
    }
}
