package com.example.fai26.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.RoomWarnings;

/** Satu temuan (kode dan jumlah) pada satu pokok. */
@SuppressWarnings(RoomWarnings.MISSING_INDEX_ON_FOREIGN_KEY_CHILD)
@Entity(tableName = "ipd_temuan",
    foreignKeys = {
        @ForeignKey(entity = IpdPokok.class, parentColumns = "pokok_id", childColumns = "pokok_id", onDelete = ForeignKey.CASCADE),
        @ForeignKey(entity = KodeTemuan.class, parentColumns = "kode_id", childColumns = "kode_id")},
    indices = {
        @Index(value = {"pokok_id", "kode_id"}, name = "ux_ipd_temuan_pokok_id_kode_id", unique = true)})
public class IpdTemuan {

    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "temuan_id")
    public long temuanId;

    @ColumnInfo(name = "pokok_id")
    public long pokokId;

    @ColumnInfo(name = "kode_id")
    public long kodeId;

    @ColumnInfo(name = "jumlah")
    public long jumlah = 1;

    @Nullable @ColumnInfo(name = "catatan")
    public String catatan;

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
