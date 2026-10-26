You are U1-overlay (v2), background implementation subagent. START WRITING CODE IMMEDIATELY. Do not install JDK/Gradle/SDK via nix. Shared toolchain READY.

WORKTREE: /work/martins-whisper-worktrees/u1-overlay
Read: SPEC.md (3.3,3.7,3.8), AGENTS.md. Commit on agent/u1-overlay. Do not merge.

TOOLCHAIN:
- source /work/android-toolchain/scripts/env.sh
- JAVA_HOME=/work/android-toolchain/jdk
- Gradle: /home/node/.local/bin/gradle (wrapper) or /work/android-toolchain/gradle/gradle-8.9/bin/gradle
- Android SDK: /work/android-toolchain/sdk (platform 35, build-tools 35.0.1, aarch64 aapt2)
- Create local.properties: sdk.dir=/work/android-toolchain/sdk
- Build: /home/node/.local/bin/gradle -p /work/martins-whisper-worktrees/u1-overlay :app:assembleDebug --no-daemon
- If aapt2 override needed: android.aapt2FromMavenOverride=/work/android-toolchain/sdk/build-tools/35.0.1/aapt2

IMPLEMENT:
1. DictationService foreground service (microphone type, persistent notification + Settings/Stop actions, START_STICKY, StateFlow, fake/pluggable engine, press/release states, timer, RMS callback, clean stop).
2. OverlayController + MicBubbleView: TYPE_APPLICATION_OVERLAY, FLAG_NOT_FOCUSABLE|FLAG_NOT_TOUCH_MODAL|FLAG_LAYOUT_IN_SCREEN, bottom-end, draggable with persisted position, 56dp bubble, expanded pill with timer/level bars, idle/loading/processing/success/error, haptics, settings/stop affordance.
3. MainActivity onboarding/settings: mic, overlay, accessibility deep link, notifications; status; model picker (use core ModelCatalog if available, else local stub); language; GPU/CPU; idle 300s display; privacy note; start/stop overlay; SharedPreferences; BootReceiver.
4. Manifest: permissions + service + receiver (leave accessibility service to I1; note merge snippet). API 29+.
5. Tests: Robolectric for settings/permission gates/service intent if feasible; otherwise pure helper tests. Try assembleDebug and paste real output.
6. Handoff handoffs/U1-overlay/HANDOFF.md + SUMMARY.txt, commit. Final message = executive summary + build evidence.

Timebox: 30 minutes of implementation/build. Do not research further.
