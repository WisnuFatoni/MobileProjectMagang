package com.example.fai26.db;

import android.graphics.BitmapFactory;

import com.example.fai26.db.dao.Hasil;
import com.example.fai26.db.dao.PanenDao;
import com.example.fai26.db.entity.AuditLog;
import com.example.fai26.lokasi.Lokasi;
import com.example.fai26.db.entity.Deteksi;
import com.example.fai26.db.entity.Foto;
import com.example.fai26.db.entity.PanenGrading;
import com.example.fai26.db.entity.PanenHeader;
import com.example.fai26.db.entity.PanenPemanen;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Menyimpan dan membaca catatan panen. Setiap penyimpanan terjadi dalam satu transaksi
 * bersama baris audit_log-nya: bila salah satu gagal, keduanya dibatalkan.
 */
public class PanenRepository {

    /** Foto yang sudah disalin ke folder aplikasi dan siap dimasukkan ke database. */
    private static final class FotoSiap {
        String fotoId;
        File tujuan;
        String sha;
        Long lebar;
        Long tinggi;
        float[] deteksi;
    }

    /** Isi satu catatan scan yang akan disimpan. */
    public static class Permintaan {
        public long unitId;
        public long blokId;
        public long tphId;
        public Long pemanenId;
        /** Tanggal panen, format YYYY-MM-DD (bukan tanggal input). */
        public String tanggalPanen;
        public double brondolanKg;
        public String catatan;
        /** Hitungan kamera per nomor kelas model (0 sampai 5). */
        public int[] jumlahAi;
        /** Hitungan setelah dikoreksi krani, per nomor kelas model. */
        public int[] jumlahFinal;
        public File foto;
        /** Kotak deteksi pada foto: per deteksi 6 angka (kelas, confidence, x1, y1, x2, y2), 0..1. */
        public float[] deteksi;
        /** Cara TPH dipilih: "QR" (discan di TPH) atau "MANUAL" (dipilih dari daftar). Dicatat di audit_log. */
        public String sumberTph = "MANUAL";
        /** Titik GPS pencatat (bisa kosong bila GPS mati atau izin ditolak). */
        public Double gpsLat;
        public Double gpsLon;
        public Double gpsAkurasiM;
        public String gpsWaktu;
        public boolean gpsMock;
        /** Hasil: status lokasi yang dihitung saat menyimpan (diisi oleh simpan()). */
        public String statusLokasi;
        /** Hasil: true bila penyimpan memegang panen.setujui (mandor), jadi catatan langsung DISETUJUI dan masuk antrean kirim. */
        public boolean disetujuiOtomatis;
    }

    /**
     * Sudah ada catatan lain untuk tanggal + TPH + pemanen yang sama. Server menolaknya (409), jadi dicegah di HP
     * sebelum sempat tersimpan. {@link #status} adalah status catatan yang sudah ada (DITOLAK berarti perlu diperbaiki
     * lewat "Edit dan kirim ulang", bukan dicatat baru).
     */
    public static class PanenDuplikatException extends IllegalStateException {
        public final String panenId;
        public final String status;

        PanenDuplikatException(String panenId, String status) {
            super(StatusPanen.DITOLAK.equals(status)
                    ? "Catatan untuk TPH, pemanen, dan tanggal ini pernah ditolak. Buka Riwayat lalu pilih Edit dan kirim ulang."
                    : "TPH ini sudah dicatat untuk pemanen dan tanggal yang sama");
            this.panenId = panenId;
            this.status = status;
        }
    }

    private final AppDatabase db;
    private final File folderFoto;
    private final String deviceId;
    private final long userId;

    public PanenRepository(AppDatabase db, File folderFoto, String deviceId, long userId) {
        this.db = db;
        this.folderFoto = folderFoto;
        this.deviceId = deviceId;
        this.userId = userId;
    }

