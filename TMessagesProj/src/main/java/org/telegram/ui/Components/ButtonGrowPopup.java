package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.Nullable;

import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.MenuOverlayHost;
import org.telegram.ui.ActionBar.SimpleActionBarMenuOverlay;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProvider;
import org.telegram.utils.glass.source.GlassSourceBitmap;

import app.exteraless.appearance.GlassMenuHelper;

/**
 * The window of a menu growing out of a button ({@link ItemOptions#growFrom}): a transparent full screen popup whose
 * content is the overlay of the header menus, with the button as its host. It pours back before the window goes.
 */
class ButtonGrowPopup extends ActionBarPopupWindow implements MenuOverlayHost {

    static class Overlay extends SimpleActionBarMenuOverlay {
        private ButtonGrowPopup popup;

        private Overlay(Context context) {
            super(context);
        }

        /** An outside tap and the back key close the overlay: the window closes with it. */
        @Override
        public void close(boolean animated) {
            if (animated) {
                popup.dismiss();
            } else {
                popup.dismiss(false);
            }
        }

        private void closeNow(boolean animated) {
            super.close(animated);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            popup.updateGlassOffset();
        }
    }

    static Overlay newOverlay(Context context) {
        return new Overlay(context);
    }

    private static ButtonGrowPopup pouringBack;

    /** A menu still pouring back holds its button hidden: end it before the next menu looks at the button. */
    static void finishClosing() {
        if (pouringBack != null) {
            pouringBack.dismiss(false);
        }
    }

    private final Overlay overlay;
    private final View button;
    private final GlassSourceBitmap source;
    private final View sourceView;
    private final boolean upward;
    private final Theme.ResourcesProvider resourcesProvider;
    private final BlurredBackgroundProvider colors;

    private BlurredBackgroundDrawable glass;
    private SimpleActionBarMenuOverlay openOverlay;
    private float buttonAlpha = 1f;
    private boolean closing;
    private final RectF buttonRect = new RectF();
    private final int[] location = new int[2];

    ButtonGrowPopup(Overlay overlay, View button, GlassSourceBitmap source, View sourceView,
                    boolean upward, Theme.ResourcesProvider resourcesProvider) {
        super(overlay, LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT);
        this.overlay = overlay;
        overlay.popup = this;
        this.button = button;
        this.source = source;
        this.sourceView = sourceView;
        this.upward = upward;
        this.resourcesProvider = resourcesProvider;
        this.colors = GlassMenuHelper.scrimMenuBackground(resourcesProvider);
    }

    /** Call once the window is shown. */
    void openMenu(ActionBarPopupWindow.ActionBarPopupWindowLayout content) {
        overlay.open(this, content);
    }

    @Override
    public void dismiss(boolean animate) {
        if (animate && closing) {
            return; // a second close of the overlay would cut its animation
        }
        if (animate && overlay.isOpened() && overlay.isAttachedToWindow()) {
            closing = true;
            finishClosing();
            pouringBack = this;
            // the screen under the menu takes touches and keys again while it pours back
            setFocusable(false);
            setTouchable(false);
            update();
            overlay.setOnClosed(() -> {
                forgetPouringBack();
                ButtonGrowPopup.super.dismiss(false);
            });
            overlay.closeNow(true);
            return;
        }
        forgetPouringBack();
        overlay.setOnClosed(null);
        overlay.closeNow(false);
        super.dismiss(false);
    }

    private void forgetPouringBack() {
        if (pouringBack == this) {
            pouringBack = null;
        }
    }

    private void updateGlassOffset() {
        if (glass == null) {
            return;
        }
        overlay.getLocationOnScreen(location);
        final int x = location[0], y = location[1];
        sourceView.getLocationOnScreen(location);
        glass.setSourceOffset(x - location[0], y - location[1]);
    }

    /* The button as the host of the overlay */

    @Override
    public BlurredBackgroundDrawable createMenuGlass(View owner) {
        final BlurredBackgroundDrawableViewFactory factory = new BlurredBackgroundDrawableViewFactory(source);
        factory.setLiquidGlassEffectAllowed(true);
        glass = factory.create(owner).setColorProvider(colors).setRadius(dp(23)).setPadding(dp(6)).setHeaderGlassLens(true);
        return glass;
    }

    @Override
    public BlurredBackgroundColorProvider getMenuGlassColorProvider() {
        return colors;
    }

    @Override
    public int getButtonsGlassBackgroundColor() {
        return colors.getBackgroundColor();
    }

    @Override
    public BlurredBackgroundColorProvider getButtonsGlassColorProvider() {
        return colors;
    }

    @Override
    public boolean hasMenuGlassStroke() {
        return true;
    }

    @Override
    public void setMenuGlassFrostedProgress(float progress) {
    }

    @Override
    public boolean getMenuItemGlassRect(ActionBarMenuItem item, RectF out) {
        // a button gone with its screen keeps its last place: the menu pours back there
        if (button.isAttachedToWindow() && button.getWidth() > 0) {
            button.getLocationOnScreen(location);
            final float cx = location[0] + button.getWidth() / 2f, cy = location[1] + button.getHeight() / 2f;
            final float hw = Math.max(button.getWidth(), dp(40)) / 2f, hh = Math.max(button.getHeight(), dp(40)) / 2f;
            buttonRect.set(cx - hw, cy - hh, cx + hw, cy + hh);
        }
        overlay.getLocationOnScreen(location);
        out.set(buttonRect);
        out.offset(-location[0], -location[1]);
        return false;
    }

    @Override
    public View getMenuView() {
        return button;
    }

    @Override
    public View getMenuHostView() {
        return overlay;
    }

    @Override
    public void setMenuOverlay(@Nullable SimpleActionBarMenuOverlay menuOverlay, @Nullable ActionBarMenuItem item) {
        if (menuOverlay != null) {
            if (openOverlay == null) {
                buttonAlpha = button.getAlpha();
            }
        } else if (openOverlay != null) {
            setButtonAlpha(buttonAlpha);
        }
        openOverlay = menuOverlay;
    }

    @Override
    public SimpleActionBarMenuOverlay getOpenMenuOverlay() {
        return openOverlay;
    }

    @Override
    public Theme.ResourcesProvider getMenuResourcesProvider() {
        return resourcesProvider;
    }

    @Override
    public boolean isMenuFromPillBelow() {
        return upward;
    }

    @Override
    public boolean ownsMenuStartButton() {
        return true;
    }

    // called from the overlay's draw, so the button goes only when the overlay's copy of it is on screen
    @Override
    public void setMenuStartReveal(float reveal) {
        setButtonAlpha(buttonAlpha * reveal);
    }

    // a bar draws the glass under its buttons itself, by their alpha (selection mode): it has to redraw with the button
    private void setButtonAlpha(float alpha) {
        if (button.getAlpha() == alpha) {
            return;
        }
        button.setAlpha(alpha);
        final ViewParent parent = button.getParent();
        if (parent != null && parent.getParent() instanceof ActionBar) {
            ((ActionBar) parent.getParent()).invalidate();
        }
    }

    @Override
    public void drawMenuStartIcon(Canvas canvas, RectF rect, float alpha, float scale) {
        final int w = button.getWidth(), h = button.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        canvas.save();
        canvas.translate(rect.centerX() - w / 2f, rect.centerY() - h / 2f);
        canvas.scale(scale, scale, w / 2f, h / 2f);
        canvas.saveLayerAlpha(0, 0, w, h, (int) (0xFF * alpha));
        button.draw(canvas);
        canvas.restore();
        canvas.restore();
    }

    @Override
    public boolean isHeaderGlassLensEnabled() {
        return true;
    }
}
