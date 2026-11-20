package com.example.fai26.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Membaca QR dari bidang luminansi (Y) gambar kamera memakai ZXing (offline).
 * Tidak thread-safe: pakai satu instance per thread analisis.
 */
public final class QrDecoder {

    private final MultiFormatReader reader = new MultiFormatReader();

    public QrDecoder() {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        reader.setHints(hints);
    }

    /**
     * @param y      luminansi, satu byte per piksel, baris berurutan tanpa celah
     * @param lebar  lebar gambar
     * @param tinggi tinggi gambar
     * @return teks QR, atau null bila tidak ada QR yang terbaca pada gambar ini
     */
    public String baca(byte[] y, int lebar, int tinggi) {
        PlanarYUVLuminanceSource sumber = new PlanarYUVLuminanceSource(
                y, lebar, tinggi, 0, 0, lebar, tinggi, false);
        String hasil = coba(new BinaryBitmap(new HybridBinarizer(sumber)));
        if (hasil != null) return hasil;
        // QR cetak terbalik (putih di atas hitam) atau tercermin oleh bahan mengkilap.
        return coba(new BinaryBitmap(new HybridBinarizer(sumber.invert())));
    }

    private String coba(BinaryBitmap bitmap) {
        try {
            Result r = reader.decodeWithState(bitmap);
            return r.getText();
        } catch (NotFoundException e) {
            return null;
        } catch (Exception e) {
            return null; // ChecksumException atau FormatException: QR rusak pada frame ini
        } finally {
            reader.reset();
        }
    }
}