    /** @return panen_id (UUID) catatan yang tersimpan. */
    public String simpan(Permintaan r) throws IOException {
        // Keputusan 18 Nov: satu TPH hanya untuk satu pemanen, dan HVD1 mencatat satu baris per pemanen.
        if (r.pemanenId == null || r.pemanenId <= 0) {
            throw new IllegalArgumentException("Pemanen wajib dipilih (satu TPH hanya untuk satu pemanen)");
        }
        final PanenDao dao = db.panenDao();
        final String now = Waktu.utcSekarang();
        final String panenId = UUID.randomUUID().toString();

        // Foto disalin dulu. Bila transaksi gagal, salinannya dihapus lagi.
        final List<FotoSiap> fotoSiap = new ArrayList<>();
        try {
            siapkanFoto(fotoSiap, r.foto, r.deteksi);
        } catch (IOException | RuntimeException e) {
            hapusSalinan(fotoSiap);
            throw e;
        }

        try {
            db.runInTransaction(() -> {
                // Anti ganda (tanggal + TPH + pemanen), diperiksa dalam transaksi yang sama dengan penyimpanannya.
                Hasil.KombinasiAda dobel = dao.kombinasiAda(r.tanggalPanen, r.tphId, r.pemanenId, "");
                if (dobel != null) throw new PanenDuplikatException(dobel.panenId, dobel.status);
                // Mandor (pemegang panen.setujui) yang menginput menyetujui sendiri: tidak ada langkah persetujuan terpisah.
                final boolean setujuSendiri = dao.izinPanenSetujui(userId, Waktu.tanggalLokalHariIni()) > 0;
                r.disetujuiOtomatis = setujuSendiri;
                List<Hasil.KelasLabel> kelas = dao.kelasLabel();
                List<AuditLog> audit = new ArrayList<>();

                PanenHeader h = new PanenHeader();
                h.panenId = panenId;
                h.unitId = r.unitId;
                h.blokId = r.blokId;
                h.tphId = r.tphId;
                h.userId = userId;
                h.deviceId = deviceId;
                h.modelId = dao.modelAktif();
                h.tanggalPanen = r.tanggalPanen;
                h.brondolanKg = r.brondolanKg;
                h.catatan = r.catatan;
                h.gpsLat = r.gpsLat;
                h.gpsLon = r.gpsLon;
                h.gpsAkurasiM = r.gpsAkurasiM;
                h.gpsWaktu = r.gpsWaktu;
                h.gpsMock = r.gpsMock ? 1 : 0;
                Hasil.Koordinat tphXY = dao.koordinatTph(r.tphId);
                Double tphLat = tphXY == null ? null : tphXY.lat;
                Double tphLon = tphXY == null ? null : tphXY.lon;
                h.statusLokasi = Lokasi.status(tphLat, tphLon, r.gpsLat, r.gpsLon, r.gpsAkurasiM);
                if (r.gpsLat != null && r.gpsLon != null && tphLat != null && tphLon != null) {
                    h.jarakKeTphM = Lokasi.jarakMeter(tphLat, tphLon, r.gpsLat, r.gpsLon);
                }
                r.statusLokasi = h.statusLokasi;
                h.status = setujuSendiri ? StatusPanen.DISETUJUI : StatusPanen.DRAFT;
                if (setujuSendiri) {
                    h.disetujuiOleh = userId;
                    h.disetujuiPada = now;
                }
                h.dibuatPada = now;
                h.dibuatOleh = userId;
                dao.insertHeader(h);
                // Catatan mandor (tanpa hak setuju) juga langsung antre: server menyimpannya MENUNGGU, krani menyetujui dari HP-nya.
                com.example.fai26.sync.SyncService.antrekan(db.syncDao(), panenId, now, userId);
                if (setujuSendiri) {
                    AuditLog setuju = audit("panen_header", panenId, now, json(
                            "status", StatusPanen.DISETUJUI, "disetujui_oleh", userId, "disetujui_pada", now));
                    setuju.aksi = "SETUJU";
                    setuju.nilaiLama = json("status", StatusPanen.DRAFT);
                    audit.add(setuju);
                }
                audit.add(audit("panen_header", panenId, now, json(
                        "tanggal_panen", h.tanggalPanen, "blok_id", h.blokId, "tph_id", h.tphId,
                        "brondolan_kg", h.brondolanKg, "status", h.status, "sumber_tph", r.sumberTph,
                        "status_lokasi", h.statusLokasi, "jarak_ke_tph_m", h.jarakKeTphM,
                        "gps_akurasi_m", h.gpsAkurasiM, "gps_mock", h.gpsMock)));

                // Satu baris per kelas, termasuk yang nol.
                List<PanenGrading> grading = new ArrayList<>();
                for (Hasil.KelasLabel k : kelas) {
                    int label = (int) k.label;
                    PanenGrading g = new PanenGrading();
                    g.panenId = panenId;
                    g.kelasId = k.kelasId;
                    g.jmlAi = r.jumlahAi[label];
                    g.jmlFinal = r.jumlahFinal[label];
                    g.diubahManual = g.jmlAi != g.jmlFinal ? 1 : 0;
                    g.dibuatPada = now;
                    g.dibuatOleh = userId;
                    grading.add(g);
                }
                long[] gradingIds = dao.insertGrading(grading);
                for (int i = 0; i < grading.size(); i++) {
                    PanenGrading g = grading.get(i);
                    audit.add(audit("panen_grading", String.valueOf(gradingIds[i]), now, json(
                            "kelas_id", g.kelasId, "jml_ai", g.jmlAi, "jml_final", g.jmlFinal,
                            "diubah_manual", g.diubahManual)));
                }

                {
                    int total = 0;
                    for (int v : r.jumlahFinal) total += v;
                    PanenPemanen p = new PanenPemanen();
                    p.panenId = panenId;
                    p.pemanenId = r.pemanenId;
                    p.jjgAlokasi = (long) total; // satu TPH satu pemanen: seluruh janjang dikreditkan ke pemanen itu (server menolak bila tidak sama dengan total)
                    p.dibuatPada = now;
                    p.dibuatOleh = userId;
                    long ppId = dao.insertPemanen(p);
                    audit.add(audit("panen_pemanen", String.valueOf(ppId), now, json(
                            "pemanen_id", p.pemanenId, "jjg_alokasi", p.jjgAlokasi)));
                }

                for (FotoSiap fs : fotoSiap) {
                    Foto f = new Foto();
                    f.fotoId = fs.fotoId;
                    f.panenId = panenId;
                    f.jenis = "DETEKSI";
                    f.pathLokal = fs.tujuan.getAbsolutePath();
                    f.sha256 = fs.sha;
                    f.lebar = fs.lebar;
                    f.tinggi = fs.tinggi;
                    f.dibuatPada = now;
                    f.dibuatOleh = userId;
                    dao.insertFoto(f);
                    audit.add(audit("foto", fs.fotoId, now, json("jenis", f.jenis, "sha256", f.sha256)));

                    if (fs.deteksi != null) {
                        List<Deteksi> kotak = new ArrayList<>();
                        for (int i = 0; i + 5 < fs.deteksi.length; i += 6) {
                            long kelasId = kelasIdDariLabel(kelas, Math.round(fs.deteksi[i]));
                            if (kelasId < 0) continue;
                            Deteksi d = new Deteksi();
                            d.fotoId = fs.fotoId;
                            d.kelasId = kelasId;
                            d.confidence = fs.deteksi[i + 1];
                            d.x = (double) fs.deteksi[i + 2];
                            d.y = (double) fs.deteksi[i + 3];
                            d.w = (double) (fs.deteksi[i + 4] - fs.deteksi[i + 2]);
                            d.h = (double) (fs.deteksi[i + 5] - fs.deteksi[i + 3]);
                            d.dibuatPada = now;
                            d.dibuatOleh = userId;
                            kotak.add(d);
                        }
                        dao.insertDeteksi(kotak);
                    }
                }
                dao.insertAudit(audit);
            });
        } catch (RuntimeException e) {
            hapusSalinan(fotoSiap);
            throw e;
        }
        return panenId;
    }

