# 02 — What ASR backend proof is required for v1 sign-off (CPU-first vs Vulkan)?

Type: grilling
Status: resolved
Blocked by: 01

## Question

ADR-004 accepts whisper.cpp with Vulkan + CPU, CPU fallback, and says a CPU-first APK is acceptable if Vulkan is not green by integration. Turn that into a testable acceptance bar:

- What must the host build prove (JVM model-layer tests, fake engine, native library presence) before a device is available?
- What exactly must the device show for the backend: active backend string, fallback reason persisted, GPU-crash counter behavior, and how many failures trigger CPU default?
- Is Vulkan required for v1 sign-off, or is CPU-only with a clear "GPU unavailable" status acceptable? Who signs that off?
- What evidence goes in the handoff so the owner can trust it without running Android themselves?

Output: a short acceptance checklist, not code.

## Answer

**Host proof (required to merge E1, no device needed):**

- `:core:test` green for the ASR layer: model catalog matches ticket 01, SHA-256 verification, download failure/retry, idle unload at 300 s with an injectable clock, model swap, and a `FakeSpeechEngine` end-to-end (audio chunks → VAD → merge → transcript).
- `:app:assembleDebug` green with the `SpeechEngine` seam present. Either the arm64 whisper.cpp native libs are packaged, or the native loader fails with an explicit, typed error and the app still runs (UI shows "engine unavailable"), never a crash on startup.
- The engine pins an exact whisper.cpp revision and model revision/SHA-256; the backend preference (`auto`/`vulkan`/`cpu`) and persisted fallback reason are in the contract.

**Device proof (H1/H2, required for sign-off but not for merging the seam):**

- `doctor`/status reports: engine version, model id, selected backend (`VULKAN` or `CPU`), load time, and fallback reason if any.
- GPU is attempted only when `auto`/`vulkan` is selected. On load/transcribe failure, fall back to CPU once, persist the reason, and never crash. Increment a GPU-crash counter; after 2 consecutive GPU failures default to CPU until the user resets it.
- CPU-only is acceptable for v1 sign-off if the device has no usable Vulkan or the build is CPU-only, **provided** the UI/doctor clearly says GPU is unavailable and H2 latency is usable (thresholds from ticket 08). Vulkan is required only to claim a GPU benefit, not to ship.
- Evidence: `doctor` output, logcat excerpt showing backend choice/fallback, one real transcription, and the H2 measurements. No secrets.

**PM decision:** merge the seam + CPU path when host proof is green; do not block G1 on Vulkan. If E1 cannot build the native lib at all, merge the seam/tests only if the app degrades gracefully and the APK still installs; record the missing native path as the top known issue.
