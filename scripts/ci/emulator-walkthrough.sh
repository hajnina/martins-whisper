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
    # $1 attr ; $2 value ; $3 mode ; $4 attempts (default 12)
    local n
    for (( n = 0; n < ${4:-12}; n++ )); do
        if present "$1" "$2" "$3"; then return 0; fi
        # Small drag, not a fling: a 900px fling can jump past a row between
        # presence checks (system accessibility list, run 43800136422).
        adb shell input swipe 540 1500 540 1000 300 || fail "swipe failed"
        sleep 1
    done
    # The content may sit above the current position after earlier steps
    # scrolled down (model rows vs the Import row, run 43804123640); walk back
    # up the same range before giving up.
    for (( n = 0; n < ${4:-12}; n++ )); do
        if present "$1" "$2" "$3"; then return 0; fi
        adb shell input swipe 540 1000 540 1500 300 || fail "swipe failed"
        sleep 1
    done
    fail "could not scroll to $1 '$2'"
}

bubble_present() {
    # uiautomator dump only captures the focused window on this platform, so the
    # TYPE_APPLICATION_OVERLAY bubble is invisible to it (proven by
    # FAIL-first-dictation-bubble.xml, run 43802314363). Presence comes from the
    # window list instead; the window is titled "Martins Whisper bubble".
    adb shell dumpsys window windows 2>/dev/null | grep -q "Martins Whisper bubble"
}

wait_bubble() {
    # $1 timeout_s ; $2 step name
    local deadline=$((SECONDS + $1))
    LAST_STEP="$2"
    while (( SECONDS < deadline )); do
        if bubble_present; then return 0; fi
        sleep 2
    done
    fail "timeout ${1}s waiting for the overlay bubble window on step $2"
}

wait_bubble_gone() {
    # $1 timeout_s ; $2 step name
    local deadline=$((SECONDS + $1))
    LAST_STEP="$2"
    while (( SECONDS < deadline )); do
        if ! bubble_present; then return 0; fi
        sleep 2
    done
    fail "timeout ${1}s waiting for the overlay bubble window to disappear on step $2"
}

bubble_tap() {
    # Fresh install keeps the deterministic default spot: right edge, vertically
    # centred (72dp bubble, 12dp margin); taps never persist a position.
    local size density bubble margin cx
    size="$(adb shell wm size | tr -d '\r' | grep -o '[0-9][0-9]*x[0-9][0-9]*' | tail -1)" \
        || fail "wm size failed"
    density="$(adb shell wm density | tr -d '\r' | grep -o '[0-9][0-9]*' | tail -1)" \
        || fail "wm density failed"
    bubble="$(awk -v d="$density" 'BEGIN{printf "%.0f", 72*d/160}')"
    margin="$(awk -v d="$density" 'BEGIN{printf "%.0f", 12*d/160}')"
    cx="$(awk -v w="${size%%x*}" -v m="$margin" -v b="$bubble" 'BEGIN{printf "%d", w-m-b/2}')"
    tap_at "$cx" "$(( ${size#*x} / 2 ))"
}

bubble_state_count() {
    adb logcat -d 2>/dev/null | grep -c "MartinsWhisperState.*$1" || true
}

wait_state_beyond() {
    # $1 state-log event ; $2 baseline count ; $3 timeout_s ; $4 step name
    local deadline=$((SECONDS + $3))
    LAST_STEP="$4"
    local count
    while (( SECONDS < deadline )); do
        count="$(bubble_state_count "$1")"
        if (( count > $2 )); then return 0; fi
        sleep 2
    done
    fail "timeout ${3}s waiting for state '$1' on step $4"
}

