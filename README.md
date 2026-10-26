# Martins Whisper

Local-only, hold-to-talk dictation for Android. Tap the floating bubble in any app, speak,
tap again, and the transcript is inserted into the focused text field. Whisper runs on-device
via whisper.cpp; audio and text never leave the phone. No account, no telemetry.

License: Apache-2.0 (see `LICENSE`).

## What you need

- An **arm64** Android 10+ phone.
- An APK built with the native engine (`-Pmartins.native=true`). The default debug APK from a
  plain `assembleDebug` has no `whisper.cpp` libraries and cannot transcribe.
- About 250 MB free storage for the default `base` model.

## Install

```bash
./gradlew :app:assembleDebug -Pmartins.native=true --offline
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The APK is debug-signed; sideloading is expected (no Play Store build yet).

## Grant access

Open the app and use the **Grant** button on each row:

1. **Microphone** - records only while the bubble is held.
2. **Display over other apps** - draws the bubble above the app you are typing in.
3. **Accessibility** (Martins Whisper text insertion) - inserts the transcript at the cursor.
4. **Notifications** (recommended, Android 13+) - keeps the foreground service visible.

Without accessibility the transcript is copied to the clipboard instead.

## Use it

1. In the app, download the default **Base** model (one tap, resumable, SHA-256 checked).
2. Turn the floating bubble on.
3. Switch to any app and focus a text field.
4. Tap the bubble, speak, tap again. It shows recording/working/error and ticks on start, stop,
   insert; an error is a distinct double tick. The text lands in the focused field.

Deeper guides (build, models, permissions, troubleshooting) live in `docs/`.
