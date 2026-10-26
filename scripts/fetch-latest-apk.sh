#!/usr/bin/env bash
# Fetch the newest successful native build from GitHub Actions for a fast flash loop.
#
# The workflow (native-build.yml) uploads `martins-whisper-native-debug-<sha>` containing
# app/build/outputs/apk/debug/app-debug.apk. This script downloads that artifact, copies the APK
# to a stable path, sanity-checks that the arm64 whisper/JNI libraries are inside, and prints the
# adb install command.
#
# Requires an authenticated `gh` on the machine that owns the GitHub credentials (this sandbox
# has none):  gh auth login  (or GH_TOKEN in the environment).
#
# Usage: scripts/fetch-latest-apk.sh [options]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

readonly DEFAULT_REPO="hajnina/martins-whisper"
readonly DEFAULT_WORKFLOW="native-build.yml"
readonly DEFAULT_OUT="dist/latest-apk"
readonly APK_NAME="martins-whisper-latest.apk"

REPO="${GH_REPO:-}"
WORKFLOW="$DEFAULT_WORKFLOW"
BRANCH="master"
ANY_BRANCH=0
RUN_ID=""
OUT_DIR="$DEFAULT_OUT"
ALLOW_FAILED=0
VERIFY=1
FULL_VERIFY=0

usage() {
  cat <<'EOF'
Usage: fetch-latest-apk.sh [options]

Download the newest native-build artifact and put the APK at <out>/martins-whisper-latest.apk.

Options:
  --repo OWNER/REPO    GitHub repository (default: origin remote, then hajnina/martins-whisper)
  --workflow NAME|ID   Workflow to query (default: native-build.yml)
  --branch NAME        Branch to query (default: master)
  --any-branch         Do not filter by branch
  --run-id ID          Use one specific workflow run instead of the newest
  --out DIR            Output directory (default: dist/latest-apk; gitignored)
  --allow-failed       Accept the newest completed run even if it is red/aborted
  --verify             Also run scripts/native-apk-check.sh on the downloaded APK
  --no-verify          Skip the built-in arm64/JNI zip sanity check
  -h, --help           Show this help and exit

Environment:
  GH_REPO              Default repository when --repo is not given
  GH_TOKEN / GITHUB_TOKEN  Credentials understood by gh (never printed by this script)

Examples:
  scripts/fetch-latest-apk.sh
  scripts/fetch-latest-apk.sh --any-branch --run-id 40380372902
  scripts/fetch-latest-apk.sh --out /tmp/mw-apk && adb install -r /tmp/mw-apk/martins-whisper-latest.apk
EOF
}

die() {
  echo "error: $1" >&2
  exit "${2:-1}"
}

have() { command -v "$1" >/dev/null 2>&1; }

