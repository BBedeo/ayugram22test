package app.exteraless.backup;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import tw.nekomimi.nekogram.helpers.SettingsBackupHelper;
import tw.nekomimi.nekogram.utils.GsonUtil;

/**
 * Settings transfer between two installs of the app (the old debug-signed one and the new
 * release-signed one). The backup is a plain JSON file in the public Downloads folder: it
 * survives uninstalling the app and the new install looks for it there by name, wherever the
 * user keeps the phone's files. It reuses {@link SettingsBackupHelper} for the whole settings
 * family and adds every other preference file of the mod (per-account profiles, notifications, emoji, ...). It never contains Telegram sessions,
 * the app lock or chat databases, and it leaves out API keys and tokens: the file sits in a
 * folder that other apps with storage access can read.
 */
public final class MigrationBackup {

    public static final String FILE_PREFIX = "ExFullGram-settings-";
    public static final String FILE_SUFFIX = ".json";

    private static final String META_KEY = "exfullgram_backup_meta";
    private static final String PROFILES_KEY = "exfullgram_extra_prefs";
    // Every preference file of the mod is carried over by name family, so settings added later are
    // included without touching this class. nkmrcfg/mainconfig/themeconfig go through SettingsBackupHelper.
    private static final String[] MOD_PREFS_FAMILIES = {"exfullgram_", "exteraless_", "oe_", "neko", "video_external_subtitles"};
    // Not settings: tokens and grants, remote-config caches, install state, plugin state without
    // the plugin files, Test Mode data and the migration marker itself.
    private static final Set<String> MOD_PREFS_DENY = new HashSet<>(java.util.Arrays.asList(
            "nekopasscode", "nekoremoteconfig", "nekocloud", "exfullgram_migration", "exfullgram_demo",
            "exteraless_updater", "exteraless_plugins", "exteraless_plugin_grants",
            "exteraless_plugin_foreign_settings"));
    private static final String PREFS = "exfullgram_migration";
    private static final String KEY_RESTORE_CHECKED = "restore_checked";
    private static final Set<String> ALLOWED_SECTIONS = new HashSet<>(java.util.Arrays.asList(
            "mainconfig", "themeconfig", "nkmrcfg", app.exteraless.utils.FaveStickersBackup.KEY));
    private static final int MAX_BYTES = 16 * 1024 * 1024;

    private MigrationBackup() {
    }

    /** A backup file found on the device. */
    public static final class Candidate {
        public Uri uri;
        public File file;
        public String name;
        public long modified;
    }

    /** A file being written into the public Downloads folder. */
    public static final class Sink implements Closeable {
        public final OutputStream stream;
        public final String name;
        private final Uri uri;
        private final ContentResolver resolver;
        private boolean finished;

        private Sink(OutputStream stream, String name, Uri uri, ContentResolver resolver) {
            this.stream = stream;
            this.name = name;
            this.uri = uri;
            this.resolver = resolver;
        }

        /** Makes the file visible to other apps; call after the last byte was written. */
        public void finish() throws IOException {
            stream.flush();
            stream.close();
            if (uri != null && resolver != null) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                resolver.update(uri, values, null, null);
            }
            finished = true;
        }

