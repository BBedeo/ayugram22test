package app.exteraless.appearance.glass;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.RenderNode;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.blur3.LiquidGlassEffect;
import org.telegram.ui.Components.blur3.LiquidGlassOptics;

import app.exteraless.appearance.LiquidGlassTuning;

/**
 * The thumb of every iOS-style slider, one instance per view: a white capsule that becomes a drop of glass
 * while held, on springs. Each frame call {@link #clipTrack} around the track drawing (it also moves the springs),
 * then {@link #draw}.
 */
public final class IosSliderThumb {
    /** Draws the slider's track in view coordinates. */
    public interface Track {
        void draw(Canvas canvas);
    }

    private final View host;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final GlassRim rim = new GlassRim();
    private RenderNode node;
    private LiquidGlassEffect lens;

    private long lastTime;
    private float lastCx;
    private float press, pressSpeed, stretch, stretchSpeed;
    private float radius;

    public IosSliderThumb(View host) {
        this.host = host;
    }

    /** Cuts the thumb out of the track where the lens is going to redraw it. The caller saves and restores. */
    public void clipTrack(Canvas canvas, float cx, float cy, float targetPress, int width) {
        advance(cx, targetPress);
        // the version check is repeated here so lint sees it guard clipOutPath
        if (Build.VERSION.SDK_INT >= 33 && refracts(canvas)) {
            layout(cx, cy, width);
            path.rewind();
            path.addRoundRect(rect, radius, radius, Path.Direction.CW);
            canvas.clipOutPath(path);
        }
    }

    /** @param track the same drawing the view puts on screen, so the lens refracts exactly what is under it */
    public void draw(Canvas canvas, float cx, float cy, int width, Track track) {
        layout(cx, cy, width);
        final boolean glass = GlassTokens.isActive();
        final boolean refracts = refracts(canvas);
        final float p = Math.max(0f, Math.min(1f, press));

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x28000000);
        if (glass) {
            paint.setAlpha(Math.round(0x28 * (1f - p)));
        }
        rect.offset(0, dpf2(1.4f));
        canvas.drawRoundRect(rect, radius, radius, paint);
        rect.offset(0, -dpf2(1.4f));

        if (refracts) {
            drawLens(canvas, p, track);
        }

        paint.setColor(0xFFFFFFFF);
        if (glass) {
            // without the lens the thumb has to stay visible over the track
            paint.setAlpha(Math.round(AndroidUtilities.lerp(255f, refracts ? 0x29 : 0x99, p)));
        }
        canvas.drawRoundRect(rect, radius, radius, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dpf2(0.75f));
        paint.setColor(0x1E000000);
        canvas.drawRoundRect(rect, radius, radius, paint);

