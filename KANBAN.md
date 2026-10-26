# Kanban - Martins Whisper (Android)

Updated: 2026-10-26T18:40Z (UTC)
Columns: Backlog | Ready | In Progress | Review | Blocked | Done
WIP limit: 2 worker sessions (free-tier quota; MARA stays <=2 workers + integrator).

## Wayfinder map (source of truth for the road to sign-off)

Destination: a signed-off APK that dictates into any app on a real Android phone.
Map: `.scratch/device-ready-apk/map.md` (10 decision tickets; local markdown). Build tickets:
`.scratch/device-ready-apk/build-tickets/`. KANBAN rows are the execution view of the map.

## Team split (owner UPDATE, generation 2)

- MARA (this session): integrator/PM; owns `master`, KANBAN/LOGBOOK/STATUS, full gate, merges, APK.
- ASHA: G1a/G1b (engine wiring + model UI), branch `agent/g1-engine`.
- TESS: T1 (JVM fake-audio E2E + instrumentation plan), branch `agent/t1-harness`.
- DORA: D1 (docs + operator scripts), branch `agent/d1-docs`.
- ORIN: research/bench only.

## In Progress / queued

| ID | Deliverable | Owner / branch | Ticket | Handoff |
|---|---|---|---|---|
| G1a-2 | Real `EngineFactory`: installed model, typed failure, persisted engine state | ASHA / `agent/g1-engine` | `build-tickets/G1a-2-engine-factory.md` | pending |
| G1b | Model download/import/delete UI + diagnostics | ASHA / `agent/g1-engine` | `build-tickets/G1b-model-ui.md` | pending |
| T1-1 | Fake-audio -> fake-engine JVM E2E | TESS / `agent/t1-harness` | `build-tickets/T1-1-fake-e2e.md` | pending |
| T1-2 | Instrumentation plan compiles; device-only run documented | TESS / `agent/t1-harness` | `build-tickets/T1-2-instrumentation-plan.md` | pending |
| D1-1 | Operator docs + device matrix | DORA / `agent/d1-docs` | `build-tickets/D1-1-docs.md` | pending |
| D1-2 | Install/model-push/doctor scripts | DORA / `agent/d1-docs` | `build-tickets/D1-2-scripts.md` | pending |

Provider: OK since 18:36:31Z (`/work/.yoloseek/PROVIDER-STATUS.md`). Launch at most 2 workers at a time.

## Review (waiting for MARA integration)

| ID | Branch | Merge gate |
|---|---|---|
| (none yet) | - | - |

## Blocked

| ID | Reason | Unblock |
|---|---|---|
| H1/H2 | No physical Android device in sandbox. | User supplies device/ADB access (owner/ASKS.md). |
| EMU1 | Android emulator requires KVM/x86_64; sandbox is aarch64 without /dev/kvm. | x86_64 KVM runner or accept JVM/Robolectric only. |
| GPU1 | No Android GPU available in sandbox. | Real device or GPU-capable emulator. |

## Backlog

- Quick-action tile / shortcut to toggle overlay.
- Custom vocabulary/hotwords.
- History (off by default) + copy last transcript.
- Model import via SAF and download manager with resume.
- Multi-language UI.
- On-device LLM punctuation/cleanup (not v1).
- Play Store packaging/policy.

## Done

| ID | Deliverable | Evidence |
|---|---|---|
| G1a-1 | App uses `core.asr.ModelCatalog`; `ui/ModelOptions` stub removed. | `1511ca1`; `:app:testDebugUnitTest` green offline |
| B1 | Build harness: Gradle wrapper 8.9, x86_64 Dockerfile, CI, env scripts, build docs. | merged `caf0d09`; `./gradlew` gate green; `handoffs/PM-2026-10-26-2/` |
| E1 | ASR/model layer + whisper.cpp JNI scaffolding + 35 tests; native opt-in. | merged `9b2ee46`; 109 tests, 0 failures; APK 8.88 MB |
| P0a | Replanned from Linux desktop to Android/Kotlin. | SPEC.md v2 |
| P0b | Repo skeleton, kanban, spec, logbook, agent worktree convention. | git commit |
| R1 | Android prior-art research + ADR-004 recommendation. | `research/ANDROID_PRIOR_ART.md`, merged `d7ce482` |
| C1 | Core VAD/chunker/merger/normalizer + 25 tests. | merged `agent/c1-core-audio`; `:core:test` green |
| I1 | Accessibility focus tracking + UTF-16-safe insertion + fallback + 31 tests. | merged `agent/i1-input` |
| U1 | Foreground service, non-focusable overlay bubble, onboarding/settings, boot receiver. | merged `agent/u1-overlay` |
| ADR-004 | whisper.cpp + Vulkan/CPU, GGML-only v1, CPU fallback; sherpa emergency only. | `DECISIONS.md` |
| ADR-007 | Reboot uses tap-to-re-arm notification; no mic FGS from boot. | `DECISIONS.md` |
