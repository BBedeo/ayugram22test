package app.exteraless.appicons;

import org.telegram.messenger.R;
import org.telegram.ui.LauncherIconController;
import org.telegram.ui.LauncherIconController.LauncherIcon;

/**
 * Small icon of notifications and of the status bar: follows the app icon the user picked in the
 * settings. Small icons must be one-colored, so each icon maps to its monochrome silhouette (the
 * same one the system uses for themed icons); icons without their own silhouette use the default.
 */
public final class NotificationIconHelper {

    private NotificationIconHelper() {
    }

    public static int currentSmallIcon() {
        try {
            for (LauncherIcon icon : LauncherIcon.values()) {
                if (LauncherIconController.isEnabled(icon)) {
                    switch (icon) {
                        case AYU:
                            return R.drawable.notification_ayu;
                        case NYA:
                            return R.drawable.notification_nya;
                        case QUACK:
                            return R.drawable.notification_quack;
                        default:
                            return R.drawable.notification_exteraless;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return R.drawable.notification_exteraless;
    }
}
