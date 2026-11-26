#!/usr/bin/env python3
"""Guards the shared iOS slider thumb: one drawer for every iOS-style slider, with the real shader."""
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1] / "TMessagesProj/src/main/java"
CALLERS = (
    "org/telegram/ui/Components/SeekBarView.java",
    "org/telegram/ui/Components/SlideChooseView.java",
    "org/telegram/ui/Components/PhotoEditorSeekBar.java",
    "app/exteraless/player/WavySeekBar.java",
)
errors = []
for path in CALLERS:
    text = (ROOT / path).read_text(encoding="utf-8")
    for needle in ("IosSliderThumb(this)", "iosThumb.clipTrack(", "iosThumb.draw("):
        if needle not in text:
            errors.append(f"{path}: missing {needle}")
drawer = (ROOT / "app/exteraless/appearance/glass/IosSliderThumb.java").read_text(encoding="utf-8")
for needle in ("new LiquidGlassEffect(node, true)", "Build.VERSION.SDK_INT >= 33", "GlassTokens.isActive()",
               "ValueAnimator.areAnimatorsEnabled()", "track.draw(c)"):
    if needle not in drawer:
        errors.append(f"IosSliderThumb: missing {needle}")
# SeekBarView once skipped the track for every custom style, so the iOS slider had a thumb and no line
seek_bar = (ROOT / CALLERS[0]).read_text(encoding="utf-8")
if "custom == SLIDER_STYLE_MD3" not in seek_bar or "needCustomDraw() != -1) {\n                return;" in seek_bar:
    errors.append("SeekBarView: drawProgressBar must skip only the MD3 track")
if errors:
    sys.exit("\n".join(errors))
print("iOS slider thumb: OK")
