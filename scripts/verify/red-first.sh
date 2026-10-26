#!/usr/bin/env bash
# V3 wall: red-first. New/changed tests must FAIL against BASE sources (they bite)
# and pass on HEAD (they're about the current code).   Usage: red-first.sh BASE
set -u
base="$(cd "$(dirname "$0")/../.." && pwd)"
BASE="${1:?usage: red-first.sh BASE}"
wt="/tmp/red-first-wt"
fail() { echo "FAIL red-first: $1"; exit 1; }

cd "$base"
cdmain=$(pwd)
tdiff=$(git diff --name-only "$BASE"...HEAD -- core/src/test app/src/test)
if [ -z "$tdiff" ]; then
  echo "PASS red-first: no new/changed tests vs $BASE"
  exit 0
fi
echo "new/changed tests vs $BASE: $(echo "$tdiff" | wc -l) file(s)"

git worktree add --detach "$wt" "$BASE" >/dev/null 2>&1 || fail "cannot make worktree at $BASE"
trap 'git worktree remove --force "$wt" >/dev/null 2>&1' EXIT

# graft current test sources + build glue onto the BASE worktree
rm -rf "$wt/core/src/test" "$wt/app/src/test"
cp -r core/src/test "$wt"/core/src/test
mkdir -p "$wt/app/src/test"
cp -r app/src/test/. "$wt"/app/src/test/
[ -f local.properties ] && cp local.properties "$wt"/
mkdir -p "$wt/scripts/verify"; cp scripts/verify/scan-allowlist.txt "$wt"/scripts/verify/ 2>/dev/null || true

export JAVA_HOME=/work/android-toolchain/jdk
export ANDROID_HOME=/work/android-toolchain/sdk
export ANDROID_SDK_ROOT=/work/android-toolchain/sdk
export GRADLE_USER_HOME=/work/android-toolchain/gradle-home
export PATH="$JAVA_HOME/bin:/work/android-toolchain/gradle/gradle-8.9/bin:$PATH"
AAPT="-Pandroid.aapt2FromMavenOverride=/work/android-toolchain/sdk/build-tools/35.0.1/aapt2"

cd "$wt"
if gradle --console=plain $AAPT :core:test :app:testDebugUnitTest >/tmp/red-first-run.log 2>&1; then
  echo "FAIL red-first: new/changed tests are GREEN against $BASE sources: they do not bite"
  grep -h 'testcase' core/build/test-results/test/*.xml 2>/dev/null | wc -l | sed 's/^/base-results: /'
  exit 1
fi
if grep -q 'BUILD FAILED' /tmp/red-first-run.log && grep -Eq 'COMPILATION ERROR|Task.*(compileTestKotlin) FAILED' /tmp/red-first-run.log; then
  echo "NOTE red-first: base does not compile new tests (signature drift) - counts as red"
  echo "PASS red-first"
  exit 0
fi
echo "NOTE red-first under $BASE was red: $(grep -c 'FAILED' /tmp/red-first-run.log) failing task(s)"
echo "PASS red-first"
