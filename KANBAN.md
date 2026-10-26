# Kanban - Martins Whisper (Android)

Updated: 2026-10-26T18:56Z (UTC)
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

Provider LIMITED since 2026-10-26T19:01:38Z (`/work/.yoloseek/PROVIDER-STATUS.md`). All running tickets blocked: provider (see Blocked). No new workers.

| ID | Deliverable | Owner / branch | Ticket | Handoff |
|---|---|---|---|---|
| T1-1 | Fake-audio -> fake-engine JVM E2E | TESS / `agent/t1-harness` | `build-tickets/T1-1-fake-e2e.md` | blocked: provider 19:01:38Z |
| T1-2 | Instrumentation plan compiles; device-only run documented | TESS / `agent/t1-harness` | `build-tickets/T1-2-instrumentation-plan.md` | blocked: provider 19:01:38Z |
| DORA-2 | Docs corrected to post-G1 reality (model UI, typed errors, native opt-in) | DORA / `agent/d1-docs` | inline in `agents/prompts/DORA-2-docs-post-g1.md` | blocked: provider 19:01:38Z |

## Review (waiting for MARA integration)

| ID | Branch | Merge gate |
|---|---|---|
| (none) | - | - |

## Merged (this wave)

| ID | Branch | Merge commit | Evidence |
|---|---|---|---|
| G1a-2 + G1b | `agent/g1-engine` | `fcb8299` | ASHA-1: 135 tests, APK 8,938,813 B; MARA gate green offline |
| D1-1 + D1-2 | `agent/d1-docs` | `b33c8c3` | DORA-1: docs/scripts + `bash -n` + no-device exits; MARA re-ran scripts |

## Blocked

| ID | Reason | Unblock |
|---|---|---|
| T1-1 | blocked: provider since 2026-10-26T19:01:38Z | Provider returns ok (`/work/.yoloseek/PROVIDER-STATUS.md`). |
| T1-2 | blocked: provider since 2026-10-26T19:01:38Z | Provider returns ok (`/work/.yoloseek/PROVIDER-STATUS.md`). |
| DORA-2 | blocked: provider since 2026-10-26T19:01:38Z | Provider returns ok (`/work/.yoloseek/PROVIDER-STATUS.md`). |
| H1/H2 | No physical Android device in sandbox. | User supplies device/ADB access (owner/ASKS.md). |
| EMU1 | Android emulator requires KVM/x86_64; sandbox is aarch64 without /dev/kvm. | x86_64 KVM runner or accept JVM/Robolectric only. |
| T1-1 | Provider limited (free tier) since 2026-10-26 19:01:38Z. | PROVIDER-STATUS.md ok + refill. |
| T1-2 | Provider limited (free tier) since 2026-10-26 19:01:38Z. | PROVIDER-STATUS.md ok + refill. |
| DORA-2 | Provider limited (free tier) since 2026-10-26 19:01:38Z. | PROVIDER-STATUS.md ok + refill. |
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
| G1a-2 | Real `EngineFactory` + installed model + typed failures + persisted `EngineState`. | ASHA-1, merged `fcb8299`; 135 tests green |
| G1b | Catalog model screen: installed state, download/SAF import/delete, progress, diagnostics. | ASHA-1, merged `fcb8299`; `:app:assembleDebug` green |
| D1-1 | Operator docs: INSTALL/PERMISSIONS/MODELS/PRIVACY/DEVICE-MATRIX/TROUBLESHOOTING/DOCTOR + README. | DORA-1, merged `b33c8c3`; commands run or marked UNVERIFIED |
| D1-2 | `install-apk.sh`, `push-model.sh` (SHA-256), `doctor.sh` (secret-free). | DORA-1, merged `b33c8c3`; `bash -n` + no-device exits verified |
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
