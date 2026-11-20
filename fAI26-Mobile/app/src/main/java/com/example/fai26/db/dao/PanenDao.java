package com.example.fai26.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.fai26.db.entity.AuditLog;
import com.example.fai26.db.entity.Deteksi;
import com.example.fai26.db.entity.Foto;
import com.example.fai26.db.entity.PanenGrading;
import com.example.fai26.db.entity.PanenHeader;
import com.example.fai26.db.entity.PanenPemanen;

import java.util.List;

@Dao
public interface PanenDao {

    @Insert
    void insertHeader(PanenHeader h);

    @Insert
    long[] insertGrading(List<PanenGrading> g);

    @Insert
    long insertPemanen(PanenPemanen p);

    @Insert
    void insertFoto(Foto f);

    @Insert
    void insertDeteksi(List<Deteksi> d);

    @Insert
    void insertAudit(List<AuditLog> a);

    @Query("SELECT kelas_id AS kelasId, label_model AS label FROM kelas_grading")
    List<Hasil.KelasLabel> kelasLabel();

    @Query("SELECT model_id FROM model_versi WHERE aktif = 1 ORDER BY model_id DESC LIMIT 1")
    Long modelAktif();

    @Query("SELECT COUNT(*) FROM panen_header WHERE dihapus_pada IS NULL")
    long jumlahPanen();

    @Query("SELECT nama || ' ' || versi FROM model_versi WHERE aktif = 1 ORDER BY model_id DESC LIMIT 1")
    String namaModelAktif();

    /**
     * Catatan terbaru. @param userId 0 untuk semua pengguna, selain itu hanya milik user tersebut.
     */
    @Query("SELECT h.panen_id AS panenId, h.dibuat_pada AS dibuatPada, h.status AS status, h.pesan_server AS pesanServer, "
            + "b.kode AS blok, t.no_tph AS noTph, h.status_lokasi AS statusLokasi, h.jarak_ke_tph_m AS jarakKeTphM, "
            + "(SELECT p.nama FROM panen_pemanen pp JOIN pemanen p ON p.pemanen_id = pp.pemanen_id "
            + " WHERE pp.panen_id = h.panen_id AND pp.dihapus_pada IS NULL LIMIT 1) AS pemanen, "
            + "(SELECT f.path_lokal FROM foto f WHERE f.panen_id = h.panen_id "
            + " AND f.dihapus_pada IS NULL LIMIT 1) AS fotoPath, "
            + "(SELECT COALESCE(SUM(g.jml_final), 0) FROM panen_grading g WHERE g.panen_id = h.panen_id "
            + " AND g.dihapus_pada IS NULL) AS total "
            + "FROM panen_header h JOIN blok b ON b.blok_id = h.blok_id "
            + "JOIN tph t ON t.tph_id = h.tph_id "
            + "WHERE h.dihapus_pada IS NULL AND (:userId = 0 OR h.user_id = :userId) "
            + "ORDER BY h.dibuat_pada DESC LIMIT :batas")
    List<Hasil.RiwayatItem> riwayat(long userId, int batas);

    @Query("SELECT COUNT(*) AS catatan, COALESCE(SUM((SELECT COALESCE(SUM(g.jml_final), 0) FROM panen_grading g "
            + "WHERE g.panen_id = h.panen_id AND g.dihapus_pada IS NULL)), 0) AS janjang "
            + "FROM panen_header h WHERE h.dihapus_pada IS NULL AND h.tanggal_panen = :tanggal "
            + "AND (:userId = 0 OR h.user_id = :userId)")
    Hasil.RingkasanHari ringkasanHari(String tanggal, long userId);

    /** Janjang hari ini dipecah per kelas (label model). */
    @Query("SELECT k.label_model AS label, COALESCE(SUM(g.jml_final), 0) AS jumlah "
            + "FROM panen_grading g JOIN panen_header h ON h.panen_id = g.panen_id "
            + "JOIN kelas_grading k ON k.kelas_id = g.kelas_id "
            + "WHERE g.dihapus_pada IS NULL AND h.dihapus_pada IS NULL AND h.tanggal_panen = :tanggal "
            + "AND (:userId = 0 OR h.user_id = :userId) GROUP BY k.label_model")
    List<Hasil.KelasJumlah> kelasHari(String tanggal, long userId);

