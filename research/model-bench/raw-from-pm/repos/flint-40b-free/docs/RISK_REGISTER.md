# Risk Register

| ID | Risk | Impact | Likelihood | Mitigation | Owner |
|---|---|---|---|---|---|
| R-01 | Android native build (whisper.cpp/NDK) impossible on aarch64 sandbox | Cannot produce APK/verify JNI here | High | B1/E1 find community toolchains or x86_64 Docker/CI; deliver buildable repo + exact commands | B1/E1 |
| R-02 | Vulkan not available/not faster on target GPU | GPU requirement unmet | Medium | Backend detection + honest status; benchmark on device; consider NNAPI/sherpa-onnx | E1/H1 |
| R-03 | Accessibility insertion fails in popular apps (Compose/WebView) | Core UX fails | Medium | ACTION_SET_TEXT + ACTION_PASTE + clipboard fallback; field matrix; on-device iterate | I1/H1 |
| R-04 | Overlay steals focus / IME opens | Text goes to wrong place | Medium | FLAG_NOT_FOCUSABLE/NOT_TOUCH_MODAL; non-focusable service window tests | U1 |
| R-05 | OEM kills foreground service | Overlay disappears | Medium | Persistent notification, START_STICKY, battery exemption onboarding, boot receiver | U1 |
| R-06 | Model download fails/large (base 142 MB) | First-run blocked | Medium | adb push, resumable download, SHA verify, clear UI | E1/D1 |
| R-07 | Idle unload doesn't free memory (native refs) | Battery/RAM waste | Medium | explicit whisper_free + null + test on device; status evidence | E1/H2 |
| R-08 | Long-hold latency/queue grows | Bad UX on slow devices | Medium | base default, GPU, chunk during hold, show queue; tiny fallback | C1/E1 |
| R-09 | Accessibility overlay policy / Play Store rejection | Distribution | High for Play | Sideload-first; document policy; not v1 | PM |
| R-10 | No emulator/KVM | No e2e in sandbox | High | Robolectric + device later; CI instrumentation on x86_64 runner | B1/T1 |
| R-11 | Privacy: transcript in clipboard/notification | Leak to clipboard managers | Low-medium | leave clipboard only when insertion fails; clear action; no logs/history | I1/U1 |
| R-12 | Floating overlay battery/GPU cost | Battery drain | Low-medium | no rendering when idle; idle unload; stop service action | U1/E1 |
