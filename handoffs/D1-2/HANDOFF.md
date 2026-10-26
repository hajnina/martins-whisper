# D1-2 handoff - docs aligned with the real G1 engine and model UI

## Executive summary (<=10 lines)

- Started while master had G1 but **not** `agent/d1-docs`; wrote the required
  `docs/FIRST-DICTATION.md` fallback from `app/src`/`core/src`.
- Master then merged D1 (`b33c8c3`), so the main task also ran: `MODELS.md` and
  `INSTALL.md` now describe the real Model screen (select, Download, Import,
  Delete, progress, `Installed · selected`) and the real `EngineFactory` →
  `WhisperCppEngine` wiring.
- The default APK still has **no native library**; dictation needs an arm64
  `-Pmartins.native=true` build (NDK 27.0.12077973). This is now prominent.
- Also corrected stale claims in README/BUILD/DOCTOR/PRIVACY/DEVICE-MATRIX/
  TROUBLESHOOTING/PERMISSIONS and taught `doctor.sh` to read persisted engine
  state via `run-as`.
- Verified: 135 JVM tests/0 failures, 52 doc links/0 broken, shellcheck clean,
  no-device exit 3, fake-adb report fields. No `app/`/`core/` edits.
- Device/native steps remain **UNVERIFIED (H1-blocked)**.

## Ticket and master condition

The ticket said: rebase once MARA has merged `agent/d1-docs` **and** G1, then
rewrite MODELS/INSTALL; otherwise write FIRST-DICTATION.

- At start: master had G1 (`fcb8299`) but not D1 (`agent/d1-docs` was not an
  ancestor). Fallback path started.
- Mid-session: MARA merged D1 as `b33c8c3`, then the integration wave as
  `def7b1c`. Branch was fast-forwarded/re-based onto `master` (`def7b1c`) with no
  conflicts. Both paths were therefore delivered.

## What changed / exact file list

- `docs/FIRST-DICTATION.md` (new) - native APK check, install, 3 required
  permissions + optional notifications, model by download/import/adb, diagnostics,
  overlay, first dictation, expected states.
- `docs/MODELS.md` - G1 model screen and actions; confirmed prediction, SHA-256
  and `.part` resume; native-library caveat; updated delete/idle-unload text.
- `docs/INSTALL.md` - native build command and expected `.so`; in-app model path;
  corrected permission count; FIRST-DICTATION link.
- `README.md` - FIRST-DICTATION index entry.
- `docs/BUILD.md` - 135-test gate, historical D1 numbers labelled historical,
  native opt-in clarified.
- `docs/DOCTOR.md`, `docs/TROUBLESHOOTING.md`, `docs/PRIVACY.md`,
  `docs/DEVICE-MATRIX.md`, `docs/PERMISSIONS.md` - post-G1 truth and password
  clipboard wording.
- `scripts/doctor.sh` - reads `shared_prefs/martins_whisper_settings.xml` via
  `run-as`; prints `selected_model_id`, `backend_setting`, `backend`, `fallback`,
  `gpu_failures`, `last_error`, idle/overlay/boot and `native_engine`.
- `handoffs/D1-2/` - this ticket record. Full detail is also in
  `handoffs/DORA-2/` (the PM's concurrent name for the same docs pass).
- `handoffs/D1-docs/` was removed as a duplicate checkpoint; `handoffs/DORA-1/`
  remains the D1 record.

No app/core Kotlin, Gradle, KANBAN, LOGBOOK, SPEC or DECISIONS edits.

## Commands and real results

```sh
$ git merge-base --is-ancestor agent/d1-docs master; echo $?
0    # after MARA merged D1
$ git rebase master
Successfully rebased and updated refs/heads/agent/d1-docs.
```

Independent build of the G1 tree (`/tmp/mw-master`, private cache,
`GRADLE_USER_HOME=/home/node/gh-dora`):

```text
BUILD SUCCESSFUL in 24s
tests=135 failures=0 errors=0 skipped=0
APK 8,938,813 bytes
SHA-256 558253ace3167d8bfa67d3a15083abc14e4936d47a167070b9ff1eab0746c172
lib/ entries in default APK: 0
```

The DORA-2 session's own gate recorded 135 tests and a 9,163,557-byte debug APK;
debug-signing/zip differences make APK hashes run-specific. Both runs agree on
the test count and `0` native libs.

Scripts/docs:

```sh
$ for f in scripts/*.sh; do bash -n "$f" || exit 1; done      # exit 0
$ nix run nixpkgs#shellcheck -- scripts/*.sh               # exit 0
$ ./scripts/doctor.sh; echo $?
error: no adb device in 'device' state. ...
3
$ node <link-check>                                        # checked=52 broken=0
$ grep -rniE "fake ?speech ?engine|no in-app model|until G1|no download screen|planned \(G1\)|not wired yet|stub engine" docs README.md
# no matches
```

Fake-adb fixture (host-only) exercised the full report, including:

```text
selected_model_id=base
backend_setting=AUTO
backend=whisper.cpp/cpu
fallback=gpu-unavailable
gpu_failures=1
idle_unload_seconds=300
overlay_enabled=true
boot_start=true
native_engine=loaded (backend above)
end_of_report
```

## Interfaces / invariants

- Model root stays `<filesDir>/models/<id>.bin`; UI and engine share
  `ModelRepositoryProvider`.
- Typed failures remain `native-unavailable`, `model-missing`, `gpu-unavailable`,
  `auto-cpu`, `engine-error`.
- Doctor field names from D1 are preserved; engine fields are additive. Unreadable
  app data prints `unavailable`/`not recorded`, never a guess.
- Password/PIN fields are never inserted into; the clipboard fallback is reported
  as copied, not as insertion success.

## Evidence and commits

- Base after rebase: `def7b1c` (integration wave 1, 135 tests).
- `702de1c` docs(d1): align docs with post-G1 engine and model UI (DORA-2)
- `1be5c16` docs(d1): DORA-2 handoff and summary
- This handoff commit follows.
- Pre-rebase equivalents: `5241c67`, `c7e678d` (same content).

## Known limitations / next

- No phone/ADB/NDK here: install, permission prompts, download/SAF import,
  `run-as` push, overlay, insertion and real inference are **UNVERIFIED**.
- Native arm64 APK was not built here; default APK cannot transcribe.
- H1: follow `FIRST-DICTATION.md`, fill `DEVICE-MATRIX.md`, run the doctor on a
  device. H2: latency/RAM thresholds remain targets.
- MARA: merge this branch's two doc commits; re-run the gate (docs-only diff, but
  run `:core:test :app:testDebugUnitTest` per policy).

## References

- `handoffs/ASHA-1/HANDOFF.md` - G1 engine/model UI, typed state, native gap.
- `handoffs/DORA-1/HANDOFF.md` - D1 docs/scripts contract and doctor fields.
- Code read on master: `MainActivity.kt`, `ui/ModelBrowser.kt`,
  `service/EngineFactory.kt`, `asr/EnginePlanner.kt`, `asr/WhisperCppEngine.kt`,
  `data/SettingsStore.kt`, `accessibility/InsertionPolicy.kt`,
  `accessibility/DictationAccessibilityService.kt`.
- `handoffs/DORA-2/HANDOFF.md` - full DORA-2 evidence and file-by-file rationale.
