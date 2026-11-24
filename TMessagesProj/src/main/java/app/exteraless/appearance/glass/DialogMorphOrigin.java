package app.exteraless.appearance.glass;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;

import app.exteraless.appearance.LiquidGlassTuning;

/**
 * Where the last tap landed on the screen: the button a confirmation dialog morphs out of (see DialogMorph).
 * Main thread only. Fed from the activity's touches; a view that draws its own sub-buttons overrides it with set().
 */
public final class DialogMorphOrigin {

    private static final long TTL_MS = 900;
    private static final float MAX_VIEW_DP = 160;
    private static final float FALLBACK_DP = 48;

    private static final RectF stored = new RectF();
    private static long storedAt;
    private static @Nullable WeakReference<View> downView;
    private static float downX, downY;
    private static final int[] loc = new int[2];

    private DialogMorphOrigin() {
    }

    /** Call before the activity's own dispatch; only the first pointer's down, up and cancel do any work. */
    public static void onActivityTouch(@Nullable Activity activity, @NonNull MotionEvent ev) {
        final int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            storedAt = 0;
            downView = null;
            if (activity == null || !LiquidGlassTuning.isActive()) {
                return;
            }
            downX = ev.getRawX();
            downY = ev.getRawY();
            final View found = find(activity.getWindow().getDecorView(), downX, downY);
            if (found != null) {
                downView = new WeakReference<>(found);
            }
        } else if (action == MotionEvent.ACTION_UP) {
            final View view = downView != null ? downView.get() : null;
            downView = null;
            if (activity == null || !LiquidGlassTuning.isActive()) {
                return;
            }
            if (view != null && view.isAttachedToWindow() && view.getWidth() > 0 && view.getHeight() > 0
                    && view.getWidth() <= dp(MAX_VIEW_DP) && view.getHeight() <= dp(MAX_VIEW_DP)) {
                view.getLocationOnScreen(loc);
                stored.set(loc[0], loc[1], loc[0] + view.getWidth(), loc[1] + view.getHeight());
            } else {
                final float r = dp(FALLBACK_DP) / 2f;
                stored.set(downX - r, downY - r, downX + r, downY + r);
            }
            storedAt = SystemClock.uptimeMillis();
        } else if (action == MotionEvent.ACTION_CANCEL) {
            downView = null;
        }
    }

    /** The deepest enabled clickable view under a screen point; later siblings are on top. */
    private static @Nullable View find(@NonNull View v, float x, float y) {
        if (v.getVisibility() != View.VISIBLE || v.getAlpha() == 0f) {
            return null;
        }
        v.getLocationOnScreen(loc);
        if (x < loc[0] || y < loc[1] || x >= loc[0] + v.getWidth() || y >= loc[1] + v.getHeight()) {
            return null;
        }
        if (v instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) v;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                final View hit = find(group.getChildAt(i), x, y);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return v.isEnabled() && (v.isClickable() || v.isLongClickable()) ? v : null;
    }

    /** A view that draws its own sub-buttons names the exact one for the tap in progress. */
    public static void set(@NonNull RectF screenRect) {
        stored.set(screenRect);
        storedAt = SystemClock.uptimeMillis();
    }

    /** The origin of the dialog being shown, once: null when there was no fresh tap. */
    public static @Nullable RectF consume() {
        if (storedAt == 0) {
            return null;
        }
        final boolean fresh = SystemClock.uptimeMillis() - storedAt <= TTL_MS;
        storedAt = 0;
        return fresh && !stored.isEmpty() ? new RectF(stored) : null;
    }
}
