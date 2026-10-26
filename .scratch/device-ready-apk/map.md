# Wayfinder map: device-ready APK

Label: `wayfinder:map`

## Destination

A signed-off APK that dictates into any app on a real Android phone: H1 device bring-up passes and H2 latency/memory/unload acceptance is measured, local-only, sideloaded.

## Notes

- Domain: Android/Kotlin, arm64-v8a, minSdk 29, sideloaded. Read `SPEC.md`, `DECISIONS.md`, `KANBAN.md` before any ticket.
- Skills every session should consult: `grilling` + `domain-modeling` (default), `tdd`/`implement`/`diagnosing-bugs` when a task needs code, `handoff` for workers, `code-review` before merge.
- Standing preferences: local-only inference, no telemetry, no secrets. The owner holds GitHub credentials and pushes; PM never looks for them.
- Environment: `/work/android-toolchain/gradle-home` is shared with a sibling PM container. Never delete its lock or kill its daemons; use a private copy (`/home/node/gh-pm`) and `--offline`.
- B1/E1 worktrees are owned by the sibling PM right now. Do not edit `/work/martins-whisper-worktrees/{b1-build,e1-asr}` from this effort.
- Tracker: local markdown, per `docs/agents/issue-tracker.md`. Tags `worktrees/` and build outputs are ignored.

## Decisions so far

- [04 — What must the E1 ASR/model layer prove before it merges?](issues/04-e1-merge-gate.md): committed clean branch + handoff; frozen SpeechEngine/ModelManager/catalog contracts; catalog follows ticket 01 with pinned HF revision + SHA-256; green JVM tests for catalog, hashes, retry, 300 s unload, swap, fake-engine E2E; native libs if they build, otherwise typed "engine unavailable" with no startup crash; Vulkan is not a merge blocker.
- [02 — What ASR backend proof is required for v1 sign-off?](issues/02-asr-backend-bar.md): host proof = green ASR JVM tests (catalog, SHA-256, retry, 300 s idle unload, swap, fake-engine E2E) + APK builds with a graceful engine seam; device proof = doctor/logcat show backend and persisted fallback, one CPU fallback, 2-strike GPU crash counter. CPU-only ships; Vulkan is needed only to claim GPU benefit.
- [03 — What must the B1 build harness guarantee before it merges?](issues/03-b1-merge-gate.md): B1 must be committed and clean with a wrapper, Docker/CI, scripts and docs; `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --offline` must pass from a fresh checkout; supported hosts are this aarch64 container and x86_64 Docker/CI; handoff needs commands, counts, APK hash and no-secrets proof. PM verifies in a scratch worktree and merges `--no-ff`.
- [01 — Which Whisper GGML models can v1 ship, from where, at what size?](issues/01-model-facts.md): v1 default `base` f16 (141 MiB, ~388 MB RAM), `tiny` first-class, `small` optional on 8 GB; `medium`/`large-v3-turbo` catalog-only and RAM-gated. Direct HF download from the pinned `ggerganov/whisper.cpp` revision, SHA-256 verified, MIT notices. Custom GGML (magic `0x67676d6c`), not GGUF; E1 must pin the whisper.cpp revision. Details: `research/01-model-facts.md`.

## Not yet specified

- OEM-specific overlay/accessibility/mic restrictions once the real device is known (ticket 08 sets the matrix; quirks appear only on hardware).
- Model-download UX details (resume, storage pressure, which larger models to offer) once ticket 01 facts land.
- Post-sign-off release hardening: R8/minify, upgrade path, long-term diagnostics, only if Martin wants ongoing distribution beyond sideload.

## Out of scope

- Play Store packaging and policy (v1 is sideload only).
- Cloud inference, telemetry, accounts, sync.
- History, custom vocabulary, on-device LLM cleanup, multi-language UI (v1 backlog, not this destination).
