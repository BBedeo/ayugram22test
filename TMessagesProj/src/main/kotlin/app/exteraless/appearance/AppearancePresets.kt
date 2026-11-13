package app.exteraless.appearance

import android.app.Activity
import app.exteraless.chats.ChatsConfig
import org.telegram.messenger.LiteMode
import org.telegram.messenger.MessagesController
import org.telegram.messenger.SharedConfig
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.LaunchActivity
import org.telegram.ui.MainTabsLayout
import org.telegram.utils.glass.GlassEngine
import xyz.nextalone.nagram.NaConfig

/**
 * Curated design presets for ExFullGram's Appearance screen.
 *
 * Presets coordinate styles scattered across Appearance, App Navigation, Chats, and Blur so users
 * can switch cleanly between full iOS 26 Liquid Glass, a lighter everyday glass setup,
 * Material Design 3, and stock/classic defaults without leaving conflicting toggles active.
 */
object AppearancePresets {

    const val PRESET_CUSTOM = -1
    const val PRESET_MAX_IOS_GLASS = 0
    const val PRESET_BALANCED_IOS_GLASS = 1
    const val PRESET_MATERIAL3 = 2
    const val PRESET_CLASSIC = 3

    private const val SWITCH_STYLE_DEFAULT = 0
    private const val SWITCH_STYLE_MD3 = 2
    private const val SWITCH_STYLE_M3 = 3
    private const val SLIDER_STYLE_DEFAULT = 0
    private const val SLIDER_STYLE_MD3 = 2
    private const val BACK_ANIMATION_SPRING = 1

    private data class PresetSpec(
        val id: Int,
        val universalLiquidGlass: Boolean,
        val liquidGlassMaterial: Int,
        val glassStrength: Int,
        val glassBlur: Int,
        val glassTransparency: Int,
        val glassThickness: Int,
        val glassHighlight: Int,
        val glassLightAngle: Int,
        val glassMotion: Boolean,
        val glassAdaptive: Boolean,
        val glassOutlineStyle: Int,
        val glassMessageMenu: Boolean,
        val disableAvatarBlur: Boolean,
        val iosNavigationBarStyle: Boolean,
        val iosChatHeader: Boolean,
        val bottomNavigationMode: Int,
        /** Null leaves the user's drawer preference untouched; false enables the top-right morphing menu. */
        val navigationDrawer: Boolean?,
        val centerTitle: Boolean,
        val squareFab: Boolean,
        val elasticDragPills: Boolean,
        val gooeyAvatarAnimation: Boolean,
        val dismissMenuOnDragOut: Boolean?,
        val backAnimationStyle: Int?,
        val sectionRadius: Int,
        val separateHeaders: Boolean,
        val dividerStyle: Int,
        val newLoadingStyle: Boolean,
        val newChatHeaderStyle: Boolean,
        val newNavigationBarStyle: Boolean,
        val m3ListItems: Boolean,
        val md3Player: Boolean,
        val md3MiniPlayer: Boolean,
        val sliderStyle: Int,
        val switchStyle: Int
    )

