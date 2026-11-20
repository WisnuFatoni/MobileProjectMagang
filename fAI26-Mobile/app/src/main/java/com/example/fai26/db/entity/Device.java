package com.example.fai26.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.RoomWarnings;

/** Dihasilkan dari erd/fAI26_mobile_sqlite.sql. Jangan diubah tangan: ubah skrip pembuatnya. */
@SuppressWarnings(RoomWarnings.MISSING_INDEX_ON_FOREIGN_KEY_CHILD)
@Entity(tableName = "device")
public class Device {

    @NonNull @PrimaryKey @ColumnInfo(name = "device_id")
    public String deviceId = "";

    @Nullable @ColumnInfo(name = "nama")
    public String nama;

    @Nullable @ColumnInfo(name = "os_versi")
    public String osVersi;

    @Nullable @ColumnInfo(name = "app_versi")
    public String appVersi;

    @ColumnInfo(name = "aktif")
    public long aktif = 1;

    @Nullable @ColumnInfo(name = "terakhir_sinkron")
    public String terakhirSinkron;

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
