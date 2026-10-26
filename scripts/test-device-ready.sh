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

results_dir="core/build/test-results/test app/build/test-results/testDebugUnitTest"
results=$(find $results_dir -name 'TEST-*.xml' 2>/dev/null | wc -l)
tests=$(find $results_dir -name 'TEST-*.xml' -exec sed -n 's/.* tests="\([0-9]*\)".*/\1/p' {} + 2>/dev/null |
  awk '{ sum += $1 } END { print sum + 0 }')
failures=$(find $results_dir -name 'TEST-*.xml' -exec sed -n 's/.* failures="\([0-9]*\)".*/\1/p' {} + 2>/dev/null |
  awk '{ sum += $1 } END { print sum + 0 }')
errors=$(find $results_dir -name 'TEST-*.xml' -exec sed -n 's/.* errors="\([0-9]*\)".*/\1/p' {} + 2>/dev/null |
  awk '{ sum += $1 } END { print sum + 0 }')

echo
echo "Layer 1 results: $results suites, $tests tests, $failures failures, $errors errors"
for module_dir in core app; do
  module_files=$(find "$module_dir/build/test-results" -name 'TEST-*.xml' 2>/dev/null)
  if [[ -n "$module_files" ]]; then
    module_tests=$(printf '%s\n' "$module_files" | xargs sed -n 's/.* tests="\([0-9]*\)".*/\1/p' | awk '{ s += $1 } END { print s + 0 }')
    module_suites=$(printf '%s\n' "$module_files" | wc -l)
    echo "  $module_dir: $module_suites suites, $module_tests tests"
  fi
done

# A green Gradle task is not enough: prove the harness tests actually ran and are present
# in this run's XML (a stale/empty results dir must never be reported as PASS).
required_tests=(
  FakeAudioPipelineE2ETest
  DictationServiceRobolectricTest
  OverlayControllerRobolectricTest
)
missing=()
for required in "${required_tests[@]}"; do
  if ! find $results_dir -name "TEST-*${required}*.xml" 2>/dev/null | grep -q .; then
    missing+=("$required")
  fi
done

fail() { echo "FAIL: $*" >&2; exit 1; }

[[ "$results" -gt 0 ]] || fail "Layer 1 produced no test-result XML"
[[ "$tests" -gt 0 ]] || fail "Layer 1 reported zero tests"
[[ "$failures" -eq 0 ]] || fail "Layer 1 reported $failures failures"
[[ "$errors" -eq 0 ]] || fail "Layer 1 reported $errors errors"
[[ ${#missing[@]} -eq 0 ]] || fail "required harness tests did not run: ${missing[*]}"

app_apk="app/build/outputs/apk/debug/app-debug.apk"
test_apk="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
[[ -f "$app_apk" ]] || fail "missing $app_apk"
[[ -f "$test_apk" ]] || fail "missing $test_apk"

echo "PASS Layer 1: $tests tests, 0 failures, 0 errors (required harness tests present)"
echo "PASS Layer 1: $app_apk ($(stat -c %s "$app_apk") B, sha256 $(sha256sum "$app_apk" | cut -c1-8)…)"
echo "PASS Layer 2 compile: $test_apk ($(stat -c %s "$test_apk") B, sha256 $(sha256sum "$test_apk" | cut -c1-8)…)"
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
