package app.exteraless.updater;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LineProgressView;

import app.exteraless.appearance.SheetSurfaceMode;

/**
 * One row of the migration flow: a badge (step number, turns into a check mark), a title, a
 * description and one action button. Colors, radii and strokes follow the active preset
 * through {@link SheetSurfaceMode}, the same way the update sheet does.
 */
final class MigrationStepView extends LinearLayout {

    private final Theme.ResourcesProvider resourcesProvider;
    private final int accent;
    private final TextView badge;
    private final TextView titleView;
    private final TextView descView;
    private final TextView button;
    private final LineProgressView progress;
    private final float buttonRadius;
    private boolean done;

    MigrationStepView(Context context, Theme.ResourcesProvider resourcesProvider, CharSequence number,
                      CharSequence title, CharSequence description, CharSequence buttonText) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.accent = SheetSurfaceMode.accent(resourcesProvider);
        this.buttonRadius = SheetSurfaceMode.buttonRadiusDp(resourcesProvider);
        setOrientation(VERTICAL);
        setPadding(dp(14), dp(12), dp(14), dp(12));

        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(SheetSurfaceMode.cardRadiusDp(resourcesProvider)));
        background.setColor(SheetSurfaceMode.cardFill(resourcesProvider, color(Theme.key_graySection)));
        int strokePx = SheetSurfaceMode.cardStrokePx(resourcesProvider);
        int stroke = SheetSurfaceMode.cardStroke(resourcesProvider);
        if (strokePx > 0 && Color.alpha(stroke) != 0) {
            background.setStroke(strokePx, stroke);
        }
        setBackground(background);

        LinearLayout top = new LinearLayout(context);
        top.setOrientation(HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        badge = new TextView(context);
        badge.setGravity(Gravity.CENTER);
        badge.setTypeface(AndroidUtilities.bold());
        badge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        setBadgeStyle(false);
        badge.setText(number);
        if (TextUtils.isEmpty(number)) {
            badge.setVisibility(GONE);
        }
        top.addView(badge, LayoutHelper.createLinear(28, 28, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(VERTICAL);
        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(color(Theme.key_dialogTextBlack));
        titleView.setText(title);
        texts.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        descView = new TextView(context);
        descView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        descView.setLineSpacing(dp(1), 1f);
        descView.setTextColor(color(Theme.key_dialogTextGray2));
        descView.setText(description);
        texts.addView(descView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        top.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        addView(top, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        progress = new LineProgressView(context);
        progress.setProgressColor(accent);
        progress.setBackColor(SheetSurfaceMode.progressTrack(resourcesProvider));
        progress.setVisibility(GONE);
        addView(progress, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 4, 0, 10, 0, 0));

        button = new TextView(context);
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        button.setTypeface(AndroidUtilities.bold());
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setText(buttonText);
        styleButton(true);
        addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, SheetSurfaceMode.isM3(resourcesProvider) ? 48 : 44, 0, 10, 0, 0));
    }

    private int color(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    private void setBadgeStyle(boolean finished) {
        boolean m3 = SheetSurfaceMode.isM3(resourcesProvider);
        int fill = finished ? accent : (m3 ? ColorUtils.setAlphaComponent(accent, 0x1F) : ColorUtils.setAlphaComponent(accent, 0x2E));
        badge.setBackground(Theme.createRoundRectDrawable(dp(14), fill));
        badge.setTextColor(finished ? SheetSurfaceMode.onAccent(resourcesProvider) : accent);
    }

    private void styleButton(boolean primary) {
        if (primary) {
            button.setTextColor(SheetSurfaceMode.onAccent(resourcesProvider));
            button.setBackground(Theme.AdaptiveRipple.filledRect(accent, buttonRadius));
        } else {
            button.setTextColor(SheetSurfaceMode.secondaryButtonText(resourcesProvider, accent));
            button.setBackground(Theme.AdaptiveRipple.filledRect(
                    SheetSurfaceMode.secondaryButtonFill(resourcesProvider, accent), buttonRadius));
        }
    }

    void setOnAction(OnClickListener listener) {
        button.setOnClickListener(v -> {
            if (button.isEnabled() && !done) {
                listener.onClick(v);
            }
        });
    }

    void setActionEnabled(boolean enabled) {
        button.setEnabled(enabled);
        button.animate().alpha(enabled ? 1f : 0.45f).setDuration(180).start();
    }

    /** Shows a busy state; a negative fraction hides the progress bar. */
    void setBusy(CharSequence text, float fraction) {
        button.setEnabled(false);
        button.setText(text);
        styleButton(false);
        if (fraction >= 0f) {
            if (progress.getVisibility() != VISIBLE) {
                progress.setAlpha(0f);
                progress.setVisibility(VISIBLE);
                progress.animate().alpha(1f).setDuration(160).start();
            }
            progress.setProgress(fraction, true);
        }
    }

    /** Back to an actionable state, e.g. after a failure. */
    void reset(CharSequence text) {
        done = false;
        button.setText(text);
        button.setEnabled(true);
        button.setAlpha(1f);
        styleButton(true);
        progress.setVisibility(GONE);
    }

    boolean isDone() {
        return done;
    }

    /** Marks the step finished: the badge pops into a check mark, the button turns into a quiet status. */
    void setDone(CharSequence statusText) {
        done = true;
        progress.setVisibility(GONE);
        badge.setVisibility(VISIBLE);
        badge.animate().scaleX(0.4f).scaleY(0.4f).setDuration(110).withEndAction(() -> {
            badge.setText("✓");
            setBadgeStyle(true);
            badge.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new OvershootInterpolator(2.2f)).start();
        }).start();
        ValueAnimator fade = ValueAnimator.ofFloat(1f, 0f, 1f);
        fade.setDuration(280);
        boolean[] swapped = {false};
        fade.addUpdateListener(a -> {
            float value = (float) a.getAnimatedValue();
            if (!swapped[0] && a.getAnimatedFraction() >= 0.5f) {
                swapped[0] = true;
                button.setText(statusText);
                button.setEnabled(false);
                styleButton(false);
            }
            button.setAlpha(value);
        });
        fade.setInterpolator(CubicBezierInterpolator.DEFAULT);
        fade.start();
    }

    void setDescription(CharSequence text) {
        descView.setText(text);
    }

    View buttonView() {
        return button;
    }
}
