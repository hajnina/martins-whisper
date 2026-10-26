You are C1-core-audio (v2), background implementation subagent. START WRITING CODE IMMEDIATELY. Do not install JDK/Gradle, do not use nix. The shared toolchain is READY.

WORKTREE: /work/martins-whisper-worktrees/c1-core-audio
Read: SPEC.md (3.4), AGENTS.md, core/.../Contracts.kt. Commit on agent/c1-core-audio. Do not merge.

TOOLCHAIN:
- JAVA_HOME=/work/android-toolchain/jdk
- Gradle wrapper: /home/node/.local/bin/gradle
- Run: JAVA_HOME=/work/android-toolchain/jdk /home/node/.local/bin/gradle -p /work/martins-whisper-worktrees/c1-core-audio :core:test --no-daemon

IMPLEMENT (pure Kotlin, no Android imports):
1. core/audio/EnergyVad.kt - adaptive noise floor EMA, absolute floor/threshold multiplier, speech hangover, reset; handles zeros/constant noise/loud speech/short blips.
2. core/audio/AudioChunker.kt implementing AudioChunkerApi - 20ms frames, 300ms pre-roll, min speech 250ms, silence finalize 500ms, max chunk 25s, 250ms overlap on MAX_LENGTH cut near lowest energy in last 2s, 10min cap, timestamps/reason/index/overlapMs/speechMs, flush semantics.
3. core/audio/RingBuffer.kt + AudioMath (RMS), as needed.
4. Replace TranscriptMerger stub body in Contracts.kt with token-level suffix/prefix dedupe (case/punctuation-insensitive, <=8 words), preserve punctuation, one space, no invented text; keep signature.
5. core/text/TextNormalizer.kt - whitespace normalization, low-confidence hallucination blocklist; empty on silence.
6. JUnit tests under core/src/test: synthetic speech/silence, pre-roll first word, short blips, silence finalize, 25s forced split + overlap, flush, cap, timestamps, merge duplicates/punctuation/case, normalizer.
7. Run tests with the command above; paste real output. Handoff handoffs/C1-core-audio/HANDOFF.md + SUMMARY.txt, commit. Final message = executive summary + test evidence.

Timebox: 25 minutes of implementation/testing. Do not research further.
