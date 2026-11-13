# Floating Headers, Morphing Menus, Layout & Transitions (Verified Recipes)

## 1. Main Screen Floating Header (`DialogsActivity`)
Verified in `ce4ac23ed` (`TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java`):
- ActionBar glass setup (`shouldUseDialogsHeaderGlass()`):
  - `actionBar.setAddToContainer(false);`
  - `actionBar.setCastShadows(false);`
  - `actionBar.setupGlass(iBlur3FactoryLiquidGlass, BlurredBackgroundProviderImpl.dialogsHeaderGlass(resourceProvider), false);`
  - `actionBar.setHeaderGlassLens(true);`
  - `actionBar.setCompactGlassTitlePill(true);`
  - `actionBar.setClipContent(false); actionBar.setClipToPadding(false);`
- Menu glass crossfade (`iBlur3SourceGlass` -> `iBlur3SourceGlassFrosted`):
  - Wrap with `new GlassSourceCrossfade(...)` and `BlurredBackgroundDrawableViewFactory` (`setLiquidGlassEffectAllowed(true)`, `setOutset(dp(16))`, `setGlassEngine(glassEngine)`).
  - `actionBar.setMenuGlass(menuFactory, menuSource);`
  - `actionBar.setMenuGlassColorProvider(BlurredBackgroundProviderImpl.dialogsMenuGlass(resourceProvider));`
  - `actionBar.setMenuRise(dp(8));`
- Morphing menu options (`showDialogsOptionsMenu(ItemOptions io)`):
  - Prefer `io.openInOverlay(actionBar, optionsItem, getSimpleMenuOverlay(), () -> invalidateBlurCaptures(GlassEngine.FLAG_INVALIDATED_POSITIONS))` and invalidate blur captures immediately.
  - Also close `actionBar.closeMenuOverlay(...)` in `onPause()`, `onBackPressed(boolean)`, `onBecomeFullyHidden()`, and `showSearch(true, ...)`.
  - Expand `iBlur3PositionActionBar` capture bottom to `Math.max(captureBottom, Math.min(Math.max(0, fragmentView.getMeasuredHeight() - additionalList), dp(640)))` whenever `actionBar.getOpenMenuOverlay() != null`.
- Search bar motion & tactile press:
  - Search open/close animation in header glass mode: `duration = show ? 320 : 280`, interpolator `CubicBezierInterpolator.EASE_OUT_QUINT`, zero start delay (`!shouldUseDialogsHeaderGlass()`).
  - `FragmentSearchField`: `ANIMATOR_ID_GLASS_PRESSED` (`BoolAnimator`, `EASE_OUT_QUINT`, `240ms`) drives `blurredBackgroundDrawable.setElasticPressProgress(factor * 0.72f)` on `ACTION_DOWN` / `UP` / `CANCEL`.

## 2. Secondary & Settings Screens (`BaseFragmentSimpleGlass` + `BaseFragment`)
Verified in `4560e46bd`:
- In `BaseFragmentSimpleGlass.setupHeader(contentView, actionBar, resourceProvider)`:
  - `actionBar.setAddToContainer(false);`
  - `actionBar.setCenterTitleAndGlass(true);`
  - `actionBar.setExtraHeight(dp(6));`
  - `actionBar.doNotDrawCenterGlass = false;`
  - `actionBar.setHeaderGlassLens(true);`
  - `actionBar.setCompactGlassTitlePill(true);`
  - `actionBar.setAllowMenuOverlayFallback(true);`
  - `actionBar.setMenuGlass(menuFactory, menuGlassSource);`
  - `actionBar.setMenuGlassColorProvider(BlurredBackgroundProviderImpl.dialogsMenuGlass(resourceProvider));`
  - `actionBar.setMenuRise(dp(1.5f));`
  - `applyAdaptiveHeaderColors(actionBar, resourceProvider);` (uses `key_windowBackgroundWhiteBlackText`, `key_windowBackgroundWhiteGrayText`, `key_windowBackgroundWhiteHintText`, `key_listSelector`, and updates search items via `ActionBarMenuItem.setSearchFieldColors`).
  - `actionBar.setupGlass(factory, BlurredBackgroundProviderImpl.simpleActionBar(resourceProvider));`
- Tactile press on `ActionBar` glass pills (`ActionBar.java`):
  - `animatorBackGlassPressed` / `animatorMenuGlassPressed` (`BoolAnimator`, `EASE_OUT_QUINT`, `220ms`) drive `setElasticPressProgress(val * 0.85f)` when `headerGlassLens && LiquidGlassTuning.motionEnabled()`.
