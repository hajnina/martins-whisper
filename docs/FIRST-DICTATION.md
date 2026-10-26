# First dictation on a real phone

This is the shortest end-to-end path from an APK to text in another app. It
assumes the phone is an **arm64 Android 10–15** device and that the APK was built
with the **native** whisper.cpp engine (see step 0).

> **Verification:** the code paths below were read from `app/src` and
> `core/src` on `master` after the G1 merge. Every host check that was run is
> marked **VERIFIED (host)**. Steps that need a phone or a native APK are
> **UNVERIFIED (device, H1-blocked)**. No device result is claimed.

## 0. Get the right APK (operator)

There are two debug APKs, and only one can dictate:

| Build | Command | Has engine? | What happens |
|---|---|---|---|
| Default | `./gradlew :app:assembleDebug` | **No** | Model UI works; first press fails with a typed "Native whisper.cpp is not available" error. |
| Native arm64 | `./gradlew :app:assembleDebug -Pmartins.native=true` | **Yes** (needs NDK 27.0.12077973) | Real dictation. |

Check an APK before installing:

```bash
unzip -l app-debug.apk | grep 'lib/arm64-v8a/libmartins_whisper.so'
```

- A line ending in `lib/arm64-v8a/libmartins_whisper.so` means the engine is
  packaged.
- No output means this is the non-native build; it cannot transcribe.

**VERIFIED (host, 2026-10-26):** the default G1 APK has no `lib/` entry, matching
`-Pmartins.native=false` (opt-in). **UNVERIFIED (device):** the native build and
the packaged `.so`; NDK 27.0.12077973 is not installed in this sandbox.

If you cannot build a native APK locally, use the x86_64 Docker/CI path in
[BUILD.md](BUILD.md); it still needs the same `-Pmartins.native=true` property.

## 1. Install the APK

Over USB (operator):

```bash
adb install -r app-debug.apk
# or, from this repo:
./scripts/install-apk.sh --apk /path/to/app-debug.apk
```

**VERIFIED (host):** the script's `--help`, missing-adb and no-device paths.
**UNVERIFIED (device):** the actual `adb install`.

From the phone: tap the APK in **Files**, allow **Install unknown apps** for that
source, **Install**, **Open**. The v1 APK is debug-signed and sideload-only.

## 2. First launch: what you should see

One scrolling screen titled **Martins Whisper** with a privacy note and sections:

1. **Permissions** (four rows + *Start overlay on boot*).
2. **Overlay** (*Start overlay*).
3. **Model** (the catalog list).
4. **Recognition** (language, compute backend, idle unload).
5. **Diagnostics**.

At the top, the bold summary says **"Missing: microphone, overlay,
accessibility, notifications"** until the permissions are granted. Then it says
**"All required permissions granted"**.

## 3. Grant the three dictation permissions

### 3.1 Microphone (required)

- Tap **Grant** on **Microphone** → Android dialog → **While using the app**
  (or **Allow**).
- Row status changes **Not granted → Granted**.
- Without it, pressing the bubble reports *Microphone permission required*.

### 3.2 Display over other apps (required)

- Tap **Grant** on **Display over other apps** → system **Display over other
  apps** screen → enable **Allow display over other apps** for Martins Whisper →
  back.
- Row status becomes **Granted**.
- This only draws the floating bubble; it does not read the screen.

### 3.3 Accessibility insertion (required for insertion)

- Tap **Grant** on **Accessibility insertion** → system **Accessibility** screen
  → **Installed apps** / **Downloaded apps** → **Martins Whisper text insertion**
  → enable → confirm the Android warning.
- Row status becomes **Granted**.
- Without it, dictation still transcribes but cannot insert; the transcript is
  copied to the clipboard and a notification/toast tells you to paste.

### 3.4 Notifications (recommended, fourth row)

- Tap **Grant** on **Notifications** → **Allow** (Android 13+).
- Dictation works without it, but the foreground-service notification (state,
  **Settings**, **Stop**) may be hidden and the top summary will still say
  `Missing: notifications`. On Android 12 and older this row is granted
  implicitly.

**UNVERIFIED (device):** all four system prompts and the resulting row states.

## 4. Put a model on the phone

The app uses one model id at a time. **Base (multilingual)** is the default and
the right first choice on a 6–8 GB phone.

### Option A — download in the app (recommended)

1. In **Model**, tap the **Base (multilingual)** row to select it. It shows
   **Not installed · selected**.
2. Tap **Download** under that row.
3. A progress bar and *Downloading Base (multilingual)… N%* appear. The download
   is explicit, resumable via `.part`, and verified by SHA-256 before the file is
   promoted to `files/models/base.bin`.
4. When it finishes, the status becomes **Installed · selected** and the message
   **Model ready**.

The catalog also lists **Tiny**, **Small**, **Medium** and **Large v3 Turbo**;
use **Tiny** on a loaded/low-RAM phone and avoid Medium/Large on 6 GB.
**VERIFIED (host):** catalog sizes/hashes and the downloader tests; the D1
operator downloads used the pinned revision and matched the catalog. **UNVERIFIED
(device):** the in-app download over the phone's network.

### Option B — import a GGML file

1. Select the model row (for example **Base (multilingual)**).
2. Tap **Import file** → pick the matching `ggml-base.bin`.
3. The app copies it to a cache file, verifies the catalog SHA-256, then installs
   it. A wrong file fails with a hash mismatch; it is not installed.

