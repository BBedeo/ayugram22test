#!/usr/bin/env python3
"""Regression and syntax sanity guard for DialogsActivity top Liquid Glass header."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "TMessagesProj/src/main/java"
RAW = ROOT / "TMessagesProj/src/main/res/raw"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def balanced_delimiters(source: str) -> bool:
    stack = []
    pairs = {")": "(", "}": "{", "]": "["}
    in_string = False
    in_char = False
    in_line_comment = False
    in_block_comment = False
    escape = False
    i = 0
    n = len(source)
    while i < n:
        ch = source[i]
        nxt = source[i + 1] if i + 1 < n else ""
        if in_line_comment:
            if ch == "\n":
                in_line_comment = False
        elif in_block_comment:
            if ch == "*" and nxt == "/":
                in_block_comment = False
                i += 1
        elif in_string:
            if escape:
                escape = False
            elif ch == "\\":
                escape = True
            elif ch == '"':
                in_string = False
        elif in_char:
            if escape:
                escape = False
            elif ch == "\\":
                escape = True
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
            elif ch in "({[":
                stack.append(ch)
            elif ch in ")}]":
                if not stack or stack[-1] != pairs[ch]:
                    return False
                stack.pop()
        i += 1
    return not stack and not in_string and not in_char and not in_block_comment


header_shader = read(RAW / "liquid_glass_header_shader.agsl")
effect = read(JAVA / "org/telegram/ui/Components/blur3/LiquidGlassEffect.java")
drawable = read(JAVA / "org/telegram/ui/Components/blur3/drawable/BlurredBackgroundDrawable.java")
provider = read(JAVA / "org/telegram/ui/Components/blur3/drawable/color/impl/BlurredBackgroundProviderImpl.java")
action_bar = read(JAVA / "org/telegram/ui/ActionBar/ActionBar.java")
simple_text = read(JAVA / "org/telegram/ui/ActionBar/SimpleTextView.java")
menu_overlay = read(JAVA / "org/telegram/ui/ActionBar/SimpleActionBarMenuOverlay.java")
item_options = read(JAVA / "org/telegram/ui/Components/ItemOptions.java")
search_field = read(JAVA / "org/telegram/ui/Components/FragmentSearchField.java")
dialogs = read(JAVA / "org/telegram/ui/DialogsActivity.java")

REQUIRED_UNIFORMS = [
    "uniform shader img;",
    "uniform float2 center;",
    "uniform float2 size;",
    "uniform float4 radius;",
    "uniform float thickness;",
    "uniform float refract_index;",
    "uniform float refract_intensity;",
    "uniform float glass_press;",
    "uniform float glass_highlight;",
    "uniform float glass_light_angle;",
    "uniform float glass_motion;",
    "uniform float glass_adaptive;",
    "uniform float4 foreground_color_premultiplied;",
]

checks = {
    "Balanced braces, parens, and quotes across all 10 touched files": all(
        balanced_delimiters(src)
        for src in (header_shader, effect, drawable, provider, action_bar, simple_text, menu_overlay, item_options, search_field, dialogs)
    ),
    "Header Liquid Glass AGSL shader declares all runtime uniforms and crystal refraction without milky dome wash": (
        all(u in header_shader for u in REQUIRED_UNIFORMS)
        and "half4 main(in float2 fragCoord)" in header_shader
        and "circleMap(" in header_shader
        and "mirrorSideUV" in header_shader
        and "mirrorVertUV" in header_shader
        and "lightWallBoost" in header_shader
        and "contrastVeil" in header_shader
        and "domeSheen" not in header_shader
    ),
    "LiquidGlassEffect selects liquid_glass_header_shader only for headerGlassLens": (
        "public LiquidGlassEffect(RenderNode node, boolean elasticPillLens, boolean headerGlassLens)" in effect
        and "R.raw.liquid_glass_header_shader" in effect
        and "R.raw.liquid_glass_pill_shader" in effect
        and "R.raw.liquid_glass_shader" in effect
    ),
    "BlurredBackgroundDrawable wires headerGlassLens with fallback rim protection": (
        "public BlurredBackgroundDrawable setHeaderGlassLens(boolean enabled)" in drawable
        and "new LiquidGlassEffect(renderNodeFill, false, true)" in drawable
        and "if (!(elasticPillLens || headerGlassLens) || !isLiquidGlassEffectEnabled())" in drawable
        and "headerGlassLens ? 0.40f : 0.20f" in drawable
    ),
    "BlurredBackgroundProviderImpl uses clear crystal dialogsHeaderGlass (no matte solveSrcColor milk) and dialogsMenuGlass": (
        "public static BlurredBackgroundProvider dialogsHeaderGlass(Theme.ResourcesProvider resourcesProvider)" in provider
        and "public static BlurredBackgroundProvider dialogsMenuGlass(Theme.ResourcesProvider resourcesProvider)" in provider
        and "if (!checkBlurEnabled(r))" in provider
        and "ColorUtils.setAlphaComponent(crystalTarget, a)" in provider
        and "liftedTarget" not in provider
    ),
    "SimpleTextView exposes full content width including drawables and gravity": (
        "public int getFullContentWidth()" in simple_text
        and "public int getGravity()" in simple_text
        and "leftDrawable.getIntrinsicWidth()" in simple_text
        and "rightDrawable.getIntrinsicWidth()" in simple_text
    ),
    "FragmentSearchField preserves blurred background visibility and animates optical press feedback": (
        "private float blurredBackgroundVisibility;" in search_field
        and "applyBlurredBackgroundVisibility();" in search_field
        and "ANIMATOR_ID_GLASS_PRESSED" in search_field
        and "blurredBackgroundDrawable.setElasticPressProgress" in search_field
    ),
    "ActionBar supports compact title pill, menu glass color provider crossfade, and tactile press optics": (
        "public void setHeaderGlassLens(boolean enabled)" in action_bar
        and "public void setCompactGlassTitlePill(boolean enabled)" in action_bar
        and "public void setMenuGlassColorProvider(" in action_bar
        and "animatorBackGlassPressed" in action_bar
        and "animatorMenuGlassPressed" in action_bar
        and "private void drawCompactTitleGlassPill(" in action_bar
        and "backButtonImageView.setTranslationX(dp(2));" in action_bar
        and "(chatAvatarContainer != null || compactGlassTitlePill) && !actionModeVisible && !isSearchFieldVisible" in action_bar
    ),
    "SimpleActionBarMenuOverlay and ItemOptions expose public openInOverlay morphing": (
        "public void open(MenuOverlayHost bar, @Nullable ActionBarMenuItem item, ActionBarPopupWindow.ActionBarPopupWindowLayout content)" in menu_overlay
        and "public boolean openInOverlay(@Nullable MenuOverlayHost host, @Nullable ActionBarMenuItem item, @Nullable SimpleActionBarMenuOverlay overlay" in item_options
    ),
    "DialogsActivity wires morphing menu overlay, dynamic blur capture expansion, and synchronized EASE_OUT_QUINT search": (
        "private boolean shouldUseDialogsHeaderGlass()" in dialogs
        and "actionBar.setupGlass(iBlur3FactoryLiquidGlass, BlurredBackgroundProviderImpl.dialogsHeaderGlass(resourceProvider), false);" in dialogs
        and "actionBar.setCompactGlassTitlePill(true);" in dialogs
        and "actionBar.setMenuGlass(menuFactory, menuSource);" in dialogs
        and "BlurredBackgroundProviderImpl.dialogsMenuGlass(resourceProvider)" in dialogs
        and "showDialogsOptionsMenu(ItemOptions io)" in dialogs
        and "actionBar.getOpenMenuOverlay() != null" in dialogs
        and "actionBar.isMenuOverlayOpened()" in dialogs
        and "shouldUseDialogsHeaderGlass() ? CubicBezierInterpolator.EASE_OUT_QUINT : CubicBezierInterpolator.EASE_OUT" in dialogs
        and "dialogsTopFadeView" in dialogs
        and "topFadeDrawable.setOverrideFadeColor(getThemedColor(Theme.key_windowBackgroundWhite));" in dialogs
        and "checkUi_searchFieldStyle();" in dialogs
        and "getRightSlidingProgress()" in dialogs
        and "(headerGlass && (child instanceof ViewPage || child == searchViewPager))" in dialogs
    ),
}

for title, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {title}")

fails = [title for title, ok in checks.items() if not ok]
if fails:
    raise SystemExit(f"{len(fails)} dialogs header glass checks failed")
