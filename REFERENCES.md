# REFERENCES

One line per source: URL/repo -> what we took -> where it landed. Maintained by research/integration agents.

## Product / UX

- https://docs.wisprflow.ai/articles/6409258247-starting-your-first-dictation - push-to-talk, release inserts; tap under ~0.5 s cancels -> SPEC 3.7 state machine.
- https://docs.wisprflow.ai/articles/2772472373-what-is-flow - overlay/any-app insertion, cleanup expectations -> product framing.

## Android prior art (R1)

- https://github.com/futo-org/voice-input (d6e1eb2, FUTO Source First 1.0 non-commercial) -> whisper.cpp CPU Android app, mmap/`whisper_init_from_buffer` load, `audio_ctx` short-utterance trick, `com.konovalov.vad` VAD loop, model catalog/download; design reference only -> `research/ANDROID_PRIOR_ART.md` §3.2, §3.7.
- https://github.com/ggml-org/whisper.cpp (231638a, MIT) -> upstream JNI example (`examples/whisper.android`), CMake/NDK baseline, `whisper_full_params`, `new_segment_callback`, `GGML_VULKAN` backend and auto-registration fallback -> §3.1, §3.6, §3.7.
- https://github.com/ggml-org/whisper.cpp/issues/2370 -> Android Vulkan CMake recipe (minSdk 29, `Vulkan_GLSLC_EXECUTABLE`, NDK headers), Pixel 8 73 s -> 67 s data point -> §3.4, §3.6.
- https://github.com/ggml-org/whisper.cpp/issues/2706 -> Adreno 640 lacks 16-bit storage, Vulkan init fails; CPU fallback requirement -> §3.4.
- https://github.com/ggml-org/whisper.cpp/discussions/3943 -> Adreno 840 + Turnip 4.3x benchmark, stock driver cm2 pipeline failures, coopmat2 workaround -> §3.4.
- https://github.com/ggerganov/whisper.cpp/pull/2914 -> ggml Vulkan cross-compilation host shader tool and Android coopmat defaults -> §3.6.
- https://github.com/k2-fsa/sherpa-onnx (af2d8d8, Apache-2.0) -> prebuilt AAR, Kotlin `OfflineRecognizer`/`Vad`, NNAPI provider, Android build script; Option B fallback -> §3.3.
- https://github.com/k2-fsa/sherpa-onnx/releases/tag/v2.0.0 -> `sherpa-onnx-2.0.0.aar` 49.2 MB (arm64/armv7/x86/x86_64; ONNX Runtime + JNI), Whisper ONNX model tar sizes -> §3.3, §3.5.
- https://github.com/kafkasl/phone-whisper (e6518cd, Apache-2.0) -> overlay inside `AccessibilityService` (`TYPE_ACCESSIBILITY_OVERLAY`), candidate scoring, custom-paste/`ACTION_PASTE`/`ACTION_SET_TEXT` order -> §4.1, §5.2, §5.3.
- https://github.com/nihal111/WhisperClientAndroid (e25e063, no license file) -> microphone FGS + `TYPE_APPLICATION_OVERLAY`, focus event evaluation, bubble visibility policy, insertion tests; behavior reference only -> §4.1-§4.3, §5.1.
- https://github.com/zackify/flo (fd5f102, no license file) -> minimal accessibility overlay + clipboard/`ACTION_PASTE`; behavior reference only -> §4.1, §5.1.
- https://github.com/trevornk/ramblr (10b3834, GPLv3) -> 5-min idle unload + trim policy, 15 s VAD segments, direct-write-before-paste `DictationTextWriter`, read-back/caret nudge, WebView/Compose quirks, password guard, candidate scan; design reference only -> §3.2, §3.7, §4.2, §5.2-§5.4, §7.
- https://developer.android.com/about/versions/12/behavior-changes-all#untrusted-touch -> `TYPE_APPLICATION_OVERLAY` is untrusted for pass-through; accessibility/IME windows trusted; 0.8 opacity default -> §4.2.
- https://developer.android.com/develop/background-work/services/fgs/service-types -> microphone FGS requires `RECORD_AUDIO`, cannot start from background/`BOOT_COMPLETED` without exemptions -> §4.2.
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start -> SAW background-start exemption, Android 15 visible-overlay condition, while-in-use FGS restrictions -> §4.2.
- https://developer.android.com/ndk/guides/neuralnetworks -> NNAPI deprecated in Android 15; docs expect CPU backend for most devices, recommend TF Lite GPU for performance -> §3.3.
- https://support.google.com/googleplay/android-developer/answer/10964491 -> `isAccessibilityTool` declaration/prominent disclosure if Play distribution is added -> §5.1, §11.
- https://huggingface.co/ggerganov/whisper.cpp (MIT weights) -> official GGML model files, sizes and SHA-256 (`ggml-base.bin` 147,951,465 B / `60ed5bc3...`) -> §3.5, Appendix A.

## Technical notes

- https://github.com/SYSTRAN/faster-whisper/discussions/431 - model unload with del + gc (desktop reference; native free needed on Android) -> ModelManager rationale.
- https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/csrc/session.cc - NNAPI provider availability + API 27 caveat -> Option B analysis.
- https://github.com/ggml-org/whisper.cpp/blob/master/ggml/src/ggml-vulkan/ggml-vulkan.cpp - Vulkan backend registration catches init exceptions and unregisters; 16-bit storage check -> CPU fallback safety.
- https://search.nixos.org/packages?channel=unstable&show=androidndk - nixpkgs `androidndk` 27.0.12077973 available on aarch64-linux (checked with `nix eval`) -> B1 build harness.
- https://search.nixos.org/packages?channel=unstable&show=shaderc - nixpkgs `shaderc` 2026.1 provides host `glslc` for Vulkan shader generation on aarch64 -> B1/E1 build.
