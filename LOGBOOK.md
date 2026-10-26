# LOGBOOK

Chronological project log. PM owns this file; agents add entries via their handoffs.

## 2026-10-26

- 14:50Z - Session starts. Initial (wrong) assumption: Linux desktop overlay. Recon: aarch64 container, no GPU, no audio, no display, docker available, /work empty. Installed `uv`. Cloned Linux prior art.
- 15:02Z - **User correction: target is Android, Kotlin.** Stop Linux track. Start replan.
- 15:04Z - New plan: Android app, Kotlin, foreground service, `TYPE_APPLICATION_OVERLAY` non-focusable bubble, AccessibilityService insertion, on-device Whisper, GPU (Vulkan/NNAPI) with CPU fallback, 300 s idle unload, VAD chunking.
- 15:05Z - Created `SPEC.md` v2, `KANBAN.md`, agent worktree convention. Environment facts: no `/dev/kvm`, no JDK/Android SDK yet, host aarch64, Docker 29.9.2 (aarch64). Emulator/GPU validation likely blocked until device.
- 15:06Z - Fan-out wave 1 (background `opencode run` sessions): R1 research, B1 build harness, C1 core audio, E1 ASR/native, U1 overlay, I1 accessibility input. Each gets a git worktree + handoff contract.
- 15:07Z - Wave 1 PIDs: R1 792, B1 1079, C1 1360, E1 1464, U1 1563, I1 1721. Launcher uses detached `opencode run` sessions; logs in `agents/logs/`.
- 15:08Z - PM added integration plan, device test matrix, risk register. B1 has JDK17 + community aarch64 SDK tools, validating build. C1/I1/U1/E1/R1 progressing.
- 15:13Z - Toolchain exposure fixed without touching read-only `~/.nix-profile`: restored Gradle launcher, added JAVA_HOME wrapper at `~/.local/bin/gradle`, wrote `/work/android-toolchain/TOOLCHAIN-READY.md` and `scripts/env.sh`.
- 15:15Z - Killed C1/I1/U1 stuck nix sessions; relaunched all three as v2 with absolute toolchain paths and code-first instructions. New PIDs: C1 7875, I1 8022, U1 8102.
- 15:17Z - B1 reports `:core:test` BUILD SUCCESSFUL (probe test, 0 failures) using shared JDK 17 + Gradle 8.9 + SDK 35. B1 still finishing `assembleDebug`, docs and handoff.
