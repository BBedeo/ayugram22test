package app.exteraless.updater;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.telegram.messenger.FileLog;

/**
 * Обновление прошло: системный установщик заменил пакет и убил наш процесс. Android
 * присылает ACTION_MY_PACKAGE_REPLACED только самому пакету и без разрешений, поэтому
 * по нему возвращаем пользователя в приложение — иначе после «Установить» он остаётся
 * на рабочем столе и думает, что ничего не произошло.
 */
public class AppUpdateReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            return;
        }
        try {
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                context.startActivity(launch);
            }
        } catch (Exception e) {
            // Часть прошивок запрещает старт из фона — тогда просто не перезапускаемся.
            FileLog.e("AppUpdateReceiver: relaunch failed", e);
        }
    }
}
