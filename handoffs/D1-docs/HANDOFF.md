# D1-docs handoff — operator/user documentation + scripts

## Executive summary (<=10 lines)

- Delivered the D1 doc set for map ticket 06: INSTALL, PERMISSIONS, MODELS,
  PRIVACY, DEVICE-MATRIX, TROUBLESHOOTING, DOCTOR, plus README index and a
  refreshed BUILD.md verified block.
- Added three operator scripts: `install-apk.sh` (adb install), `push-model.sh`
  (SHA-256-checked push to `files/models/<id>.bin` via `run-as`), and
  `doctor.sh` (pasteable, secret-free report).
- Every documented host command was run or is marked UNVERIFIED. All device
  steps are marked **UNVERIFIED (device, H1-blocked)**; no device results claimed.
- Build evidence: 109 JVM tests, 0 failures; APK 8,873,577 bytes, SHA-256
  `004e39e4...`; tiny model download verified against its catalog SHA-256.
- **Known product gap, not a docs defect:** 0.1.0 still uses `FakeSpeechEngine`
  and has no in-app model download/import screen (G1). The docs state this
  plainly; "APK to real dictation" acceptance is blocked until G1 + H1.
- Branch `agent/d1-docs`, feature commit `561b059`; handoff commit is HEAD after
  this file. No merge into `master` (MARA integrates).

## What changed / exact file list

New:

- `docs/INSTALL.md` — operator + non-technical user sideload paths, first-run
  checklist, upgrade/uninstall.
- `docs/PERMISSIONS.md` — mic, overlay, accessibility, notifications, battery,
  boot behavior; grant and revoke steps.
- `docs/MODELS.md` — catalog/sizes/hashes, on-disk `files/models/<id>.bin`,
  pinned HF download, adb push, storage/RAM guidance, idle unload, delete.
- `docs/PRIVACY.md` — local-only audio/transcripts, no telemetry/account, the one
  network use, accessibility scope, clipboard, doctor exclusions.
- `docs/DEVICE-MATRIX.md` — support tiers, H1 checklist, 8-field insertion matrix,
  H2 thresholds, empty tested-device table, OEM placeholders.
- `docs/TROUBLESHOOTING.md` — install/mic/overlay/insertion/model/engine failures
  with a concrete next step, including the 0.1.0 fake-engine gap.
- `docs/DOCTOR.md` — report fields, script usage, exclusions, bug-report template,
  planned in-app doctor fields.
- `scripts/install-apk.sh` — adb install -r, `--apk`, `--serial`, `--help`;
  exit 2 missing adb, 3 no device.
- `scripts/push-model.sh` — derives id, verifies catalog SHA-256, refuses
  overwrite without `--force`, pushes to `/data/local/tmp`, copies with
  `run-as` to `files/models/<id>.bin`, chmod 600, cleans temp.
- `scripts/doctor.sh` — device/ABI/Android/RAM/storage, app version/APK path,
  permission states, model files/sizes/SHA-256, `--serial`, `--help`; never
  prints device serial or transcripts.

Modified:

- `README.md` — documentation index + current 0.1.0 status (fake engine/G1 gap).
- `docs/BUILD.md` — replaced the stale "NO-SOURCE / 8,578,276 B" block with the
  real 2026-10-26 run (109 tests, APK 8,873,577 B, private `GRADLE_USER_HOME`);
  fixed the `TOOLCHAIN.md` reference to `docs/native-build.md`.
- `docs/DEVICE_TEST_MATRIX.md` — added a pointer to `DEVICE-MATRIX.md`; kept the
  longer exploratory matrix as a hardening checklist.

## Verification (real commands and output)

All scripts:

```sh
$ cd /work/martins-whisper-worktrees/d1-docs
$ for f in scripts/*.sh; do bash -n "$f" || exit 1; done
# exit 0, no output for all six scripts
```

Help paths (representative):

```sh
$ ./scripts/install-apk.sh --help   # exit 0, usage printed
$ ./scripts/push-model.sh --help    # exit 0, usage printed
$ ./scripts/doctor.sh --help        # exit 0, usage printed
```

Failure paths (no device in this sandbox):

```sh
$ ADB=/tmp/nope ./scripts/doctor.sh
error: ADB=/tmp/nope is not executable
# exit 2

$ ./scripts/doctor.sh
error: no adb device in 'device' state. Enable USB debugging and accept the RSA prompt; check 'adb devices'.
# exit 3

$ ./scripts/push-model.sh /tmp/ggml-tiny.bin
Model:  /tmp/ggml-tiny.bin
Id:     tiny
Bytes:  77691713
SHA256: be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21
Hash:   OK (matches the catalog)
error: no adb device in 'device' state. ...
# exit 3

$ ./scripts/push-model.sh --id base /tmp/ggml-tiny.bin
error: SHA-256 mismatch for 'base': expected 60ed5bc3..., got be07e048e...
# exit 1
```

