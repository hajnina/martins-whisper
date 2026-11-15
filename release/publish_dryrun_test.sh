#!/usr/bin/env bash
# DRY_RUN tests for publish.sh: the APK must be found in flat (android.yml) and nested
# (upload-artifact@v4) artifact layouts, an empty dir must fail clearly, and no gh call
# may run. A fake gh on PATH logs invocations and exits 1, so any gh call fails the run.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work="$(mktemp -d)"
log="$work/gh-calls.log"
trap 'rm -rf "$work"; rm -f /tmp/martins-whisper-debug.apk /tmp/martins-whisper-native-debug.apk' EXIT

mkdir "$work/bin"
cat > "$work/bin/gh" <<'EOF'
#!/usr/bin/env bash
printf 'gh invoked: %s\n' "$*" >> "${GH_CALL_LOG:?GH_CALL_LOG not set}"
exit 1
EOF
chmod +x "$work/bin/gh"

unset GH_TOKEN GH_REPO
cd "$repo_root"

# run_publish <kind> <apk-dir> <asset> <expected-apk-source>
run_publish() {
  local kind="$1" dir="$2" asset="$3" src="$4" out
  rm -f "/tmp/$asset" "$log"
  out="$(PATH="$work/bin:$PATH" DRY_RUN=1 GITHUB_SHA=fakesha GH_CALL_LOG="$log" \
    bash release/publish.sh "$kind" "$dir" 2>&1)"
  cmp -s "$src" "/tmp/$asset" \
    || { echo "FAIL [$kind]: /tmp/$asset missing or wrong content" >&2; exit 1; }
  grep -q "gh release upload latest /tmp/$asset" <<<"$out" \
    || { echo "FAIL [$kind]: DRY output missing upload of /tmp/$asset" >&2; printf '%s\n' "$out" >&2; exit 1; }
  if [ -s "$log" ]; then echo "FAIL [$kind]: gh was called:" >&2; cat "$log" >&2; exit 1; fi
  echo "ok [$kind]: $src -> /tmp/$asset, no gh calls"
}

# a) flat layout: android.yml uploads two files from one dir, so the artifact root is it.
flat="$work/flat"; mkdir "$flat"; echo flat-apk > "$flat/app-debug.apk"
run_publish android "$flat" martins-whisper-debug.apk "$flat/app-debug.apk"

# b) nested layout: native-build.yml uploads 3 files whose LCA is the repo root.
nested="$work/apk"
mkdir -p "$nested/app/build/outputs/apk/debug" "$nested/build"
echo nested-apk > "$nested/app/build/outputs/apk/debug/app-debug.apk"
echo '{}' > "$nested/app/build/outputs/apk/debug/output-metadata.json"
echo report > "$nested/build/native-apk-report.txt"
run_publish native "$nested" martins-whisper-native-debug.apk \
  "$nested/app/build/outputs/apk/debug/app-debug.apk"

# c) empty dir: non-zero exit with a clear error.
empty="$work/empty"; mkdir "$empty"; rm -f "$log"
if out="$(PATH="$work/bin:$PATH" DRY_RUN=1 GITHUB_SHA=fakesha GH_CALL_LOG="$log" \
  bash release/publish.sh native "$empty" 2>&1)"; then
  echo "FAIL [empty]: exited 0, expected non-zero" >&2; exit 1
fi
grep -q "app-debug.apk not found" <<<"$out" \
  || { echo "FAIL [empty]: unclear error: $out" >&2; exit 1; }
if [ -s "$log" ]; then echo "FAIL [empty]: gh was called:" >&2; cat "$log" >&2; exit 1; fi
echo "ok [empty]: non-zero exit, clear error, no gh calls"

echo "all publish.sh DRY_RUN tests passed"
