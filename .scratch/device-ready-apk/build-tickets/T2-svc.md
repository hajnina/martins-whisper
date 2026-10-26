# T2-svc — Robolectric service + overlay state-machine tests

Owner: W2 (branch `agent/t2-svc`, worktree `/work/martins-whisper-worktrees/t2-svc`)
Status: done (W2, 2026-10-26)
Base: `agent/g2-engine` @ 5494b79 (G2 not merged yet; rebase/merge after MARA lands G2)
Scope: `app/src/test/**` only, plus the documented test seams. No production behaviour changes.

## Goal

1. Robolectric service test for the G2 path in `DictationService`/`DictationEngineHost`:
   - record while the model loads: frames captured during a blocked load are buffered in order and
     the transcript after release contains the first syllables (fake audio source that emits on
     demand, fake `SpeechEngine` whose factory blocks on a latch);
   - idle unload after 300 s and lazy reload on the next press (advance the Robolectric clock /
     `SystemClock.elapsedRealtime`; the service watchdog is 15 s);
   - model switch closes the previous engine; cancel while loading does not insert.
   Keep the existing static-factory save/restore pattern (`EngineFactory.factory`,
   `DictationService.inserterFactory`, `DictationService.audioSourceFactory`) and restore in `@After`.
2. Overlay state-machine test: drive `OverlayController` through
   idle -> loading/listening -> processing/transcribing -> success/inserting -> error -> idle and
   assert the bubble/controller state at each step (no window attachment assumptions; use
   `ShadowWindowManagerImpl` like `OverlayControllerRobolectricTest`).

Use `@ConscryptMode(OFF)` and `@Config(sdk = [34])` as the existing Robolectric tests do. No new
dependencies. Do not touch `core/` or `scripts/` (TESS owns integration).

## Acceptance

```sh
cd /work/martins-whisper-worktrees/t2-svc
GRADLE_USER_HOME=/home/node/gh-t2-svc ./gradlew --offline --console=plain \
  :app:testDebugUnitTest :app:assembleDebug
```
BUILD SUCCESSFUL, failures 0, new test count printed from
`app/build/test-results/testDebugUnitTest/TEST-*.xml`.

## Result

DONE. No production code changed; the existing test seams were sufficient.

- Branch `agent/t2-svc`, tests commit `bcc409f`, base `agent/g2-engine` @ `5494b79`
  (must merge after G2; TESS/MARA owns the order).
- New files (app tests only):
  - `app/src/test/java/com/martins/whisper/harness/DictationServiceLifecycleRobolectricTest.kt`
    (4 tests): frames captured while the loader is blocked are buffered in order and inserted
    once; 300 s idle unload closes the engine and the next press creates a new instance; cancel
    during a blocked load inserts nothing and leaves the loaded engine reusable; switching
    `settings.modelId` closes the previous engine.
  - `app/src/test/java/com/martins/whisper/harness/OverlayStateMachineRobolectricTest.kt`
    (2 tests): Idle -> Loading -> Recording -> Processing -> Success -> Error -> Idle via
    `OverlayController.update`, asserting `MicBubbleView.state`/`contentDescription`; a second
    test re-applies every state (plus duplicate Idle) and asserts exactly one registered bubble.
- Acceptance (actually run):
  `GRADLE_USER_HOME=/home/node/gh-t2-svc ./gradlew --offline --console=plain :app:testDebugUnitTest :app:assembleDebug`
  => BUILD SUCCESSFUL. `TEST-*.xml`: before 15 suites / 66 tests / 0 failures;
  after 17 suites / 72 tests / 0 failures / 0 errors (the two new suites add 6 tests).
- Findings: `MicBubbleView.contentDescription` is null until `state` is assigned (Kotlin property
  initializer bypasses the custom setter); the service already calls `overlay.update(state.value)`
  right after `show`, so the new test mirrors that. Cancel while loading clears the buffer but the
  background load still completes and leaves the engine loaded (READY, not closed), so the next
  press reuses it without calling the factory again. `isShowing` is `view.isAttachedToWindow`;
  Robolectric does not attach overlays, so the test compares it with the attachment flag instead of
  assuming true.
- Details: `handoffs/TESS-2/W2-SVC.md`.
