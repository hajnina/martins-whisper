# BUILD-READY (MARA, 2026-10-26)

**SHA: `4fd07e6`** (master tip, clean tree, all work committed) — push master and dispatch `native-build.yml`.

## Full offline gate evidence
- `source /work/android-toolchain/scripts/env.sh; GRADLE_USER_HOME=/home/node/gh-mara`
- `gradle :core:test :app:testDebugUnitTest :app:assembleDebug --offline -Pandroid.aapt2FromMavenOverride=/work/android-toolchain/sdk/build-tools/35.0.1/aapt2`
- result: **BUILD SUCCESSFUL; 49 suites, 300 tests, 0 failures, 0 errors, 2 env-skips** (`HostWhisperJniTest`, `WavReplayRobolectricTest.jfk` — they need the host native build/`replay-wav.sh` or CI).
- verify walls: `scan-weak-tests PASS`, `scan-mocks PASS`, `scan-oracle-copy PASS`.
- Zero-trust: mutation controls ran this shift: flip `marginDb >=` → `<=` kills 4 SpeechEnvelope tests; `|| true` mutation of the relative-verdict `isSpeech` kills 4 tests (`*WavReplay*` + `*SpeechEnvelope*`).

## What changed in this build (top 6)
1. **Relative speech gate** (core `SpeechEnvelope`): noise floor = 10th-percentile 100 ms-window RMS in dBFS; verdict = margin ≥ +10 dB above floor OR uniformly-loud (floor ≥ −45 dBFS); digital silence still a distinct mic error. UI error on no-speech now shows measured levels: "Didn't catch that (peak X dBFS, floor Y dBFS, gate floor+10 dB)".
2. **Telemetry per dictation** (debug-only, local files/telemetry/): `records.jsonl` (one JSONL line: t_tap_start, t_mic_open, t_tap_stop, audio_ms, sample_rate, peak/rms/noise_floor dBFS, gate_threshold, gate_verdict, model, language, threads, engine_load_ms, engine_infer_ms, text_len, target_package, target_changed, t_inserted, insert_method, result, error) + `last.json` mirror. Pull with adb.
3. **Debug WAV dump**: every captured buffer written to `files/debug-audio/<ts>.wav` (+ `.txt` sidecar) on debug builds; replay a pulled file on the host with `scripts/replay-wav.sh <wav>` (real engine + real gate path).
4. **Capture fixes (NORA)**: source chain MIC → UNPROCESSED(if advertised) → VOICE_RECOGNITION with a dead-source probe (300 ms silence → reopen next source); larger buffer (4×minBuffer, ≥8 frames); `read()==0` now errors after ~2 s; capture config logged (tag MartinsWhisper).
5. **UI device fixes (ASHA)**: Android-16 edge-to-edge insets on main screen; live accessibility/status re-check on window focus; download progress % + bytes; raw-delta bubble drag + edge snap persisted; explicit language (English default, Czech; auto opt-in). `recordings/`, `dist/` gitignored.
6. **Host replay (TESS)**: any pulled WAV replays through the REAL service path; sweep adapted to the relative gate (steady ≤ −45 dBFS rejects with measured message; bursty −60 dBFS floor with −30 dBFS bursts inserts — the exact case the old −36.5 dBFS absolute gate lost).

## Owner actions
1. Push master (`git push` from this machine) → dispatch `native-build.yml` → CI builds the arm64 APK (also uploads `latest-apk` artifact; `scripts/fetch-latest-apk.sh`).
2. Flash + run `owner/device/dictate-test.sh`; expected: no more false "No speech detected" on real speech; every attempt leaves a JSONL line + WAV; report → `/work/.yoloseek/DEVICE-REPORT.md`.
3. If a run still fails, attach the WAV: I replay it on the host and turn the failure into a failing replay test FIRST.
