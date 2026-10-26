# Agent Status (PM view)

Snapshot: 2026-10-26T15:17Z

| ID | Alive | Branch commits | Last observed | Next PM action |
|---|---|---|---|---|
| R1 | yes | 1 | cloned prior art (sherpa-onnx, whisper.cpp, futo voice-input), reading FUTO VAD/chunking | wait for report |
| B1 | yes | 1 | shared toolchain READY; `:core:test` BUILD SUCCESSFUL (probe test); finishing app build + docs | wait for handoff, then validate |
| C1 | yes (v2 pid 7875) | 1 | v2 relaunched with ready toolchain; reading spec/contracts | check for files in 10m |
| E1 | yes | 1 | writing `core/.../asr/` model layer | wait for code/tests |
| U1 | yes (v2 pid 8102) | 1 | v2 relaunched; shared SDK/env verified | check for files in 10m |
| I1 | yes (v2 pid 8022) | 1 | v2 relaunched; Gradle/JDK verified | check for files in 10m |

Toolchain: `/work/android-toolchain/TOOLCHAIN-READY.md`; wrapper `/home/node/.local/bin/gradle`; do not write `~/.nix-profile`.
Logs: `agents/logs/<id>.log` (v1), `<id>-v2.log` (relaunched). Handoffs: `handoffs/<ID>/HANDOFF.md` in each worktree.
