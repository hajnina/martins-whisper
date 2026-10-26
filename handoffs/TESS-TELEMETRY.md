# TESS — telemetry/state host tests (trunk slice)

Branch `tess-telemetry`, one slice: production seam (3 lines) + `TelemetryWavDumpTest` rewrite.

## Why the seam
`BuildConfig.DEBUG` is a compile-time constant, so the JVM cannot exercise the release path.
`DictationService.debugVariant` (default `= BuildConfig.DEBUG`) replaces it in the two guards
(`wavDumpEnabled()`, default `telemetryFactory`). Release behaviour is unchanged; tests can
simulate release. Revert the 3 lines if MARA prefers another seam — the release test then has
to be dropped or rewritten.

## What the tests pin (7)
- protocol record → `records.jsonl` + `last.json` mirror, all 21 fields.
- WAV header: RIFF/WAVE/fmt, 16 kHz, mono, 16-bit, data size; sidecar stem == WAV stem.
- **no-speech rejected**: WAV + same-stamp JSON (`verdict=no-speech`, levels, error) +
  `last.json` (`gate_verdict=no-speech`, `result=error`) + one JSONL line; state trace contains
  tap-start, capture-start/stop, tap-stop, `state=Loading`, and `gate=no-speech` before `state=Error`.
- **silent-mic rejected**: same artifacts with `verdict=gate=silent-mic`.
- **success**: sidecar `verdict=transcribed`, transcript/inserted_text; telemetry `result=inserted`,
  `text_len`; trace has `gate=speak`, `insert`, `state=Success`.
- **too-short**: WAV + `too-short` sidecar + `result=cancelled` telemetry.
- **release variant** (`debugVariant=false`): successful session leaves no `debug-audio/` and no
  `telemetry/`.

## Zero-trust mutations (each reverted, each killed ≥1 test)
drop `&& debugVariant` (1 fail) · force telemetry guard true (1) · drop `dumpSessionAudio(samples)`
(4) · drop no-speech `writeSidecar` (1) · drop no-speech `publishTelemetry` (1) · drop
`stateLog("gate=no-speech …")` (1).

## Evidence (local, gradle-slot, private GRADLE_USER_HOME)
`TelemetryWavDumpTest` 7/7 green. Repo-wide JVM gate was run on the lane worktree before this
slice: 50 suites, 312 tests, 0 failures, 0 errors, 4 environmental skips; APKs + scanners PASS.
Full gate for this slice runs in the cloud via `land.sh --cloud`.

## Owner/release captain
After landing, B2 device check: run a no-speech tap pair and
`adb shell run-as com.martins.whisper ls files/telemetry files/debug-audio`; both dirs must have
one new entry; `adb logcat -s MartinsWhisperState` must show `gate=no-speech` before `state=Error`.
