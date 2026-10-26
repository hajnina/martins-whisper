You are C1-core-audio, a background implementation subagent for Martins Whisper (Android/Kotlin).

WORKTREE (work only here): /work/martins-whisper-worktrees/c1-core-audio
Read first: SPEC.md (especially 3.4), KANBAN.md, DECISIONS.md, AGENTS.md, core/src/main/kotlin/com/martins/whisper/core/Contracts.kt.
Do not edit the main checkout. Do not merge. Commit on branch agent/c1-core-audio.

YOUR JOB: implement the pure-Kotlin, Android-free audio/chunking/merging core, with strong JUnit tests.

Deliverables (new files under `core/src/main/kotlin/com/martins/whisper/core/audio/` and `.../text/`, plus tests under `core/src/test/kotlin/...`):
1. `EnergyVad` implements `Vad`: adaptive noise floor (EMA), configurable absolute floor/threshold multiplier, speech start hangover, reset per session. Handle silence/zeros, constant noise, loud speech, short blips. Thread-safe enough for single audio thread (document).
2. `AudioChunker` implements `AudioChunkerApi`:
   - 20 ms frames (configurable sample rate), pre-roll ~300 ms, min speech 250 ms, silence finalize 500 ms, max chunk 25 s, overlap 250 ms on MAX_LENGTH cuts, 10 min session cap.
   - `push()` returns zero or more finalized `AudioChunk`s (SILENCE or MAX_LENGTH); `flush()` returns final FLUSH chunk; cap produces a final chunk and resets/discards appropriately.
   - choose max-length cut near lowest-energy frame in last 2 s; include overlap from tail; avoid infinite loops.
   - track timestamps (`capturedAtMs`), reasons, overlapMs, index, speechMs.
   - drop chunks with no speech; handle flush after no speech -> null.
3. `RingBuffer` (float/short) for pre-roll, and any small `AudioMath` helpers.
4. `TranscriptMerger` implementation replacing the stub in Contracts.kt. DO NOT break the public signature. Merge chunk texts with token-level longest suffix/prefix dedupe (case/punctuation-insensitive, up to 8 words), preserve punctuation, one space, no invented text. Handle overlapsMs hints (only dedupe when overlap > 0? Document behavior).
5. `TextNormalizer`/hallucination filter: trim/collapse whitespace; drop low-confidence segments based on Transcript fields if provided; small blocklist for known Whisper hallucinations only when low confidence. Return empty for silence.
6. Tests (JUnit4 or kotlin-test) covering: synthetic speech/silence, first-word pre-roll, short blips ignored, long silence finalization, 25 s forced split, overlap dedupe, flush, max session, timestamp monotonicity, merge punctuation/duplicates/case, insertion edge cases not yours (I1 owns planner but stub exists).
7. Run `./gradlew :core:test` or `gradle :core:test`; if JDK/Gradle are not ready, check `/work/martins-whisper-worktrees/b1-build/TOOLCHAIN.md` and use that; otherwise install JDK17+Gradle via Nix in your own shell (`nix shell nixpkgs#jdk17 nixpkgs#gradle -c gradle ...`) and report exact commands. Do not claim tests passed without output.
8. Handoff `handoffs/C1-core-audio/HANDOFF.md` + SUMMARY.txt, commit.

Rules: no Android imports in :core. No new runtime dependencies. Keep code simple, documented only where non-obvious. If you must adjust Contracts.kt, only replace the stub body and note it; do not change signatures. Final message = executive summary + test evidence.
