# T1-1 — Layer 1 fake-audio end-to-end test

Owner: TESS (branch `agent/t1-harness`). One session, one commit.

## Goal

One JVM/Robolectric E2E test: synthesized frames -> VAD/chunker -> merger -> insertion plan, using
`FakeSpeechEngine` (core testFixtures). Assert transcript text, insertion caret/selection, and model
lifecycle (`ModelManager` load/markUsed/idle-unload). No real model, no device, no network.

## Acceptance

```
./gradlew :core:test :app:testDebugUnitTest --offline
```
green with the new test(s) counted in the suite output.

## Status

open (provider OK 18:36Z)

## Result

-
