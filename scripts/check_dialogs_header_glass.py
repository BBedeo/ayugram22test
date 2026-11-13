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


tuning = read(JAVA / "app/exteraless/appearance/LiquidGlassTuning.java")
header_shader = read(RAW / "liquid_glass_header_shader.agsl")
effect = read(JAVA / "org/telegram/ui/Components/blur3/LiquidGlassEffect.java")
drawable = read(JAVA / "org/telegram/ui/Components/blur3/drawable/BlurredBackgroundDrawable.java")
provider = read(JAVA / "org/telegram/ui/Components/blur3/drawable/color/impl/BlurredBackgroundProviderImpl.java")
action_bar = read(JAVA / "org/telegram/ui/ActionBar/ActionBar.java")
simple_action_bar = read(JAVA / "org/telegram/ui/ActionBar/SimpleActionBar.java")
simple_text = read(JAVA / "org/telegram/ui/ActionBar/SimpleTextView.java")
menu_overlay = read(JAVA / "org/telegram/ui/ActionBar/SimpleActionBarMenuOverlay.java")
item_options = read(JAVA / "org/telegram/ui/Components/ItemOptions.java")
search_field = read(JAVA / "org/telegram/ui/Components/FragmentSearchField.java")
dialogs = read(JAVA / "org/telegram/ui/DialogsActivity.java")
popup_window = read(JAVA / "org/telegram/ui/ActionBar/ActionBarPopupWindow.java")
menu_item = read(JAVA / "org/telegram/ui/ActionBar/ActionBarMenuItem.java")
menu_sub_item = read(JAVA / "org/telegram/ui/ActionBar/ActionBarMenuSubItem.java")
chats_config = read(ROOT / "TMessagesProj/src/main/kotlin/app/exteraless/chats/ChatsConfig.kt")
oe_chats = read(JAVA / "app/exteraless/settings/OpenExteraChatsActivity.java")

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
    "Balanced braces, parens, and quotes across all 13 touched files": all(
        balanced_delimiters(src)
        for src in (tuning, header_shader, effect, drawable, provider, action_bar, simple_action_bar, simple_text, menu_overlay, item_options, search_field, dialogs, popup_window)
    ),
    "Header Liquid Glass AGSL shader declares all runtime uniforms and crystal refraction with wide-pill glint compensation and no milky dome wash": (
        all(u in header_shader for u in REQUIRED_UNIFORMS)
        and "half4 main(in float2 fragCoord)" in header_shader
        and "circleMap(" in header_shader
        and "mirrorSideUV" in header_shader
        and "mirrorVertUV" in header_shader
        and "lightWallBoost" in header_shader
        and "contrastVeil" in header_shader
        and "pillWeight" in header_shader
        and "offDiagTopRight" in header_shader
        and "offDiagBotLeft" in header_shader
        and "darkGlintScale" in header_shader
        and "domeSheen" not in header_shader
    ),
    "LiquidGlassEffect selects liquid_glass_header_shader only for headerGlassLens": (
        "public LiquidGlassEffect(RenderNode node, boolean elasticPillLens, boolean headerGlassLens)" in effect
        and "R.raw.liquid_glass_header_shader" in effect
        and "R.raw.liquid_glass_pill_shader" in effect
        and "R.raw.liquid_glass_shader" in effect
    ),
    "BlurredBackgroundDrawable wires headerGlassLens with setting-driven optics and fallback rim protection": (
        "public BlurredBackgroundDrawable setHeaderGlassLens(boolean enabled)" in drawable
        and "new LiquidGlassEffect(renderNodeFill, false, true)" in drawable
        and "if (!(elasticPillLens || headerGlassLens) || !isLiquidGlassEffectEnabled())" in drawable
        and "headerGlassLens ? 0.40f : 0.20f" in drawable
        and "LiquidGlassTuning.thicknessDp(headerGlassLens)" in drawable
        and "LiquidGlassTuning.headerRefractionScale()" in drawable
        and "LiquidGlassTuning.headerShine()" in drawable
        and "LiquidGlassTuning.sanitizeRimStroke" in drawable
    ),
    "LiquidGlassTuning exposes setting-scaled crystal header optics (tintAlphaHeader, thicknessDp, headerShine, headerRefractionScale)": (
        "public static float tintAlphaHeader(boolean dark, float original)" in tuning
        and "public static int thicknessDp(boolean headerLens)" in tuning
        and "public static float headerShine()" in tuning
        and "public static float headerRefractionScale()" in tuning
    ),
    "BlurredBackgroundProviderImpl uses clear crystal dialogsHeaderGlass (no matte solveSrcColor milk) and outline-free dark dialogsMenuGlass": (
        "public static BlurredBackgroundProvider dialogsHeaderGlass(Theme.ResourcesProvider resourcesProvider)" in provider
        and "public static BlurredBackgroundProvider dialogsMenuGlass(Theme.ResourcesProvider resourcesProvider)" in provider
        and "if (!checkBlurEnabled(r))" in provider
        and "LiquidGlassTuning.tintAlphaHeader(" in provider
        and "ColorUtils.setAlphaComponent(crystalTarget, a)" in provider
        and "liftedTarget" not in provider
        and "0x18FFFFFF" not in provider
        and "0x10FFFFFF" not in provider
    ),
    "SimpleTextView exposes full content width including drawables and gravity": (
        "public int getFullContentWidth()" in simple_text
        and "public int getGravity()" in simple_text
        and "leftDrawable.getIntrinsicWidth()" in simple_text
        and "rightDrawable.getIntrinsicWidth()" in simple_text
    ),
    "FragmentSearchField preserves blurred background visibility, enables active header lens, and animates tactile press swell": (
        "private float blurredBackgroundVisibility;" in search_field
        and "applyBlurredBackgroundVisibility();" in search_field
        and "drawable.setHeaderGlassLens(true);" in search_field
        and "ANIMATOR_ID_GLASS_PRESSED" in search_field
        and "blurredBackgroundDrawable.setElasticPressProgress" in search_field
    ),
    "ActionBar supports compact title pill, menu glass color provider crossfade, and tactile press optics across back, title, and menu pills": (
        "public void setHeaderGlassLens(boolean enabled)" in action_bar
        and "public void setCompactGlassTitlePill(boolean enabled)" in action_bar
        and "public void setMenuGlassColorProvider(" in action_bar
        and "animatorBackGlassPressed" in action_bar
        and "animatorMenuGlassPressed" in action_bar
        and "animatorTitleGlassPressed" in action_bar
        and "glassTitleRestRect" in action_bar
        and "canvas.scale(pressScale, pressScale" in action_bar
        and "private void drawCompactTitleGlassPill(" in action_bar
        and "backButtonImageView.setTranslationX(dp(2));" in action_bar
        and "(chatAvatarContainer != null || compactGlassTitlePill) && !actionModeVisible && !isSearchFieldVisible" in action_bar
    ),
    "SimpleActionBarMenuOverlay and ItemOptions expose public openInOverlay morphing and intra-menu slide selection": (
        "public void open(MenuOverlayHost bar, @Nullable ActionBarMenuItem item, ActionBarPopupWindow.ActionBarPopupWindowLayout content)" in menu_overlay
        and "public ActionBarPopupWindow.ActionBarPopupWindowLayout getContent()" in menu_overlay
        and "content.setDragSelectEnabled(true);" in menu_overlay
        and "public boolean openInOverlay(@Nullable MenuOverlayHost host, @Nullable ActionBarMenuItem item, @Nullable SimpleActionBarMenuOverlay overlay" in item_options
        and "lastLayout.setDragSelectEnabled(true);" in item_options
        and "public void setDragSelectEnabled(boolean enabled)" in popup_window
        and "canActivePageScrollVertically()" in popup_window
        and "dragHoverStrokePaint" in popup_window
    ),
    "DialogsActivity wires morphing menu overlay, dynamic blur capture expansion, and synchronized EASE_OUT_QUINT search": (
        "private boolean shouldUseDialogsHeaderGlass()" in dialogs
        and "actionBar.setupGlass(iBlur3FactoryLiquidGlass, BlurredBackgroundProviderImpl.dialogsHeaderGlass(resourceProvider), false);" in dialogs
        and "actionBar.setCompactGlassTitlePill(true);" in dialogs
        and "actionBar.setMenuGlass(menuFactory, menuSource);" in dialogs
        and "BlurredBackgroundProviderImpl.dialogsMenuGlass(resourceProvider)" in dialogs
        and "showDialogsOptionsMenu(ItemOptions io)" in dialogs
        and "optionsItem.setDragOpenMenu(true);" in dialogs
        and "actionBar.getOpenMenuOverlay() != null" in dialogs
        and "actionBar.isMenuOverlayOpened()" in dialogs
        and "shouldUseDialogsHeaderGlass() ? CubicBezierInterpolator.EASE_OUT_QUINT : CubicBezierInterpolator.EASE_OUT" in dialogs
        and "dialogsTopFadeView" in dialogs
        and "topFadeDrawable.setOverrideFadeColor(getThemedColor(Theme.key_windowBackgroundWhite));" in dialogs
        and "checkUi_searchFieldStyle();" in dialogs
        and "getRightSlidingProgress()" in dialogs
        and "(headerGlass && (child instanceof ViewPage || child == searchViewPager))" in dialogs
    ),
    "Header glass tames 3x slate over-saturation, expanded menus skip coarse corner glare, hover uses directional glass pill, and drag-out dismisses menus per ChatsConfig": (
        "clamp(minHalf / 23.0, 1.0, 3.6)" in header_shader
        and "deSatScale" in header_shader
        and "half3(0.92, 0.96, 1.0)" in header_shader
        and "glass.setHeaderGlassLens(false);" in menu_overlay
        and ".setHeaderGlassLens(false)" in action_bar
        and "public void setUseCustomGlassHover(boolean enabled)" in menu_sub_item
        and "applyCustomGlassHoverToTree(" in popup_window
        and "dragHoverFillPaint.setShader(new LinearGradient(" in popup_window
        and "dragHoverStrokePaint.setShader(new LinearGradient(" in popup_window
        and "ChatsConfig.dismissMenuOnDragOut()" in menu_item
        and "ChatsConfig.dismissMenuOnDragOut()" in popup_window
        and "ChatsConfig.dismissMenuOnDragOut()" in item_options
        and "val dismissMenuOnDragOut" in chats_config
        and "OEChatsDismissMenuOnDragOut" in oe_chats
    ),
    "Glass menus collapse GapView items cleanly across popups and rounded settings cards drop legacy greydivider bars": (
        lambda repo: (
            (lambda glass_menu, menu_inj, back_menu, chat_notif, dialogs, chat, base_neko, dc, passcode, notice: (
                "setCollapsedForGlass" in popup_window
                and "isCollapsedForGlass" in popup_window
                and "setMeasuredDimension(0, 0)" in popup_window
                and "((GapView) child).isCollapsedForGlass()" in popup_window
                and "ItemOptions.setGapCollapsedForGlass(" in menu_overlay
                and "ItemOptions.setGapBackgroundColor(" not in menu_overlay
                and "collapseGapsForGlass" in item_options
                and "setGapCollapsedForGlass(" in item_options
                and "LiquidGlassTuning.isActive()" in item_options
                and "ItemOptions.setGapCollapsedForGlass(popupLayout, true)" in menu_item
                and "new ActionBarPopupWindow.GapView(" in menu_item
                and "Color.TRANSPARENT" in glass_menu
                and "setCollapsedForGlass(" in glass_menu
                and "ItemOptions.setGapCollapsedForGlass(wrapper.getSwipeBack(), true)" in menu_inj
                and "new ActionBarPopupWindow.GapView(" in back_menu
                and "setCollapsedForGlass(true)" in back_menu
                and "new ActionBarPopupWindow.GapView(" in chat_notif
                and "setCollapsedForGlass(true)" in dialogs
                and "setCollapsedForGlass(true)" in chat
                and "ShadowSectionCell" in base_neko
                and "TextInfoPrivacyCell" in base_neko
                and "AppearanceConfig.sectionRadius() > 0" in dc
                and "holder.itemView.setBackground(null)" in dc
                and "AppearanceConfig.sectionRadius() > 0" in passcode
                and "cell.setBackground(null)" in passcode
                and "AppearanceConfig.sectionRadius() > 0" in notice
                and "cell.setBackground(null)" in notice
            ))(
                (repo / "TMessagesProj/src/main/java/app/exteraless/appearance/GlassMenuHelper.java").read_text(encoding="utf-8"),
                (repo / "TMessagesProj/src/main/java/app/exteraless/plugins/menus/MenuInjector.java").read_text(encoding="utf-8"),
                (repo / "TMessagesProj/src/main/java/org/telegram/ui/Components/BackButtonMenu.java").read_text(encoding="utf-8"),
                (repo / "TMessagesProj/src/main/java/org/telegram/ui/Components/ChatNotificationsPopupWrapper.java").read_text(encoding="utf-8"),
                (repo / "TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java").read_text(encoding="utf-8"),
                (repo / "TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java").read_text(encoding="utf-8"),
                (repo / "TMessagesProj/src/main/java/tw/nekomimi/nekogram/settings/BaseNekoSettingsActivity.java").read_text(encoding="utf-8"),
                (repo / "TMessagesProj/src/main/java/tw/nekomimi/nekogram/DatacenterActivity.java").read_text(encoding="utf-8"),
                (repo / "TMessagesProj/src/main/java/tw/nekomimi/nekogram/settings/NekoPasscodeSettingsActivity.java").read_text(encoding="utf-8"),
                (repo / "TMessagesProj/src/main/java/tw/nekomimi/nekogram/config/cell/ConfigCellNotice.java").read_text(encoding="utf-8"),
            )
        )
    )(__import__("pathlib").Path(__file__).resolve().parents[1]),
}

for title, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {title}")

fails = [title for title, ok in checks.items() if not ok]
if fails:
    raise SystemExit(f"{len(fails)} dialogs header glass checks failed")
