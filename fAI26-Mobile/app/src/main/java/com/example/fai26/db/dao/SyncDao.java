package com.example.fai26.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.fai26.db.entity.AuditLog;
import com.example.fai26.db.entity.SyncQueue;

import java.util.List;

/** Akses data untuk sinkronisasi ke server: antrean, isi kiriman, dan penandaan hasilnya. */
@Dao
public interface SyncDao {

    // ---- Antrean ---------------------------------------------------------------------------------

    @Insert
    long insertQueue(SyncQueue q);

    @Query("SELECT * FROM sync_queue WHERE status = 'MENUNGGU' ORDER BY dibuat_pada, queue_id")
    List<SyncQueue> antreanMenunggu();

    @Query("SELECT COUNT(*) FROM sync_queue WHERE panen_id = :panenId AND entitas = 'PANEN' AND status = 'MENUNGGU'")
    long jumlahAntreanPanenMenunggu(String panenId);

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'MENUNGGU'")
    long jumlahMenunggu();

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'GAGAL'")
    long jumlahGagal();

    @Query("UPDATE sync_queue SET status = :status, percobaan = :percobaan, error_terakhir = :error, "
            + "coba_terakhir = :waktu, diubah_pada = :waktu WHERE queue_id = :id")
    void updateQueue(long id, String status, long percobaan, String error, String waktu);

    /** Tombol "Kirim ulang": semua yang GAGAL kembali MENUNGGU dengan hitungan percobaan dari awal. */
    @Query("UPDATE sync_queue SET status = 'MENUNGGU', percobaan = 0, error_terakhir = NULL, diubah_pada = :waktu "
            + "WHERE status = 'GAGAL'")
    int kirimUlangYangGagal(String waktu);

    @Query("SELECT error_terakhir FROM sync_queue WHERE status = 'GAGAL' ORDER BY diubah_pada DESC LIMIT 1")
    String galatGagalTerbaru();

    /**
     * Server sudah punya koordinat TPH lain: HP mengikutinya. Hanya mengubah bila berbeda, supaya baris yang sama
     * tidak dianggap berubah (diubah_pada tidak maju tanpa alasan).
     */
    @Query("UPDATE tph SET lat = :lat, lon = :lon, diubah_pada = :waktu "
            + "WHERE tph_id = :tphId AND (lat IS NOT :lat OR lon IS NOT :lon)")
    int ikutiKoordinatServer(long tphId, double lat, double lon, String waktu);

    /** Catatan yang sudah disetujui mandor tetapi belum punya baris antrean PANEN. */
    @Query("SELECT h.panen_id FROM panen_header h WHERE h.status IN ('DISETUJUI', 'DRAFT') AND h.dihapus_pada IS NULL "
            + "AND NOT EXISTS (SELECT 1 FROM sync_queue q WHERE q.panen_id = h.panen_id AND q.entitas = 'PANEN')")
    List<String> disetujuiTanpaAntrean();

    // ---- Isi kiriman -----------------------------------------------------------------------------

    @Query("SELECT h.panen_id AS panenId, u.kode AS unitKode, h.tanggal_panen AS tanggalPanen, "
            + "b.erp_oid AS asm3Oid, b.kode AS blokKode, t.no_tph AS tphKode, h.rotasi_no AS rotasi, "
            + "h.kondisi_cuaca AS kondisiCuaca, h.brondolan_kg AS brondolanKg, h.gps_lat AS gpsLat, "
            + "h.gps_lon AS gpsLon, h.gps_akurasi_m AS gpsAkurasiM, h.gps_waktu AS gpsWaktu, h.gps_mock AS gpsMock, "
            + "h.jarak_ke_tph_m AS jarakKeTphM, h.status_lokasi AS statusLokasi, "
            + "(SELECT m.nama || ' ' || m.versi FROM model_versi m WHERE m.model_id = h.model_id) AS modelVersi, "
            + "h.catatan AS catatan, h.dibuat_pada AS dibuatPada, "
            + "(SELECT a.username FROM app_user a WHERE a.user_id = h.user_id) AS pencatatUsername, "
            + "(SELECT a.username FROM app_user a WHERE a.user_id = h.disetujui_oleh) AS disetujuiUsername, "
            + "h.disetujui_pada AS disetujuiPada, h.kirim_ulang AS kirimUlang "
            + "FROM panen_header h JOIN unit u ON u.unit_id = h.unit_id JOIN blok b ON b.blok_id = h.blok_id "
            + "JOIN tph t ON t.tph_id = h.tph_id WHERE h.panen_id = :panenId")
    Hasil.PanenKirim panenKirim(String panenId);

