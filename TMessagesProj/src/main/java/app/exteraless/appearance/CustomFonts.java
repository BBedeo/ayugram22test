package app.exteraless.appearance;

import android.graphics.Typeface;
import android.os.Build;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Fonts the user imported in Appearance settings: stored in files/fonts, one is active (AppearanceConfig.customFont). */
public final class CustomFonts {

    private static String cachedName;
    private static Typeface cachedBase;

    private CustomFonts() {
    }

    private static File dir() {
        File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "fonts");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static List<String> list() {
        String[] names = dir().list((d, n) -> {
            String l = n.toLowerCase();
            return l.endsWith(".ttf") || l.endsWith(".otf") || l.endsWith(".ttc");
        });
        if (names == null) {
            return new ArrayList<>();
        }
        Arrays.sort(names, String.CASE_INSENSITIVE_ORDER);
        return new ArrayList<>(Arrays.asList(names));
    }

    public static String active() {
        String name = AppearanceConfig.customFont.String();
        return name != null && !name.isEmpty() && new File(dir(), name).isFile() ? name : "";
    }

    /** Copies the stream into files/fonts and checks that Android can load it; returns the stored name, or null. */
    public static String importFont(InputStream in, String displayName) {
        String safe = displayName == null ? "font.ttf" : displayName.replaceAll("[^\\p{L}\\p{N}._ -]", "_");
        String lower = safe.toLowerCase();
        if (!lower.endsWith(".ttf") && !lower.endsWith(".otf") && !lower.endsWith(".ttc")) {
            safe += ".ttf";
        }
        File target = new File(dir(), safe);
        try (FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[16384];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
        } catch (Exception e) {
            FileLog.e(e);
            target.delete();
            return null;
        }
        try {
            Typeface.createFromFile(target);
        } catch (Exception e) {
            target.delete();
            return null;
        }
        return safe;
    }

    public static void delete(String name) {
        new File(dir(), name).delete();
        if (name.equals(AppearanceConfig.customFont.String())) {
            select("");
        }
    }

    public static void select(String name) {
        AppearanceConfig.customFont.setConfigString(name);
        cachedName = null;
        cachedBase = null;
        AndroidUtilities.clearTypefaceCache();
    }

    private static Typeface base() {
        String name = active();
        if (name.isEmpty()) {
            return null;
        }
        if (!name.equals(cachedName)) {
            cachedName = name;
            try {
                cachedBase = Typeface.createFromFile(new File(dir(), name));
            } catch (Exception e) {
                FileLog.e(e);
                cachedBase = null;
            }
        }
        return cachedBase;
    }

    private static Typeface weight(Typeface base, int weight, boolean italic) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Typeface.create(base, weight, italic);
        }
        return Typeface.create(base, weight >= 600 ? (italic ? Typeface.BOLD_ITALIC : Typeface.BOLD) : (italic ? Typeface.ITALIC : Typeface.NORMAL));
    }

    /** The imported font in the weight/slant Telegram asks for under {@code assetPath}, or null to use the stock font. */
    public static Typeface create(String assetPath) {
        Typeface base = base();
        if (base == null) {
            return null;
        }
        switch (assetPath) {
            case AndroidUtilities.TYPEFACE_ROBOTO_REGULAR:
                return base;
            case AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM:
            case AndroidUtilities.TYPEFACE_RCONDENSED_BOLD:
                return weight(base, 700, false);
            case AndroidUtilities.TYPEFACE_ROBOTO_EXTRA_BOLD:
                return weight(base, 800, false);
            case AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM_ITALIC:
                return weight(base, 700, true);
            case AndroidUtilities.TYPEFACE_RITALIC:
                return weight(base, 400, true);
            default:
                return null; // mono and the rest keep their own fonts
        }
    }
}