parse_args() {
  while (($#)); do
    case "$1" in
      --repo) [[ $# -ge 2 ]] || die "--repo needs OWNER/REPO"; REPO="$2"; shift 2 ;;
      --workflow) [[ $# -ge 2 ]] || die "--workflow needs a name or id"; WORKFLOW="$2"; shift 2 ;;
      --branch) [[ $# -ge 2 ]] || die "--branch needs a value"; BRANCH="$2"; shift 2 ;;
      --any-branch) ANY_BRANCH=1; shift ;;
      --run-id) [[ $# -ge 2 ]] || die "--run-id needs a value"; RUN_ID="$2"; shift 2 ;;
      --out) [[ $# -ge 2 ]] || die "--out needs a directory"; OUT_DIR="$2"; shift 2 ;;
      --allow-failed) ALLOW_FAILED=1; shift ;;
      --verify) FULL_VERIFY=1; shift ;;
      --no-verify) VERIFY=0; shift ;;
      -h|--help) usage; exit 0 ;;
      *) die "unknown argument: $1 (try --help)" 2 ;;
    esac
  done
  if ((ANY_BRANCH)); then
    BRANCH=""
  fi
}

detect_repo() {
  [[ -n "$REPO" ]] && return
  if git rev-parse --git-dir >/dev/null 2>&1; then
    local remote
    remote="$(git config --get remote.origin.url 2>/dev/null || true)"
    if [[ -n "$remote" ]]; then
      REPO="$(printf '%s' "$remote" |
        sed -e 's#^git@github\.com:##' -e 's#^ssh://git@github\.com/##' \
            -e 's#^https://github\.com/##' -e 's#\.git$##')"
    fi
  fi
  if [[ -z "$REPO" ]]; then
    REPO="$DEFAULT_REPO"
    echo "note: no origin remote; using $REPO" >&2
  fi
  [[ "$REPO" == */* ]] || die "cannot determine OWNER/REPO (got '$REPO'); pass --repo" 2
}

require_gh() {
  have gh || die "gh is not installed. Install it (e.g. 'nix profile install nixpkgs#gh' or https://cli.github.com) and run 'gh auth login'." 2
  if ! gh auth status >/dev/null 2>&1; then
    die "gh is not authenticated. Run 'gh auth login' (or export GH_TOKEN) on the machine that holds the GitHub credentials." 3
  fi
}

run_metadata() {
  if [[ -n "$RUN_ID" ]]; then
    gh run view "$RUN_ID" \
      --json databaseId,headSha,headBranch,createdAt,conclusion,url \
      --jq '[.databaseId,.headSha,.headBranch,.createdAt,.conclusion,.url] | @tsv'
    return
  fi

  local args=(run list --workflow="$WORKFLOW" --limit 1)
  if ((ALLOW_FAILED)); then
    args+=(--status completed)
  else
    args+=(--status success)
  fi
  [[ -n "$BRANCH" ]] && args+=(--branch "$BRANCH")
  args+=(--json databaseId,headSha,headBranch,createdAt,conclusion,url
         --jq '.[0] | [.databaseId,.headSha,.headBranch,.createdAt,.conclusion,.url] | @tsv')
  gh "${args[@]}"
}

download_run() {
  local run_id="$1" tmp="$2"
  mkdir -p "$tmp"
  gh run download "$run_id" --dir "$tmp"
}

sanity_check_apk() {
  local apk="$1"
  [[ -s "$apk" ]] || die "downloaded APK is empty: $apk"

  if ((VERIFY)); then
    if have unzip; then
      local entries
      entries="$(unzip -Z1 "$apk" 2>/dev/null || true)"
      for required in \
        "lib/arm64-v8a/libmartins_whisper.so" \
        "lib/arm64-v8a/libwhisper.so" \
        "lib/arm64-v8a/libggml.so"; do
        if ! printf '%s\n' "$entries" | grep -Fqx "$required"; then
          die "APK is missing $required (not a native arm64 build?)" 4
        fi
      done
      if printf '%s\n' "$entries" | grep -q "lib/x86"; then
        echo "warning: APK contains x86 libraries (unexpected for the arm64-only build)" >&2
      fi
    else
      echo "warning: unzip not found; skipping the arm64 .so sanity check" >&2
    fi
  fi

  if ((FULL_VERIFY)); then
    [[ -x "$ROOT/scripts/native-apk-check.sh" ]] ||
      die "scripts/native-apk-check.sh not found or not executable" 4
    "$ROOT/scripts/native-apk-check.sh" "$apk"
  fi
}

main() {
  parse_args "$@"
  detect_repo
  export GH_REPO="$REPO"
  require_gh

  local row
  row="$(run_metadata)"
  [[ -n "$row" ]] || {
    if ((ALLOW_FAILED)); then
      die "no completed '$WORKFLOW' run found for ${BRANCH:-any branch} in $REPO" 4
    fi
    die "no successful '$WORKFLOW' run found for ${BRANCH:-any branch} in $REPO (try --allow-failed or --any-branch)" 4
  }

  local run_id sha branch created conclusion url
  IFS=$'\t' read -r run_id sha branch created conclusion url <<<"$row"
  [[ -n "$run_id" ]] || die "could not parse the workflow run id" 4
  if [[ "$conclusion" != "success" ]]; then
    echo "warning: newest run $run_id concluded '$conclusion' (only use --allow-failed when you know why)" >&2
  fi

  local tmp
  tmp="$(mktemp -d "${TMPDIR:-/tmp}/mw-fetch-apk.XXXXXX")"
  # shellcheck disable=SC2064
  trap "rm -rf '$tmp'" EXIT

  echo "Repo:     $REPO"
  echo "Workflow: $WORKFLOW"
  echo "Run:      $run_id ($branch @ ${sha:0:12}, $created, $conclusion)"
  [[ -n "$url" ]] && echo "Run URL:  $url"
  echo "Downloading artifact(s) ..."
  download_run "$run_id" "$tmp"

  local apk
  apk="$(find "$tmp" -type f -name 'app-debug.apk' -print -quit)"
  [[ -n "$apk" ]] || die "run $run_id has no app-debug.apk artifact (was the build cancelled before upload?)" 4

  sanity_check_apk "$apk"

  mkdir -p "$OUT_DIR"
  local out_apk="$OUT_DIR/$APK_NAME"
  cp -f "$apk" "$out_apk"

  local sha256 size
  sha256="$( { sha256sum "$out_apk" 2>/dev/null || shasum -a 256 "$out_apk"; } | awk '{print $1}')"
  size="$(wc -c <"$out_apk" | tr -d ' ')"

  cat >"$OUT_DIR/latest-apk.txt" <<EOF
run_id=$run_id
sha=$sha
branch=$branch
created=$created
conclusion=$conclusion
url=$url
apk=$out_apk
sha256=$sha256
bytes=$size
EOF

  echo
  echo "APK:    $out_apk"
  echo "SHA256: $sha256"
  echo "Size:   $size bytes"
  echo "Meta:   $OUT_DIR/latest-apk.txt"
  echo
  echo "Install (device on USB/wireless adb):"
  echo "  adb install -r \"$out_apk\""
  echo "or with the repo helper:"
  echo "  source scripts/env.sh && scripts/install-apk.sh --apk \"$out_apk\""
}

main "$@"
