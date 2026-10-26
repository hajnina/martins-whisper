#!/usr/bin/env bash
# Verifies the installable native APK: arm64-v8a only, required whisper.cpp
# libraries present, no model bundled, size within the v1 budget and the JNI
# library actually stripped with its JNI symbols exported.
#
# Usage: scripts/native-apk-check.sh [path/to/app-debug.apk]
# Env:   MAX_APK_BYTES (default 20 MiB)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK="${1:-$ROOT/app/build/outputs/apk/debug/app-debug.apk}"
MAX_APK_BYTES="${MAX_APK_BYTES:-20971520}"
ABI="arm64-v8a"
REQUIRED_LIBS=(
    libmartins_whisper.so
    libwhisper.so
    libggml.so
    libggml-base.so
    libggml-cpu.so
)
REPORT="${NATIVE_APK_REPORT:-$ROOT/build/native-apk-report.txt}"

fail() { echo "native-apk-check: FAIL: $*" >&2; exit 1; }

test -f "$APK" || fail "APK not found: $APK"
mkdir -p "$(dirname "$REPORT")"

command -v unzip >/dev/null || fail "unzip is required"
command -v sha256sum >/dev/null || fail "sha256sum is required"

entries="$(unzip -Z1 "$APK")"

# --- ABI: arm64-v8a only -----------------------------------------------------
mapfile -t abis < <(printf '%s\n' "$entries" | sed -nE 's#^lib/([^/]+)/.*#\1#p' | sort -u)
[[ ${#abis[@]} -gt 0 ]] || fail "APK has no lib/<abi>/ entries (native build did not run?)"
[[ "${abis[*]}" == "$ABI" ]] || fail "expected only $ABI, found: ${abis[*]}"

# --- required native libraries ----------------------------------------------
for lib in "${REQUIRED_LIBS[@]}"; do
    printf '%s\n' "$entries" | grep -qx "lib/$ABI/$lib" || fail "missing lib/$ABI/$lib"
done

# --- no model bundled (models are downloaded/imported on device) -------------
# kotlinx-coroutines ships a tiny JVM debug probe named DebugProbesKt.bin; it is
# not a model. Any other .bin/.gguf/.ggml is.
bundled_models="$(printf '%s\n' "$entries" \
    | grep -Ei '\.(bin|gguf|ggml)$' \
    | grep -vx 'DebugProbesKt.bin' || true)"
[[ -z "$bundled_models" ]] || fail "APK bundles model-like files: $(echo "$bundled_models" | tr '\n' ' ')"

# --- manifest ABI ------------------------------------------------------------
AAPT2="${AAPT2:-}"
if [[ -z "$AAPT2" && -n "${ANDROID_HOME:-}" ]]; then
    AAPT2="$(find "$ANDROID_HOME/build-tools" -maxdepth 2 -name aapt2 2>/dev/null | sort -V | tail -1)"
fi
[[ -n "$AAPT2" ]] || AAPT2="$(command -v aapt2 || true)"
if [[ -n "$AAPT2" ]]; then
    badging="$("$AAPT2" dump badging "$APK" 2>/dev/null)"
    grep -q "native-code: '$ABI'" <<<"$badging" || fail "manifest native-code is not '$ABI'"
fi

# --- size budget -------------------------------------------------------------
apk_bytes="$(stat -c%s "$APK")"
(( apk_bytes <= MAX_APK_BYTES )) \
    || fail "APK is $apk_bytes bytes, budget is $MAX_APK_BYTES (see docs/native-build.md)"

# --- exported symbols + stripping -------------------------------------------
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
unzip -p "$APK" "lib/$ABI/libmartins_whisper.so" > "$TMP/libmartins_whisper.so"
[[ -s "$TMP/libmartins_whisper.so" ]] || fail "could not extract libmartins_whisper.so"

READELF=""
if [[ -n "${ANDROID_NDK_HOME:-}" ]]; then
    READELF="$(find "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt" -maxdepth 3 -name llvm-readelf 2>/dev/null | head -1)"
fi
if [[ -z "$READELF" ]]; then
    READELF="$(command -v llvm-readelf || true)"
fi
[[ -n "$READELF" ]] || fail "llvm-readelf not found (set ANDROID_NDK_HOME)"

sections="$("$READELF" -S "$TMP/libmartins_whisper.so")"
if grep -q '\.debug_info' <<<"$sections"; then
    fail "libmartins_whisper.so is not stripped (has .debug_info)"
fi

syms="$("$READELF" --dyn-syms "$TMP/libmartins_whisper.so")"
grep -q 'Java_com_martins_whisper_asr_WhisperCppNative_nativeVersion' <<<"$syms" \
    || fail "libmartins_whisper.so does not export the JNI entry points"

# --- report ------------------------------------------------------------------
apk_sha="$(sha256sum "$APK" | awk '{print $1}')"
{
    echo "# Native APK report"
    echo
    echo "- APK: $APK"
    echo "- sha256: $apk_sha"
    echo "- size: $apk_bytes bytes ($(( apk_bytes / 1024 )) KiB), budget $MAX_APK_BYTES bytes"
    echo "- ABI: ${abis[*]}"
    echo "- stripped: libmartins_whisper.so has no .debug_info"
    echo "- JNI exports: present"
    echo "- bundled model files: none"
    echo
    echo "| library (in APK, compressed) | bytes |"
    echo "|---|---:|"
    while read -r bytes _date _time name; do
        [[ "$name" == "lib/$ABI/"* ]] || continue
        echo "| $name | $bytes |"
    done < <(unzip -l "$APK")
    echo
    echo "Toolchain: ${ANDROID_NDK_HOME:-unknown NDK}"
} > "$REPORT"

cat "$REPORT"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    cat "$REPORT" >> "$GITHUB_STEP_SUMMARY"
fi
