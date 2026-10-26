# Permissions and first-run setup

Martins Whisper needs four Android permissions to dictate into other apps. There
is no account, and none of these permissions sends data anywhere. The app shows
each one on its main screen with a **Grant** button and a **Granted / Not granted**
status.

> **Status:** the permission screen is implemented and covered by JVM/Robolectric
> tests. The real system prompts and OEM differences are **UNVERIFIED (device,
> H1-blocked)** in this sandbox.

## The four permissions

| Permission | Android name | Why the app needs it | What happens without it |
|---|---|---|---|
| **Microphone** | `RECORD_AUDIO` (runtime) | Record while you hold the bubble. | Pressing the bubble shows *Microphone permission required*; no audio is captured. |
| **Display over other apps** | `SYSTEM_ALERT_WINDOW` (special) | Draw the floating mic bubble above the app you are typing in. | The bubble cannot appear; the notification says *Overlay permission is off*. |
| **Accessibility insertion** | Accessibility service (special) | Read the focused text field and insert the transcript at the cursor. | Dictation can still run, but text cannot be inserted; the app falls back to copying the transcript to the clipboard. |
| **Notifications** | `POST_NOTIFICATIONS` (runtime, Android 13+) | Show the ongoing "Martins Whisper is ready" notification that keeps the foreground service alive and exposes **Stop / Settings**. | On Android 13+ the foreground-service notification may be hidden; the service can be killed more aggressively. |

The app also declares `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`
(needed on Android 14+) and `RECEIVE_BOOT_COMPLETED`. These are normal
permissions and do not show a prompt.

## Grant them in order

Open **Martins Whisper** and use the **Grant** button next to each row.

### 1. Microphone

- Tap **Grant** → the Android permission dialog → **While using the app** (or
  **Allow**). "While using" is enough; the microphone is used only while the
  bubble is held.
- Revoke: **Settings → Apps → Martins Whisper → Permissions → Microphone → Don't
  allow**. Dictation then fails with a clear message instead of crashing.

### 2. Display over other apps

- Tap **Grant** → the system **Display over other apps** screen → enable
  **Allow display over other apps** for Martins Whisper.
- This is the permission that lets the bubble float above Chrome, WhatsApp, etc.
  It does **not** let the app read what is on screen.
- Revoke: same screen → turn the switch off. The bubble disappears the next time
  the service starts.

### 3. Accessibility insertion

- Tap **Grant** → **Settings → Accessibility → Installed apps / Downloaded apps →
  Martins Whisper text insertion** → enable it → confirm the warning.
- Android shows a standard warning that an accessibility service can observe
  what you type and do actions. Martins Whisper's service is configured to track
  the focused editable field and to set/paste text; see [PRIVACY.md](PRIVACY.md).
- Revoke: **Settings → Accessibility → Martins Whisper text insertion → off**, or
  **Settings → Apps → Martins Whisper → ... → Accessibility**.
- If accessibility is off, the app deliberately degrades to **clipboard +
  notification** instead of trying another injection method. Nothing crashes.

### 4. Notifications (Android 13+)

- Tap **Grant** → **Allow**.
- The notification is how you reach **Settings** and **Stop** while the overlay is
  running. It also shows recording/transcribing state.
- On Android 12 and older there is no runtime prompt; notifications are allowed
  unless you turned them off for the app.

## Optional: battery optimization

The app does not request a battery-optimization exemption in 0.1.0. Some phones
(Samsung, Xiaomi/MIUI, OnePlus/Oppo, Huawei) stop background services even when a
notification is visible.

To keep the bubble reliable:

1. **Settings → Apps → Martins Whisper → Battery → Unrestricted** (wording varies).
2. On Xiaomi/MIUI also allow **Autostart**.
3. On Samsung check **Settings → Battery → Background usage limits** and remove
   the app from **Sleeping apps**.

This is a device setting, not an app permission. See
[DEVICE-MATRIX.md](DEVICE-MATRIX.md) for per-OEM notes and
[TROUBLESHOOTING.md](TROUBLESHOOTING.md) if the bubble disappears.

## Start on boot (optional)

The **Start overlay on boot** switch is stored by the app. When it is on and the
overlay is enabled, the app restores the bubble after a reboot. Android 14+
restricts starting a microphone foreground service from boot, so the restored
bubble starts **without the microphone** until you open the app once. This is
expected; it is not a permission failure.

## Checking permissions from a computer

The doctor script prints the same states without guessing:

```bash
./scripts/doctor.sh
```

Example fields:

```text
permission.RECORD_AUDIO=granted
appop.SYSTEM_ALERT_WINDOW=allow
accessibility_service=enabled
permission.POST_NOTIFICATIONS=granted
battery_optimization=not ignored
```

**VERIFIED (script):** `bash -n`, `--help`, and the no-device error path.
**UNVERIFIED (device):** the reported values on a real phone.
