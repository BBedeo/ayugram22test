#!/usr/bin/env python3
"""Guard: automatic updates stay opt-in, Wi-Fi aware and never install silently.

* the setting is off by default and the Wi-Fi-only option is on by default;
* the settings page is reachable from the ExFullGram home and registered for search and deep links;
* an automatic download only starts when the setting allows it, and ends with a notification or
  a sheet that asks the user to install (Android confirms the installation itself);
* "Remind me at the next update" (postpone) keeps working for downloaded updates.
"""

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "TMessagesProj/src/main"
JAVA = MAIN / "java"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="ignore")


def main() -> int:
    failures: list[str] = []

    config = read(MAIN / "kotlin/app/exteraless/general/GeneralConfig.kt")
    if not re.search(r'autoUpdate = addConfig\("OEGeneralAutoUpdate", ConfigItem\.configTypeBool, false\)', config):
        failures.append("automatic updates must be off by default")
    if not re.search(r'autoUpdateWifiOnly = addConfig\("OEGeneralAutoUpdateWifiOnly", ConfigItem\.configTypeBool, true\)', config):
        failures.append("automatic updates must default to Wi-Fi only")

    updater = read(JAVA / "app/exteraless/updater/GitHubUpdater.java")
    if "GeneralConfig.autoUpdate.Bool()" not in updater or "StatsController.TYPE_WIFI" not in updater:
        failures.append("the automatic download must honour the setting and the Wi-Fi-only option")
    for needle, why in (
        ("markReady(", "a downloaded update must be remembered"),
        ("notifyReady(", "the user must be told that an update is ready"),
        ("offerReady()", "a downloaded update must be offered in the app"),
        ("KEY_SKIPPED", "postponing an update must keep working"),
    ):
        if needle not in updater:
            failures.append(f"GitHubUpdater {why} (missing `{needle}`)")
    if "ACTION_INSTALL_PACKAGE" in updater or "PackageInstaller" in updater:
        failures.append("updates must go through the system installer prompt, never install silently")

    home = read(JAVA / "app/exteraless/settings/OpenExteraSettingsActivity.java")
    if 'addRow("updates")' not in home or "new OpenExteraUpdatesActivity()" not in home:
        failures.append("the ExFullGram home must have a version and updates row that opens the page")
    helper = read(JAVA / "tw/nekomimi/nekogram/helpers/SettingsHelper.java")
    if helper.count("OpenExteraUpdatesActivity") < 3:
        failures.append("the updates page must be imported, listed for search and handled by deep links")
    page = read(JAVA / "app/exteraless/settings/OpenExteraUpdatesActivity.java")
    if 'getSearchPrefix() {\n        return "OEUpdates"' not in page.replace("\r\n", "\n"):
        failures.append("the updates page lost its search prefix")

    if failures:
        for failure in failures:
            print(f"FAIL: {failure}")
        return 1
    print("PASS: automatic updates are opt-in, Wi-Fi aware, confirmed by the system installer and searchable")
    return 0


if __name__ == "__main__":
    sys.exit(main())
