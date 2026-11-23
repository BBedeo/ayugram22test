package app.exteraless.updater;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * A minimal plate at the top of the screen that shows the progress of an update downloaded in the
 * background. It uses the dialog colors of the active theme, so it fits every theme; the cross cancels.
 */
final class UpdateProgressPill extends FrameLayout {

    private final TextView label;
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final String version;
    private float progress;

    private UpdateProgressPill(Context context, String version, Runnable onCancel) {
        super(context);
        this.version = version;
        setWillNotDraw(false);
        setElevation(dp(6));
        setBackground(Theme.createRoundRectDrawable(dp(20), Theme.getColor(Theme.key_dialogBackground)));
        barPaint.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        trackPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_dialogTextGray2), 0.25f));

        label = new TextView(context);
        label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        label.setTypeface(AndroidUtilities.bold());
        label.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        label.setSingleLine(true);
        addView(label, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL | Gravity.LEFT, 16, 0, 44, 0));

        TextView cancel = new TextView(context);
        cancel.setText("✕");
        cancel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        cancel.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_CIRCLE_20DP));
        cancel.setOnClickListener(v -> onCancel.run());
        addView(cancel, LayoutHelper.createFrame(36, 36, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 4, 0));
        update();
    }

    static UpdateProgressPill show(Activity activity, String version, Runnable onCancel) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) {
            return null;
        }
        UpdateProgressPill pill = new UpdateProgressPill(activity, version, onCancel);
        FrameLayout.LayoutParams params = LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 40, Gravity.TOP | Gravity.CENTER_HORIZONTAL,
                0, 0, 0, 0);
        params.topMargin = AndroidUtilities.statusBarHeight + dp(8);
        content.addView(pill, params);
        pill.setAlpha(0f);
        pill.setTranslationY(-dp(16));
        pill.animate().alpha(1f).translationY(0).setDuration(220).start();
        return pill;
    }

    void setProgress(long done, long total) {
        if (total > 0) {
            progress = Math.max(0f, Math.min(1f, done / (float) total));
            update();
            invalidate();
        }
    }

    private void update() {
        label.setText(LocaleController.formatString(R.string.OEUpdateBackgroundPill, version, Math.round(progress * 100)));
    }

    void remove() {
        final View self = this;
        animate().alpha(0f).translationY(-dp(16)).setDuration(180).withEndAction(() -> {
            if (self.getParent() instanceof ViewGroup) {
                ((ViewGroup) self.getParent()).removeView(self);
            }
        }).start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final float left = dp(16);
        final float right = getWidth() - dp(16);
        final float y = getHeight() - dp(5);
        rect.set(left, y - dp(1.5f), right, y + dp(1.5f));
        canvas.drawRoundRect(rect, dp(2), dp(2), trackPaint);
        rect.set(left, y - dp(1.5f), left + (right - left) * progress, y + dp(1.5f));
        canvas.drawRoundRect(rect, dp(2), dp(2), barPaint);
    }
}
