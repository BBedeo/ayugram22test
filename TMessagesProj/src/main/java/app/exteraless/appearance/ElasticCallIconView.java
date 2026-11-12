package app.exteraless.appearance;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.widget.ImageView;

/**
 * Top-left/top-right VoIP actions: glass and icon follow the touch together,
 * without changing hit geometry, listeners, or accessibility semantics.
 * A dragged icon cannot accidentally minimize a call or open a people sheet.
 */
public class ElasticCallIconView extends ImageView {
    private final ElasticPillGesture elastic = new ElasticPillGesture(this);
    private boolean consumedDrag;

    public ElasticCallIconView(Context context) {
        super(context);
    }

    @Override
    public void draw(Canvas canvas) {
        if (!ElasticPillGesture.enabled()) {
            super.draw(canvas);
            return;
        }
        canvas.save();
        elastic.transform(canvas, getWidth() / 2f, getHeight() / 2f,
                Math.min(getWidth(), getHeight()));
        super.draw(canvas);
        canvas.restore();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!ElasticPillGesture.enabled()) return super.dispatchTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                consumedDrag = false;
                elastic.down(event.getX(), event.getY());
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_MOVE:
                if (elastic.move(event.getX(), event.getY())) {
                    if (!consumedDrag) {
                        consumedDrag = true;
                        MotionEvent cancel = MotionEvent.obtain(event);
                        cancel.setAction(MotionEvent.ACTION_CANCEL);
                        super.dispatchTouchEvent(cancel);
                        cancel.recycle();
                    }
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
                boolean dragged = elastic.up();
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                if (consumedDrag || dragged) {
                    consumedDrag = false;
                    return true;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                elastic.up();
                consumedDrag = false;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                break;
        }
        return super.dispatchTouchEvent(event);
    }
}
