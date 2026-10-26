# PARK — NORA (native build / CI), 2026-10-26

## DONE

- **N1 CI fix + native APK** (merged to master, verified ancestor):
  - `b37577d` — bypass failing `android-actions/setup-android@v3`, native workflow with
    runner NDK/CMake, CPU-first default, JNI/APK check scripts, device smoke test.
  - `8938610` — robust sdkmanager discovery + `--sdk_root`.
  - `89bc35f`, `0bd7e8d` — NORA-1 handoff + APK signature evidence.
  - Local real arm64 APK built and verified:
    `/work/martins-whisper-worktrees/n1-native/app/build/outputs/apk/debug/app-debug.apk`
    sha256 `c90e356f6e65e82722f6a29b35ae2d661244f68b53c806820b3c4d5c751b7476`,
    12,902,712 bytes, arm64-v8a only, stripped whisper/ggml libs, JNI exports present,
    no model bundled, debug-signed. Gate then: 141 tests / 0 failures.
- **N2 intentionally dropped per MFVP scope.** Branch `agent/n2-release` @ `c3939d1`
  contains a parked refactor (`scripts/native-ci-sdk.sh`) and has the release workflow
  deleted again; the tag/draft-release idea is NOT in any merged tree. Do not merge this
  branch for the critical path unless its CI change proves green and useful.
- Auto-commits by ratewatch: `f5f600e`, master `9ead9ff` (park checkpoints). Nothing of
  ours was left uncommitted. All worktrees + main report clean.

## RUNNING

- Nothing of mine. I launched no worker sessions. The last local Gradle gate process was
  killed; `pgrep -f gradle` is empty.
- No post-merge GitHub Actions run was observed before the park (public API last showed
  the pre-merge failures `40378749976` / `40378749927` on `2dbac3e`, both dying at the old
  `android-actions/setup-android@v3` step).

## NEXT (ordered small tickets, after refill)

1. **CI green on master (critical path item 1).** Read runs unauthenticated:
   `curl -s 'https://api.github.com/repos/hajnina/martins-whisper/actions/runs?per_page=5'`.
   Report run ids. Per job steps:
   `curl -s .../actions/runs/<id>/jobs | jq -r '.jobs[].steps[] | [.number,.name,.conclusion] | @tsv'`.
   Logs endpoint is 403 without auth; owner must paste failed-step logs if needed.
   If red, fix only the failing workflow step; re-check both `Android CI` and
   `Android native build` until green.
2. **Native artifact proof.** Download `martins-whisper-native-debug-<sha>` from the green
   native run; verify sha256, arm64-v8a only, no bundled model; install with
   `adb install -r` on an arm64 phone.
3. **On-device engine smoke (MFVP).** Push `ggml-tiny.bin` to app `files/models/` or use the
   app download; run `NativeWhisperSmokeTest`; then the real flow: bubble → speak → stop →
   text inserted in a focused field.
4. Only after 1–3: fix whatever the phone exposes (input focus, mic, model path). No new
   features outside MFVP.

## TRAPS LEARNED

- `gh` is unauthenticated in the worker; the repo is public, so run/job metadata works via
  `curl api.github.com`, but `/logs` returns 403. Owner must fetch raw logs.
- A fresh worktree has no `app/src/main/cpp/whisper.cpp` (gitignored): run
  `scripts/fetch-whisper-cpp.sh` before any `-Pmartins.native=true` build.
- Local aarch64 native build needs the community NDK r30 plus two sandbox-only hacks
  (patchelf to newer glibc, `prebuilt/linux-x86_64 -> linux-aarch64`). CI does not need them.
- Use a private `GRADLE_USER_HOME` (`/home/node/gh-nora`) and `--offline`; the shared Gradle
  home has cross-agent locks.
- CI pins are explicit: `-Pmartins.ndkVersion=27.3.13750724`,
  `-Pmartins.cmakeVersion=3.31.5`, Vulkan off. Don't rely on runner defaults silently.
- `sdkmanager --install` is a no-op for present packages; `scripts/native-ci-sdk.sh` was
  exercised locally on JDK 17 and exits 0.
- The shared git config authored the N2 merge as `PM`; commits are still NORA's branch only.
- Do not push/merge/release: owner holds credentials; MARA merges.

## FILES / BRANCHES

- `agent/n1-native` @ `0bd7e8d` (merged).
- `agent/n2-release` @ `c3939d1` (parked, unmerged, release workflow deleted).
- `handoffs/NORA-1/HANDOFF.md`, `handoffs/NORA-2/HANDOFF.md` may be absent — N2 was dropped;
  this file is the authoritative park note.
