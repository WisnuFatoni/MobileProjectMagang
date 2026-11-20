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
@Entity(tableName = "deteksi",
    foreignKeys = {
        @ForeignKey(entity = Foto.class, parentColumns = "foto_id", childColumns = "foto_id", onDelete = ForeignKey.CASCADE),
        @ForeignKey(entity = KelasGrading.class, parentColumns = "kelas_id", childColumns = "kelas_id")})
public class Deteksi {

    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "deteksi_id")
    public long deteksiId;

    @NonNull @ColumnInfo(name = "foto_id")
    public String fotoId = "";

    @ColumnInfo(name = "kelas_id")
    public long kelasId;

    @ColumnInfo(name = "confidence")
    public double confidence;

    @ColumnInfo(name = "x")
    public Double x;

    @ColumnInfo(name = "y")
    public Double y;

    @ColumnInfo(name = "w")
    public Double w;

    @ColumnInfo(name = "h")
    public Double h;

    @NonNull @ColumnInfo(name = "dibuat_pada")
    public String dibuatPada = "";

    @ColumnInfo(name = "dibuat_oleh")
    public Long dibuatOleh;

    @Nullable @ColumnInfo(name = "diubah_pada")
    public String diubahPada;

    @ColumnInfo(name = "diubah_oleh")
    public Long diubahOleh;
}
