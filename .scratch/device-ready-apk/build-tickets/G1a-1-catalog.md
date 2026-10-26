# G1a-1 — Replace the model catalog stub with core ModelCatalog

Owner: MARA (integrator). Ticket: one session, one commit.

## Goal

Remove `app/.../ui/ModelOptions.kt`; use `core.asr.ModelCatalog` everywhere so ids, display names and
SHA-256 data are the single source of truth. No behaviour change beyond the catalog swap.

## Inputs

- `app/src/main/java/com/martins/whisper/MainActivity.kt` (model spinner)
- `app/src/main/java/com/martins/whisper/service/DictationService.kt` (loading label)
- `app/src/test/java/com/martins/whisper/data/SettingsDefaultsTest.kt`
- `core/src/main/kotlin/com/martins/whisper/core/asr/ModelCatalog.kt`

## Acceptance

```
./gradlew :app:testDebugUnitTest --offline
```
green, and `grep -r ModelOptions app core` returns nothing.

## Status

open

## Result

-