    /** Pencatat (user_id) sebuah catatan, untuk memutuskan siapa yang boleh mengirimnya. */
    @Query("SELECT user_id FROM panen_header WHERE panen_id = :panenId")
    Long pemilikPanen(String panenId);

    @Query("SELECT k.kode AS kelasKode, k.erp_kolom AS erpKolom, g.jml_ai AS jmlAi, g.jml_final AS jmlFinal, "
            + "g.diubah_manual AS diubahManual FROM panen_grading g JOIN kelas_grading k ON k.kelas_id = g.kelas_id "
            + "WHERE g.panen_id = :panenId AND g.dihapus_pada IS NULL ORDER BY k.urutan")
    List<Hasil.GradingKirim> gradingKirim(String panenId);

    @Query("SELECT p.erp_hrmb_code AS hrmbCode, pp.jjg_alokasi AS jjgAlokasi FROM panen_pemanen pp "
            + "JOIN pemanen p ON p.pemanen_id = pp.pemanen_id WHERE pp.panen_id = :panenId AND pp.dihapus_pada IS NULL")
    List<Hasil.PemanenKirim> pemanenKirim(String panenId);

    @Query("SELECT foto_id AS fotoId, panen_id AS panenId, jenis, path_lokal AS pathLokal, sha256 "
            + "FROM foto WHERE panen_id = :panenId AND dihapus_pada IS NULL AND status_unggah <> 'SUDAH'")
    List<Hasil.FotoKirim> fotoBelumUnggah(String panenId);

    @Query("SELECT foto_id AS fotoId, panen_id AS panenId, jenis, path_lokal AS pathLokal, sha256 "
            + "FROM foto WHERE foto_id = :fotoId")
    Hasil.FotoKirim fotoById(String fotoId);

    // ---- Hasil kiriman ---------------------------------------------------------------------------

    @Query("UPDATE panen_header SET status = :status, dikirim_pada = :waktu, pesan_server = :pesan, "
            + "diubah_pada = :waktu WHERE panen_id = :panenId")
    int setStatusKirim(String panenId, String status, String waktu, String pesan);

    /**
     * Status dari server (Terkirim, Diterima, Ditolak). Hanya untuk catatan yang memang sudah dikirim, dan hanya bila
     * ada yang berubah, supaya jumlah baris yang dikembalikan sama dengan jumlah perubahan sungguhan.
     */
    @Query("UPDATE panen_header SET status = :status, pesan_server = :pesan, diubah_pada = :waktu "
            + "WHERE panen_id = :panenId AND status IN ('TERKIRIM', 'DITERIMA', 'DITOLAK', 'MENUNGGU_SETUJU') "
            + "AND (status <> :status OR COALESCE(pesan_server, '') <> COALESCE(:pesan, ''))")
    int setStatusServer(String panenId, String status, String pesan, String waktu);

    /** Penanda kirim ulang dihapus setelah server menerima catatan. */
    @Query("UPDATE panen_header SET kirim_ulang = 0 WHERE panen_id = :panenId")
    void resetKirimUlang(String panenId);

    @Query("UPDATE foto SET status_unggah = 'SUDAH', diubah_pada = :waktu WHERE foto_id = :fotoId")
    void fotoSudahDiunggah(String fotoId, String waktu);

    // ---- Audit -----------------------------------------------------------------------------------

    @Query("SELECT * FROM audit_log WHERE disinkron = 0 ORDER BY waktu, audit_id LIMIT :batas")
    List<AuditLog> auditBelumKirim(int batas);

    @Query("UPDATE audit_log SET disinkron = 1 WHERE audit_id IN (:ids)")
    void auditSudahKirim(List<String> ids);

    @Query("SELECT COUNT(*) FROM audit_log WHERE disinkron = 0")
    long jumlahAuditBelumKirim();
}
