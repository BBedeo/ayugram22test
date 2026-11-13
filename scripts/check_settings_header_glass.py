"""Static verification for Liquid Glass headers in ExFullGram / Neko / Plugin settings.

Checks balanced delimiters across all modified files plus key structural
contracts for:
1. Crystal Liquid Glass provider in BlurredBackgroundProviderImpl.simpleActionBar
2. Header lens, overlay fallback, and compact centered title pill in ActionBar / SimpleActionBar / ActionBarMenuItem
3. Edge-to-edge SimpleGlass wiring and fade/overlay plumbing in BaseFragment / BaseFragmentSimpleGlass
4. Section card shadow preservation in SectionCardRecyclerView & simpleGlassMode in BlurredRecyclerView
5. Settings / Preferences / Plugins / Feed activity opt-in and removal of legacy setAdaptiveBackground
"""

from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "TMessagesProj/src/main/java"

MODIFIED_FILES = [
    "org/telegram/ui/Components/blur3/drawable/color/impl/BlurredBackgroundProviderImpl.java",
    "org/telegram/ui/ActionBar/SimpleActionBar.java",
    "org/telegram/ui/ActionBar/ActionBar.java",
    "org/telegram/ui/ActionBar/ActionBarLayout.java",
    "org/telegram/ui/ActionBar/ActionBarMenuItem.java",
    "org/telegram/ui/ActionBar/BaseFragment.java",
    "org/telegram/utils/glass/BaseFragmentSimpleGlass.java",
    "org/telegram/ui/Components/BlurredRecyclerView.java",
    "app/exteraless/appearance/SectionCardRecyclerView.java",
    "tw/nekomimi/nekogram/settings/BaseNekoSettingsActivity.java",
    "app/exteraless/settings/OpenExteraSettingsActivity.java",
    "tw/nekomimi/nekogram/settings/BaseNekoXSettingsActivity.java",
    "com/exteragram/messenger/preferences/BasePreferencesActivity.java",
    "app/exteraless/plugins/ui/PluginSettingsActivity.java",
    "app/exteraless/settings/OpenExteraAppNavigationActivity.java",
    "app/exteraless/plugins/ui/PluginsActivity.java",
    "app/exteraless/plugins/ui/PluginsInfoActivity.java",
    "app/exteraless/feed/ui/FeedChannelsActivity.java",
    "app/exteraless/feed/ui/FeedCategoryEditActivity.java",
    "app/exteraless/settings/InlineResultsActivity.java",
    "app/exteraless/plugins/ui/PluginActivityLogActivity.java",
    "app/exteraless/plugins/ui/PluginPermissionsActivity.java",
]


def read(rel: str) -> str:
    return (JAVA / rel).read_text(encoding="utf-8")


def strip_comments_and_strings(source: str) -> str:
    out: list[str] = []
    i = 0
    n = len(source)
    state = "code"
    while i < n:
        ch = source[i]
        nxt = source[i + 1] if i + 1 < n else ""
        if state == "line_comment":
            if ch == "\n":
                state = "code"
                out.append(ch)
            i += 1
            continue
        if state == "block_comment":
            if ch == "*" and nxt == "/":
                state = "code"
                i += 2
                continue
            if ch == "\n":
                out.append(ch)
            i += 1
            continue
        if state in ("string", "char"):
            quote = '"' if state == "string" else "'"
            if ch == "\\":
                i += 2
                continue
            if ch == quote:
                state = "code"
            i += 1
            continue
        if ch == "/" and nxt == "/":
            state = "line_comment"
            i += 2
            continue
        if ch == "/" and nxt == "*":
            state = "block_comment"
            i += 2
            continue
        if ch == '"':
            state = "string"
            i += 1
            continue
        if ch == "'":
            state = "char"
            i += 1
            continue
        out.append(ch)
        i += 1
    return "".join(out)


