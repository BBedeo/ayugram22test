package app.exteraless.appearance.glass;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;

import app.exteraless.appearance.AppearanceConfig;
import app.exteraless.appearance.SheetSurfaceMode;

/** The switches of Glass Touch and the one gate they share; read at the start of each effect, never cached. */
public final class GlassTouchSettings {

    private GlassTouchSettings() {
    }

    /** Material 3 is a separate design with its own motion; power saver and disabled animations pause the effects. */
    public static boolean allowed() {
        return !SheetSurfaceMode.isM3(null)
                && !LiteMode.isPowerSaverApplied()
                && AndroidUtilities.getAnimatorDurationScale() > 0f;
    }

    public static boolean press() {
        return AppearanceConfig.isGlassTouchPress() && allowed();
    }

    public static boolean selection() {
        return AppearanceConfig.isGlassTouchSelection() && allowed();
    }

    public static boolean back() {
        return AppearanceConfig.isGlassTouchBack() && allowed();
    }

    public static boolean pages() {
        return AppearanceConfig.isGlassTouchPages() && allowed();
    }
}
