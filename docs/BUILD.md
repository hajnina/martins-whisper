# Build guide - Martins Whisper

Two supported build paths:

| Path | Host | Status | Use for |
|---|---|---|---|
| A. Shared local toolchain | this aarch64 Linux container | **Verified** - JDK/Gradle/SDK/aapt2 work natively | fast local `:core:test` + `:app:assembleDebug` |
| B. Docker | x86_64 Linux (`--platform linux/amd64`) | Image definition ready; cannot execute in this aarch64 sandbox (no binfmt/QEMU for amd64) | CI parity, official NDK/emulator |
| C. GitHub Actions | `ubuntu-latest` (x86_64) | Workflow ready | canonical CI |

All local installs live in `/work/android-toolchain` and are shared by every worktree. No root, no `apt`.

## A. Local aarch64 toolchain

```bash
cd /work/martins-whisper-worktrees/<work-id>
./scripts/bootstrap-build.sh            # idempotent; ~1 min when cached
source scripts/env.sh
./gradlew :core:test
./gradlew :app:assembleDebug
```

`bootstrap-build.sh`:

- downloads and checksum-verifies Temurin JDK 17, Gradle 8.9, platform 35, build-tools 34.0.0 + 35.0.1;
- on aarch64, replaces the x86_64 `aapt2`/`aapt`/`zipalign`/`aidl` from Google's build-tools with native aarch64 builds from [lzhiyong/android-sdk-tools](https://github.com/lzhiyong/android-sdk-tools/releases/tag/35.0.2) and installs an aarch64 `adb`;
- writes `local.properties` (gitignored) pointing at the SDK;
- writes `$ANDROID_TOOLCHAIN_DIR/gradle-home/gradle.properties` with `android.aapt2FromMavenOverride=<aarch64 aapt2>` (AGP otherwise downloads an x86_64 aapt2 from Maven and fails with `Exec format error`);
- writes `$ANDROID_TOOLCHAIN_DIR/STATUS.md`.

Configurable locations:

```bash
ANDROID_TOOLCHAIN_DIR=/some/other/path ./scripts/bootstrap-build.sh
source scripts/env.sh          # reads ANDROID_TOOLCHAIN_DIR
```

Optional community NDK (needed only for aarch64 native builds):

```bash
./scripts/bootstrap-build.sh --with-ndk
```

This installs the community aarch64 NDK r30 (`30.0.16248370`) under `sdk/ndk/`. See "Native NDK" below.

### Verified result on this host (2026-10-26)

```
$ source scripts/env.sh && ./gradlew clean :core:test :app:assembleDebug --no-build-cache
BUILD SUCCESSFUL in 1s
35 actionable tasks: 35 executed

$ ls -l app/build/outputs/apk/debug/app-debug.apk
-rw-r--r-- 8578276 app-debug.apk
```

- JDK: Temurin 17.0.21+9 (aarch64)
- Gradle: 8.9 (wrapper pinned)
- AGP 8.7.3 / Kotlin 2.0.21 / compileSdk 35 / minSdk 29
- aapt2: 2.19 (build-tools 35.0.2 native aarch64, used through the override)
- APK signature: debug v2 scheme verified with `apksigner`; `aapt2 dump badging` reports package `com.martins.whisper`, minSdk 29, targetSdk 35.

Note: `:core:test` currently reports `NO-SOURCE` because the skeleton has no tests. A temporary probe test was run during bring-up (`tests=1 failures=0`), then removed; C1 owns the real tests. Similarly, an `Activity` probe was compiled and dexed to prove Kotlin/Android compilation, then removed (the current skeleton has no Kotlin app sources).

### Manual fallback

If the bootstrap script cannot run, set:

```bash
export JAVA_HOME=/work/android-toolchain/jdk
export ANDROID_HOME=/work/android-toolchain/sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export PATH="$JAVA_HOME/bin:/work/android-toolchain/gradle/current/bin:$ANDROID_HOME/platform-tools:$PATH"
export GRADLE_USER_HOME=/work/android-toolchain/gradle-home
```

and keep `android.aapt2FromMavenOverride` in `$GRADLE_USER_HOME/gradle.properties` on aarch64.

## Why aapt2 needs special handling

Google ships Android SDK host tools for `linux-x86_64` only. On aarch64:

