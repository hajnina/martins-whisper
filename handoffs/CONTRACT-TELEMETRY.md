# CONTRACT: telemetry record (owner: MARA, lane 1 CORE-FIX)

Status: **frozen for B2/B3**. Consumers (replay/TESS, docs/DORA, UX/LENA) may rely on these
field names and meanings. Canonical code: `DictationRecord` / `DictationTelemetry` in
`app/src/main/java/com/martins/whisper/service/TelemetryRecorder.kt`.

## Where it is written (debug builds only, local, never networked)
- `files/telemetry/records.jsonl` — one JSON object per line, appended per finished session.
- `files/telemetry/last.json` — mirror of the most recent record (same object).
- Written for **every** session outcome: inserted, insert-failed, rejected, no-speech,
  silent-mic, too-short, cancelled, error.
- `files/debug-audio/<stamp>.wav` — raw 16 kHz mono PCM16 capture of the session.
- `files/debug-audio/<stamp>.json` — per-session sidecar (below).

## Session record fields (`records.jsonl` / `last.json`)
| field | type | meaning |
|---|---|---|
| `t_tap_start` | long ms (elapsedRealtime) | start tap handled |
| `t_mic_open` | long ms | AudioRecord started |
| `t_tap_stop` | long ms | stop tap handled |
| `audio_ms` | long | captured audio duration |
| `sample_rate` | int | always 16000 today |
| `peak_dBFS` | number (string-formatted 1 dp) | buffer peak |
| `rms_dBFS` | number | buffer RMS |
| `noise_floor_dBFS` | number | 10th-percentile 100 ms window RMS |
| `gate_threshold` | number | relative gate margin in dB (10) |
| `gate_verdict` | string | `speak` / `no-speech` / `silent-mic` / `too-short` / `cancelled` / `error` |
| `model` | string | model id (`base`, ...) |
| `language` | string | effective language or `auto` |
| `threads` | int | engine threads |
| `engine_load_ms` | long | model load time |
| `engine_infer_ms` | long | transcription time |
| `text_len` | int | characters inserted/transcribed |
| `target_package` | string or null | focused app package at insert |
| `target_changed` | bool or null | target guard rejected the insert |
| `t_inserted` | long ms | insert finished |
| `insert_method` | string | `accessibility` / `clipboard` |
| `result` | string | `inserted` / `insert-failed` / `rejected` / `error` / `cancelled` |
| `error` | string or null | user-facing error text |
| `ts_utc` | long ms (wall clock) | record written |

## WAV sidecar fields (`<stamp>.json`)
`verdict` (same vocabulary as `gate_verdict`), `peak_dBFS`, `rms_dBFS`, `threshold`,
`language`, `model`, `transcript`, `inserted_text`, `target_package` (or null),
`engine_ms`, `ts` (wall clock ms), and `error` only when a failure has a reason.
Every sidecar sits next to its WAV with the same `<stamp>`.

## Stability rules
- Additive changes only within B3: new fields may be added, existing names/types never change.
- A rename or semantic change needs a new contract revision and a note in this file.
- No secrets, no device identifiers, no audio in telemetry; audio stays in `debug-audio/`.
