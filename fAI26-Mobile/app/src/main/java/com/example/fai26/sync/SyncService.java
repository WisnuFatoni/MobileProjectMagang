package com.example.fai26.sync;

import com.example.fai26.db.AppDatabase;
import com.example.fai26.db.StatusPanen;
import com.example.fai26.db.Waktu;
import com.example.fai26.db.dao.Hasil;
import com.example.fai26.db.dao.SyncDao;
import com.example.fai26.db.entity.AuditLog;
import com.example.fai26.db.entity.SyncQueue;

import org.json.JSONException;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * Mesin sinkronisasi HP ke server. Urutannya:
 * 1. catatan yang sudah disetujui mandor masuk antrean (PANEN);
 * 2. antrean dikirim dari yang paling lama: PANEN, lalu FOTO milik catatan itu, dan TPH_KOORDINAT (titik GPS TPH);
 * 3. audit_log yang belum terkirim dikirim berkelompok;
 * 4. status Diterima atau Ditolak dari server diambil dan dicatat di catatan.
 *
 * Aman diulang: server mengenali panen_id, foto, dan audit_id yang sama. Kegagalan jaringan dicoba lagi dengan
 * jeda yang makin panjang; kegagalan yang ditolak server (isi tidak sah) ditandai GAGAL sampai "Kirim ulang".
 */
public class SyncService {

    public static final int MAKS_PERCOBAAN = 8;
    static final long JEDA_AWAL_MS = 30_000L;
    static final long JEDA_MAKS_MS = 30 * 60_000L;
    private static final int BATCH_AUDIT = 200;
    private static final int MAKS_BATCH_AUDIT = 10;
    private static final long SISA_TOKEN_MS = 30_000L;

    /** Ringkasan satu kali jalan. */
    public static final class Ringkasan {
        public int panenTerkirim;
        public int fotoTerkirim;
        /** Koordinat TPH yang direkam di HP dan sudah sampai ke server. */
        public int koordinatTerkirim;
        public int auditTerkirim;
        /** Inspeksi IPD dan fotonya yang sudah sampai ke server. */
        public int ipdTerkirim;
        public int fotoIpdTerkirim;
        public int ditolak;
        public int statusDiperbarui;
        /** Master baru diunduh dan diterapkan pada putaran ini. */
        public boolean masterDiperbarui;
        /** Master gagal diperbarui (tidak menghentikan pengiriman); null bila tidak ada masalah. */
        public String masalahMaster;
        /** Pesan untuk pengguna bila ada masalah, atau null. */
        public String masalah;
        /** Perlu login online ulang (sesi server tidak berlaku atau belum pernah login online). */
        public boolean perluLogin;
        /** Tidak berjalan karena sinkronisasi lain sedang berjalan. */
        public boolean sedangBerjalan;
        /** Ada kegagalan sementara (jaringan atau server): layak dicoba lagi nanti. */
        public boolean bisaDicobaLagi;
        /** Catatan milik orang lain yang menunggu pemiliknya (atau mandor) login online. */
        public int menungguPemilik;
        /** Perubahan yang ditolak karena krani sudah menyetujui catatannya (server menjawab 423). */
        public int terkunci;

        public boolean adaYangDikirim() {
            return panenTerkirim + fotoTerkirim + koordinatTerkirim + auditTerkirim + statusDiperbarui + ipdTerkirim + fotoIpdTerkirim > 0
                    || masterDiperbarui;
        }

        public String teks() {
            if (sedangBerjalan) return "Sinkronisasi sedang berjalan";
            StringBuilder s = new StringBuilder();
            if (panenTerkirim > 0) s.append(panenTerkirim).append(" catatan terkirim");
            if (fotoTerkirim > 0) tambah(s, fotoTerkirim + " foto");
            if (ipdTerkirim > 0) tambah(s, ipdTerkirim + " inspeksi IPD");
            if (fotoIpdTerkirim > 0) tambah(s, fotoIpdTerkirim + " foto IPD");
            if (koordinatTerkirim > 0) tambah(s, koordinatTerkirim + " koordinat TPH");
            if (auditTerkirim > 0) tambah(s, auditTerkirim + " audit");
            if (statusDiperbarui > 0) tambah(s, statusDiperbarui + " status diperbarui");
            if (masterDiperbarui) tambah(s, "master diperbarui");
            if (ditolak > 0) tambah(s, ditolak + " ditolak server");
            if (terkunci > 0) tambah(s, terkunci + " perubahan tidak diterima (sudah disetujui krani)");
            if (menungguPemilik > 0) tambah(s, menungguPemilik + " menunggu pemiliknya login");
            if (s.length() == 0 && masalah == null && masalahMaster == null) s.append("Tidak ada yang perlu dikirim");
            if (masalahMaster != null) tambah(s, masalahMaster);
            if (masalah != null) tambah(s, masalah);
            return s.toString();
        }

