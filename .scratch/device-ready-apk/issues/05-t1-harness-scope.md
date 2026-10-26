# 05 — Which test layers can T1 deliver without KVM, and what waits for device?

Type: grilling
Status: resolved
Blocked by: 03, 04

## Question

The sandbox has no `/dev/kvm` and is aarch64. Decide what T1 actually delivers:

- Layer 1: existing JVM/Robolectric suites as the merge gate — what must be added (fake audio source, fake `SpeechEngine`, end-to-end chunk→VAD→merge→insertion-plan test)?
- Layer 2: instrumentation tests that compile and are documented but cannot run here — which flows (overlay, accessibility, permissions, service lifecycle) and how are they kept green-on-device later?
- Layer 3: attempted emulator/Docker path — is a software-emulated x86_64 runner worth one timeboxed try, or explicitly out of scope until hardware?
- What is the pass/fail command list for G1, and what is documented as device-only?

Output: T1 scope, test inventory, exact commands, device-only checklist.

## Answer

**Layer 1 — merge gate, must run here (JVM/Robolectric):**

- Keep the existing suites green: `:core:test` (VAD, chunker, merger, normalizer, insertion planner, ASR model layer) and `:app:testDebugUnitTest` (settings, overlay geometry, service state, permissions).
- Add one fake-audio → fake-`SpeechEngine` end-to-end test in `core` or `app`: feed synthesized frames, exercise VAD/chunk finalize/merge, assert the transcript and that `markUsed`/idle-unload state transitions fire. No real model, no device.
- Add a Robolectric service/overlay smoke test for start/stop and state flow if the existing tests do not cover it.
- Commands: `./gradlew :core:test :app:testDebugUnitTest` and `./gradlew :app:assembleDebug`; all must be green and counted.

**Layer 2 — device-only, compile now, run at H1:**

- Instrumentation tests for overlay focus retention, accessibility insertion/read-back, password-field refusal, permission denial paths, reboot re-arm notification, and real model load/transcribe.
- Gate: `./gradlew :app:assembleDebugAndroidTest` must build. They are documented as "not run in this sandbox" and become part of the H1 checklist; do not fake a pass.
- Any instrumentation test that needs a real model is marked `@LargeTest`/documented so CI can skip it.

**Layer 3 — emulator:** no `/dev/kvm` and aarch64 host. Do not spend more than one timeboxed attempt, and do not block T1/G1 on it. Record it as unsupported here; the device (ticket 09) or an x86_64 KVM runner can run Layer 2 later.

**Pass/fail for G1:** Layer 1 green + `assembleDebug` + `assembleDebugAndroidTest` built = T1 pass. Device execution of Layer 2 is H1, not T1.
