# R1 Research Handoff — Android Prior Art

## Executive summary

R1 recommends **Option A for ADR-004**: vendor MIT-licensed whisper.cpp, build one arm64-v8a library with both Vulkan and CPU, default to `auto`, fall back to CPU, and show the active backend. The prebuilt sherpa-onnx AAR is a viable emergency fallback, but its GPU path is NNAPI, which Android deprecated in Android 15. Keep SPEC’s overlay shape (`TYPE_APPLICATION_OVERLAY` + microphone FGS + separate AccessibilityService). Insert with `ACTION_SET_TEXT` first, verify by read-back, then custom paste/`ACTION_PASTE`, then clipboard + notification; never write to password fields. Implement the 300 s idle unload with a single ASR dispatcher and leases, because an accessibility-bound process may never receive `onTrimMemory(BACKGROUND)`. Vulkan is buildable but device-dependent (Pixel 8: 73 s -> 67 s for 30 s audio; Adreno 640 fails 16-bit storage; Adreno 840 + Turnip showed 4.3x), so do not block v1 on GPU. PM must decide whether v1 supports GGML-only custom import (recommended) or also ONNX, which implies a second engine. No APK/device validation was possible in this sandbox.

## What was built / changed

Research only; no production code and no changes to `core/` contracts.

- `research/ANDROID_PRIOR_ART.md` (456 lines): engine comparison, native Vulkan build feasibility, overlay/FGS rules, accessibility focus/insertion design, VAD/chunking, ModelManager invariants, failure/privacy matrix, testing plan, code-reuse/license map, model sizes/SHA-256, source line map.
- `REFERENCES.md`: replaced the R1 TBD section with 18 concrete sources (repos, issues, Android docs, HF weights), each with what was taken and where it landed; refreshed technical notes.
- `handoffs/R1-research/HANDOFF.md` (this file) and `SUMMARY.txt`.

Repos cloned read-only under `/work/references/repos-android/` (not committed):
`futo-voice-input d6e1eb2`, `whisper.cpp 231638a`, `sherpa-onnx af2d8d8`, `phone-whisper e6518cd`, `whisperclient-android e25e063`, `flo fd5f102`, `ramblr 10b3834`.

## How to run/validate

There is no build or test suite in R1. The report is self-contained; run these checks when reviewing:

```bash
cd /work/martins-whisper-worktrees/r1-research
git diff --check
git show --stat a44964f
grep -n "Option A\|ADR-004\|ACTION_SET_TEXT\|300 s\|Vulkan" research/ANDROID_PRIOR_ART.md | head
```

To reproduce the evidence (network required):

```bash
curl -sL 'https://huggingface.co/api/models/ggerganov/whisper.cpp?blobs=true' -o /tmp/hf.json
jq -r '.siblings[] | select(.rfilename|test("^ggml-(tiny|base|small)(\\.en)?\\.bin$")) | [.rfilename,.size,.lfs.sha256] | @tsv' /tmp/hf.json

curl -sL 'https://api.github.com/repos/k2-fsa/sherpa-onnx/releases/latest' -o /tmp/sherpa.json
jq -r '.tag_name, (.assets[] | select(.name|test("android|\\.aar$")) | [.name,.size] | @tsv)' /tmp/sherpa.json

curl -sL -o /tmp/sherpa.aar 'https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.0/sherpa-onnx-2.0.0.aar'
unzip -l /tmp/sherpa.aar | grep -E '\\.so|classes.jar'
```

Expected key results (captured): `ggml-base.bin` = 147,951,465 B, SHA-256 `60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe`; `sherpa-onnx-2.0.0.aar` = 49.2 MB with arm64 `libonnxruntime.so` 22,249,560 B + `libsherpa-onnx-jni.so` 4,555,952 B; latest sherpa release `v2.0.0` at research time (`v2.0.1` AAR URL returned 404).

## Interfaces / invariants / contract notes

- **ADR-004 recommendation (not yet accepted):** Option A = whisper.cpp Vulkan+CPU in one arm64 library, `auto` backend with CPU fallback; keep `SpeechEngine` seam; do not implement Option B in v1.
- **Backend contract:** `SpeechEngine.backend` should report `whisper.cpp/vulkan (<device>)` or `whisper.cpp/cpu`; persist a CPU fallback reason and expose it in doctor/settings.
- **Insertion contract (matches ADR-003):** direct `ACTION_SET_TEXT` with composed full text -> read-back verification -> custom labelled paste action -> `ACTION_PASTE` -> clipboard + notification/toast. `ACTION_SET_TEXT` returning true is not proof; refresh/read back. Never write password/PIN fields; discard the result. Do not auto-clear clipboard.
- **ModelManager invariants:** single ASR dispatcher; lease/refcount around inference; lazy load; unload after 300 s idle only when no lease; unload off-main; model switch waits for in-flight work; no use-after-close.
- **Proposed contract change (needs PM):** SPEC R4 says “custom GGML/ONNX model file”. Option A natively supports GGML; ONNX import needs a second engine. Recommend v1 GGML-only and document ONNX as follow-up. No files were edited to make this change.
- **Boot/FGS caveat:** start the microphone FGS from a visible Activity and keep it alive. Android 14+ restricts microphone FGS from `BOOT_COMPLETED`; Android 15 requires SAW + a currently visible overlay for the SAW background-start exemption. Prefer a “tap to re-arm” notification after reboot unless U1 verifies the overlay-first flow on device.

## Evidence

