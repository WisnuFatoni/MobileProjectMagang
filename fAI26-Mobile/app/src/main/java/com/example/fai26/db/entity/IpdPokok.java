package com.example.fai26.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.RoomWarnings;

/** Satu pokok yang diperiksa (termasuk yang bersih). */
@SuppressWarnings(RoomWarnings.MISSING_INDEX_ON_FOREIGN_KEY_CHILD)
@Entity(tableName = "ipd_pokok",
    foreignKeys = {
        @ForeignKey(entity = IpdHeader.class, parentColumns = "ipd_id", childColumns = "ipd_id", onDelete = ForeignKey.CASCADE)},
    indices = {
        @Index(value = {"ipd_id", "no_baris", "no_pokok"}, name = "ux_ipd_pokok_ipd_id_baris_pokok", unique = true)})
public class IpdPokok {

    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "pokok_id")
    public long pokokId;

    @NonNull @ColumnInfo(name = "ipd_id")
    public String ipdId = "";

    @ColumnInfo(name = "no_baris")
    public long noBaris;

    @ColumnInfo(name = "no_pokok")
    public long noPokok;

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
