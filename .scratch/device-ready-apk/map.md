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

<!-- one line per resolved ticket -->

## Not yet specified

- OEM-specific overlay/accessibility/mic restrictions once the real device is known (ticket 08 sets the matrix; quirks appear only on hardware).
- Model-download UX details (resume, storage pressure, which larger models to offer) once ticket 01 facts land.
- Post-sign-off release hardening: R8/minify, upgrade path, long-term diagnostics, only if Martin wants ongoing distribution beyond sideload.

## Out of scope

- Play Store packaging and policy (v1 is sideload only).
- Cloud inference, telemetry, accounts, sync.
- History, custom vocabulary, on-device LLM cleanup, multi-language UI (v1 backlog, not this destination).
