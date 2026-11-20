package com.example.fai26.db;

import com.example.fai26.db.dao.IpdDao;
import com.example.fai26.db.dao.IpdHasil;
import com.example.fai26.db.entity.IpdFoto;
import com.example.fai26.db.entity.IpdHeader;
import com.example.fai26.db.entity.IpdPokok;
import com.example.fai26.db.entity.IpdTemuan;
import com.example.fai26.db.entity.KodeTemuan;
import com.example.fai26.ipd.FotoKompres;
import com.example.fai26.ipd.IpdDraf;
import com.example.fai26.sync.SyncService;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Menyimpan dan mengirim inspeksi IPD (Inspeksi Panen Detail). Satu inspeksi = satu pemanen. Alur status HP:
 * DRAFT (di HP) -> MENUNGGU_SETUJU (sudah di server, menunggu asisten) -> DISETUJUI atau DITOLAK. Selama belum disetujui
 * inspeksi boleh diubah dan dikirim ulang; setelah disetujui terkunci.
 */
public class IpdRepository {

    public static final String DRAFT = "DRAFT";
    public static final String MENUNGGU_SETUJU = "MENUNGGU_SETUJU";
    public static final String DISETUJUI = "DISETUJUI";
    public static final String DITOLAK = "DITOLAK";

    private final AppDatabase db;
    private final long userId;
    private final String deviceId;

    public IpdRepository(AppDatabase db, long userId, String deviceId) {
        this.db = db;
        this.userId = userId;
        this.deviceId = deviceId;
    }

    /** Boleh diubah: draf, sudah terkirim tetapi belum disetujui, atau ditolak. Yang DISETUJUI terkunci. */
    public static boolean bisaDiubah(String status) {
        return DRAFT.equals(status) || MENUNGGU_SETUJU.equals(status) || DITOLAK.equals(status);
    }

    /**
     * Menyimpan inspeksi sebagai draf (belum dikirim). Bila inspeksinya sudah pernah terkirim, perubahan ditandai
     * kirim_ulang supaya pengiriman berikutnya menggantikan isi di server.
     *
     * @return ipd_id (dibuat bila draf baru).
     * @throws IllegalStateException bila inspeksi sudah DISETUJUI (terkunci) atau milik pengguna lain tanpa hak.
     */
    public String simpanDraf(IpdDraf d) {
        final IpdDao dao = db.ipdDao();
        final String sekarang = Waktu.utcSekarang();
        if (d.ipdId == null || d.ipdId.isEmpty()) d.ipdId = UUID.randomUUID().toString();
        final String id = d.ipdId;
        db.runInTransaction(() -> {
            IpdHeader h = dao.header(id);
            boolean baru = h == null;
            if (baru) {
                h = new IpdHeader();
                h.ipdId = id;
                h.userId = userId;
                h.deviceId = deviceId;
                h.dibuatPada = sekarang;
                h.dibuatOleh = userId;
                h.status = DRAFT;
            } else {
                if (!bisaDiubah(h.status)) throw new IllegalStateException("Inspeksi sudah disetujui asisten, tidak bisa diubah lagi");
                if (h.userId != userId) throw new IllegalStateException("Inspeksi ini milik pengguna lain");
                if (!DRAFT.equals(h.status)) h.kirimUlang = 1; // sudah pernah di server: perubahan menggantikan isinya
                h.status = DRAFT;
                h.pesanServer = null;
                h.diubahPada = sekarang;
                h.diubahOleh = userId;
            }
            h.unitId = unitDivisi(dao, d.divisiId);
            h.divisiId = d.divisiId;
            h.blokId = d.blokId;
            h.pemanenId = d.pemanenId;
            h.tanggalInspeksi = d.tanggal;
            h.ancakNo = kosongJadiNull(d.ancakNo);
            h.catatan = kosongJadiNull(d.catatan);
            h.mandorBlokUserId = d.mandorBlokUserId;
            h.mandorAncakUserId = d.mandorAncakUserId;
            h.mandorBlokHrmb = hrmbMandor(d.mandorBlokUserId);
            h.mandorAncakHrmb = hrmbMandor(d.mandorAncakUserId);
            h.asistenUserId = asistenDivisi(dao, d.divisiId);
            if (baru) dao.insertHeader(h);
            else dao.updateHeader(h);

            dao.hapusPokok(id);
            Map<String, Long> kodeId = new HashMap<>();
            for (KodeTemuan k : dao.kodeAktif()) kodeId.put(k.kode, k.kodeId);
            for (IpdDraf.Pokok p : d.pokok) {
                IpdPokok pk = new IpdPokok();
                pk.ipdId = id;
                pk.noBaris = p.baris;
                pk.noPokok = p.pokok;
                pk.dibuatPada = sekarang;
                pk.dibuatOleh = userId;
                long pokokId = dao.insertPokok(pk);
                for (Map.Entry<String, Integer> e : p.temuan.entrySet()) {
                    Long kid = kodeId.get(e.getKey());
                    if (kid == null) throw new IllegalArgumentException("Kode temuan tidak dikenal: " + e.getKey());
                    IpdTemuan t = new IpdTemuan();
                    t.pokokId = pokokId;
                    t.kodeId = kid;
                    t.jumlah = Math.max(1, e.getValue());
                    t.dibuatPada = sekarang;
                    t.dibuatOleh = userId;
                    dao.insertTemuan(t);
                }
            }
        });
        d.statusLama = DRAFT;
        return id;
    }

