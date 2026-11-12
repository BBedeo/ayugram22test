package app.exteraless.appearance;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.os.Build;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.OvershootInterpolator;

import org.telegram.messenger.AndroidUtilities;

/**
 * Visual-only, bounded rubber-band offset for a single touch surface.
 * The hit rectangle never moves and no click/parent gesture is synthesized.
 * A drag is distinguishable from a normal tap, so destructive actions (hang up)
 * can explicitly suppress activation after the user's finger travels.
 */
public final class ElasticPillGesture {
    private final View view;
    private final float maxOffset;
    private final int slop;
    private ValueAnimator release;
    private float startX, startY, dx, dy;
    private boolean tracking, dragged;

    public ElasticPillGesture(View view) {
        this.view = view;
        this.maxOffset = AndroidUtilities.dp(17);
        this.slop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
    }

    public static boolean enabled() {
        return AppearanceConfig.elasticDragPills.Bool();
    }

    public void down(float x, float y) {
        if (release != null) {
            release.cancel();
            release = null;
        }
        startX = x;
        startY = y;
        tracking = true;
        dragged = false;
    }

    /** Returns true once touch has crossed the system slop. */
    public boolean move(float x, float y) {
        if (!tracking) return false;
        final float xTravel = x - startX;
        final float yTravel = y - startY;
        if (Math.hypot(xTravel, yTravel) > slop) dragged = true;
        // Hyperbolic resistance feels elastic but never leaves its hit target.
        dx = maxOffset * (float) Math.tanh(xTravel / (maxOffset * 2f));
        dy = maxOffset * (float) Math.tanh(yTravel / (maxOffset * 2f));
        view.invalidate();
        return dragged;
    }

    public boolean up() {
        final boolean wasDrag = dragged;
        tracking = false;
        dragged = false;
        final float ox = dx, oy = dy;
        if (release != null) release.cancel();
        if (Math.abs(ox) < .2f && Math.abs(oy) < .2f) {
            dx = dy = 0;
            view.invalidate();
            return wasDrag;
        }
        if (Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()) {
            dx = dy = 0;
            view.invalidate();
            return wasDrag;
        }
        release = ValueAnimator.ofFloat(1f, 0f);
        release.setDuration(300);
        release.setInterpolator(new OvershootInterpolator(.74f));
        release.addUpdateListener(animation -> {
            final float f = (float) animation.getAnimatedValue();
            dx = ox * f;
            dy = oy * f;
            view.invalidate();
        });
        release.start();
        return wasDrag;
    }

    /** Call inside a save/restore pair around ONLY the circle or pill drawing. */
    public void transform(Canvas canvas, float cx, float cy, float diameter) {
        if (!enabled()) return;
        canvas.translate(dx, dy);
        final float amount = Math.min(1f, (float) Math.hypot(dx, dy) / Math.max(1f, maxOffset));
        final float sx = 1f + amount * .115f;
        final float sy = 1f - amount * .055f;
        // Keep the effect directional: a horizontal tug stretches along X.
        if (Math.abs(dx) >= Math.abs(dy)) {
            canvas.scale(sx, sy, cx, cy);
        } else {
            canvas.scale(sy, sx, cx, cy);
        }
    }
}
