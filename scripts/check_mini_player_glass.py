#!/usr/bin/env python3
"""Guards the glass of the main screen's mini player: the pages under it must be captured where it stands."""
import pathlib
import sys

JAVA = pathlib.Path(__file__).resolve().parents[1] / "TMessagesProj/src/main/java"
NEEDLES = {
    "org/telegram/utils/glass/GlassEngine.java": (
        "public void setExternalCapturePositions(", "allVisibleDrawablesWithDisplayListPositions.add(externalSecondCapturePosition);"),
    "org/telegram/ui/ActionBar/BaseFragment.java": ("glassEngine.setExternalCapturePositions(position, second);",),
    "app/exteraless/player/PlayerMiniView.java": ("public boolean isGlassShown()",),
    # the card is above the tabs' band or under the header: without its own area the glass samples a plain colour
    "org/telegram/ui/MainTabsActivity.java": (
        "miniPlayer.isGlassShown()", "fragment.setParentGlass(tabs, mini,",
        "if (!miniPlayerBounds.equals(miniPlayerCapturedBounds)) {"),
    # the chat list captures by itself, the parent's area never reaches it through the simple glass
    "org/telegram/ui/DialogsActivity.java": (
        "public void setParentGlass(@Nullable RectF position, @Nullable RectF second,",
        "captureBottom = Math.max(captureBottom, (int) Math.ceil(miniPlayerGlassArea.bottom));",
        "iBlur3PositionMainTabs.union(miniPlayerGlassArea);",
        # the parent's source recorded the old set of captured parts and its hash does not see them
        "onCaptured.run();"),
}

errors = []
for name, needles in NEEDLES.items():
    text = (JAVA / name).read_text(encoding="utf-8")
    errors.extend(f"{name}: missing {needle!r}" for needle in needles if needle not in text)

if errors:
    sys.exit("\n".join(errors))
print("Mini player glass: OK")
