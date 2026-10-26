# HANDOFF — MARA integration baseline (2026-10-26T18:45Z)

## Executive summary

- Master `1511ca1` + board/handoff commit; clean tree, full gate green offline.
- Completed G1a-1 (catalog swap) before the scope split: app now uses `core.asr.ModelCatalog`; the
  `ui/ModelOptions` stub is deleted; no source references remain.
- Gate: `:core:test` 78 + `:app:testDebugUnitTest` 31 = **109 tests, 0 failures/errors**;
  `:app:assembleDebug` OK. APK `app/build/outputs/apk/debug/app-debug.apk` = 8,873,577 bytes,
  SHA-256 `867dec1f868a9ed72093d33db13453b2f4d02de1e1167f423105b6e02c141354`.
- Scope is split per owner UPDATE: ASHA owns G1a-2/G1b on `agent/g1-engine`, TESS T1-1/T1-2 on
  `agent/t1-harness`, DORA D1-1/D1-2 on `agent/d1-docs`; MARA integrates only.
- Provider is OK since 18:36:31Z; at most 2 workers at a time.
- No device: H1/H2, EMU1, GPU1 remain blocked (owner/ASKS.md).

## Commands actually run

```
export JAVA_HOME=/work/android-toolchain/jdk ANDROID_HOME=/work/android-toolchain/sdk \
       ANDROID_SDK_ROOT=/work/android-toolchain/sdk GRADLE_USER_HOME=/home/node/gh-mara
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --offline --rerun-tasks
```

Output (tail): `BUILD SUCCESSFUL in 9s` / `47 actionable tasks: 47 executed`.

Test XML totals (`*/build/test-results/**/*.xml`):

```
core: tests=78 skipped=0 failures=0 errors=0
app : tests=31 skipped=0 failures=0 errors=0
```

APK:

```
8,873,577 bytes
867dec1f868a9ed72093d33db13453b2f4d02de1e1167f423105b6e02c141354  app-debug.apk
```

## What changed on master

- `1511ca1` feat(g1): `MainActivity` and `DictationService` use `core.asr.ModelCatalog`; delete
  `app/.../ui/ModelOptions.kt`; `SettingsDefaultsTest` asserts catalog coverage and default-id parity;
  strings stub note replaced with a model note.
- `4ded11b` chore(pm): team split, refreshed KANBAN/STATUS, corrected stale repo `PROVIDER-STATUS.md`,
  build tickets under `.scratch/device-ready-apk/build-tickets/`.

## Branch readiness

| Branch | Head | State | Merge when |
|---|---|---|---|
| `agent/g1-engine` (ASHA) | `1511ca1` | clean, tickets assigned | handoff + green gate |
| `agent/t1-harness` (TESS) | `1511ca1` | clean, worktree registered | handoff + green gate |
| `agent/d1-docs` (DORA) | `1511ca1` | clean (stale checkpoint discarded) | handoff + docs lint |
| `master` | this commit | clean | - |

## Integration rules used

- Never merge without a worker handoff containing real command output and SHAs.
- Full gate on the merge result: `:core:test :app:testDebugUnitTest :app:assembleDebug --offline`.
- Keep `GRADLE_USER_HOME=/home/node/gh-mara`; never touch the shared toolchain cache.

## Next actions

1. ASHA: G1a-2 then G1b; merge from `handoffs/ASHA-1/HANDOFF.md`.
2. TESS: T1-1/T1-2; merge from `handoffs/TESS-1/HANDOFF.md`.
3. DORA: D1-1/D1-2; merge from `handoffs/DORA-1/HANDOFF.md`.
4. Owner: push `master` when convenient (no secrets; clean tree).

## Known limitations

- Default APK has no native whisper.cpp; `WhisperCppEngine.isNativeAvailable == false` on host. The
  wired path must show a typed error until a `-Pmartins.native=true` build runs on a device.
- No physical device/KVM/GPU: H1/H2 cannot run here.
