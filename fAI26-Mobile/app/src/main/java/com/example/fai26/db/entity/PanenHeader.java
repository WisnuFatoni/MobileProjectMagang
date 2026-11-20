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
@Entity(tableName = "panen_header",
    foreignKeys = {
        @ForeignKey(entity = Unit.class, parentColumns = "unit_id", childColumns = "unit_id"),
        @ForeignKey(entity = Blok.class, parentColumns = "blok_id", childColumns = "blok_id"),
        @ForeignKey(entity = Tph.class, parentColumns = "tph_id", childColumns = "tph_id"),
        @ForeignKey(entity = AppUser.class, parentColumns = "user_id", childColumns = "user_id"),
        @ForeignKey(entity = Device.class, parentColumns = "device_id", childColumns = "device_id"),
        @ForeignKey(entity = ModelVersi.class, parentColumns = "model_id", childColumns = "model_id"),
        @ForeignKey(entity = PeriodeRotasi.class, parentColumns = "periode_id", childColumns = "periode_id")},
    indices = {
        @Index(value = {"tanggal_panen", "unit_id"}, name = "ix_panen_header_tanggal_panen_unit_id"),
        @Index(value = {"status"}, name = "ix_panen_header_status"),
        @Index(value = {"tph_id", "tanggal_panen"}, name = "ix_panen_header_tph_id_tanggal_panen")})
public class PanenHeader {

    @NonNull @PrimaryKey @ColumnInfo(name = "panen_id")
    public String panenId = "";

    @ColumnInfo(name = "unit_id")
    public long unitId;

    @ColumnInfo(name = "blok_id")
    public long blokId;

    @ColumnInfo(name = "tph_id")
    public long tphId;

    @ColumnInfo(name = "user_id")
    public long userId;

    @NonNull @ColumnInfo(name = "device_id")
    public String deviceId = "";

    @ColumnInfo(name = "model_id")
    public Long modelId;

    @ColumnInfo(name = "periode_id")
    public Long periodeId;

    @NonNull @ColumnInfo(name = "tanggal_panen")
    public String tanggalPanen = "";

    @ColumnInfo(name = "rotasi_no")
    public Long rotasiNo;

    @ColumnInfo(name = "kondisi_cuaca")
    public Long kondisiCuaca;

    @ColumnInfo(name = "brondolan_kg")
    public double brondolanKg = 0;

    @ColumnInfo(name = "gps_lat")
    public Double gpsLat;

    @ColumnInfo(name = "gps_lon")
    public Double gpsLon;

    @ColumnInfo(name = "gps_akurasi_m")
    public Double gpsAkurasiM;

    @Nullable @ColumnInfo(name = "gps_waktu")
    public String gpsWaktu;

    @ColumnInfo(name = "gps_mock")
    public long gpsMock = 0;

    @ColumnInfo(name = "jarak_ke_tph_m")
    public Double jarakKeTphM;

    @NonNull @ColumnInfo(name = "status_lokasi")
    public String statusLokasi = "TANPA_GPS";

    @Nullable @ColumnInfo(name = "catatan")
    public String catatan;

    @NonNull @ColumnInfo(name = "status")
    public String status = "DRAFT";

    @Nullable @ColumnInfo(name = "dikirim_pada")
    public String dikirimPada;

    @Nullable @ColumnInfo(name = "pesan_server")
    public String pesanServer;

    @ColumnInfo(name = "disetujui_oleh")
    public Long disetujuiOleh;

    @Nullable @ColumnInfo(name = "disetujui_pada")
    public String disetujuiPada;

    /** 1 bila catatan DITOLAK sudah diperbaiki dan sengaja dikirim ulang dengan panen_id yang sama. Versi DB 4. */
    @ColumnInfo(name = "kirim_ulang", defaultValue = "0")
    public long kirimUlang = 0;

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
