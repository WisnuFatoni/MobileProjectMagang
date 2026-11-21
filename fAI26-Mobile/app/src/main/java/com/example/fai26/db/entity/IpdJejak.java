package com.example.fai26.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Satu titik jalur GPS inspeksi IPD. Dicatat selama inspeksi berjalan (tiap 3 meter, akurasi baik saja) dan dikirim bersama
 * inspeksi. Tanpa kunci asing ke ipd_header supaya perekam latar tidak bergantung pada kapan draf pertama kali disimpan; titik
 * dibersihkan bersama drafnya.
 */
@Entity(tableName = "ipd_jejak", indices = {@Index(value = {"ipd_id"}, name = "ix_ipd_jejak_ipd_id")})
public class IpdJejak {

    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "jejak_id")
    public long jejakId;

    @NonNull @ColumnInfo(name = "ipd_id")
    public String ipdId = "";

    /** Waktu titik diambil, UTC ISO 8601. */
    @NonNull @ColumnInfo(name = "waktu")
    public String waktu = "";

    @ColumnInfo(name = "lat")
    public double lat;

    @ColumnInfo(name = "lon")
    public double lon;

    /** Akurasi dalam meter; null bila perangkat tidak melaporkan. */
    @Nullable @ColumnInfo(name = "akurasi")
    public Double akurasi;
}
