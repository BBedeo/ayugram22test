#!/usr/bin/env python3
"""Guard: two readability fixes stay in place.

* the glass reaction bar and its bubbles draw a theme-colored backing fill under the glass, so the
  emoji do not float over message text without a background;
* the update sheet has a draggable scroll thumb for the release notes and does not let the sheet's
  swipe-to-dismiss steal the gesture while the notes can scroll.
"""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "TMessagesProj/src/main/java"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="ignore")


def main() -> int:
    failures: list[str] = []

    reactions = read(JAVA / "org/telegram/ui/Components/ReactionsContainerLayout.java")
    if reactions.count("drawGlassBacking(") < 4:
        failures.append("every glass branch of the reaction bar must draw drawGlassBacking() first")
    for glass in ("GlassMenuHelper.draw(glassBackground, canvas, rect, radius, 255)",
                  "GlassMenuHelper.draw(glassBackground, canvas, rectF, br, alpha)",
                  "GlassMenuHelper.draw(glassBackground, canvas, rectF, sr, alpha)"):
        index = reactions.find(glass)
        if index < 0 or "drawGlassBacking(" not in reactions[max(0, index - 260):index]:
            failures.append(f"missing backing fill before `{glass}`")

    sheet = read(JAVA / "app/exteraless/updater/UpdateSheet.java")
    if "new NotesScrollThumb(" not in sheet:
        failures.append("the update sheet must show the notes scroll thumb")
    if "canDismissWithSwipe()" not in sheet:
        failures.append("the update sheet must not close by swipe while the notes can scroll")
    thumb = read(JAVA / "app/exteraless/updater/NotesScrollThumb.java")
    if "requestDisallowInterceptTouchEvent(true)" not in thumb or "scrollTo(" not in thumb:
        failures.append("the scroll thumb must be draggable and keep the gesture")

    helper = read(JAVA / "app/exteraless/appicons/NotificationIconHelper.java")
    if "LauncherIconController.isEnabled(icon)" not in helper:
        failures.append("the notification small icon must follow the selected app icon")
    controller = read(JAVA / "org/telegram/messenger/NotificationsController.java")
    if "NotificationIconHelper.currentSmallIcon()" not in controller:
        failures.append("message notifications must use the selected app icon silhouette")
    for name in ("exteraless", "ayu", "nya", "quack"):
        if not (ROOT / f"TMessagesProj/src/main/res/drawable-anydpi/notification_{name}.xml").exists():
            failures.append(f"missing status bar icon notification_{name}.xml")

    if failures:
        for failure in failures:
            print(f"FAIL: {failure}")
        return 1
    print("PASS: reaction bar backing and update notes scroll thumb are in place")
    return 0


if __name__ == "__main__":
    sys.exit(main())
