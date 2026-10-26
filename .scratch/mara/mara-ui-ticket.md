# Ticket: owner DEVICE-REPORT items 2/3/4 (+ gitignore). Branch agent/mara-ui from master c62f533.
Worktree: /work/martins-whisper-worktrees/mara-ui. Read MFVP: /work/owner/MFVP.md.

Env traps (MANDATORY):
- export JAVA_HOME=/work/android-toolchain/jdk; ANDROID_HOME/ANDROID_SDK_ROOT=/work/android-toolchain/sdk; source /work/android-toolchain/scripts/env.sh; GRADLE_USER_HOME=/home/node/gh-mara-ui (mkdir, copy from /work/android-toolchain/gradle-home if gradle needs network... use --offline and a COPIED gradle-home: cp -r is allowed: cp -a /work/android-toolchain/gradle-home /home/node/gh-mara-ui)
- run tests with: gradle :core:test :app:testDebugUnitTest --offline -Pandroid.aapt2FromMavenOverride=/work/android-toolchain/sdk/build-tools/35.0.1/aapt2
- aarch64 host: NO Android build of native code, no adb, no python3 (system; there IS one via nix now), Robolectric is fine.
- wip-commit every ~10 minutes; keep handoffs/PARK-MARA-UI.md live.

DO EXACTLY THESE (nothing else):
1. Android 16 edge-to-edge insets: MainActivity (app/src/main/java/com/martins/whisper/MainActivity.kt) title draws under status bar; ModelBrowser last rows under nav bar. Apply WindowInsetsCompat systemBars padding to the root view / ScrollView. Robolectric test for padding logic if cheap; at minimum keep code simple.
2. Stale status: 'Missing: accessibility' stays until activity recreated. MainActivity already re-checks in onResume (line ~93); ALSO observe changes: re-check when the accessibility service binds/binds-off. Investigate PermissionGate/MainActivity current logic; minimal fix: re-check in BOTH onResume AND when the screen turns on/unlocks or on ACTION_SETTING_CHANGE... simplest robust: re-check in onResume AND register a ContentObserver / update on window focus regained (ViewTreeObserver onWindowFocusChanged). Prove with a Robolectric test that enabling the accessibility setting then re-focusing updates the status text.
3. Download progress: ModelBrowser.kt (app/src/main/java/com/martins/whisper/ui/ModelBrowser.kt) currently a grey button ~15 s no feedback. Show percent + bytes (e.g. "42% 30.1/147.9 MB"). The downloader already reports progress somewhere (check ModelRepository/HttpModelDownloader) - surface it. A unit test that a progress callback produces percent text.
4. .gitignore: add the line `recordings/` (and keep everything else unchanged).
CONSTRAINTS: zero scope beyond these 4; no refactors; tests must actually fail if code removed (state mutations tried); no new dependencies; public gradle not allowed (offline only).
When done: commit all on agent/mara-ui, write /work/martins-whisper-worktrees/mara-ui/handoffs/MARA-UI/HANDOFF.md with: file list, exact test commands + outputs, test-count, mutations you tried. Final message = executive summary.
