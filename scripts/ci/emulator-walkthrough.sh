#!/usr/bin/env bash
# Fail-closed emulator walkthrough: walks every app screen via uiautomator semantics
# (dump -> find node bounds by text/content-desc -> tap), screenshots each state and
# records shots/manifest.tsv as <shot-file>\t<screen>\t<verification>.
# Any missing screen/element fails the whole gate with ::error:: plus diagnostics.
set -euo pipefail

APP_ID="${APP_ID:-com.martins.whisper}"
SHOTS="${GATE_SHOTS:-shots}"
WAV_SRC="${GATE_WAV:-app/src/main/cpp/whisper.cpp/samples/jfk.wav}"
DUMP_FILE=/sdcard/gate-ui.xml
DUMP_LOCAL=/tmp/gate-ui.xml
LAST_STEP="startup"
mkdir -p "$SHOTS"
: > "$SHOTS/manifest.tsv"

fail() {
    echo "::error::emulator-walkthrough: $*" >&2
    gate_diagnostics "$LAST_STEP" || true
    exit 1
}

gate_diagnostics() {
    local step="$1"
    adb exec-out screencap -p > "$SHOTS/FAIL-$step.png" 2>/dev/null || true
    adb shell uiautomator dump "$DUMP_FILE" >/dev/null 2>&1 || true
    adb pull "$DUMP_FILE" "$SHOTS/FAIL-$step.xml" >/dev/null 2>&1 || true
    echo "diagnostics: FAIL-$step.png/xml saved; logcat tail follows:" >&2
    adb logcat -d -t 400 2>/dev/null \
        | grep -iE "$APP_ID|MartinsWhisper|AndroidRuntime|FATAL" | tail -80 >&2 || true
}

shot() {
    local name="$1" screen="$2" verify="$3"
    adb exec-out screencap -p > "$SHOTS/$name.png" || fail "screencap failed for $name"
    [[ -s "$SHOTS/$name.png" ]] || fail "empty screencap for $name"
    printf '%s\t%s\t%s\n' "$name.png" "$screen" "$verify" >> "$SHOTS/manifest.tsv"
    echo "shot: $name.png ($screen) verified: $verify"
}

dump_ui() {
    local attempt
    for attempt in 1 2 3 4 5; do
        if adb shell uiautomator dump "$DUMP_FILE" >/dev/null 2>&1 \
            && adb pull "$DUMP_FILE" "$DUMP_LOCAL" >/dev/null 2>&1 \
            && [[ -s "$DUMP_LOCAL" ]]; then
            return 0
        fi
        sleep 2
    done
    return 1
}

