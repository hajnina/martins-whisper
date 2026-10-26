# PARK-VERA (done 2026-10-26, quota refill kill)

## DONE (SHAs on agent/v1-verify, worktree /work/martins-whisper-worktrees/v1-verify)
- V1 COMPLETE (40fb8e5): scripts/verify/ + scan-weak-tests.{sh,py} (tautologies incl. literal "x","x", assertTrue(true), zero-assert @Test, @Ignore/@Disabled/assume*, swallowed catch(_:X)), scan-mocks.{sh,py} (mock libs + mock-of-class-under-test), scan-oracle-copy.{sh,py} (expected side calls the class under test). All fail-closed, 演员表 allowlist scripts/verify/scan-allowlist.txt. Real findings vs current 217 tests: 11 hits, one class (throw AssertionError + catch(_:X) assertion-free) in ModelDownloaderTest, ModelCatalogTest, ModelRepositoryTest, WhisperCppEngineTest -> all rewritten with assertFailsWith/assertThrows. Suite re-proven green: :core:test 137 + :app:testDebugUnitTest 80 = 217, 0 fail. Negative control: injected tautology+noassert -> wall FAILs (first control exposed a literal-quote gap, fixed).
- Headless-repo-proof: gradle invocations need source /work/android-toolchain/scripts/env.sh + GRADLE_USER_HOME=/work/android-toolchain/gradle-home + -Pandroid.aapt2FromMavenOverride=/work/android-toolchain/sdk/build-tools/35.0.1/aapt2 (x86 maven aapt2 unusable on this aarch64 host).

## RUNNING (killed at park)
- V2 mutation runner: scripts/verify/mutate.{sh,py} written (35-mutant table over TextNormalizer/EnergyVad/AudioMath/AudioChunker/Contracts merger+planner; mutants = flip comparison, off-by-one, drop call, swap div/mul, kill dedupe/hangover/floor). First bg run CRASHED instantly: KeyError JAVA_HOME (mutate.py does env["JAVA_HOME"] - the nohup env lacked it; fix = os.environ.get default or require sourced env). Score NOT yet measured, zero kills proven, bytes not committed as green. NEXT RUN = fresh.

## NEXT (ordered, each small with proof)
- V2a fix env bug in mutate.py, run mutate.sh to completion (~25 min, background, poll), record mutation-report.txt; every survivor -> test ticket + write the killing test (must show red when the mutant is applied, include mutant id in commit message).
- V3a finish guard-test-diff.sh: currently half-written (placeholder python call, bogus lines to remove); wire to guard-red-fail-closed.py meaningfully. guard-red-fail-closed.py exists but has patchy semantics (per-file test/assert ratchet vs BASE, ratchet.json floors, gate-file hashes; strict mode must fail on missing /work/owner/verify.sha256 which does NOT exist yet - owner must provide it; until then run with STRICT=0 and file a ticket).
- V3b red-first.sh exists but unverified end-to-end; test it by making a hit and confirming the wall proves the test red vs BASE sources.
- V3c ratchet.json bootstrap (script writes it; owner re-signs; lowering a floor must require owner signature).
- V4 tracer bullets (OWNER MUSTARD, keep order): (a) transcript string -> text in the focused node (inserter was unwired while 217 green!), (b) silence inserts nothing + hallucination strings stripped on the pipeline path, (c) idle-unload racing a running transcription on the REAL JNI path, (d) host JNI jfk.wav literal-word assertion on the engine NORA builds. L1 JVM integration first with synthesized wav fixture + reproducible generator script, then L2 Robolectric.
- V5 verify-gate.sh: run all walls + run-twice shuffled; PASS/FAIL table, exit non-zero on any fail; short docs/VERIFY.md.

## TRAPS
- gradle --offline in a fresh worktree fails (plugins not cached there); drop --offline.
- aapt2 override property is mandatory for :app tasks here.
- tests currently use JUnit4: core has kotlin-test throws assertFailsWith; app has only kotlin-test? NO: app uses org.junit.Assert.assertThrows (kotlin-test not in app deps).
- never commit while mutate.py is mid-mutant (source temporarily mutated); kill kill only between mutants, check .mutbak absent and git diff empty.
