package app.exteraless.appearance.glass;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.ContextLinkCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Wallet.WalletGiftCell;

import tw.nekomimi.nekogram.NekoConfig;

/**
 * The touch effect of Glass Touch: shine, squeeze and (Android 13+) a lens under the finger. It follows the pressed
 * state the list already gives its rows, holds one row at a time and leaves alone a row that animates itself. Shine and
 * squeeze belong to the row. The lens belongs to the whole list, so a row's edges never cut it, and it stays under the
 * finger until the finger is gone, also when a scroll or a swipe takes the row's press away.
 */
public final class GlassTouchController implements View.OnAttachStateChangeListener, ViewTreeObserver.OnPreDrawListener {

    private static final long MIN_VISIBLE_MS = 140;
    private static final long LIST_RELEASE_MS = 150; // longer than a list keeps a tapped row pressed
    private static final float LENS_PRESS_STIFFNESS = 520f;
    private static final float LENS_RELEASE_STIFFNESS = 260f;
    private static final float LENS_DAMPING = 0.5f;

    private static GlassTouchController active;

    // the finger that started the current gesture in the activity, in screen coordinates
    private static boolean fingerDown;
    private static int fingerId;
    private static float fingerX;
    private static float fingerY;
    private static int gestures; // counts finger downs, so a lens never moves on to another finger

    private final View row;
    private final View host; // the list: it shows the lens
    private int gesture = gestures; // the finger down this press belongs to
    private final int[] spot = new int[2];
    private final long started = SystemClock.uptimeMillis();
    private final float pressedScale;
    private final Shine shine = new Shine();
    private final SpringAnimation scale = new SpringAnimation(new FloatValueHolder(1f));
    private final SpringAnimation lensSpring = new SpringAnimation(new FloatValueHolder(0f));
    private final Runnable letGo = this::letGo;

    private Object lens; // Lens, Android 13+; typed loosely so older runtimes never resolve it
    private float writtenScale = 1f;
    private boolean ownScale = true;
    private boolean lensed;
    private boolean carried; // the list took the press back, the finger and its lens are still there
    private boolean rowGone;
    private boolean releasing;
    private boolean done;

    /** {@code x}, {@code y} are list coordinates of the touch; releases carry none. */
    public static void onRowPressed(RecyclerListView list, View row, float x, float y, boolean pressed) {
        if (!pressed) {
            if (active != null && active.row == row) {
                active.release();
            }
            return;
        }
        if (active != null) {
            if (active.row == row && !active.releasing && !active.carried) {
                AndroidUtilities.cancelRunOnUIThread(active.letGo);
                active.gesture = gestures;
                return;
            }
            active.cleanup();
        }
        if (GlassTouchSettings.press() && eligible(list, row)) {
            active = new GlassTouchController(list, row, x, y);
        }
    }

