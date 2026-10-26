# Native whisper.cpp build (Android arm64-v8a)

The speech engine is built from a pinned whisper.cpp checkout with CMake/NDK. The JNI wrapper is
`app/src/main/cpp/whisper_jni.cpp`; the Kotlin bridge is
`app/src/main/kotlin/com/martins/whisper/asr/WhisperCppEngine.kt`.

Native is **opt-in**: `./gradlew :app:assembleDebug` builds a CPU-seam APK without the NDK. Add
`-Pmartins.native=true` for the real arm64 APK. The default is **CPU-only**; Vulkan is a separate
opt-in (`-Pmartins.vulkan=true`). This matches ADR-004's CPU-first fallback.

**Status (2026-10-26, NORA N1):** a native debug APK was built and verified on this aarch64 host
with the community NDK r30 workaround below, and the CI workflow for the canonical
`ubuntu-latest` path is ready. See [Verification](#verification-2026-10-26) for the exact evidence.

## Pinned source

```bash
scripts/fetch-whisper-cpp.sh
# pin: 231638a0717b7822784297e5980965ffcec05810 (whisper.cpp 1.9.5-dev)
```

The checkout goes to `app/src/main/cpp/whisper.cpp/` and is gitignored. Override with
`WHISPER_CPP_REF` only if you also re-test the JNI wrapper.

## Toolchain

- JDK 17, Gradle 8.9
- Android SDK platform 35, build-tools 35.0.1
- Android NDK `27.0.12077973` (default, pinned in `app/build.gradle.kts`)
- CMake `3.22.1` (install via `sdkmanager "cmake;3.22.1"`)
- Ninja
- For Vulkan: `glslc` from the NDK/Vulkan SDK and a device exposing Vulkan

Three Gradle properties override the pins without editing the build file:

| Property | Default | Use |
|---|---|---|
| `martins.ndkVersion` | `27.0.12077973` | CI uses the preinstalled `27.3.13750724`; this sandbox uses the community `30.0.16248370` |
| `martins.cmakeVersion` | `3.22.1` | CI uses the preinstalled `3.31.5` |
| `martins.vulkan` | `false` | `true` builds the Vulkan backend |

## Build

```bash
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/27.0.12077973
scripts/fetch-whisper-cpp.sh
./gradlew :app:assembleDebug -Pmartins.native=true
```

CPU-only (default, explicit):

```bash
./gradlew :app:assembleDebug -Pmartins.native=true -Pmartins.vulkan=false
```

Kotlin-only build on a machine without NDK/CMake (native library is absent at runtime, so the engine
reports itself unavailable):

```bash
./gradlew :app:testDebugUnitTest -Pmartins.native=false
```

`abiFilters` is `arm64-v8a` only. The APK contains `libmartins_whisper.so` plus the whisper/ggml
shared libraries produced by the external native build.

## CI (canonical x86_64 path)

`.github/workflows/native-build.yml` runs on `ubuntu-latest` and uploads
`martins-whisper-native-debug-<sha>` as a workflow artifact:

1. Uses the **preinstalled** Android SDK (`/usr/local/lib/android/sdk`) and calls its `sdkmanager`
   directly. It deliberately does not use `android-actions/setup-android@v3`, which failed on this
   repo with `sdkmanager failed with exit code 1`.
2. Reuses the runner's NDK `27.3.13750724` and CMake `3.31.5` (falls back to `sdkmanager --install`
   only if the image drops them) and passes them through the two Gradle properties.
3. Caches Gradle (`gradle/actions/setup-gradle@v4`) and the pinned whisper.cpp checkout.
4. Runs `scripts/native-jni-check.sh` (Kotlin `external fun` vs JNI exports vs `loadLibrary`),
   then `:core:test :app:testDebugUnitTest :app:assembleDebug`.
