# Native whisper.cpp build (Android arm64-v8a)

The speech engine is built from a pinned whisper.cpp checkout with CMake/NDK. The JNI wrapper is
`app/src/main/cpp/whisper_jni.cpp`; the Kotlin bridge is
`app/src/main/kotlin/com/martins/whisper/asr/WhisperCppEngine.kt`.

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
- Android NDK `27.0.12077973` (pinned in `app/build.gradle.kts`)
- CMake `3.22.1` (install via `sdkmanager "cmake;3.22.1"`)
- Ninja
- For Vulkan: `glslc` from the NDK/Vulkan SDK and a device exposing Vulkan

## Build

```bash
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/27.0.12077973
scripts/fetch-whisper-cpp.sh
gradle :app:assembleDebug
```

CPU-only (no Vulkan, useful for bring-up):

```bash
gradle :app:assembleDebug -Pmartins.vulkan=false
```

Kotlin-only build on a machine without NDK/CMake (native library is absent at runtime, so the engine
reports itself unavailable):

```bash
gradle :app:testDebugUnitTest -Pmartins.native=false
```

`abiFilters` is `arm64-v8a` only. The APK contains `libmartins_whisper.so` plus the whisper/ggml
shared libraries produced by the external native build.

## Vulkan notes

- `GGML_VULKAN=ON` is the default. whisper.cpp asks ggml for a GPU device; if the Vulkan backend
  fails, the Kotlin wrapper retries context creation once with `use_gpu=false`.
- `backend` is reported as `whisper.cpp/vulkan`, `whisper.cpp/gpu` (non-Vulkan GPU backend), or
  `whisper.cpp/cpu`. `runtimeInfo` includes `whisper_print_system_info()` and the ggml device list.
- Vulkan shader generation requires `glslc`. If the NDK does not ship it, point CMake at a Vulkan SDK
  built for the host. Cross-compiling the shader generator itself is not supported by the Android
  NDK; use an x86_64 host or pre-generated shaders.

## Host blocker observed in this sandbox (aarch64 Linux)

The official NDK package from `dl.google.com` is a Linux **x86_64** host toolchain. On an aarch64
host the compiler cannot execute:

```
$ file android-ndk-r27/toolchains/llvm/prebuilt/linux-x86_64/bin/clang
ELF 64-bit LSB pie executable, x86-64 ...
$ .../bin/clang --version
bash: .../clang: cannot execute binary file: Exec format error
```

Nixpkgs also marks `androidenv.androidPkgs.ndk-bundle` as unavailable for `aarch64-linux`:

```
$ nix eval --json nixpkgs#androidenv.androidPkgs.ndk-bundle.meta
{"available":false, ...}
```

Unblock options, in order of preference:

1. Run the build on an x86_64 Linux host or CI (`ubuntu-latest`).
2. Run an x86_64 container with binfmt/QEMU on this host (see `docker/native-build.Dockerfile` and
   `scripts/build-native-docker.sh`).
3. Use a community aarch64 NDK build (B1 is investigating
   `kaspersigi/android-ndk-r30-linux-aarch64`); pin and validate it before relying on it.

No `.so` is committed: the native artifact must be produced by the build.
