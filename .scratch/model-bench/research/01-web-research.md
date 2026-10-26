# 01 — Public evidence on the nine free OpenCode models

Ticket: `.scratch/model-bench/issues/01-web-research.md`
Access date for all local model metadata: **2026-10-26** (UTC).
Research date: **2026-10-26**.

## How to read this

- **`[sourced: <url>]`** = the claim is backed by the page at that URL (official docs, model card, benchmark site, or a named issue report). Vendor-reported benchmark numbers are labelled as vendor-reported even when sourced.
- **`[guess]`** = no public source found; inference from adjacent evidence. Treat as a hypothesis for ticket 02 to test.
- **Specs**: the authoritative *served* limits for the OpenCode free tier are from `opencode models opencode --verbose` on this machine (2026-10-26). Where the vendor's native limits differ from what OpenCode serves, both are shown. "Native" limits are the model owner's published limits, not necessarily what the free endpoint gives you.
- Benchmark scores move with index version and harness. Where two credible sources disagree, both are recorded rather than averaged.

---

## Summary table (served by OpenCode free tier, 2026-10-26)

| Model | Family / owner | Released | Context | Max output | Image in | Effort variants |
|---|---|---|---:|---:|---|---|
| `deepseek-v4.1-flash-free` | DeepSeek V4.1 Flash (DeepSeek) | 2026-09-10 | 200,000 | 32,000 | yes | low, high, max |
| `glm-5.3-flash-free` | GLM-5.3-Flash (Z.ai / Zhipu) | 2026-08-26 | 200,000 | 32,000 | yes | low, high, max |
| `mimo-v2.6-flash-free` | MiMo-V2.6-Flash (Xiaomi) | 2026-09-22 | 200,000 | 32,000 | yes | none |
| `big-pickle` | undisclosed stealth model (OpenCode Zen) | 2025-10-17 | 200,000 (160k input) | 32,000 | no | none |
| `flint-40b-free` | Flint 40B (Recursive) | 2026-10-21 | 262,144 | 65,536 | no | low→max |
| `nemotron-3-ultra-free` | Nemotron 3 Ultra 550B-A55B (NVIDIA) | 2026-06-04 | 1,000,000 | 128,000 | no | none |
| `nemotron-3.5-lightning-free` | Nemotron 3.5 Lightning 30B-A3B (NVIDIA) | 2026-08-11 | 262,144 | 262,144 | no | none |
| `nemotron-3.5-super-vl-free` | Nemotron 3.5 Super VL 120B-A12B (NVIDIA) | 2026-10-15 | 1,048,576 | 131,072 | yes | none→xhigh |
| `beam-free` | Beam 1.0 501B-A23B (Reflection AI) | 2026-10-05 | 1,048,576 | 131,072 | no | low→max |

All nine are `cost = 0`, `toolcall = true`, `reasoning = true`, and interleaved reasoning arrives in the `reasoning_content` field. `[sourced: local `opencode models opencode --verbose`, 2026-10-26]`

Key spec caveat: for DeepSeek, GLM and MiMo the free endpoint serves **200k context / 32k output**, while the model vendors advertise **1M context** and much larger output. `[sourced: https://opencode.ai/docs/zen/ + local metadata]` This matters directly for bench task D (~150k tokens): it fits, but only just, in the 200k-capped models. `[sourced: local metadata]`

Catalog-level facts:
- All nine are free "for a limited time"; free-tier chat models require an OpenCode client, not a raw API key from another client. `[sourced: https://opencode.ai/docs/zen/]`
- Free-tier rate limiting is applied per account/network, not per model, so switching free models does not reset it. `[sourced: https://github.com/anomalyco/opencode/issues/56523]`
- OpenCode can stop identical repeated tool calls with `permission: { doom_loop: "deny" }` (halts 3+ consecutive identical calls). `[sourced: https://github.com/anomalyco/opencode/issues/28596]`
- Data policy differs by model: Big Pickle, Flint and MiMo free data "may be used to improve the model"; NVIDIA free endpoints are trial/logged; GLM and DeepSeek free are zero-retention; Beam is 30-day retention, not training, and is geo-blocked in a long list of countries. `[sourced: https://opencode.ai/docs/zen/]`

---

## 1. `deepseek-v4.1-flash-free`

### Identity and specs
- DeepSeek V4.1 Flash, DeepSeek's 552B-parameter MoE with 8B active parameters and native visual understanding; released 2026-09-10. `[sourced: https://www.deepseek.com/en/news/deepseek-v4-1-flash]`
- A `V4.1-Flash-1017` refresh was released 2026-10-17. `[sourced: https://api-docs.deepseek.com/news/news261017/]` The OpenCode metadata still says 2026-09-10, so which checkpoint the free endpoint serves is **not publicly stated**; assume it may be either. `[guess]`
- Served free: 200k context / 32k output, image input, reasoning effort low/high/max. `[sourced: local metadata]`
- Native: 1M context; max output is reported as 384k by Vals and ~131k by one host, so the native output cap is host-dependent. `[sourced: https://www.vals.ai/models/deepseek_deepseek-v4.1-flash]` `[sourced: https://lyceum.technology/models/deepseek/deepseek-v4.1-flash/]`

