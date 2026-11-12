#!/usr/bin/env python3
"""Static regression contracts for ExFullGram's opt-in elastic controls.

Not a visual or runtime test; use Android Gradle plus on-device verification.
"""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
java = root / "TMessagesProj/src/main/java"

def read(rel):
    return (java / rel).read_text(encoding="utf-8")

config = (root / "TMessagesProj/src/main/kotlin/app/exteraless/appearance/AppearanceConfig.kt").read_text()
appearance = read("app/exteraless/settings/OpenExteraAppearanceActivity.java")
nav = read("org/telegram/ui/MainTabsLayout.java")
main = read("org/telegram/ui/MainTabsActivity.java")
pill = read("app/exteraless/appearance/ElasticGlassPillRenderer.java")
buttons = read("org/telegram/ui/Components/voip/VoIPToggleButton.java")
switch = read("org/telegram/ui/Components/voip/VoIpSwitchLayout.java")
top = read("app/exteraless/appearance/ElasticCallIconView.java")
call = read("org/telegram/ui/VoIPFragment.java")
slider = read("org/telegram/ui/Components/SeekBarView.java")
gesture = read("app/exteraless/appearance/ElasticPillGesture.java")

checks = {
    "Optional, off by default, independent of Liquid Glass":
        'addConfig("OEAppearanceElasticDragPills", ConfigItem.configTypeBool, false)' in config
        and "universalLiquidGlass" not in gesture,
    "Appearance setting triggers view rebuild":
        "elasticDragPillsRow" in appearance
        and "item = AppearanceConfig.elasticDragPills;" in appearance
        and "rebuild = true;" in appearance,
    "Selected nav follows the real ViewPager state, not a fading tab":
        "actualSelected = tab;" in main
        and "tabsView.setElasticSelectedTab(actualSelected, animated)" in main
        and "elasticSelectedTab != null" in nav,
    "Measured tab sizes constrain a single pill's width and height":
        "getInterpolatedWidthByX(centerX, this)" in nav
        and "Math.min(availableWidth, dp(70)) + extension" in nav
        and "Math.min(dp(14), travelled * .15f)" in nav,
    "Native glass sampling is preserved behind icons":
        "iBlur3FactoryGlass.create(tabsView," in main
        and "elasticGlassPill.draw(canvas, selectorRect, elasticPillGlass" in nav
        and "nativeGlass.draw(canvas);" in pill,
    "No giant fake white arcs or flat opaque image":
        "canvas.drawArc(" not in pill
        and "new LinearGradient" in pill
        and "0x22FFFFFF" in pill,
    "Touch motion drives only the pill and blocks parent swipe":
        "ViewConfiguration.get(getContext()).getScaledTouchSlop()" in nav
        and "animatedLongSelectedViewCenterX = clampXToChildrenCenters(x + elasticTouchOffsetX, this)" in nav
        and "requestDisallowInterceptTouchEvent(true)" in nav,
    "Dragging cancels child click; click commits only on finger release":
        "super.dispatchTouchEvent(cancel);" in nav
        and "hovered.performClick();" in nav
        and "action == MotionEvent.ACTION_UP" in nav
        and "if (!ElasticPillGesture.enabled())" in nav,
    "Cancelled drag restores actual tab":
        "finishElasticTouch(false)" in nav
        and "syncElasticPillToSelected(true);" in nav,
    "Original nav and scaling remain untouched when option is off":
        "clickHelper.onTouchEvent(this, ev);" in nav
        and "if (!ElasticPillGesture.enabled()) animatorIsScaled.setValue(true, true);" in nav,
    "Call buttons cancel actions on drag":
        "cancelClickForDrag" in buttons
        and "MotionEvent.ACTION_CANCEL" in buttons
        and "if (!dragged && isClick(" in switch,
    "Top-left and top-right call buttons support drag":
        "new ElasticCallIconView(context)" in call and "consumedDrag" in top,
    "Slider thumb stretches without scaling whole row":
        "elasticThumbStretch" in slider
        and "canvas.drawRoundRect(rect, height, height, outerPaint1);" in slider,
    "Optical press snapback uses bounded translation":
        "Math.tanh" in gesture and "OvershootInterpolator" in gesture,
}
for name, good in checks.items():
    print(("PASS" if good else "FAIL") + ": " + name)
if not all(checks.values()):
    raise SystemExit("Elastic pill contract regression")
