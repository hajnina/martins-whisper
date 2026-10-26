#!/usr/bin/env bash
# Verifies the installable native APK end to end without a device:
#   - arm64-v8a only, required whisper.cpp libraries present, no model bundled
#   - APK size within the v1 budget
#   - every bundled .so is a stripped AArch64 shared object
#   - every .so is 16 KB-page aligned and every DT_NEEDED resolves (bundled or
#     Android system library), i.e. the dynamic loader will not fail at startup
#   - every Kotlin `external fun` in WhisperCppEngine.kt is exported by
#     libmartins_whisper.so (artifact-level JNI contract, not just source text)
#   - uncompressed .so entries are 16 KB aligned inside the zip
#
# Usage: scripts/native-apk-check.sh [path/to/app-debug.apk]
# Env:   MAX_APK_BYTES (default 32 MiB; Compose onboarding adds ~12 MiB of debug dex), ANDROID_NDK_HOME, ANDROID_HOME
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK="${1:-$ROOT/app/build/outputs/apk/debug/app-debug.apk}"
MAX_APK_BYTES="${MAX_APK_BYTES:-33554432}"
ABI="arm64-v8a"
PAGE_ALIGN=0x4000
REQUIRED_LIBS=(
    libmartins_whisper.so
    libwhisper.so
    libggml.so
    libggml-base.so
    libggml-cpu.so
)
# Libraries provided by the Android platform. Anything else must be inside the APK.
SYSTEM_LIBS=(
    libc.so
    libm.so
    libdl.so
    liblog.so
    libandroid.so
    libz.so
    libstdc++.so
    libOpenSLES.so
    libOpenMAXAL.so
    libEGL.so
    libGLESv2.so
    libGLESv3.so
    libvulkan.so
    libnativehelper.so
    libjnigraphics.so
    libui.so
    libutils.so
    libbinder.so
)
KOTLIN_ENGINE="${KOTLIN_ENGINE:-$ROOT/app/src/main/kotlin/com/martins/whisper/asr/WhisperCppEngine.kt}"
REPORT="${NATIVE_APK_REPORT:-$ROOT/build/native-apk-report.txt}"

fail() { echo "native-apk-check: FAIL: $*" >&2; exit 1; }
warn() { echo "native-apk-check: WARN: $*" >&2; }

test -f "$APK" || fail "APK not found: $APK"
mkdir -p "$(dirname "$REPORT")"

command -v unzip >/dev/null || fail "unzip is required"
command -v sha256sum >/dev/null || fail "sha256sum is required"

# --- locate an ELF reader ----------------------------------------------------
find_readelf() {
    local candidate
    for candidate in "${READELF:-}" "$(command -v readelf || true)" \
        "$(command -v llvm-readelf || true)"; do
        [[ -n "$candidate" && -x "$candidate" ]] || continue
        if "$candidate" --version >/dev/null 2>&1; then
            printf '%s\n' "$candidate"
            return 0
        fi
    done
    local ndk
    local ndks=()
    [[ -n "${ANDROID_NDK_HOME:-}" ]] && ndks+=("$ANDROID_NDK_HOME")
    [[ -n "${ANDROID_NDK_ROOT:-}" ]] && ndks+=("$ANDROID_NDK_ROOT")
    if [[ -n "${ANDROID_HOME:-}" && -d "$ANDROID_HOME/ndk" ]]; then
        while IFS= read -r ndk; do ndks+=("$ndk"); done < <(find "$ANDROID_HOME/ndk" -mindepth 1 -maxdepth 1 -type d 2>/dev/null)
    fi
    for ndk in "${ndks[@]}"; do
        candidate="$(find "$ndk/toolchains/llvm/prebuilt" -maxdepth 3 -name llvm-readelf 2>/dev/null | head -1)"
        [[ -n "$candidate" && -x "$candidate" ]] || continue
        if "$candidate" --version >/dev/null 2>&1; then
            printf '%s\n' "$candidate"
            return 0
        fi
    done
    return 1
}
READELF="$(find_readelf || true)"
[[ -n "$READELF" ]] || fail "readelf/llvm-readelf not found (set ANDROID_NDK_HOME or install binutils)"

find_zipalign() {
    local candidate
    for candidate in "${ZIPALIGN:-}" "$(command -v zipalign || true)"; do
        [[ -n "$candidate" && -x "$candidate" ]] && { printf '%s\n' "$candidate"; return 0; }
    done
    local sdk
    for sdk in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}"; do
        [[ -n "$sdk" && -d "$sdk/build-tools" ]] || continue
        candidate="$(find "$sdk/build-tools" -maxdepth 2 -name zipalign 2>/dev/null | sort -V | tail -1)"
        [[ -n "$candidate" && -x "$candidate" ]] && { printf '%s\n' "$candidate"; return 0; }
    done
    return 1
}

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

