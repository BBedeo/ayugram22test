package org.telegram.utils.recorder.round;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;

/**
 * Generates the ExFullGram curved corner watermark overlay ("Ex" top-left, "Full" top-right,
 * "Gram" bottom-right) centered in each visible corner outside the circular crop.
 */
public final class RoundVideoWatermarkOverlay {

    private RoundVideoWatermarkOverlay() {
    }

    @NonNull
    public static Bitmap createAlphaBitmap(int size) {
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8);
        Canvas canvas = new Canvas(bitmap);

        float center = size * 0.5f;
        float arcRadius = center * 1.105f;
        RectF oval = new RectF(
                center - arcRadius,
                center - arcRadius,
                center + arcRadius,
                center + arcRadius
        );

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(AndroidUtilities.bold());
        paint.setTextSize(size * 0.044f);
        paint.setLetterSpacing(0.14f);

        Paint.FontMetrics metrics = paint.getFontMetrics();
        float verticalOffset = -(metrics.ascent + metrics.descent) * 0.5f;

        Path path = new Path();
        path.addArc(oval, 195f, 60f);
        canvas.drawTextOnPath("Ex", path, 0f, verticalOffset, paint);

        path.reset();
        path.addArc(oval, 285f, 60f);
        canvas.drawTextOnPath("Full", path, 0f, verticalOffset, paint);

        path.reset();
        path.addArc(oval, 75f, -60f);
        canvas.drawTextOnPath("Gram", path, 0f, verticalOffset, paint);

        return bitmap;
    }
}
