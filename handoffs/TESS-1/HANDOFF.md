# TESS-1 handoff — T1 JVM/Robolectric harness + compile-only device tests

Status: **DONE** (2026-10-26 18:00Z)
Branch: `agent/t1-harness` (worktree `/work/martins-whisper-worktrees/t1-harness`)
Commits: `bc182f9` (harness), `cf92851` (harden device tests + runner + docs)
Base: `fcb8299` (G1 merge); current master at handoff time: `def7b1c` (D1/G1 integration wave 1)
Integration: **conflict-free** — `git merge-tree def7b1c agent/t1-harness` prints no conflict markers.
Do not merge `agent/*`; MARA fast-forwards/merges after review.

## What changed

Production (1 seam, 3 hunks, `app/src/main/java/com/martins/whisper/service/DictationService.kt`):
- `private lateinit var audioSource: AudioSource`, assigned in `onCreate` from
  `@Volatile internal var audioSourceFactory: (Context) -> AudioSource = { AudioRecordSource(it) }`.
  Lets Robolectric inject `FakeAudioSource`; default behavior unchanged.
- Existing `EngineFactory.factory` and `DictationService.inserterFactory` seams are used for fakes.

Layer 1 (JVM/Robolectric, runs in the sandbox):
- `app/src/test/kotlin/com/martins/whisper/harness/FakeAudioPipelineE2ETest.kt`
  synthesized PCM → `EnergyVad`/`AudioChunker` → `FakeSpeechEngine` → `TranscriptMerger` →
  `TextInsertionPlanner` → `ModelManager` markUsed/300 s idle unload; asserts chunk reasons
  `[SILENCE, MAX_LENGTH, SILENCE]`, 250 ms overlap, merged text, caret after insert and replace.
- `app/src/test/java/com/martins/whisper/harness/DictationServiceRobolectricTest.kt`
  press → record → transcribe → insert → idle unload with fake mic/engine/inserter; saves and
  restores all static factories in `@After`.
- `app/src/test/java/com/martins/whisper/harness/OverlayControllerRobolectricTest.kt`
  show/state updates/drag/persist/hide through `ShadowWindowManagerImpl`; permission refusal.

Layer 2 (compiled here, executed on hardware at H1):
- `OverlayFocusRetentionTest` — non-focusable bubble does not steal focus (needs overlay app-op).
- `AccessibilityInsertionReadBackTest` — inserted text + caret visible via `AccessibilityNodeInfo`.
- `PasswordFieldRefusalTest` — password field routes clipboard-only, text untouched.
- `PermissionDenialTest` — mic denied => `PermissionGate` reports it and `AudioRecordSource`
  fails closed. Requires the permission to be pre-revoked (`scripts/test-device.sh --deny-mic`),
  because revoking a runtime permission kills the process; the test skips when mic is granted.
- `ServiceLifecycleTest` — starts foreground service from `MainActivity` without mic type,
  publishes `Idle`, stops on `ACTION_STOP`.
- `app/src/androidTest/DEVICE-TESTS.md` — per-test proof, run instructions, manual fallbacks.

Scripts:
- `scripts/test-device-ready.sh` — Layer 1 + Layer 2 **compile** gate; refuses unset/shared
  `GRADLE_USER_HOME`; uses `--offline --rerun-tasks`; prints suite/test/failure counts.
- `scripts/test-device.sh` — H1 runner: builds/installs both APKs, best-effort grants, runs
  `am instrument`, `--deny-mic` branch. Exits 3 with a clear message when `adb devices` is empty.

Ticket: `.scratch/device-ready-apk/build-tickets/T1-harness.md` (status DONE, evidence inline).

## Evidence (commands actually run)

```sh
export JAVA_HOME=/work/android-toolchain/jdk
export ANDROID_HOME=/work/android-toolchain/sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME=/home/node/gh-tess
cd /work/martins-whisper-worktrees/t1-harness
scripts/test-device-ready.sh
```

Output (full log `/tmp/t1-gate-final3.log`):

```
BUILD SUCCESSFUL in 7s
PASS Layer 1: 25 suites, 141 tests, 0 failures, 0 errors
PASS Layer 1: app/build/outputs/apk/debug/app-debug.apk
PASS Layer 2 compile: app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

Per module (from `build/test-results`): core 11 suites / 85 tests; app 14 suites / 56 tests.

Artifacts:

```
app-debug.apk              8,939,909 B  sha256 65a8a028431a27e2557036d63ab0296dcecf20dfba2b96f8730a58f2c78194d0
app-debug-androidTest.apk  1,326,409 B  sha256 612b179fbb270c42993254b57c8667e96cc216caffeb944603917998bb80482d
```

Merge check:

```sh
git -C /work/martins-whisper merge-tree $(git -C /work/martins-whisper rev-parse master) cf92851
# -> single tree/blob line, no conflict markers
```

## Environment notes (reproducibility)

- Use a **private** Gradle home: `/home/node/gh-tess`. Never the shared
  `/work/android-toolchain/gradle-home` (other containers corrupt/race it). The gate script
  enforces this.
- aarch64 Robolectric loads `conscrypt_openjdk_jni-linux-aarch_64` and fails; both Robolectric
  tests use `@ConscryptMode(ConscryptMode.Mode.OFF)`. `android-all-instrumented` jars live in
  `/home/node/.m2` (outside GRADLE_USER_HOME). First run needs network for
  `androidx.test.ext:junit`, espresso and conscrypt; they are now prefetched in gh-tess, so
  `--offline` works.
- Under Robolectric the overlay view is never attached to a window, so
  `OverlayController.isShowing` stays false. Assert the WM shadow instead:
  `(shadowOf(wm) as ShadowWindowManagerImpl).views.filterIsInstance<MicBubbleView>()`.
- `--rerun-tasks` is deliberate. Without it, a stale/foreign test-result XML from another session
  in the shared build cache can make the gate report a failure (or a pass) that does not match
  the checked-out sources. Re-verify with fresh results before trusting counts.
- Concurrent sessions wrote into this worktree while TESS worked (a transient foreign
  `FakeAudioEndToEndTest.kt`, then `PermissionDenialTest` hardening, `ServiceLifecycleTest`,
  `DEVICE-TESTS.md`, `test-device.sh`). All such changes that pass the gate are included in
  `cf92851`. Branch tip is authoritative; if another session keeps editing, compare its changes
  against `cf92851` and re-run the gate before merging.

## Known limitations / next

- Layer 2 has never executed (no KVM/emulator/device here). At H1: connect hardware, run
  `GRADLE_USER_HOME=/home/node/gh-tess scripts/test-device.sh`; expect `PermissionDenialTest`
  skipped unless run with `--deny-mic`. Cross-app accessibility injection is manual (the service
  refuses its own package by design).
- Real model inference is out of scope (native build + GGML model push; D-series).
- No GitHub credentials in TESS container; MARA/owner pushes.
