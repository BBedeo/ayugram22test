#!/usr/bin/env python3
"""Guard: the in-app updater must offer ExFullGram builds from this repository only.

Background: ``GitHubUpdater`` used to read releases of ``exteraless/exteraless``. That
repository ships an APK with a different ``applicationId`` and a different signing key,
so the sheet offered "an update from Extera" and the install died with
``INSTALL_FAILED_UPDATE_INCOMPATIBLE``. Five more traps are pinned here:

* the CI workflow publishes one ``ci-<sha>`` pre-release per commit, so scanning the ten
  newest releases never reaches a real release -- the updater must ask for
  ``/releases/latest`` and must ignore non-version tags;
* CI used ``fetch-depth: 1``, which breaks ``git rev-list --count`` in ``build.gradle``:
  such APKs carry the base Telegram version (13.2.1 / code 1261) and look "newer" than
  the fork while being a version *downgrade* against a local build;
* ``Theme.AdaptiveRipple.filledRect()`` takes radii in dp and converts them itself, so
  passing pixels doubles the conversion and turns every button into a capsule;
* the brand sanitizer must not rewrite URLs, autolinks or inline code inside release
  notes, otherwise links lead nowhere;
* a Material 3 sheet gate keyed on ``isMaterial3OpaqueChrome()`` is wrong: it counts the
  MD3 player, which is on by default, so classic presets would get opaque M3 sheets.
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
RECEIVER = UPDATER / "AppUpdateReceiver.java"
MANIFEST = MAIN / "AndroidManifest.xml"
WORKFLOW = ROOT / ".github/workflows/release.yml"
SURFACE_MODE = JAVA / "app/exteraless/appearance/SheetSurfaceMode.java"
BOTTOM_SHEET = JAVA / "org/telegram/ui/ActionBar/BottomSheet.java"
LOCALE_CONTROLLER = JAVA / "org/telegram/messenger/LocaleController.java"

OWN_REPO = "BBedeo/ayugram22test"
FOREIGN_REPOS = ("exteraless/exteraless",)
BRAND_STRINGS = {"values/strings_oe.xml": "OpenExtera"}

BACKSLASH = chr(92)
QUOTE_DOUBLE = '"'
QUOTE_SINGLE = "'"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="ignore")


def code_only(text: str) -> str:
    """Drop comments but keep string literals.

    Pins below are about code, not about the explanation above it. A naive strip is wrong
    here: `//` inside "https?://" is not a comment, and cutting there broke the pattern.
    """
    out: list[str] = []
    i, n = 0, len(text)
    while i < n:
        ch = text[i]
        if ch == QUOTE_DOUBLE or ch == QUOTE_SINGLE:
            quote = ch
            out.append(ch)
            i += 1
            while i < n:
                out.append(text[i])
                if text[i] == BACKSLASH and i + 1 < n:
                    out.append(text[i + 1])
                    i += 2
                    continue
                if text[i] == quote:
                    i += 1
                    break
                i += 1
            continue
        if text.startswith("//", i):
            while i < n and text[i] != "\n":
                i += 1
            continue
        if text.startswith("/*", i):
            end = text.find("*/", i + 2)
            i = n if end < 0 else end + 2
            continue
        out.append(ch)
        i += 1
    return "".join(out)


def main() -> int:
    failures: list[str] = []

    updater = read(GITHUB_UPDATER)
    updater_code = code_only(updater)
    sheet_code = code_only(read(UPDATE_SHEET))
    surface_code = code_only(read(SURFACE_MODE)) if SURFACE_MODE.exists() else ""
    manifest = read(MANIFEST)
    workflow = read(WORKFLOW)

    repo = re.search(r'String REPO\s*=\s*"([^"]+)"', updater_code)
    if not repo:
        failures.append("GitHubUpdater lost its REPO constant")
    elif repo.group(1) != OWN_REPO:
        failures.append(f"GitHubUpdater.REPO is {repo.group(1)!r}, expected {OWN_REPO!r}")
    for foreign in FOREIGN_REPOS:
        if f'"{foreign}"' in updater_code:
            failures.append(f"GitHubUpdater still points at the foreign repository {foreign}")

    # Only version tags may be offered: ci-<sha> builds are not releases of the app.
    if "RELEASE_TAG" not in updater_code:
        failures.append("GitHubUpdater lost its version-tag filter")
    elif not re.search(r"RELEASE_TAG\.matcher\(", updater_code):
        failures.append("GitHubUpdater must apply its version-tag filter when picking a release")
    if '"/releases/latest"' not in updater_code:
        failures.append("stable channel must ask /releases/latest, not a window of all releases")

    # Branding must not leak from the release JSON into the sheet.
    subtitle = re.search(r"StringBuilder subtitle\s*=\s*new StringBuilder\(([^)]*)\)", updater_code)
    if not subtitle:
        failures.append("update sheet lost its subtitle builder")
    elif "release.name" in subtitle.group(1):
        failures.append("update sheet subtitle must not use release.name")
    if "LocaleController.getAppName()" not in updater_code:
        failures.append("update sheet subtitle must start from the app name")
    if "stripUpstreamBrand(" not in updater_code:
        failures.append("release notes must not print the upstream brand")

    # Sanitizing the whole body rewrote URLs into broken links, so link targets, autolinks
    # and inline code have to be skipped as tokens.
    if "BRAND_TOKEN.matcher(" not in updater_code:
        failures.append("brand sanitizer must scan tokens, not the whole body")
    token = re.search(r"Pattern BRAND_TOKEN = Pattern\.compile\((.*?)\);", updater_code, re.S)
    if not token:
        failures.append("brand sanitizer lost its token pattern")
    else:
        pattern = token.group(1)
        for part in ("https?://", "<", "`[^`]*`"):
            if part not in pattern:
                failures.append(f"brand sanitizer must skip `{part}` tokens")
    if "NOTES_LIMIT" not in updater_code:
        failures.append("release notes must be capped before sanitizing")

    # The relaunch after installing must be our own update, not any manual APK install.
    if "relaunch_after_update" not in updater_code or "consumeRelaunchRequest(" not in updater_code:
        failures.append("GitHubUpdater must flag the update it started for the relaunch receiver")

    # The installer path: system installer + relaunch after replacement.
    if "canRequestPackageInstalls()" not in updater_code:
        failures.append("GitHubUpdater must request the unknown-sources permission before installing")
    if "AppUpdateReceiver" not in manifest:
        failures.append("AppUpdateReceiver is not registered in AndroidManifest.xml")
    receiver = read(RECEIVER) if RECEIVER.exists() else ""
    if "ACTION_MY_PACKAGE_REPLACED" not in receiver:
        failures.append("AppUpdateReceiver must relaunch the app on MY_PACKAGE_REPLACED")
    elif "consumeRelaunchRequest(" not in receiver:
        failures.append("AppUpdateReceiver must only relaunch for updates started in the app")

    # Per-preset styling: the sheet content follows the chrome BottomSheet picked.
    if not surface_code:
        failures.append("SheetSurfaceMode is missing")
    else:
        for marker in ("LiquidGlassTuning.isActive()", "GlassOutlineStyle.current()",
                       "AppearanceConfig.m3ListItems()", "AppearanceConfig.newChatHeaderStyle()"):
            if marker not in surface_code:
                failures.append(f"SheetSurfaceMode lost its `{marker}` gate")
        if "isMaterial3OpaqueChrome(" in surface_code:
            failures.append("SheetSurfaceMode must not use isMaterial3OpaqueChrome(): md3Player is on by default")
    if "SheetSurfaceMode.of(" not in sheet_code:
        failures.append("UpdateSheet must follow the active surface mode")
    # Radii for AdaptiveRipple.filledRect() are dp: dp(...) there doubles the conversion.
    radius = re.search(r"\bbuttonRadius\s*=\s*([^;]+);", sheet_code)
    if not radius:
        failures.append("UpdateSheet lost its button radius")
    elif re.search(r"\bdp\s*\(", radius.group(1)) or "Math.round" in radius.group(1):
        failures.append("UpdateSheet must pass button radii in dp, not converted to px")
    if "SheetSurfaceMode.buttonRadiusDp(" not in sheet_code:
        failures.append("UpdateSheet lost its preset button radius")
    if "SheetSurfaceMode.isM3(" not in code_only(read(BOTTOM_SHEET)):
        failures.append("BottomSheet must use SheetSurfaceMode.isM3() for its Material 3 chrome")

    # CI must keep the full history, otherwise versionName/versionCode collapse.
    checkout = re.search(r"uses: actions/checkout@\S+\r?\n\s+with:\r?\n((?:\s+.+\r?\n)+)", workflow)
    if not checkout or "fetch-depth: 0" not in checkout.group(1):
        failures.append("release workflow must check out the full history (fetch-depth: 0)")

    # The app name fallback is the only place a stale fork name was left.
    if '"exteraless"' in read(LOCALE_CONTROLLER):
        failures.append("LocaleController.getAppName() still falls back to 'exteraless'")

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
