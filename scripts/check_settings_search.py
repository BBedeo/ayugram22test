#!/usr/bin/env python3
"""Guard: every ExFullGram settings row must be findable through settings search.

Background: settings search builds its index from ``addRow("<key>")`` calls and resolves
a title for each key in ``SettingsHelper.rowTitleResource()``:

  1. ``SEARCH_TITLE_ALIASES`` entry for ``<prefix>:<key>`` (or bare ``<key>``);
  2. ``<prefix><Key>``;
  3. ``<prefix><Key>Title``;
  4. ``<Key>``;
  5. ``<Key>Title``;
  6. the raw key.

Headers (``*Header``) are intentionally skipped by the search index, and a short list of
decorative preview cells has no label string at all — those fall back to a humanized key.
Anything else that cannot be resolved is a regression: the setting silently disappears
from search, so this script fails and points at the offending row.
"""

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "TMessagesProj/src/main"
JAVA = MAIN / "java"
HELPER = JAVA / "tw/nekomimi/nekogram/helpers/SettingsHelper.java"
SETTINGS_HOME = JAVA / "app/exteraless/settings/OpenExteraSettingsActivity.java"
ACTION_BAR = JAVA / "org/telegram/ui/ActionBar/ActionBar.java"
ACTION_BAR_MENU = JAVA / "org/telegram/ui/ActionBar/ActionBarMenu.java"

# Rows rendered by custom preview/hero cells: they have no dedicated label string,
# search falls back to a humanized key ("Chat List Preview"). Keep this list empty
# unless the row is genuinely decorative.
DECORATIVE = {
    "chatListPreview",
    "foldersPreview",
    "doubleTapPreview",
    "wideChannelPostsPreview",
    "fabShape",
    "glassPrecision",
    "roundVideoQuality",
    "cloudApiKeys",
    "activeProfile",
    "profiles",
}


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="ignore")


def string_names() -> set[str]:
    names: set[str] = set()
    for path in MAIN.glob("res/values*/**/*.xml"):
        names.update(re.findall(r'<string name="([^"]+)"', read(path)))
    return names


def aliases() -> dict[str, str]:
    # A commented-out alias must not count as a live one: drop full-line comments first.
    text = re.sub(r"(?m)^\s*//[^\n]*", "", read(HELPER))
    return dict(
        re.findall(r'SEARCH_TITLE_ALIASES\.put\("([^"]+)",\s*"([^"]+)"\)', text)
    )


def rows() -> dict[tuple[str, str], str]:
    found: dict[tuple[str, str], str] = {}
    for path in JAVA.glob("app/exteraless/**/*.java"):
        text = read(path)
        prefix = re.search(r'getSearchPrefix\(\)\s*\{\s*return\s*"([^"]*)"', text)
        if not prefix:
            continue
        for key in re.findall(r'addRow\(\s*"([^"]+)"', text):
            found.setdefault((prefix.group(1), key), path.name)
    return found


def resolves(prefix: str, key: str, names: set[str], alias_map: dict[str, str]) -> bool:
    capitalized = key[:1].upper() + key[1:]
    candidates = [key]
    if f"{prefix}:{key}" in alias_map:
        candidates.insert(0, alias_map[f"{prefix}:{key}"])
    if key in alias_map:
        candidates.insert(0, alias_map[key])
    candidates += [
        f"{prefix}{capitalized}",
        f"{prefix}{capitalized}Title",
        capitalized,
        f"{capitalized}Title",
    ]
    return any(candidate in names for candidate in candidates)


def main() -> int:
    names = string_names()
    alias_map = aliases()
    all_rows = rows()

    failures: list[str] = []

    # Every alias must point at a real default-locale string, otherwise the row keeps
    # falling through to the next naming scheme (or to the humanized key).
    for alias_key, target in alias_map.items():
        if target not in names:
            failures.append(f"alias {alias_key} -> {target}: resource not found")

    unresolved: list[str] = []
    for (prefix, key), file_name in sorted(all_rows.items()):
        if key.endswith("Header"):
            continue
        if resolves(prefix, key, names, alias_map):
            continue
        if key in DECORATIVE:
            continue
        unresolved.append(f"{file_name}: addRow(\"{key}\") [{prefix}]")

    if unresolved:
        failures.append("Rows invisible to settings search:\n  " + "\n  ".join(unresolved))

    # Regression pin for the fork's own typing bubble row: it must resolve through the
    # explicit alias, not through a guess.
    if "OEAppearance:typingBubbleAtBottom" not in alias_map:
        failures.append("OEAppearance:typingBubbleAtBottom alias is missing")

    # The humanized-key fallback must stay in place, otherwise decorative rows would
    # vanish from the index instead of showing a readable placeholder.
    helper_text = read(HELPER)
    for marker in ("rowTitleResource(", "humanize(key)"):
        if marker not in helper_text:
            failures.append(f"SettingsHelper lost `{marker}`")

    # Search UI must stay reachable and animated: the ExFullGram settings home keeps an
    # inline search field, the glass pill has to wrap that field (not collapse to zero
    # while the field is open), and the index must be warmed before the animation runs.
    settings_home = read(SETTINGS_HOME)
    action_bar = read(ACTION_BAR)
    action_bar_menu = read(ACTION_BAR_MENU)

    if ".setIsSearchField(true)" not in settings_home or "onSearchExpand" not in settings_home:
        failures.append("OpenExteraSettingsActivity lost its inline search field wiring")
    if "scheduleSearchIndexBuild" not in settings_home or "enterInlineSearch" not in settings_home:
        failures.append("OpenExteraSettingsActivity must prebuild the search index off the animation frame")
    if "getSearchItemWidth" not in action_bar_menu:
        failures.append("ActionBarMenu lost getSearchItemWidth()")
    if "menu.getSearchItemWidth()" not in action_bar:
        failures.append("ActionBar no longer wraps the search field in its glass pill")
    if "isSearchFieldVisible" not in action_bar:
        failures.append("ActionBar lost its search field visibility state")

    # The pill has to stay visible for the whole unfold: its alpha used to be multiplied by the
    # fading search container, so the glass vanished and the field hung without a background.
    if "!hasSearchItemInMenu()" not in action_bar:
        failures.append("ActionBar must keep the glass pill alive while the search field is laid out")
    # The field is laid out at full width at once, so the pill has to clip it while it springs.
    if "menu.getSearchItemLeft()" not in action_bar or "getSearchItemLeft" not in action_bar_menu:
        failures.append("ActionBar must clip the search field to the growing pill")
    # A row of the section root has the same title as the section: "ExFullGram > ExFullGram".
    if "parentPath(fragment, e_title, f_title)" not in helper_text:
        failures.append("SettingsHelper must not repeat the section name in the result path")
    if alias_map.get("OpenExtera:messageHistory") != "OEGeneralAyuMoments":
        failures.append("OpenExtera:messageHistory must resolve to its localized title")

    total = len(all_rows)
    if failures:
        for failure in failures:
            print(f"FAIL: {failure}")
        print(f"\n{len(failures)} problem(s) across {total} settings rows.")
        return 1

    print(f"PASS: {total} settings rows indexed, aliases resolve, headers skipped by design")
    print(f"PASS: {len(DECORATIVE)} decorative rows fall back to humanized keys")
    return 0


if __name__ == "__main__":
    sys.exit(main())