    private val PRESETS = listOf(
        // Max iOS · Liquid Glass: enables expressive native glass, crystal glare outlines,
        // floating dock, centered floating header pills, elastic pill gestures, spring
        // transitions, and hold-and-drag menu overlay selection.
        PresetSpec(
            id = PRESET_MAX_IOS_GLASS,
            universalLiquidGlass = true,
            liquidGlassMaterial = 2,
            glassStrength = 110,
            glassBlur = 65,
            glassTransparency = 72,
            glassThickness = 15,
            glassHighlight = 85,
            glassLightAngle = 315,
            glassMotion = true,
            glassAdaptive = true,
            glassOutlineStyle = 0,
            glassMessageMenu = true,
            disableAvatarBlur = false,
            iosNavigationBarStyle = true,
            iosChatHeader = true,
            bottomNavigationMode = MainTabsLayout.BOTTOM_NAVIGATION_MODE_FLOATING,
            navigationDrawer = false,
            centerTitle = true,
            squareFab = false,
            elasticDragPills = true,
            gooeyAvatarAnimation = true,
            dismissMenuOnDragOut = true,
            backAnimationStyle = BACK_ANIMATION_SPRING,
            sectionRadius = 22,
            separateHeaders = true,
            dividerStyle = AppearanceConfig.DIVIDER_LINE,
            newLoadingStyle = false,
            newChatHeaderStyle = false,
            newNavigationBarStyle = false,
            m3ListItems = false,
            md3Player = false,
            md3MiniPlayer = false,
            sliderStyle = SLIDER_STYLE_DEFAULT,
            switchStyle = SWITCH_STYLE_DEFAULT
        ),
        // Everyday iOS Liquid Glass: balanced lens optics with the floating dock and elastic controls.
        PresetSpec(
            id = PRESET_BALANCED_IOS_GLASS,
            universalLiquidGlass = true,
            liquidGlassMaterial = 1,
            glassStrength = 75,
            glassBlur = 60,
            glassTransparency = 50,
            glassThickness = 11,
            glassHighlight = 55,
            glassLightAngle = 315,
            glassMotion = true,
            glassAdaptive = true,
            glassOutlineStyle = 0,
            glassMessageMenu = true,
            disableAvatarBlur = false,
            iosNavigationBarStyle = true,
            iosChatHeader = true,
            bottomNavigationMode = MainTabsLayout.BOTTOM_NAVIGATION_MODE_FLOATING,
            navigationDrawer = false,
            centerTitle = true,
            squareFab = false,
            elasticDragPills = true,
            gooeyAvatarAnimation = true,
            dismissMenuOnDragOut = true,
            backAnimationStyle = BACK_ANIMATION_SPRING,
            sectionRadius = 20,
            separateHeaders = true,
            dividerStyle = AppearanceConfig.DIVIDER_LINE,
            newLoadingStyle = false,
            newChatHeaderStyle = false,
            newNavigationBarStyle = false,
            m3ListItems = false,
            md3Player = false,
            md3MiniPlayer = false,
            sliderStyle = SLIDER_STYLE_DEFAULT,
            switchStyle = SWITCH_STYLE_DEFAULT
        ),
        // Material Design 3: tonal M3 navigation, sliders, switches, player, and segmented lists.
        PresetSpec(
            id = PRESET_MATERIAL3,
            universalLiquidGlass = false,
            liquidGlassMaterial = 1,
            glassStrength = 75,
            glassBlur = 60,
            glassTransparency = 50,
            glassThickness = 11,
            glassHighlight = 55,
            glassLightAngle = 315,
            glassMotion = true,
            glassAdaptive = true,
            glassOutlineStyle = 1,
            glassMessageMenu = true,
            disableAvatarBlur = false,
            iosNavigationBarStyle = false,
            iosChatHeader = false,
            bottomNavigationMode = MainTabsLayout.BOTTOM_NAVIGATION_MODE_SHOW,
            navigationDrawer = null,
            centerTitle = false,
            squareFab = true,
            elasticDragPills = false,
            gooeyAvatarAnimation = false,
            dismissMenuOnDragOut = null,
            backAnimationStyle = null,
            sectionRadius = 18,
            separateHeaders = true,
            dividerStyle = AppearanceConfig.DIVIDER_SEGMENTS,
            newLoadingStyle = true,
            newChatHeaderStyle = true,
            newNavigationBarStyle = true,
            m3ListItems = true,
            md3Player = true,
            md3MiniPlayer = true,
            sliderStyle = SLIDER_STYLE_MD3,
            switchStyle = SWITCH_STYLE_M3
        ),
        // Classic default look without universal Liquid Glass or elastic overrides.
        PresetSpec(
            id = PRESET_CLASSIC,
            universalLiquidGlass = false,
            liquidGlassMaterial = 1,
            glassStrength = 75,
            glassBlur = 60,
            glassTransparency = 50,
            glassThickness = 11,
            glassHighlight = 55,
            glassLightAngle = 315,
            glassMotion = true,
            glassAdaptive = true,
            glassOutlineStyle = 0,
            glassMessageMenu = true,
            disableAvatarBlur = false,
            iosNavigationBarStyle = false,
            iosChatHeader = false,
            bottomNavigationMode = MainTabsLayout.BOTTOM_NAVIGATION_MODE_SHOW,
            navigationDrawer = null,
            centerTitle = false,
            squareFab = true,
            elasticDragPills = false,
            gooeyAvatarAnimation = true,
            dismissMenuOnDragOut = null,
            backAnimationStyle = null,
            sectionRadius = 20,
            separateHeaders = true,
            dividerStyle = AppearanceConfig.DIVIDER_LINE,
            newLoadingStyle = true,
            newChatHeaderStyle = false,
            newNavigationBarStyle = false,
            m3ListItems = false,
            md3Player = true,
            md3MiniPlayer = true,
            sliderStyle = SLIDER_STYLE_MD3,
            switchStyle = SWITCH_STYLE_MD3
        )
    )

