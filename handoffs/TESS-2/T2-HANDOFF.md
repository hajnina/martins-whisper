# TESS T2 handoff — parallel edge + service test wave

Status: **DONE** (2026-10-26)
Owner: TESS. Two background workers, max 2, disjoint branches/worktrees, no production edits.

| Worker | Branch | Worktree | Base | Ticket | Handoff |
|---|---|---|---|---|---|
| W1 | `agent/t2-edge` | `/work/martins-whisper-worktrees/t2-edge` | `master` `88d189a` | `T2-edge` (done) | `handoffs/TESS-2/W1-EDGE.md` |
| W2 | `agent/t2-svc` | `/work/martins-whisper-worktrees/t2-svc` | `agent/g2-engine` `5494b79` | `T2-svc` (done) | `handoffs/TESS-2/W2-SVC.md` |

Both branches are test-only. `git diff --name-only <base>..HEAD` shows only `core/src/test/**` /
`app/src/test/**`, the ticket files and handoffs — no `app/src/main`, `core/src/main` or `scripts/`
changes. Neither branch pushes or merges; MARA integrates.

## Independent verification (TESS, after the workers)

Re-ran both acceptance commands with `--rerun-tasks`, `--offline` and the private Gradle homes:

```sh
# W1
cd /work/martins-whisper-worktrees/t2-edge
GRADLE_USER_HOME=/home/node/gh-t2-edge ./gradlew --offline --rerun-tasks \
  :core:test :app:testDebugUnitTest
# BUILD SUCCESSFUL
# core: 15 suites / 133 tests / 0 failures / 0 errors   (base 11 / 85, +48 tests)
# app : 14 suites /  56 tests / 0 failures / 0 errors   (unchanged)

# W2
cd /work/martins-whisper-worktrees/t2-svc
GRADLE_USER_HOME=/home/node/gh-t2-svc ./gradlew --offline --rerun-tasks \
  :app:testDebugUnitTest :app:assembleDebug
# BUILD SUCCESSFUL
# app: 17 suites / 72 tests / 0 failures / 0 errors    (base 15 / 66, +6 tests)
```

Logs: `/work/tess-t1/logs/tess2-w1-verify-final.log`, `.../tess2-w2-verify.log`.
No skips, `@Ignore` or `assumeTrue` in the new tests. New tests use JUnit4 only, no new
dependencies; the Gradle caches stayed offline (`gh-t2-edge`, `gh-t2-svc` are private copies).

## W1 — `agent/t2-edge` (tests commit `b7f0ae7`, then docs)

New suites (48 tests): `TextInsertionPlannerEdgeTest` (13), `TextNormalizerEdgeTest` (10),
`TranscriptMergerEdgeTest` (13), `AudioChunkerEdgeTest` (12). Covers surrogate pairs/ZWJ/CJK-B,
RTL + combining marks, empty/invalid/huge selections, Unicode vs ASCII whitespace, empty and
non-monotonic merges, chunk boundary timestamps, empty frames, 600 s hold, 3 h streams, seeded
random property checks.

### Findings (production deliberately untouched; fixes need their own ticket)

1. **F1 — real bug, high: `AudioChunker.splitAtMaxLength` duplicates post-cut audio.**
   `tailFrom(overlapStart)` scans the whole `chunk` and therefore already contains the frames after
   `cutIndex`; those frames are appended again from `afterCut`. TESS independently reproduced it
   with a throwaway JUnit test (deleted afterwards):
   `expected:<11680> but was:<19360>` for the 1225 loud + 1 quiet + 24 loud frame case.
   Next chunk `24_270..25_000 ms` (730 ms of timeline) carries 19,360 samples (1,210 ms).
   Suggested fix: build the overlap tail from `emittedFrames`, not from `chunk`, then append
   `afterCut`; add `next.samples.size == 11_680` as the regression.
2. **F2 — minor: session-cap overshoot.** The cap is checked on the frame start, so
   `FLUSH 599_990..600_010` is emitted; C1's invariant says no chunk ends past first + 600,000 ms.
3. **F3 — real bug, medium: `TranscriptMerger` collision for non-content tokens.** Tokens that
   normalize to `""` (emoji-only, punctuation-only) compare equal:
   `merge(["hi 😀", "😁 there"])` drops 😁; `merge(["hello ...", "!!! world"])` drops `!!!`.
   Confirmed by direct execution.
4. **F4 — limitation: overlaps longer than 8 words dedupe nothing** (10-word boundary is fully
   duplicated; exact 8-word case works). `overlapsMs` is indexed by raw part position, blanks
   included, so callers must pass one value per raw part.
5. **F5 — limitation: `TextNormalizer` is ASCII-only for internal whitespace/punctuation.**
   NBSP/U+3000 inside text and Arabic comma/question keep the space; leading/trailing trimming is
   Unicode-aware.
6. **F6 — documented limitation: `TextInsertionPlanner` protects UTF-16 surrogate pairs only**,
   not grapheme clusters (combining marks / ZWJ can be split; no lone surrogates).

## W2 — `agent/t2-svc` (tests commit `bcc409f`, then docs)

New suites (6 tests): `DictationServiceLifecycleRobolectricTest` (4) and
`OverlayStateMachineRobolectricTest` (2). Covers:
- frames captured while the engine loader is blocked are buffered in order and inserted once
  (manual `AudioSource` + latch-blocked `EngineFactory`), transcript contains the first syllables;
- 300 s idle unload closes the engine, next press creates a new instance and inserts;
- cancel during a blocked load inserts nothing; the load still completes and the engine stays
  loaded/reusable (asserted as the current behaviour);
- changing `settings.modelId` closes the previous engine;
- overlay Idle → Loading → Recording → Processing → Success → Error → Idle with
  `MicBubbleView.state`/`contentDescription`, and a no-throw/re-registration test.

Findings recorded, no code changed: `MicBubbleView.contentDescription` is null until the first
`update` (Kotlin initializer bypasses the setter; the service already calls
`overlay.update(state.value)` after `show`); `OverlayController.isShowing` is
`view?.isAttachedToWindow == true`, so Robolectric tests must not assume it is true.

## Merge order (MARA)

1. `agent/g2-engine` first (`8497d83` is docs-only after the tested code `5494b79`).
2. Then `agent/t2-svc` — it is based on G2 and adds tests only, expected conflict-free.
3. Then `agent/t2-edge` — based on `master` `88d189a`, adds `core/src/test/**` only.
4. Separately, `agent/t1-harness` still has two unmerged commits from the T1 re-verify:
   `7211eab` (fail-closed `scripts/test-device-ready.sh`) and `667dba3` (T1 ticket closes +
   `handoffs/TESS-2/HANDOFF.md`). They are not in master; merge them too, otherwise the gate
   remains non-fail-closed. `handoffs/TESS-2/T2-HANDOFF.md` (this file) does not collide with
   `handoffs/TESS-2/HANDOFF.md` from T1.

After each merge run the branch's acceptance command; after all of them run
`GRADLE_USER_HOME=/home/node/gh-tess scripts/test-device-ready.sh` (needs the T1 commit) and
expect core 15/133 + app 17/72.

## Risks / notes

- `agent/t2-svc` inherits G2's unmerged commits. If ASHA changes `DictationService` semantics
  (especially cancel-while-loading), the assertion "engine stays loaded after cancel" may need an
  update; it is documenting current behaviour, not a requirement.
- F1 is a user-audible defect (duplicated speech in the next chunk) and should get a production
  ticket before H1; T2 deliberately does not fix it.
- APK sha256 is not reproducible across rebuilds; test counts are.
