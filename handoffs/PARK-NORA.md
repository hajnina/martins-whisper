# PARK — NORA (2026-10-26, quota refill)

## DONE (SHAs)

- `master` @ `1de97d6` (clean; "park: u2-states reconciled, gate 235/0, push-ready").
- `agent/n3-native` @ `d843cbc` — 16 KB ELF/zip link flag, rewritten
  `scripts/native-apk-check.sh` (PASS on N1 + N3 APKs), `scripts/device-smoke.sh`
  (mock-adb PASS; never run on a real device), docs/model contract. APK then:
  sha256 `ba768f8e…c864409`, 12,327,744 B, still bundled `libparakeet.so`.
- `agent/n3-host-tracer` @ `2369ffc` (parked wip commit) — **host JNI tracer bullet works**:
  - `scripts/host-jni-test.sh` builds pinned whisper.cpp (`samples/jfk.wav`, tiny model) and
    `whisper_jni.cpp` against `app/src/main/cpp/host-stubs/android/log.h` for this aarch64 host
    (no NDK), then runs `HostWhisperJniTest` on the JVM. Local result: **PASS**
    (asserts "ask not what your country can do for you" + "fellow americans", no U+FFFD).
  - JNI UTF-8: `nativeSegmentText` now returns `jbyteArray`; Kotlin `decodeNativeUtf8()`
    (`String(bytes, UTF_8)`); `NativeUtf8Test` covers Czech diacritics + emoji + malformed input.
  - `libparakeet.so` excluded in `app/build.gradle.kts` `packaging.jniLibs.excludes`.
  - CI `native-build.yml`: host JNI step + tiny-model cache (`WHISPER_TINY_SHA256`) + path triggers.
  - Not re-verified after these edits: full `:core:test :app:testDebugUnitTest :app:assembleDebug`
    and `native-apk-check.sh` (APK is stale from `d843cbc`).

## RUNNING

None. No workers launched or awaited. Running PM/other agents were not touched.

## NEXT (small tickets, in order)

1. **N3-GATE** — rebuild `:app:assembleDebug`, run full unit gate, run
   `scripts/native-apk-check.sh`; confirm `libparakeet.so` is absent and record new APK sha/tests.
2. **N3-VERA-UTF8** — hand the UTF-8 assertion set to VERA for review; verify the byte-array
   signature in `native-jni-check.sh` + both host and device paths.
3. **N3-HOST-CI** — owner pushes `agent/n3-host-tracer`; watch the new host JNI CI step
   (cmake/ninja, model cache) and fix any runner-only failures.
4. **N3-DEVICE** — on a real arm64 device with adb: `scripts/device-smoke.sh --serial …`
   (still only mock-tested; no aarch64 adb here).
5. **N3-DOC-GATE** — decide whether `scripts/host-jni-test.sh` joins `test-device-ready.sh`
   (owner asked for "local gate"; currently CI + standalone only) and document the sha/model fixtures.

## TRAPS

- Host toolchain: `nix profile add nixpkgs#gcc --priority 9` (binutils conflict); JDK at
  `/work/android-toolchain/jdk` is not on PATH; SDK CMake find needs `-type f` (the dir is named `cmake`).
- Host link: CMake emits only versioned sonames; create unversioned `lib*.so` symlinks and run
  tests with `LD_LIBRARY_PATH=build/host-native/lib` for libggml deps.
- NDK r27 still defaults to 4 KB LOAD alignment: explicit `-Wl,-z,max-page-size=16384` is required.
  `readelf -W` (non-W wraps the alignment column). `libparakeet.so` is an independent target
  (`whisper.cpp/src/CMakeLists.txt:196`), not in `libwhisper.so` DT_NEEDED.
- Gradle KTS: `java.io.File` is misparsed (`java` = extension); use `File.pathSeparator`.
- `libparakeet` exclusion needs a rebuild; old APK sha is not evidence.
- GGML magic is LE bytes `6c6d6767`, not `67676d6c`. Tiny model sha256
  `be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21`.
- `whisper.cpp` is gitignored: fetch before native builds; host fixture model cached in
  `build/host-native/models` (ignored).
- Local NDK r30 needs `LD_LIBRARY_PATH=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-aarch64/lib`
  for libxml2; nix libxml2 2.15.4 has the wrong soname. No aarch64 adb/device here; `gh` absent.
