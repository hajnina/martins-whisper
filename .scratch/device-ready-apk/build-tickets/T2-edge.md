# T2-edge — property/edge tests for core text + audio

Owner: W1 (branch `agent/t2-edge`, worktree `/work/martins-whisper-worktrees/t2-edge`)
Status: done (W1, 2026-10-26)
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

Done. Added 48 JUnit4 edge/property tests in four new core suites; no production, app, script, or
dependency changes.

Files added:
- `core/src/test/kotlin/com/martins/whisper/core/TextInsertionPlannerEdgeTest.kt` (13)
- `core/src/test/kotlin/com/martins/whisper/core/text/TextNormalizerEdgeTest.kt` (10)
- `core/src/test/kotlin/com/martins/whisper/core/TranscriptMergerEdgeTest.kt` (13)
- `core/src/test/kotlin/com/martins/whisper/core/AudioChunkerEdgeTest.kt` (12)

Acceptance (offline, `GRADLE_USER_HOME=/home/node/gh-t2-edge`):

```sh
./gradlew --offline --console=plain :core:test :app:testDebugUnitTest
```

Plain run: `BUILD SUCCESSFUL`. Forced fresh evidence with the same task graph:
`./gradlew --offline --console=plain :core:test :app:testDebugUnitTest --rerun-tasks`
-> `BUILD SUCCESSFUL in 23s`, `32 actionable tasks: 32 executed`.

Counts from the XML:
- core: 15 suites / 133 tests / 0 failures / 0 errors / 0 skipped (was 11 / 85; +48 new)
- app: 14 suites / 56 tests / 0 failures / 0 errors / 0 skipped (unchanged)

Findings (recorded in `handoffs/TESS-2/W1-EDGE.md`; production intentionally untouched):
1. **AudioChunker sample duplication after a mid-chunk MAX_LENGTH cut (real bug).** Repro: 1225
   loud 20 ms frames + one quiet frame at 24,500 ms + 24 loud frames. MAX chunk `0..24_520`.
   Next flushed chunk `24_270..25_000` (730 ms) has 19,360 samples; expected 11,680. `tailFrom`
   already includes the post-cut frames and `afterCut` appends them again.
2. **Session cap end overshoot (minor).** `push(loud,0); push(loud,599_990);
   push(loud,600_000)` returns a FLUSH `599_990..600_010`; the C1 invariant says no chunk ends
   past first + 600,000 ms.
3. **TranscriptMerger non-content token collision (real bug).** `merge(["hi 😀","😁 there"])`
   -> `"hi 😀 there"` (drops 😁); punctuation-only boundary tokens collide the same way.
4. **TranscriptMerger >8-word overlap dedupes nothing.** A 10-word repeated boundary is left
   fully duplicated; the exact 8-word case still dedupes. `overlapsMs` is also indexed by raw
   part position, including blank parts.
5. **TextNormalizer is ASCII-only for internal whitespace/punctuation.** `"a\u00A0b"` and
   `"a\u3000b"` keep the internal space; Arabic comma/question (`U+060C`, `U+061F`) keep the
   leading space. ASCII punctuation around RTL works.
6. **TextInsertionPlanner protects surrogate pairs only, not grapheme clusters.** Combining
   marks and ZWJ boundaries can be split (no lone surrogates); matches the documented contract.

Merge: branch `agent/t2-edge`, base `master` `88d189a`; `git diff --name-only master...HEAD --
core/src/main app scripts` is empty. Tests commit `b7f0ae7`; docs/ticket commit is the final
`agent/t2-edge` tip. Handoff: `handoffs/TESS-2/W1-EDGE.md`.