        private static void tambah(StringBuilder s, String t) {
            if (s.length() > 0) s.append(" • ");
            s.append(t);
        }
    }

    private static final AtomicBoolean BERJALAN = new AtomicBoolean(false);

    /** Apakah ada sinkronisasi yang sedang berjalan di proses ini (dipakai tes untuk menunggu sinkron latar selesai). */
    public static boolean sedangBerjalan() {
        return BERJALAN.get();
    }

    private final AppDatabase db;
    private final ServerApi api;
    private final PengaturanSync pref;
    private final LongSupplier jam;

    public SyncService(AppDatabase db, ServerApi api, PengaturanSync pref) {
        this(db, api, pref, System::currentTimeMillis);
    }

    public SyncService(AppDatabase db, ServerApi api, PengaturanSync pref, LongSupplier jam) {
        this.db = db;
        this.api = api;
        this.pref = pref;
        this.jam = jam;
    }

    /** Memasukkan satu catatan yang baru disetujui ke antrean. Dipanggil dari repository dalam transaksi yang sama. */
    public static void antrekan(SyncDao dao, String panenId, String waktu, long userId) {
        if (dao.jumlahAntreanPanenMenunggu(panenId) > 0) return; // sudah antre (mis. disimpan lalu disetujui di HP yang sama)
        SyncQueue q = new SyncQueue();
        q.panenId = panenId;
        q.entitas = "PANEN";
        q.aksi = "KIRIM";
        q.dibuatPada = waktu;
        q.dibuatOleh = userId;
        dao.insertQueue(q);
    }

    /**
     * Memasukkan koordinat TPH yang baru direkam di HP ke antrean (TPH_KOORDINAT). Dipanggil dari repository dalam
     * transaksi yang sama dengan penyimpanan koordinatnya, jadi tidak mungkin tersimpan tanpa antrean.
     */
    public static void antrekanKoordinat(SyncDao dao, long tphId, double lat, double lon, double akurasiM,
                                         String waktu, long userId) {
        SyncQueue q = new SyncQueue();
        q.panenId = null;
        q.entitas = ENTITAS_KOORDINAT;
        q.aksi = "KIRIM";
        q.payloadJson = payloadKoordinat(tphId, lat, lon, akurasiM);
        q.dibuatPada = waktu;
        q.dibuatOleh = userId;
        dao.insertQueue(q);
    }

    /** Memasukkan satu inspeksi IPD ke antrean (payload = ipd_id). Dipanggil dari repository dalam transaksi yang sama. */
    public static void antrekanIpd(SyncDao dao, String ipdId, String waktu, long userId) {
        SyncQueue q = new SyncQueue();
        q.panenId = null;
        q.entitas = "IPD";
        q.aksi = "KIRIM";
        q.payloadJson = ipdId;
        q.dibuatPada = waktu;
        q.dibuatOleh = userId;
        dao.insertQueue(q);
    }

    static final String ENTITAS_KOORDINAT = "TPH_KOORDINAT";

