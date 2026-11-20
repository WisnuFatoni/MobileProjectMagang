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
@Entity(tableName = "foto",
    foreignKeys = {
        @ForeignKey(entity = PanenHeader.class, parentColumns = "panen_id", childColumns = "panen_id", onDelete = ForeignKey.CASCADE)},
    indices = {
        @Index(value = {"panen_id"}, name = "ix_foto_panen_id")})
public class Foto {

    @NonNull @PrimaryKey @ColumnInfo(name = "foto_id")
    public String fotoId = "";

    @NonNull @ColumnInfo(name = "panen_id")
    public String panenId = "";

    @NonNull @ColumnInfo(name = "jenis")
    public String jenis = "";

    @NonNull @ColumnInfo(name = "path_lokal")
    public String pathLokal = "";

    @Nullable @ColumnInfo(name = "sha256")
    public String sha256;

    @ColumnInfo(name = "lebar")
    public Long lebar;

    @ColumnInfo(name = "tinggi")
    public Long tinggi;

    @NonNull @ColumnInfo(name = "status_unggah")
    public String statusUnggah = "BELUM";

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
