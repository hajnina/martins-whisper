# G1b — Model management UI and diagnostics

Owner: ASHA (branch `agent/g1-engine`). One session, one commit.

## Goal

Replace the remaining stub spinner notes with the core catalog plus installed state. Add
download / import (SAF) / delete actions, progress, and diagnostics showing native availability,
active backend, fallback reason and last engine error from `SettingsStore`. Keep the instrumentation
free (JVM-testable helpers; all file/network work off the main thread).

## Acceptance

```
./gradlew :app:testDebugUnitTest :app:assembleDebug --offline
```
green; a JVM test covers installed/not-installed rendering and delete/import state transitions.

## Status

done (2026-10-26)

## Result

Merged: ASHA-1 `fcb8299` (G1), DORA-1 `b33c8c3` (D1). MARA gate: 135 tests, 0 failures, APK 8,938,813 B.
