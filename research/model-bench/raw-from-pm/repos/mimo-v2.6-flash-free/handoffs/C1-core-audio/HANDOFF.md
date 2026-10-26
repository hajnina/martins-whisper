# C1-core-audio Handoff

Executive summary: Implemented the pure-JVM core audio/text layer: normalized RMS math, adaptive energy VAD with hangover, a speech-aware 20 ms chunker with pre-roll/min-speech/silence/max-length/overlap/session-cap behavior, text cleanup, and transcript boundary dedupe. Added 25 focused JUnit tests and all pass with the requested Gradle command. Public contract signatures are unchanged. Commit 838dc30 on `agent/c1-core-audio`.

## Built / changed

- `core/src/main/kotlin/com/martins/whisper/core/audio/AudioMath.kt`
  - `rms(ShortArray)`, `rmsFloat(FloatArray)`, `shortToFloat`, `floatToShort`.
  - RMS is normalized by `Short.MAX_VALUE`; empty input returns `0.0`.
- `core/src/main/kotlin/com/martins/whisper/core/audio/EnergyVad.kt`
  - `EnergyVad : Vad` with defaults `minSpeechRmsDb=-45`, `thresholdMultiplier=3`, `hangoverFrames=8`, EMA alpha `0.02`.
  - Speech requires above both the absolute/floor+6 dB and floor*multiplier thresholds, with 8-frame hangover; `reset()` clears floor/state.
- `core/src/main/kotlin/com/martins/whisper/core/audio/AudioChunker.kt`
  - `AudioChunker : AudioChunkerApi`: 16 kHz / 320-sample (20 ms) framing, 300 ms pre-roll, 250 ms minimum speech, 500 ms silence finalize, 25 s max chunk, 250 ms split overlap, 10 min cap, monotonic indices, `startMs/endMs/overlapMs`, `speechMs`, `flush()`, `reset()`.
  - MAX_LENGTH cut searches the last 2 s for the lowest-energy frame boundary; overlap is exact via sample trimming. `overlapMs` describes overlap at the **start** of a chunk, so the next chunk carries `250`; the first chunk keeps its prior start overlap (normally `0`).
- `core/src/main/kotlin/com/martins/whisper/core/text/TextNormalizer.kt`
  - Whitespace collapse/trim, punctuation spacing cleanup, blank -> `""`, drops common Whisper silence/hallucination strings.
- `core/src/main/kotlin/com/martins/whisper/core/Contracts.kt`
  - Only the `TranscriptMerger.merge` body was replaced (local helpers inside the body). Max 8-word suffix/prefix dedupe, case/punctuation-insensitive comparison, richer original punctuation retained, zero/positive `overlapsMs` honored, single-space output, no invented text.
- Tests:
  - `core/src/test/kotlin/com/martins/whisper/core/EnergyVadTest.kt` (6)
  - `core/src/test/kotlin/com/martins/whisper/core/AudioChunkerTest.kt` (7)
  - `core/src/test/kotlin/com/martins/whisper/core/TextNormalizerTest.kt` (5)
  - `core/src/test/kotlin/com/martins/whisper/core/TranscriptMergerTest.kt` (7)

## Build / test

Command run from `/work/martins-whisper-worktrees/c1-core-audio`:

```bash
JAVA_HOME=/work/android-toolchain/jdk /home/node/.local/bin/gradle -p /work/martins-whisper-worktrees/c1-core-audio :core:test --no-daemon
```

Result:

```text
BUILD SUCCESSFUL in 9s
4 actionable tasks: 4 executed
```

Test result XML totals: `6 + 7 + 5 + 7 = 25` tests, 0 failures, 0 skipped, 0 errors.

## Interfaces / invariants

- No public contract signatures changed. `AudioChunker` has a default `Vad` constructor (`AudioChunker(vad: Vad = EnergyVad())`).
- Chunker processes arbitrary frame sizes by splitting into 320-sample sub-frames; timestamps are derived from `capturedAtMs`.
- Pre-roll is bounded to 300 ms preceding detected speech. Short speech under 250 ms is discarded and does not emit a chunk.
- Silence finalization is based on actual (audible) silence rather than VAD hangover, so a chunk ends about 500 ms after the last audible frame.
- MAX_LENGTH chunks are <= 25 s; the following chunk starts 250 ms before the cut with `overlapMs = 250`.
- Session cap is measured from the first pushed timestamp; no emitted chunk ends beyond `first + 600_000 ms`. A cap-triggered final chunk uses `ChunkReason.FLUSH`.
- `speechMs` counts audible speech frames seen (including a short discarded blip); chunk-level min-speech gating uses chunk-local audible duration.

## Limitations / next agent

- VAD/audibility thresholds are intentionally simple and not tuned on real microphone data; `speechFloorRms` is 10% of the first speech-frame RMS, bounded below at `1e-4`. Very low-level speech after a loud onset could be undercounted. Tune against a real corpus/service integration.
- No Android service, recorder, model, or UI integration in this package.
- MAX_LENGTH cut is frame-boundary based within the last 2 s; exact overlap is sample-trimmed.
- `AudioChunker.speechMs` counts incoming audible speech, not emitted-chunk duration. If the service needs exact emitted speech seconds, compute from emitted `AudioChunk`s instead.

## References used

- No external code/URLs copied. Implemented from the supplied contract/requirements and the existing Kotlin/JUnit project setup.

## Commit SHA(s)

- `838dc30` - implementation and tests (`feat(core): implement audio chunking, VAD, and transcript merging`).
- Handoff commit: see this commit (`handoffs/C1-core-audio` docs only).
