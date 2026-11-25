package app.exteraless.player;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import app.exteraless.appearance.glass.GlassSurfaceDrawable;
import app.exteraless.appearance.glass.GlassTokens;

/**
 * What the iOS player's control capsule shrinks into after a few idle seconds: a glass capsule with a
 * pause/play button and the running track line. A tap on the button toggles playback, a tap elsewhere expands the controls.
 */
final class PlayerCollapsedBar extends View {

    private final GlassSurfaceDrawable glass = new GlassSurfaceDrawable(0xFFFFFFFF, GlassTokens.TIER_THIN, GlassTokens.RADIUS_CAPSULE);
    private final PlayerIcon play = PlayerIcon.fill(PlayerIcon.PLAY, 22);
    private final PlayerIcon pause = PlayerIcon.fill(PlayerIcon.PAUSE, 22);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private boolean playing;
    private float progress;
    private Runnable onToggle;
    private Runnable onExpand;
    private boolean downOnButton;

    PlayerCollapsedBar(Context context) {
        super(context);
        glass.setShadow(true);
        glass.setFillAlpha(0.42f);
    }

    void setActions(Runnable toggle, Runnable expand) {
        onToggle = toggle;
        onExpand = expand;
    }

    void setColors(int onSurface, int base) {
        glass.setBaseColor(base);
        play.setColor(onSurface);
        pause.setColor(onSurface);
        trackPaint.setColor((onSurface & 0x00FFFFFF) | 0x38000000);
        fillPaint.setColor(onSurface);
        invalidate();
    }

    void setPlaying(boolean value) {
        if (playing != value) {
            playing = value;
            invalidate();
        }
    }

    void setProgress(float value) {
        value = Math.max(0f, Math.min(1f, value));
        if (progress != value) {
            progress = value;
            invalidate();
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        final int w = getWidth();
        final int h = getHeight();
        glass.setBounds(0, 0, w, h);
        glass.draw(canvas);
        final int button = h;
        final PlayerIcon icon = playing ? pause : play;
        icon.setBounds(0, 0, button, h);
        icon.draw(canvas);
        final float left = button;
        final float right = w - dp(20);
        final float th = dp(4);
        rect.set(left, h / 2f - th / 2f, right, h / 2f + th / 2f);
        canvas.drawRoundRect(rect, th / 2f, th / 2f, trackPaint);
        rect.right = Math.max(left + th, left + (right - left) * progress);
        canvas.drawRoundRect(rect, th / 2f, th / 2f, fillPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downOnButton = event.getX() < getHeight();
                return true;
            case MotionEvent.ACTION_UP:
                if (downOnButton && event.getX() < getHeight()) {
                    if (onToggle != null) {
                        onToggle.run();
                    }
                } else if (onExpand != null) {
                    onExpand.run();
                }
                return true;
            default:
                return true;
        }
    }
}
