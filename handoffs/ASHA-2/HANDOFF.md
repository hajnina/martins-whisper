# ASHA-2 — G2a (custom models) + G2b (engine lifecycle) handoff

## Executive summary

Branch `agent/g2-engine` (worktree `/work/martins-whisper-worktrees/g2-engine`), code tip
**`5494b79`** (branch tip **`4a5cd07`**, docs only), merged with current `master` (`ba6b160`, wave 2)
at **`045e55c`**. Working tree clean.

- **G2a — custom (non-catalog) GGML import:** a user can import any local model file, it is persisted
  as a `ModelSpec` (Base64 text records in `SharedPreferences`), listed and selected on the model
  screen, resolved by `EnginePlanner`/`EngineFactory`, and removed from both disk and settings on
  delete. Catalog models keep their download/import-by-id behaviour.
- **G2b — record while loading + idle unload:** `DictationService` now owns the engine through
  `DictationEngineHost`. Audio capture starts before whisper.cpp finishes loading and frames are
  buffered in order (600 s capacity, ~19.5 MB) and drained at release; the engine is loaded lazily,
  switched on model change, and freed after the configured idle time (default 300 s) by the existing
  15 s watchdog.
- Merged `master` into the branch; one conflict in `DictationService.kt` (TESS's injectable
  `audioSourceFactory` vs. the new host). Resolved by keeping both: `audioSource = audioSourceFactory(this)`
  in `onCreate`, engine lifecycle through `DictationEngineHost`.
- Gate on the merged, committed tree, offline, `--rerun-tasks`: **155 tests, 0 failures**
  (`:core:test` 89, `:app:testDebugUnitTest` 66), `:app:assembleDebug` green. APK 8,970,737 bytes,
  SHA-256 `02937d2347b79d0a7b01fb26a37e68d6595f2d0e1e2256c46d756a5b503bde53`.

## What was built / changed

G2a (custom models):
- `core/src/main/kotlin/com/martins/whisper/core/asr/CustomModelCodec.kt` — boring, versioned
  (`v1`) newline/pipe record format; text fields Base64-URL encoded; decoding never throws, skips
  malformed records, rejects duplicate ids (first wins), path separators and non-hex SHA-256.
- `app/src/main/java/com/martins/whisper/data/SettingsStore.kt` — `customModels` list plus
  `addCustomModel` / `removeCustomModel` under `custom_models`.
- `app/src/main/java/com/martins/whisper/asr/EnginePlanner.kt` — optional `customModels` list; custom
  id resolves before catalog, unknown ids still fall back to `ModelCatalog.default`.
- `app/src/main/java/com/martins/whisper/service/EngineFactory.kt` — passes `settings.customModels`
  to the planner (test seam `createWith(..., customModels)`).
- `app/src/main/java/com/martins/whisper/service/DictationService.kt` — loading label resolves custom
  display names.
- `app/src/main/java/com/martins/whisper/ui/ModelBrowser.kt` — `setSpecs`, `begin(spec, action)` so a
  not-yet-persisted custom spec can enter the list and be imported; `setSpecs` drops deleted ids and
  falls the selection back.
- `app/src/main/java/com/martins/whisper/MainActivity.kt` — “Import custom model…” button; SAF
  `OpenDocument`; display name from `OpenableColumns`; unique `custom-<slug>` id; copies through cache
  into the shared models dir on `Dispatchers.IO`; computes SHA-256/size; persists and selects;
  delete removes the file and the descriptor, resets `modelId` to `base` if it was selected, and
  keeps the descriptor when the file delete throws.
- `app/src/main/res/values/strings.xml` — `model_import_custom`.

G2b (engine lifecycle):
- `app/src/main/java/com/martins/whisper/service/AudioCaptureBuffer.kt` — synchronized ordered frame
  buffer; `add` never drops (returns false only when full); `CapturedAudio.toFloatArray()`.
- `app/src/main/java/com/martins/whisper/service/DictationEngineHost.kt` — owns `ModelManager`
  (injectable clock, lazy load, switch, idle unload), buffers capture, drains on finish, transcribes
  through the manager lock. No Android types.
- `app/src/main/java/com/martins/whisper/service/DictationService.kt` — capture starts on press;
  load runs on `Dispatchers.Default`; release drains the buffer and transcribes after the load
  completes; watchdog calls `unloadIfIdle()`; `stopEverything` closes the host. `idleUnloadSeconds`
  is honoured (minimum 30 s, default 300 s).

