package app.exteraless.appearance.glass;

import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;

import androidx.annotation.RequiresApi;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;

/**
 * Glass over the previous screen during the back gesture (Android 12+): frosted, on 13+ also bent like a lens, clearing
 * as the screen is uncovered. It adds no animator; the layout feeds it the position its own animations produce.
 */
public final class GlassBackBlur {

    private View view;
    private float applied = -1f; // strength of the effect set on the view, -1 when there is none
    private Object shader;       // RuntimeShader, Android 13+; typed loosely so older runtimes never resolve it
    private boolean shaderFailed;

    /** Starts with the previous screen of a back gesture; {@link #disarm()} must follow before it is shown. */
    public void arm(View behind) {
        disarm();
        if (Build.VERSION.SDK_INT >= 31 && GlassTouchSettings.back()) {
            view = behind;
        }
    }

    /** {@code q}: share of the previous screen that is uncovered, 0..1. */
    public void update(float q) {
        if (Build.VERSION.SDK_INT < 31 || view == null) {
            return;
        }
        final float k = GlassTouchMath.backStrength(q);
        if (k < 0.015f) {
            remove();
        } else if ((applied < 0 || Math.abs(k - applied) > 0.012f) && view.getWidth() > 0 && view.getHeight() > 0) {
            view.setRenderEffect(effect(k));
            applied = k;
        }
    }

    public void disarm() {
        remove();
        view = null;
    }

    private void remove() {
        if (Build.VERSION.SDK_INT >= 31 && applied >= 0 && view != null) {
            view.setRenderEffect(null);
        }
        applied = -1f;
    }

    @RequiresApi(31)
    private RenderEffect effect(float k) {
        if (Build.VERSION.SDK_INT >= 33 && !shaderFailed) {
            try {
                return liquid(k);
            } catch (Exception e) {
                shaderFailed = true;
                FileLog.e(e);
            }
        }
        final float radius = AndroidUtilities.dpf2(20) * k;
        return RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP);
    }

    @RequiresApi(33)
    private RenderEffect liquid(float k) {
        if (shader == null) {
            shader = new RuntimeShader(AndroidUtilities.readRes(R.raw.glass_touch_back_shader));
        }
        final RuntimeShader liquid = (RuntimeShader) shader;
        liquid.setFloatUniform("size", view.getWidth(), view.getHeight());
        liquid.setFloatUniform("p", k);
        final float radius = AndroidUtilities.dpf2(9) * k;
        // the inner effect runs first: blur, then the shader
        return RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(liquid, "img"),
                RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
    }
}
