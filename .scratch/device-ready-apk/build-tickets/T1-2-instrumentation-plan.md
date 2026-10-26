# T1-2 — Instrumentation plan that compiles, device-only to run

Owner: TESS (branch `agent/t1-harness`). One session, one commit.

## Goal

`app/src/androidTest` covering overlay focus retention, accessibility insertion/read-back,
password-field refusal, permission denial and service lifecycle. They must be documented as
device-only and compile here. Optionally add `scripts/test-device.sh` that fails clearly without a
device. Do not fake a pass.

## Acceptance

```
./gradlew :app:assembleDebugAndroidTest --offline
```
BUILD SUCCESSFUL; `scripts/test-device.sh` exits non-zero with a clear "no device" message when
`adb devices` is empty.

## Status

running (TESS, 18:52Z)

## Result

-
