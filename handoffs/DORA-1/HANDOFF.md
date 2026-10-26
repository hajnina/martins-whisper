# DORA-1 handoff - D1 docs + operator scripts

## Executive summary (<=10 lines)

- Delivered the D1 doc set from ticket 06: README index, refreshed BUILD, INSTALL,
  PERMISSIONS, MODELS, PRIVACY, DEVICE-MATRIX, TROUBLESHOOTING and DOCTOR.
- Added three operator scripts: `install-apk.sh`, `push-model.sh` (catalog
  SHA-256 enforced) and `doctor.sh` (pasteable, secret-free report).
- Host checks pass: `bash -n` and shellcheck clean; no-device paths fail with a
  clear message and exit 3; a bad hash exits 1.
- Gradle gate green: 109 tests, 0 failures; APK 8,873,577 bytes; default APK has
  zero native libs. `tiny` and `base` model downloads match their catalog hashes.
- Every phone-dependent step is marked **UNVERIFIED (device, H1-blocked)**.
  Real dictation remains blocked by G1 (0.1.0 still uses the fake engine and has
  no in-app model UI); the docs say so instead of overclaiming the acceptance.
- Branch `agent/d1-docs`; MARA integrates. No merge or push was performed.

## What changed / exact file list

Docs (all under `docs/` unless noted):

- `README.md` - documentation index and current 0.1.0 status.
- `docs/BUILD.md` - restored a real verified build block, private
  `GRADLE_USER_HOME` guidance, corrected the aapt2/build-tools version and
  recorded a second run hash.
- `docs/INSTALL.md` - APK path, operator and non-technical sideload paths, first
  run, upgrade, uninstall/cleanup; model-push location.
- `docs/PERMISSIONS.md` - microphone, overlay, accessibility, notifications,
  battery, boot behavior; grant/revoke steps.
- `docs/MODELS.md` - catalog, sizes, full/pinned hashes, download, adb push,
  storage/RAM, idle unload, delete, no cloud.
- `docs/PRIVACY.md` - local-only audio/transcripts, no telemetry/account, the one
  network use, accessibility scope, clipboard, doctor exclusions.
- `docs/DEVICE-MATRIX.md` - supported/best-effort/unsupported tiers, H1 checklist,
  8-field insertion matrix, H2 thresholds, empty tested-device table, OEM notes.
- `docs/DEVICE_TEST_MATRIX.md` - pointer to the v1 gate plus the longer
  exploratory checklist kept for hardening.
- `docs/TROUBLESHOOTING.md` - install, mic, overlay, insertion, model, OOM and
  engine failures, each with a concrete next step.
- `docs/DOCTOR.md` - report fields, script usage, exclusions, bug-report template,
  planned in-app doctor fields.

Scripts:

- `scripts/install-apk.sh` - `adb install -r`; `--apk`, `--serial`, `--help`;
  exit 2 for missing adb and 3 for no usable device.
- `scripts/push-model.sh` - derives the catalog id, verifies the catalog SHA-256,
  refuses overwrite without `--force`, pushes via `/data/local/tmp` and
  `run-as` into `files/models/<id>.bin`, `chmod 600`, removes the temp file.
- `scripts/doctor.sh` - device/ABI/Android/RAM/storage, app version/APK path,
  permission states, model files/sizes/SHA-256; never prints the serial,
  transcripts or audio.

Handoffs:

- `handoffs/D1-docs/HANDOFF.md` and `handoffs/D1-docs/SUMMARY.txt` (earlier D1
  checkpoint).
- `handoffs/DORA-1/HANDOFF.md` and `handoffs/DORA-1/SUMMARY.txt` (this file).

No `app/`, `core/`, `build.gradle*`, `settings.gradle*` or Kotlin source was
edited.

## Verification - real commands and output

All commands below were run in `/work/martins-whisper-worktrees/d1-docs` on
2026-10-26.

### Script syntax and lint

```sh
$ bash -n scripts/*.sh
# exit 0, no output

$ nix run nixpkgs#shellcheck -- scripts/install-apk.sh scripts/push-model.sh scripts/doctor.sh
# exit 0, no findings
```

### No-device and error paths

