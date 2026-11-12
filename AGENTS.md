# ExFullGram development rules

## Test Mode parity is a release requirement

Test Mode is a **local transport and storage backend**, not a separate simplified
Telegram interface. Every new ExFullGram UI feature, screen, menu, visual change,
animation, and interaction must work in Test Mode as well as in a live account.

- Reuse the **same** Telegram `ChatActivity`, composer, message cells, animations,
  themed dialogs, and UI components in both account types. Prefer Telegram's
  native `Theme` and components; use Material 3 Expressive patterns only for new
  UI that Telegram does not provide. Support light and dark themes.
- Do not bypass the native send animation path with instant hand-built UI bubbles.
  ExFullGram's local outgoing `MessageObject` must carry the composer-provided
  `SendAnimationData` and `wasJustSent` fields. Incoming messages must use
  standard Telegram controller notifications.
- Keep both account types on a shared send-feature pipeline:
  `SendMessagesHelper.applySharedOutgoingFeatures()`. Add new outgoing
  feature transformations there (or to an equally shared helper), **before**
  the Test transport switch. Respect installed message hooks.
- Test Mode must not send requests to Telegram's servers. Implement offline
  behavior in `app.exteraless.demo.DemoBackend` when a new feature relies
  on server state. Preserve native message types, formatting, reactions,
  reply/edit/delete events, and UI notifications wherever possible.
- Do not reset or reseed user-created Test chats merely to apply a UI update.
  Only migrate Test data when needed and protect user-created content.
- Do not fork an entire feature into Test-only view code. Changes to common UI
  should appear in Test Mode automatically; add narrowly scoped backend
  adaptations only for server-dependent behavior.
- A new feature is not complete until it has been checked in Test Mode.
  If the real Telegram backend is required and cannot be emulated, document
  the Test Mode limitation explicitly instead of silently hiding the feature.

## Regression checks

Run `python3 scripts/check_test_mode_parity.py` after touching the composer,
`SendMessagesHelper`, `DemoBackend`, message presentation, or animations.
The pull-request workflow also runs this lightweight static guard.

Then check on an Android device, for both a normal chat and Test chat:

1. Text: smooth composer-to-bubble transition; scroll and send button behavior.
2. Emoji, rich formatting, reply and edit: native animations and content.
3. Photos, documents, voice notes, stickers: native media cells and transitions.
4. Reactions, deletion and typing: native UI updates and animations.
5. Chat navigation, account switching, menus and theme changes.
6. Reopen the chat: every Test message and dialog persists locally.
7. No Telegram network traffic or stalled loading in local Test accounts.

Static checks alone do not prove visual parity or successful Android compilation.
