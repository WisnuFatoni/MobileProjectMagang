package com.example.fai26.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.RoomWarnings;

/** Satu inspeksi panen detail (satu pemanen). Status: DRAFT, MENUNGGU_SETUJU, DISETUJUI, DITOLAK. */
@SuppressWarnings(RoomWarnings.MISSING_INDEX_ON_FOREIGN_KEY_CHILD)
@Entity(tableName = "ipd_header",
    indices = {
        @Index(value = {"tanggal_inspeksi", "unit_id"}, name = "ix_ipd_header_tanggal_inspeksi_unit_id"),
        @Index(value = {"status"}, name = "ix_ipd_header_status"),
        @Index(value = {"pemanen_id", "blok_id", "tanggal_inspeksi"}, name = "ix_ipd_header_pemanen_id_blok_id_tanggal_inspeksi")})
public class IpdHeader {

    @NonNull @PrimaryKey @ColumnInfo(name = "ipd_id")
    public String ipdId = "";

    @ColumnInfo(name = "unit_id")
    public long unitId;

    @ColumnInfo(name = "blok_id")
    public long blokId;

    @ColumnInfo(name = "pemanen_id")
    public long pemanenId;

    @ColumnInfo(name = "user_id")
    public long userId;

    @NonNull @ColumnInfo(name = "device_id")
    public String deviceId = "";

    @NonNull @ColumnInfo(name = "tanggal_inspeksi")
    public String tanggalInspeksi = "";

    @Nullable @ColumnInfo(name = "ancak_no")
    public String ancakNo;

    @ColumnInfo(name = "divisi_id")
    public long divisiId;

    @ColumnInfo(name = "asisten_user_id")
    public Long asistenUserId;

    @ColumnInfo(name = "mandor_blok_user_id")
    public Long mandorBlokUserId;

    @ColumnInfo(name = "mandor_blok_hrmb")
    public Long mandorBlokHrmb;

    @ColumnInfo(name = "mandor_ancak_user_id")
    public Long mandorAncakUserId;

    @ColumnInfo(name = "mandor_ancak_hrmb")
    public Long mandorAncakHrmb;

    @Nullable @ColumnInfo(name = "catatan")
    public String catatan;

    @NonNull @ColumnInfo(name = "status")
    public String status = "DRAFT";

    @Nullable @ColumnInfo(name = "dikirim_pada")
    public String dikirimPada;

    @Nullable @ColumnInfo(name = "pesan_server")
    public String pesanServer;

    @ColumnInfo(name = "divalidasi_oleh")
    public Long divalidasiOleh;

    @Nullable @ColumnInfo(name = "divalidasi_pada")
    public String divalidasiPada;

    @ColumnInfo(name = "disetujui_oleh")
    public Long disetujuiOleh;

    @Nullable @ColumnInfo(name = "disetujui_pada")
    public String disetujuiPada;

    @ColumnInfo(name = "kirim_ulang")
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