```sh
$ scripts/install-apk.sh
error: no adb device in 'device' state. Enable USB debugging and accept the RSA prompt; check 'adb devices'.
# exit 3

$ scripts/doctor.sh
error: no adb device in 'device' state. Enable USB debugging and accept the RSA prompt; check 'adb devices'.
# exit 3

$ scripts/push-model.sh
error: no model file given (try --help)
# exit 1

$ scripts/push-model.sh /home/node/ggml-tiny.bin
Model:  /home/node/ggml-tiny.bin
Id:     tiny
Bytes:  77691713
SHA256: be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21
Hash:   OK (matches the catalog)
error: no adb device in 'device' state. Enable USB debugging and accept the RSA prompt; check 'adb devices'.
# exit 3

$ scripts/push-model.sh --id base /home/node/ggml-tiny.bin
error: SHA-256 mismatch for 'base': expected 60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe, got be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21
# exit 1

$ ADB=/tmp/nope scripts/install-apk.sh --apk app/build/outputs/apk/debug/app-debug.apk
error: ADB=/tmp/nope is not executable
# exit 2
```

Help paths for all three scripts exit 0 and print usage.

### Build / test gate

```sh
$ source scripts/env.sh
$ export GRADLE_USER_HOME=/home/node/gh-dora
$ ./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --offline --no-daemon --console=plain
BUILD SUCCESSFUL in 11s
47 actionable tasks: 1 executed, 46 up-to-date

$ ls -l app/build/outputs/apk/debug/app-debug.apk
-rw-r--r-- 1 node node 8873577 Oct 26 17:46 app/build/outputs/apk/debug/app-debug.apk
$ sha256sum app/build/outputs/apk/debug/app-debug.apk
867dec1f868a9ed72093d33db13453b2f4d02de1e1167f423105b6e02c141354  app-debug.apk
```

Test totals parsed from `*/build/test-results/**/*.xml`:
`tests=109 failures=0 errors=0 skipped=0`.

An earlier full run in the same worktree was `BUILD SUCCESSFUL in 21s`,
`47 actionable tasks: 36 executed, 11 from cache`, APK `8873577` bytes,
SHA-256 `004e39e4c6c01b22dc5dc53aeb5c4253e257a5ab407159b3e2395f689630867c`.
The debug signature timestamp changes the hash on every rebuild; both hashes are
valid for their own run.

APK facts:

```sh
$ unzip -l app/build/outputs/apk/debug/app-debug.apk | grep -c ' lib/'
0
$ aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk | head
package: name='com.martins.whisper' versionCode='1' versionName='0.1.0' ...
targetSdkVersion:'35'
```

- Package `com.martins.whisper`, versionCode 1, versionName 0.1.0, targetSdk 35,
  minSdk 29 (from the build files).
- Default APK has no `lib/` entries, consistent with
  `-Pmartins.native=false` and the documented "no native libs" fact.

### Model downloads (host, pinned revision)

```sh
$ curl -fL --retry 2 -o /home/node/ggml-tiny.bin \
  "https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-tiny.bin"
$ sha256sum /home/node/ggml-tiny.bin
be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21  /home/node/ggml-tiny.bin
# 77,691,713 bytes

$ curl -fL --retry 2 -o /home/node/ggml-base.bin \
  "https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-base.bin"
$ sha256sum /home/node/ggml-base.bin
60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe  /home/node/ggml-base.bin
# 147,951,465 bytes
```

Both match `core/.../asr/ModelCatalog.kt` and `research/01-model-facts.md`.

### Documentation link check

A Node script resolved every relative Markdown link in `README.md` and
`docs/*.md`: `no broken links`.

## Interfaces / invariants

- Package id: `com.martins.whisper`.
- App-private model directory: `files/models/` (`/data/data/com.martins.whisper/files/models/`);
  destination file is `<catalog-id>.bin`, not the upstream `ggml-*.bin` name.
- Catalog ids accepted by the script: `tiny tiny.en base base.en small small.en
  medium medium.en large-v3-turbo`; each has a fixed SHA-256 in `push-model.sh`.
- `push-model.sh` requires a debuggable APK because it uses `run-as`; it refuses
  a hash mismatch, an unknown id, or an overwrite without `--force`.
- `push-model.sh` exit codes: 1 generic/hash/model-file error, 2 adb missing,
  3 no usable device, 4 `run-as` unavailable, 5 existing destination without
  `--force`.
- `install-apk.sh` exit codes: 1 generic, 2 adb missing, 3 no usable device.
- `doctor.sh` exit codes: 0 report printed, 2 adb missing, 3 no usable device.
- Doctor field names are the contract for the future in-app doctor screen; it
  deliberately omits the device serial, transcripts, audio and full package dump.
- No secrets, tokens, keystores or account data were added.
- No contract or code files were changed; the docs describe the current 0.1.0
  behavior (fake engine, no in-app model UI, boot bubble without mic).

## UNVERIFIED list (with reason)

