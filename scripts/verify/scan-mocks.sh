#!/usr/bin/env bash
# V1 wall: over-mocking scanner (fail-closed). See scan-mocks.py.
set -u
base="$(cd "$(dirname "$0")/../.." && pwd)"
python3 "$(dirname "$0")/scan-mocks.py" "$base" || { echo "FAIL scan-mocks"; exit 1; }
echo "PASS scan-mocks"
