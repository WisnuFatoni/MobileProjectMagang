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
@Entity(tableName = "role_permission",
    foreignKeys = {
        @ForeignKey(entity = Role.class, parentColumns = "role_id", childColumns = "role_id"),
        @ForeignKey(entity = Permission.class, parentColumns = "permission_id", childColumns = "permission_id")},
    indices = {
        @Index(value = {"role_id", "permission_id"}, name = "ux_role_permission_role_id_permission_id", unique = true)})
public class RolePermission {

    @PrimaryKey @ColumnInfo(name = "rp_id")
    public long rpId;

    @ColumnInfo(name = "role_id")
    public long roleId;

    @ColumnInfo(name = "permission_id")
    public long permissionId;

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
