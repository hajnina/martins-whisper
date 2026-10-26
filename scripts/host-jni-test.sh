#!/usr/bin/env bash
# Host JNI tracer bullet: build whisper.cpp and app/src/main/cpp/whisper_jni.cpp natively
# for this host (no NDK, stub <android/log.h>), then run HostWhisperJniTest on the JVM.
# The test System.load()s the host library through java.library.path, transcribes
# whisper.cpp's samples/jfk.wav with ggml-tiny.bin and asserts the literal words.
#
# Usage: scripts/host-jni-test.sh
# Env:   WHISPER_HOST_MODEL   use an existing ggml-tiny.bin (default: download once)
#        WHISPER_HOST_JFK     use another 16 kHz mono WAV (default: pinned jfk.wav)
#        JAVA_HOME, CC, CXX, CMAKE, GRADLE_USER_HOME as usual
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
ROOT="$PWD"
BUILD="$ROOT/build/host-native"
LIB="$BUILD/lib"
MODELS="$BUILD/models"
WHISPER="$ROOT/app/src/main/cpp/whisper.cpp"
TINY_SHA256="be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21"

die() { echo "host-jni-test: FAIL: $*" >&2; exit "${2:-1}"; }

JAVA_HOME="${JAVA_HOME:-/work/android-toolchain/jdk}"
[[ -x "$JAVA_HOME/bin/java" ]] || die "JDK not found (set JAVA_HOME)" 2

CMAKE="${CMAKE:-$(command -v cmake || true)}"
if [[ -z "$CMAKE" && -n "${ANDROID_HOME:-}" ]]; then
    CMAKE="$(find "$ANDROID_HOME/cmake" -maxdepth 3 -type f -name cmake 2>/dev/null | sort -V | tail -1)"
fi
[[ -n "$CMAKE" && -x "$CMAKE" ]] || die "cmake not found (set CMAKE or ANDROID_HOME)" 2
CXX="${CXX:-$(command -v c++ || command -v g++ || command -v clang++ || true)}"
[[ -n "$CXX" && -x "$CXX" ]] || die "C++ compiler not found (set CXX)" 2
if [[ -z "${CC:-}" ]]; then
    case "$(basename "$CXX")" in
        *g++) CC="${CXX%g++}gcc" ;;
        *c++) CC="${CXX%c++}cc" ;;
        *) CC="$(command -v gcc || command -v clang || true)" ;;
    esac
fi
[[ -n "$CC" && -x "$CC" ]] || die "C compiler not found (set CC)" 2
if command -v ninja >/dev/null; then
    GENERATOR="Ninja"
else
    GENERATOR="Unix Makefiles"
fi

mkdir -p "$LIB" "$MODELS"

# --- pinned whisper.cpp + host build -----------------------------------------
scripts/fetch-whisper-cpp.sh "$WHISPER" >/dev/null
echo "== Building whisper.cpp for the host ($("$CMAKE" --version | head -1))"
"$CMAKE" -S "$WHISPER" -B "$BUILD/whisper" -G "$GENERATOR" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_C_COMPILER="$CC" \
    -DCMAKE_CXX_COMPILER="$CXX" \
    -DBUILD_SHARED_LIBS=ON \
    -DWHISPER_BUILD_TESTS=OFF \
    -DWHISPER_BUILD_EXAMPLES=OFF \
    -DWHISPER_BUILD_SERVER=OFF \
    -DWHISPER_BUILD_IS_DEV=OFF \
    -DGGML_NATIVE=OFF \
    -DGGML_OPENMP=OFF \
    -DGGML_LLAMAFILE=OFF \
    -DGGML_VULKAN=OFF >/dev/null
# Build only whisper (not parakeet): it pulls ggml, ggml-base and ggml-cpu as dependencies.
"$CMAKE" --build "$BUILD/whisper" --target whisper -j "$(getconf _NPROCESSORS_ONLN || echo 4)" >/dev/null

find "$BUILD/whisper" -maxdepth 4 -type f -name '*.so*' -exec cp -Lf {} "$LIB/" \;
# CMake installs versioned sonames only; both the unversioned dev symlink and the
# soname major (libwhisper.so.1) are needed by the loader at JNI time.
for so in "$LIB"/lib*.so.*; do
    [[ -e "$so" ]] || continue
    ln -sf "$(basename "$so")" "${so%%.so.*}.so"
    major="${so##*.so.}"; major="${major%%.*}"
    ln -sf "$(basename "$so")" "${so%%.so.*}.so.$major"
done
[[ -f "$LIB/libwhisper.so" ]] || die "host whisper build produced no libwhisper.so"

# --- JNI wrapper against the stub android/log.h ------------------------------
echo "== Building libmartins_whisper.so for the host"
"$CXX" -shared -fPIC -O2 -std=c++17 \
    -I "$ROOT/app/src/main/cpp/host-stubs" \
    -I "$JAVA_HOME/include" -I "$JAVA_HOME/include/linux" \
    -I "$WHISPER/include" -I "$WHISPER/ggml/include" \
    "$ROOT/app/src/main/cpp/whisper_jni.cpp" \
    -L "$LIB" -lwhisper -lggml -lggml-cpu -lggml-base -lpthread -ldl -lm \
    -Wl,-rpath,'$ORIGIN' \
    -o "$LIB/libmartins_whisper.so"

# --- fixtures ----------------------------------------------------------------
MODEL="${WHISPER_HOST_MODEL:-$MODELS/ggml-tiny.bin}"
if [[ ! -f "$MODEL" ]]; then
    echo "== Downloading ggml-tiny.bin (~78 MB, cached in build/host-native/models)"
    command -v curl >/dev/null || die "curl is required to download the model" 2
    curl -fL --retry 3 --connect-timeout 20 \
        -o "$MODEL.part" \
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin"
    mv "$MODEL.part" "$MODEL"
fi
actual="$(sha256sum "$MODEL" | awk '{print $1}')"
[[ "$actual" == "$TINY_SHA256" ]] || die "tiny model sha256 mismatch: $actual" 2

JFK="${WHISPER_HOST_JFK:-$WHISPER/samples/jfk.wav}"
[[ -f "$JFK" ]] || die "jfk.wav not found at $JFK" 2

if [[ "${HOST_JNI_BUILD_ONLY:-0}" == "1" ]]; then
    echo "host-jni-test: build-only OK (lib=$LIB model=$MODEL)"
    exit 0
fi

# --- run the JVM test --------------------------------------------------------
echo "== Java library dir: $LIB"
echo "== Model: $MODEL"
echo "== Audio: $JFK"
export LD_LIBRARY_PATH="$LIB${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
export JAVA_HOME WHISPER_HOST_MODEL="$MODEL" WHISPER_HOST_JFK="$JFK"
"$ROOT/gradlew" :app:testDebugUnitTest \
    --tests 'com.martins.whisper.asr.HostWhisperJniTest' \
    "-Pwhisper.host.libDir=$LIB" \
    --console=plain --no-daemon
echo "host-jni-test: PASS"