- Morphing menu spring physics (`SimpleActionBarMenuOverlay.java`) & hold/pull-down drag selection:
  - Open width spring: `springStep(t, 0.78, 0.30)` (~2% overshoot); height spring: `springStep(t - 0.02, 0.80, 0.34)` (~1.5% overshoot).
  - Close pours smoothly back into button (`0.17s` / `0.20s`) followed by button squash wobble (`~0.35s` settle).
  - Holding (`200ms`) or pulling downward on any `ActionBarMenuItem` with submenus (`addSubItem`/`lazilyAddSubItem` or `setDragOpenMenu(true)` such as `DialogsActivity.optionsItem` and `SharedMediaLayout.simpleOptionsItem`) opens the morphing overlay or popup immediately and tracks the pointer across items (`ActionBarPopupWindowLayout.setDragHoverView`, `PopupSwipeBackLayout.getVisiblePageView`, `ItemOptions` hover sync) with an animated gliding Liquid-Glass hover pill and `HapticFeedbackConstants.CLOCK_TICK` haptics; releasing activates the hovered item.
- Wide-pill & Profile header glints (`liquid_glass_header_shader.agsl`, `SimpleActionBar.java`):
  - `liquid_glass_header_shader.agsl` computes `pillWeight` from `(size.x - size.y) / size.y`, softening horizontal falloff and adding off-diagonal `perpDir` specular catchlights (`offDiagTopRight` / `offDiagBotLeft`) on wide right-side capsules (`[🔍 ☁️ 🔄]`, `[🔍 ⋮]`) without altering standalone circular buttons (`pillWeight == 0`).
  - `SimpleActionBar.createButtonsGlass()` and `createMenuGlass()` always pass `.setHeaderGlassLens(true)` even when a custom `buttonsGlassFactory` (e.g. `ProfileButtonsGlass`) is attached.
- `BaseFragment`:
  - Exposes `getListViewForSimpleGlassTopPadding()` / `getListViewForSimpleGlassBottomPadding()` (e.g., `FeedCategoryEditActivity` keeps `dp(72)` bottom clearance for floating button; `OpenExteraSettingsActivity` returns `0` top padding and suppresses the floating title pill via `actionBar.setTitle(null)` + `actionBar.setDrawGlassTitle(false)`).
  - `BaseNekoSettingsActivity` (`useSimpleHeaderGlass()`): creates `SizeNotifierFrameLayout` instead of `BlurContentView`, skips `actionBar.setDrawBlurBackground`, skips `setupAdaptiveBackground()`, calls `listView.setSimpleGlassMode(true)`, and computes `isLightStatusBar()` from `key_windowBackgroundWhite`.
  - Any converted activity using `listViewForSimpleGlass` must remove legacy `actionBar.setAdaptiveBackground(...)`, override `getListViewForSimpleGlass()`, enable `isSupportEdgeToEdge() -> true` if needed, and keep `BlurredRecyclerView.setSimpleGlassMode(true)` so `-blurTopPadding` negative margin does not apply.

## 3. Critical Layout & Transition Traps (Fixed in `6edb52231`)
1. **Compact Glass Title Pill Centering (`ActionBar.java`):**
   - `SimpleTextView` defaults to `widthWrapContent == false`, meaning `titleTextView[i].getMeasuredWidth()` returns the full `availableWidth` rather than the text width!
   - Never use `tv0.getMeasuredWidth()` to compute centered pill coordinates; compute actual width with `tv0.getFullContentWidth()` and `subtitleTextView.getFullContentWidth()`, keep `availableWidth` capped cleanly via `getCompactSafeLeft(boolean)` / `getCompactSafeRightForMenuWidth(int, int, int)`, and apply `rightExtra` shift cleanly without clipping non-outside right drawables.
2. **Disabling Legacy ActionBar Transition Crossfade (`BaseFragment.java` & `ActionBarLayout.java`):**
   - If two screens slide/spring-transition with legacy ActionBar crossfade enabled, `ActionBar.drawChild()` hides icons inside the scaling card (leaving empty glass circles/pills) while `ActionBarLayout.draw()` renders unscaled detached white/dimmed icons over the top fade.
   - `BaseFragment.isActionBarCrossfadeEnabled()` MUST return `false` when `actionBar == null || !actionBar.shouldAddToContainer() || actionBar.isGlassMode()`.
   - `ActionBarLayout.isActionBarInCrossfade()` MUST verify `fgActionBar != null && fgActionBar.shouldAddToContainer() && !fgActionBar.isGlassMode()` and the same for `bgActionBar`.
