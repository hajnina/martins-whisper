# TESS-REPLAY — DEVICE-REPORT 1e host WAV replay

Branch `agent/replay1` (worktree `/work/martins-whisper-worktrees/replay1`), based on master
`c62f533`. Commits: `964ac87`, `e6b724f` (+ this handoff commit).

## Executive summary

- A WAV now replays through the **real production dictation path** on the host:
  `DictationService` -> `AudioCaptureBuffer` -> `DictationEngineHost` -> real whisper.cpp JNI
  (`WhisperCppEngine`) -> `TranscriptGate` -> injected inserter. No reimplementation of the
  gate: `WavAudioSource` feeds 20 ms frames into the service; the real service computes the
  speech flag, peak/RMS and gate verdict.
- `scripts/replay-wav.sh [WAV]` prints `peak_dbfs`, `rms_dbfs`, `gate=SPEAK|REJECT`, `reason`
  and `transcript`. Default WAV is the public `samples/jfk.wav`.
- jfk fixture asserts literal words + `SPEAK`; a second case sweeps synthetic speech-like audio
  from -60 to -20 dBFS and proves exactly one gate transition: REJECT <= -37.5, SPEAK >= -37.0
  (declared per-frame threshold -36.48 dBFS; the flip is early because 20 ms frames cannot
  contain whole 120 Hz cycles — this is real frame-RMS behaviour, not a bug).
- Gate: 269 JVM/Robolectric tests, 0 failures, 0 errors, 2 environmental skips
  (`HostWhisperJniTest`, replay-jfk); `:app:assembleDebug` + `:app:assembleDebugAndroidTest` green.
  v1-verify scanners pass.

## Files

| File | What |
|---|---|
| `app/src/test/kotlin/com/martins/whisper/harness/WavFixtures.kt` | WAV read (16 kHz mono PCM16), frames, dBFS math, synthetic harmonic speech-like generator |
| `app/src/test/kotlin/com/martins/whisper/harness/WavReplayRobolectricTest.kt` | replay through `DictationService`; jfk word/verdict assertions; dBFS sweep; writes report (`WHISPER_REPLAY_OUT`) |
| `scripts/replay-wav.sh` | one-command replay: private Gradle home guard, one-time host build, prints peak/RMS dBFS + verdict + transcript |
| `scripts/host-jni-test.sh` | fixed host build: SONAME-major symlinks, `CC`/CMake compiler wiring, `HOST_JNI_BUILD_ONLY=1` |
| `scripts/verify/scan-allowlist.txt` | `SKIP:…WavReplayRobolectricTest.kt` (env-gated jfk case, same rule as `HostWhisperJniTest`) |
| `.gitignore` | `recordings/` (human recordings stay local) |

No production code was changed. No capture/gate code was touched.

## How to run (verified commands)

```sh
cd /work/martins-whisper-worktrees/replay1
export GRADLE_USER_HOME=/home/node/gh-replay1      # private copy, never the shared home
scripts/replay-wav.sh                              # public jfk.wav, first run builds host libs
scripts/replay-wav.sh /path/to/recording.wav       # arbitrary 16 kHz mono PCM16 WAV
```

Actual output (jfk, first-run build then replay):

```
== Replaying /work/martins-whisper-worktrees/replay1/app/src/main/cpp/whisper.cpp/samples/jfk.wav
wav=…/app/src/main/cpp/whisper.cpp/samples/jfk.wav
samples=176000
duration_ms=11000
peak_dbfs=-2.13
rms_dbfs=-16.95
gate=SPEAK
transcript=And so my fellow Americans ask not what your country can do for you, ask what you can do for your country.
replay-wav: PASS
```

Actual output (quiet synthetic -50 dBFS WAV, `/tmp/quiet50.wav`):

```
peak_dbfs=-47.02
rms_dbfs=-50.04
gate=REJECT
transcript=
reason=Didn't catch that
replay-wav: PASS
```

Gate run (offline works now; first run needed network once to cache
`androidx.test.ext:junit:1.2.1` / `espresso-core:3.6.1`, absent from the copied Gradle home):