    /**
     * Memperbaiki catatan yang DITOLAK (oleh ERP atau server), atau mengubah catatan yang sudah terkirim tetapi belum
     * disetujui krani (MENUNGGU_SETUJU), lalu mengirimnya ulang dengan panen_id yang sama. Setelah krani menyetujui,
     * server mengunci catatan (423) dan perubahan tidak diterima.
     * Yang bisa diubah: tanggal panen, pemanen, hitungan akhir per kelas, brondolan, dan catatan (TPH dan foto tetap).
     * Pemegang panen.setujui (mandor) langsung DISETUJUI dan masuk antrean; selain itu kembali DRAFT menunggu mandor.
     *
     * @throws IllegalStateException bila catatan tidak ada atau statusnya bukan DITOLAK atau MENUNGGU_SETUJU.
     * @throws SecurityException bila bukan pencatatnya dan tanpa panen.koreksi.
     * @throws PanenDuplikatException bila tanggal + TPH + pemanen baru bentrok dengan catatan lain.
     */
    public void perbaikiDitolak(String panenId, Permintaan r) {
        if (r.pemanenId == null || r.pemanenId <= 0) {
            throw new IllegalArgumentException("Pemanen wajib dipilih (satu TPH hanya untuk satu pemanen)");
        }
        int total = 0;
        for (int v : r.jumlahFinal) total += v;
        if (total <= 0) throw new IllegalArgumentException("Jumlah tandan tidak boleh nol");
        final int jumlahTotal = total;
        final PanenDao dao = db.panenDao();
        final String now = Waktu.utcSekarang();
        db.runInTransaction(() -> {
            PanenHeader h = dao.headerById(panenId);
            if (h == null || !bisaDiubah(h.status)) {
                throw new IllegalStateException("Hanya catatan yang ditolak atau belum disetujui krani yang bisa diubah");
            }
            List<String> izin = db.akunDao().permission(userId, Waktu.tanggalLokalHariIni());
            if (h.userId != userId && !izin.contains("panen.koreksi")) {
                throw new SecurityException("Catatan ini milik pengguna lain");
            }
            Hasil.KombinasiAda dobel = dao.kombinasiAda(r.tanggalPanen, h.tphId, r.pemanenId, panenId);
            if (dobel != null) throw new PanenDuplikatException(dobel.panenId, dobel.status);

            final boolean setujuSendiri = izin.contains("panen.setujui");
            r.disetujuiOtomatis = setujuSendiri;
            final String status = setujuSendiri ? StatusPanen.DISETUJUI : StatusPanen.DRAFT;
            if (dao.perbaikiDitolak(panenId, r.tanggalPanen, r.brondolanKg, r.catatan, status,
                    setujuSendiri ? userId : null, setujuSendiri ? now : null, now, userId) != 1) {
                throw new IllegalStateException("Catatan sudah berubah status; muat ulang Riwayat");
            }
            for (Hasil.KelasLabel k : dao.kelasLabel()) {
                dao.ubahGrading(panenId, k.kelasId, r.jumlahFinal[(int) k.label], now, userId);
            }
            dao.ubahPemanen(panenId, r.pemanenId, jumlahTotal, now, userId);
            dao.tutupAntreanPanenLama(panenId, now);
            List<AuditLog> audit = new ArrayList<>();
            AuditLog a = audit("panen_header", panenId, now, json(
                    "status", status, "tanggal_panen", r.tanggalPanen, "brondolan_kg", r.brondolanKg,
                    "pemanen_id", r.pemanenId, "jumlah_total", jumlahTotal, "kirim_ulang", 1));
            a.aksi = "UBAH";
            a.nilaiLama = json("status", h.status, "pesan_server", h.pesanServer);
            audit.add(a);
            com.example.fai26.sync.SyncService.antrekan(db.syncDao(), panenId, now, userId);
            if (setujuSendiri) {
                AuditLog setuju = audit("panen_header", panenId, now, json(
                        "status", StatusPanen.DISETUJUI, "disetujui_oleh", userId, "disetujui_pada", now));
                setuju.aksi = "SETUJU";
                setuju.nilaiLama = json("status", StatusPanen.DRAFT);
                audit.add(setuju);
            }
            dao.insertAudit(audit);
        });
    }

