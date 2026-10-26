# PARK - oc-eph-226089 (2026-10-26)

## DONE (master, pushed through d529395)
- `07bd75c` C1 prompt; `2472867` first-push handoff (74 tests).
- `caf0d09` B1 build harness; `9b2ee46` E1 ASR/model layer + JNI scaffolding; `08de31c` merge B1+E1.
- `323a380`/`c22617c` agent B1/E1 commits; `beabf22`/`d529395` provenance merges (`-s ours`; content came via `pm/b1-e1`).
- `7771eaa` raw bench workspace handoff; `69d6ea8` removed 3150 `raw-from-pm/repos` files and ignored that path.
- `cf36223` handoff evidence; `148e5b4`/`0262633` provider-block docs; `b3703d0` resume docs.
- Gate: `109 tests, 0 failures`; APK `8,876,133` bytes; SHA-256 `c2aa1350607ae00239f2bdab02633b2e91980b7f3eca3658f4d301bfe83e8cd0`.
- All worktrees and master clean. No workers running. Raw bench ownership is ORIN's.

## RUNNING at park
- Ticket **G1** (integration), branch/worktree `master` + `pm/b1-e1`, model `deepseek-v4.1-flash-free`. Progress: B1+E1 merged and gate green; G1 code (real engine wiring, model UI) not started after the provider pause.
- No background `opencode run` processes; bench already stopped and handed off.

## NEXT (ordered, small tickets)
1. **G1a** Add app `ModelRepository` provider; wire `EngineFactory` to `WhisperCppEngine` + installed model; remove default fake engine; persist backend/fallback/GPU-failure state.
2. **G1b** Replace `ui/ModelOptions` stub with `core.asr.ModelCatalog`; add download/import/delete UI and diagnostics (native availability, backend, fallback reason).
3. **G1c** Tests for model selection/fallback; run `:core:test :app:testDebugUnitTest :app:assembleDebug --offline`; update `handoffs/PM-2026-10-26-2/HANDOFF.md`.
4. **T1** Robolectric/fake-audio E2E; instrumented layer compiles, device run blocked.
5. **D1** Build/install/adb/docs/device matrix.
6. **H1/H2** physical device + signing; blocked on owner (`owner/ASKS.md`).

## TRAPS
- Never touch shared `/work/android-toolchain/gradle-home`; use `GRADLE_USER_HOME=/home/node/gh-pm --offline`, `JAVA_HOME=/work/android-toolchain/jdk`, `ANDROID_HOME=/work/android-toolchain/sdk`.
- Default APK has no native libs (opt-in `-Pmartins.native=true`); `EngineFactory` must fail typed/gracefully when `WhisperCppEngine.isNativeAvailable == false`.
- Do not re-apply `agent/b1-build` or `agent/e1-asr`; content is already in master.
- Do not `git add -f` benchmark repos/build dirs. `/work/PROVIDER-STATUS.md` gates worker launches.
- `/tmp/opencode` is unwritable; use `/home/node/scratch`.
