# DORA-2 handoff - align D1 docs with the post-G1 reality

## Executive summary (<=10 lines)

- Corrected the stale pre-G1 claims: 0.1.0 now has real `WhisperCppEngine` wiring, the catalog model screen, download/SAF import/delete, progress and typed diagnostics.
- Kept the honest limitation: the default APK has no native libs; real transcription needs `-Pmartins.native=true` on an arm64 device, still untested on hardware.
- Rewrote MODELS for select -> download/import -> installed -> engine loads it, and TROUBLESHOOTING/DOCTOR for `native-unavailable`, `model-missing`, `gpu-unavailable`, `auto-cpu`, `engine-error`.
- `doctor.sh` now reads the persisted engine state (`backend`, `fallback`, `gpu_failures`, `last_error`, `native_engine`) via `run-as`; report shape verified with a fake adb fixture.
- Latest integrated gate: 135 JVM tests, 0 failures; default APK 9,163,557 B, no native libs.
- Removed the duplicate older `handoffs/D1-docs/` checkpoint; `handoffs/DORA-1/` remains the D1 handoff and its evidence was not edited.
- No `app/` or `core/` code touched. All device steps remain **UNVERIFIED (H1-blocked)**. Branch `agent/d1-docs`; MARA integrates.

## What changed / exact file list

Docs:

- `README.md` - current 0.1.0 status (real engine + model UI, 135 tests, native-libs caveat); added FIRST-DICTATION to the index.
- `docs/FIRST-DICTATION.md` - added the shortest native-APK-to-text path (was an untracked leftover from the prior session; reviewed against `app/`/`core/`).
- `docs/BUILD.md` - latest integrated gate numbers; historical D1 numbers labelled as historical.
- `docs/INSTALL.md` - in-app model download/import is the normal path; adb push is the offline fallback; typed native/model errors.
- `docs/MODELS.md` - in-app select/download/import/delete/progress/installed-state flow, engine loading, SHA-256 checks, offline adb fallback.
- `docs/TROUBLESHOOTING.md` - typed-reason table and where Diagnostics/doctor show them; model and boot fixes.
- `docs/DOCTOR.md` - in-app Diagnostics contents, engine fields in the report, updated sample output.
- `docs/PRIVACY.md` - in-app delete/download wording; import uses no network; password fallback wording corrected to match `InsertionPolicy`.
- `docs/DEVICE-MATRIX.md` - removed the "stub engine" note; corrected password/boot acceptance wording.
- `docs/PERMISSIONS.md` - post-boot mic re-arm via the bubble or opening the app.
- `scripts/doctor.sh` - reads `SettingsStore` prefs through `run-as`; prints `selected_model_id`, `backend_setting`, `backend`, `fallback`, `gpu_failures`, `last_error`, `idle_unload_seconds`, `overlay_enabled`, `boot_start`, `native_engine`; help text updated.

Handoffs:

- Deleted `handoffs/D1-docs/` (older duplicate checkpoint, 194-line HANDOFF + SUMMARY). `handoffs/DORA-1/` is the D1 record and was not rewritten.
- Added `handoffs/DORA-2/HANDOFF.md` + `SUMMARY.txt` (this file).

No `app/`, `core/`, Gradle or Kotlin source was edited.

## Commands and real results

All commands run in `/work/martins-whisper-worktrees/d1-docs` on 2026-10-26.

### Stale-claim grep (acceptance)

```sh
$ grep -rniE "fake ?speech ?engine|no in-app model|G1 gap" docs README.md
# no output, exit 1
```

Broader grep for `FakeSpeechEngine|fake engine|fake stub|until G1|no download screen|planned (G1)|not wired yet` also returns no hits.

### Scripts

```sh
$ bash -n scripts/*.sh
# exit 0, no output

$ nix run nixpkgs#shellcheck -- scripts/doctor.sh scripts/install-apk.sh scripts/push-model.sh
# exit 0, no findings

$ ./scripts/doctor.sh; echo $?
error: no adb device in 'device' state. Enable USB debugging and accept the RSA prompt; check 'adb devices'.
3
```

