# Doctor report for bug reports

A good bug report needs the device, Android version, app version, backend, model
and permission state. The doctor report collects exactly that. It contains **no
transcripts, no audio, no accounts and no tokens**.

> **Status in 0.1.0:** the in-app **Diagnostics** section shows native
> availability, resolved backend, fallback reason, GPU-failure count and the last
> engine error. `scripts/doctor.sh` adds device/permission/model facts and, on a
> debuggable APK, the persisted engine state. A device run is **UNVERIFIED
> (H1-blocked)** because this sandbox has no phone.

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

**VERIFIED (host):** `bash -n`, `--help`, the missing-device path
(`error: no adb device in 'device' state...`, exit 3), and the report/engine
parsing against a fake `adb` fixture. **UNVERIFIED (device):** every reported
value on a real phone, including the `run-as` SharedPreferences read.

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
| `selected_model_id` | `base` | which catalog row the engine will plan |
| `backend_setting` | `AUTO` / `GPU` / `CPU` | user preference vs actual backend |
| `backend` | `whisper.cpp/cpu` / `whisper.cpp/vulkan` / `not recorded` | which engine actually loaded |
| `fallback` | `none` / `gpu-unavailable` / `auto-cpu` / `model-missing` / ... | why CPU was used |
| `gpu_failures` | `0` / `1` | repeated GPU trouble |
| `last_error` | `Model 'Base (multilingual)' is not installed...` | typed failure text |
| `native_engine` | `loaded` / `missing` / `not loaded yet` | quick native-APK check |
| `idle_unload_seconds`, `overlay_enabled`, `boot_start` | `300`, `true`, `true` | service/settings state |

The engine fields come from the persisted `SettingsStore` SharedPreferences via
`run-as` on a debuggable APK. If the app data is not readable, the script prints
`unavailable` or `not recorded`, never a guess.

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
selected_model_id=base
backend_setting=AUTO
backend=whisper.cpp/cpu
fallback=gpu-unavailable
gpu_failures=1
last_error=none recorded
idle_unload_seconds=300
overlay_enabled=true
boot_start=true
native_engine=loaded (backend above)
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

## In-app Diagnostics (shipped with the G1 wiring)

The app's **Diagnostics** section currently shows:

- `Native whisper.cpp: available` / `missing`, plus the native load error when
  missing,
- `Resolved backend: not loaded yet` or the loaded backend
  (`whisper.cpp/vulkan`, `whisper.cpp/gpu`, `whisper.cpp/cpu`),
- `Fallback:` the typed reason (`none`, `native-unavailable`, `model-missing`,
  `gpu-unavailable`, `auto-cpu`, `engine-error`) in human wording,
- `GPU fallbacks recorded: N`,
- `Last engine error: …` after a failure,
- a hint to press the bubble when no engine has loaded yet.

It deliberately excludes audio, transcripts, the device serial and accounts. It
is the first place to look for the typed reasons in
[TROUBLESHOOTING.md](TROUBLESHOOTING.md#engine-unavailable--no-transcript-at-all);
`doctor.sh` reports the same persisted fields so both stay consistent.
