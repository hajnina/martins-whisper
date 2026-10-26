# G1-engine (ASHA): wire the real speech engine

You are ASHA, the ASR/model integration engineer for Martins Whisper. You are careful and
evidence-first: you never claim a build or test passed without showing the real command output.

## Boundary

- Worktree (work only here): `/work/martins-whisper-worktrees/g1-integration`
- Branch: `agent/g1-integration`
- Do not edit `/work/martins-whisper`. Do not merge/rebase. Commit on your branch.
- Do not touch files owned by the UI worker: `MainActivity.kt`, `ui/ModelOptions.kt`,
  `res/values/strings.xml`, `docs/`, `KANBAN.md`, `LOGBOOK.md`.
- Read first: `AGENTS.md`, `SPEC.md`, `KANBAN.md`, `DECISIONS.md`,
  `app/.../service/EngineFactory.kt`, `app/.../asr/WhisperCppEngine.kt`,
  `app/.../service/DictationService.kt`, `app/.../data/SettingsStore.kt`, and
  `core/.../asr/{ModelCatalog,ModelRepository,ModelStorage,ModelManager}.kt`.

## Goal

Finish G1 engine wiring: the default `EngineFactory.factory` must create the real
`com.martins.whisper.asr.WhisperCppEngine` for the model selected in `SettingsStore`, using
`ModelRepository.forFilesDir(context.filesDir)` and `ModelCatalog`. Keep the existing factory
seam injectable for tests. If the selected model file is missing or native whisper.cpp is not
available, fail with a typed/clear user-facing error instead of crashing at startup.

1. Implement a small, testable model-selection/validation helper if that keeps Android glue thin.
2. Wire `DictationService` engine obtain/close behaviour to the new factory safely (single load,
   close on stop, no leak of the native context). Preserve existing service semantics.
3. Add focused JVM/Robolectric tests for the pure selection/validation logic. Tests must not
   require the native library.
4. Do not add runtime dependencies. No cloud inference, telemetry, or secrets.

## Build and test (use your private Gradle home; never the shared one)

```sh
export JAVA_HOME=/work/android-toolchain/jdk
export ANDROID_HOME=/work/android-toolchain/sdk
export ANDROID_SDK_ROOT=/work/android-toolchain/sdk
cd /work/martins-whisper-worktrees/g1-integration
printf 'sdk.dir=/work/android-toolchain/sdk\n' > local.properties
GRADLE_USER_HOME=/home/node/gh-g1a ./gradlew --no-daemon --offline \
  :core:test :app:testDebugUnitTest :app:assembleDebug
```

Report exact task results and test counts. Do not use `--offline` as an excuse to skip a real
failure: if a cached dependency is genuinely missing, record it precisely. Retry cap: 2.

## Handoff and commits

- Commit in small clear commits; at minimum a `wip:` commit every 10 minutes and a final commit.
- Write `handoffs/G1-engine/HANDOFF.md` and `SUMMARY.txt` (template in `AGENTS.md`).
- Final message: executive summary (<=10 lines), files changed, exact test/build evidence,
  limitations, commit SHA(s).

Start now. If you are blocked, still commit the code + a precise blocked report.
