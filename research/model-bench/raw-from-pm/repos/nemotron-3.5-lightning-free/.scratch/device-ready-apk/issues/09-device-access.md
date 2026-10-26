# 09 — Can we obtain a physical Android device and ADB access?

Type: task
Status: open
Blocked by: none

## Question

H1/H2 cannot start without hardware. Resolve the prerequisite:

- Which device can Martin provide (model, Android version, RAM, GPU vendor), and is it available now or later?
- Can it be reached over ADB from a container/host (USB passthrough, `adb connect` over Wi-Fi), or must Martin run commands and paste output?
- If no device: is there an x86_64 KVM runner available instead, and does that satisfy H1/H2 or only T1?

This is HITL: ask Martin in `owner/ASKS.md` if the answer is not already known. Record the device profile and access method; the support matrix and H2 thresholds depend on it.