    /** Reads the activity's touches and changes none of them. */
    public static void onActivityTouch(MotionEvent ev) {
        final int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            gestures++;
            fingerId = ev.getPointerId(0);
            fingerDown = true;
            if (active != null && active.carried) {
                active.letGo(); // the finger that carried it is gone
            }
        }
        final int index = fingerDown ? ev.findPointerIndex(fingerId) : -1;
        if (index < 0) {
            return;
        }
        // getRawX() is the first pointer's, the window's offset on the screen is the same for all of them
        fingerX = ev.getX(index) + ev.getRawX() - ev.getX();
        fingerY = ev.getY(index) + ev.getRawY() - ev.getY();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
                || action == MotionEvent.ACTION_POINTER_UP && ev.getActionIndex() == index) {
            fingerDown = false;
            if (active != null && active.gesture == gestures) {
                active.fingerLifted();
            }
        } else if (action == MotionEvent.ACTION_MOVE && active != null && active.lens != null) {
            active.host.invalidate(); // the lens moves before that draw, when the pages have moved too
        }
    }

    private static boolean eligible(RecyclerListView list, View row) {
        return row.getHeight() > 0 && row.getWidth() >= list.getWidth() * 0.85f
                // chat cells draw their own press, the last two scale themselves
                && !(row instanceof ChatMessageCell) && !(row instanceof ChatActionCell)
                && !(row instanceof ContextLinkCell) && !(row instanceof WalletGiftCell)
                && row.getStateListAnimator() == null
                && row.getScaleX() == 1f && row.getScaleY() == 1f && row.getTranslationX() == 0f;
    }

    private GlassTouchController(View host, View row, float x, float y) {
        this.host = host;
        this.row = row;
        pressedScale = GlassTouchMath.squeezeScale(Math.max(row.getWidth(), row.getHeight()), AndroidUtilities.density);
        shine.setBounds(0, 0, row.getWidth(), row.getHeight());
        row.getOverlay().add(shine);
        row.addOnAttachStateChangeListener(this);
        host.addOnAttachStateChangeListener(this);

        scale.setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE);
        scale.addUpdateListener((animation, value, velocity) -> squeeze(value));
        scale.addEndListener((animation, canceled, value, velocity) -> finish());
        GlassMotion.spring(scale, pressedScale, GlassMotion.PRESS_STIFFNESS, SpringForce.DAMPING_RATIO_NO_BOUNCY);
        if (Build.VERSION.SDK_INT >= 33 && host.isHardwareAccelerated()) {
            holdLens(x, y);
        }
        if (!NekoConfig.disableVibration.Bool()) {
            row.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
    }

    /** {@code x}, {@code y} are list coordinates. */
    @RequiresApi(33)
    private void holdLens(float x, float y) {
        final Lens fresh;
        try {
            fresh = new Lens(host, x, y,
                    GlassTouchMath.lensRadius(row.getWidth(), row.getHeight(), AndroidUtilities.density));
        } catch (Exception e) {
            FileLog.e(e);
            return;
        }
        lens = fresh;
        lensed = true;
        follow(); // the finger may have moved during the tap delay
        fresh.set(0f);
        host.getViewTreeObserver().addOnPreDrawListener(this);
        lensSpring.setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_ALPHA);
        lensSpring.addUpdateListener((animation, value, velocity) -> fresh.set(value));
        lensSpring.addEndListener((animation, canceled, value, velocity) -> finish());
        GlassMotion.spring(lensSpring, 1f, LENS_PRESS_STIFFNESS, LENS_DAMPING);
    }

    @Override
    public boolean onPreDraw() {
        follow();
        return true;
    }

    /** The finger that pressed the row is still on the screen. */
    private boolean fingerHolds() {
        return fingerDown && gesture == gestures;
    }

    /** Puts the lens under the finger, in the list's own coordinates. */
    private void follow() {
        if (Build.VERSION.SDK_INT < 33 || lens == null || !fingerHolds() || releasing) {
            return;
        }
        host.getLocationOnScreen(spot);
        ((Lens) lens).move(
                Math.max(0f, Math.min(host.getWidth(), (fingerX - spot[0]) / host.getScaleX())),
                Math.max(0f, Math.min(host.getHeight(), (fingerY - spot[1]) / host.getScaleY())));
    }

    private void squeeze(float value) {
        // someone else scaled the row: their value stays
        ownScale &= row.getScaleX() == writtenScale && row.getScaleY() == writtenScale;
        if (ownScale) {
            row.setScaleX(value);
            row.setScaleY(value);
            writtenScale = value;
        }
        shine.setStrength((1f - value) / (1f - pressedScale));
    }

    private void release() {
        AndroidUtilities.cancelRunOnUIThread(letGo);
        final long wait = MIN_VISIBLE_MS - (SystemClock.uptimeMillis() - started);
        if (wait > 0) {
            AndroidUtilities.runOnUIThread(letGo, wait);
        } else {
            letGo();
        }
    }

    private void fingerLifted() {
        if (carried) {
            letGo();
        } else {
            // the list releases its row itself, but one that gave the gesture away may never name the row again
            AndroidUtilities.cancelRunOnUIThread(letGo);
            AndroidUtilities.runOnUIThread(letGo, LIST_RELEASE_MS);
        }
    }

    private void letGo() {
        if (releasing || done) {
            return;
        }
        if (lens != null && fingerHolds()) {
            carried = true; // a scroll or a swipe took the press: the row springs back, the lens stays
            springRowBack();
            return;
        }
        releasing = true;
        springRowBack();
        if (lens != null) {
            GlassMotion.spring(lensSpring, 0f, LENS_RELEASE_STIFFNESS, LENS_DAMPING);
        }
        finish(); // does nothing while a spring is running
    }

    private void springRowBack() {
        if (!rowGone) {
            GlassMotion.spring(scale, 1f, GlassMotion.RELEASE_STIFFNESS, GlassMotion.RELEASE_DAMPING);
        }
    }

    /** Gives the row back as it was; a recycled row shows another chat at once. */
    private void dropRow() {
        if (rowGone) {
            return;
        }
        rowGone = true;
        row.removeOnAttachStateChangeListener(this);
        scale.cancel();
        if (ownScale && row.getScaleX() == writtenScale && row.getScaleY() == writtenScale) {
            row.setScaleX(1f);
            row.setScaleY(1f);
        }
        row.getOverlay().remove(shine);
    }

    private void finish() {
        if (releasing && !scale.isRunning() && !lensSpring.isRunning()) {
            cleanup();
        }
    }

    private void cleanup() {
        if (done) {
            return;
        }
        done = true;
        AndroidUtilities.cancelRunOnUIThread(letGo);
        host.removeOnAttachStateChangeListener(this);
        dropRow();
        lensSpring.cancel();
        if (Build.VERSION.SDK_INT >= 33 && lens != null) {
            final Lens gone = (Lens) lens;
            lens = null;
            host.getViewTreeObserver().removeOnPreDrawListener(this);
            gone.remove();
        }
        if (active == this) {
            active = null;
        }
    }

    @Override
    public void onViewAttachedToWindow(@NonNull View view) {
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull View view) {
        if (view == host) {
            cleanup();
        } else {
            dropRow(); // scrolled away
            letGo();
            finish();
        }
    }

    @RequiresApi(33)
    private static final class Lens {
        // compiled once: one list holds the lens at a time, and an effect keeps the uniforms it was created with
        private static RuntimeShader shader;

        private final View view;
        private float p = -1f;
        private float x;
        private float y;
        private boolean applied;

        Lens(View view, float x, float y, float radius) {
            this.view = view;
            this.x = x;
            this.y = y;
            if (shader == null) {
                shader = new RuntimeShader(AndroidUtilities.readRes(R.raw.glass_touch_lens_shader));
            }
            shader.setFloatUniform("center", x, y);
            shader.setFloatUniform("radius", radius);
            shader.setFloatUniform("rim", 1f);
        }

        void set(float value) {
            if (Math.abs(value - p) <= 0.004f) {
                return;
            }
            p = value;
            shader.setFloatUniform("p", value);
            apply();
        }

        /** Moving rebuilds the effect even when the strength stands still. */
        void move(float toX, float toY) {
            if (Math.abs(toX - x) < 1f && Math.abs(toY - y) < 1f) {
                return;
            }
            x = toX;
            y = toY;
            shader.setFloatUniform("center", x, y);
            if (applied) {
                apply();
            }
        }

        private void apply() {
            shader.setFloatUniform("size", view.getWidth(), view.getHeight());
            view.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "img"));
            applied = true;
        }

        void remove() {
            if (applied) {
                view.setRenderEffect(null);
            }
        }
    }

    /** The glint over the held row: a diagonal gradient with the app's glass rim, drawn in the row's overlay. */
    private final class Shine extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final GlassRim rim = new GlassRim();
        private boolean dark;
        private float strength;

        void setStrength(float value) {
            strength = Math.max(0f, Math.min(1f, value));
            invalidateSelf();
        }

        @Override
        protected void onBoundsChange(@NonNull android.graphics.Rect bounds) {
            rect.set(bounds);
            rect.inset(AndroidUtilities.dp(3), AndroidUtilities.dp(3));
            dark = GlassTokens.isDark();
            paint.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                    dark ? Color.argb(70, 255, 255, 255) : Color.argb(12, 0, 0, 0),
                    dark ? Color.argb(10, 255, 255, 255) : Color.argb(34, 0, 0, 0),
                    Shader.TileMode.CLAMP));
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            final float alpha = strength * (lensed ? 0.6f : 1f);
            if (alpha <= 0f || rect.width() <= 0 || rect.height() <= 0) {
                return;
            }
            final float radius = Math.min(rect.height() / 2f, AndroidUtilities.dp(16));
            paint.setAlpha(Math.round(255 * alpha));
            canvas.drawRoundRect(rect, radius, radius, paint);
            rim.draw(canvas, rect, radius, radius, alpha);
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