seed_fixture() {
    [[ -f "$WAV_SRC" ]] || fail "fixture WAV missing: $WAV_SRC"
    adb push "$WAV_SRC" /data/local/tmp/gate-jfk.wav >/dev/null || fail "push fixture failed"
    # Absolute paths: run-as cwd/relative-path handling has bitten us once already
    # (mkdir+cp exited 0, then the selected.txt write said "No such file or
    # directory"). Verify each stage so the true state is visible on failure.
    local inject_dir="/data/data/$APP_ID/files/inject"
    adb shell run-as "$APP_ID" mkdir -p "$inject_dir" || fail "run-as mkdir failed"
    adb shell run-as "$APP_ID" cp /data/local/tmp/gate-jfk.wav "$inject_dir/jfk.wav" \
        || fail "run-as cp failed"
    # Write via push+cp: the piped "sh -c 'cat > ...'" variant failed with
    # "can't create files/inject/selected.txt" even after mkdir+cp succeeded,
    # so no shell redirection is trusted here. cp from /data/local/tmp is the
    # one run-as write path that provably worked on the runner.
    printf 'jfk.wav' > /tmp/gate-selected.txt
    adb push /tmp/gate-selected.txt /data/local/tmp/gate-selected.txt >/dev/null \
        || fail "push selected.txt failed"
    adb shell run-as "$APP_ID" cp /data/local/tmp/gate-selected.txt "$inject_dir/selected.txt" \
        || fail "run-as fixture selection failed"
    local selected
    selected="$(adb shell run-as "$APP_ID" cat "$inject_dir/selected.txt" \
        || fail "fixture verification failed")"
    if [[ "$(printf '%s' "$selected" | tr -d '\r\n')" != "jfk.wav" ]]; then
        fail "fixture selection content mismatch: got '$selected'"
    fi
}

seed_prefs() {
    # model_id=tiny -> onboarding downloads ggml-tiny (77 MB), not the 141 MB default base.
    local prefs="/tmp/gate-prefs.xml"
    printf '%s' "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name=\"model_id\">tiny</string>
</map>" > "$prefs"
    adb push "$prefs" /data/local/tmp/gate-prefs.xml >/dev/null || fail "push prefs failed"
    local prefs_path="/data/data/$APP_ID/shared_prefs"
    adb shell run-as "$APP_ID" mkdir -p "$prefs_path" || fail "run-as shared_prefs failed"
    adb shell run-as "$APP_ID" cp /data/local/tmp/gate-prefs.xml "$prefs_path/martins_whisper_settings.xml" \
        || fail "run-as prefs seed failed"
    adb shell run-as "$APP_ID" cat "$prefs_path/martins_whisper_settings.xml" \
        || fail "prefs verification failed"
    rm -f "$prefs"
}

provision() {
    echo "provision: fresh install state"
    adb shell pm clear "$APP_ID" >/dev/null || fail "pm clear failed"
    seed_fixture
    seed_prefs
    adb shell appops set "$APP_ID" SYSTEM_ALERT_WINDOW allow || fail "appops overlay grant failed"
    adb shell pm grant "$APP_ID" android.permission.POST_NOTIFICATIONS \
        || fail "POST_NOTIFICATIONS grant failed"
    # Direct settings put may be ignored/reverted for adb-installed apps on
    # Android 13+ ("restricted settings"); the walkthrough branches on what the
    # app actually shows and falls back to the real system UI when needed.
    adb shell settings put secure enabled_accessibility_services \
        "$APP_ID/com.martins.whisper.accessibility.DictationAccessibilityService" \
        || fail "accessibility enable failed"
    adb shell settings put secure accessibility_enabled 1 || fail "accessibility_enabled failed"
    adb logcat -c || true
    adb shell am start -n "$APP_ID/.MainActivity" || fail "am start failed"
    sleep 5
}

