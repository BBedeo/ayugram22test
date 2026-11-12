package app.exteraless.appearance;

import android.os.Build;
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

    /** Preserve all prior theme colors when the optional effect is disabled. */
    public static float tintAlpha(boolean dark, float original) {
        if (!isActive()) return original;
        switch (AppearanceConfig.liquidGlassMaterial()) {
            case 0: return dark ? 0.38f : 0.29f;
            case 2: return dark ? 0.61f : 0.48f;
            default: return dark ? 0.49f : 0.39f;
        }
    }

    /** Text-heavy floating surfaces need more opacity than navigation chrome. */
    public static float tintAlphaMenu(boolean dark, float original) {
        if (!isActive()) return original;
        switch (AppearanceConfig.liquidGlassMaterial()) {
            case 0: return dark ? 0.63f : 0.58f;
            case 2: return dark ? 0.80f : 0.73f;
            default: return dark ? 0.71f : 0.65f;
        }
    }

    /** Hints and notifications may contain white text over arbitrary imagery. */
    public static float tintAlphaOverlay(boolean dark, float original) {
        if (!isActive()) return original;
        return dark ? 0.83f : 0.76f;
    }

    /** Per-material multiplier for Telegram's existing refraction intensity. */
    public static float refractionScale() {
        if (!isActive()) return 1.0f;
        switch (AppearanceConfig.liquidGlassMaterial()) {
            case 0: return 0.72f;
            case 2: return 1.20f;
            default: return 1.0f;
        }
    }
}
