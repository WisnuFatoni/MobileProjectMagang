package com.example.fai26.detect;

/** Satu deteksi pada satu frame. Koordinat ternormalisasi 0..1 terhadap frame tegak. */
public final class Detection {

    public final float x1;
    public final float y1;
    public final float x2;
    public final float y2;
    public final float confidence;
    public final int classIndex;

    public Detection(float x1, float y1, float x2, float y2, float confidence, int classIndex) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.confidence = confidence;
        this.classIndex = classIndex;
    }

    public float width() {
        return x2 - x1;
    }

    public float height() {
        return y2 - y1;
    }

    public float area() {
        return Math.max(0f, width()) * Math.max(0f, height());
    }

    public static float iou(float ax1, float ay1, float ax2, float ay2,
                            float bx1, float by1, float bx2, float by2) {
        float iw = Math.min(ax2, bx2) - Math.max(ax1, bx1);
        float ih = Math.min(ay2, by2) - Math.max(ay1, by1);
        if (iw <= 0 || ih <= 0) return 0f;
        float inter = iw * ih;
        float union = (ax2 - ax1) * (ay2 - ay1) + (bx2 - bx1) * (by2 - by1) - inter;
        return union <= 0 ? 0f : inter / union;
    }
}
