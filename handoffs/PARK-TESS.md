# PARK — TESS (live, 2026-10-26, MFVP shift)

Master at shift start: `636b3f9` (gate 217/0). Worktree: `/work/martins-whisper-worktrees/f1-chunker`
(branch `agent/f1-chunker`, based on `636b3f9`). Nothing merged by TESS.

## DONE on agent/f1-chunker

- **F1 fix** `cef801a`: `AudioChunker.splitAtMaxLength` no longer duplicates post-cut audio
  (`tailFrom(fromMs, lastIndex)` stops at the cut frame). Regression in
  `AudioChunkerEdgeTest` asserts exactly 11_680 samples for the W1 repro; low-energy test now
  exact; random stream asserts `samples <= duration`. Gate green: core 16/138, app 18/80,
  0 failures/errors (rerun, offline). Handoff `handoffs/TESS-F1/HANDOFF.md`.
- Earlier merged work (T1/T2) unchanged: see git log / `handoffs/TESS-1`, `TESS-2`.

## RUNNING (this note updated after each step)

- **Priority 2 — MFVP-path service failure tests** (tests only, app/src/test): engine
  transcribe throws -> Error + no insert + reset; empty transcript -> No speech detected;
  inserter Copied/Failed result surfacing; missing RECORD_AUDIO -> Error before audio;
  too-short press -> Idle, no engine call. No production changes. Branch continues
  `agent/f1-chunker`.

## NEXT (ordered, MFVP critical path only)

1. Finish the failure-path Robolectric tests and run `:app:testDebugUnitTest`.
2. Handoff `handoffs/TESS-MFVP-TESTS/`, update this note, final message to owner.
3. Then stop: no new harness/lint/repro scope. H1 phone smoke is ASHA/owner work.

## FINDINGS for PM/owner (not fixed here)

- **MFVP gap:** `DictationService.inserterFactory` defaults to `ClipboardTextInserter`, so the
  tap→speak→tap flow copies to the clipboard and does NOT insert into the focused field.
  `DictationAccessibilityService` + `AccessibilityInserter` exist but nothing wires them into
  the service (`service.TextInserter` vs `accessibility.TextInserter` are different interfaces).
  This blocks MFVP item 2 (real insertion) and needs an ASHA ticket.
- **F1 fixed**; F2 (frame can end past 600 s cap), F3 (merger drops distinct emoji/punct),
  F4/F5/F6 (from W1-EDGE) remain open, none on the current hot service path.

## TRAPS (verified)

- Never use `/work/android-toolchain/gradle-home`; this shift uses private
  `/home/node/gh-f1` (`cp -r` from the shared copy). Offline is enough.
- Robolectric: `@ConscryptMode(OFF)`, `@Config(sdk=[34])`, idle the main looper; overlay
  `isShowing` false in Robolectric.
- One branch for both priorities to keep MARA to a single small merge; F1 is its own commit.
