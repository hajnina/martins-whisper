#!/usr/bin/env bash
# Publishes one APK to the rolling prerelease `latest`.
# Run by CI inside release/Dockerfile:  publish.sh <kind> <apk-dir>
#   kind     android | native   (which workflow is calling; picks asset name + notes)
#   apk-dir  directory holding the downloaded app-debug.apk
# Env (set by CI): GH_TOKEN, GH_REPO, GITHUB_SHA.   Local dry run: DRY_RUN=1
#
# This file is the extension point: edit it (and release/Dockerfile) to change what a
# release does. The workflows only checkout, download the artifact and `docker run` this.
set -euo pipefail

kind="${1:?usage: publish.sh <android|native> <apk-dir>}"
dir="${2:?usage: publish.sh <android|native> <apk-dir>}"
sha="${GITHUB_SHA:?GITHUB_SHA not set}"

case "$kind" in
  android)
    asset="martins-whisper-debug.apk"
    notes="Rolling build from the default branch at $sha. $asset ships without the engine; use martins-whisper-native-debug.apk for dictation." ;;
  native)
    asset="martins-whisper-native-debug.apk"
    notes="Rolling build from the default branch at $sha. $asset bundles the whisper.cpp engine (arm64-v8a) — the installable one." ;;
  *) echo "unknown kind: $kind" >&2; exit 2 ;;
esac

cp "$dir/app-debug.apk" "/tmp/$asset"
run() { if [ "${DRY_RUN:-}" = 1 ]; then echo "DRY: $*"; else "$@"; fi; }

# --- hooks: add checksums, extra assets, release-note sections here -------------------
# sha256sum "/tmp/$asset" > "/tmp/$asset.sha256"; extra+=("/tmp/$asset.sha256")
extra=()
# ---------------------------------------------------------------------------------------

if [ "${DRY_RUN:-}" != 1 ]; then gh auth status; fi
if [ "${DRY_RUN:-}" = 1 ] || ! gh release view latest --json tagName >/dev/null 2>&1; then
  run gh release create latest --title "Martins Whisper · latest" --prerelease --target "$sha" --notes "$notes"
fi
run gh release edit latest --prerelease --notes "$notes"
run gh release upload latest "/tmp/$asset" "${extra[@]}" --clobber
