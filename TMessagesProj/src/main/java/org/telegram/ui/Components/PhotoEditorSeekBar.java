/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;

import tw.nekomimi.nekogram.helpers.NaConfig;

public class PhotoEditorSeekBar extends View {

    private Paint innerPaint = new Paint();
    private Paint outerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean ios = NaConfig.INSTANCE.getSliderStyle().Int() == SeekBarView.SLIDER_STYLE_IOS;
    private final RectF rect = new RectF();
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int thumbSize = AndroidUtilities.dp(ios ? 24 : 16);
    private int thumbDX = 0;
    private float progress = 0;
    private boolean pressed = false;
    private int minValue;
    private int maxValue;
    private PhotoEditorSeekBarDelegate delegate;

    public interface PhotoEditorSeekBarDelegate {
        void onProgressChanged(int i, int progress);
    }

    public PhotoEditorSeekBar(Context context) {
        super(context);

        innerPaint.setColor(ios ? 0x59ffffff : 0xff4d4d4d);
        innerPaint.setAntiAlias(true);
        outerPaint.setColor(0xffffffff);
        thumbPaint.setColor(0xffffffff);
        if (ios) {
            thumbPaint.setShadowLayer(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(1), 0x40000000);
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }
    }

    public void setDelegate(PhotoEditorSeekBarDelegate delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event == null) {
            return false;
        }
        float x = event.getX();
        float y = event.getY();
        float thumbX = (int)((getMeasuredWidth() - thumbSize) * progress);
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            int additionWidth = (getMeasuredHeight() - thumbSize) / 2;
            if (thumbX - additionWidth <= x && x <= thumbX + thumbSize + additionWidth && y >= 0 && y <= getMeasuredHeight()) {
                pressed = true;
                thumbDX = (int)(x - thumbX);
                getParent().requestDisallowInterceptTouchEvent(true);
                invalidate();
                return true;
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (pressed) {
                pressed = false;
                invalidate();
                return true;
            }
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (pressed) {
                thumbX = (int)(x - thumbDX);
                if (thumbX < 0) {
                    thumbX = 0;
                } else if (thumbX > getMeasuredWidth() - thumbSize) {
                    thumbX = getMeasuredWidth() - thumbSize;
                }
                progress = thumbX / (getMeasuredWidth() - thumbSize);
                if (delegate != null) {
                    delegate.onProgressChanged((Integer) getTag(), getProgress());
                }
                invalidate();
                return true;
            }
        }
        return false;
    }

    public void setProgress(int progress) {
        setProgress(progress, true);
    }

    public void setProgress(int progress, boolean notify) {
        if (progress < minValue) {
            progress = minValue;
        } else if (progress > maxValue) {
            progress = maxValue;
        }
        this.progress = (progress - minValue) / (float) (maxValue - minValue);
        invalidate();
        if (notify && delegate != null) {
            delegate.onProgressChanged((Integer) getTag(), getProgress());
        }
    }

    public int getProgress() {
        return (int) (minValue + progress * (maxValue - minValue));
    }

    public void setMinMax(int min, int max) {
        minValue = min;
        maxValue = max;
    }

    private void drawIos(Canvas canvas) {
        int w = getMeasuredWidth();
        int cy = getMeasuredHeight() / 2;
        int thumbX = (int) ((w - thumbSize) * progress);
        float half = AndroidUtilities.dp(2);
        float r = half;
        rect.set(thumbSize / 2f, cy - half, w - thumbSize / 2f, cy + half);
        canvas.drawRoundRect(rect, r, r, innerPaint);
        float from = minValue == 0 ? thumbSize / 2f : w / 2f;
        float cx = thumbX + thumbSize / 2f;
        rect.set(Math.min(from, cx), cy - half, Math.max(from, cx), cy + half);
        canvas.drawRoundRect(rect, r, r, outerPaint);
        canvas.drawCircle(cx, cy, thumbSize / 2f - AndroidUtilities.dp(2), thumbPaint);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (ios) {
            drawIos(canvas);
            return;
        }
        int y = (getMeasuredHeight() - thumbSize) / 2;
        int thumbX = (int)((getMeasuredWidth() - thumbSize) * progress);
        canvas.drawRect(thumbSize / 2, getMeasuredHeight() / 2 - AndroidUtilities.dp(1), getMeasuredWidth() - thumbSize / 2, getMeasuredHeight() / 2 + AndroidUtilities.dp(1), innerPaint);
        if (minValue == 0) {
            canvas.drawRect(thumbSize / 2, getMeasuredHeight() / 2 - AndroidUtilities.dp(1), thumbX, getMeasuredHeight() / 2 + AndroidUtilities.dp(1), outerPaint);
        } else {
            if (progress > 0.5f) {
                canvas.drawRect(getMeasuredWidth() / 2 - AndroidUtilities.dp(1), (getMeasuredHeight() - thumbSize) / 2, getMeasuredWidth() / 2, (getMeasuredHeight() + thumbSize) / 2, outerPaint);
                canvas.drawRect(getMeasuredWidth() / 2, getMeasuredHeight() / 2 - AndroidUtilities.dp(1), thumbX, getMeasuredHeight() / 2 + AndroidUtilities.dp(1), outerPaint);
            } else {
                canvas.drawRect(getMeasuredWidth() / 2, (getMeasuredHeight() - thumbSize) / 2, getMeasuredWidth() / 2 + AndroidUtilities.dp(1), (getMeasuredHeight() + thumbSize) / 2, outerPaint);
                canvas.drawRect(thumbX, getMeasuredHeight() / 2 - AndroidUtilities.dp(1), getMeasuredWidth() / 2, getMeasuredHeight() / 2 + AndroidUtilities.dp(1), outerPaint);
            }
        }
        canvas.drawCircle(thumbX + thumbSize / 2, y + thumbSize / 2, thumbSize / 2, outerPaint);
    }
}
