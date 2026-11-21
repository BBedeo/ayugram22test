package org.telegram.messenger;

import android.app.backup.BackupAgentHelper;
import android.app.backup.BackupManager;
import android.app.backup.SharedPreferencesBackupHelper;

public class BackupAgent extends BackupAgentHelper {

    private static BackupManager backupManager;

    @Override
    public void onCreate() {
        SharedPreferencesBackupHelper helper = new SharedPreferencesBackupHelper(
                this,
                "saved_tokens",
                "saved_tokens_login",
                "nkmrcfg",
                "mainconfig",
                "exteraconfig",
                "pillstackconfig",
                "exfullgram_account_profiles_meta",
                "exfullgram_account_profiles_template"
        );
        addHelper("prefs", helper);
    }

    public static void requestBackup() {
        if (backupManager == null) {
            backupManager = new BackupManager(ApplicationLoader.applicationContext);
        }
        backupManager.dataChanged();
    }
}
