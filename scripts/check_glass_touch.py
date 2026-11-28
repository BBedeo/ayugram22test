#!/usr/bin/env python3
"""Guards Glass Touch: runs the real formulas and reads the wiring that keeps existing animations safe."""
import os
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile

MAIN = pathlib.Path(__file__).resolve().parents[1] / "TMessagesProj/src/main"
JAVA = MAIN / "java"
GLASS = JAVA / "app/exteraless/appearance/glass"
errors = []


def need(path, *needles):
    if not path.exists():
        errors.append(f"{path.name}: file is missing")
        return
    text = path.read_text(encoding="utf-8")
    errors.extend(f"{path.name}: missing {needle}" for needle in needles if needle not in text)


config = (MAIN / "kotlin/app/exteraless/appearance/AppearanceConfig.kt").read_text(encoding="utf-8")
for key in ("Press", "Selection", "Back", "Pages"):
    if not re.search(rf'addConfig\("OEAppearanceGlassTouch{key}",\s*ConfigItem\.configTypeBool,\s*false\)', config):
        errors.append(f"AppearanceConfig: OEAppearanceGlassTouch{key} must exist and default to false")

need(GLASS / "SelectionGlassFrame.java",
     "boolean allSelected) {\n        if (!GlassTouchSettings.selection()) {\n            return;")
# the app's own test of the Material 3 design; segmented dividers alone are not it
need(GLASS / "GlassTouchSettings.java", "SheetSurfaceMode.isM3(null)",
     "LiteMode.isPowerSaverApplied()", "AndroidUtilities.getAnimatorDurationScale()")
need(GLASS / "GlassTouchController.java", "instanceof ChatMessageCell", "instanceof ChatActionCell",
     "instanceof ContextLinkCell", "instanceof WalletGiftCell", "getStateListAnimator()", "setRenderEffect(null)",
     "private static RuntimeShader shader;")  # compiled once, not on every press
need(JAVA / "org/telegram/ui/Components/RecyclerListView.java", "GlassTouchController.onRowPressed(")
need(JAVA / "org/telegram/ui/ChatActivity.java", "chatListView.setGlassTouchEnabled(false)",
     "glassSelection.beginFrame()", "glassSelection.addGroup(", "glassSelection.draw(")
need(JAVA / "app/exteraless/player/LyricsView.java", "list.setGlassTouchEnabled(false)")
LAYOUT = JAVA / "org/telegram/ui/ActionBar/ActionBarLayout.java"
# the previous screen's own view, as the plugin does; a header that is a separate view gets a second veil
need(LAYOUT, "glassBack.arm(fragmentView)", "glassBack.release()", "glassBack.settle()", "glassBack.pulse(")
layout = LAYOUT.read_text(encoding="utf-8")
for op in ("arm", "update", "release", "settle", "disarm", "pulse"):
    if layout.count(f"glassBack.{op}(") != layout.count(f"glassBackBar.{op}("):
        errors.append(f"ActionBarLayout: glassBack.{op} and glassBackBar.{op} must go together")
need(GLASS / "GlassBackBlur.java", "private static Object shader;", "void release()", "void pulse(",
     "void settle()", "onViewDetachedFromWindow")
need(GLASS / "GlassPageBlur.java", "addOnPreDrawListener", "removeOnPreDrawListener", "GlassTouchSettings.pages()")
need(GLASS / "GlassTouchSettings.java", "isGlassTouchPages()")
need(GLASS / "GlassTouchController.java", "ACTION_POINTER_UP", "static void onActivityTouch(",
     "static void carryToList(")
need(JAVA / "org/telegram/ui/LaunchActivity.java", "GlassTouchController.onActivityTouch(ev)")
dialogs = JAVA / "org/telegram/ui/DialogsActivity.java"
need(dialogs, "GlassTouchController.carryToList(viewPages[0].listView)")
if dialogs.read_text(encoding="utf-8").count("glassPages.start()") != 2:
    errors.append("DialogsActivity: the folder swipe and the folder tab tap must both start the tracker")
