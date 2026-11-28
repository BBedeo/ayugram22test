#!/usr/bin/env python3
"""Guards the message menu that shrinks into the delete question: where it may ask and when the dialog must stay."""
import pathlib
import sys

JAVA = pathlib.Path(__file__).resolve().parents[1] / "TMessagesProj/src/main/java"
errors = []


def need(name, *needles):
    path = JAVA / name
    if not path.exists():
        errors.append(f"{path.name}: file is missing")
        return ""
    text = path.read_text(encoding="utf-8")
    errors.extend(f"{path.name}: missing {needle!r}" for needle in needles if needle not in text)
    return text


card = need("app/exteraless/appearance/glass/MenuDeleteCard.java",
            "popup.addBottomCard(", "popup.setTopCut(", "RenderEffect.createBlurEffect(",
            "popup.setDragSelectEnabled(false)", "popup.removeView(")
# the card appears under the finger that pressed the bin: no button answers while it moves or after the answer
if card.count("if (!moving && !answered)") != 2:
    errors.append("MenuDeleteCard: both buttons must ignore taps while the card moves and after an answer")

# a tap on a menu that is still moving closes nothing, and one menu holds one card
for needle in ("if (!moving) {\n                onOutside.run();", "if (popup.hasBottomCard()) {"):
    if needle not in card:
        errors.append(f"MenuDeleteCard: missing {needle!r}")
# the question may be asked while the menu is still entering: what surrounds it comes back opaque, never half faded
if "view.setAlpha(menuAlpha);" not in card:
    errors.append("MenuDeleteCard: the views around the menu must return to full opacity")

popup = need("org/telegram/ui/ActionBar/ActionBarPopupWindow.java", "public int addBottomCard(", "public void setTopCut(",
     "public boolean hasBottomCard()",
     "gapStartY != -1000000) {\n                return -1;", "AndroidUtilities.rectTmp2.top += (int) topCut;")
# the items are cut off in both ways of drawing them, the entering one included
cut = popup.find("canvas.clipRect(0, shownTop() + (int) topCut")
if not 0 <= cut < popup.find("drawDragHoverHighlight(canvas);\n                super.dispatchDraw(canvas);\n                canvas.restore();", max(cut, 0)):
    errors.append("ActionBarPopupWindow: the cut must be clipped before the entering menu is drawn")

chat = need("org/telegram/ui/ChatActivity.java", "public boolean askDeleteInMenu(",
            "message != selectedObject", "menuAsksDelete = false;", "if (menuAsksDelete) {\n                    return;")
start = chat.find("public boolean askDeleteInMenu(")
if "LiquidGlassTuning.motionEnabled()" not in chat[start:start + 900]:
    errors.append("ChatActivity: the menu may ask only with glass animations on")

alerts = need("org/telegram/ui/Components/AlertsCreator.java",
              "!partialDelete && fragment instanceof ChatActivity", ".askDeleteInMenu(")
# a supergroup deletes for everyone and only the question says so: the card repeats it above its buttons
for needle in ("if (question != R.string.AreYouSureDeleteSingleMessage) {\n                    forEveryone = LocaleController.getString(question);",
               ".askDeleteInMenu(selectedMessage, forEveryone, ayuFrameLayout"):
    if needle not in alerts:
        errors.append(f"AlertsCreator: missing {needle!r}")
if not 0 <= card.find("panel.addView(warning(") < card.find("panel.addView(checks,"):
    errors.append("MenuDeleteCard: the warning must stand above the checks and the buttons")
# both texts that say only part of the messages will go keep the dialog
if alerts.count("partialDelete = true;") != 2:
    errors.append("AlertsCreator: both partial-delete texts must keep the dialog")
ask = alerts.find(".askDeleteInMenu(")
if ask >= 0:
    for earlier in ("DeleteMessagesBottomSheet(", "SuggestionStarsWillBeLostDelete", "SuggestionTONWillBeLost",
                    "BoostingGiveawayDeleteMsgText"):
        if not 0 <= alerts.rfind(earlier, 0, ask):
            errors.append(f"AlertsCreator: {earlier} must be decided before the menu is asked")

if errors:
    sys.exit("\n".join(errors))
print("Menu delete card: OK")
