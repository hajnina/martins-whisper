# HANDOFF — MARA integration wave 1 (2026-10-26T18:57Z)

## Executive summary

- Merged ASHA's G1 (`fcb8299`) and DORA's D1 (`b33c8c3`) into master; working tree clean.
- Full gate on the merged tree, offline: **135 tests, 0 failures** (`:core:test` 85, `:app:testDebugUnitTest` 50);
  `:app:assembleDebug` OK. APK 8,938,813 bytes, SHA-256
  `bebb88356ea05e89536615f71dddcb9de239f20e89626432af1d62d2e4f6657c`.
- G1: real `EngineFactory` -> installed catalog model -> `WhisperCppEngine`; typed
  `SpeechEngineUnavailableException` reasons (`native-unavailable`, `model-missing`), persisted
  `EngineState` (backend, fallback, GPU failures, last error); catalog model screen with installed
  state + download/SAF import/delete + diagnostics. `FakeSpeechEngine` removed from app main.
- D1: INSTALL/PERMISSIONS/MODELS/PRIVACY/DEVICE-MATRIX/TROUBLESHOOTING/DOCTOR docs + README, and
  `install-apk.sh` / `push-model.sh` (SHA-256) / `doctor.sh` (secret-free). MARA re-ran `bash -n` and
  the no-device failure paths.
- Still running: TESS (T1-1/T1-2) and DORA-2 (correct pre-G1 stale docs). H1/H2 remain device-blocked.

## Commands and real output

```
export JAVA_HOME=/work/android-toolchain/jdk ANDROID_HOME=/work/android-toolchain/sdk \
       ANDROID_SDK_ROOT=/work/android-toolchain/sdk GRADLE_USER_HOME=/home/node/gh-mara
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --offline --rerun-tasks
```

```
BUILD SUCCESSFUL in 9s
47 actionable tasks: 47 executed
```

Test XML totals:

```
core: tests=85 skipped=0 failures=0 errors=0
app : tests=50 skipped=0 failures=0 errors=0
```

APK:

```
8,938,813 bytes
bebb88356ea05e89536615f71dddcb9de239f20e89626432af1d62d2e4f6657c  app-debug.apk
```

D1 scripts re-checked by MARA on `agent/d1-docs`:

```
bash -n scripts/*.sh                  # all ok
scripts/install-apk.sh                # exit 3, clear no-device message
scripts/doctor.sh                     # exit 3, clear no-device message
scripts/push-model.sh --id tiny ...   # exit 1/3, clear message
```

## Merges

| Merge | Branch | SHA | Handoff |
|---|---|---|---|
| ASHA G1a-2 + G1b | `agent/g1-engine` | `fcb8299` | `handoffs/ASHA-1/` |
| DORA D1-1 + D1-2 | `agent/d1-docs` | `b33c8c3` | `handoffs/D1-docs/` + `handoffs/DORA-1/` |

## Open follow-ups

- DORA-2: remove stale "uses FakeSpeechEngine / no in-app model screen" claims; document typed
  engine errors and the in-app model flow (docs-only).
- `EngineState.loaded()` counts an AUTO->CPU load in `gpuFailureCount`; harmless today (no consumer),
  but the 2-strike GPU policy should only count explicit GPU requests / real GPU failures. Backlog.
- Default APK still has no native libs; real transcription needs `-Pmartins.native=true` on an arm64
  device. H1/H2 unblock requires hardware (owner/ASKS.md).

## Next

1. TESS T1-1/T1-2 -> merge from `handoffs/TESS-1/`, re-run this gate (plus
   `:app:assembleDebugAndroidTest`).
2. DORA-2 -> merge docs correction, no app change expected.
3. Owner: push `master` (private repo) when convenient; tree is clean, no secrets in these diffs.
