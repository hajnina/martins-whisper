# G1a-2 — Wire EngineFactory to whisper.cpp + installed model, persist state

Owner: ASHA (branch `agent/g1-engine`). One session, one commit.

## Goal

`EngineFactory.create` returns a real `WhisperCppEngine` loaded from the installed catalog model at
`<filesDir>/models/<id>.bin` using `settings.backend`. Remove the fake-engine default. Fail typed and
gracefully when native is unavailable or the model is missing (no startup crash). Persist resolved
backend, fallback reason, GPU-failure count and last engine error (a pure `core.asr.EngineState` is
fine; test it without Robolectric).

## Inputs

- `app/.../service/EngineFactory.kt`, `app/.../service/DictationService.kt`
- `app/.../data/SettingsStore.kt`, `app/.../asr/WhisperCppEngine.kt`
- `core/.../asr/ModelRepository.kt`, `ModelCatalog.kt`

## Acceptance

```
./gradlew :core:test :app:testDebugUnitTest --offline
```
green; host-JVM tests prove: a missing native library yields `SpeechEngineUnavailableException` with
`fallbackReason = native-unavailable`; a missing model yields `model-missing`; no `FakeSpeechEngine`
default remains.

## Status

done (2026-10-26)

## Result

Merged: ASHA-1 `fcb8299` (G1), DORA-1 `b33c8c3` (D1). MARA gate: 135 tests, 0 failures, APK 8,938,813 B.
