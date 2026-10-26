# G3b — Backend choice (CPU / Vulkan auto) + CPU retry once

Owner: G3B-CODER (branch `agent/g3-backend`). One session, one commit. Stacked on `agent/g2-engine`.

## Goal

Make the compute-backend choice explicit and safe:

1. **Settings**: keep the persisted enum `ComputeBackend.AUTO/GPU/CPU`, but label the spinner
   `Auto (Vulkan, CPU fallback)`, `Vulkan (GPU)`, `CPU`. The selected value must survive restarts
   (`SettingsStore.backend`).
2. **Persisted fallback reason**: the diagnostics block already renders
   `EngineState.fallbackReason`; keep that and make sure a GPU/Vulkan load failure that retries on
   CPU persists `gpu-unavailable` (requested GPU) or `auto-cpu` (requested AUTO) and increments
   `gpuFailureCount`.
3. **Retry CPU once**: in `EngineFactory.createWith`, when the requested backend is not `CPU` and
   `engineLoader(plan)` throws, build a CPU plan for the same model file, call the loader exactly
   once more, and return that engine. Record the CPU fallback in `EngineState` via
   `previousState.loaded(cpuEngine.backend, requestedBackend)`. Never retry when the request was
   already CPU, and never retry more than once. If the CPU retry also throws, surface the failure
   typed (`ENGINE_ERROR`) and do not loop.

Add host-JVM tests in `app/src/test/java/com/martins/whisper/service/EngineFactoryTest.kt` with
`FakeSpeechEngine` and a loader that fails on the GPU plan:

- GPU loader failure -> CPU retry once, loader calls are `[GPU, CPU]`, returned engine is the CPU
  fake, `fallbackReason == gpu-unavailable`, `gpuFailureCount == 1`, `lastError == null`.
- AUTO loader failure -> CPU retry, `fallbackReason == auto-cpu`, count incremented.
- Explicit CPU loader failure -> **no retry** (one call), typed `ENGINE_ERROR`.
- GPU then CPU both fail -> exactly two loader calls, typed failure, `ENGINE_ERROR` (no third call).
- A successful Vulkan load still records no fallback reason (existing test must stay green).

## Inputs

- `app/src/main/java/com/martins/whisper/service/EngineFactory.kt`
- `app/src/main/java/com/martins/whisper/MainActivity.kt` (backend spinner + diagnostics)
- `app/src/main/res/values/strings.xml` (`backend_*`, `diag_*`)
- `app/src/main/java/com/martins/whisper/data/SettingsStore.kt`
- `core/.../asr/EngineState.kt`, `core/.../Contracts.kt` (`ComputeBackend`)

## Acceptance

```
cd /work/martins-whisper-worktrees/g3-backend
export JAVA_HOME=/work/android-toolchain/jdk
export ANDROID_HOME=/work/android-toolchain/sdk ANDROID_SDK_ROOT=/work/android-toolchain/sdk
export GRADLE_USER_HOME=/home/node/gh-g3b
./gradlew :app:testDebugUnitTest --offline
```

BUILD SUCCESSFUL; the new retry tests are counted in the XML totals; existing engine tests stay
green. `git status` clean on the branch.

## Status

open

## Result

-
