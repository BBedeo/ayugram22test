#!/usr/bin/env python3
"""Regression guard for ExFullGram's independently configurable elastic pills.
Static contracts only: Android compilation and physical touch tests are separate.
"""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
java = root / "TMessagesProj/src/main/java"
config = (root / "TMessagesProj/src/main/kotlin/app/exteraless/appearance/AppearanceConfig.kt").read_text()
appearance = (java / "app/exteraless/settings/OpenExteraAppearanceActivity.java").read_text()
nav = (java / "org/telegram/ui/MainTabsLayout.java").read_text()
buttons = (java / "org/telegram/ui/Components/voip/VoIPToggleButton.java").read_text()
switch = (java / "org/telegram/ui/Components/voip/VoIpSwitchLayout.java").read_text()
top = (java / "app/exteraless/appearance/ElasticCallIconView.java").read_text()
call = (java / "org/telegram/ui/VoIPFragment.java").read_text()
slider = (java / "org/telegram/ui/Components/SeekBarView.java").read_text()
gesture = (java / "app/exteraless/appearance/ElasticPillGesture.java").read_text()

checks = {
    "Opt-in independent of Liquid Glass":
        'addConfig("OEAppearanceElasticDragPills", ConfigItem.configTypeBool, false)' in config
        and "universalLiquidGlass" not in gesture,
    "User can toggle elastic controls in appearance and rebuild existing views":
        "elasticDragPillsRow" in appearance
        and "item = AppearanceConfig.elasticDragPills;" in appearance
        and "rebuild = true;" in appearance,
    "Nav pill follows measured tab centers and widths":
        "getInterpolatedWidthByX(x, this)" in nav
        and "syncElasticPillToSelected" in nav
        and "selectedTabPositionX.animateToFinalPosition(cx)" in nav,
    "Nav changes page only after releasing drag":
        "if (!ElasticPillGesture.enabled()) setTabSelected(found, true);" in nav
        and "lastLongSelectedView.performClick();" in nav,
    "Nav never scales entire bar while elastic mode is active":
        "if (!ElasticPillGesture.enabled()) animatorIsScaled.setValue(true, true);" in nav,
    "Navigation gestures are blocked only when a finger touches a tab":
        "findChildUnder(this, ev.getX(), ev.getY()) != null" in nav
        and "requestDisallowInterceptTouchEvent(true)" in nav,
    "Cancelled nav gesture restores the real selected tab":
        "syncElasticPillToSelected(true);" in nav
        and "onLongPressCancelled" in nav,
    "VoIP circle drag cannot trigger hang-up":
        "cancelClickForDrag" in buttons
        and "MotionEvent.ACTION_CANCEL" in buttons,
    "VoIP mic/video/speaker actions ignore dragged release":
        "if (!dragged && isClick(" in switch,
    "Top call icons use elastic widgets":
        "new ElasticCallIconView(context)" in call
        and "consumedDrag" in top,
    "Slider uses only thumb geometry and protects page navigation":
        "elasticThumbStretch" in slider
        and "requestDisallowInterceptTouchEvent(true)" in slider
        and "canvas.drawRoundRect(rect, height, height, outerPaint1);" in slider,
    "Elastic motion returns to origin without modifying hit boxes":
        "Math.tanh" in gesture
        and "OvershootInterpolator" in gesture
        and "public boolean up()" in gesture,
}
for name, passes in checks.items():
    print(("PASS" if passes else "FAIL") + ": " + name)
if not all(checks.values()):
    raise SystemExit("Elastic drag regression checks failed")
