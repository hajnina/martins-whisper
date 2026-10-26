# Layer 2 device-only tests

These tests live under `app/src/androidTest` and are **compiled** by the T1 merge gate
(`:app:assembleDebugAndroidTest`) but **never executed in the sandbox**: there is no
`/dev/kvm`, no emulator and no physical Android device. They run at H1 on hardware.

| Test | What it proves on a device |
|---|---|
| `OverlayFocusRetentionTest` | The bubble overlay is non-focusable and does not steal focus from the target `EditText`. Needs `SYSTEM_ALERT_WINDOW`. |
| `AccessibilityInsertionReadBackTest` | The insertion plan applied to a focused field is visible through `AccessibilityNodeInfo`, including text and caret/selection. |
| `PasswordFieldRefusalTest` | A real password `EditText` is marked `isPassword`, routes to clipboard-only instead of `ACTION_SET_TEXT`, and its text is unchanged. |
| `PermissionDenialTest` | With `RECORD_AUDIO` denied, `PermissionGate` reports the missing permission and `AudioRecordSource` fails closed with `SecurityException`. Skipped when mic is granted. |
| `ServiceLifecycleTest` | The foreground service starts from a visible activity without the microphone type, publishes Idle, and stops cleanly from `ACTION_STOP`. |

## Run

```sh
GRADLE_USER_HOME=/home/node/gh-tess scripts/test-device.sh
```

The script exits non-zero with a clear message when `adb devices` has no online device. It builds
both APKs offline, installs them, best-effort grants the overlay app-op and enables the
accessibility service, then runs `am instrument`.

Permission-denial case (revoking a runtime permission kills the app process, so it must happen
before instrumentation starts):

```sh
scripts/test-device.sh --deny-mic
```

## Manual prerequisites / fallbacks

- Overlay: `adb shell appops set com.martins.whisper SYSTEM_ALERT_WINDOW allow`
- Accessibility: Settings > Accessibility > Martins Whisper text insertion (or the `settings put`
  commands in `scripts/test-device.sh`).
- Microphone: granted for the normal suite; revoked for `--deny-mic`.
- The accessibility test exercises the planner plus accessibility read-back inside this app. The
  service deliberately rejects its own package (`InsertionPolicy`), so true cross-app injection is
  validated manually at H1 against a third-party notes/messaging app.
- Real model load/transcribe is not covered here; it needs a `-Pmartins.native=true` arm64 build
  and a GGML model pushed to the device (see `docs/` and the D1 scripts).
