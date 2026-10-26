# Kanban - Martins Whisper (Android)

Updated: 2026-10-26T15:17Z
Columns: Backlog | Ready | In Progress | Review | Blocked | Done
WIP limit: 6 background agents.

## In Progress (background agents)

| ID | Deliverable | Owner / branch | Depends on | Handoff |
|---|---|---|---|---|
| B1 | Android build harness: JDK 17, Gradle, SDK 35, aarch64 host workarounds, native NDK feasibility, emulator feasibility, reproducible Docker/CI. Produce build report + scripts. **Toolchain READY; `:core:test` passes; finishing app build + handoff.** | agent/b1-build | skeleton | handoffs/B1-build/HANDOFF.md |
| C1 | Pure Kotlin core audio: EnergyVad, AudioChunker, TranscriptMerger, TextInsertionPlanner + JUnit tests. **v2 relaunched with ready toolchain (pid 7875).** | agent/c1-core-audio | skeleton/contract | handoffs/C1-core-audio/HANDOFF.md |
| E1 | ASR/model layer: SpeechEngine contract, whisper.cpp JNI + CMake + Vulkan, ModelCatalog/Repository, ModelManager idle-unload + tests. Coordinate with R1 decision. | agent/e1-asr | R1 (partial), B1 (partial) | handoffs/E1-asr/HANDOFF.md |
| U1 | Android overlay + foreground service + state UI + settings/onboarding shell. Uses fake engine initially. **v2 relaunched with shared SDK (pid 8102).** | agent/u1-overlay | skeleton | handoffs/U1-overlay/HANDOFF.md |
| I1 | Accessibility insertion service: focus tracking, ACTION_SET_TEXT + paste fallback, clipboard recovery, unit/Robolectric tests. **v2 relaunched with ready toolchain (pid 8022).** | agent/i1-input | skeleton/contract | handoffs/I1-input/HANDOFF.md |

## Ready / queued (next wave)

| ID | Deliverable | Depends on | Notes |
|---|---|---|---|
| T1 | Test + emulator harness: Robolectric, instrumented test plan, Docker emulator attempt, fake audio/ASR end-to-end. | B1, C1, E1, U1, I1 | No KVM currently; try software/robolectric. |
| D1 | Docs + operator scripts: build/install, adb model push, permission checklist, device matrix, privacy. | B1, E1 | Keep tidy. |
| G1 | Integration: merge branches, resolve contracts, build APK, run all tests, fix breakage, produce final handoff + demo video/screenshots if emulator works. | all above | PM invokes after wave 1. |
| H1 | Device bring-up: install on real Android device, validate Vulkan/GPU, accessibility insert in apps, idle unload, OEM quirks. | G1 + physical device | BLOCKED until user provides device. |
| H2 | Hardware acceptance: measure latency, memory, GPU vs CPU, 5-min unload actually frees memory. | H1 | BLOCKED until device. |

## Backlog

- Quick-action tile / shortcut to toggle overlay.
- Custom vocabulary/hotwords.
- History (off by default) + copy last transcript.
- Model import via SAF and download manager with resume.
- Multi-language UI.
- On-device LLM punctuation/cleanup (not v1).
- Play Store packaging/policy.

## Blocked

| ID | Reason | Unblock |
|---|---|---|
| H1/H2 | No physical Android device in sandbox. | User supplies device/ADB access later. |
| EMU1 | Android emulator requires KVM/x86_64 typically; sandbox is aarch64 with no /dev/kvm. | Provide x86_64 KVM runner or accept JVM/Robolectric only. |
| GPU1 | No Android GPU available in sandbox. | Real device or GPU-capable emulator. |

## Done

| ID | Deliverable | Evidence |
|---|---|---|
| P0a | Replanned from Linux desktop to Android/Kotlin. | This document, SPEC.md v2 |
| P0b | Repo skeleton, kanban, spec, logbook, agent worktree convention. | git commit |
| R1 | Android prior-art research + ADR-004 recommendation (whisper.cpp/GGML, insertion, model lifecycle). | `research/ANDROID_PRIOR_ART.md`, `handoffs/R1-research/`, merged `d7ce482` |
| ADR-004 | Accept whisper.cpp + Vulkan/CPU, GGML-only v1, CPU fallback; sherpa emergency only. | `DECISIONS.md` |
| ADR-007 | Reboot uses tap-to-re-arm notification; no mic FGS from boot. | `DECISIONS.md` |
