# Overnight visual audit — ExFullGram (branch `overnight/visual-fixes`)

Scope: Liquid Glass / iOS-style UI, audited as a renderer from the source (no device, no screen).
Rules followed: nothing pushed to `main`, no release build, no signing/GitHub release, no native/JNI, no data/migrations,
no resource renames, no trailers on commits. Liquid Glass OFF-state code paths were left untouched.

Commits on this branch are authored by the cloud environment's git identity (not by the owner); re-author them if you
want them to look like your own.

## 1. Fixes

| # | Fix | File:line | Why it was wrong | Commit |
|---|-----|-----------|------------------|--------|
| 1 | Player lens plates no longer get a second rim | `app/exteraless/player/PlayerGlass.java` (`attach`), `PlayerSheet.java` (`glassPlate`) | The like/collapse/more circles and the action capsule use `setHeaderGlassLens(true)`; its AGSL shader already draws Fresnel catchlights at top-left and bottom-right (and `BlurredBackgroundDrawable.draw` skips the vector rim for header lenses). `attach()` layered a fill-less `GlassSurfaceDrawable` rim on top, so each plate showed two different highlights — the exact "два разных блика" case recorded in `memory/glass-rim-uniformity.md`. The lens effect is always on when `PlayerGlass` exists (`GlassEngine.checkGlassSettings` forces `isLiquidGlassEnabled` when `LiquidGlassTuning.isActive()`), so the second rim was never a needed fallback. | `71a306a8` |

## 2. Suspected, not fixed (and why)

_(filled in below as the audit proceeds)_

## 3. Could not verify

- **No build.** `dl.google.com` is not reachable from this environment, so the Android SDK cannot be installed and
  `./gradlew :TMessagesProj:compileReleaseJavaWithJavac` cannot run. Every edited file was parse-checked with
  `javac`'s parser (syntax only, no symbol resolution) and each diff was re-read for imports and removed symbols.
- **All 17 `scripts/check_*.py` guards pass** before each push (they are static string guards, not a compile).
- **No device.** Nothing here was seen on screen. See section 4 for what to look at first.

## 4. Look at first on the phone

_(filled in below)_
