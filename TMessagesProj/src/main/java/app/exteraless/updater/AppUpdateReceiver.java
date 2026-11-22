package app.exteraless.updater;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.telegram.messenger.FileLog;

/**
 * Обновление прошло: системный установщик заменил пакет и убил наш процесс. Android
 * присылает ACTION_MY_PACKAGE_REPLACED только самому пакету, поэтому по нему возвращаем
 * пользователя в приложение — иначе после «Установить» он остаётся на рабочем столе.
 *
 * <p>Перезапускаем только если установку начал наш апдейтер ({@link
 * GitHubUpdater#consumeRelaunchRequest(Context)}): при ручной установке APK из файлового
 * менеджера пользователь занят другим делом, и выдёргивать его из чужого приложения нельзя.
 *
 * <p>С Android 10 запуск активности из фона может быть заблокирован
 * ({@code Background activity launch blocked}); исключения при этом не будет, а след
 * останется только в logcat. Тогда пользователь открывает приложение кнопкой «Открыть»
 * в системном установщике.
 */
public class AppUpdateReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            return;
        }
        if (!GitHubUpdater.consumeRelaunchRequest(context)) {
            return;
        }
        try {
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if (launch == null) {
                return;
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            FileLog.d("AppUpdateReceiver: relaunch after update");
            context.startActivity(launch);
        } catch (Exception e) {
            FileLog.e("AppUpdateReceiver: relaunch failed", e);
        }
    }
}
