# Doctor report for bug reports

A good bug report needs the device, Android version, app version, backend, model
and permission state. The doctor report collects exactly that. It contains **no
transcripts, no audio, no accounts and no tokens**.

> **Status in 0.1.0:** the app has a **Diagnostics** section but it only shows a
> status caption; the full in-app doctor screen is **not implemented yet (G1)**.
> Use the operator script below. A device run is **UNVERIFIED (H1-blocked)**
> because this sandbox has no phone.

## Run the script

Requirements: `adb` and a connected device (see [INSTALL.md](INSTALL.md)).

```bash
./scripts/doctor.sh > doctor.txt
cat doctor.txt
```

Pick the device when several are connected:

```bash
./scripts/doctor.sh --serial 1234567890abcdef > doctor.txt
```

If `adb` or a device is missing, the script exits with code **2** (adb) or **3**
(device) and a clear message; it does not print a partial report.

**VERIFIED (host):** `bash -n`, `--help`, and the missing-device path
(`error: no adb device in 'device' state...`, exit 3).
**UNVERIFIED (device):** every reported value on a real phone.

## What the report contains

| Field | Example | Why it matters |
|---|---|---|
| `generated_utc` | `2026-10-26T17:45:00Z` | correlate with logcat |
| `adb` | `Android Debug Bridge version 1.0.41` | tooling mismatch |
| `manufacturer`, `model` | `Google`, `Pixel 8` | device-specific bugs |
| `android_release`, `sdk` | `15`, `35` | API behavior/permissions |
| `abi`, `abilist` | `arm64-v8a`, `arm64-v8a,armeabi-v7a` | native library availability |
| `ram_total_mb` | `7923` | model choice/OOM |
| `data_free_mb` | `48211` | model download/storage |
| `package`, `app_installed` | `com.martins.whisper`, `yes` | right app |
| `version_name`, `version_code` | `0.1.0`, `1` | fixed in which build? |
| `apk_path` | `/data/app/.../base.apk` | debug vs release |
| `first_install`, `last_update` | timestamps | "after update" bugs |
| `permission.RECORD_AUDIO` | `granted` / `denied` | mic failures |
| `permission.POST_NOTIFICATIONS` | `granted` / `not-required (sdk<33)` | hidden service UI |
| `appop.SYSTEM_ALERT_WINDOW` | `allow` / `deny` | overlay failures |
| `accessibility_service` | `enabled` / `disabled` | insertion failures |
| `battery_optimization` | `not ignored` / `ignored (exempt)` | service killed |
| `models_dir`, `model` | `base.bin bytes=147951465 sha256=...` | missing/corrupt model |
| `backend` | `unavailable (app v0.1.0...)` | GPU/CPU fallback |
| `last_error` | `unavailable (not surfaced...)` | app error code |

`backend` and `last_error` are placeholders until the app exposes them; the
script prints `unavailable`, never a guess.

Illustrative shape (not a captured device run):

```text
Martins Whisper doctor report
format=1
generated_utc=2026-10-26T17:45:00Z
adb=Android Debug Bridge version 1.0.41
manufacturer=Google
model=Pixel 8
android_release=15
sdk=35
abi=arm64-v8a
abilist=arm64-v8a,armeabi-v7a,armeabi
ram_total_mb=7923
data_free_mb=48211
package=com.martins.whisper
app_installed=yes
version_name=0.1.0
version_code=1
permission.RECORD_AUDIO=granted
permission.POST_NOTIFICATIONS=granted
appop.SYSTEM_ALERT_WINDOW=allow
accessibility_service=enabled
battery_optimization=not ignored
models_dir=files/models
model=base.bin bytes=147951465 sha256=60ed5bc3dd14eea8...
backend=unavailable (app v0.1.0 has no doctor screen; see docs/DOCTOR.md)
last_error=unavailable (not surfaced by app v0.1.0)
end_of_report
```

## What it deliberately excludes

- Device serial number (used only to select the target, never printed).
- Transcripts, clipboard contents and audio.
- Accounts, email addresses, tokens, keys.
- A full package dump or the list of other installed apps.
- App-private file contents other than model file names/sizes/hashes.

Read the report before attaching it: the phone model and model list are still
device-specific information. If you need to hide the model, delete those lines.

## Bug report template

```text
What I did:
What I expected:
What happened:
When (local time + timezone):
Reproducible: always / sometimes / once
App version (from doctor): 0.1.0 (code 1)
Doctor report: <attach doctor.txt>
Logcat: <attach the filtered logcat excerpt; see docs/TROUBLESHOOTING.md>
Screenshot: <optional; avoid private text>
```

## Planned in-app doctor (G1)

When G1 wires the real engine, the in-app **Diagnostics** section should show and
copy the same fields plus:

- active backend (`whisper.cpp/vulkan` or `whisper.cpp/cpu`) and the fallback
  reason,
- selected model id and SHA-256 prefix,
- idle-unload state (`loaded`, `unloaded`, `last used N s ago`),
- last error code,
- free storage.

It must still exclude audio and transcripts. The operator script is the reference
for the field names so both stay consistent.
