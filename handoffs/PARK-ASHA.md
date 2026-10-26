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

## RUNNING at this note

- **APK-1 local native build**: `./gradlew :app:assembleDebug -Pmartins.native=true
  -Pmartins.vulkan=false --offline` in the u2-states worktree, log `/tmp/asha-native-build2.log`.
  First attempt failed on a dangling `ninja` symlink; fixed by `nix profile install nixpkgs#ninja`.
  Result pending; if green, inspect `app-debug.apk` for `lib/arm64-v8a/*.so`.

## NEXT (ordered, small)

1. **APK-1**: finish native build; `unzip -l` + `apksigner verify` + `aapt2 dump badging` evidence
   for arm64 `.so`; DO NOT add release/signing workflow (NORA-1 covers CI).
2. **README-1**: one short README (what, install, 3 grants: mic + overlay + accessibility, use).
3. **PHONE-1**: on-device wireless adb run; needs Martin's phone (owner).
4. Hand the branch to MARA: gate = `:core:test :app:testDebugUnitTest` green + the flow test names.

## TRAPS / NOTES

- Build env (always `--offline`): `export JAVA_HOME=/work/android-toolchain/jdk
  ANDROID_HOME=/work/android-toolchain/sdk ANDROID_SDK_ROOT=/work/android-toolchain/sdk
  GRADLE_USER_HOME=/home/node/gh-asha` (copied this session; never touch the shared home).
- `ninja` symlinks in `/work/android-toolchain/sdk/cmake/*/bin` point at
  `/home/node/.nix-profile/bin/ninja`; a refilled container loses it → `nix profile install
  nixpkgs#ninja` before native builds.
- Do **not** edit `/work/martins-whisper` main checkout; it stays clean for MARA's merge.
- `DictationService.onStartCommand` still calls `ensureForeground()` + `startOverlay()` after
  actions; tile work remains out of scope.
- MFVP scope: no tile, no extra states beyond recording/working/error, no GPU tuning, no lint,
  no release ceremony, no new docs except the README.