        @Override
        public void close() {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
            if (!finished && uri != null && resolver != null) {
                try {
                    resolver.delete(uri, null, null);
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static String buildJson() throws Exception {
        JSONObject root = new JSONObject(SettingsBackupHelper.backupSettingsJson(false, 0, false));
        // The helper also dumps the app lock hash (without its salt); that must never leave the device.
        root.remove("userconfing");
        root.remove("aiConfig");
        JSONObject meta = new JSONObject();
        meta.put("app", "ExFullGram");
        meta.put("version", BuildVars.BUILD_VERSION_STRING);
        meta.put("created", System.currentTimeMillis());
        root.put(META_KEY, meta);
        root.put(PROFILES_KEY, profilesToJson());
        return root.toString();
    }

    /** Writes a fresh backup into Downloads and returns its file name. */
    public static String exportToDownloads(Context context) throws Exception {
        byte[] data = buildJson().getBytes(StandardCharsets.UTF_8);
        String name = FILE_PREFIX + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + FILE_SUFFIX;
        try (Sink sink = openDownload(context, name, "application/json")) {
            sink.stream.write(data);
            sink.finish();
        }
        return name;
    }

    /** Opens a new file in the public Downloads folder (no storage permission needed on 10+). */
    public static Sink openDownload(Context context, String name, String mime) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new IOException("Downloads insert failed");
            }
            OutputStream stream = resolver.openOutputStream(uri);
            if (stream == null) {
                resolver.delete(uri, null, null);
                throw new IOException("Downloads stream failed");
            }
            String actualName = name;
            try (Cursor cursor = resolver.query(uri, new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst() && cursor.getString(0) != null) {
                    actualName = cursor.getString(0);
                }
            } catch (Exception ignored) {
            }
            return new Sink(stream, actualName, uri, resolver);
        }
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Downloads folder unavailable");
        }
        return new Sink(new FileOutputStream(new File(dir, name)), name, null, null);
    }

    /** Newest backup in Downloads, or null. Only looks by name, so the file may be renamed after the prefix. */
    public static Candidate findLatest(Context context) {
        Candidate best = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try (Cursor cursor = context.getContentResolver().query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATE_MODIFIED},
                    MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ?",
                    new String[]{FILE_PREFIX + "%"}, null)) {
                while (cursor != null && cursor.moveToNext()) {
                    Candidate candidate = new Candidate();
                    candidate.uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(0));
                    candidate.name = cursor.getString(1);
                    candidate.modified = cursor.getLong(2) * 1000L;
                    if (best == null || candidate.modified > best.modified) {
                        best = candidate;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            File[] files = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).listFiles(
                    (dir, n) -> n.startsWith(FILE_PREFIX) && n.endsWith(FILE_SUFFIX));
            if (files != null) {
                for (File file : files) {
                    if (best == null || file.lastModified() > best.modified) {
                        Candidate candidate = new Candidate();
                        candidate.file = file;
                        candidate.name = file.getName();
                        candidate.modified = file.lastModified();
                        best = candidate;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return best;
    }

    public static String read(Context context, Candidate candidate) throws IOException {
        if (candidate.uri != null) {
            return read(context, candidate.uri);
        }
        try (InputStream input = new java.io.FileInputStream(candidate.file)) {
            return readAll(input);
        }
    }

    public static String read(Context context, Uri uri) throws IOException {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IOException("Cannot open " + uri);
            }
            return readAll(input);
        }
    }

    private static String readAll(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int read;
        while ((read = input.read(buffer)) > 0) {
            out.write(buffer, 0, read);
            if (out.size() > MAX_BYTES) {
                throw new IOException("Backup is too large");
            }
        }
        return out.toString("UTF-8");
    }

    /** Applies a backup. The caller restarts the app afterwards, as with the regular import. */
    public static void restore(String text) throws Exception {
        JsonObject json = GsonUtil.toJsonObject(text);
        if (!json.has(META_KEY)) {
            throw new IllegalArgumentException("Not an ExFullGram settings backup");
        }
        json.remove(META_KEY);
        // Only the settings families we export are accepted; anything else in a foreign file is dropped.
        for (String key : new java.util.ArrayList<>(json.keySet())) {
            if (!ALLOWED_SECTIONS.contains(key) && !key.equals(PROFILES_KEY)) {
                json.remove(key);
            }
        }
        JsonElement profiles = json.remove(PROFILES_KEY);
        SettingsBackupHelper.importSettings(json);
        if (profiles != null && profiles.isJsonObject()) {
            restoreProfiles(profiles.getAsJsonObject());
        }
    }

    /** Offer the restore only once, and only on a fresh install that has no account yet. */
    public static boolean shouldOfferRestore(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return !prefs.getBoolean(KEY_RESTORE_CHECKED, false);
    }

    public static void markRestoreChecked(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_RESTORE_CHECKED, true).commit();
    }

    private static boolean isModPrefs(String name) {
        if (MOD_PREFS_DENY.contains(name)) {
            return false;
        }
        for (String family : MOD_PREFS_FAMILIES) {
            if (name.startsWith(family)) {
                return true;
            }
        }
        return false;
    }

    private static JSONObject profilesToJson() throws Exception {
        Context context = ApplicationLoader.applicationContext;
        JSONObject result = new JSONObject();
        File[] files = new File(context.getApplicationInfo().dataDir, "shared_prefs").listFiles(
                (dir, n) -> n.endsWith(".xml") && isModPrefs(n.substring(0, n.length() - 4)));
        if (files == null) {
            return result;
        }
        for (File file : files) {
            String name = file.getName().substring(0, file.getName().length() - 4);
            JSONObject values = new JSONObject();
            for (Map.Entry<String, ?> entry : context.getSharedPreferences(name, Context.MODE_PRIVATE).getAll().entrySet()) {
                Object value = entry.getValue();
                String key = entry.getKey();
                if (value instanceof Long) {
                    values.put(key + "_long", value);
                } else if (value instanceof Float) {
                    values.put(key + "_float", (double) (Float) value);
                } else if (value instanceof Set) {
                    JSONArray array = new JSONArray();
                    for (Object item : (Set<?>) value) {
                        array.put(String.valueOf(item));
                    }
                    values.put(key + "_set", array);
                } else if (value != null) {
                    values.put(key, value);
                }
            }
            result.put(name, values);
        }
        return result;
    }

    private static void restoreProfiles(JsonObject profiles) {
        Context context = ApplicationLoader.applicationContext;
        for (Map.Entry<String, JsonElement> file : profiles.entrySet()) {
            String name = file.getKey();
            if (!isModPrefs(name) || !name.matches("[A-Za-z0-9_]+") || !file.getValue().isJsonObject()) {
                continue;
            }
            try {
                restoreProfile(context, name, file.getValue().getAsJsonObject());
            } catch (Exception ignored) {
                // A malformed profile must not undo the settings that were already applied.
            }
        }
    }

    private static void restoreProfile(Context context, String name, JsonObject values) {
        {
            SharedPreferences.Editor editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear();
            for (Map.Entry<String, JsonElement> item : values.entrySet()) {
                String key = item.getKey();
                JsonElement value = item.getValue();
                if (key.endsWith("_set") && value.isJsonArray()) {
                    Set<String> set = new HashSet<>();
                    for (JsonElement element : value.getAsJsonArray()) {
                        if (element.isJsonPrimitive()) {
                            set.add(element.getAsString());
                        }
                    }
                    editor.putStringSet(key.substring(0, key.length() - 4), set);
                } else if (!value.isJsonPrimitive()) {
                    continue;
                } else if (value.getAsJsonPrimitive().isBoolean()) {
                    editor.putBoolean(key, value.getAsBoolean());
                } else if (value.getAsJsonPrimitive().isNumber()) {
                    if (key.endsWith("_long")) {
                        editor.putLong(key.substring(0, key.length() - 5), value.getAsLong());
                    } else if (key.endsWith("_float")) {
                        editor.putFloat(key.substring(0, key.length() - 6), value.getAsFloat());
                    } else {
                        editor.putInt(key, value.getAsInt());
                    }
                } else {
                    editor.putString(key, value.getAsString());
                }
            }
            editor.commit();
        }
    }
}
