# Martins Whisper

A local-only, hold-to-talk dictation app for **Android**, in Kotlin. Think "Wispr Flow clone" with no cloud:

- Always-visible floating mic overlay. Hold it, speak, release.
- Text is inserted into whatever text field currently has focus.
- Whisper runs on-device (GPU where available), with an idle unload after 5 minutes.
- Models are configurable (Whisper base by default).
- Long holds are chunked with VAD and transcribed incrementally.

Status: **active development / replan v2 (Android)**. See `KANBAN.md`, `SPEC.md`, `LOGBOOK.md`.
