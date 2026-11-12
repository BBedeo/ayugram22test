#!/usr/bin/env python3
"""Small regression guard for optional universal Liquid Glass.

This checks integration contracts only. It does NOT replace the Android
Gradle build, an emulator, GPU shader checks, or an actual device visual test.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "TMessagesProj/src/main/java"
KOTLIN = ROOT / "TMessagesProj/src/main/kotlin"


def read(path):
    return path.read_text(encoding="utf-8")


config = read(KOTLIN / "app/exteraless/appearance/AppearanceConfig.kt")
settings_ui = read(JAVA / "app/exteraless/settings/OpenExteraAppearanceActivity.java")
tuning = read(JAVA / "app/exteraless/appearance/LiquidGlassTuning.java")
engine = read(JAVA / "org/telegram/utils/glass/GlassEngine.java")
state = read(JAVA / "org/telegram/utils/glass/GlassSettings.java")
factory = read(JAVA / "org/telegram/ui/Components/blur3/BlurredBackgroundDrawableViewFactory.java")
drawable = read(JAVA / "org/telegram/ui/Components/blur3/drawable/BlurredBackgroundDrawable.java")
menu = read(JAVA / "app/exteraless/appearance/GlassMenuHelper.java")
dialog = read(JAVA / "org/telegram/ui/ActionBar/AlertDialog.java")
profiles = read(JAVA / "app/exteraless/settings/AccountSettingsProfiles.java")
shader = read(ROOT / "TMessagesProj/src/main/res/raw/liquid_glass_shader.agsl")
arrow = read(ROOT / "TMessagesProj/src/main/res/raw/liquid_glass_arrow_shader.agsl")

checks = {
    "Master setting stays opt-in and OFF by default":
        'addConfig("OEAppearanceUniversalLiquidGlass", ConfigItem.configTypeBool, false)' in config,
    "Master and material settings share ExFullGram Appearance preferences":
        'addConfig("OEAppearanceLiquidGlassMaterial", ConfigItem.configTypeInt, 1)' in config
        and 'OEAppearanceUniversalLiquidGlass' in config,
    "Settings UI exposes master and material and rebuilds existing screens":
        'universalLiquidGlassRow' in settings_ui
        and 'liquidGlassMaterialRow' in settings_ui
        and 'rebuildAll()' in settings_ui,
    "API and battery fallback stay active":
        'Build.VERSION.SDK_INT >= Build.VERSION_CODES.S' in tuning
        and '!LiteMode.isPowerSaverApplied()' in tuning,
    "Native GlassEngine respects opt-in and invalidates when material changes":
        'LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) || exGlass' in engine
        and 'glassSettings.liquidMaterial != material' in engine
        and 'new GlassSettings(isBlurEnabled, isLiquidGlassEnabled, material)' in engine,
    "Material participates in native glass equality and hash code":
        'liquidMaterial == settings.liquidMaterial' in state
        and 'Objects.hash(isBlurEnabled, isLiquidGlassEnabled, liquidMaterial)' in state,
    "GPU refraction stays on Telegram native AGSL shader":
        'LiquidGlassEffect' in drawable
        and 'LiquidGlassTuning.refractionScale()' in drawable
        and 'uniform float refract_intensity;' in shader
        and 'uniform float refract_intensity;' in arrow,
    "Snapshot menus use drawable-owner GPU lens with fallback":
        'CapturedGlassSource implements GlassSource, DrawableOwner' in factory
        and 'new BlurredBackgroundDrawable(' in factory
        and 'lensSource, glassSource, glassSource' in factory
        and 'bitmapLensDrawables' in factory,
    "Snapshot capture invalidates recorded GPU frames":
        'd.invalidateDisplayList();' in factory
        and 'factory.invalidateAllLinkedViews();' in menu,
    "Popup materials preserve text contrast":
        'tintAlphaMenu' in tuning
        and 'tintAlphaOverlay' in tuning
        and 'LiquidGlassTuning.tintAlphaMenu' in menu,
    "Standard dialogs use native system blur only on opt-in":
        'LaunchActivity.systemBlurEnabled' in dialog
        and 'LiquidGlassTuning.isActive()' in dialog,
    "Live/Test account profiles reload glass renderer":
        'AppearanceConfig.INSTANCE.loadConfig(true);' in profiles
        and 'GlassEngine.getCurrentGlassSettings();' in profiles,
}

for title, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {title}")

fails = [title for title, ok in checks.items() if not ok]
if fails:
    raise SystemExit(f"{len(fails)} universal Liquid Glass regressions detected")
