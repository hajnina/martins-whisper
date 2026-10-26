# Troubleshooting

Find the symptom, try the next step. If you are reporting a bug, attach the
secret-free report from [DOCTOR.md](DOCTOR.md) and the app version. Device-only
steps are **UNVERIFIED (H1-blocked)** until they are run on real hardware.

> **Known 0.1.0 gap:** the APK ships a fake speech engine and has no in-app model
> download screen (G1). If dictation returns placeholder text rather than your
> words, that is this gap, not a permission problem. Check
> [INSTALL.md](INSTALL.md) for the current status.

## Install

### "App not installed" / "There was a problem parsing the package"

- The file is truncated or not an APK. Rebuild, or compare SHA-256 with the
  sender: `sha256sum app-debug.apk`.
- The phone is older than Android 10 (`minSdk 29`). Check
  **Settings → About phone → Android version**.
- The phone is 32-bit or x86. v1 is arm64 only.
- The old copy was signed with a different key. Uninstall the old app, then
  install again:

  ```bash
  adb uninstall com.martins.whisper
  ./scripts/install-apk.sh
  ```

### "Install unknown apps" / "unsafe app blocked"

That is the normal sideload warning. Allow the **source app** (Files, Chrome, the
messaging app) to install unknown apps, then retry. Revoke the permission
afterwards if you do not need it.

### `INSTALL_FAILED_UPDATE_INCOMPATIBLE`

The installed APK was signed with another debug key. Uninstall (this deletes
models and settings), then reinstall. Do not mix APKs built on different
machines.

## Microphone

### "Microphone permission required" or no recording

1. Open the app; the **Microphone** row must say **Granted**.
2. **Settings → Apps → Martins Whisper → Permissions → Microphone → While using
   the app**.
3. Close other apps that hold the microphone (camera, voice recorder, a call).
   Android gives the mic to one app at a time.
4. Force-stop and reopen: **Settings → Apps → Martins Whisper → Force stop**, then
   **Start overlay**.
5. If it still fails, run `./scripts/doctor.sh` and check
   `permission.RECORD_AUDIO=granted`.

### First word is clipped

Start speaking after the bubble shows the recording state. A short pre-roll is
implemented; if it is still clipped on your device, record the case in
[DEVICE-MATRIX.md](DEVICE-MATRIX.md) — it is a device/H2 finding.

## Overlay

### The bubble does not appear

1. **Display over other apps** must say **Granted**. Use the app's **Grant**
   button; the switch is on the system screen, not in the app.
2. Press **Start overlay** in the app. The notification should say *Martins
   Whisper is ready*.
3. If the notification is hidden (Android 13+), grant notifications so you can
   see the service state and **Stop** it.
4. Some OEMs kill background services. Set the app to **Unrestricted** battery
   and allow autostart; see [PERMISSIONS.md](PERMISSIONS.md#optional-battery-optimization).

### The bubble disappears after a reboot

With **Start overlay on boot** enabled, the bubble is restored **without the
microphone** (Android 14+ restricts boot-time mic starts). Open the app once;
dictation is re-armed. If nothing is restored, check the boot switch and battery
settings.

### The bubble blocks a button / cannot be moved

Drag the bubble. Its position is persisted in app settings. If it is stuck off
screen, **Stop overlay** in the app, then **Start overlay**; the default position
is used if the saved one is invalid.

## Insertion

### Accessibility is enabled but no text appears

1. Check the app row **Accessibility insertion → Granted**. If not, tap **Grant**
   and enable **Martins Whisper text insertion**.
2. Tap the text field once before dictating, so it is focused.
3. Some fields are not editable or are inside a custom canvas; the app then falls
   back to clipboard.
4. Password/PIN fields are **refused by design**. This is not a bug.
5. If the transcript is on the clipboard, paste it manually
   (long-press → **Paste**). The app shows *Transcript copied; paste it
   manually*.

### `ACTION_SET_TEXT` fails in a WebView / Compose field

The app tries `ACTION_SET_TEXT`, then a paste action, then clipboard +
notification. If a specific app fails, add it to the insertion matrix in
[DEVICE-MATRIX.md](DEVICE-MATRIX.md) with the app version. Do not expect every
custom editor to work; this is the v1 best-effort boundary.

### Text is inserted twice or at the wrong place

Record the target app and field in [DEVICE-MATRIX.md](DEVICE-MATRIX.md). Workaround:
place the cursor before dictating and use a simple `EditText` if possible.

## Models

### "Model missing" / dictation says the model is not installed

0.1.0 has no download screen. Push a model on the operator path:

```bash
./scripts/push-model.sh ggml-base.bin
```

Then select **Base (multilingual)** in **Settings → Model**. Verify the file
exists with `./scripts/doctor.sh` (it lists `files/models/<id>.bin`).

### Push fails with `package not debuggable`

`run-as` only works on a debuggable APK. Install the debug APK from
[BUILD.md](BUILD.md) (or use the in-app import once G1 lands). A release build
does not allow adb access to app-private storage.

### Push fails with `SHA-256 mismatch`

The download is corrupt or is not the pinned file. Re-download from the pinned
revision in [MODELS.md](MODELS.md) and compare the full hash. Do **not** force
`--no-verify` for a catalog model.

### "Out of storage" / download cannot finish

A `base` model needs about 0.15 GB plus a little working space; `small` about
0.5 GB. Free space, then retry. The downloader writes `<id>.bin.part` and only
replaces the model after verification.

### App is killed / OOM when using a large model

Use `tiny` or `base` on a 6 GB phone. `small` is 8 GB-optional; `medium` and
`large-v3-turbo` are experimental and may be killed by the low-memory killer.
Check **Settings → Unload model when idle** and lower it to 1 minute. The doctor
report shows free storage and RAM, not live RSS; use
`adb shell dumpsys meminfo com.martins.whisper` for the measurement.

## Engine

### "Engine unavailable" / no transcript at all

- 0.1.0 intentionally has a fake engine until G1 wires whisper.cpp. If the text
  is a placeholder, this is the known gap.
- When the real engine is built, the APK must contain the arm64 library
  (`-Pmartins.native=true`). A CPU-only/default APK without native libs must show
  a typed "engine unavailable" error, not crash.
- Native build failures are documented in [BUILD.md](BUILD.md) and
  [native-build.md](native-build.md); do not chase them on a phone.

### GPU selected but it falls back to CPU

This is expected on devices without a working Vulkan driver, and on a driver
crash. v1 falls back to CPU once and reports the reason. GPU is optional and
never a sign-off blocker. Record the backend in the doctor report and the H2
table.

## Getting evidence for a bug report

```bash
./scripts/doctor.sh > doctor.txt
adb logcat -d -v time | grep -iE 'martins|whisper|FATAL EXCEPTION' | tail -200 > logcat.txt
```

Attach `doctor.txt` and `logcat.txt`; both are filtered and contain no
transcripts. Do **not** attach an audio recording or a screen recording that
shows private text unless you are deliberately sharing it. **UNVERIFIED
(device):** these two commands on a real phone.

If `adb` is missing, use **Settings → Apps → Martins Whisper** for the version and
describe the step that failed; the doctor report is still the preferred evidence.
