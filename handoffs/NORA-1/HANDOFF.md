# NORA-1 — Native APK + CI fix — HANDOFF

## Executive summary (<=10 lines)

- Fixed the two failing GitHub workflows: they no longer use
  `android-actions/setup-android@v3` (the step that died with
  `sdkmanager failed with exit code 1`); they call the runner's preinstalled
  `sdkmanager` directly with explicit `--sdk_root`.
- The native workflow now builds `-Pmartins.native=true` CPU-first on
  `ubuntu-latest`, reusing the runner's NDK `27.3.13750724` and CMake `3.31.5`,
  caching Gradle + the pinned whisper.cpp checkout, verifying the APK, and
  uploading it as an artifact.
- **A real arm64 native APK was built locally and verified** (12.9 MB, only
  `arm64-v8a`, whisper/ggml libs present and stripped, JNI symbols exported, no
  model bundled). Path/hash below.
- `:core:test :app:testDebugUnitTest :app:assembleDebug -Pmartins.native=true`
  is green locally: **141 tests, 0 failures**; `assembleDebugAndroidTest` compiles;
  workflow YAML passes `actionlint`.
- I could not run Actions myself: `gh` in this worker is unauthenticated. Owner
  must push `agent/n1-native` and let CI run (or dispatch it); send me
  `gh run view <id> --log-failed` if anything is still red.

## Local artifact (already built, host-mounted)

```
/work/martins-whisper-worktrees/n1-native/app/build/outputs/apk/debug/app-debug.apk
sha256 c90e356f6e65e82722f6a29b35ae2d661244f68b53c806820b3c4d5c751b7476
size   12,902,712 bytes (12.9 MB; budget 20 MiB)
ABI    arm64-v8a only
native libs (compressed in APK):
  libggml-base.so 919,000   libggml-cpu.so 866,016   libggml.so 705,344
  libmartins_whisper.so 37,504 (stripped)   libparakeet.so 148,424   libwhisper.so 604,416
```

This APK is installable on an arm64 phone. It is CPU-first (Vulkan off). Models
are not bundled: download in-app or push one (smoke test instructions below).

## Branches / commits

- Worktree: `/work/martins-whisper-worktrees/n1-native`
- Branch: `agent/n1-native`
- Base: `ba6b160` (master when started)
- Commits:
  - `b37577d` fix(ci): bypass failing setup-android, build native arm64 APK
  - `8938610` fix(ci): resolve sdkmanager robustly and pin --sdk_root

Nothing was merged to master; MARA owns integration.

## What changed (files)

| File | Change |
|---|---|
| `.github/workflows/android.yml` | Removed `android-actions/setup-android@v3`; discover preinstalled `sdkmanager`, accept licences with `yes`, install platform 35/build-tools 34+35/platform-tools, explicit `--sdk_root`. Gradle cache via `setup-gradle@v4`. Still uploads `app-debug.apk`. |
| `.github/workflows/native-build.yml` | Same SDK bootstrap + reuse runner NDK `27.3.13750724` / CMake `3.31.5` (installs only if missing); cache whisper.cpp; `native-jni-check.sh`; tests + `assembleDebug -Pmartins.native=true -Pmartins.vulkan=false`; `native-apk-check.sh`; uploads APK + `build/native-apk-report.txt`. Triggers: dispatch, push/PR on native-relevant paths. |
| `app/build.gradle.kts` | New `-Pmartins.ndkVersion` and `-Pmartins.cmakeVersion` overrides (defaults unchanged: NDK 27.0.12077973, CMake 3.22.1). Used by CI and the aarch64 sandbox. |
| `app/src/main/cpp/CMakeLists.txt` | `GGML_VULKAN` default is now `OFF` (CPU-first). Gradle still passes it explicitly. |
| `scripts/native-jni-check.sh` | New: cross-checks 14 Kotlin `external fun` names vs JNI exports vs `System.loadLibrary("martins_whisper")` vs CMake target. |
| `scripts/native-apk-check.sh` | New: arm64-only, required `.so`s, no bundled model, 20 MiB budget, stripped lib, exported JNI symbol, manifest `native-code`, writes a report. |
| `app/src/androidTest/.../NativeWhisperSmokeTest.kt` | New device smoke test: version/backends; loads smallest installed model (or `-e whisperModel`) and transcribes 1 s of silence. Skips cleanly without native/model. |
| `docs/native-build.md` | Rewritten: CI path, aarch64 community-NDK recipe, ABI/size/strip policy, evidence from this run, device smoke test. |

## Evidence (commands + real output)

Environment used locally (NOT committed, sandbox-only):