    @JvmStatic
    fun currentPreset(): Int {
        AppearanceConfig.ensureLoaded()
        return PRESETS.firstOrNull { matches(it) }?.id ?: PRESET_CUSTOM
    }

    @JvmStatic
    fun isMaxIosGlassActive(): Boolean = currentPreset() == PRESET_MAX_IOS_GLASS

    @JvmStatic
    fun applyPreset(presetId: Int, activity: Activity?): Boolean {
        val spec = PRESETS.firstOrNull { it.id == presetId } ?: return false
        AppearanceConfig.ensureLoaded()
        ChatsConfig.ensureLoaded()

        AppearanceConfig.universalLiquidGlass.setConfigBool(spec.universalLiquidGlass)
        AppearanceConfig.liquidGlassMaterial.setConfigInt(spec.liquidGlassMaterial)
        AppearanceConfig.liquidGlassStrength.setConfigInt(spec.glassStrength)
        AppearanceConfig.liquidGlassBlur.setConfigInt(spec.glassBlur)
        AppearanceConfig.liquidGlassTransparency.setConfigInt(spec.glassTransparency)
        AppearanceConfig.liquidGlassThickness.setConfigInt(spec.glassThickness)
        AppearanceConfig.liquidGlassHighlight.setConfigInt(spec.glassHighlight)
        AppearanceConfig.liquidGlassLightAngle.setConfigInt(spec.glassLightAngle)
        AppearanceConfig.liquidGlassMotion.setConfigBool(spec.glassMotion)
        AppearanceConfig.liquidGlassAdaptive.setConfigBool(spec.glassAdaptive)
        AppearanceConfig.glassOutlineStyle.setConfigInt(spec.glassOutlineStyle)
        AppearanceConfig.glassMessageMenu.setConfigBool(spec.glassMessageMenu)
        NaConfig.disableAvatarBlur.setConfigBool(spec.disableAvatarBlur)

        // Material 3 flags must be updated before iOS bars so MainTabsUiHelper/ChatHeaderUiHelper do not conflict.
        AppearanceConfig.newLoadingStyle.setConfigBool(spec.newLoadingStyle)
        AppearanceConfig.newChatHeaderStyle.setConfigBool(spec.newChatHeaderStyle)
        AppearanceConfig.newNavigationBarStyle.setConfigBool(spec.newNavigationBarStyle)
        AppearanceConfig.m3ListItems.setConfigBool(spec.m3ListItems)
        AppearanceConfig.md3Player.setConfigBool(spec.md3Player)
        AppearanceConfig.md3MiniPlayer.setConfigBool(spec.md3MiniPlayer)
        NaConfig.sliderStyle.setConfigInt(spec.sliderStyle)
        NaConfig.switchStyle.setConfigInt(spec.switchStyle)

        AppearanceConfig.iosNavigationBarStyle.setConfigBool(spec.iosNavigationBarStyle)
        AppearanceConfig.iosChatHeader.setConfigBool(spec.iosChatHeader)
        MainTabsLayout.setBottomNavigationMode(spec.bottomNavigationMode)
        if (spec.navigationDrawer != null) {
            AppearanceConfig.navigationDrawer.setConfigBool(spec.navigationDrawer)
            if (activity is LaunchActivity) {
                activity.syncDrawerContainerEnabled()
            }
        }
        AppearanceConfig.setCenterTitle(spec.centerTitle)
        AppearanceConfig.squareFab.setConfigBool(spec.squareFab)

        AppearanceConfig.elasticDragPills.setConfigBool(spec.elasticDragPills)
        AppearanceConfig.gooeyAvatarAnimation.setConfigBool(spec.gooeyAvatarAnimation)
        if (spec.dismissMenuOnDragOut != null) {
            ChatsConfig.dismissMenuOnDragOut.setConfigBool(spec.dismissMenuOnDragOut)
        }
        if (spec.backAnimationStyle != null) {
            NaConfig.backAnimationStyle.setConfigInt(spec.backAnimationStyle)
            if (spec.backAnimationStyle == BACK_ANIMATION_SPRING) {
                MessagesController.getGlobalMainSettings().edit()
                    .putBoolean("view_animations", true).apply()
                SharedConfig.setAnimationsEnabled(true)
            }
        }

        AppearanceConfig.sectionRadius.setConfigInt(spec.sectionRadius)
        AppearanceConfig.separateHeaders.setConfigBool(spec.separateHeaders)
        AppearanceConfig.dividerStyle.setConfigInt(spec.dividerStyle)
        NaConfig.hideDividers.setConfigBool(spec.dividerStyle == AppearanceConfig.DIVIDER_HIDDEN)
        AppearanceConfig.invalidateDividerStyle()

        if (spec.universalLiquidGlass) {
            LiteMode.toggleFlag(LiteMode.FLAG_CHAT_BLUR, true)
            LiteMode.toggleFlag(LiteMode.FLAG_LIQUID_GLASS, true)
        }

        GlassEngine.getCurrentGlassSettings()
        Theme.applyCommonTheme()
        return true
    }

