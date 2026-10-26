# NORA / capture1 handoff (for MARA)

Branch `agent/capture1`, worktree `/work/martins-whisper-worktrees/capture1`, base master `c62f533`.
Ticket: (1) audit real-phone capture for "no speech", fix capture-source/config only;
(2) `scripts/fetch-latest-apk.sh` + native CI uploads the APK in <~6 min.

## AUDIT: why a real phone can say "no speech" (read-only findings for MARA)

Pipeline: `AudioRecordSource` (capture) -> `AudioCaptureBuffer` -> `DictationService.onRelease`
-> `speechDetected` flag -> `TranscriptGate.evaluate(text, speechDetected, peak)`.

1. **Gate is absolute and per-frame (MARA's area, NOT changed by me).**
   `DictationService.kt:535` `SPEECH_RMS_THRESHOLD = 0.015f` (RMS = **-36.5 dBFS**); used at
   `DictationService.kt:190` per 20 ms frame. If no single 20 ms frame exceeds it, `speech=false`
   and `TranscriptGate.kt:50` rejects with `Reject("no speech energy")` **even when whisper
   produced a correct transcript**. A phone mic at normal dictation distance / quiet voice /
   VOICE_RECOGNITION gain routinely sits between -45 and -35 dBFS RMS, so this is the most
   likely direct cause. `EnergyVad` already has a relative `noiseFloorDb + 6 dB` rule; reuse it
   or lower/relativize the gate and judge the whole buffer. The device report's telemetry
   (peak/RMS dBFS + gate verdict) will confirm.
2. **Capture source was VOICE_RECOGNITION (fixed here).** `AudioSource.VOICE_RECOGNITION` is
   documented as "if available, behaves like DEFAULT otherwise"; on some OEMs it routes to a DSP
   or a privacy-muted path that delivers silence/near-silence to the app. Xiaomi/HyperOS is a
   known class of device for this. It was the hard-coded first and only choice, no fallback.
3. **Buffer too small (fixed here).** `maxOf(minBuffer, 320*2*4) = 2560 B = 80 ms` (4 frames).
   A single minBuffer gives the HAL no slack during engine load/GC/logcat; underruns show up as
   clipped/gappy audio, not just silence. Now `4 * minBuffer` and >= 8 frames.
4. **`read() == 0` was ignored (fixed here).** The loop spun on 0 with no error. If another
   app holds the mic, OSC/privacy mute, or a dead HAL source, the user talks into a void and
   only later gets "no speech". Now 0-returns are counted and fail after ~2 s with a distinct
   error: "Microphone returned no audio (source=...)".
5. **16 kHz / mono / PCM_16BIT is correct** for whisper.cpp; do not change. `getMinBufferSize`
   is checked (>0) and the recorder state is validated. Keep it.
6. **HyperOS foreground rule:** HyperOS only feeds real mic audio while the app is "in use";
   backgrounded capture gets silence (not an error). We already call
   `ensureForeground(withMicrophone = true)` before `startAudio`; keep that ordering. If the
   service is ever started from the background, the mic will be silent on HyperOS.
7. No explicit AGC/NS control exists in the API; `UNPROCESSED` is the only raw option and it is
   optional (`AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED`). MIC is the
   default; UNPROCESSED is the second candidate when advertised.

## FIX on this branch (capture-source/config only)

`app/src/main/java/com/martins/whisper/audio/AudioRecordSource.kt`:
- Source chain: MIC -> UNPROCESSED (only if advertised) -> VOICE_RECOGNITION
  (never start with VOICE_RECOGNITION; MIC is universal).
- Dead-source probe: after 15 frames (300 ms) with peak <= 8/32768 (~-72 dBFS), stop/release
  and reopen with the next source; logs a warning. Real room noise/quiet speech has higher
  peaks, so a user who pauses before speaking is not switched. Disabled when `preferredSource`
  is set (tests).
- Buffer `max(4 * minBuffer, 8 * frameBytes)`; validates `recordingState` after start.
- `read()==0` handled (2 s grace, 5 ms backoff, then error) instead of a busy loop.
- Logcat (tag `MartinsWhisper`) source/config line:
  `capture start: source=MIC rate=16000 channels=1 frame=320 samples buffer=<bytes>`
  and `capture: <SRC> returned only silence (peak=...)` on fallback. Exposed to code as
  `AudioRecordSource.sourceName` if you want it in the telemetry JSONL.
- New JVM test `app/src/test/java/com/martins/whisper/audio/AudioRecordSourceTest.kt`
  (MIC before VOICE_RECOGNITION, UNPROCESSED second when supported, preferred override, names).
  Green: `:app:testDebugUnitTest --tests com.martins.whisper.audio.AudioRecordSourceTest`.

I did **not** touch `DictationService`, `TranscriptGate`, `EnergyVad` or WAV dumping (yours).

## CI / flash loop (same branch)

- `scripts/fetch-latest-apk.sh`: newest **successful** `native-build.yml` run via `gh run list`
  + `gh run download`, verifies the arm64 `.so` is inside, prints sha256/size and the adb install
  command. Defaults: repo from origin, branch `master`; flags `--branch`, `--any-branch`,
  `--run-id`, `--out`, `--no-verify`, `--allow-failed`. Requires `gh` authenticated on the
  owner's host (no credentials in this container).
- `native-build.yml` split so the APK uploads early: job `apk` (assembleDebug + verify + upload,
  target <6 min warm) runs in parallel with job `tests` (JVM tests + host JNI tracer). Push
  path filters broadened so app/core changes actually trigger the native build.

## LOCAL EVIDENCE

- `:app:testDebugUnitTest --tests com.martins.whisper.audio.AudioRecordSourceTest` => BUILD
  SUCCESSFUL (see commit message / log in handoff dir if stored).
- Full gate on this branch: see `handoffs/NORA-CAPTURE/GATE.log` after the run.
- No real-device run from this container (no adb device, no gh credentials).

## FOR MARA

1. Merge `agent/capture1` (capture only) with the usual gate.
2. Your gate/telemetry ticket is the other half: make the gate relative and log peak/RMS in
   dBFS + verdict; the device report expects both fixes in one flash.
3. If the next device run still logs `source=MIC` with healthy peak/RMS but no text, it is an
   engine/model issue, not capture. If it logs `returned only silence` for every source, the
   mic is held/muted by HyperOS -> check foreground-service state.

## FLASH-LOOP EVIDENCE (local, no GitHub credentials here)

- `scripts/fetch-latest-apk.sh --help` and fake-`gh` runs:
  - happy path: picks run 424242, downloads artifact, copies to
    `dist/latest-apk/martins-whisper-latest.apk`, checks `lib/arm64-v8a/libmartins_whisper.so`,
    `libwhisper.so`, `libggml.so`, prints sha256/size + adb command, writes `latest-apk.txt`.
  - no successful run: exits 4 with the `--allow-failed` / `--any-branch` hint.
  - `--run-id 999`: resolves metadata via `gh run view` and downloads.
  - `bash -n` clean. Real gh/CI execution is owner-only (this container has no gh auth/device).
- `native-build.yml`: `actionlint` (nix actionlint) => exit 0. Also caches `app/.cxx` (keyed on the pinned whisper ref + CMakeLists/JNI/build.gradle) so a Kotlin-only change does not recompile whisper.cpp. Jobs:
  - `apk`: SDK fast path (only missing packages), cached whisper.cpp, JNI check,
    `:app:assembleDebug -Pmartins.native=true`, `native-apk-check.sh`, upload => target <6 min
    warm. Unit tests were removed from this job so the artifact is not gated on them.
  - `tests`: `:core:test :app:testDebugUnitTest` + host JNI tracer, parallel.
  - Push path filters now include `app/**`, `core/**`, `scripts/**` (before, a change in
    `app/src/main/java/.../audio/` did not trigger a native build at all).
- ACTION NEEDED after the first green owner push: report the `apk` job wall time; if >6 min,
  the remaining cost is the whisper.cpp NDK compile and would need a `.cxx` cache keyed on the
  pinned whisper ref + CMakeLists/JNI hashes (do not add without measuring).
