package com.example.fai26.tracking;

import com.example.fai26.detect.Detection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pelacak sederhana bergaya ByteTrack untuk menghitung janjang unik saat krani
 * menyisir tumpukan dengan kamera.
 *
 * <ul>
 *   <li>Asosiasi dua tahap: deteksi confidence tinggi dulu, lalu yang rendah
 *       untuk track yang belum cocok.</li>
 *   <li>Gerakan kamera diperkirakan dari median pergeseran track yang cocok,
 *       lalu dipakai memprediksi posisi track di frame berikutnya.</li>
 *   <li>Satu janjang dihitung sekali, setelah terlihat cukup frame dan ukurannya
 *       cukup. Kelasnya ditentukan voting berbobot confidence.</li>
 * </ul>
 *
 * Tidak thread-safe: panggil dari satu thread, atau sinkronkan dari luar.
 */
public class BunchTracker {

    public final int numClasses;
    public final float highConf;
    public final float minIouHigh;
    public final float minIouLow;
    public final int minHits;
    /** Berapa lama (ms) track boleh tidak terlihat sebelum dibuang. Berbasis waktu agar tidak bergantung fps. */
    public final long maxLostMs;
    public final float minArea;
    /** Dua track aktif dengan IoU di atas ini dianggap tandan yang sama (kotak ganda). */
    public final float mergeIou;

    private final List<Track> active = new ArrayList<>();
    /** Track yang sudah dihitung lalu hilang dari layar. Tetap dihitung. */
    private final List<Track> finished = new ArrayList<>();
    private int nextId = 1;
    private float motionX = 0f;
    private float motionY = 0f;

    public BunchTracker(int numClasses) {
        this(numClasses, 0.30f, 0.25f, 0.45f, 4, 3500L, 0.015f, 0.60f);
    }

    public BunchTracker(int numClasses, float highConf, float minIouHigh, float minIouLow,
                        int minHits, long maxLostMs, float minArea, float mergeIou) {
        this.numClasses = numClasses;
        this.highConf = highConf;
        this.minIouHigh = minIouHigh;
        this.minIouLow = minIouLow;
        this.minHits = minHits;
        this.maxLostMs = maxLostMs;
        this.minArea = minArea;
        this.mergeIou = mergeIou;
    }

    public void reset() {
        active.clear();
        finished.clear();
        nextId = 1;
        motionX = 0f;
        motionY = 0f;
    }

    /** Hitungan janjang unik per indeks kelas. */
    public int[] counts() {
        int[] c = new int[numClasses];
        for (Track t : active) if (t.counted) c[t.classIndex()]++;
        for (Track t : finished) if (t.counted) c[t.classIndex()]++;
        return c;
    }

    public int total() {
        int sum = 0;
        for (int v : counts()) sum += v;
        return sum;
    }

    public void update(List<Detection> detections) {
        update(detections, System.currentTimeMillis());
    }

    /** Info satu track yang sedang terlihat, untuk digambar di layar. */
    public static final class Info {
        public final int id;
        public final float x1, y1, x2, y2;
        public final boolean counted;
        public final int classIndex;
        public final int hits;

        Info(Track t) {
            this.id = t.id;
            this.x1 = t.x1;
            this.y1 = t.y1;
            this.x2 = t.x2;
            this.y2 = t.y2;
            this.counted = t.counted;
            this.classIndex = t.classIndex();
            this.hits = t.hits;
        }
    }

    /** Track yang terlihat pada frame terakhir. */
    public List<Info> terlihat() {
        List<Info> hasil = new ArrayList<>();
        for (Track t : active) if (t.misses == 0) hasil.add(new Info(t));
        return hasil;
    }

    /** @param nowMs waktu frame (milidetik), dipakai untuk membuang track yang lama tak terlihat. */
    public void update(List<Detection> detections, long nowMs) {
        // Track yang terlalu lama tak terlihat dibuang lebih dulu, supaya tidak ikut dicocokkan
        // dengan deteksi baru yang kebetulan berada di dekatnya.
        List<Track> basi = new ArrayList<>();
        for (Track t : active) if (nowMs - t.lastSeenMs > maxLostMs) basi.add(t);
        for (Track t : basi) {
            active.remove(t);
            if (t.counted) finished.add(t);
        }

        // Posisi prediksi: bergeser mengikuti perkiraan gerak kamera.
        List<float[]> predicted = new ArrayList<>(active.size());
        for (Track t : active) {
            predicted.add(new float[]{t.x1 + motionX, t.y1 + motionY, t.x2 + motionX, t.y2 + motionY});
        }

        List<Detection> high = new ArrayList<>();
        List<Detection> low = new ArrayList<>();
        for (Detection d : detections) {
            if (d.confidence >= highConf) high.add(d);
            else low.add(d);
        }

        Set<Integer> matchedTracks = new HashSet<>(); // indeks di `active`
        Set<Detection> matchedDets = new HashSet<>();
        List<float[]> shifts = new ArrayList<>();

        // Tahap 1: confidence tinggi terhadap semua track.
        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < active.size(); i++) all.add(i);
        apply(match(all, predicted, high, minIouHigh), matchedTracks, matchedDets, shifts, nowMs);