Model download (host, pinned revision):

```sh
$ curl -fL --retry 2 -o /tmp/ggml-tiny.bin \
  "https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-tiny.bin"
$ sha256sum /tmp/ggml-tiny.bin
be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21  /tmp/ggml-tiny.bin
# 77,691,713 bytes, matches ModelCatalog
```

Build (private Gradle cache, offline, no daemon):

```sh
$ source scripts/env.sh
$ export GRADLE_USER_HOME=/home/node/gh-dora
$ ./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --offline --no-daemon --console=plain
BUILD SUCCESSFUL in 21s
47 actionable tasks: 36 executed, 11 from cache

$ ls -l app/build/outputs/apk/debug/app-debug.apk
-rw-r--r-- 8873577 app-debug.apk
$ sha256sum app/build/outputs/apk/debug/app-debug.apk
004e39e4c6c01b22dc5dc53aeb5c4253e257a5ab407159b3e2395f689630867c  app-debug.apk
```

Test totals from XML: `tests=109 failures=0 errors=0 skipped=0`.

APK facts: `aapt2 dump badging` → package `com.martins.whisper`, versionCode 1,
versionName 0.1.0, minSdk 29, targetSdk 35. `unzip -l | grep lib/` = 0 (default
APK has no native libs), consistent with the docs.

Link check: every relative `*.md` link in README/docs resolves (scripted check,
no broken links).

## Interfaces / invariants

- Package `com.martins.whisper`; model dir `files/models/`; file name
  `<catalog id>.bin` (not the upstream `ggml-*.bin` name).
- Catalog ids and SHA-256 values match
  `core/.../asr/ModelCatalog.kt` and `research/01-model-facts.md`.
- `push-model.sh` requires a debuggable APK because it uses `run-as`; it refuses
  to overwrite without `--force` and refuses a hash mismatch.
- No app/core/Kotlin/build-logic files were changed; docs/scripts/README only.
- No secrets, tokens, keystores or private account data in docs or scripts.
  Doctor report deliberately omits the device serial.

## Known limitations / blockers

- **G1:** `EngineFactory` still returns `FakeSpeechEngine`; there is no in-app
  download/import/delete UI. `MODELS.md`, `INSTALL.md`, `TROUBLESHOOTING.md` and
  `DOCTOR.md` say so explicitly. Real dictation is blocked on G1.
- **H1/H2:** no physical device or ADB access here. All device instructions,
  permission prompts, model push, insertion matrix and H2 numbers are
  **UNVERIFIED** and marked as such. `DEVICE-MATRIX.md` has no tested-device rows.
- **Boot behavior:** `BootReceiver` restores the overlay with `EXTRA_NO_MIC=true`;
  ADR-007's tap-to-re-arm notification is not implemented. Docs describe the
  actual behavior (bubble without mic until the app is opened).
- The in-app doctor screen is planned; `doctor.sh` is the only current report.

## What the next agent should do

1. After G1: update `MODELS.md`/`INSTALL.md`/`DOCTOR.md` from "planned" to the
   shipped UI, and verify the download/import/delete paths.
2. On H1: run the checklist and 8-field matrix, fill `DEVICE-MATRIX.md`, and
   replace every UNVERIFIED marker with the real result/evidence.
3. Keep `doctor.sh` field names in sync with the in-app doctor screen.

## References used

- Map ticket 06 `.scratch/device-ready-apk/issues/06-d1-docs-matrix.md` — required
  docs, two audiences, support matrix, doctor fields.
- Map ticket 08 `.scratch/device-ready-apk/issues/08-h1-h2-thresholds.md` — 8-field
  insertion matrix, H1 checklist, H2 thresholds.
- `research/01-model-facts.md` — model sizes, SHA-256, HF pin, memory, license.
- `core/.../asr/ModelCatalog.kt`, `ModelStorage.kt`, `ModelRepository.kt` — ids,
  hashes, `<id>.bin` layout, verification.
- `app/.../MainActivity.kt`, `PermissionGate.kt`, `SettingsStore.kt`,
  `AndroidManifest.xml`, `strings.xml`, `DictationService.kt`, `BootReceiver.kt`
  — UI strings, permission rows, defaults, boot behavior.
- `handoffs/PM-2026-10-26-2/HANDOFF.md` — build/test state and G1 gap.
- `SPEC.md`, `DECISIONS.md` (ADR-003/004/007) — privacy, insertion and boot rules.

## Commits

- `561b059` docs(d1): install/permissions/models/privacy/troubleshooting/doctor +
  operator scripts
- Handoff commit: HEAD of `agent/d1-docs` after this file (see `git log`).
