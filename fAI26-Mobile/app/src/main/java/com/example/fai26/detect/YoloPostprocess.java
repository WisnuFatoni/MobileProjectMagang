package com.example.fai26.detect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pasca-proses keluaran mentah YOLOv8 (tanpa NMS di dalam model).
 * Murni Java supaya bisa dites unit tanpa Android.
 */
public final class YoloPostprocess {

    private YoloPostprocess() {
    }

    /**
     * Mengubah keluaran mentah menjadi deteksi pada koordinat frame (0..1).
     *
     * @param out          keluaran model dalam satu larik datar
     * @param channels     jumlah kanal per kandidat (4 kotak + jumlah kelas), contoh 10
     * @param anchors      jumlah kandidat, contoh 8400
     * @param channelsFirst true bila bentuk [1, channels, anchors], false bila [1, anchors, channels]
     * @param inputSize    sisi masukan model, contoh 640
     * @param frameW       lebar frame tegak sebelum letterbox
     * @param frameH       tinggi frame tegak sebelum letterbox
     * @param confThreshold batas confidence minimal
     * @param iouThreshold  batas IoU untuk NMS (agnostik kelas)
     * @param maxDet       jumlah deteksi maksimal
     */
    public static List<Detection> decode(float[] out, int channels, int anchors,
                                         boolean channelsFirst, int inputSize,
                                         int frameW, int frameH,
                                         float confThreshold, float iouThreshold, int maxDet) {
        int numClasses = channels - 4;
        float scale = Math.min((float) inputSize / frameW, (float) inputSize / frameH);
        float newW = frameW * scale;
        float newH = frameH * scale;
        float padX = (inputSize - newW) / 2f;
        float padY = (inputSize - newH) / 2f;

        // Kotak bisa berupa piksel (0..640) atau ternormalisasi (0..1), tergantung ekspor.
        float maxCx = 0f;
        for (int i = 0; i < anchors; i++) {
            maxCx = Math.max(maxCx, at(out, 0, i, channels, anchors, channelsFirst));
        }
        float toPixel = maxCx > 1.5f ? 1f : inputSize;

        List<Detection> cands = new ArrayList<>();
        for (int i = 0; i < anchors; i++) {
            int best = 0;
            float bestScore = at(out, 4, i, channels, anchors, channelsFirst);
            for (int c = 1; c < numClasses; c++) {
                float s = at(out, 4 + c, i, channels, anchors, channelsFirst);
                if (s > bestScore) {
                    bestScore = s;
                    best = c;
                }
            }
            if (bestScore < confThreshold) continue;

            float cx = at(out, 0, i, channels, anchors, channelsFirst) * toPixel;
            float cy = at(out, 1, i, channels, anchors, channelsFirst) * toPixel;
            float w = at(out, 2, i, channels, anchors, channelsFirst) * toPixel;
            float h = at(out, 3, i, channels, anchors, channelsFirst) * toPixel;

            // Dari piksel masukan model (ber-letterbox) ke koordinat frame ternormalisasi.
            float x1 = clamp01((cx - w / 2f - padX) / newW);
            float y1 = clamp01((cy - h / 2f - padY) / newH);
            float x2 = clamp01((cx + w / 2f - padX) / newW);
            float y2 = clamp01((cy + h / 2f - padY) / newH);
            if (x2 - x1 <= 0f || y2 - y1 <= 0f) continue;
            cands.add(new Detection(x1, y1, x2, y2, bestScore, best));
        }
        return nms(cands, iouThreshold, maxDet);
    }

    /** NMS agnostik kelas: kotak yang saling tumpang tindih berat dianggap objek yang sama. */
    public static List<Detection> nms(List<Detection> dets, float iouThreshold, int maxDet) {
        List<Detection> sorted = new ArrayList<>(dets);
        Collections.sort(sorted, (a, b) -> Float.compare(b.confidence, a.confidence));
        List<Detection> keep = new ArrayList<>();
        for (Detection d : sorted) {
            boolean suppressed = false;
            for (Detection k : keep) {
                if (Detection.iou(d.x1, d.y1, d.x2, d.y2, k.x1, k.y1, k.x2, k.y2) > iouThreshold) {
                    suppressed = true;
                    break;
                }
            }
            if (!suppressed) {
                keep.add(d);
                if (keep.size() >= maxDet) break;
            }
        }
        return keep;
    }

    private static float at(float[] out, int channel, int anchor, int channels, int anchors,
                            boolean channelsFirst) {
        return channelsFirst ? out[channel * anchors + anchor] : out[anchor * channels + channel];
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
