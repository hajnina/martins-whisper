#!/usr/bin/env bash
# One command from this checkout to a real device: build the arm64 native APK,
# verify it, install it plus the instrumentation APK, push a GGML model into the
# app-private model directory, and run NativeWhisperSmokeTest (loads whisper.cpp
# and transcribes 1 s of silence).
#
# Usage:
#   scripts/device-smoke.sh                          # USB device, downloads ggml-tiny.bin
#   scripts/device-smoke.sh --wireless 192.168.1.7:5555
#   scripts/device-smoke.sh --serial R58M123 --model /path/ggml-tiny.bin
#
# Wireless setup on the phone: Developer options -> Wireless debugging, or once
# over USB: adb tcpip 5555 && adb connect <phone-ip>:5555
#
# Options:
#   --serial SERIAL    adb serial (env ANDROID_SERIAL)
#   --wireless ADDR    run `adb connect ADDR` first, then use it as the serial
#   --adb PATH         adb binary (env ADB, PATH, or $ANDROID_HOME/platform-tools/adb)
#   --model PATH       GGML model file (default: build/models/ggml-<id>.bin, downloaded if absent)
#   --model-id ID      catalog id used for the device file name (default: tiny)
#   --apk PATH         native app APK (default: app/build/outputs/apk/debug/app-debug.apk)
#   --test-apk PATH    instrumentation APK (default: app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk)
#   --no-build         use the APKs already on disk
#   --keep-model       do not replace an existing files/models/<id>.bin
#   -h, --help         show this help
#
# Env: MARTINS_NDK_VERSION / MARTINS_CMAKE_VERSION are forwarded to Gradle.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
ROOT="$PWD"

PACKAGE="com.martins.whisper"
RUNNER="com.martins.whisper.test/androidx.test.runner.AndroidJUnitRunner"
SMOKE_CLASS="com.martins.whisper.harness.NativeWhisperSmokeTest"
MODEL_ID="tiny"
MODEL_FILE=""
SERIAL="${ANDROID_SERIAL:-}"
ADB="${ADB:-}"
WIRELESS=""
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
DO_BUILD=1
FORCE_MODEL=1

usage() { sed -n '2,30p' "$0" | sed 's/^# \{0,1\}//'; }
die() { echo "device-smoke: FAIL: $*" >&2; exit "${2:-1}"; }

