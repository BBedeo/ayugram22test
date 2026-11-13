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
tab = read("org/telegram/ui/Components/glass/GlassTabView.java")
menu = read("org/telegram/ui/Components/ItemOptions.java")
pill = read("app/exteraless/appearance/ElasticGlassPillRenderer.java")
blur_drawable = read("org/telegram/ui/Components/blur3/drawable/BlurredBackgroundDrawable.java")
glass_effect = read("org/telegram/ui/Components/blur3/LiquidGlassEffect.java")
pill_shader = (root / "TMessagesProj/src/main/res/raw/liquid_glass_pill_shader.agsl").read_text(encoding="utf-8")
common_shader = (root / "TMessagesProj/src/main/res/raw/liquid_glass_shader.agsl").read_text(encoding="utf-8")
buttons = read("org/telegram/ui/Components/voip/VoIPToggleButton.java")
switch = read("org/telegram/ui/Components/voip/VoIpSwitchLayout.java")
top = read("app/exteraless/appearance/ElasticCallIconView.java")
call = read("org/telegram/ui/VoIPFragment.java")
slider = read("org/telegram/ui/Components/SeekBarView.java")
gesture = read("app/exteraless/appearance/ElasticPillGesture.java")

checks = {
    "Only touching the visible selected pill may start drag":
        "selectorRect.contains(elasticDownX, elasticDownY)" in nav
        and "if (!elasticTouchStartedOnPill || elasticMenuShownDuringTouch)" in nav,
    "Small wobble stays eligible for a long-press menu":
        "slop * 1.25f" in nav
        and "tabsView.onElasticTabLongPressed()" in main
        and "final boolean shown = processLongClick(v, tabIndex);" in main,
    "Popup menus use native blur with optional Liquid Glass tint":
        "o.setBlur(true);" in main
        and "GlassMenuHelper.scrimMenuBackground(resourcesProvider)" in menu
        and "LiquidGlassTuning.isActive()" in menu,
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
    "Elastic pill reuses exact native long-press tab geometry":
        "getInterpolatedWidthByX(centerX, this)" in nav
        and "final float height = Math.max(1f, availableHeight - verticalCompression + jellyY)" in nav
        and "verticalCompression = Math.min(dp(1.8f), totalStretch * .10f)" in nav
        and "final float baseWidth = Math.max(dp(28), widthAtX + jellyX)" in nav
        and "getSelectorVisualWidth()" in nav
        and "public float getSelectorVisualWidth()" in tab
        and "Math.min(dp(16), travelled * .17f)" in nav,
    "Moving pill deforms directionally but rests at native size":
        "verticalCompression" in nav
        and "leadingStretch" in nav
        and "trailingStretch" in nav
        and "direction = elasticDragging" in nav
        and "elasticGlideAnimating" in nav
        and "elasticPressScale.setStartVelocity(4.2f)" in nav
        and "travelled <= dp(3.5f)" in nav,
    "Native glass sampling and forced GPU refraction are preserved behind icons":
        "iBlur3FactoryGlass.create(tabsView," in main
        and "iBlur3FactoryTabLens.create(tabsView," in main
        and "createTabLensSource()" in main
        and "capsule.setLiquidGlassEffectAllowed();" in main
        and "capsule.setGlassSettings(new GlassSettings(" in main
        and "capsule.setIntensity(3.60f);" in main
        and "source != iBlur3SourceTabGlass" in main
        and "iBlur3SourceTabGlass.updateDisplayListIfNeeded();" in main
        and "iBlur3SourceTabLens.updateDisplayListIfNeeded();" in main
        and "elasticGlassPill.draw(canvas, selectorRect, elasticPillGlass" in nav
        and "nativeGlass.draw(canvas);" in pill,
    "Forced elastic lens keeps local Fresnel optics":
        "setLiquidOptics" in blur_drawable
        and "liquidShineOverride" in blur_drawable
        and "liquidLightAngleOverride" in blur_drawable
        and "nativeGlass.setLiquidOptics" in pill
        and "baseAngle = 315f" in pill
        and "dark ? .86f : .92f" in pill
        and "totalStretch / dp(16)" in nav,
    "Elastic lens never falls back to SOLID divider or chalky wireframe outline":
        ".setStrokeColorFull(0x00000000, 0x00000000)" in main
        and ".setStrokeWidth(AndroidUtilities.dpf2(.35f), AndroidUtilities.dpf2(.25f))" in main
        and "canRefract ? 0x14DCE8F6 : 0x24DCE8F6" in main
        and "tabsViewWrapper.setClipChildren(false);" in main,
    "Elastic navigation uses isolated shader without replacing shared glass":
        "R.raw.liquid_glass_pill_shader" in glass_effect
        and "R.raw.liquid_glass_shader" in glass_effect
        and "new LiquidGlassEffect(renderNodeFill, elasticPillLens)" in blur_drawable
        and "capsule.setElasticPillLens(true);" in main,
    "Isolated shader refracts live capture and adds dynamic internal caustics":
        "uniform shader img;" in pill_shader
        and pill_shader.count("float move =") == 1
        and "float lift =" in pill_shader
        and "float circleMap(" in pill_shader
        and "img.eval(uv)" in pill_shader
        and "glass_motion" in pill_shader
        and "float rimSpec =" in pill_shader
        and "float capBoost =" in pill_shader
        and "float meniscus =" in pill_shader
        and "float sweep = gaussian(sweepCoord" in pill_shader
        and "float rimCore = gaussian(" in pill_shader
        and "rChan" in pill_shader
        and "bChan" in pill_shader
        and "glass_press" in pill_shader
        and "half4 main" in pill_shader
        and "uniform float radius;" in (root / "TMessagesProj/src/main/res/raw/liquid_glass_pill_content_shader.agsl").read_text(encoding="utf-8")
        and "float circleMap(" in (root / "TMessagesProj/src/main/res/raw/liquid_glass_pill_content_shader.agsl").read_text(encoding="utf-8")
        and "capMask" in (root / "TMessagesProj/src/main/res/raw/liquid_glass_pill_content_shader.agsl").read_text(encoding="utf-8")
        and "specRGB" in (root / "TMessagesProj/src/main/res/raw/liquid_glass_pill_content_shader.agsl").read_text(encoding="utf-8")
        and "outAlpha" in (root / "TMessagesProj/src/main/res/raw/liquid_glass_pill_content_shader.agsl").read_text(encoding="utf-8")
        and "uniform shader img;" in common_shader,
    "Refraction highlight responds to actual frame displacement":
        "lastCenterX" in pill
        and "centerX - lastCenterX" in pill
        and "lightVelocity +=" in pill
        and "nativeGlass.setLiquidOptics" in pill,
    "Touch compression drives the real GPU shader, not a fake overlay":
        "elasticTouchStartedOnPill && !elasticMenuShownDuringTouch" in nav
        and "nativeGlass.setElasticPressProgress(pressure)" in pill
        and "setElasticPressProgress(float amount)" in blur_drawable
        and "elasticPressOverride" in blur_drawable
        and "glass_press, 0.0, 1.0" in pill_shader,
    "No fake painted double rim over the native lens":
        "canvas.drawArc(" not in pill
        and "LinearGradient" not in pill
        and "insideEdge" not in pill
        and "rim.setStyle" not in pill,
    "Touch motion drives only the pill and blocks parent swipe":
        "ViewConfiguration.get(getContext()).getScaledTouchSlop()" in nav
        and "animatedLongSelectedViewCenterX = clampXToChildrenCenters(x + elasticTouchOffsetX, this)" in nav
        and "requestDisallowInterceptTouchEvent(true)" in nav,
    "Dragging previews selection under the lens without committing the page":
        "updateElasticSelectionPreview(animatedLongSelectedViewCenterX)" in nav
        and "setGestureSelectedOverride(factor, true)" in nav
        and "clearElasticSelectionPreview()" in nav,
    "Tab icon and text accent follow the travelling lens":
        "applyColors();" in tab
        and "checkPlayAnimation(true);" in tab
        and "hasGestureSelectedOverride" in tab
        and "gestureSelectedOverride" in tab
        and "final float selectedFactor = hasGestureSelectedOverride" in tab
        and "elasticSmoothedStretch" in nav
        and "leftHeadroom" in nav
        and "final float sink =" not in nav
        and "final float targetScale = 1f + overlap * boost;" in nav
        and "matchesGesturePreview" in tab
        and "finishElasticTouch(true, false);" in nav,
    "Release keeps finger velocity and settles with a controlled spring":
        "VelocityTracker" in nav
        and "computeCurrentVelocity(1000)" in nav
        and "setStartVelocity(elasticReleaseVelocityX)" in nav
        and ".setStiffness(340f)" in nav
        and ".setDampingRatio(.72f)" in nav
        and "elasticJellyBounce" in nav
        and "elasticPressScale" in nav,
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
