#!/usr/bin/env bash
# T1 harness gate: Layer 1 (JVM/Robolectric) must run and pass here; Layer 2
# (instrumentation) must compile here and is executed only on a real device at H1.
#
# Requires a PRIVATE Gradle user home (never the shared /work/android-toolchain/gradle-home):
#   GRADLE_USER_HOME=/home/node/gh-tess scripts/test-device-ready.sh
set -euo pipefail

cd "$(dirname "$0")/.."

SHARED_GRADLE_HOME="/work/android-toolchain/gradle-home"
if [[ -z "${GRADLE_USER_HOME:-}" ]]; then
  echo "error: set GRADLE_USER_HOME to a private copy (shared one is used by other containers)" >&2
  exit 2
fi
if [[ "$GRADLE_USER_HOME" == "$SHARED_GRADLE_HOME" ]]; then
  echo "error: refusing to use the shared Gradle home: $SHARED_GRADLE_HOME" >&2
  exit 2
fi

export JAVA_HOME="${JAVA_HOME:-/work/android-toolchain/jdk}"
export ANDROID_HOME="${ANDROID_HOME:-/work/android-toolchain/sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
AAPT2="${AAPT2:-$ANDROID_HOME/build-tools/35.0.1/aapt2}"

GRADLE=(./gradlew --offline --rerun-tasks --console=plain "-Pandroid.aapt2FromMavenOverride=$AAPT2")

echo "== Layer 1 + Layer 2 compile (Gradle user home: $GRADLE_USER_HOME) =="
"${GRADLE[@]}" \
  :core:test \
  :app:testDebugUnitTest \
  :app:assembleDebug \
  :app:assembleDebugAndroidTest

results=$(find core/build/test-results/test app/build/test-results/testDebugUnitTest \
  -name 'TEST-*.xml' 2>/dev/null | wc -l)
tests=$(find core/build/test-results/test app/build/test-results/testDebugUnitTest \
  -name 'TEST-*.xml' -exec sed -n 's/.* tests="\([0-9]*\)".*/\1/p' {} + 2>/dev/null |
  awk '{ sum += $1 } END { print sum + 0 }')
failures=$(find core/build/test-results/test app/build/test-results/testDebugUnitTest \
  -name 'TEST-*.xml' -exec sed -n 's/.* failures="\([0-9]*\)".*/\1/p' {} + 2>/dev/null |
  awk '{ sum += $1 } END { print sum + 0 }')
errors=$(find core/build/test-results/test app/build/test-results/testDebugUnitTest \
  -name 'TEST-*.xml' -exec sed -n 's/.* errors="\([0-9]*\)".*/\1/p' {} + 2>/dev/null |
  awk '{ sum += $1 } END { print sum + 0 }')

echo
echo "PASS Layer 1: $results suites, $tests tests, $failures failures, $errors errors"
echo "PASS Layer 1: app/build/outputs/apk/debug/app-debug.apk"
echo "PASS Layer 2 compile: app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
cat <<'EOF'
Layer 2 execution is DEVICE-ONLY (H1); it is NOT run in this sandbox:
  OverlayFocusRetentionTest           overlay bubble keeps focus in the target field
  AccessibilityInsertionReadBackTest  insertion + caret visible to accessibility
  PasswordFieldRefusalTest            password/PIN refused, text untouched
  PermissionDenialTest                missing RECORD_AUDIO fails closed
  ServiceLifecycleTest               foreground service starts without mic, stops on ACTION_STOP
Run on hardware with:
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
  adb shell am instrument -w com.martins.whisper.test/androidx.test.runner.AndroidJUnitRunner
EOF
