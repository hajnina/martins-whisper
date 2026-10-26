# G1a-2 — Wire EngineFactory to whisper.cpp + installed model, persist state

Owner: MARA. One session, one commit.

## Goal

`EngineFactory.create` returns a real `WhisperCppEngine` loaded from the installed catalog model at
`<filesDir>/models/<id>.bin` using `settings.backend`. Remove the fake-engine default. Fail typed and
gracefully when native is unavailable or the model is missing (no startup crash). Persist resolved
backend, fallback reason, GPU-failure count and last engine error in `SettingsStore`.

## Inputs

- `app/.../service/EngineFactory.kt`, `app/.../service/DictationService.kt`
- `app/.../data/SettingsStore.kt`
- `app/.../asr/WhisperCppEngine.kt`
- `core/.../asr/ModelRepository.kt`, `ModelCatalog.kt`

## Acceptance

```
./gradlew :app:testDebugUnitTest --offline
```
green; a host-JVM test proves a create with missing native throws `SpeechEngineUnavailableException`
and records the error; no `FakeSpeechEngine` default remains.

## Status

open

## Result

-
