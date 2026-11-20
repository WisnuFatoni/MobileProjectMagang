package com.example.fai26.tracking;

import com.example.fai26.detect.Detection;

/** Satu janjang yang sedang dilacak. */
public final class Track {

    public final int id;
    float x1, y1, x2, y2;
    int hits = 1;
    int misses = 0;
    long lastSeenMs;
    float maxArea;
    boolean counted = false;

    /** Jumlah confidence per kelas, dipakai untuk voting kelas akhir. */
    final float[] classScores;

    Track(int id, Detection d, int numClasses, long nowMs) {
        this.id = id;
        this.lastSeenMs = nowMs;
        this.x1 = d.x1;
        this.y1 = d.y1;
        this.x2 = d.x2;
        this.y2 = d.y2;
        this.maxArea = d.area();
        this.classScores = new float[numClasses];
        this.classScores[d.classIndex] += d.confidence;
    }

    float cx() {
        return (x1 + x2) / 2f;
    }

    float cy() {
        return (y1 + y2) / 2f;
    }

    public boolean isCounted() {
        return counted;
    }

    public int classIndex() {
        int best = 0;
        for (int i = 1; i < classScores.length; i++) {
            if (classScores[i] > classScores[best]) best = i;
        }
        return best;
    }
}
