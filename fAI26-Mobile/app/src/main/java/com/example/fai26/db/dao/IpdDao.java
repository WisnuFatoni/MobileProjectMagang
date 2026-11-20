package com.example.fai26.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.fai26.db.entity.IpdFoto;
import com.example.fai26.db.entity.IpdHeader;
import com.example.fai26.db.entity.IpdPokok;
import com.example.fai26.db.entity.IpdTemuan;
import com.example.fai26.db.entity.KodeTemuan;

import java.util.List;

/** Akses data modul IPD (Inspeksi Panen Detail). */
@Dao
public interface IpdDao {

    // ---- Master untuk memilih ---------------------------------------------------------------------

    @Query("SELECT * FROM kode_temuan WHERE aktif = 1 AND dihapus_pada IS NULL ORDER BY urut, kode_id")
    List<KodeTemuan> kodeAktif();

    @Query("SELECT d.divisi_id AS divisiId, d.kode AS kode, d.nama AS nama, d.asisten_user_id AS asistenUserId, a.nama AS asistenNama "
            + "FROM divisi d LEFT JOIN app_user a ON a.user_id = d.asisten_user_id "
            + "WHERE d.aktif = 1 AND d.dihapus_pada IS NULL ORDER BY d.kode")
    List<IpdHasil.DivisiPilihan> divisiPilihan();

    @Query("SELECT blok_id AS blokId, kode AS kode, erp_oid AS erpOid, divisi_id AS divisiId FROM blok "
            + "WHERE aktif = 1 AND dihapus_pada IS NULL AND (:divisiId = 0 OR divisi_id = :divisiId) ORDER BY kode")
    List<IpdHasil.BlokPilihan> blokPilihan(long divisiId);

    @Query("SELECT u.user_id AS userId, u.nama AS nama, u.mandor_hrmb_code AS hrmbCode FROM app_user u "
            + "WHERE u.aktif = 1 AND u.dihapus_pada IS NULL AND EXISTS (SELECT 1 FROM user_role ur JOIN role r ON r.role_id = ur.role_id "
            + "WHERE ur.user_id = u.user_id AND ur.dihapus_pada IS NULL AND r.kode = :role) ORDER BY u.nama")
    List<IpdHasil.OrangPilihan> orangBerperan(String role);

    @Query("SELECT pemanen_id AS pemanenId, nama AS nama, erp_hrmb_code AS hrmbCode FROM pemanen "
            + "WHERE aktif = 1 AND dihapus_pada IS NULL ORDER BY nama")
    List<IpdHasil.PemanenPilihan> pemanenPilihan();

    // ---- Simpan ---------------------------------------------------------------------------------------

    @Insert
    void insertHeader(IpdHeader h);

    @Update
    int updateHeader(IpdHeader h);

    @Insert
    long insertPokok(IpdPokok p);

    @Insert
    void insertTemuan(IpdTemuan t);

    @Insert
    void insertFoto(IpdFoto f);

    @Query("SELECT * FROM ipd_header WHERE ipd_id = :id AND dihapus_pada IS NULL")
    IpdHeader header(String id);

    /** Menghapus semua pokok (dan temuannya, lewat cascade) sebuah inspeksi sebelum disimpan ulang. */
    @Query("DELETE FROM ipd_pokok WHERE ipd_id = :id")
    void hapusPokok(String id);

    @Query("DELETE FROM ipd_header WHERE ipd_id = :id AND status = 'DRAFT'")
    int hapusDraf(String id);

    // ---- Memuat ulang -----------------------------------------------------------------------------

    @Query("SELECT p.no_baris AS noBaris, p.no_pokok AS noPokok, k.kode AS kode, t.jumlah AS jumlah FROM ipd_pokok p "
            + "LEFT JOIN ipd_temuan t ON t.pokok_id = p.pokok_id AND t.dihapus_pada IS NULL "
            + "LEFT JOIN kode_temuan k ON k.kode_id = t.kode_id "
            + "WHERE p.ipd_id = :id AND p.dihapus_pada IS NULL ORDER BY p.no_baris, p.no_pokok, k.urut")
    List<IpdHasil.PokokBaris> pokokBaris(String id);

