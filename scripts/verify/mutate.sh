#!/usr/bin/env bash
# V2 wall: hand-rolled mutation runner over :core logic (PIT unavailable offline).
# Applies each mutant to the source, runs :core:test, records killed/survived, restores.
# Output: scripts/verify/mutation-report.txt ; exit 1 if score below floor or survivors exist.
set -u
base="$(cd "$(dirname "$0")/../.." && pwd)"
out="$base/scripts/verify/mutation-report.txt"
: > "$out"
python3 "$(dirname "$0")/mutate.py" "$base" 2>&1 | tee -a "$out"
rc="${PIPESTATUS[0]}"
echo "score and survivors in $out"
exit "$rc"
