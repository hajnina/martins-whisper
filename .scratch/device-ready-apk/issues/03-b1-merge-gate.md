# 03 — What must the B1 build harness guarantee before it merges?

Type: grilling
Status: resolved
Blocked by: none

## Question

B1 has a working toolchain and a green `:core:test`, but its branch still has uncommitted wrapper/Docker/CI/docs/scripts. Decide the merge gate:

- Which files must be committed (Gradle wrapper + jar, `gradlew`, Dockerfile, CI, scripts, docs) and which must stay untracked (SDK, caches, models, keys)?
- Must `./gradlew` work from a clean checkout with only documented env vars, and must the build be reproducible offline with a warm cache?
- What is the supported build host matrix: this aarch64 container, x86_64 Docker/CI, or both? What is explicitly unsupported?
- What evidence (command, output, APK path/size) is required in B1's handoff before merging?
- How do we avoid clobbering the sibling PM's in-flight B1 worktree?

Output: merge checklist + exact commands for the PM to verify.

## Answer

**Merge gate (all must be true before `agent/b1-build` merges):**

1. **Committed, clean branch.** The B1 owner commits the wrapper, Docker/CI, scripts, docs, `.dockerignore` and `.gitignore` changes on `agent/b1-build`; `git status` is clean; a handoff exists at `handoffs/B1-build/HANDOFF.md` with the evidence below. The PM never edits or commits inside the sibling PM's B1 worktree.
2. **Tracked vs ignored.** Track: `gradlew`, `gradlew.bat`, `gradle/wrapper/{gradle-wrapper.jar,gradle-wrapper.properties}`, root/module build files, `Dockerfile.build`, `.dockerignore`, `.github/workflows/*`, `scripts/env.sh`, build/install docs. Ignore: `/work/android-toolchain`, Gradle homes/caches, `local.properties`, `**/build/`, APK/AAB, model files (`.bin`, `.ggml`, `.gguf`, `.onnx`), keystores, logs.
3. **Clean-checkout build.** From a fresh checkout and documented env (`JAVA_HOME`, `ANDROID_HOME`, optional `GRADLE_USER_HOME`), `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --offline` succeeds with a warm cache; a cold run may use network and must be documented. No manual file copying, no writes to `~/.nix-profile`, no shared-cache lock deletion.
4. **Host matrix stated.** Supported: this aarch64 container (JDK 17 + SDK 35 + aapt2 override) and x86_64 Docker/CI (`Dockerfile.build`). Explicitly unsupported for v1: Windows/macOS host builds, NDK/Vulkan builds on aarch64, and KVM emulators.
5. **Evidence in handoff.** Exact commands, `BUILD SUCCESSFUL`, test counts (need 74+), APK path/size/SHA-256, JDK/Gradle/SDK versions, clean `git status`, and a no-secrets statement. `git diff origin/master` contains no keys/tokens.
6. **PM verification and merge order.** PM fetches the branch into a scratch worktree, runs item 3 itself, runs `code-review` on the diff, then merges `--no-ff` into `master`, resolves `.gitignore`/wrapper conflicts in master only, re-runs the full gate, and updates KANBAN/LOGBOOK/STATUS.

**Abort/return conditions:** uncommitted work in the B1 worktree, failing tests, a wrapper that needs undocumented manual setup, secrets, build outputs tracked, or a handoff without real output. Do not merge on a verbal claim.