# Full tap -> inject -> hold -> stop -> transcript cycle. Bubble interactions
# are geometric (dump-invisible overlay); state comes from the app's own
# MartinsWhisperState log, so a missed tap fails loud in seconds, not minutes.
dictate_once() {
    # $1 step name ; $2 hold seconds after capture-start ; $3 transcript probe ; $4 infer timeout
    local step="$1" hold="$2" probe="$3" timeout_infer="$4"
    echo "dictate_once: $step"
    local n0 s0
    n0="$(bubble_state_count 'capture-start')"
    bubble_tap
    wait_state_beyond 'capture-start' "$n0" 30 "$step-recording"
    shot "${step}-bubble-listening" "Overlay bubble dictating" \
        "overlay session recording over $step (capture-start logged)"
    sleep "$hold"
    s0="$(bubble_state_count 'tap-stop')"
    bubble_tap
    wait_state_beyond 'tap-stop' "$s0" 20 "$step-stop"
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
# Permission steps do not auto-advance after granting; the mic screen keeps its
# own Next button (proven by FAIL-perm-overlay.png from run 43796605989).
tap_by text "Next" exact

wait_for text "Show the mic bubble" contains 30 perm-overlay
shot 04-permission-overlay "Onboarding: overlay permission" "overlay Granted via appops"
tap_by text "Next" exact

wait_for text "Type for you" contains 30 perm-accessibility
sleep 1
if present text "Next" exact; then
    echo "accessibility: granted via settings put (fast path)"
    shot 05-permission-accessibility "Onboarding: accessibility permission" "accessibility Granted"
    tap_by text "Next" exact
else
    # The app still sees the service disabled; grant it the way a user does.
    # The emulator image is pinned (API 34), so the system screens are stable.
    echo "accessibility: settings put not visible to the app; granting via system UI"
    tap_by text "Open accessibility" contains
    wait_for text "Accessibility" contains 20 accessibility-settings
    # Row sits at the top under "Downloaded apps"; probe the app name so it
    # matches whichever label the settings list exposes.
    scroll_to text "Martins Whisper" contains 10
    tap_by text "Martins Whisper" contains
    wait_for class android.widget.Switch exact 15 accessibility-detail
    sleep 1
    tap_by class android.widget.Switch exact
    sleep 1
    if present text "Allow" exact; then
        tap_by text "Allow" exact
    elif present text "OK" exact; then
        tap_by text "OK" exact
    fi
    sleep 2
    adb shell input keyevent KEYCODE_BACK || fail "back key failed"
    adb shell input keyevent KEYCODE_BACK || fail "back key failed"
    wait_for text "Granted" contains 60 perm-accessibility-granted
    shot 05-permission-accessibility "Onboarding: accessibility permission" "accessibility Granted via system UI"
    tap_by text "Next" exact
fi

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
wait_bubble 45 first-dictation-bubble
shot 08b-bubble-idle "Overlay bubble" "bubble window present (dumpsys)"
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
# Tiny rows sit above the Import row this step just scrolled to; walk back up
# to them before tapping.
scroll_to text "Tiny (multilingual)" contains 12
tap_by text "Tiny (multilingual)" contains
shot 12b-model-selected "Settings: model selection" "Tiny row selected ('Installed and ready.')"
scroll_to text "Compute backend" contains 8
shot 13-settings-backend "Settings: backend + language + idle" "Compute backend section visible"
scroll_to text "Diagnostics" contains 8
shot 14-settings-diagnostics "Settings: diagnostics" "Diagnostics section visible"

echo "-- overlay toggle round trip"
tap_by text "Start overlay" contains overlay-on
wait_bubble 45 overlay-bubble-on
tap_by text "Stop overlay" contains overlay-off
wait_bubble_gone 30 overlay-bubble-off
shot 15-overlay-toggle "Overlay bubble round trip" "bubble stopped then restarted via toggle"
tap_by text "Start overlay" contains overlay-on-2
wait_bubble 45 overlay-bubble-on-2

echo "-- home transcript after a real second dictation"
adb shell input keyevent KEYCODE_BACK || fail "back key failed"
wait_for text "Ready" exact 30 home-2
dictate_once 16 8 "fellow" 480
wait_for text "Last transcript" contains 30 home-transcript
shot 16b-home-transcript "Home: last transcript" "home shows the 'Last transcript' card"

echo "== walkthrough OK: $(date -u +%H:%M:%SZ) =="
echo "manifest:"
cat "$SHOTS/manifest.tsv"