ui_find() {
    # $1 attr: text|content-desc ; $2 value ; $3 mode: exact|contains ; $4 optional "clickable"
    python3 - "$DUMP_LOCAL" "$@" <<'PYEOF'
import re, sys, xml.etree.ElementTree as ET
path, attr, value, mode = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
attr = "content-desc" if attr == "desc" else attr
want_clickable = len(sys.argv) > 5 and sys.argv[5] == "clickable"
def matches(node):
    raw = node.get(attr, "")
    return raw == value if mode == "exact" else value.lower() in raw.lower()
nodes = [n for n in ET.parse(path).getroot().iter("node") if matches(n)]
if want_clickable:
    nodes = [n for n in nodes if n.get("clickable") == "true"] or nodes
if not nodes:
    sys.exit(1)
m = re.match(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", nodes[0].get("bounds", ""))
if not m:
    sys.exit(1)
print((int(m.group(1)) + int(m.group(3))) // 2, (int(m.group(2)) + int(m.group(4))) // 2)
PYEOF
}

present() {
    dump_ui || return 1
    ui_find "$@" >/dev/null 2>&1
}

tap_at() {
    adb shell input tap "$1" "$2" || fail "input tap $1 $2 failed"
}

tap_by() {
    # $1 attr ; $2 value ; $3 mode ; $4 optional "clickable"
    dump_ui || fail "uiautomator dump failed before tap_by $1=$2"
    local cx_cy
    cx_cy="$(ui_find "$@" 2>/dev/null)" || fail "no node $1='$2' to tap"
    tap_at $cx_cy
}

wait_for() {
    # $1 attr ; $2 value ; $3 mode ; $4 timeout_s ; $5 step name
    local deadline=$((SECONDS + $4))
    LAST_STEP="$5"
    while (( SECONDS < deadline )); do
        if present "$1" "$2" "$3"; then return 0; fi
        sleep 3
    done
    fail "timeout ${4}s waiting for $1 '$2' on step $5"
}

wait_gone() {
    # $1 attr ; $2 value ; $3 mode ; $4 timeout_s ; $5 step name
    local deadline=$((SECONDS + $4))
    LAST_STEP="$5"
    while (( SECONDS < deadline )); do
        if ! present "$1" "$2" "$3"; then return 0; fi
        sleep 3
    done
    fail "timeout ${4}s waiting for $1 '$2' to disappear on step $5"
}

scroll_to() {
    # $1 attr ; $2 value ; $3 mode ; $4 attempts (default 8)
    local n
    for (( n = 0; n < ${4:-8}; n++ )); do
        if present "$1" "$2" "$3"; then return 0; fi
        adb shell input swipe 540 1400 540 500 250 || fail "swipe failed"
        sleep 1
    done
    fail "could not scroll to $1 '$2'"
}

seed_fixture() {
    [[ -f "$WAV_SRC" ]] || fail "fixture WAV missing: $WAV_SRC"
    adb push "$WAV_SRC" /data/local/tmp/gate-jfk.wav >/dev/null || fail "push fixture failed"
    adb shell run-as "$APP_ID" mkdir -p files/inject || fail "run-as mkdir failed"
    adb shell run-as "$APP_ID" cp /data/local/tmp/gate-jfk.wav files/inject/jfk.wav \
        || fail "run-as cp failed"
    printf 'jfk.wav' | adb shell run-as "$APP_ID" sh -c 'cat > files/inject/selected.txt' \
        || fail "run-as fixture selection failed"
    adb shell run-as "$APP_ID" sh -c 'ls -la files/inject' || fail "fixture listing failed"
}

seed_prefs() {
    # model_id=tiny -> onboarding downloads ggml-tiny (77 MB), not the 141 MB default base.
    adb shell run-as "$APP_ID" mkdir -p shared_prefs || fail "run-as shared_prefs failed"
    printf '%s' "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name=\"model_id\">tiny</string>
</map>" | adb shell run-as "$APP_ID" sh -c 'cat > shared_prefs/martins_whisper_settings.xml' \
        || fail "run-as prefs seed failed"
    adb shell run-as "$APP_ID" sh -c 'cat shared_prefs/martins_whisper_settings.xml'
}

provision() {
    echo "provision: fresh install state"
    adb shell pm clear "$APP_ID" >/dev/null || fail "pm clear failed"
    seed_fixture
    seed_prefs
    adb shell appops set "$APP_ID" SYSTEM_ALERT_WINDOW allow || fail "appops overlay grant failed"
    adb shell pm grant "$APP_ID" android.permission.POST_NOTIFICATIONS \
        || fail "POST_NOTIFICATIONS grant failed"
    adb shell settings put secure enabled_accessibility_services \
        "$APP_ID/com.martins.whisper.accessibility.DictationAccessibilityService" \
        || fail "accessibility enable failed"
    adb shell settings put secure accessibility_enabled 1 || fail "accessibility_enabled failed"
    adb logcat -c || true
    adb shell am start -n "$APP_ID/.MainActivity" || fail "am start failed"
    sleep 5
}

# Full tap -> inject -> hold -> stop -> transcript cycle.
dictate_once() {
    # $1 step name ; $2 hold seconds after Recording ; $3 transcript probe ; $4 infer timeout
    local step="$1" hold="$2" probe="$3" timeout_infer="$4"
    echo "dictate_once: $step"
    tap_by desc "Tap to dictate" exact
    wait_for desc "Listening; tap to stop" exact 180 "$step-recording"
    shot "${step}-bubble-listening" "Overlay bubble dictating" \
        "desc 'Listening; tap to stop' visible over $step"
    sleep "$hold"
    tap_by desc "Listening; tap to stop" exact
    wait_for text "$probe" contains "$timeout_infer" "$step-transcript"
}

echo "== walkthrough start: $(date -u +%H:%M:%SZ) =="
adb get-state | grep -q device || fail "no adb device"

# Provision fresh state, fixture, prefs, permissions; launch into onboarding.
provision

echo "-- onboarding"
wait_for text "Talk. It types." contains 90 welcome
shot 01-welcome "Onboarding: welcome" "text 'Talk. It types.' visible"
tap_by text "Get started" contains

wait_for text "Your language" contains 30 language-step
shot 02-language "Onboarding: language" "language list visible"
tap_by text "English" contains clickable
wait_for text "✓" contains 15 language-selected
shot 02b-language-selected "Onboarding: language" "English row selected (✓)"
tap_by text "Continue" contains

wait_for text "Let me hear you" contains 30 perm-mic
shot 03-permission-mic "Onboarding: mic permission" "'Allow microphone' shown"
tap_by text "Allow microphone" contains
wait_for text "record audio" contains 30 mic-dialog
shot 03b-mic-system-dialog "System permission dialog" "RECORD_AUDIO dialog visible"
tap_by text "While using the app" contains
wait_for text "Granted" contains 30 perm-mic-granted
shot 03c-mic-granted "Onboarding: mic permission" "mic Granted after dialog"

wait_for text "Show the mic bubble" contains 30 perm-overlay
shot 04-permission-overlay "Onboarding: overlay permission" "overlay Granted via appops"
tap_by text "Next" exact

wait_for text "Type for you" contains 30 perm-accessibility
shot 05-permission-accessibility "Onboarding: accessibility permission" "accessibility Granted"
tap_by text "Next" exact

wait_for text "Stay informed" contains 30 perm-notifications
shot 06-permission-notifications "Onboarding: notifications permission" "notifications Granted"
tap_by text "Next" exact

echo "-- model step (real ggml-tiny download, checksum verified by the app)"
wait_for text "Choose your model" contains 30 model-step
shot 07-model-step "Onboarding: model download" "real ggml-tiny download started"
model_deadline=$((SECONDS + 900))
until present text "Model ready." contains; do
    if present text "Retry" exact; then
        tap_by text "Retry" exact || true
        echo "model download retry tapped ($(date -u +%H:%M:%SZ))"
    fi
    (( SECONDS < model_deadline )) || fail "model download did not finish within 900s (retry loop exhausted)"
    sleep 10
done
shot 07b-model-ready "Onboarding: model download" "ggml-tiny installed ('Model ready.')"
tap_by text "Continue" contains

echo "-- first dictation"
wait_for text "Try your first dictation" contains 30 first-dictation
shot 08-first-dictation "Onboarding: first dictation" "practice field + 'Try it now'"
tap_by text "Try it now" contains
wait_for desc "Tap to dictate" exact 45 first-dictation-bubble
shot 08b-bubble-idle "Overlay bubble" "bubble visible with desc 'Tap to dictate'"
dictate_once 08 8 "fellow" 480
shot 08c-transcript-result "Onboarding: first dictation" \
    "transcript containing 'fellow' in the practice field"
tap_by text "Continue" contains

echo "-- done step"
wait_for text "You are set" contains 30 done
shot 09-done "Onboarding: done" "'You are set' + 'Start using it'"
tap_by text "Start using it" contains

echo "-- home"
wait_for text "Ready" exact 45 home
wait_for text "Floating bubble" contains 15 home-bubble
shot 10-home "Home screen" "'Ready' pill, 'Floating bubble' switch, bubble overlay"
tap_by desc "Settings" exact

echo "-- settings (legacy gear screen)"
wait_for text "Permissions" contains 45 settings
shot 11-settings "Settings (gear)" "permission rows + overlay toggle visible"
scroll_to text "Import custom model" contains 8
shot 12-settings-models "Settings: model browser" "model rows with Download/Import/Delete actions"
tap_by text "Tiny (multilingual)" contains
shot 12b-model-selected "Settings: model selection" "Tiny row selected ('Installed and ready.')"
scroll_to text "Compute backend" contains 8
shot 13-settings-backend "Settings: backend + language + idle" "Compute backend section visible"
scroll_to text "Diagnostics" contains 8
shot 14-settings-diagnostics "Settings: diagnostics" "Diagnostics section visible"

echo "-- overlay toggle round trip"
tap_by text "Start overlay" contains overlay-on
wait_for desc "Tap to dictate" exact 45 overlay-bubble-on
tap_by text "Stop overlay" contains overlay-off
wait_gone desc "Tap to dictate" exact 30 overlay-bubble-off
shot 15-overlay-toggle "Overlay bubble round trip" "bubble stopped then restarted via toggle"
tap_by text "Start overlay" contains overlay-on-2
wait_for desc "Tap to dictate" exact 45 overlay-bubble-on-2

echo "-- home transcript after a real second dictation"
adb shell input keyevent KEYCODE_BACK || fail "back key failed"
wait_for text "Ready" exact 30 home-2
dictate_once 16 8 "fellow" 480
wait_for text "Last transcript" contains 30 home-transcript
shot 16b-home-transcript "Home: last transcript" "home shows the 'Last transcript' card"

echo "== walkthrough OK: $(date -u +%H:%M:%SZ) =="
echo "manifest:"
cat "$SHOTS/manifest.tsv"
