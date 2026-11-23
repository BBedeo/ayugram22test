#!/usr/bin/env python3
"""Guard: an update download can be sent to the background and shows a minimal themed progress plate."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
U = ROOT / "TMessagesProj/src/main/java/app/exteraless/updater"


def main() -> int:
    sheet = (U / "UpdateSheet.java").read_text(encoding="utf-8", errors="ignore")
    updater = (U / "GitHubUpdater.java").read_text(encoding="utf-8", errors="ignore")
    pill = (U / "UpdateProgressPill.java").read_text(encoding="utf-8", errors="ignore")
    failures = []
    if "onBackground(" not in sheet or "OEUpdateBackground" not in sheet:
        failures.append("the update sheet must offer a background download")
    if "UpdateProgressPill.show(" not in updater or "markReady(release, file)" not in updater:
        failures.append("a background download must show the plate and finish as a ready update")
    if "Theme.key_dialogBackground" not in pill:
        failures.append("the plate must use theme colors")
    page = (ROOT / "TMessagesProj/src/main/java/app/exteraless/settings/OpenExteraUpdatesActivity.java").read_text(encoding="utf-8", errors="ignore")
    if "position == intervalRow || position == postponedRow" not in page:
        failures.append("the check-frequency row must be a text row in getItemViewType, not a switch")
    for f in failures:
        print("FAIL:", f)
    if failures:
        return 1
    print("PASS: background update download and progress plate are in place")
    return 0


if __name__ == "__main__":
    sys.exit(main())
