# PARK-ASHA — 2026-10-26

## DONE (merged on master unless noted)

- **G1 core**: merged before this session.
- **G2 custom model import + record-while-loading / idle unload**: branch `agent/g2-engine` @ `8497d83`; merge `01fa735`; handoff `handoffs/ASHA-2/`.
- **G3a real-HTTP downloader proof tests**: branch `agent/g3-download` @ `c207082`; merge `5bc822d`; handoff `handoffs/ASHA-3/`.
- **G3b backend labels + CPU-retry-once engine fallback**: branch `agent/g3-backend` @ `63230d3`; merge `c53b6ef`; handoff `handoffs/ASHA-3/`.
- **NORA-1 CI/native APK evidence**: merge `c3c45bb`; handoff `handoffs/NORA-1/`.
- **TESS-2 T2 service lifecycle + overlay state-machine tests**: merge `25ea766`.
- **TESS-2 T2 edge suites**: merge `ed8d96c`.
- **Wave-4 integration**: master @ `83a65df`; `handoffs/` wave-4 note records gate **217 tests / 0 failures**.
- **U2b overlay states/haptics/accessibility (DONE, UNMERGED)**: branch `agent/u2-states` @ `db0b5f6` (code `4763078`, handoff `db0b5f6`); 86 app tests green on its base. Handoff `handoffs/ASHA-4/U2b-HANDOFF.md`.
- **U2a tile + notification toggle (PARKED, DO NOT MERGE)**: branch `agent/u2-tile` @ `5a84f51` (`wip: park`); no handoff; out of MFVP scope.

## RUNNING at park

- **U2A worker** — ticket U2a (Quick Settings tile + notification overlay toggle), branch `agent/u2-tile`, model `opencode/deepseek-v4.1-flash-free`. Reached a compiling-ish WIP (tile service, manifest, service action, Robolectric test) but **was stopped by scope order**; all files committed in `5a84f51`. Do not wait for it; do not merge it.
- **U2B worker** — ticket U2b, branch `agent/u2-states`, same model. **Completed**: `db0b5f6`; 86 tests, 0 failures.
- No other workers were launched by ASHA this session.

## NEXT (ordered; each is one small ticket)

1. **E2E-1 audit + smallest fixes**: trace bubble tap -> `DictationService.onPress` -> audio frames -> `DictationEngineHost` buffering/VAD/chunk -> engine transcribe -> `TextInserter.insert` into focused field. Read code and existing tests; fix only missing/broken links. Acceptance: existing `:app:testDebugUnitTest --offline` green + a targeted test for each link found broken. Hand off to MARA with evidence.
2. **U2B-2 haptics/visibility refinement** (branch `agent/u2-states`): start/stop/inserted each get one tick; **error gets a distinct double tick**; bubble state visible within ~100 ms for recording/working/error; content descriptions stay; **bubble must never steal focus**. Keep Robolectric tests small; update `handoffs/ASHA-4/U2b-HANDOFF.md`; hand to MARA.
3. **MODEL-1 verify default model path**: one default (base or tiny), one-tap download, resume, SHA check — confirm the already-built G3 path works end-to-end in code/tests; fix smallest gap.
4. **APK-1 verify arm64 artifact**: CI-built APK actually contains the working whisper.cpp `.so` (NORA/N1). Do not add release workflow/signing ceremony; debug-signed is fine.
5. **PHONE-1 on-device run**: install on real phone via wireless adb, grant 3 permissions, tap/speak/insert; fix real bugs found. Requires the physical phone.
6. **README-1**: one short README: what it is, install, permissions, use. No new docs beyond this.

## TRAPS / NOTES

- **Master moved during U2 work**: U2 branches are based on `c53b6ef`; master is `83a65df`. Merge master into each branch before final gate.
- **T2 test conflict with U2b**: `OverlayStateMachineRobolectricTest` (master) asserts old content descriptions (`"Recording"`, raw success/error messages). U2b intentionally changes them. After merging master into `agent/u2-states`, update those expectations deliberately; do not weaken the behavior.
- **`DictationService.onStartCommand` calls `ensureForeground()` + `startOverlay()` after action handling.** Any new action that hides the overlay must return early or it will be immediately re-shown. Relevant if tile/toggle work is ever resumed.
- **Robolectric `ShadowTileService`** exposes `getQsTile()`, `setLocked()`; no click recording — tile click is observed via `ShadowApplication.getNextStartedService()`. Tile work is deferred anyway.
- **Build env** (always `--offline`): `export JAVA_HOME=/work/android-toolchain/jdk ANDROID_HOME=/work/android-toolchain/sdk ANDROID_SDK_ROOT=/work/android-toolchain/sdk GRADLE_USER_HOME=<own copy>`; never share `/work/android-toolchain/gradle-home` between concurrent workers.
- **Provider check**: `/work/.yoloseek/PROVIDER-STATUS.md` was **OK** at park.
- **Many worktrees exist**; `/tmp/tess-master-verify` is a prunable stale worktree. Do not assume old branch tips are merged — check `git log master`.
- **Park commits**: `m1-release` `94d76e6`, `n2-release` `f5f600e`, `t3-lint` `1e7e3fd` are `wip: park` only (release/lint work is out of MFVP scope).
- **Scope**: MFVP is the only goal. Do not merge tile, extra overlay states beyond recording/working/error, GPU/Vulkan tuning, lint, release workflow, or new docs.
