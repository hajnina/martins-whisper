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
