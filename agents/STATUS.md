# Agent Status (MARA view)

Snapshot: 2026-10-26T18:40Z (UTC). Provider: OK since 18:36:31Z.

| ID | State | Evidence | Next MARA action |
|---|---|---|---|
| MARA | integrating | master `1511ca1`, clean; G1a-1 done | keep master green, merge from handoffs |
| ASHA | G1a-2/G1b open | branch `agent/g1-engine` @ `1511ca1` | launch; then verify handoff + full gate |
| TESS | T1-1/T1-2 open | branch `agent/t1-harness` @ `1511ca1` | launch after ASHA (2-worker cap) |
| DORA | D1-1/D1-2 open | branch `agent/d1-docs` @ `1511ca1` | launch after ASHA/TESS |
| ORIN | parked | `research/model-bench` @ `4fbf306` | none (research only) |
| R1 | done, merged | `handoffs/R1-research/` | closed |
| C1 | done, merged | 25 tests | closed |
| I1 | done, merged | 31 tests | closed |
| U1 | done, merged | APK 8.4 MB | closed |
| B1 | done, merged | `caf0d09`; wrapper/Docker/CI/scripts | closed |
| E1 | done, merged | `9b2ee46`; 35 new tests; native opt-in | closed |
| H1/H2 | blocked | map tickets 08/09 | needs physical device + thresholds |

Toolchain: `/work/android-toolchain/TOOLCHAIN-READY.md`. MARA uses `GRADLE_USER_HOME=/home/node/gh-mara --offline`; workers use their own copies.
Map: `.scratch/device-ready-apk/map.md`. Latest integration handoff: `handoffs/PM-2026-10-26-3/`.
