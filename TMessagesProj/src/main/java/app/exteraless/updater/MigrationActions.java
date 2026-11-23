package app.exteraless.updater;

import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.LaunchActivity;

import app.exteraless.backup.MigrationBackup;
import tw.nekomimi.nekogram.helpers.AppRestartHelper;

/** Shared actions of the migration flow: export on the old app, restore on the new one. */
final class MigrationActions {

    static final int REQUEST_PICK_BACKUP = 8421;

    private MigrationActions() {
    }

    /** Writes the backup into Downloads off the UI thread and animates the step meanwhile. */
    static void export(Activity activity, MigrationStepView step) {
        step.setBusy(getString(R.string.OEMigExporting), -1f);
        new Thread(() -> {
            String name = null;
            try {
                name = MigrationBackup.exportToDownloads(activity);
            } catch (Throwable e) {
                FileLog.e("MigrationActions: export failed", e);
            }
            final String saved = name;
            AndroidUtilities.runOnUIThread(() -> {
                if (saved != null) {
                    step.setDone(getString(R.string.OEMigExported));
                    step.setDescription(org.telegram.messenger.LocaleController.formatString(R.string.OEMigExportedDesc, saved));
                } else {
                    step.reset(getString(R.string.OEMigExportButton));
                    step.setDescription(getString(R.string.OEMigExportFailed));
                }
            });
        }, "migration-export").start();
    }

    /** Applies a backup read from the given text and restarts the app. Returns false if it is not a backup. */
    static boolean restore(Activity activity, String text) {
        try {
            MigrationBackup.restore(text);
        } catch (Throwable e) {
            FileLog.e("MigrationActions: restore failed", e);
            return false;
        }
        AndroidUtilities.runOnUIThread(() ->
                AppRestartHelper.triggerRebirth(activity, new Intent(activity, LaunchActivity.class)), 400);
        return true;
    }

    static void pickFile(Activity activity) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            activity.startActivityForResult(intent, REQUEST_PICK_BACKUP);
        } catch (Exception e) {
            FileLog.e("MigrationActions: picker failed", e);
        }
    }

    /** Called from LaunchActivity.onActivityResult; returns true when the result was ours. */
    static boolean onPicked(Activity activity, int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_PICK_BACKUP) {
            return false;
        }
        Uri uri = resultCode == Activity.RESULT_OK && data != null ? data.getData() : null;
        if (uri == null) {
            return true;
        }
        new Thread(() -> {
            boolean ok;
            try {
                ok = restore(activity, MigrationBackup.read(activity, uri));
            } catch (Throwable e) {
                FileLog.e("MigrationActions: read failed", e);
                ok = false;
            }
            if (!ok) {
                AndroidUtilities.runOnUIThread(() -> android.widget.Toast
                        .makeText(activity, getString(R.string.OEMigRestoreFailed), android.widget.Toast.LENGTH_LONG).show());
            }
        }, "migration-pick").start();
        return true;
    }
}