Every item below needs a real arm64 device; this sandbox has no phone, no ADB
target, no KVM and no emulator.

- `adb install -r` and the in-app/from-files sideload flow.
- All Android runtime permission prompts and their locations on Android 10-15.
- Overlay/display-over-other-apps behavior, accessibility enablement and
  insertion into the 8-field matrix.
- `push-model.sh` copy into app-private storage and the final file listing.
- `doctor.sh` values on a phone (permission states, model listing, RAM/storage).
- Uninstall/cleanup, upgrade over the same debug key, reboot/boot behavior.
- H1 functional checklist and H2 latency/RSS/unload thresholds.
- In-app doctor screen (planned, not implemented).
- Real whisper.cpp inference and GPU/Vulkan fallback (G1; current APK has no
  native libs).
- Docker `--platform linux/amd64` path (not executable here: no binfmt/QEMU) and
  GitHub Actions (no GitHub credentials/runner here); both are documented as CI
  paths, not locally verified.
- "A new user can install and dictate from INSTALL + PERMISSIONS alone" cannot be
  demonstrated until G1 (real engine + model UI) and H1 (device) land.

All such steps are labelled **UNVERIFIED** in the docs rather than presented as
results.

## Known limitations / blockers

- **G1:** `EngineFactory` still uses a fake engine in 0.1.0 and there is no
  in-app download/import/delete UI. Docs state this clearly; real dictation is
  blocked on G1.
- **H1/H2:** no physical device or ADB access. `DEVICE-MATRIX.md` has no tested
  device rows; thresholds are proposals.
- **Branch base:** this branch was cut from `1511ca1`; master is at `a8b45d0`
  and contains later PM/ticket commits. This branch does not include those
  commits, so MARA should do a normal merge (no rebase was performed here).
- `doctor.sh` is the only current diagnostics path; the in-app doctor screen is
  still planned.
- Boot behavior is the implemented one: the bubble returns without the
  microphone until the app is opened once.

## What the next agent should do

1. After G1: update `MODELS.md`, `INSTALL.md` and `DOCTOR.md` from "planned" to
   the shipped model UI and verify download/import/delete on a device.
2. At H1: run the checklist and insertion matrix, fill `DEVICE-MATRIX.md`, and
   replace UNVERIFIED markers with real evidence.
3. Keep `doctor.sh` field names aligned with the in-app doctor.
4. MARA: merge `agent/d1-docs` into master normally; do not cherry-pick the
   branch's diff against current master because the branch is behind.

## References used

- `.scratch/device-ready-apk/issues/06-d1-docs-matrix.md` - audiences, support
  matrix, required docs, doctor fields.
- `.scratch/device-ready-apk/issues/08-h1-h2-thresholds.md` - insertion matrix,
  H1 checklist, H2 thresholds.
- `.scratch/device-ready-apk/issues/09-device-access.md` - H1 device still open;
  no hardware available.
- `research/01-model-facts.md` - model sizes, SHA-256, pinned HF revision,
  memory, license.
- `core/.../asr/ModelCatalog.kt`, `ModelStorage.kt`, `ModelRepository.kt` -
  ids, hashes, `<id>.bin` layout, verification.
- `app/.../MainActivity.kt`, `PermissionGate.kt`, `SettingsStore.kt`,
  `AndroidManifest.xml`, `strings.xml`, `DictationService.kt`, `BootReceiver.kt`
  - UI strings, permission rows, defaults, boot behavior.
- `SPEC.md`, `DECISIONS.md` (ADR-003/004/007) - privacy, insertion and boot rules.
- `handoffs/PM-2026-10-26-2/HANDOFF.md` and `/work/martins-whisper/handoffs/PARK-oc-eph-226089.md`
  - build state, G1 gap and environment traps.
- `/work/android-toolchain/TOOLCHAIN-READY.md` - JDK/SDK/Gradle paths and
  aarch64 aapt2 override.

## Commits

Base: `1511ca1` (`feat(g1): use core ModelCatalog instead of the app stub`).

- `561b059` docs(d1): install/permissions/models/privacy/troubleshooting/doctor +
  operator scripts
- `14e02de` docs(d1): handoff + restrict model push to catalog ids
- `fe6461d` docs(d1): verify base+model download hashes in evidence
- `1340d9b` docs(d1): record final commit list in handoff
- `f29819e` docs(d1): shellcheck-clean operator scripts
- `17f8f9c` docs(d1): correct aapt2 build-tools version and record rerun hash
- Final commit: this handoff file on `agent/d1-docs` (see `git log`).