# --- extract native libraries ------------------------------------------------
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/lib"
mapfile -t so_entries < <(printf '%s\n' "$entries" | grep -E "^lib/$ABI/.*\.so$" | sort)
[[ ${#so_entries[@]} -gt 0 ]] || fail "no .so files under lib/$ABI"
for entry in "${so_entries[@]}"; do
    name="$(basename "$entry")"
    unzip -p "$APK" "$entry" > "$TMP/lib/$name"
    [[ -s "$TMP/lib/$name" ]] || fail "could not extract $entry"
done

# --- ELF machine, stripping, page alignment, DT_NEEDED -----------------------
declare -A NEEDED=()
declare -A BUNDLED=()
for entry in "${so_entries[@]}"; do
    BUNDLED["$(basename "$entry")"]=1
done

for entry in "${so_entries[@]}"; do
    name="$(basename "$entry")"
    so="$TMP/lib/$name"

    header="$("$READELF" -W -h "$so")"
    grep -qE 'Class:[[:space:]]+ELF64' <<<"$header" || fail "$name is not ELF64"
    grep -qE 'Machine:[[:space:]]+AArch64' <<<"$header" || fail "$name is not AArch64"
    grep -qE 'Type:[[:space:]]+DYN' <<<"$header" || fail "$name is not a shared object (DYN)"

    sections="$("$READELF" -W -S "$so")"
    if grep -q '\.debug_info' <<<"$sections"; then
        fail "$name is not stripped (has .debug_info)"
    fi

    aligns="$("$READELF" -W -l "$so" | awk '/[[:space:]]LOAD[[:space:]]/ {print $NF}')"
    [[ -n "$aligns" ]] || fail "$name has no LOAD segments"
    while read -r align; do
        [[ -n "$align" ]] || continue
        value=$((align))
        (( value >= PAGE_ALIGN )) \
            || fail "$name LOAD alignment $align < 0x4000 (16 KB pages unsupported); rebuild with -Wl,-z,max-page-size=16384"
    done <<<"$aligns"

    while read -r need; do
        [[ -n "$need" ]] || continue
        NEEDED["$need"]=1
    done < <("$READELF" -W -d "$so" | sed -nE 's/.*\(NEEDED\).*Shared library: \[([^]]+)\].*/\1/p')
done

# --- DT_NEEDED closure: bundled or an Android system library ------------------
for need in "${!NEEDED[@]}"; do
    [[ -n "${BUNDLED[$need]:-}" ]] && continue
    if [[ "$need" == "libc++_shared.so" ]]; then
        fail "APK requires $need but does not bundle it (it is not an Android system library)"
    fi
    found=0
    for system in "${SYSTEM_LIBS[@]}"; do
        [[ "$need" == "$system" ]] && found=1 && break
    done
    (( found == 1 )) || fail "DT_NEEDED $need is neither bundled in the APK nor a known Android system library"
done

# --- artifact-level JNI contract: Kotlin external funs <-> exported symbols ---
[[ -f "$KOTLIN_ENGINE" ]] || fail "Kotlin engine not found: $KOTLIN_ENGINE"
PKG="$(sed -n 's/^package[[:space:]]\+\([A-Za-z0-9_.]*\).*/\1/p' "$KOTLIN_ENGINE" | head -1)"
OBJ="$(grep -oE 'object[[:space:]]+WhisperCppNative' "$KOTLIN_ENGINE" | awk '{print $2}' | head -1)"
[[ -n "$PKG" && -n "$OBJ" ]] || fail "cannot find package/object WhisperCppNative in $KOTLIN_ENGINE"
PREFIX="Java_$(printf '%s.%s' "$PKG" "$OBJ" | tr '.' '_')"
mapfile -t KOTLIN_FNS < <(sed -nE 's/.*external fun ([A-Za-z0-9_]+).*/\1/p' "$KOTLIN_ENGINE" | sort -u)
[[ ${#KOTLIN_FNS[@]} -gt 0 ]] || fail "no external fun declarations found in $KOTLIN_ENGINE"
jni_syms="$("$READELF" -W --dyn-syms "$TMP/lib/libmartins_whisper.so")"
for fn in "${KOTLIN_FNS[@]}"; do
    grep -qE "[[:space:]]${PREFIX}_${fn}$" <<<"$jni_syms" \
        || fail "Kotlin declares ${fn}() but libmartins_whisper.so does not export ${PREFIX}_${fn}"
done

# --- zip alignment for uncompressed native libraries -------------------------
ZIPALIGN="$(find_zipalign || true)"
zip_alignment="not checked (zipalign not found)"
if [[ -n "$ZIPALIGN" ]]; then
    if ! "$ZIPALIGN" -c -P 16 -v 4 "$APK" >/dev/null 2>&1; then
        fail "APK is not 16 KB zipaligned (run zipalign -f -P 16 4 in the packaging step)"
    fi
    zip_alignment="16 KB ($ZIPALIGN)"
else
    warn "zipalign not found; zip 16 KB alignment not verified"
    zip_alignment="UNVERIFIED"
fi

# --- report ------------------------------------------------------------------
apk_sha="$(sha256sum "$APK" | awk '{print $1}')"
{
    echo "# Native APK report"
    echo
    echo "- APK: $APK"
    echo "- sha256: $apk_sha"
    echo "- size: $apk_bytes bytes ($(( apk_bytes / 1024 )) KiB), budget $MAX_APK_BYTES bytes"
    echo "- ABI: ${abis[*]}"
    echo "- stripped: all native libraries have no .debug_info"
    echo "- ELF: AArch64 shared objects, LOAD alignment >= 0x4000"
    echo "- DT_NEEDED: all resolved (bundled or Android system libraries)"
    echo "- JNI exports: ${#KOTLIN_FNS[@]}/${#KOTLIN_FNS[@]} Kotlin external funs present"
    echo "- zip alignment: $zip_alignment"
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
