package com.example.fai26.sync;

import com.example.fai26.db.dao.Hasil;
import com.example.fai26.db.entity.AuditLog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/** Membentuk badan JSON kiriman sesuai fai26-api/docs/API.md. Murni (tanpa Android) supaya mudah diuji. */
public final class PayloadBuilder {

    private PayloadBuilder() {
    }

    /** Badan POST /api/panen. Kolom yang kosong tidak ikut dikirim. */
    public static JSONObject panen(Hasil.PanenKirim p, List<Hasil.GradingKirim> grading,
                                   List<Hasil.PemanenKirim> pemanen) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("panen_id", p.panenId);
        o.put("unit_kode", p.unitKode);
        o.put("tanggal_panen", p.tanggalPanen);
        o.put("asm3_oid", p.asm3Oid);
        o.putOpt("blok_kode", p.blokKode);
        o.putOpt("tph_kode", p.tphKode);
        o.putOpt("rotasi", p.rotasi);
        o.putOpt("kondisi_cuaca", p.kondisiCuaca);
        o.put("brondolan_kg", p.brondolanKg);
        o.putOpt("gps_lat", p.gpsLat);
        o.putOpt("gps_lon", p.gpsLon);
        o.putOpt("gps_akurasi_m", p.gpsAkurasiM);
        o.putOpt("gps_waktu", p.gpsWaktu);
        o.put("gps_mock", p.gpsMock != 0);
        o.putOpt("jarak_ke_tph_m", p.jarakKeTphM);
        o.put("status_lokasi", p.statusLokasi);
        o.putOpt("model_versi", p.modelVersi);
        o.putOpt("catatan", p.catatan);
        o.put("dibuat_pada", p.dibuatPada);
        o.putOpt("pencatat_username", p.pencatatUsername);
        o.putOpt("disetujui_username", p.disetujuiUsername);
        o.putOpt("disetujui_pada", p.disetujuiPada);
        if (p.kirimUlang != 0) o.put("kirim_ulang", true); // perbaikan catatan DITOLAK, panen_id sama

        JSONArray g = new JSONArray();
        for (Hasil.GradingKirim x : grading) {
            JSONObject b = new JSONObject();
            b.put("kelas_kode", x.kelasKode);
            b.putOpt("erp_kolom", x.erpKolom);
            b.put("jml_ai", x.jmlAi);
            b.put("jml_final", x.jmlFinal);
            b.put("diubah_manual", x.diubahManual != 0);
            g.put(b);
        }
        o.put("grading", g);

        JSONArray pm = new JSONArray();
        for (Hasil.PemanenKirim x : pemanen) {
            JSONObject b = new JSONObject();
            b.put("hrmb_code", x.hrmbCode);
            b.put("jjg_alokasi", x.jjgAlokasi == null ? 0 : x.jjgAlokasi);
            pm.put(b);
        }
        o.put("pemanen", pm);
        return o;
    }

    /** Badan POST /api/ipd: satu inspeksi (satu pemanen) beserta semua pokok yang diperiksa. */
    public static JSONObject ipd(com.example.fai26.db.dao.IpdHasil.HeaderKirim h, List<com.example.fai26.db.dao.IpdHasil.PokokKirim> pokok,
                                 List<com.example.fai26.db.dao.IpdHasil.TemuanKirim> temuan) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("ipd_id", h.ipdId);
        o.put("unit_kode", h.unitKode);
        o.put("tanggal_inspeksi", h.tanggalInspeksi);
        o.put("divisi_kode", h.divisiKode);
        o.put("asm3_oid", h.asm3Oid);
        o.putOpt("blok_kode", h.blokKode);
        o.put("pemanen_hrmb_code", h.pemanenHrmb);
        o.putOpt("ancak_no", h.ancakNo);
        o.putOpt("mandor_username", h.mandorUsername);
        o.putOpt("catatan", h.catatan);
        o.put("dibuat_pada", h.dibuatPada);
        if (h.kirimUlang != 0) o.put("kirim_ulang", true); // mengubah inspeksi yang sudah terkirim atau memperbaiki yang ditolak

        java.util.Map<Long, JSONArray> perPokok = new java.util.HashMap<>();
        for (com.example.fai26.db.dao.IpdHasil.TemuanKirim t : temuan) {
            JSONObject b = new JSONObject();
            b.put("kode", t.kode);
            b.put("jumlah", t.jumlah);
            b.putOpt("catatan", t.catatan);
            JSONArray a = perPokok.get(t.pokokId);
            if (a == null) {
                a = new JSONArray();
                perPokok.put(t.pokokId, a);
            }
            a.put(b);
        }
        JSONArray pk = new JSONArray();
        for (com.example.fai26.db.dao.IpdHasil.PokokKirim p : pokok) {
            JSONObject b = new JSONObject();
            b.put("no_baris", p.noBaris);
            b.put("no_pokok", p.noPokok);
            JSONArray a = perPokok.get(p.pokokId);
            b.put("temuan", a == null ? new JSONArray() : a);
            pk.put(b);
        }
        o.put("pokok", pk);
        return o;
    }

    /** Badan POST /api/audit/batch. */
    public static JSONArray audit(List<AuditLog> baris) throws JSONException {
        JSONArray a = new JSONArray();
        for (AuditLog x : baris) {
            JSONObject o = new JSONObject();
            o.put("audit_id", x.auditId);
            o.put("tabel", x.tabel);
            o.put("baris_id", x.barisId);
            o.put("aksi", x.aksi);
            o.putOpt("nilai_lama", x.nilaiLama);
            o.putOpt("nilai_baru", x.nilaiBaru);
            o.putOpt("username", x.username);
            o.put("waktu", x.waktu);
            o.putOpt("keterangan", x.keterangan);
            a.put(o);
        }
        return a;
    }
}
