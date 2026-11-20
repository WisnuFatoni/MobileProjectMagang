package com.example.fai26;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.fai26.db.AppDatabase;
import com.example.fai26.db.Waktu;
import com.example.fai26.db.dao.Hasil;
import com.example.fai26.sync.ApiException;
import com.example.fai26.sync.HttpServerApi;
import com.example.fai26.sync.PengaturanSync;
import com.example.fai26.sync.ServerApi;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bagian dashboard di Beranda: produksi hari ini (target dan kualitas), rekap per blok, tren 7 hari, dan
 * peringatan tindak lanjut.
 *
 * Sumber data:
 * - Saat terhubung ke server, mengambil data nyata dari {@code GET /api/panen/ringkasan} di database server
 *   ditambah catatan lokal di HP yang belum terkirim (agar catatan yang baru dibuat tetap langsung terhitung).
 * - Saat offline, otomatis memakai database lokal HP dan cache server terakhir.
 */
final class DashboardBeranda {

    private static final String PREF = "beranda";
    private static final String KUNCI_TARGET = "target_harian";
    private static final String KUNCI_CACHE_JSON = "cache_ringkasan_json";
    private static final String KUNCI_CACHE_TANGGAL = "cache_ringkasan_tanggal";
    private static final String KUNCI_CACHE_JAM = "cache_ringkasan_jam";
    static final int TARGET_BAWAAN = 200;
    /** Indeks kelas model "Ripe" (Matang). */
    private static final int KELAS_MATANG = 3;
    private static final int HARI_TREN = 7;

    private DashboardBeranda() {
    }

    /** Hasil pembacaan dashboard (baik dari database lokal maupun gabungan data real server). */
    static final class Data {
        long catatanHariIni;
        long janjangHariIni;
        long[] perKelas = new long[KelasModel.JUMLAH];
        List<Hasil.BlokRekap> blok = new ArrayList<>();
        /** Janjang 7 hari terakhir, indeks 0 = 6 hari lalu, indeks 6 = hari ini. */
        long[] tren = new long[HARI_TREN];
        String[] tanggalTren = new String[HARI_TREN];
        long ditolak;
        long tertunda;
        boolean dariServer;
        String infoSumber = "Data Lokal HP (Offline)";
    }

    /** Memuat cepat dari database lokal HP (ditambah cache server hari ini bila ada). */
    static Data muatLokal(Context ctx, AppDatabase db, long pemilik) {
        String hariIni = Waktu.tanggalLokalHariIni();
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String cacheTanggal = sp.getString(KUNCI_CACHE_TANGGAL, "");
        String cacheJson = sp.getString(KUNCI_CACHE_JSON, null);
        String cacheJam = sp.getString(KUNCI_CACHE_JAM, "");
        if (hariIni.equals(cacheTanggal) && cacheJson != null) {
            try {
                Data d = gabungServerDanLokalBelumKirim(new JSONObject(cacheJson), db, pemilik, hariIni);
                d.dariServer = true;
                d.infoSumber = "Data Real Server • Diperbarui " + cacheJam;
                return d;
            } catch (Exception ignored) {
                // Bila cache rusak, jatuh ke perhitungan lokal murni di bawah.
            }
        }
        return hitungMurniLokal(db, pemilik, hariIni);
    }

