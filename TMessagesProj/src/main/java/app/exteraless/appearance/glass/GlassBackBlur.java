package app.exteraless.appearance.glass;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.RequiresApi;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;

/**
 * The veil of Glass Touch (Android 12+): frosted, on 13+ also bent like a lens. One object blurs at most one view. A back
 * gesture feeds it the position its own animations produce; the only animator here fades the veil out, and it touches
 * nothing but the effect.
 */
public final class GlassBackBlur implements View.OnAttachStateChangeListener {

    private static final long BACK_FADE_MS = 380;

    private static Object shader; // RuntimeShader, Android 13+; typed loosely so older runtimes never resolve it
    private static boolean shaderFailed;

    private View view;
    private float applied = -1f; // strength of the effect set on the view, -1 when there is none
    private ValueAnimator fade;

    /** Starts with the previous screen of a back gesture; {@link #disarm()} or a fade must follow before it is shown. */
    public void arm(View behind) {
        disarm();
        if (GlassTouchSettings.back()) {
            take(behind);
        }
    }

    /** {@code q}: share of the previous screen that is uncovered, 0..1. A running fade ignores it. */
    public void update(float q) {
        if (fade == null) {
            apply(GlassTouchMath.backStrength(q));
        }
    }

    /** Veils {@code target} at {@code strength}; the caller has checked its own switch. */
    public void set(View target, float strength) {
        if (target != view || fade != null) {
            disarm();
            take(target);
        }
        apply(strength);
    }

    /** A committed back: clears from the current strength by time. */
    public void release() {
        if (fade != null) {
            return;
        }
        if (applied < 0) {
            disarm();
        } else {
            fade(applied, 0, BACK_FADE_MS);
        }
    }

    /** Veils {@code target} at once and clears it by time; the caller has checked its own switch. */
    public void pulse(View target, float strength, long ms) {
        disarm();
        take(target);
        if (view != null) {
            apply(strength);
            fade(strength, 0, ms); // a view that is not laid out yet picks the effect up on a later frame
        }
    }

    /** Like {@link #pulse(View, float, long)}, but swells in over {@code riseMs}: the view is sharp when it starts. */
    public void pulse(View target, float strength, long riseMs, long ms) {
        disarm();
        take(target);
        if (view != null) {
            fade(strength, riseMs, ms);
        }
    }

    /** Leaves a running fade to finish, drops anything else. */
    public void settle() {
        if (fade == null) {
            disarm();
        }
    }

    public void disarm() {
        final ValueAnimator running = fade;
        fade = null;
        if (running != null) {
            running.cancel();
        }
        remove();
        if (view != null) {
            view.removeOnAttachStateChangeListener(this);
            view = null;
        }
    }

    @Override
    public void onViewAttachedToWindow(View v) {
    }

    @Override
    public void onViewDetachedFromWindow(View v) {
        disarm();
    }

    private void take(View target) {
        if (Build.VERSION.SDK_INT >= 31 && target != null) {
            view = target;
            target.addOnAttachStateChangeListener(this);
        }
    }

    private void fade(float peak, long riseMs, long ms) {
        final ValueAnimator animator = ValueAnimator.ofFloat(0f, ms);
        animator.setDuration(ms);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            if (fade == a) {
                final float elapsed = (float) a.getAnimatedValue();
                apply(riseMs > 0 ? GlassTouchMath.pulseStrength(peak, elapsed, riseMs, ms)
                        : GlassTouchMath.fadeStrength(peak, elapsed, ms));
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                if (fade == a) {
                    disarm();
                }
            }
        });
        fade = animator;
        animator.start();
    }

    private void apply(float k) {
        if (Build.VERSION.SDK_INT < 31 || view == null) {
            return;
        }
        if (k < 0.015f) {
            remove();
        } else if ((applied < 0 || Math.abs(k - applied) > 0.012f) && view.getWidth() > 0 && view.getHeight() > 0) {
            view.setRenderEffect(effect(view, k));
            applied = k;
        }
    }

    private void remove() {
        if (Build.VERSION.SDK_INT >= 31 && applied >= 0 && view != null) {
            view.setRenderEffect(null);
        }
        applied = -1f;
    }

    @RequiresApi(31)
    private static RenderEffect effect(View view, float k) {
        if (Build.VERSION.SDK_INT >= 33 && !shaderFailed) {
            try {
                return liquid(view, k);
            } catch (Exception e) {
                shaderFailed = true;
                FileLog.e(e);
            }
        }
        final float radius = AndroidUtilities.dpf2(20) * k;
        return RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP);
    }

    @RequiresApi(33)
    private static RenderEffect liquid(View view, float k) {
        if (shader == null) {
            shader = new RuntimeShader(AndroidUtilities.readRes(R.raw.glass_touch_back_shader));
        }
        // an effect keeps the uniform values it was created with, so one shader serves every veil
        final RuntimeShader liquid = (RuntimeShader) shader;
        liquid.setFloatUniform("size", view.getWidth(), view.getHeight());
        liquid.setFloatUniform("p", k);
        final float radius = AndroidUtilities.dpf2(9) * k;
        // the inner effect runs first: blur, then the shader
        return RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(liquid, "img"),
                RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
    }
}