- whisper.cpp Android example is CPU-only (`GGML_USE_CPU`); `GGML_VULKAN` exists in `ggml/CMakeLists.txt:224` and the Vulkan CMake requires host `glslc` + SPIRV-Headers. Community Android recipe and caveats: issue #2370.
- GPU data points: issue #2370 Pixel 8 30 s audio CPU 73 s vs Vulkan 67 s; issue #2706 Adreno 640 “does not support 16-bit storage” -> `Unsupported device`; discussion #3943 Adreno 840 + Turnip 120 s small model 27.8 s vs 120.8 s (~4.3x), stock driver cm2 pipeline failures.
- `ggml_backend_vk_reg()` catches Vulkan init exceptions and returns null, so unsupported backends can be skipped; a driver SIGSEGV is still fatal (motivates crash counter/CPU fallback).
- sherpa AAR is 49.2 MB, includes all four ABIs; `provider.h` has no Vulkan provider; NNAPI is API 27+ and deprecated in Android 15 (official docs recommend CPU or TF Lite GPU for performance).
- Futo closes the model after every inference (`AudioRecognizer.kt:523`, `:538`) and uses a patched `partial_text_callback`; upstream whisper.cpp only exposes `new_segment_callback` (`include/whisper.h:463`). Futo also uses `audio_ctx = min(1500, ceil(samples/320)+32)` for short holds.
- Ramble implements exactly `TRANSCRIBER_IDLE_UNLOAD_MS = 5 * 60 * 1000L` (`DictationRuntime.kt:231`) with generation-guarded release (`:602`), VAD max segment 15 s (`SherpaVadHandle.kt:36`), direct-before-paste/read-back insertion (`TextDestination.kt:232`), and password guard (`PasswordFieldGuard.kt:37`). GPLv3: design only.
- Android docs: untrusted touch on Android 12 (TYPE_APPLICATION_OVERLAY not trusted; accessibility/IME trusted; 0.8 opacity), FGS microphone/while-in-use restrictions, Play accessibility declaration.
- Model sizes/SHA-256 for tiny/base/small/medium/large-v3-turbo are in report §3.5. Quantized base q5_1/q8_0 are 59.7/81.8 MB.
- Nix feasibility checked, not built: `nix eval nixpkgs#androidndk` -> 27.0.12077973; `nixpkgs#shaderc` -> 2026.1; `nixpkgs#vulkan-headers` -> 1.4.363.0.

## Known limitations / blockers

- No Android SDK/NDK build, no APK, no emulator, no device, no GPU, no mic/audio in this sandbox. Vulkan feasibility is from upstream/community evidence, not a local build.
- Vulkan speed is device/driver dependent; stock Adreno/Mali behavior may differ from the Turnip benchmark. H1/H2 must measure.
- Accessibility field matrix is untested on device; WebView/Compose/Terminal behavior is based on source reading, not live tests.
- Boot-time microphone FGS behavior needs Android 14/15 device validation; the report gives a safe notification-based fallback.
- Licenses: Futo (non-commercial), Ramble (GPLv3), Whisperclient and Flo (no license) are design references only. Do not copy their code. whisper.cpp (MIT), sherpa-onnx/phone-whisper (Apache-2.0) are reusable with notices.
- `sherpa-onnx` README references `v2.0.1` while the latest published release was `v2.0.0` on 2026-10-26; re-check before pinning.
- The report does not modify `DECISIONS.md`; ADR-004 remains open until PM accepts it.

## What the next agent should do

- **PM:** accept/reject ADR-004 Option A; decide GGML-only vs GGML+ONNX for v1; decide reboot behavior (notification vs overlay-first FGS); provide a target device/SoC for H1/H2.
- **E1:** vendor whisper.cpp at the pinned commit; get CPU build green first; then add Vulkan using issue #2370/PR #2914 as a starting point, host `glslc` from nix `shaderc`, minSdk 29, `COOPMAT2=OFF`, whole-archive Vulkan objects. Implement `WhisperCppEngine` with mmap load, `audio_ctx` proportional to chunk length, CPU fallback + backend persistence, and `ModelManager` leases/idle unload. Add JVM tests for lifecycle; device GPU validation is H2.
- **I1:** implement the insertion state machine with pure `composeOneShotInjection`, read-back, custom paste action, password guard, candidate scoring, and clipboard/notification recovery. Write Robolectric tests for event reduction and pure tests for composition.
- **U1:** implement `TYPE_APPLICATION_OVERLAY` + `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_NO_LIMITS`, microphone FGS from visible Activity, notification, drag/persist, and the boot “tap to re-arm” fallback. Do not set `FLAG_NOT_TOUCHABLE` on the bubble.
- **C1:** implement SPEC’s energy VAD/chunker/merger exactly; keep it swappable for Silero later.
- **B1:** prove NDK/CMake/Gradle on aarch64 with nix `androidndk`, `shaderc`, and a Vulkan config shim; report exact commands and build logs.

## References used

Full list with links and landing sections is in `REFERENCES.md`. Primary sources:
`futo-org/voice-input`, `ggml-org/whisper.cpp` and issues #2370/#2706/discussion #3943/PR #2914, `k2-fsa/sherpa-onnx`, `kafkasl/phone-whisper`, `nihal111/WhisperClientAndroid`, `zackify/flo`, `trevornk/ramblr`, Android developer docs (untrusted touch, FGS types/restrictions, NNAPI deprecation, Play accessibility policy), and Hugging Face `ggerganov/whisper.cpp`.

## Commits

- Report + references: `a44964f` — `docs(research): Android dictation prior art and ADR-004 recommendation`.
- Handoff + summary: see the commit containing this file (`git log -1 --oneline`).