    /**
     * Mengambil data real-time langsung dari endpoint {@code GET /api/panen/ringkasan} di server,
     * menyimpannya ke cache, dan menggabungkannya dengan catatan lokal yang belum terkirim.
     */
    static Data muatDariServer(Context ctx, AppDatabase db, long pemilik) {
        String hariIni = Waktu.tanggalLokalHariIni();
        PengaturanSync pref = new PengaturanSync(ctx);
        if (!pref.serverSudahDiatur() || !pref.adaToken()) {
            return muatLokal(ctx, db, pemilik);
        }
        HttpServerApi api = new HttpServerApi(pref.serverUrl());
        try {
            String token = tokenValid(api, pref);
            JSONObject json;
            try {
                json = api.ringkasanPanen(token, hariIni);
            } catch (ApiException e) {
                if (!e.tidakLogin()) throw e;
                token = perbaruiToken(api, pref);
                json = api.ringkasanPanen(token, hariIni);
            }
            String jam = new SimpleDateFormat("HH:mm", new Locale("id", "ID")).format(new Date());
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                    .putString(KUNCI_CACHE_TANGGAL, hariIni)
                    .putString(KUNCI_CACHE_JSON, json.toString())
                    .putString(KUNCI_CACHE_JAM, jam)
                    .apply();
            Data d = gabungServerDanLokalBelumKirim(json, db, pemilik, hariIni);
            d.dariServer = true;
            String lingkup = "UNIT".equals(json.optString("lingkup")) ? "Se-Unit" : "Akun Ini";
            d.infoSumber = "Data Real Server (" + lingkup + ") • Diperbarui " + jam;
            return d;
        } catch (Exception e) {
            Data lokal = muatLokal(ctx, db, pemilik);
            if (!lokal.dariServer) {
                lokal.infoSumber = "Data Lokal HP • Server belum terjangkau";
            }
            return lokal;
        }
    }

    private static String tokenValid(ServerApi api, PengaturanSync pref) throws ApiException {
        String akses = pref.akses();
        if (akses != null && pref.aksesKedaluwarsa() > System.currentTimeMillis() + 30_000L) return akses;
        return perbaruiToken(api, pref);
    }

    private static String perbaruiToken(ServerApi api, PengaturanSync pref) throws ApiException {
        String refresh = pref.refresh();
        if (refresh == null) throw new ApiException(401, "Belum login online");
        ServerApi.Token t = api.refresh(refresh);
        pref.simpanToken(pref.tokenUser(), t);
        return t.akses;
    }

