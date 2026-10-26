# T1-1 — Layer 1 fake-audio end-to-end test

Owner: TESS (branch `agent/t1-harness`). One session, one commit.

## Goal

One JVM/Robolectric E2E test: synthesized frames -> VAD/chunker -> merger -> insertion plan, using
`FakeSpeechEngine` (core testFixtures). Assert transcript text, insertion caret/selection, and model
lifecycle (`ModelManager` load/markUsed/idle-unload). No real model, no device, no network.

## Acceptance

```
./gradlew :core:test :app:testDebugUnitTest --offline
```
green with the new test(s) counted in the suite output.

## Status

<<<<<<< HEAD
running (TESS, 18:52Z)
=======
done (TESS, 2026-10-26; re-verified 2026-10-26 on master 421f223)
>>>>>>> 667dba3

## Result

Delivered in `bc182f9` (test committed with the harness), merged to master in `425412c`.
`app/src/test/kotlin/com/martins/whisper/harness/FakeAudioPipelineE2ETest.kt` covers synthesized
frames → `AudioChunker`/`EnergyVad` → `FakeSpeechEngine` → `TranscriptMerger` → `TextInsertionPlanner`
→ `ModelManager` markUsed/300 s idle-unload, and asserts chunk reasons, 250 ms overlap, merged text,
caret/selection after insert and replace, and `READY → UNLOADED`. A second test proves pure silence
emits no chunks and no engine calls. Supporting Robolectric E2E:
`DictationServiceRobolectricTest` (press→record→transcribe→insert→broadcast→idle unload).

Acceptance (private Gradle home, from the worktree and from a clean master 421f223 worktree):

```
scripts/test-device-ready.sh  ->  Layer 1: 25 suites, 141 tests, 0 failures, 0 errors
                                  core 11/85, app 14/56
```
