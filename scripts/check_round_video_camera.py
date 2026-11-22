#!/usr/bin/env python3
"""Regression and syntax guard for round video max/60/120 FPS, ultrawide/stabilization, and settings backup."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "TMessagesProj/src/main"
JAVA = MAIN / "java"
RES = MAIN / "res"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def balanced_delimiters(source: str) -> bool:
    stack = []
    pairs = {")": "(", "}": "{", "]": "["}
    in_string = False
    in_char = False
    in_line_comment = False
    in_block_comment = False
    escape = False
    i = 0
    n = len(source)
    while i < n:
        ch = source[i]
        nxt = source[i + 1] if i + 1 < n else ""
        if in_line_comment:
            if ch == "\n":
                in_line_comment = False
        elif in_block_comment:
            if ch == "*" and nxt == "/":
                in_block_comment = False
                i += 1
        elif in_string:
            if escape:
                escape = False
            elif ch == "\\":
                escape = True
            elif ch == '"':
                in_string = False
        elif in_char:
            if escape:
                escape = False
            elif ch == "\\":
                escape = True
            elif ch == "'":
                in_char = False
        else:
            if ch == "/" and nxt == "/":
                in_line_comment = True
                i += 1
            elif ch == "/" and nxt == "*":
                in_block_comment = True
                i += 1
            elif ch == '"':
                in_string = True
            elif ch == "'":
                in_char = True
            elif ch in "({[":
                stack.append(ch)
            elif ch in ")}]":
                if not stack or stack[-1] != pairs[ch]:
                    return False
                stack.pop()
        i += 1
    return not stack and not in_string and not in_char and not in_block_comment


def valid_xml(path: Path) -> bool:
    try:
        ET.parse(path)
        return True
    except ET.ParseError:
        return False


gl_proc = read(JAVA / "org/telegram/utils/recorder/round/RoundVideoGlProcessor.java")
session = read(JAVA / "org/telegram/utils/recorder/round/RoundVideoSession.java")
camera_ctrl = read(JAVA / "org/telegram/utils/recorder/round/RoundVideoCameraController.java")
codec_rec = read(JAVA / "org/telegram/utils/recorder/round/RoundVideoCodecRecorder.java")
shared_settings = read(JAVA / "org/telegram/utils/settings/SharedSettings.java")
camera_view = read(JAVA / "org/telegram/ui/Components/InstantCameraView2.java")
settings_ui = read(JAVA / "org/telegram/ui/RoundVideoSettingsActivity.java")
backup_agent = read(JAVA / "org/telegram/messenger/BackupAgent.java")
manifest = read(MAIN / "AndroidManifest.xml")
backup_rules = read(RES / "xml/backup_rules.xml")
extraction_rules = read(RES / "xml/data_extraction_rules.xml")

PREF_FILES = [
    "nkmrcfg",
    "mainconfig",
    "exteraconfig",
    "pillstackconfig",
    "exfullgram_account_profiles_meta",
    "exfullgram_account_profiles_template",
    "saved_tokens",
    "saved_tokens_login",
]

checks = {
    "Balanced braces, parens, and quotes across all round-video and backup Java files": all(
        balanced_delimiters(src)
        for src in (gl_proc, session, camera_ctrl, codec_rec, shared_settings, camera_view, settings_ui, backup_agent)
    ),
    "Manifest and backup XML configs parse cleanly": all(
        valid_xml(p)
        for p in (
            MAIN / "AndroidManifest.xml",
            RES / "xml/backup_rules.xml",
            RES / "xml/data_extraction_rules.xml",
            RES / "values/round_video_diagnostics.xml",
        )
    ),
    "RoundVideoGlProcessor preserves real presentation timestamps (no divisor bug) and supports software frame pacing": (
        "getCameraPresentationTime(timestamp) / highSpeedFrameDivisor" not in gl_proc
        and "long presentationTime = getCameraPresentationTime(timestamp);" in gl_proc
        and "public void setTargetOutputFps(int fps)" in gl_proc
        and "private void drawPacedFrame()" in gl_proc
        and "pacedFrameRunnable" in gl_proc
    ),
    "RoundVideoSession & SharedSettings expose 60/120/240/Max FPS and ultrawide zoom capabilities": (
        "FPS_120(120)" in session
        and "FPS_240(240)" in session
        and "FPS_MAX(240)" in session
        and "public boolean isMaxAuto()" in session
        and "public float getMinimumZoomRatio()" in session
        and "public boolean isUltrawideAvailable()" in session
        and "public boolean setZoomRatio(float ratio)" in session
        and "public static RoundVideoSession.FrameRate getRequestedRoundVideoFrameRate()" in shared_settings
    ),
    "RoundVideoCameraController runs per-camera max/120/60 FPS, ultrawide switching, and preview/OIS/EIS stabilization": (
        "|| pipelineFrameRate == RoundVideoSession.FrameRate.FPS_30" not in camera_ctrl
        and "MAXIMUM_UI_ZOOM_RATIO = 8f" in camera_ctrl
        and "private int resolveMaxSessionEncoderFps()" in camera_ctrl
        and "private int probeMaxFpsForFacing(" in camera_ctrl
        and "private static float readMinimumZoomRatio(" in camera_ctrl
        and "private String discoverBackUltrawideFallbackId(" in camera_ctrl
        and "private void switchBackUltrawideCamera(boolean wantUltrawide)" in camera_ctrl
        and "CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION" in camera_ctrl
        and "forceMax60Fps" in camera_ctrl
        and "* 102L / 100L" in camera_ctrl
    ),
    "RoundVideoCodecRecorder gracefully falls back from >60 FPS encoder limits to 60 FPS": (
        "targetFps > 60 && capabilities.areSizeAndRateSupported(outputSize, outputSize, 60)" in codec_rec
        and "retrying at 60 fps" in codec_rec
    ),
    "InstantCameraView2 wires zoom pill preset switcher, sticky pinch zoom, and 60 Hz UI clock": (
        "private final TextView zoomButton;" in camera_view
        and "private void cycleZoomPreset()" in camera_view
        and "pinchStartZoomRatio" in camera_view
        and "SharedSettings.getRequestedRoundVideoFrameRate()" in camera_view
        and "UI_FRAME_FALLBACK_DELAY_NS = 33_000_000L" in camera_view
        and "addFrameCallback(\n                    recordingUiFallback,\n                    60" in camera_view
    ),
    "RoundVideoSettingsActivity supports 30/60/120/240/Max FPS, safe bitrate labels array, and extended diagnostics": (
        'new CharSequence[]{"30 FPS", "60 FPS", "120 FPS", "240 FPS", "Max (Auto)"}' in settings_ui
        and "CharSequence[] labels = new CharSequence[BITRATES.length];" in settings_ui
        and "private static String frameRateLabel(" in settings_ui
        and "CONTROL_ZOOM_RATIO_RANGE" in settings_ui
        and "getConcurrentCameraIds()" in settings_ui
    ),
    "AndroidManifest, BackupAgent, and XML rules preserve ExFullGram settings across uninstall/reinstall": (
        'android:hasFragileUserData="true"' in manifest
        and 'android:allowBackup="true"' in manifest
        and 'android:fullBackupContent="@xml/backup_rules"' in manifest
        and 'android:dataExtractionRules="@xml/data_extraction_rules"' in manifest
        and all(f'"{pref}"' in backup_agent for pref in PREF_FILES)
        and all(f'path="{pref}.xml"' in backup_rules for pref in PREF_FILES)
        and all(f'path="{pref}.xml"' in extraction_rules for pref in PREF_FILES)
    ),
}

for title, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {title}")

fails = [title for title, ok in checks.items() if not ok]
if fails:
    raise SystemExit(f"{len(fails)} round video & backup checks failed")
