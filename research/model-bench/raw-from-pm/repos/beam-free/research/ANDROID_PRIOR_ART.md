# Android Prior Art: Local Hold-to-Talk Dictation

Status: R1 research complete (2026-10-26). No APK was built and no Android device was available in this sandbox.
Scope: engine choice, native build path, overlay/foreground-service shape, accessibility focus + text insertion, VAD/chunking, model lifecycle, error and privacy behavior.
Related: `SPEC.md` v2, `DECISIONS.md` ADR-002/003/004/005/006, `KANBAN.md` R1/E1/I1/U1/C1/B1.

## 0. Executive summary

**Engine: adopt Option A for v1 — vendor whisper.cpp (MIT), build one arm64-v8a APK with both Vulkan and CPU, default to `auto`, fall back to CPU, and surface the active backend.** Whisper.cpp is the only studied option that gives direct GGML model support, quantized models, Vulkan, and per-call control over language/audio context on Android. The prebuilt sherpa-onnx AAR is easier to build but its GPU story is NNAPI, which Android deprecated in Android 15; it is a useful emergency fallback, not the primary engine.

**Do not make “GPU” a release blocker.** Public evidence is highly device-dependent: a Pixel 8 community build went from 73 s to 67 s for 30 s of audio with Vulkan, an Adreno 640 failed Vulkan init entirely because it lacks 16-bit storage, and only a Turnip/Adreno 840 setup showed a 4.3x win. Ship CPU-first if the Vulkan build is not green by integration; keep the `SpeechEngine` seam so Vulkan can be enabled/disabled per device and per release.

**Overlay: keep the SPEC shape — `TYPE_APPLICATION_OVERLAY` owned by a microphone foreground service, plus a separate accessibility service.** `TYPE_ACCESSIBILITY_OVERLAY` avoids `SYSTEM_ALERT_WINDOW` but couples the bubble lifetime to the accessibility service; it is the right fallback if PM rejects the draw-over permission, not the default.

**Insertion: direct write first, paste second, clipboard+notification last, with read-back verification.** `ACTION_SET_TEXT` can return `true` and still not apply. Compose full replacement text from the live node text + selection, call `ACTION_FOCUS`, write, read back, nudge the caret to the end, then fall back to a custom paste action or `ACTION_PASTE`. Never write to password/PIN fields; discard the dictation instead. Keep the clipboard copy; do not auto-clear it.

**Model lifecycle: `ModelManager` with a single ASR dispatcher, lease/refcount around inference, lazy load, and a 300 s idle unload.** The accessibility-bound process can stay alive indefinitely and may never receive `onTrimMemory(BACKGROUND)`, so a timer is mandatory, not an optimization.

**Open decision for PM:** Option A means v1 imports GGML models only. “Custom GGML/ONNX import” in SPEC R4 would require a second engine (or an ONNX-only fallback); recommend narrowing v1 to GGML and documenting ONNX as a follow-up. This is a contract change and needs PM approval, not a silent edit.

## 1. Requirements this research answers

| Requirement | Answer from prior art | Confidence |
|---|---|---|
| R2 insert at cursor/selection | Accessibility direct-write + paste fallback is proven by 4 apps; field matrix still needs device testing | High |
| R3 local-only | whisper.cpp and sherpa-onnx both run entirely on-device; no network needed after model download | High |
| R4 default base, configurable | GGML base is 147,951,465 B; tiny/small/medium/large-v3-turbo all available; quantized variants cut size | High |
| R5 GPU by default, CPU fallback | Vulkan is feasible and auto-fallback exists, but real speedups vary by SoC/driver | Medium |
| R6 idle unload 300 s | Ramble implements this exact policy; Futo instead frees after every inference | High |
| R7 VAD chunking + incremental decode | Ramble uses Silero + 15 s segments; Futo uses energy/VAD in the audio loop; SPEC’s pure-Kotlin energy VAD is sufficient for v1 | High |
| R8 robust failure | Password guard, read-back, clipboard recovery, model checksum, idle unload all have precedents | High |
| R9 testable core | Insertion composer and model manager are pure/testable; candidate scoring can be pure | High |

## 2. Repositories studied

All repos were cloned read-only under `/work/references/repos-android/`; nothing from them is committed to this worktree.

| Repo | Commit | License | minSdk | Role | Reuse posture |
|---|---|---|---|---|---|
| `futo-org/voice-input` | `d6e1eb2` (2026-05-01) | FUTO Source First 1.0 (non-commercial) | 24 | whisper.cpp Android app, model catalog/download, VAD, IME + speech intent | **Design reference only. Do not copy code into this project.** |
| `ggml-org/whisper.cpp` | `231638a` (2026-10-19) | MIT | 26 (example) | Upstream C/C++ engine, JNI example, CMake, Vulkan backend | **Copy/adapt freely with MIT notice.** |
| `k2-fsa/sherpa-onnx` | `af2d8d8` (2026-10-26) | Apache-2.0 | 21 (AAR) | Prebuilt AAR, Kotlin API, Whisper ONNX, Silero VAD, NNAPI | Adapt with Apache notice if Option B is used. |
| `kafkasl/phone-whisper` | `e6518cd` (2026-08-24) | Apache-2.0 | 30 | Overlay inside accessibility service, sherpa-onnx local model, insertion order | Structure/ideas can be adapted with notice. |
| `nihal111/WhisperClientAndroid` | `e25e063` (2026-04-25) | **No license file found** | 29 | Foreground overlay service, focus-event evaluation, bubble policy, insertion tests | **All rights reserved by default. Do not copy.** |
| `zackify/flo` | `fd5f102` (2026-03-27) | **No license file found** | 26 | Minimal floating bubble + accessibility + Wyoming client | **All rights reserved by default. Do not copy.** |
| `trevornk/ramblr` | `10b3834` (2026-10-24) | GPLv3 | 30 | Richest reference: idle unload, VAD segmentation, injection hardening, password guard | **GPLv3 is viral. Design reference only; do not copy into a non-GPL app.** |

