package app.exteraless.appearance.glass;

import android.animation.TimeInterpolator;
import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/**
 * Spring physics of the Liquid Glass language: no linear or plain ease curves on glass surfaces.
 *
 * <ul>
 *     <li>{@link #SPRING} / {@link #SPRING_SOFT}: damped-spring interpolators for ValueAnimator /
 *     ViewPropertyAnimator based transitions (sheets, toasts, indicators);</li>
 *     <li>{@link #attachPress(View)}: tactile press — the view sinks to {@link GlassTokens#PRESS_SCALE}
 *     on a spring and springs back with a small overshoot on release. It never consumes touches.</li>
 * </ul>
 * Motion follows the "Motion" switch of the Liquid Glass settings ({@link GlassTokens#motionEnabled()}).
 */
public final class GlassMotion {

    /** Snappy spring: ~3% overshoot, settles in about 60% of the duration. Use 380–450ms. */
    public static final TimeInterpolator SPRING = new SpringInterpolator(0.72f, 3.2f);
    /** Soft spring for large surfaces (sheets, dialogs): ~1.5% overshoot. Use 420–520ms. */
    public static final TimeInterpolator SPRING_SOFT = new SpringInterpolator(0.82f, 2.7f);

    public static final float PRESS_STIFFNESS = 1100f;
    public static final float RELEASE_STIFFNESS = 520f;
    public static final float RELEASE_DAMPING = 0.55f;

    private GlassMotion() {
    }

    /**
     * Damped harmonic oscillator normalised to reach 1 at t = 1:
     * x(t) = 1 - e^(-ζ·ω·t) · (cos(ωd·t) + ζ/√(1-ζ²) · sin(ωd·t)).
     */
    public static final class SpringInterpolator implements TimeInterpolator {
        private final float zeta;
        private final float omega;
        private final float omegaD;
        private final float end;

        /**
         * @param dampingRatio ζ, 0 &lt; ζ &lt; 1 (lower bounces more)
         * @param cycles       how many radians·π of natural frequency fit into the duration
         */
        public SpringInterpolator(float dampingRatio, float cycles) {
            zeta = Math.max(0.05f, Math.min(0.99f, dampingRatio));
            omega = (float) (cycles * Math.PI);
            omegaD = (float) (omega * Math.sqrt(1 - zeta * zeta));
            end = raw(1f);
        }

        private float raw(float t) {
            final double decay = Math.exp(-zeta * omega * t);
            return (float) (1 - decay * (Math.cos(omegaD * t) + zeta / Math.sqrt(1 - zeta * zeta) * Math.sin(omegaD * t)));
        }

        @Override
        public float getInterpolation(float t) {
            if (t <= 0f) {
                return 0f;
            }
            if (t >= 1f) {
                return 1f;
            }
            // Normalise so the curve lands exactly on 1 at the end of the animation.
            return raw(t) / end;
        }
    }

    /**
     * Adds the tactile glass press to {@code view} (scale on springs). It installs the view's touch
     * listener (returning false, so clicks still work): use it on views without their own listener.
     */
    @SuppressLint("ClickableViewAccessibility")
    public static void attachPress(View view) {
        attachPress(view, GlassTokens.PRESS_SCALE);
    }

    @SuppressLint("ClickableViewAccessibility")
    public static void attachPress(View view, float pressedScale) {
        final SpringAnimation sx = new SpringAnimation(view, DynamicAnimation.SCALE_X, 1f);
        final SpringAnimation sy = new SpringAnimation(view, DynamicAnimation.SCALE_Y, 1f);
        view.setOnTouchListener((v, event) -> {
            // the gate guards only the press: release must always spring back, or a view stays shrunk
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (!GlassTokens.motionEnabled() || !v.isEnabled()) {
                        return false;
                    }
                    spring(sx, pressedScale, PRESS_STIFFNESS, SpringForce.DAMPING_RATIO_NO_BOUNCY);
                    spring(sy, pressedScale, PRESS_STIFFNESS, SpringForce.DAMPING_RATIO_NO_BOUNCY);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    spring(sx, 1f, RELEASE_STIFFNESS, RELEASE_DAMPING);
                    spring(sy, 1f, RELEASE_STIFFNESS, RELEASE_DAMPING);
                    break;
            }
            return false;
        });
    }

    static void spring(SpringAnimation animation, float target, float stiffness, float damping) {
        SpringForce force = animation.getSpring();
        if (force == null) {
            force = new SpringForce(target);
            animation.setSpring(force);
        }
        force.setFinalPosition(target);
        force.setStiffness(stiffness);
        force.setDampingRatio(damping);
        animation.start();
    }
}
