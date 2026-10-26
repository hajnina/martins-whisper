#!/usr/bin/env bash
# V1 wall: oracle==implementation scanner (fail-closed). See scan-oracle-copy.py.
set -u
base="$(cd "$(dirname "$0")/../.." && pwd)"
python3 "$(dirname "$0")/scan-oracle-copy.py" "$base" || { echo "FAIL scan-oracle-copy"; exit 1; }
echo "PASS scan-oracle-copy"