5. Runs `scripts/native-apk-check.sh` (see [Artifact proof](#artifact-proof-no-device)) and
   uploads the APK plus `build/native-apk-report.txt`.

Trigger it from any branch with:

```bash
gh workflow run native-build.yml --repo hajnina/martins-whisper --ref <branch>
gh run download <run-id> --repo hajnina/martins-whisper
```

Vulkan is never built by default; dispatch the workflow with `vulkan: true` to try it.

## Artifact proof (no device)

`scripts/native-apk-check.sh [APK]` proves as much as possible without a phone. It fails the build
if any of these is false:

- only `arm64-v8a` is present and the manifest says `native-code: 'arm64-v8a'`;
- `libmartins_whisper.so`, `libwhisper.so`, `libggml{,-base,-cpu}.so` are inside the APK;
- every bundled `.so` is an **AArch64 ELF64 shared object**, stripped (no `.debug_info`);
- every **LOAD segment is aligned to at least 0x4000 (16 KB)**, and the zip stores the
  uncompressed `.so` entries 16 KB-aligned (`zipalign -c -P 16`). This is required on Android 15+
  devices running a 16 KB page-size kernel. `CMakeLists.txt` requests
  `-Wl,-z,max-page-size=16384` for every target because CI pins NDK r27, which still defaults to
  4 KB; NDK r28+ would set it implicitly;
- every `DT_NEEDED` is either bundled in the APK or a known Android system library. In particular a
  missing `libc++_shared.so` fails here instead of at app startup;
- **all 14 Kotlin `external fun` declarations** in `WhisperCppEngine.kt` are exported by
  `libmartins_whisper.so` (renaming one side only cannot slip through);
- no `.ggml/.gguf/.bin` model is bundled, and the APK is under the 20 MiB budget.

This is the load-time contract the Android dynamic linker checks; a green report plus the
instrumented smoke test below is the strongest proof available without hardware.

## Model path and format contract

The Kotlin side and whisper.cpp agree on exactly one layout:

| Item | Value |
|---|---|
| App-private root | `Context.filesDir` (`/data/user/0/com.martins.whisper/files`) |
| Models directory | `<filesDir>/models` |
| File name | `<model id>.bin`, e.g. `models/base.bin`, `models/tiny.bin` |
| Format | whisper.cpp **GGML** (`ggml`, `ggmf`, `ggjt` or `ggla` magic), not GGUF |
| Download integrity | catalog size + SHA-256 verified before `ModelStorage` promotes the file |
| JNI hand-off | `nativeInit(modelPath, useGpu, gpuDevice)` receives the absolute path and calls `whisper_init_from_file_with_params` |

`scripts/push-model.sh` writes exactly that path through `run-as` on a debuggable APK and verifies
the catalog SHA-256. `scripts/device-smoke.sh` additionally checks the GGML magic, the on-device
byte count, and passes the resolved absolute path to the smoke test.

## Local aarch64 sandbox (this host)

The official NDK archives are `linux-x86_64`, and amd64 containers cannot run here (no
binfmt/QEMU). The community aarch64 NDK r30 is installed by
`scripts/bootstrap-build.sh --with-ndk`. Two local-only workarounds are needed; **neither is part
of the committed build or CI**:

1. **GLIBC:** the r30 binaries require `GLIBC_2.38`; this host has 2.36. Point each host ELF in
   `ndk/30.0.16248370/toolchains/llvm/prebuilt/linux-aarch64/bin` at a newer glibc with
   `patchelf --set-interpreter <glibc>/lib/ld-linux-aarch64.so.1` and an rpath containing that
   glibc plus its libstdc++ (here: Nix `glibc` and `gcc.cc.lib`). `ld.lld` also needs
   `libxml2.so.2`.
2. **Host tag:** AGP 8.7.3 looks for `prebuilt/linux-x86_64` when stripping, even on aarch64;
   symlink it to `prebuilt/linux-aarch64`.

CMake/ninja: this environment uses the aarch64 CMake 3.22.1 (or 3.31.5) build from Kitware placed
under `sdk/cmake/<version>/bin`, with `ninja` symlinked next to it and a matching
`source.properties`.

Then:

```bash
source scripts/env.sh
export GRADLE_USER_HOME=/home/node/gh-<agent>   # private copy, see docs/BUILD.md

./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug \
  -Pmartins.native=true -Pmartins.vulkan=false \
  -Pmartins.ndkVersion=30.0.16248370 -Pmartins.cmakeVersion=3.31.5 \
  --offline --no-daemon

export ANDROID_NDK_HOME=/work/android-toolchain/sdk/ndk/30.0.16248370
scripts/native-jni-check.sh
scripts/native-apk-check.sh app/build/outputs/apk/debug/app-debug.apk
```

### Verification 2026-10-26

Commands and results from worktree `n1-native` (branch `agent/n1-native`):

```
$ ./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug \
    -Pmartins.native=true -Pmartins.vulkan=false \
    -Pmartins.ndkVersion=30.0.16248370 -Pmartins.cmakeVersion=3.31.5 --offline --no-daemon
BUILD SUCCESSFUL in 1m 36s

$ scripts/native-jni-check.sh
native-jni-check: OK (14 methods, class com.martins.whisper.asr.WhisperCppNative, library martins_whisper.so)

$ scripts/native-apk-check.sh app/build/outputs/apk/debug/app-debug.apk
- sha256: c90e356f6e65e82722f6a29b35ae2d661244f68b53c806820b3c4d5c751b7476
- size: 12902712 bytes (12600 KiB), budget 20971520 bytes
- ABI: arm64-v8a
- stripped: libmartins_whisper.so has no .debug_info
- JNI exports: present
- bundled model files: none

lib/arm64-v8a/libggml-base.so       919000
lib/arm64-v8a/libggml-cpu.so        866016
lib/arm64-v8a/libggml.so            705344
lib/arm64-v8a/libmartins_whisper.so  37504
lib/arm64-v8a/libparakeet.so        148424
lib/arm64-v8a/libwhisper.so         604416
```

`aapt2 dump badging` reports `native-code: 'arm64-v8a'`; the APK is 12.9 MB, well under the
20 MiB budget. The `.so` files are stripped by AGP (`libmartins_whisper.so` 183,640 → 37,504
bytes in the APK). The model is not bundled: it is downloaded/imported on device.

## ABI and size policy (v1)

- **ABI:** `arm64-v8a` only. No x86/x86_64/armeabi-v7a in the v1 artifact. The workflow and
  `scripts/native-apk-check.sh` fail if another ABI appears.
- **Size budget:** 20 MiB (20,971,520 bytes) for the debug APK; currently 12.9 MB. The check is
  `MAX_APK_BYTES` in `scripts/native-apk-check.sh`.
- **Stripping:** AGP's `stripDebugDebugSymbols` runs for debug native builds; the check asserts
  every bundled `.so` has no `.debug_info` and still exports the JNI symbols.
- **Page size:** all LOAD segments and uncompressed zip entries are 16 KB-aligned so the APK runs
  on Android 15+ devices with 16 KB pages and still runs on 4 KB devices.
- **Models:** never bundled. They are downloaded from the catalog or imported by the user.
- Do not add `x86_64` "for the emulator": the sandbox has no KVM/emulator and v1 targets phones.

## Vulkan notes

- Vulkan is **off** by default (`-Pmartins.vulkan=false`; `CMakeLists.txt` also defaults
  `GGML_VULKAN=OFF`).
- whisper.cpp asks ggml for a GPU device; if the Vulkan backend fails, the Kotlin wrapper retries
  context creation once with `use_gpu=false`.
- `backend` is reported as `whisper.cpp/vulkan`, `whisper.cpp/gpu` (non-Vulkan GPU backend), or
  `whisper.cpp/cpu`. `runtimeInfo` includes `whisper_print_system_info()` and the ggml device list.
- Vulkan shader generation requires `glslc`. If the NDK does not ship it, point CMake at a Vulkan
  SDK built for the host. Cross-compiling the shader generator itself is not supported by the
  Android NDK; use an x86_64 host or pre-generated shaders.

## Device smoke test (H1)

One command builds the native APK, verifies it, installs both APKs, pushes a GGML model into the
app-private contract path, and runs `NativeWhisperSmokeTest`:

```bash
# USB device (downloads ggml-tiny.bin on first run, SHA-verified on push)
scripts/device-smoke.sh

# wireless adb: enable Wireless debugging, then
scripts/device-smoke.sh --wireless 192.168.1.7:5555

# already-built APK + local model
scripts/device-smoke.sh --no-build --model /path/to/ggml-tiny.bin

# non-default NDK/CMake on the build host
MARTINS_NDK_VERSION=27.3.13750724 MARTINS_CMAKE_VERSION=3.31.5 scripts/device-smoke.sh
```

The script fails fast if there is no arm64 device, if `native-apk-check.sh` is not green, if the
model is not GGML, or if the pushed byte count does not match. On success it prints
`PASS: native whisper.cpp loads on the device...` and the next manual MFVP step.

`NativeWhisperSmokeTest.kt` asserts `whisper.cpp` version/system info are non-empty, loads the
smallest installed model with `ComputeBackend.CPU`, transcribes 1 s of silence at 16 kHz and
closes the engine. It skips cleanly on a non-native APK and when no model is installed.

Manual equivalent:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
scripts/push-model.sh ggml-tiny.bin          # -> files/models/tiny.bin

adb shell am instrument -w \
  -e class com.martins.whisper.harness.NativeWhisperSmokeTest \
  com.martins.whisper.test/androidx.test.runner.AndroidJUnitRunner
```

## Historical host blocker note

Before the community NDK workaround, the official NDK failed with `Exec format error` on this
aarch64 host because the package is `linux-x86_64`. Nixpkgs also marks
`androidenv.androidPkgs.ndk-bundle` unavailable for `aarch64-linux`. The preferred production
route remains an x86_64 host or GitHub Actions; the local r30 route is a verification aid.
