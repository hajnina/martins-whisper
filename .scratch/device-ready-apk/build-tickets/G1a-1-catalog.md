# G1a-1 — Replace the model catalog stub with core ModelCatalog

Owner: MARA (done before the split). Ticket: one session, one commit.

## Goal

Remove `app/.../ui/ModelOptions.kt`; use `core.asr.ModelCatalog` everywhere so ids, display names and
SHA-256 data are the single source of truth. No behaviour change beyond the catalog swap.

## Acceptance

```
./gradlew :app:testDebugUnitTest --offline
```
green, and `grep -r ModelOptions app/src core/src` returns nothing.

## Status

done (2026-10-26, MARA)

## Result

`1511ca1`. Acceptance: `:app:testDebugUnitTest` BUILD SUCCESSFUL (offline); no source references to
`ModelOptions` remain.
