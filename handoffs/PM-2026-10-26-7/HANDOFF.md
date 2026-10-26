# Integration wave 4 (merge queue): NORA + ASHA G3a/G3b + DORA-3 + TESS-2/T1 — MARA, 2026-10-26

## Executive summary (<= 10 lines)
Owner's ordered queue, merged one at a time from handoffs, full offline gate after each merge.

| # | Source | Handoff evidence | Merge | Gate after |
|---|---|---|---|---|
| 1 | `agent/n1-native` `0bd7e8d` | `NORA-1`: CI fix (sdkmanager `--sdk_root`), local native arm64 APK built+verified (12.9 MB, 141/0), actionlint 0 | `c3c45bb` | 155/0 |
| 2 | `agent/g3-download` `c207082` | `ASHA-3`: loopback-HTTP download tests (happy/resume/bad-hash/404) | `5bc822d` | 159/0 |
| 3 | `agent/g3-backend` `63230d3` | `ASHA-3`: GPU/Auto CPU-retry-once + persisted fallback | `c53b6ef` | 163/0 |
| 4 | `agent/d1-docs` `a2cef93` | Docs-only `docs/RELEASE-CHECKLIST.md` (469 lines); no handoff file exists — reviewed by MARA, no secrets, noted below | `39ca92d` | (docs-only) |
| 5 | `agent/t2-svc` `f1af84e` | `TESS-2`: service lifecycle + overlay state machine tests (+6) | `25ea766` | 169/0 |
| 6 | `agent/t2-edge` `a2a2bcf` | `TESS-2`: 48 core edge tests; **F1 real AudioChunker bug flagged** (needs production ticket before H1) | `ed8d96c` | 217/0 |
| 7 | `agent/t1-harness` `667dba3` | `TESS-2` merge-order section: fail-closed `test-device-ready.sh` + T1 sub-ticket closure | final | 217/0 |

Final master gate (`GRADLE_USER_HOME=/home/node/gh-mara`, `--offline --rerun-tasks`, full suite):
**core + app JVM: 217 tests, 0 failures, 0 errors; `:app:assembleDebug` + `:app:assembleDebugAndroidTest` green.**
```
app-debug.apk             8,970,737 B  sha256 5809b1ecfb5f1b957852dc2e480e3f398547cf106386c31f8d108562530ba107
app-debug-androidTest.apk 1,326,409 B  sha256 c215815222c901860507727585f25b3ce537154dbe19da98311d0805f91a4bfe
```

## Public-repo hygiene (Apache-2.0)
- `git diff origin/master --name-only | grep -ciE 'raw-from-pm|model-bench|\.keystore|\.jks|\.bin$|local\.properties'` = **0**.
- Secrets grep over the full wave diff: only benign hits (test names about text tokens, debug-keystore `android` password in RELEASE-CHECKLIST operator text, README license line).
- Wave diff: 38 files, +4167/−87.

## Not merged, waiting on handoffs
- `agent/m1-release` (W1 CODER, signing/versioning) — no `handoffs/W1-release/` yet.
- `agent/m2-checklist` (W2 CRITIC, issue-10 verify + gap tickets) — no `handoffs/W2-checklist/` yet.
Both workers are running; merge wave 5 when their handoffs land.

## Notes for the owner
1. Push-ready master includes the **CI fix first** (owner's "CI can validate the rest"): expect both workflows to go green; if `native-build.yml` stays red, send `gh run view <id> --log-failed` to NORA.
2. DORA owes a handoff for `a2cef93` (D1-3); content was reviewed and is safe, but process gap to close.
3. TESS's F1 finding (`AudioChunker.splitAtMaxLength` duplicates post-cut audio; reproduced standalone) must become a production ticket before H1 — audible defect.
4. NORA's native APK (arm64, `c90e356f...`) is at `/work/martins-whisper-worktrees/n1-native/app/build/outputs/apk/debug/app-debug.apk` for H1 install testing.
