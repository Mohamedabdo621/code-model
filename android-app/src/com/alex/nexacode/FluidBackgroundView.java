package com.alex.nexacode;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.View;

/**
 * خلفية سائلة متحركة — كتل ضوئية نيون بتتحرك بحركة موجية ناعمة شبه المية،
 * على قاعدة كحلية غامقة. ده اللي بيدي إحساس "سنة 2100".
 */
public class FluidBackgroundView extends View {

    private static class Blob {
        final float baseX, baseY, radius, ampX, ampY, speedX, speedY, phase;
        final int color;

        Blob(float bx, float by, float r, float ax, float ay,
             float sx, float sy, float ph, int color) {
            baseX = bx; baseY = by; radius = r; ampX = ax; ampY = ay;
            speedX = sx; speedY = sy; phase = ph; this.color = color;
        }
    }

    private final Blob[] blobs = new Blob[]{
            new Blob(0.18f, 0.12f, 0.55f, 0.10f, 0.07f, 0.11f, 0.07f, 0.0f, 0x8C00E5FF),
            new Blob(0.85f, 0.25f, 0.50f, 0.08f, 0.11f, 0.09f, 0.13f, 1.7f, 0x8C8B5CFF),
            new Blob(0.55f, 0.75f, 0.60f, 0.12f, 0.08f, 0.07f, 0.10f, 3.4f, 0x73FF3EA5),
            new Blob(0.15f, 0.90f, 0.45f, 0.09f, 0.09f, 0.13f, 0.08f, 5.1f, 0x6600FFC8),
            new Blob(0.90f, 0.92f, 0.40f, 0.07f, 0.10f, 0.10f, 0.12f, 2.6f, 0x5C2979FF),
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean running = false;

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (!running) return;
            invalidate();
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    public FluidBackgroundView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(5, 5, 20));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        float t = SystemClock.uptimeMillis() / 1000f;
        float maxDim = Math.max(w, h);
        for (Blob b : blobs) {
            float cx = (b.baseX + (float) Math.sin(t * b.speedX + b.phase) * b.ampX) * w;
            float cy = (b.baseY + (float) Math.cos(t * b.speedY + b.phase * 1.3f) * b.ampY) * h;
            float r = b.radius * maxDim * (1f + 0.08f * (float) Math.sin(t * 0.15f + b.phase));
            int rgb = b.color & 0x00FFFFFF;
            RadialGradient grad = new RadialGradient(
                    cx, cy, r,
                    new int[]{b.color, rgb | 0x38000000, Color.TRANSPARENT},
                    new float[]{0f, 0.55f, 1f},
                    Shader.TileMode.CLAMP);
            paint.setShader(grad);
            canvas.drawCircle(cx, cy, r, paint);
        }
        paint.setShader(null);
    }
}