    /** Rekap per blok pada satu hari, terbanyak dulu. */
    @Query("SELECT b.kode AS blok, COUNT(DISTINCT h.tph_id) AS jumlahTph, COUNT(DISTINCT h.panen_id) AS catatan, "
            + "COALESCE(SUM(g.jml_final), 0) AS janjang "
            + "FROM panen_header h JOIN blok b ON b.blok_id = h.blok_id "
            + "LEFT JOIN panen_grading g ON g.panen_id = h.panen_id AND g.dihapus_pada IS NULL "
            + "WHERE h.dihapus_pada IS NULL AND h.tanggal_panen = :tanggal AND (:userId = 0 OR h.user_id = :userId) "
            + "GROUP BY b.blok_id, b.kode ORDER BY janjang DESC LIMIT :batas")
    List<Hasil.BlokRekap> rekapBlokHari(String tanggal, long userId, int batas);

    /** Janjang per hari sejak tanggal tertentu (hari tanpa catatan tidak muncul). */
    @Query("SELECT h.tanggal_panen AS tanggal, COALESCE(SUM(g.jml_final), 0) AS janjang "
            + "FROM panen_header h LEFT JOIN panen_grading g ON g.panen_id = h.panen_id AND g.dihapus_pada IS NULL "
            + "WHERE h.dihapus_pada IS NULL AND h.tanggal_panen >= :dari AND (:userId = 0 OR h.user_id = :userId) "
            + "GROUP BY h.tanggal_panen")
    List<Hasil.HariJumlah> janjangPerHari(String dari, long userId);

    /** Catatan yang ditolak server. */
    @Query("SELECT COUNT(*) FROM panen_header WHERE dihapus_pada IS NULL AND status = 'DITOLAK' "
            + "AND (:userId = 0 OR user_id = :userId)")
    long jumlahDitolak(long userId);

    @Query("SELECT COUNT(*) AS catatan, COALESCE(SUM((SELECT COALESCE(SUM(g.jml_final), 0) FROM panen_grading g "
            + "WHERE g.panen_id = h.panen_id AND g.dihapus_pada IS NULL)), 0) AS janjang "
            + "FROM panen_header h WHERE h.dihapus_pada IS NULL AND h.tanggal_panen = :tanggal "
            + "AND h.status NOT IN ('TERKIRIM', 'DITERIMA', 'DITOLAK', 'MENUNGGU_SETUJU') AND (:userId = 0 OR h.user_id = :userId)")
    Hasil.RingkasanHari ringkasanHariBelumKirim(String tanggal, long userId);

    @Query("SELECT k.label_model AS label, COALESCE(SUM(g.jml_final), 0) AS jumlah "
            + "FROM panen_grading g JOIN panen_header h ON h.panen_id = g.panen_id "
            + "JOIN kelas_grading k ON k.kelas_id = g.kelas_id "
            + "WHERE g.dihapus_pada IS NULL AND h.dihapus_pada IS NULL AND h.tanggal_panen = :tanggal "
            + "AND h.status NOT IN ('TERKIRIM', 'DITERIMA', 'DITOLAK', 'MENUNGGU_SETUJU') "
            + "AND (:userId = 0 OR h.user_id = :userId) GROUP BY k.label_model")
    List<Hasil.KelasJumlah> kelasHariBelumKirim(String tanggal, long userId);

    @Query("SELECT b.kode AS blok, COUNT(DISTINCT h.tph_id) AS jumlahTph, COUNT(DISTINCT h.panen_id) AS catatan, "
            + "COALESCE(SUM(g.jml_final), 0) AS janjang "
            + "FROM panen_header h JOIN blok b ON b.blok_id = h.blok_id "
            + "LEFT JOIN panen_grading g ON g.panen_id = h.panen_id AND g.dihapus_pada IS NULL "
            + "WHERE h.dihapus_pada IS NULL AND h.tanggal_panen = :tanggal "
            + "AND h.status NOT IN ('TERKIRIM', 'DITERIMA', 'DITOLAK', 'MENUNGGU_SETUJU') AND (:userId = 0 OR h.user_id = :userId) "
            + "GROUP BY b.blok_id, b.kode ORDER BY janjang DESC")
    List<Hasil.BlokRekap> rekapBlokHariBelumKirim(String tanggal, long userId);