    /**
     * Menyimpan lalu mengirim: inspeksi masuk antrean sinkron (valid otomatis saat dikirim). Wajib lengkap: divisi, blok, pemanen,
     * mandor blok, mandor ancak, dan paling sedikit satu pokok.
     */
    public String kirim(IpdDraf d) {
        pastikanLengkap(d);
        final String id = simpanDraf(d);
        final String sekarang = Waktu.utcSekarang();
        db.runInTransaction(() -> {
            db.ipdDao().tutupAntreanLama(id, sekarang);
            SyncService.antrekanIpd(db.syncDao(), id, sekarang, userId);
        });
        return id;
    }

    /** @throws IllegalArgumentException dengan pesan untuk pengguna bila isi belum lengkap. */
    public static void pastikanLengkap(IpdDraf d) {
        if (d.divisiId <= 0) throw new IllegalArgumentException("Pilih divisi dulu");
        if (d.blokId <= 0) throw new IllegalArgumentException("Pilih blok dulu");
        if (d.pemanenId <= 0) throw new IllegalArgumentException("Pilih pemanen dulu");
        if (d.mandorBlokUserId == null) throw new IllegalArgumentException("Pilih mandor di blok");
        if (d.mandorAncakUserId == null) throw new IllegalArgumentException("Pilih mandor di ancak");
        if (d.pokok.isEmpty()) throw new IllegalArgumentException("Catat minimal satu pokok yang diperiksa");
    }

    /** Memuat inspeksi tersimpan untuk dilanjutkan atau diubah. Null bila tidak ada. */
    public IpdDraf muat(String ipdId) {
        IpdDao dao = db.ipdDao();
        IpdHeader h = dao.header(ipdId);
        if (h == null) return null;
        IpdDraf d = new IpdDraf();
        d.ipdId = h.ipdId;
        d.tanggal = h.tanggalInspeksi;
        d.divisiId = h.divisiId;
        d.blokId = h.blokId;
        d.pemanenId = h.pemanenId;
        d.ancakNo = h.ancakNo;
        d.catatan = h.catatan;
        d.mandorBlokUserId = h.mandorBlokUserId;
        d.mandorAncakUserId = h.mandorAncakUserId;
        d.statusLama = h.status;
        IpdDraf.Pokok terakhir = null;
        for (IpdHasil.PokokBaris b : dao.pokokBaris(ipdId)) {
            if (terakhir == null || terakhir.baris != b.noBaris || terakhir.pokok != b.noPokok) {
                terakhir = new IpdDraf.Pokok((int) b.noBaris, (int) b.noPokok);
                d.pokok.add(terakhir);
            }
            if (b.kode != null) terakhir.temuan.put(b.kode, (int) (b.jumlah == null ? 1 : b.jumlah));
        }
        return d;
    }

    /** Menghapus draf yang belum pernah dikirim (beserta fotonya). Yang sudah terkirim tidak bisa dihapus. */
    public boolean hapusDraf(String ipdId) {
        List<IpdFoto> foto = db.ipdDao().foto(ipdId);
        if (db.ipdDao().hapusDraf(ipdId) == 0) return false;
        for (IpdFoto f : foto) hapusBerkas(f.pathLokal);
        return true;
    }

    // ---- Foto ----------------------------------------------------------------------------------------

    /** Mendaftarkan foto yang sudah dikompres. Inspeksinya harus sudah tersimpan (draf). */
    public IpdFoto tambahFoto(String ipdId, File berkas, FotoKompres.Hasil h, String keterangan) {
        IpdDao dao = db.ipdDao();
        IpdFoto f = new IpdFoto();
        f.fotoId = UUID.randomUUID().toString();
        f.ipdId = ipdId;
        f.keterangan = kosongJadiNull(keterangan);
        f.urut = dao.foto(ipdId).size();
        f.pathLokal = berkas.getAbsolutePath();
        f.sha256 = h.sha256;
        f.lebar = (long) h.lebar;
        f.tinggi = (long) h.tinggi;
        f.ukuranByte = h.ukuranByte;
        f.dibuatPada = Waktu.utcSekarang();
        f.dibuatOleh = userId;
        dao.insertFoto(f);
        return f;
    }

    public void hapusFoto(String fotoId) {
        IpdFoto f = db.ipdDao().fotoById(fotoId);
        if (f == null) return;
        db.ipdDao().hapusFoto(fotoId);
        hapusBerkas(f.pathLokal);
    }

    private static void hapusBerkas(String path) {
        if (path == null) return;
        //noinspection ResultOfMethodCallIgnored
        new File(path).delete();
    }

    // ---- Bantu -------------------------------------------------------------------------------------

    private static String kosongJadiNull(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    private long unitDivisi(IpdDao dao, long divisiId) {
        Long u = db.masterDao().unitIdDivisi(divisiId);
        if (u == null) throw new IllegalArgumentException("Divisi tidak dikenal");
        return u;
    }

    private Long asistenDivisi(IpdDao dao, long divisiId) {
        for (IpdHasil.DivisiPilihan x : dao.divisiPilihan()) if (x.divisiId == divisiId) return x.asistenUserId;
        return null;
    }

    private Long hrmbMandor(Long mandorUserId) {
        if (mandorUserId == null) return null;
        for (IpdHasil.OrangPilihan o : db.ipdDao().orangBerperan("MANDOR")) if (o.userId == mandorUserId) return o.hrmbCode;
        return null;
    }
}