def balanced_delimiters(source: str) -> tuple[bool, str]:
    pairs = {"(": ")", "[": "]", "{": "}"}
    closing = {v: k for k, v in pairs.items()}
    stack: list[tuple[str, int]] = []
    i = 0
    line = 1
    n = len(source)
    state = "code"
    while i < n:
        ch = source[i]
        nxt = source[i + 1] if i + 1 < n else ""
        if ch == "\n":
            if state == "line_comment":
                state = "code"
            line += 1
            i += 1
            continue
        if state == "line_comment":
            i += 1
            continue
        if state == "block_comment":
            if ch == "*" and nxt == "/":
                state = "code"
                i += 2
                continue
            i += 1
            continue
        if state in ("string", "char"):
            quote = '"' if state == "string" else "'"
            if ch == "\\":
                i += 2
                continue
            if ch == quote:
                state = "code"
            i += 1
            continue
        if ch == "/" and nxt == "/":
            state = "line_comment"
            i += 2
            continue
        if ch == "/" and nxt == "*":
            state = "block_comment"
            i += 2
            continue
        if ch == '"':
            state = "string"
            i += 1
            continue
        if ch == "'":
            state = "char"
            i += 1
            continue
        if ch in pairs:
            stack.append((ch, line))
        elif ch in closing:
            if not stack or stack[-1][0] != closing[ch]:
                return False, f"unmatched '{ch}' at line {line}"
            stack.pop()
        i += 1
    if stack:
        ch, ln = stack[-1]
        return False, f"unclosed '{ch}' from line {ln}"
    return True, "ok"


def method_body(source: str, signature_regex: str) -> str:
    match = re.search(signature_regex, source)
    if not match:
        raise AssertionError(f"Method not found for pattern: {signature_regex}")
    brace_start = source.find("{", match.end() - 1)
    if brace_start < 0:
        raise AssertionError(f"Opening brace not found after: {signature_regex}")
    depth = 0
    i = brace_start
    n = len(source)
    state = "code"
    while i < n:
        ch = source[i]
        nxt = source[i + 1] if i + 1 < n else ""
        if state == "line_comment":
            if ch == "\n":
                state = "code"
            i += 1
            continue
        if state == "block_comment":
            if ch == "*" and nxt == "/":
                state = "code"
                i += 2
                continue
            i += 1
            continue
        if state in ("string", "char"):
            quote = '"' if state == "string" else "'"
            if ch == "\\":
                i += 2
                continue
            if ch == quote:
                state = "code"
            i += 1
            continue
        if ch == "/" and nxt == "/":
            state = "line_comment"
            i += 2
            continue
        if ch == "/" and nxt == "*":
            state = "block_comment"
            i += 2
            continue
        if ch == '"':
            state = "string"
            i += 1
            continue
        if ch == "'":
            state = "char"
            i += 1
            continue
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return source[brace_start : i + 1]
        i += 1
    raise AssertionError(f"Unclosed method body for: {signature_regex}")


