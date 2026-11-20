package com.example.fai26.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.RoomWarnings;

/** Dihasilkan dari erd/fAI26_mobile_sqlite.sql. Jangan diubah tangan: ubah skrip pembuatnya. */
@SuppressWarnings(RoomWarnings.MISSING_INDEX_ON_FOREIGN_KEY_CHILD)
@Entity(tableName = "kelas_grading",
    indices = {
        @Index(value = {"kode"}, name = "ux_kelas_grading_kode", unique = true)})
public class KelasGrading {

    @PrimaryKey @ColumnInfo(name = "kelas_id")
    public long kelasId;

    @NonNull @ColumnInfo(name = "kode")
    public String kode = "";

    @NonNull @ColumnInfo(name = "nama")
    public String nama = "";

    @ColumnInfo(name = "label_model")
    public long labelModel;

    @Nullable @ColumnInfo(name = "erp_kolom")
    public String erpKolom;

    @ColumnInfo(name = "boleh_dipanen")
    public long bolehDipanen = 0;

    @ColumnInfo(name = "urutan")
    public long urutan = 0;

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
