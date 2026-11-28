#!/usr/bin/env python3
"""Guards the iOS player's cover without art and its collapsed mode."""
import pathlib
import sys

PLAYER = pathlib.Path(__file__).resolve().parents[1] / "TMessagesProj/src/main/java/app/exteraless/player"
NEEDLES = {
    # the placeholder is a gradient of the same tints as the drifting background, with a smaller note
    "CoverImage.java": ("public void setGradient(int[] colors, int foreground)",),
    "PlayerSheet.java": (
        "new CoverImage(context, Md3Player.isIosPlayer() ? 72 : 120)",
        "cover.setGradient(tinted, c.onPrimaryContainer);", "smallCover.setGradient(tinted, c.onPrimaryContainer);",
        "final int[] tints = auroraTints(colors);",
        # the collapsed capsule wears the same lens as the other capsules, not its own plate with a shadow
        "collapsedBar.setBackground(glassPlate(5, c));"),
    "PlayerCollapsedBar.java": ("if (getBackground() == null) {",),
    # a silent band stays a short bar, never a lone dot
    "PlayerVisualizer.java": ("final float floor = bw * 1.6f;",),
}

errors = []
for name, needles in NEEDLES.items():
    text = (PLAYER / name).read_text(encoding="utf-8")
    errors.extend(f"{name}: missing {needle!r}" for needle in needles if needle not in text)

if errors:
    sys.exit("\n".join(errors))
print("Player cover and collapsed mode: OK")
