# Agent Status (MARA view)

Snapshot: 2026-10-26T19:01:38Z (UTC). Provider: LIMITED since 19:01:38Z. Master: G1 + D1 merged.

| ID | State | Evidence | Next MARA action |
|---|---|---|---|
| MARA | integrating | master `1511ca1`, clean; G1a-1 done | keep master green, merge from handoffs |
| ASHA | done, merged | `agent/g1-engine` -> `fcb8299`; ASHA-1; 135 tests, APK 8,938,813 B | closed |
| TESS | blocked: provider (2026-10-26 19:01:38Z) | branch `agent/t1-harness` @ `fcb8299` | wait for PROVIDER-STATUS.md ok |
| DORA | blocked: provider (2026-10-26 19:01:38Z) | `agent/d1-docs` -> `b33c8c3`; DORA-1 | wait for PROVIDER-STATUS.md ok |
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