    private fun matches(spec: PresetSpec): Boolean {
        return AppearanceConfig.universalLiquidGlass.Bool() == spec.universalLiquidGlass &&
            AppearanceConfig.liquidGlassMaterial.Int() == spec.liquidGlassMaterial &&
            AppearanceConfig.liquidGlassStrength.Int() == spec.glassStrength &&
            AppearanceConfig.liquidGlassBlur.Int() == spec.glassBlur &&
            AppearanceConfig.liquidGlassTransparency.Int() == spec.glassTransparency &&
            AppearanceConfig.liquidGlassThickness.Int() == spec.glassThickness &&
            AppearanceConfig.liquidGlassHighlight.Int() == spec.glassHighlight &&
            AppearanceConfig.liquidGlassLightAngle.Int() == spec.glassLightAngle &&
            AppearanceConfig.liquidGlassMotion.Bool() == spec.glassMotion &&
            AppearanceConfig.liquidGlassAdaptive.Bool() == spec.glassAdaptive &&
            AppearanceConfig.glassOutlineStyle.Int() == spec.glassOutlineStyle &&
            AppearanceConfig.glassMessageMenu.Bool() == spec.glassMessageMenu &&
            NaConfig.disableAvatarBlur.Bool() == spec.disableAvatarBlur &&
            AppearanceConfig.iosNavigationBarStyle.Bool() == spec.iosNavigationBarStyle &&
            AppearanceConfig.iosChatHeader.Bool() == spec.iosChatHeader &&
            MainTabsLayout.getBottomNavigationMode() == spec.bottomNavigationMode &&
            (spec.navigationDrawer == null || AppearanceConfig.navigationDrawer.Bool() == spec.navigationDrawer) &&
            AppearanceConfig.centerTitle() == spec.centerTitle &&
            AppearanceConfig.squareFab.Bool() == spec.squareFab &&
            AppearanceConfig.elasticDragPills.Bool() == spec.elasticDragPills &&
            AppearanceConfig.gooeyAvatarAnimation.Bool() == spec.gooeyAvatarAnimation &&
            (spec.dismissMenuOnDragOut == null || ChatsConfig.dismissMenuOnDragOut.Bool() == spec.dismissMenuOnDragOut) &&
            (spec.backAnimationStyle == null || NaConfig.backAnimationStyle.Int() == spec.backAnimationStyle) &&
            AppearanceConfig.sectionRadius.Int() == spec.sectionRadius &&
            AppearanceConfig.separateHeaders.Bool() == spec.separateHeaders &&
            AppearanceConfig.dividerStyle.Int() == spec.dividerStyle &&
            AppearanceConfig.newLoadingStyle.Bool() == spec.newLoadingStyle &&
            AppearanceConfig.newChatHeaderStyle.Bool() == spec.newChatHeaderStyle &&
            AppearanceConfig.newNavigationBarStyle.Bool() == spec.newNavigationBarStyle &&
            AppearanceConfig.m3ListItems.Bool() == spec.m3ListItems &&
            AppearanceConfig.md3Player.Bool() == spec.md3Player &&
            AppearanceConfig.md3MiniPlayer.Bool() == spec.md3MiniPlayer &&
            NaConfig.sliderStyle.Int() == spec.sliderStyle &&
            NaConfig.switchStyle.Int() == spec.switchStyle
    }
}