**UNVERIFIED (device):** the system file picker and import copy. This path is for
a file whose id matches the selected catalog row; arbitrary custom ids are not
selectable in this build.

### Option C — push with adb (operator, offline)

Use this on a debug APK when the phone has no network or you already have the
file:

```bash
./scripts/push-model.sh /path/to/ggml-base.bin
```

The script enforces the catalog SHA-256 and writes
`/data/data/com.martins.whisper/files/models/base.bin` via `run-as`, then the app
shows it as **Installed** after it resumes. See [MODELS.md](MODELS.md).

**VERIFIED (host):** script `bash -n`, `--help`, hash-match and hash-mismatch
paths, and the no-device error. **UNVERIFIED (device):** the `run-as` copy.

### Do not skip this

If no model is installed, the first press fails before recording with:

```text
Model 'Base (multilingual)' is not installed. Download or import it in Settings.
```

## 5. Check the engine before dictating

Scroll to **Diagnostics**. On a native APK before the first press it should show:

```text
Native whisper.cpp: available
Model: Base (multilingual) (installed)
Resolved backend: not loaded yet
Fallback: none
GPU fallbacks recorded: 0
No engine has been loaded yet; press the bubble to test.
```

If it says **Native whisper.cpp: missing**, you installed the default non-native
APK. Stop and install the native build from step 0; downloading a model will not
fix this.

**UNVERIFIED (device):** the actual diagnostics text on a phone.

## 6. Start the overlay

1. Tap **Start overlay**. The button changes to **Stop overlay**.
2. The ongoing notification appears: **"Martins Whisper is ready"** /
   **"Hold the bubble to dictate"**.
3. A floating mic bubble appears over the current app. Drag it to move it; its
   position is saved.

If the bubble does not appear, the overlay permission is off (the app shows
*Overlay permission is off. Tap Settings.*), or an OEM battery policy killed the
service. See [TROUBLESHOOTING.md](TROUBLESHOOTING.md).

**UNVERIFIED (device).**

## 7. First dictation

1. Open a simple app with a text field, for example **Messages → New message**.
2. Tap the text field once so the cursor is visible and the field is focused.
3. **Press and hold** the bubble. Do not tap it: a tap shorter than 400 ms is not
   a dictation and shows the toast **Hold to talk**.
4. While holding, the overlay shows the recording level/time. Say a short
   sentence, for example *"hello world this is my first dictation"*.
5. Release the bubble. The state goes **Transcribing…**, then one of:
   - **Inserted** — the text appeared at the cursor.
   - **Transcript copied to clipboard** — the focused field could not be edited;
     paste it manually (long-press → **Paste**).
6. The success message resets to idle after about 1.8 s.

The very first dictation also loads the model, so it is the slowest. Later
dictations reuse the loaded engine until the idle timeout (default 300 s).

**UNVERIFIED (device, H1):** microphone capture, overlay focus retention,
insertion and the success messages on hardware.

## What to expect (and what is not a bug)

| Situation | What you see / should expect |
|---|---|
| First press after launch | `Loading Base (multilingual)…` while the model loads, then recording. |
| Tap instead of hold (<400 ms) | Toast **Hold to talk**, no transcript, no audio kept. |
| Silence / no speech detected | **No speech detected**; the session resets. |
| Hold longer than 10 min | The service stops at the 10-minute safety cap. |
| No accessibility service | **Transcript copied to clipboard**; paste manually. |
| Password / PIN field focused | Text is **not inserted** into the password field; the transcript is copied to the clipboard instead. Do not dictate secrets into a password field. |
| GPU requested but unavailable | Falls back to CPU once; Diagnostics shows **Fallback: GPU unavailable; using CPU** and a GPU-fallback count. GPU is optional. |
| No native library (default APK) | Typed error: *Native whisper.cpp is not available in this APK. Install a build with native support…*; Diagnostics says **Native whisper.cpp: missing**. |
| Model missing/corrupt | Typed *not installed* message, or a load failure for a corrupt file. |
| Idle 300 s | The engine is closed; the next dictation reloads it (slower once). |
| Reboot | The bubble is restored **without the microphone** (Android 14+ boot limits). Press the bubble to re-arm the microphone (opening the app also works); if the mic is still refused, stop/start the overlay. |

H2 latency/RAM targets (base p50 ≤ 2 s, p95 ≤ 4 s; tiny p50 ≤ 1.5 s, p95 ≤ 3 s)
are **targets, not measured here**. Run them per [DEVICE-MATRIX.md](DEVICE-MATRIX.md).

## 8. Stop, uninstall, clean up

- **Stop overlay** in the app, or **Stop** on the notification.
- Uninstall: **Settings → Apps → Martins Whisper → Uninstall**, or
  `adb uninstall com.martins.whisper`. This deletes the app, models and settings;
  nothing was uploaded anywhere.

## Related guides

[INSTALL.md](INSTALL.md) · [PERMISSIONS.md](PERMISSIONS.md) ·
[MODELS.md](MODELS.md) · [PRIVACY.md](PRIVACY.md) ·
[TROUBLESHOOTING.md](TROUBLESHOOTING.md) · [DOCTOR.md](DOCTOR.md) ·
[DEVICE-MATRIX.md](DEVICE-MATRIX.md)
