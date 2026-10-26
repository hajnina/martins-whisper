# 03 — What must the B1 build harness guarantee before it merges?

Type: grilling
Status: open
Blocked by: none

## Question

B1 has a working toolchain and a green `:core:test`, but its branch still has uncommitted wrapper/Docker/CI/docs/scripts. Decide the merge gate:

- Which files must be committed (Gradle wrapper + jar, `gradlew`, Dockerfile, CI, scripts, docs) and which must stay untracked (SDK, caches, models, keys)?
- Must `./gradlew` work from a clean checkout with only documented env vars, and must the build be reproducible offline with a warm cache?
- What is the supported build host matrix: this aarch64 container, x86_64 Docker/CI, or both? What is explicitly unsupported?
- What evidence (command, output, APK path/size) is required in B1's handoff before merging?
- How do we avoid clobbering the sibling PM's in-flight B1 worktree?

Output: merge checklist + exact commands for the PM to verify.
