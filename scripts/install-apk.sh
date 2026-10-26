#!/usr/bin/env bash
# Install the Martins Whisper debug APK on a connected Android device.
#
# This is an operator script: it uses adb and never needs root. It does not
# download anything and does not modify the device beyond installing the APK.
set -euo pipefail

readonly PACKAGE="com.martins.whisper"
readonly DEFAULT_APK="app/build/outputs/apk/debug/app-debug.apk"

APK="$DEFAULT_APK"
SERIAL="${ANDROID_SERIAL:-}"

usage() {
  cat <<'EOF'
Usage: install-apk.sh [options]

Install the Martins Whisper APK with adb install -r (keeps app data).

Options:
  --apk PATH       APK to install (default: app/build/outputs/apk/debug/app-debug.apk)
  --serial SERIAL  Target device serial (default: $ANDROID_SERIAL or the only device)
  -h, --help       Show this help and exit

Environment:
  ADB              Path to adb (default: adb on PATH, then $ANDROID_HOME/platform-tools/adb)
  ANDROID_SERIAL   Default device serial

The script exits non-zero with a clear message when adb or a device is missing.
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

parse_args() {
  while (($#)); do
    case "$1" in
      --apk)
        [[ $# -ge 2 ]] || die "--apk needs a path"
        APK="$2"
        shift 2
        ;;
      --serial)
        [[ $# -ge 2 ]] || die "--serial needs a value"
        SERIAL="$2"
        shift 2
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      *)
        die "unknown argument: $1 (try --help)"
        ;;
    esac
  done
}

main() {
  parse_args "$@"
  [[ -f "$APK" ]] || die "APK not found: $APK (build it with ./gradlew :app:assembleDebug)"

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

  echo "APK:    $APK"
  if command -v sha256sum >/dev/null 2>&1; then
    echo "SHA256: $(sha256sum "$APK" | awk '{print $1}')"
  fi
  echo "Target: ${SERIAL:-<only connected device>}"
  echo "Installing $PACKAGE ..."
  "${adb_args[@]}" install -r "$APK"
  echo "Installed. Open the app and grant the permissions listed in docs/PERMISSIONS.md."
}

main "$@"
