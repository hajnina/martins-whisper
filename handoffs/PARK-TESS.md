# PARK — TESS (2026-10-26, DEVICE-REPORT 1e shift)

## DONE (SHAs, branch agent/replay1 from master c62f533)

- **Host WAV replay through the real path** `964ac87`, `e6b724f` (+ handoff commit):
  `app/src/test/kotlin/com/martins/whisper/harness/WavReplayRobolectricTest.kt` drives
  `DictationService` (real `AudioCaptureBuffer`, `DictationEngineHost`, speech flag, peak/RMS,
  `TranscriptGate`) with a WAV-backed `AudioSource`; real `WhisperCppEngine` for jfk.
  jfk: `SPEAK`, literal JFK words. Synthetic sweep: REJECT <= -37.5, SPEAK >= -37.0 dBFS,
  declared threshold -36.48, exactly one transition.
- **`scripts/replay-wav.sh [WAV]`**: peak/RMS dBFS, gate verdict, reason, transcript.
  Default public jfk; human recordings replay from `recordings/` (now gitignored).
  Verified on jfk (SPEAK) and a -50 dBFS WAV (REJECT "Didn't catch that").
- **Host build fixes in `scripts/host-jni-test.sh`**: SONAME-major symlinks (libwhisper.so.1 —
  without it JNI load failed and the test silently skipped), `CC`/CMake compiler wiring,
  `HOST_JNI_BUILD_ONLY=1`.
- Gate green: 43 suites, **269 tests, 0 failures, 0 errors, 2 env skips**; `:app:assembleDebug`
  + `:app:assembleDebugAndroidTest` green; scan-weak-tests/scan-mocks/scan-oracle-copy PASS.
- Handoff: `handoffs/TESS-REPLAY/` (HANDOFF.md + SUMMARY.txt).
- Unmerged on purpose: MARA merges from handoff. No production code, no capture/gate code changed.

## RUNNING

- None. No workers launched.

## NEXT (small tickets for MARA/VERA)

1. MERGE `agent/replay1`; run replay after MARA's telemetry/gate changes land to confirm the
   jfk words and sweep still pass (`scripts/replay-wav.sh`).
2. H1: replay pulled `files/debug-audio/*.wav` through the script; attach transcript/verdict to
   DEVICE-REPORT. Do not publish human WAVs (gitignored).
3. Optional: add a golden fixture for one owner recording (local only) once 1(d) dumps exist.

## TRAPS (verified this shift)

- **Compiler/glibc:** nix gcc here emits `GLIBC_2.43/2.44`; Temurin JVM is on system glibc 2.36
  and cannot dlopen those libs. Use `/home/node/mamba/env/bin/aarch64-conda-linux-gnu-g++`
  (conda-forge, glibc 2.17 sysroot); the script auto-prefers it. Do not reuse host libs built in
  another container.
- **SONAME:** whisper.cpp produces `libwhisper.so.1.9.5`; `libmartins_whisper.so` NEEDs
  `libwhisper.so.1`. Missing major symlink = `UnsatisfiedLinkError` (and `HostWhisperJniTest`
  skips via assumeTrue — looks green). Fixed.
- Gradle: private `/home/node/gh-replay1`; first online run needed to fetch androidTest deps
  (`androidx.test.ext:junit:1.2.1`, `espresso-core:3.6.1`) missing from the copied Gradle home;
  offline works after. `--rerun-tasks` required to replay changed WAV contents.
- `python3` was absent; installed via `nix profile install nixpkgs#python3` for v1 scanners.
- `scripts/replay-wav.sh` exits 2 for shared/absent GRADLE_USER_HOME and for a missing WAV.