```sh
JAVA_HOME=/work/android-toolchain/jdk ANDROID_HOME=/work/android-toolchain/sdk \
GRADLE_USER_HOME=/home/node/gh-replay1 \
./gradlew --offline --rerun-tasks \
  -Pandroid.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/35.0.1/aapt2 \
  :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
# BUILD SUCCESSFUL
# 43 suites, 269 tests, 0 failures, 0 errors, 2 skipped
```

Scanners (python3 installed via nix this shift):

```sh
for s in scan-weak-tests.sh scan-mocks.sh scan-oracle-copy.sh; do bash scripts/verify/$s; done
# PASS ×3
```

Synthetic sweep (from the test report):

```
level_dbfs=-60.00 … gate=REJECT reason=Didn't catch that
…
level_dbfs=-37.50 gate=REJECT
level_dbfs=-37.00 gate=SPEAK transcript=hello world
…
level_dbfs=-20.00 gate=SPEAK
gate_flip=between -37.50 and -37.00 dBFS
declared_speech_threshold_dbfs=-36.48
```

## Design / invariants

- `WavReplayRobolectricTest` asserts the reflected `DictationService.SPEECH_RMS_THRESHOLD`
  equals the harness constant, so drift in the production gate fails the sweep.
- The replay asserts the end state is either `Success` (and text inserted) or a **gate**
  `Error` (`Didn't catch that` / `Microphone returned silence`). Engine-load/insert failures
  therefore fail the test instead of looking like a quiet recording.
- Literal-word assertions only run when `WHISPER_REPLAY_EXPECT` is set (script sets it for
  `jfk.wav`) or the WAV is named `jfk.wav`. Human recordings assert path/verdict consistency
  and always print the transcript.
- WAV format is strict: RF64/WAVE, PCM16, mono, 16 kHz. Debug dumps from 1(d) must use that.

## TRAPS (verified)

1. **Host native build must use a glibc-compatible compiler.** The nix gcc here (16.2) links
   `GLIBC_2.43/2.44`; the Temurin JVM runs on system glibc 2.36 and cannot load it
   (`UnsatisfiedLinkError`). Use conda-forge `aarch64-conda-linux-gnu-g++` (sysroot glibc 2.17).
   `scripts/replay-wav.sh` auto-prefers `/home/node/mamba/env/bin/aarch64-conda-linux-gnu-g++`
   when present; otherwise pass a working `CC`/`CXX`. Never reuse host libs built by another
   container/toolchain.
2. **SONAME-major symlinks are required.** whisper.cpp installs `libwhisper.so.1.9.5`; the JNI
   wrapper needs `libwhisper.so.1` at load time. `host-jni-test.sh` previously only made
   `libwhisper.so`, so JNI load failed and `HostWhisperJniTest` silently *skipped* (assumeTrue).
   Fixed in this branch; the replay proves the real engine loads.
3. `Libwhisper.so.1` must be in the same dir as `libmartins_whisper.so` (rpath `$ORIGIN`);
   the script exports `LD_LIBRARY_PATH` too.
4. `--rerun-tasks` is needed for a replayed WAV's *contents* to take effect (Gradle does not
   hash env vars); the script always passes it.
5. `ShadowSystemClock.advanceBy(700 ms)` before release avoids the `<400 ms` hold-to-talk
   branch, so quiet WAVs reach the real gate verdict.
6. Private Gradle home only: `/home/node/gh-replay1`. Shared home guard enforced by script.

## Known limitations / next

- jfk tests were run with `ggml-tiny.bin` (multilingual) and `language=en`.
- Human recordings replay later via the same script; no golden WER yet (that is H1/owner data).
- MARA merges from this handoff; TESS does not merge. Nothing else in the capture/gate code
  was touched, so MARA's telemetry/gate work should merge cleanly except `host-jni-test.sh`
  (test script only) and the allowlist.

## References

- Repo `scripts/host-jni-test.sh`, `HostWhisperJniTest`, `DictationServiceRobolectricTest`
  (replay pattern), `DictationEngineHost` (host-testable service path).
- Robolectric 4.14.1 `ShadowSystemClock.advanceBy(Duration)`.
- DEVICE-REPORT #1 items 1(a)-(e), MFVP failure-mode list (hallucinated silence).
