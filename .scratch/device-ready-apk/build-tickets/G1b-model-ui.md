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

open (provider OK 18:36Z)

## Result

-