tabs = (JAVA / "org/telegram/ui/MainTabsActivity.java").read_text(encoding="utf-8")
if tabs.count("pulseGlassPages();") != 2:
    errors.append("MainTabsActivity: the tab tap and the back to chats must both pulse the pages")
need(JAVA / "app/exteraless/settings/OpenExteraAppearanceActivity.java", 'addRow("glassTouchPages")')
for strings in ("values/strings_oe_appearance.xml", "values-ru/strings_oe_liquid_glass.xml",
                "values-uk/strings_oe_liquid_glass.xml"):
    need(MAIN / "res" / strings, 'name="OEAppearanceGlassTouchPages"', 'name="OEAppearanceGlassTouchPagesSub"')
need(MAIN / "res/raw/glass_touch_lens_shader.agsl", "uniform shader img;", "uniform float2 center;",
     "uniform float radius;", "uniform float rim;", "uniform float p;")
need(MAIN / "res/raw/glass_touch_back_shader.agsl", "uniform shader img;", "uniform float2 size;",
     "uniform float p;")

CASES = (
    ("backStrength(0)", 1), ("backStrength(0.275f)", 0.89), ("backStrength(0.5499f)", 0.78),
    ("backStrength(0.55f)", 0.78), ("backStrength(1)", 0), ("backStrength(-1)", 1), ("backStrength(2)", 0),
    ("squeezeScale(100, 1)", 0.91), ("squeezeScale(400, 1)", 0.96),
    ("lensRadius(400, 72, 1)", 96), ("lensRadius(60, 60, 1)", 48), ("lensRadius(150, 50, 1)", 75),
    ("pageStrength(0, 1000)", 0), ("pageStrength(500, 1000)", 0.35), ("pageStrength(-1000, 1000)", 0.7),
    ("pageStrength(1500, 1000)", 0.7), ("pageStrength(100, 0)", 0),
    ("fadeStrength(1, 0, 380)", 1), ("fadeStrength(1, 190, 380)", 0.25), ("fadeStrength(0.7f, 160, 320)", 0.175),
    ("fadeStrength(0.7f, 400, 320)", 0), ("fadeStrength(1, -10, 380)", 1), ("fadeStrength(0.5f, 380, 380)", 0),
)
probe = "package app.exteraless.appearance.glass;\npublic class GlassTouchProbe {\n" \
    "  static int failed;\n" \
    "  static void is(String call, float got, float want) {\n" \
    "    if (!(Math.abs(got - want) <= 0.001f)) { failed = 1; System.out.println(call + \" = \" + got + \", want \" + want); }\n" \
    "  }\n" \
    "  public static void main(String[] args) {\n" \
    + "".join(f'    is("{call}", GlassTouchMath.{call}, {want}f);\n' for call, want in CASES) \
    + "    System.exit(failed);\n  }\n}\n"


def tool(name):
    home = os.environ.get("JAVA_HOME")
    found = shutil.which(name, path=os.path.join(home, "bin")) if home else None
    return found or shutil.which(name)


javac, java = tool("javac"), tool("java")
if not javac or not java:
    errors.append("javac and java are required (JAVA_HOME or PATH)")
elif not (GLASS / "GlassTouchMath.java").exists():
    errors.append("GlassTouchMath.java: file is missing")
else:
    with tempfile.TemporaryDirectory() as tmp:
        source = pathlib.Path(tmp) / "GlassTouchProbe.java"
        source.write_text(probe, encoding="utf-8")
        build = subprocess.run([javac, "-encoding", "utf-8", "-d", tmp, str(GLASS / "GlassTouchMath.java"), str(source)],
                               capture_output=True, text=True)
        if build.returncode:
            errors.append("GlassTouchMath does not compile:\n" + build.stderr)
        else:
            run = subprocess.run([java, "-cp", tmp, "app.exteraless.appearance.glass.GlassTouchProbe"],
                                 capture_output=True, text=True)
            if run.returncode:
                errors.append("GlassTouchMath:\n" + run.stdout + run.stderr)

if errors:
    sys.exit("\n".join(errors))
print("Glass Touch: OK")
