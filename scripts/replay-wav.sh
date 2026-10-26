#!/usr/bin/env bash
# Replays a 16 kHz mono PCM16 WAV through the production dictation path on the host
# (DictationService -> AudioCaptureBuffer -> DictationEngineHost -> whisper.cpp -> TranscriptGate)
# and prints peak/RMS dBFS, the gate verdict and the transcript.
#
# Usage: scripts/replay-wav.sh [WAV]
#        WAV defaults to the public whisper.cpp samples/jfk.wav.
# Env:   WHISPER_REPLAY_MODEL   GGML model (default build/host-native/models/ggml-tiny.bin)
#        GRADLE_USER_HOME      required private Gradle home (never the shared toolchain one)
#        CXX / CC / CMAKE      host compilers for the one-time native build
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
ROOT="$PWD"
BUILD="$ROOT/build/host-native"
LIB="$BUILD/lib"
MODEL="${WHISPER_REPLAY_MODEL:-$BUILD/models/ggml-tiny.bin}"
DEFAULT_WAV="$ROOT/app/src/main/cpp/whisper.cpp/samples/jfk.wav"
WAV="${1:-$DEFAULT_WAV}"
TEST_CLASS="com.martins.whisper.harness.WavReplayRobolectricTest"
TEST_METHOD="jfkReplayAssertsLiteralWordsAndSpeakVerdict"

die() { echo "replay-wav: FAIL: $*" >&2; exit "${2:-1}"; }

SHARED_GRADLE_HOME="/work/android-toolchain/gradle-home"
[[ -n "${GRADLE_USER_HOME:-}" ]] || die "set GRADLE_USER_HOME to a private copy of the toolchain Gradle home" 2
[[ "$GRADLE_USER_HOME" != "$SHARED_GRADLE_HOME" ]] || die "refusing the shared Gradle home: $SHARED_GRADLE_HOME" 2

export JAVA_HOME="${JAVA_HOME:-/work/android-toolchain/jdk}"
export ANDROID_HOME="${ANDROID_HOME:-/work/android-toolchain/sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
AAPT2="${AAPT2:-$ANDROID_HOME/build-tools/35.0.1/aapt2}"

# The nix gcc in this sandbox links against a newer glibc than the JVM loads; prefer the
# conda-forge compiler (glibc 2.17 sysroot) when present, unless the caller chose one.
if [[ -z "${CXX:-}" && -x /home/node/mamba/env/bin/aarch64-conda-linux-gnu-g++ ]]; then
    CXX=/home/node/mamba/env/bin/aarch64-conda-linux-gnu-g++
fi
if [[ -z "${CC:-}" && -n "${CXX:-}" ]]; then
    case "$(basename "$CXX")" in
        *g++) CC="${CXX%++}" ;;
        *c++) CC="${CXX%c++}cc" ;;
    esac
fi
if [[ -z "${CMAKE:-}" && -x "$ANDROID_HOME/cmake/3.31.5/bin/cmake" ]]; then
    CMAKE="$ANDROID_HOME/cmake/3.31.5/bin/cmake"
fi
export CC CXX CMAKE

if [[ ! -f "$LIB/libmartins_whisper.so" || ! -f "$MODEL" || ! -f "$WAV" ]]; then
    echo "== Host native build (one time; libs=$LIB model=$MODEL)"
    WHISPER_HOST_MODEL="$MODEL" HOST_JNI_BUILD_ONLY=1 scripts/host-jni-test.sh \
        || die "host native build failed" 2
fi
[[ -f "$LIB/libmartins_whisper.so" ]] || die "missing $LIB/libmartins_whisper.so" 2
[[ -f "$MODEL" ]] || die "missing model: $MODEL" 2
[[ -f "$WAV" ]] || die "WAV not found: $WAV (pass the file as the first argument)" 2
WAV="$(readlink -f "$WAV")"
[[ "$(stat -c %s "$WAV")" -gt 44 ]] || die "WAV is empty: $WAV" 2

OUT="$(mktemp)"
LOG="$(mktemp)"
cleanup() { rm -f "$OUT" "$LOG"; }
trap cleanup EXIT

echo "== Replaying $WAV"
echo "== Model: $MODEL"
echo "== Native: $LIB"
export LD_LIBRARY_PATH="$LIB${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
export WHISPER_REPLAY_WAV="$WAV"
export WHISPER_REPLAY_MODEL="$MODEL"
export WHISPER_REPLAY_OUT="$OUT"

set +e
./gradlew --offline --console=plain --rerun-tasks \
    "-Pandroid.aapt2FromMavenOverride=$AAPT2" \
    "-Pwhisper.host.libDir=$LIB" \
    :app:testDebugUnitTest \
    --tests "$TEST_CLASS.$TEST_METHOD" >"$LOG" 2>&1
status=$?
set -e

if [[ -s "$OUT" ]]; then
    cat "$OUT"
fi
if [[ "$status" -ne 0 ]]; then
    tail -40 "$LOG" >&2
    die "replay test failed (see output above)" "$status"
fi
[[ -s "$OUT" ]] || die "replay produced no result (native lib/model not loadable? see $LOG)" 2
echo "replay-wav: PASS"
