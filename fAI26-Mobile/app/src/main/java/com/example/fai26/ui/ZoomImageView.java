package com.example.fai26.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/**
 * ImageView dengan dukungan cubit-zoom (pinch-to-zoom), geser (pan), dan ketuk-ganda (double-tap zoom),
 * sangat ideal untuk melihat foto panorama tumpukan TBS secara penuh dan detail.
 */
public class ZoomImageView extends AppCompatImageView {

    private final Matrix matrix = new Matrix();
    private final float[] matrixValues = new float[9];

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    private float minScale = 1.0f;
    private float maxScale = 5.0f;
    private float baseScale = 1.0f;

    private final PointF lastTouch = new PointF();
    private int activePointerId = MotionEvent.INVALID_POINTER_ID;

    private int viewWidth, viewHeight;
    private int bmWidth, bmHeight;

    public ZoomImageView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public ZoomImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ZoomImageView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setScaleType(ScaleType.MATRIX);

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                float currentScale = getScale();
                float targetScale = currentScale * factor;

                if (targetScale < minScale) {
                    factor = minScale / currentScale;
                } else if (targetScale > maxScale) {
                    factor = maxScale / currentScale;
                }

                matrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                cekBatasDanTerapkan();
                return true;
            }
        });

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                float currentScale = getScale();
                if (currentScale > baseScale * 1.5f) {
                    resetZoom();
                } else {
                    float target = Math.min(baseScale * 2.5f, maxScale);
                    float factor = target / currentScale;
                    matrix.postScale(factor, factor, e.getX(), e.getY());
                    cekBatasDanTerapkan();
                }
                return true;
            }
        });
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        super.setImageBitmap(bm);
        if (bm != null) {
            bmWidth = bm.getWidth();
            bmHeight = bm.getHeight();
            sesuaikanUkuranAwal();
        }
    }

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        super.setImageDrawable(drawable);
        if (drawable != null) {
            bmWidth = drawable.getIntrinsicWidth();
            bmHeight = drawable.getIntrinsicHeight();
            sesuaikanUkuranAwal();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
        viewHeight = h;
        sesuaikanUkuranAwal();
    }

    public void sesuaikanUkuranAwal() {
        if (viewWidth == 0 || viewHeight == 0 || bmWidth == 0 || bmHeight == 0) return;

        float scaleX = (float) viewWidth / bmWidth;
        float scaleY = (float) viewHeight / bmHeight;
        baseScale = Math.min(scaleX, scaleY);
        minScale = baseScale;
        maxScale = Math.max(baseScale * 5.0f, 4.0f);

        matrix.reset();
        matrix.postScale(baseScale, baseScale);

        float redX = (viewWidth - (bmWidth * baseScale)) / 2f;
        float redY = (viewHeight - (bmHeight * baseScale)) / 2f;
        matrix.postTranslate(redX, redY);

        setImageMatrix(matrix);
    }

    public void resetZoom() {
        sesuaikanUkuranAwal();
    }

    public float getScale() {
        matrix.getValues(matrixValues);
        return matrixValues[Matrix.MSCALE_X];
    }

    private void cekBatasDanTerapkan() {
        RectF rect = new RectF(0, 0, bmWidth, bmHeight);
        matrix.mapRect(rect);

        float width = rect.width();
        float height = rect.height();

        float deltaX = 0, deltaY = 0;

        if (width < viewWidth) {
            deltaX = (viewWidth - width) / 2f - rect.left;
        } else if (rect.left > 0) {
            deltaX = -rect.left;
        } else if (rect.right < viewWidth) {
            deltaX = viewWidth - rect.right;
        }

        if (height < viewHeight) {
            deltaY = (viewHeight - height) / 2f - rect.top;
        } else if (rect.top > 0) {
            deltaY = -rect.top;
        } else if (rect.bottom < viewHeight) {
            deltaY = viewHeight - rect.bottom;
        }

        matrix.postTranslate(deltaX, deltaY);
        setImageMatrix(matrix);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);

        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                lastTouch.set(event.getX(), event.getY());
                activePointerId = event.getPointerId(0);
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                int pointerIndex = event.findPointerIndex(activePointerId);
                if (pointerIndex != -1) {
                    float x = event.getX(pointerIndex);
                    float y = event.getY(pointerIndex);

                    if (!scaleDetector.isInProgress()) {
                        float dx = x - lastTouch.x;
                        float dy = y - lastTouch.y;
                        matrix.postTranslate(dx, dy);
                        cekBatasDanTerapkan();
                    }

                    lastTouch.set(x, y);
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                activePointerId = MotionEvent.INVALID_POINTER_ID;
                break;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                int pointerIndex = event.getActionIndex();
                int pointerId = event.getPointerId(pointerIndex);
                if (pointerId == activePointerId) {
                    int newPointerIndex = pointerIndex == 0 ? 1 : 0;
                    lastTouch.set(event.getX(newPointerIndex), event.getY(newPointerIndex));
                    activePointerId = event.getPointerId(newPointerIndex);
                }
                break;
            }
        }

        return true;
    }
}

