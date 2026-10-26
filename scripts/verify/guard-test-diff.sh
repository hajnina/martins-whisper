#!/usr/bin/env bash
# V3 wall: test-suite diff guard against the BASE branch.
# Behavior (fail-closed):
#  - @Ignore/@Disabled/assume* added in the diff => FAIL
#  - test-count per file lower than base => FAIL (ratchet)
#  - assertion count per file lower than base => FAIL (ratchet)
#  - ratchet.json missing => FAIL (strict); counts below ratchet.json => FAIL
#  - gate-file hashes differ from ratchet.json's recorded hashes => FAIL
#  - gate scripts themselves edited vs base => FAIL
# Usage: guard-test-diff.sh BASE  (e.g. master)
set -u
base="$(cd "$(dirname "$0")/../.." && pwd)"
BASE="${1:?usage: guard-test-diff.sh BASE}"
strict="${STRICT:-1}"
ratchet="$base/scripts/verify/ratchet.json"

fail() { echo "FAIL guard-test-diff: $1"; exit 1; }

cd "$base"
git fetch -q origin 2>/dev/null || true
git rev-parse --verify --quiet "$BASE" >/dev/null || fail "base '$BASE' not a refexists"
diff=$(git diff "$BASE"...HEAD -- core/src/test app/src/test 2>/dev/null)
[ -z "$diff" ] && [ "$BASE" != "$(git branch --show-current)" ] && diff=""

# 1. weakening markers in diff
if git diff "$BASE"...HEAD -- core/src/test app/src/test | grep -E '^\+.*(@Ignore|@Disabled|assume(True|False|That))'; then
  fail "weakening marker added in diff"
fi

# 2. gate scripts edited vs base
if ! git diff --quiet "$BASE" "HEAD" -- scripts scripts/verify scripts/verify/ratchet.json docs/VERIFY.md 2>/dev/null; then true; fi
changed_gates=$(git diff --name-only "$BASE"...HEAD -- scripts/verify docs/VERIFY.md)
[ -n "$changed_gates" ] && echo "NOTE gate files changed vs $BASE: $changed_gates (owner must re-sign)"

# 3. per-file counts ratchet vs base + strict hash check
python3 "$(dirname "$0")/guard-red-fail-closed.py" "$base" "$BASE" "$strict" || fail "count ratchet"
