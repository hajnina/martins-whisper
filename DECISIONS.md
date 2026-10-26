# Architecture Decisions

## ADR-001: Target Android with Kotlin (2026-10-26)

Status: Accepted.
Context: User corrected the target: Android phone/device, Kotlin; Linux desktop work discarded.
Decision: Native Android app in Kotlin; minSdk 29, targetSdk 35, arm64-v8a primary.
Consequences: Overlay/input use Android platform APIs; GPU via Android-native backends; emulator/build constraints differ.

## ADR-002: Classic Android Views, not Compose, for the overlay (2026-10-26)

Status: Proposed (pending U1 spike).
Decision: Use `WindowManager` + a custom `View`/Canvas for the bubble, XML/programmatic Views for settings. Avoid Compose for the overlay to keep the service simple and non-focusable.
Consequences: Smaller dependency graph, fewer lifecycle surprises; less fancy UI.

## ADR-003: Insertion via AccessibilityService, overlay stays non-focusable (2026-10-26)

Status: Accepted.
Decision: Overlay window uses `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL`, so the target field keeps focus. `DictationAccessibilityService` inserts via `ACTION_SET_TEXT`, falling back to clipboard + `ACTION_PASTE`, then clipboard+toast.
Consequences: User must enable accessibility; some apps need fallbacks. Accessibility policy risk for Play Store (sideload OK).

## ADR-004: ASR engine - whisper.cpp + Vulkan vs sherpa-onnx (2026-10-26)

Status: **Open; research/build spike R1 + E1.**
Options:
- A) Vendor whisper.cpp, build JNI/NDK with `GGML_VULKAN=1`; direct GPU, GGML models, full control. Risk: native build on this aarch64 sandbox; Vulkan speedup varies.
- B) sherpa-onnx AAR with Whisper ONNX, `provider=nnapi|cpu`; prebuilt native libs, easier build. Risk: NNAPI GPU delegation is device-dependent/deprecated; GPU story weaker.
- C) Both behind `SpeechEngine`; default A on Vulkan devices, B fallback.
Decision: pending evidence. Interim contract: `SpeechEngine` interface in `core`.
Consequences: Model format/catalog differs; decide before final packaging.

## ADR-005: VAD chunking with overlap + token-boundary dedupe (2026-10-26)

Status: Accepted.
Decision: 20 ms frames, adaptive energy VAD, 500 ms silence finalize, 25 s max chunk, 250 ms overlap on forced splits, merge by suffix/prefix token dedupe.
Consequences: No external VAD dependency in v1; may need Silero/WebRTC for noisy environments later.

## ADR-006: Non-focusable overlay + no direct text injection (2026-10-26)

Status: Accepted.
Decision: Never use an invisible IME or root; rely on accessibility. If accessibility is off, the app degrades to clipboard + notification.
Consequences: No root required; limited to accessibility-exposed fields.
