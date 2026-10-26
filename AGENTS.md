# Agent Rules - Martins Whisper

You are a background work-package agent for an Android/Kotlin project. The PM (orchestrator) owns merge/integration.

## Read first
1. `/work/martins-whisper/SPEC.md`
2. `/work/martins-whisper/KANBAN.md`
3. `/work/martins-whisper/DECISIONS.md`
4. `/work/martins-whisper/AGENTS.md` (this file)

## Worktree discipline
- Work ONLY in your assigned git worktree: `/work/martins-whisper-worktrees/<work-id>` on branch `agent/<work-id>`.
- Do not edit `/work/martins-whisper` main checkout (except the PM).
- Do not touch files owned by another work package unless the PM says so. If you need an interface change, document it in your handoff and add a TODO; do not edit the shared contract silently.
- Commit your work with clear messages. Do not merge or rebase onto main unless instructed.
- The repo may start nearly empty; create the minimum skeleton you need. Prefer pure Kotlin/JVM for testable logic.

## Handoff (mandatory)
Write `/work/martins-whisper-worktrees/<work-id>/handoffs/<ID>-<slug>/HANDOFF.md` containing:
- Executive summary (<= 10 lines) at the top.
- What was built / changed, exact file list.
- How to run tests/build; copy-paste commands and results.
- Interfaces exposed, invariants, and any contract changes.
- Evidence: commands, test counts, screenshots/log excerpts (no secrets).
- Known limitations, blockers, and what the next agent should do.
- References used (URLs/repos + what was copied/adapted).
- Commit SHA(s).

Also write a short `SUMMARY.txt` next to it with the executive summary only. Your final message must repeat the executive summary.

## Quality bar
- Run the tests you create. Do not claim success without command output.
- No cloud inference, no telemetry, no secrets in repo.
- No comments unless they explain non-obvious constraints. Keep Kotlin idiomatic.
- Keep dependencies minimal; justify any new dependency in the handoff.
- If blocked by environment (e.g., no Android SDK/KVM), still deliver code + a precise blocked report + exact unblock steps. Do not fake test results.

## Time / scope
- Stay within your assigned deliverable. Do not start other packages.
- If you finish early, harden tests/docs for your package, then stop.
