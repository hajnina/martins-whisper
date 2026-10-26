# T1 — JVM/Robolectric harness + compile-only instrumented plan

Status: running (TESS, 2026-10-26)
Branch: `agent/t1-harness` (worktree `/work/martins-whisper-worktrees/t1-harness`)
Scope: `app/src/test`, `app/src/androidTest`, `core/src/test`, `scripts/test-device-ready.sh`;
one documented production seam (`DictationService.audioSourceFactory`).

## Goal

Make T1 (issue 05) buildable and provable without KVM or a device:

- Layer 1 (merge gate, runs here): existing JVM/Robolectric suites stay green, plus a fake
  audio → `FakeSpeechEngine` → merge → markUsed/300 s idle-unload end-to-end test and a
  Robolectric service/overlay start–stop/state smoke test.
- Layer 2 (device-only): instrumentation tests compile via `assembleDebugAndroidTest` for
  overlay focus retention, accessibility insertion/read-back, password refusal and permission
  denial. They are not executed in this sandbox; they run at H1.

## Acceptance

```sh
export JAVA_HOME=/work/android-toolchain/jdk
export ANDROID_HOME=/work/android-toolchain/sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
GRADLE_USER_HOME=/home/node/gh-tess scripts/test-device-ready.sh
```

Pass = Layer 1 green (`:core:test`, `:app:testDebugUnitTest`) + `:app:assembleDebug` +
`:app:assembleDebugAndroidTest` all BUILD SUCCESSFUL, test counts printed, 0 failures.

Raw commands if not using the script:

```sh
./gradlew --offline -Pandroid.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/35.0.1/aapt2 \
  :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

## Result

DONE (TESS, 2026-10-26 18:00Z). Branch `agent/t1-harness`, commits `bc182f9` + `cf92851`. Gate with private `GRADLE_USER_HOME=/home/node/gh-tess`:

```
PASS Layer 1: 25 suites, 141 tests, 0 failures, 0 errors
  core: 11 suites, 85 tests
  app : 14 suites, 56 tests
PASS Layer 1: app-debug.apk (8,939,909 B, sha256 65a8a028...94d0)
PASS Layer 2 compile: app-debug-androidTest.apk (1,326,409 B, sha256 612b179f...482d)
```

Layer 2 is compiled only; execution is H1/device-only (`scripts/test-device.sh`, see
`app/src/androidTest/DEVICE-TESTS.md`). Merge vs master `def7b1c` is conflict-free
(`git merge-tree def7b1c agent/t1-harness` prints no conflict markers). Evidence and commands:
`handoffs/TESS-1/HANDOFF.md`.

### Re-verification after the provider pause (TESS-2, 2026-10-26)

T1 was integrated before the pause (`425412c`); master has since moved to `421f223`. Re-ran the
merge gate from a clean `master` worktree with the hardened runner (commit `7211eab`):

```
Layer 1 results: 25 suites, 141 tests, 0 failures, 0 errors
  core: 11 suites, 85 tests
  app : 14 suites, 56 tests
PASS Layer 1: app-debug.apk (8,939,909 B)
PASS Layer 2 compile: app-debug-androidTest.apk (1,326,409 B)
```

`scripts/test-device-ready.sh` now fails closed: zero tests, any failure/error, a missing harness
test class (FakeAudioPipelineE2ETest, DictationServiceRobolectricTest,
OverlayControllerRobolectricTest) or a missing APK exits non-zero instead of printing PASS.
`scripts/test-device.sh` without a device exits 3 with a clear message. Fresh evidence:
`handoffs/TESS-2/HANDOFF.md`.
