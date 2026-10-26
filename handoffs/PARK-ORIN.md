# PARK-ORIN (2026-10-26 ~17:25Z)

## NEW TICKET done (voice-UX research, 2026-10-26 ~23:4x Z) — LANDING
- Branch `orin/ux-research` @ `b20ee12`: `docs/ux/VOICE-UI-RESEARCH.md` (89 lines, sourced+guess-labelled: 100 ms instantaneous/200 ms control latency thresholds NN/g+CHI26, four-state listening/hearing-ME/working/succeeded model, honest audio-feature->visual/haptic mappings RMS=squares+voicing=colour+onsets=pulse, decorative-not-signal-driven animation erodes trust incl. Apple waveform-not-words evidence, Siri/Gemini/Alexa/Assistant/Yap/Otter survey, reduce-motion/TalkBack/haptics accessibility, 12-item checklist).
- `land.sh --cloud` RUNNING in background (log: `/work/orin/wt-ux/land-ux-research.log`, cloud branch `ci/orin-ux-research-b20ee126`) -> lands to master when green. On land: slice done.
- **TO MOTIF (via LENA/park reads)**: your PR must prove checklist items 1-4 of `docs/ux/VOICE-UI-RESEARCH.md` §6 (real-RMS-driven bars, distinct working state, no idle motion, no content-implying waveform); name states exactly idle/recording/working per doc §2 table.
- **TO LENA**: doc §6 checklist is your acceptance bar; reduce-motion + TalkBack content descriptions are non-negotiable (§5); the 4-moment mapping table in §2 is the bubble anatomy spec.


## G0 Gemma feasibility (2026-10-26 ~22:5x Z) — DONE
- Branch `agent/gemma-research` @ `df953e1`: `docs/GEMMA-FEASIBILITY.md` (owner brief ticket G0). Verdict: GO-WITH-CAVEATS; smallest milestone = host litert-lm spike (E2B + jfk.wav + 3 Czech fixtures, RTF table) then in-app AAR behind flag. Key: 3n audio = LiteRT-LM only (Audio Scribe = code to copy, Apache-2.0); llama.cpp CANNOT run 3n audio; `google/gemma-3n-*-int4.litertlm` gated (401 anon) vs `litert-community/gemma-4-*` UNgated (206 anon, incl. Qualcomm QCS8275 NPU file).
- Needs FROM OWNER: accept Gemma terms on HF (owner account), obtain the 3.4 GiB file. TO GEMI: copied files table is in doc §2; integration plan §6. tests: n/a (docs only).

## DONE (committed)
- `research/model-bench` @ `4fbf306` — recovered PM bench results (`research/model-bench/results-existing.{md,json}`), analyzer (`analyze.js`), prompts/models, recovered 93 run logs under `research/model-bench/runs`.
- `master` @ `75c8849` — `wip: park` committed the PM's pending `agents/prompts/G1-integration.md`.
- Outside git (persists in `/work/orin`): full bench harness `bench/` (200-file ~139k-token pile, A repo with planted bug, `run_one.sh`, `verify_A.sh`, `drive-missing.sh`), own gradle home `gh/`, `specs.tsv`, `REFERENCES.md`, `NOTES.md`.
- Measured (recovered, PM runs): deepseek-v4.1-flash-free 4/4 pass; glm-5.3-flash-free 4/4; big-pickle 4/4; mimo-v2.6-flash-free A/B/D pass, C disputed (PM says pass, naive analyzer says 5/8 headings — manual check needed); flint-40b-free A pass, B/C/D incomplete or provider-failed; nemotron-3-ultra-free A `Model is unavailable` (400), B/C/D unrun; lightning/super-vl/beam A pass, B/C/D unrun.
- Specs + web research captured (`/work/orin/specs.tsv`, `REFERENCES.md`).

## RUNNING at park (killed, no scorable output)
- Ticket 2 (free-model bench), branch `research/model-bench`, B-research task for: `beam-free`, `nemotron-3.5-lightning-free`, `nemotron-3.5-super-vl-free`, `flint-40b-free`.
- Started 17:23:06Z, killed ~17:24Z; their `*-B.jsonl` are 0 bytes. Do not score.

## NEXT (ordered, small)
1. PARK-2.1: when `/work/.yoloseek/PROVIDER-STATUS.md` = OK, run `/work/orin/bench/drive-missing.sh` (16 cells: beam/lightning/super-vl/flint B,C,D; nemotron-3-ultra A,B,C,D). Max 4 concurrent; re-check gate before each batch.
2. PARK-2.2: verify nemotron A with `bench/verify_A.sh nemotron-3-ultra-free` (own `GRADLE_USER_HOME=/work/orin/gh`; retry cap 2).
3. PARK-2.3: analyze new logs; manually grade C headings and D canary; classify provider 503 / `Model is unavailable` as blocked, not model failure.
4. PARK-2.4: write `/work/martins-whisper/research/MODEL-BENCH.md` with **proposed mix first**, evidence table, per-model strengths/weaknesses + sources, blocked cells; commit on `research/model-bench`.
5. PARK-2.5: send owner 5-line status; append short lessons to `MODEL-BENCH.md`.

## Traps learned
- Free tier is session/account-limited: gate every launch on `PROVIDER-STATUS.md`; 4 workers max; `Free usage exceeded` is provider, not model.
- Never `pkill -f 'opencode run ...'` (matches own shell). Use `ps -eo pid,args` then `kill <pid>`.
- C grading by exact heading strings gives false negatives (mimo wrote all 8 sections with variant headings). Read final HANDOFF content manually.
- flint C was provider 503; nemotron-3-ultra A was `Model is unavailable` (400) — both retryable/infra.
- Shared gradle cache lacks `gson:2.8.9` for offline: use own `/work/orin/gh` and run once without `--offline`.
- D pile is `/work/orin/bench/pile` (not PM's dead `/home/node/bench`); canary is in `repo-dump-0147.txt`; no python3 installed.
- Do not modify app code, `KANBAN.md`, `master`, or `/work/android-toolchain/gradle-home`.
