package app.exteraless.appearance;

import android.os.Build;
import android.graphics.Color;
import org.telegram.messenger.LiteMode;

/** Central opt-in policy for Telegram's native glass renderer, never a fake overlay. */
public final class LiquidGlassTuning {
    private LiquidGlassTuning() {}

    /** Blurred fallback from Android 12; shader refraction on Android 13+. */
    public static boolean isActive() {
        return AppearanceConfig.universalLiquidGlass()
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && !LiteMode.isPowerSaverApplied();
    }

    /** The effective style participates in GlassEngine's invalidation state. */
    public static int material() {
        return isActive() ? AppearanceConfig.liquidGlassMaterial() : -1;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(v, hi));
    }

    public static boolean motionEnabled() {
        return isActive() && AppearanceConfig.liquidGlassMotion.Bool()
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
    }

    public static boolean adaptiveLightEnabled() {
        return isActive() && AppearanceConfig.liquidGlassAdaptive.Bool();
    }

    public static float blurScale() {
        return isActive() ? clamp(AppearanceConfig.liquidGlassBlur.Int() / 60f, 0f, 1.7f) : 1f;
    }

    public static int thicknessDp() {
        return Math.max(3, Math.min(24, AppearanceConfig.liquidGlassThickness.Int()));
    }

    /** Floating header pills need deeper bevel profiles than flat list chrome. */
    public static int thicknessDp(boolean headerLens) {
        final int base = thicknessDp();
        if (!headerLens) {
            return base;
        }
        final int bonus;
        switch (AppearanceConfig.liquidGlassMaterial()) {
            case 0: bonus = 2; break;
            case 2: bonus = 6; break;
            default: bonus = 4; break;
        }
        return Math.min(24, base + bonus);
    }

    public static float shine() {
        if (!isActive()) return 0f;
        return clamp(AppearanceConfig.liquidGlassHighlight.Int() / 100f, 0f, 1f);
    }

    /** Stronger passive rim specular for floating header pills, scaling with material and Highlight. */
    public static float headerShine() {
        if (!isActive()) return 0.72f;
        final float h = shine();
        if (h <= 0.001f) return 0f;
        final float presetBias;
        switch (AppearanceConfig.liquidGlassMaterial()) {
            case 0: presetBias = 0.08f; break;
            case 2: presetBias = 0.28f; break;
            default: presetBias = 0.18f; break;
        }
        return clamp(h * (0.95f + presetBias) + presetBias * 0.65f, 0f, 1.25f);
    }

    public static float lightAngleRadians() {
        if (!isActive()) return 0f;
        float degrees = AppearanceConfig.liquidGlassLightAngle.Int();
        if (adaptiveLightEnabled()) degrees += 9f;
        return (float) Math.toRadians(degrees);
    }

    /**
     * Tint-aware adaptive material. We do not read pixels synchronously from
     * the GPU; the known theme tint is a cheap and stable lighting estimate.
     */
    public static int adaptTint(int color) {
        if (!adaptiveLightEnabled()) return color;
        float luma = (Color.red(color) * .2126f + Color.green(color) * .7152f
                + Color.blue(color) * .0722f) / 255f;
        float density = .97f + (luma - .5f) * .08f;
        return Color.argb(Math.round(clamp(Color.alpha(color) * density, 0, 255)),
                Color.red(color), Color.green(color), Color.blue(color));
    }

    public static int adaptHighlight(int color) {
        if (!isActive() || color == 0) return color;
        final int r = Color.red(color);
        final int g = Color.green(color);
        final int b = Color.blue(color);
        // Never boost bright white 2D stroke rims into chalky wireframe outlines.
        if (r > 205 && g > 205 && b > 205) {
            return 0;
        }
        float gain = clamp(.72f + shine() * .28f, 0.5f, 1.0f);
        return Color.argb(Math.round(clamp(Color.alpha(color) * gain, 0, 255)), r, g, b);
    }

    /**
     * Sanitizes 2D rim stroke colors when GPU Liquid Glass shader is driving optical edge glint:
     * preserves subtle dark shadow hairlines in light themes while eliminating white wireframe strokes.
     */
    public static int sanitizeRimStroke(int color, boolean hasGpuGlass, boolean isDark) {
        if (color == 0) return 0;
        final int r = Color.red(color);
        final int g = Color.green(color);
        final int b = Color.blue(color);
        final boolean isWhiteStroke = r > 205 && g > 205 && b > 205;
        if (isWhiteStroke && (isDark || hasGpuGlass)) {
            return 0;
        }
        return isActive() ? adaptHighlight(color) : color;
    }

    private static float tuneTransparency(float base, float min) {
        final float factor = 1.45f - clamp(AppearanceConfig.liquidGlassTransparency.Int(), 0, 100) * .009f;
        return clamp(base * factor, min, .94f);
    }

    /** Fast bit-packed identity of the user-visible precision controls. */
    public static long settingsSignature() {
        if (!isActive()) return 0L;
        long v = AppearanceConfig.liquidGlassStrength.Int() & 255L;
        v |= (AppearanceConfig.liquidGlassBlur.Int() & 127L) << 8;
        v |= (AppearanceConfig.liquidGlassTransparency.Int() & 127L) << 15;
        v |= (AppearanceConfig.liquidGlassThickness.Int() & 31L) << 22;
        v |= (AppearanceConfig.liquidGlassHighlight.Int() & 127L) << 27;
        v |= (AppearanceConfig.liquidGlassLightAngle.Int() & 511L) << 34;
        v |= (AppearanceConfig.liquidGlassMotion.Bool() ? 1L : 0L) << 43;
        v |= (AppearanceConfig.liquidGlassAdaptive.Bool() ? 1L : 0L) << 44;
        return v;
    }

    /** Preserve all prior theme colors when the optional effect is disabled. */
    public static float tintAlpha(boolean dark, float original) {
        if (!isActive()) return original;
        final float base;
        switch (AppearanceConfig.liquidGlassMaterial()) {
            case 0: base = dark ? .38f : .29f; break;
            case 2: base = dark ? .61f : .48f; break;
            default: base = dark ? .49f : .39f; break;
        }
        return tuneTransparency(base, dark ? .24f : .20f);
    }

    /** Crystal-clear floating header pill tint scaled by material and Transparency slider. */
    public static float tintAlphaHeader(boolean dark, float original) {
        if (!isActive()) return original;
        final float base;
        switch (AppearanceConfig.liquidGlassMaterial()) {
            case 0: base = dark ? .09f : .18f; break;
            case 2: base = dark ? .20f : .34f; break;
            default: base = dark ? .14f : .26f; break;
        }
        return tuneTransparency(base, dark ? .06f : .14f);
    }

    /** Text-heavy floating surfaces need more opacity than navigation chrome. */
    public static float tintAlphaMenu(boolean dark, float original) {
        if (!isActive()) return original;
        final float base;
        switch (AppearanceConfig.liquidGlassMaterial()) {
            case 0: base = dark ? .63f : .58f; break;
            case 2: base = dark ? .80f : .73f; break;
            default: base = dark ? .71f : .65f; break;
        }
        return tuneTransparency(base, .48f);
    }

    /** Hints and notifications may contain white text over arbitrary imagery. */
    public static float tintAlphaOverlay(boolean dark, float original) {
        if (!isActive()) return original;
        return tuneTransparency(dark ? .83f : .76f, .62f);
    }

    /** Per-material multiplier for Telegram's existing refraction intensity. */
    public static float refractionScale() {
        if (!isActive()) return 1.0f;
        float preset;
        switch (AppearanceConfig.liquidGlassMaterial()) {
            case 0: preset = .72f; break;
            case 2: preset = 1.20f; break;
            default: preset = 1f; break;
        }
        return clamp(preset * AppearanceConfig.liquidGlassStrength.Int() / 75f, 0f, 2.2f);
    }

    /** Boosted Snell lens multiplier for compact floating header pills so settings are distinct. */
    public static float headerRefractionScale() {
        if (!isActive()) return 1.0f;
        final float preset;
        switch (AppearanceConfig.liquidGlassMaterial()) {
            case 0: preset = 1.25f; break;
            case 2: preset = 2.25f; break;
            default: preset = 1.72f; break;
        }
        final float s = AppearanceConfig.liquidGlassStrength.Int() / 75f;
        final float curved = s <= 1f ? s : (1f + (s - 1f) * 1.45f);
        return clamp(preset * curved, 0f, 3.6f);
    }
}
