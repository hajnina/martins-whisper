# T2-edge — property/edge tests for core text + audio

Owner: W1 (branch `agent/t2-edge`, worktree `/work/martins-whisper-worktrees/t2-edge`)
Status: running (TESS, 2026-10-26)
Scope: `core/src/test/**` only. No production edits.

## Goal

Add edge and property-style JUnit tests (no new dependencies) for the core pipeline:

- `TextInsertionPlanner.insert` (`core/src/main/kotlin/com/martins/whisper/core/Contracts.kt`):
  UTF-16 surrogate pairs, emoji/ZWJ/grapheme clusters, RTL (Arabic/Hebrew), empty insert/field,
  insert at 0/end/mid, invalid or out-of-range selections, huge input, no caret splitting inside
  a surrogate pair (or the documented behaviour if the planner intentionally allows it).
- `TextNormalizer` (`core/.../core/text/TextNormalizer.kt`): whitespace, RTL, punctuation,
  empty/large strings.
- `TranscriptMerger` (`Contracts.kt`): overlap boundaries, empty parts, all-empty, single part,
  non-monotonic/oversized boundaries, RTL, huge part count.
- `AudioChunker` (`core/.../core/audio/AudioChunker.kt`): boundary timestamps (0, exactly
  MAX_LENGTH, one frame over, overlapping silence/speech), empty frames, 600 s hold, 3 h of
  pushes without overflow, index/overlap invariants.

Prefer deterministic loops/seeded `Random` over new libraries; read the implementation first and
assert intended behaviour. If a test exposes a real bug, do NOT change production: put the
failing case in the handoff as a finding with the exact input and observed/expected value.

## Acceptance

```sh
cd /work/martins-whisper-worktrees/t2-edge
GRADLE_USER_HOME=/home/node/gh-t2-edge ./gradlew --offline --console=plain \
  :core:test :app:testDebugUnitTest
```
BUILD SUCCESSFUL, failures 0, new test count printed. Also print the per-suite counts from
`core/build/test-results/test/TEST-*.xml`.

## Result

-
