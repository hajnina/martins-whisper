You are I1-input (v2), background implementation subagent. START WRITING CODE IMMEDIATELY. Do not install JDK/Gradle or use nix. Shared toolchain READY.

WORKTREE: /work/martins-whisper-worktrees/i1-input
Read: SPEC.md (3.6), AGENTS.md, Contracts.kt. Commit on agent/i1-input. Do not merge.

TOOLCHAIN:
- JAVA_HOME=/work/android-toolchain/jdk
- /home/node/.local/bin/gradle -p /work/martins-whisper-worktrees/i1-input :core:test --no-daemon
- Android SDK: /work/android-toolchain/sdk (source /work/android-toolchain/scripts/env.sh)

IMPLEMENT:
1. Replace TextInsertionPlanner.insert stub body in core/Contracts.kt with clamping/selection/caret logic (UTF-16 correct). Add pure JUnit tests incl. emoji/surrogates, invalid ranges, null/empty, selection at ends.
2. app/src/main/kotlin/.../accessibility/DictationAccessibilityService.kt:
   - track focused editable node + selection; ignore own package; validate.
   - insert(text): plan ACTION_SET_TEXT with selection args; fallback ClipboardManager + ACTION_PASTE; final fallback copy + toast/notification; return typed result.
   - process-wide AccessibilityInserter bridge (StateFlow connected + suspend insert), no context leaks; recycle nodes.
   - handle service enable/disable, stale nodes, multi-window, WebView/Compose limitations.
3. app/src/main/res/xml/accessibility_service_config.xml + manifest service/permission entries (U1 also edits manifest; provide a merge snippet in handoff; keep changes minimal and additive).
4. Tests: pure planner tests; decision-parsing tests; Robolectric if it works without SDK hassle. Document exact on-device matrix.
5. Run tests/build if possible, paste real output. Handoff handoffs/I1-input/HANDOFF.md + SUMMARY.txt, commit. Final message = executive summary + limits.

Timebox: 25 minutes of implementation/testing. Do not research further.