    /** Catatan boleh diedit bila ditolak, atau sudah terkirim tetapi belum disetujui krani. */
    public static boolean bisaDiubah(String status) {
        return StatusPanen.DITOLAK.equals(status) || StatusPanen.MENUNGGU_SETUJU.equals(status);
    }

    /**
     * Mandor menyetujui catatan: status DRAFT menjadi DISETUJUI, lengkap dengan siapa dan kapan, dan satu
     * baris audit_log per catatan. Seluruhnya dalam satu transaksi.
     *
     * @return jumlah catatan yang benar-benar disetujui (yang sudah berubah status dilewati).
     * @throws SecurityException bila pengguna tidak memegang permission panen.setujui.
     */
    public int setujui(List<String> panenIds) {
        List<String> izin = db.akunDao().permission(userId, Waktu.tanggalLokalHariIni());
        if (!izin.contains("panen.setujui")) {
            throw new SecurityException("Tidak berhak menyetujui catatan panen");
        }
        final PanenDao dao = db.panenDao();
        final String now = Waktu.utcSekarang();
        final int[] jumlah = {0};
        db.runInTransaction(() -> {
            List<AuditLog> audit = new ArrayList<>();
            for (String id : panenIds) {
                if (dao.setujui(id, userId, now) == 1) {
                    jumlah[0]++;
                    com.example.fai26.sync.SyncService.antrekan(db.syncDao(), id, now, userId);
                    AuditLog a = audit("panen_header", id, now, json(
                            "status", StatusPanen.DISETUJUI, "disetujui_oleh", userId, "disetujui_pada", now));
                    a.aksi = "SETUJU";
                    a.nilaiLama = json("status", StatusPanen.DRAFT);
                    audit.add(a);
                }
            }
            if (!audit.isEmpty()) dao.insertAudit(audit);
        });
        return jumlah[0];
    }

