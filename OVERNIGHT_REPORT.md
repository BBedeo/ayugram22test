# Overnight visual audit — ExFullGram (branch `overnight/visual-fixes`)

Scope: Liquid Glass / iOS-style UI, audited as a renderer from the source (no device, no screen).
Rules followed: nothing pushed to `main`, no release build, no signing / GitHub release, no native/JNI, no data or
migrations, no resource renames, no trailers on commits. Liquid Glass OFF-state code paths were left as they were
(one fix, #5, *restores* the OFF state to upstream).

Commits are authored by the cloud environment's git identity, not by the owner; re-author them if you want them to
look like your own.

How the audit was done: I read the draw code of the core (`GlassTokens`, `GlassSurfaceDrawable`, both `GlassRim`s,
`BlurredBackgroundDrawable` draw pipeline, `BlurredBackgroundProviderImpl`, the header/pill/default AGSL shaders,
`DialogMorph`, `GlassWindowBackdrop`, `PlayerGlass`, profile aura, `ProfileButtonsGlass`, search pill, switches/sliders)
and had four read-only sub-audits cover headers/nav, menus/dialogs/toasts, the player and mini player, and
profile/search/chat/providers. **Every finding below was re-checked by me against the code before a fix**; sub-audit claims
I could not confirm, or that would change tuned golden looks, are in section 2 and were not touched.

## 1. Fixes (7 commits, one fix each)

| # | Fix | File:line | Why it was wrong | Commit |
|---|-----|-----------|------------------|--------|
| 1 | Player lens plates no longer get a second rim | `app/exteraless/player/PlayerGlass.java:94` (`attach`), `PlayerSheet.java` (`glassPlate`) | The like / collapse / more circles and the action capsule use `setHeaderGlassLens(true)`, whose AGSL shader already draws Fresnel catchlights at top-left and bottom-right (and `BlurredBackgroundDrawable.draw` skips the vector rim for header lenses). `attach()` layered a fill-less `GlassSurfaceDrawable` rim over it, so each plate showed two different highlights — exactly the "два разных блика" case in `memory/glass-rim-uniformity.md`. The lens effect is always on when `PlayerGlass` exists (`GlassEngine.checkGlassSettings` forces `isLiquidGlassEnabled` when `LiquidGlassTuning.isActive()`), so the extra rim was never a needed fallback. | `71a306a8` |
| 2 | Re-tapping the lyrics button mid-animation no longer snaps the panel | `PlayerSheet.java:1498` (`setLyricsMode`) | `Animator.cancel()` also delivers `onAnimationEnd`; the old end listener ran `setLyricsFraction(oldTarget)` before the new animator read `lyricsFraction` as its start value, so cover/title jumped to fully hidden/shown for a frame, then animated back. `morphAnimator`/`backAnimator` in the same file already call `removeAllListeners()` first. | `6352ab9c` |
| 3 | Re-opening the style editor during its close animation no longer hides the panel | `PlayerSheet.java:1994` (`animateEditor`) | Same cancel pattern: the closing animator's end listener set the fraction to 0 and, because its target was 0, `editorPanel.setVisibility(GONE)` right after `openEditor()` had made it VISIBLE. The new animation then ran against a hidden panel while the player layout scaled. | `7c6bbd3d` |
| 4 | Dialog morph restores the panel's outline clip | `app/exteraless/appearance/glass/DialogMorph.java:201,259` | `AlertDialog` clips its container to the 28dp window corners (`outline provider` + `clipToOutline(true)`, `AlertDialog.java:957-966`). `DialogMorph` saved/restored the outline provider but ended with `setClipToOutline(false)`, so after every morph the clip was gone and button ripples/content could draw square corners past the rounded glass window. | `bab70a53` |
| 5 | Liquid Glass OFF: `sanitizeRimStroke` no longer deletes upstream's white rims | `app/exteraless/appearance/LiquidGlassTuning.java:130` | Every other `LiquidGlassTuning` helper returns its input untouched when the effect is inactive; `sanitizeRimStroke` removed white top/bottom strokes first (dark theme, or any native GPU glass) and only then checked `isActive()`. With the setting OFF the chat composer, top panel, emoji button, hints etc. lost the white rim stock Telegram draws — breaking "OFF is identical to upstream". Behaviour with the setting ON is unchanged. | `4173447b` |
| 6 | iOS player: open/close morph draws the same backdrop as the settled sheet | `PlayerSheet.java:1065` (`drawMorphBackdrop`) | `drawBackdrop()` ignores the stored background mode for the iOS player (always blur/gradient) but `drawMorphBackdrop()` still returned on a stored `BACKGROUND_SOLID` (left over from the Material player). The blurred cover background was missing for the whole ~380 ms morph and then popped in. | `203ed796` |
| 7 | Dark glass surfaces: rim also catches the light at the bottom-right | `GlassTokens.java:130` (`rimOpposite`), `GlassSurfaceDrawable.java:126-139`, `DESIGN_SYSTEM.md` §2 | `GlassSurfaceDrawable` (dialogs, sheets, toasts, hints, dialog morph) was lit top-left and faded to ~3% white; the AGSL lens and the profile's `GlassRim` are lit at both diagonal ends (`memory/glass-rim-uniformity.md` points at this "older one-sided rim"). Dark surfaces now get an opposite catchlight (~0.9× the top-left, scaled by the Highlight setting). **Light surfaces keep their exact previous gradient** (the dark shade defines the edge there). | `62bc21d6` |

## 2. Suspected, not fixed (and why)

Confirmed in the code but deliberately left (design call, golden asset, or no visible effect today):

- **Default AGSL shader lights top-right, header/pill shaders top-left.** `liquid_glass_shader.agsl:62` uses
  `angle = glass_light_angle` (315° → direction (0.71, −0.71), y-down = top-right); `liquid_glass_header_shader.agsl:149` and the
  pill shader re-base it to top-left. Menus/dialogs/composer glint in the opposite corner to headers. Those menus are the
  owner's "liked" look, so not touched; if you want one light direction, re-base the default shader like the header one.
- **`BlurredBackgroundWithFadeDrawable.setFadeHeight` (`:89-90`) leaves the colour fast path shaderless** (it nulls
  `colorStaticPaint`'s shader but the draw cache only re-attaches on a colour change → solid black strip) and keeps a stale
  `opacity` gradient. Latent: every runtime caller with a colour source (`ChatActivityFadeView.setupColorKey` users) sets the
  height before the first draw. Fix when someone starts resizing those at runtime: set `gradientShader = null` in `setFadeHeight`.
- **`GlassMotion.attachPress`** returns early (gate closed) even on `ACTION_UP/CANCEL`, which would leave a view at 0.97 scale
  if motion is switched off mid-press. Nobody calls it today.
- **Tab pill in light theme** (`ScrollSlidingTextTabStrip.java:731-735`): the lens' vector stroke (`simpleActionBar`: dark crescent
  at top/bottom in light) plus the hand-drawn white `GlassRim`. In dark theme the stroke is `0`, so it is single. Needs a look on
  device (open risk already in your rim memory).
- **iOS veil weaker than Material** (`PlayerSheet.java:2410`: `0x70/0x80` vs `0xa8/0xb4`): over a pure-white cover in dark theme the
  title measures ~2:1 and the artist ~1.6:1. A design call (iOS look), not a bug; raise the veil or add a gradient behind the
  text block if you see unreadable covers. Header label on the `COVER_FULL` top scrim has the same kind of contrast risk.
- **Player: backdrop dropped while the cover is still loading** (`PlayerSheet.updateBackdrop` sets `backdrop = null` when no bitmap
  yet), so the new "melt" cross-fade doesn't run for tracks whose art decodes late; it flashes the gradient then pops to blur.
  Fixing it means keeping the previous track's backdrop for a while — a behaviour choice, so left.
- **Player glass plates have no press feedback** (the lens has no pressed state; the selector ripple set in `applyColors` is
  replaced by the plate). Header buttons elsewhere have the elastic press. Feature gap, not a rendering bug.
- **Hint arrow thinned ×0.6 over the lens** (`HintView.java:155`): a sub-audit argues the premise ("lens body is lighter") is wrong
  because the rim layer carries the tier-3 fill. You tuned it on device in `15c11acb`, so left.
- **Touch targets / labels (not visual, left to avoid layout changes in golden screens):** header back/done buttons are 46dp
  (`SimpleActionBar.java:169`, `SimpleActionBarDoneButton.java:204`; menu items are 48dp) and the press glow starts 4dp outside the
  hit area (`SimpleActionBar.java:939`); `FragmentSearchField` close icon is 24dp with no `contentDescription`
  (`:158-179`); `UndoView` undo button ~25dp; player mini 44dp, `PlayerBarView` 40dp, island buttons 44dp, queue drag handle 22dp;
  `HintView` close icon has no content description.
- **Smaller:** light-theme elastic capsule rest fill is ~29% white vs ~8% in dark (`MainTabsActivity.java:623-627`, golden pill);
  `GlassTabView` counter badge clipped by its `saveLayer` width on 5–6 tabs (`:234`); `MainTabsLayout` glass `Drawable.Callback`
  never verified (`:133`); `PlayerGlass` registers its pre-draw listener twice and `release()` has no caller, `PlayerSheet.detach`
  does not cancel `lyricsAnimator`/`backdropFader`; photo viewer send button draws an opaque fill over its lens
  (`ChatActivityEnterView.java:17230-17238`, may be intended); `BottomSheet` zeroes `shadowDrawable` when the lens arrives
  (`:1881`, unverified that the 9-patch has an outer shadow); `messageMenuBackground` non-glass branch ignores its provider
  (`BlurredBackgroundProviderImpl.java:269`); iOS switch off-track `#E9E9EA` on white is ~1.2:1 (iOS-faithful); mini player uses a
  6dp system elevation shadow while the nav pill uses a light vector shadow; profile aura swaps colour with no cross-fade
  (`ProfileActivity.java:11462`); menu radii differ by path (12 / 20 / 22dp: `GlassMenuHelper.java:108`, `ItemOptions.java:1729`,
  `GlassTokens.RADIUS_MENU`).

Checked and found correct (so you don't have to wonder): header/pill AGSL shaders (symmetric key/catch arcs, single-sign
meniscus), `BaseFragment.isActionBarCrossfadeEnabled` / `ActionBarLayout.isActionBarInCrossfade` invariants, compact title-pill
centring via `getFullContentWidth()`, `BlurredBackgroundProviderImpl` dark/light branches, `ProfileButtonsGlass`, profile aura
caching, iOS switch/slider geometry, search pill press handling, BottomSheet lens/rim radius (18, as the design doc says).

## 3. Could not verify

- **No build.** `dl.google.com` is not reachable from this environment, so the Android SDK cannot be installed and
  `./gradlew :TMessagesProj:compileReleaseJavaWithJavac` cannot run. Every edited file was parse-checked with `javac`'s parser
  (syntax only, no symbol resolution) and every diff was re-read for imports and removed symbols. Please run the compile once
  before you trust the branch.
- **All 17 `scripts/check_*.py` guards pass** before each push (they are static string guards, not a compile).
- **No device.** Nothing here was seen on screen.

## 4. Look at first on the phone

1. **iOS player** (like / collapse / more circles and the action capsule): one rim now, not two. Also tap the lyrics button twice
   quickly and open/close the style editor quickly — no jump, panel stays visible.
2. **A confirmation dialog opened from a profile round button**: after the morph, press a bottom button near a rounded corner —
   the ripple must stay inside the corner.
3. **Dark theme dialogs / sheets / toasts**: the new bottom-right catchlight. If it is too strong or too faint tune the `0x2A` in
   `GlassTokens.rimOpposite`; delete the method's use in `GlassSurfaceDrawable.updateShaders` to go back to one-sided.
4. **Liquid Glass OFF, Android 13+, dark theme**: chat composer, top panel and emoji button have the stock white rim again.
5. **iOS player with a Material "Solid" background stored**: open/close morph keeps the blurred cover behind it.
