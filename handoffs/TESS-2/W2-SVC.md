# W2 / T2-svc handoff — Robolectric service + overlay state-machine tests

Status: **DONE** (2026-10-26)
Branch: `agent/t2-svc` (worktree `/work/martins-whisper-worktrees/t2-svc`)
Tests commit: `bcc409f` (this ticket + handoff commit follows on the same branch)
Base: **`agent/g2-engine` @ `5494b79`** — G2 is not merged yet. This branch must merge **after**
G2; TESS/MARA owns integration order. Do not merge `agent/*` from here.

Executive summary:
- Added 6 Robolectric tests in 2 new app-test suites for the G2 "record while loading" / lazy
  idle-unload path and for the overlay state machine. No production code changed.
- Acceptance `:app:testDebugUnitTest :app:assembleDebug` is BUILD SUCCESSFUL: app tests went from
  **15 suites / 66 tests / 0 failures** to **17 suites / 72 tests / 0 failures / 0 errors**.
- Existing seams (`EngineFactory.factory`, `DictationService.inserterFactory`,
  `DictationService.audioSourceFactory`) were sufficient; no new dependency.

## Files added

- `app/src/test/java/com/martins/whisper/harness/DictationServiceLifecycleRobolectricTest.kt`
- `app/src/test/java/com/martins/whisper/harness/OverlayStateMachineRobolectricTest.kt`

Ticket updated: `.scratch/device-ready-apk/build-tickets/T2-svc.md` (Status done + Result).

## Scenarios covered

Service (`@Config(sdk=[34])`, `@ConscryptMode(OFF)`, factories saved/restored in `@After`):

1. **Record while the model loads.** `ManualAudioSource` stores `onFrame`; the fake
   `EngineFactory.factory` blocks on `CountDownLatch` inside `createEngine`. Press => audio starts
   and state is `Loading`; 10 frames (distinct per-frame sample values) are emitted while the
   loader is still blocked. The loader is released, the service reaches `Recording`, then release
   inserts the transcript exactly once in `Success`. The fake engine's received chunk has 3200
   samples / 200 ms and every sample appears in frame order (100, 200, ... 1000).
2. **Idle unload + lazy reload.** After a successful session,
   `ShadowLooper.idleFor(301 s)` fires the 15 s watchdog; the first engine is closed, state is
   `Idle`, the next press calls the factory again (2 engines total) and the second transcript is
   inserted in `Success`.
3. **Cancel while loading.** Press, wait for the loader latch, emit frames, invoke `cancelSession`
   while the loader is still blocked. It stays `Idle`, inserts nothing, then the load completes:
   the engine is **not closed** (current behaviour), and a follow-up press reuses the same engine
   (factory called once) and inserts successfully.
4. **Model switch.** First session on `base`; set `SettingsStore(app).modelId = "tiny"`; second
   press. The previous engine is closed, a new engine is created, and both sessions insert once.

Overlay (`ShadowWindowManagerImpl`, no window-attachment assumptions):

5. `show()` then `Idle -> Loading -> Recording -> Processing -> Success -> Error -> Idle`;
   each step asserts `MicBubbleView.state` and `contentDescription`. `controller.isShowing` is
   compared with the real `isAttachedToWindow` flag rather than assumed true; `hide()` removes
   the registered view.
6. `show()` + every state update (including duplicate `Idle`) does not throw and leaves exactly
   one registered `MicBubbleView` (same instance) throughout; `hide()` clears it.

## Acceptance evidence (actually run)

```sh
cd /work/martins-whisper-worktrees/t2-svc
export JAVA_HOME=/work/android-toolchain/jdk
export ANDROID_HOME=/work/android-toolchain/sdk ANDROID_SDK_ROOT=$ANDROID_HOME
export GRADLE_USER_HOME=/home/node/gh-t2-svc
./gradlew --offline --console=plain :app:testDebugUnitTest :app:assembleDebug
# BUILD SUCCESSFUL in 7s; EXIT=0
```

Per-suite counts from `app/build/test-results/testDebugUnitTest/TEST-*.xml`:

- Before (same base, before edits): **15 suites / 66 tests / 0 failures / 0 errors**.
- After: **17 suites / 72 tests / 0 failures / 0 errors** (skipped 0).
- New suites: `DictationServiceLifecycleRobolectricTest` 4 tests,
  `OverlayStateMachineRobolectricTest` 2 tests.
- The new suites were also run 3x with `--rerun-tasks` to check flakiness: 3/3 green.

For reference, the prompt's master pre-G2 baseline was 14 suites / 56 tests; G2 itself (host
tests etc.) accounts for the branch baseline of 15/66 observed here.

## Findings / notes

- **No production seam missing.** `DictationService` starts `audioSource.start(...)` before
  `prepare`/`load`, so frames emitted during a blocked load accumulate in `AudioCaptureBuffer`;
  the fake engine receives them after the latch opens. Behaviour matches G2.
- **Cancel semantics** (asserted as-is, not changed): `cancelSession()` clears the buffer and
  returns to `Idle`, but the in-flight background load still completes. `ModelManager` ends in
  `READY` with the engine alive, so the next press reuses it and the factory is not called again.
- **Idle unload** uses `settings.idleUnloadSeconds.coerceAtLeast(30) * 1000` (default 300 000 ms)
  with `ModelManager.WATCHDOG_INTERVAL_MS = 15 000`; Robolectric virtual time via `idleFor`
  drives it without sleeping.
- **Overlay initial description:** `MicBubbleView.contentDescription` is null immediately after
  construction because Kotlin's property initializer bypasses the custom `state` setter. The
  service calls `overlay.update(state.value)` right after `show`; the test does the same before
  asserting `"Hold to dictate"`.
- `isShowing` is `view?.isAttachedToWindow == true`. Robolectric does not attach overlay windows,
  so the test asserts consistency with `isAttachedToWindow` and only asserts registered views,
  never `isShowing == true`.
- No `app/src/main` / `core` / `scripts` edits, no new dependencies, no hard-coded waits beyond
  bounded latches (2-5 s) and the existing `await` polling pattern.

## Next steps

- Do not merge before G2. After `agent/g2-engine` lands, merge/rebase this branch (expected to be
  a test-only, conflict-free addition) and run the same acceptance command.
- Optional hardening (not required by the ticket): assert the buffer-overflow abort path, which
  would need a smaller `maxFrames` than the service currently constructs; that is a production
  seam and was intentionally not added.

## References

- Existing tests reused as patterns: `app/src/test/java/com/martins/whisper/harness/
  DictationServiceRobolectricTest.kt`, `OverlayControllerRobolectricTest.kt`;
  `app/src/test/java/com/martins/whisper/service/DictationEngineHostTest.kt` (host layer, not
  duplicated).
- Production read for behaviour: `DictationService.kt`, `DictationEngineHost.kt`,
  `AudioCaptureBuffer.kt`, `ModelManager.kt`, `EngineFactory.kt`, `OverlayController.kt`,
  `MicBubbleView.kt`, `SettingsStore.kt`.
