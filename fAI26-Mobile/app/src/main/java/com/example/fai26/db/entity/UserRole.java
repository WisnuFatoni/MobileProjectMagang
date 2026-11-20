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
@Entity(tableName = "user_role",
    foreignKeys = {
        @ForeignKey(entity = AppUser.class, parentColumns = "user_id", childColumns = "user_id"),
        @ForeignKey(entity = Role.class, parentColumns = "role_id", childColumns = "role_id")},
    indices = {
        @Index(value = {"user_id", "role_id"}, name = "ux_user_role_user_id_role_id", unique = true)})
public class UserRole {

    @PrimaryKey @ColumnInfo(name = "ur_id")
    public long urId;

    @ColumnInfo(name = "user_id")
    public long userId;

    @ColumnInfo(name = "role_id")
    public long roleId;

    @Nullable @ColumnInfo(name = "berlaku_mulai")
    public String berlakuMulai;

    @Nullable @ColumnInfo(name = "berlaku_sampai")
    public String berlakuSampai;

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
