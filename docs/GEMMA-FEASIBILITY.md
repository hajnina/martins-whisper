# GEMMA-FEASIBILITY (Ticket G0, ORIN, 2026-10-26)

**Question:** can Gemma 3n E2B do speech-to-text on-device on Android arm64 (POCO F8 Pro 2510DPC44G: Qualcomm Snapdragon 8 Elite SM8750-AB + Adreno 830 + Qualcomm AI engine, LPDDR5X 12 GB, Android 16/HyperOS 3 — device specs sourced [S1])?
**Scope owner:** MFVP §"TWO ENGINES + COMPARE" (`/work/owner/MFVP.md:52-56`); ROADMAP lane 4 gate for B4.
**Marking:** every claim is `[S#]` = sourced (see §Sources), `[G]` = my inference/guess, `[V]` = verified locally by me (curl/git/code read on this machine).

## TL;DR / VERDICT: **GO-WITH-CAVEATS** (details §7)

1. Yes: Gemma 3n E2B does ASR on-device on arm64 via **LiteRT-LM** (Google's official Kotlin runtime, AAR `com.google.ai.edge.litertlm:litertlm-android:0.18.0`), which is exactly what the current Google AI Edge Gallery "Audio Scribe" uses — the code to copy is Apache-2.0. `[S2,S4,S5,S6]`
2. Caveat 1: audio runs on the **CPU backend only** in today's stack for Gemma 3n (code comment: "must be CPU for Gemma 3n"); GPU/NPU acceleration exists in the framework but for Snapdragon NPU today only via **Gemma 4** files. `[V: gallery LlmChatModelHelper.kt:154]` `[S7]`
3. Caveat 2: the model file `gemma-3n-E2B-it-int4.litertlm` (3.66 GB) is **gated on Hugging Face** (HTTP 401 anonymous, login + Gemma-terms accept required). First version imports/adb-pushes the file, per MFVP; in-app official download later. `[V: curl test]` `[S8]`
4. Caveat 3: **llama.cpp cannot run Gemma 3n audio** (mtmd explicitly skips the GEMMA3NV audio projector) — the whisper.cpp-style fallback is off the table for 3n audio on both device and host; host reference must use LiteRT-LM or Gemma 4 GGUF. `[S9]`
5. Caveat 4: expected CPU decode ≈ **16 tok/s** on a comparable Snapdragon flagship (S24 Ultra) → a 10-s dictation transcribes in ~3-6 s. Slower than whisper base CPU; acceptable for MFVP wait tolerance, must be measured on the real phone. `[S5,G]`
6. Ecosystem shift caught during research: Gemma **4** E2B
/E4B `.litertlm` (incl. a `Qualcomm_QCS8275` NPU variant) exist in `litert-community` **ungated** (anonymous download works, HTTP 206 `[V]`) with audio supported in **llama.cpp** too. If Martin's "Gemma E2B" intent is "the best on-device Gemma for ASR today", Gemma 4 E2B is the stronger target; Gemma 3n remains the named MFVP engine. `[S7,S10,S11,V]`

---

## 1. Runtimes that support Gemma 3n AUDIO input today

| Runtime | Gemma 3n audio today? | Coordinate / version | Licence | Notes |
|---|---|---|---|---|
| **LiteRT-LM** (google-ai-edge/LiteRT-LM) | **YES** — official route | `com.google.ai.edge.litertlm:litertlm-android`, latest **0.18.1**, current Google Maven list includes 0.15.0…0.18.1; Gallery pins **0.18.0** | **Apache-2.0** (repo root LICENSE) | Kotlin API (`Engine`, `EngineConfig`, `Backend.{CPU,GPU,NPU,GoogleTensor}`, `Content.AudioBytes`, `Conversation`). Audio fed as WAV bytes; audio encoder runs on `Backend.CPU()`. Ships JNI via the AAR (Gallery does `packaging { jniLibs { pickFirsts.add("**/liblitertlm_jni.so") } }`). App preview status: model card says LiteRT-LM is "in preview". `[S4,S5,S12,V]` |
| **MediaPipe GenAI** (`com.google.mediapipe:tasks-genai`) | YES (this is the **original** Audio Scribe route, Sept 2025: "Gemma 3n, accessible through the MediaPipe LLM Inference API") | latest **0.10.35** on Google Maven (0.10.24 → 0.10.35 range present) | Apache-2.0 `[G]` (same repo family as upstream mediapipe — not re-verified) | Today's Gallery HEAD has migrated the Audio Scribe task to LiteRT-LM; MediaPipe route still works, older AAR. API for audio clips: `LlmInferenceSession` + audio clip input `[G: exact method name not re-verified in this pass]`. `[S2,S13]` |
| **llama.cpp / mtmd** | **NO** for Gemma 3n audio. llama.cpp comment: "we don't support audio for Gemma 3N, but GGUF contains audio tensors … skip_audio = proj_type == PROJECTOR_TYPE_GEMMA3NV". | build from source, or `llama-mtmd-cli` | MIT | **YES for Gemma 4 audio** (PR merged 2026-04-12): `llama-mtmd-cli -hf ggml-org/gemma-4-E2B-it-GGUF --audio file.wav`. Note: `llama-server` OpenAI `input_audio` had a routing gap for Gemma 4 audio (issue #21868, Apr 2026) — use the CLI, not the server. `[S9,S7,S14]` |
| **transformers (HFPy)** | YES (host only; not a phone runtime) | `Gemma3nForConditionalGeneration`, supported from transformers **4.53.0** | per repo, model weights Gemma ToU | Host reference path, slow on CPU. `[S15]` |
| Others | Ollama/MLX serve Gemma 3n variants; not Android-on-device ASR paths we care about. | — | — | `[S16,G]` |

**Conclusion:** the only sensible on-device runtime is LiteRT-LM (Kotlin, one AAR, JNI-free from the app's point of view). MediaPipe GenAI is the fallback if 0.18.x misbehaves. `[S4,V]`

## 2. The open-source reference to COPY: Gallery "Audio Scribe"

Repo: https://github.com/google-ai-edge/gallery — **Apache-2.0** (LICENSE at root) `[S4,V]`. Audio Scribe strings still present in HEAD; the dev blog describes it (Sept 2025): "upload an audio clip or use your device's microphone… Gemma 3n transcribes it directly on your phone". `[S2,V]`

Exact files (verified by reading HEAD today `[V]`):

| File | What to copy / learn |
|---|---|
| `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatTaskModule.kt` | Task `LlmAskAudioTask` (`BuiltInTaskId.LLM_ASK_AUDIO`, `supportAudio = true`, ~:345-364; enablement logic ~:184-198) |
| `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatModelHelper.kt` | Engine creation from LiteRT-LM; **the audio wiring**: `audioBackend = if (shouldEnableAudio) Backend.CPU() else null` with comment "must be CPU for Gemma 3n" (:154); contents assembly: `for (audioClip in audioClips) contents.add(Content.AudioBytes(audioClip))` then "Add text after images/audio to ensure proper autoregressive token sequencing" (:484-492) |
| `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatViewModel.kt` | attach audio as WAV: `attachments.add(Attachment.AudioBytes(audioMessage.genByteArrayForWav()))` (:211); streaming inference plumbing |
| `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/common/chat/ChatMessage.kt` | WAV byte builder (`genByteArrayForWav`): writes PCM-WAV header by hand (header[24..27] = sampleRate) — 16 kHz mono assumed via `Consts.SAMPLE_RATE` |
| `Android/src/app/src/main/java/com/google/ai/edge/gallery/data/Consts.kt` | `MAX_AUDIO_CLIP_DURATION_SEC = 30`, `SAMPLE_RATE = 16000` (:67-70) |
| `Android/src/app/src/main/res/values/strings.xml` | Audio Scribe copy: "limited to **1 clip up to 30 seconds** and type a prompt to transcribe or translate it!" (:43, :908-920) |

- **How it feeds audio:** record/pick clip (16 kHz mono, WAV) → `Content.AudioBytes(wavBytes)` into the LiteRT-LM message contents → stream generation. No custom JNI.
- **Max audio length:** hard 30 s, 1 clip/turn in UI `[V]`; matches the native encoder limit "up to 30 seconds" `[S3]` and 6.25 tokens/s `[S3,S5]`.
- **Prompt used for transcription:** in HEAD the user types the prompt (empty-state says so) and may use a system-prompt repository; the **official ASR prompt to copy** is from Google's own audio-capabilities doc:
  ```
  Transcribe the following speech segment in {LANGUAGE} into {LANGUAGE} text.
  Follow these specific instructions for formatting the answer:
  * Only output the transcription, with no newlines.
  * When transcribing numbers, write the digits …
  ``` `[S6]` `[G: history — the Sept-2025 MediaPipe build used a fixed transcription prompt; not re-verified from that tag]`
- **Model it loads:** `google/gemma-3n-E2B-it-int4.litertlm` (allowlist `model_allowlists/1_0_8.json`: "supports text, vision, and audio input, 4096 context"). `[V,S11]`

## 3. Model files, gating, licence consequences

From HF API (file sizes are exact bytes) `[V: API fetch]`:

| Repo | File | Size |
|---|---|---|
| `google/gemma-3n-E2B-it-litert-lm` *(main)* | `gemma-3n-E2B-it-int4.litertlm` | **3,655,827,456 B ≈ 3.4 GiB** |
| | `gemma-3n-E2B-it-int4-Web.litertlm` | 3,038,117,888 B (purpose unclear — maybe audio-less web preset `[G]`) |
| | `gemma-3n-E2B-it-int4.mediatek.mt6993.litertlm` | 4.33 GB (MediaTek NPU only) |
| `google/gemma-3n-E4B-it-litert-lm` | `gemma-3n-E4B-it-int4.litertlm` | exists on HF (repo referenced by Gallery allowlist; sizes not fetched this pass `[G]`) |
| `litert-community/gemma-4-E2B-it-litert-lm` | `gemma-4-E2B-it.litertlm` | 2,588,147,712 B; GPU variant 2.01 GB; **`gemma-4-E2B-it_Qualcomm_QCS8275.litertlm` 2.72 GB (Qualcomm NPU!)**; Google-Tensor-G5/G6 variants |
| `litert-community/gemma-4-E4B-it-litert-lm` | `gemma-4-E4B-it.litertlm` | 3,659,530,240 B |

- **Gating:** `google/gemma-3n-E2B-it-litert-lm` → anonymous `resolve/main/…` returns **HTTP 401** `[V: curl test]`; HF page: "you have to accept the conditions … logged in to Hugging Face … Requests are processed immediately" `[S8]`. So **no anonymous download, no anonymous mirror shipping** without terms acceptance.
  - **Ungated counter-example:** `litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm` → **HTTP 206 anonymous** `[V]`. The Gemma licence "gemma" is stamped on these repos (model card gemma terms references) `[G]`.
  - **litert-community org contains NO Gemma 3n entries today** (search over the org returned Gemma3-1B, gemma-3-270m, gemma-4-E2B/E4B, embeddinggemma, functiongemma only) `[V]` → the only official download for the 3n litertlm is the gated `google/…` repo; alternates: Kaggle `google/gemma-3n` (same ToU) `[G]`.
- **Gemma Terms of Use consequences** (https://ai.google.dev/gemma/terms) `[V: grep of ToU text]`: "You may reproduce or Distribute copies of Gemma or Model Derivatives **if you meet [the conditions]**"; "you must provide notice to subsequent users you Distribute to that Gemma"/"cause any modified files to carry prominent notices"/"Notice" text file — §3.1 Distribution and Redistribution. Practical rule for us: weights are never bundled in our APK (also 3.4 GB is not APK-friendly); the user either imports the file or (later) downloads it in-app through HF/Kaggle; our UI carries the Gemma notice and points at the official gated HF repo. Deviating mirrors = licence breach; keep MFVP's "official download path stated" honest `[G: interpretation of the clauses above]`.

## 4. Expected speed / RAM on Snapdragon; audio limits; languages

Official LiteRT-LM benchmarks (Gemma-3n-E2B, int4, from the HF model card) `[S5]`:

| Device | Backend | Prefill tok/s | Decode tok/s |
|---|---|---|---|
| MacBook M3 | CPU (XNNPACK, 4 thr) | 232.5 | 27.6 |
| Samsung S24 Ultra (Snapdragon 8 Gen 3-class) | CPU | 110.5 | **16.1** |
| Samsung S24 Ultra | GPU | 816.4 | 15.6 |
| Vivo X300 Pro | NPU | 1671.3 | 28.4 |

- POCO F8 Pro = Snapdragon 8 Elite + Adreno 830 + Qualcomm AI engine `[S1]`; I expect CPU decode in the S24U ballpark (~16 tok/s) or better `[G]`. Dictation math `[G]`: 10 s speech → ~63 audio tokens (6.25/s) `[S3,S5]` + ASCII prompt ≈ 100-200 prefill tokens (ms-scale at CPU prefill) → output ~40-80 tokens → ≈ **3-6 s** decode on CPU. Compare against MFVP aim "~2 s" for whisper base — **Gemma-CPU is the slower engine; be honest in the Compare cards**. NPU variant for this SoC = Gemma 4 files (see above) — B5 territory. `[G]`
- **RAM:** the 3.4 GiB file → runtime footprint ~**4-4.5 GB** with weights loaded + activations + modest KV `[G: extrapolated; measure]`. Device RAM 11-12 GB LPDDR5X `[S1,E brief]` → fits, but do not run it resident next to a large whisper model; unload idle engines (already a MFVP failure-mode).
- **Audio limits:** 16 kHz, mono, 32-bit float normalized; **max 30 s**; 6.25 tokens/s (≈188 tokens/30 s). `[S3,S6,S5,V: consts]`
- **Languages:** Gemma 3n trained on 140+ text languages; Audio: ASR + speech→text translation (AST) trained. The canonical audio **ASR language list is not enumerated in the docs I read** — the docs say "transcribe audio input in **various** languages into text" `[S6,G]`. Gemini-model-card style coverage (~24 audio languages at launch for 3n `[G]`); **Czech inclusion is unverified → must be measured** (same WER-fixture method as the whisper table, MFVP acceptance §languages). Honest fallback: keep whisper as default engine per language where Gemma WER is bad.

## 5. Host reference transcriber: Gemma E4B (or E2B) on aarch64 Linux, no GPU

Host measured right now: aarch64, 8 cores, 30 GB RAM (≈9-10 GB free) `[V: free/uname]`. Options, fastest-to-standing first:

1. **llama.cpp mtmd + Gemma 4 GGUF** (pragmatic): `llama-mtmd-cli -hf ggml-org/gemma-4-E4B-it-GGUF --audio clip.wav -p "<official ASR prompt>"` — audio+vision supported since Apr 2026 `[S7,S14]`. Q4 GGUF ≈ 3-5 GB file, few GB RAM, minutes-level host parity for WER tables. **Caveat: these are Gemma 4 weights, not the phone's Gemma 3n E2B** — a *family* reference, not file-for-file parity. `[S14,G]`
2. **LiteRT-LM host serve = full file parity with the phone** (nice for MFVP's "re-check sidecar results on the host"): `pip install litert-lm` → `litert-lm run --from-huggingface-repo=…` works for text `[S5]`; the OpenAI-compatible `serve` CLI parses `{"type":"input_audio","input_audio":{"data":<base64>}}` parts (code verified in repo) → POST the same WAV to the host server. Needs one verification session (CPU backend on linux-aarch64). `[V: python/litert_lm_cli/commands/serve/openai_common.py:357,475-480]` `[G: end-to-end audio on host]`
3. **transformers ≥ 4.53** + `google/gemma-3n-E4B-it`: exact 3n parity, but bf16 E-total params (~8 B) → ~15-20 GB RAM and slow CPU decode; only if 1-2 fail. `[S15,G]`

Recommendation: **(1) for the nightly WER/WER-min tables, (2) as the exact-parity recheck** for COMPARE sidecars. Both under Gemma ToU (licence-accept + notice) and fine on a 30 GB host.

## 6. Minimal integration plan for our SpeechEngine contract

Constraint anchors: same `SpeechEngine` interface as NORA's whisper engine (one buffer → text + timings + error), EngineRegistry + per-dictation choice + COMPARE runs both sequentially and stores engine id + timings in sidecar json (MFVP §TWO ENGINES). JNI-free: yes — LiteRT-LM is a Kotlin API shipped as an AAR; only dimension: add the dep and the `pickFirsts` line. `[S4,S12,V]`

Plan (sizes = APK-relevant bits):
1. **Gradle:** `libs.versions.toml`: `litertlm = { group = "com.google.ai.edge.litertlm", name = "litertlm-android", version = "0.18.0" }` + in the engine module `implementation(libs.litertlm)`; in packaging: `jniLibs { pickFirsts.add("**/liblitertlm_jni.so") }` (Gallery's exact solution). Measured AAR today: `classes.jar` = **188 KB**; the native `.so"s live in the AAR's `jni/` arm64 split `[G: .so total ~10-40 MB; measure from the AAR in the build step — not sized this pass]`. No NDK changes, no CMake, no new Gradle plugin. `[V,S4,S12]`
2. **GemmaEngine** (lane-4 `engines1` code, GEMI's; ORIN hands over this doc): `init(modelPath)`: `Engine(EngineConfig(..., backend = per-accelerator, audioBackend = Backend.CPU()))`; `transcribe(pcm)` → wrap 16 kHz mono float PCM in WAV bytes (copy Gallery's `genByteArrayForWav` pattern, Apache-2.0 header intact) → contents = [AudioBytes, ASR-prompt text (official template, `{LANGUAGE}` = user's chosen language; **explicit language, no auto** per MFVP)] → generate with `maxOutputTokens` cap + temp guard → trim/normalise → `EngineResult(text, loadMs, inferMs, rtf, error)`. Keep 30-s cap: our single-shot tap-to-tap flow is ≤ ~30 s by design (MFVP §ACCEPTANCE #1).
3. **Model lifecycle:** no weights in APK/repo (ToU + size). v1: import file / `adb push` (MFVP already has import machinery for whisper); UI registers `.litertlm` files by name → EngineRegistry maps `gemma-3n-E2B-it-int4.litertlm` → GemmaEngine. Official-download caveat card in onboarding: "model is gated on Hugging Face: sign in, accept Gemma terms, download 3.4 GB file" (or offer the **ungated Gemma 4 E2B file as an alternative download** `[G: engine-compat check first]`).
4. **COMPARE wiring:** both engines on the SAME recorded buffer, sequential; store `engineId, loadMs, inferMs, rtf` in sidecar json + telemetry (already specced); practice run in onboarding picks the winner → persisted default.
5. **Failure modes to carry over from whisper lane:** silence-hallucination gate (LLM ASR produces the same "Thank you." class of artefacts — same VAD+strip pipeline applies `[G]`), idle-unload race serialized on the real path (litertlm calls are behind our Kotlin engine; same discipline) `[G]`, UTF-8 out is plain Kotlin strings (no NewStringUTF hazard on this path `[V: Kotlin API]`).

## 7. GO / NO-GO / GO-WITH-CAVEATS + smallest first milestone

**VERDICT: GO-WITH-CAVEATS** (with whisper privileged until Gemma is proven on-device; Compare flow ships with whisper only until then).

Caveats (each with an owner-visible consequence):
1. **Model gating** → no in-app official download in v1 (import/adb push; MFVP already plans this). `[S8,V]`
2. **CPU-only audio for Gemma 3n** → slower engine; honest cards; Snapdragon-NPU via Gemma 4 files is a B5 follow-up. `[V,S7,S10]`
3. **llama.cpp parity absent for 3n audio** → host reference uses Gemma 4 GGUF (or exactly-parity LiteRT-LM host serve) instead of the whisper.cpp-style native build. `[S7,S9]`
4. **Speed risk** ~3-6 s / 10-s clip on CPU `[G,S5]` and **language coverage for ASR unverified** → measure before the onboarding "winner" cards claim anything. `[S6,G]`
5. **Licence hygiene:** weights never entered the APK; Gemma notice shipped; accept-terms flow spelled out. `[V: ToU grep]`

**Smallest first milestone (M-G0, ~1 session):**
Owner: accept Gemma terms on HF with the owner account, download `google/gemma-3n-E2B-it-int4.litertlm` (3.4 GiB) to the device/host.
ORIN: (a) `pip install litert-lm`; run E2B on this host; (b) transcribe jfk.wav + 3 short Czech phrase clips via the serve/input_audio path; record loadMs/inferMs/RTF/WER into `research/GEMMA-BENCH-0.md`.
Then in-app (B4): add the AAR behind a flag + fake→real GemmaEngine + one device transcription of jfk.wav on the POCO with the same measurements. Stop until number verify.

## Sources

- [S1] POCO F8 Pro (2510DPC44G) specs — Snapdragon 8 Elite SM8750-AB, Adreno 830, LPDDR5X 12 GB, Android 16/HyperOS 3: https://www.po.co/global/product/poco-f8-pro/specs + https://www.mi.com/global/support/faq/details/KA-628083 + https://www.gsmchoice.com/en/catalogue/poco/f8-pro/
- [S2] Audio Scribe announcement (Gemma 3n via MediaPipe LLM Inference API, upload/mic, on-device): https://developers.googleblog.com/google-ai-edge-gallery-now-with-audio-and-on-google-play
- [S3] Gemma 3n audio: 30-s clip limit at launch, USM streaming encoder, 6.25 tok/s: https://developers.googleblog.com/introducing-gemma-3n-developer-guide
- [S4] google-ai-edge/gallery (Apache-2.0, Audio Scribe in README/assets): https://github.com/google-ai-edge/gallery + `LICENSE` at root + HEAD files table in §2 `[V]`
- [S5] `google/gemma-3n-E2B-it-litert-lm` model card: LiteRT-LM benchmarks (S24U CPU/GPU, X300 Pro NPU, M3), 6.25 tok/s audio, 32k ctx, CLI usage (`litert-lm run`): https://huggingface.co/google/gemma-3n-E2B-it-litert-lm (+ HF gated-accept notice)
- [S6] ASR/AST prompt templates, 16 kHz/mono/32-bit float, 30-s max clip, 6.25 tok/s (Gemma 3n): https://ai.google.dev/gemma/docs/capabilities/audio (+ https://ai.google.dev/gemma/docs/core if doc migrated to Gemma 4)
- [S7] llama.cpp Gemma 4 audio support (12-layer USM-style conformer; `llama-mtmd-cli -hf ggml-org/gemma-4-E2B-it-GGUF --audio …`) + `PROJECTOR_TYPE_GEMMA3NV skip_audio` (3n audio NOT supported): https://github.com/ggml-org/llama.cpp/pull/21421
- [S8] HF gating: repo "publicly accessible, but you have to accept the conditions… logged in" + `[V]` anonymous resolve = HTTP 401: https://huggingface.co/google/gemma-3n-E2B-it-litert-lm
- [S9] llama.cpp multimodal doc (audio+image+video via libmtmd; model list): https://github.com/ggml-org/llama.cpp/blob/master/docs/multimodal.md
- [S10] `litert-community/gemma-4-E2B-it-litert-lm` file list (incl. `gemma-4-E2B-it_Qualcomm_QCS8275.litertlm`): https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm (`[V]` anonymous HTTP 206)
- [S11] Gallery model allowlist (gemma-3n E2B/E4B int4 `.litertlm`, "supports text, vision, and audio input", 4096 ctx): https://github.com/google-ai-edge/gallery/blob/main/model_allowlists/1_0_8.json `[V]`
- [S12] LiteRT-LM (Apache-2.0 asp; Kotlin README/android): https://github.com/google-ai-edge/LiteRT-LM + Google Maven metadata https://dl.google.com/dl/android/maven2/com/google/ai/edge/litertlm/litertlm-android/maven-metadata.xml `[V]`
- [S13] MediaPipe GenAI AAR versions: https://dl.google.com/dl/android/maven2/com/google/mediapipe/tasks-genai/maven-metadata.xml `[V]`
- [S14] Gemma-4 GGUF model usage w/ mmproj + `--audio`: https://github.com/ggml-org/llama.cpp/blob/master/docs/multimodal.md | server input_audio gap: https://github.com/ggml-org/llama.cpp/issues/21868
- [S15] transformers Gemma 3n audio support (from 4.53.0) + audio WER finetuning examples: https://huggingface.co/EpistemeAI/Audiogemma-3N-finetune (README) / https://huggingface.co/google/gemma-3n-E2B-it
- [S16] Ollama/MLX paths (context only): https://ollama.com/library/gemma3n
- [V-appendix] Gemma ToU §3.1 (Distribute if you meet [conditions]; notice to recipients; Notice text file): https://ai.google.dev/gemma/terms — verified by text grep at 2026-10-26.
