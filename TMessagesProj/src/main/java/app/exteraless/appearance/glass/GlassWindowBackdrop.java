package app.exteraless.appearance.glass;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Build;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ScrimOptions;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import org.telegram.utils.glass.source.GlassSourceBitmap;

import app.exteraless.appearance.LiquidGlassTuning;

/**
 * One still snapshot of the screen behind a dialog or sheet window, fed to the same lens glass the scrim menu uses.
 * The lens animates in once and is static at rest; the sampled region follows the anchor view's position on screen.
 */
public final class GlassWindowBackdrop {

    private final View anchor;
    private final GlassSourceBitmap source = new GlassSourceBitmap();
    private final BlurredBackgroundDrawableViewFactory factory = new BlurredBackgroundDrawableViewFactory(source);
    private final Matrix matrix = new Matrix();
    private final int[] location = new int[2];

    private @Nullable BlurredBackgroundDrawable drawable;
    private @Nullable ValueAnimator animator;
    private @Nullable ViewTreeObserver waitObserver;
    private @Nullable ViewTreeObserver.OnPreDrawListener waitListener;
    private @Nullable ViewTreeObserver trackObserver;
    private @Nullable ViewTreeObserver.OnPreDrawListener trackListener;
    private @Nullable View.OnAttachStateChangeListener attachListener;
    private int screenWidth, screenHeight;
    private boolean released;

    public static boolean supported() {
        return LiquidGlassTuning.isActive() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
    }

    /** {@code anchor}: the view whose top-left is the origin the lens drawable is drawn from. */
    public GlassWindowBackdrop(@NonNull View anchor) {
        this.anchor = anchor;
    }

    /**
     * Waits for the anchor's first layout, then captures the screen with {@code hide} invisible.
     * {@code before} runs right ahead of the capture (e.g. to clear a window background that would be captured).
     * {@code done} gets true once the snapshot is set, false when the capture failed; never called after release.
     */
    public void captureWhenLaidOut(@Nullable View hide, @Nullable Runnable before, @NonNull Utilities.Callback<Boolean> done) {
        if (released) {
            return;
        }
        cancelWait();
        waitObserver = anchor.getViewTreeObserver();
        waitListener = () -> {
            if (anchor.getWidth() <= 0 || anchor.getHeight() <= 0) {
                return true;
            }
            cancelWait();
            // the capture draws other windows: do it out of this window's own draw pass
            anchor.post(() -> capture(hide, before, done));
            return true;
        };
        waitObserver.addOnPreDrawListener(waitListener);
    }

    private void capture(@Nullable View hide, @Nullable Runnable before, @NonNull Utilities.Callback<Boolean> done) {
        if (released) {
            return;
        }
        final int visibility = hide != null ? hide.getVisibility() : View.VISIBLE;
        final Bitmap[] result = new Bitmap[1];
        try {
            if (before != null) {
                before.run();
            }
            if (hide != null) {
                hide.setVisibility(View.INVISIBLE);
            }
            ScrimOptions.makeGlobalBlurBitmaps((bitmapBg, bitmapOptions) -> {
                if (bitmapBg != null) {
                    bitmapBg.recycle();
                }
                result[0] = bitmapOptions;
            });
        } catch (Throwable t) {
            FileLog.e(t);
        } finally {
            if (hide != null) {
                hide.setVisibility(visibility);
            }
        }
        final Bitmap bitmap = result[0];
        if (released) {
            return;
        }
        if (bitmap == null || bitmap.isRecycled()) {
            done.run(false);
            return;
        }
        source.setBitmap(bitmap);
        done.run(true);
    }

    /** Call after a successful capture; the caller composes the result under its rim. */
    public BlurredBackgroundDrawable createDrawable(BlurredBackgroundColorProvider colorProvider) {
        final BlurredBackgroundDrawable lens = factory.create(null, colorProvider);
        drawable = lens;
        syncPosition();
        trackerAttach();
        return lens;
    }

    /** The lens starts lifted (clear crystal) and settles to rest. */
    public void animateIn() {
        final BlurredBackgroundDrawable lens = drawable;
        if (released || lens == null) {
            return;
        }
        cancelAnimator();
        if (!LiquidGlassTuning.motionEnabled()) {
            lens.setElasticPressProgress(0f);
            return;
        }
        lens.setElasticPressProgress(1f);
        final ValueAnimator a = ValueAnimator.ofFloat(1f, 0f);
        a.setDuration(320);
        a.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        a.addUpdateListener(v -> lens.setElasticPressProgress((float) v.getAnimatedValue()));
        animator = a;
        a.start();
    }

    /** The bitmap is left to the GC: the window may still draw the last frames of its exit. */
    public void release() {
        if (released) {
            return;
        }
        released = true;
        cancelAnimator();
        cancelWait();
        removePreDraw(trackObserver, trackListener);
        if (attachListener != null) {
            anchor.removeOnAttachStateChangeListener(attachListener);
        }
        trackObserver = null;
        trackListener = null;
        attachListener = null;
        drawable = null;
    }

    private void cancelAnimator() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    private void cancelWait() {
        removePreDraw(waitObserver, waitListener);
        waitObserver = null;
        waitListener = null;
    }

    /** A listener added to a not yet attached view moved to the window's observer: take it off both. */
    private void removePreDraw(@Nullable ViewTreeObserver stored, @Nullable ViewTreeObserver.OnPreDrawListener listener) {
        if (listener == null) {
            return;
        }
        if (stored != null && stored.isAlive()) {
            stored.removeOnPreDrawListener(listener);
        }
        final ViewTreeObserver current = anchor.getViewTreeObserver();
        if (current != stored && current.isAlive()) {
            current.removeOnPreDrawListener(listener);
        }
    }

    private void trackerAttach() {
        trackListener = () -> {
            syncPosition();
            return true;
        };
        attachListener = new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
                subscribeTracker();
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
            }
        };
        anchor.addOnAttachStateChangeListener(attachListener);
        subscribeTracker();
    }

    private void subscribeTracker() {
        final ViewTreeObserver now = anchor.getViewTreeObserver();
        if (released || trackListener == null || now == trackObserver) {
            return;
        }
        removePreDraw(trackObserver, trackListener);
        trackObserver = now;
        now.addOnPreDrawListener(trackListener);
    }

    /** The snapshot is in screen pixels: the lens samples it at the anchor's position on screen. */
    private void syncPosition() {
        final BlurredBackgroundDrawable lens = drawable;
        final Bitmap bitmap = source.getBitmap();
        if (released || lens == null || bitmap == null || bitmap.isRecycled()) {
            return;
        }
        final int sw = AndroidUtilities.displaySize.x;
        final int sh = AndroidUtilities.displaySize.y + AndroidUtilities.statusBarHeight + AndroidUtilities.navigationBarHeight;
        boolean changed = false;
        if (sw != screenWidth || sh != screenHeight) {
            screenWidth = sw;
            screenHeight = sh;
            matrix.setScale((float) sw / bitmap.getWidth(), (float) sh / bitmap.getHeight());
            source.setMatrix(matrix);
            lens.invalidateDisplayList();
            changed = true;
        }
        anchor.getLocationOnScreen(location);
        if (lens.getSourceOffsetX() != location[0] || lens.getSourceOffsetY() != location[1]) {
            lens.setSourceOffset(location[0], location[1]);
            changed = true;
        }
        if (changed) {
            lens.invalidateSelf();
        }
    }
}
