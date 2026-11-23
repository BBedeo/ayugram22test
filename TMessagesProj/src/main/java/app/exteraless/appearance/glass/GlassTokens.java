package app.exteraless.appearance.glass;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Color;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

import app.exteraless.appearance.LiquidGlassTuning;

/**
 * Design tokens of ExFullGram's iOS 26 Liquid Glass language (see DESIGN_SYSTEM.md).
 *
 * <p>Everything here is consulted only while Liquid Glass is active ({@link #isActive()}); with the
 * setting off callers keep their original drawing, so the classic look stays untouched.
 *
 * <p>Three density tiers:
 * <ul>
 *     <li>{@link #TIER_THIN}: chips, pills, secondary buttons, inputs;</li>
 *     <li>{@link #TIER_BASE}: content cards, widgets, list sections;</li>
 *     <li>{@link #TIER_THICK}: floating bars, dialogs, menus, toasts, hints — the most readable.</li>
 * </ul>
 * A surface that has a real backdrop capture (the {@code GlassEngine} factories) uses the AGSL lens;
 * a surface without one (toasts, hints, dialogs in their own window) uses {@link GlassSurfaceDrawable},
 * whose fill is the element's own color made translucent so the text colors chosen for it keep their
 * contrast.
 */
public final class GlassTokens {

    public static final int TIER_THIN = 1;
    public static final int TIER_BASE = 2;
    public static final int TIER_THICK = 3;

    /** Corner radius that turns a surface into a capsule (half of its height). */
    public static final float RADIUS_CAPSULE = -1f;

    // Radii in dp. Concentric rule: R_outer = R_inner + padding (see concentric()).
    public static final float RADIUS_CHIP = RADIUS_CAPSULE;
    public static final float RADIUS_HINT = 14;
    public static final float RADIUS_CARD = 22;
    public static final float RADIUS_TOAST = 22;
    public static final float RADIUS_MENU = 22;
    public static final float RADIUS_DIALOG = 28;
    public static final float RADIUS_SHEET = 30;

    /** Press feedback: the scale a glass control sinks to under the finger. */
    public static final float PRESS_SCALE = 0.97f;

    private GlassTokens() {
    }

    public static boolean isActive() {
        return LiquidGlassTuning.isActive();
    }

    public static boolean motionEnabled() {
        return LiquidGlassTuning.motionEnabled();
    }

    public static boolean isDark() {
        return Theme.isCurrentThemeDark();
    }

    /** Outer radius of a container whose child has {@code innerRadiusDp} and sits {@code paddingDp} in. */
    public static float concentric(float innerRadiusDp, float paddingDp) {
        return innerRadiusDp + paddingDp;
    }

    /** Inner radius for a child placed {@code paddingDp} inside a container of {@code outerRadiusDp}. */
    public static float innerRadius(float outerRadiusDp, float paddingDp) {
        return Math.max(0, outerRadiusDp - paddingDp);
    }

    /**
     * Opacity of a tier's fill when no backdrop capture is available. Tier 3 stays dense enough that
     * text reads over a pure white, pure black or busy photo backdrop; the Transparency slider of the
     * Liquid Glass settings shifts it a little but never below the readability floor.
     */
    public static float fillAlpha(int tier, boolean dark) {
        final float base;
        final float floor;
        switch (tier) {
            case TIER_THIN:
                base = dark ? 0.62f : 0.66f;
                floor = 0.50f;
                break;
            case TIER_BASE:
                base = dark ? 0.76f : 0.80f;
                floor = 0.66f;
                break;
            default:
                base = dark ? 0.86f : 0.88f;
                floor = 0.80f;
                break;
        }
        // The user's Transparency slider (0..100, 50 neutral) nudges the fill by at most ±6%.
        final float slider = (app.exteraless.appearance.AppearanceConfig.liquidGlassTransparency.Int() - 50) / 50f;
        return Math.max(floor, Math.min(0.96f, base - slider * 0.06f));
    }

    /** The element's own color made into a tier fill. */
    public static int fill(int baseColor, int tier) {
        final boolean dark = AndroidUtilities.computePerceivedBrightness(baseColor | 0xFF000000) < 0.5f;
        final int opaque = baseColor | 0xFF000000;
        return ColorUtils.setAlphaComponent(opaque, Math.round(255 * fillAlpha(tier, dark)));
    }

    /** Color of the bright (top-left) side of the 1px rim. */
    public static int rimLight(int baseColor) {
        final boolean dark = AndroidUtilities.computePerceivedBrightness(baseColor | 0xFF000000) < 0.5f;
        final float shine = 0.6f + 0.4f * Math.min(1f, LiquidGlassTuning.shine());
        // Dark surfaces get a faint catchlight only: a bright white rim reads as a chalky outline there.
        return Color.argb(Math.round((dark ? 0x2E : 0xE6) * shine), 255, 255, 255);
    }

    /** Color of the shaded (bottom-right) side of the 1px rim. */
    public static int rimShade(int baseColor) {
        final boolean dark = AndroidUtilities.computePerceivedBrightness(baseColor | 0xFF000000) < 0.5f;
        return dark ? 0x0FFFFFFF : 0x14000000;
    }

    /** Inner specular highlight just under the top edge (the polished bevel). */
    public static int innerHighlight(int baseColor) {
        final boolean dark = AndroidUtilities.computePerceivedBrightness(baseColor | 0xFF000000) < 0.5f;
        final float shine = 0.6f + 0.4f * Math.min(1f, LiquidGlassTuning.shine());
        return Color.argb(Math.round((dark ? 0x1C : 0x66) * shine), 255, 255, 255);
    }

    /** Soft ambient shadow under floating tier-3 surfaces. */
    public static int ambientShadow(boolean dark) {
        return dark ? 0x40000000 : 0x1A000000;
    }

    public static int px(float dpValue) {
        return dp(dpValue);
    }
}
