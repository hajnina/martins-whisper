# Free-model bench + proposed mix (2026-10-26)

Scope: the 9 free opencode/Zen models only. Measured on 4 tasks (A code fix, B web research with 3 citations, C strict HANDOFF template, D ~200k-token canary lookup). Full per-run logs: `research/model-bench/runs/` (recovered PM runs) and `research/model-bench/runs-new/` (ORIN reruns). Raw table: `research/model-bench/results-all.md`.

## Proposed mix (measured; 5 cells provider-blocked)

| Role | Primary | Fallback | Why (measured) |
|---|---|---|---|
| MARA / PM, integrator | `glm-5.3-flash-free` | `deepseek-v4.1-flash-free` | 4/4; lowest token total of the 4/4 models (372k); zero-retention; strong external agentic scores |
| Planner / grill | `glm-5.3-flash-free` | `deepseek-v4.1-flash-free` | 4/4; reasoning effort low/high/max; efficient |
| CODER / ASHA | `deepseek-v4.1-flash-free` | `glm-5.3-flash-free` | 4/4; strongest external coding (Terminal-Bench 2.1 90.6, DeepSWE 74.2, Codeforces 3471) |
| SCOUT / web research | `big-pickle` | `glm-5.3-flash-free` | 4/4; fastest B (70 s) with 3 correct citations; GLM is the zero-retention fallback |
| READER / long context | `beam-free` | `nemotron-3.5-super-vl-free` | 4/4; 1M ctx; fastest D (5 s); external 3/3 on all 4 tasks. Super VL = 1M + vision |
| SCRIBE / strict format | `deepseek-v4.1-flash-free` | `big-pickle` | C 8/8 in 22 s (fastest); big-pickle 8/8 in 31 s |
| CRITIC / reviewer | `glm-5.3-flash-free` | `deepseek-v4.1-flash-free` | Different model from CODER; 4/4 and fastest; catches coder errors |

**Fallback order (any role, when the primary stalls or is unavailable):** `glm-5.3-flash-free` → `deepseek-v4.1-flash-free` → `beam-free` → `big-pickle` → `nemotron-3.5-lightning-free` → `mimo-v2.6-flash-free` → `nemotron-3.5-super-vl-free` → `flint-40b-free` → `nemotron-3-ultra-free`.

**Operating rules (measured, not guessed):**
- **2 workers max at once.** 4 simultaneous sessions hit inotify `ENOSPC` (`max_user_watches=65536`; each opencode watcher holds ~14–17k). Probe once before a wave.
- **Free quota is account-wide, not per model.** A burst produced `Free usage exceeded` on `glm`, `big-pickle` *and* `deepseek` at the same time, so switching Zen models does not bypass the cap. Cap concurrency, not model choice.
- **Stall rule:** no git change and ~0 % CPU for 10 min → kill, commit `wip:`, retry once with the fallback; max 2 retries.
- **Provider errors are not model failures.** `Free usage exceeded`, `Endpoint is unavailable` (503), `Model is unavailable` (400) → mark `blocked: provider`, pause launches, do not score the model.

**Quota ceiling measured (2026-10-26):** 2 concurrent workers ran 16 jobs / ~2.74M tokens in 16 min (17:39:47–17:55:51Z) with **zero** `Free usage exceeded` errors. The earlier 4-concurrent burst hit the limit and inotify `ENOSPC` within minutes. Safe ceiling today: **2 concurrent**, probe before each wave. This is a floor, not a hard number — the cap is account-wide and may move.

## Evidence (measured)

| Model | A | B | C | D | wall A/B/C/D (s) | tokens A/B/C/D | tools | toolErr | apiErr | repeats |
|---|---|---|---|---|---|---|---|---|---|---|
| deepseek-v4.1-flash-free | PASS | PASS | PASS | PASS | 31/129/22/9 | 137263/307869/106410/36834 | 33 | 2 | 0 | 0 |
| glm-5.3-flash-free | PASS | PASS | PASS | PASS | 11/105/47/8 | 49599/224414/61995/35743 | 21 | 2 | 0 | 1 |
| mimo-v2.6-flash-free | PASS | PASS | PASS | PASS | 12/198/64/8 | 38083/341867/133682/23800 | 34 | 3 | 0 | 0 |
| big-pickle | PASS | PASS | PASS | PASS | 8/70/31/7 | 39075/212369/138771/24327 | 28 | 1 | 0 | 0 |
| flint-40b-free | PASS | PASS | BLOCKED | PASS | 21/381/205/30 | 87473/262029/77057/64187 | 31 | 8 | 1 | 6 |
| nemotron-3-ultra-free | BLOCKED | BLOCKED | BLOCKED | BLOCKED | 76/73/76/74 | 0/0/0/0 | 0 | 0 | 4 | 0 |
| nemotron-3.5-lightning-free | PASS | PASS | PASS | PASS | 24/128/36/8 | 55851/521654/136753/41870 | 34 | 3 | 0 | 2 |
| nemotron-3.5-super-vl-free | PASS | PARTIAL | PASS | PASS | 8/395/42/14 | 40026/825900/87162/27576 | 28 | 5 | 0 | 1 |
| beam-free | PASS | PASS | PASS | PASS | 7/89/40/5 | 58635/349548/308210/36456 | 38 | 3 | 0 | 5 |

