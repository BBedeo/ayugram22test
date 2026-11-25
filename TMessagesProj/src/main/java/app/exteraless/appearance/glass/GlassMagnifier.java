package app.exteraless.appearance.glass;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Magnifier;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.Theme;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * The loupe that appears over the finger while a text cursor or selection handle is dragged, as a
 * Liquid Glass pill: it pops up with a spring, trails the finger and shows the window content magnified under it.
 * Replaces the system {@link Magnifier}, which cannot be restyled.
 */
public final class GlassMagnifier {

    private static final float ZOOM = 1.3f;
    private static final int WIDTH_DP = 150;
    private static final int HEIGHT_DP = 56;

    private final View anchor;
    private final int[] loc = new int[2];
    private Pill pill;
    private SpringAnimation springX, springY;

    public GlassMagnifier(View anchor) {
        this.anchor = anchor;
    }

    public static boolean enabled() {
        return GlassTokens.isActive();
    }

    /** Shows or moves the loupe over the point at the given screen coordinates. */
    public void show(float screenX, float screenY) {
        if (!(anchor.getRootView() instanceof ViewGroup)) {
            return;
        }
        final ViewGroup root = (ViewGroup) anchor.getRootView();
        final int w = dp(WIDTH_DP);
        final int h = dp(HEIGHT_DP);
        boolean fresh = false;
        if (pill == null || pill.root != root) {
            removeNow();
            pill = new Pill(anchor.getContext(), root);
            try {
                root.addView(pill, new FrameLayout.LayoutParams(w, h));
            } catch (Exception e) {
                FileLog.e(e);
                pill = null;
                return;
            }
            fresh = true;
        }
        root.getLocationOnScreen(loc);
        pill.focusX = screenX - loc[0];
        pill.focusY = screenY - loc[1];
        final float tx = Math.max(dp(8), Math.min(root.getWidth() - w - dp(8), pill.focusX - w / 2f));
        final float ty = Math.max(dp(8), pill.focusY - h - dp(34));
        if (fresh) {
            pill.setTranslationX(tx);
            pill.setTranslationY(ty);
            pill.setPivotX(w / 2f);
            pill.setPivotY(h);
            pill.setScaleX(0.4f);
            pill.setScaleY(0.4f);
            pill.setAlpha(0f);
            pill.animate().alpha(1f).setDuration(110).start();
            spring(pill, DynamicAnimation.SCALE_X, 380f, 0.5f, 1f).start();
            spring(pill, DynamicAnimation.SCALE_Y, 380f, 0.5f, 1f).start();
            springX = spring(pill, DynamicAnimation.TRANSLATION_X, 700f, 0.75f, tx);
            springY = spring(pill, DynamicAnimation.TRANSLATION_Y, 900f, 0.8f, ty);
        } else {
            springX.animateToFinalPosition(tx);
            springY.animateToFinalPosition(ty);
        }
        pill.capture();
        pill.invalidate();
    }

    public void dismiss() {
        final Pill p = pill;
        if (p == null) {
            return;
        }
        pill = null;
        cancelSprings();
        p.animate().cancel();
        p.animate().alpha(0f).scaleX(0.6f).scaleY(0.6f).setDuration(150).withEndAction(() -> {
            if (p.getParent() instanceof ViewGroup) {
                ((ViewGroup) p.getParent()).removeView(p);
            }
        }).start();
    }

    private void removeNow() {
        cancelSprings();
        if (pill != null && pill.getParent() instanceof ViewGroup) {
            ((ViewGroup) pill.getParent()).removeView(pill);
        }
        pill = null;
    }

    private void cancelSprings() {
        if (springX != null) {
            springX.cancel();
            springY.cancel();
            springX = null;
            springY = null;
        }
    }

    private static SpringAnimation spring(View v, DynamicAnimation.ViewProperty p, float stiffness, float damping, float target) {
        final SpringAnimation a = new SpringAnimation(v, p);
        a.setSpring(new SpringForce(target).setStiffness(stiffness).setDampingRatio(damping));
        return a;
    }

    private static final class Pill extends View {
        final ViewGroup root;
        float focusX, focusY;
        private boolean capturing;
        private Bitmap shot;
        private final GlassSurfaceDrawable glass;
        private final Path clip = new Path();
        private final RectF rect = new RectF();

        Pill(Context context, ViewGroup root) {
            super(context);
            this.root = root;
            glass = new GlassSurfaceDrawable(Theme.getColor(Theme.key_windowBackgroundWhite), GlassTokens.TIER_THICK, HEIGHT_DP / 2f);
            glass.setShadow(true);
            glass.setFillAlpha(0.18f);
        }

        /**
         * Magnified copy of the window around the focus, drawn into a software bitmap: drawing the live tree from onDraw
         * re-enters the display list that is being recorded and crashes.
         */
        void capture() {
            final int w = getLayoutParams().width;
            final int h = getLayoutParams().height;
            if (shot == null) {
                shot = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            }
            shot.eraseColor(0);
            final Canvas c = new Canvas(shot);
            c.translate(w / 2f, h / 2f);
            c.scale(ZOOM, ZOOM);
            c.translate(-focusX, -focusY);
            capturing = true;
            try {
                root.draw(c);
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                capturing = false;
            }
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            if (capturing) {
                return;
            }
            final int w = getWidth();
            final int h = getHeight();
            rect.set(dp(1), dp(1), w - dp(1), h - dp(1));
            clip.rewind();
            clip.addRoundRect(rect, h / 2f, h / 2f, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clip);
            if (shot != null) {
                canvas.drawBitmap(shot, 0, 0, null);
            }
            canvas.restore();
            glass.setBounds(0, 0, w, h);
            glass.draw(canvas);
        }
    }

    /**
     * Swaps the animator inside the field's Editor for one wrapping a 1px magnifier, so the system loupe stops showing
     * while the cursor is dragged. Best effort: on any failure the system loupe simply stays.
     */
    public static void suppressSystem(TextView view) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return;
        }
        try {
            org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("Landroid/widget/");
            final Field editorField = TextView.class.getDeclaredField("mEditor");
            editorField.setAccessible(true);
            final Object editor = editorField.get(view);
            if (editor == null) {
                return;
            }
            final Field animatorField = editor.getClass().getDeclaredField("mMagnifierAnimator");
            animatorField.setAccessible(true);
            final Class<?> animatorClass = Class.forName("android.widget.Editor$MagnifierMotionAnimator");
            final Magnifier tiny = new Magnifier.Builder(view).setSize(1, 1).setInitialZoom(1f).setElevation(0f).setCornerRadius(0f).build();
            final Constructor<?> ctor = animatorClass.getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            final Object animator = ctor.getParameterTypes().length == 2 ? ctor.newInstance(editor, tiny) : ctor.newInstance(tiny);
            animatorField.set(editor, animator);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
}
