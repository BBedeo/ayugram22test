#!/usr/bin/env python3
"""Static safety checks for ExFullGram Test Mode UI/transport parity.

This is deliberately a small regression guard, NOT an Android animation test.
The real UI must still be exercised on an Android device after changes.
"""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "TMessagesProj/src/main/java"
send = (BASE / "org/telegram/messenger/SendMessagesHelper.java").read_text(encoding="utf-8")
backend = (BASE / "app/exteraless/demo/DemoBackend.java").read_text(encoding="utf-8")
composer = (BASE / "org/telegram/ui/Components/ChatActivityEnterView.java").read_text(encoding="utf-8")

checks = {
    "Both live and Test sends use shared ExFullGram transforms":
        send.count("applySharedOutgoingFeatures(sendMessageParams);") == 2,
    "Test send intercept precedes network transport":
        "DemoBackend.isTestAccount(currentAccount)" in send
        and send.index("DemoBackend.sendLocalMessage(currentAccount, sendMessageParams)")
            < send.index("BotAccountRouter.guardSend(currentAccount"),
    "Text composer passes native animation metadata":
        "params.sendAnimationData = sendAnimationData" in composer
        or "scheduleRepeatPeriod, sendAnimationData, updateStickersOrder" in composer,
    "Local send carries native animation origin":
        "object.sendAnimationData = sendParams.sendAnimationData;" in backend,
    "Local outgoing MessageObject is treated as just sent":
        "object.wasJustSent = true;" in backend,
    "Local dispatch uses Telegram message event pipeline":
        "updateInterfaceWithMessages(" in backend
        and "NotificationCenter.dialogsNeedReload" in backend,
    "Local send does not reload all dialog objects on each message":
        "registerLocalDialog(account, dialogId, message," not in backend,
    "Test send preserves media message types":
        "private static void applySendParameters(" in backend
        and "TLRPC.TL_messageMediaDocument" in backend
        and "TLRPC.TL_messageMediaPhoto" in backend,
    "Test local edit and reaction paths still notify UI":
        "NotificationCenter.replaceMessagesObjects" in backend,
}

failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {name}")
if failed:
    raise SystemExit(f"{len(failed)} Test Mode parity checks failed")
