# 01 — What do reviews, benchmarks and issue reports say about each free model?

Type: research
Status: open
Blocked by: none

## Question

For each of the nine models (deepseek-v4.1-flash-free, glm-5.3-flash-free, mimo-v2.6-flash-free, big-pickle, flint-40b-free, nemotron-3-ultra-free, nemotron-3.5-lightning-free, nemotron-3.5-super-vl-free, beam-free), collect evidence on:

- Model identity/family, release date, context/output limits, image support (from `opencode models opencode --verbose`; record access date).
- Published benchmark or review evidence: coding, reasoning, tool-call reliability, long-context recall, speed.
- Known weaknesses: loops, repeated tool calls, empty/idle turns, refusal/over-asking, context degradation.
- opencode-specific reports: issues, docs, release notes, compatibility quirks.

Every claim must be marked **sourced** (with URL) or **guess**. No vendor marketing as fact without a source. If a model has little or no public evidence, say so explicitly.

Write findings to `.scratch/model-bench/research/01-web-research.md` and append a short answer to this ticket.
