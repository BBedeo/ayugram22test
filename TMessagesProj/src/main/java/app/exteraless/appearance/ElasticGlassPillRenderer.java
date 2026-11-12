package app.exteraless.appearance;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;

/**
 * Visible, refractive selection capsule for the bottom navigation. It draws
 * one shared pill under the tab icons, instead of low-alpha separate MD3 cells.
 *
 * The native drawable samples the same underlying page as the main tabs glass.
 * The lightweight specular coat adds a clear highlight even when Android
 * disables the GPU refraction or the user has Liquid Glass switched off.
 * There are no per-frame bitmap captures or additional touch interceptors.
 */
public final class ElasticGlassPillRenderer {
    private final Paint coat = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outerRim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerRim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint topGlint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bottomShade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF inset = new RectF();
    private final RectF arc = new RectF();
    private final float stroke;
    private final float insetSize;
    private LinearGradient cachedCoat;
    private float cachedTop = Float.NaN;
    private float cachedBottom = Float.NaN;
    private boolean cachedDark;
    private float radiusCache = -1;

    public ElasticGlassPillRenderer() {
        stroke = AndroidUtilities.dpf2(1.15f);
        insetSize = AndroidUtilities.dpf2(1.8f);
        outerRim.setStyle(Paint.Style.STROKE);
        outerRim.setStrokeWidth(stroke);
        innerRim.setStyle(Paint.Style.STROKE);
        innerRim.setStrokeWidth(AndroidUtilities.dpf2(.55f));
        topGlint.setStyle(Paint.Style.STROKE);
        topGlint.setStrokeWidth(AndroidUtilities.dpf2(2.0f));
        topGlint.setStrokeCap(Paint.Cap.ROUND);
        bottomShade.setStyle(Paint.Style.STROKE);
        bottomShade.setStrokeWidth(AndroidUtilities.dpf2(1.3f));
    }

    public void draw(Canvas canvas, RectF rect, BlurredBackgroundDrawable nativeGlass,
                     boolean dark, float stretch) {
        if (rect.isEmpty() || rect.width() < 2f || rect.height() < 2f) return;
        final float radius = rect.height() / 2f;

        if (nativeGlass != null) {
            nativeGlass.setBounds(Math.round(rect.left), Math.round(rect.top),
                    Math.round(rect.right), Math.round(rect.bottom));
            if (radiusCache != radius) {
                radiusCache = radius;
                nativeGlass.setRadius(radius);
            }
            nativeGlass.draw(canvas);
        }
        if (cachedCoat == null || cachedTop != rect.top || cachedBottom != rect.bottom
                || cachedDark != dark) {
            cachedTop = rect.top;
            cachedBottom = rect.bottom;
            cachedDark = dark;
            cachedCoat = new LinearGradient(0f, rect.top, 0f, rect.bottom,
                    dark ? new int[]{0x53FFFFFF, 0x1CFFFFFF, 0x07FFFFFF, 0x24FFFFFF}
                         : new int[]{0xA6FFFFFF, 0x4BFFFFFF, 0x1AFFFFFF, 0x50FFFFFF},
                    new float[]{0f, .27f, .70f, 1f}, Shader.TileMode.CLAMP);
        }
        coat.setShader(cachedCoat);
        canvas.drawRoundRect(rect, radius, radius, coat);
        coat.setShader(null);

        // Outer optical edge and an inner return reflection establish real
        // glass thickness rather than merely a selected-tab color wash.
        inset.set(rect);
        inset.inset(stroke / 2f, stroke / 2f);
        outerRim.setColor(dark ? 0xA6F7FBFF : 0xC9FFFFFF);
        canvas.drawRoundRect(inset, Math.max(0, radius - stroke / 2f),
                Math.max(0, radius - stroke / 2f), outerRim);
        inset.set(rect);
        inset.inset(insetSize, insetSize);
        innerRim.setColor(dark ? 0x4AFFFFFF : 0x8CFFFFFF);
        canvas.drawRoundRect(inset, Math.max(0, radius - insetSize),
                Math.max(0, radius - insetSize), innerRim);

        // Specular glint follows the upper hemisphere; it deliberately does
        // not affect tab icons, labels, or their accessibility hit targets.
        arc.set(rect);
        arc.inset(insetSize * 1.55f, insetSize * 1.55f);
        topGlint.setColor(dark ? 0xC7FFFFFF : 0xDFFFFFFF);
        canvas.drawArc(arc, 199f, 142f, false, topGlint);

        bottomShade.setColor(dark ? 0x35000000 : 0x16091C33);
        canvas.drawArc(arc, 19f, 142f, false, bottomShade);

        // During a tug, a thin secondary rim hints at the stretched liquid
        // bridge without continuously animating a separate view.
        if (stretch > .01f) {
            innerRim.setColor(Color.argb(Math.round(42f * Math.min(1f, stretch)),
                    225, 245, 255));
            inset.set(rect);
            inset.inset(AndroidUtilities.dpf2(3), AndroidUtilities.dpf2(3));
            canvas.drawRoundRect(inset, inset.height() / 2f, inset.height() / 2f, innerRim);
        }
    }
}
