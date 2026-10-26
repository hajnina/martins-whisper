# G1 Integration Worker - Martins Whisper

You are a background work-package agent for the Android/Kotlin dictation app in `/work/martins-whisper`.

## Read first (in your worktree)
1. `AGENTS.md` (handoff contract: write handoff under `handoffs/G1-integration/HANDOFF.md` + `SUMMARY.txt`)
2. `SPEC.md`, `DECISIONS.md`, `handoffs/PM-2026-10-26-2/HANDOFF.md`
3. `.scratch/device-ready-apk/issues/10-g1-checklist.md` and `docs/INTEGRATION.md`

Your worktree is `/work/martins-whisper-worktrees/g1-integration`, branch `agent/g1-integration`.

## Deliverable (single scope, do not expand)
1. Wire `app/src/main/java/com/martins/whisper/service/EngineFactory.kt` to the real `WhisperCppEngine` JNI bridge + `ModelRepository`/`ModelManager` from `core/`, keeping the `FakeSpeechEngine` for tests. Respect ADR-004 CPU-first: default build has no native lib; engine selection must handle native-unavailable with a clear error/state, never a crash.
2. Model download/import groundwork per `.scratch/device-ready-apk/issues/01-model-facts.md` (base multilingual f16 default, tiny low-end): repository + storage wiring and minimal UI surface for picking/importing a GGML model. Direct HF download, pin SHA-256; no third-party SDKs. If UI is too large for scope, deliver the repository/storage path + a stub screen and a TODO in the handoff - do not silently change shared contracts.
3. Run the gate and record evidence:
```
export JAVA_HOME=/work/android-toolchain/jdk
export ANDROID_HOME=/work/android-toolchain/sdk
export ANDROID_SDK_ROOT=/work/android-toolchain/sdk
export GRADLE_USER_HOME=/home/node/gh-pm
printf 'sdk.dir=/work/android-toolchain/sdk\n' > local.properties
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --offline --no-daemon --console=plain
./gradlew :app:assembleDebugAndroidTest --offline --no-daemon --console=plain
```
The shared Gradle home `/home/node/gh-pm` is used by the sibling PM; do not delete or reconfigure it. Do not claim success without command output.
4. Commit work with clear messages; do not merge to master. No secrets, no telemetry, no new dependencies without justification in the handoff. Keep Kotlin idiomatic, comments only for non-obvious constraints.
5. Write the mandatory handoff: executive summary (<=10 lines), exact file list, copy-paste commands + results, interfaces/invariants/contract changes, evidence, limitations/next agent, references, commit SHAs. Final message must repeat the executive summary.

## Constraints
- Environment: aarch64, no /dev/kvm, no physical device. JVM tests must be green; device work is out of scope (H1/H2).
- If blocked by environment or contracts, still deliver code + precise blocked report + exact unblock steps. Do not fake results.
- Stay in G1 scope; other packages are done or owned elsewhere.
