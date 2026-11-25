package app.exteraless.appearance.glass;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;

import app.exteraless.appearance.GlassMenuHelper;

/**
 * A glass dialog growing out of the button that opened it. The dialog is its own window sized to the panel, so the
 * morphing shape is a ghost in the activity's window, under the dialog's window: one lens shape that goes from the
 * button's rect to the panel's, while the panel fades in over it. The ghost's snapshot is handed to the dialog, whose
 * own lens then takes over at the same place.
 */
public final class DialogMorph {

    private static final float DURATION = 0.34f;
    private static final float PANEL_FADE_AT = 0.55f;
    private static final float HANDOFF_AT = 0.8f;
    private static final long PANEL_WAIT_MS = 500;

    private static @Nullable DialogMorph current;

    private final Ghost host;
    private final View panel;
    private final GlassWindowBackdrop backdrop;
    private final Utilities.Callback<Bitmap> handoff;
    private final Theme.ResourcesProvider resourcesProvider;
    private final int rimColor;
    private final RectF origin = new RectF();
    private final RectF target = new RectF();
    private final RectF shape = new RectF();
    private final int[] hostLoc = new int[2];
    private final int[] panelLoc = new int[2];
    private final float originRadius;
    private final Runnable stepper = this::step;

    // the panel is revealed by the growing shape: it is clipped to it, so it comes out of the shape and touches its edges
    private final RectF reveal = new RectF();
    private float revealRadius;
    private ViewOutlineProvider panelOutline;
    private boolean panelClipped;
    // the panel's own outline clip (a glass dialog clips its content to the window's rounded corners)
    private boolean panelClipToOutline;
    private final ViewOutlineProvider revealOutline = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            outline.setRoundRect(Math.round(reveal.left), Math.round(reveal.top), Math.round(reveal.right), Math.round(reveal.bottom), revealRadius);
        }
    };

    private @Nullable BlurredBackgroundDrawable lens;
    private @Nullable GlassSurfaceDrawable rim;
    private @Nullable LayerDrawable layer;
    private @Nullable Bitmap snapshot;
    private long readyAt, startTime;
    private int lastAlpha = 255;
    private boolean panelFading, handedOff, done, detaching;

    /** The activity a dialog's context belongs to, or null when it has none (or it is going away). */
    public static @Nullable Activity findActivity(@Nullable Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                final Activity activity = (Activity) context;
                return activity.isFinishing() || activity.isDestroyed() ? null : activity;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    /**
     * Starts the ghost over {@code originScreen} (screen px) and grows it into {@code panel}. {@code handoff} gets the
     * snapshot once at {@code HANDOFF_AT} (the dialog puts its own lens in, the panel is fading in by then), or null when
     * the morph failed: the dialog then falls back to its plain look. It is never called after cancel().
     * Returns null when nothing was started.
     */
    public static @Nullable DialogMorph start(@NonNull Activity activity, @NonNull RectF originScreen, @NonNull View panel,
                                              @Nullable Theme.ResourcesProvider resourcesProvider, int rimColor,
                                              @NonNull Utilities.Callback<Bitmap> handoff) {
        return start(activity, null, -1, originScreen, panel, resourcesProvider, rimColor, handoff);
    }

    /** As above, with the ghost in {@code parent} at {@code index} (under the panel, when the panel is not a window of its own). */
    public static @Nullable DialogMorph start(@NonNull Activity activity, @Nullable ViewGroup parent, int index, @NonNull RectF originScreen, @NonNull View panel,
                                              @Nullable Theme.ResourcesProvider resourcesProvider, int rimColor,
                                              @NonNull Utilities.Callback<Bitmap> handoff) {
        final View decor = activity.getWindow().getDecorView();
        final ViewGroup container = parent != null ? parent : decor instanceof ViewGroup ? (ViewGroup) decor : null;
        if (container == null || originScreen.isEmpty()) {
            return null;
        }
        if (current != null) {
            current.complete();
        }
        final DialogMorph morph = new DialogMorph(activity, originScreen, panel, resourcesProvider, rimColor, handoff);
        current = morph;
        try {
            container.addView(morph.host, index, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } catch (Throwable t) {
            FileLog.e(t);
            current = null;
            return null;
        }
        morph.backdrop.captureWhenLaidOut(morph.host, null, morph::onCaptured);
        return morph;
    }

    private DialogMorph(Activity activity, RectF originScreen, View panel, @Nullable Theme.ResourcesProvider resourcesProvider,
                        int rimColor, Utilities.Callback<Bitmap> handoff) {
        this.panel = panel;
        this.resourcesProvider = resourcesProvider;
        this.rimColor = rimColor;
        this.handoff = handoff;
        origin.set(originScreen);
        originRadius = Math.min(origin.width(), origin.height()) / 2f;
        host = new Ghost(activity);
        backdrop = new GlassWindowBackdrop(host);
    }

    private void onCaptured(boolean ok) {
        if (done) {
            return;
        }
        final Bitmap bitmap = ok ? backdrop.getSnapshot() : null;
        if (bitmap == null) {
            complete();
            return;
        }
        try {
            snapshot = bitmap;
            lens = backdrop.createDrawable(GlassMenuHelper.scrimPanelBackgroundFill(resourcesProvider)).setRadius(originRadius);
            rim = new GlassSurfaceDrawable(rimColor, GlassTokens.TIER_THICK, GlassTokens.RADIUS_DIALOG)
                    .setFillAlpha(0f)
                    .setShadow(false);
            layer = new LayerDrawable(new Drawable[]{lens, rim});
            layer.setCallback(host);
        } catch (Throwable t) {
            FileLog.e(t);
            snapshot = null;
            complete();
            return;
        }
        readyAt = AnimationUtils.currentAnimationTimeMillis();
        host.postOnAnimation(stepper);
    }

    private void step() {
        final LayerDrawable layer = this.layer;
        final BlurredBackgroundDrawable lens = this.lens;
        final GlassSurfaceDrawable rim = this.rim;
        if (done || layer == null || lens == null || rim == null) {
            return;
        }
        final long now = AnimationUtils.currentAnimationTimeMillis();
        // the dialog's window may not be laid out yet
        if (!panel.isAttachedToWindow() || panel.getWidth() <= 0 || panel.getHeight() <= 0) {
            if (now - readyAt > PANEL_WAIT_MS) {
                complete();
            } else {
                host.postOnAnimation(stepper);
            }
            return;
        }
        if (startTime == 0) {
            startTime = now;
        }
        panel.getLocationOnScreen(panelLoc);
        target.set(panelLoc[0], panelLoc[1], panelLoc[0] + panel.getWidth(), panelLoc[1] + panel.getHeight());

        final float t = (now - startTime) / 1000f;
        final float u = Utilities.clamp01(t / DURATION);
        // the same springs as the glass menus
        final float pw = t > 0.5f ? 1f : (float) springStep(t, 0.78, 0.30);
        final float ph = t > 0.5f ? 1f : (float) springStep(t - 0.02, 0.80, 0.34);
        shape.set(lerp(origin.left, target.left, pw), lerp(origin.top, target.top, ph),
                lerp(origin.right, target.right, pw), lerp(origin.bottom, target.bottom, ph));
        final float radius = Math.min(Math.min(shape.width(), shape.height()) / 2f,
                lerp(originRadius, dp(GlassTokens.RADIUS_DIALOG), Utilities.clamp01(ph)));

        reveal.set(shape.left - panelLoc[0], shape.top - panelLoc[1], shape.right - panelLoc[0], shape.bottom - panelLoc[1]);
        revealRadius = radius;
        if (!panelClipped) {
            panelClipped = true;
            panelOutline = panel.getOutlineProvider();
            panelClipToOutline = panel.getClipToOutline();
            panel.setOutlineProvider(revealOutline);
            panel.setClipToOutline(true);
        }
        panel.invalidateOutline();
        panel.setAlpha(Utilities.clamp01((u - 0.08f) / 0.3f));
        host.getLocationOnScreen(hostLoc);
        lens.setRadius(radius);
        rim.setRadius(radius / AndroidUtilities.density);
        layer.setBounds(Math.round(shape.left) - hostLoc[0], Math.round(shape.top) - hostLoc[1],
                Math.round(shape.right) - hostLoc[0], Math.round(shape.bottom) - hostLoc[1]);
        // the lens starts lifted and settles as the shape grows
        lens.setElasticPressProgress(1f - Utilities.clamp01(Math.max(pw, ph)));
        final int alpha = Math.round(255 * (1f - Utilities.clamp01((u - HANDOFF_AT) / (1f - HANDOFF_AT))));
        if (alpha != lastAlpha) {
            lastAlpha = alpha;
            layer.setAlpha(alpha);
        }

        if (!handedOff && u >= HANDOFF_AT) {
            handedOff = true;
            handoff.run(snapshot);
        }
        host.invalidate();
        if (u >= 1f) {
            finish();
        } else {
            host.postOnAnimation(stepper);
        }
    }

    /** Ends the morph at once, the dialog keeping its panel: it gets its hand-off now if it has not had it. */
    public void complete() {
        if (done) {
            return;
        }
        if (!handedOff) {
            handedOff = true;
            panel.animate().cancel();
            panel.setAlpha(1f);
            handoff.run(layer != null ? snapshot : null);
        }
        finish();
    }

    /** The dialog is gone: drops the ghost without any call back. */
    public void cancel() {
        handedOff = true;
        finish();
    }

    private void finish() {
        if (done) {
            return;
        }
        done = true;
        if (panelClipped) {
            panelClipped = false;
            panel.setClipToOutline(panelClipToOutline);
            panel.setOutlineProvider(panelOutline);
        }
        if (current == this) {
            current = null;
        }
        host.removeCallbacks(stepper);
        backdrop.release();
        // taking it off its parent while that detaches its children would corrupt the loop
        final ViewGroup parent = !detaching && host.getParent() instanceof ViewGroup ? (ViewGroup) host.getParent() : null;
        if (parent != null) {
            parent.removeView(host);
        }
    }

    private static float lerp(float a, float b, float f) {
        return a + (b - a) * f;
    }

    private static double springStep(double t, double z, double period) {
        if (t <= 0) return 0;
        final double w0 = 2 * Math.PI / period, wd = w0 * Math.sqrt(1 - z * z);
        return 1 - Math.exp(-z * w0 * t) * (Math.cos(wd * t) + z / Math.sqrt(1 - z * z) * Math.sin(wd * t));
    }

    /** Draws the one lens shape; never takes a touch. */
    private final class Ghost extends View {
        Ghost(Context context) {
            super(context);
            setClickable(false);
            setFocusable(false);
        }

        @Override
        protected boolean verifyDrawable(@NonNull Drawable who) {
            return who == layer || super.verifyDrawable(who);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            final LayerDrawable l = layer;
            if (l != null && !l.getBounds().isEmpty()) {
                l.draw(canvas);
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            detaching = true;
            // the activity is going away (or something took the ghost off): the dialog must not stay invisible
            complete();
        }
    }
}
