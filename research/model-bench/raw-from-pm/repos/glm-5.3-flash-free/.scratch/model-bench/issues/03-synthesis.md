# 03 — What is the best mix of free models across PM and worker roles?

Type: grilling
Status: open
Blocked by: 01, 02

## Question

Using only the measured results (ticket 02) and sourced evidence (ticket 01), produce `research/MODEL-BENCH.md` with:

- A model × task table (A/B/C/D) with pass/fail, wall time, notable failures.
- Strengths/weaknesses per model, separating measured from sourced/guess.
- **Proposed mix**: first choice and fallback for PM/orchestrator, planner/griller, coder, researcher, long-context reader, reviewer.
- Fallback order when a model stalls, and a concrete stall-detection rule (e.g. no new tool call for N seconds, repeated identical call, empty output).
- Short lessons that should steer future session prompts.
- A one-paragraph "confidence and limits" note: what this bench did not test.

Keep the proposed mix under one page. No invented results.
