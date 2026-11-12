package org.telegram.ui.Components.voip;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import app.exteraless.appearance.LiquidGlassTuning;

import org.telegram.messenger.AndroidUtilities;

/**
 * Low-allocation optical treatment for the specialised VoIP controls.
 *
 * The VoIP button pipeline draws on a software Canvas to preserve Telegram's
 * animated lottie cutouts and its moving call gradient. A glass overlay in
 * that pipeline cannot safely reuse the chat RenderNode shader: it would
 * accidentally sample the UI controls themselves. Instead, this material
 * keeps the already available VoIP background capture, adds a bright
 * directional lens rim and subtly elastically distorts the existing image.
 * Never interfere with the Lottie icon mask or touch listener.
 */
public final class VoIPCallGlass {
    private VoIPCallGlass() {}

    /** One painter per View. GPU shaders and matrices are reused between frames. */
    public static final class Painter {
        private final Paint face = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix gradientMatrix = new Matrix();
        private final RectF arcRect = new RectF();
        private LinearGradient faceGradient;
        private LinearGradient rimGradient;
        private float cachedRadius = -1;
        private int cachedColor = 0;
        private float alpha = 1f;

        public Painter() {
            rim.setStyle(Paint.Style.STROKE);
            glint.setStyle(Paint.Style.STROKE);
            glint.setStrokeCap(Paint.Cap.ROUND);
        }

        public void drawFace(Canvas canvas, float cx, float cy, float radius, int baseColor, float pressed) {
            drawFace(canvas, cx, cy, radius, baseColor, pressed, 1f);
        }

        public void drawFace(Canvas canvas, float cx, float cy, float radius, int baseColor, float pressed, float alpha) {
            if (!LiquidGlassTuning.isActive() || radius <= 0f) return;
            this.alpha = alpha;
            refreshShaders(radius, baseColor);
            final float angle = LiquidGlassTuning.lightAngleRadians() * 180f / (float) Math.PI
                    + pressed * 17f;
            gradientMatrix.reset();
            gradientMatrix.setRotate(angle, 0, 0);
            gradientMatrix.postTranslate(cx, cy);
            faceGradient.setLocalMatrix(gradientMatrix);
            rimGradient.setLocalMatrix(gradientMatrix);

            face.setAlpha(Math.round(255 * alpha));
            canvas.drawCircle(cx, cy, radius, face);

            rim.setStrokeWidth(AndroidUtilities.dpf2(1.05f));
            rim.setAlpha(Math.round(255 * alpha));
            canvas.drawCircle(cx, cy, radius - AndroidUtilities.dpf2(.65f), rim);

            final float shine = LiquidGlassTuning.shine();
            if (shine > .01f) {
                glint.setColor(0xFFFFFFFF);
                glint.setAlpha(Math.round(Math.min(255, 210 * shine * alpha)));
                glint.setStrokeWidth(AndroidUtilities.dpf2(1.4f + pressed * .9f));
                final float inset = AndroidUtilities.dpf2(2.0f);
                arcRect.set(cx - radius + inset, cy - radius + inset,
                        cx + radius - inset, cy + radius - inset);
                canvas.drawArc(arcRect, angle - 142f + pressed * 26f, 76f, false, glint);
            }
        }

        /** For circles already filled by Telegram's sampled VoIP gradient. */
        public void drawRim(Canvas canvas, float cx, float cy, float radius, float pressed) {
            if (!LiquidGlassTuning.isActive() || radius <= 0f) return;
            refreshShaders(radius, Color.WHITE);
            final float angle = LiquidGlassTuning.lightAngleRadians() * 180f / (float) Math.PI
                    + pressed * 17f;
            gradientMatrix.reset();
            gradientMatrix.setRotate(angle, 0, 0);
            gradientMatrix.postTranslate(cx, cy);
            rimGradient.setLocalMatrix(gradientMatrix);
            rim.setStrokeWidth(AndroidUtilities.dpf2(1.15f));
            rim.setAlpha(255);
            canvas.drawCircle(cx, cy, radius - AndroidUtilities.dpf2(.7f), rim);
            final float shine = LiquidGlassTuning.shine();
            if (shine > .01f) {
                glint.setColor(0xFFFFFFFF);
                glint.setAlpha(Math.round(Math.min(255, 186 * shine)));
                glint.setStrokeWidth(AndroidUtilities.dpf2(1.6f));
                arcRect.set(cx - radius + AndroidUtilities.dpf2(2f),
                        cy - radius + AndroidUtilities.dpf2(2f),
                        cx + radius - AndroidUtilities.dpf2(2f),
                        cy + radius - AndroidUtilities.dpf2(2f));
                canvas.drawArc(arcRect, angle - 142f + pressed * 26f, 76f, false, glint);
            }
        }

