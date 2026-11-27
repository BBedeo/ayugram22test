#!/usr/bin/env python3
"""Guards the music player's motion: the spectrum waits until it is heard, the coverless backdrop drifts and rests."""
import pathlib
import sys

JAVA = pathlib.Path(__file__).resolve().parents[1] / "TMessagesProj/src/main/java"
errors = []
for path, needles in (
        ("org/telegram/ui/Components/VideoPlayer.java",
         ("new ForwardingAudioSink(sink)", "(presentationTimeUs - heardUs)", "}, delayMs);",
          "spectrum.generation++;", "posted == generation")),
        ("app/exteraless/player/PlayerSheet.java",
         ("auroraShaders[i].setLocalMatrix(auroraMatrix);", "if (auroraDrifts())","layout.getWindowVisibility() == View.VISIBLE",
          "!MediaController.getInstance().isMessagePaused() && !org.telegram.messenger.LiteMode.isPowerSaverApplied()",
          "ValueAnimator.areAnimatorsEnabled()"))):
    text = (JAVA / path).read_text(encoding="utf-8")
    errors.extend(f"{path}: missing {needle}" for needle in needles if needle not in text)

if errors:
    sys.exit("\n".join(errors))
print("Player motion: OK")
