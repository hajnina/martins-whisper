#!/usr/bin/env bash
# Cross-checks the Kotlin `external fun` declarations against the JNI symbols
# exported by app/src/main/cpp/whisper_jni.cpp and the library name loaded by
# WhisperCppNative. Cheap guard against renaming one side only.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KOTLIN="${KOTLIN_ENGINE:-$ROOT/app/src/main/kotlin/com/martins/whisper/asr/WhisperCppEngine.kt}"
CPP="${JNI_SOURCE:-$ROOT/app/src/main/cpp/whisper_jni.cpp}"
CMAKE="${CMAKE_FILE:-$ROOT/app/src/main/cpp/CMakeLists.txt}"

fail() { echo "native-jni-check: FAIL: $*" >&2; exit 1; }

for f in "$KOTLIN" "$CPP" "$CMAKE"; do
    test -f "$f" || fail "missing file $f"
done

PKG="$(sed -n 's/^package[[:space:]]\+\([A-Za-z0-9_.]*\).*/\1/p' "$KOTLIN" | head -1)"
OBJ="$(grep -oE 'object[[:space:]]+WhisperCppNative' "$KOTLIN" | awk '{print $2}' | head -1)"
[[ -n "$PKG" && -n "$OBJ" ]] || fail "cannot find package/object WhisperCppNative in $KOTLIN"
PREFIX="Java_$(printf '%s.%s' "$PKG" "$OBJ" | tr '.' '_')"

mapfile -t KOTLIN_FNS < <(sed -nE 's/.*external fun ([A-Za-z0-9_]+).*/\1/p' "$KOTLIN" | sort -u)
mapfile -t CPP_FNS < <(grep -oE "${PREFIX}_[A-Za-z0-9_]+" "$CPP" | sed "s/^${PREFIX}_//" | sort -u)

[[ ${#KOTLIN_FNS[@]} -gt 0 ]] || fail "no external fun declarations found"
[[ ${#CPP_FNS[@]} -gt 0 ]] || fail "no $PREFIX_* JNI exports found in $CPP"

missing_cpp="$(comm -23 <(printf '%s\n' "${KOTLIN_FNS[@]}") <(printf '%s\n' "${CPP_FNS[@]}"))"
missing_kotlin="$(comm -13 <(printf '%s\n' "${KOTLIN_FNS[@]}") <(printf '%s\n' "${CPP_FNS[@]}"))"

[[ -z "$missing_cpp" ]] || fail "Kotlin declares but JNI does not export: $(echo "$missing_cpp" | tr '\n' ' ')"
[[ -z "$missing_kotlin" ]] || fail "JNI exports but Kotlin does not declare: $(echo "$missing_kotlin" | tr '\n' ' ')"

LOAD_LIB="$(sed -nE 's/.*System\.loadLibrary\("([A-Za-z0-9_]+)"\).*/\1/p' "$KOTLIN" | head -1)"
[[ -n "$LOAD_LIB" ]] || fail "no System.loadLibrary(...) found"
grep -qE "add_library\(${LOAD_LIB}[[:space:]]" "$CMAKE" \
    || fail "loadLibrary(\"$LOAD_LIB\") has no matching add_library target in CMakeLists.txt"

printf 'native-jni-check: OK (%d methods, class %s.%s, library %s.so)\n' \
    "${#KOTLIN_FNS[@]}" "$PKG" "$OBJ" "$LOAD_LIB"
printf '  %s\n' "${KOTLIN_FNS[@]}"
