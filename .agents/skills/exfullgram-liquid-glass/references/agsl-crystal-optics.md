# AGSL Crystal Optics & Elastic Navigation Physics (Verified Recipes)

## 1. Crystal Header Material (`BlurredBackgroundProviderImpl`)
Verified in `ce4ac23ed` and `4560e46bd` (`TMessagesProj/src/main/java/org/telegram/ui/Components/blur3/drawable/color/impl/BlurredBackgroundProviderImpl.java`):

- Shader activation gate:
  ```java
  private static boolean isHeaderLiquidShaderActive(Theme.ResourcesProvider r) {
      return Build.VERSION.SDK_INT >= 33
          && checkBlurEnabled(r)
          && (LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) || LiquidGlassTuning.isActive());
  }
  ```
- **Crystal header color (`dialogsHeaderGlass` and `simpleActionBar` when `isHeaderLiquidShaderActive(r)` is true):**
  - `baseAlpha = isDark ? 0.24f : 0.30f`
  - `alpha = LiquidGlassTuning.tintAlpha(isDark, baseAlpha)`
  - `crystalTarget`: dark blends `colorTarget` toward `0xFFDCE8F6` by `0.24f`; light blends `colorTarget` toward `0xFFFFFFFF` by `0.86f`
  - Alpha clamped: `MathUtils.clamp(Math.round(255f * alpha), isDark ? 16 : 24, isDark ? 105 : 138)`
  - Strokes in dark crystal mode: `strokeColorTop = 0`, `strokeColorBottom = 0` (let AGSL Fresnel catchlights draw the rim; light mode uses `0x0D000000` top / `0x16000000` bottom)
  - Shadow: `0x20000000` (light) / `0x26000000` (dark), `dpf2(4.0f)` radius, `0, dpf2(1.1f)` offset, stroke width `dpf2(0.45f)`
  - Fallback blur (without AGSL active): `frostedAlpha = LiquidGlassTuning.tintAlpha(isDark, isDark ? 0.56f : 0.68f)` clamped `[64/90 .. 220]`
- **Morphing menu crystal/frosted surface (`dialogsMenuGlass`):**
  - `baseAlpha = LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) ? 0.72f : 0.80f` (`LiquidGlassTuning.tintAlphaMenu`)
  - Target tint blends `key_actionBarDefaultSubmenuBackground` toward `0xFF1A232E` (`0.25f`, dark) or `0xFFFFFFFF` (`0.65f`, light), alpha clamped `[90..235]`
  - Strokes: `0x16000000 / 0x18FFFFFF` top, `0x20000000 / 0x0EFFFFFF` bottom; shadow `0x2A000000 / 0x38000000`, `dpf2(9.0f)` radius, `dpf2(2.6f)` dy

## 2. `LiquidGlassEffect` & `BlurredBackgroundDrawable` Wiring
- `LiquidGlassEffect(RenderNode node, boolean elasticPillLens, boolean headerGlassLens)`:
  - `elasticPillLens == true` -> `R.raw.liquid_glass_pill_shader`
  - `headerGlassLens == true && !elasticPillLens` -> `R.raw.liquid_glass_header_shader`
  - default -> `R.raw.liquid_glass_shader` (keep untouched for legacy chat/dialog controls)
- In `BlurredBackgroundDrawable`:
  - `thicknessFactor = elasticPillLens ? 0.44f : (headerGlassLens ? 0.40f : 0.20f)`
  - `defaultThicknessDp = LiquidGlassTuning.isActive() ? LiquidGlassTuning.thicknessDp() : (headerGlassLens ? 15 : 11)`

## 3. Header Crystal AGSL Profile (`liquid_glass_header_shader.agsl`)
- Single-sign inset `circleMap` toric meniscus (~`1.45dp` inset, zero UV fold):
  - `uDp = max(minHalf / 23.0, 1.0)`, `dDp = -sd / uDp`
  - `lensH = clamp((6.8 + 2.4 * lift) * uDp, 5.0, minHalf * 0.62)`
  - `rimT = clamp(1.0 + (sd + 1.45 * uDp) / lensH, 0.0, 1.0)`
  - `insetFade = smoothstep(0.22, 1.65, dDp)`
  - `bevel = mix(circleMap(rimT), rimT * rimT, 0.35) * (0.48 + 0.52 * insetFade)`
- Crisp readable center + Snell perimeter refraction (`0.52 * rayDepth * refract_intensity * (0.72 + lift * 0.34)`).
- Chromatic RGB dispersion (`disp = ray.xy * uDp * (1.85 + lift * 2.15) * bevel * insetFade`, `dispMix <= 0.88`).
- Subtle opposite-side/vertical internal reflections (`hReflWeight` ~`0.16 + 0.14 * lift`, `vReflWeight` ~`0.14 + 0.12 * lift`).
- Saturation boost `mix(luma, backdrop.rgb, 1.24 + 0.16 * lift)` + adaptive readability veil (`contrastVeil = smoothstep(0.56, 0.96, bgLumaF) * darkCrystalTint * 0.16 * (1.0 - lift * 0.35)` when `glass_adaptive > 0.5`).
- Diagonal catchlights (`angle = -2.356 + move * 0.42 + (glass_light_angle - 5.4978)`, `rimCore` at `dDp ≈ 1.05dp`).

## 4. Navigation Elastic Pill (`57f7111be`)
### AGSL (`liquid_glass_pill_shader.agsl` & `liquid_glass_pill_content_shader.agsl`)
- **Single-sign `circleMap` meniscus (zero UV fold):**
  - `uDp = max(minHalf / 24.0, 1.0)`
  - Inset `1.45 * uDp` in backdrop shader / `1.65 * uDp` in content shader; `insetFade = smoothstep(0.25, 1.85, dDp)`.
  - Horizontal/vertical tide `rightWave` stays vertical (`float2(0.0, rightWave * 0.24)`) to avoid breaking left/right optics symmetry.
  - Content pass uses `pillAA` (`smoothstep(0.75, -0.75, sdRaw)`) without perimeter alpha holes because unrefracted content uses `clipOutPath`.
- **Subtle prismatic opposite-rim reflection:**
  - `reflectInsetBand = gaussian((dDp - 2.65) / 0.88) * pow(abs(edgeDir.x), 1.75) * smoothstep(0.70, 0.92, abs(relCenter.x)) * insetFade`
  - Masked with `(1.0 - float(refracted.a))` in content shader (`0.13 + 0.15 * lift`, backdrop `0.11 + 0.13 * lift`).
- **Clean rim:** static white vector outline suppressed on active `BlurredBackgroundDrawable` (`drawRim`), leaving pure Fresnel rim (`rimCore` around `1.25–1.30dp`) and right-side liquid tide (`rightFlowArc`).

### Stretch & Anti-Jitter Physics (`MainTabsLayout.java`)
- `bridgeStretch = elasticDragging ? interTabArch * dp(10.0f) : 0f` split **50% left / 50% right (`symStretch * .5f`)** so stationary micro-tremor never flips pill side.
- Directional velocity deadzone `absDelta = Math.max(0f, Math.abs(elasticSmoothedDelta) - dp(0.45f))` + smoothed `elasticSmoothedDir` (`0.20f`) applied only to `dirStretch` / `leadingPullSquish`.
- Vertical thumb pull uses `3.0dp` deadzone (`deadzonedPullY`) + low-pass `elasticSmoothedPullY`.
- Clamp edge stretch against `leftHeadroom` / `rightHeadroom` and apply `leftOverscroll` / `rightOverscroll` squish (`1.25f`).
