#!/usr/bin/env bash
# Layer 2 runner (device-only). Fails fast and clearly when no Android device is attached.
#
# Usage:
#   GRADLE_USER_HOME=/home/node/gh-tess scripts/test-device.sh
#   scripts/test-device.sh --deny-mic    # pre-revoke RECORD_AUDIO, run PermissionDenialTest
#   ADB=/path/to/adb scripts/test-device.sh
set -euo pipefail

cd "$(dirname "$0")/.."

ANDROID_HOME="${ANDROID_HOME:-/work/android-toolchain/sdk}"
export ANDROID_HOME ANDROID_SDK_ROOT="$ANDROID_HOME"
export JAVA_HOME="${JAVA_HOME:-/work/android-toolchain/jdk}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/home/node/gh-tess}"

ADB="${ADB:-$ANDROID_HOME/platform-tools/adb}"
TARGET_PACKAGE="com.martins.whisper"
TEST_PACKAGE="com.martins.whisper.test"
RUNNER="$TEST_PACKAGE/androidx.test.runner.AndroidJUnitRunner"
APP_APK="app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

deny_mic=false
for arg in "$@"; do
  case "$arg" in
    --deny-mic) deny_mic=true ;;
    -h|--help)
      sed -n '2,9p' "$0"
      exit 0
      ;;
    *)
      echo "error: unknown argument: $arg" >&2
      exit 64
      ;;
  esac
done

if [[ ! -x "$ADB" ]]; then
  echo "error: adb not found at $ADB" >&2
  echo "Set ANDROID_HOME or ADB to a platform-tools directory that contains adb." >&2
  exit 127
fi

devices_output="$("$ADB" devices 2>&1)" || true
device_count="$(printf '%s\n' "$devices_output" | awk 'NR > 1 && $2 == "device" { count++ } END { print count + 0 }')"

if [[ "$device_count" -eq 0 ]]; then
  echo "No Android device or emulator is connected; Layer 2 tests not run." >&2
  echo "Connect a device with USB debugging enabled (or start an emulator), then re-run:" >&2
  echo "  adb devices" >&2
  echo "  scripts/test-device.sh" >&2
  if [[ -n "$devices_output" ]]; then
    echo "--- adb devices output ---" >&2
    printf '%s\n' "$devices_output" >&2
  fi
  exit 3
fi

echo "== Building debug APK + instrumentation APK (offline) =="
./gradlew --offline --console=plain :app:assembleDebug :app:assembleDebugAndroidTest

echo "== Installing on device =="
"$ADB" install -r "$APP_APK"
"$ADB" install -r "$TEST_APK"

echo "== Device setup (best effort) =="
"$ADB" shell appops set "$TARGET_PACKAGE" SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1 || true
"$ADB" shell settings put secure accessibility_enabled 1 >/dev/null 2>&1 || true
"$ADB" shell settings put secure enabled_accessibility_services \
  "$TARGET_PACKAGE/$TARGET_PACKAGE.accessibility.DictationAccessibilityService" >/dev/null 2>&1 || true

instrument_args=(-w)
if [[ "$deny_mic" == true ]]; then
  echo "== Revoking RECORD_AUDIO before instrumentation =="
  "$ADB" shell pm revoke "$TARGET_PACKAGE" android.permission.RECORD_AUDIO >/dev/null 2>&1 || true
  instrument_args+=(-e class com.martins.whisper.harness.PermissionDenialTest)
fi

output_file="$(mktemp)"
trap 'rm -f "$output_file"' EXIT

echo "== am instrument =="
set +e
"$ADB" shell am instrument "${instrument_args[@]}" "$RUNNER" | tee "$output_file"
status=${PIPESTATUS[0]}
set -e

if [[ "$deny_mic" == true ]]; then
  "$ADB" shell pm grant "$TARGET_PACKAGE" android.permission.RECORD_AUDIO >/dev/null 2>&1 || true
fi

if [[ "$status" -ne 0 ]] || ! grep -qE '^OK \(' "$output_file"; then
  echo "Layer 2 tests failed on device (adb exit $status)." >&2
  exit 1
fi

grep -E '^OK \(' "$output_file"
