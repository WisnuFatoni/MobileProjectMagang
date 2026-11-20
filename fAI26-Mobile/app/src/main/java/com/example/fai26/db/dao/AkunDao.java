package com.example.fai26.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.fai26.db.entity.AppUser;
import com.example.fai26.db.entity.AuditLog;

import java.util.List;

@Dao
public interface AkunDao {

    @Query("SELECT * FROM app_user WHERE username = :username AND dihapus_pada IS NULL")
    AppUser cari(String username);

    @Query("SELECT * FROM app_user WHERE user_id = :userId AND dihapus_pada IS NULL")
    AppUser cariId(long userId);

    @Update
    void ubah(AppUser u);

    @Insert
    void insertAudit(AuditLog a);

    /** Nama role pertama milik user (untuk ditampilkan di beranda). */
    @Query("SELECT r.nama FROM user_role ur JOIN role r ON r.role_id = ur.role_id "
            + "WHERE ur.user_id = :userId AND ur.dihapus_pada IS NULL ORDER BY r.role_id LIMIT 1")
    String namaRole(long userId);

    /** Kode permission milik user dari semua role yang masih berlaku pada tanggal :hari (YYYY-MM-DD). */
    @Query("SELECT DISTINCT p.kode FROM user_role ur "
            + "JOIN role_permission rp ON rp.role_id = ur.role_id AND rp.dihapus_pada IS NULL "
            + "JOIN permission p ON p.permission_id = rp.permission_id AND p.dihapus_pada IS NULL "
            + "WHERE ur.user_id = :userId AND ur.dihapus_pada IS NULL "
            + "AND (ur.berlaku_mulai IS NULL OR ur.berlaku_mulai <= :hari) "
            + "AND (ur.berlaku_sampai IS NULL OR ur.berlaku_sampai >= :hari) "
            + "ORDER BY p.kode")
    List<String> permission(long userId, String hari);
}
