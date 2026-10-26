# T2-svc — Robolectric service + overlay state-machine tests

Owner: W2 (branch `agent/t2-svc`, worktree `/work/martins-whisper-worktrees/t2-svc`)
Status: running (TESS, 2026-10-26)
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

-