Tests:
- `core/.../asr/CustomModelCodecTest.kt` — 4 tests (round-trip, malformed, duplicates, bad SHA).
- `app/src/test/java/com/martins/whisper/asr/EnginePlannerTest.kt` — custom id resolves to its file.
- `app/src/test/java/com/martins/whisper/service/DictationEngineHostTest.kt` — 6 tests (capture during
  blocked load, release while loading, 300 s idle unload with injected clock, 600 s buffer, switch
  closes old engine, cancel leaves engine loaded).
- `app/src/test/java/com/martins/whisper/ui/ModelBrowserTest.kt` — 3 new tests (custom spec added on
  import + progress + installed/selected, `setSpecs` forgets deleted custom and falls back, new spec
  refused while busy); 11 tests total in the class.
- Existing TESS Robolectric suite still green against the new host (2 service E2E tests).

## Commands and real results

```
cd /work/martins-whisper-worktrees/g2-engine
export JAVA_HOME=/work/android-toolchain/jdk
export ANDROID_HOME=/work/android-toolchain/sdk ANDROID_SDK_ROOT=/work/android-toolchain/sdk
export GRADLE_USER_HOME=/home/node/gh-asha
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --offline --rerun-tasks
```

```
BUILD SUCCESSFUL in 9s
47 actionable tasks: 47 executed
```

Test XML totals (run at 2026-10-26T18:20Z):

```
core: tests=89 skipped=0 failures=0 errors=0
app : tests=66 skipped=0 failures=0 errors=0
total 155, 0 failures, 0 errors
```

APK:

```
8,970,737 bytes
02937d2347b79d0a7b01fb26a37e68d6595f2d0e1e2256c46d756a5b503bde53  app-debug.apk
```

## Commits on `agent/g2-engine` since `master`

```
4a5cd07 docs(g2): ASHA-2 handoff and ticket results
5494b79 test(g2): cover custom model flows; keep descriptor when delete fails
045e55c Merge branch 'master' into agent/g2-engine
dd04afd wip: checkpoint before provider limited   (G2a custom models)
480b0ae feat(service): record while the model loads and unload the engine when idle (G2b)
```

The gate below ran on `5494b79`; the commits after it are docs/tickets only.

Ticket files (reconstructed from the WIP commits, so a restart needs no session context):
`.scratch/device-ready-apk/build-tickets/G2a-custom-models.md` and `G2b-engine-lifecycle.md`.

## Interfaces / invariants

- Custom models are ordinary `ModelSpec`s with `url == null`; on-disk name is always
  `<filesDir>/models/<id>.bin` (`ModelStorage`), the persisted `fileName` is display metadata only.
- `CustomModelCodec` is the only persistence format; `decode` is total (never throws) and
  idempotent for duplicate ids.
- `EnginePlanner.plan(modelId, backend, repository, customModels = emptyList())`: custom match,
  then catalog, then `ModelCatalog.default`; a custom model that is not installed still raises the
  typed `SpeechEngineUnavailableException(model-missing)`.
- `DictationEngineHost` is the only owner of `ModelManager` in the service; capture is buffered
  before the engine exists and drained exactly once per session (`finishSession` clears).
- Idle unload is driven by `ModelManager.maybeUnload()` with `settings.idleUnloadSeconds` (min 30 s,
  default 300 s); the clock is injected for tests.

## Limitations / next steps

- No Android device/GPU/KVM here: the custom import picker and on-device native engine are
  compile/JVM-verified only; H1/H2 remain device-blocked.
- Custom import accepts any file; it does not validate the GGML magic/architecture. A wrong file
  fails later with a typed engine error. Documented v1 decision is GGML-only, so a magic check is a
  small follow-up if wanted.
- Custom models cannot be re-downloaded (no URL); deleting one removes its only copy.
- Buffering a full 600 s hold costs ~19.5 MB; a full-buffer condition aborts the session with a
  visible error instead of silently dropping speech.
- `ModelBrowser` treats a custom spec added by `begin(spec, …)` as UI state only; it is persisted by
  the activity after a successful copy. A process death mid-import leaves no descriptor (the cache
  temp is deleted).

## Integration notes for MARA

- Merge `agent/g2-engine` into `master` with `--no-ff`; branch is clean at `4a5cd07` (code at
  `5494b79`; later commits are docs/tickets only).
- Re-run the gate on the merge result; expected totals are 155 tests / 0 failures (or more if wave 3
  lands first), `assembleDebug` green.
- Suggested KANBAN rows: `G2a` custom model import and `G2b` record-while-loading/idle-unload, both
  `done`, handoff `handoffs/ASHA-2/`.
- No secrets, no GitHub credentials, no changes outside `app/`, `core/asr`, `.scratch/…/build-tickets/`
  and this handoff.
