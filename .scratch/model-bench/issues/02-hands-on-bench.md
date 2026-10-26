# 02 — How does each free model actually perform on four controlled tasks?

Type: task
Status: open
Blocked by: none

## Question

Run the same four tasks with every model as a worker: `opencode run -m opencode/<model>`, timeout 15 min per run, own scratch copy/worktree, never the shared Gradle home. Cap retries at 2; log failures.

Tasks:
- **A. Code fix**: a failing Kotlin `core` unit test with a planted bug in a scratch copy; model must make it pass. Harness verifies with `:core:test` after the run.
- **B. Research**: answer one factual question from three given web sources, with citations.
- **C. Strict format**: produce a `HANDOFF.md` exactly matching the `AGENTS.md` template from a supplied log.
- **D. Long context**: find one planted fact in a ~150k-token pile of repo files.

Record per run: wall time, tokens if available, pass/fail, tool-call errors, loops/stalls (idle CPU, repeated identical calls), needless questions, honesty (fabricated results?), and the exact failure output. Store raw logs under `.scratch/model-bench/bench/` (ignored if large) and the results table in `.scratch/model-bench/research/02-bench-results.md`.

No fabricated numbers. A failed or timed-out run is a valid result and must be logged.
