# PARK - NORA (live note, capture1 shift 2026-10-26)

## DONE (this session)
- Read SHIFT / DEVICE-REPORT / MFVP / HTP / briefs.
- **Capture audit** ("no speech" on POCO F8 Pro / HyperOS). Top finding for MARA: the absolute
  per-frame gate (`SPEECH_RMS_THRESHOLD = 0.015f` = -36.5 dBFS RMS, `DictationService.kt:535`
  used at :190) rejects with "no speech energy" even when whisper transcribed correctly; a quiet
  phone mic sits under it. Capture-side: hard-coded `VOICE_RECOGNITION` (optional per OEM,
  silence-prone on HyperOS), 80 ms buffer (4 frames), `read()==0` ignored. Full details in
  `handoffs/NORA-CAPTURE/HANDOFF.md`.
- **Fix (capture-source/config only)**: `AudioRecordSource` chain MIC -> UNPROCESSED (if advertised) -> VOICE_RECOGNITION; 4x minBuffer (>=8 frames); 300 ms dead-source probe + reopen next
  source; read()==0 detection (~2 s) instead of a spin; logcat source/config; `sourceName`
  exposed for telemetry. New test `AudioRecordSourceTest` (4 tests) green.
- Branch `agent/capture1`, worktree `/work/martins-whisper-worktrees/capture1`,
  private `GRADLE_USER_HOME=/home/node/gh-nora` (copy of the shared home).
- `scripts/fetch-latest-apk.sh` (new): gh run list/download newest successful native build,
  arm64/JNI sanity, sha256 + adb hint; `--branch master` default (any-branch / run-id /
  allow-failed flags).
- `native-build.yml`: split `apk` (fast assemble + upload, target <6 min) from `tests` (JVM +
  host JNI tracer) so the artifact lands early; broadened push paths to app/core; NDK/CMake
  sdkmanager fast path only installs what is missing.

## DONE
- Full local gate on `agent/capture1` (GATE.md): BUILD SUCCESSFUL, core 157 + app 114 = 271 tests, 0 failures/errors; assembleDebug + assembleDebugAndroidTest green; actionlint clean; fetch script fake-gh E2E green.

## NEXT
1. MARA: merge `agent/capture1`; pair it with the relative gate/telemetry fix before the next
   device flash (both are needed for "no speech").
2. Owner: `scripts/fetch-latest-apk.sh` after pushing master; check the Actions run duration of
   job `apk` (<~6 min) on the first green run; report the timing back if >6 min.
3. Device: next session should show `peak/RMS dBFS + gate + capture source=MIC` in logcat.

## TRAPS
- No `gh` here and no device: CI timing and on-phone verification are owner-run.
- Keep `GRADLE_USER_HOME=/home/node/gh-nora`; never the shared home.
- `MIC` before `VOICE_RECOGNITION` deliberately; do not reorder without a device measurement.
- Don't touch gate/service (MARA owns); this branch is capture-only.

## BRANCH TIP
- Substantive commits: `b379cb2` (capture), `e62732a` (fetch+CI), `c2910ac` (gate evidence),
  `9cab45b` (MIC first); docs commit on top. Gate green after `9cab45b`.

## NOTE FOR MARA
- Base `c62f533`; your master moved to `cbb17d5` (SpeechEnvelope) after I branched. No file
  overlap: this branch only has `AudioRecordSource.kt` + test, scripts, workflow, handoffs.
- Your relative gate fixes my #1 finding; merge both before the next device flash.
