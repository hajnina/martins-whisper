# Kanban - Martins Whisper (Android)

Updated: 2026-10-26T17:10Z
Columns: Backlog | Ready | In Progress | Review | Blocked | Done
WIP limit: 6 background agents.

## Wayfinder map (source of truth for the road to sign-off)

Destination: a signed-off APK that dictates into any app on a real Android phone.
Map: `.scratch/device-ready-apk/map.md` (10 decision tickets; local markdown). KANBAN rows below are the execution view of that map.

## In Progress (background agents)

| ID | Deliverable | Owner / branch | Depends on | Handoff |
|---|---|---|---|---|
| G1 | Integration: B1+E1 merged; wire `EngineFactory` to `WhisperCppEngine` + `ModelRepository`, model download/import UI, then full gate + handoff. | pm/b1-e1 (PM) | B1, E1 | handoffs/PM-2026-10-26-2/HANDOFF.md |

## Ready / queued (next wave)

| ID | Deliverable | Depends on | Notes |
|---|---|---|---|
| T1 | Test + emulator harness: Robolectric, instrumented test plan, fake audio/ASR end-to-end. Layer 2 `assembleDebugAndroidTest` builds; device run pending. | B1, C1, E1, U1, I1 | No KVM; device run is H1. |
| D1 | Docs + operator scripts: build/install, adb model push, permission checklist, device matrix, privacy. | B1, E1 | Keep tidy. |

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
| B1 | Build harness: Gradle wrapper 8.9, x86_64 Dockerfile, CI, env scripts, build docs. | merged `caf0d09`; `./gradlew` gate green; `handoffs/PM-2026-10-26-2/` |
| E1 | ASR/model layer + whisper.cpp JNI scaffolding + 35 tests; native opt-in. | merged `9b2ee46`; 109 tests, 0 failures; APK 8.88 MB |
| P0a | Replanned from Linux desktop to Android/Kotlin. | This document, SPEC.md v2 |
| P0b | Repo skeleton, kanban, spec, logbook, agent worktree convention. | git commit |
| R1 | Android prior-art research + ADR-004 recommendation (whisper.cpp/GGML, insertion, model lifecycle). | `research/ANDROID_PRIOR_ART.md`, `handoffs/R1-research/`, merged `d7ce482` |
| C1 | Core VAD/chunker/merger/normalizer + 25 tests. | merged `agent/c1-core-audio`; `:core:test` green |
| I1 | Accessibility focus tracking + UTF-16-safe insertion + fallback + 31 tests. | merged `agent/i1-input`; APK builds |
| U1 | Foreground service, non-focusable overlay bubble, onboarding/settings, boot receiver. | merged `agent/u1-overlay`; APK 8.4 MB |
| ADR-004 | Accept whisper.cpp + Vulkan/CPU, GGML-only v1, CPU fallback; sherpa emergency only. | `DECISIONS.md` |
| ADR-007 | Reboot uses tap-to-re-arm notification; no mic FGS from boot. | `DECISIONS.md` |