        if (glass) {
            rim.draw(canvas, rect, radius, radius, p);
        }
    }

    /** The thumb grows out of the track by {@code pop}; at zero only the track is drawn. */
    public void drawPopping(Canvas canvas, float cx, float cy, int width, float pop, Track track) {
        final boolean show = pop > 0.01f;
        canvas.save();
        if (show) {
            // the clip has to match the thumb, which grows by scaling
            canvas.scale(pop, pop, cx, cy);
            clipTrack(canvas, cx, cy, 1f, width);
            canvas.scale(1f / pop, 1f / pop, cx, cy);
        }
        track.draw(canvas);
        canvas.restore();
        if (show) {
            canvas.save();
            canvas.scale(pop, pop, cx, cy);
            draw(canvas, cx, cy, width, c -> {
                // the thumb is drawn scaled; its lens has to see the track at the real size
                c.save();
                c.scale(1f / pop, 1f / pop, cx, cy);
                track.draw(c);
                c.restore();
            });
            canvas.restore();
        }
    }

    private boolean refracts(Canvas canvas) {
        return Build.VERSION.SDK_INT >= 33 && press > 0.01f && canvas.isHardwareAccelerated() && GlassTokens.isActive();
    }

    private void layout(float cx, float cy, int width) {
        final float w = dpf2(36f + 6f * press) + 2f * stretch;
        final float h = dpf2(22f + 4f * press) - 0.35f * stretch;
        rect.set(Math.max(0, cx - w / 2f), cy - h / 2f, Math.min(width, cx + w / 2f), cy + h / 2f);
        radius = Math.min(rect.width(), rect.height()) / 2f;
    }

    /** One spring pops the thumb after the finger's press, the other stretches it with the finger's speed. */
    private void advance(float cx, float targetPress) {
        final long now = SystemClock.uptimeMillis();
        final long gap = now - lastTime;
        if (lastTime == 0 || Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()) {
            lastTime = now;
            lastCx = cx;
            press = targetPress;
            pressSpeed = stretch = stretchSpeed = 0;
            return;
        }
        if (gap <= 0) {
            return;
        }
        // after a pause neither the distance moved nor a leftover stretch means anything
        final boolean paused = gap > 100;
        if (paused) {
            stretch = stretchSpeed = 0;
        }
        final float dt = paused ? 0.016f : gap / 1000f;
        final float speed = paused ? 0f : Math.abs(cx - lastCx) / dt;
        lastTime = now;
        lastCx = cx;
        final float stretchTarget = targetPress > 0 ? Math.min(speed * 0.01f, dp(8)) : 0f;

        final int steps = (int) Math.ceil(dt * 60f);
        final float step = dt / steps;
        for (int i = 0; i < steps; i++) {
            // stiffness 500, damping ratio 0.55
            pressSpeed += (500f * (targetPress - press) - 24.6f * pressSpeed) * step;
            press += pressSpeed * step;
            // stiffness 350, damping ratio 0.4
            stretchSpeed += (350f * (stretchTarget - stretch) - 15f * stretchSpeed) * step;
            stretch += stretchSpeed * step;
        }

        if (stretchTarget == 0 && Math.abs(targetPress - press) < 0.002f && Math.abs(pressSpeed) < 0.02f
                && Math.abs(stretch) < 0.25f && Math.abs(stretchSpeed) < 2f) {
            press = targetPress;
            pressSpeed = stretch = stretchSpeed = 0;
        } else {
            host.postInvalidateOnAnimation();
        }
    }

    private void drawLens(Canvas canvas, float p, Track track) {
        if (Build.VERSION.SDK_INT < 33) {
            return;
        }
        final float w = rect.width();
        final float h = rect.height();
        if (node == null) {
            node = new RenderNode("IosSliderThumb");
            lens = new LiquidGlassEffect(node, false);
        }
        node.setPosition(0, 0, (int) Math.ceil(w), (int) Math.ceil(h));
        final Canvas c = node.beginRecording();
        c.translate(-rect.left, -rect.top);
        track.draw(c);
        node.endRecording();

        // the plain lens bends only its bevel and leaves empty pixels empty
        final boolean engine = LiquidGlassEffect.engineActive();
        lens.update(w, h, 0, 0, radius, radius, radius, radius,
                engine ? LiquidGlassOptics.sliderBevel(h, AndroidUtilities.density, LiquidGlassTuning.thicknessDp())
                        : Math.min(dp(LiquidGlassTuning.thicknessDp()), h / 3f),
                engine ? LiquidGlassOptics.sliderBend(LiquidGlassTuning.refractionScale(), p)
                        : Math.min(0.09f * LiquidGlassTuning.refractionScale(), 0.1f) * p, 1.5f, 0,
                0, 0, 0, 0, 0,
                0, 0, 1);

        canvas.save();
        path.rewind();
        path.addRoundRect(rect, radius, radius, Path.Direction.CW);
        canvas.clipPath(path);
        canvas.translate(rect.left, rect.top);
        canvas.drawRenderNode(node);
        canvas.restore();
    }
}
