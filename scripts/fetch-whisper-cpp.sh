#!/usr/bin/env bash
# Fetches the pinned whisper.cpp checkout used by app/src/main/cpp/CMakeLists.txt.
set -euo pipefail

REF="${WHISPER_CPP_REF:-231638a0717b7822784297e5980965ffcec05810}"
REPO="${WHISPER_CPP_REPO:-https://github.com/ggml-org/whisper.cpp.git}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEST="${1:-${SCRIPT_DIR}/../app/src/main/cpp/whisper.cpp}"

if [[ -e "$DEST" && ! -d "$DEST/.git" ]]; then
    echo "error: $DEST exists but is not a git checkout; move it away first" >&2
    exit 1
fi

mkdir -p "$DEST"
git -C "$DEST" init -q
git -C "$DEST" remote remove origin >/dev/null 2>&1 || true
git -C "$DEST" remote add origin "$REPO"
git -C "$DEST" fetch --depth 1 origin "$REF"
git -C "$DEST" checkout -q --detach FETCH_HEAD

ACTUAL="$(git -C "$DEST" rev-parse HEAD)"
if [[ "$ACTUAL" != "$REF" ]]; then
    echo "error: expected $REF but checked out $ACTUAL" >&2
    exit 1
fi

echo "whisper.cpp $ACTUAL ready at $DEST"
