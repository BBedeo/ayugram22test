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

## New settings search and account profiles

- Every new ExFullGram settings page must be reachable from the ExFullGram
  settings home, included in `SettingsHelper.onCreateSearchArray()`, and
  provide stable searchable row keys plus localized `getSearchPrefix()`
  resources. Add its deep-link route to `SettingsHelper.processDeepLink()`.
- All settings belonging to the `nkmrcfg` family (NekoConfig, NaConfig and
  ExFullGram's ConfigItem-backed settings) are part of account-scoped mod
  profiles when the user enables **Separate settings for each account**.
- Profiles are keyed by Test/live identity + Telegram user ID, not just account
  slot. Do not share preferences accidentally when a slot is reused.
- Account changes must go through `AccountSettingsProfiles.beforeAccountSwitch`
  before `UserConfig.selectedAccount` changes. Keep
  `AccountSettingsProfiles.initialize` at app startup.
- New settings written to a different SharedPreferences namespace must opt
  into profile support deliberately; never clone auth, passcodes, Telegram
  session/network state or chat databases as if they were cosmetic settings.
- When adding settings to compare/merge, maintain a native themed interface,
  per-setting left/right choice and confirmation before overwriting.
- Account-specific settings should persist after cold starts and account
  switching. Test account reset must not erase its settings profile.
- Some appearance changes require app restart because Telegram caches view
  resources. Do not claim live theme refresh without device verification.


## Optional visual features

Before implementing a substantial new user-facing feature, ask the user whether
they want a configurable setting or always-on native behavior. Liquid Glass is
an optional ExFullGram design setting, default OFF. Its visual appearance must
stay byte-for-byte compatible with upstream when off, and the setting must
work in both live and Test Mode as well as per-account settings profiles.

Use Telegram's existing capture + runtime refraction shader; never replace
Liquid Glass with a dark overlay and never sample text without a valid backdrop.
Provide sensible power-saver, API-level and readability fallbacks.


## Visual-reference fidelity contract (scoped rule)

This section applies **only** when the user supplies a visual reference
(screenshot, video frame, Figma mockup, presentation image, etc.) and asks to
reproduce it closely / identically. It does **not** change the completion
standard for ordinary tasks that have no visual acceptance reference.

- Treat "хочу вот так", "сделай как на скрине", "повтори 1:1", and equivalent
  requests as a visual specification, not loose inspiration, unless the user
  explicitly says an approximation is acceptable.
- The user is not required to provide code, measurements, or a technical
  drawing. Derive measurable constraints yourself: silhouette, relative size,
  spacing, alignment, optical depth, transparency, blur/refraction, highlights,
  animation timing, deformation, and interaction semantics.
- A successful Gradle build, static guard, or plausible-looking code is **not**
  evidence that the visual-reference task is complete.
- For this scoped class of task, use these status labels internally and in
  progress reports when useful:
  1. coded / not visually verified;
  2. builds successfully;
  3. visually compared / discrepancies remain;
  4. visually matched within platform limits.
  Only state 4 may be described as complete for that specific reference task.
- Before saying "готово", "сделано", "1:1", "идеально", or equivalent about a
  reference-matching task, compare a screenshot/video from the latest real
  Android build against the supplied reference. If device evidence is not yet
  available, say "implemented, not visually verified" instead.
- Do not hide known gaps behind implementation language. If geometry, material,
  motion, or interaction still differs from the reference, list the remaining
  discrepancies explicitly and continue fixing them when the task is still in
  scope.
- Preserve the original ExFullGram UI when an optional reference-based visual
  feature is disabled.
- The Figma acceptance board for this workflow is:
  https://www.figma.com/design/WhGugsuIwY8estzyP3Biyq
  Use its Reference vs Implementation comparison and checklist as the review
  structure. The user's supplied visual remains authoritative; external UI kits
  are only supporting references.


## Approved Liquid Glass engineering standards (first-try fidelity)

When implementing, extending, or fixing Liquid Glass surfaces (floating
headers, morphing menus, bottom navigation pills, buttons, dialogs), **read and
follow `.agents/skills/exfullgram-liquid-glass/SKILL.md`** before making code
changes so accepted visuals match on the first attempt:

- **Implicit acceptance tracking:** Verified golden commits in this repository
  (`57f7111be` bottom nav pill, `ce4ac23ed` main screen header & morphing
  menu, plus `4560e46bd` / `6edb52231` settings header glass) define the
  expected look. Whenever the user skips commenting on a delivered screen and
  immediately sends screenshots of another screen to style, lock that delivered
  screen's parameters and architecture in as the standard for subsequent work.
- **Crystal clarity first:** Never ship opaque white or milky matte fills on
  crystal headers (`dialogsHeaderGlass`, `simpleActionBar`); use
  `liquid_glass_header_shader.agsl` via `setHeaderGlassLens(true)` with
  transparent crystal tint (`0.24f` dark / `0.30f` light) and suppress dark-mode
  static vector strokes so Fresnel catchlights draw the rim cleanly.
- **Single-sign AGSL refraction:** Keep `circleMap` UV displacement monotonic
  across the boundary (`~1.45dp–1.65dp` inset) so edge characters/icons never
  pinch or fold.
- **Morphing menus & tactile bounce:** Use `SimpleActionBarMenuOverlay` /
  `ItemOptions.openInOverlay(...)` with `dialogsMenuGlass` instead of static
  popup windows, plus tactile press bounce (`setElasticPressProgress`) gated on
  `LiquidGlassTuning.motionEnabled()`.
- **Centering & transition invariants:** Measure compact glass title pills via
  `SimpleTextView.getFullContentWidth()` (never `getMeasuredWidth()`), keep
  `BaseFragment.isActionBarCrossfadeEnabled()` and
  `ActionBarLayout.isActionBarInCrossfade()` returning `false` when
  `actionBar.isGlassMode()` or `!shouldAddToContainer()`, and enable
  `BlurredRecyclerView.setSimpleGlassMode(true)` under edge-to-edge glass
  headers.
- **Regression suite:** After editing Liquid Glass or header/navigation code,
  run all static checks in `scripts/check_*.py` (all must exit `0`).