License findings:
- Whisper weights (`ggerganov/whisper.cpp` on Hugging Face) are MIT; OpenAI Whisper is MIT.
- Futo’s `LICENSE.md` is a source-first non-commercial license: use for personal/research, distribution free of charge, no commercial use. Treat it as read-only even if this app is currently sideloaded.
- `phone-whisper` ships Apache-2.0; `sherpa-onnx` and `whisper.cpp` are permissive.
- `whisperclient-android` and `flo` have no `LICENSE`/`COPYING` file in the cloned trees. Absence of a license means no reuse permission; read for behavior only.
- Ramble’s `NOTICE` confirms GPLv3. Copying any of its Kotlin would force GPLv3 on this app; use it to validate behavior, then write independent code.

## 3. Engine decision (ADR-004)

### 3.1 whisper.cpp: what it gives us

- MIT-licensed C/C++ library with an Android JNI example (`examples/whisper.android`) and a Java example.
- GGML/GGUF model files; official base multilingual is 147,951,465 B. Quantized variants exist (`q5_1`, `q8_0`) and are much smaller.
- `whisper_full_params` exposes the controls this product needs: `language`, `initial_prompt`, `n_threads`, `audio_ctx`, `no_timestamps`, `suppress_non_speech_tokens`, `temperature_inc`, greedy vs beam search, and abort callbacks.
- `whisper_init_from_buffer` + a mapped `ByteBuffer` avoids copying the model into a second heap allocation (Futo does this).
- `whisper_full` can decode a chunk independently; `new_segment_callback` fires as segments complete, which is enough for chunk-level incremental text. Upstream does **not** expose token-level `partial_text_callback`; Futo carries a patched fork for that. Do not plan token-level live text without maintaining a patch.
- Vulkan is a first-class ggml backend (`GGML_VULKAN`); CPU is always available.

Relevant evidence:
- Upstream Android CMake is CPU-only: `examples/whisper.android/lib/src/main/jni/whisper/CMakeLists.txt` uses `GGML_USE_CPU`.
- `ggml/CMakeLists.txt` exposes `GGML_VULKAN`, `GGML_VULKAN_CHECK_RESULTS`, `GGML_VULKAN_SHADER_DEBUG_INFO`, etc.
- `ggml/src/ggml-vulkan/CMakeLists.txt` requires a Vulkan SDK/loader/glslc and SPIRV-Headers; in cross-compilation it builds a host-native `vulkan-shaders-gen` and supports `GGML_VULKAN_SHADERS_GEN_TOOLCHAIN`.
- `ggml_backend_vk_reg()` catches Vulkan init exceptions and returns `nullptr`, so an unsupported driver can unregister the backend instead of aborting the process. A driver segfault is still fatal.

### 3.2 Futo’s implementation details worth copying as ideas (not code)

- `AudioRecognizer.kt` closes the model immediately after each inference (`model!!.close()`, lines ~523 and ~538) and reloads on the next run. That keeps resident memory low but pays model load latency on every hold; the SPEC’s 300 s idle unload is the better trade-off.
- `AudioRecognizer.kt` uses `com.konovalov.vad` (`Vad.builder()`) over 480-sample frames (30 ms at 16 kHz), with consecutive non-speech counters. It is a useful reference for a Silero-style VAD fallback, not a dependency to add blindly.
- `voiceinput.cpp` sets `wparams.audio_ctx = min(1500, ceil(num_samples / 320.0) + 32)` for short utterances. This is a meaningful encoder-cost reduction for 2–10 s holds; E1 should benchmark it against full `audio_ctx` on the target device.
- Futo uses `whisper_init_from_buffer` on a mapped model file; this is the preferred load path to avoid a 148 MB heap copy.
- Futo’s model catalog is custom q8 “acft” models (`tiny_en_acft_q8_0.bin`, `base_en_acft_q8_0.bin`, etc.) served from `voiceinput.futo.org`, not stock `ggml-base.bin`. We should use stock GGML files and our own checksums.
- Futo’s native `partial_text_callback` is not in upstream `whisper.h`; only `new_segment_callback` is. Treat the callback as a Futo fork feature.

### 3.3 sherpa-onnx: the easy build, weaker GPU

What is proven:
- The Apache-2.0 AAR is real and current: `sherpa-onnx-2.0.0.aar` is 49.2 MB, published 2026-10-23. At research time GitHub `v2.0.1` was referenced by the AAR README but not published (the AAR URL returned 404; latest release was `v2.0.0`).
- The AAR contains `classes.jar` and `jni/{arm64-v8a,armeabi-v7a,x86,x86_64}/libonnxruntime.so`, `libsherpa-onnx-c-api.so`, `libsherpa-onnx-cxx-api.so`, `libsherpa-onnx-jni.so`. Arm64 native payload is about 31.3 MB uncompressed.
- Kotlin API: `OfflineRecognizer`, `OfflineStream`, `OfflineWhisperModelConfig`, `Vad`, `VadModelConfig`; `phone-whisper` and `ramblr` both use it successfully.
- NNAPI: `sherpa-onnx/csrc/provider.h` has `kNNAPI = 4`; `session.cc` appends `NnapiExecutionProvider` only when `__ANDROID_API__ >= 27`, and falls back to CPU on failure. There is **no Vulkan provider** in the enum.
- Android model archives on the `asr-models` release are `.tar.bz2`: Whisper tiny 116.2 MB, base 207.6 MB, small 639.4 MB, medium 1931.4 MB, large-v3 1068.5 MB. These are larger than GGML and do not include the quantized variants we want.

Why it is not the primary:
- Android’s own docs now say: “NNAPI is deprecated… we expect the majority of devices in the future to use the CPU backend… for performance critical workloads, we recommend migrating to alternative solutions, for example the TF Lite GPU runtime.” NNAPI was deprecated in Android 15.
- NNAPI is a delegation API, not a guaranteed GPU path. Unsupported Whisper ops partition back to CPU; performance is device/driver dependent.
- Model format is ONNX only; custom GGML import would not work without a second engine.
- The AAR does make the build trivial and would satisfy a CPU fallback quickly.

**Recommendation:** Option A. Keep `SpeechEngine` as the seam. Do not implement Option B in v1. If E1 cannot get the Vulkan build green, ship whisper.cpp CPU-only first; if even the whisper.cpp native build fails, then fall back to B rather than blocking the product.

If a future ADR chooses C (both), the AAR is the low-risk “CPU/ONNX” leg, but it should be added after v1, not now.

### 3.4 GPU evidence and what “GPU by default” should mean

