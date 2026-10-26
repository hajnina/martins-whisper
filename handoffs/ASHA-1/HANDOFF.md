# ASHA-1 — G1a-2 + G1b handoff

## Executive summary

G1 is implemented on `agent/g1-engine`: `EngineFactory` now loads the installed catalog model through
the real `WhisperCppEngine`, with no fake default, and persists a typed `core.asr.EngineState`
(active backend, fallback reason, GPU-failure count, last error) in `SettingsStore`. The model screen
now lists the catalog with installed state and download / SAF import / delete actions, progress,
and diagnostics. Missing native/model/GPU paths fail typed and are visible instead of crashing.
Gate: `:core:test` 85 tests, `:app:testDebugUnitTest` 50 tests, all 0 failures; `assembleDebug` green;
APK 8,938,813 bytes. No device/GPU run was possible (no hardware in this sandbox).

## What was built / changed

Core:
- `core/src/main/kotlin/com/martins/whisper/core/asr/EngineState.kt` — pure state + reason constants
  (`native-unavailable`, `model-missing`, `gpu-unavailable`, `auto-cpu`, `engine-error`) and
  `loaded()` / `failed()` transitions.
- `core/src/test/kotlin/com/martins/whisper/core/asr/EngineStateTest.kt` — 7 host-JVM tests.

App:
- `app/src/main/java/com/martins/whisper/asr/EnginePlanner.kt` — pure plan selection; unknown/stale id
  falls back to `ModelCatalog.default`; missing file throws typed `model-missing`.
- `app/src/main/java/com/martins/whisper/asr/ModelRepositoryProvider.kt` — process-wide
  `ModelRepository.forFilesDir(context.filesDir)`; one models directory for UI + engine.
- `app/src/main/java/com/martins/whisper/asr/WhisperCppEngine.kt` (moved under `src/main/kotlin` in the
  earlier wip commit) — `SpeechEngineUnavailableException(message, reason, cause)`; native JNI engine.
- `app/src/main/java/com/martins/whisper/service/EngineFactory.kt` — real default factory; optional
  `factory` test override; `createWith(...)` records `EngineState` via `previousState`/`onState`.
- `app/src/main/java/com/martins/whisper/service/DictationService.kt` — reloads the engine when the
  selected model id changes (from the wip commit); no fake-engine special case.
- `app/src/main/java/com/martins/whisper/data/SettingsStore.kt` — persists `engineState` under
  `engine_active_backend`, `engine_fallback_reason`, `engine_gpu_failure_count`, `engine_last_error`.
- `app/src/main/java/com/martins/whisper/MainActivity.kt` — catalog list replaces the spinner-only
  section; per-model installed/selected state, download/import/delete buttons, progress bar, and
  diagnostics (native availability, active backend, fallback, GPU failures, last error).
- `app/src/main/java/com/martins/whisper/ui/ModelBrowser.kt` — JVM-testable model-management state
  machine + presentation helpers; all file/network work stays in `Dispatchers.IO` in the activity.
- `app/src/main/res/values/strings.xml` — model/diagnostics strings (wip commit).
- Deleted: `app/.../asr/EngineDiagnostics.kt` (replaced by `EngineState`) and
  `app/.../service/FakeSpeechEngine.kt` (no fake default).

Tests:
- `app/src/test/java/com/martins/whisper/asr/EnginePlannerTest.kt` — 3 tests.
- `app/src/test/java/com/martins/whisper/service/EngineFactoryTest.kt` — 8 tests.
- `app/src/test/java/com/martins/whisper/ui/ModelBrowserTest.kt` — 8 tests
  (installed/not-installed rendering, download/import/delete transitions, presentation).

## Commands and real results

```
export JAVA_HOME=/work/android-toolchain/jdk
export ANDROID_HOME=/work/android-toolchain/sdk ANDROID_SDK_ROOT=/work/android-toolchain/sdk
export GRADLE_USER_HOME=/home/node/gh-asha
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --offline
```

Result: `BUILD SUCCESSFUL` (exit 0).

Test counts from `build/test-results`:
- `:core:test` — 85 tests, 0 failures, 0 errors, 0 skipped.
- `:app:testDebugUnitTest` — 50 tests, 0 failures, 0 errors, 0 skipped.
- Total 135 tests.

APK:
- `app/build/outputs/apk/debug/app-debug.apk` — 8,938,813 bytes.
- SHA-256 `bebb88356ea05e89536615f71dddcb9de239f20e89626432af1d62d2e4f6657c`.
- Default build has no native libs (`-Pmartins.native` is opt-in), so on host/JVM the app reports
  native unavailable and fails with the typed message.

The earlier wip gate on the same branch was also green: `BUILD SUCCESSFUL`, `:core:test` + `:app:testDebugUnitTest`.

## Interfaces / invariants / contract changes

- `core.asr.EngineState(activeBackend, fallbackReason, gpuFailureCount, lastError)` is pure and
  persisted by `SettingsStore.engineState`.
- `EngineState.loaded(backend, requested)` derives `auto-cpu` / `gpu-unavailable` and increments the
  GPU-failure counter; `failed(reason, message)` clears `activeBackend` and keeps the counter.
- `SpeechEngineUnavailableException(message, reason = engine-error, cause = null)`: callers can now
  distinguish native/model/GPU/engine failures without parsing text.
- `EngineFactory.createWith(modelId, backend, repository, nativeAvailable, engineLoader,
  previousState, onState)` is the pure seam used by tests. Production `EngineFactory.create` uses the
  cached repository, `WhisperCppEngine.isNativeAvailable`, `WhisperCppEngine.create`, and writes
  `settings.engineState`.
- Unknown `settings.modelId` falls back to `ModelCatalog.default` (base) rather than crashing.
- Invariant: the model repository root is always `<filesDir>/models`; the engine and UI share it.
- Dictation loads the engine off the main thread; model downloads/imports/deletes run on `Dispatchers.IO`.

## Limitations / blockers / next steps

- No Android device, GPU or KVM in this sandbox: no on-device microphone/overlay/Vulkan validation.
- Default APK is intentionally native-less; the real engine path can only succeed in a
  `-Pmartins.native=true` arm64 build on a device.
- Import verifies the selected model's catalog SHA-256. Arbitrary non-catalog GGML files are not
  selectable yet; adding a persisted custom-model catalog would also require changing the planner's
  unknown-id policy. This is a documented follow-up, not part of G1.
- Downloads were not exercised against the network here (offline gate); the core downloader already
  covers resume/SHA in `ModelDownloader` tests.
- Next: MARA integrates and runs the full gate; TESS can reuse `EngineState`/`EngineFactory.factory`
  for the fake-audio E2E; on-device work should `adb push` a `base.bin` into
  `/data/data/com.martins.whisper/files/models/` (or download in Settings) and confirm the typed
  messages and backend diagnostics.

## References

- Android `ActivityResultContracts.OpenDocument` (SAF import) and `Dispatchers.IO` guidance from the
  Android developer docs; adapted the existing repo programmatic-View style, `core.asr` model layer,
  and whisper.cpp JNI engine rather than adding dependencies.

## Commits

- `c3e9d17` — wip(g1): real engine factory, model repository provider, model UI + diagnostics
  (inherited wip commit on this branch; contains the first version of the G1 wiring).
- `cb6e500` — feat(g1): persist engine state and manage catalog models (final ASHA delta).
- Handoff commit follows this file.
