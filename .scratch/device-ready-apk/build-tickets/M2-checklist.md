# M2 — G1 integration acceptance checklist verified + gap tickets (issue 10)
Manager: MARA | Role: CRITIC (W2) | Branch: `agent/m2-checklist` (worktree `/work/martins-whisper-worktrees/m2-checklist`)
Status: running(glm-5.3-flash-free, start 2026-10-26 ~19:25Z) | Retry cap: 2
Boundary: ONLY this worktree. Files owned: `.scratch/device-ready-apk/checklist-report.md` (new), `.scratch/device-ready-apk/tickets/*.md` (new), handoffs. NO production code, NO `app/`/`core/` edits, NO docs outside `.scratch/`, NO other branches.

## Goal
Per `.scratch/device-ready-apk/issues/10-g1-checklist.md`, verify EVERY item against the actual repo state (code, tests, git log, KANBAN/handoffs) rather than trust:
1. Merge history (B1/E1/T1/D1/C1/I1/U1/DORA waves) — check `git log --oneline master` evidence exists.
2. Required gate commands (`:core:test`, `:app:testDebugUnitTest`, `:app:assembleDebug`), test counts, APK size/sha256, no-secrets scan, clean status — point each at the handoff that proves it (`handoffs/PM-2026-10-26-5/`, `TESS-1`, `ASHA-1`, `DORA-2`).
3. At-merge updates (KANBAN/LOGBOOK/DECISIONS/STATUS) — check each file was actually touched in merge commits.
4. Native-fallback decision (ship seam + CPU path vs hold APK; tie to issue 02) — check DECISIONS/ADR wording.
5. Definition of done for G1: artifact path, docs, known issues, H1/H2 checklist readiness.
Write a report `.scratch/device-ready-apk/checklist-report.md`: per issue-10 bullet, `verified` (with the exact file/commit/handoff as evidence) or `gap`.
Every `gap` becomes ONE small ticket file in `.scratch/device-ready-apk/tickets/` named `GAP-<n>-<slug>.md` with: goal, inputs, acceptance check (a command), status `open`, empty Result/SHA. Read `docs/agents/issue-tracker.md` for the file format.

## Acceptance (paste real output into the handoff)
```
cd /work/martins-whisper-worktrees/m2-checklist
git log --oneline | head -30
grep -c verified .scratch/device-ready-apk/checklist-report.md   # and the gap list
ls .scratch/device-ready-apk/tickets/*.md
# no template/TODO placeholders left in tickets:
grep -rn "TBD\|goal: TODO" .scratch/device-ready-apk/tickets/ && echo FAIL || echo PASS-git
```
Cross-check claim vs reality: do NOT accept an item from a handoff's own claim unless the named file/commit exists (`git cat-file -e <sha>^{commit}`).

## Rules
- Handoff MANDATORY: `handoffs/W2-checklist/HANDOFF.md` (+ `SUMMARY.txt`) in the worktree; final message repeats the executive summary.
- wip commit every ~10 min; commit at end even if incomplete (`wip:` prefix).
- No builds needed (read-only verification). If you believe a build is required, note it as a follow-up ticket instead of running it.
- No fake results: if an item cannot be verified, mark `gap` with the exact missing evidence.
