#!/usr/bin/env python3
"""Guard: the settings migration (key change) flow stays wired end to end.

* every R.string.OEMig* used in Java exists in the default and the Russian resources;
* LaunchActivity offers the restore on resume and forwards the file-picker result;
* the updater understands the release markers and routes `exfullgram:resign` to the sheet;
* the manifest allows removing the old app;
* the backup never carries sessions or passcodes (only the settings family and account profiles).
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

    used = set()
    for path in (JAVA / "app/exteraless/updater").glob("*.java"):
        used.update(re.findall(r"R\.string\.(OEMig\w+)", read(path)))
    for res in ("values/strings_oe_general.xml", "values-ru-rRU/strings_oe_general.xml"):
        text = read(MAIN / "res" / res)
        for name in sorted(used):
            if f'name="{name}"' not in text:
                failures.append(f"{res} is missing string {name}")

    launch = read(JAVA / "org/telegram/ui/LaunchActivity.java")
    if "RestoreBackupSheet.maybeOffer(this)" not in launch:
        failures.append("LaunchActivity must offer the restore on resume")
    if "RestoreBackupSheet.onActivityResult(" not in launch:
        failures.append("LaunchActivity must forward the backup file picker result")

    updater = read(JAVA / "app/exteraless/updater/GitHubUpdater.java")
    for marker in ("exfullgram:resign", "exfullgram:backup"):
        if marker not in updater:
            failures.append(f"GitHubUpdater lost the `{marker}` release marker")
    if "new MigrationSheet(" not in updater or "release.resign" not in updater:
        failures.append("releases marked exfullgram:resign must open the MigrationSheet")
    if "release.backupFirst" not in updater:
        failures.append("releases marked exfullgram:backup must offer the export in the update sheet")

    if "REQUEST_DELETE_PACKAGES" not in read(MAIN / "AndroidManifest.xml"):
        failures.append("manifest must declare REQUEST_DELETE_PACKAGES for removing the old app")

    backup = read(JAVA / "app/exteraless/backup/MigrationBackup.java")
    for forbidden in ("tgnet", "cache4.db", "UserConfig"):
        if forbidden in backup:
            failures.append(f"MigrationBackup must not touch `{forbidden}`")
    # The helper dumps the app lock hash (without its salt) into `userconfing`: it must be dropped on
    # export, rejected on restore, and the lock preferences of the mod must stay out of the file.
    if 'root.remove("userconfing")' not in backup:
        failures.append("the backup must drop the userconfing section (app lock hash)")
    if '"nekopasscode"' not in backup or "ALLOWED_SECTIONS" not in backup:
        failures.append("the backup must exclude nekopasscode and restore only whitelisted sections")
    if "Environment.DIRECTORY_DOWNLOADS" not in backup:
        failures.append("the backup must live in the public Downloads folder")

    if failures:
        for failure in failures:
            print(f"FAIL: {failure}")
        return 1
    print("PASS: export, restore offer, release markers and strings are wired")
    return 0


if __name__ == "__main__":
    sys.exit(main())
