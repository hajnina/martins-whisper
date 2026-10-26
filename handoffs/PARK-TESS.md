# PARK — TESS (2026-10-26, before refill/killall)

Master at park: `83a65df` (wave 4, gate 217/0). All worktrees clean; nothing uncommitted.

## DONE (SHAs on master)

- **T1 harness** (merged): harness `bc182f9` + harden `cf92851` (merge `425412c`); fail-closed
  gate `7211eab` + T1 closes/`handoffs/TESS-2/HANDOFF.md` `667dba3` (merged in wave 4, tip
  `agent/t1-harness`). Layer 1 = 141 tests at the time; Layer 2 device tests compile via
  `assembleDebugAndroidTest`; `scripts/test-device-ready.sh` fails closed; `scripts/test-device.sh`
  exits 3 with no device. Re-verified on master `ba6b160`.
- **T2 tests** (merged): W1 edge/property `b7f0ae7` (merge `ed8d96c`), W2 Robolectric service +
  overlay `bcc409f` (merge `25ea766`). Verified independently and integrated: master gate green
  (core 16/137 + app 17/72 at the time; 209 tests, later 217 with wave 4).
- Handoffs: `handoffs/TESS-1/`, `handoffs/TESS-2/` (`HANDOFF.md`, `T2-HANDOFF.md`, `W1-EDGE.md`,
  `W2-SVC.md`).

## RUNNING at park (note only, do NOT resume — MFVP-stopped scope)

- **W1** — branch `agent/t3-lint` (tip `1e7e3fd` `wip: park`), ticket `T3-lint`, model
  `deepseek-v4.1-flash-free`. State: ticket committed `f766ba5`; lint/ktlint/detekt NOT wired,
  no baselines. Unmerged.
- **W2** — branch `agent/t3-repro` (tip `4a2b820`), ticket `T3-repro`, model
  `deepseek-v4.1-flash-free`. State: ticket only; `scripts/test-clean-checkout.sh` NOT written.
  Unmerged.
- Both were in-process Task subagents: they cannot be cancelled from this session; they were told
  never to merge, so the park commits on their branches are the only residue.
- Critical-path work was NOT started (owner MFVP steer arrived before any code action).

## NEXT (ordered, MFVP critical path only)

1. **MFVP-1 — real engine in the default APK:** verify NORA-1's native arm64 APK
   (`/work/martins-whisper-worktrees/n1-native/app/build/outputs/apk/debug/app-debug.apk`,
   12,902,712 B) contains the whisper JNI/`.so` and that `EngineFactory.createDefault` picks
   `WhisperCppEngine`; owner pushes `agent/n1-native`/master to run the Actions workflow.
   Small: one APK byte audit + one JVM assertion.
2. **MFVP-2 — end-to-end wiring audit:** bubble press → `AudioCaptureBuffer`/VAD/chunk →
   `DictationEngineHost.transcribe` → `TextInserter` into the focused field. Read
   `DictationService`/`OverlayController`/`MainActivity`; ASHA fixes gaps as small tickets; TESS
   keeps only the existing minimal tests (`DictationServiceRobolectricTest`,
   `DictationServiceLifecycleRobolectricTest`, `FakeAudioPipelineE2ETest`).
3. **MFVP-3 — one default model:** confirm one-tap download (base/tiny), resume + SHA check via the
   existing G3a loopback tests; delete extra model UI roads that block the flow.
4. **MFVP-4 — H1 phone smoke (owner provides phone):** install debug-signed arm64 APK, grant the 3
   permissions, tap/speak/tap into a real app; fix what breaks. This is the definition of done.
   TESS writes the one-page run sheet only if asked; no new harness scope.
5. **MFVP-5 — README:** one short install/permissions/use file.

Explicitly NOT to resume: `agent/t3-lint`, `agent/t3-repro`, M1/M2 release+checklist,
`agent/n2-release`, u2-tile/extra overlay states, benches.

## TRAPS (verified)

- **F1 production bug:** `AudioChunker.splitAtMaxLength` duplicates post-cut audio
  (`tailFrom` over the whole chunk + `afterCut`). Independently reproduced:
  `expected:<11680> but was:<19360>`. User-audible duplicate speech in the next chunk; needs a
  small production ticket before/at H1. Test evidence in `handoffs/TESS-2/W1-EDGE.md`.
- **Handoff-dir collisions:** T1 and T2 both added `handoffs/TESS-2/SUMMARY.txt`; MARA hit
  merge conflicts (`UU/AA`). New work gets a fresh `handoffs/TESS-<n>/` per branch or unique file
  names.
- **Task subagents cannot be cancelled** and may keep writing after a steer/park. Prefer
  dedicated `opencode run`/yoloseek sessions for cancellable workers; always instruct "never
  merge", commit `wip: park` sweeps.
- **Gradle:** never use `/work/android-toolchain/gradle-home`; private `gh-*` copies die with the
  container on refill (re-copy once). One online fetch is needed for `androidx.test.ext:junit`
  and espresso before `--offline` works. Never `pkill` gradle/java on this shared host.
- **Robolectric:** use `@ConscryptMode(OFF)`; overlay `isShowing` is false in Robolectric (assert
  `ShadowWindowManagerImpl` views); APK sha256 is not stable across rebuilds, sizes/counts are.
- **Native build:** `-Pmartins.native=true` uses the community NDK override
  (`-Pmartins.ndkVersion=...`); keep `cmake` 3.22.1 from the SDK. The native APK is arm64-only and
  contains no model (12.9 MB).
- **MFVP product gap:** real inference and the tap→speak→insert loop have NEVER run on a device;
  all current proof is JVM/Robolectric + a locally built APK. H1 is the only way to close it.
