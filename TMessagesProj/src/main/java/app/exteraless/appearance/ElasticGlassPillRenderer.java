package app.exteraless.appearance;

import android.graphics.Canvas;
import android.graphics.RectF;

import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;

/**
 * Draws the travelling bottom-navigation lens using Telegram's real captured
 * glass source. Do not paint a second fake rim or gradient on top: the native
 * LiquidGlassEffect is responsible for refraction, shine and the optical edge.
 */
public final class ElasticGlassPillRenderer {
    private float lastRadius = -1f;

    public void draw(Canvas canvas, RectF bounds, BlurredBackgroundDrawable nativeGlass,
                     boolean dark, float stretching) {
        if (bounds.width() < 2f || bounds.height() < 2f || nativeGlass == null) return;

        final float radius = bounds.height() * .5f;
        nativeGlass.setBounds(Math.round(bounds.left), Math.round(bounds.top),
                Math.round(bounds.right), Math.round(bounds.bottom));
        if (Math.abs(lastRadius - radius) > .2f) {
            lastRadius = radius;
            nativeGlass.setRadius(radius);
        }

        // The forced pill lens must keep its own Fresnel lighting even when
        // universal Liquid Glass is off. A small directional angle/phase shift
        // follows the stretch, so the highlight reacts to motion instead of
        // looking painted onto a rigid dark oval.
        final float clampedStretch = Math.max(-1f, Math.min(1f, stretching));
        final float baseAngle = 315f;
        final float angle = (float) Math.toRadians(baseAngle + clampedStretch * 9f);
        final float shine = dark ? .64f : .72f;
        nativeGlass.setLiquidOptics(shine, angle, clampedStretch * .24f);

        nativeGlass.draw(canvas);
    }
}
