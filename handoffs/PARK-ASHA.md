# PARK-ASHA — 2026-10-26 (shift 2, live)

Branch `agent/u2-states`, worktree `/work/martins-whisper-worktrees/u2-states`.
Master merged in at `fbb94d7` (master tip `636b3f9`). App gate: **97 tests / 0 failures**.

## DONE (this shift, on branch unless noted)

- **U2b merged with master** (`fbb94d7`); T2 `OverlayStateMachineRobolectricTest` expectations
  updated deliberately to the new descriptions (Recording → "Listening; release to transcribe",
  Error → "Error: …").
- **U2b-2 / MFVP 4b** (`63a3d9d`): start/stop VIRTUAL_KEY ticks, inserted CONFIRM tick,
  **error = double REJECT ~90 ms apart**, synchronous state+contentDescription (visible next
  frame), and a focus-safety test (`FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL`).
  Handoff `handoffs/ASHA-5/HANDOFF.md`.
- **E2E-1 insert link** (`82b1a9e`): `DictationService` default inserter is now
  `AccessibilityTextInserter` (focused-field `ACTION_SET_TEXT`/`ACTION_PASTE` via
  `DictationAccessibilityService`) with clipboard fallback; empty text rejected. Added
  `AccessibilityInserter.insertNow` for main-thread callers. **This was the missing
  "text appears in the focused field" link.**
- **MODEL-1 review**: default model `base`, `ModelRepositoryProvider` → `<filesDir>/models`,
  `ResumableModelDownloader` SHA+resume already covered by `ModelDownloaderTest` (7) and
  `HttpModelDownloaderTest` (4). No gap found; no change.

## DONE after the first note (same shift)

- **APK-1 evidence** (`87a2bcb`): verified NORA-1's arm64 artifact — `native-code: 'arm64-v8a'`,
  `lib/{whisper,ggml,ggml-base,ggml-cpu,martins_whisper,parakeet}.so`, debug-signed, 12,902,712 B,
  sha256 `c90e356f...b7476`. Recipe on master in `.github/workflows/native-build.yml`.
  **Local native build on THIS host is impossible: uname -m = aarch64, NDK clang is x86_64
  (`Exec format error`).** CI/ubuntu x86_64 or an x86 host must build it.
- **README-1** (`87a2bcb`): README rewritten to the short MFVP page (what, install, permissions,
  use). Existing `docs/` left untouched.
- **Full gate**: `:core:test :app:testDebugUnitTest :app:assembleDebug --offline` → **137 + 97 =
  234 tests, 0 failures/errors**; default APK 8,982,513 B.

## NEXT (ordered, small)

1. **MARA: merge `agent/u2-states`** (`87a2bcb`) after re-running the gate; handoff
   `handoffs/ASHA-5/HANDOFF.md`. Branch includes U2b-2 + the accessibility insert link + README.
2. **Owner/CI**: push and confirm the native Build workflow produced the arm64 APK (Actions).
3. **PHONE-1**: on-device wireless adb run; needs Martin's phone. Fix only real bugs it exposes.

## TRAPS / NOTES

- Build env (always `--offline`): `export JAVA_HOME=/work/android-toolchain/jdk
  ANDROID_HOME=/work/android-toolchain/sdk ANDROID_SDK_ROOT=/work/android-toolchain/sdk
  GRADLE_USER_HOME=/home/node/gh-asha` (copied this session; never touch the shared home).
- `ninja` symlinks in `/work/android-toolchain/sdk/cmake/*/bin` point at
  `/home/node/.nix-profile/bin/ninja`; a refilled container loses it → `nix profile install
  nixpkgs#ninja`. But the real native-build blocker here is the **aarch64 host**: NDK
  `linux-x86_64/clang` cannot execute. Use CI or an x86 host for `-Pmartins.native=true`.
- Do **not** edit `/work/martins-whisper` main checkout; it stays clean for MARA's merge.
- `DictationService.onStartCommand` still calls `ensureForeground()` + `startOverlay()` after
  actions; tile work remains out of scope.
- MFVP scope: no tile, no extra states beyond recording/working/error, no GPU tuning, no lint,
  no release ceremony, no new docs except the README.
