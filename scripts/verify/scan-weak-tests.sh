#!/usr/bin/env bash
# V1 wall: weak/tautological test scanner (fail-closed).
# Detects: assertTrue(true); assertEquals(x, x); zero-assertion @Test blocks;
# @Ignore/@Disabled/assume*; swallowed catch(_: X) with no assertion after it.
# Allowlist: scripts/verify/scan-allowlist.txt (one entry per line: "type:path").
set -u
base="$(cd "$(dirname "$0")/../.." && pwd)"
if ! python3 "$(dirname "$0")/scan-weak-tests.py" "$base"; then
  echo "FAIL scan-weak-tests"
  exit 1
fi
echo "PASS scan-weak-tests"
