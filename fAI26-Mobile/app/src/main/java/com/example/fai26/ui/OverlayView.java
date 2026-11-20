package com.example.fai26.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.example.fai26.KelasModel;
import com.example.fai26.detect.Detection;
import com.example.fai26.tracking.BunchTracker;

import java.util.Collections;
import java.util.List;

/**
 * Menggambar kotak deteksi di atas PreviewView (mode fillCenter).
 * Koordinat deteksi ternormalisasi terhadap frame tegak, jadi perlu dipetakan
 * dengan memperhitungkan bagian frame yang terpotong.
 */
public class OverlayView extends View {

    private final Paint kotak = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint latar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint teks = new Paint(Paint.ANTI_ALIAS_FLAG);

    private volatile List<Detection> deteksi = Collections.emptyList();
    private volatile List<BunchTracker.Info> track = Collections.emptyList();
    private int frameW = 9;
    private int frameH = 16;

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float d = getResources().getDisplayMetrics().density;
        kotak.setStyle(Paint.Style.STROKE);
        kotak.setStrokeWidth(2.5f * d);
        latar.setStyle(Paint.Style.FILL);
        teks.setColor(0xFF000000);
        teks.setTextSize(12f * d);
    }

    public void set(List<Detection> deteksi, int frameW, int frameH) {
        set(deteksi, Collections.<BunchTracker.Info>emptyList(), frameW, frameH);
    }

    /** @param track track yang terlihat; digambar sebagai label ID (hanya saat memindai). */
    public void set(List<Detection> deteksi, List<BunchTracker.Info> track, int frameW, int frameH) {
        this.deteksi = deteksi;
        this.track = track;
        this.frameW = frameW;
        this.frameH = frameH;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int vw = getWidth();
        int vh = getHeight();
        if (vw == 0 || vh == 0) return;

        // fillCenter: skala terbesar, bagian yang lebih disembunyikan.
        float scale = Math.max((float) vw / frameW, (float) vh / frameH);
        float offX = (vw - frameW * scale) / 2f;
        float offY = (vh - frameH * scale) / 2f;
        float d = getResources().getDisplayMetrics().density;

        for (Detection det : deteksi) {
            int warna = KelasModel.WARNA[det.classIndex];
            float l = det.x1 * frameW * scale + offX;
            float t = det.y1 * frameH * scale + offY;
            float r = det.x2 * frameW * scale + offX;
            float b = det.y2 * frameH * scale + offY;
            kotak.setColor(warna);
            canvas.drawRect(l, t, r, b, kotak);

            String label = KelasModel.TAMPIL[det.classIndex] + " " + Math.round(det.confidence * 100) + "%";
            float tw = teks.measureText(label);
            float th = teks.getTextSize();
            latar.setColor(warna);
            canvas.drawRect(l, t - th - 4 * d, l + tw + 8 * d, t, latar);
            canvas.drawText(label, l + 4 * d, t - 4 * d, teks);
        }

        // Label ID tracking: hijau bertanda centang bila sudah dihitung, abu-abu bila belum.
        // Bila ID satu tandan berganti-ganti atau satu tandan punya dua ID, tracking sedang gagal.
        for (BunchTracker.Info tr : track) {
            float l = tr.x1 * frameW * scale + offX;
            float b = tr.y2 * frameH * scale + offY;
            String label = (tr.counted ? "✓ #" : "#") + tr.id;
            float tw = teks.measureText(label);
            float th = teks.getTextSize();
            latar.setColor(tr.counted ? 0xFF2E7D32 : 0xFF757575);
            canvas.drawRect(l, b - th - 4 * d, l + tw + 8 * d, b, latar);
            teks.setColor(0xFFFFFFFF);
            canvas.drawText(label, l + 4 * d, b - 4 * d, teks);
            teks.setColor(0xFF000000);
        }
    }
}
