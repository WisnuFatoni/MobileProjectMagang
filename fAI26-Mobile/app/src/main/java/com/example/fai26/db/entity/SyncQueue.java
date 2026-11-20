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
@Entity(tableName = "sync_queue",
    foreignKeys = {
        @ForeignKey(entity = PanenHeader.class, parentColumns = "panen_id", childColumns = "panen_id", onDelete = ForeignKey.CASCADE)},
    indices = {
        @Index(value = {"status", "dibuat_pada"}, name = "ix_sync_queue_status_dibuat_pada")})
public class SyncQueue {

    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "queue_id")
    public long queueId;

    @Nullable @ColumnInfo(name = "panen_id")
    public String panenId;

    @NonNull @ColumnInfo(name = "entitas")
    public String entitas = "";

    @NonNull @ColumnInfo(name = "aksi")
    public String aksi = "KIRIM";

    @Nullable @ColumnInfo(name = "payload_json")
    public String payloadJson;

    @ColumnInfo(name = "percobaan")
    public long percobaan = 0;

    @NonNull @ColumnInfo(name = "status")
    public String status = "MENUNGGU";

    @Nullable @ColumnInfo(name = "error_terakhir")
    public String errorTerakhir;

    @Nullable @ColumnInfo(name = "coba_terakhir")
    public String cobaTerakhir;

    @NonNull @ColumnInfo(name = "dibuat_pada")
    public String dibuatPada = "";

    @ColumnInfo(name = "dibuat_oleh")
    public Long dibuatOleh;

    @Nullable @ColumnInfo(name = "diubah_pada")
    public String diubahPada;

    @ColumnInfo(name = "diubah_oleh")
    public Long diubahOleh;
}
