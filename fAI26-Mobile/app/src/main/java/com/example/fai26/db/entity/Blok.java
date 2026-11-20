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
@Entity(tableName = "blok",
    foreignKeys = {
        @ForeignKey(entity = Unit.class, parentColumns = "unit_id", childColumns = "unit_id"),
        @ForeignKey(entity = Divisi.class, parentColumns = "divisi_id", childColumns = "divisi_id")},
    indices = {
        @Index(value = {"unit_id", "kode"}, name = "ux_blok_unit_id_kode", unique = true)})
public class Blok {

    @PrimaryKey @ColumnInfo(name = "blok_id")
    public long blokId;

    @ColumnInfo(name = "unit_id")
    public long unitId;

    @ColumnInfo(name = "divisi_id")
    public Long divisiId;

    @ColumnInfo(name = "erp_oid")
    public long erpOid;

    @NonNull @ColumnInfo(name = "kode")
    public String kode = "";

    @Nullable @ColumnInfo(name = "nama")
    public String nama;

    @ColumnInfo(name = "luas_ha")
    public Double luasHa;

    @ColumnInfo(name = "tahun_tanam")
    public Long tahunTanam;

    @ColumnInfo(name = "sph")
    public Double sph;

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
