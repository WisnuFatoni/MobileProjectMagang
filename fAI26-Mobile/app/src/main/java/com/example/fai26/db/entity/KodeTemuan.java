package com.example.fai26.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.RoomWarnings;

/** Master kode temuan IPD (A, G, S, M1 ... R), diunduh dari server lewat master. */
@SuppressWarnings(RoomWarnings.MISSING_INDEX_ON_FOREIGN_KEY_CHILD)
@Entity(tableName = "kode_temuan",
    indices = {
        @Index(value = {"kode"}, name = "ux_kode_temuan_kode", unique = true)})
public class KodeTemuan {

    @PrimaryKey @ColumnInfo(name = "kode_id")
    public long kodeId;

    @NonNull @ColumnInfo(name = "kode")
    public String kode = "";

    @NonNull @ColumnInfo(name = "nama")
    public String nama = "";

    @NonNull @ColumnInfo(name = "kelompok")
    public String kelompok = "";

    @ColumnInfo(name = "urut")
    public long urut = 0;

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
