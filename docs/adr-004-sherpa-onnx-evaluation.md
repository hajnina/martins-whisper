# ADR-004 evaluation: sherpa-onnx AAR fallback (not implemented)

Status: evaluated, deferred. whisper.cpp remains the primary engine.

## Context

ADR-004 leaves the ASR engine open between:

- A) vendored whisper.cpp + JNI + Vulkan (implemented by E1)
- B) sherpa-onnx prebuilt AAR with Whisper ONNX, `provider=nnapi|cpu`
- C) both behind `SpeechEngine`

## Findings from the sherpa-onnx checkout

- sherpa-onnx ships prebuilt Android AARs and Kotlin bindings (`OfflineRecognizer`, `OfflineStream`,
  `Vad`). Integration is much simpler than the NDK path and avoids the aarch64 NDK blocker.
- Whisper models are split ONNX artifacts (encoder/decoder/tokens), so the GGML catalog used by
  whisper.cpp is not reusable. A separate catalog and storage layout would be required.
- Acceleration is `cpu` or `nnapi`; there is no Vulkan provider. NNAPI delegation is device/driver
  dependent, often does not use the GPU, and NNAPI itself is deprecated in recent Android releases.
  This does not satisfy the "GPU by default, surface backend" requirement as reliably as Vulkan.
- Model quality and timestamps differ slightly; `SpeechEngine.Transcript` can represent both.

## Decision

Do not add sherpa-onnx in v1. Keep it as a documented fallback if the Vulkan build proves unstable
on the target device. The `SpeechEngine` contract and `ModelManager` are engine-agnostic; a
`SherpaOnnxEngine` plus its own catalog can be added later without touching the pipeline.

## Consequences

- Only GGML models are supported now (plus custom GGML files).
- No new dependency: the app does not bundle the sherpa-onnx AAR.
- If NNAPI becomes required, add a separate `ModelSpec` catalog and storage directory to avoid
  confusing GGML files with ONNX bundles.
