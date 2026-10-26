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
- 15:23Z - C1 v2 wasted time on broad `find`; killed and relaunched v3 with a no-reconnaissance, write-first prompt (pid 11231).
- 15:24Z - R1 delivered handoff + 456-line prior-art report. PM merged `agent/r1-research` (`d7ce482`), accepted ADR-004 Option A (whisper.cpp + Vulkan/CPU, GGML-only v1, CPU fallback) and ADR-007 (tap-to-re-arm after reboot). SPEC R4 narrowed to GGML import.
- 15:25Z - E1 writing model layer + JNI; U1 writing overlay/service/UI; I1 writing accessibility tests; B1 still investigating aarch64 NDK after green `:core:test`.
- 15:32Z - I1 and U1 delivered committed handoffs. PM merged both, combined manifest (mic/overlay/FGS + accessibility service) and strings, fixed accessibility settingsActivity to `MainActivity`. C1 delivered 25 tests; merged. Master now passes 74 JVM tests and assembles `app-debug.apk`.
- 15:35Z - ADR-003 strengthened with read-back verification, password-field guard, and no clipboard auto-clear from R1/I1 findings.
- 15:36Z - E1 core ASR unit tests are green (7 downloader tests initially failed, then fixed); still finalizing native/NDK build and handoff. B1 still on aarch64 NDK/emulator feasibility after proving APK build.
- 16:45Z - First-push milestone prepared: master clean, `agents/prompts/C1-core-audio-v3.md` committed (`07bd75c`), PM handoff `handoffs/PM-2026-10-26-1/HANDOFF.md` (`2472867`) records 74 JVM tests green and `app-debug.apk` 8.8 MB. Build used private `GRADLE_USER_HOME=/home/node/gh-pm --offline` because the shared toolchain cache is locked by the sibling PM. `.gitignore` now covers android-toolchain, `.kotlin`, model formats. No secrets found.
- 16:47Z - Ran `setup-matt-pocock-skills` for the local-markdown tracker (`docs/agents/`). Charted the wayfinder map `.scratch/device-ready-apk/map.md` with 10 decision tickets: model facts, ASR backend bar, B1/E1 merge gates, T1 harness, D1 docs/matrix, signing, H1/H2 thresholds, device access, G1 checklist. Frontier: model facts, B1 gate, signing, device access.
- 17:07Z - B1+E1 integration: copied the sibling workers' uncommitted build harness and ASR/model layer into `pm/b1-e1` (their worktrees were inactive; not edited). Made native whisper.cpp opt-in (`-Pmartins.native=true`) so the default APK builds without an NDK. Full gate: 109 JVM tests green, `:app:assembleDebug` OK, APK 8,876,133 bytes; `:app:assembleDebugAndroidTest` builds. Handoff `handoffs/PM-2026-10-26-2/`.
- 17:10Z - Merged `pm/b1-e1` into `master`. Remaining for G1: wire `EngineFactory` to the real engine and `ModelRepository`, add model download/import UI, then T1 fake-audio E2E. H1/H2 still blocked on device; signing/device questions in `owner/ASKS.md`.
- 18:20Z - Provider limited (free tier); G1 set blocked: provider; worker branches wip-committed; waiting for PROVIDER-STATUS.md ok.