`BLOCKED` = provider/infra (`Model is unavailable` 400, `Endpoint is unavailable` 503), not a model verdict. `PARTIAL` = super-vl B: (b) correct, but it refused (a) because source 1 was unreachable (honest, not a wrong answer). flint C: 503 on both attempts (retry cap 2 reached).

## Per-model notes

### `deepseek-v4.1-flash-free`
- Specs: 200k ctx / 32k out, text+image, reasoning; zero-retention.
- Measured: 4/4. A 31 s, B 129 s (3 URLs, correct), C 22 s (8/8), D 9 s.
- Strengths: best external coding scores of the nine; correct and concise on format.
- Weaknesses: B is token-heavy (308k tokens); not the fastest.
- Sources: NVIDIA build model card (90.6 Terminal-Bench 2.1, 74.2 DeepSWE, 3471 Codeforces), DeepSeek API news 2026-10-17, eesel review, Vals AI comparison. [sourced]
- Verdict: default CODER/SCRIBE. [guess]

### `glm-5.3-flash-free`
- Specs: 200k ctx / 32k out, text+image, reasoning; zero-retention.
- Measured: 4/4. A 11 s, B 105 s (4 URLs, correct), C 47 s (8/8), D 8 s; lowest tokens overall.
- Strengths: most token-efficient 4/4 model; strong agentic scores (Terminal-Bench 2.1 84.3, DeepSWE 63.4, AAII 57).
- Weaknesses: C slower than deepseek; only 200k ctx on Zen.
- Sources: z.ai launch post, Vals AI model page, Artificial Analysis. [sourced]
- Verdict: default PM/planner/critic. [guess]

### `mimo-v2.6-flash-free`
- Specs: 200k ctx / 32k out, text+image, reasoning; 309B total / 15B active.
- Measured: 4/4, but the slowest: B 198 s / 342k tokens, C 64 s / 134k tokens.
- Strengths: correct on all four; omnimodal; strong DeepSWE (67.9).
- Weaknesses: verbose and slow; vendor reports 1.02 % tool-call repetition on OpenCode; one external timeout at 420 s.
- Sources: Xiaomi MiMo V2.6 article + tool-call-repetition post, Hugging Face card, Vals AI, bigiron.cc. [sourced]
- Verdict: keep for vision/omni tasks, not for quota-tight long runs. [guess]

### `big-pickle`
- Specs: 200k ctx / 32k out, text-only, reasoning; stealth model, free period.
- Measured: 4/4. A 8 s, B 70 s (fastest B), C 31 s (8/8), D 7 s.
- Strengths: fastest researcher; balanced; efficient.
- Weaknesses: user reports losing track past ~50–70k ctx and variable latency (29–337 s on a one-bug task); data may be used for training during the free period.
- Sources: opencode Zen docs (stealth), bigiron.cc test + user report, ayautomate. [sourced]
- Verdict: SCOUT primary; keep tasks short. [guess]

### `flint-40b-free`
- Specs: 262k ctx / 65k out, text-only; 40B total / 2.5B active; Apache 2.0.
- Measured: A 21 s; B 381 s (very slow, fetch-heavy, but correct); C BLOCKED — 503 on both attempts (retry cap 2); D 30 s.
- Strengths: strong vendor coding scores (Terminal-Bench 2.1 54.9, SWE-bench Pro 89.7, SWE-bench Verified 74.2); cheap/fast locally.
- Weaknesses: web research is slow (7+ fetches); endpoint stability was poor in the first wave.
- Sources: Recursive/X launch posts, Hugging Face (Apache 2.0), opencode usage page, bigiron.cc. [sourced]
- Verdict: coding fallback only if the endpoint is healthy. [guess]

