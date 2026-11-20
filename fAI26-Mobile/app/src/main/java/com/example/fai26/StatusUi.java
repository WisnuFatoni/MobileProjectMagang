package com.example.fai26;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;

/** Tampilan status catatan panen: teks Indonesia dan warna lencana. */
final class StatusUi {

    private StatusUi() {
    }

    static String teks(String status) {
        if (status == null) return "";
        switch (status) {
            case "DRAFT":
                return "Draft";
            case "DISETUJUI":
                return "Disetujui";
            case "SIAP_KIRIM":
                return "Siap kirim";
            case "TERKIRIM":
                return "Terkirim";
            case "DITERIMA":
                return "Diterima";
            case "DITOLAK":
                return "Ditolak";
            case "MENUNGGU_SETUJU":
                return "Menunggu krani";
            default:
                return status;
        }
    }

    /** Warna latar lencana (ARGB) dan warna teks. */
    private static int[] warna(String status) {
        if (status == null) return new int[]{0xFFE0E0E0, 0xFF303030};
        switch (status) {
            case "DISETUJUI":
            case "TERKIRIM":
            case "DITERIMA":
                return new int[]{0xFFDCFCE7, 0xFF14532D};
            case "SIAP_KIRIM":
            case "MENUNGGU_SETUJU":
                return new int[]{0xFFDBEAFE, 0xFF1E3A8A};
            case "DITOLAK":
                return new int[]{0xFFFCE8E6, 0xFF7A1410};
            case "DRAFT":
            default:
                return new int[]{0xFFFEF3C7, 0xFF5B3A00};
        }
    }

    /** Memberi teks dan warna lencana pada sebuah TextView. */
    static void pasang(Context c, TextView v, String status) {
        float d = c.getResources().getDisplayMetrics().density;
        int[] w = warna(status);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(w[0]);
        bg.setCornerRadius(12 * d);
        v.setBackground(bg);
        v.setTextColor(w[1]);
        v.setText(teks(status));
        v.setTextSize(12);
        v.setPadding((int) (10 * d), (int) (4 * d), (int) (10 * d), (int) (4 * d));
    }
}
