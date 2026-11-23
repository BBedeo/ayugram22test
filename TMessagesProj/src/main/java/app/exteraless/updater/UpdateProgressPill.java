package app.exteraless.updater;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * A minimal plate that shows the progress of an update downloaded in the background. It uses the
 * dialog colors of the active theme. It does not stay in the way: a few seconds after it appears it
 * shrinks to a small chip with the percentage, a tap expands or shrinks it, and it can be dragged
 * anywhere on the screen. The cross cancels the download.
 */
final class UpdateProgressPill extends FrameLayout {

    private static final long COLLAPSE_DELAY_MS = 3000;

    private final TextView label;
    private final TextView cancel;
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final String version;
    private final int touchSlop;
    private final Runnable collapseRunnable = () -> setCollapsed(true);
    private float progress;
    private boolean collapsed;
    private float downX, downY, startTx, startTy;
    private boolean dragging;

    private UpdateProgressPill(Context context, String version, Runnable onCancel) {
        super(context);
        this.version = version;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
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

        cancel = new TextView(context);
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
        FrameLayout.LayoutParams params = LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 40,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0);
        params.topMargin = AndroidUtilities.statusBarHeight + dp(8);
        content.addView(pill, params);
        pill.setAlpha(0f);
        pill.setTranslationY(-dp(16));
        pill.animate().alpha(1f).translationY(0).setDuration(220).start();
        pill.scheduleCollapse();
        return pill;
    }

    private void scheduleCollapse() {
        removeCallbacks(collapseRunnable);
        postDelayed(collapseRunnable, COLLAPSE_DELAY_MS);
    }

    private void setCollapsed(boolean value) {
        if (collapsed == value) {
            return;
        }
        collapsed = value;
        cancel.setVisibility(value ? View.GONE : View.VISIBLE);
        final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) label.getLayoutParams();
        params.leftMargin = dp(value ? 12 : 16);
        params.rightMargin = dp(value ? 12 : 44);
        label.setLayoutParams(params);
        update();
        requestLayout();
        invalidate();
        if (!value) {
            scheduleCollapse();
        }
    }

    void setProgress(long done, long total) {
        if (total > 0) {
            progress = Math.max(0f, Math.min(1f, done / (float) total));
            update();
            invalidate();
        }
    }

    private void update() {
        final int percent = Math.round(progress * 100);
        label.setText(collapsed ? LocaleController.formatString(R.string.OEUpdateBackgroundChip, percent)
                : LocaleController.formatString(R.string.OEUpdateBackgroundPill, version, percent));
    }

    void remove() {
        removeCallbacks(collapseRunnable);
        final View self = this;
        animate().alpha(0f).translationY(getTranslationY() - dp(16)).setDuration(180).withEndAction(() -> {
            if (self.getParent() instanceof ViewGroup) {
                ((ViewGroup) self.getParent()).removeView(self);
            }
        }).start();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getRawX();
                downY = event.getRawY();
                startTx = getTranslationX();
                startTy = getTranslationY();
                dragging = false;
                removeCallbacks(collapseRunnable);
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE: {
                final float dx = event.getRawX() - downX;
                final float dy = event.getRawY() - downY;
                if (!dragging && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                    dragging = true;
                }
                if (dragging) {
                    setTranslationX(startTx + dx);
                    setTranslationY(startTy + dy);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                if (!dragging) {
                    setCollapsed(!collapsed);
                    if (!collapsed) {
                        scheduleCollapse();
                    }
                } else if (!collapsed) {
                    scheduleCollapse();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (!collapsed) {
                    scheduleCollapse();
                }
                return true;
            default:
                return true;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (collapsed) {
            // The chip shows the progress as a thin ring-like bar under the percentage.
            final float left = dp(10);
            final float right = getWidth() - dp(10);
            final float y = getHeight() - dp(5);
            rect.set(left, y - dp(1.25f), right, y + dp(1.25f));
            canvas.drawRoundRect(rect, dp(2), dp(2), trackPaint);
            rect.set(left, y - dp(1.25f), left + (right - left) * progress, y + dp(1.25f));
            canvas.drawRoundRect(rect, dp(2), dp(2), barPaint);
            return;
        }
        final float left = dp(16);
        final float right = getWidth() - dp(16);
        final float y = getHeight() - dp(5);
        rect.set(left, y - dp(1.5f), right, y + dp(1.5f));
        canvas.drawRoundRect(rect, dp(2), dp(2), trackPaint);
        rect.set(left, y - dp(1.5f), left + (right - left) * progress, y + dp(1.5f));
        canvas.drawRoundRect(rect, dp(2), dp(2), barPaint);
    }
}
