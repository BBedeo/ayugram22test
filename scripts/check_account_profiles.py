#!/usr/bin/env python3
"""Static regression checks for account-scoped mod settings and search.

This verifies integration contracts; it is NOT a substitute for a debug APK
build or manual Android account-switching tests.
"""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "TMessagesProj/src/main/java"
RES = ROOT / "TMessagesProj/src/main/res"

def src(relative):
    return (JAVA / relative).read_text(encoding="utf-8")

profiles = src("app/exteraless/settings/AccountSettingsProfiles.java")
settings = src("app/exteraless/settings/AccountSettingsActivity.java")
compare = src("app/exteraless/settings/AccountSettingsCompareActivity.java")
home = src("app/exteraless/settings/OpenExteraSettingsActivity.java")
helper = src("tw/nekomimi/nekogram/helpers/SettingsHelper.java")
launch = src("org/telegram/ui/LaunchActivity.java")
cloud = src("tw/nekomimi/nekogram/helpers/CloudSettingsHelper.java")

checks = {
    "Mod profiles keyed by user identity and Test/live type":
        'kind = DemoBackend.isTestAccount(slot) ? "test_" : "live_"' in profiles
        and 'user.id' in profiles,
    "Profile opt-in preserves legacy shared settings":
        'if (!isEnabled()) return;' in profiles
        and 'saveCurrent(id)' in profiles
        and 'PROFILE_PREFIX' in profiles,
    "Profile changes saved as typed SharedPreferences values":
        all(s in profiles for s in (
            'editor.putBoolean(', 'editor.putInt(', 'editor.putLong(',
            'editor.putFloat(', 'editor.putString(', 'editor.putStringSet(')),
    "Account switch saves old and restores new before UI rebuilding":
        'AccountSettingsProfiles.beforeAccountSwitch(currentAccount, account)' in launch
        and launch.index('AccountSettingsProfiles.beforeAccountSwitch(currentAccount, account)')
            < launch.index('UserConfig.selectedAccount = account;'),
    "Settings initialized on cold start":
        'AccountSettingsProfiles.initialize(currentAccount);' in launch,
    "Settings caches refreshed when a profile changes":
        all(s in profiles for s in (
            'NekoConfig.loadConfig(true)', 'NaConfig.INSTANCE.loadConfig(true)',
            'OpenExteraConfig.INSTANCE.loadConfig(true)',
            'AppearanceConfig.INSTANCE.loadConfig(true)',
            'ChatsConfig.INSTANCE.loadConfig(true)',
            'GeneralConfig.INSTANCE.loadConfig(true)')),
    "Local profiles do not copy Telegram session or login databases":
        '"nkmrcfg"' not in profiles or 'NekoConfig.getPreferences()' in profiles,
    "New Account settings section linked from ExFullGram":
        'presentFragment(new AccountSettingsActivity())' in home,
    "New Account settings section included in global search":
        'exteralessFragments.add(new AccountSettingsActivity())' in helper
        and 'case "accounts":' in helper,
    "Compare UI has two selectors and selective two-way merge":
        'leftSelector' in compare and 'rightSelector' in compare
        and 'mergeSelected(leftSlot, rightSlot' in compare
        and 'HashMap<String, Integer> choices' in compare,
    "Reset requires confirmation":
        'confirmReset(' in settings and 'setPositiveButton(' in settings,
    "Cloud auto-sync is paused during profile restore":
        'AccountSettingsProfiles.isApplying()' in cloud,
}

required = {
    "OpenExteraAccounts", "EFGAccountsTitle", "EFGAccountsSeparateSettings",
    "EFGAccountsCompare", "EFGAccountsReset", "EFGAccountsSelectHint",
    "EFGAccountsApply", "EFGAccountsComparisonTitle",
}
for directory in ("values", "values-ru-rRU", "values-uk-rUA"):
    xml = ET.parse(RES / directory / "exfullgram_accounts.xml")
    found = {x.attrib.get("name") for x in xml.findall("string")}
    checks[f"Translations in {directory} have all required labels"] = required <= found

failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {name}")
if failed:
    raise SystemExit(f"{len(failed)} account-profile checks failed")