        // Tahap 2: confidence rendah terhadap track yang belum cocok.
        List<Integer> rest = new ArrayList<>();
        for (int i = 0; i < active.size(); i++) if (!matchedTracks.contains(i)) rest.add(i);
        apply(match(rest, predicted, low, minIouLow), matchedTracks, matchedDets, shifts, nowMs);

        // Perbarui perkiraan gerakan kamera dari median pergeseran.
        if (!shifts.isEmpty()) {
            float[] dx = new float[shifts.size()];
            float[] dy = new float[shifts.size()];
            for (int i = 0; i < shifts.size(); i++) {
                dx[i] = shifts.get(i)[0];
                dy[i] = shifts.get(i)[1];
            }
            motionX = motionX * 0.6f + median(dx) * 0.4f;
            motionY = motionY * 0.6f + median(dy) * 0.4f;
        } else {
            motionX *= 0.9f;
            motionY *= 0.9f;
        }

        // Track yang tidak cocok ikut bergeser mengikuti kamera, lalu dibuang bila hilang terlalu lama.
        List<Track> removed = new ArrayList<>();
        for (int i = 0; i < active.size(); i++) {
            if (matchedTracks.contains(i)) continue;
            Track t = active.get(i);
            float[] p = predicted.get(i);
            t.x1 = p[0];
            t.y1 = p[1];
            t.x2 = p[2];
            t.y2 = p[3];
            t.misses++;
            if (nowMs - t.lastSeenMs > maxLostMs) removed.add(t);
        }
        for (Track t : removed) {
            active.remove(t);
            if (t.counted) finished.add(t);
        }

        // Deteksi confidence tinggi yang tidak cocok jadi track baru.
        for (Detection d : high) {
            if (!matchedDets.contains(d)) active.add(new Track(nextId++, d, numClasses, nowMs));
        }

        // Kotak ganda pada tandan yang sama: yang lebih muda dibuang (dan tidak ikut terhitung).
        List<Track> ganda = new ArrayList<>();
        for (int i = 0; i < active.size(); i++) {
            Track a = active.get(i);
            if (a.misses != 0 || ganda.contains(a)) continue;
            for (int j = i + 1; j < active.size(); j++) {
                Track b = active.get(j);
                if (b.misses != 0 || ganda.contains(b)) continue;
                if (Detection.iou(a.x1, a.y1, a.x2, a.y2, b.x1, b.y1, b.x2, b.y2) > mergeIou) {
                    ganda.add(a.hits >= b.hits ? b : a);
                    if (ganda.contains(a)) break;
                }
            }
        }
        active.removeAll(ganda);

        // Gerbang kualitas: cukup frame dan ukuran cukup.
        for (Track t : active) {
            if (!t.counted && t.hits >= minHits && t.maxArea >= minArea) t.counted = true;
        }
    }

    private void apply(List<Pair> pairs, Set<Integer> matchedTracks, Set<Detection> matchedDets,
                       List<float[]> shifts, long nowMs) {
        for (Pair p : pairs) {
            Track t = active.get(p.trackIndex);
            Detection d = p.det;
            shifts.add(new float[]{(d.x1 + d.x2) / 2f - t.cx(), (d.y1 + d.y2) / 2f - t.cy()});
            t.x1 = d.x1;
            t.y1 = d.y1;
            t.x2 = d.x2;
            t.y2 = d.y2;
            t.hits++;
            t.misses = 0;
            t.lastSeenMs = nowMs;
            t.maxArea = Math.max(t.maxArea, d.area());
            t.classScores[d.classIndex] += d.confidence;
            matchedTracks.add(p.trackIndex);
            matchedDets.add(d);
        }
    }

    private static final class Pair {
        final int trackIndex;
        final Detection det;
        final float iou;

        Pair(int trackIndex, Detection det, float iou) {
            this.trackIndex = trackIndex;
            this.det = det;
            this.iou = iou;
        }
    }

    /** Pencocokan serakah berdasarkan IoU terbesar. */
    private List<Pair> match(List<Integer> trackIndexes, List<float[]> predicted,
                             List<Detection> dets, float minIou) {
        List<Pair> cands = new ArrayList<>();
        for (int ti : trackIndexes) {
            float[] p = predicted.get(ti);
            for (Detection d : dets) {
                float iou = Detection.iou(p[0], p[1], p[2], p[3], d.x1, d.y1, d.x2, d.y2);
                if (iou >= minIou) cands.add(new Pair(ti, d, iou));
            }
        }
        Collections.sort(cands, (a, b) -> Float.compare(b.iou, a.iou));
        Set<Integer> usedT = new HashSet<>();
        Set<Detection> usedD = new HashSet<>();
        List<Pair> result = new ArrayList<>();
        for (Pair c : cands) {
            if (usedT.contains(c.trackIndex) || usedD.contains(c.det)) continue;
            usedT.add(c.trackIndex);
            usedD.add(c.det);
            result.add(c);
        }
        return result;
    }

    private static float median(float[] v) {
        Arrays.sort(v);
        int m = v.length / 2;
        return v.length % 2 == 1 ? v[m] : (v[m - 1] + v[m]) / 2f;
    }
}
