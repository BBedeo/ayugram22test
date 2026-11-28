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
import android.view.View;

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
 * state the list already gives its rows, holds one row at a time and leaves alone a row that animates itself.
 */
public final class GlassTouchController implements View.OnAttachStateChangeListener {

    private static final long MIN_VISIBLE_MS = 140;
    private static final float LENS_PRESS_STIFFNESS = 520f;
    private static final float LENS_RELEASE_STIFFNESS = 260f;
    private static final float LENS_DAMPING = 0.5f;

    private static GlassTouchController active;

    private final View row;
    private final long started = SystemClock.uptimeMillis();
    private final float pressedScale;
    private final Shine shine = new Shine();
    private final SpringAnimation scale = new SpringAnimation(new FloatValueHolder(1f));
    private final SpringAnimation lensSpring = new SpringAnimation(new FloatValueHolder(0f));
    private final Runnable letGo = this::letGo;

    private Object lens; // Lens, Android 13+; typed loosely so older runtimes never resolve it
    private float writtenScale = 1f;
    private boolean ownScale = true;
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
            if (active.row == row && !active.releasing) {
                AndroidUtilities.cancelRunOnUIThread(active.letGo);
                return;
            }
            active.cleanup();
        }
        if (GlassTouchSettings.press() && eligible(list, row)) {
            active = new GlassTouchController(row, x - row.getX(), y - row.getY());
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

    private GlassTouchController(View row, float x, float y) {
        this.row = row;
        final float density = AndroidUtilities.density;
        pressedScale = GlassTouchMath.squeezeScale(Math.max(row.getWidth(), row.getHeight()), density);
        if (Build.VERSION.SDK_INT >= 33 && row.isHardwareAccelerated()) {
            try {
                lens = new Lens(row, x, y, GlassTouchMath.lensRadius(row.getWidth(), row.getHeight(), density));
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        shine.setBounds(0, 0, row.getWidth(), row.getHeight());
        row.getOverlay().add(shine);
        row.addOnAttachStateChangeListener(this);

        scale.setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE);
        scale.addUpdateListener((animation, value, velocity) -> squeeze(value));
        scale.addEndListener((animation, canceled, value, velocity) -> finish());
        GlassMotion.spring(scale, pressedScale, GlassMotion.PRESS_STIFFNESS, SpringForce.DAMPING_RATIO_NO_BOUNCY);
        if (lens != null) {
            lensSpring.setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_ALPHA);
            lensSpring.addUpdateListener((animation, value, velocity) -> ((Lens) lens).set(value));
            lensSpring.addEndListener((animation, canceled, value, velocity) -> finish());
            GlassMotion.spring(lensSpring, 1f, LENS_PRESS_STIFFNESS, LENS_DAMPING);
        }
        if (!NekoConfig.disableVibration.Bool()) {
            row.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
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

    private void letGo() {
        if (releasing || done) {
            return;
        }
        releasing = true;
        GlassMotion.spring(scale, 1f, GlassMotion.RELEASE_STIFFNESS, GlassMotion.RELEASE_DAMPING);
        if (lens != null) {
            GlassMotion.spring(lensSpring, 0f, LENS_RELEASE_STIFFNESS, LENS_DAMPING);
        }
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
        row.removeOnAttachStateChangeListener(this);
        scale.cancel();
        lensSpring.cancel();
        if (ownScale && row.getScaleX() == writtenScale && row.getScaleY() == writtenScale) {
            row.setScaleX(1f);
            row.setScaleY(1f);
        }
        if (Build.VERSION.SDK_INT >= 33 && lens != null) {
            ((Lens) lens).remove();
        }
        row.getOverlay().remove(shine);
        if (active == this) {
            active = null;
        }
    }

    @Override
    public void onViewAttachedToWindow(@NonNull View view) {
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull View view) {
        cleanup();
    }

    @RequiresApi(33)
    private static final class Lens {
        // compiled once: one row holds the lens at a time, and an effect keeps the uniforms it was created with
        private static RuntimeShader shader;

        private final View row;
        private float p = -1f;
        private boolean applied;

        Lens(View row, float x, float y, float radius) {
            this.row = row;
            if (shader == null) {
                shader = new RuntimeShader(AndroidUtilities.readRes(R.raw.glass_touch_lens_shader));
            }
            shader.setFloatUniform("size", row.getWidth(), row.getHeight());
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
            row.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "img"));
            applied = true;
        }

        void remove() {
            if (applied) {
                row.setRenderEffect(null);
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
            final float alpha = strength * (lens != null ? 0.6f : 1f);
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