    @Query("SELECT * FROM ipd_foto WHERE ipd_id = :id AND dihapus_pada IS NULL ORDER BY urut, dibuat_pada")
    List<IpdFoto> foto(String id);

    @Query("SELECT * FROM ipd_foto WHERE foto_id = :id")
    IpdFoto fotoById(String id);

    @Query("DELETE FROM ipd_foto WHERE foto_id = :id")
    void hapusFoto(String id);

    @Query("UPDATE ipd_foto SET keterangan = :keterangan, diubah_pada = :waktu WHERE foto_id = :id")
    void ubahKeteranganFoto(String id, String keterangan, String waktu);

    // ---- Riwayat ----------------------------------------------------------------------------------

    @Query("SELECT h.ipd_id AS ipdId, h.tanggal_inspeksi AS tanggalInspeksi, h.status AS status, h.pesan_server AS pesanServer, "
            + "b.kode AS blok, pm.nama AS pemanen, d.kode AS divisi, a.nama AS asisten, "
            + "(SELECT COUNT(*) FROM ipd_pokok p WHERE p.ipd_id = h.ipd_id AND p.dihapus_pada IS NULL) AS pokok, "
            + "(SELECT COUNT(*) FROM ipd_pokok p WHERE p.ipd_id = h.ipd_id AND p.dihapus_pada IS NULL AND EXISTS "
            + "(SELECT 1 FROM ipd_temuan t WHERE t.pokok_id = p.pokok_id AND t.dihapus_pada IS NULL)) AS bertemuan, "
            + "h.dibuat_pada AS dibuatPada, h.user_id AS userId "
            + "FROM ipd_header h JOIN blok b ON b.blok_id = h.blok_id JOIN pemanen pm ON pm.pemanen_id = h.pemanen_id "
            + "JOIN divisi d ON d.divisi_id = h.divisi_id LEFT JOIN app_user a ON a.user_id = h.asisten_user_id "
            + "WHERE h.dihapus_pada IS NULL AND (:userId = 0 OR h.user_id = :userId) "
            + "ORDER BY h.tanggal_inspeksi DESC, h.dibuat_pada DESC LIMIT :batas")
    List<IpdHasil.Riwayat> riwayat(long userId, int batas);

    @Query("SELECT COUNT(*) FROM ipd_header WHERE dihapus_pada IS NULL AND status = 'DRAFT' AND (:userId = 0 OR user_id = :userId)")
    long jumlahDraf(long userId);

    @Query("SELECT h.ipd_id AS ipdId, h.tanggal_inspeksi AS tanggalInspeksi, u.nama AS inspektur, h.status AS status FROM ipd_header h "
            + "JOIN app_user u ON u.user_id = h.user_id "
            + "WHERE h.dihapus_pada IS NULL AND h.pemanen_id = :pemanenId AND h.blok_id = :blokId AND h.status <> 'DITOLAK' "
            + "AND h.tanggal_inspeksi >= :dari AND h.tanggal_inspeksi <= :sampai AND h.ipd_id <> :kecuali "
            + "ORDER BY h.tanggal_inspeksi DESC, h.dibuat_pada DESC LIMIT 5")
    List<IpdHasil.Terakhir> terakhirLokal(long pemanenId, long blokId, String dari, String sampai, String kecuali);

    // ---- Isi kiriman ----------------------------------------------------------------------------------

    @Query("SELECT h.ipd_id AS ipdId, u.kode AS unitKode, h.tanggal_inspeksi AS tanggalInspeksi, d.kode AS divisiKode, "
            + "b.erp_oid AS asm3Oid, b.kode AS blokKode, pm.erp_hrmb_code AS pemanenHrmb, h.ancak_no AS ancakNo, "
            + "mb.username AS mandorBlokUsername, ma.username AS mandorAncakUsername, h.catatan AS catatan, "
            + "h.dibuat_pada AS dibuatPada, h.kirim_ulang AS kirimUlang, h.user_id AS userId "
            + "FROM ipd_header h JOIN unit u ON u.unit_id = h.unit_id JOIN divisi d ON d.divisi_id = h.divisi_id "
            + "JOIN blok b ON b.blok_id = h.blok_id JOIN pemanen pm ON pm.pemanen_id = h.pemanen_id "
            + "LEFT JOIN app_user mb ON mb.user_id = h.mandor_blok_user_id LEFT JOIN app_user ma ON ma.user_id = h.mandor_ancak_user_id "
            + "WHERE h.ipd_id = :id AND h.dihapus_pada IS NULL")
    IpdHasil.HeaderKirim headerKirim(String id);

