# ExFullGram design system: iOS 26 Liquid Glass

This file is the visual standard for the optional **Liquid Glass** design mode
(Appearance → Liquid Glass, and the "Max iOS · Liquid Glass" / "Balanced iOS ·
Liquid Glass" presets). Everything below applies only while
`LiquidGlassTuning.isActive()` is true. With the setting off the app must look
exactly like upstream (see `AGENTS.md`).

Code entry points:

| What | Where |
|---|---|
| Gate, tuning sliders, tint alphas | `app/exteraless/appearance/LiquidGlassTuning.java` |
| Tokens: tiers, radii, rim colors, press scale | `app/exteraless/appearance/glass/GlassTokens.java` |
| Glass surface without a backdrop capture | `app/exteraless/appearance/glass/GlassSurfaceDrawable.java` |
| Spring curves, tactile press | `app/exteraless/appearance/glass/GlassMotion.java` |
| Glass with a real backdrop (AGSL lens) | `BlurredBackgroundDrawable` + `BlurredBackgroundProviderImpl.*` via the fragment's `GlassEngine` factories |
| Shader and header/menu recipes | `.agents/skills/exfullgram-liquid-glass/` (local) |

## 1. Optical depth, not a milky fill

Liquid Glass bends and intensifies what is under it; it is not a grey frosted card.

- **With a backdrop capture** (headers, tab bar, menus, search, attach panel), use
  the AGSL lens (`setHeaderGlassLens(true)` / `elasticPillLens`). The capture is
  already pre-saturated 3× (`SaturationX3RenderEffect`); never add more saturation
  in the shader. Crystal tints stay low: `tintAlphaHeader` (≈0.06–0.26),
  `tintAlphaMenu` (≈0.16–0.38).
- **Without a capture** (toasts, hints, dialogs in their own window, sheet rims),
  use `GlassSurfaceDrawable`. Its fill is the element's *own* color made
  translucent, so the text colors chosen for that element keep their contrast. A
  dialog window with native background blur passes its tuned `blurAlpha` through
  `setFillAlpha(...)` so the real blur shows.
- Never replace glass with a dark overlay, and never sample text without a valid
  backdrop.

## 2. Specular rim and polished bevel

Every glass surface has:

- a **1px rim** (`0.9dp`): a linear gradient from the top-left (light) to the
  bottom-right (shade). Light from `GlassTokens.rimLight`, shade from `rimShade`;
- an **inner highlight** just under the top edge (`innerHighlight`), fading out
  over 28dp: the polished bevel.

In dark themes the catchlight is faint (`0x2E` white at most). A bright white
rim reads as a chalky outline (see the skill's "no dark-mode vector rim" rule).
AGSL surfaces get their rim from the shader's Fresnel term instead.

## 3. Three density tiers

| Tier | Token | Used for | Fill opacity without capture (dark / light) |
|---|---|---|---|
| 1 Thin | `TIER_THIN` | chips, pills, secondary buttons, inputs | 0.62 / 0.66 (floor 0.50) |
| 2 Base | `TIER_BASE` | content cards, widgets, list sections | 0.76 / 0.80 (floor 0.66) |
| 3 Thick | `TIER_THICK` | floating bars, dialogs, menus, toasts, hints | 0.86 / 0.88 (floor 0.80) |

The Transparency slider shifts the fill by at most ±6% and never below the floor.
The floor keeps text readable over pure white, pure black and busy photos.

## 4. Concentric geometry

`R_outer = R_inner + padding`. Use `GlassTokens.concentric(inner, padding)` and
`innerRadius(outer, padding)` instead of guessing.

| Element | Radius (dp) |
|---|---|
| Chips, tabs, navigation, primary buttons | capsule (`RADIUS_CAPSULE`, half the height) |
| Hints / tooltips | 14 |
| Cards, list sections, menus, toasts | 22 |
| Dialogs | 28 |
| Sheets (top corners) | 30 for new sheets; 18 is kept for the existing sheet asset |

## 5. Spring physics

No `linear` or plain `ease` curves on glass:

- `GlassMotion.SPRING`: damped spring with about 3% overshoot. Use it for
  indicators, toasts, checks and toggles (380–450ms).
- `GlassMotion.SPRING_SOFT`: about 1.5% overshoot. Use it for sheets and
  dialogs (420–520ms).
- **Press:** `GlassMotion.attachPress(view)` sinks the view to 0.97 on a stiff
  spring and releases it on a bouncy one. Header pills keep their optical
  `setElasticPressProgress` press (BoolAnimator, `EASE_OUT_QUINT`, 220–240ms).
- **Tabs:** the active glass indicator flows between tabs (bottom navigation
  elastic pill).
- **Gating:** all motion follows `LiquidGlassTuning.motionEnabled()` (the
  "Motion" switch and Android 13+).

## 6. Typography and icons

- Large titles use tight tracking (Telegram's bold title face, letter spacing
  about −0.02em). Body text stays at the theme sizes.
- Every number that changes in place (counters, badges, timers, progress
  percent) uses tabular figures (`fontFeatureSettings = "tnum"`).
- Icons come from the Solar set already used in menus. Keep one stroke weight per
  screen and don't mix filled and outline glyphs in the same row.

## 7. Performance and readability rules

- Never nest more than two surfaces with an active backdrop blur. Overlays that
  float over glass (toasts, hints) use `GlassSurfaceDrawable`, which does not
  blur at all.
- Power saver, API < 31 and LiteMode fall back to the classic drawing
  automatically through `LiquidGlassTuning.isActive()`.
- Check every new glass element over a white page, a black page and a busy photo
  wallpaper in both themes before calling it done. Visual QA happens on the
  device and is done by the owner.

## 8. Status of the rollout

| Area | Glass treatment |
|---|---|
| Main chat list header, search, morphing menu, bottom navigation pill | AGSL lens (golden commits `ce4ac23ed`, `57f7111be`) |
| Settings and most secondary screens | floating glass header (`getListViewForSimpleGlass`) |
| Chat header, composer, attach panel tabs | AGSL lens |
| Popup and context menus (ItemOptions) | AGSL lens, gapless |
| Toasts (Bulletin, UndoView) | `GlassSurfaceDrawable`, tier 3 |
| Tooltips (HintView) | `GlassSurfaceDrawable`, tier 3; HintView2 already uses the lens |
| Alert dialogs (message type) | native window blur + glass rim, 28dp |
| Bottom sheets | translucent material + glass rim and bevel highlight |
| Switches, sliders | iOS style through the glass presets (`IosSwitchHelper`, `SeekBarView` iOS) |
