package app.exteraless.appearance;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * Renders an iOS / iOS 26 style toggle switch with capsule track, iOS green checked tint,
 * shadowed pill-stretching white thumb, and subtle top highlight.
 */
public final class IosSwitchHelper {

    private static final int IOS_GREEN_LIGHT = 0xFF34C759;
    private static final int IOS_GREEN_DARK = 0xFF30D158;
    private static final int IOS_OFF_LIGHT = 0xFFE9E9EA;
    private static final int IOS_OFF_DARK = 0xFF39393D;

    private IosSwitchHelper() {
    }

    public static void draw(
            Canvas canvas,
            int measuredWidth,
            int measuredHeight,
            float progress,
            float overrideAlpha,
            int trackColorKey,
            int trackCheckedColorKey,
            int resolvedOffColor,
            int resolvedOnColor,
            RectF rectF,
            Paint paint,
            Drawable rippleDrawable) {
        final int alphaSave;
        if (overrideAlpha < 1f) {
            final int pad = dp(8);
            alphaSave = canvas.saveLayerAlpha(-pad, -pad, measuredWidth + pad, measuredHeight + pad,
                    (int) (255 * Utilities.clamp01(overrideAlpha)));
        } else {
            alphaSave = -1;
        }

        final float pp = Utilities.clamp01(progress);
        final float colorProgress = Utilities.clamp01(progress);
        final boolean dark = Theme.isCurrentThemeDark();

        final int offColor = (trackColorKey == Theme.key_switch2Track || trackColorKey == Theme.key_switchTrack)
                ? (dark ? IOS_OFF_DARK : IOS_OFF_LIGHT)
                : resolvedOffColor;
        final int onColor = (trackCheckedColorKey == Theme.key_switch2TrackChecked
                || trackCheckedColorKey == Theme.key_switchTrackChecked)
                ? (dark ? IOS_GREEN_DARK : IOS_GREEN_LIGHT)
                : resolvedOnColor;

        // Track geometry slightly wider than the 37dp View box cleanly matching iOS ~46x28 capsule.
        final float trackH = dpf2(27);
        final float trackTop = measuredHeight / 2f - trackH / 2f;
        final float trackBottom = trackTop + trackH;
        final float trackLeft = -dpf2(4);
        final float trackRight = measuredWidth + dpf2(4);
        final float trackRadius = trackH / 2f;

        paint.setColor(ColorUtils.blendARGB(offColor, onColor, colorProgress));
        rectF.set(trackLeft, trackTop, trackRight, trackBottom);
        canvas.drawRoundRect(rectF, trackRadius, trackRadius, paint);

        // Subtle inner rim for dark mode depth.
        if (dark && colorProgress < 0.99f) {
            int rimAlpha = (int) ((1f - colorProgress) * 26);
            paint.setColor(Color.argb(rimAlpha, 255, 255, 255));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dpf2(0.75f));
            canvas.drawRoundRect(rectF, trackRadius, trackRadius, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        final float thumbR = dpf2(11.5f);
        final float inset = (trackH - thumbR * 2f) / 2f;
        final float minCx = trackLeft + inset + thumbR;
        final float maxCx = trackRight - inset - thumbR;
        final float cx = minCx + (maxCx - minCx) * pp;
        final float cy = measuredHeight / 2f;

        if (rippleDrawable != null) {
            rippleDrawable.setBounds((int) cx - dp(18), (int) cy - dp(18), (int) cx + dp(18), (int) cy + dp(18));
            rippleDrawable.draw(canvas);
        }

        // Liquid iOS 26 horizontal pill stretch at transition midpoint.
        final float stretch = dpf2(4.2f) * (1f - Math.abs(pp * 2f - 1f));
        final float left = cx - thumbR - stretch;
        final float top = cy - thumbR;
        final float right = cx + thumbR + stretch;
        final float bottom = cy + thumbR;

        // Soft ambient drop shadow beneath the knob.
        paint.setColor(dark ? 0x3A000000 : 0x26000000);
        rectF.set(left, top + dpf2(1.4f), right, bottom + dpf2(1.4f));
        canvas.drawRoundRect(rectF, thumbR, thumbR, paint);

        // Crisp white thumb (slightly translucent glass highlight during active slide).
        int thumbColor = ColorUtils.blendARGB(0xFFFFFFFF, 0xFFF4F6FA, 1f - Math.abs(pp * 2f - 1f));
        paint.setColor(thumbColor);
        rectF.set(left, top, right, bottom);
        canvas.drawRoundRect(rectF, thumbR, thumbR, paint);

        if (alphaSave != -1) {
            canvas.restoreToCount(alphaSave);
        }
    }
}
