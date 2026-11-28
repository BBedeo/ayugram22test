package app.exteraless.appearance.glass;

/** The formulas of Glass Touch, free of Android classes so scripts/check_glass_touch.py can run them. */
public final class GlassTouchMath {

    private GlassTouchMath() {
    }

    /** Strength of the back glass while {@code q} (0..1) of the previous screen is uncovered. */
    public static float backStrength(float q) {
        q = Math.max(0f, Math.min(1f, q));
        if (q < 0.55f) {
            return 1f - 0.4f * q;
        }
        final float t = (1f - q) / 0.45f;
        return 0.78f * t * t;
    }

    /** Strength of the glass on a page that arrives while it still sits {@code shift} px aside. */
    public static float pageStrength(float shift, float width) {
        return width <= 0 ? 0f : 0.7f * Math.min(Math.abs(shift) / width, 1f);
    }

    /** A veil clearing by time, from {@code start} down to zero. */
    public static float fadeStrength(float start, float elapsedMs, float durationMs) {
        final float left = 1f - Math.max(0f, Math.min(1f, elapsedMs / durationMs));
        return start * left * left;
    }

    /** A veil that swells to {@code peak} and clears again, easing at both ends. */
    public static float pulseStrength(float peak, float elapsedMs, float riseMs, float durationMs) {
        return elapsedMs < riseMs ? peak * smooth(elapsedMs / riseMs)
                : peak * (1f - smooth((elapsedMs - riseMs) / (durationMs - riseMs)));
    }

    private static float smooth(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    /** Scale of a held row: 9% at most, and never more than 16dp off the longer side. */
    public static float squeezeScale(float longSidePx, float density) {
        return 1f - Math.min(0.09f, 16f * density / longSidePx);
    }

    /** Radius of the touch lens: half the longer side, held between 48dp and 96dp. */
    public static float lensRadius(float width, float height, float density) {
        return Math.max(48f * density, Math.min(96f * density, Math.max(width, height) * 0.5f));
    }
}
