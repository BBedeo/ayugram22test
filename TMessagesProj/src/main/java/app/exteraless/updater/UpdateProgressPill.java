package app.exteraless.updater;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import app.exteraless.appearance.SheetSurfaceMode;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * A minimal plate that shows the progress of an update downloaded in the background. It uses the
 * dialog colors of the active theme. It never sits on top of anything the user can tap: a few seconds
 * after it appears it shrinks to a small chip, and every second it checks which spot of the screen
 * has no buttons, lists or other tappable views under it and glides there. A tap on the chip expands
 * it (with the cross that cancels the download) for a few seconds.
 */
final class UpdateProgressPill extends FrameLayout {

    private static final long COLLAPSE_DELAY_MS = 3000;
    private static final long RELOCATE_INTERVAL_MS = 1000;

    private final ViewGroup content;
    private final TextView label;
    private final TextView cancel;
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Rect probe = new Rect();
    private final Rect viewRect = new Rect();
    private final int[] location = new int[2];
    private final String version;
    private final Runnable collapseRunnable = () -> setCollapsed(true);
    private final Runnable relocateRunnable = new Runnable() {
        @Override
        public void run() {
            relocate(true);
            postDelayed(this, RELOCATE_INTERVAL_MS);
        }
    };
    private float progress;
    private boolean collapsed;
    private boolean removed;

    private UpdateProgressPill(Context context, ViewGroup content, String version, Runnable onCancel) {
        super(context);
        this.content = content;
        this.version = version;
        setWillNotDraw(false);
        setElevation(dp(6));
        // Same surface rules as the update sheet: classic dialog color, an opaque Material 3 card with an
        // outline, or a translucent liquid glass plate with a hairline rim.
        final int mode = SheetSurfaceMode.of(null);
        final int dialogColor = Theme.getColor(Theme.key_dialogBackground);
        final GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(20));
        if (mode == SheetSurfaceMode.GLASS) {
            background.setColor(Theme.multAlpha(dialogColor, 0.82f));
        } else if (mode == SheetSurfaceMode.M3) {
            background.setColor(SheetSurfaceMode.cardFill(null, dialogColor));
        } else {
            background.setColor(dialogColor);
        }
        final int strokePx = SheetSurfaceMode.cardStrokePx(null);
        if (strokePx > 0) {
            background.setStroke(strokePx, SheetSurfaceMode.cardStroke(null));
        }
        setBackground(background);
        barPaint.setColor(SheetSurfaceMode.accent(null));
        trackPaint.setColor(mode == SheetSurfaceMode.CLASSIC
                ? Theme.multAlpha(Theme.getColor(Theme.key_dialogTextGray2), 0.25f)
                : SheetSurfaceMode.progressTrack(null));

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
        UpdateProgressPill pill = new UpdateProgressPill(activity, content, version, onCancel);
        content.addView(pill, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 40, Gravity.TOP | Gravity.LEFT));
        pill.setAlpha(0f);
        pill.post(() -> {
            pill.relocate(false);
            pill.animate().alpha(1f).setDuration(220).start();
        });
        pill.postDelayed(pill.relocateRunnable, RELOCATE_INTERVAL_MS);
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
        final FrameLayout.LayoutParams labelParams = (FrameLayout.LayoutParams) label.getLayoutParams();
        labelParams.leftMargin = dp(value ? 12 : 16);
        labelParams.rightMargin = dp(value ? 12 : 44);
        label.setLayoutParams(labelParams);
        label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, value ? 12 : 13);
        final ViewGroup.LayoutParams params = getLayoutParams();
        params.height = dp(value ? 26 : 40);
        setLayoutParams(params);
        update();
        invalidate();
        // The new size may need another spot: look again once it has been laid out.
        post(() -> relocate(true));
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
        removed = true;
        removeCallbacks(collapseRunnable);
        removeCallbacks(relocateRunnable);
        final View self = this;
        animate().alpha(0f).setDuration(180).withEndAction(() -> {
            if (self.getParent() instanceof ViewGroup) {
                ((ViewGroup) self.getParent()).removeView(self);
            }
        }).start();
    }

    // ---- staying off the tappable views ---------------------------------------------------------

    /** Moves the plate to the first spot of the screen where nothing tappable is under it. */
    private void relocate(boolean animated) {
        if (removed || getWidth() == 0 || getHeight() == 0 || content.getWidth() == 0) {
            return;
        }
        final int w = getWidth();
        final int h = getHeight();
        final int cw = content.getWidth();
        final int ch = content.getHeight();
        final int strip = Math.max(0, (AndroidUtilities.statusBarHeight - h) / 2);
        final int side = dp(8);
        // Candidates in the order of preference: the top edge (above the action bar buttons) in the
        // center, then at the sides, then a line below the action bar and the bottom edge.
        final int[][] spots = {
                {(cw - w) / 2, strip},
                {side, strip},
                {cw - w - side, strip},
                {(cw - w) / 2, AndroidUtilities.statusBarHeight + dp(2)},
                {(cw - w) / 2, ch - h - dp(2)},
        };
        int[] chosen = spots[0];
        for (int[] spot : spots) {
            if (!coversTappable(spot[0], spot[1], w, h)) {
                chosen = spot;
                break;
            }
        }
        final float x = chosen[0];
        final float y = chosen[1];
        if (animated && (Math.abs(getTranslationX() - x) > 1 || Math.abs(getTranslationY() - y) > 1)) {
            animate().translationX(x).translationY(y).setDuration(220).start();
        } else if (!animated) {
            setTranslationX(x);
            setTranslationY(y);
        }
    }

    private boolean coversTappable(int x, int y, int w, int h) {
        content.getLocationInWindow(location);
        probe.set(location[0] + x, location[1] + y, location[0] + x + w, location[1] + y + h);
        return hasTappable(content, probe);
    }

    private boolean hasTappable(View view, Rect area) {
        if (view == this || view.getVisibility() != View.VISIBLE || view.getAlpha() < 0.05f) {
            return false;
        }
        view.getLocationInWindow(location);
        viewRect.set(location[0], location[1], location[0] + view.getWidth(), location[1] + view.getHeight());
        if (!Rect.intersects(viewRect, area)) {
            return false;
        }
        if (view instanceof RecyclerView || view instanceof AbsListView
                || view.isClickable() || view.isLongClickable()) {
            return true;
        }
        if (view instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                if (hasTappable(group.getChildAt(i), area)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            setCollapsed(!collapsed);
            if (!collapsed) {
                scheduleCollapse();
            }
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final float inset = dp(collapsed ? 10 : 16);
        final float left = inset;
        final float right = getWidth() - inset;
        final float y = getHeight() - dp(collapsed ? 4 : 5);
        final float half = dp(collapsed ? 1.25f : 1.5f);
        rect.set(left, y - half, right, y + half);
        canvas.drawRoundRect(rect, dp(2), dp(2), trackPaint);
        rect.set(left, y - half, left + (right - left) * progress, y + half);
        canvas.drawRoundRect(rect, dp(2), dp(2), barPaint);
    }
}
