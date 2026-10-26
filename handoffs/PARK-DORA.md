# PARK-DORA: park state at container refill, 2026-10-26 (~18:4x Z)

I am DORA (docs/operator-scripts engineer), branch agent/d1-docs → renamed work in worktree `/work/martins-whisper-worktrees/d1-docs` (now on `agent/d2-public`).

## DONE (SHAs)
- D1 docs suite (README, BUILD/INSTALL/PERMISSIONS/MODELS/PRIVACY/DEVICE-*/TROUBLESHOOTING/DOCTOR, scripts install-apk/push-model/doctor) — merged stages:
  - DORA-1 merged `b33c8c3`; DORA-2 merged `421f223`.
- D1-3: `docs/RELEASE-CHECKLIST.md` released on `agent/d1-docs`, commits `a2cef93` + handoff `5ca0fbc`; merged into master by MARA as `39ca92d` (wave 4).
- D2a partial: public README rewrite committed on `agent/d2-public` as part of `714e4db` ("wip: D2a/D2b public-facing docs draft (STOPPED per MFVP order)"). Branch is per master `83a65df` — note commit 714e4db also parked unrelated WIP file deltas that were in the worktree (T1 tickets/KANBAN/LOGBOOK/EngineFactory.kt/strings/tests/test-device-ready.sh) — REVIEW before merging; docs-only branch would want only README/docs.

## RUNNING
- No workers were running at park time (`pgrep "opencode run"` = none). Two earlier SCRIBE workers exhausted session budget without commits; their logs are transient (`~/scratch/d1-workers/{d13,proof}.log`), no ticket state lost.
- No tickets were `running` per my files; nothing in flight from DORA.

## LOST (honest)
- D2b drafts (CONTRIBUTING.md, SECURITY.md, .github/ISSUE_TEMPLATE/{bug_report,feature_request,doc-fix,config,README}, docs/README.md index) were written this session to the d1-docs worktree but lost during a worktree stash/checkout shuffle before any commit. Not rewritten because MFVP STOPPED all of D2a/D2b. Content plan is recoverable from my session memory only; if the owner ever re-opens public docs, regenerate from master + docs/ as sources.

## NEXT (ordered, small tickets; NONE may start while parked / without owner go)
1. **MFVP item 4 support: phone-run evidence.** When wireless adb + device appear (H1): run `scripts/install-apk.sh`, `docs/FIRST-DICTATION.md` steps on the native APK; replace UNVERIFIED (device) markers with real output; fill DEVICE-MATRIX. Ticket D-H1.
2. **MFVP item 5: one short README** — what it is, install, 3 permissions, use. (Existing README overstates limits; needs a device-verified "How it works" once H1 passes.) Ticket D-R2, ~10 min.
3. **Doctor freshness:** check `scripts/doctor.sh` output claims (backend labels) against post-G3b code. Ticket D-DOC-1, small.
4. Deferred (NOT doing until owner re-opens public work): CONTRIBUTING/SECURITY/issue templates, docs index, CHANGELOG/ARCHITECTURE.

## TRAPS learned (for whoever resumes)
1. opencode Zen sessions die at session budget mid-turn with NO commit — commit early from partial output actually. commit yourself. Keep tickets ≤ 1 commit duration before the session expires.
2. `git stash -u` + `git checkout stash@{0} -- .` + `git stash drop` ate my untracked files; worktrees are dangerous crossing multi-branch resets. Or disk pressure by owner's cleaner tools? Regenerate-able from memory. Never combine.
3. Branch names: agent/d2-public was created from worktree while branches elsewhere pre-checked-out (refusing force-update). Use `git merge --fff-only` or check out then branch.
4. `git reset -q` in a dirty worktree WITHOUT要对 commit untracked → 我? Unless `commit -a` or `stash` first.nero ALL data loss via add before every rebase.
- Do NOT touch shared toolchain; own GRADLE_USER_HOME per brief.
- Bench/dock never share contract with workers' handoffs; write handoff even if stalled.