- `/work/android-toolchain` JDK 17 / Gradle 8.9 / platform 35 / build-tools 35.0.1
- private `GRADLE_USER_HOME=/home/node/gh-nora`
- community aarch64 NDK r30 (`scripts/bootstrap-build.sh --with-ndk`)
- CMake 3.31.5 (also validated CMake 3.22.1) under `sdk/cmake/<ver>` + nix ninja
- local aarch64 only: patched NDK host binaries to newer glibc via `patchelf`
  and symlinked `prebuilt/linux-x86_64 -> linux-aarch64` (AGP 8.7.3 strip step).
  Neither hack is in git or needed in CI.

```
$ ./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug \
    -Pmartins.native=true -Pmartins.vulkan=false \
    -Pmartins.ndkVersion=30.0.16248370 -Pmartins.cmakeVersion=3.31.5 \
    --offline --no-daemon --console=plain
BUILD SUCCESSFUL in 1m 36s

$ # XML totals: core 85 + app 56 = 141 tests, 0 failures, 0 errors

$ ./scripts/native-jni-check.sh
native-jni-check: OK (14 methods, class com.martins.whisper.asr.WhisperCppNative,
library martins_whisper.so)

$ ./scripts/native-apk-check.sh app/build/outputs/apk/debug/app-debug.apk
# Native APK report
- sha256: c90e356f6e65e82722f6a29b35ae2d661244f68b53c806820b3c4d5c751b7476
- size: 12902712 bytes (12600 KiB), budget 20971520 bytes
- ABI: arm64-v8a
- stripped: libmartins_whisper.so has no .debug_info
- JNI exports: present
- bundled model files: none
(exit 0)

$ ./gradlew :app:assembleDebugAndroidTest -Pmartins.native=false --no-daemon
BUILD SUCCESSFUL in 17s

$ actionlint .github/workflows/*.yml
(no output, exit 0)

$ /work/android-toolchain/sdk/build-tools/35.0.1/aapt2 dump badging app-debug.apk
package: name='com.martins.whisper' versionName='0.1.0' ...
native-code: 'arm64-v8a'
```

Real CI failure this addresses (owner-provided): runs `40376837424` (Android CI)
and `40376837520` (native) both failed in 20–37 s at
`android-actions/setup-android@v3` with `sdkmanager failed with exit code 1`.
The replacement calls the preinstalled SDK's own `sdkmanager`; the same command
sequence (`--licenses`, then `--install`) was exercised locally on JDK 17 and
exited 0.

## Owner / next-agent actions

1. **Owner:** push `agent/n1-native` (commits `b37577d`, `8938610`) and let both
   workflows run. If needed:
   `gh workflow run native-build.yml --repo hajnina/martins-whisper --ref agent/n1-native`
   and `gh workflow run android.yml --repo hajnina/martins-whisper --ref agent/n1-native`.
2. If red, send `gh run view <run-id> --repo hajnina/martins-whisper --log-failed`
   to NORA — the local build proves the code path, so any failure is likely in
   the SDK/NDK bootstrap or runner image detail.
3. Download `martins-whisper-native-debug-<sha>` artifact once green; install on
   an arm64 phone. Then run the smoke test:
   ```
   adb push ggml-tiny.bin /data/local/tmp/ggml-tiny.bin
   adb shell run-as com.martins.whisper mkdir -p files/models
   adb shell run-as com.martins.whisper cp /data/local/tmp/ggml-tiny.bin files/models/tiny.bin
   adb shell am instrument -w -e class \
     com.martins.whisper.harness.NativeWhisperSmokeTest \
     com.martins.whisper.test/androidx.test.runner.AndroidJUnitRunner
   ```
4. **MARA:** merge only after both workflows are green; keep the NDK/CMake
   override properties (they are portable defaults-preserving).

## Known limitations / notes

- GitHub Actions was not executed from this worker (no `gh` auth). Local
  evidence is strong but CI remains unproven until the owner pushes.
- CI uses NDK `27.3.13750724` (runner default) while `app/build.gradle.kts`
  defaults to `27.0.12077973`; the override property makes this explicit. If we
  want one canonical pin later, change the default + Dockerfile together.
- `docs/BUILD.md` still says "AGP integration still needs an externalNativeBuild
  block" and names NDK 27.0; that is DORA's file, not edited here. `docs/native-build.md`
  is current.
- The aarch64 NDK r30 + glibc patch is a sandbox workaround, not a supported
  build environment.
- The smoke test requires hardware (H1); no emulator/KVM here.
- Vulkan build remains opt-in and untested on hardware.

## References used

- whisper.cpp pinned source: `https://github.com/ggml-org/whisper.cpp` @
  `231638a0717b7822784297e5980965ffcec05810` (via existing `scripts/fetch-whisper-cpp.sh`).
- GitHub runner image inventory (NDK/CMake/SDK paths):
  `https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md`.
- Community aarch64 NDK r30: `https://github.com/kaspersigi/android-ndk-r30-linux-aarch64`
  (downloaded by `scripts/bootstrap-build.sh --with-ndk`).
- Android NDK/AGP conventions: existing repo docs + `app/src/main/cpp/CMakeLists.txt`.
