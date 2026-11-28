package app.exteraless.appearance.glass;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

import org.telegram.messenger.AndroidUtilities;

import app.exteraless.appearance.LiquidGlassTuning;

/**
 * The hairline rim of every Liquid Glass surface that is drawn by hand (cards, tabs, round buttons), shaped like the
 * Fresnel catchlights of the lens shader on the header buttons: bright at the top-left and at the bottom-right edge,
 * almost gone along the sides. One look for all surfaces, so the profile does not mix several kinds of highlights.
 */
public final class GlassRim {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final Path path = new Path();
    private final float[] radii = new float[8];
    private int shaderColor;
    private LinearGradient unit;

    public GlassRim() {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, AndroidUtilities.dpf2(0.9f)));
    }

    /** Peak alpha of the catchlights: faint on dark surfaces, where a bright rim reads as a chalky outline. */
    private static int peak(boolean dark) {
        final float shine = 0.6f + 0.4f * Math.min(1f, LiquidGlassTuning.shine());
        return Math.round((dark ? 0x48 : 0xB0) * shine);
    }

    /** Strokes the rim just inside {@code rect}; the radii are the corner radii of the surface in px. */
    public void draw(Canvas canvas, RectF rect, float topRadius, float bottomRadius, float alpha) {
        if (rect.width() <= 0 || rect.height() <= 0 || alpha <= 0f) {
            return;
        }
        shade(rect, alpha);

        final float half = paint.getStrokeWidth() / 2f;
        final float max = Math.min(rect.width(), rect.height()) / 2f;
        final float rt = Math.max(0f, Math.min(topRadius, max) - half);
        final float rb = Math.max(0f, Math.min(bottomRadius, max) - half);
        radii[0] = radii[1] = radii[2] = radii[3] = rt;
        radii[4] = radii[5] = radii[6] = radii[7] = rb;
        path.rewind();
        path.addRoundRect(rect.left + half, rect.top + half, rect.right - half, rect.bottom - half, radii, Path.Direction.CW);
        canvas.drawPath(path, paint);
    }

    /** The same rim along any outline, a message bubble for one; {@code bounds} encloses it. */
    public void draw(Canvas canvas, Path outline, RectF bounds, float alpha) {
        if (bounds.width() <= 0 || bounds.height() <= 0 || alpha <= 0f) {
            return;
        }
        shade(bounds, alpha);
        canvas.drawPath(outline, paint);
    }

    private void shade(RectF rect, float alpha) {
        final int peak = peak(GlassTokens.isDark());
        final int light = Color.argb(peak, 255, 255, 255);
        if (unit == null || shaderColor != light) {
            shaderColor = light;
            // diagonal, light at both ends: the opposite-side catchlights of the lens
            unit = new LinearGradient(0, 0, 1, 1,
                    new int[]{light, 0x00FFFFFF, 0x00FFFFFF, light},
                    new float[]{0f, 0.30f, 0.70f, 1f}, Shader.TileMode.CLAMP);
            paint.setShader(unit);
        }
        // 45deg axis (see GlassSurfaceDrawable): corners lit at the same angle as on the lens, for any aspect
        final float diag = (rect.width() + rect.height()) / 2f;
        matrix.setScale(diag, diag);
        matrix.postTranslate(rect.left, rect.top);
        unit.setLocalMatrix(matrix);
        paint.setAlpha(Math.round(255 * Math.max(0f, Math.min(1f, alpha))));
    }
}
