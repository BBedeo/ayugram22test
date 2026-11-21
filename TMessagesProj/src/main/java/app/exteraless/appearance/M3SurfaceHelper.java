package app.exteraless.appearance;

import android.graphics.Color;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.LiteMode;
import org.telegram.ui.ActionBar.Theme;

/**
 * Unified Android 16/17 Material 3 Expressive opaque surface hierarchy and chrome guards.
 * Provides 100% opaque tonal containers (never translucent glass or backdrop bleed)
 * harmonized with whichever dark or light Telegram theme is active.
 */
public final class M3SurfaceHelper {

    private M3SurfaceHelper() {
    }

    /** True when segmented Material 3 tonal cards are active without Liquid Glass or chat blur. */
    public static boolean isMaterial3SurfaceMode() {
        return !LiquidGlassTuning.isActive()
                && !LiteMode.isEnabled(LiteMode.FLAG_CHAT_BLUR)
                && (AppearanceConfig.m3ListItems()
                        || AppearanceConfig.dividerStyle() == AppearanceConfig.DIVIDER_SEGMENTS);
    }

    /** True when top bars, search bars, menus, and transitions use 100% opaque Material 3 chrome. */
    public static boolean isMaterial3OpaqueChrome() {
        return !LiquidGlassTuning.isActive()
                && !LiteMode.isEnabled(LiteMode.FLAG_CHAT_BLUR)
                && (AppearanceConfig.m3ListItems()
                        || AppearanceConfig.newNavigationBarStyle.Bool()
                        || AppearanceConfig.newChatHeaderStyle()
                        || AppearanceConfig.md3Player.Bool()
                        || MainTabsUiHelper.isMaterial3NavigationBar());
    }

    public static boolean isDark(@Nullable Theme.ResourcesProvider resourcesProvider) {
        return resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
    }

    /** M3 Expressive page canvas ({@code surfaceContainerLowest} / deepened background). */
    public static int getCanvasColor(@Nullable Theme.ResourcesProvider resourcesProvider) {
        final int base = Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider);
        if (!isMaterial3SurfaceMode()) {
            return base;
        }
        if (isDark(resourcesProvider)) {
            if (isNearBlack(base)) {
                return ColorUtils.setAlphaComponent(base, 255);
            }
            return ColorUtils.setAlphaComponent(ColorUtils.blendARGB(base, 0xFF0A0D12, 0.30f), 255);
        }
        return ColorUtils.setAlphaComponent(ColorUtils.blendARGB(base, 0xFFEEF2F9, 0.55f), 255);
    }

    /** M3 Expressive card container ({@code surfaceContainer} Dark / {@code surfaceBright} Light). */
    public static int getCardColor(@Nullable Theme.ResourcesProvider resourcesProvider) {
        final int base = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
        if (!isMaterial3SurfaceMode()) {
            return base;
        }
        if (isDark(resourcesProvider)) {
            if (isNearBlack(base)) {
                return 0xFF1A1D24;
            }
            return ColorUtils.setAlphaComponent(ColorUtils.blendARGB(base, 0xFFE6ECF7, 0.055f), 255);
        }
        return ColorUtils.setAlphaComponent(ColorUtils.blendARGB(base, 0xFFFFFFFF, 0.85f), 255);
    }

    /** Subtle tonal distinction for pinned cards in Material 3 mode without harsh brightness jumps. */
    public static int getPinnedCardColor(@Nullable Theme.ResourcesProvider resourcesProvider) {
        final int card = getCardColor(resourcesProvider);
        if (!isMaterial3SurfaceMode()) {
            final int pinnedOverlay = Theme.getColor(Theme.key_chats_pinnedOverlay, resourcesProvider);
            return ColorUtils.compositeColors(pinnedOverlay, card);
        }
        final int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourcesProvider);
        final boolean dark = isDark(resourcesProvider);
        final int accentTint = ColorUtils.blendARGB(card, accent, dark ? 0.06f : 0.045f);
        return ColorUtils.setAlphaComponent(accentTint, 255);
    }

    /** Opaque Material 3 Top App Bar surface at rest or elevated when content scrolls underneath. */
    public static int getTopBarColor(@Nullable Theme.ResourcesProvider resourcesProvider, boolean scrolled) {
        final int canvas = getCanvasColor(resourcesProvider);
        final int card = getCardColor(resourcesProvider);
        final boolean dark = isDark(resourcesProvider);
        if (!scrolled) {
            return ColorUtils.setAlphaComponent(
                    dark ? ColorUtils.blendARGB(canvas, card, 0.25f)
                         : ColorUtils.blendARGB(canvas, 0xFFFFFFFF, 0.18f),
                    255);
        }
        return ColorUtils.setAlphaComponent(
                dark ? ColorUtils.blendARGB(card, 0xFFFFFFFF, 0.055f)
                     : ColorUtils.blendARGB(canvas, 0xFFE3E8F4, 0.65f),
                255);
    }

    /** Opaque Material 3 {@code surfaceContainerHigh} search pill fill. */
    public static int getSearchBarColor(@Nullable Theme.ResourcesProvider resourcesProvider) {
        final int card = getCardColor(resourcesProvider);
        final int canvas = getCanvasColor(resourcesProvider);
        final boolean dark = isDark(resourcesProvider);
        return ColorUtils.setAlphaComponent(
                dark ? ColorUtils.blendARGB(card, 0xFFFFFFFF, 0.075f)
                     : ColorUtils.blendARGB(canvas, 0xFFDAE1EE, 0.55f),
                255);
    }

    /** Opaque Material 3 {@code surfaceContainer} bottom Navigation Bar surface. */
    public static int getNavBarColor(@Nullable Theme.ResourcesProvider resourcesProvider) {
        final int card = getCardColor(resourcesProvider);
        final int canvas = getCanvasColor(resourcesProvider);
        final boolean dark = isDark(resourcesProvider);
        return ColorUtils.setAlphaComponent(
                dark ? ColorUtils.blendARGB(card, 0xFFFFFFFF, 0.045f)
                     : ColorUtils.blendARGB(canvas, 0xFFEAEEF7, 0.65f),
                255);
    }

    /** Opaque Material 3 {@code surfaceContainer} popup menu / dialog sheet fill. */
    public static int getMenuSurfaceColor(@Nullable Theme.ResourcesProvider resourcesProvider) {
        final int card = getCardColor(resourcesProvider);
        final int canvas = getCanvasColor(resourcesProvider);
        final boolean dark = isDark(resourcesProvider);
        return ColorUtils.setAlphaComponent(
                dark ? ColorUtils.blendARGB(card, 0xFFFFFFFF, 0.055f)
                     : ColorUtils.blendARGB(canvas, 0xFFFFFFFF, 0.90f),
                255);
    }

    /** M3 {@code outlineVariant} hairline color used only where structural separation is needed. */
    public static int getOutlineVariantColor(@Nullable Theme.ResourcesProvider resourcesProvider) {
        final boolean dark = isDark(resourcesProvider);
        final int canvas = getCanvasColor(resourcesProvider);
        return ColorUtils.setAlphaComponent(
                dark ? ColorUtils.blendARGB(canvas, 0xFFE6ECF7, 0.14f)
                     : ColorUtils.blendARGB(canvas, 0xFF1F2633, 0.14f),
                255);
    }

    private static boolean isNearBlack(int color) {
        return Color.red(color) < 16 && Color.green(color) < 16 && Color.blue(color) < 16;
    }
}