### `nemotron-3-ultra-free`
- Specs: 1M ctx / 128k out, text-only; 550B total / 55B active.
- Measured: all 4 cells blocked with `Model is unavailable` (HTTP 400) on both attempts (retry cap 2) — infra, not a model verdict. External test (bigiron.cc) passed all four small tasks 3/3.
- Strengths: 1M ctx, strong long-context/agentic (Terminal-Bench 2.1 56.4, PinchBench 90).
- Weaknesses: Zen route was down in our window; 1M context on a free tier is quota-expensive.
- Sources: NVIDIA research page, build.nvidia.com, Vals AI, bigiron.cc. [sourced]
- Verdict: long-context candidate once the route is proven. [guess]

### `nemotron-3.5-lightning-free`
- Specs: 262k ctx / 262k out, text-only; 30B total / 3B active.
- Measured: 4/4. A 24 s; B 128 s (correct, but 522k tokens); C 36 s (8/8); D 8 s.
- Strengths: cheap, fast, big output window; SWE-bench Verified 52.8, PinchBench 83.4.
- Weaknesses: token-hungry B; weaker hard coding (Terminal-Bench 2.1 24.6); wandered outside its workdir once during C.
- Sources: NVIDIA Nemotron 3.5 tech report, Hugging Face card, benchmarklist, DataCamp. [sourced]
- Verdict: cheap fallback, not a primary. [guess]

### `nemotron-3.5-super-vl-free`
- Specs: 1M ctx / 131k out, text+image+video, reasoning; 120B total / 12B active.
- Measured: A 8 s; B 395 s — PARTIAL: it could not fetch the Android page (network), so it declined to answer (a); (b) correct. C 42 s (8/8); D 14 s.
- Strengths: 1M ctx + multimodal; SWE-bench Pro 51.78; CharXiv 80.8.
- Weaknesses: very slow web research; hedges when a source is unreachable (honest, but not a finisher).
- Sources: NVIDIA technical blog, Nemotron 3.5 tech report, build.nvidia.com. [sourced]
- Verdict: READER/multimodal fallback. [guess]

### `beam-free`
- Specs: 1M ctx / 131k out, text-only; 501B total / 23B active; Apache 2.0.
- Measured: 4/4. A 7 s; B 89 s (correct, 3 URLs); C 40 s (8/8, 308k tokens); D 5 s (fastest).
- Strengths: 1M ctx; efficient (external: 5 calls/36k on a one-bug task); fast.
- Weaknesses: text-only; less independent evidence than GLM/DeepSeek.
- Sources: Reflection launch post, ollama library card, bigiron.cc, aipromonow. [sourced]
- Verdict: READER primary. [guess]

## Gaps / blocked cells

- `nemotron-3-ultra-free`: A–D were `Model is unavailable` (400) on both attempts (retry cap 2); retry only after the Zen route is proven healthy. External evidence says the model itself is fine.
- `flint-40b-free`: C was `Endpoint is unavailable` (503) on both attempts; the model never produced a C file. Endpoint stability, not format ability.
- `nemotron-3.5-super-vl-free`: B is PARTIAL by manual grade (source unreachable, answer (a) withheld), not a wrong answer.
- `nemotron-3.5-lightning-free`: C content passed 8/8 but the model wrote into a live app worktree and the owner inbox; it ignored its workdir. Containment risk — sandbox before using it unsupervised.
- D was measured on a ~200k-token pile, not the advertised 1M windows; 1M claims are vendor claims, not measured here.
- One run per cell (except retries); latency is noisy on the free host (see big-pickle 29–337 s external spread).

## Method

- 9 models × 4 tasks, same prompts (`/work/orin/bench/prompts/`). A is verified by running `:core:test` against a planted bug with a private `GRADLE_USER_HOME=/work/orin/gh`; B/C/D are graded from the JSONL plus a manual read of the final text/files.
- Runs: recovered app-PM runs 16:58–17:03Z + ORIN reruns 17:37–17:55Z, `opencode run -m opencode/<model> --format json --auto`, 900 s timeout, retry cap 2.
- Provider gate: `/work/.yoloseek/PROVIDER-STATUS.md` must say OK; a real PONG probe precedes a wave.
- Tokens are the sum of `step_finish.tokens.total` in the JSONL (includes cached reads); wall time is whole-process.

## Lessons

- Free tier is burst-limited and account-wide; 2 concurrent workers is the safe ceiling we could sustain, 4 triggered the limit and inotify `ENOSPC`.
- Exact-heading C grading gives false negatives (MiMo used variant headings); always read the final HANDOFF.
- `Model is unavailable` / 503 are infra, not model quality.
- Never `pkill -f 'opencode run ...'` (it matches the calling shell); use `ps -eo pid,args` then `kill <pid>`.
- Keep bench data under `/work`, never container-local `/home/node`.
- Containment: `--auto` lets a model write anywhere it can `cd`. One C run wrote into a live app worktree and the owner inbox; restore with `git checkout --` and sandbox writes (or check `git status` after every C run).