- `sdkmanager` itself is Java and works.
- `aapt2`, `aapt`, `zipalign`, `aidl`, `adb` are ELF x86_64 and fail with `Exec format error`.
- AGP also separately downloads a Maven aapt2 for the host; `android.aapt2FromMavenOverride` points it at the native binary.

Community native aarch64 builds are used for those binaries. Java tools (`d8`, `r8`, `apksigner`, `sdkmanager`) are unaffected.

## B. Docker (x86_64)

`Dockerfile.build` uses `eclipse-temurin:17-jdk-jammy`, installs command-line tools + platform 35 + build-tools 34.0.0/35.0.1 + NDK 27.0.12077973, then runs tests and assembles the APK.

```bash
docker buildx build --platform linux/amd64 -f Dockerfile.build -t martins-whisper-build .
docker run --rm --platform linux/amd64 \
  -v "$PWD/app/build/outputs/apk/debug:/out" martins-whisper-build
```

On a native x86_64 host drop `--platform linux/amd64`.

**Local status:** not executable in this sandbox. The Docker daemon is aarch64-only and `/proc/sys/fs/binfmt_misc` is unavailable, so `docker run --platform linux/amd64 alpine uname -m` fails with `exec format error`. `docker build --check` passes (no lint warnings). Treat this path as first-class CI definition, not locally verified.

To test it locally on aarch64, a privileged binfmt registration is required (not possible as `node`):

```bash
docker run --privileged --rm tonistiigi/binfmt --install amd64
```

## C. GitHub Actions

`.github/workflows/android.yml` runs on `ubuntu-latest`:

1. JDK 17 (Temurin), Gradle cache, Android SDK setup.
2. `sdkmanager` installs platform 35, build-tools 34.0.0 + 35.0.1, platform-tools, NDK 27.0.12077973.
3. `./gradlew :core:test :app:testDebugUnitTest`
4. `./gradlew :app:assembleDebug`
5. Uploads `app-debug.apk`.

If E1 pins a different NDK, update `NDK_VERSION` in the workflow and Dockerfile.

## Native NDK (whisper.cpp arm64)

Details and evidence: `TOOLCHAIN.md` → "Native NDK". Short version:

- Official NDK archives are `linux-x86_64`; they cannot run natively here.
- This sandbox cannot run amd64 Docker images (no binfmt), so "use x86_64 Docker" is a CI-only answer.
- A community aarch64 NDK r30 is available and is downloaded by `bootstrap-build.sh --with-ndk`.
- Nix also has a cross toolchain (`pkgsCross.aarch64-android`) for ad-hoc C/C++ builds, but the NDK sysroot + `ndk-build`/CMake toolchain is the path that matches Android conventions.
- AGP integration still needs an `externalNativeBuild` block and a pinned `ndkVersion` (E1's package).

## Emulator

**Blocked in this sandbox**, with no fake device:

- The SDK repository offers the Linux emulator only for `host-arch=x64`; the only `aarch64` emulator is macOS. There is no `linux-aarch64` emulator package.
- `/dev/kvm` does not exist; nested virtualization is unavailable.
- amd64 emulation is unavailable because binfmt/QEMU is not registered with the kernel.
- Even if an x86_64 emulator were run under QEMU TCG, boot and Whisper inference would be unusably slow, and GPU/Vulkan acceptance would be meaningless.

Use JVM/Robolectric locally and a physical arm64 device (or an x86_64 KVM runner) for instrumented/emulator tests. T1 owns that harness.

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `aapt2: Exec format error` / `Illegal instruction` | x86_64 aapt2 from Maven or build-tools | `source scripts/env.sh`; verify `android.aapt2FromMavenOverride` points at `sdk/build-tools/35.0.1/aapt2`; `aapt2 version` must run |
| `Failed to install ... licences have not been accepted` | missing `sdk/licenses` | rerun `scripts/bootstrap-build.sh` |
| `SDK location not found` | no `local.properties` | rerun bootstrap or write `sdk.dir=/work/android-toolchain/sdk` |
| Gradle daemon OOM | default heap | project sets `-Xmx3g`; host has 30 GiB |
| Wrapper downloads Gradle again | fresh `GRADLE_USER_HOME` | use `source scripts/env.sh` (shared cache) |