    private static Data gabungServerDanLokalBelumKirim(JSONObject srv, AppDatabase db, long pemilik, String hariIni) {
        Data d = new Data();
        siapkanTanggalTren(d);

        // 1. Catatan & janjang hari ini: data server + catatan lokal yang belum terkirim
        Hasil.RingkasanHari belum = db.panenDao().ringkasanHariBelumKirim(hariIni, pemilik);
        long srvCatatan = srv.optLong("catatan_hari_ini", 0);
        long srvJanjang = srv.optLong("janjang_hari_ini", 0);
        d.catatanHariIni = srvCatatan + (belum != null ? belum.catatan : 0);
        d.janjangHariIni = srvJanjang + (belum != null ? belum.janjang : 0);

        // 2. Per kelas hari ini
        JSONArray arrKelas = srv.optJSONArray("per_kelas");
        if (arrKelas != null) {
            for (int i = 0; i < arrKelas.length(); i++) {
                JSONObject o = arrKelas.optJSONObject(i);
                if (o == null) continue;
                int idx = indeksKelasKode(o.optString("kelas_kode", ""));
                if (idx >= 0) d.perKelas[idx] += o.optLong("janjang", 0);
            }
        }
        for (Hasil.KelasJumlah k : db.panenDao().kelasHariBelumKirim(hariIni, pemilik)) {
            if (k.label >= 0 && k.label < KelasModel.JUMLAH) d.perKelas[(int) k.label] += k.jumlah;
        }
        long sumKelas = 0;
        for (long v : d.perKelas) sumKelas += v;
        if (sumKelas > d.janjangHariIni) d.janjangHariIni = sumKelas;

        // 3. Rekap per blok hari ini
        Map<String, Hasil.BlokRekap> petaBlok = new LinkedHashMap<>();
        JSONArray arrBlok = srv.optJSONArray("per_blok");
        if (arrBlok != null) {
            for (int i = 0; i < arrBlok.length(); i++) {
                JSONObject o = arrBlok.optJSONObject(i);
                if (o == null) continue;
                Hasil.BlokRekap b = new Hasil.BlokRekap();
                b.blok = o.optString("blok_kode", "-");
                b.jumlahTph = o.optLong("tph", 0);
                b.catatan = o.optLong("catatan", 0);
                b.janjang = o.optLong("janjang", 0);
                petaBlok.put(b.blok, b);
            }
        }
        for (Hasil.BlokRekap bLokal : db.panenDao().rekapBlokHariBelumKirim(hariIni, pemilik)) {
            Hasil.BlokRekap ada = petaBlok.get(bLokal.blok);
            if (ada == null) {
                petaBlok.put(bLokal.blok, bLokal);
            } else {
                ada.jumlahTph = Math.max(ada.jumlahTph, bLokal.jumlahTph);
                ada.catatan += bLokal.catatan;
                ada.janjang += bLokal.janjang;
            }
        }
        d.blok = new ArrayList<>(petaBlok.values());
        Collections.sort(d.blok, (a, b) -> Long.compare(b.janjang, a.janjang));
        if (d.blok.size() > 5) d.blok = new ArrayList<>(d.blok.subList(0, 5));

        // 4. Tren 7 hari terakhir
        Map<String, Long> petaTren = new HashMap<>();
        JSONArray arrTren = srv.optJSONArray("tren_7_hari");
        if (arrTren != null) {
            for (int i = 0; i < arrTren.length(); i++) {
                JSONObject o = arrTren.optJSONObject(i);
                if (o == null) continue;
                petaTren.put(o.optString("tanggal", ""), o.optLong("janjang", 0));
            }
        }
        for (Hasil.HariJumlah hLokal : db.panenDao().janjangPerHariBelumKirim(d.tanggalTren[0], pemilik)) {
            long lama = petaTren.containsKey(hLokal.tanggal) ? petaTren.get(hLokal.tanggal) : 0L;
            petaTren.put(hLokal.tanggal, lama + hLokal.janjang);
        }
        for (int i = 0; i < HARI_TREN; i++) {
            Long v = petaTren.get(d.tanggalTren[i]);
            d.tren[i] = v == null ? 0 : v;
        }

        // 5. Status ditolak & tertunda
        JSONObject st = srv.optJSONObject("catatan_per_status");
        long srvDitolak = st != null ? st.optLong("DITOLAK", 0) : 0;
        d.ditolak = Math.max(srvDitolak, db.panenDao().jumlahDitolak(pemilik));
        d.tertunda = db.panenDao().jumlahBelumTerkirim(pemilik);
        return d;
    }

    private static int indeksKelasKode(String kode) {
        if (kode == null) return -1;
        switch (kode.toLowerCase(Locale.US)) {
            case "abnormal":
                return 0;
            case "empty":
            case "empty bunch":
                return 1;
            case "overripe":
                return 2;
            case "ripe":
                return 3;
            case "underripe":
                return 4;
            case "unripe":
                return 5;
            default:
                return -1;
        }
    }

    private static Data hitungMurniLokal(AppDatabase db, long pemilik, String hariIni) {
        Data d = new Data();
        Hasil.RingkasanHari rh = db.panenDao().ringkasanHari(hariIni, pemilik);
        if (rh != null) {
            d.catatanHariIni = rh.catatan;
            d.janjangHariIni = rh.janjang;
        }
        for (Hasil.KelasJumlah k : db.panenDao().kelasHari(hariIni, pemilik)) {
            if (k.label >= 0 && k.label < KelasModel.JUMLAH) d.perKelas[(int) k.label] += k.jumlah;
        }
        d.blok = db.panenDao().rekapBlokHari(hariIni, pemilik, 5);
        siapkanTanggalTren(d);
        Map<String, Long> peta = new HashMap<>();
        for (Hasil.HariJumlah h : db.panenDao().janjangPerHari(d.tanggalTren[0], pemilik)) peta.put(h.tanggal, h.janjang);
        for (int i = 0; i < HARI_TREN; i++) {
            Long v = peta.get(d.tanggalTren[i]);
            d.tren[i] = v == null ? 0 : v;
        }
        d.ditolak = db.panenDao().jumlahDitolak(pemilik);
        d.tertunda = db.panenDao().jumlahBelumTerkirim(pemilik);
        d.dariServer = false;
        d.infoSumber = "Data Lokal HP (Offline)";
        return d;
    }