    @Query("SELECT h.tanggal_panen AS tanggal, COALESCE(SUM(g.jml_final), 0) AS janjang "
            + "FROM panen_header h LEFT JOIN panen_grading g ON g.panen_id = h.panen_id AND g.dihapus_pada IS NULL "
            + "WHERE h.dihapus_pada IS NULL AND h.tanggal_panen >= :dari "
            + "AND h.status NOT IN ('TERKIRIM', 'DITERIMA', 'DITOLAK', 'MENUNGGU_SETUJU') AND (:userId = 0 OR h.user_id = :userId) "
            + "GROUP BY h.tanggal_panen")
    List<Hasil.HariJumlah> janjangPerHariBelumKirim(String dari, long userId);

    /** Catatan yang belum sampai ke server. */
    @Query("SELECT COUNT(*) FROM panen_header WHERE dihapus_pada IS NULL "
            + "AND status NOT IN ('TERKIRIM', 'DITERIMA', 'MENUNGGU_SETUJU') AND (:userId = 0 OR user_id = :userId)")
    long jumlahBelumTerkirim(long userId);

    /** Catatan berstatus DRAFT yang belum disetujui, terlama dulu. */
    @Query("SELECT h.panen_id AS panenId, h.dibuat_pada AS dibuatPada, b.kode AS blok, t.no_tph AS noTph, "
            + "u.nama AS krani, h.brondolan_kg AS brondolanKg, h.catatan AS catatan, "
            + "h.status_lokasi AS statusLokasi, h.jarak_ke_tph_m AS jarakKeTphM, h.gps_mock AS gpsMock, "
            + "(SELECT p.nama FROM panen_pemanen pp JOIN pemanen p ON p.pemanen_id = pp.pemanen_id "
            + " WHERE pp.panen_id = h.panen_id AND pp.dihapus_pada IS NULL LIMIT 1) AS pemanen, "
            + "(SELECT COALESCE(SUM(g.jml_final), 0) FROM panen_grading g WHERE g.panen_id = h.panen_id "
            + " AND g.dihapus_pada IS NULL) AS total "
            + "FROM panen_header h JOIN blok b ON b.blok_id = h.blok_id JOIN tph t ON t.tph_id = h.tph_id "
            + "JOIN app_user u ON u.user_id = h.user_id "
            + "WHERE h.dihapus_pada IS NULL AND h.status = 'DRAFT' ORDER BY h.dibuat_pada")
    List<Hasil.PersetujuanItem> menungguPersetujuan();

    /** Mengisi koordinat hanya bila masih kosong. */
    @Query("UPDATE tph SET lat = :lat, lon = :lon, diubah_pada = :waktu, diubah_oleh = :user "
            + "WHERE tph_id = :tphId AND lat IS NULL AND lon IS NULL")
    int setKoordinatTph(long tphId, double lat, double lon, String waktu, long user);

    @Query("SELECT lat, lon FROM tph WHERE tph_id = :tphId")
    Hasil.Koordinat koordinatTph(long tphId);

    @Query("SELECT COUNT(*) FROM panen_header WHERE dihapus_pada IS NULL AND status = 'DRAFT'")
    long jumlahMenunggu();

    /** Menyetujui satu catatan. Hanya berlaku bila masih DRAFT. @return 1 bila berhasil, 0 bila tidak. */
    @Query("UPDATE panen_header SET status = 'DISETUJUI', disetujui_oleh = :mandor, disetujui_pada = :waktu, "
            + "diubah_pada = :waktu, diubah_oleh = :mandor "
            + "WHERE panen_id = :panenId AND status = 'DRAFT' AND dihapus_pada IS NULL")
    int setujui(String panenId, long mandor, String waktu);

    /** Jumlah hak panen.setujui milik user pada tanggal :hari (lebih dari 0 berarti mandor yang boleh menyetujui sendiri). */
    @Query("SELECT COUNT(*) FROM user_role ur "
            + "JOIN role_permission rp ON rp.role_id = ur.role_id AND rp.dihapus_pada IS NULL "
            + "JOIN permission p ON p.permission_id = rp.permission_id AND p.dihapus_pada IS NULL "
            + "WHERE ur.user_id = :userId AND ur.dihapus_pada IS NULL AND p.kode = 'panen.setujui' "
            + "AND (ur.berlaku_mulai IS NULL OR ur.berlaku_mulai <= :hari) "
            + "AND (ur.berlaku_sampai IS NULL OR ur.berlaku_sampai >= :hari)")
    long izinPanenSetujui(long userId, String hari);

