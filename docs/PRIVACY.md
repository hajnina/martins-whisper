# Privacy and data flow

Martins Whisper is **local-only**:

- **No cloud inference.** Audio, transcripts and model input never leave the phone.
- **No telemetry.** No analytics SDK, crash reporter, advertising ID or usage
  pings.
- **No account.** No sign-up, login, sync or server-side history.
- **One network use:** an explicit model download from Hugging Face. Nothing else
  uses the network.

This page describes what the app touches and where it goes. It is a plain
description of the code, not a legal policy.

## What happens to your audio

1. While you hold the bubble, `AudioRecordSource` captures 16 kHz mono PCM into
   memory.
2. Frames are chunked (VAD + overlap) and passed to the local Whisper engine.
3. On release, the audio buffers are discarded. **0.1.0 writes no audio file.**
   There is no debug-recording flag in the shipped code.
4. The transcript is handed to the accessibility service for insertion, or copied
   to the clipboard if insertion is not possible.

Audio is never uploaded. The microphone is active only while the bubble is held
(plus the engine's brief warm-up), and the foreground service shows a
notification while it is running.

## What is stored on the phone

| Data | Where | Why | Removed by |
|---|---|---|---|
| Settings | app-private SharedPreferences `martins_whisper_settings` | model id, language, GPU/CPU preference, idle-unload time, overlay on/off, boot switch, bubble position | uninstall / app data clear |
| Model files | app-private `files/models/<id>.bin` | the GGML weights you downloaded, imported or pushed | in-app delete or uninstall |
| Transcripts | **not stored** by 0.1.0 | inserted into the target field or clipboard only | n/a |
| Audio | **not stored** | discarded after transcription | n/a |
| History | **not implemented** (backlog, opt-in) | none | n/a |

`android:allowBackup="false"` is set in the manifest, so Android cloud backup
does not copy the app's models or settings.

### Clipboard

When insertion fails, the transcript is copied to the clipboard so you can paste
it yourself. The clipboard is shared with the rest of the device: on Android 10+
only the focused app (or the default IME) can read it in the background, but
treat it as sensitive. The app **does not auto-clear** the clipboard (ADR-003),
so clear it yourself if the text is sensitive.

## The one network use: model download

- The app declares `INTERNET` because a model download needs it.
- The in-app flow downloads only after you tap **Download** on a model row,
  shows progress, verifies the catalog SHA-256, and stores the file in
  app-private storage. No silent download, no background prefetch.
- **Import file** uses the Android file picker and does not touch the network.
- Hosts used: `huggingface.co` for the redirect and the Hugging Face CDN hosts it
  redirects to (for example `us.aws.cdn.hf.co`, `cdn-lfs-us-1.hf.co`). The exact
  list is in `research/01-model-facts.md`.
- The request contains normal HTTP metadata (IP address, user agent). No token,
  account or device identifier is sent.
- **A stock install makes no network request** until you tap **Download**.
  Models can also be imported from a local file or pushed over adb by the
  operator.

## Accessibility service: what it can see

The accessibility service is the mechanism that inserts text into other apps. It
is configured with `canRetrieveWindowContent="true"` and tracks focused editable
fields, text selection and window changes. That means Android technically lets
the service observe on-screen content while it is enabled.

In this app the service is used to:

- remember the focused editable field and its selection,
- write the transcript with `ACTION_SET_TEXT` (preferred), then paste, then
  clipboard + notification as fallbacks,
- **refuse to insert** into password/PIN fields (ADR-003); like any other
  unsuccessful insertion, the transcript is copied to the clipboard so you can
  decide what to do with it.

The service does **not** upload, log or persist what it reads, and it is active
only while the app's accessibility service is enabled in system settings. You can
disable it at any time in **Settings → Accessibility**.

## Permissions

- **Microphone** is used only for dictation.
- **Display over other apps** draws the bubble; it does not read screen content.
- **Notifications** shows the foreground-service controls.
- No contacts, location, camera, SMS, storage or account permission is requested.

See [PERMISSIONS.md](PERMISSIONS.md) for the exact list and how to revoke each.

## Diagnostics you choose to share

`scripts/doctor.sh` builds a report for bug reports. It contains device build
facts (manufacturer, model, Android/API, ABI, RAM, free storage), app
version/versionCode/APK path, permission states, and model file names/sizes/SHA-256.

It deliberately **excludes**:

- device serial number (used only to select a target, never printed),
- transcripts and audio,
- accounts, email addresses, tokens and clipboard contents,
- a full `dumpsys`/package dump.

`docs/DOCTOR.md` shows the exact fields. Read the report before attaching it; the
device model and installed model list are still device-specific information.

## Network policy in one line

Audio and transcripts: **never** uploaded. Network: **only** an explicit model
download, and **only** from the pinned Hugging Face source.
