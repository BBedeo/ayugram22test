package org.telegram.ui.Components.blur3;

/**
 * The numbers of the single Liquid Glass lens: the band from the shape's size, one bend for every shape.
 * No Android classes: scripts/check_liquid_glass_engine.py compiles and runs it.
 */
public final class LiquidGlassOptics {

    public static final int PANEL = 0, FLOATING = 1, PILL = 2;

    private LiquidGlassOptics() {
    }

    public static int role(boolean pill, boolean header) {
        return pill ? PILL : header ? FLOATING : PANEL;
    }

    /** Width of the bent band, px. */
    public static float bevel(float minSidePx, float density, int thicknessSettingDp) {
        final float width = Math.max(6f, Math.min(24f, minSidePx / density * 0.2f * thicknessSettingDp / 11f)) * density;
        return Math.max(Math.min(width, 0.45f * minSidePx), 1f);
    }

    /** Shift at the very edge in widths of the band: 4 at the default settings. */
    public static float bend(float strengthScale) {
        return Math.max(0f, Math.min(6f, 4f * strengthScale));
    }

    /** The bottom capsule's pill is low: with {@link #bend} its far side showed upside down at the edge. */
    public static float pillBend(float strengthScale) {
        return 0.12f * bend(strengthScale);
    }

    public static float saturation(int role) {
        return role == PILL ? 1.2f : 1f;
    }

    /** The shader antialiases the edge itself, as the lens of the headers did. */
    public static float coverage(int role) {
        return role == FLOATING ? 1f : 0f;
    }

    /** The tide of the bottom capsule. */
    public static float flow(int role) {
        return role == PILL ? 1f : 0f;
    }

    public static float sliderBevel(float heightPx, float density, int thicknessSettingDp) {
        return Math.max(Math.min(thicknessSettingDp * density, heightPx / 3f), 1f);
    }

    /** Weak enough that the edge of the low thumb never reaches the track and mirrors it. */
    public static float sliderBend(float strengthScale, float press) {
        return Math.min(0.8f * strengthScale, 0.9f) * press;
    }
}
