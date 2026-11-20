package com.example.fai26.qr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.junit.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/** Membuat QR dengan ZXing, mengubahnya jadi "gambar kamera" bidang Y, lalu membacanya kembali. */
public class QrDecoderTest {

    private static final String ISI = "FAI26|TPH|1|KP_STL|A12|07";

    private static BitMatrix buatQr(String isi) throws Exception {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.MARGIN, 4);
        return new QRCodeWriter().encode(isi, BarcodeFormat.QR_CODE, 0, 0, hints);
    }

    /**
     * Gambar abu-abu berukuran lebar x tinggi dengan QR di tengah, diperbesar {@code skala} kali.
     * @param gelap  nilai luminansi modul gelap
     * @param terang nilai luminansi modul terang dan latar
     */
    private static byte[] gambar(BitMatrix qr, int skala, int lebar, int tinggi, int gelap, int terang) {
        byte[] y = new byte[lebar * tinggi];
        java.util.Arrays.fill(y, (byte) terang);
        int w = qr.getWidth() * skala;
        int h = qr.getHeight() * skala;
        int ox = (lebar - w) / 2;
        int oy = (tinggi - h) / 2;
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                boolean hitam = qr.get(i / skala, j / skala);
                y[(oy + j) * lebar + ox + i] = (byte) (hitam ? gelap : terang);
            }
        }
        return y;
    }

    @Test
    public void qrJelasTerbaca() throws Exception {
        byte[] y = gambar(buatQr(ISI), 6, 640, 480, 0, 255);
        assertEquals(ISI, new QrDecoder().baca(y, 640, 480));
    }

    @Test
    public void qrKontrasRendahYangKurangTerangTerbaca() throws Exception {
        // Kamera di bawah naungan: modul gelap tidak hitam, latar tidak putih.
        byte[] y = gambar(buatQr(ISI), 6, 640, 480, 70, 150);
        assertEquals(ISI, new QrDecoder().baca(y, 640, 480));
    }

    @Test
    public void qrDenganNoiseTerbaca() throws Exception {
        byte[] y = gambar(buatQr(ISI), 6, 640, 480, 20, 235);
        Random rnd = new Random(7);
        for (int i = 0; i < y.length; i++) {
            int v = (y[i] & 0xFF) + rnd.nextInt(41) - 20;
            y[i] = (byte) Math.max(0, Math.min(255, v));
        }
        assertEquals(ISI, new QrDecoder().baca(y, 640, 480));
    }

    @Test
    public void qrTerbalikPutihDiAtasHitamTerbaca() throws Exception {
        byte[] y = gambar(buatQr(ISI), 6, 640, 480, 255, 0);
        assertEquals(ISI, new QrDecoder().baca(y, 640, 480));
    }

    @Test
    public void qrDiputarSembilanPuluhDerajatTerbaca() throws Exception {
        int lebar = 640;
        int tinggi = 480;
        byte[] y = gambar(buatQr(ISI), 6, lebar, tinggi, 0, 255);
        byte[] putar = new byte[y.length];
        for (int j = 0; j < tinggi; j++) {
            for (int i = 0; i < lebar; i++) {
                putar[i * tinggi + (tinggi - 1 - j)] = y[j * lebar + i]; // hasil: lebar 480, tinggi 640
            }
        }
        assertEquals(ISI, new QrDecoder().baca(putar, tinggi, lebar));
    }

    @Test
    public void gambarTanpaQrMengembalikanNull() {
        byte[] y = new byte[640 * 480];
        Random rnd = new Random(3);
        rnd.nextBytes(y);
        assertNull(new QrDecoder().baca(y, 640, 480));
        java.util.Arrays.fill(y, (byte) 128);
        assertNull(new QrDecoder().baca(y, 640, 480));
    }

    @Test
    public void hasilBacaDiuraikanOlehQrTph() throws Exception {
        byte[] y = gambar(buatQr(QrTph.buatKode("KP_STL", "A13", "02")), 6, 640, 480, 0, 255);
        String teks = new QrDecoder().baca(y, 640, 480);
        QrTph.Hasil h = QrTph.baca(teks);
        assertEquals("A13", h.tph.blok);
        assertEquals("02", h.tph.noTph);
    }
}
