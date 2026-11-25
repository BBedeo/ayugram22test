package app.exteraless.appearance.glass;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.Shader;
import android.os.Build;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A Liquid Glass surface for places that have no backdrop capture (toasts, hints, dialogs in their own
 * window): a translucent tier fill of the element's own color, a 1px rim whose top-left catches the
 * light and whose bottom-right falls into shade, a polished inner highlight just under the top edge,
 * and an optional soft ambient shadow. It never blurs or samples the backdrop, so it is cheap and
 * never stacks a second blur layer.
 */
public class GlassSurfaceDrawable extends Drawable {

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path clipPath = new Path();
    private final int tier;
    private int baseColor;
    private float radiusDp;
    private boolean shadow;
    private int alpha = 255;
    private float fillAlphaOverride = -1f;
    private boolean topOnly;

    public GlassSurfaceDrawable(int baseColor, int tier, float radiusDp) {
        this.tier = tier;
        this.radiusDp = radiusDp;
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(Math.max(1f, dpf2(0.9f)));
        highlightPaint.setStyle(Paint.Style.STROKE);
        highlightPaint.setStrokeWidth(Math.max(1f, dpf2(1f)));
        shadow = tier == GlassTokens.TIER_THICK;
        setBaseColor(baseColor);
    }

    public GlassSurfaceDrawable setShadow(boolean shadow) {
        this.shadow = shadow;
        invalidateSelf();
        return this;
    }

    /** Rounds only the top corners (a bottom sheet whose lower edge sits on the screen edge). */
    public GlassSurfaceDrawable setTopCornersOnly(boolean value) {
        topOnly = value;
        invalidateSelf();
        return this;
    }

    public GlassSurfaceDrawable setRadius(float radiusDp) {
        this.radiusDp = radiusDp;
        invalidateSelf();
        return this;
    }

    /**
     * Overrides the tier's fill opacity. Only for surfaces that sit on a real backdrop blur (a dialog
     * window with native background blur), where the tier's no-blur density would hide the blur.
     */
    public GlassSurfaceDrawable setFillAlpha(float alpha) {
        fillAlphaOverride = Math.max(0f, Math.min(1f, alpha));
        setBaseColor(baseColor);
        return this;
    }

    private int fillColor() {
        if (fillAlphaOverride >= 0f) {
            return androidx.core.graphics.ColorUtils.setAlphaComponent(baseColor | 0xFF000000, Math.round(255 * fillAlphaOverride));
        }
        return GlassTokens.fill(baseColor, tier);
    }

    public float getRadiusDp() {
        return radiusDp;
    }

    public void setBaseColor(int color) {
        baseColor = color;
        fillPaint.setColor(fillColor());
        shadowPaint.setColor(GlassTokens.ambientShadow(GlassTokens.isDark()));
        updateShaders(getBounds());
        invalidateSelf();
    }

    public int getBaseColor() {
        return baseColor;
    }

    private float radiusPx(RectF r) {
        if (radiusDp < 0) {
            return Math.min(r.width(), r.height()) / 2f;
        }
        return Math.min(dp(radiusDp), Math.min(r.width(), r.height()) / 2f);
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        super.onBoundsChange(bounds);
        updateShaders(bounds);
    }

    private void updateShaders(Rect b) {
        if (b.isEmpty()) {
            return;
        }
        // Light from the top-left: the rim is bright there and fades into shade at the bottom-right. A dark surface also
        // catches the light again at the bottom-right, like the lens' rim (lit at both diagonal ends).
        // a true 45deg axis whose end projects onto the far corner: the catchlights sit in the corners whatever the aspect
        // (a corner-to-corner axis of a wide capsule is near-horizontal and made the rim lit on the vertical ends)
        final float diag = (b.width() + b.height()) / 2f;
        final int light = GlassTokens.rimLight(baseColor);
        final int shade = GlassTokens.rimShade(baseColor);
        final int opposite = GlassTokens.rimOpposite(baseColor);
        if (opposite == shade) {
            rimPaint.setShader(new LinearGradient(b.left, b.top, b.left + diag, b.top + diag,
                    new int[]{light, shade, shade},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        } else {
            rimPaint.setShader(new LinearGradient(b.left, b.top, b.left + diag, b.top + diag,
                    new int[]{light, shade, shade, opposite},
                    new float[]{0f, 0.40f, 0.65f, 1f}, Shader.TileMode.CLAMP));
        }
        // The bevel highlight lives only in the top third of the surface.
        final float h = Math.max(1, Math.min(b.height(), dp(28)));
        highlightPaint.setShader(new LinearGradient(0, b.top, 0, b.top + h,
                new int[]{GlassTokens.innerHighlight(baseColor), 0x00FFFFFF},
                null, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final Rect b = getBounds();
        if (b.isEmpty()) {
            return;
        }
        setArea(b);
        final float r = radiusPx(rect);
        final int a = alpha;
        final int clipSave = topOnly ? canvas.save() : -1;
        if (topOnly) {
            // The lower corners are pushed below the bounds and clipped away.
            canvas.clipRect(b);
        }
        if (shadow && !topOnly) {
            // The shadow is drawn only outside the surface: under a translucent fill it would darken it.
            final int shadowColor = GlassTokens.ambientShadow(GlassTokens.isDark());
            shadowPaint.setColor(shadowColor);
            shadowPaint.setAlpha(Math.round(Color_alpha(shadowColor) * a / 255f));
            clipPath.rewind();
            clipPath.addRoundRect(rect, r, r, Path.Direction.CW);
            canvas.save();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                canvas.clipOutPath(clipPath);
            } else {
                canvas.clipPath(clipPath, Region.Op.DIFFERENCE);
            }
            rect.offset(0, dpf2(1.5f));
            rect.inset(-dpf2(0.5f), -dpf2(0.5f));
            canvas.drawRoundRect(rect, r, r, shadowPaint);
            canvas.restore();
            setArea(b);
        }
        fillPaint.setAlpha(Math.round(Color_alpha(fillColor()) * a / 255f));
        if (fillPaint.getAlpha() > 0) {
            canvas.drawRoundRect(rect, r, r, fillPaint);
        }

        final float half = rimPaint.getStrokeWidth() / 2f;
        // one soft hairline: a second bright line inside it reads as a cheap double border
        rimPaint.setAlpha(Math.round(a * 0.6f));
        rect.inset(half, half);
        canvas.drawRoundRect(rect, r - half, r - half, rimPaint);
        if (clipSave != -1) {
            canvas.restoreToCount(clipSave);
        }
    }

    private void setArea(Rect b) {
        rect.set(b);
        if (topOnly) {
            rect.bottom += Math.max(dp(40), radiusDp < 0 ? b.height() : dp(radiusDp)) * 2;
        }
    }

    private static int Color_alpha(int color) {
        return color >>> 24;
    }

    @Override
    public void getOutline(@NonNull Outline outline) {
        final Rect b = getBounds();
        rect.set(b);
        outline.setRoundRect(b, radiusPx(rect));
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        fillPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
