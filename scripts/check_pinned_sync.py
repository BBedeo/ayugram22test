#!/usr/bin/env python3
"""Guard: with "Unlimited pinned dialogs" ON, pins must never be sent to Telegram servers.

Pins beyond the server limit are local-only. Pushing the order (or a pin toggle) makes the
server reshuffle the pins of every other client, so every server request that carries pin
state in MessagesController has to be behind the NekoConfig.unlimitedPinnedDialogs gate.
"""

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
STORAGE = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/MessagesStorage.java"
CONTROLLER = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java"


def method_body(text: str, signature: str) -> str:
    start = text.find(signature)
    if start < 0:
        return ""
    brace = text.find("{", start)
    depth = 0
    for i in range(brace, len(text)):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                return text[start : i + 1]
    return ""


def main() -> int:
    text = CONTROLLER.read_text(encoding="utf-8", errors="ignore")
    failures = []

    for signature, request in (
        ("public void reorderPinnedDialogs(", "sendRequest(req"),
        ("public boolean pinDialog(", "sendRequest(req"),
    ):
        body = method_body(text, signature)
        if not body:
            failures.append(f"{signature} not found")
            continue
        gate = body.find("NekoConfig.unlimitedPinnedDialogs.Bool()")
        send = body.find(request)
        if gate < 0:
            failures.append(f"{signature} is not gated by unlimitedPinnedDialogs")
        elif send >= 0 and gate > send:
            failures.append(f"{signature} sends the request before checking unlimitedPinnedDialogs")

    # Server refreshes arrive unpinned (exclude_pinned) and used to wipe local-only pins,
    # leaving only the 4-5 server pins in the forward picker.
    if "keepsLocalPin(currentDialog, value)" not in text or "keepsLocalPin(currentDialog, newDialog)" not in text:
        failures.append("dialog refreshes from the server must go through keepsLocalPin()")
    if not re.search(r"keepsLocalPin\([^)]*\)\s*\{\s*return NekoConfig\.unlimitedPinnedDialogs\.Bool\(\)", text):
        failures.append("keepsLocalPin() must depend on unlimitedPinnedDialogs")

    storage = STORAGE.read_text(encoding="utf-8", errors="ignore")
    if not re.search(r"unlimitedPinnedDialogs\.Bool\(\)[\s\S]{0,400}SELECT pinned FROM dialogs", storage):
        failures.append("MessagesStorage must keep the stored local pin when saving server dialogs")

    # A long absence triggers a dialogs reset that deleted local-only pins and hid old pinned chats.
    if "keepLocalPins" not in storage or "serverDialogIds" not in storage:
        failures.append("MessagesStorage.resetDialogs must keep local-only pins")
    if "keepLocalPins && oldDialog.pinned" not in text:
        failures.append("completeDialogsReset must keep local-only pins in memory")
    if "d.pinned && NekoConfig.unlimitedPinnedDialogs.Bool()" not in text:
        failures.append("sortDialogs must not drop pinned dialogs by date with unlimited pins")

    if "localPinsInResponse" not in storage or "localPins.get(key)" not in text:
        failures.append("a local pin on a chat the server returned unpinned must be put back after a reset")

    if failures:
        for failure in failures:
            print(f"FAIL: {failure}")
        return 1
    print("PASS: pin order and pin toggles stay local with unlimited pinned dialogs")
    return 0


if __name__ == "__main__":
    sys.exit(main())
