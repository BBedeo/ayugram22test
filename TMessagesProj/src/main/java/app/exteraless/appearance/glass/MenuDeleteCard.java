package app.exteraless.appearance.glass;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Components.ChatScrimPopupContainerLayout;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;

import java.util.ArrayList;

/**
 * The message menu's own delete question: the same window shrinks to its bottom edge, the items and whatever surrounds
 * the menu dissolve, and the dialog's checks with «Cancel» and the red action come out of a light blur. «Cancel» unfolds
 * the menu again; a tap beside the card closes everything.
 */
public final class MenuDeleteCard {

    private static final long MOVE_MS = 340;
    private static final float BLUR_DP = 6;

    private final ActionBarPopupWindow.ActionBarPopupWindowLayout popup;
    private final FrameLayout card;
    private final LinearLayout panel;
    private final ArrayList<View> around = new ArrayList<>();
    private final boolean dragSelect;
    private int cut;
    private boolean moving;
    private boolean answered;

    /** False when the menu cannot hold the question; nothing has changed then and the caller shows the dialog. */
    public static boolean show(ChatScrimPopupContainerLayout menu, @Nullable View checks, CharSequence action,
                               Theme.ResourcesProvider resourcesProvider, Runnable onOutside, Runnable onDelete) {
        final ActionBarPopupWindow.ActionBarPopupWindowLayout popup = menu.getPopupWindowLayout();
        if (popup == null) {
            return false;
        }
        if (popup.hasBottomCard()) {
            return true; // two fingers pressed two ways to delete: the question is already there
        }
        final MenuDeleteCard question = new MenuDeleteCard(menu.getContext(), popup, checks, action, resourcesProvider, onOutside, onDelete);
        question.cut = popup.addBottomCard(question.card);
        if (question.cut < 0) {
            question.panel.removeAllViews(); // the dialog takes the checks
            return false;
        }
        for (int i = 0; i < menu.getChildCount(); i++) {
            final View child = menu.getChildAt(i);
            if (child != popup && child.getVisibility() == View.VISIBLE) {
                question.around.add(child);
            }
        }
        if (checks != null) {
            fitChecks(checks);
        }
        popup.resetDragHover();
        popup.setDragSelectEnabled(false);
        question.move(true);
        return true;
    }

    private MenuDeleteCard(Context context, ActionBarPopupWindow.ActionBarPopupWindowLayout popup, @Nullable View checks,
                           CharSequence action, Theme.ResourcesProvider resourcesProvider, Runnable onOutside, Runnable onDelete) {
        this.popup = popup;
        this.dragSelect = popup.isDragSelectEnabled();

        card = new FrameLayout(context);
        card.setOnClickListener(v -> {
            if (!moving) {
                onOutside.run();
            }
        });
        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClickable(true);
        card.addView(panel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        if (checks != null && anyShown(checks)) {
            panel.addView(checks, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            panel.addView(divider(context, resourcesProvider), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }

        final LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        final TextView cancel = button(context, LocaleController.getString(R.string.Cancel),
                Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider));
        cancel.setOnClickListener(v -> {
            if (!moving && !answered) {
                move(false);
            }
        });
        final TextView delete = button(context, action, Theme.getColor(Theme.key_text_RedRegular, resourcesProvider));
        delete.setTypeface(AndroidUtilities.bold());
        delete.setOnClickListener(v -> {
            if (!moving && !answered) {
                answered = true;
                onDelete.run();
            }
        });
        buttons.addView(cancel, new LinearLayout.LayoutParams(0, dp(48), 1));
        buttons.addView(divider(context, resourcesProvider), new LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT));
        buttons.addView(delete, new LinearLayout.LayoutParams(0, dp(48), 1));
        panel.addView(buttons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    /** The dialog hides every check for messages already deleted by their author. */
    private static boolean anyShown(View checks) {
        if (!(checks instanceof ViewGroup)) {
            return true;
        }
        final ViewGroup group = (ViewGroup) checks;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (group.getChildAt(i).getVisibility() == View.VISIBLE) {
                return true;
            }
        }
        return false;
    }

    /** The menu is narrower than the dialog the checks were made for: their labels get a second line. */
    private static void fitChecks(View checks) {
        if (!(checks instanceof ViewGroup)) {
            return;
        }
        final ViewGroup group = (ViewGroup) checks;
        for (int i = 0; i < group.getChildCount(); i++) {
            final View child = group.getChildAt(i);
            if (child instanceof CheckBoxCell && ((CheckBoxCell) child).getTextView() != null) {
                final TextView label = ((CheckBoxCell) child).getTextView();
                label.setSingleLine(false);
                label.setMaxLines(2);
                label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            }
        }
    }

    private static TextView button(Context context, CharSequence text, int color) {
        final TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setPadding(dp(8), 0, dp(8), 0);
        view.setBackground(Theme.createRadSelectorDrawable(Theme.multAlpha(color, .12f), 10, 10));
        ScaleStateListAnimator.apply(view);
        return view;
    }

    private static View divider(Context context, Theme.ResourcesProvider resourcesProvider) {
        final View view = new View(context);
        view.setBackgroundColor(Theme.getColor(Theme.key_divider, resourcesProvider));
        return view;
    }

    private void move(boolean toQuestion) {
        moving = true;
        final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(MOVE_MS);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> frame(toQuestion, (float) a.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                moving = false;
                if (!toQuestion) {
                    popup.removeView(card);
                    popup.setDragSelectEnabled(dragSelect);
                }
            }
        });
        frame(toQuestion, 0f);
        animator.start();
    }

    /** What leaves is gone by 40% of the time, what arrives starts at 15% and is whole at 70%. */
    private void frame(boolean toQuestion, float time) {
        final float shape = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(time);
        final float leaving = 1f - Math.min(1f, time / 0.4f);
        final float arriving = Math.max(0f, Math.min(1f, (time - 0.15f) / 0.55f));
        final float menuAlpha = toQuestion ? leaving : arriving;
        final float cardAlpha = toQuestion ? arriving : leaving;
        popup.setTopCut(cut * (toQuestion ? shape : 1f - shape), menuAlpha);
        for (int i = 0; i < around.size(); i++) {
            final View view = around.get(i);
            view.setAlpha(menuAlpha);
            view.setVisibility(menuAlpha > 0 ? View.VISIBLE : View.INVISIBLE);
        }
        panel.setAlpha(cardAlpha);
        if (Build.VERSION.SDK_INT >= 31) {
            final float blur = dp(BLUR_DP) * (1f - cardAlpha) * (1f - cardAlpha);
            panel.setRenderEffect(blur > 0.5f ? RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP) : null);
        }
    }
}
