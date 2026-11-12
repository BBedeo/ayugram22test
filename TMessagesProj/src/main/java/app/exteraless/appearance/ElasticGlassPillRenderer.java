package app.exteraless.appearance;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;

/**
 * Minimal optical coating over Telegram's native glass backdrop.
 *
 * No bitmap sprites, no opaque imitation overlay, and no bright painted arcs.
 * Real refraction and blur are supplied by the shared page capture underneath
 * the navigation bar, while this painter only supplies a soft edge reflection.
 */
public final class ElasticGlassPillRenderer {
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint insideEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF inside = new RectF();
    private LinearGradient sheenGradient, rimGradient;
    private float lastTop = Float.NaN, lastBottom = Float.NaN;
    private boolean lastDark;
    private float lastRadius = -1f;
    private final float stroke = AndroidUtilities.dpf2(.85f);
    private final float inset = AndroidUtilities.dpf2(1.1f);

    public ElasticGlassPillRenderer() {
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(stroke);
        insideEdge.setStyle(Paint.Style.STROKE);
        insideEdge.setStrokeWidth(AndroidUtilities.dpf2(.45f));
    }

    public void draw(Canvas canvas, RectF bounds, BlurredBackgroundDrawable nativeGlass,
                     boolean dark, float stretching) {
        if (bounds.width() < 2f || bounds.height() < 2f) return;
        final float radius = bounds.height() * .5f;

        // The pill reuses live source pixels: it never replaces the existing
        // glass on the whole navigation bar, and never covers the tab icons.
        if (nativeGlass != null) {
            nativeGlass.setBounds(Math.round(bounds.left), Math.round(bounds.top),
                    Math.round(bounds.right), Math.round(bounds.bottom));
            if (Math.abs(lastRadius - radius) > .2f) {
                lastRadius = radius;
                nativeGlass.setRadius(radius);
            }
            nativeGlass.draw(canvas);
        }

        if (sheenGradient == null || bounds.top != lastTop || bounds.bottom != lastBottom
                || dark != lastDark) {
            lastTop = bounds.top;
            lastBottom = bounds.bottom;
            lastDark = dark;
            sheenGradient = new LinearGradient(0f, bounds.top, 0f, bounds.bottom,
                    dark ? new int[]{0x22FFFFFF, 0x06FFFFFF, 0x00FFFFFF, 0x0BFFFFFF}
                         : new int[]{0x3BFFFFFF, 0x0FFFFFFF, 0x00FFFFFF, 0x12FFFFFF},
                    new float[]{0f, .23f, .64f, 1f}, Shader.TileMode.CLAMP);
            rimGradient = new LinearGradient(0f, bounds.top, 0f, bounds.bottom,
                    dark ? new int[]{0x7FFFFFFF, 0x30FFFFFF, 0x1FFFFFFF}
                         : new int[]{0xABFFFFFF, 0x42FFFFFF, 0x28FFFFFF},
                    new float[]{0f, .46f, 1f}, Shader.TileMode.CLAMP);
            sheen.setShader(sheenGradient);
            rim.setShader(rimGradient);
        }

        // Quiet, uniform rounded reflection; no fake PNG-like white arcs.
        canvas.drawRoundRect(bounds, radius, radius, sheen);
        inside.set(bounds);
        inside.inset(stroke * .5f, stroke * .5f);
        canvas.drawRoundRect(inside, inside.height() * .5f, inside.height() * .5f, rim);
        inside.inset(inset, inset);
        insideEdge.setColor(dark ? 0x1AFFFFFF : 0x33FFFFFF);
        insideEdge.setAlpha(Math.round((.60f + .25f * Math.min(1f, stretching)) * 255f));
        canvas.drawRoundRect(inside, inside.height() * .5f, inside.height() * .5f, insideEdge);
    }
}
