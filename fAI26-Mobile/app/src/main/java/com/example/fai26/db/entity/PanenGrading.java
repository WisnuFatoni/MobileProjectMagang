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
@Entity(tableName = "panen_grading",
    foreignKeys = {
        @ForeignKey(entity = PanenHeader.class, parentColumns = "panen_id", childColumns = "panen_id", onDelete = ForeignKey.CASCADE),
        @ForeignKey(entity = KelasGrading.class, parentColumns = "kelas_id", childColumns = "kelas_id")},
    indices = {
        @Index(value = {"panen_id", "kelas_id"}, name = "ux_panen_grading_panen_id_kelas_id", unique = true)})
public class PanenGrading {

    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "grading_id")
    public long gradingId;

    @NonNull @ColumnInfo(name = "panen_id")
    public String panenId = "";

    @ColumnInfo(name = "kelas_id")
    public long kelasId;

    @ColumnInfo(name = "jml_ai")
    public long jmlAi = 0;

    @ColumnInfo(name = "jml_final")
    public long jmlFinal = 0;

    @ColumnInfo(name = "diubah_manual")
    public long diubahManual = 0;

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
