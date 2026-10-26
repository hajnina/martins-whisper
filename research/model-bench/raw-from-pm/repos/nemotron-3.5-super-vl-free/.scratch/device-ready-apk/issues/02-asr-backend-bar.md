# 02 — What ASR backend proof is required for v1 sign-off (CPU-first vs Vulkan)?

Type: grilling
Status: open
Blocked by: 01

## Question

ADR-004 accepts whisper.cpp with Vulkan + CPU, CPU fallback, and says a CPU-first APK is acceptable if Vulkan is not green by integration. Turn that into a testable acceptance bar:

- What must the host build prove (JVM model-layer tests, fake engine, native library presence) before a device is available?
- What exactly must the device show for the backend: active backend string, fallback reason persisted, GPU-crash counter behavior, and how many failures trigger CPU default?
- Is Vulkan required for v1 sign-off, or is CPU-only with a clear "GPU unavailable" status acceptable? Who signs that off?
- What evidence goes in the handoff so the owner can trust it without running Android themselves?

Output: a short acceptance checklist, not code.
