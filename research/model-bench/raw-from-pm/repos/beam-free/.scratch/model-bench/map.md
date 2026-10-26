# Wayfinder map: free-model bench and mix

Label: `wayfinder:map`

## Destination

`research/MODEL-BENCH.md` containing real measured behaviour of the nine free opencode models across four tasks, a strengths/weaknesses table, and a proposed PM/worker mix with fallback and stall-detection rules.

## Notes

- Low priority: this effort must never delay B1/E1 merge, the APK map, or device work. Run bench workers in the background.
- Live model list and specs: `opencode models opencode` and `opencode models opencode --verbose` (accessed 2026-10-26).
- Models: deepseek-v4.1-flash-free, glm-5.3-flash-free, mimo-v2.6-flash-free, big-pickle, flint-40b-free, nemotron-3-ultra-free, nemotron-3.5-lightning-free, nemotron-3.5-super-vl-free, beam-free.
- Rules: real measured numbers only, no fabrication; cap retries at 2; log failures too. Each bench run uses its own scratch copy/worktree and `GRADLE_USER_HOME=/home/node/gh-pm` (never the shared `/work/android-toolchain/gradle-home`).
- Skills: `research` for ticket 01, `implement`/`tdd` for the code task, `handoff` for format task, `grilling` + `domain-modeling` for synthesis.
- PM may not edit `owner/`; lessons go in `research/MODEL-BENCH.md`.

## Decisions so far

<!-- one line per resolved ticket -->

## Not yet specified

- Rate limits and quota behaviour over repeated long agentic runs.
- Tool-call reliability under real multi-file edits, not just the four tasks.
- Image/VL capability (super-vl) is not exercised by this bench; a separate effort if needed.
- Whether the proposed mix holds under a real PM session (validate after ticket 03).

## Out of scope

- Paid models and cost optimisation.
- Fine-tuning, local hosting, or provider benchmarking outside `opencode/models`.
- Any APK-critical-path work.
