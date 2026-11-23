package app.exteraless.updater;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ScrollView;

/**
 * A slim scroll thumb on the right edge of the release notes. It follows the scroll position and
 * can be dragged (or tapped) to scroll, so long notes stay reachable in both directions without
 * the bottom sheet taking the gesture. Colors come from the active preset (accent and track).
 */
final class NotesScrollThumb extends View {

    private final ScrollView scrollView;
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private boolean dragging;
    private float dragOffset;

    NotesScrollThumb(Context context, ScrollView scrollView, int accent, int trackColor) {
        super(context);
        this.scrollView = scrollView;
        thumbPaint.setColor(accent);
        trackPaint.setColor(trackColor);
        scrollView.getViewTreeObserver().addOnScrollChangedListener(this::invalidate);
        scrollView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> invalidate());
    }

    private int contentHeight() {
        return scrollView.getChildCount() > 0 ? scrollView.getChildAt(0).getHeight() : 0;
    }

    private int scrollRange() {
        return Math.max(0, contentHeight() - scrollView.getHeight());
    }

    private float thumbHeight() {
        final float height = getHeight();
        final float content = Math.max(1, contentHeight());
        return Math.max(dp(36), Math.min(height, height * scrollView.getHeight() / content));
    }

    private float thumbTop() {
        final int range = scrollRange();
        final float travel = getHeight() - thumbHeight();
        return range <= 0 ? 0 : travel * scrollView.getScrollY() / range;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (scrollRange() <= 0 || getHeight() <= 0) {
            return;
        }
        final float cx = getWidth() - dp(5);
        final float half = dragging ? dp(3.5f) : dp(2.5f);
        rect.set(cx - half, 0, cx + half, getHeight());
        canvas.drawRoundRect(rect, half, half, trackPaint);
        final float top = thumbTop();
        rect.set(cx - half, top, cx + half, top + thumbHeight());
        canvas.drawRoundRect(rect, half, half, thumbPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (scrollRange() <= 0) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                getParent().requestDisallowInterceptTouchEvent(true);
                final float top = thumbTop();
                final float height = thumbHeight();
                dragging = true;
                // Grab the thumb where it was touched, or center it on the tap.
                dragOffset = event.getY() >= top && event.getY() <= top + height ? event.getY() - top : height / 2f;
                scrollToTouch(event.getY());
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    scrollToTouch(event.getY());
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                invalidate();
                return true;
            default:
                return true;
        }
    }

    private void scrollToTouch(float y) {
        final float travel = getHeight() - thumbHeight();
        if (travel <= 0) {
            return;
        }
        final float progress = Math.max(0f, Math.min(1f, (y - dragOffset) / travel));
        scrollView.scrollTo(0, Math.round(progress * scrollRange()));
    }
}
