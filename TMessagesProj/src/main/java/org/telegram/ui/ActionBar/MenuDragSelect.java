package org.telegram.ui.ActionBar;

import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Hold a button, slide over the rows of the menu it opened, lift the finger to pick one:
 * the glass menu follows the finger with its hover pill, the row under it is pressed.
 * For buttons that are not menu items themselves (the profile's action row).
 */
public final class MenuDragSelect {
    private final ActionBarPopupWindow.ActionBarPopupWindowLayout content;
    private final int[] location = new int[2];
    private View selected;

    public MenuDragSelect(ActionBarPopupWindow.ActionBarPopupWindowLayout content) {
        this.content = content;
    }

    /** Presses the row under the finger (screen coordinates). */
    public void move(float screenX, float screenY) {
        if (content == null || content.getWidth() <= 0 || content.getHeight() <= 0) {
            return;
        }
        final View pageRoot = content.getVisiblePageRoot();
        final View hit = ActionBarMenuItem.findHoveredMenuChild(pageRoot, pageRoot, screenX, screenY, location);
        if (ActionBarMenuItem.applyHoverSelection(selected, hit, screenX, screenY, location)) {
            hit.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
        selected = hit;
        content.setDragHoverView(hit);
    }

    /** The finger is up: the row under it is chosen. False when it is over none, the menu then stays open. */
    public boolean release(float screenX, float screenY) {
        move(screenX, screenY);
        final View view = selected;
        cancel();
        if (view == null) {
            return false;
        }
        view.performClick();
        return true;
    }

    public void cancel() {
        ActionBarMenuItem.clearHoverSelection(selected);
        selected = null;
        if (content != null) {
            content.setDragHoverView(null);
        }
    }

    @Nullable
    public View getSelected() {
        return selected;
    }
}