        private void refreshShaders(float radius, int baseColor) {
            if (faceGradient != null && Math.abs(cachedRadius - radius) < .5f && cachedColor == baseColor) return;
            cachedRadius = radius;
            cachedColor = baseColor;
            final boolean warning = Color.red(baseColor) > Color.green(baseColor) * 1.5f
                    && Color.red(baseColor) > Color.blue(baseColor) * 1.5f;
            final int bodyAlpha = warning ? 214 : 135;
            final int top = ColorUtils.setAlphaComponent(
                    ColorUtils.blendARGB(baseColor, Color.WHITE, warning ? .19f : .24f), bodyAlpha);
            final int bottom = ColorUtils.setAlphaComponent(
                    ColorUtils.blendARGB(baseColor, Color.BLACK, .10f), warning ? 193 : 107);
            faceGradient = new LinearGradient(-radius, -radius, radius, radius,
                    top, bottom, Shader.TileMode.CLAMP);
            rimGradient = new LinearGradient(-radius, -radius, radius, radius,
                    0xF0FFFFFF, 0x29FFFFFF, Shader.TileMode.CLAMP);
            face.setShader(faceGradient);
            rim.setShader(rimGradient);
        }
    }

    /** Optional glass background for every icon at the top of a private call. */
    public static final class IconBackground extends Drawable {
        private final Painter painter = new Painter();
        private float press;
        private boolean targetPressed;
        private ValueAnimator pressAnimator;
        private int drawableAlpha = 255;
        private final VoIPBackgroundProvider callBackground;
        private final int[] windowPosition = new int[2];

        public IconBackground(VoIPBackgroundProvider callBackground) {
            this.callBackground = callBackground;
        }

        @Override
        public boolean isStateful() {
            return true;
        }

        @Override
        protected boolean onStateChange(int[] state) {
            boolean pressed = false;
            for (int value : state) {
                if (value == android.R.attr.state_pressed) { pressed = true; break; }
            }
            if (pressed == targetPressed) return false;
            targetPressed = pressed;
            if (pressAnimator != null) pressAnimator.cancel();
            if (!LiquidGlassTuning.motionEnabled()
                    || (Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled())) {
                press = 0f;
                invalidateSelf();
                return true;
            }
            pressAnimator = ValueAnimator.ofFloat(press, pressed ? 1f : 0f);
            pressAnimator.setDuration(pressed ? 145 : 250);
            pressAnimator.addUpdateListener(a -> {
                press = (float) a.getAnimatedValue();
                invalidateSelf();
            });
            pressAnimator.start();
            return true;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect b = getBounds();
            if (b.isEmpty()) return;
            final float cx = b.exactCenterX(), cy = b.exactCenterY();
            final float radius = Math.min(b.width(), b.height()) / 2f - AndroidUtilities.dpf2(2f);
            canvas.save();
            if (LiquidGlassTuning.motionEnabled()) {
                canvas.scale(1f + press * .075f, 1f - press * .085f, cx, cy);
            }
            // Use the call's own animated gradient capture as a backdrop,
            // rather than an unrelated solid circle. We only sample the
            // existing bitmap shader and do not recapture the entire screen.
            if (callBackground != null && callBackground.getTotalWidth() > 0
                    && callBackground.getTotalHeight() > 0 && getCallback() instanceof View) {
                View owner = (View) getCallback();
                owner.getLocationInWindow(windowPosition);
                callBackground.setLightTranslation(windowPosition[0], windowPosition[1]);
                canvas.drawCircle(cx, cy, radius, callBackground.getLightPaint());
                if (callBackground.isReveal()) {
                    canvas.drawCircle(cx, cy, radius, callBackground.getRevealPaint());
                }
            }
            painter.drawFace(canvas, cx, cy, radius, Color.WHITE, press, drawableAlpha / 255f);
            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) {
            drawableAlpha = alpha;
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter filter) {}

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
