package app.exteraless.player;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.view.ViewConfiguration;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import app.exteraless.appearance.glass.GlassSurfaceDrawable;
import app.exteraless.appearance.glass.GlassTokens;
import app.exteraless.appearance.glass.IosSliderThumb;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;

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
    private final IosSliderThumb iosThumb = new IosSliderThumb(this);
    private final AnimatedFloat thumbPop = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_BACK);
    private boolean playing;
    private float progress;
    private Runnable onToggle;
    private Runnable onExpand;
    private boolean downOnButton;
    private boolean scrubbing;
    private float downX;
    private final int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();

    interface Seek {
        void onStart();

        void onMove(float progress);

        void onEnd(float progress);
    }

    private Seek seek;

    void setSeek(Seek value) {
        seek = value;
    }

    /** Where the track position {@code p} sits in this view's own coordinates, so a bubble can follow the thumb. */
    float trackX(float p) {
        final float left = getHeight();
        return left + (getWidth() - dp(20) - left) * p;
    }

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
        final float th = scrubbing ? dp(6) : dp(4);
        final float cy = h / 2f;
        final IosSliderThumb.Track track = c -> {
            rect.set(left, cy - th / 2f, right, cy + th / 2f);
            c.drawRoundRect(rect, th / 2f, th / 2f, trackPaint);
            rect.right = Math.max(left + th, left + (right - left) * progress);
            c.drawRoundRect(rect, th / 2f, th / 2f, fillPaint);
        };
        if (GlassTokens.isActive()) {
            final boolean still = Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled();
            final float pop = Math.max(0f, thumbPop.set(scrubbing ? 1f : 0f, still));
            iosThumb.drawPopping(canvas, trackX(progress), cy, w, pop, track);
        } else {
            track.draw(canvas);
        }
    }

    private float fraction(float x) {
        final float left = getHeight();
        final float right = getWidth() - dp(20);
        return Math.max(0f, Math.min(1f, (x - left) / Math.max(1f, right - left)));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downOnButton = event.getX() < getHeight();
                downX = event.getX();
                scrubbing = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!downOnButton && seek != null) {
                    if (!scrubbing && Math.abs(event.getX() - downX) > slop) {
                        scrubbing = true;
                        invalidate();
                        getParent().requestDisallowInterceptTouchEvent(true);
                        seek.onStart();
                    }
                    if (scrubbing) {
                        seek.onMove(fraction(event.getX()));
                    }
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (scrubbing) {
                    scrubbing = false;
                    seek.onEnd(progress);
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (scrubbing) {
                    scrubbing = false;
                    seek.onEnd(fraction(event.getX()));
                    invalidate();
                    return true;
                }
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
