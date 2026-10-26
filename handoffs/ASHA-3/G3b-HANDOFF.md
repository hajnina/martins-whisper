# ASHA-3 — G3b backend choice + CPU retry once

## Executive summary

Branch `agent/g3-backend` (worktree `/work/martins-whisper-worktrees/g3-backend`).

- Settings spinner keeps persisted `ComputeBackend.AUTO/GPU/CPU`; labels are now
  `Auto (Vulkan, CPU fallback)`, `Vulkan (GPU)`, `CPU`.
- `EngineFactory.createWith` now retries exactly once on CPU when the requested backend is
  `AUTO` or `GPU` and the first engine load throws. A successful CPU retry persists the
  fallback through `EngineState.loaded(cpuEngine.backend, requestedBackend)`:
  `GPU -> gpu-unavailable`, `AUTO -> auto-cpu`, `gpuFailureCount + 1`, `lastError == null`.
- Explicit `CPU` requests are never retried. If the CPU retry also throws, exactly two loader
  calls have happened, `ENGINE_ERROR` is persisted, and the CPU exception is rethrown typed.
- `./gradlew :app:testDebugUnitTest --offline`: **BUILD SUCCESSFUL**, **70 tests, 0 failures,
  0 errors, 0 skipped** across 15 suites; `EngineFactoryTest` has **12 tests, all green**
  (8 existing + 4 new).

## Files changed

- `app/src/main/java/com/martins/whisper/service/EngineFactory.kt` — retry-once logic; helper
  `reasonOf(Throwable)` keeps the old typed-reason behaviour for the non-retry (CPU) path.
- `app/src/main/res/values/strings.xml` — `backend_auto`, `backend_gpu` labels (enum and
  persistence unchanged).
- `app/src/test/java/com/martins/whisper/service/EngineFactoryTest.kt` — 4 new host-JVM tests.

## Behaviour / state transitions

`createWith` order is unchanged: plan -> native check -> loader. Only loader failures retry.

| Requested | First loader | CPU retry | Loader calls | Persisted state |
|---|---|---|---|---|
| GPU | throws | succeeds | `[GPU, CPU]` | `activeBackend=cpu`, `gpu-unavailable`, count+1, `lastError=null` |
| AUTO | throws | succeeds | `[AUTO, CPU]` | `activeBackend=cpu`, `auto-cpu`, count+1, `lastError=null` |
| CPU | throws | not attempted | `[CPU]` | `ENGINE_ERROR` (or typed unavailable reason), exception rethrown |
| GPU | throws | throws | `[GPU, CPU]` | `activeBackend=null`, `ENGINE_ERROR`, CPU exception rethrown; no third call |

A clean Vulkan/GPU load still calls `loaded(engine.backend, requestedBackend)` with no
fallback and no count increment (existing test stays green). Plan failures (missing model)
and the native-availability check happen before the loader and are not retried.

The CPU retry plan is `plan.copy(backend = ComputeBackend.CPU)`, so it targets the exact same
`ModelSpec`/model file as the failed request.

## New tests

All in `EngineFactoryTest`, loader records `plan.backend` into a list and the assertions check
the explicit backend order:

- `gpu loader failure retries once on cpu and records gpu-unavailable` — `[GPU, CPU]`, CPU fake
  returned, `gpu-unavailable`, count 1, `lastError == null`.
- `auto loader failure retries once on cpu and records auto-cpu` — `[AUTO, CPU]`, `auto-cpu`,
  count 1, `lastError == null`.
- `explicit cpu loader failure is not retried` — `[CPU]` only, typed `SpeechEngineException`,
  `ENGINE_ERROR`.
- `gpu and cpu loader failures stop after exactly two calls` — `[GPU, CPU]` only, typed
  exception, `ENGINE_ERROR`, `activeBackend == null`.

## Commands and real output

```
cd /work/martins-whisper-worktrees/g3-backend
export JAVA_HOME=/work/android-toolchain/jdk
export ANDROID_HOME=/work/android-toolchain/sdk ANDROID_SDK_ROOT=/work/android-toolchain/sdk
export GRADLE_USER_HOME=/home/node/gh-g3b
./gradlew :app:testDebugUnitTest --offline
```

Result (tail):

```
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 17s
30 actionable tasks: 7 executed, 23 up-to-date
```

Test XML totals (`app/build/test-results/testDebugUnitTest/*.xml`, 15 suite files):

```
TOTAL tests=70 failures=0 errors=0 skipped=0
com.martins.whisper.service.EngineFactoryTest tests=12 failures=0 errors=0 skipped=0
```

The 12 `EngineFactoryTest` cases:

```
explicit cpu request is not counted as a gpu fallback
requested gpu that ran on cpu records gpu-unavailable
unknown persisted id falls back to the default catalog model
gpu and cpu loader failures stop after exactly two calls
vulkan backend keeps no fallback reason
explicit cpu loader failure is not retried
auto loader failure retries once on cpu and records auto-cpu
missing model file fails typed and records model-missing
auto backend falling back to cpu is persisted with a reason
engine loader exceptions are recorded and rethrown typed
gpu loader failure retries once on cpu and records gpu-unavailable
missing native library fails typed and records native-unavailable
```

## Commits

- Code + tests: `db95d149299afb39023cec744984acb9bd74fde5`
  `feat(g3b): explicit backend labels and one CPU retry on GPU failure`
- Handoff: this commit (see `git log -1` on `agent/g3-backend`).

## Limitations / notes for the integrator

- No physical device/GPU validation was possible here; host-JVM tests only. Vulkan availability
  and real driver failure behaviour still need the H1/H2 hardware checks.
- The persistent enum values are unchanged (`AUTO`, `GPU`, `CPU`), so existing settings survive.
- On a double failure the rethrown exception is the CPU-retry exception, while the persisted
  `fallbackReason` is `ENGINE_ERROR` as the ticket requires; its message is the CPU failure
  message (falling back to the first failure message when blank).
- No new dependencies.

## References

- Ticket: `.scratch/device-ready-apk/build-tickets/G3b-backend-settings.md`
- Existing contracts: `core/.../asr/EngineState.kt`, `core/.../Contracts.kt`
- Existing test patterns: `app/src/test/java/com/martins/whisper/service/EngineFactoryTest.kt`
