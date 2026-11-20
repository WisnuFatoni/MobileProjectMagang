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
@Entity(tableName = "panen_pemanen",
    foreignKeys = {
        @ForeignKey(entity = PanenHeader.class, parentColumns = "panen_id", childColumns = "panen_id", onDelete = ForeignKey.CASCADE),
        @ForeignKey(entity = Pemanen.class, parentColumns = "pemanen_id", childColumns = "pemanen_id")})
public class PanenPemanen {

    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "pp_id")
    public long ppId;

    @NonNull @ColumnInfo(name = "panen_id")
    public String panenId = "";

    @ColumnInfo(name = "pemanen_id")
    public Long pemanenId;

    @ColumnInfo(name = "jjg_alokasi")
    public Long jjgAlokasi;

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
