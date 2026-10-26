# M1 — signing, versioning, release build (issue 07)
Manager: MARA | Role: CODER (W1) | Branch: `agent/m1-release` (worktree `/work/martins-whisper-worktrees/m1-release`)
Status: running(deepseek-v4.1-flash-free, start 2026-10-26 ~19:25Z) | Retry cap: 2
Boundary: ONLY this worktree. Files owned: `app/build.gradle*`, gradle properties/wiring, `.gitignore`, `scripts/*` (new release scripts), `docs/`. Do NOT touch `core/`, `research/`, other branches, or the shared `/work/android-toolchain/gradle-home`.

## Goal
Per `.scratch/device-ready-apk/issues/07-signing-distribution.md`, decide answers and implement:
1. Debug-signed APK for Martin's device is the v1 path (per ADRs/park docs); script to CREATE a debug/release keystore is committed, but NO keystore binary ever enters the repo (`.gitignore` guards `*.jks`, `*.keystore`, `local.properties`, `*.bin` models remain ignored).
2. Release signing via env vars only (e.g. `MARTINS_KEYSTORE` / `MARTINS_KEYSTORE_PASSWORD` / `MARTINS_KEY_ALIAS` / `MARTINS_KEY_PASSWORD`): when unset, `assembleRelease` produces an unsigned/`DebugSigning`-path release build that still builds; when set, a real signature. Wire in `app/build.gradle*` behind `signingConfig` that reads env.
3. `versionCode` = `git rev-list --count HEAD`, `versionName` = `0.1.0` (`0.1.0-<code>-g<sha>` acceptable). Fallback when git count fails.
4. Custody/backup steps for Martin + push checklist + upgrade path (key change => uninstall/reinstall) in `docs/RELEASE.md` (new) and a short update to `docs/BUILD.md`.

## Acceptance (run and paste real output into the handoff)
```
export JAVA_HOME=/work/android-toolchain/jdk ANDROID_HOME=/work/android-toolchain/sdk ANDROID_SDK_ROOT=/work/android-toolchain/sdk
export GRADLE_USER_HOME=/home/node/gh-w1        # make once: cp -r /work/android-toolchain/gradle-home /home/node/gh-w1
cd /work/martins-whisper-worktrees/m1-release
bash -n scripts/*.sh
./gradlew :app:assembleDebug :app:assembleRelease --offline --console=plain   # release WITHOUT env: must build
MARTINS_KEYSTORE=/tmp/m1-test.keystore MARTINS_KEYSTORE_PASSWORD=x MARTINS_KEY_ALIAS=a MARTINS_KEY_PASSWORD=x \
  keytool -genkeypair ... # create a THROWAWAY keystore OUTSIDE the repo to prove env signing works, delete after
./gradlew :app:assembleRelease --offline   # with env: must produce a signed APK; verify with apksigner/`keytool -printcert` or jarsigner
sha256sum app/build/outputs/apk/*/*.apk
```

## Rules
- Handoff MANDATORY: `handoffs/W1-release/HANDOFF.md` (+ `SUMMARY.txt`, exec summary <=10 lines) in the worktree; final message repeats the summary.
- wip commit every ~10 min; commit at the end even if incomplete (`wip:` prefix).
- No secrets, no keystores, no generated model files committed. `git status` clean-relevant check before handoff.
- If blocked (env/toolchain/native), still commit + precise blocked report + exact unblock steps; never fake output.
