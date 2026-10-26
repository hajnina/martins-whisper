#!/usr/bin/env bash
# Push a whisper.cpp GGML model into Martins Whisper's app-private model directory.
#
# Target path on device: /data/data/com.martins.whisper/files/models/<id>.bin
# This works on the debug (debuggable) v1 APK through `adb shell run-as`; it does
# not need root and does not modify any other app's data.
set -euo pipefail

readonly PACKAGE="com.martins.whisper"
readonly TMP_PREFIX="mw-model"
readonly SCRIPT_NAME="$(basename "$0")"

MODEL_FILE=""
MODEL_ID=""
SERIAL="${ANDROID_SERIAL:-}"
FORCE=0
VERIFY=1

usage() {
  cat <<'EOF'
Usage: push-model.sh [options] MODEL.bin

Copy a whisper.cpp GGML model (ggml-*.bin) into the installed app's private
files/models/ directory, where the app loads it from.

Options:
  --id ID          Model id / destination name (default: derived from ggml-<id>.bin)
  --serial SERIAL  Target device serial (default: $ANDROID_SERIAL or the only device)
  --force          Overwrite an existing model file on the device
  --no-verify      Do not compare the file SHA-256 with the catalog value
  -h, --help       Show this help and exit

Environment:
  ADB              Path to adb (default: adb on PATH, then $ANDROID_HOME/platform-tools/adb)
  ANDROID_SERIAL   Default device serial

Supported catalog ids and hashes:
  tiny tiny.en base base.en small small.en medium medium.en large-v3-turbo
A custom id is allowed for a model that is not in the catalog; verification is
then limited to printing the local SHA-256.

The app is debug-signed in v1. A non-debuggable (release) build makes `run-as`
unavailable; this script then exits with an explanation instead of touching data.
EOF
}

die() {
  local message="$1"
  local code="${2:-1}"
  echo "error: $message" >&2
  exit "$code"
}

find_adb() {
  if [[ -n "${ADB:-}" ]]; then
    [[ -x "$ADB" ]] || die "ADB=$ADB is not executable" 2
    printf '%s\n' "$ADB"
    return
  fi
  if command -v adb >/dev/null 2>&1; then
    command -v adb
    return
  fi
  local candidate
  for candidate in \
    "${ANDROID_HOME:-}/platform-tools/adb" \
    "${ANDROID_SDK_ROOT:-}/platform-tools/adb" \
    "/work/android-toolchain/sdk/platform-tools/adb"; do
    if [[ -n "$candidate" && -x "$candidate" ]]; then
      printf '%s\n' "$candidate"
      return
    fi
  done
  die "adb not found. Install Android platform-tools, run 'source scripts/env.sh', or set ADB=/path/to/adb." 2
}

catalog_sha() {
  case "$1" in
    tiny)              echo "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21" ;;
    tiny.en)           echo "921e4cf8686fdd993dcd081a5da5b6c365bfde1162e72b08d75ac75289920b1f" ;;
    base)              echo "60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe" ;;
    base.en)           echo "a03779c86df3323075f5e796cb2ce5029f00ec8869eee3fdfb897afe36c6d002" ;;
    small)             echo "1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b" ;;
    small.en)          echo "c6138d6d58ecc8322097e0f987c32f1be8bb0a18532a3f88f734d1bbf9c41e5d" ;;
    medium)            echo "6c14d5adee5f86394037b4e4e8b59f1673b6cee10e3cf0b11bbdbee79c156208" ;;
    medium.en)         echo "cc37e93478338ec7700281a7ac30a10128929eb8f427dda2e865faa8f6da4356" ;;
    large-v3-turbo)    echo "1fc70f774d38eb169993ac391eea357ef47c88757ef72ee5943879b7e8e2bc69" ;;
    *)                 return 1 ;;
  esac
}

local_sha() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  else
    return 1
  fi
}

parse_args() {
  while (($#)); do
    case "$1" in
      --id)
        [[ $# -ge 2 ]] || die "--id needs a value"
        MODEL_ID="$2"
        shift 2
        ;;
      --serial)
        [[ $# -ge 2 ]] || die "--serial needs a value"
        SERIAL="$2"
        shift 2
        ;;
      --force)
        FORCE=1
        shift
        ;;
      --no-verify)
        VERIFY=0
        shift
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      -*)
        die "unknown option: $1 (try --help)"
        ;;
      *)
        [[ -z "$MODEL_FILE" ]] || die "only one model file may be given"
        MODEL_FILE="$1"
        shift
        ;;
    esac
  done
  [[ -n "$MODEL_FILE" ]] || die "no model file given (try --help)"
}

