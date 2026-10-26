# PM Handoff 2026-10-26-2 - B1 + E1 merged

## Executive summary
- Merged the B1 build harness and E1 ASR/model layer into `pm/b1-e1` and verified the full gate.
- `:core:test` + `:app:testDebugUnitTest`: **109 tests, 0 failures** (74 previous + 35 new).
- `:app:assembleDebug`: `BUILD SUCCESSFUL`, `app-debug.apk` 8,876,133 bytes, SHA-256 `c2aa1350607ae00239f2bdab02633b2e91980b7f3eca3658f4d301bfe83e8cd0`.
- `:app:assembleDebugAndroidTest`: `BUILD SUCCESSFUL` (T1 Layer 2 compiles; not run without a device).
- Native whisper.cpp is now **opt-in** (`-Pmartins.native=true`); the default APK builds without an NDK, per ADR-004's CPU-first fallback.
- B1/E1 source worktrees were inactive with uncommitted files, so their content was integrated via `pm/b1-e1`; those worktrees were then committed and recorded on `master` with `-s ours` merges (`beabf22`, `d529395`) so no content was duplicated.
- The PM bench workspace was archived to `research/model-bench/raw-from-pm` (`7771eaa`) and handed to ORIN; no further bench work is done here.
- Still not wired: real engine in `EngineFactory` (currently fake), model download/import UI, and the device work. These are G1/T1/D1 scope.

## What changed
- **B1 (`caf0d09`)**: Gradle wrapper (8.9) + jar, `Dockerfile.build` (x86_64), `.github/workflows/android.yml`, `docs/BUILD.md`, `scripts/{bootstrap-build,env}.sh`, `.dockerignore`, `.kotlin/` ignore.
- **E1 (`9b2ee46`)**: `core/.../asr/` (`ModelCatalog`, `ModelDownloader`, `ModelRepository`, `ModelStorage`, `ModelManager`, `WhisperCppEngine` contract), 33 unit tests + `FakeSpeechEngine` fixture, `app/.../asr/WhisperCppEngine.kt` JNI bridge, `app/src/main/cpp/{CMakeLists.txt,whisper_jni.cpp}`, native Docker/CI/docs, references.
- **Integration fixes**: `nativeEnabled`/`vulkanEnabled` now default false (opt-in), `docs/native-build.md` says so, native CI passes `-Pmartins.native=true`, combined `.gitignore`/`.dockerignore`, `core` test fixtures enabled.

## Exact commands and real output
Environment:

```sh
export JAVA_HOME=/work/android-toolchain/jdk
export ANDROID_HOME=/work/android-toolchain/sdk
export ANDROID_SDK_ROOT=/work/android-toolchain/sdk
export GRADLE_USER_HOME=/home/node/gh-pm
printf 'sdk.dir=/work/android-toolchain/sdk\n' > local.properties
```

Gate (offline, private Gradle home):

```sh
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --offline --no-daemon --console=plain
```

```
> Task :app:testDebugUnitTest
> Task :app:packageDebug
> Task :app:assembleDebug
BUILD SUCCESSFUL in 18s
47 actionable tasks: 31 executed, 16 from cache
```

Test totals from XML:

```
tests=109 skipped=0 failures=0 errors=0
```

Instrumentation compile:

```sh
./gradlew :app:assembleDebugAndroidTest --no-daemon --console=plain
```

```
> Task :app:assembleDebugAndroidTest
BUILD SUCCESSFUL in 10s
```

APK:

```
-rw-r--r-- 8,876,133 app/build/outputs/apk/debug/app-debug.apk
c2aa1350607ae00239f2bdab02633b2e91980b7f3eca3658f4d301bfe83e8cd0  app-debug.apk
```

Native build is not proven here: NDK 27.0.12077973 is absent. `-Pmartins.native=true` is expected to fail on this host; the CPU-seam APK is the merge artifact.

## Contracts / invariants
- `SpeechEngine`, `Transcript`, `ModelSpec`, `ModelManager`, `ModelCatalog`, `ModelRepository` are frozen for v1; changes need PM approval.
- Model catalog uses the pinned Hugging Face revision and SHA-256 values from `research/01-model-facts.md`; custom GGML only.
- Default APK contains no native libs; `EngineFactory` still returns `FakeSpeechEngine` until G1 wires the real engine/model.
- No cloud inference, no telemetry, no secrets.

## Known limitations / next
- **G1**: wire `EngineFactory` to `WhisperCppEngine` + `ModelRepository`, add model download/import UI, and surface doctor/backend/fallback state. Without this the shipped APK dictation is still the fake engine.
- **T1**: run the Layer 1 fake-audio E2E once wired; instrumentation is compiled but device-only.
- **D1**: write the docs set from map ticket 06 to match the real UI.
- **H1/H2**: blocked on a physical device; ADB access and signing are in `owner/ASKS.md`.
- B1/E1 worker branches are committed at `323a380` and `c22617c` and merged into `master` for provenance; their content is identical to the `pm/b1-e1` integration, so do not re-apply it.

## References
- `research/01-model-facts.md` (model hashes/sizes)
- `docs/native-build.md`, `docs/BUILD.md`
- `DECISIONS.md` ADR-004

## Commits
- `caf0d09` build(b1): wrapper, Docker/CI, env scripts
- `9b2ee46` feat(e1): ASR model layer + native scaffolding + tests
- `08de31c` merge: B1 build harness + E1 ASR layer
- `7771eaa` research(bench): hand off raw PM bench workspace to ORIN
- `323a380` / `c22617c` B1/E1 worker commits
- `beabf22` / `d529395` provenance merges (`-s ours`)
- Post-merge gate re-run: `BUILD SUCCESSFUL`, 109 tests / 0 failures, APK 8,876,133 bytes, same SHA-256.
- Handoff commit: this file