def main() -> None:
    failures: list[str] = []

    def check(name: str, ok: bool, detail: str = "") -> None:
        status = "PASS" if ok else "FAIL"
        suffix = f" ({detail})" if detail else ""
        print(f"{status}: {name}{suffix}")
        if not ok:
            failures.append(f"{name}{suffix}")

    for rel in MODIFIED_FILES:
        path = JAVA / rel
        if not path.is_file():
            check(f"exists {rel}", False, "missing file")
            continue
        src = path.read_text(encoding="utf-8")
        ok, msg = balanced_delimiters(src)
        check(f"delimiters {rel}", ok, msg)

        code_only = strip_comments_and_strings(src)
        if (
            not rel.startswith("org/telegram/ui/Components/")
            and re.search(r"\bRecyclerListView\b", code_only)
            and not re.search(r"import\s+org\.telegram\.ui\.Components\.(?:\*|RecyclerListView)\s*;", code_only)
        ):
            check(f"import RecyclerListView in {rel}", False, "missing import")

    provider_src = read("org/telegram/ui/Components/blur3/drawable/color/impl/BlurredBackgroundProviderImpl.java")
    simple_ab = method_body(provider_src, r"public static BlurredBackgroundProvider simpleActionBar\(")
    check(
        "BlurredBackgroundProviderImpl.simpleActionBar uses crystal liquid glass",
        all(
            token in simple_ab
            for token in (
                "checkBlurEnabled(r)",
                "isHeaderLiquidShaderActive(r)",
                "LiquidGlassTuning.tintAlpha(",
                "ColorUtils.blendARGB(",
                "ColorUtils.setAlphaComponent(",
                "solveSrcColor(",
            )
        ),
    )

    simple_src = read("org/telegram/ui/ActionBar/SimpleActionBar.java")
    create_btns = method_body(simple_src, r"private void createButtonsGlass\(")
    create_menu = method_body(simple_src, r"public BlurredBackgroundDrawable createMenuGlass\(View owner\)")
    check(
        "SimpleActionBar enables header glass lens when no custom buttonsGlassFactory is set",
        "final boolean lens = buttonsGlassFactory == null;" in create_btns
        and create_btns.count(".setHeaderGlassLens(lens)") >= 3
        and ".setHeaderGlassLens(buttonsGlassFactory == null)" in create_menu,
    )

    ab_src = read("org/telegram/ui/ActionBar/ActionBar.java")
    ab_layout_method = method_body(ab_src, r"protected void onLayout\(boolean changed")
    check(
        "ActionBar exposes menu overlay provider, fallback toggle, isGlassMode, and compact title pill layout",
        all(
            token in ab_src
            for token in (
                "import org.telegram.messenger.Utilities;",
                "public boolean isGlassMode()",
                "public void setMenuOverlayProvider(",
                "public void setAllowMenuOverlayFallback(",
                "boolean isMenuOverlayFallbackAllowed(",
                "SimpleActionBarMenuOverlay getMenuOverlay(",
                "compactGlassTitlePill",
                "getCompactSafeLeft(",
                "getCompactSafeRightForMenuWidth(",
            )
        )
        and "tv0.getFullContentWidth()" in ab_layout_method
        and "tv0.getMeasuredWidth()" not in ab_layout_method
        and "subtitleTextView.getFullContentWidth()" in ab_layout_method,
    )

    layout_src = read("org/telegram/ui/ActionBar/ActionBarLayout.java")
    crossfade_method = method_body(layout_src, r"public boolean isActionBarInCrossfade\(")
    check(
        "ActionBarLayout skips legacy crossfade when either ActionBar is glass or embedded in fragmentView",
        all(
            token in crossfade_method
            for token in (
                "fgActionBar.shouldAddToContainer()",
                "!fgActionBar.isGlassMode()",
                "bgActionBar.shouldAddToContainer()",
                "!bgActionBar.isGlassMode()",
            )
        ),
    )

    item_src = read("org/telegram/ui/ActionBar/ActionBarMenuItem.java")
    check(
        "ActionBarMenuItem supports adaptive search field colors and ActionBar overlay fallback",
        "public void setSearchFieldColors(" in item_src
        and item_src.count("isMenuOverlayFallbackAllowed()") >= 2,
    )

    base_frag_src = read("org/telegram/ui/ActionBar/BaseFragment.java")
    base_crossfade = method_body(base_frag_src, r"public boolean isActionBarCrossfadeEnabled\(")
    check(
        "BaseFragment wires ActionBar overlay provider, padding hooks, light status bar, and skips crossfade for glass headers",
        all(
            token in base_frag_src
            for token in (
                "actionBar.setMenuOverlayProvider(this::getSimpleMenuOverlay);",
                "protected int getListViewForSimpleGlassTopPadding()",
                "protected int getListViewForSimpleGlassBottomPadding()",
                "listViewForSimpleGlass != null",
            )
        )
        and "!actionBar.shouldAddToContainer()" in base_crossfade
        and "actionBar.isGlassMode()" in base_crossfade,
    )

    simple_glass_src = read("org/telegram/utils/glass/BaseFragmentSimpleGlass.java")
    check(
        "BaseFragmentSimpleGlass enables crystal ActionBar pills, lens, menu glass, and adaptive header colors",
        all(
            token in simple_glass_src
            for token in (
                "actionBar.doNotDrawCenterGlass = false;",
                "actionBar.setHeaderGlassLens(true);",
                "actionBar.setCompactGlassTitlePill(true);",
                "actionBar.setAllowMenuOverlayFallback(true);",
                "actionBar.setMenuGlass(menuFactory, menuGlassSource);",
                "BlurredBackgroundProviderImpl.dialogsMenuGlass(resourceProvider)",
                "actionBar.setMenuRise(dp(1.5f));",
                "applyAdaptiveHeaderColors(actionBar, resourceProvider);",
                "public static void applyAdaptiveHeaderColors(",
            )
        ),
    )

    blurred_rv_src = read("org/telegram/ui/Components/BlurredRecyclerView.java")
    check(
        "BlurredRecyclerView supports setSimpleGlassMode to avoid -203dp blur offset",
        "public void setSimpleGlassMode(boolean" in blurred_rv_src
        and "!simpleGlassMode && SharedConfig.chatBlurEnabled()" in blurred_rv_src,
    )

    section_rv_src = read("app/exteraless/appearance/SectionCardRecyclerView.java")
    set_legacy = method_body(section_rv_src, r"public void setLegacySections\(boolean")
    check(
        "SectionCardRecyclerView keeps ListSectionsDecoration active for rounded card shadows",
        "super.setLegacySections(true);" in set_legacy,
    )

    neko_base_src = read("tw/nekomimi/nekogram/settings/BaseNekoSettingsActivity.java")
    check(
        "BaseNekoSettingsActivity opts into simple glass mode and preserves status bar contrast",
        all(
            token in neko_base_src
            for token in (
                "protected boolean useSimpleHeaderGlass()",
                "protected RecyclerListView getListViewForSimpleGlass()",
                "listView.setSimpleGlassMode(true);",
                "!useSimpleHeaderGlass()",
            )
        ),
    )

    open_extera_src = read("app/exteraless/settings/OpenExteraSettingsActivity.java")
    check(
        "OpenExteraSettingsActivity sets zero top padding, fades title pill on scroll, and cleans up animator",
        all(
            token in open_extera_src
            for token in (
                "protected int getListViewForSimpleGlassTopPadding()",
                "return 0;",
                "headerTitleAnimator",
                "onFragmentDestroy()",
            )
        )
        and "actionBar.setAdaptiveBackground(" not in open_extera_src
        and "setupAdaptiveBackground" not in open_extera_src,
    )

    migrated_activities = [
        ("tw/nekomimi/nekogram/settings/BaseNekoXSettingsActivity.java", True, False),
        ("com/exteragram/messenger/preferences/BasePreferencesActivity.java", True, True),
        ("app/exteraless/plugins/ui/PluginSettingsActivity.java", False, False),
        ("app/exteraless/settings/OpenExteraAppNavigationActivity.java", False, False),
        ("app/exteraless/plugins/ui/PluginsActivity.java", True, False),
        ("app/exteraless/plugins/ui/PluginsInfoActivity.java", True, False),
        ("app/exteraless/feed/ui/FeedChannelsActivity.java", True, False),
        ("app/exteraless/feed/ui/FeedCategoryEditActivity.java", True, True),
        ("app/exteraless/settings/InlineResultsActivity.java", True, True),
        ("app/exteraless/plugins/ui/PluginActivityLogActivity.java", True, True),
        ("app/exteraless/plugins/ui/PluginPermissionsActivity.java", True, True),
    ]
    for rel, needs_getter, needs_edge in migrated_activities:
        src = read(rel)
        check(f"{rel} removes legacy actionBar.setAdaptiveBackground", "setAdaptiveBackground(" not in src)
        if needs_getter:
            check(f"{rel} overrides getListViewForSimpleGlass", "protected RecyclerListView getListViewForSimpleGlass()" in src)
        if needs_edge:
            check(f"{rel} enables isSupportEdgeToEdge", "public boolean isSupportEdgeToEdge()" in src)

    feed_cat_src = read("app/exteraless/feed/ui/FeedCategoryEditActivity.java")
    check(
        "FeedCategoryEditActivity preserves 72dp bottom clearance for floating button",
        "protected int getListViewForSimpleGlassBottomPadding()" in feed_cat_src
        and "AndroidUtilities.dp(72)" in feed_cat_src,
    )

    if failures:
        raise SystemExit(f"{len(failures)} check(s) failed")
    print("All settings header glass checks passed.")


if __name__ == "__main__":
    main()