| Evidence | Result | Implication |
|---|---|---|
| whisper.cpp issue #2370, Pixel 8 community CMake | 30 s audio: CPU 73 s, Vulkan 67 s | Vulkan can be only ~9% faster on some devices; measure, don’t promise |
| whisper.cpp issue #2706, Adreno 640 | `device Vulkan0 does not support 16-bit storage` → `Unsupported device` | Old Adreno can fail init; CPU fallback must be real |
| whisper.cpp discussion #3943, Adreno 840 + Turnip | 120 s, small model: GPU 27.8 s vs CPU 120.8 s (~4.3x) | Large wins exist, but the report used Termux/Turnip; stock Qualcomm driver had cm2 pipeline failures |
| whisper.cpp issue #2370, NDK glslc note | Vulkan requires minSdk 29; `Vulkan_GLSLC_EXECUTABLE` needed | Native build is not the stock example; plan for it |
| QVAC ggml issue #34, Mali-G715 | Coopmat path ~5x slower for encoder shapes; disabling it made Vulkan faster than CPU | Vendor-specific kernel behavior; don’t tune for one GPU |

Recommended semantics for R5:
1. Compile Vulkan + CPU into the same arm64 library.
2. `auto` backend: enumerate GPU devices, attempt Vulkan init; if it fails, retry CPU once and persist `backend=cpu` + reason.
3. Show `whisper.cpp/vulkan (<device>)` or `whisper.cpp/cpu` in settings/doctor.
4. Add a small device/driver denylist for known-bad cases (Adreno 6xx without 16-bit storage; stock Adreno 8xx cm2 crashes; old Mali) and a “force CPU” developer setting.
5. If Vulkan init succeeds but the first transcription crashes the process, the FGS restarts; the next start should default to CPU until the user explicitly re-enables GPU (persist a `gpu_crash_count`).
6. Do not treat NNAPI as the GPU requirement; it is deprecated and not a generic Android GPU API.

### 3.5 Model catalog and checksums

Official `ggerganov/whisper.cpp` GGML files (HF API, 2026-10-26):

| File | Bytes | MiB | SHA-256 |
|---|---:|---:|---|
| `ggml-tiny.bin` | 77,691,713 | 74.1 | `be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21` |
| `ggml-tiny.en.bin` | 77,704,715 | 74.1 | `921e4cf8686fdd993dcd081a5da5b6c365bfde1162e72b08d75ac75289920b1f` |
| `ggml-base.bin` | 147,951,465 | 141.1 | `60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe` |
| `ggml-base.en.bin` | 147,964,211 | 141.1 | `a03779c86df3323075f5e796cb2ce5029f00ec8869eee3fdfb897afe36c6d002` |
| `ggml-small.bin` | 487,601,967 | 465.0 | `1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b` |
| `ggml-small.en.bin` | 487,614,201 | 465.0 | `c6138d6d58ecc8322097e0f987c32f1be8bb0a18532a3f88f734d1bbf9c41e5d` |
| `ggml-medium.bin` | 1,533,763,059 | 1462.8 | `6c14d5adee5f86394037b4e4e8b59f1673b6cee10e3cf0b11bbdbee79c156208` |
| `ggml-medium.en.bin` | 1,533,774,781 | 1462.8 | `cc37e93478338ec7700281a7ac30a10128929eb8f427dda2e865faa8f6da4356` |
| `ggml-large-v3-turbo.bin` | 1,624,555,275 | 1549.3 | `1fc70f774d38eb169993ac391eea357ef47c88757ef72ee5943879b7e8e2bc69` |

Quantized sizes (same HF repo): base q5_1 59.7 MB, base q8_0 81.8 MB; tiny q5_1 32.2 MB, tiny q8_0 43.5 MB; small q5_1 190.1 MB, small q8_0 264.5 MB. Default `base` is the SPEC choice; q5_1 is the sensible “smaller/faster” option if quality holds on the target device.

Download URLs are stable HF resolve URLs:
`https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin` (replace filename). Verify SHA-256 while streaming to disk; do not load a partial file. Do not silently download; onboarding must offer “download base” or “import file”.

### 3.6 Native build recipe for B1/E1 (Vulkan on Android)

This is a feasibility sketch, not a verified build. The community recipe in whisper.cpp issue #2370 is the closest thing to a working Android Vulkan CMake and explicitly says it was AI-generated; treat it as a starting point and test it.