    private static void siapkanTanggalTren(Data d) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -(HARI_TREN - 1));
        for (int i = 0; i < HARI_TREN; i++) {
            d.tanggalTren[i] = f.format(c.getTime());
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
    }

    static int target(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(KUNCI_TARGET, TARGET_BAWAAN);
    }

    private static void simpanTarget(Context c, int nilai) {
        SharedPreferences.Editor e = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit();
        e.putInt(KUNCI_TARGET, nilai).apply();
    }

    /** Menggambar seluruh dashboard. Dipanggil di thread utama. */
    static void tampil(View raiz, Data d, Runnable bukaRiwayat, Runnable ulangi, Runnable segarkanServer) {
        Context ctx = raiz.getContext();
        tampilProduksi(raiz, ctx, d, ulangi, segarkanServer);
        tampilBlok(raiz, ctx, d);
        tampilTren(raiz, ctx, d);
        tampilTindak(raiz, d, bukaRiwayat);
    }

    // ---- produksi hari ini ------------------------------------------------------------------------

    private static void tampilProduksi(View raiz, Context ctx, Data d, Runnable ulangi, Runnable segarkanServer) {
        int target = target(ctx);
        TextView txtSumber = raiz.findViewById(R.id.txtSumberData);
        if (txtSumber != null) {
            txtSumber.setText(d.infoSumber);
            txtSumber.setTextColor(d.dariServer ? primer(raiz) : redup(raiz));
        }
        View btnSegarkan = raiz.findViewById(R.id.btnSegarkanDashboard);
        if (btnSegarkan != null && segarkanServer != null) {
            btnSegarkan.setOnClickListener(v -> segarkanServer.run());
        }

        TextView txtTarget = raiz.findViewById(R.id.txtTarget);
        LinearProgressIndicator progres = raiz.findViewById(R.id.progresTarget);
        TextView info = raiz.findViewById(R.id.txtProgresInfo);
        txtTarget.setText(Format.angka(d.janjangHariIni) + " / " + Format.angka(target));
        txtTarget.setOnClickListener(v -> dialogTarget(ctx, target, ulangi));
        int persen = target <= 0 ? 0 : (int) Math.min(100, Math.round(d.janjangHariIni * 100.0 / target));
        progres.setProgressCompat(persen, true);
        if (d.janjangHariIni >= target) {
            info.setText("Target tercapai (" + persen + "%). Ketuk angka target untuk mengubahnya.");
        } else {
            info.setText(persen + "% dari target, kurang " + Format.angka(target - d.janjangHariIni)
                    + " janjang. Ketuk angka target untuk mengubahnya.");
        }

        TextView matang = raiz.findViewById(R.id.txtPersenMatang);
        LinearLayout bar = raiz.findViewById(R.id.barKelas);
        LinearLayout legenda = raiz.findViewById(R.id.legendaKelas);
        bar.removeAllViews();
        legenda.removeAllViews();
        if (d.janjangHariIni <= 0) {
            matang.setText("Kualitas panen: belum ada data hari ini");
            return;
        }
        long m = d.perKelas[KELAS_MATANG];
        matang.setText("Kualitas panen: " + Math.round(m * 100.0 / d.janjangHariIni) + "% matang");
        float dp = ctx.getResources().getDisplayMetrics().density;
        for (int i = 0; i < KelasModel.JUMLAH; i++) {
            if (d.perKelas[i] <= 0) continue;
            View seg = new View(ctx);
            seg.setBackgroundColor(KelasModel.WARNA[i]);
            bar.addView(seg, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, d.perKelas[i]));

            LinearLayout baris = new LinearLayout(ctx);
            baris.setOrientation(LinearLayout.HORIZONTAL);
            baris.setGravity(Gravity.CENTER_VERTICAL);
            baris.setPadding(0, (int) (3 * dp), 0, (int) (3 * dp));
            View titik = new View(ctx);
            titik.setBackgroundColor(KelasModel.WARNA[i]);
            LinearLayout.LayoutParams lpTitik = new LinearLayout.LayoutParams((int) (12 * dp), (int) (12 * dp));
            lpTitik.setMarginEnd((int) (10 * dp));
            baris.addView(titik, lpTitik);
            baris.addView(teks(ctx, KelasModel.TAMPIL[i], 13, false, onSurface(raiz)),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            long persenKelas = Math.round(d.perKelas[i] * 100.0 / d.janjangHariIni);
            baris.addView(teks(ctx, Format.angka(d.perKelas[i]) + " (" + persenKelas + "%)", 13, true, onSurface(raiz)));
            legenda.addView(baris);
        }
    }

    private static void dialogTarget(Context ctx, int sekarang, Runnable ulangi) {
        EditText input = new EditText(ctx);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(sekarang));
        input.setSelection(input.getText().length());
        float dp = ctx.getResources().getDisplayMetrics().density;
        LinearLayout wadah = new LinearLayout(ctx);
        wadah.setPadding((int) (24 * dp), (int) (8 * dp), (int) (24 * dp), 0);
        wadah.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        new MaterialAlertDialogBuilder(ctx)
                .setTitle("Target janjang per hari")
                .setView(wadah)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Simpan", (dlg, w) -> {
                    try {
                        int v = Integer.parseInt(input.getText().toString().trim());
                        if (v > 0 && v <= 1_000_000) {
                            simpanTarget(ctx, v);
                            ulangi.run();
                        }
                    } catch (NumberFormatException ignored) {
                        // Isian kosong atau terlalu besar: abaikan.
                    }
                })
                .show();
    }

    // ---- rekap blok -------------------------------------------------------------------------------

    private static void tampilBlok(View raiz, Context ctx, Data d) {
        LinearLayout daftar = raiz.findViewById(R.id.daftarBlok);
        View kosong = raiz.findViewById(R.id.txtBlokKosong);
        daftar.removeAllViews();
        kosong.setVisibility(d.blok.isEmpty() ? View.VISIBLE : View.GONE);
        float dp = ctx.getResources().getDisplayMetrics().density;
        long maks = 1;
        for (Hasil.BlokRekap b : d.blok) maks = Math.max(maks, b.janjang);
        for (Hasil.BlokRekap b : d.blok) {
            LinearLayout kolom = new LinearLayout(ctx);
            kolom.setOrientation(LinearLayout.VERTICAL);
            kolom.setPadding(0, (int) (7 * dp), 0, (int) (7 * dp));

            LinearLayout atas = new LinearLayout(ctx);
            atas.setOrientation(LinearLayout.HORIZONTAL);
            atas.addView(teks(ctx, "Blok " + b.blok, 15, true, onSurface(raiz)),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            atas.addView(teks(ctx, Format.angka(b.janjang) + " janjang", 15, true, primer(raiz)));
            kolom.addView(atas);

            kolom.addView(teks(ctx, b.jumlahTph + " TPH • " + b.catatan + " catatan", 12, false, redup(raiz)));

            LinearProgressIndicator p = new LinearProgressIndicator(ctx);
            p.setMax(100);
            p.setTrackThickness((int) (6 * dp));
            p.setTrackCornerRadius((int) (3 * dp));
            p.setProgressCompat((int) Math.round(b.janjang * 100.0 / maks), false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = (int) (6 * dp);
            kolom.addView(p, lp);
            daftar.addView(kolom);
        }
    }

    // ---- tren 7 hari ------------------------------------------------------------------------------

    private static void tampilTren(View raiz, Context ctx, Data d) {
        LinearLayout bar = raiz.findViewById(R.id.barTren);
        TextView judul = raiz.findViewById(R.id.txtJudulTren);
        bar.removeAllViews();
        float dp = ctx.getResources().getDisplayMetrics().density;
        long maks = 1;
        long total = 0;
        for (long v : d.tren) {
            maks = Math.max(maks, v);
            total += v;
        }
        judul.setText("Janjang 7 hari terakhir • total " + Format.angka(total));
        SimpleDateFormat parse = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat hari = new SimpleDateFormat("EEE", new Locale("id", "ID"));
        int tinggiMaks = (int) (96 * dp);
        for (int i = 0; i < HARI_TREN; i++) {
            boolean hariIni = i == HARI_TREN - 1;
            LinearLayout kolom = new LinearLayout(ctx);
            kolom.setOrientation(LinearLayout.VERTICAL);
            kolom.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);

            TextView angka = teks(ctx, d.tren[i] > 0 ? Format.angka(d.tren[i]) : "-", 11, hariIni, onSurface(raiz));
            angka.setGravity(Gravity.CENTER);
            kolom.addView(angka);

            View batang = new View(ctx);
            int warna = primer(raiz);
            batang.setBackgroundColor(hariIni ? warna : Color.argb(110, Color.red(warna), Color.green(warna), Color.blue(warna)));
            int tinggi = d.tren[i] <= 0 ? (int) (3 * dp) : Math.max((int) (6 * dp), (int) (tinggiMaks * d.tren[i] / (double) maks));
            LinearLayout.LayoutParams lpBatang = new LinearLayout.LayoutParams((int) (22 * dp), tinggi);
            lpBatang.topMargin = (int) (3 * dp);
            kolom.addView(batang, lpBatang);

            String nama;
            try {
                nama = hari.format(parse.parse(d.tanggalTren[i]));
            } catch (java.text.ParseException e) {
                nama = "";
            }
            TextView label = teks(ctx, hariIni ? "Hari ini" : nama, 11, hariIni, redup(raiz));
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lpLabel = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lpLabel.topMargin = (int) (4 * dp);
            kolom.addView(label, lpLabel);

            bar.addView(kolom, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
    }

    // ---- tindak lanjut ----------------------------------------------------------------------------

    private static void tampilTindak(View raiz, Data d, Runnable bukaRiwayat) {
        View kartu = raiz.findViewById(R.id.kartuTindak);
        TextView txt = raiz.findViewById(R.id.txtTindak);
        List<String> pesan = new ArrayList<>();
        if (d.ditolak > 0) pesan.add(d.ditolak + " catatan ditolak, perlu dicek");
        if (d.tertunda - d.ditolak > 0) pesan.add((d.tertunda - d.ditolak) + " catatan belum terkirim");
        if (pesan.isEmpty()) {
            kartu.setVisibility(View.GONE);
            return;
        }
        txt.setText(android.text.TextUtils.join("\n", pesan));
        raiz.findViewById(R.id.btnTindak).setOnClickListener(v -> bukaRiwayat.run());
        kartu.setVisibility(View.VISIBLE);
    }

    // ---- util tampilan ----------------------------------------------------------------------------

    private static TextView teks(Context ctx, String isi, int sp, boolean tebal, int warna) {
        TextView t = new TextView(ctx);
        t.setText(isi);
        t.setTextSize(sp);
        t.setTextColor(warna);
        if (tebal) t.setTypeface(t.getTypeface(), Typeface.BOLD);
        return t;
    }

    private static int onSurface(View v) {
        return MaterialColors.getColor(v, com.google.android.material.R.attr.colorOnSurface);
    }

    private static int redup(View v) {
        return MaterialColors.getColor(v, com.google.android.material.R.attr.colorOnSurfaceVariant);
    }

    private static int primer(View v) {
        return MaterialColors.getColor(v, androidx.appcompat.R.attr.colorPrimary);
    }
}
