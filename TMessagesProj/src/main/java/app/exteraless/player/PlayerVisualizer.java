package app.exteraless.player;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.LiteMode;
import org.telegram.ui.Components.VideoPlayer;

/**
 * Soft frequency bars under the collapsed control capsule. Telegram gives no spectrum for music files, so the bands are a
 * smooth synthetic motion that is heavier in the bass (left) and slowed down so it never flickers; it eases out when paused.
 */
final class PlayerVisualizer extends View {

    private static final int BARS = 28;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float[] level = new float[BARS];
    private boolean playing;
    private float energy;
    private float bass;
    private long last;
    private float time;
    private final float[] real = new float[BARS];
    private long lastReal;

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        VideoPlayer.spectrumListener = bands -> {
            System.arraycopy(bands, 0, real, 0, Math.min(bands.length, BARS));
            lastReal = SystemClock.elapsedRealtime();
        };
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        VideoPlayer.spectrumListener = null;
    }

    PlayerVisualizer(Context context) {
        super(context);
    }

    private Runnable frameListener;

    void setFrameListener(Runnable value) {
        frameListener = value;
    }

    void setColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    void setPlaying(boolean value) {
        if (playing != value) {
            playing = value;
            last = 0;
            invalidate();
        }
    }

    /** 0..1 smoothed low-band level, used to make the cover breathe. */
    float getBass() {
        return bass;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        final long now = SystemClock.elapsedRealtime();
        final float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1000f);
        last = now;
        final boolean live = playing && !LiteMode.isPowerSaverApplied();
        energy += ((live ? 1f : 0f) - energy) * (1f - (float) Math.pow(0.9, dt / 0.04f));
        time += dt * 1.6f;
        final int w = getWidth();
        final int h = getHeight();
        final float step = w / (float) BARS;
        final float bw = step * 0.55f;
        float bassSum = 0;
        for (int i = 0; i < BARS; i++) {
            final float f = i / (float) (BARS - 1);
            final float tilt = 1f - 0.6f * f;
            final float beat = 0.5f + 0.5f * (float) Math.sin(time * 2.1f - f * 1.5f);
            final float wob = 0.5f + 0.5f * (float) Math.sin(time * (1.1f + f * 1.7f) + i * 1.9f);
            final float wob2 = 0.5f + 0.5f * (float) Math.sin(time * 0.7f + i * 0.6f);
            final boolean fresh = now - lastReal < 300;
            // shortcut: synthetic motion only when the decoder gives no samples (e.g. unsupported encoding)
            final float target = fresh ? energy * real[i] : energy * tilt * (0.18f + 0.82f * (0.45f * beat * (1f - f) + 0.35f * wob + 0.2f * wob2));
            final float k = target > level[i] ? 0.55f : 0.85f;
            level[i] += (target - level[i]) * (1f - (float) Math.pow(k, dt / 0.04f));
            if (i < 5) {
                bassSum += level[i];
            }
            final float bh = Math.max(dp(3), level[i] * h);
            final float cx = step * (i + 0.5f);
            rect.set(cx - bw / 2f, h - bh, cx + bw / 2f, h);
            canvas.drawRoundRect(rect, bw / 2f, bw / 2f, paint);
        }
        bass = Math.min(1f, bassSum / 5f);
        if (frameListener != null) {
            frameListener.run();
        }
        if (live || energy > 0.01f) {
            postInvalidateOnAnimation();
        }
    }
}