while (($#)); do
    case "$1" in
        --serial) [[ $# -ge 2 ]] || die "--serial needs a value"; SERIAL="$2"; shift 2 ;;
        --wireless) [[ $# -ge 2 ]] || die "--wireless needs HOST[:PORT]"; WIRELESS="$2"; shift 2 ;;
        --adb) [[ $# -ge 2 ]] || die "--adb needs a path"; ADB="$2"; shift 2 ;;
        --model) [[ $# -ge 2 ]] || die "--model needs a path"; MODEL_FILE="$2"; shift 2 ;;
        --model-id) [[ $# -ge 2 ]] || die "--model-id needs a value"; MODEL_ID="$2"; shift 2 ;;
        --apk) [[ $# -ge 2 ]] || die "--apk needs a path"; APK="$2"; shift 2 ;;
        --test-apk) [[ $# -ge 2 ]] || die "--test-apk needs a path"; TEST_APK="$2"; shift 2 ;;
        --no-build) DO_BUILD=0; shift ;;
        --keep-model) FORCE_MODEL=0; shift ;;
        -h|--help) usage; exit 0 ;;
        *) die "unknown argument: $1 (try --help)" 64 ;;
    esac
done

find_adb() {
    local candidate
    for candidate in "$ADB" "$(command -v adb || true)" \
        "${ANDROID_HOME:-}/platform-tools/adb" "${ANDROID_SDK_ROOT:-}/platform-tools/adb"; do
        [[ -n "$candidate" && -x "$candidate" ]] || continue
        printf '%s\n' "$candidate"
        return 0
    done
    return 1
}
ADB="$(find_adb || true)"
[[ -n "$ADB" ]] || die "adb not found. Install platform-tools or pass --adb PATH." 2

if [[ -n "$WIRELESS" ]]; then
    echo "== adb connect $WIRELESS"
    "$ADB" connect "$WIRELESS"
    SERIAL="$WIRELESS"
fi

ADB_ARGS=("$ADB")
[[ -n "$SERIAL" ]] && ADB_ARGS+=(-s "$SERIAL")
on_device() { "${ADB_ARGS[@]}" "$@"; }

# --- device preflight --------------------------------------------------------
state="$(on_device get-state 2>/dev/null || true)"
[[ "$state" == "device" ]] || die "no ready device (state: ${state:-none}). Check 'adb devices' or --wireless ADDR." 3

model_name="$(on_device shell getprop ro.product.model | tr -d '\r')"
abi_list="$(on_device shell getprop ro.product.cpu.abilist | tr -d '\r')"
sdk="$(on_device shell getprop ro.build.version.sdk | tr -d '\r')"
page_size="$(on_device shell getconf PAGE_SIZE 2>/dev/null | tr -d '\r' || true)"
[[ "$page_size" =~ ^[0-9]+$ ]] || page_size="unknown"
echo "== Device: ${model_name:-unknown} (serial ${SERIAL:-auto}, API $sdk)"
echo "== ABI: $abi_list"
echo "== Page size: $page_size bytes"
grep -q 'arm64-v8a' <<<"$abi_list" || die "device has no arm64-v8a support; the v1 APK is arm64-only." 3
if [[ "$page_size" == "16384" ]]; then
    echo "   (16 KB device: the APK must have 16 KB-aligned ELF segments and zip entries)"
fi

# --- build -------------------------------------------------------------------
if (( DO_BUILD )); then
    command -v java >/dev/null || [[ -x "${JAVA_HOME:-}/bin/java" ]] || die "JDK 17 not found (set JAVA_HOME)." 2
    gradle_args=(:app:assembleDebug :app:assembleDebugAndroidTest -Pmartins.native=true -Pmartins.vulkan=false)
    [[ -n "${MARTINS_NDK_VERSION:-}" ]] && gradle_args+=("-Pmartins.ndkVersion=$MARTINS_NDK_VERSION")
    [[ -n "${MARTINS_CMAKE_VERSION:-}" ]] && gradle_args+=("-Pmartins.cmakeVersion=$MARTINS_CMAKE_VERSION")
    echo "== Building native APK + instrumentation APK"
    ./gradlew --console=plain "${gradle_args[@]}"
fi
[[ -f "$APK" ]] || die "APK not found: $APK (run without --no-build)" 2
[[ -f "$TEST_APK" ]] || die "test APK not found: $TEST_APK (run without --no-build)" 2

# --- static proof before touching the device ---------------------------------
echo "== Verifying native APK (ABI, JNI, 16 KB alignment, DT_NEEDED)"
scripts/native-apk-check.sh "$APK" >/dev/null || die "native APK verification failed (run scripts/native-apk-check.sh directly for the report)"

# --- model file (host side) --------------------------------------------------
if [[ -z "$MODEL_FILE" ]]; then
    MODEL_FILE="$ROOT/build/models/ggml-$MODEL_ID.bin"
    if [[ ! -f "$MODEL_FILE" ]]; then
        mkdir -p "$(dirname "$MODEL_FILE")"
        url="https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-$MODEL_ID.bin"
        echo "== Downloading $url"
        command -v curl >/dev/null || die "curl is required to download the model" 2
        curl -fL --retry 3 --connect-timeout 20 -o "$MODEL_FILE.part" "$url"
        mv "$MODEL_FILE.part" "$MODEL_FILE"
    fi
fi
[[ -f "$MODEL_FILE" ]] || die "model not found: $MODEL_FILE" 2
magic="$(od -An -tx1 -N4 "$MODEL_FILE" | tr -d ' \n')"
case "$magic" in
    # GGML magic is a little-endian uint32: "ggml" is stored as 6c6d6767.
    6c6d6767|666d6767|746a6767|616c6767|\
    67676d6c|67676d66|67676a74|67676c61) ;;
    *) die "not a whisper.cpp GGML model (magic 0x$magic; expected ggml/ggmf/ggjt/ggla): $MODEL_FILE" 2 ;;
esac
echo "== Model: $MODEL_FILE ($(wc -c < "$MODEL_FILE" | tr -d ' ') bytes, GGML)"

# --- install -----------------------------------------------------------------
echo "== Installing $APK"
on_device install -r "$APK"
echo "== Installing $TEST_APK"
on_device install -r "$TEST_APK"

# --- push model into the app-private contract path ---------------------------
push_args=(--id "$MODEL_ID")
[[ -n "$SERIAL" ]] && push_args+=(--serial "$SERIAL")
(( FORCE_MODEL )) && push_args+=(--force)
ADB="$ADB" scripts/push-model.sh "${push_args[@]}" "$MODEL_FILE"

remote_rel="files/models/$MODEL_ID.bin"
host_bytes="$(wc -c < "$MODEL_FILE" | tr -d ' ')"
device_bytes="$(on_device shell run-as "$PACKAGE" sh -c "wc -c < $remote_rel" 2>/dev/null | tr -d '\r ' || true)"
[[ "$device_bytes" == "$host_bytes" ]] \
    || die "device model size ${device_bytes:-unreadable} != host $host_bytes at $remote_rel" 4
echo "== Model contract: $PACKAGE $remote_rel = $device_bytes bytes (matches host)"

model_path="$(on_device shell run-as "$PACKAGE" realpath "$remote_rel" 2>/dev/null | tr -d '\r' || true)"
if [[ "$model_path" != /* ]]; then
    model_path=""
fi

# --- run the engine smoke test ----------------------------------------------
echo "== Running $SMOKE_CLASS"
instrument_args=(-w -e class "$SMOKE_CLASS")
[[ -n "$model_path" ]] && instrument_args+=(-e whisperModel "$model_path")
out="$(mktemp)"
trap 'rm -f "$out"' EXIT
set +e
on_device shell am instrument "${instrument_args[@]}" "$RUNNER" | tee "$out"
status=${PIPESTATUS[0]}
set -e
if [[ "$status" -ne 0 ]] || ! grep -qE '^OK \(' "$out"; then
    echo "--- last JNI/app log lines ---" >&2
    on_device logcat -d -t 200 -s MartinsWhisperJNI:* AndroidRuntime:E "*:S" 2>/dev/null | tail -60 >&2 || true
    die "on-device whisper.cpp smoke test failed (adb exit $status)" 5
fi
grep -E '^OK \(' "$out"
cat <<EOF

PASS: native whisper.cpp loads on the device and transcribed 1 s of silence.
Model path contract: $PACKAGE files/models/$MODEL_ID.bin
Next (MFVP): grant the 3 permissions, focus a text field in another app, tap the
bubble, speak, tap again; the text should be inserted.
EOF
