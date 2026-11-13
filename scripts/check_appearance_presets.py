#!/usr/bin/env python3
"""Static regression checks for ExFullGram Appearance design presets."""

from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
KOTLIN = ROOT / "TMessagesProj/src/main/kotlin"
JAVA = ROOT / "TMessagesProj/src/main/java"
RES = ROOT / "TMessagesProj/src/main/res"

presets = (KOTLIN / "app/exteraless/appearance/AppearancePresets.kt").read_text(encoding="utf-8")
config = (KOTLIN / "app/exteraless/appearance/AppearanceConfig.kt").read_text(encoding="utf-8")
appearance = (JAVA / "app/exteraless/settings/OpenExteraAppearanceActivity.java").read_text(encoding="utf-8")
tabs = (JAVA / "org/telegram/ui/MainTabsLayout.java").read_text(encoding="utf-8")
profiles = (JAVA / "app/exteraless/settings/AccountSettingsProfiles.java").read_text(encoding="utf-8")
settings_helper = (JAVA / "tw/nekomimi/nekogram/helpers/SettingsHelper.java").read_text(encoding="utf-8")

required_string_keys = [
    "OEAppearancePresets",
    "OEAppearancePreset",
    "OEAppearancePresetCustom",
    "OEAppearancePresetMaxIosGlass",
    "OEAppearancePresetMaxIosGlassInfo",
    "OEAppearancePresetBalancedIosGlass",
    "OEAppearancePresetBalancedIosGlassInfo",
    "OEAppearancePresetMaterial3",
    "OEAppearancePresetMaterial3Info",
    "OEAppearancePresetClassic",
    "OEAppearancePresetClassicInfo",
    "OEAppearancePresetApplied",
]


def has_valid_strings(rel_path: str) -> bool:
    tree = ET.parse(RES / rel_path)
    root = tree.getroot()
    found = {}
    for elem in root.findall("string"):
        name = elem.attrib.get("name")
        if not name:
            continue
        if name in found:
            return False
        found[name] = elem.text or ""
    if not all(k in found and len(found[k]) > 0 for k in required_string_keys):
        return False
    return "%1$s" in found["OEAppearancePresetApplied"]


def balanced_delimiters(text: str) -> bool:
    stack = []
    pairs = {"{": "}", "(": ")", "[": "]"}
    closing = set(pairs.values())
    i = 0
    n = len(text)
    in_line_comment = False
    in_block_comment = False
    in_string = False
    in_char = False
    while i < n:
        ch = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if in_line_comment:
            if ch == "\n":
                in_line_comment = False
        elif in_block_comment:
            if ch == "*" and nxt == "/":
                in_block_comment = False
                i += 1
        elif in_string:
            if ch == "\\":
                i += 1
            elif ch == '"':
                in_string = False
        elif in_char:
            if ch == "\\":
                i += 1
            elif ch == "'":
                in_char = False
        else:
            if ch == "/" and nxt == "/":
                in_line_comment = True
                i += 1
            elif ch == "/" and nxt == "*":
                in_block_comment = True
                i += 1
            elif ch == '"':
                in_string = True
            elif ch == "'":
                in_char = True
            elif ch in pairs:
                stack.append(pairs[ch])
            elif ch in closing:
                if not stack or stack.pop() != ch:
                    return False
        i += 1
    return not stack and not in_block_comment and not in_string and not in_char


max_ios_block = presets.split("id = PRESET_MAX_IOS_GLASS,", 1)[1].split("id = PRESET_BALANCED_IOS_GLASS,", 1)[0]

checks = {
    "Opt-in Appearance defaults stay OFF on fresh installs": (
        'addConfig("OEAppearanceUniversalLiquidGlass", ConfigItem.configTypeBool, false)' in config
        and 'addConfig("OEAppearanceElasticDragPills", ConfigItem.configTypeBool, false)' in config
        and 'addConfig("OEAppearanceHideVoIPControlLabels", ConfigItem.configTypeBool, false)' in config
    ),
    "Max iOS Liquid Glass preset enables all glass, iOS bars, elastic pills, spring back, and menu drag-out": (
        "universalLiquidGlass = true" in max_ios_block
        and "liquidGlassMaterial = 2" in max_ios_block
        and "glassOutlineStyle = 0" in max_ios_block
        and "glassMessageMenu = true" in max_ios_block
        and "iosNavigationBarStyle = true" in max_ios_block
        and "iosChatHeader = true" in max_ios_block
        and "bottomNavigationMode = MainTabsLayout.BOTTOM_NAVIGATION_MODE_FLOATING" in max_ios_block
        and "navigationDrawer = false" in max_ios_block
        and "centerTitle = true" in max_ios_block
        and "squareFab = false" in max_ios_block
        and "elasticDragPills = true" in max_ios_block
        and "gooeyAvatarAnimation = true" in max_ios_block
        and "dismissMenuOnDragOut = true" in max_ios_block
        and "backAnimationStyle = BACK_ANIMATION_SPRING" in max_ios_block
        and "newNavigationBarStyle = false" in max_ios_block
        and "newChatHeaderStyle = false" in max_ios_block
        and "m3ListItems = false" in max_ios_block
    ),
    "AppearancePresets enables LiteMode blur/glass flags and refreshes GlassEngine & Theme": (
        "LiteMode.toggleFlag(LiteMode.FLAG_CHAT_BLUR, true)" in presets
        and "LiteMode.toggleFlag(LiteMode.FLAG_LIQUID_GLASS, true)" in presets
        and "GlassEngine.getCurrentGlassSettings()" in presets
        and "Theme.applyCommonTheme()" in presets
        and "syncDrawerContainerEnabled()" in presets
    ),
    "OpenExteraAppearanceActivity exposes Design Presets section, Max iOS switch, dialog, and live sync": (
        'addRow("presetsHeader")' in appearance
        and 'addRow("designPreset")' in appearance
        and 'addRow("maxIosGlassPreset")' in appearance
        and "showDesignPresetDialog()" in appearance
        and "applyDesignPreset(" in appearance
        and "notifyPresetRows()" in appearance
        and "case TYPE_SECTION_SLIDER:" in appearance
    ),
    "AccountSettingsProfiles reloads MainTabsLayout floating navigation state": (
        "public static void reloadBottomNavigationMode()" in tabs
        and "MainTabsLayout.reloadBottomNavigationMode();" in profiles
    ),
    "Global settings search indexes Design Preset and Max iOS Glass rows": (
        'SEARCH_TITLE_ALIASES.put("OEAppearance:designPreset", "OEAppearancePreset");' in settings_helper
        and 'SEARCH_TITLE_ALIASES.put("OEAppearance:maxIosGlassPreset", "OEAppearancePresetMaxIosGlass");' in settings_helper
    ),
    "Valid localized strings exist in values, values-ru, and values-uk": (
        has_valid_strings("values/strings_oe_appearance.xml")
        and has_valid_strings("values-ru/strings_oe_liquid_glass.xml")
        and has_valid_strings("values-uk/strings_oe_liquid_glass.xml")
    ),
    "Balanced delimiters across all modified Kotlin and Java source files": (
        balanced_delimiters(presets)
        and balanced_delimiters(appearance)
        and balanced_delimiters(tabs)
        and balanced_delimiters(profiles)
        and balanced_delimiters(settings_helper)
    ),
}

failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {name}")
if failed:
    raise SystemExit(f"{len(failed)} appearance preset checks failed")
