# REFERENCES

One line per source: URL/repo -> what we took -> where it landed. Maintained by research/integration agents.

## Product / UX

- https://docs.wisprflow.ai/articles/6409258247-starting-your-first-dictation - push-to-talk, release inserts; tap under ~0.5 s cancels -> SPEC 3.7 state machine.
- https://docs.wisprflow.ai/articles/2772472373-what-is-flow - overlay/any-app insertion, cleanup expectations -> product framing.

## Android prior art (to be filled by R1)

- TBD: futo-org/voice-input - offline whisper.cpp Android, model handling, long-form chunking.
- TBD: whisper.cpp Android example - JNI + CMake + Vulkan flags.
- TBD: sherpa-onnx Android - prebuilt AAR / NNAPI / Whisper ONNX alternative.
- TBD: accessibility overlay examples - TYPE_ACCESSIBILITY_OVERLAY / TYPE_APPLICATION_OVERLAY non-focusable.
- TBD: clipboard/ACTION_SET_TEXT insertion battle stories.

## Technical notes

- https://github.com/ggml-org/whisper.cpp/issues/2370 - Vulkan on Android build instructions; Vulkan speedup can be modest; minSdk 29 note.
- https://github.com/SYSTRAN/faster-whisper/discussions/431 - model unload with del + gc (desktop reference; native free needed on Android).
- https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/csrc/session.cc - NNAPI provider availability + API 27 caveat.

## E1 (ASR/model layer)

- https://github.com/ggml-org/whisper.cpp (pinned 231638a0717b7822784297e5980965ffcec05810) -> whisper.h API, Android CMake/JNI patterns, GGML format -> `app/src/main/cpp`, `scripts/fetch-whisper-cpp.sh`.
- https://github.com/ggml-org/whisper.cpp/tree/master/examples/whisper.android -> CMake subdirectory/ggml wiring and JNI segment/text approach (adapted, not copied verbatim) -> `app/src/main/cpp/CMakeLists.txt`, `whisper_jni.cpp`.
- https://huggingface.co/ggerganov/whisper.cpp -> model URLs, LFS sizes and SHA256 (`x-linked-etag`); tiny/base hashes re-verified with local `sha256sum` -> `ModelCatalog.kt`.
- https://github.com/futo-org/voice-input -> Android model settings/download UX reference -> design review only (no code copied).
- https://github.com/kafkasl/phone-whisper (reference clone) -> model downloader test shape and path-traversal/verification ideas -> `ModelDownloaderTest.kt` pattern.
- https://developer.android.com/ndk/downloads -> official NDK host package is linux-x86_64; aarch64 blocker evidence -> `docs/native-build.md`.
- https://github.com/kaspersigi/android-ndk-r30-linux-aarch64 -> community aarch64 NDK investigated by B1; not vendored -> blocker note.
- https://github.com/k2-fsa/sherpa-onnx -> AAR/NNAPI evaluation -> `docs/adr-004-sherpa-onnx-evaluation.md`.
