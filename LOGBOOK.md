# LOGBOOK

Chronological project log. PM owns this file; agents add entries via their handoffs.

## 2026-10-26

- 14:50Z - Session starts. Initial (wrong) assumption: Linux desktop overlay. Recon: aarch64 container, no GPU, no audio, no display, docker available, /work empty. Installed `uv`. Cloned Linux prior art.
- 15:02Z - **User correction: target is Android, Kotlin.** Stop Linux track. Start replan.
- 15:04Z - New plan: Android app, Kotlin, foreground service, `TYPE_APPLICATION_OVERLAY` non-focusable bubble, AccessibilityService insertion, on-device Whisper, GPU (Vulkan/NNAPI) with CPU fallback, 300 s idle unload, VAD chunking.
- 15:05Z - Created `SPEC.md` v2, `KANBAN.md`, agent worktree convention. Environment facts: no `/dev/kvm`, no JDK/Android SDK yet, host aarch64, Docker 29.9.2 (aarch64). Emulator/GPU validation likely blocked until device.
- 15:06Z - Fan-out wave 1 (background `opencode run` sessions): R1 research, B1 build harness, C1 core audio, E1 ASR/native, U1 overlay, I1 accessibility input. Each gets a git worktree + handoff contract.
