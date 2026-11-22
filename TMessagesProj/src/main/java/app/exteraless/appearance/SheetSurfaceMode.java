package app.exteraless.appearance;

import android.graphics.Color;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.GlassOutlineStyle;

/**
 * Единый режим поверхности шторок и баннеров.
 *
 * <p>Пресет в {@link AppearancePresets} — не режим, а совпадение трёх десятков независимых
 * флагов: любая ручная правка даёт {@code PRESET_CUSTOM}, поэтому ветвиться на
 * {@code currentPreset()} в UI нельзя. Компоненты спрашивают те же гейты, по которым уже
 * выбирает оформление {@code BottomSheet.onCreateInternal()}: стекло — {@link LiquidGlassTuning},
 * Material 3 — {@link M3SurfaceHelper} / {@link GlassOutlineStyle}, остальное — классика.
 *
 * <p>Задача класса — чтобы контент шторки (карточка релиз-ноутов, кнопки, прогресс) говорил
 * теми же радиусами, цветами и обводками, что и её собственный фон. Иначе в M3 шит
 * непрозрачный с обводкой 28dp, а внутри классические серые плашки 10dp.
 */
public final class SheetSurfaceMode {

    public static final int CLASSIC = 0;
    public static final int M3 = 1;
    public static final int GLASS = 2;

    private SheetSurfaceMode() {
    }

    public static int of(@Nullable Theme.ResourcesProvider resourcesProvider) {
        if (LiquidGlassTuning.isActive()) {
            return GLASS;
        }
        if (isM3(resourcesProvider)) {
            return M3;
        }
        return CLASSIC;
    }

    /**
     * Тот же гейт, по которому {@code BottomSheet} выбирает непрозрачную M3-поверхность.
     * Прежняя проверка требовала {@link GlassOutlineStyle#SOLID}, но пресет Material 3
     * ставит {@code HIDDEN} — и шит молча рисовался классикой.
     */
    public static boolean isM3(@Nullable Theme.ResourcesProvider resourcesProvider) {
        if (LiquidGlassTuning.isActive() || LiteMode.isEnabled(LiteMode.FLAG_CHAT_BLUR)) {
            return false;
        }
        // isMaterial3SurfaceMode() включает ещё и стиль разделителей «сегменты», из-за чего
        // шторки становились непрозрачными у классического пресета. Берём гейт именно хрома.
        return GlassOutlineStyle.current() == GlassOutlineStyle.SOLID
                || M3SurfaceHelper.isMaterial3OpaqueChrome();
    }

    public static boolean isDark(@Nullable Theme.ResourcesProvider resourcesProvider) {
        return AndroidUtilities.computePerceivedBrightness(
                Theme.getColor(Theme.key_dialogBackground, resourcesProvider)) < .72f;
    }

    /** Радиус карточек внутри шторки. M3 берёт пользовательский радиус секций. */
    public static float cardRadiusDp(@Nullable Theme.ResourcesProvider resourcesProvider) {
        if (isM3(resourcesProvider)) {
            return Math.max(12, AppearanceConfig.sectionRadius());
        }
        return 18f;
    }

    public static float buttonRadiusDp(@Nullable Theme.ResourcesProvider resourcesProvider) {
        switch (of(resourcesProvider)) {
            case M3:
                return 20f;
            case GLASS:
                return 22f;
            default:
                return 10f;
        }
    }

    /** Заливка карточки: {@code classicColor} — то, чем карточка была в классическом стиле. */
    public static int cardFill(@Nullable Theme.ResourcesProvider resourcesProvider, int classicColor) {
        switch (of(resourcesProvider)) {
            case GLASS:
                return Theme.multAlpha(Theme.getColor(Theme.key_dialogBackground, resourcesProvider), 0.30f);
            case M3:
                return M3SurfaceHelper.getCardColor(resourcesProvider);
            default:
                return classicColor;
        }
    }

    /** Обводка карточки; {@code Color.TRANSPARENT} — рисовать без обводки. */
    public static int cardStroke(@Nullable Theme.ResourcesProvider resourcesProvider) {
        switch (of(resourcesProvider)) {
            case GLASS:
                return isDark(resourcesProvider) ? 0x1AFFFFFF : 0x14000000;
            case M3:
                return M3SurfaceHelper.getOutlineVariantColor(resourcesProvider);
            default:
                return Color.TRANSPARENT;
        }
    }

    /** Толщина обводки карточки в пикселях (0 — без обводки). */
    public static int cardStrokePx(@Nullable Theme.ResourcesProvider resourcesProvider) {
        switch (of(resourcesProvider)) {
            case GLASS:
                return Math.max(1, Math.round(AndroidUtilities.dp(0.6f)));
            case M3:
                return Math.max(1, AndroidUtilities.dp(1));
            default:
                return 0;
        }
    }

    /** Акцент: в M3 это синий токен Material, в остальных — прежний акцент Telegram. */
    public static int accent(@Nullable Theme.ResourcesProvider resourcesProvider) {
        return isM3(resourcesProvider)
                ? Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourcesProvider)
                : Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
    }

    /** Цвет текста на акцентной плашке. */
    public static int onAccent(@Nullable Theme.ResourcesProvider resourcesProvider) {
        return Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider);
    }

    /** Фон «вторичной» кнопки. */
    public static int secondaryButtonFill(@Nullable Theme.ResourcesProvider resourcesProvider, int accent) {
        switch (of(resourcesProvider)) {
            case M3:
                return ColorUtils.setAlphaComponent(accent, 0x14);
            case GLASS:
                return ColorUtils.setAlphaComponent(accent, 0x1F);
            default:
                return Theme.getColor(Theme.key_graySection, resourcesProvider);
        }
    }

    /** Цвет текста «вторичной» кнопки. */
    public static int secondaryButtonText(@Nullable Theme.ResourcesProvider resourcesProvider, int accent) {
        return isM3(resourcesProvider) ? accent : Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider);
    }

    /** Дорожка прогресса. */
    public static int progressTrack(@Nullable Theme.ResourcesProvider resourcesProvider) {
        return isM3(resourcesProvider)
                ? M3SurfaceHelper.getOutlineVariantColor(resourcesProvider)
                : Theme.getColor(Theme.key_graySection, resourcesProvider);
    }
}
