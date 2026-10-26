# TESS-F1 — AudioChunker post-cut duplication fix (regression tested)

Executive summary: Fixed the real F1 production bug in `AudioChunker.splitAtMaxLength`
(the next chunk re-appended the post-cut frames that the overlap tail already contained, so
every mid-chunk MAX_LENGTH cut duplicated audio). `tailFrom` now stops at the cut frame.
Added an exact-count regression plus an upper bound on samples-per-duration in the random
stream. Full JVM gate green: core 16 suites / 138 tests, app 18 / 80, 0 failures/errors.
No app, script, schema, or dependency changes. F2 (frame may end past the 600 s cap) and
F3–F6 from W1-EDGE remain open and out of this ticket's scope.

## Root cause

`splitAtMaxLength` built the next chunk as `overlapTail + afterCut`, but
`tailFrom(overlapStart)` scanned the whole `chunk`, so `overlapTail` already contained
`afterCut`. Repro (constant 20 ms frames, one quiet frame at 24_500 ms):
emitted MAX chunk 0..24_520 ms (392_320 samples), next chunk 24_270..25_000 ms had
19_360 samples instead of 11_680 (250 ms overlap + 480 ms after the cut). The light case
seen by the random-stream test was 12_670 ms of samples in a 12_530 ms chunk.

Fix: `tailFrom(fromMs, lastIndex)` iterates `0..lastIndex`; the call passes `cutIndex`, so
the overlap tail only comes from the emitted frames. Boundary behaviour is unchanged when
the cut is the last frame (`afterCut` empty).

## Files changed

- `core/src/main/kotlin/com/martins/whisper/core/audio/AudioChunker.kt` — `tailFrom` takes
  `lastIndex`; call site passes `cutIndex`.
- `core/src/test/kotlin/com/martins/whisper/core/AudioChunkerEdgeTest.kt` — tightened the
  existing low-energy-cut test from `>= 11_680` to exactly `11_680` plus duration
  continuity; new `regression F1 mid chunk cut does not duplicate post cut audio in the
  next chunk` (exact size, timeline, overlap prefix equals the emitted chunk's tail); the
  seeded random stream now also asserts samples never exceed the chunk duration.

## Acceptance (actually run)

```sh
cd /work/martins-whisper-worktrees/f1-chunker
export JAVA_HOME=/work/android-toolchain/jdk
export ANDROID_HOME=/work/android-toolchain/sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export GRADLE_USER_HOME=/home/node/gh-f1
./gradlew --offline --console=plain :core:test :app:testDebugUnitTest --rerun-tasks
```

Result: `BUILD SUCCESSFUL in 1m 34s`, 32 actionable tasks executed. Parsed XML:
`core: suites=16 tests=138 failures=0 errors=0`; `app: suites=18 tests=80 failures=0
errors=0`. Red-before-fix evidence: the same three chunker tests failed on the pre-fix
tree (`maximum split ... FAILED`, `seeded random stream ... FAILED`, `regression F1 ...
FAILED`); after the one-line scan-bound fix they pass.

## Invariants now protected

- For a MAX_LENGTH cut at frame `c`, the next chunk = frames overlapping the last
  `OVERLAP_MS` of frame `c` (trimmed at `cutEnd - OVERLAP_MS`) + frames strictly after `c`.
- `samples.size / 16 kHz == endMs - startMs` for every emitted chunk (within 1 ms rounding).
- Existing silence/pre-roll/session-cap/reset behaviour unchanged (all prior tests green).

## Not in scope / still open

- F2: a frame whose start is just below `SESSION_CAP_MS` can end past the cap (check the
  frame end, not only the start).
- F3: `TranscriptMerger` drops distinct emoji/punctuation tokens (non-letter/digit tokens
  normalize to `""`). F4/F5/F6 are API/product decisions.
- The MFVP service path captures frames in `AudioCaptureBuffer` and transcribes one chunk
  (`DictationEngineHost`); it does not call `AudioChunker`, so this fix is not on the hot
  MFVP path today, but any chunker use (and core API consumers) is now correct.

## Commits

- `cef801a` — `fix(audio): F1 chunker duplicated post-cut audio in next chunk`
- Handoff/park commit reported in the final message (self-referential SHA omitted).

Reference: W1-EDGE F1 report in `handoffs/TESS-2/W1-EDGE.md`.
