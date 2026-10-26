# PARK-ASHA — owner acceptance list (a1-insert-quality)

Date: 2026-10-26. Shift: implementing `/work/owner/MFVP.md` ACCEPTANCE items 1–5 on
`agent/a1-insert-quality` (worktree `/work/martins-whisper-worktrees/a1-insert-quality`).

## DONE (branch `agent/a1-insert-quality`)
- `f220ea4` ticket 1 — silence/hallucination gate + token strip; digital silence = mic error.
  - New `core/TranscriptGate.kt` (`EMPTY_MESSAGE = "Didn't catch that"`,
    `SILENT_MIC_MESSAGE = "Microphone returned silence"`, strip `[...]/(...)/<...>`,
    exact-match hallucination set, `evaluate(raw, speechDetected, peakLevel)`).
  - New `core/AudioEnergy.kt` (peak/rms/digital-silence).
  - `DictationService`: energy + RMS log (`Log.d(TAG, "captured N samples ... peak=... rms=...")`),
    digital silence → `Error` before engine; gate decides insert vs `Error`.
  - Tests: `TranscriptGateTest` (8), `AudioEnergyTest` (3), lifecycle gate tests (2). Green.
- `608eff1` ticket 2 — capture at START, verify at insert, hint text, caret spacing/case.
  - `InsertionPolicy`: `NodeSnapshot` + windowId/viewId/isShowingHintText; `TargetGuard`;
    hint → SetText route.
  - `TextInsertionPlanner.formatForCaret` (trim, single leading space only after
    non-whitespace, capital at field start / after `. ! ?` / newline, lowercase mid-sentence).
  - `DictationAccessibilityService`: `captureTargetAtStart()`, consume-once capture,
    `Rejected("target field disappeared"/"target changed")`, hint-aware `current`.
  - `InsertResult.Rejected` (accessibility + service) = insert nothing, no clipboard fallback;
    `DictationService` publishes `Error("Nothing inserted: <reason>")`; capture seam
    `DictationService.targetCapture` called at START tap.
  - Tests: `CaretFormattingTest` (8), `InsertionPolicyTest` (+16 total), lifecycle
    capture-at-start test. Gate green at this commit (`:core:test` 157, `:app:testDebugUnitTest` 93, 0 failures).
- `2343c4b` ticket 3 — single-shot proven: one `FLUSH` chunk, `overlapMs == 0`, one transcribe
  call per tap-to-tap hold; `MAX_HOLD_MS` capped 600 s → 30 s. `grep -rn 'AudioChunker|TranscriptMerger'
  app/src/main` = none. New lifecycle test holds >30 s and asserts exactly one engine call.
- `7a5e850` `wip: park` — ticket 4 test only (see RUNNING); WIP, unrun.

Evidence: all green at `2343c4b`; build outputs excluded (park commit touches one test file only).

## RUNNING when killed
- Only this session (no worker subagents launched). Ticket 4 (`mic between taps`) partially done:
  added `stopCalls/stopped` to `ManualAudioSource` and lifecycle test
  `the microphone is only open between the start and stop taps` (service creation opens no mic,
  press opens, release closes, no re-open). Committed as `7a5e850` but **not compiled/run**.
  Ticket 5 not started.

## NEXT (ordered, each a small ticket)
1. **Rebase `agent/a1-insert-quality` onto master `1de97d6`** ("park: u2-states reconciled, gate
   235/0"). Branch was based on `0b71e0e`; master now has the reconciled single
   `AccessibilityTextInserter` (u2-states) instead of MFVP-1 duplicates. Resolve
   `DictationService`/`InsertionPolicy`/inserter conflicts; keep ticket 1–3 behavior.
2. Finish ticket 4: run lifecycle test; ensure mic stops on cancel/error too; confirm RMS log
   and all-zero→error still covered; commit.
3. Ticket 5: default language explicit `"en"` (`SettingsStore`, keep `LANGUAGE_AUTO` constant,
   remove auto from UI list), update `SettingsDefaultsTest` (currently asserts `"auto"`), README
   note; commit.
4. Full gate `:core:test :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin`; write
   `handoffs/ASHA-6/HANDOFF.md`; hand branch to MARA for merge + CI APK.

## TRAPS
- Host is aarch64: NDK `linux-x86_64/clang` → `Exec format error`. JVM tests fine; APK must be
  built in CI (`ubuntu-latest`). `ninja` dangling symlinks fixed with `nix profile install nixpkgs#ninja`.
- Always export `JAVA_HOME=/work/android-toolchain/jdk ANDROID_HOME/ANDROID_SDK_ROOT=/work/android-toolchain/sdk
  GRADLE_USER_HOME=/home/node/gh-asha` and pass `--offline`.
- Silence tests: press/release must span ≥ `MIN_HOLD_MS`; use
  `ShadowSystemClock.advanceBy(500, MILLISECONDS)`; real audio needs frames ≥ ~500/32768 peak to
  set `speechDetected`.
- `DictationService.targetCapture` is a test seam: save original in `setUp`, restore in `tearDown`.
- `AccessibilityTextInserter.insert` copies to clipboard when the accessibility service is not
  connected — tests must use `InserterFactory` fakes, not the real inserter.
- Caret formatting expects a leading space when the char before the caret is not whitespace
  (tests fixed accordingly at `608eff1`).
- Keep `/work/martins-whisper` clean; all work in `/work/martins-whisper-worktrees/<id>`.
- Never push from worker containers (no credentials); owner pushes after reviewing this handoff.