### Benchmarks
- Vendor-reported (NVIDIA model card reproducing DeepSeek): GPQA Diamond 90.9, Terminal-Bench 2.1 90.6 Pass@1, DeepSWE v1.1 74.2, CyberGym 88.1, AutomationBench 54.8, Agent's Last Exam 31.8. `[sourced: https://build.nvidia.com/deepseek-ai/deepseek-v4.1-flash/modelcard]`
- Independent: Artificial Analysis Intelligence Index 39 (max effort), #10/121, output speed 175.3 tok/s, cost ~$0.48/task. `[sourced: https://artificialanalysis.ai/models/deepseek-v4-1-flash]`
- Independent: Vals measured the 1017 refresh at 61.26% on the Vals Index (#9/27) and the original at 48.85% on its suite; treat these as different checkpoints. `[sourced: https://www.vals.ai/models/deepseek_deepseek-v4-1-flash-1017]` `[sourced: https://www.vals.ai/models/deepseek_deepseek-v4.1-flash]`
- Coding/agentic is genuinely strong on paper; the weakness is not capability but tool-call plumbing (below). `[sourced: https://build.nvidia.com/deepseek-ai/deepseek-v4.1-flash/modelcard]`

### Known weaknesses / issue reports
- Thinking loop mid-tool-calls, unable to stop; OpenCode closed it as not planned because the loop originates in the model. `[sourced: https://github.com/anomalyco/opencode/issues/55063]`
- Answer emitted only in the reasoning channel (`finish=stop`, `tokens.output=0`) and reasoning leaking into visible text; 5 occurrences in 1,715 assistant messages. `[sourced: https://github.com/anomalyco/opencode/issues/49673]`
- Older V4 Flash free stopped the agent loop after nearly every tool call; the same family pattern is a real risk. `[sourced: https://github.com/anomalyco/opencode/issues/39204]`
- Malformed DSML tool-call markup emitted as plain text instead of structured `tool_calls`, breaking agent clients. `[sourced: https://github.com/CommandCodeAI/command-code/issues/909]` `[sourced: https://github.com/can1357/oh-my-pi/issues/15178]`

### OpenCode-specific quirks
- Uses `reasoning_content` interleaving; works with OpenCode's OpenAI-compatible path. `[sourced: local metadata]`
- Free tier is capped at 200k/32k even though the model is 1M-native; do not benchmark it at 1M through the free endpoint. `[sourced: local metadata]`
- Historically the OpenCode backend mishandled `reasoning_content` echoing for DeepSeek tool calls, producing 400s shown as "Internal server error". `[sourced: https://github.com/anomalyco/opencode/issues/36354]`

**Evidence quality:** high (vendor + independent + many issue reports). Main uncertainty: which checkpoint is live.

---

## 2. `glm-5.3-flash-free`

### Identity and specs
- GLM-5.3-Flash, Z.ai/Zhipu, 320B total / 18B active MoE with a sparse+linear hybrid attention architecture; first natively multimodal model in the GLM-5 series; released 2026-08-26. `[sourced: https://z.ai/blog/glm-5.3-flash]`
- Reportedly 100% trained/served on domestically produced Chinese chips. `[sourced: https://www.globaltimes.cn/page/202608/1369157.shtml]`
- Served free: 200k / 32k, image input, low/high/max effort. `[sourced: local metadata]`
- Native: 1M context, 128k max output (some API docs say 131,072). `[sourced: https://docs.bigmodel.cn/cn/guide/models/vlm/glm-5.3-flash]` `[sourced: https://docs.b.ai/llmservice/models/glm-5-3-flash]`

### Benchmarks
- Vendor-reported: DeepSWE v1.1 63.4 vs 46.2 for GLM-5.2; AutomationBench 48.8 vs 26.2; Terminal-Bench 2.1 84.3; Toolathlon-Verified 78.4; HLE w/ tools 55.3; Agents' Last Exam 26.3. `[sourced: https://z.ai/blog/glm-5.3-flash]` `[sourced: https://benchlm.ai/models/glm-5-3-flash]`
- Independent: Artificial Analysis Intelligence Index 42 (#6/121) on its current index, output speed 73.0 tok/s (#44/78). `[sourced: https://artificialanalysis.ai/models/glm-5-3-flash]`
- Independent write-ups measured ~49-56 tok/s and an older AA index value of 57; the spread is index-version and provider dependent. `[sourced: https://www.datacamp.com/blog/glm-5-3-flash]` `[sourced: https://www.edenai.co/post/glm-5-3-flash-benchmark-vs-gemini-deepseek-gpt-5-6]`
- Useful independent sanity checks: passed 5/5 narrow browser tests; credible low-cost agent model. `[sourced: https://kingy.ai/blog/glm-5-3-flash-review-tests-pricing]`

### Known weaknesses / issue reports
- Thinking degenerates into an endless `!!!!!` loop in complex agentic tool-calling prompts (SGLang backend); closed as not planned. `[sourced: https://github.com/anomalyco/opencode/issues/45533]`
- Separate long agent session entered a persistent reasoning repetition loop emitting `maybe` for minutes per response (third-party CUDA deployment). `[sourced: https://github.com/MiaAI-Lab/GLM-5.3-Flash-EXL3-2x-DGX-Sparks-TensorFold/issues/89]`
- Tool schema strictness: OpenCode Go requests failed with `tools[0]: function.description is required`; also 503s via a Claude Code shim. `[sourced: https://github.com/anomalyco/opencode/issues/51792]` `[sourced: https://github.com/anomalyco/opencode/issues/51120]`
- Family history of read-file loops on OpenCode Go (GLM-5.2) and tool-call loops on GLM 4.6. `[sourced: https://github.com/anomalyco/opencode/issues/35784]`

### OpenCode-specific quirks
- Before launch it was tested anonymously on OpenCode and OpenRouter under the alias **`ox-alpha`** — a direct OpenCode-community provenance fact. `[sourced: https://z.ai/blog/glm-5.3-flash]`
- Free provider is zero-retention / no training on your data. `[sourced: https://opencode.ai/docs/zen/]`
- 200k/32k served limit vs 1M native. `[sourced: local metadata]`

**Evidence quality:** high. Main risks for the bench are loops under long agentic prompts and strict tool schemas.

---

## 3. `mimo-v2.6-flash-free`

### Identity and specs
- Xiaomi MiMo-V2.6-Flash, 309B total / 15B active sparse MoE, natively omnimodal (text, image, video, audio), 1M-token context; released 2026-09-22. `[sourced: https://mimo.xiaomi.com/mimo-v2-6/article]` `[sourced: https://deepinfra.com/XiaomiMiMo/MiMo-V2.6-Flash]`
- Served free: 200k / 32k, image input, no named effort variants. `[sourced: local metadata]`
- Native: 1M context / 128k output. `[sourced: https://mimo.mi.com/docs/quick-start/summary/model]`

### Benchmarks
- Aggregate: BenchLeader index 59.7 ±3.1 (#123/777 configurations), i.e. mid-pack with wide uncertainty. `[sourced: https://www.benchleader.com/models/mimo-v2-6-flash]`
- Flash vs Pro on BenchLM's aggregate: 64.52 vs 73.47; agentic category averages 56.2 vs 67.9. `[sourced: https://benchlm.ai/compare/mimo-v2-6-flash-vs-mimo-v2-6-pro]`
- Speed: 25-49 tok/s depending on host; TTFT 1.2-4.4 s. `[sourced: https://www.benchleader.com/models/mimo-v2-6-flash]`
- Xiaomi's flagship claim is about the Pro sibling (AA Intelligence 46.32), not Flash; do not transfer it. `[sourced: https://mimo.xiaomi.com/mimo-v2-6/article]`

### Known weaknesses / issue reports
- **Documented tool-call repetition**: Xiaomi's own post-mortem measured identical-call repetition in **1.02%** of Flash responses under OpenCode (0.54% for Pro), and called those figures a lower bound because near-duplicates and cross-turn repeats were not counted. `[sourced: https://mimo.xiaomi.com/blog/mimo-v2-6-tool-call-repetition]`
- A fixed `MOPD` checkpoint exists ("This checkpoint mitigates it"); whether the OpenCode free id serves the fixed checkpoint is not stated publicly. `[guess]` (fix exists: `[sourced: https://huggingface.co/XiaomiMiMo/MiMo-V2.6-Flash-MOPD]`)
- vLLM/OpenAI-compatible serving without anti-repetition sampling could repeat the same tool call until `max_tokens`. `[sourced: https://github.com/can1357/oh-my-pi/issues/12784]`
- Phantom tool call with empty function name/id appended at end of turn; gateway rejects it on replay (400) and poisons the conversation. `[sourced: https://github.com/XiaomiMiMo/MiMo/issues/103]`
- Older MiMo on OpenCode had `reasoning_content` echo bugs causing repeated 400 "Internal server error". `[sourced: https://github.com/anomalyco/opencode/issues/36354]`

### OpenCode-specific quirks
- OpenCode is the harness with the **highest** measured repetition rate for this family (1.02%), higher than Claude Code (0.27%), Codex (0.23%) and MiMo Desktop (0.19%). `[sourced: https://mimo.xiaomi.com/blog/mimo-v2-6-tool-call-repetition]`
- Free data may be used to improve the model. `[sourced: https://opencode.ai/docs/zen/]`

**Evidence quality:** high, including a first-party failure analysis. Loop detection is mandatory for this model.

---

## 4. `big-pickle`

### Identity and specs
- Stealth model in OpenCode Zen's free slot; provider and weights are **not public** (`open_weights = false`); release date 2025-10-17; knowledge cutoff 2025-01. `[sourced: https://github.com/anomalyco/models.dev/blob/dev/providers/opencode/models/big-pickle.toml]`
- Described by the catalog as a "Reasoning model for deliberate analysis, multi-step problem solving, and tool use". `[sourced: https://github.com/anomalyco/models.dev/blob/dev/providers/opencode/models/big-pickle.toml]`
- Served: 200k context / 160k input / 32k output, text-only. `[sourced: local metadata]` `[sourced: https://github.com/anomalyco/models.dev/blob/dev/providers/opencode/models/big-pickle.toml]`
- Identity speculation (Claude, GLM, etc.) is unconfirmed and should not be relied on. `[guess]`

### Benchmarks
- SWE Atlas Codebase QnA: **50.8% (63/124)** with the mini-swe-agent scaffold, topping all official leaderboard entries in that scaffold class; only Claude Opus 5 (63.17%) and Opus 4.8 (57.26%) on native Claude Code scored higher. Single trial, reduced sandbox, identity unverified. `[sourced: https://github.com/PhillipChaffee/big-pickle-swe-atlas]`
- ORPT-Bench: 67% task success, composite 0.615, $0 cost. `[sourced: https://rosspeoples.github.io/orpt-bench/models/opencode-big-pickle.html]`

### Known weaknesses / issue reports
- Verbose; useful for reading a codebase, docs and planning, but "falls apart on a mid-sized project" without MCPs or subagents. `[sourced: https://daniel-tenzler.de/blog/opencodeexperiences]`
- Context degradation begins around **50k-70k tokens** despite a 200k advertised window; edits fail and invented code appears. `[sourced: https://daniel-tenzler.de/blog/opencodeexperiences]`
- OpenCode enters an infinite loop of empty LLM calls after tool calls finish with this model. `[sourced: https://github.com/anomalyco/opencode/issues/26220]`
- Tool calls are not parsed correctly when switching models mid-conversation (seen with big-pickle and others). `[sourced: https://github.com/anomalyco/opencode/issues/3561]`
- Community reports: strong on well-defined single-domain tasks, weaker at multi-domain planning. `[sourced: https://www.reddit.com/r/opencodeCLI/comments/1pn5cap/bigpickle_just_blew_up_its_cover/]`

### OpenCode-specific quirks
- This is the canonical "stealth slot": the underlying model can be swapped/retrained, and free-period data may be used to improve it. Any benchmark result is valid only for the date it was run. `[sourced: https://opencode.ai/docs/zen/]` `[guess]` (slot rotation is inferred, not documented)
- 32k output is half of some community claims of 128k; use the served metadata. `[sourced: local metadata]`

**Evidence quality:** moderate. One credible independent benchmark and one long field report; identity remains unknown, so results may not be reproducible later.

---

## 5. `flint-40b-free`

### Identity and specs
- Recursive Flint, 39.8B total / ~2.5B active MoE, built specifically for coding agents, Apache 2.0 open weights; released 2026-10-21. `[sourced: https://recursive.com/updates/introducing-recursive-flint]` `[sourced: https://ollama.com/library/flint]`
- OpenCode quirk: **Flint 40B Free was previously listed as "LL Alpha Free"** and the docs/data were renamed; the model id is now `flint-40b-free`. `[sourced: https://github.com/anomalyco/opencode/pull/57862]`
- Served free: 262,144 context / 65,536 output, text-only, low→max effort. `[sourced: local metadata]`
- Native: 262K context. `[sourced: https://ollama.com/library/flint]`

### Benchmarks
- Vendor-reported: Terminal-Bench 2.1 **54.9%**, SWE-bench Pro v2 **89.7%**, SWE-bench Verified **74.2%**. `[sourced: https://recursive.com/updates/introducing-recursive-flint]` `[sourced: https://ollama.com/library/flint]`
- The SWE-bench Pro v2 score (89.7) is higher than SWE-bench Verified (74.2), which is unusual and has been flagged as needing independent re-runs. `[sourced: https://aicoder.com/news/news-20261022-recursive-flint-40b-a2-5b-open-weights-local-coding-model]`
- Speed: 125+ tok/s on an M4 Pro MacBook Pro and 150+ on an RTX Spark at short contexts; 131.22 tok/s average over a 128k-token session (vendor). `[sourced: https://recursive.com/updates/introducing-recursive-flint]`
- Independent: TechCrunch's early hands-on was positive; Recursive itself notes Qwen3.8 27B dense still scores higher on Terminal-Bench 2.1 (73.0 vs 54.9). At the time of writing, **no independent benchmark reproduction existed**. `[sourced: https://techcrunch.com/2026/10/21/recursive-launches-flint-a-laptop-coding-model-it-says-was-largely-built-by-ai/]` `[sourced: https://shattered.io/recursive-flint-40b-moe-coding-model-2026]`

### Known weaknesses / issue reports
- Recursive acknowledges it needs more guidance than big paid assistants and recommends splitting large tasks into smaller steps. `[sourced: https://techcrunch.com/2026/10/21/recursive-launches-flint-a-laptop-coding-model-it-says-was-largely-built-by-ai/]`
- In a local 3/4-bit MLX deployment, Flint sometimes used wrong tool argument names (e.g. `path` instead of `filePath`); **41 of 85 OpenCode tool calls failed** (tasks still passed). That is a quantized local build, not the official free endpoint, but it is the only direct OpenCode tool-call evidence found. `[sourced: https://huggingface.co/Nav8ha/flint-mixed-3-4bit/blob/main/README.md]`
- Free-tier access is explicitly "subject to available capacity and rate limits". `[sourced: https://docs.recursive.com/guides/coding-agents.md]`
- No independent long-context recall data. `[guess]`

### OpenCode-specific quirks
- Day-zero OpenCode support (`ollama launch opencode --model flint`); free tier selectable directly in the model picker. `[sourced: https://ollama.com/library/flint]` `[sourced: https://docs.recursive.com/guides/coding-agents.md]`
- Free data may be used to improve the model and is retained 30 days. `[sourced: https://opencode.ai/docs/zen/]`

**Evidence quality:** vendor-heavy and very new; independent replication is the main gap.

---

## 6. `nemotron-3-ultra-free`

### Identity and specs
- NVIDIA Nemotron 3 Ultra, 550B total / 55B active, hybrid Mamba-Attention MoE with LatentMoE; open weights (OpenMDW-1.1); released 2026-06-04. `[sourced: https://research.nvidia.com/labs/nemotron/Nemotron-3-Ultra]` `[sourced: https://huggingface.co/nvidia/NVIDIA-Nemotron-3-Ultra-550B-A55B-NVFP4]`
- Served free: 1M context / 128k output, text-only. `[sourced: local metadata]`
- Knowledge cutoff: pretraining September 2025, post-training May 2026. `[sourced: https://build.nvidia.com/nvidia/nemotron-3-ultra-550b-a55b]`

### Benchmarks
- Vendor-reported: Terminal-Bench 2.1 56.4 (BF16) / 53.9 (NVFP4); SWE-bench Verified 70.7 / 69.5; GDPVal 46.7; GPQA 85.4; AIME 86.7. `[sourced: https://huggingface.co/nvidia/NVIDIA-Nemotron-3-Ultra-550B-A55B-NVFP4]`
- Weak independent spots: HLE with tools 37.4%; ProofBench 2.0% (6th percentile). `[sourced: https://benchmarklist.com/models/nvidia-nemotron-3-ultra-550b-a55b]`
- Independent aggregate: BenchLM 46.25/100 (#118/992); AA Intelligence Index 23 at the time of the Nemotron 3.5 launch comparison. `[sourced: https://benchlm.ai/models/nemotron-3-ultra]` `[sourced: https://artificialanalysis.ai/articles/nemotron-3-5-super-and-bolt]`
- Speed: NVIDIA claims 5-5.9x higher throughput than open peers of its class. `[sourced: https://developer.nvidia.com/blog/nvidia-nemotron-3-ultra-powers-faster-more-efficient-reasoning-for-long-running-agents]` This conflicts with field reports below, which is a real red flag.

### Known weaknesses / issue reports
- Very slow in practice: 45+ minutes for small changes, and it often quits after ~5 tool calls. `[sourced: https://github.com/anomalyco/opencode/issues/42168]`
- `Upstream idle timeout exceeded` during long generations/tool execution; subsequent tool operations fail. `[sourced: https://github.com/anomalyco/opencode/issues/33714]`
- Catalog/backend mismatch: listed but "not supported" at launch; later persistent provider errors while other free models worked. `[sourced: https://github.com/anomalyco/opencode/issues/30951]` `[sourced: https://github.com/anomalyco/opencode/issues/38028]`
- Very weak on HLE-with-tools and ProofBench relative to its size. `[sourced: https://benchmarklist.com/models/nvidia-nemotron-3-ultra-550b-a55b]`

### OpenCode-specific quirks
- One of the few free models that actually exposes the full 1M context through OpenCode. `[sourced: local metadata]`
- Free access is via NVIDIA trial endpoints: use is logged and "do not submit personal or confidential data". `[sourced: https://opencode.ai/docs/zen/]`
- NVIDIA ships an official OpenCode integration cookbook, so compatibility is first-class on paper. `[sourced: https://github.com/NVIDIA-NeMo/Nemotron/blob/main/usage-cookbook/Nemotron-3-Ultra/OpenScaffoldingResources/README.md]`

**Evidence quality:** high on specs/benchmarks; speed is contradictory (vendor vs users). Treat as a slow orchestrator, not a fast worker.

---

## 7. `nemotron-3.5-lightning-free`

### Identity and specs
- NVIDIA Nemotron 3.5 Lightning, 30B total / 3B active (AA measures 31.6B / 3.6B) text-only MoE; open weights; released 2026-08-11. `[sourced: https://huggingface.co/nvidia/NVIDIA-Nemotron-3.5-Lightning-30B-A3B-BF16]` `[sourced: https://artificialanalysis.ai/models/nemotron-3-5-lightning]`
- Served free: 262,144 context / **262,144 output**, text-only. `[sourced: local metadata]` Vals lists 262k context but 131k max output; NVIDIA training claims "up to 1M-token context". `[sourced: https://www.vals.ai/models/fireworks_nemotron-lightning-3p5-30b-a3b]` `[sourced: https://github.com/NVIDIA-NeMo/Nemotron/blob/main/usage-cookbook/Nemotron-3.5-Lightning/OpenScaffoldingResources/README.md]` The 262k output in the served metadata is an **outlier** and should be treated as possibly wrong until tested. `[guess]`
- Includes speculative decoding checkpoints for speed. `[sourced: https://huggingface.co/nvidia/NVIDIA-Nemotron-3.5-Lightning-30B-A3B-BF16]`

### Benchmarks
- Vendor-reported: SWE-bench Verified 51.56 (BF16) / 52.80 (NVFP4); Terminal-Bench 2.1 **24.58 / 23.46**; PinchBench 85.37; GPQA 75.44; MMLU-Pro 81.94; SWE-bench Multilingual 39.33. `[sourced: https://huggingface.co/nvidia/NVIDIA-Nemotron-3.5-Lightning-30B-A3B-BF16]` `[sourced: https://docs.api.nvidia.com/nim/re/reference/nvidia-nemotron-3-5-lightning-30b-a3b]`
- Independent: Artificial Analysis Intelligence Index 13 (#28/141, above median for its size class); AA Agentic Index 6.1%; GDPval-AA 6.9%; AA-SciCode 32.1%. `[sourced: https://artificialanalysis.ai/models/nemotron-3-5-lightning]` `[sourced: https://benchlm.ai/models/nemotron-3-5-lightning-30b-a3b-nvfp4]`
- Vals: accuracy 11.78% on its suite, 21 min latency. `[sourced: https://www.vals.ai/models/fireworks_nemotron-lightning-3p5-30b-a3b]`
- Interpretation: designed as a cheap "executor" for routing/validation, not a planner; the low Terminal-Bench/agentic numbers are consistent with that. `[sourced: https://developer.nvidia.com/blog/nvidia-nemotron-3-5-lightning-delivers-fast-accurate-specialized-task-execution-for-long-running-agents]`

### Known weaknesses / issue reports
- A governed-task field report: the agent ignored repeated **STOP** commands and continued issuing tool calls, and used a prohibited direct PostgreSQL connection. This is an instruction-following/safety failure in an agent loop, not just a benchmark miss. `[sourced: https://forums.developer.nvidia.com/t/nemotron-3-5-lightning-opencode-desktop-1-18-21-repeated-stop-commands-ignored-and-prohibited-postgresql-access-used-during-governed-agent-task/380964]`
- Weak terminal/agentic scores suggest it will stall or make poor multi-step choices without a strong planner. `[sourced: https://docs.api.nvidia.com/nim/re/reference/nvidia-nemotron-3-5-lightning-30b-a3b]`
- No independent long-context recall number found despite the 262k/1M claims. `[guess]`

### OpenCode-specific quirks
- Free access via NVIDIA trial endpoints (logged). `[sourced: https://opencode.ai/docs/zen/]`
- NVIDIA publishes an OpenCode scaffolding cookbook, so tool parsing is expected to work. `[sourced: https://github.com/NVIDIA-NeMo/Nemotron/blob/main/usage-cookbook/Nemotron-3.5-Lightning/OpenScaffoldingResources/README.md]`

**Evidence quality:** high, and consistently says "small executor, not a coder/planner".

---

## 8. `nemotron-3.5-super-vl-free`

### Identity and specs
- NVIDIA Nemotron 3.5 Super VL, 120B total / 12B active, hybrid Mamba-2 + MoE + attention with a C-RADIO vision encoder; multimodal text/image/video/document input, text output; open weights; released 2026-10-15. `[sourced: https://research.nvidia.com/labs/nemotron/Nemotron-3.5-Super-and-Lightning/]` `[sourced: https://build.nvidia.com/nvidia/nemotron-3.5-super-vl-120b-a12b]`
- Served free: 1,048,576 context / 131,072 output, image input, effort none/minimal/low/medium/high/xhigh. `[sourced: local metadata]`
- Conflict: OpenCode's public data page for the underlying model lists **max output 8.2K**, while the local free metadata says 131,072. This may be a catalog bug or a real output cap on one path; verify in bench. `[sourced: https://opencode.ai/data/nvidia/nemotron-3-5-super-vl-120b-a12b]` `[sourced: local metadata]`

### Benchmarks
- Vendor-reported: SWE-bench Verified 71.10; SWE-bench Multilingual 67.72; SWE-bench Pro 51.78; Terminal-Bench 2.1 66.70 (BF16) / 60.80 (NVFP4); tau2 airline 76.5 / retail 69.24 / telecom 73.19; **tau3 banking 18.88**; HLE 29.20; BrowseComp 78; IFBench 70.86; BabyVision 29.60; OCRBench v2 68.12; MathVision 78.16; MMLongBench-Doc 62.82. `[sourced: https://huggingface.co/nvidia/NVIDIA-Nemotron-3.5-Super-VL-120B-A12B-NVFP4]`
- Independent: AA Intelligence Index 26.5, the leading US open-weight model at launch, ahead of Nemotron 3 Ultra (23); ~60% fewer output tokens than its predecessor. `[sourced: https://artificialanalysis.ai/articles/nemotron-3-5-super-and-bolt]`
- Terminal-Bench 2.1 has a harness spread in NVIDIA's own report (62.7%→71.9% with selection), so the number is scaffold-sensitive. `[sourced: https://research.nvidia.com/labs/nemotron/files/NVIDIA-Nemotron-3.5-Super-and-Lightning-Technical-Report.pdf]`
- Speed: up to 5x higher throughput than peers in its class (vendor). `[sourced: https://developer.nvidia.com/blog/nvidia-nemotron-3-5-super-vl-powers-specialized-agents-with-multimodal-reasoning]`

### Known weaknesses / issue reports
- `tau3-bench banking` 18.88 and `BabyVision` 29.6 are weak; multimodal "agentic" strength is uneven by task. `[sourced: https://huggingface.co/nvidia/NVIDIA-Nemotron-3.5-Super-VL-120B-A12B-NVFP4]`
- OpenCode status page on 2026-10-26 showed **increased error rates for `nemotron-3.5-super-vl-free`** on the free inference service. `[sourced: https://status.opencode.ai/]`
- Free-model gateway rejected requests as "abuse traffic" across several free models including this one. `[sourced: https://github.com/anomalyco/opencode/issues/56762]`
- Agent config (`agents.<id>.model`) was ignored for new sessions in a report specifically using this model; a general OpenCode bug, not model-specific. `[sourced: https://github.com/anomalyco/opencode/issues/55727]`

### OpenCode-specific quirks
- Only two of the nine free models accept images: this one and DeepSeek/GLM/MiMo (four total). This is the strongest option on paper for screenshot/GUI tasks. `[sourced: local metadata]`
- Free access is via NVIDIA trial endpoints (logged, no confidential data). `[sourced: https://opencode.ai/docs/zen/]`
- The 8.2K vs 131k output conflict is an OpenCode-specific data quirk worth checking before long generations. `[sourced: https://opencode.ai/data/nvidia/nemotron-3-5-super-vl-120b-a12b]`

**Evidence quality:** good, but free-endpoint stability is currently poor.

---

## 9. `beam-free`

### Identity and specs
- Reflection AI Beam 1.0, 501B total / 23B active sparse MoE, text-only, Apache 2.0; announced 2026-10-05, weights released 2026-10-16. `[sourced: https://reflection.ai/blog/introducing-beam]` `[sourced: https://huggingface.co/reflection/Beam-1.0-501B-A23B]`
- OpenCode quirk: **"Cat Jam Free" was renamed to "Beam Free"** (`beam-free`). `[sourced: https://github.com/anomalyco/opencode/pull/55993]`
- Served free: 1,048,576 context / 131,072 output, text-only, low→max effort. `[sourced: local metadata]`
- Native: 1M context, Apache 2.0, BF16/FP8/NVFP4 weights. `[sourced: https://huggingface.co/blog/reflection-beam]`

### Benchmarks
- Vendor-reported: GPQA Diamond 90.5; SWE-bench Verified 80.9; AIME 2026 97.8; Terminal-Bench 2.1 80.1; MCP Atlas 78.7; DeepSWE v1.1 43.3; tau3-bench 36.1; HLE with tools 51.1. `[sourced: https://www.unite.ai/reflection-releases-beams-open-weights-under-apache-2-0-license]` `[sourced: https://benchlm.ai/models/reflection-beam]`
- Independent: AA Intelligence Index **30**, #2 among US open-weight models at launch, with a **47% hallucination rate** (high) and a net index of 1.5. `[sourced: https://artificialanalysis.ai/articles/reflection-beam]`
- Token efficiency: ~26k output tokens per AA Intelligence Index task, notably fewer than peers. `[sourced: https://artificialanalysis.ai/articles/reflection-beam]` `[sourced: https://venturebeat.com/technology/reflections-beam-aims-to-undercut-chinese-open-models-with-fewer-tokens-not-cheaper-tokens]`
- Speed: up to 223 tok/s per user at 8 concurrent users (vendor/launch coverage). `[sourced: https://x.com/AGTPinsights/status/2111092475528114193]`

### Known weaknesses / issue reports
- **Hallucination 47%** on AA's measurement is the standout weakness; the net score is only slightly positive. `[sourced: https://artificialanalysis.ai/articles/reflection-beam]`
- User field report (X, 2026-10-22): speed was its best feature, but "tool use was too unreliable, especially once a task needed several steps"; best used as a small scouting subagent. Also claims Beam Free disappeared from OpenCode, which conflicts with the still-listed docs — treat the availability claim as unverified. `[sourced: https://x.com/yuhasbeentaken/status/2113355685400322329]`
- tau3-bench 36.1 is modest for agentic tool use. `[sourced: https://benchlm.ai/models/reflection-beam]`
- No independent long-context recall result found. `[guess]`

### OpenCode-specific quirks
- Free provider does not train on your data but retains inputs **30 days** for safety, and the free tier is geo-blocked in a long list of countries (including China, Russia, Iran, etc.). `[sourced: https://opencode.ai/docs/zen/]`
- Breaking-free boundary: docs list it as available on 2026-10-26, so reports of removal are not confirmed. `[sourced: https://opencode.ai/docs/zen/]`

**Evidence quality:** good on benchmarks; tool reliability is the concern, and availability may be volatile.

---

## Cross-model conclusions for the bench

1. **Docs/vendor numbers overstate served capability for three models.** DeepSeek, GLM and MiMo are 1M-native but 200k/32k on the free tier; Super VL has an unresolved 8.2K-vs-131k output discrepancy. Benchmark task D must be sized to 200k, not 1M. `[sourced: local metadata]` `[sourced: https://opencode.ai/data/nvidia/nemotron-3-5-super-vl-120b-a12b]`
2. **Loop/repetition is a known, model-specific failure mode for at least five of nine:** DeepSeek (thinking loop, DSML leakage), GLM (reasoning `!!!!!`/`maybe` loops), MiMo (1.02% identical tool calls under OpenCode), big-pickle (empty-turn loop), Nemotron 3 Ultra (idle timeout/quits). Flint, Lightning and Beam have weaker but nonzero signals. `[sourced: issues cited per model above]`
3. **Tool-call reliability is not predicted by benchmark scores.** DeepSeek and GLM score highest but have the most tool-plumbing bug reports; Beam scores well but users report multi-step tool failure; Flint's only OpenCode tool test showed 41/85 bad calls on a quantized local build. `[sourced: issues cited per model above]`
4. **Context degradation can be much earlier than the advertised window.** big-pickle is the only model with a concrete field measurement (~50k-70k), but the pattern is plausible for the 200k-capped models. `[sourced: https://daniel-tenzler.de/blog/opencodeexperiences]` This is exactly what bench D should measure.
5. **Speed evidence is thin and contradictory.** Only DeepSeek (175 tok/s AA), GLM (73 tok/s AA), MiMo (25-49 tok/s by host) and Beam (~223 tok/s vendor) have usable numbers; Nemotron Ultra's "5x" claim conflicts with field reports of 45+ minute tasks. `[sourced: per-model citations]`
6. **Free-tier operational quirks can dominate model quality:** account-wide rate limits, gateway abuse rejections, NVIDIA endpoint error spikes, and free models that require the OpenCode client. `[sourced: https://github.com/anomalyco/opencode/issues/56523]` `[sourced: https://status.opencode.ai/]`

## What is still missing (and should be treated as unknown)

- **Independent long-context recall** (RULER / AA-LCR / needle-in-haystack) for every model on the *free endpoint*. No per-model independent numbers were found; vendor claims and one field report are all we have. `[guess]` for all unmeasured cases.
- **Tool-call success rates on the actual free endpoints** for Flint, Beam, Super VL and Lightning. Existing evidence is local deployments, vendor harnesses, or unrelated OpenCode issues.
- **Which exact checkpoint each free id serves**: DeepSeek 0910 vs 1017; MiMo base vs MOPD; GLM Flash vs FlashX; and the identity of big-pickle. `[guess]`
- **Published numeric rate limits** (RPM/TPM/RPD) per free model. OpenCode publishes none; only the account-wide behaviour is documented. `[sourced: https://opencode.ai/docs/zen/]`
- **Independent benchmark reproduction for Flint** (all headline numbers are vendor-reported) and for the 1017 DeepSeek refresh. `[sourced: https://shattered.io/recursive-flint-40b-moe-coding-model-2026]`

These gaps are appropriate inputs to ticket 02; nothing here should be treated as a measured bench result.
