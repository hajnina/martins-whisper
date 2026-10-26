# Device support and acceptance matrix

v1 targets **arm64 Android phones**, Android 10–15. This page is the single
source for what is supported, what is best-effort, and the H1/H2 tests that must
be run on a real device.

> **H1-blocked:** there is no physical Android device or ADB access in this
> sandbox. **No row below has been run on hardware.** Do not present the
> thresholds as measured results. Fill a row after each H1/H2 run and attach the
> evidence.

## Support matrix

| Tier | Device | Android | RAM | GPU | Status |
|---|---|---|---|---|---|
| **Supported** | arm64-v8a phone/tablet with a working mic | 10–15 (API 29–35) | 6 GB min for `tiny`/`base`; 8 GB recommended for `small` | Optional. Vulkan present is a bonus; CPU-only works | v1 target |
| **Best-effort** | other arm64 phones on the same Android range | 10–15 | 6 GB+ | any | Works or degrades per OEM; record quirks |
| **Unsupported** | x86/x86_64 devices and emulators | any | any | any | not built or tested for v1 |
| **Unsupported** | 32-bit (`armeabi-v7a`, `x86`) | any | any | any | no 32-bit native build |
| **Unsupported** | Android 9 and older (API < 29) | 9- | any | any | below `minSdk`; will not install |
| **Unsupported** | tablets without a usable microphone | any | any | any | no input |
| **Unsupported** | devices where accessibility cannot be enabled | any | any | any | no insertion path (clipboard fallback only) |

Notes:

- The APK is `minSdk 29`, `targetSdk 35`, package `com.martins.whisper`,
  version `0.1.0` (versionCode 1).
- The default 0.1.0 APK has **no native libraries** (`-Pmartins.native=false`);
  the engine wiring is real, but the arm64 whisper.cpp library is opt-in with
  `-Pmartins.native=true`. The support matrix describes the v1 target; no
  on-device run has validated it yet.
- Vulkan is **not required**. Never promise a GPU speedup; report the device's
  measured result.

## Tested devices

No rows yet (H1-blocked). Add one row per run:

| Date | Device / model | Android / API | RAM | GPU / backend | Result | Evidence |
|---|---|---|---|---:|---|---|
| — | — | — | — | — | not run | — |

Also record the exact build fingerprint (`adb shell getprop ro.build.fingerprint`)
and the APK SHA-256 in the run's handoff, not in this table.

## H1 functional checklist (device)

Pass = all items below. Any functional failure blocks sign-off.

- [ ] APK installs over adb or from the file manager.
- [ ] Microphone, overlay, accessibility and notification grants work.
- [ ] The overlay bubble appears above another app and **never steals focus**
      from the target text field.
- [ ] Insertion succeeds in at least 5 of the 7 non-password fields in the
      matrix below.
- [ ] Accessibility-off path falls back to clipboard + notification without a
      crash.
- [ ] Password/PIN fields are refused (no insertion). The clipboard fallback may
      still copy the transcript; the UI must say "copied", not "inserted".
- [ ] After reboot, the app restores the bubble **without** starting the
      microphone from boot; pressing the bubble re-arms the foreground service
      (verify the Android 14+ restriction).
- [ ] 300 s idle unload is visible in state/logs and the next dictation reloads.
- [ ] No crash across the matrix.
- [ ] A 10-run stability pass with mixed apps leaves RSS growth ≤ 10% after
      warm-up.

## Insertion matrix (8 fields)

Run each on the real device; record pass/fail and the method used
(`ACTION_SET_TEXT`, paste, clipboard fallback).

| # | Target | Field type | Pass? | Method / notes |
|---|---|---|---|---|
| 1 | Compose Material text field | Compose | | known hard case |
| 2 | Classic `EditText` (e.g. Messages) | native | | baseline |
| 3 | WebView input | WebView | | |
| 4 | Chrome/browser omnibox | WebView/omnibox | | |
| 5 | Chat compose (WhatsApp or Signal) | custom/native | | |
| 6 | Multiline notes field | native | | newlines preserved |
| 7 | Emoji + RTL text (Arabic/Hebrew) | native | | no corruption; cursor correct |
| 8 | Password/PIN field | native | | **must refuse** in every case |

For every row also check: focus retained after the overlay press, cursor position,
selection replaced, no duplicate text, no app crash, clipboard state.

The older, longer exploratory checklist (13 targets including Slack, Telegram,
Keep, Word, terminal) is kept in [DEVICE_TEST_MATRIX.md](DEVICE_TEST_MATRIX.md)
for a later hardening run. The 8 above are the v1 gate.

## H2 measurement table (device)

Measure `base` and `tiny` on the target phone. All numbers are **proposed v1
thresholds**, not measured results.

| Metric | How measured | Pass |
|---|---|---|
| End-of-speech → text inserted, `base` CPU, 1 s utterance | screen recording + logcat timestamps, 10 runs | p50 ≤ 2.0 s, p95 ≤ 4.0 s |
| Same, `tiny` | same | p50 ≤ 1.5 s, p95 ≤ 3.0 s |
| Cold model load | doctor/logcat | `base` ≤ 5 s, `tiny` ≤ 3 s on 8 GB |
| Peak app RSS, `base` | `adb shell dumpsys meminfo com.martins.whisper` during/after load | ≤ 1.2 GB, no OOM over 10 dictations |
| Peak app RSS, `tiny` | same | ≤ 800 MB |
| Idle unload after 300 s | doctor + `dumpsys meminfo` before/after | RSS drop ≥ 80% of the loaded model, or ≤ 400 MB total; next dictation reloads |
| GPU vs CPU | same 10 utterances with the backend forced each way | report the speedup; GPU must not crash, no minimum speedup |
| GPU failure handling | force/observe a load failure | fall back to CPU once, persist the reason, 2-strike counter then CPU default, no crash |
| Stability | 10 consecutive dictations, mixed apps | no crash; RSS growth ≤ 10% after warm-up |
| Battery/thermal | battery stats + device temperature, 10 min mixed use | **report only**; fail only if throttling stops dictation |

**Sign-off rule:** any H1 functional failure blocks. An H2 threshold miss is a
known issue unless it causes OOM, a crash, or unusable latency (> 2× the p95
target). Record the device model, Android version, RAM and GPU with every number.

## Evidence to capture per run

- Screen recording of the insertion matrix.
- `dumpsys meminfo` before and after idle unload.
- `adb logcat` excerpt with backend/fallback and timestamps (no transcripts).
- Filled doctor report ([DOCTOR.md](DOCTOR.md)).
- One completed row in **Tested devices**.

No secrets, accounts, transcript text or audio in the handoff.

## Known OEM quirks

These are placeholders until H1; do not mark them as verified observations.

| OEM | Area | What to check |
|---|---|---|
| Samsung | battery/overlay | Sleeping apps, background limits, overlay after reboot |
| Xiaomi/MIUI | autostart/overlay | Autostart, "display pop-up windows while running in background" |
| Pixel / stock AOSP | baseline | reference device |
| OnePlus/Oppo | background start | background start restrictions |
| Huawei | background | no GMS relevance; battery manager |
| Android 14/15 | microphone FGS | `FOREGROUND_SERVICE_MICROPHONE`, while-in-use restrictions, overlay exemption |
