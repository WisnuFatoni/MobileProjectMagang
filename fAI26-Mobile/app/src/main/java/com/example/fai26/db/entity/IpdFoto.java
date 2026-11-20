package com.example.fai26.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.RoomWarnings;

/** Foto bukti inspeksi. path_lokal menjadi NULL setelah terkirim ke server (berkas di HP dihapus). */
@SuppressWarnings(RoomWarnings.MISSING_INDEX_ON_FOREIGN_KEY_CHILD)
@Entity(tableName = "ipd_foto",
    foreignKeys = {
        @ForeignKey(entity = IpdHeader.class, parentColumns = "ipd_id", childColumns = "ipd_id", onDelete = ForeignKey.CASCADE)},
    indices = {
        @Index(value = {"ipd_id"}, name = "ix_ipd_foto_ipd_id")})
public class IpdFoto {

    @NonNull @PrimaryKey @ColumnInfo(name = "foto_id")
    public String fotoId = "";

    @NonNull @ColumnInfo(name = "ipd_id")
    public String ipdId = "";

    @Nullable @ColumnInfo(name = "keterangan")
    public String keterangan;

    @ColumnInfo(name = "urut")
    public long urut = 0;

    @Nullable @ColumnInfo(name = "path_lokal")
    public String pathLokal;

    @Nullable @ColumnInfo(name = "sha256")
    public String sha256;

    @ColumnInfo(name = "lebar")
    public Long lebar;

    @ColumnInfo(name = "tinggi")
    public Long tinggi;

    @ColumnInfo(name = "ukuran_byte")
    public Long ukuranByte;

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