Required pieces:
- NDK r27+; `nix eval nixpkgs#androidndk` resolves `27.0.12077973` on this aarch64 host. Nix also has `shaderc` 2026.1 and `vulkan-headers` 1.4.363.0.
- A host `glslc`. Do not assume the NDK’s shader-tools binary runs on this aarch64 host; set `Vulkan_GLSLC_EXECUTABLE` to the host `glslc` (e.g. nix `shaderc`).
- Vulkan headers. The NDK sysroot has the loader/header surface, but community reports say the NDK headers can mismatch C/C++ and cause `IntegerDotProduct` errors; fetching Khronos `Vulkan-Headers`/`Vulkan-Hpp` at a pinned tag is a known workaround.
- Link the target `libvulkan.so` from the NDK, not a host Vulkan loader.
- `minSdk 29` for the Vulkan build (issue #2370 and the community CMake). The app already targets minSdk 29.
- Set `GGML_VULKAN ON` before adding the `ggml` subdirectory, and force `BUILD_SHARED_LIBS OFF`, `GGML_NATIVE OFF`.
- Disable `COOPMAT2` shader generation for the first build (`GGML_VULKAN_COOPMAT2_GLSLC_SUPPORT=OFF` and the decode-vector variant). NDK/older shaderc can false-positive the feature test and then fail to optimize `cm2` shaders; the discussion #3943 workaround shows the exact failure mode.
- Prevent the linker from stripping the Vulkan backend: use whole-archive for `ggml-vulkan` or add its objects to the final shared library. The community CMake does this explicitly.
- Build only `arm64-v8a` for release; keep `x86_64` optional for emulator builds (no KVM here).

CMake sketch (verify paths and target names against the pinned whisper.cpp commit):

```cmake
set(GGML_VULKAN ON CACHE BOOL "" FORCE)
set(GGML_VULKAN_COOPMAT2_GLSLC_SUPPORT OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN_COOPMAT2_DECODE_VECTOR_GLSLC_SUPPORT OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)

# Host glslc, not the target shader-tools binary.
set(Vulkan_GLSLC_EXECUTABLE "/path/to/host/glslc" CACHE FILEPATH "" FORCE)

# Provide the NDK Vulkan surface to the inner ggml find_package.
find_library(VULKAN_LIB vulkan)
set(Vulkan_LIBRARY "${VULKAN_LIB}" CACHE FILEPATH "" FORCE)
set(Vulkan_INCLUDE_DIR "${ANDROID_NDK}/.../sysroot/usr/include" CACHE PATH "" FORCE)
set(Vulkan_FOUND TRUE CACHE BOOL "" FORCE)

add_subdirectory(${WHISPER_LIB_DIR}/ggml ${CMAKE_CURRENT_BINARY_DIR}/ggml)
target_compile_definitions(whisper PRIVATE GGML_USE_VULKAN)
target_link_libraries(whisper ${LOG_LIB} android "${VULKAN_LIB}" ggml)
```

Practical warning: `ggml/src/ggml-vulkan/CMakeLists.txt` calls `find_package(Vulkan COMPONENTS glslc REQUIRED)` and `find_package(SPIRV-Headers CONFIG REQUIRED)`. Simply forcing `Vulkan_FOUND` may not bypass that search on every CMake version; a small `VulkanConfig.cmake` shim in `CMAKE_PREFIX_PATH` that defines `Vulkan_FOUND`, `Vulkan_LIBRARY`, `Vulkan_INCLUDE_DIR`, `Vulkan_GLSLC_EXECUTABLE`, and the `Vulkan::Vulkan` imported target is the robust route. B1 should validate this before E1 depends on it.

### 3.7 JNI / model lifetime design

- Load model from a mapped read-only `ByteBuffer` (`FileChannel.map`) and call `whisper_init_from_buffer`. Keep a strong reference to the buffer for the context lifetime.
- Use one dedicated ASR dispatcher (`newSingleThreadContext` or a single-thread executor). Never touch a `whisper_context` from two threads.
- `close()` must run on the same dispatcher after any in-flight `whisper_full` returns; set the handle to 0 and make subsequent calls throw a typed `EngineClosedException`.
- Model swap: mark the old context “retiring”, wait for the current inference, close, then load the new one. Do not free under an active call.
- If `whisper_full` fails, free nothing until the call returns; log the result code and fall back to CPU for the next attempt if the failure looks backend-related.
- Native OOM is not reliably catchable. Pre-check `ActivityManager.MemoryInfo` against the model size + working set, catch Java `OutOfMemoryError`, and treat a process death as a recoverable FGS restart.
- Optional hardening: run the engine in a separate `:asr` process so a SIGSEGV does not take down the overlay/accessibility service. This is extra IPC and not v1; note it as a future option if driver crashes are observed.

## 4. Overlay and foreground-service architecture

### 4.1 Window type comparison

| | `TYPE_APPLICATION_OVERLAY` (SPEC, whisperclient) | `TYPE_ACCESSIBILITY_OVERLAY` (phone-whisper, flo, ramblr) |
|---|---|---|
| Permission | `SYSTEM_ALERT_WINDOW` special access | None beyond enabled accessibility service |
| Lifetime | Owned by our service; survives a11y reconnect | Owned by accessibility service; disappears if service disabled/crashes |
| Focus | `FLAG_NOT_FOCUSABLE` keeps target field focused | Same flag works |
| Touch pass-through | Android 12 blocks pass-through for untrusted `TYPE_APPLICATION_OVERLAY`; use a touchable bubble, and alpha ≤0.8 for any non-touchable status window | Accessibility windows are trusted; pass-through is allowed |
| Background FGS start | `SYSTEM_ALERT_WINDOW` is a background-start exemption (Android 15: also needs a visible overlay) | No SAW exemption; accessibility service itself may be exempt in some paths, but not a general FGS grant |
| UX | Bubble can show before accessibility is enabled and prompt the user | Bubble only exists after accessibility is on; couples two failures |
| Verdict | **Keep for the product bubble** | Good for a status-only HUD or a no-SAW build flavor |

`phone-whisper` creates its overlay with `TYPE_ACCESSIBILITY_OVERLAY` + `FLAG_NOT_FOCUSABLE` (`WhisperAccessibilityService.kt:144`) and records from the accessibility service without a microphone FGS. `whisperclient-android` uses `TYPE_APPLICATION_OVERLAY` + `FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_NO_LIMITS` in a microphone FGS (`WisprFloatingBubbleService.kt:218`). `flo` and Ramble also use the accessibility-overlay variant, even though Ramble runs a microphone FGS. The SPEC’s choice matches whisperclient’s app-overlay shape and is the more robust product shape because the bubble does not disappear when the accessibility service reconnects.

### 4.2 Foreground service and boot behavior

- Manifest: `RECORD_AUDIO`, `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS` (13+), `RECEIVE_BOOT_COMPLETED` (optional), `INTERNET` only for model downloads.
- Service: `android:foregroundServiceType="microphone"`; call `startForeground(id, notification, FOREGROUND_SERVICE_TYPE_MICROPHONE)` promptly after start. Notification channel low importance, ongoing, “Stop dictation” action.
- Start the microphone FGS from a visible Activity (onboarding/settings) and keep it alive. Android 14+ enforces while-in-use permission at FGS creation; starting from the background can throw `SecurityException`, and `SYSTEM_ALERT_WINDOW` is listed as a background-start exemption but not in the while-in-use exemption list. Do not rely on a background restart to regain the mic.
- Android 14+ also restricts microphone FGS from `BOOT_COMPLETED`; Android 15 requires `SYSTEM_ALERT_WINDOW` plus a currently visible overlay for the SAW background-start exemption. **Do not make “restore overlay at boot” depend on starting a microphone FGS in the boot receiver.** Options: post a high-priority notification (“Tap to start dictation”), or create the overlay from a short-lived service and then start the FGS only if the visible-overlay path verifies on the device. U1 must test this on Android 14/15; fall back to a notification if it is flaky.
- If the FGS is killed, use `START_STICKY` and a restart path that checks whether mic access is still allowed; if not, show “Open Martins Whisper to re-arm” rather than crashing.
- Android 12+ untrusted touch: the bubble is touchable, so it is not the pass-through case; do not set `FLAG_NOT_TOUCHABLE` on the bubble. For any non-interactive “recording” HUD, either set alpha ≤0.8 with `FLAG_NOT_TOUCHABLE`, or use `TYPE_ACCESSIBILITY_OVERLAY` (trusted). Logcat shows `Untrusted touch due to occlusion by <pkg>` if this is wrong.
- Battery optimization: optional prompt, especially Samsung/Xiaomi; document that OEM killers may stop the FGS.

### 4.3 Overlay parameters and UX

- `WindowManager.LayoutParams`: `TYPE_APPLICATION_OVERLAY`, `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_NO_LIMITS`, `PixelFormat.TRANSLUCENT`, `gravity = TOP|START`.
- `FLAG_NOT_FOCUSABLE` is the requirement that keeps the target field focused; `FLAG_NOT_TOUCH_MODAL` lets touches outside the bubble reach the app. `FLAG_LAYOUT_NO_LIMITS` is for edge dragging/auto-peek; clamp coordinates to the current window metrics or the bubble can park under the status bar/navigation bar.
- 56 dp circular bubble, bottom-end default, draggable, persisted x/y in DataStore/SharedPreferences. Snap to edge with a small inset; handle rotation and cutouts via `WindowMetrics`/insets, not deprecated `Display.getSize`.
- Press-and-hold: haptic on `ACTION_DOWN`, start capture immediately, expand to pill with elapsed time + level bars. Release before 400 ms: cancel, discard audio, show “Hold to talk”. Release after: `FINALIZING`, then insert.
- States: `IDLE -> ARMING (audio + lazy model load) -> RECORDING -> FINALIZING -> INSERTING -> IDLE`; `ERROR` surfaces a short message and returns to IDLE. This matches SPEC 3.3.
- The bubble must not request focus or show an IME. If a settings gear is needed, open the Activity from a tap/notification action, not an inline EditText.
- Screen off: stop rendering/animation; keep the service and state. Do not hold a wake lock during idle. If recording, a short partial wake lock is acceptable but must be released.

## 5. Focus tracking and text insertion

### 5.1 Accessibility service configuration

Use `res/xml/accessibility_service_config.xml`:

```xml
<accessibility-service
    android:accessibilityEventTypes="typeViewFocused|typeViewClicked|typeViewTextSelectionChanged|typeWindowStateChanged|typeWindowContentChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagReportViewIds|flagRetrieveInteractiveWindows"
    android:canRetrieveWindowContent="true"
    android:notificationTimeout="100"
    android:description="@string/accessibility_description" />
```

Notes:
- `flagRetrieveInteractiveWindows` is required for `getWindows()` to return anything (see Ramble’s config comment). Without it, dialogs/popups/split-screen targets silently degrade to clipboard fallback.
- `typeWindowContentChanged` is noisy; keep it only if testing shows fields that do not emit focus/click events. Debounce all events (100–150 ms) and ignore our own package.
- Do not add `flagDefault` (would make the app the default handler for `feedbackGeneric`) or `flagRequestAccessibilityButton` unless the PM explicitly wants the system accessibility button; Ramble documents that the latter changes service classification and can tie enabled state to shortcut bindings.
- For Play Store, `android:isAccessibilityTool="false"` is appropriate for a dictation app; it triggers the accessibility declaration and prominent-disclosure requirements. Sideload v1 is not subject to Play review, but the declaration should still be honest if distribution changes.
- Manifest service: `android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"`, `android:exported="false"`, intent filter `android.accessibilityservice.AccessibilityService`, metadata pointing at the XML.

### 5.2 Target discovery and candidate scoring

Recommended flow (independent implementation of ideas proven by phone-whisper, whisperclient, and Ramble):
1. On event, get `rootInActiveWindow`; if null (secure window, system UI, transient), hide the bubble and do not insert.
2. `root.findFocus(FOCUS_INPUT)`; if it is editable, has a labelled paste action, or is an `EditText`/terminal-like class, accept it.
3. If not, walk the active root for editable nodes; then iterate `windows.filter { it.isActive || it.isFocused }`, skipping `TYPE_INPUT_METHOD`, and collect candidates.
4. Score: custom paste action +100, terminal class +80, `isEditable` +60, `isFocused` +40, `EditText` class +20, bare WebView container −penalty. Sort descending. This is the same shape as `phone-whisper.candidateScore` and Ramble’s `candidateScore`; write it as a pure Kotlin function over a node snapshot so JVM tests can cover the ordering.
5. Snapshot package, class, window id, text, selection start/end. Do not keep a raw `AccessibilityNodeInfo` across arbitrary user activity without refreshing; nodes go stale. Re-resolve by window/id at insertion time, refresh, and recycle.
6. Password/PIN guard: if `node.isPassword` or the input type variation is password/PIN, hide the bubble, refuse to start a new recording, and discard a result if the target became a password field. Ramble’s `PasswordFieldGuard` is the right policy (refuse at IDLE, discard on delivery, no partials).
7. On focus loss, hide the bubble unless state is RECORDING/FINALIZING. On transient `TYPE_WINDOW_STATE_CHANGED` during recording, keep the session and re-resolve at insert; do not cancel.
8. For a `FLAG_SECURE` window, `rootInActiveWindow` may be null; do not attempt insertion, put text on the clipboard and show “Copied; paste manually”.

### 5.3 Insertion algorithm (recommended)

The SPEC order is direct `ACTION_SET_TEXT` → clipboard + `ACTION_PASTE` → clipboard + notification. Prior art adds a read-back check and a custom-paste-action branch. Implement:

```
insert(transcript):
  target = reResolveFocusedTarget() or highest-scored candidate
  if target == null or target.isPassword: return Copied("no editable target")
  target.refresh() or return Copied("target stale")
  target.performAction(ACTION_FOCUS)
  current = target.text ?: ""
  start = target.selectionStart; end = target.selectionEnd
  merged = composeOneShotInjection(current, start, end, transcript)
  direct = target.performAction(ACTION_SET_TEXT, bundle(merged))
  if direct and readBack(target) == merged:
      nudgeCaretToEnd(target, merged.length)
      return Inserted(DIRECT, priorText = current)
  clipboard.set(transcript)          // not merged: paste inserts at the current selection
  custom = findActionWithLabel(target, "paste")
  if custom != null and target.performAction(custom.id): return Inserted(CLIPBOARD)
  if target.performAction(ACTION_PASTE): return Inserted(CLIPBOARD)
  notify("Copied; paste manually")
  return Copied("paste failed")
```

Details that matter:
- **Compose the full replacement for direct write.** Read text and selection after `ACTION_FOCUS`; a field can report a different caret once focused. Use a pure `composeOneShotInjection(current, selStart, selEnd, text)` that:
  - resolves `selStart == -1 || selEnd == -1` and `(0,0)` against non-empty text to a pure insertion at the end,
  - clamps both ends,
  - replaces the selected range,
  - adds a leading space only when the preceding character is not whitespace/punctuation (Ramble’s `leadingSeparatorFor` behavior).
- **Read back.** `ACTION_SET_TEXT` returning `true` is not proof. `writeConfirmed` should refresh the node and compare the expected string after a short pause (one frame / ~50–100 ms). If it did not stick, fall through to paste. Some Compose fields accept the write then overwrite it on recomposition; read-back catches that.
- **Caret nudge.** After a successful bulk set, set the selection to the end with `ACTION_SET_SELECTION`; treat failure as best-effort. Ramble documents a Google Keep quirk where the platform contract is not enough.
- **Paste fallback uses the raw transcript, not the merged string**, otherwise a paste at the cursor would duplicate existing text.
- **Custom paste actions.** Some Compose/terminal fields expose a labelled “Paste” action but no `ACTION_PASTE`; scan `node.actionList` for a label containing “paste” before giving up.
- **WebView.** A focused WebView page/container is not the input; walk to the actual inner `EditText`. A bare WebView node can be a silent no-op. Ramble’s `isNonFieldWebViewNode` penalty and `correctWebViewSelectAllEnd` are useful behaviors to reimplement, not copy.
- **Do not clear the clipboard.** Android 12+ shows a “pasted from clipboard” toast; leaving the transcript is useful for recovery and avoids extra clipboard access. Android 10+ restricts background clipboard reads, but setting from the a11y service is allowed.
- **No target.** Put the transcript on the clipboard, show a heads-up notification and a toast. Keep the transcript in an in-memory “last result” slot so a notification action can copy it again; do not persist history unless the user enables it.
- **IME alternative.** `whisperclient-android` and Ramble both have an IME surface for keyboards; it is a stronger integration path for some apps but conflicts with ADR-006 (no invisible IME) and adds a second UX. Keep it out of v1; revisit for terminals/apps where accessibility fails.

### 5.4 Field matrix to validate on a device

| Target | Expected path | Known risk |
|---|---|---|
| Native `EditText` (test app, Settings search) | `ACTION_SET_TEXT` + caret nudge | Minimal |
| Jetpack Compose `BasicTextField`/`TextField` | Custom paste action or `ACTION_PASTE`; direct write often not exposed | Direct write may report success but not apply; read-back required |
| Chrome/WebView input | Inner `EditText` via tree walk; direct write or paste | Focused WebView container is a decoy; select-all `(0,0)` quirk |
| Google Keep | Direct write + explicit selection nudge | Bulk set leaves caret/selection wrong |
| WhatsApp/Telegram/Slack | `ACTION_SET_TEXT` or paste | Some fields expose non-editable nodes; use paste action |
| Termux/terminal | Custom paste action or `ACTION_PASTE` | `TerminalView` isn’t `EditText`; score it specially |
| Password/PIN fields | Refuse recording, discard result | `node.text` is empty; writing would wipe the field |
| Secure/incognito windows | No root; copy to clipboard only | `FLAG_SECURE` hides content; don’t guess |
| Split-screen/dialog | `flagRetrieveInteractiveWindows` + candidate scan | Without the flag, `getWindows()` is empty |

## 6. VAD, chunking, and merge

SPEC ADR-005 already chooses a pure-Kotlin energy VAD; prior art supports that for v1:
- Futo uses `com.konovalov.vad` over 480-sample frames and non-speech counters; Ramble uses sherpa-onnx Silero VAD with `maxSpeechDuration = 15.0F` (`SherpaVadHandle.kt:36`) and a `SpeechSegmenter`. Silero is better in noise, but adding sherpa-onnx just for VAD is a large dependency; keep energy VAD and make it swappable behind the `Vad` interface.
- SPEC’s 20 ms frames, 300 ms pre-roll, 500 ms trailing silence, 25 s max chunk, 250 ms overlap on forced splits is consistent with whisper’s 30 s window and is slightly more generous than Ramble’s 15 s cap. Use SPEC values.
- Finalize at silence only when speech ≥250 ms; drop pure-silence chunks. On a forced split, cut at the lowest-energy frame in the last 2 s and carry 250 ms of tail into the next chunk. The merger dedupes suffix/prefix words (case/punctuation-insensitive, up to 8 words) and joins with one space.
- Incremental decode: finalize a chunk while the user is still holding, queue it to the single ASR thread, and show the partial transcript in the bubble. **Do not write partials into the target field in v1.** Insert once on release after merging; this avoids caret churn and makes the rollback story simple. If PM later wants live field preview, follow Ramble’s opt-in streaming-preview design, not the default path.
- Long hold safety cap: 10 min. At 16 kHz mono PCM16 that is ~19.2 MB; float conversion doubles it. Keep at most a few 25 s chunks in flight; if transcription lags, continue recording and let the queue drain; never drop audio silently.
- `whisper_full_params`: `no_timestamps = true`, `suppress_non_speech_tokens = true`, `temperature_inc = 0.0`, greedy for latency (beam 5 optional), `n_threads` = 4 (benchmark 2/4/6), `audio_ctx` proportional to chunk length for short holds. Set `condition_on_previous_text = false` equivalent (no cross-chunk prompt) and keep the language stable from the first chunk.
- Hallucination filter: use `no_speech_prob`, `avg_logprob`, compression ratio, and a small low-confidence blocklist (“Thank you.”, “Subtitles by…”, etc.); never drop high-confidence speech.

## 7. ModelManager invariants

```
UNLOADED --getOrLoad()--> LOADING --> READY --markUsed()--> READY
READY --idle >= 300s and no lease--> UNLOADING --> UNLOADED
READY --switch--> UNLOADING --> LOADING --> READY
any --error--> ERROR (surface, allow retry)
```

Required behavior:
- One ASR dispatcher; `withModel {}` takes a lease (refcount or mutex) so unload cannot free under inference.
- `markUsed()` after every transcription and after insertion; reset the idle deadline.
- Idle watchdog: `postDelayed(300_000)` or a 15 s tick; if state is IDLE and no lease, unload. Cancel the timer on recording start.
- Unload off the main thread: call `whisper_free`, null the handle, `System.gc()` best-effort; log RSS before/after in debug. Do not block the accessibility service main thread.
- Model switch: unload immediately, then load the new model; cancel/await in-flight work. If a dictation is in progress, finish it with the old model or fail with a clear “model changed” message; do not mix contexts.
- While unloaded, a new hold still starts recording and shows “Loading base…”; chunks queue until the model is ready. If load fails, keep the transcript recoverable (clipboard) and show retry.
- `onTrimMemory(TRIM_MEMORY_RUNNING_LOW)`: unload if IDLE; never unload during RECORDING/FINALIZING. On API 34+ the accessibility-bound process may never get `BACKGROUND`, so do not rely on `onTrimMemory` alone. Ramble’s comment about ~800 MB of resident recognizers is the cautionary tale.
- Store model files under `filesDir/models/<id>.bin`; verify SHA-256 after download/import; write to a temp file and atomically rename; keep a `.complete` marker if using directory-based ONNX models (Ramble/phone-whisper pattern).
- Expose state (`UNLOADED/LOADING/READY/UNLOADING/ERROR`) and backend string to the settings/doctor UI.

## 8. Failure modes and privacy

| Failure | Behavior |
|---|---|
| Mic denied | Onboarding blocks recording; deep-link to app settings; keep overlay usable to show why |
| Overlay denied | Cannot show bubble; notification/settings deep link; accessibility-only mode is not a substitute |
| Accessibility off | Clipboard-only mode: record/transcribe, copy, notify “Enable accessibility to insert”; never silently drop text |
| Model missing/corrupt | Prompt to download/import; verify checksum; do not load partial files |
| Model load OOM | Check available memory vs model size; catch Java OOM; unload other models; retry once; then clipboard fallback |
| Native crash (SIGSEGV) | FGS restarts; persist a crash counter; next run defaults to CPU and offers “report” (no transcript in logs) |
| Insertion failure | Clipboard + notification + toast; keep last transcript in memory; optional “Copy again” action |
| No editable target | Same clipboard path; do not paste into a random node |
| Secure window | Do not read/insert; clipboard path only |
| Password field | Refuse/discard; no clipboard, no history, no logs (Ramble’s policy) |
| Rotation/focus change during recording | Keep recording; re-resolve target at insert; if target changed, use the new focused field only if it was focused at release, otherwise clipboard |

Privacy rules:
- No network during dictation; Internet permission is only for explicit model downloads. No analytics/telemetry/crash reporting in v1.
- Audio stays in memory; if a temp WAV is needed for debugging, gate it behind an explicit debug flag and delete it on stop.
- Do not log transcripts, node text, package contents, or clipboard data. Log state, durations, backend, and error codes only.
- Clipboard fallback leaves text available to other apps; show a clear message and let the user overwrite it. Do not auto-clear (background clipboard access is restricted and clearing can race the paste).
- Skip password/PIN fields and secure windows; consider a package denylist for banking/password-manager apps in settings (opt-in).

## 9. Testing and validation plan

JVM (C1/E1):
- `TextInsertionPlanner`/`composeOneShotInjection`: empty/whitespace, collapsed caret, ranged selection, `-1/-1`, `(0,0)` against non-empty text, clamp out-of-range, separator before/after punctuation, emoji/surrogate pairs (use code points where possible).
- `TranscriptMerger`: no overlap, exact overlap, partial word overlap, case/punctuation differences, repeated words, empty parts.
- `AudioChunker`: silence-only, short speech, 25 s forced split, 250 ms overlap, 10 min cap, reset.
- `ModelManager`: fake clock, idle unload at exactly 300 s, lease prevents unload, switch during load, load failure/retry, no use-after-close.
- Candidate scoring: pure snapshot input (class name, editable, focused, paste action, password) → expected order.

Robolectric (I1/U1):
- Accessibility event → focus state reducer (focused/lost/selection changed, own package ignored, password hidden).
- Overlay LayoutParams flags and position clamping.
- Permission gates and onboarding state.
- Notification/clipboard recovery path with a fake `TextInserter`.

Instrumented / device (H1/H2, blocked here):
- Fake `SpeechEngine` for deterministic overlay/insertion tests; then real base model smoke.
- Field matrix in §5.4; log `InsertMethod` (DIRECT/FROM_CLIPBOARD/NONE) per app.
- Backend doctor: device model, Vulkan API version, `storageBuffer16BitAccess`, selected backend, load time, first-token latency, RSS after unload.
- Idle unload: `dumpsys meminfo <pkg>` before/after 5 min; confirm RSS drop and successful reload.
- 60 s speech with pauses: no duplicated boundary words; 25 s splits; 10 min cap.
- Long-press/tap <400 ms; focus changes; rotation; split-screen; IME open; screen off; FGS kill/restart; boot behavior on Android 14/15.

Commands (from SPEC; run when the skeleton/build exists):
```
./gradlew :core:test
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```
No Android SDK/NDK build was run in R1; see B1/E1 for the harness. Do not claim GPU/device success without H1/H2 output.

## 10. Reusable code map

| Need | Best source | License | Action |
|---|---|---|---|
| JNI bridge + CMake baseline | `whisper.cpp/examples/whisper.android` (`jni.c`, `LibWhisper.kt`, `CMakeLists.txt`) | MIT | Copy/adapt into `app/src/main/cpp`, keep MIT notice |
| Vulkan CMake deltas | whisper.cpp issue #2370 community CMake, PR #2914 cross-compile support | Upstream MIT / issue guidance | Adapt and test; do not trust the AI-generated snippet blindly |
| Model download + checksum | Write independently; HF URLs + SHA table above | — | New code |
| Model buffer load / `audio_ctx` | Futo `WhisperModel.kt`, `voiceinput.cpp` | Non-commercial | Reimplement the idea; no code copy |
| Overlay inside a11y service | `phone-whisper` `WhisperAccessibilityService.kt` | Apache-2.0 | Fallback only; structure can be adapted with notice |
| App-overlay + FGS + focus evaluator | `whisperclient-android` | No license | Behavior reference only; write independently |
| Idle unload / trim / generation guards | Ramble `DictationRuntime.kt` | GPLv3 | Behavior reference only; independent implementation |
| Insertion hardening (read-back, password, WebView, separator) | Ramble `AccessibilityTextDestination.kt`, `TextDestination.kt`, `StreamingPreview.kt`, `PasswordFieldGuard.kt` | GPLv3 | Behavior reference only; independent implementation |
| Candidate scoring | `phone-whisper` (Apache-2.0) / Ramble (GPLv3) | Mixed | Independent pure-Kotlin scoring function |
| Sherpa AAR fallback | `sherpa-onnx` + `phone-whisper` | Apache-2.0 | Only if Option B is activated |

## 11. Open questions for PM / next agents

1. **ADR-004:** approve Option A (whisper.cpp Vulkan+CPU, auto fallback) for v1. Keep B as a documented emergency fallback, not a parallel implementation.
2. **Custom model import:** approve GGML-only for v1, or require an ONNX path (which adds a second engine). SPEC R4 currently says “GGML/ONNX”; this needs an explicit decision.
3. **Boot behavior:** accept “notification to re-arm after reboot” if the microphone FGS cannot legally start from `BOOT_COMPLETED` on the target Android version, or invest in the SAW+visible-overlay boot flow and test it on Android 14/15.
4. **Target device/SoC:** needed for H1/H2 GPU and insertion matrix; without it, Vulkan remains “buildable but unproven”.
5. **Play Store:** if ever planned, budget for accessibility declaration/prominent disclosure and possible policy review; sideload v1 avoids this but the code should still be honest about `isAccessibilityTool=false`.
6. **x86_64:** only if an emulator runner becomes available; otherwise arm64-v8a only.
7. **Model hosting:** HF direct vs a mirror; provide checksums and resume; consider a “download base” onboarding flow with size/network warning.

## Appendix A: key source locations

These are the exact files/lines reviewed; line numbers are from the commits in §2.

- Futo `AudioRecognizer.kt:358` VAD builder; `:444`/`:453` non-speech counters; `:523`/`:538` close after inference; `:191` model wrapper creation.
- Futo `WhisperModel.kt:66` mapped model load; `:190` close; `voiceinput.cpp:63`/`:80` `whisper_init_from_file`/`from_buffer`; `:121` `whisper_full_params`; `:169` patched partial callback; `:224` `whisper_full`.
- Futo `Util.kt:251` English model catalog; `:329` multilingual catalog; `MigrationUtils.kt:28` download URL.
- whisper.cpp `examples/whisper.android/lib/src/main/jni/whisper/CMakeLists.txt:30` `GGML_USE_CPU`; `ggml/CMakeLists.txt:224` `GGML_VULKAN`; `ggml/src/ggml-vulkan/CMakeLists.txt:9` `find_package(Vulkan ... glslc)`; `:162` cross-compile host shader tool; `ggml-vulkan.cpp:4643` 16-bit storage throw; `:16262` `ggml_backend_vk_reg` exception catch.
- whisper.cpp `include/whisper.h:463` `new_segment_callback`; no upstream `partial_text_callback`.
- sherpa `sherpa-onnx/csrc/provider.h:16` providers; `session.cc:474` NNAPI; `build-android-arm64-v8a.sh:164` API 21 default, `:194` NNAPI API 27 note; `android/SherpaOnnxAar/README.md` AAR assembly.
- phone-whisper `WhisperAccessibilityService.kt:144` `TYPE_ACCESSIBILITY_OVERLAY`; `:570` candidate score; `:578` focus; `:586` `ACTION_PASTE`; `:603` `ACTION_SET_TEXT`; `res/xml/accessibility_service_config.xml` focus-only events.
- whisperclient `WisprFloatingBubbleService.kt:218` app overlay; `:590` `startForeground`; `WisprFocusAccessibilityService.kt:121` insert; `:158` setText/paste; `res/xml/wispr_focus_accessibility_service.xml` all four event types + view IDs/windows.
- flo `FloAccessibilityService.kt:57` accessibility overlay; `:151` clipboard; `:156` `ACTION_PASTE`.
- Ramble `DictationRuntime.kt:231` 5 min idle; `:568` transcriber release; `:618` schedule; `:560` trim comment (~800 MB); `SherpaVadHandle.kt:36` 15 s max segment; `AccessibilityTextDestination.kt:65` password guard, `:71` direct write, `:78` paste; `TextDestination.kt:232` direct-before-paste commit; `StreamingPreview.kt:97` one-shot compose, `:325` selection/separator; `WhisperAccessibilityService.kt:3706` candidate scan, `:3828` score, `:3776` password filter; `PasswordFieldGuard.kt:37` policy.
- Android docs: untrusted touch (Android 12), FGS while-in-use restrictions, NNAPI deprecation, Play accessibility policy. URLs in `REFERENCES.md`.

## Appendix B: evidence commands

```
# model sizes + SHA-256
curl -sL 'https://huggingface.co/api/models/ggerganov/whisper.cpp?blobs=true' -o /tmp/hf.json
jq -r '.siblings[] | select(.rfilename|test("^ggml-(tiny|base|small)(\\.en)?\\.bin$")) | [.rfilename,.size,.lfs.sha256] | @tsv' /tmp/hf.json

# sherpa release assets
curl -sL 'https://api.github.com/repos/k2-fsa/sherpa-onnx/releases/latest' -o /tmp/sherpa.json
jq -r '.tag_name, (.assets[] | select(.name|test("android|\\.aar$")) | [.name,.size] | @tsv)' /tmp/sherpa.json

# AAR contents
curl -sL -o /tmp/sherpa.aar 'https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.0/sherpa-onnx-2.0.0.aar'
unzip -l /tmp/sherpa.aar | grep -E '\\.so|classes.jar'
```

No secrets or credentials were used.
