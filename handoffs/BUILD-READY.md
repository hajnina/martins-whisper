# BUILD-READY #2 (MARA, 2026-10-26 ~21:40)

**Code SHA: `06b7db6`** (master, all work committed; this file is a docs-only tip on top of it).
Push master and dispatch `native-build.yml`.

## Why this build exists (DEVICE-REPORT #2)
1. **Tap-to-toggle** (the actual bug): the 12 ms AudioRecord session in the report was the
   ACTION_DOWN/UP of an `adb input tap`; the bubble was push-to-talk. Now the first tap starts and
   latches capture, the next tap stops it (MFVP: tap, speak, tap again). Drag still cancels.
2. **Bubble never moves on tap**: it used to expand 56 -> 240 dp and clamp sideways (x 952 -> ~460),
   so the second tap missed it. State changes no longer resize or move the window; state shows in
   the fixed circle (colour, icon, content description).
3. **`MartinsWhisperState` logcat tag** at every transition: tap-start/stop, capture start/stop,
   model load start/end, gate verdict, transcribe start/end, insert, cancel, fail, state class.
4. **All audio saved**: every session, including too-short/cancelled/error, writes
   `files/debug-audio/<stamp>.wav` + `<stamp>.json` sidecar; telemetry `last.json` is written for
   aborts too.
5. **Stable debug signature**: `app/debug.keystore` is committed and used by the debug build, so
   CI builds install over each other (`adb install -r`) and the downloaded model survives.

## Gate evidence (fresh, offline)
- `source /work/android-toolchain/scripts/env.sh; GRADLE_USER_HOME=/home/node/gh-mara`
- `/work/owner/tools/gradle-slot.sh :core:test :app:testDebugUnitTest :app:assembleDebug --offline`
- **BUILD SUCCESSFUL**: core 165 tests + app 139 tests, **0 failures, 0 errors**, 2 env-skips
  (host-native JNI + jfk replay, need the host native build/CI).
- APK `app/build/outputs/apk/debug/app-debug.apk` 9,054,349 B, signed by the committed key
  (SHA-256 `0aae920be5363081f479c65fd9165ecd9c523ac4bab53c55212885259275dfac`).

## Device expectations (build #2)
- Tap 1: logcat `MartinsWhisperState: tap-start ...` then `capture-start ...`; bubble turns red.
- Speak, tap 2: `tap-stop` -> `gate=speak` -> `transcribe-start/end` -> `insert ...`; text in field.
- Pull evidence after a run:
  `adb pull /sdcard/Android/data/com.martins.whisper/files/debug-audio` (or via run-as on debug)
  and `.../files/telemetry/last.json`.
- HyperOS: `appops set ... SYSTEM_ALERT_WINDOW allow` still has to be re-applied after a fresh
  install until the first launch.
