#!/usr/bin/env python3
"""Guards the single Liquid Glass lens: runs the real optics formulas and reads the shader's contract."""
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile

MAIN = pathlib.Path(__file__).resolve().parents[1] / "TMessagesProj/src/main"
BLUR = MAIN / "java/org/telegram/ui/Components/blur3"
errors = []


def read(path):
    return path.read_text(encoding="utf-8")


shader = read(MAIN / "res/raw/liquid_glass_engine_shader.agsl")
for uniform in ("shader img", "float2 center", "float2 size", "float4 radius", "float thickness",
                "float refract_intensity", "float glass_press", "float glass_highlight",
                "float glass_light_angle", "float glass_motion", "float glass_adaptive",
                "float4 foreground_color_premultiplied", "float2 optics", "float2 shape",
                "float4 arrow", "float4 arrow_axis"):
    if f"uniform {uniform};" not in shader:
        errors.append(f"engine shader: missing uniform {uniform}")
if "refract_index" in shader:
    errors.append("engine shader: refract_index is not part of the engine")
if "0.28 * min(refract_intensity, 1.0)" not in shader:
    errors.append("engine shader: the tide must stop with the bend, or the icons under a resting pill shift")
if shader.count("img.eval(") != 1:
    errors.append("engine shader: the backdrop is sampled exactly once")

effect = read(BLUR / "LiquidGlassEffect.java")
for needle in ("R.raw.liquid_glass_engine_shader", "R.raw.liquid_glass_pill_shader",
               "R.raw.liquid_glass_header_shader", "R.raw.liquid_glass_shader",
               "R.raw.liquid_glass_arrow_shader", "public static boolean engineActive()", "if (engine || shader == arrowShader)",
               "LiquidGlassTuning.isActive()", "catch (RuntimeException e)"):
    if needle not in effect:
        errors.append(f"LiquidGlassEffect: missing {needle}")
for path, needles in (
        (BLUR / "drawable/BlurredBackgroundDrawable.java",
         ("LiquidGlassEffect.engineActive()", "LiquidGlassOptics.bevel(", "LiquidGlassOptics.bend(",
          "elasticPillLens ? LiquidGlassOptics.pillBend(",
          "LiquidGlassEffect.engineActive() || (headerGlassLens && !elasticPillLens)")),
        (MAIN / "java/org/telegram/ui/MainTabsLayout.java",
         ("LiquidGlassEffect.engineActive()", "R.raw.liquid_glass_tab_lens_shader", "R.raw.liquid_glass_pill_content_shader",
          'AndroidUtilities.dpf2(14) * lift')),
        (MAIN / "java/app/exteraless/appearance/glass/IosSliderThumb.java",
         ("LiquidGlassEffect.engineActive()", "LiquidGlassOptics.sliderBevel(", "LiquidGlassOptics.sliderBend("))):
    text = read(path)
    errors.extend(f"{path.name}: missing {needle}" for needle in needles if needle not in text)

CASES = (
    ("bevel(165, 3, 11)", 33), ("bevel(30, 3, 11)", 13.5), ("bevel(600, 3, 11)", 72),
    ("bevel(150, 3, 22)", 60), ("bevel(1, 3, 11)", 1),
    ("bend(1)", 4), ("bend(2.2f)", 6), ("bend(0)", 0),
    ("pillBend(1)", 0.48), ("pillBend(2.2f)", 0.72), ("pillBend(0)", 0),
    ("sliderBevel(60, 3, 11)", 20), ("sliderBend(1, 1)", 0.8), ("sliderBend(2, 0.5f)", 0.45),
    ("sliderBend(0, 1)", 0),
    ("role(true, true)", 2), ("role(false, true)", 1), ("role(false, false)", 0),
)
probe = "package org.telegram.ui.Components.blur3;\npublic class OpticsProbe {\n" \
    "  static int failed;\n" \
    "  static void is(String call, float got, float want) {\n" \
    "    if (!(Math.abs(got - want) <= 0.01f)) { failed = 1; System.out.println(call + \" = \" + got + \", want \" + want); }\n" \
    "  }\n" \
    "  public static void main(String[] args) {\n" \
    + "".join(f'    is("{call}", LiquidGlassOptics.{call}, {want}f);\n' for call, want in CASES) \
    + "    System.exit(failed);\n  }\n}\n"


def tool(name):
    home = os.environ.get("JAVA_HOME")
    found = shutil.which(name, path=os.path.join(home, "bin")) if home else None
    return found or shutil.which(name)


javac, java = tool("javac"), tool("java")
if not javac or not java:
    errors.append("javac and java are required (JAVA_HOME or PATH)")
else:
    with tempfile.TemporaryDirectory() as tmp:
        source = pathlib.Path(tmp) / "OpticsProbe.java"
        source.write_text(probe, encoding="utf-8")
        build = subprocess.run([javac, "-encoding", "utf-8", "-d", tmp, str(BLUR / "LiquidGlassOptics.java"), str(source)],
                               capture_output=True, text=True)
        if build.returncode:
            errors.append("LiquidGlassOptics does not compile:\n" + build.stderr)
        else:
            run = subprocess.run([java, "-cp", tmp, "org.telegram.ui.Components.blur3.OpticsProbe"],
                                 capture_output=True, text=True)
            if run.returncode:
                errors.append("LiquidGlassOptics:\n" + run.stdout + run.stderr)

if errors:
    sys.exit("\n".join(errors))
print("Liquid Glass engine: OK")
