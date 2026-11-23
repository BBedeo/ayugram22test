package app.exteraless.updater;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import tw.nekomimi.nekogram.helpers.remote.UpdateHelper;
import xyz.nextalone.nagram.NaConfig;

public final class GitHubUpdater {

    // Обновления раздаются только из собственного репозитория форка: upstream
    // (exteraless/exteraless) отдаёт чужой APK с другим пакетом и другой подписью —
    // установка поверх ExFullGram падала с INSTALL_FAILED_UPDATE_INCOMPATIBLE,
    // а баннер предлагал «обновление» другого приложения.
    private static final String REPO = "BBedeo/ayugram22test";
    public static final String RELEASES_URL = "https://github.com/" + REPO + "/releases";
    private static final String API = "https://api.github.com/repos/" + REPO;
    private static final long AUTO_INTERVAL = TimeUnit.HOURS.toMillis(6);
    private static final String PREFS = "exteraless_updater";
    private static final String KEY_LAST_CHECK = "last_check";
    private static final String KEY_SKIPPED = "skipped_tag";
    private static final String KEY_RELAUNCH = "relaunch_after_update";
    private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");
    // Релизы CI помечаются тегом ci-<sha> и собираются рабочим процессом без истории
    // коммитов: их versionName — базовый, подпись — ci-ключ. Ставить такой APK поверх
    // локальной сборки нельзя (INSTALL_FAILED_UPDATE_INCOMPATIBLE), а в списке релизов
    // они стоят сверху и заслоняют настоящий. Поэтому обновлением считается только
    // релиз с версионным тегом.
    private static final Pattern RELEASE_TAG = Pattern.compile("^v?\\d+\\.\\d+\\.\\d+");

    private static volatile OkHttpClient client;
    private static volatile boolean checking;
    private static volatile Call download;

    private GitHubUpdater() {
    }

    public static final class Release {
        public String tag;
        public String name;
        public String body;
        public String apkUrl;
        public String apkName;
        public long apkSize;
        public long published;
        /** Release notes carry `exfullgram:backup`: offer the settings export before updating. */
        public boolean backupFirst;
        /** Release notes carry `exfullgram:resign`: signed with another key, needs remove + reinstall. */
        public boolean resign;

        public String displayVersion() {
            String value = TextUtils.isEmpty(tag) ? name : tag;
            return value != null && value.startsWith("v") ? value.substring(1) : value;
        }
    }

    public interface CheckCallback {
        void onChecked(Release release, Boolean newer, boolean failed);
    }

    public interface DownloadListener {
        void onProgress(long done, long total);

        void onFinished(boolean success, boolean canceled);
    }

