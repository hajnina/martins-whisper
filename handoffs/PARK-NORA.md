# PARK — NORA (native build / CI), 2026-10-26 (N3 session)

## DONE

### N1 (previous session, merged)
- Native CI workflow fixed and merged (`b37577d`, `8938610`, `0bd7e8d`); CI green on
  master `636b3f9` (runs 40380372902 native / 40380372979 CI, both success).
- Real arm64 APK built locally, sha256 `c90e356f…b7476`, 12.9 MB, JNI exports present.

### N3 this session (branch `agent/n3-native`, worktree `/work/martins-whisper-worktrees/n3-native`)
- **N3-1 16 KB pages:** `app/src/main/cpp/CMakeLists.txt` now passes
  `-Wl,-z,max-page-size=16384` via `add_link_options()` for *every* target. CI pins NDK r27,
  which still defaults to 4 KB; r28+ would do it implicitly.
- **N3-2 artifact proof:** `scripts/native-apk-check.sh` rewritten to verify, without a device:
  AArch64 ELF64 shared objects; stripped; every LOAD segment align >= 0x4000; every `DT_NEEDED`
  resolved inside the APK or by an Android system lib (missing `libc++_shared.so` fails); all 14
  Kotlin `external fun`s exported by `libmartins_whisper.so`; zip `.so` entries 16 KB zipaligned;
  ABI/size/no-model as before. Verified against the existing N1 APK: PASS, 14/14 JNI, 16 KB.
- **N3-3 model contract:** documented in `docs/native-build.md` (filesDir/models/<id>.bin, GGML
  magic, sha256, nativeInit absolute path). `device-smoke.sh` enforces GGML magic + device byte
  count + resolved absolute path.
- **N3-4 one-command device smoke:** `scripts/device-smoke.sh` = build native APK -> apk check ->
  install app+test APK -> push model (catalog SHA) -> verify contract -> run
  `NativeWhisperSmokeTest`. Supports `--wireless ADDR`, `--serial`, `--model`, `--no-build`.
  Mock-adb dry run PASS (two tests).

## RUNNING

- Local full gate + native rebuild in worktree `n3-native` with the new CMake flag
  (`/tmp/n3-build.log`): to be reported in `handoffs/NORA-3/HANDOFF.md`.

## NEXT (after this session)

1. Owner: download the CI native artifact once a green run exists for the N3 merge and run
   `scripts/device-smoke.sh --no-build --apk <artifact> --model ggml-tiny.bin` on the phone.
2. On a 16 KB-page device, confirm `getconf PAGE_SIZE` = 16384 and the smoke still passes.
3. Then the real MFVP flow (bubble -> speak -> text inserted); fix only what the phone exposes.

## TRAPS LEARNED

- CI NDK r27 does **not** default to 16 KB ELF alignment; the explicit link flag is required.
  `zipalign -c -P 16 4` checks the zip side (AGP already aligns).
- `readelf -l` wraps long lines; use `-W`, otherwise the LOAD alignment column is on the next
  line and parsing $NF silently reads 0.
- GGML magic is a little-endian uint32: `ggml` appears as bytes `6c6d6767` ("lmgg"), not
  `67676d6c`.
- Local aarch64 NDK r30 needs `LD_LIBRARY_PATH=<ndk>/toolchains/llvm/prebuilt/linux-aarch64/lib`
  because the patched `ld.lld` needs `libxml2.so.2` (it is in the NDK lib dir).
- `gh` is not installed/authenticated here; artifact download must be done by the owner.
- Do not push/merge/release: owner holds credentials; MARA merges.

## FILES / BRANCHES

- Branch `agent/n3-native`, worktree `/work/martins-whisper-worktrees/n3-native`.
- Changed: `app/src/main/cpp/CMakeLists.txt`, `scripts/native-apk-check.sh`,
  `scripts/device-smoke.sh` (new), `docs/native-build.md`, this file, `handoffs/NORA-3/*`.
- Previous: `agent/n1-native` @ `0bd7e8d` (merged). `agent/n2-release` @ `c3939d1` (parked,
  not needed for MFVP).
