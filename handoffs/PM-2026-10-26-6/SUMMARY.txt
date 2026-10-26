# Integration wave 3: G2 (ASHA-2) — manager MARA, 2026-10-26

## Executive summary (<= 10 lines)
- Merged `agent/g2-engine` into master from `handoffs/ASHA-2/HANDOFF.md` (owner directive; CI broken on GitHub, local gate authoritative).
- G2a: custom (non-catalog) GGML import — persist (`CustomModelCodec` v1, Base64 text records), list/select/delete in `ModelBrowser`/`MainActivity`, resolution in `EnginePlanner`.
- G2b: `DictationEngineHost` — audio capture starts before engine load (600 s ordered buffer), lazy load/switch, idle unload (min 30 s, default 300 s). One merge conflict was pre-resolved by ASHA on the branch (kept TESS's `audioSourceFactory` seam).
- `git merge --no-ff` into `ba6b160`, no conflicts. Master at `01fa735`.
- Full gate (MARA env: JAVA_HOME=/work/android-toolchain/jdk, GRADLE_USER_HOME=/home/node/gh-mara, --offline --rerun-tasks): BUILD SUCCESSFUL, 73 tasks executed.
- Tests: core 89 + app 66 = **155, 0 failures, 0 errors** — equals ASHA's branch gate.
- APK: 8,970,737 B, SHA-256 `b6fe0ecbe1c3d57def2ab5289a44550825d947289d7f00a2fbd17217594e49dc`; androidTest APK 1,326,409 B still builds.

## Commands actually run
```
cd /work/martins-whisper
git merge --no-ff agent/g2-engine -m "merge: G2 custom model import + record-while-loading/idle-unload (ASHA-2)"
export JAVA_HOME=/work/android-toolchain/jdk ANDROID_HOME=/work/android-toolchain/sdk \
  ANDROID_SDK_ROOT=/work/android-toolchain/sdk GRADLE_USER_HOME=/home/node/gh-mara
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest \
  --offline --rerun-tasks --no-daemon --console=plain
```
`BUILD SUCCESSFUL in 33s, 73 actionable tasks: 73 executed`

## Not yet merged / in flight
- W1 CODER on `agent/m1-release` (signing/versioning per issue 07) — no handoff yet; merge when `handoffs/W1-release/` appears.
- W2 CRITIC on `agent/m2-checklist` (issue 10 verification + gap tickets) — no handoff yet; merge when `handoffs/W2-checklist/` appears.
- NORA `agent/n1-native` — no handoff yet; pre-merge-check when it appears.

## Next
1. Await W1/W2 handoffs; merge one at a time with the full gate.
2. H1/H2 device-blocked (`owner/ASKS.md`).
