# Martins Whisper

License: Apache-2.0 (see LICENSE)

A local-only, hold-to-talk dictation app for **Android**, in Kotlin. Think "Wispr Flow clone" with no cloud:

- Always-visible floating mic overlay. Hold it, speak, release.
- Text is inserted into whatever text field currently has focus.
- Whisper runs on-device (GPU where available), with an idle unload after 5 minutes.
- Models are configurable (Whisper base by default).
- Long holds are chunked with VAD and transcribed incrementally.

Status: **active development / replan v2 (Android)**. See `KANBAN.md`, `SPEC.md`, `LOGBOOK.md`.

## Documentation

| Guide | Audience | What it covers |
|---|---|---|
| [docs/BUILD.md](docs/BUILD.md) | operator | Build the APK and run the JVM tests on this host, in Docker, or in CI. |
| [docs/INSTALL.md](docs/INSTALL.md) | user + operator | Sideload the APK, first run, upgrade and uninstall. |
| [docs/FIRST-DICTATION.md](docs/FIRST-DICTATION.md) | user + operator | Shortest path from a native APK to text in another app, including model download/import. |
| [docs/FIRST-DICTATION.md](docs/FIRST-DICTATION.md) | user + operator | Shortest path from APK to text: native build, 3 permissions, model, overlay, first dictation. |
| [docs/PERMISSIONS.md](docs/PERMISSIONS.md) | user | Microphone, overlay, accessibility, notifications, battery; what each is for and how to revoke it. |
| [docs/MODELS.md](docs/MODELS.md) | user + operator | Model sizes/hashes, download/import, adb model push, storage and deletion. |
| [docs/PRIVACY.md](docs/PRIVACY.md) | everyone | Local-only audio and transcripts, the one network use, and what is stored. |
| [docs/DEVICE-MATRIX.md](docs/DEVICE-MATRIX.md) | operator | Supported/best-effort/unsupported devices plus the H1/H2 device tests. |
| [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) | user + operator | Permission, overlay, model, memory and insertion failures with a next step. |
| [docs/DOCTOR.md](docs/DOCTOR.md) | user + operator | Pasteable, secret-free diagnostics for a bug report. |

## Current build status (0.1.0)

The Android shell, permissions UI, overlay, settings, model catalog, in-app model
management (installed state, download, SAF import, delete, progress) and the real
`WhisperCppEngine` wiring are in place and pass 135 JVM tests. Diagnostics shows
native availability, the resolved backend, fallback reason, GPU-failure count and
the last engine error. The default APK still has **no native libraries**
(`-Pmartins.native=false`), so real transcription needs an arm64 build with
`-Pmartins.native=true`; no native/device run has been done yet. Everything in
these docs that requires a physical phone is marked **UNVERIFIED** until the H1
device run.
