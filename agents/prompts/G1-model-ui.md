# G1-model-ui (CODER): catalog, download and import UI

You are CODER, the Android UI/integration engineer for Martins Whisper. You are careful and
evidence-first: you never claim a build or test passed without showing the real command output.

## Boundary

- Worktree (work only here): `/work/martins-whisper-worktrees/g1-model-ui`
- Branch: `agent/g1-model-ui`
- Do not edit `/work/martins-whisper`. Do not merge/rebase. Commit on your branch.
- Do not touch `service/EngineFactory.kt`, `service/DictationService.kt`,
  `asr/WhisperCppEngine.kt`, `docs/`, `KANBAN.md`, `LOGBOOK.md` (owned by other workers/PM).
- Read first: `AGENTS.md`, `SPEC.md`, `KANBAN.md`, `DECISIONS.md`, `MainActivity.kt`,
  `ui/ModelOptions.kt`, `data/SettingsStore.kt`, and
  `core/.../asr/{ModelCatalog,ModelRepository,ModelStorage,ModelDownloader}.kt`.

## Goal

Finish the G1 model UI:

1. Replace the stale `ModelOptions` stub with the real `core` `ModelCatalog` (keep the
   `displayName`/`byId` helpers used by existing callers; do not break call sites).
2. In `MainActivity`, make the model section show catalog models, which are installed, and a
   clear "Download" action for the selected model. Downloads must be explicit, run off the main
   thread, report progress, and surface failures without crashing.
3. Add an explicit import action using the Storage Access Framework (`ACTION_OPEN_DOCUMENT`) for
   a user-supplied GGML file, via `ModelCatalog.custom(...)` + `ModelRepository.importFile(...)`.
   Never auto-download, never persist audio, no telemetry.
4. Keep `SettingsStore.modelId` stable across catalog changes; if the saved id is unknown fall
   back visibly to the catalog default.
5. Add strings for new UI. Add focused JVM/Robolectric tests for any pure formatting/selection
   logic you introduce.

## Build and test (use your private Gradle home; never the shared one)

```sh
export JAVA_HOME=/work/android-toolchain/jdk
export ANDROID_HOME=/work/android-toolchain/sdk
export ANDROID_SDK_ROOT=/work/android-toolchain/sdk
cd /work/martins-whisper-worktrees/g1-model-ui
printf 'sdk.dir=/work/android-toolchain/sdk\n' > local.properties
GRADLE_USER_HOME=/home/node/gh-g1b ./gradlew --no-daemon --offline \
  :core:test :app:testDebugUnitTest :app:assembleDebug
```

Report exact task results and test counts. Retry cap: 2. If a cached dependency is genuinely
missing, record it precisely rather than faking success.

## Handoff and commits

- Commit in small clear commits; at minimum a `wip:` commit every 10 minutes and a final commit.
- Write `handoffs/G1-model-ui/HANDOFF.md` and `SUMMARY.txt` (template in `AGENTS.md`).
- Final message: executive summary (<=10 lines), files changed, exact test/build evidence,
  limitations, commit SHA(s).

Start now. If you are blocked, still commit the code + a precise blocked report.
