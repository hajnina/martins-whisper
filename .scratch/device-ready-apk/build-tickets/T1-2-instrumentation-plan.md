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

<<<<<<< HEAD
running (TESS, 18:52Z)
=======
done (TESS, 2026-10-26; re-verified 2026-10-26 on master 421f223)
>>>>>>> 667dba3

## Result

Delivered in `cf92851`, merged to master in `425412c`. `app/src/androidTest` contains
`OverlayFocusRetentionTest`, `AccessibilityInsertionReadBackTest`, `PasswordFieldRefusalTest`,
`PermissionDenialTest` (skips when mic is granted; run with `--deny-mic`) and
`ServiceLifecycleTest`, documented device-only in `app/src/androidTest/DEVICE-TESTS.md`.
`scripts/test-device.sh` builds/installs both APKs, best-effort grants overlay/accessibility,
runs `am instrument`, and exits 3 with a clear message when `adb devices` is empty.

Acceptance evidence (run in this sandbox; Layer 2 is compiled only, never faked as executed):

```
scripts/test-device-ready.sh -> :app:assembleDebugAndroidTest BUILD SUCCESSFUL
                                app-debug-androidTest.apk 1,326,409 B
scripts/test-device.sh       -> exit 3, "No Android device or emulator is connected"
```