main() {
  parse_args "$@"
  [[ -f "$MODEL_FILE" ]] || die "model file not found: $MODEL_FILE"

  if [[ -z "$MODEL_ID" ]]; then
    local base
    base="$(basename "$MODEL_FILE")"
    case "$base" in
      ggml-*.bin) MODEL_ID="${base#ggml-}"; MODEL_ID="${MODEL_ID%.bin}" ;;
      *) die "cannot derive a model id from '$base'; pass --id ID" ;;
    esac
  fi
  [[ "$MODEL_ID" =~ ^[A-Za-z0-9._-]+$ ]] || die "invalid model id '$MODEL_ID' (use letters, digits, . _ -)"
  [[ "$MODEL_ID" != "." && "$MODEL_ID" != ".." ]] || die "invalid model id '$MODEL_ID'"

  local expected actual
  expected="$(catalog_sha "$MODEL_ID" || true)"
  actual="$(local_sha "$MODEL_FILE" || true)"
  echo "Model:  $MODEL_FILE"
  echo "Id:     $MODEL_ID"
  echo "Bytes:  $(wc -c < "$MODEL_FILE" | tr -d ' ')"
  echo "SHA256: ${actual:-<sha256sum not available>}"
  if [[ "$VERIFY" -eq 1 && -n "$expected" ]]; then
    [[ -n "$actual" ]] || die "cannot verify: no sha256sum/shasum on this host"
    [[ "$actual" == "$expected" ]] || die "SHA-256 mismatch for '$MODEL_ID': expected $expected, got $actual"
    echo "Hash:   OK (matches the catalog)"
  elif [[ "$VERIFY" -eq 1 ]]; then
    echo "Hash:   not in the catalog; recorded above (not compared)"
  fi

  local adb
  adb="$(find_adb)"

  if [[ -z "$SERIAL" ]]; then
    local count
    count="$("$adb" devices 2>/dev/null | awk 'NR>1 && $2=="device" {n++} END {print n+0}' || true)"
    case "$count" in
      0) die "no adb device in 'device' state. Enable USB debugging and accept the RSA prompt; check 'adb devices'." 3 ;;
      1) ;;
      *) die "multiple devices connected; pass --serial SERIAL (see 'adb devices')." 3 ;;
    esac
  else
    local state
    state="$("$adb" -s "$SERIAL" get-state 2>/dev/null || true)"
    [[ "$state" == "device" ]] || die "device '$SERIAL' is not ready (state: ${state:-unknown})." 3
  fi

  local adb_args=("$adb")
  [[ -n "$SERIAL" ]] && adb_args+=(-s "$SERIAL")

  if ! "${adb_args[@]}" shell run-as "$PACKAGE" true >/dev/null 2>&1; then
    die "cannot use 'run-as $PACKAGE'. Install the debuggable v1 APK (scripts/install-apk.sh) or use a device build that exposes model import." 4
  fi

  local remote="$TMP_PREFIX-$(basename "$MODEL_FILE")"
  local target="files/models/$MODEL_ID.bin"
  local cleanup=0

  if "${adb_args[@]}" shell run-as "$PACKAGE" test -f "$target" >/dev/null 2>&1; then
    if [[ "$FORCE" -ne 1 ]]; then
      die "'$target' already exists on the device; pass --force to replace it." 5
    fi
    echo "Replacing existing $target (--force)."
  fi

  echo "Pushing to /data/local/tmp/$remote ..."
  "${adb_args[@]}" push "$MODEL_FILE" "/data/local/tmp/$remote" >/dev/null
  cleanup=1

  "${adb_args[@]}" shell run-as "$PACKAGE" mkdir -p files/models
  "${adb_args[@]}" shell run-as "$PACKAGE" cp "/data/local/tmp/$remote" "$target"
  "${adb_args[@]}" shell run-as "$PACKAGE" chmod 600 "$target"

  if [[ "$cleanup" -eq 1 ]]; then
    "${adb_args[@]}" shell rm -f "/data/local/tmp/$remote" >/dev/null 2>&1 || true
  fi

  echo "Installed on device: $target"
  "${adb_args[@]}" shell run-as "$PACKAGE" ls -l "$target" || true
  echo "In the app, choose '$MODEL_ID' in Settings > Model, then hold the bubble to dictate."
}

main "$@"
