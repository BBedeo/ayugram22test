#!/usr/bin/env python3
"""Guard: the in-app updater must offer ExFullGram builds from this repository only.

Background: ``GitHubUpdater`` used to read releases of ``exteraless/exteraless``. That
repository ships an APK with a different ``applicationId`` and a different signing key,
so the sheet offered "an update from Extera" and the install died with
``INSTALL_FAILED_UPDATE_INCOMPATIBLE``. Two more traps are pinned here:

* the CI workflow publishes one ``ci-<sha>`` pre-release per commit, so a ``per_page=10``
  scan of all releases never reaches a real release — the updater must ask for
  ``/releases/latest`` and must ignore non-version tags;
* CI used ``fetch-depth: 1``, which breaks ``git rev-list --count`` in ``build.gradle``:
  such APKs carry the base Telegram version (13.2.1 / code 1261), look "newer" than the
  fork and are a version *downgrade* against a local build.
"""

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "TMessagesProj/src/main"
JAVA = MAIN / "java"
UPDATER = JAVA / "app/exteraless/updater"
GITHUB_UPDATER = UPDATER / "GitHubUpdater.java"
UPDATE_SHEET = UPDATER / "UpdateSheet.java"
MANIFEST = MAIN / "AndroidManifest.xml"
WORKFLOW = ROOT / ".github/workflows/release.yml"
SURFACE_MODE = JAVA / "app/exteraless/appearance/SheetSurfaceMode.java"
BOTTOM_SHEET = JAVA / "org/telegram/ui/ActionBar/BottomSheet.java"
LOCALE_CONTROLLER = JAVA / "org/telegram/messenger/LocaleController.java"

OWN_REPO = "BBedeo/ayugram22test"
FOREIGN_REPOS = ("exteraless/exteraless",)
# Release titles/bodies come from GitHub; the sheet must not print them verbatim as the
# app name, so the subtitle has to be built from the local app name.
BRAND_STRINGS = {"values/strings_oe.xml": "OpenExtera"}


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="ignore")


def main() -> int:
    failures: list[str] = []

    updater = read(GITHUB_UPDATER)
    sheet = read(UPDATE_SHEET)
    manifest = read(MANIFEST)
    workflow = read(WORKFLOW)

    repo = re.search(r'String REPO\s*=\s*"([^"]+)"', updater)
    if not repo:
        failures.append("GitHubUpdater lost its REPO constant")
    elif repo.group(1) != OWN_REPO:
        failures.append(f"GitHubUpdater.REPO is {repo.group(1)!r}, expected {OWN_REPO!r}")
    for foreign in FOREIGN_REPOS:
        if f'"{foreign}"' in updater:
            failures.append(f"GitHubUpdater still points at the foreign repository {foreign}")

    # Only version tags may be offered: ci-<sha> builds are signed by the CI runner.
    if "RELEASE_TAG" not in updater:
        failures.append("GitHubUpdater lost its version-tag filter")
    if not re.search(r"RELEASE_TAG\.matcher\(", updater):
        failures.append("GitHubUpdater must apply its version-tag filter when picking a release")
    if '"/releases/latest"' not in updater:
        failures.append("stable channel must ask /releases/latest, not a window of all releases")

    # Branding must not leak from the release JSON into the sheet.
    subtitle = re.search(r"StringBuilder subtitle\s*=\s*new StringBuilder\(([^)]*)\)", updater)
    if not subtitle:
        failures.append("update sheet lost its subtitle builder")
    elif "release.name" in subtitle.group(1):
        failures.append("update sheet subtitle must not use release.name")
    if "LocaleController.getAppName()" not in updater:
        failures.append("update sheet subtitle must start from the app name")
    if "stripUpstreamBrand(" not in updater:
        failures.append("release notes must not print the upstream brand")
    # Sanitizing the whole body also rewrote URLs (github.com/exteraless/exteraless) into
    # broken links, so link targets must be skipped.
    if "MD_LINK" not in updater:
        failures.append("brand sanitizer must leave markdown link targets untouched")

    # The installer path: system installer + relaunch after replacement.
    if "canRequestPackageInstalls()" not in updater:
        failures.append("GitHubUpdater must request the unknown-sources permission before installing")
    if "AppUpdateReceiver" not in manifest:
        failures.append("AppUpdateReceiver is not registered in AndroidManifest.xml")
    receiver = JAVA / "app/exteraless/updater/AppUpdateReceiver.java"
    if not receiver.exists() or "ACTION_MY_PACKAGE_REPLACED" not in read(receiver):
        failures.append("AppUpdateReceiver must relaunch the app on MY_PACKAGE_REPLACED")

    # Per-preset styling: the sheet content follows the chrome BottomSheet picked.
    surface = read(SURFACE_MODE) if SURFACE_MODE.exists() else ""
    if not surface:
        failures.append("SheetSurfaceMode is missing")
    else:
        for marker in ("LiquidGlassTuning.isActive()", "M3SurfaceHelper.isMaterial3OpaqueChrome()",
                       "GlassOutlineStyle.current()"):
            if marker not in surface:
                failures.append(f"SheetSurfaceMode lost its `{marker}` gate")
    if "SheetSurfaceMode.of(" not in sheet:
        failures.append("UpdateSheet must follow the active surface mode")
    # Theme.AdaptiveRipple.filledRect() converts radii from dp itself; wrapping them in dp()
    # doubles the conversion and turns a 10dp button into a capsule.
    if "Math.round(dp(SheetSurfaceMode.buttonRadiusDp" in sheet:
        failures.append("UpdateSheet must pass button radii in dp, not converted to px")
    if "SheetSurfaceMode.buttonRadiusDp(" not in sheet:
        failures.append("UpdateSheet lost its preset button radius")
    if "SheetSurfaceMode.isM3(" not in read(BOTTOM_SHEET):
        failures.append("BottomSheet must use SheetSurfaceMode.isM3() for its Material 3 chrome")

    # CI must keep the full history, otherwise versionName/versionCode collapse.
    checkout = re.search(r"uses: actions/checkout@\S+\n\s+with:\n((?:\s+.+\n)+)", workflow)
    if not checkout or "fetch-depth: 0" not in checkout.group(1):
        failures.append("release workflow must check out the full history (fetch-depth: 0)")

    # The app name fallback is the only place a stale fork name was left.
    if '"exteraless"' in read(LOCALE_CONTROLLER):
        failures.append("LocaleController.getAppName() still falls back to 'exteraless'")

    # Every brand string that must exist for the sheet.
    for rel, key in BRAND_STRINGS.items():
        if f'name="{key}"' not in read(MAIN / "res" / rel):
            failures.append(f"missing brand string {key} in {rel}")

    if failures:
        for failure in failures:
            print(f"FAIL: {failure}")
        print(f"\n{len(failures)} problem(s) in the update pipeline.")
        return 1

    print(f"PASS: updater reads releases of {OWN_REPO} only and ignores ci-* pre-releases")
    print("PASS: installer path, relaunch receiver and per-preset sheet styling are wired")
    print("PASS: release workflow keeps the full git history for versionName/versionCode")
    return 0


if __name__ == "__main__":
    sys.exit(main())
