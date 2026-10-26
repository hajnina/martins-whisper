# G2b — Record while the model loads; unload the engine when idle

Owner: ASHA (branch `agent/g2-engine`). One session, one commit.

## Goal

The dictation service must not lose the first syllables while whisper.cpp loads: capture starts
immediately, frames are buffered in order, and the buffer is drained at release. The service owns the
`ModelManager` lifecycle through a host object so the engine is lazily loaded, switched on model
change, and freed after the configured idle time (default 300 s) by the existing 15 s watchdog.

## Inputs

- `app/.../service/DictationEngineHost.kt`, `app/.../service/AudioCaptureBuffer.kt` (new)
- `app/.../service/DictationService.kt`, `core/.../asr/ModelManager.kt`
- `app/.../data/SettingsStore.kt` (`idleUnloadSeconds`, default 300)

## Acceptance

```
./gradlew :app:testDebugUnitTest :app:assembleDebug --offline
```

green; host-JVM tests prove: frames captured during a blocked load are kept in order, release while
loading still transcribes after the load finishes, the injected clock unloads at 300 s and reloads on
the next use, a full 600 s hold fits the buffer, model switches close the previous engine, and cancel
does not touch the loaded engine (`DictationEngineHostTest`).

## Status

done (2026-10-26)

## Result

WIP `480b0ae`; final branch tip and gate evidence in `handoffs/ASHA-2/HANDOFF.md`.
