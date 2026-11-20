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
@Entity(tableName = "periode_rotasi",
    foreignKeys = {
        @ForeignKey(entity = Unit.class, parentColumns = "unit_id", childColumns = "unit_id")})
public class PeriodeRotasi {

    @PrimaryKey @ColumnInfo(name = "periode_id")
    public long periodeId;

    @ColumnInfo(name = "unit_id")
    public long unitId;

    @ColumnInfo(name = "rotasi_no")
    public long rotasiNo;

    @NonNull @ColumnInfo(name = "tgl_mulai")
    public String tglMulai = "";

    @NonNull @ColumnInfo(name = "tgl_selesai")
    public String tglSelesai = "";

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
