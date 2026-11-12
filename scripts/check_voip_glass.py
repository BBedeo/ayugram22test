#!/usr/bin/env python3
"""Regression guard for ExFullGram's optional private-call glass UI.

This checks wiring and default/fallback behavior, not visual fidelity.
Audio/video call placement and GPU rendering still need a device test.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
J = ROOT / "TMessagesProj/src/main/java"
K = ROOT / "TMessagesProj/src/main/kotlin"

def read(root, path):
    return (root / path).read_text(encoding="utf-8")

appearance = read(K, "app/exteraless/appearance/AppearanceConfig.kt")
settings = read(J, "app/exteraless/settings/OpenExteraAppearanceActivity.java")
call = read(J, "org/telegram/ui/VoIPFragment.java")
toggle = read(J, "org/telegram/ui/Components/voip/VoIPToggleButton.java")
switch = read(J, "org/telegram/ui/Components/voip/VoIpSwitchLayout.java")
bar = read(J, "org/telegram/ui/Components/voip/VoIPButtonsLayout.java")
painter = read(J, "org/telegram/ui/Components/voip/VoIPCallGlass.java")
tuning = read(J, "app/exteraless/appearance/LiquidGlassTuning.java")

checks = {
    "Caption toggle is independent from glass and defaults OFF":
        'addConfig("OEAppearanceHideVoIPControlLabels", ConfigItem.configTypeBool, false)' in appearance
        and 'AppearanceConfig.hideVoIPControlLabels.toggleConfigBool()' in settings,
    "Captions toggle is available in the calls section":
        'callsHeaderRow = addRow("callsHeader")' in settings
        and 'hideVoIPLabelsRow = addRow("hideVoIPControlLabels")' in settings
        and 'R.string.OEAppearanceHideVoIPControlLabels' in settings,
    "All four primary call controls receive the independent caption setting":
        all(f'{name}.setLabelsHidden(compactCallControls);' in call
            for name in ('bottomSpeakerBtn', 'bottomVideoBtn', 'bottomMuteBtn', 'bottomEndCallBtn'))
        and 'buttonsLayout.setCompact(compactCallControls)' in call,
    "All three top call icon backgrounds use sampled native call backdrop":
        call.count('new VoIPCallGlass.IconBackground(backgroundProvider)') == 3,
    "Four bottom circular controls use the glass painter":
        'new VoIPCallGlass.Painter()' in toggle
        and 'new VoIPCallGlass.Painter()' in switch
        and 'callGlass.drawFace(' in toggle
        and 'callGlass.drawFace(' in switch
        and 'callGlass.drawRim(' in switch,
    "Glass is gated and defaults to original Telegram drawing when disabled":
        'LiquidGlassTuning.isActive()' in toggle
        and 'LiquidGlassTuning.isActive()' in switch
        and 'LiquidGlassTuning.isActive()' in call,
    "Original icon animation and click handlers remain":
        'singleIcon.draw(canvas' in switch
        and 'selectedIcon.draw(canvas' in switch
        and 'onBtnClickedListener.onClicked(this)' in switch
        and 'icon[0].draw(canvas)' in toggle,
    "Elongation uses brief press animations and respects motion setting":
        'LiquidGlassTuning.motionEnabled()' in toggle
        and 'LiquidGlassTuning.motionEnabled()' in switch
        and 'ValueAnimator.ofFloat' in toggle
        and 'ValueAnimator.ofFloat' in switch,
    "Hidden captions remain available to TalkBack":
        'voIpButtonView.setContentDescription(newText)' in switch
        and 'info.setText(currentText)' in toggle,
    "Hidden captions cannot reappear on animated state changes":
        'if (labelsHidden)' in switch
        and 'currentTextView.setVisibility(GONE)' in switch
        and 'newTextView.setVisibility(GONE)' in switch
        and 'textLayoutContainer.setVisibility(hidden ? GONE : VISIBLE)' in toggle,
    "Compact call bar reclaims unused space":
        'AndroidUtilities.dp(compact ? 60 : 80)' in bar,
    "Top buttons share the animated gradient and avoid screenshot recapture":
        'callBackground.getLightPaint()' in painter
        and 'callBackground.getTotalWidth() > 0' in painter
        and 'getLocationInWindow(windowPosition)' in painter
        and 'LiquidGlassTuning.shine()' in painter,
    "No frame timer or invasive touch interception added to glass backgrounds":
        'setOnTouchListener' not in painter
        and 'postDelayed' not in painter
        and 'invalidateSelf();' in painter,
}
for label, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {label}")
if not all(checks.values()):
    raise SystemExit("VoIP glass/caption regression checks failed")
