package com.example.fai26.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.fai26.db.entity.AppUser;
import com.example.fai26.db.entity.Blok;
import com.example.fai26.db.entity.Device;
import com.example.fai26.db.entity.Divisi;
import com.example.fai26.db.entity.Pemanen;
import com.example.fai26.db.entity.Tph;
import com.example.fai26.db.entity.Unit;
import com.example.fai26.db.entity.UserRole;

import java.util.List;

@Dao
public interface MasterDao {

    @Insert
    void insertUnit(Unit u);

    @Insert
    void insertDivisi(List<Divisi> d);

    @Insert
    void insertBlok(List<Blok> b);

    @Insert
    void insertTph(List<Tph> t);

    @Insert
    void insertPemanen(List<Pemanen> p);

    @Insert
    void insertUser(AppUser u);

    @Insert
    void insertUserRole(UserRole ur);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertDeviceJikaBelumAda(Device d);

    @Query("SELECT COUNT(*) FROM unit")
    long jumlahUnit();

    @Query("SELECT unit_id FROM divisi WHERE divisi_id = :divisiId AND dihapus_pada IS NULL")
    Long unitIdDivisi(long divisiId);

    @Query("SELECT t.tph_id AS tphId, b.blok_id AS blokId, b.unit_id AS unitId, b.kode AS blok, "
            + "t.no_tph AS noTph, t.qr_kode AS qrKode "
            + "FROM tph t JOIN blok b ON b.blok_id = t.blok_id "
            + "WHERE t.aktif = 1 AND t.dihapus_pada IS NULL AND b.dihapus_pada IS NULL "
            + "ORDER BY b.kode, t.no_tph")
    List<Hasil.TphPilihan> daftarTph();

    @Query("SELECT t.tph_id AS tphId, b.blok_id AS blokId, b.unit_id AS unitId, b.kode AS blok, "
            + "t.no_tph AS noTph, t.qr_kode AS qrKode "
            + "FROM tph t JOIN blok b ON b.blok_id = t.blok_id "
            + "WHERE t.qr_kode = :qr AND t.aktif = 1 AND t.dihapus_pada IS NULL")
    Hasil.TphPilihan tphDariQr(String qr);

    /** Satu TPH menurut id, tanpa menyaring aktif/hapus (untuk menampilkan catatan lama yang TPH-nya sudah tidak ada di master). */
    @Query("SELECT t.tph_id AS tphId, b.blok_id AS blokId, b.unit_id AS unitId, b.kode AS blok, "
            + "t.no_tph AS noTph, t.qr_kode AS qrKode FROM tph t JOIN blok b ON b.blok_id = t.blok_id WHERE t.tph_id = :tphId")
    Hasil.TphPilihan tphPilihanById(long tphId);

    @Query("SELECT u.kode AS unitKode, u.nama AS unitNama, d.nama AS divisiNama, b.kode AS blokKode, "
            + "b.nama AS blokNama, b.luas_ha AS luasHa, b.tahun_tanam AS tahunTanam, b.sph AS sph, "
            + "(SELECT MAX(h.tanggal_panen) FROM panen_header h WHERE h.tph_id = t.tph_id "
            + " AND h.dihapus_pada IS NULL) AS panenTerakhir, "
            + "(SELECT COUNT(*) FROM panen_header h WHERE h.tph_id = t.tph_id AND h.dihapus_pada IS NULL "
            + " AND h.tanggal_panen = :hariIni) AS catatanHariIni, "
            + "t.lat AS lat, t.lon AS lon "
            + "FROM tph t JOIN blok b ON b.blok_id = t.blok_id JOIN unit u ON u.unit_id = b.unit_id "
            + "LEFT JOIN divisi d ON d.divisi_id = b.divisi_id WHERE t.tph_id = :tphId")
    Hasil.TphDetail detailTph(long tphId, String hariIni);

    @Query("SELECT pemanen_id AS pemanenId, nama, kode FROM pemanen "
            + "WHERE aktif = 1 AND dihapus_pada IS NULL ORDER BY nama")
    List<Hasil.PemanenPilihan> daftarPemanen();

    @Query("SELECT user_id FROM app_user WHERE username = :username AND dihapus_pada IS NULL")
    Long userIdDariUsername(String username);

    @Query("SELECT nama FROM app_user WHERE user_id = :userId")
    String namaUser(long userId);
}