    private static OkHttpClient client() {
        if (client == null) {
            synchronized (GitHubUpdater.class) {
                if (client == null) {
                    client = new OkHttpClient.Builder()
                            .connectTimeout(20, TimeUnit.SECONDS)
                            .readTimeout(60, TimeUnit.SECONDS)
                            .build();
                }
            }
        }
        return client;
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void check(boolean force) {
        int channel = NaConfig.INSTANCE.getAutoUpdateChannel().Int();
        if (!force) {
            if (channel == UpdateHelper.UPDATE_OFF) {
                return;
            }
            if (Math.abs(System.currentTimeMillis() - lastCheck()) < AUTO_INTERVAL) {
                return;
            }
        }
        if (checking) {
            return;
        }
        checking = true;
        fetch((found, isNewer, failed) -> {
            checking = false;
            boolean offer = found != null && (Boolean.TRUE.equals(isNewer) || force && isNewer == null);
            if (offer && (force || !TextUtils.equals(found.tag, prefs().getString(KEY_SKIPPED, null)))) {
                show(found);
            } else if (force) {
                bulletin(failed
                        ? LocaleController.getString(R.string.OEUpdateCheckFailed)
                        : LocaleController.getString(R.string.YourVersionIsLatestNax), failed);
            }
        });
    }

    public static void fetch(CheckCallback callback) {
        final boolean prerelease = NaConfig.INSTANCE.getAutoUpdateChannel().Int() == UpdateHelper.UPDATE_CHANNEL_BETA;
        new Thread(() -> {
            Release release = null;
            Boolean newer = null;
            boolean failed = false;
            try {
                release = latest(prerelease);
                if (release != null) {
                    newer = isNewer(release);
                }
                prefs().edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();
            } catch (Exception e) {
                failed = true;
                FileLog.e("GitHubUpdater: check failed", e);
            }
            final Release found = release;
            final Boolean isNewer = newer;
            final boolean error = failed;
            AndroidUtilities.runOnUIThread(() -> callback.onChecked(found, isNewer, error));
        }, "gh-updater").start();
    }

    public static long lastCheck() {
        return prefs().getLong(KEY_LAST_CHECK, 0);
    }

    public static boolean isDownloading() {
        return download != null;
    }

    public static void cancelDownload() {
        Call call = download;
        if (call != null) {
            call.cancel();
        }
    }

    private static void bulletin(String text, boolean error) {
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment == null) {
            return;
        }
        if (error) {
            BulletinFactory.of(fragment).createErrorBulletin(text).show();
        } else {
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.done, text).show();
        }
    }

    private static JSONObject getJson(String url) throws Exception {
        Request request = new Request.Builder().url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "ExFullGram")
                .build();
        try (Response response = client().newCall(request).execute()) {
            ResponseBody body = response.body();
            if (response.code() == 404) {
                return null;
            }
            if (!response.isSuccessful() || body == null) {
                throw new IllegalStateException("HTTP " + response.code());
            }
            String text = body.string();
            return text.trim().startsWith("[") ? new JSONObject().put("items", new JSONArray(text)) : new JSONObject(text);
        }
    }

    /**
     * Стабильный канал спрашивает {@code /releases/latest}: CI кладёт по релизу на коммит, и
     * десяток верхних записей — сплошные {@code ci-*} пре-релизы, из-за чего настоящий релиз
     * в выборку не попадал вовсе. Бета дополнительно читает список, но тоже только версионные
     * теги: APK из CI собран без истории коммитов и подписан ключом раннера, ставить его
     * поверх локальной сборки нельзя.
     */
    private static Release latest(boolean prerelease) throws Exception {
        if (!prerelease) {
            Release release = parseRelease(getJson(API + "/releases/latest"));
            if (release != null) {
                return release;
            }
        }
        JSONObject wrapper = getJson(API + "/releases?per_page=30");
        JSONArray releases = wrapper == null ? null : wrapper.optJSONArray("items");
        if (releases == null) {
            return null;
        }
        for (int i = 0; i < releases.length(); i++) {
            JSONObject item = releases.optJSONObject(i);
            if (item == null || item.optBoolean("draft") || item.optBoolean("prerelease") && !prerelease) {
                continue;
            }
            Release release = parseRelease(item);
            if (release != null) {
                return release;
            }
        }
        return null;
    }

    private static Release parseRelease(JSONObject item) {
        if (item == null) {
            return null;
        }
        String tag = item.optString("tag_name");
        if (!RELEASE_TAG.matcher(tag).find()) {
            return null;
        }
        JSONObject asset = pickAsset(item.optJSONArray("assets"));
        if (asset == null) {
            return null;
        }
        Release release = new Release();
        release.tag = tag;
        release.name = item.optString("name", tag);
        release.body = item.optString("body", "");
        release.apkUrl = asset.optString("browser_download_url");
        release.apkName = asset.optString("name");
        release.apkSize = asset.optLong("size");
        release.published = parseDate(item.optString("published_at"));
        release.resign = release.body.contains("exfullgram:resign");
        release.backupFirst = release.resign || release.body.contains("exfullgram:backup");
        return release;
    }

    private static JSONObject pickAsset(JSONArray assets) {
        if (assets == null) {
            return null;
        }
        JSONObject universal = null;
        JSONObject any = null;
        String abi = Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "";
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            String name = asset == null ? "" : asset.optString("name").toLowerCase(Locale.ROOT);
            if (!name.endsWith(".apk")) {
                continue;
            }
            if (!abi.isEmpty() && name.contains(abi.toLowerCase(Locale.ROOT))) {
                return asset;
            }
            if (name.contains("universal") && universal == null) {
                universal = asset;
            }
            if (any == null && !name.contains("armeabi") && !name.contains("x86")) {
                any = asset;
            }
        }
        return universal != null ? universal : any;
    }

    private static int[] version(String... sources) {
        for (String source : sources) {
            if (source == null) {
                continue;
            }
            Matcher matcher = VERSION.matcher(source);
            if (matcher.find()) {
                return new int[]{Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3))};
            }
        }
        return null;
    }

    private static Integer compareVersion(Release release) {
        int[] remote = version(release.tag, release.name, release.apkName);
        int[] local = version(BuildVars.BUILD_VERSION_STRING);
        if (remote == null || local == null) {
            return null;
        }
        for (int i = 0; i < 3; i++) {
            if (remote[i] != local[i]) {
                return Integer.compare(remote[i], local[i]);
            }
        }
        return 0;
    }

    private static Boolean isNewer(Release release) throws Exception {
        Integer versionOrder = compareVersion(release);
        if (versionOrder != null && versionOrder != 0) {
            return versionOrder > 0;
        }
        String commit = BuildConfig.BUILD_COMMIT_ID;
        if (!TextUtils.isEmpty(commit)) {
            JSONObject compare = getJson(API + "/compare/" + commit + "..." + Uri.encode(release.tag));
            if (compare != null) {
                return compare.optInt("ahead_by", 0) > 0;
            }
        }
        if (BuildConfig.BUILD_TIMESTAMP > 0) {
            JSONObject item = getJson(API + "/releases/tags/" + Uri.encode(release.tag));
            long published = item == null ? 0 : parseDate(item.optString("published_at"));
            return published > BuildConfig.BUILD_TIMESTAMP * 1000L;
        }
        return versionOrder != null ? Boolean.FALSE : null;
    }

    private static long parseDate(String value) {
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            return format.parse(value).getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    // Всё, что не является обычным текстом, переносим как есть: адрес ссылки «exteraless/
    // exteraless» после замены бренда вёл бы в несуществующий репозиторий, а имя в
    // <autolink> или в `коде` — часть синтаксиса, а не подпись.
    private static final Pattern BRAND_TOKEN = Pattern.compile(
            "\\[([^\\]]*)]\\((https?://[^)\\s]+)\\)"   // [label](url)
                    + "|<(https?://[^>\\s]+)>"          // <autolink>
                    + "|`[^`]*`"                          // inline code
                    + "|https?://\\S+");                 // bare url
    private static final Pattern UPSTREAM_BRAND = Pattern.compile("(?i)exteraless|extera\\s*gram|extera\\b");
    /** Тело релиза на GitHub — до 125 КБ; разбор регулярным выражением по такому тексту стоит секунд. */
    private static final int NOTES_LIMIT = 8000;

    /**
     * В тексте релиза с апстрима остаётся чужое имя — в листе обновления его быть не должно.
     * Ссылки и код при этом не трогаем: иначе адрес превращается в битую ссылку и ведёт в никуда.
     */
    private static String stripUpstreamBrand(String text) {
        if (text.indexOf(']') < 0 && text.indexOf("http") < 0) {
            return sanitizeBrand(text);
        }
        StringBuilder out = new StringBuilder(text.length());
        Matcher token = BRAND_TOKEN.matcher(text);
        int last = 0;
        while (token.find()) {
            out.append(sanitizeBrand(text.substring(last, token.start())));
            if (token.group(1) != null) {
                out.append('[').append(sanitizeBrand(token.group(1))).append("](")
                        .append(token.group(2)).append(')');
            } else {
                out.append(token.group(0));
            }
            last = token.end();
        }
        out.append(sanitizeBrand(text.substring(last)));
        return out.toString();
    }

    private static String sanitizeBrand(String text) {
        return UPSTREAM_BRAND.matcher(text).replaceAll("ExFullGram");
    }

    private static void show(Release release) {
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        Activity activity = fragment == null ? null : fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        String size = release.apkSize > 0 ? AndroidUtilities.formatFileSize(release.apkSize) : "";
        int[] remote = version(release.tag, release.name, release.apkName);
        // Имя релиза приходит с GitHub и раньше протекало в подзаголовок чужой сборкой
        // («exteraGram 13.x»). Подпись собираем сами: имя приложения, версия, размер.
        StringBuilder subtitle = new StringBuilder(LocaleController.getAppName());
        if (remote != null) {
            subtitle.append(' ').append(remote[0]).append('.').append(remote[1]).append('.').append(remote[2]);
        } else if (!TextUtils.isEmpty(release.displayVersion())) {
            subtitle.append(' ').append(release.displayVersion());
        }
        if (!size.isEmpty()) {
            subtitle.append(" · ").append(size);
        }
        String body = release.body;
        if (body != null) {
            // Markers for the app live in HTML comments; they are not part of the text shown to people.
            body = body.replaceAll("(?s)<!--.*?(-->|$)", "").trim();
        }
        if (body != null && body.length() > NOTES_LIMIT) {
            body = body.substring(0, NOTES_LIMIT) + "…";
        }
        String notes = TextUtils.isEmpty(body) ? release.tag : stripUpstreamBrand(body);
        if (release.resign) {
            new MigrationSheet(activity, fragment.getResourceProvider(), release, subtitle.toString(),
                    TextUtils.isEmpty(body) ? "" : notes).show();
            return;
        }
        String updateText = size.isEmpty() ? LocaleController.getString(R.string.OEUpdateInstall)
                : LocaleController.formatString(R.string.OEUpdateInstallSize, size);
        new UpdateSheet(activity, fragment.getResourceProvider(), LocaleController.getString(R.string.OEUpdateTitle),
                subtitle.toString(), notes, updateText, release.backupFirst, new UpdateSheet.Delegate() {
            @Override
            public void onUpdate(UpdateSheet sheet) {
                sheet.setDownloading(true);
                download(activity, release, new DownloadListener() {
                    @Override
                    public void onProgress(long done, long total) {
                        sheet.setProgress(done, total);
                    }

                    @Override
                    public void onFinished(boolean success, boolean canceled) {
                        if (success) {
                            sheet.finishDownload();
                        } else if (!canceled) {
                            sheet.setDownloading(false);
                        }
                    }
                });
            }

            @Override
            public void onSkip() {
                prefs().edit().putString(KEY_SKIPPED, release.tag).apply();
            }

            @Override
            public void onCancelDownload() {
                cancelDownload();
            }
        }).show();
    }

    public static void download(Activity activity, Release release, DownloadListener listener) {
        if (download != null) {
            return;
        }
        File dir = new File(activity.getCacheDir(), "updates");
        if (!dir.exists() && !dir.mkdirs()) {
            bulletin(LocaleController.getString(R.string.OEUpdateDownloadFailed), true);
            listener.onFinished(false, false);
            return;
        }
        File[] old = dir.listFiles();
        if (old != null) {
            for (File file : old) {
                file.delete();
            }
        }
        File target = new File(dir, release.apkName.replaceAll("[^A-Za-z0-9._-]", "_"));
        Call call = client().newCall(new Request.Builder().url(release.apkUrl)
                .header("User-Agent", "ExFullGram").build());
        download = call;
        new Thread(() -> {
            boolean ok = false;
            try (Response response = call.execute()) {
                ResponseBody body = response.body();
                if (!response.isSuccessful() || body == null) {
                    throw new IllegalStateException("HTTP " + response.code());
                }
                long total = body.contentLength() > 0 ? body.contentLength() : release.apkSize;
                try (InputStream input = body.byteStream(); OutputStream output = new FileOutputStream(target)) {
                    byte[] buffer = new byte[64 * 1024];
                    long done = 0;
                    int lastPercent = -1;
                    int read;
                    while ((read = input.read(buffer)) > 0) {
                        output.write(buffer, 0, read);
                        done += read;
                        if (total > 0) {
                            int percent = (int) (done * 100 / total);
                            if (percent != lastPercent) {
                                lastPercent = percent;
                                final long doneBytes = done;
                                AndroidUtilities.runOnUIThread(() -> listener.onProgress(doneBytes, total));
                            }
                        }
                    }
                }
                ok = true;
            } catch (Exception e) {
                if (!call.isCanceled()) {
                    FileLog.e("GitHubUpdater: download failed", e);
                }
            }
            final boolean success = ok;
            AndroidUtilities.runOnUIThread(() -> {
                download = null;
                if (success) {
                    listener.onFinished(true, false);
                    install(activity, target);
                } else {
                    target.delete();
                    listener.onFinished(false, call.isCanceled());
                    if (!call.isCanceled()) {
                        bulletin(LocaleController.getString(R.string.OEUpdateDownloadFailed), true);
                    }
                }
            });
        }, "gh-updater-download").start();
    }


    /**
     * Downloads the release APK into the public Downloads folder (for the key-change flow, where
     * the system installer cannot replace the old app until it is removed).
     */
    public static void downloadToDownloads(Activity activity, Release release, DownloadListener listener,
                                           java.util.function.Consumer<String> saved) {
        if (download != null) {
            return;
        }
        String name = "ExFullGram-" + (TextUtils.isEmpty(release.displayVersion()) ? "update" : release.displayVersion()) + ".apk";
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        final String fileName = name;
        Call call = client().newCall(new Request.Builder().url(release.apkUrl)
                .header("User-Agent", "ExFullGram").build());
        download = call;
        new Thread(() -> {
            String savedName = null;
            try (Response response = call.execute()) {
                ResponseBody body = response.body();
                if (!response.isSuccessful() || body == null) {
                    throw new IllegalStateException("HTTP " + response.code());
                }
                long total = body.contentLength() > 0 ? body.contentLength() : release.apkSize;
                try (InputStream input = body.byteStream();
                     app.exteraless.backup.MigrationBackup.Sink sink = app.exteraless.backup.MigrationBackup.openDownload(
                             activity, fileName, "application/vnd.android.package-archive")) {
                    byte[] buffer = new byte[64 * 1024];
                    long done = 0;
                    int lastPercent = -1;
                    int read;
                    while ((read = input.read(buffer)) > 0) {
                        sink.stream.write(buffer, 0, read);
                        done += read;
                        if (total > 0) {
                            int percent = (int) (done * 100 / total);
                            if (percent != lastPercent) {
                                lastPercent = percent;
                                final long doneBytes = done;
                                AndroidUtilities.runOnUIThread(() -> listener.onProgress(doneBytes, total));
                            }
                        }
                    }
                    sink.finish();
                    savedName = sink.name;
                }
            } catch (Exception e) {
                if (!call.isCanceled()) {
                    FileLog.e("GitHubUpdater: download to Downloads failed", e);
                }
            }
            final String result = savedName;
            AndroidUtilities.runOnUIThread(() -> {
                download = null;
                if (result != null) {
                    listener.onFinished(true, false);
                    saved.accept(result);
                } else {
                    listener.onFinished(false, call.isCanceled());
                    if (!call.isCanceled()) {
                        bulletin(LocaleController.getString(R.string.OEUpdateDownloadFailed), true);
                    }
                }
            });
        }, "gh-updater-download-dl").start();
    }

    /** Перезапуск после установки запросил наш апдейтер, а не сторонний установщик. */
    public static boolean consumeRelaunchRequest(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!preferences.getBoolean(KEY_RELAUNCH, false)) {
            return false;
        }
        preferences.edit().putBoolean(KEY_RELAUNCH, false).commit();
        return true;
    }

    /**
     * Отдаём скачанный APK системному установщику. Android не умеет применять новый код
     * без переустановки пакета: ART не переопределяет загруженные классы, а base.apk
     * доступен только на чтение. Поэтому «применить на лету» здесь невозможно в принципе —
     * единственный путь ACTION_VIEW → системный установщик → перезапуск.
     */
    public static void install(Activity activity, File file) {
        if (activity == null || file == null || !file.exists() || file.length() == 0) {
            bulletin(LocaleController.getString(R.string.OEUpdateInstallFailed), true);
            return;
        }
        // Отмечаем перезапуск ДО установщика: процесс будет убит, и только этот флаг
        // отличит нашу установку от ручной установки APK из файлового менеджера.
        // commit(), а не apply(): процесс умрёт раньше, чем отработает фоновая запись.
        prefs().edit().putBoolean(KEY_RELAUNCH, true).commit();
        // «Неизвестные источники»: без этого гранта установщик молча откажет.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !ApplicationLoader.applicationContext.getPackageManager().canRequestPackageInstalls()) {
            try {
                org.telegram.ui.Components.AlertsCreator
                        .createApkRestrictedDialog(activity, null).show();
            } catch (Exception e) {
                FileLog.e("GitHubUpdater: restricted dialog failed", e);
                bulletin(LocaleController.getString(R.string.OEUpdateInstallFailed), true);
            }
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setDataAndType(FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", file),
                    "application/vnd.android.package-archive");
            activity.startActivity(intent);
        } catch (Exception e) {
            FileLog.e("GitHubUpdater: open installer failed", e);
            bulletin(LocaleController.getString(R.string.OEUpdateInstallFailed), true);
        }
    }
}