    static String payloadKoordinat(long tphId, double lat, double lon, double akurasiM) {
        try {
            return new org.json.JSONObject().put("tph_id", tphId).put("lat", lat).put("lon", lon)
                    .put("akurasi_m", akurasiM).toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e); // hanya bila angka NaN/tak hingga: sudah ditolak sebelum sampai sini
        }
    }

    /**
     * Menjalankan satu putaran sinkronisasi (blocking; panggil dari thread latar).
     *
     * @param paksa true bila pengguna menekan "Kirim sekarang": jeda coba-ulang diabaikan.
     */
    public Ringkasan jalankan(boolean paksa) {
        Ringkasan hasil = new Ringkasan();
        if (!BERJALAN.compareAndSet(false, true)) {
            hasil.sedangBerjalan = true;
            return hasil;
        }
        try {
            if (!pref.serverSudahDiatur()) {
                hasil.masalah = "Alamat server belum diatur";
                return hasil;
            }
            SyncDao dao = db.syncDao();
            masukkanYangBelumAntre(dao);

            Sesi sesi = new Sesi();
            final boolean punyaToken = pref.adaToken();
            try {
                sesi.token = tokenValid();
            } catch (ApiException e) {
                return gagalToken(hasil, e, punyaToken);
            }

            ambilMaster(sesi, hasil);
            if (hasil.perluLogin) return hasil;
            try {
                kirimAntrean(dao, sesi, paksa, hasil);
                if (!hasil.perluLogin && hasil.masalah == null) kirimAudit(dao, sesi, hasil);
                if (!hasil.perluLogin) ambilStatus(dao, sesi, hasil);
                if (!hasil.perluLogin) ambilStatusIpd(sesi, hasil);
            } catch (ApiException e) {
                if (e.tidakLogin()) {
                    hasil.perluLogin = true;
                    hasil.masalah = "Sesi server tidak berlaku. Login online ulang";
                } else {
                    hasil.masalah = e.getMessage();
                }
            }
            return hasil;
        } finally {
            pref.catatSinkron(jam.getAsLong(), hasil.teks());
            BERJALAN.set(false);
        }
    }

    // ---- Token -----------------------------------------------------------------------------------

    private static final class Sesi {
        String token;
        long userId = -1;
        boolean koreksi;
    }

    private String tokenValid() throws ApiException {
        String akses = pref.akses();
        if (akses != null && pref.aksesKedaluwarsa() > jam.getAsLong() + SISA_TOKEN_MS) return akses;
        return perbaruiToken();
    }

    private String perbaruiToken() throws ApiException {
        String refresh = pref.refresh();
        if (refresh == null) throw new ApiException(401, "Belum login online");
        try {
            ServerApi.Token t = api.refresh(refresh);
            pref.simpanToken(pref.tokenUser(), t);
            return t.akses;
        } catch (ApiException e) {
            if (e.tidakLogin()) pref.hapusToken();
            throw e;
        }
    }

    private Ringkasan gagalToken(Ringkasan hasil, ApiException e, boolean punyaToken) {
        if (e.tidakLogin()) {
            hasil.perluLogin = true;
            hasil.masalah = punyaToken
                    ? "Sesi server tidak berlaku. Login online ulang"
                    : "Belum login online. Login saat ada sinyal agar data bisa dikirim";
        } else {
            hasil.masalah = e.getMessage();
        }
        return hasil;
    }

    // ---- Master ----------------------------------------------------------------------------------

    /** Memperbarui master dari server. Gagal di sini tidak menghentikan pengiriman catatan. */
    private void ambilMaster(Sesi sesi, Ringkasan hasil) {
        MasterSync master = new MasterSync(db, jam);
        try {
            try {
                hasil.masterDiperbarui = master.ambil(api, sesi.token).berubah;
            } catch (ApiException e) {
                if (!e.tidakLogin()) throw e;
                sesi.token = perbaruiToken();
                hasil.masterDiperbarui = master.ambil(api, sesi.token).berubah;
            }
        } catch (ApiException e) {
            if (e.tidakLogin()) {
                hasil.perluLogin = true;
                hasil.masalah = "Sesi server tidak berlaku. Login online ulang";
            } else {
                hasil.masalahMaster = "Master belum diperbarui: " + e.getMessage();
                hasil.bisaDicobaLagi |= e.bisaDicobaLagi();
            }
        }
    }

    // ---- Antrean ---------------------------------------------------------------------------------

    private void masukkanYangBelumAntre(SyncDao dao) {
        List<String> ids = dao.disetujuiTanpaAntrean();
        if (ids.isEmpty()) return;
        final String waktu = Waktu.utc(jam.getAsLong());
        db.runInTransaction(() -> {
            for (String id : ids) antrekan(dao, id, waktu, 0);
        });
    }

    private boolean sudahWaktunya(SyncQueue q, boolean paksa) {
        if (paksa || q.percobaan == 0 || q.cobaTerakhir == null) return true;
        long jeda = Math.min(JEDA_MAKS_MS, JEDA_AWAL_MS << Math.min(20, (int) q.percobaan - 1));
        return jam.getAsLong() - Waktu.keMillis(q.cobaTerakhir) >= jeda;
    }

    /** Siapa yang boleh mengirim: pemilik catatan, atau pemegang panen.koreksi (mandor). */
    private boolean bolehMengirim(SyncDao dao, Sesi sesi, SyncQueue q) {
        if (q.panenId == null) {
            // Koordinat TPH: pemilik adalah yang merekamnya (dibuat_oleh); tanpa pemilik dikirim siapa saja.
            if (q.dibuatOleh == null || sesi.userId == q.dibuatOleh) return true;
            return sesi.koreksi;
        }
        Long pemilik = dao.pemilikPanen(q.panenId);
        if (pemilik == null) return true; // catatan hilang: biarkan diproses supaya ditandai gagal dengan jelas
        if (sesi.userId == pemilik) return true;
        return sesi.koreksi;
    }

    /** Mengenali pengguna pemilik token dari username yang tersimpan saat login online. */
    private void kenaliPengguna(Sesi sesi) {
        String username = pref.tokenUser();
        sesi.userId = -1;
        sesi.koreksi = false;
        if (username == null) return;
        com.example.fai26.db.entity.AppUser u = db.akunDao().cari(username);
        if (u == null) return;
        sesi.userId = u.userId;
        sesi.koreksi = db.akunDao().permission(u.userId, Waktu.tanggalLokalHariIni()).contains("panen.koreksi");
    }

    private void kirimAntrean(SyncDao dao, Sesi sesi, boolean paksa, Ringkasan hasil) throws ApiException {
        kenaliPengguna(sesi);
        // Antrean dibaca ulang tiap putaran: pengiriman catatan menambah baris FOTO yang harus ikut terkirim sekarang.
        // Setiap baris hanya dicoba sekali per jalankan().
        Set<Long> sudahDicoba = new HashSet<>();
        boolean adaBaru;
        do {
            adaBaru = false;
            for (SyncQueue q : dao.antreanMenunggu()) {
                if (!sudahDicoba.add(q.queueId)) continue;
                adaBaru = true;
                if (!sudahWaktunya(q, paksa)) continue;
                if (!bolehMengirim(dao, sesi, q)) {
                    if ("PANEN".equals(q.entitas) || "IPD".equals(q.entitas) || ENTITAS_KOORDINAT.equals(q.entitas)) hasil.menungguPemilik++;
                    continue;
                }
                try {
                    prosesDenganUlangToken(dao, sesi, q, hasil);
                } catch (ApiException e) {
                    if (e.tidakLogin()) throw e; // sesi benar-benar habis
                    catatGagal(dao, q, e, hasil);
                    // Jaringan atau server bermasalah: berhenti, sisanya akan gagal juga.
                    if (e.bisaDicobaLagi()) {
                        hasil.masalah = e.getMessage();
                        hasil.bisaDicobaLagi = true;
                        return;
                    }
                }
            }
        } while (adaBaru);
    }

    /** Memproses satu baris. Bila token ditolak (kedaluwarsa), perbarui sekali lalu ulangi. */
    private void prosesDenganUlangToken(SyncDao dao, Sesi sesi, SyncQueue q, Ringkasan hasil) throws ApiException {
        try {
            proses(dao, sesi.token, q, hasil);
        } catch (ApiException e) {
            if (!e.tidakLogin()) throw e;
            sesi.token = perbaruiToken();
            proses(dao, sesi.token, q, hasil);
        }
    }

    private void proses(SyncDao dao, String token, SyncQueue q, Ringkasan hasil) throws ApiException {
        switch (q.entitas) {
            case "PANEN":
                prosesPanen(dao, token, q, hasil);
                break;
            case "FOTO":
                prosesFoto(dao, token, q, hasil);
                break;
            case "IPD":
                prosesIpd(dao, token, q, hasil);
                break;
            case "IPD_FOTO":
                prosesFotoIpd(dao, token, q, hasil);
                break;
            case ENTITAS_KOORDINAT:
                prosesKoordinat(dao, token, q, hasil);
                break;
            default:
                throw new ApiException(422, "Jenis antrean tidak dikenal: " + q.entitas);
        }
    }

    private void prosesPanen(SyncDao dao, String token, SyncQueue q, Ringkasan hasil) throws ApiException {
        Hasil.PanenKirim p = q.panenId == null ? null : dao.panenKirim(q.panenId);
        if (p == null) throw new ApiException(404, "Catatan tidak ditemukan di HP");
        try {
            api.kirimPanen(token, PayloadBuilder.panen(p, dao.gradingKirim(p.panenId), dao.pemanenKirim(p.panenId)));
        } catch (JSONException e) {
            throw new ApiException(422, "Isi catatan tidak bisa dibentuk: " + e.getMessage(), e);
        } catch (ApiException e) {
            // 423: krani sudah menyetujui catatan ini, jadi perubahan dari HP tidak diterima. Ini bukan penolakan catatan:
            // yang berlaku di server adalah versi yang disetujui krani. Tandai terkirim dan hentikan kirim ulang.
            if (e.kode == 423 && p.kirimUlang != 0) {
                final String waktuKunci = Waktu.utc(jam.getAsLong());
                db.runInTransaction(() -> {
                    dao.setStatusKirim(p.panenId, StatusPanen.TERKIRIM, waktuKunci,
                            "Perubahan tidak diterima: " + e.getMessage() + ". Isi di server adalah versi yang disetujui krani.");
                    dao.resetKirimUlang(p.panenId);
                    dao.updateQueue(q.queueId, "SELESAI", q.percobaan, e.getMessage(), waktuKunci);
                });
                hasil.terkunci++;
                return;
            }
            throw e;
        }
        final String waktu = Waktu.utc(jam.getAsLong());
        final List<Hasil.FotoKirim> foto = dao.fotoBelumUnggah(p.panenId);
        db.runInTransaction(() -> {
            // Tanpa penyetuju (catatan mandor): sudah di server tetapi menunggu krani. Dengan penyetuju: siap ditarik ERP.
            dao.setStatusKirim(p.panenId, p.disetujuiUsername == null ? StatusPanen.MENUNGGU_SETUJU : StatusPanen.TERKIRIM, waktu, null);
            if (p.kirimUlang != 0) dao.resetKirimUlang(p.panenId);
            dao.updateQueue(q.queueId, "SELESAI", q.percobaan, null, waktu);
            for (Hasil.FotoKirim f : foto) {
                SyncQueue fq = new SyncQueue();
                fq.panenId = p.panenId;
                fq.entitas = "FOTO";
                fq.aksi = "KIRIM";
                fq.payloadJson = f.fotoId;
                fq.dibuatPada = waktu;
                dao.insertQueue(fq);
            }
        });
        hasil.panenTerkirim++;
    }

    /**
     * Mengirim satu inspeksi IPD. Server menjawab 423 bila asisten sudah menyetujuinya: perubahan dari HP tidak diterima, yang
     * berlaku di server adalah versi yang disetujui, jadi HP menandai DISETUJUI dan menghentikan kirim ulang.
     */
    private void prosesIpd(SyncDao dao, String token, SyncQueue q, Ringkasan hasil) throws ApiException {
        final com.example.fai26.db.dao.IpdDao ipd = db.ipdDao();
        final String id = q.payloadJson;
        com.example.fai26.db.dao.IpdHasil.HeaderKirim h = id == null ? null : ipd.headerKirim(id);
        if (h == null) throw new ApiException(404, "Inspeksi tidak ditemukan di HP");
        try {
            api.kirimIpd(token, PayloadBuilder.ipd(h, ipd.pokokKirim(id), ipd.temuanKirim(id), ipd.jejakKirim(id)));
        } catch (JSONException e) {
            throw new ApiException(422, "Isi inspeksi tidak bisa dibentuk: " + e.getMessage(), e);
        } catch (ApiException e) {
            if (e.kode == 423 && h.kirimUlang != 0) {
                final String waktuKunci = Waktu.utc(jam.getAsLong());
                db.runInTransaction(() -> {
                    ipd.setStatusKirim(id, com.example.fai26.db.IpdRepository.DISETUJUI, waktuKunci,
                            "Perubahan tidak diterima: " + e.getMessage() + ". Isi di server adalah versi yang disetujui asisten.");
                    ipd.resetKirimUlang(id);
                    dao.updateQueue(q.queueId, "SELESAI", q.percobaan, e.getMessage(), waktuKunci);
                });
                hasil.terkunci++;
                return;
            }
            throw e;
        }
        final String waktu = Waktu.utc(jam.getAsLong());
        // Pemegang ipd.setujui (asisten) yang mengirim langsung DISETUJUI; selain itu menunggu asisten divisi.
        final boolean langsungSah = db.akunDao().permission(h.userId, Waktu.tanggalLokalHariIni()).contains("ipd.setujui");
        final List<com.example.fai26.db.dao.IpdHasil.FotoKirim> foto = ipd.fotoBelumUnggah(id);
        db.runInTransaction(() -> {
            ipd.setStatusKirim(id, langsungSah ? com.example.fai26.db.IpdRepository.DISETUJUI : com.example.fai26.db.IpdRepository.MENUNGGU_SETUJU, waktu, null);
            if (h.kirimUlang != 0) ipd.resetKirimUlang(id);
            dao.updateQueue(q.queueId, "SELESAI", q.percobaan, null, waktu);
            for (com.example.fai26.db.dao.IpdHasil.FotoKirim f : foto) {
                SyncQueue fq = new SyncQueue();
                fq.panenId = null;
                fq.entitas = "IPD_FOTO";
                fq.aksi = "KIRIM";
                fq.payloadJson = f.fotoId;
                fq.dibuatPada = waktu;
                fq.dibuatOleh = h.userId;
                dao.insertQueue(fq);
            }
        });
        hasil.ipdTerkirim++;
    }

    /** Mengirim satu foto bukti IPD. Setelah server menerimanya, berkas di HP dihapus supaya memori tidak penuh. */
    private void prosesFotoIpd(SyncDao dao, String token, SyncQueue q, Ringkasan hasil) throws ApiException {
        final com.example.fai26.db.dao.IpdDao ipd = db.ipdDao();
        com.example.fai26.db.dao.IpdHasil.FotoKirim f = q.payloadJson == null ? null : ipd.fotoKirim(q.payloadJson);
        if (f == null) throw new ApiException(404, "Data foto IPD tidak ada di HP");
        if (f.pathLokal == null) { // sudah terkirim sebelumnya
            dao.updateQueue(q.queueId, "SELESAI", q.percobaan, null, Waktu.utc(jam.getAsLong()));
            return;
        }
        File berkas = new File(f.pathLokal);
        if (!berkas.exists()) throw new ApiException(404, "Berkas foto IPD tidak ada di HP");
        if (f.sha256 == null) throw new ApiException(422, "Foto IPD tanpa checksum");
        api.kirimFotoIpd(token, f.ipdId, f.fotoId, f.sha256, f.keterangan, (int) f.urut, berkas);
        final String waktu = Waktu.utc(jam.getAsLong());
        db.runInTransaction(() -> {
            ipd.fotoSudahDiunggah(f.fotoId, waktu);
            dao.updateQueue(q.queueId, "SELESAI", q.percobaan, null, waktu);
        });
        //noinspection ResultOfMethodCallIgnored
        berkas.delete(); // sudah di server: lepas dari HP
        hasil.fotoIpdTerkirim++;
    }

    private void prosesFoto(SyncDao dao, String token, SyncQueue q, Ringkasan hasil) throws ApiException {
        Hasil.FotoKirim f = q.payloadJson == null ? null : dao.fotoById(q.payloadJson);
        if (f == null) throw new ApiException(404, "Data foto tidak ada di HP");
        File berkas = new File(f.pathLokal);
        if (!berkas.exists()) throw new ApiException(404, "Berkas foto tidak ada di HP");
        if (f.sha256 == null) throw new ApiException(422, "Foto tanpa checksum");
        api.kirimFoto(token, f.panenId, f.fotoId, f.sha256, f.jenis, berkas);
        final String waktu = Waktu.utc(jam.getAsLong());
        db.runInTransaction(() -> {
            dao.fotoSudahDiunggah(f.fotoId, waktu);
            dao.updateQueue(q.queueId, "SELESAI", q.percobaan, null, waktu);
        });
        hasil.fotoTerkirim++;
    }

    /**
     * Mengirim koordinat TPH. Bila server sudah punya titik lain, HP mengikuti server (titik yang sudah benar tidak
     * boleh tertimpa titik dari HP); selisihnya hanya mengubah tampilan HP, tidak dikirim balik.
     */
    private void prosesKoordinat(SyncDao dao, String token, SyncQueue q, Ringkasan hasil) throws ApiException {
        final long tphId;
        final double lat;
        final double lon;
        final double akurasi;
        try {
            org.json.JSONObject p = new org.json.JSONObject(q.payloadJson == null ? "" : q.payloadJson);
            tphId = p.getLong("tph_id");
            lat = p.getDouble("lat");
            lon = p.getDouble("lon");
            akurasi = p.getDouble("akurasi_m");
        } catch (JSONException e) {
            throw new ApiException(422, "Isi antrean koordinat TPH rusak", e);
        }
        ServerApi.KoordinatHasil k = api.kirimKoordinatTph(token, tphId, lat, lon, akurasi);
        final String waktu = Waktu.utc(jam.getAsLong());
        db.runInTransaction(() -> {
            if (!k.diterapkan) dao.ikutiKoordinatServer(tphId, k.lat, k.lon, waktu);
            dao.updateQueue(q.queueId, "SELESAI", q.percobaan, null, waktu);
        });
        hasil.koordinatTerkirim++;
    }

    private void catatGagal(SyncDao dao, SyncQueue q, ApiException e, Ringkasan hasil) {
        final String waktu = Waktu.utc(jam.getAsLong());
        final long percobaan = q.percobaan + 1;
        if (e.bisaDicobaLagi()) {
            // Tetap MENUNGGU dengan jeda makin panjang; sesudah terlalu banyak gagal baru ditandai GAGAL.
            String status = percobaan >= MAKS_PERCOBAAN ? "GAGAL" : "MENUNGGU";
            dao.updateQueue(q.queueId, status, percobaan, e.getMessage(), waktu);
            return;
        }
        // Ditolak server (isi tidak sah, tidak berhak, dan sebagainya): mencoba lagi tidak membantu.
        db.runInTransaction(() -> {
            dao.updateQueue(q.queueId, "GAGAL", percobaan, e.getMessage(), waktu);
            if ("PANEN".equals(q.entitas) && q.panenId != null) {
                dao.setStatusKirim(q.panenId, StatusPanen.DITOLAK, waktu, e.getMessage());
            }
            if ("IPD".equals(q.entitas) && q.payloadJson != null) {
                db.ipdDao().setStatusKirim(q.payloadJson, com.example.fai26.db.IpdRepository.DITOLAK, waktu, e.getMessage());
            }
        });
        hasil.ditolak++;
    }

    // ---- Audit -----------------------------------------------------------------------------------

    private void kirimAudit(SyncDao dao, Sesi sesi, Ringkasan hasil) throws ApiException {
        for (int i = 0; i < MAKS_BATCH_AUDIT; i++) {
            List<AuditLog> batch = dao.auditBelumKirim(BATCH_AUDIT);
            if (batch.isEmpty()) return;
            List<String> ids = new ArrayList<>();
            for (AuditLog a : batch) ids.add(a.auditId);
            int diterima;
            try {
                diterima = kirimAuditSekali(sesi, batch);
            } catch (ApiException e) {
                if (e.tidakLogin()) throw e;
                hasil.masalah = "Audit belum terkirim: " + e.getMessage();
                hasil.bisaDicobaLagi = e.bisaDicobaLagi();
                return;
            }
            dao.auditSudahKirim(ids);
            hasil.auditTerkirim += diterima;
        }
    }

    private int kirimAuditSekali(Sesi sesi, List<AuditLog> batch) throws ApiException {
        try {
            try {
                return api.kirimAudit(sesi.token, PayloadBuilder.audit(batch));
            } catch (ApiException e) {
                if (!e.tidakLogin()) throw e;
                sesi.token = perbaruiToken();
                return api.kirimAudit(sesi.token, PayloadBuilder.audit(batch));
            }
        } catch (JSONException e) {
            throw new ApiException(422, "Isi audit tidak bisa dibentuk", e);
        }
    }

    // ---- Status dari server ----------------------------------------------------------------------

    private void ambilStatus(SyncDao dao, Sesi sesi, Ringkasan hasil) throws ApiException {
        List<ServerApi.StatusItem> daftar;
        try {
            try {
                daftar = api.statusSejak(sesi.token, pref.statusSejak());
            } catch (ApiException e) {
                if (!e.tidakLogin()) throw e;
                sesi.token = perbaruiToken();
                daftar = api.statusSejak(sesi.token, pref.statusSejak());
            }
        } catch (ApiException e) {
            if (e.tidakLogin()) throw e;
            if (hasil.masalah == null) hasil.masalah = "Status belum diperbarui: " + e.getMessage();
            hasil.bisaDicobaLagi |= e.bisaDicobaLagi();
            return;
        }
        if (daftar.isEmpty()) return;
        final String waktu = Waktu.utc(jam.getAsLong());
        long terbaru = pref.statusSejak();
        final List<ServerApi.StatusItem> salinan = daftar;
        final int[] berubah = {0};
        db.runInTransaction(() -> {
            for (ServerApi.StatusItem s : salinan) {
                berubah[0] += dao.setStatusServer(s.id, s.status, s.pesan, waktu);
            }
        });
        for (ServerApi.StatusItem s : daftar) terbaru = Math.max(terbaru, s.diubahMillis);
        pref.aturStatusSejak(terbaru);
        hasil.statusDiperbarui += berubah[0];
    }

    /**
     * Status inspeksi IPD dari server (menunggu asisten, disetujui, ditolak). Akun tanpa hak ipd.buat dijawab 403 dan dilewati
     * tanpa dianggap masalah.
     */
    private void ambilStatusIpd(Sesi sesi, Ringkasan hasil) throws ApiException {
        if (sesi.userId <= 0 || !db.akunDao().permission(sesi.userId, Waktu.tanggalLokalHariIni()).contains("ipd.buat")) return;
        List<ServerApi.StatusItem> daftar;
        try {
            try {
                daftar = api.statusIpdSejak(sesi.token, pref.statusIpdSejak());
            } catch (ApiException e) {
                if (!e.tidakLogin()) throw e;
                sesi.token = perbaruiToken();
                daftar = api.statusIpdSejak(sesi.token, pref.statusIpdSejak());
            }
        } catch (ApiException e) {
            if (e.tidakLogin()) throw e;
            if (e.kode == 403 || e.kode == 404) return; // belum berhak, atau server belum punya modul IPD
            if (hasil.masalah == null) hasil.masalah = "Status IPD belum diperbarui: " + e.getMessage();
            hasil.bisaDicobaLagi |= e.bisaDicobaLagi();
            return;
        }
        if (daftar.isEmpty()) return;
        final String waktu = Waktu.utc(jam.getAsLong());
        long terbaru = pref.statusIpdSejak();
        final List<ServerApi.StatusItem> salinan = daftar;
        final int[] berubah = {0};
        db.runInTransaction(() -> {
            for (ServerApi.StatusItem s : salinan) berubah[0] += db.ipdDao().setStatusServer(s.id, s.status, s.pesan, waktu);
        });
        for (ServerApi.StatusItem s : daftar) terbaru = Math.max(terbaru, s.diubahMillis);
        pref.aturStatusIpdSejak(terbaru);
        hasil.statusDiperbarui += berubah[0];
    }

    /** Dipakai layar Akun: jumlah yang masih menunggu, yang gagal, dan audit yang belum terkirim. */
    public static long[] hitungAntrean(AppDatabase db) {
        SyncDao d = db.syncDao();
        return new long[]{d.jumlahMenunggu(), d.jumlahGagal(), d.jumlahAuditBelumKirim()};
    }

    /** Tombol "Kirim ulang" untuk yang GAGAL. */
    public static int kirimUlangYangGagal(AppDatabase db) {
        return db.syncDao().kirimUlangYangGagal(Waktu.utcSekarang());
    }
}