    @Query("SELECT pokok_id AS pokokId, no_baris AS noBaris, no_pokok AS noPokok FROM ipd_pokok "
            + "WHERE ipd_id = :id AND dihapus_pada IS NULL ORDER BY no_baris, no_pokok")
    List<IpdHasil.PokokKirim> pokokKirim(String id);

    @Query("SELECT t.pokok_id AS pokokId, k.kode AS kode, t.jumlah AS jumlah, t.catatan AS catatan FROM ipd_temuan t "
            + "JOIN kode_temuan k ON k.kode_id = t.kode_id JOIN ipd_pokok p ON p.pokok_id = t.pokok_id "
            + "WHERE p.ipd_id = :id AND t.dihapus_pada IS NULL AND p.dihapus_pada IS NULL ORDER BY k.urut")
    List<IpdHasil.TemuanKirim> temuanKirim(String id);

    @Query("SELECT foto_id AS fotoId, ipd_id AS ipdId, keterangan AS keterangan, urut AS urut, path_lokal AS pathLokal, sha256 AS sha256 "
            + "FROM ipd_foto WHERE ipd_id = :id AND status_unggah = 'BELUM' AND path_lokal IS NOT NULL AND dihapus_pada IS NULL ORDER BY urut")
    List<IpdHasil.FotoKirim> fotoBelumUnggah(String id);

    @Query("SELECT foto_id AS fotoId, ipd_id AS ipdId, keterangan AS keterangan, urut AS urut, path_lokal AS pathLokal, sha256 AS sha256 "
            + "FROM ipd_foto WHERE foto_id = :id")
    IpdHasil.FotoKirim fotoKirim(String id);

    /** Foto terkirim: ditandai SUDAH dan berkasnya dilepas (path_lokal menjadi NULL) supaya memori HP tidak penuh. */
    @Query("UPDATE ipd_foto SET status_unggah = 'SUDAH', path_lokal = NULL, diubah_pada = :waktu WHERE foto_id = :id")
    void fotoSudahDiunggah(String id, String waktu);

    // ---- Status kirim ----------------------------------------------------------------------------------

    @Query("UPDATE ipd_header SET status = :status, dikirim_pada = :waktu, pesan_server = :pesan, diubah_pada = :waktu WHERE ipd_id = :id")
    int setStatusKirim(String id, String status, String waktu, String pesan);

    /** Status dari server (GET /api/ipd). Hanya catatan yang sudah terkirim; draf dan perubahan yang belum terkirim tidak ditimpa. */
    @Query("UPDATE ipd_header SET status = :status, pesan_server = :pesan, diubah_pada = :waktu "
            + "WHERE ipd_id = :id AND status IN ('MENUNGGU_SETUJU', 'DISETUJUI', 'DITOLAK') AND kirim_ulang = 0 "
            + "AND (status <> :status OR COALESCE(pesan_server, '') <> COALESCE(:pesan, ''))")
    int setStatusServer(String id, String status, String pesan, String waktu);

    @Query("UPDATE ipd_header SET kirim_ulang = 0 WHERE ipd_id = :id")
    void resetKirimUlang(String id);

    @Query("SELECT COUNT(*) FROM sync_queue WHERE entitas = 'IPD' AND payload_json = :id AND status = 'MENUNGGU'")
    long jumlahAntreanMenunggu(String id);

    @Query("UPDATE sync_queue SET status = 'SELESAI', diubah_pada = :waktu WHERE entitas = 'IPD' AND payload_json = :id AND status IN ('MENUNGGU', 'GAGAL')")
    int tutupAntreanLama(String id, String waktu);

    @Query("SELECT COUNT(*) FROM ipd_header WHERE dihapus_pada IS NULL AND status = 'DRAFT' AND ipd_id IN "
            + "(SELECT payload_json FROM sync_queue WHERE entitas = 'IPD' AND status IN ('MENUNGGU', 'GAGAL'))")
    long jumlahBelumTerkirim();
}
