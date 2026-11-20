package com.example.fai26.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;
import androidx.room.RoomWarnings;

/** Dihasilkan dari erd/fAI26_mobile_sqlite.sql. Jangan diubah tangan: ubah skrip pembuatnya. */
@SuppressWarnings(RoomWarnings.MISSING_INDEX_ON_FOREIGN_KEY_CHILD)
@Entity(tableName = "divisi",
    foreignKeys = {
        @ForeignKey(entity = Unit.class, parentColumns = "unit_id", childColumns = "unit_id")})
public class Divisi {

    @PrimaryKey @ColumnInfo(name = "divisi_id")
    public long divisiId;

    @ColumnInfo(name = "unit_id")
    public long unitId;

    @ColumnInfo(name = "erp_oid")
    public Long erpOid;

    @NonNull @ColumnInfo(name = "kode")
    public String kode = "";

    @NonNull @ColumnInfo(name = "nama")
    public String nama = "";

    @ColumnInfo(name = "aktif")
    public long aktif = 1;

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
