package com.example.fai26.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.RoomWarnings;

/** Dihasilkan dari erd/fAI26_mobile_sqlite.sql. Jangan diubah tangan: ubah skrip pembuatnya. */
@SuppressWarnings(RoomWarnings.MISSING_INDEX_ON_FOREIGN_KEY_CHILD)
@Entity(tableName = "master_sync",
    primaryKeys = {"nama_master", "unit_id"},
    foreignKeys = {
        @ForeignKey(entity = Unit.class, parentColumns = "unit_id", childColumns = "unit_id")})
public class MasterSync {

    @NonNull @ColumnInfo(name = "nama_master")
    public String namaMaster = "";

    @ColumnInfo(name = "unit_id")
    public long unitId;

    @Nullable @ColumnInfo(name = "versi_server")
    public String versiServer;

    @Nullable @ColumnInfo(name = "sinkron_terakhir")
    public String sinkronTerakhir;

    @NonNull @ColumnInfo(name = "dibuat_pada")
    public String dibuatPada = "";

    @ColumnInfo(name = "dibuat_oleh")
    public Long dibuatOleh;

    @Nullable @ColumnInfo(name = "diubah_pada")
    public String diubahPada;

    @ColumnInfo(name = "diubah_oleh")
    public Long diubahOleh;
}