    /** Catatan lain (bukan :kecuali) untuk tanggal + TPH + pemanen yang sama, atau null. */
    @Query("SELECT h.panen_id AS panenId, h.status AS status FROM panen_header h "
            + "JOIN panen_pemanen pp ON pp.panen_id = h.panen_id AND pp.dihapus_pada IS NULL "
            + "WHERE h.dihapus_pada IS NULL AND h.tanggal_panen = :tanggal AND h.tph_id = :tphId "
            + "AND pp.pemanen_id = :pemanenId AND h.panen_id <> :kecuali ORDER BY h.dibuat_pada LIMIT 1")
    Hasil.KombinasiAda kombinasiAda(String tanggal, long tphId, long pemanenId, String kecuali);

    @Query("SELECT * FROM panen_header WHERE panen_id = :panenId AND dihapus_pada IS NULL")
    PanenHeader headerById(String panenId);

    @Query("SELECT g.kelas_id AS kelasId, k.label_model AS label, g.jml_ai AS jmlAi, g.jml_final AS jmlFinal "
            + "FROM panen_grading g JOIN kelas_grading k ON k.kelas_id = g.kelas_id "
            + "WHERE g.panen_id = :panenId AND g.dihapus_pada IS NULL")
    List<Hasil.GradingEdit> gradingEdit(String panenId);

    @Query("SELECT pemanen_id FROM panen_pemanen WHERE panen_id = :panenId AND dihapus_pada IS NULL ORDER BY pp_id LIMIT 1")
    Long pemanenPanen(String panenId);

    @Query("SELECT path_lokal FROM foto WHERE panen_id = :panenId AND dihapus_pada IS NULL LIMIT 1")
    String fotoPathPanen(String panenId);

    /**
     * Perbaikan catatan DITOLAK, atau perubahan catatan yang masih MENUNGGU_SETUJU (belum disetujui krani): isi diganti,
     * persetujuan diatur ulang, dan ditandai kirim ulang.
     */
    @Query("UPDATE panen_header SET tanggal_panen = :tanggal, brondolan_kg = :brondolan, catatan = :catatan, "
            + "status = :status, disetujui_oleh = :disetujuiOleh, disetujui_pada = :disetujuiPada, "
            + "pesan_server = NULL, kirim_ulang = 1, diubah_pada = :waktu, diubah_oleh = :user "
            + "WHERE panen_id = :panenId AND status IN ('DITOLAK', 'MENUNGGU_SETUJU') AND dihapus_pada IS NULL")
    int perbaikiDitolak(String panenId, String tanggal, double brondolan, String catatan, String status,
                        Long disetujuiOleh, String disetujuiPada, String waktu, long user);

    @Query("UPDATE panen_grading SET jml_final = :jmlFinal, diubah_manual = CASE WHEN jml_ai <> :jmlFinal THEN 1 ELSE 0 END, "
            + "diubah_pada = :waktu, diubah_oleh = :user WHERE panen_id = :panenId AND kelas_id = :kelasId AND dihapus_pada IS NULL")
    int ubahGrading(String panenId, long kelasId, long jmlFinal, String waktu, long user);

    @Query("UPDATE panen_pemanen SET pemanen_id = :pemanenId, jjg_alokasi = :jjg, diubah_pada = :waktu, diubah_oleh = :user "
            + "WHERE panen_id = :panenId AND dihapus_pada IS NULL")
    int ubahPemanen(String panenId, long pemanenId, long jjg, String waktu, long user);

    /** Baris antrean PANEN lama yang GAGAL dinyatakan selesai: pengiriman ulang memakai baris antrean baru. */
    @Query("UPDATE sync_queue SET status = 'SELESAI', diubah_pada = :waktu WHERE panen_id = :panenId "
            + "AND entitas = 'PANEN' AND status IN ('GAGAL', 'MENUNGGU')")
    int tutupAntreanPanenLama(String panenId, String waktu);

    @Query("SELECT g.panen_id AS panenId, k.label_model AS label, g.jml_final AS jumlah "
            + "FROM panen_grading g JOIN kelas_grading k ON k.kelas_id = g.kelas_id "
            + "WHERE g.dihapus_pada IS NULL")
    List<Hasil.GradingBaris> gradingSemua();
}
