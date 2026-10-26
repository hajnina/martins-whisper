#!/usr/bin/env bash
# Print a pasteable, secret-free diagnostics report for Martins Whisper.
#
# Safe to attach to a bug report: device/build facts, app version, permission
# states, installed model files and free storage. It never prints transcripts,
# audio, accounts, tokens or the full package dump.
set -uo pipefail

readonly PACKAGE="com.martins.whisper"

SERIAL="${ANDROID_SERIAL:-}"

usage() {
  cat <<'EOF'
Usage: doctor.sh [options]

Print a pasteable diagnostics report for Martins Whisper (no secrets, no
transcripts, no audio). Exit codes: 0 report printed, 2 adb missing,
3 no usable device.

Options:
  --serial SERIAL  Target device serial (default: $ANDROID_SERIAL or the only device)
  -h, --help       Show this help and exit

Environment:
  ADB              Path to adb (default: adb on PATH, then $ANDROID_HOME/platform-tools/adb)
  ANDROID_SERIAL   Default device serial

The in-app Diagnostics section shows the engine state; this script adds the
device/permission/model facts and reads the persisted engine state on a
debuggable APK. Fields it cannot read are printed as "unavailable", never
guessed.
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

  local adb
  adb="$(find_adb)" || exit $?

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

  # shellcheck disable=SC2317
  q() { "${adb_args[@]}" shell "$@" 2>/dev/null | tr -d '\r'; }
  prop() { q getprop "$1" | head -1; }
  first_line() { head -1; }

  local generated model manufacturer release sdk abi abilist
  generated="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  model="$(prop ro.product.model)"
  manufacturer="$(prop ro.product.manufacturer)"
  release="$(prop ro.build.version.release)"
  sdk="$(prop ro.build.version.sdk)"
  abi="$(prop ro.product.cpu.abi)"
  abilist="$(prop ro.product.cpu.abilist)"

  local ram_mb data_free_mb
  ram_mb="$(q cat /proc/meminfo | awk '/MemTotal/ {printf "%.0f", $2/1024; exit}')"
  data_free_mb="$(q df -k /data | awk 'NR==2 {printf "%.0f", $4/1024}')"

  local installed=no version_name="" version_code="" apk_path="" first_install="" last_update=""
  local pkg_dump
  pkg_dump="$(q dumpsys package "$PACKAGE")"
  if [[ -n "$pkg_dump" ]] && ! grep -q "Unable to find package" <<<"$pkg_dump"; then
    installed=yes
    version_name="$(sed -n 's/.*versionName=\([^ ]*\).*/\1/p' <<<"$pkg_dump" | first_line)"
    version_code="$(sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' <<<"$pkg_dump" | first_line)"
    apk_path="$(q pm path "$PACKAGE" | sed -n 's/^package://p' | first_line)"
    first_install="$(sed -n 's/.*firstInstallTime=//p' <<<"$pkg_dump" | first_line)"
    last_update="$(sed -n 's/.*lastUpdateTime=//p' <<<"$pkg_dump" | first_line)"
  fi

  local mic notif overlay accessibility battery models
  if [[ "$installed" == "yes" ]]; then
    mic="$(sed -n 's/.*android.permission.RECORD_AUDIO: granted=\([a-z]*\).*/\1/p' <<<"$pkg_dump" | first_line)"
    notif="$(sed -n 's/.*android.permission.POST_NOTIFICATIONS: granted=\([a-z]*\).*/\1/p' <<<"$pkg_dump" | first_line)"
    if [[ -z "$notif" ]]; then
      if [[ "${sdk:-0}" -ge 33 ]]; then notif="unknown"; else notif="not-required (sdk<33)"; fi
    fi
    overlay="$(q appops get "$PACKAGE" SYSTEM_ALERT_WINDOW | sed -n 's/.*SYSTEM_ALERT_WINDOW: \([a-z]*\).*/\1/p' | first_line)"
    if q settings get secure enabled_accessibility_services | grep -q "$PACKAGE"; then
      accessibility="enabled"
    else
      accessibility="disabled"
    fi
    if q dumpsys deviceidle whitelist | grep -q "$PACKAGE"; then
      battery="ignored (exempt)"
    else
      battery="not ignored"
    fi
    if q run-as "$PACKAGE" true >/dev/null 2>&1; then
      models="$(q run-as "$PACKAGE" ls -l files/models 2>/dev/null | grep -v '^total ' )"
      [[ -n "$models" ]] || models="none (directory empty or missing)"
    else
      models="unavailable (non-debuggable build or app data not readable)"
    fi
  else
    mic="n/a"; notif="n/a"; overlay="n/a"; accessibility="n/a"; battery="n/a"
    models="n/a (app not installed)"
  fi

  echo "Martins Whisper doctor report"
  echo "format=1"
  echo "generated_utc=$generated"
  echo "adb=$("$adb" version | first_line)"
  echo "manufacturer=$manufacturer"
  echo "model=$model"
  echo "android_release=$release"
  echo "sdk=$sdk"
  echo "abi=$abi"
  echo "abilist=$abilist"
  echo "ram_total_mb=${ram_mb:-unavailable}"
  echo "data_free_mb=${data_free_mb:-unavailable}"
  echo "package=$PACKAGE"
  echo "app_installed=$installed"
  echo "version_name=${version_name:-unavailable}"
  echo "version_code=${version_code:-unavailable}"
  echo "apk_path=${apk_path:-unavailable}"
  echo "first_install=${first_install:-unavailable}"
  echo "last_update=${last_update:-unavailable}"
  echo "permission.RECORD_AUDIO=${mic:-unknown}"
  echo "permission.POST_NOTIFICATIONS=${notif:-unknown}"
  echo "appop.SYSTEM_ALERT_WINDOW=${overlay:-unknown}"
  echo "accessibility_service=$accessibility"
  echo "battery_optimization=$battery"
  if [[ "$installed" == "yes" && -n "$models" && "$models" != unavailable* && "$models" != n/a* ]]; then
    echo "models_dir=files/models"
    while IFS= read -r line; do
      [[ -z "$line" ]] && continue
      local name size
      name="$(awk '{print $NF}' <<<"$line")"
      size="$(awk '{print $5}' <<<"$line")"
      local sha
      sha="$(q run-as "$PACKAGE" sha256sum "files/models/$name" 2>/dev/null | awk '{print $1}' | first_line)"
      echo "model=$name bytes=${size:-unknown} sha256=${sha:-unavailable}"
    done <<<"$models"
  else
    echo "models=$models"
  fi
  local prefs_read=no prefs=""
  if [[ "$installed" == "yes" ]]; then
    prefs="$(q run-as "$PACKAGE" cat shared_prefs/martins_whisper_settings.xml 2>/dev/null)"
    [[ -n "$prefs" ]] && prefs_read=yes
  fi

  pref_string() {
    local key="$1" value
    value="$(sed -n "s:.*<string name=\"$key\">\(.*\)</string>.*:\1:p" <<<"$prefs" | first_line)"
    value="${value//&amp;/&}"
    value="${value//&lt;/<}"
    value="${value//&gt;/>}"
    value="${value//&apos;/$'\''}"
    value="${value//&quot;/\"}"
    printf '%s\n' "$value"
  }
  pref_int() {
    sed -n "s:.*<int name=\"$1\" value=\"\([-0-9]*\)\".*:\1:p" <<<"$prefs" | first_line
  }
  pref_bool() {
    sed -n "s:.*<boolean name=\"$1\" value=\"\(true\|false\)\".*:\1:p" <<<"$prefs" | first_line
  }

  local selected_model backend_setting active_backend fallback_reason gpu_failures last_error idle_unload overlay_setting boot_start
  if [[ "$installed" != "yes" ]]; then
    selected_model="n/a (app not installed)"; backend_setting="n/a"; active_backend="n/a"
    fallback_reason="n/a"; gpu_failures="n/a"; last_error="n/a"; idle_unload="n/a"
    overlay_setting="n/a"; boot_start="n/a"
  elif [[ "$prefs_read" != "yes" ]]; then
    selected_model="unavailable (non-debuggable build or app data not readable)"
    backend_setting="unavailable"; active_backend="unavailable"; fallback_reason="unavailable"
    gpu_failures="unavailable"; last_error="unavailable"; idle_unload="unavailable"
    overlay_setting="unavailable"; boot_start="unavailable"
  else
    selected_model="$(pref_string model_id)"; selected_model="${selected_model:-base (default not written yet)}"
    backend_setting="$(pref_string backend)"; backend_setting="${backend_setting:-AUTO (default not written yet)}"
    active_backend="$(pref_string engine_active_backend)"; active_backend="${active_backend:-not recorded (no engine load yet)}"
    fallback_reason="$(pref_string engine_fallback_reason)"; fallback_reason="${fallback_reason:-none}"
    gpu_failures="$(pref_int engine_gpu_failure_count)"; gpu_failures="${gpu_failures:-0 (default not written yet)}"
    last_error="$(pref_string engine_last_error)"; last_error="${last_error:-none recorded}"
    idle_unload="$(pref_int idle_unload_seconds)"; idle_unload="${idle_unload:-300 (default not written yet)}"
    overlay_setting="$(pref_bool overlay_enabled)"; overlay_setting="${overlay_setting:-false (default not written yet)}"
    boot_start="$(pref_bool boot_start)"; boot_start="${boot_start:-true (default not written yet)}"
  fi

  echo "selected_model_id=$selected_model"
  echo "backend_setting=$backend_setting"
  echo "backend=$active_backend"
  echo "fallback=$fallback_reason"
  echo "gpu_failures=$gpu_failures"
  echo "last_error=$last_error"
  echo "idle_unload_seconds=$idle_unload"
  echo "overlay_enabled=$overlay_setting"
  echo "boot_start=$boot_start"
  if [[ "$active_backend" == whisper.cpp* ]]; then
    echo "native_engine=loaded (backend above)"
  elif [[ "$last_error" == *"Native whisper.cpp is not available"* ]]; then
    echo "native_engine=missing (last_error says the APK has no native library)"
  else
    echo "native_engine=not loaded yet; check in-app Diagnostics before the first press"
  fi
  echo "end_of_report"
}

main "$@"
