# Install Martins Whisper (sideload)

v1 is **sideload only**. There is no Play Store listing, account or sign-up.
You need the APK file and, for the operator path, a computer with `adb`.

Two readers:

- **User** (no terminal): install the APK file that someone sent you, then follow
  [PERMISSIONS.md](PERMISSIONS.md). The APK must include the native library for
  real dictation; without it the app can download models but not transcribe.
- **Operator** (terminal): build or receive the APK, install it with `adb`.

> **Status in 0.1.0:** the app installs, the settings/permission screens work, the
> model catalog has in-app download/import/delete, and `EngineFactory` loads the
> selected model through the real `WhisperCppEngine`. The default APK has **no
> native libraries** (`-Pmartins.native=false`), so real transcription needs an
> arm64 build with `-Pmartins.native=true`. Device steps below are **UNVERIFIED
> (device, H1-blocked)** because this sandbox has no phone.

## What you need

- An **arm64** Android phone or tablet, **Android 10 or newer** (API 29+).
- **6 GB RAM** minimum for the `tiny`/`base` models, **8 GB recommended** for `small`.
- About **250 MB free storage** for `base` plus the app.
- The `app-debug.apk` file (debug-signed; see [BUILD.md](BUILD.md)).

## Operator path (recommended for v1)

1. Build the APK (or take the one from the build handoff):

   ```bash
   cd /work/martins-whisper-worktrees/d1-docs
   source scripts/env.sh
   ./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --offline
   ls -l app/build/outputs/apk/debug/app-debug.apk
   ```

   **VERIFIED (host, 2026-10-26):** `BUILD SUCCESSFUL`, **135 JVM tests, 0
   failures**, APK `9,163,557` bytes. The SHA-256 changes on every rebuild
   (debug-signing timestamp); compare only against the hash printed for the same
   build, not across builds.

   That command builds the **default APK with no native engine**. For real
   dictation build the arm64 native variant (needs NDK `27.0.12077973`):

   ```bash
   ./gradlew :app:assembleDebug --offline -Pmartins.native=true
   unzip -l app/build/outputs/apk/debug/app-debug.apk | grep libmartins_whisper
   # expected: lib/arm64-v8a/libmartins_whisper.so
   ```

   **UNVERIFIED (host):** this sandbox has no NDK, so the native variant was not
   built here. The default APK contains no `lib/` entries (**VERIFIED**).

2. Enable **Developer options → USB debugging** on the phone and accept the RSA
   prompt. Check the connection:

   ```bash
   adb devices
   # emulator-5554   device     <- the word must be "device"
   ```

3. Install (keeps app data on reinstall):

   ```bash
   ./scripts/install-apk.sh
   # or: adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

   **VERIFIED (script):** `--help` and `bash -n`; missing adb/device exits with a
   clear message. **UNVERIFIED (device):** the actual `adb install`.

4. Open **Martins Whisper** and grant the **three required** permissions
   (microphone, display over other apps, accessibility insertion); notifications
   are optional but needed for the ongoing status. See
   [PERMISSIONS.md](PERMISSIONS.md).

5. Make a model available. The normal path is in the app: tap a catalog row,
   then **Download** or **Import file**; wait for **Installed · selected**. On a
   debug APK with no network you can still push one over adb:

   ```bash
   ./scripts/push-model.sh /path/to/ggml-base.bin
   ```

   See [MODELS.md](MODELS.md) for the in-app flow, downloads, hashes and storage.

6. In the app, the **Model** section shows the catalog with **Installed** /
   **Not installed** and the selected row. Tap **Start overlay**, open another
   app, and hold the floating bubble; the first press loads the selected model.
   **Diagnostics** explains a typed failure. On the default (native-less) APK the
   engine reports *Native whisper.cpp is not available*; install a
   `-Pmartins.native=true` arm64 build to dictate for real.

   The shortest end-to-end path is [FIRST-DICTATION.md](FIRST-DICTATION.md).

## User path (APK file, no computer)

These steps vary slightly by Android version and OEM; the wording below is the
stock-Android path.

1. Copy `app-debug.apk` to the phone (USB file transfer, cloud drive, or a
   messaging app that allows APK files).
2. Tap the file in **Files** (or your file manager). If Android blocks it, choose
   **Settings** on the prompt and enable **Allow from this source** /
   **Install unknown apps** for the app you are installing from (Files, Chrome,
   the messaging app). This is a per-source permission; only grant it to a source
   you trust.
3. Tap **Install**, then **Open**.
4. Grant the permissions in [PERMISSIONS.md](PERMISSIONS.md).
5. A model must still be present. Tap the **Base (multilingual)** row, then
   **Download** (network) or **Import file** (a `ggml-base.bin` you already
   have). The row must say **Installed · selected**. If the phone has no network
   and you have no file, ask the person who gave you the APK to push it with
   [MODELS.md → adb push](MODELS.md#operator-fallback-adb-push).

> Android may warn "unsafe app blocked" or "app from unknown source". That is the
> normal sideload warning, not a virus scan result. Only install an APK you got
> from someone you trust. This app has no Play Store signature.

## First run checklist (device, H1)

- [ ] App opens to the **Martins Whisper** single scrolling screen (Permissions,
      Overlay, Model, Recognition, Diagnostics).
- [ ] Microphone shows **Granted** after the system prompt.
- [ ] Display over other apps shows **Granted**.
- [ ] Accessibility insertion shows **Granted** after enabling the service.
- [ ] Notifications shows **Granted** (Android 13+); optional, needed for the
      ongoing status.
- [ ] A model file is listed under `files/models` and the row says
      **Installed · selected** (doctor script or [MODELS.md](MODELS.md)).
- [ ] **Start overlay** shows the floating bubble above another app.
- [ ] Holding the bubble records; releasing returns a transcript (native arm64
      build only).

**UNVERIFIED (device, H1-blocked):** this checklist has not been run on a phone
in this environment.

## Upgrade

Reinstall over the old version; app data and models are kept:

```bash
./scripts/install-apk.sh
```

The APK is debug-signed, so Android accepts the update only if the signing key is
the same. If install fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, uninstall
first (this deletes models and settings) and install again. Do not mix an APK
built on another machine/key.

## Uninstall and cleanup

Phone: **Settings → Apps → Martins Whisper → Uninstall**.

With adb (also removes app-private models and settings):

```bash
adb uninstall com.martins.whisper
```

If the overlay is still visible, stop it first from the app (**Stop overlay**) or
from the notification (**Stop**). Uninstalling removes the app, its models under
`/data/data/com.martins.whisper/files/models/`, and its settings. Nothing is left
on a server, because nothing was ever uploaded.
