package com.example.fai26.ui;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.example.fai26.R;

/**
 * Bayangan lembut untuk kapsul kaca. Digambar terpisah di belakang kapsul (bukan memakai elevasi),
 * karena kapsul tembus pandang sehingga bayangan elevasi akan tampak di dalamnya.
 * Isi kapsul menutupi bagian tengah bayangan, jadi hanya tepinya yang terlihat.
 */
public class GlassShadowView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF kotak = new RectF();
    private final float dp;

    public GlassShadowView(Context context, AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
        setLayerType(LAYER_TYPE_SOFTWARE, null); // BlurMaskFilter butuh lapisan perangkat lunak
        paint.setColor(context.getColor(R.color.glass_shadow));
        paint.setMaskFilter(new BlurMaskFilter(14 * dp, BlurMaskFilter.Blur.NORMAL));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Kapsul berada di tengah, dengan ruang di sekelilingnya untuk lebar kabur. Bayangan turun sedikit.
        float margin = 18 * dp;
        kotak.set(margin, margin + 5 * dp, getWidth() - margin, getHeight() - margin + 5 * dp);
        canvas.drawRoundRect(kotak, kotak.height() / 2f, kotak.height() / 2f, paint);
    }
}
