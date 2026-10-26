# TESS-2 handoff — T1 re-verification + fail-closed gate

Status: **DONE** (2026-10-26)
Branch: `agent/t1-harness` (worktree `/work/martins-whisper-worktrees/t1-harness`)
Commits: `7211eab` (gate hardening) + the docs commit containing this handoff.
Already merged: `bc182f9`, `cf92851` (T1 harness) via `425412c`; branch tip was `b7cfeaa`.
Verified master: `ba6b160` (T1+D2 wave 2). Branch is a fast-forward candidate for `master`:
`git -C /work/martins-whisper merge-tree master agent/t1-harness` printed no conflict markers.

## What TESS-2 changed

1. `scripts/test-device-ready.sh` now **fails closed**. Before, a green Gradle run was reported as
   PASS even if the results directory was empty or a required harness test had disappeared. It now
   exits non-zero on: zero result XML, zero tests, any failure, any error, a missing required test
   class (`FakeAudioPipelineE2ETest`, `DictationServiceRobolectricTest`,
   `OverlayControllerRobolectricTest`), or a missing APK. It also prints per-module counts, APK
   size and sha256.
2. Re-ran the full gate from a **clean detached worktree of current master** (`ba6b160`), not only
   from the feature branch, because T1 had been merged before the provider pause and master kept
   moving.
3. Filled the Result sections of `T1-1-fake-e2e.md`, `T1-2-instrumentation-plan.md` and
   `T1-harness.md`; no code behavior changed.

No production code was touched in this session.

## Evidence (commands actually run)

```sh
export JAVA_HOME=/work/android-toolchain/jdk
export ANDROID_HOME=/work/android-toolchain/sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME=/home/node/gh-tess        # private copy of the shared Gradle home
cd /tmp/tess-master-verify                         # git worktree add --detach ... ba6b160
scripts/test-device-ready.sh
```

Output (full log `/work/tess-t1/logs/tess2-master-gate.log`):

```
BUILD SUCCESSFUL in 7s
Layer 1 results: 25 suites, 141 tests, 0 failures, 0 errors
  core: 11 suites, 85 tests
  app : 14 suites, 56 tests
PASS Layer 1: 141 tests, 0 failures, 0 errors (required harness tests present)
PASS Layer 1: app/build/outputs/apk/debug/app-debug.apk (8939909 B, sha256 2dd15f24…)
PASS Layer 2 compile: app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk (1326409 B, sha256 f7e1dedd…)
```

Artifacts (full hashes):

```
app-debug.apk              8,939,909 B  sha256 2dd15f2426a185f2813635ce89ad42d7c106ec6f907df6b7a6ebfd2b2d692d03
app-debug-androidTest.apk  1,326,409 B  sha256 f7e1deddf0bc0f27fab655eb054728c1f1e6fbf50bcc103a5e7c5ecbfecc5603
```

No-device path (`/work/tess-t1/logs/tess2-nodevice.log`):

```sh
scripts/test-device.sh
# exit 3
# No Android device or emulator is connected; Layer 2 tests not run.
```

Branch gate log (identical counts, `/work/tess-t1/logs/tess2-branch-gate.log`) is on
`agent/t1-harness` @ `7211eab`.

## Layer 1 / Layer 2 status

- Layer 1 (merge gate, runs here): 141 tests green on master `ba6b160`, including the fake-audio →
  `FakeSpeechEngine` E2E (chunk reasons/overlap, merge, insertion caret/selection,
  markUsed/300 s idle unload) and Robolectric service/overlay smoke tests.
- Layer 2 (device-only): `:app:assembleDebugAndroidTest` builds; the five instrumentation tests
  have never executed (no `/dev/kvm`, no device). Run at H1 with `scripts/test-device.sh`; use
  `--deny-mic` for `PermissionDenialTest`. No pass is claimed for Layer 2.

## Environment notes

- `/home/node/gh-tess` was missing after the container restart; recreated with
  `cp -r /work/android-toolchain/gradle-home /home/node/gh-tess` (never touch the shared one).
- The shared Gradle home lacks `androidx.test.ext:junit:1.2.1` and
  `androidx.test.espresso:espresso-core:3.6.1`; they were fetched ONCE online into `gh-tess`, then
  the gate ran `--offline`. A fresh private home needs that one online prefetch
  (`./gradlew :app:assembleDebugAndroidTest`) before `--offline` succeeds.
- APK sha256 is not stable across rebuilds (timestamps); size and test counts are.

## Integration note for MARA

Merge `agent/t1-harness` (or cherry-pick `7211eab` + the ticket/handoff docs). No `app/` source
changes, so no new merge risk. After merge, re-run
`GRADLE_USER_HOME=/home/node/gh-tess scripts/test-device-ready.sh`.