    /**
     * Merekam koordinat TPH dari titik GPS di lokasi (dilakukan saat QR dipasang atau pertama kali dipakai).
     * Hanya bila TPH belum punya koordinat, supaya titik yang sudah benar tidak tertimpa oleh titik asal-asalan;
     * mengubah titik yang sudah ada lewat web admin. Tercatat di audit_log.
     *
     * @return true bila koordinat tersimpan, false bila TPH sudah punya koordinat.
     * @throws IllegalArgumentException bila akurasi lebih buruk dari batas.
     */
    public boolean rekamKoordinatTph(long tphId, double lat, double lon, double akurasiM) {
        if (akurasiM > com.example.fai26.lokasi.Lokasi.AKURASI_MAKS_M) {
            throw new IllegalArgumentException("Akurasi GPS " + Math.round(akurasiM)
                    + " m terlalu rendah. Coba di tempat terbuka.");
        }
        final PanenDao dao = db.panenDao();
        final String now = Waktu.utcSekarang();
        final boolean[] hasil = {false};
        db.runInTransaction(() -> {
            if (dao.setKoordinatTph(tphId, lat, lon, now, userId) == 1) {
                hasil[0] = true;
                AuditLog a = audit("tph", String.valueOf(tphId), now,
                        json("lat", lat, "lon", lon, "gps_akurasi_m", akurasiM));
                a.aksi = "UBAH";
                dao.insertAudit(java.util.Collections.singletonList(a));
                // Titik ini juga harus sampai ke server (PUT /api/tph/{id}/koordinat), lewat antrean sinkron.
                com.example.fai26.sync.SyncService.antrekanKoordinat(db.syncDao(), tphId, lat, lon, akurasiM, now, userId);
            }
        });
        return hasil[0];
    }

    private static long kelasIdDariLabel(List<Hasil.KelasLabel> kelas, long label) {
        for (Hasil.KelasLabel k : kelas) if (k.label == label) return k.kelasId;
        return -1;
    }

    private AuditLog audit(String tabel, String barisId, String waktu, String nilaiBaru) {
        AuditLog a = new AuditLog();
        a.auditId = UUID.randomUUID().toString();
        a.tabel = tabel;
        a.barisId = barisId;
        a.aksi = "BUAT";
        a.nilaiBaru = nilaiBaru;
        a.userId = userId;
        a.deviceId = deviceId;
        a.waktu = waktu;
        return a;
    }

    private static String json(Object... pasangan) {
        JSONObject o = new JSONObject();
        try {
            for (int i = 0; i < pasangan.length; i += 2) {
                o.put((String) pasangan[i], pasangan[i + 1] == null ? JSONObject.NULL : pasangan[i + 1]);
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        return o.toString();
    }

    /** Menyalin satu foto ke folder aplikasi, menghitung checksum dan ukurannya. Foto kosong atau hilang dilewati. */
    private void siapkanFoto(List<FotoSiap> hasil, File sumber, float[] deteksi) throws IOException {
        if (sumber == null || !sumber.exists()) return;
        if (!folderFoto.exists() && !folderFoto.mkdirs()) {
            throw new IOException("Gagal membuat folder foto");
        }
        FotoSiap f = new FotoSiap();
        f.fotoId = UUID.randomUUID().toString();
        f.tujuan = new File(folderFoto, f.fotoId + ".jpg");
        f.deteksi = deteksi;
        salin(sumber, f.tujuan);
        hasil.add(f); // setelah disalin: bila langkah berikutnya gagal, salinan ini ikut dihapus
        f.sha = sha256(f.tujuan);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.tujuan.getAbsolutePath(), o);
        if (o.outWidth > 0) {
            f.lebar = (long) o.outWidth;
            f.tinggi = (long) o.outHeight;
        }
    }

    private static void hapusSalinan(List<FotoSiap> daftar) {
        for (FotoSiap f : daftar) if (f.tujuan != null && !f.tujuan.delete()) f.tujuan.deleteOnExit();
    }

    private static void salin(File dari, File ke) throws IOException {
        try (InputStream in = new FileInputStream(dari);
             OutputStream out = new FileOutputStream(ke)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    private static String sha256(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
