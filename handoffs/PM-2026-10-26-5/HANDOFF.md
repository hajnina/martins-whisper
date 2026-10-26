# Integration wave 2: T1 harness + D1 docs refresh (MARA, 2026-10-26)

## Executive summary (<= 10 lines)
- MARA took over from the parked PM (see `handoffs/PARK-oc-eph-226089.md`). Handoff-driven integration only.
- Merged `agent/t1-harness` (TESS-1) at `b7cfeaa` into master: fake-audio E2E, DictationService/OverlayController Robolectric tests, Layer-2 device tests (compiled, not run — H1-blocked), `scripts/test-device-ready.sh`, `scripts/test-device.sh`.
- Merged `agent/d1-docs` (DORA-2) at `a2e59ac` into master: docs aligned to the post-G1 engine/model UI, `doctor.sh` reads persisted engine state, duplicate `handoffs/D1-docs/` removed, DORA-2 handoff added.
- `agent/g2-engine` (ASHA) was NOT merged: wip checkpoint `dd04afd` has no handoff; next wave.
- MARA merge-tree checks before each merge: 0 conflict markers both times. `git merge --no-ff` on master, clean.
- Full gate on merged master (MARA's own env below): BUILD SUCCESSFUL, `--offline --rerun-tasks`; core 85 + app 56 = **141 tests, 0 failures**; both APKs rebuilt.
- APK sha256 diffs from ASHA's/DORA's branch builds are expected (fresh debug-signing build, different build root).

## Environment (MARA's private toolchain state)
```
export JAVA_HOME=/work/android-toolchain/jdk
export ANDROID_HOME=/work/android-toolchain/sdk ANDROID_SDK_ROOT=/work/android-toolchain/sdk
export GRADLE_USER_HOME=/home/node/gh-mara   # cp -r of the shared gradle-home; shared dir never touched
cd /work/martins-whisper
# one-time: first androidTest compile fetched androidx.test.ext:junit 1.2.1 + espresso 3.6.1 online
# (TESS's /home/node/gh-tess was destroyed with her container; her caches could not be reused)
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest \
  --offline --rerun-tasks --no-daemon --console=plain
```
Result: `BUILD SUCCESSFUL in 1m 35s, 73 actionable tasks: 73 executed`

## Evidence (parsed from build/test-results XML)
| Suite | Tests | Failures | Errors | Suites |
|---|---|---|---|---|
| `:core:test` | 85 | 0 | 0 | 11 |
| `:app:testDebugUnitTest` | 56 | 0 | 0 | 14 |
| total | **141** | **0** | **0** | 25 |

(Tally commands run on master after the gate; accounts for every `testsuite` XML element.)

Artifacts:
```
app-debug.apk              8,939,909 B  sha256 73c0fa1cd3340126e6e50172cc7299dee3c072c9e7af64f0c0d1f04e56606873
app-debug-androidTest.apk  1,326,409 B  sha256 2ca9451851b2d861ca4852c1103029edaf8bc73748666e80cf23db0babda62ad
```

## Commits (master)
- `4e0d782` wip: ratewatch checkpoint at provider limit (pre-existing)
- `425412c` merge: T1 test harness (TESS-1) — 15 files, +1216/−1
- `421f223` merge: D1 docs refresh post-G1 + doctor engine fields (DORA-2) — 16 files, +860/−352
- Working tree clean after commit; nothing left uncommitted.

Not merged and why:
- `agent/g2-engine` (`dd04afd` wip): no handoff on the branch; ASHA should hand off before T2-wave integration.
- `research/model-bench` stays ORIN's (brief forbids touching `research/`).

## Next for the next integration wave
1. Wait for handoffs on `agent/g2-engine` (ASHA) / any new TESS or DORA tickets.
2. Provider LIMITED since 19:01:38Z — no worker launches; only merge when handoffs appear.
3. H1/H2 still blocked on a physical device + signing (`owner/ASKS.md`).

## References
- `handoffs/TESS-1/HANDOFF.md` (branch tip `b7cfeaa`, tip only adds ticket/handoff files)
- `handoffs/DORA-2/HANDOFF.md` (branch tip `a2e59ac`, tip only adds `handoffs/D1-2/`)
- Owner directives: `owner/BRIEF-MARA.md`, `owner/TEAM-PLAN.md`, `owner/STEERING-NOTES.md`
