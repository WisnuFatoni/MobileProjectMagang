package com.example.fai26.ipd;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.fai26.db.IpdRepository;
import com.example.fai26.db.dao.IpdHasil;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

/** Kartu satu inspeksi IPD di HP (dipakai Beranda IPD dan Riwayat IPD). */
final class IpdKartu {

    interface Aksi {
        void edit(IpdHasil.Riwayat r);

        void hapusDraf(IpdHasil.Riwayat r);
    }

    private IpdKartu() {
    }

    static View buat(Context c, IpdHasil.Riwayat r, Aksi aksi) {
        LinearLayout[] isi = new LinearLayout[1];
        MaterialCardView kartu = IpdUi.kartu(c, isi);
        LinearLayout l = isi[0];

        LinearLayout atas = new LinearLayout(c);
        atas.setOrientation(LinearLayout.HORIZONTAL);
        atas.setGravity(Gravity.CENTER_VERTICAL);
        TextView judul = IpdUi.judul(c, r.pemanen + " • Blok " + r.blok, 16);
        atas.addView(judul, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        int[] w = IpdUi.warnaStatus(c, r.status);
        atas.addView(IpdUi.lencana(c, IpdUi.labelStatus(r.status), w[0], w[1]));
        l.addView(atas);

        double persen = r.pokok > 0 ? Math.round(r.bertemuan * 1000.0 / r.pokok) / 10.0 : 0;
        StringBuilder meta = new StringBuilder();
        meta.append(IpdUi.tanggal(r.tanggalInspeksi)).append(" • ").append(r.divisi);
        meta.append("\n").append(r.pokok).append(" pokok diperiksa • ").append(r.bertemuan).append(" bertemuan (").append(IpdUi.persen(persen)).append(")");
        if (r.asisten != null && !r.asisten.isEmpty()) meta.append("\nAsisten: ").append(r.asisten);
        TextView txt = IpdUi.redup(c, meta, 13);
        txt.setPadding(0, IpdUi.dp(c, 4), 0, 0);
        l.addView(txt);

        if (r.pesanServer != null && !r.pesanServer.isEmpty()) {
            TextView pesan = IpdUi.teks(c, r.pesanServer, 13, true, androidx.appcompat.R.attr.colorError);
            pesan.setPadding(0, IpdUi.dp(c, 6), 0, 0);
            l.addView(pesan);
        }

        if (aksi != null && IpdRepository.bisaDiubah(r.status)) {
            LinearLayout tombol = new LinearLayout(c);
            tombol.setOrientation(LinearLayout.HORIZONTAL);
            tombol.setPadding(0, IpdUi.dp(c, 10), 0, 0);
            String label = IpdRepository.DRAFT.equals(r.status) ? "Lanjutkan" : IpdRepository.DITOLAK.equals(r.status) ? "Perbaiki dan kirim ulang" : "Edit sebelum disetujui";
            MaterialButton edit = IpdUi.tombol(c, label, false);
            edit.setOnClickListener(v -> aksi.edit(r));
            tombol.addView(edit, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            if (IpdRepository.DRAFT.equals(r.status)) {
                MaterialButton hapus = IpdUi.tombol(c, "Hapus", false);
                hapus.setOnClickListener(v -> aksi.hapusDraf(r));
                LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                hlp.setMarginStart(IpdUi.dp(c, 8));
                tombol.addView(hapus, hlp);
            }
            l.addView(tombol);
        }
        return kartu;
    }
}
