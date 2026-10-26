# 01 — What do reviews, benchmarks and issue reports say about each free model?

Type: research
Status: resolved
Blocked by: none

## Question

For each of the nine models (deepseek-v4.1-flash-free, glm-5.3-flash-free, mimo-v2.6-flash-free, big-pickle, flint-40b-free, nemotron-3-ultra-free, nemotron-3.5-lightning-free, nemotron-3.5-super-vl-free, beam-free), collect evidence on:

- Model identity/family, release date, context/output limits, image support (from `opencode models opencode --verbose`; record access date).
- Published benchmark or review evidence: coding, reasoning, tool-call reliability, long-context recall, speed.
- Known weaknesses: loops, repeated tool calls, empty/idle turns, refusal/over-asking, context degradation.
- opencode-specific reports: issues, docs, release notes, compatibility quirks.

Every claim must be marked **sourced** (with URL) or **guess**. No vendor marketing as fact without a source. If a model has little or no public evidence, say so explicitly.

Write findings to `.scratch/model-bench/research/01-web-research.md` and append a short answer to this ticket.

## Answer

All nine models have credible public evidence (vendor cards + independent benchmarks + OpenCode issue reports); full citations are in `.scratch/model-bench/research/01-web-research.md` (accessed 2026-10-26).

- **Specs that matter:** the free tier serves DeepSeek/GLM/MiMo at **200k ctx / 32k out**, not their 1M-native windows; Nemotron Ultra, Super VL and Beam get ~1M; Flint gets 256k; big-pickle 200k. Only DeepSeek, GLM, MiMo and Super VL take images.
- **Strongest public coding/agentic evidence:** DeepSeek V4.1 Flash, GLM-5.3-Flash, Beam 1.0 and Flint 40B (Flint is entirely vendor-reported and unreplicated). Big-pickle (50.8% SWE Atlas QnA) is credible but is an undisclosed stealth model. MiMo is mid-pack. Nemotron 3.5 Lightning is a cheap executor (51.6% SWE-bench Verified, 24.6% Terminal-Bench), not a planner; Nemotron 3 Ultra is mid-tier and slow in practice.
- **Tool-call/loop risk is the differentiator, not benchmark scores:** documented loops/repetition for DeepSeek, GLM, MiMo, big-pickle, Nemotron Ultra and Beam (user report); GLM and MiMo have first-party or OpenCode-specific failure reports. Enable `doom_loop` deny and a stall timeout in the bench.
- **Gaps:** no independent long-context recall per model on the free endpoint; no published numeric rate limits; unclear which checkpoint each `-free` id serves (esp. DeepSeek 0910 vs 1017, MiMo base vs MOPD, big-pickle identity). These are unknowns for ticket 02, not blockers to this answer.
