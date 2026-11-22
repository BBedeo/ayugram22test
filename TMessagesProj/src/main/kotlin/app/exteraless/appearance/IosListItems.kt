package app.exteraless.appearance

import android.graphics.Color
import androidx.core.graphics.ColorUtils

/**
 * Styling helpers for iOS-style Settings rows (squircle icon badges, inset dividers, compact spacing).
 */
object IosListItems {

    @JvmStatic
    fun enabled(): Boolean = AppearanceConfig.isIosListItems()

    @JvmStatic
    fun iconBadgeColor(top: Int, bottom: Int): Int {
        val mid = ColorUtils.blendARGB(top, bottom, 0.5f)
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(mid, hsl)
        hsl[1] = hsl[1].coerceIn(0.68f, 0.96f)
        hsl[2] = hsl[2].coerceIn(0.42f, 0.56f)
        return ColorUtils.HSLToColor(hsl)
    }

    @JvmStatic
    fun iconForeground(): Int = Color.WHITE
}
