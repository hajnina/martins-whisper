# PARK - MARA (updated 2026-10-26 ~late, live note)

## DONE
- Prior (locked master, clean, 217 tests 0 fail, APK 8,970,737 B): see git history `9ead9ff..636b3f9`.
- **MFVP e2e flow audit (this shift, DONE, commit `1b13c47`)**. Verified by reading code:
  - bubble -> onPress (DictationService.kt:126) -> audio start + engine load -> onRelease -> finishSession buffer -> WhisperCppEngine transcribe (EngineFactory real engine, CPU fallback) -> emitTranscript broadcast.
  - MFVP-1 build: CI native-build.yml builds arm64 APK with `-Pmartins.native=true` + jniLibs checks (native-apk-check.sh) -> OK, engine embedded.
  - VAD/chunk: RMS gate (SPEECH_RMS_THRESHOLD) + whole-buffer transcription (AudioCaptureBuffer drained at finishSession). No chunker in prod path; acceptable for MFVP (short sessions, <=10 min cap). TESS F1 chunker bug does NOT affect prod path.
- **Tickets written** (`.scratch/device-ready-apk/tickets/`): MFVP-1-inserter-wired (BLOCKING), MFVP-2-feel-bubble-focus.

## MISSING LINKS found (the audit's point)
1. ** inserter not wired**: DictationService.kt:514 defaults to ClipboardTextInserter -> transcript only lands on clipboard, NOT in focused field. AccessibilityInserter exists but is never used. = MFVP-1 ticket, next.
2. FEEL haptics unverified = MFVP-2 ticket.
3. H1 device session (blocked: Martin's phone).

## NEXT (ordered)
1. MFVP-1: CODER launched in background, branch `agent/mfvp1-inserter`, worktree mfvp1 -> I merge with gate `:core:test :app:testDebugUnitTest :app:assembleDebug --offline`.
2. MFVP-3 model sanity: **DONE, verified on master** - SettingsStore DEFAULT_MODEL_ID="base" (SettingsStore.kt:102), one-tap download w/ progress in ModelBrowser.kt:149; HttpModelDownloaderTest covers happy path, resume mid-body, sha-mismatch rejection. Single default, nothing to build.
3. MFVP-2 FEEL polish (haptics/bubble focus) after MFVP-1 merge.
4. H1: ask in owner/ASKS.md once more, concretely (need Martin's phone on same Wi-Fi + dev options, wireless adb pair).
5. Owner pushes master; verify CI green + native APK artifact.

## TRAPS
- `opencode run -m <model>` errors; use default model (no -m flag).
- Gradle: own copy gh-mara + `--offline --rerun-tasks`; never shared gradle-home. Refill = re-copy toolchain caches once.
- workers: wip-commit every 10 min; park note live; PROVIDER-STATUS OK as of last read.
- InsertResult.duplicate TextInserter interfaces exist (service vs accessibilty package); don't unify now (abstraction = churn), adapter bridges them.

## Owner orders executed (this shift, after audit)
- merged `agent/f1-chunker` (TESS, handoff TESS-F1 @09e6398, real reg tests) -> merge commit `1d9563a`. FULL GATE re-run: `:core:test :app:testDebugUnitTest :app:assembleDebug --offline` => 34 suites, **218 tests, 0 failures/errors**, APK 8,971,253 B. master clean.
- MFVP-1 note: CODER bg worker running on `agent/mfvp1-inserter` (may overlap owner's mention of ASHA u2-states; whoever hands off first with test output gets merged, other branch reconciled).

### TO VERA (agent/v1-verify) - from owner via MARA
When you run L1/L2 tracer bullets, they must catch exactly this class of gap: on 2026-10-26 master, 217 tests were green while `DictationService.inserterFactory` (DictationService.kt:514) defaulted to ClipboardTextInserter - transcript was PRODUCED and broadcast but never appeared in the focused text field. So your tracers must assert the FULL chain "transcript produced -> text appears in the focused node", not just that a transcript exists. Ticket `.scratch/device-ready-apk/tickets/MFVP-1-inserter-wired.md` documents the gap.
