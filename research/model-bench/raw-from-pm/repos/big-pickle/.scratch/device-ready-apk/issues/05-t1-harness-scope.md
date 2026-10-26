# 05 — Which test layers can T1 deliver without KVM, and what waits for device?

Type: grilling
Status: open
Blocked by: 03, 04

## Question

The sandbox has no `/dev/kvm` and is aarch64. Decide what T1 actually delivers:

- Layer 1: existing JVM/Robolectric suites as the merge gate — what must be added (fake audio source, fake `SpeechEngine`, end-to-end chunk→VAD→merge→insertion-plan test)?
- Layer 2: instrumentation tests that compile and are documented but cannot run here — which flows (overlay, accessibility, permissions, service lifecycle) and how are they kept green-on-device later?
- Layer 3: attempted emulator/Docker path — is a software-emulated x86_64 runner worth one timeboxed try, or explicitly out of scope until hardware?
- What is the pass/fail command list for G1, and what is documented as device-only?

Output: T1 scope, test inventory, exact commands, device-only checklist.
