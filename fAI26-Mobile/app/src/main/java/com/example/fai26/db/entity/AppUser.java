package com.example.fai26.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.RoomWarnings;

/** Dihasilkan dari erd/fAI26_mobile_sqlite.sql. Jangan diubah tangan: ubah skrip pembuatnya. */
@SuppressWarnings(RoomWarnings.MISSING_INDEX_ON_FOREIGN_KEY_CHILD)
@Entity(tableName = "app_user",
    foreignKeys = {
        @ForeignKey(entity = Unit.class, parentColumns = "unit_id", childColumns = "unit_id")},
    indices = {
        @Index(value = {"username"}, name = "ux_app_user_username", unique = true)})
public class AppUser {

    @PrimaryKey @ColumnInfo(name = "user_id")
    public long userId;

    @ColumnInfo(name = "unit_id")
    public long unitId;

    @ColumnInfo(name = "erp_user_oid")
    public Long erpUserOid;

    /** Kode HRMB mandor di ERP (akun mandor, diisi admin) dan afdelingnya; kosong untuk akun lain. Versi DB 4. */
    @Nullable @ColumnInfo(name = "mandor_hrmb_code")
    public Long mandorHrmbCode;

    @Nullable @ColumnInfo(name = "afdeling_kode")
    public String afdelingKode;

    @NonNull @ColumnInfo(name = "username")
    public String username = "";

    @NonNull @ColumnInfo(name = "nama")
    public String nama = "";

    @ColumnInfo(name = "aktif")
    public long aktif = 1;

    @ColumnInfo(name = "gagal_login")
    public long gagalLogin = 0;

    @Nullable @ColumnInfo(name = "terkunci_sampai")
    public String terkunciSampai;

    @Nullable @ColumnInfo(name = "login_terakhir")
    public String loginTerakhir;

    @Nullable @ColumnInfo(name = "offline_hash")
    public String offlineHash;

    @Nullable @ColumnInfo(name = "offline_salt")
    public String offlineSalt;

    @Nullable @ColumnInfo(name = "offline_berlaku_sampai")
    public String offlineBerlakuSampai;

    @NonNull @ColumnInfo(name = "dibuat_pada")
    public String dibuatPada = "";

    @ColumnInfo(name = "dibuat_oleh")
    public Long dibuatOleh;

    @Nullable @ColumnInfo(name = "diubah_pada")
    public String diubahPada;

    @ColumnInfo(name = "diubah_oleh")
    public Long diubahOleh;

    @Nullable @ColumnInfo(name = "dihapus_pada")
    public String dihapusPada;

    @ColumnInfo(name = "dihapus_oleh")
    public Long dihapusOleh;
}
