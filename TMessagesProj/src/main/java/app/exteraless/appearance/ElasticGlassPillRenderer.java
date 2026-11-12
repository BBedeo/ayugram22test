package app.exteraless.appearance;

import android.graphics.Canvas;
import android.graphics.RectF;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;

/**
 * Single travelling native GPU lens behind the real tab icons.
 * Its dedicated AGSL shader samples the captured page, bends edge pixels,
 * disperses colour and shades a directional glass reflection.
 * No drawn white outline or synthetic opaque oval.
 */
public final class ElasticGlassPillRenderer {
    private float lastRadius = -1f;
    private float lastCenterX = Float.NaN;
    private float lightVelocity;
    private float opticalPress;
    private float tiltRadians;
    private float lastPhase;

    public void setTiltOffset(float radians) {
        tiltRadians = Math.max(-.35f, Math.min(.35f, radians));
    }

    public float getOpticalPress() {
        return opticalPress;
    }

    public float getLightPhase() {
        return lastPhase;
    }

    public void draw(Canvas canvas, RectF bounds, BlurredBackgroundDrawable nativeGlass,
                     boolean dark, float stretching, boolean pressed) {
        if (bounds.width() < 2f || bounds.height() < 2f || nativeGlass == null) return;

        final float radius = bounds.height() * .5f;
        nativeGlass.setBounds(Math.round(bounds.left), Math.round(bounds.top),
                Math.round(bounds.right), Math.round(bounds.bottom));
        if (Math.abs(lastRadius - radius) > .2f) {
            lastRadius = radius;
            nativeGlass.setRadius(radius);
        }

        // Optical motion tracks actual lens displacement per frame; no timer,
        // looping animation or permanent redraw when the tab is at rest.
        final float centerX = bounds.centerX();
        final float delta = Float.isNaN(lastCenterX) ? 0f : centerX - lastCenterX;
        lastCenterX = centerX;
        final float velocity = Math.max(-1f, Math.min(1f,
                delta / Math.max(1f, AndroidUtilities.dp(7))));
        final float target = velocity + Math.max(-.10f, Math.min(.10f, stretching * .10f));
        final float smoothing = Math.abs(target) > Math.abs(lightVelocity) ? .46f : .23f;
        lightVelocity += (target - lightVelocity) * smoothing;
        if (Math.abs(lightVelocity) < .018f && Math.abs(target) < .018f) {
            lightVelocity = 0f;
        }
        // Quantize the phase to avoid tiny float updates keeping the GPU awake.
        final float phase = Math.round(Math.max(-1f, Math.min(1f, lightVelocity)) * 100f) / 100f;
        lastPhase = phase;

        // The highlight is part of the optical material, not a 2D stroked rim.
        final float baseAngle = 315f;
        final float angle = (float) Math.toRadians(baseAngle + phase * 24f) + tiltRadians;
        final float shine = dark ? .86f : .92f;
        nativeGlass.setLiquidOptics(shine, angle, phase);

        // A stationary finger still compresses the optical surface; both
        // down and release ease over several native frames. No extra overlay
        // View is added and long-press remains the original tab listener.
        final float targetPress = pressed ? 1f : 0f;
        opticalPress += (targetPress - opticalPress) * (pressed ? .30f : .22f);
        if (Math.abs(targetPress - opticalPress) < .018f) {
            opticalPress = targetPress;
        }
        final float pressure = Math.round(opticalPress * 100f) / 100f;
        nativeGlass.setElasticPressProgress(pressure);

        nativeGlass.draw(canvas);
    }
}
