package com.example.fai26;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adapter daftar pilihan yang bisa dicari dengan mengetik: setiap kata yang diketik harus ada di teks pilihan (huruf besar
 * atau kecil sama saja, urutan kata bebas). Contoh: "c7 67" menemukan "Blok C7 • TPH 67". Dipakai untuk daftar TPH dan pemanen
 * yang panjang supaya tidak perlu menggulir.
 */
public final class PencarianAdapter<T> extends ArrayAdapter<T> {

    private final List<T> semua;

    public PencarianAdapter(Context c, List<T> data) {
        super(c, android.R.layout.simple_list_item_1, new ArrayList<>(data));
        semua = new ArrayList<>(data);
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence q) {
                String teks = q == null ? "" : q.toString().trim().toLowerCase(Locale.ROOT);
                List<T> hasil = new ArrayList<>();
                if (teks.isEmpty()) {
                    hasil.addAll(semua);
                } else {
                    String[] kata = teks.split("\\s+");
                    for (T item : semua) {
                        String s = String.valueOf(item).toLowerCase(Locale.ROOT);
                        boolean cocok = true;
                        for (String k : kata) {
                            if (!s.contains(k)) {
                                cocok = false;
                                break;
                            }
                        }
                        if (cocok) hasil.add(item);
                    }
                }
                FilterResults r = new FilterResults();
                r.values = hasil;
                r.count = hasil.size();
                return r;
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void publishResults(CharSequence q, FilterResults r) {
                clear();
                if (r != null && r.values != null) addAll((List<T>) r.values);
                notifyDataSetChanged();
            }

            @Override
            public CharSequence convertResultToString(Object o) {
                return String.valueOf(o);
            }
        };
    }
}
