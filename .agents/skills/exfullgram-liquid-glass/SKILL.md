---
name: exfullgram-liquid-glass
description: >-
  Authoritative iOS 26 Liquid Glass design recipes, AGSL shader parameters,
  floating header pills, morphing menu overlays, elastic navigation pill physics,
  and ExFullGram screen integration rules. Use this skill BEFORE modifying or
  adding any Liquid Glass surface, floating ActionBar header, ActionBar menu,
  settings header, bottom navigation pill, or transition animation in ayugram22test.
---

# ExFullGram Liquid Glass First-Try Implementation Guide

Use these verified, user-approved recipes on the **first try** whenever implementing or extending Liquid Glass UI across `ayugram22test`. Do NOT reinvent shaders, alpha values, popup menus, or animations from scratch.

## User Feedback & Baseline Rule
- If the user skips commenting on a screen and immediately sends screenshots of the next screen to update, treat the skipped screen as **approved golden behavior** and replicate its exact material, shader, geometry, and motion elsewhere.
- **Verified golden baselines in git history:**
  1. **Bottom navigation elastic pill** (`57f7111be`): single-sign inset `circleMap` meniscus, jitter-free symmetric `bridgeStretch`, opposite-side chromatic caustic.
  2. **Main screen (`DialogsActivity`) crystal header + morphing menu** (`ce4ac23ed`): crystal `liquid_glass_header_shader.agsl`, transparent `dialogsHeaderGlass` (`0.24f` dark / `0.30f` light), tactile press bounce, and `SimpleActionBarMenuOverlay` / `ItemOptions.openInOverlay` morphing from the menu button pill.
  3. **Settings & plugin screens (`BaseFragmentSimpleGlass` + `ActionBar`)** (`4560e46bd`, plus centering/crossfade fixes in `6edb52231`).

## Non-Negotiable First-Try Checklist

1. **Never ship milky/matte white glass.**
   - Use `BlurredBackgroundProviderImpl.dialogsHeaderGlass` or `simpleActionBar` — NEVER high-alpha `solveSrcColor` matte fills on active API 33+ crystal surfaces.
   - Keep active crystal tint low (`isDark ? 0.24f : 0.30f` clamped `[16..105]` dark / `[24..138]` light) so scrolled content visibly refracts through the capsule.
2. **Remove dark-mode vector rim outlines on crystal headers.**
   - In `isHeaderLiquidShaderActive(r)` dark mode, return `0` for `strokeColorTop` and `strokeColorBottom`; let the AGSL Fresnel catchlights form the rim cleanly without a static chalky outline.
3. **Select the right AGSL lens shader.**
   - Bottom nav pill -> `elasticPillLens = true` (`R.raw.liquid_glass_pill_shader` + `R.raw.liquid_glass_pill_content_shader`).
   - Floating header pills / ActionBar / settings headers -> `setHeaderGlassLens(true)` (`R.raw.liquid_glass_header_shader`).
   - Always keep standard/fallback surfaces (`LiteMode`, API < 33, power saver) safe; note HONOR/Huawei OEM power-saver guard in `LiteMode`.
4. **Never fight UV signs inside AGSL.**
   - Use single-sign `circleMap(rimT)` shifted inset (`~1.45dp–1.65dp` from edge) multiplied by `insetFade`. Opposing displacement signs create Jacobian UV folds that pinch glyphs.
5. **Menus MUST morph out of the header button pill.**
   - Wire `ActionBar` / `SimpleActionBar` with `setMenuGlass(menuFactory, menuGlassSource)`, `setMenuGlassColorProvider(BlurredBackgroundProviderImpl.dialogsMenuGlass(r))`, `setAllowMenuOverlayFallback(true)`, and `setMenuOverlayProvider(this::getSimpleMenuOverlay)`.
   - Open `ItemOptions` with `io.openInOverlay(...)` and expand `DialogsActivity`-style blur capture bounds (`dp(640)`) while `actionBar.getOpenMenuOverlay() != null` (or let `BaseFragmentSimpleGlass` manage captures).
6. **Add tactile optical press & synchronized motion.**
   - Header glass buttons and `FragmentSearchField` should drive `setElasticPressProgress(...)` via `BoolAnimator` (`EASE_OUT_QUINT`, `220ms` on header pills / `240ms` on search field) gated by `LiquidGlassTuning.motionEnabled()`.
7. **Always use `getFullContentWidth()` for compact pill centering.**
   - `SimpleTextView` defaults to `widthWrapContent == false`, so `getMeasuredWidth()` equals `availableWidth`, NOT text width! Use `getFullContentWidth()` when computing `compactCenterX` in `ActionBar.onLayout`.
8. **Disable legacy `ActionBar` transition crossfade when `isGlassMode()` is active.**
   - Both `BaseFragment.isActionBarCrossfadeEnabled()` and `ActionBarLayout.isActionBarInCrossfade()` must return `false` when an `ActionBar` is in `glassMode` or detached (`!shouldAddToContainer()`). Otherwise sliding transitions render empty glass pills inside the moving card while icons detach to an unscaled overlay!
9. **Configure RecyclerViews properly under floating headers.**
   - Call `listView.setSimpleGlassMode(true)` on `BlurredRecyclerView` so `-blurTopPadding` negative top margin does not break edge-to-edge scrolling/shadows.
   - In settings screens extending `BaseNekoSettingsActivity` / `BasePreferencesActivity` / plugin activities: disable legacy `actionBar.setDrawBlurBackground(...)`, `setupAdaptiveBackground()`, and `actionBar.setAdaptiveBackground(...)`; implement `getListViewForSimpleGlass()` and edge-to-edge where required.
10. **Respect workspace rules & verify.**
    - Work only in `C:\Users\Bb\Desktop\ayugram22test` (never `StudioProjects`), maintain Test Mode parity (`AGENTS.md`), and run all Python guards (`scripts/check_*.py`) after edits.

## Detailed Reference Docs
- Shader math, optical constants, crystal alpha palettes, and elastic nav physics: [references/agsl-crystal-optics.md](./references/agsl-crystal-optics.md)
- Floating header pills, `BaseFragmentSimpleGlass`, `ActionBar` centering/tactile press, morphing menus, crossfade & RecyclerView fixes: [references/header-menus-and-screens.md](./references/header-menus-and-screens.md)