Fake-adb fixture (host-only, no phone) verified the full report and engine parsing:

```text
selected_model_id=base
backend_setting=AUTO
backend=whisper.cpp/cpu
fallback=gpu-unavailable
gpu_failures=1
last_error=Native whisper.cpp is not available in this APK.
idle_unload_seconds=300
overlay_enabled=true
boot_start=true
native_engine=loaded (backend above)
end_of_report
```

### Build / test gate

```sh
$ export JAVA_HOME=/work/android-toolchain/jdk
$ export ANDROID_HOME=/work/android-toolchain/sdk ANDROID_SDK_ROOT=/work/android-toolchain/sdk
$ export GRADLE_USER_HOME=/home/node/gh-dora
$ ./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --offline --no-daemon --console=plain
BUILD SUCCESSFUL in 15s
47 actionable tasks: 23 executed, 24 up-to-date

$ ls -l app/build/outputs/apk/debug/app-debug.apk
-rw-r--r-- 1 node node 9163557 Oct 26 17:54 app/build/outputs/apk/debug/app-debug.apk
$ sha256sum app/build/outputs/apk/debug/app-debug.apk
9eec641a56e777e036b1c88a7688f8f5e856b64baf4bad25c23b968c3954986a  app-debug.apk
```

Test totals parsed from `*/build/test-results/**/*.xml`:
`tests=135 failures=0 errors=0 skipped=0` (`:core:test` 85 + `:app:testDebugUnitTest` 50).
Default APK has 0 `lib/` entries (`-Pmartins.native=false`).

### Docs link check

A Node script resolved every relative Markdown link in `README.md` and
`docs/*.md`: `checked=52 broken=0`.

## Interfaces / invariants

- Doctor report keeps the DORA-1 field names and adds engine fields. On a
  non-debuggable build or unreadable app data it prints `unavailable`/`not
  recorded`, never a guess.
- Typed reasons come from `core.asr.EngineState`: `native-unavailable`,
  `model-missing`, `gpu-unavailable`, `auto-cpu`, `engine-error`.
- Model root remains `<filesDir>/models/<id>.bin`; the UI and engine share
  `ModelRepositoryProvider`.
- No secrets, transcripts, audio, serial or account data added to the report.

## Limitations / blockers / next steps

- No Android device, GPU or KVM here: every device step (install, download,
  SAF import, `run-as` copy, permission prompts, overlay, insertion, real
  inference, Vulkan fallback) is **UNVERIFIED**.
- The default APK still cannot transcribe; a `-Pmartins.native=true` arm64 build
  with NDK `27.0.12077973` is required and was not built here.
- The APK size/hash differ from ASHA's branch build (9,163,557 B vs 8,938,813 B)
  because this is a fresh debug-signing build; both are valid for their own run.
- Next: at H1, run `FIRST-DICTATION.md`, fill `DEVICE-MATRIX.md`, and replace
  UNVERIFIED markers with real evidence. MARA re-runs the gate during merge.

## References used

- `handoffs/ASHA-1/HANDOFF.md` - G1 engine/model-UI wiring, typed `EngineState`,
  test counts and native-libs limitation.
- `handoffs/DORA-1/HANDOFF.md` - D1 doc/script contract and field names.
- `app/src/main/java/com/martins/whisper/MainActivity.kt`,
  `ui/ModelBrowser.kt`, `service/EngineFactory.kt`,
  `core/.../asr/EngineState.kt`, `EnginePlanner.kt`, `ModelRepository.kt`,
  `data/SettingsStore.kt`, `accessibility/InsertionPolicy.kt` - shipped labels,
  typed reasons, status strings, prefs keys and password fallback behavior.

## Commits

- Base: `b33c8c3` (`merge: D1 operator docs + scripts (DORA-1)`).
- `5241c67` docs(d1): align docs with post-G1 engine and model UI (DORA-2)
- Handoff commit follows this file.
