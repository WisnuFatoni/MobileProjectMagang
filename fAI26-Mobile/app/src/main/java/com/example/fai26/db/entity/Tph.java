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
@Entity(tableName = "tph",
    foreignKeys = {
        @ForeignKey(entity = Blok.class, parentColumns = "blok_id", childColumns = "blok_id")},
    indices = {
        @Index(value = {"qr_kode"}, name = "ux_tph_qr_kode", unique = true),
        @Index(value = {"blok_id", "no_tph"}, name = "ux_tph_blok_id_no_tph", unique = true)})
public class Tph {

    @PrimaryKey @ColumnInfo(name = "tph_id")
    public long tphId;

    @ColumnInfo(name = "blok_id")
    public long blokId;

    @NonNull @ColumnInfo(name = "no_tph")
    public String noTph = "";

    @NonNull @ColumnInfo(name = "qr_kode")
    public String qrKode = "";

    @ColumnInfo(name = "lat")
    public Double lat;

    @ColumnInfo(name = "lon")
    public Double lon;

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
