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

## MFVP-1 MERGED (owner shift 3)
- merged `agent/mfvp1-inserter` (CODER bg worker; handoff `MFVP1-HANDOFF` @50a4b13, code d5c8521: AccessibilityTextInserter adapter + factory default switched, 4 new Robolectric tests, clipboard fallback preserved) -> merge `e5ca2f8`. FULL GATE: 35 suites, **222 tests, 0 failures/errors**; APK 8,973,941 B; worktree clean.
- ASHA `agent/u2-states`: tip 82b1a9e does same insertion wiring + FEEL states/haptics (6 commits ahead) but NO handoff for the tip yet -> NOT merged; reconcile on her handoff (expected conflicts in DictationService.kt factory default + AdapterName: hers lives in `service/`, mine in `accessibility/`; keep ONE, prefer hers if her tests ride along).
- Remaining: MFVP-2 FEEL (or via u2-states), MFVP-4 device (owner/ASKS.md), MFVP-5 README, owner push + CI check.

## u2-states reconciled (owner shift 4)
- merged `agent/u2-states` (ASHA-4/5 handoffs incl. README MFVP-5 replacement, FEEL ticks double-pulse error, FLAG_NOT_FOCUSABLE proof) -> clean auto-merge; then deleted duplicate: my `accessibility/AccessibilityTextInserter.kt` + test removed (ASHA's `service/` version kept - simplest, sync `insertNow`, clipboard fallback on refusal too). Refs: reconcile commit `1d5056f`.
- FULL GATE after reconcile: 37 suites, **235 tests, 0 failures/errors**; APK 8,982,577 B. master clean, push-ready.
- NEXT: merge ASHA a1-insert-quality items as they land (with handoffs only). MFVP-2 feels covered by u2-states FEEL. ~10 min to 2M budget park - no new work after this commit except merge+gate if a1 lands instantly.

## PARK (owner order, quota refill, ~2M budget)
DONE (all on master, clean, push-ready tip `1de97d6` + park `this`):
- MFVP flow audit + tickets MFVP-1/2 (`1b13c47`).
- F1 merge (TESS, gate 218/0) `1d9563a`; MFVP-1 merge (CODER handoff MFVP1 @50a4b13, gate 222/0) `e5ca2f8`.
- u2-states merge + reconcile to ONE inserter (ASHA service/ version) `1d5056f`; FULL GATE 37 suites, **235 tests, 0 failures**; APK 8,982,577 B.
- u2-states brought FEEL (haptics incl. error double pulse, FLAG_NOT_FOCUSABLE test) and short README = MFVP-2 + MFVP-5 covered.
- owner/ASKS.md refreshed for H1 (wireless-debugging steps).
RUNNING: none of mine (CODER mfvp1 completed). Incoming unmerged branches: `agent/a1-insert-quality` (ASHA, tip 2343c4b + park 7a5e850), `agent/v1-verify` (VERA tracers, park 0a38de6), `agent/n3-native` (host tracer/NORA successor?, park 2369ffc). NOT committed to master; each has wip: park commits on its branch.
NEXT (ordered, small tickets):
1. On refill: MFVP-1..5 all done pending device. Merge `agent/a1-insert-quality` from ASHA handoff (quality of insertion text merges - expect single-shot flush transcribe tests; gate `:core:test :app:testDebugUnitTest :app:assembleDebug --offline`, GHA=/work/android-toolchain/jdk, GRADLE_USER_HOME=/home/node/gh-mara).
2. Merge `agent/v1-verify` tracers if she hands off with the focused-node assertions (PARK note relays owner message).
3. n3-native: read its park/handoff; merge only if it contains MFVP path items (16K page-align IS MFVP-relevant: newer devices demand it).
4. MFVP-4 H1 device test (owner/ASKS.md has Martin's steps) - the only open critical item.
5. KANBAN/LOGBOOK touch-up + handoffs/PM-<n> consolidation at next milestone.
TRAPS: same as above plus: `find -newermt -15 minutes` fails here (use `-mmin`); no python3 exists (node works); awk quoting with double quotes throws runaway-string - avoid.
