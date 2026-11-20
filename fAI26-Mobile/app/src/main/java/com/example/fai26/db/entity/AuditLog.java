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
@Entity(tableName = "audit_log",
    indices = {
        @Index(value = {"tabel", "baris_id"}, name = "ix_audit_log_tabel_baris_id"),
        @Index(value = {"user_id", "waktu"}, name = "ix_audit_log_user_id_waktu")})
public class AuditLog {

    @NonNull @PrimaryKey @ColumnInfo(name = "audit_id")
    public String auditId = "";

    @NonNull @ColumnInfo(name = "tabel")
    public String tabel = "";

    @NonNull @ColumnInfo(name = "baris_id")
    public String barisId = "";

    @NonNull @ColumnInfo(name = "aksi")
    public String aksi = "";

    @Nullable @ColumnInfo(name = "nilai_lama")
    public String nilaiLama;

    @Nullable @ColumnInfo(name = "nilai_baru")
    public String nilaiBaru;

    @ColumnInfo(name = "user_id")
    public Long userId;

    @Nullable @ColumnInfo(name = "username")
    public String username;

    @Nullable @ColumnInfo(name = "device_id")
    public String deviceId;

    @NonNull @ColumnInfo(name = "waktu")
    public String waktu = "";

    @ColumnInfo(name = "disinkron")
    public long disinkron = 0;

    @Nullable @ColumnInfo(name = "keterangan")
    public String keterangan;
}
