package app.exteraless.appearance.glass;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.os.Build;
import android.view.View;
import android.widget.Magnifier;
import android.widget.TextView;

import org.telegram.messenger.FileLog;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * The text loupe in the shape of the glass pills: a wide capsule with a soft shadow. It is still the system
 * {@link Magnifier} (own windows or overlays make some vendor shells react), only built with another shape.
 */
public final class GlassMagnifier {

    private GlassMagnifier() {
    }

    public static boolean enabled() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && GlassTokens.isActive();
    }

    public static Magnifier create(View view) {
        final Magnifier.Builder builder = new Magnifier.Builder(view)
                .setSize(dp(150), dp(56))
                .setInitialZoom(1.3f)
                .setCornerRadius(dp(28))
                .setElevation(dp(8));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // hairline rim like the glass pills, drawn over the magnified content
            final android.graphics.drawable.GradientDrawable rim = new android.graphics.drawable.GradientDrawable();
            rim.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            rim.setCornerRadius(dp(28));
            rim.setColor(0x14ffffff);
            rim.setStroke(Math.max(1, dp(1)), 0x99ffffff);
            builder.setOverlay(rim);
        }
        return builder.build();
    }

    /** Puts the capsule loupe into the field's Editor in place of the stock one. Best effort: on failure the stock loupe stays. */
    public static void styleField(TextView view) {
        try {
            org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("Landroid/widget/");
            final Field editorField = TextView.class.getDeclaredField("mEditor");
            editorField.setAccessible(true);
            final Object editor = editorField.get(view);
            if (editor == null) {
                return;
            }
            final Field animatorField = editor.getClass().getDeclaredField("mMagnifierAnimator");
            animatorField.setAccessible(true);
            final Class<?> animatorClass = Class.forName("android.widget.Editor$MagnifierMotionAnimator");
            final Constructor<?> ctor = animatorClass.getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            final Magnifier loupe = create(view);
            animatorField.set(editor, ctor.getParameterTypes().length == 2 ? ctor.newInstance(editor, loupe) : ctor.newInstance(loupe));
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
}
