# PARK — TESS (2026-10-26, killed before refill)

## DONE (SHAs)

- **F1 chunker fix** `cef801a` (merged to master `1d9563a`): `tailFrom` stops at the cut
  frame; exact 11_680-sample regression. Gate then core 16/138 + app 18/80.
- **MFVP failure-path tests** `248c46a` on `agent/f1-chunker` (+ docs `b62440d`, `9ccd117`,
  tip `9ccd117`, based on merged F1): 6 Robolectric tests on the real DictationService
  (engine exception, blank transcript, clipboard/insert fallback, missing mic permission,
  too-short press). Gate run then: core 16/138 + app 19/86 = **224 tests, 0 failures**.
  UNMERGED; diff vs master is only the new test class + handoffs. Handoffs:
  `TESS-F1/`, `TESS-MFVP-TESTS/`.
- Earlier merged T1/T2 harness work: see `handoffs/TESS-1`, `TESS-2`.

## RUNNING

- None. No workers launched this shift.
- **T4 L2 tracer: NOT STARTED** (killed during research; no branch/worktree created).
  Research done against current master `1de97d6`:
  - Reconciled inserter is `com.martins.whisper.service.AccessibilityTextInserter` (master
    `1d5056f`); the `accessibility/` duplicate was deleted.
  - Service path: `DictationService.onPress` -> AudioCaptureBuffer -> ModelManager ->
    `DictationEngineHost.transcribe` -> `AccessibilityTextInserter` -> `AccessibilityInserter.insertNow`
    -> real `DictationAccessibilityService.insert` -> `InsertionPolicy` -> `setText`/paste.
  - **Gap:** no guard for focus/window/package changing between press and insert; the target
    is looked up fresh at insert, so text can land in a different app. Ticket needed.
  - Robolectric 4.14.1 cannot drive the live accessibility tree: no shadow for
    `AccessibilityNodeInfo.findFocus` or `AccessibilityService.rootInActiveWindow`, so the
    real `obtainTarget()` sees nothing → tracer needs a minimal injection seam.

## NEXT (ordered, each a small ticket)

1. **T4-1 seam** on `agent/t4-tracer` (from master `1de97d6`): `internal var
   focusedNodeProvider: (() -> AccessibilityNodeInfo?)?` consulted first in
   `DictationAccessibilityService.obtainTarget()`; null in production. No behaviour change;
   document in handoff.
2. **T4-2 target guard**: capture target package (and window if cheap) at session start
   (`DictationService.onPress` -> accessibility inserter `beginSession()`); at insert, if the
   focused node's package/window differs, return Failed/Copied and write nothing to any node.
   Unit test both match and change.
3. **T4-3 L2 tracer Robolectric**: synthetic tone burst + silence frames -> real
   DictationService -> real AudioCaptureBuffer/ModelManager -> scripted engine returning a
   literal transcript -> real AccessibilityTextInserter -> real DictationAccessibilityService
   -> fake `AccessibilityNodeInfo` whose OnPerformActionListener applies ACTION_SET_TEXT.
   Assert: exact final node text; silence inserts nothing; package/window change inserts
   nothing. Then mutation table (one mutation per assertion, record killed/survived).
4. **VERA coordination** via `handoffs/TESS-T4/VERA.md`: hand her the tracer path, the fake-node
   recipe, and the 3 mutations so her walls cover the same class. She owns the walls; do not
   duplicate scan scripts.
5. If time: re-run full `:core:test :app:testDebugUnitTest` and `:app:assembleDebug` on
   `agent/t4-tracer`; handoff `handoffs/TESS-T4/`. TESS does not merge.

## TRAPS (verified this shift)

- **SHIFT.md is a snapshot**: master moved from `0b71e0e` to `1de97d6` mid-shift (u2-states
  merged/reconciled). Always `git log -1` before branching.
- Robolectric 4.14.1 `ShadowAccessibilityNodeInfo.performAction` only records the action and
  calls an optional `OnPerformActionListener`; ACTION_SET_TEXT does NOT mutate text by
  default. The fake node must apply the Bundle (`ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE` +
  selection) itself. `recycle()` on the shadow throws if the node is not in its
  obtained-instances map — avoid double recycle / use API 34 no-op expectations carefully.
- `ShadowAccessibilityService.setWindows(...)` exists but node-level `findFocus` is not
  shadowed; the seam is the only practical route.
- Only one `TextInserter` family remains after `1d5056f` (service package). Do not resurrect
  the accessibility-package adapter.
- Gradle: private `/home/node/gh-f1` (copy of toolchain gradle-home); `--offline` works.
  Never shared gradle-home. No build outputs left uncommitted.
- Do not merge `agent/f1-chunker` docs commit into a product branch without the test class;
  it is tests+handoffs only.
