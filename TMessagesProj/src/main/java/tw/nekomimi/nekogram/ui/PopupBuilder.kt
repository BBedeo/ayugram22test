package tw.nekomimi.nekogram.ui

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.exteraless.appearance.LiquidGlassTuning
import app.exteraless.appearance.M3SurfaceHelper
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.Theme

@SuppressLint("ViewConstructor")
class PopupBuilder @JvmOverloads constructor(anchor: View, dialog: Boolean = false) : ActionBarMenuItem(anchor.context, null, Theme.ACTION_BAR_WHITE_SELECTOR_COLOR, -0x4c4c4d) {

    private val useFullWidthGlassDropdown = LiquidGlassTuning.isActive() &&
        !M3SurfaceHelper.isMaterial3OpaqueChrome() &&
        !dialog &&
        anchor.measuredWidth >= AndroidUtilities.dp(200f)

    init {

        setAnchor(anchor)

        isShowOnTop = dialog

        isVerticalScrollBarEnabled = true

        if (useFullWidthGlassDropdown) {
            setFullWidthAnchorDropdown(true)
        }

    }

    private fun styleSubItem(subItem: TextView) {
        if (!useFullWidthGlassDropdown) return
        val lp = subItem.layoutParams as? LinearLayout.LayoutParams
        if (lp != null) {
            lp.gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            subItem.layoutParams = lp
        }
        subItem.gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
    }

    fun setItems(items: Array<CharSequence?>, listener: (Int, CharSequence) -> Unit) {

        removeAllSubItems()

        for (item in items) {
            if (item == null) continue
            styleSubItem(addSubItem(items.indexOf(item), item))
        }

        setDelegate {

            listener(it, items[it]!!)

        }

    }

    fun setItems(items: List<CharSequence?>, listener: (Int, CharSequence) -> Unit) {

        removeAllSubItems()

        for (item in items) {
            if (item == null) continue
            styleSubItem(addSubItem(items.indexOf(item), item))
        }

        setDelegate {

            listener(it, items[it]!!)

        }

    }

    fun show() {

        toggleSubMenu()

    }

}
