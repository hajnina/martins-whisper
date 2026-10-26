# ASHA-3 — G3a (real-HTTP downloader proof) + G3b (backend choice, CPU retry once)

## Executive summary

Two small tickets, one branch each, both stacked on `agent/g2-engine` (`8497d83`) and both clean:

- **G3a** (`agent/g3-download`): an app-level JVM test proves the production download path
  (`ModelRepository` -> `ResumableModelDownloader` -> `HttpModelSource`) against a real loopback
  `ServerSocket`: happy path (bytes/SHA-256/monotonic progress/`.part` removed), resume after a
  mid-body cut (`Range: bytes=N-`, `resumed == true`, `attempts == 2`), bad hash
  (`ModelVerificationException`, no files), 404 (`DownloadException` containing `404`, no files).
  No production code changed.
- **G3b** (`agent/g3-backend`): backend labels are now `Auto (Vulkan, CPU fallback)` /
  `Vulkan (GPU)` / `CPU` (persisted enum unchanged), and `EngineFactory` retries the engine load
  exactly once on CPU when a non-CPU request fails. The fallback is persisted in `EngineState`
  (`gpu-unavailable` for GPU, `auto-cpu` for AUTO, `gpuFailureCount + 1`, `lastError == null`).
  Explicit CPU is never retried; a second failure stops after exactly two calls and records
  `ENGINE_ERROR`.
- Combined integration gate (G2 + G3a + G3b merged in a throwaway branch, offline,
  `--rerun-tasks`): **163 tests, 0 failures** (`:core:test` 89, `:app:testDebugUnitTest` 74);
  `:app:assembleDebug` green. APK 8,971,193 bytes, SHA-256
  `2f5ecf7742fb29b9eb80dc0c3dfb7978ed3615e3dad1d6e951e57560cd075025`.

## Branches / commits

| Ticket | Branch | Base | Code commits |
|---|---|---|---|
| G3a | `agent/g3-download` | `agent/g2-engine` `8497d83` | `ea971b5` ticket, `3111cf7` tests, `befd96b` handoff |
| G3b | `agent/g3-backend` | `agent/g2-engine` `8497d83` | `3373e9b` ticket, `db95d14` code+tests, `cfdbb8a` handoff |

Each branch also has this combined handoff commit; `git log -1` gives the exact tip. Both trees are
clean. Detailed worker handoffs: `handoffs/ASHA-3/G3a-HANDOFF.md`, `G3b-HANDOFF.md`.

## G3a — what the tests prove

`app/src/test/java/com/martins/whisper/asr/HttpModelDownloaderTest.kt` (new, 273 lines):

- `FakeHttpServer`: daemon thread on `ServerSocket(0)` bound to `127.0.0.1`; parses request line +
  headers, records method/path/`Range`, answers with fixed `Content-Length`, 206 +
  `Content-Range` for `Range` requests, a `/missing` 404, and a `/cut` response that writes 500 of
  2,000 bytes then closes the socket.
- Repository under test: `ModelRepository(ModelStorage(tempDir), ResumableModelDownloader(storage,
  HttpModelSource(connectTimeoutMs = 2_000, readTimeoutMs = 5_000), retryDelayMs = 10))`.
- Cases: happy path; resume after cut (server sees `Range: bytes=500-`); bad hash; 404. All assert
  final bytes/hash and that neither target nor `.part` remains where the test expects failure.
- No external dependency, no internet, no device. Existing core `ModelDownloaderTest` untouched.
- The model screen already shows download progress via `MainActivity`'s progress bar/transfer
  label; no notification was added.

G3a gate (branch alone): `:app:testDebugUnitTest --offline` BUILD SUCCESSFUL; 70 tests, 0 failures;
`HttpModelDownloaderTest` 4/4.

## G3b — what the code and tests do

`app/src/main/java/com/martins/whisper/service/EngineFactory.kt`:

- Plan -> native check -> loader unchanged.
- On a loader failure with requested `GPU`/`AUTO`, build `plan.copy(backend = CPU)`, call the loader
  once more; on success return that engine and persist
  `previousState.loaded(cpuEngine.backend, plan.backend)`.
- On explicit `CPU`, keep the old typed-failure path (`reasonOf` preserves a
  `SpeechEngineUnavailableException` reason, else `ENGINE_ERROR`).
- If the CPU retry also throws, persist `ENGINE_ERROR` and rethrow the CPU exception; no third call.
- `strings.xml`: `backend_auto` = `Auto (Vulkan, CPU fallback)`, `backend_gpu` = `Vulkan (GPU)`;
  `backend_cpu` stays `CPU`. Enum values and `SettingsStore.backend` persistence are unchanged.
- Diagnostics already renders `EngineState.fallbackReason` / `gpuFailureCount`; no change needed.

`EngineFactoryTest` gained 4 tests (12 total, all green): GPU failure -> `[GPU, CPU]` + CPU engine +
`gpu-unavailable` + count 1; AUTO failure -> `[AUTO, CPU]` + `auto-cpu` + count 1; explicit CPU
failure -> `[CPU]` only + `ENGINE_ERROR`; GPU and CPU both fail -> exactly `[GPU, CPU]` + typed
exception + `ENGINE_ERROR`.

G3b gate (branch alone): `:app:testDebugUnitTest --offline` BUILD SUCCESSFUL; 70 tests, 0 failures;
`EngineFactoryTest` 12/12.

## Combined integration gate (verified by ASHA)

Throwaway branch `tmp/g3-mergecheck` from `agent/g2-engine`, merged `agent/g3-download` then
`agent/g3-backend` (no code overlap; only the two workers' `handoffs/ASHA-3/SUMMARY.txt` add/add was
unified), then:

```
export JAVA_HOME=/work/android-toolchain/jdk
export ANDROID_HOME=/work/android-toolchain/sdk ANDROID_SDK_ROOT=/work/android-toolchain/sdk
export GRADLE_USER_HOME=/home/node/gh-g3a
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --offline --rerun-tasks
```

```
BUILD SUCCESSFUL in 11s
47 actionable tasks: 47 executed
```

```
core: tests=89 skipped=0 failures=0 errors=0
app : tests=74 skipped=0 failures=0 errors=0
total 163, 0 failures, 0 errors
HttpModelDownloaderTest 4/4, EngineFactoryTest 12/12
APK 8,971,193 bytes
2f5ecf7742fb29b9eb80dc0c3dfb7978ed3615e3dad1d6e951e57560cd075025
```

## Merge order for MARA

1. Merge `agent/g2-engine` first (G2 is the base of both G3 branches and is not on `master` yet).
2. Merge `agent/g3-download` and `agent/g3-backend` in either order; they touch disjoint files
   (`HttpModelDownloaderTest.kt` vs `EngineFactory.kt`/`strings.xml`/`EngineFactoryTest.kt`).
3. Re-run the full gate on the merge result; expect 163 tests / 0 failures (more if other waves
   land) and `assembleDebug` green.
4. Suggested KANBAN: `G3a` downloader proof and `G3b` backend choice/CPU retry, both `done`,
   handoff `handoffs/ASHA-3/`.

## Limitations / notes

- No physical device, GPU or real network here: G3a uses loopback TCP only (no TLS/redirects/
  Android network-security config); G3b is host-JVM with `FakeSpeechEngine` only. Real Vulkan
  driver failure behaviour still needs H1/H2 hardware.
- The core downloader retries `IOException` (including HTTP 404) up to `maxRetries`; the 404 test
  asserts the surfaced `DownloadException` and the absence of files, not the attempt count. Existing
  core behaviour, unchanged by G3a.
- No secrets, no GitHub credentials, no network access, no new dependencies. Only the two worktrees
  and their branches were touched.
