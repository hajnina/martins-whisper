# 06 — Which operators, devices and permissions does D1 document for v1?

Type: grilling
Status: resolved
Blocked by: 01

## Question

Decide the D1 documentation set and its audience:

- Who installs v1: a technical sideloader (Martin/dev) or a non-technical user? What does that imply for the permission checklist and troubleshooting?
- Which minimum device profile is v1 supported on (Android version, RAM, arm64, Vulkan presence)? What is best-effort vs unsupported?
- Which docs are required: build, install/sideload, adb model push, permission wizard, privacy/data-flow, known OEM quirks, uninstall/cleanup?
- How are model download/import, accessibility enablement, and overlay/mic permissions explained without assuming Android expertise?
- What is the "doctor" output that should be pasted into a bug report (backend, model, permissions, device, version), with no secrets?

Output: doc outline with owners and acceptance ("a new user can install and dictate from this alone").

## Answer

**Audience:** two readers. (1) Martin/owner: technical sideloader with ADB and a terminal. (2) A careful non-technical Android user who can enable permissions but should never need a shell. Every doc must work for reader 2 unless it is explicitly labelled "operator".

**v1 support matrix (proposed, confirm with ticket 09 device facts):**

- Supported: arm64-v8a Android 10–15 (API 29–35), 6 GB RAM minimum for `tiny`/`base`, 8 GB recommended for `small`.
- Best-effort: other arm64 phones on Android 10–15; OEM background/overlay restrictions documented per device.
- Unsupported v1: x86/x86_64 devices/emulators, 32-bit, Android 9 and below, tablets without a usable mic, devices where accessibility cannot be enabled.
- GPU: optional. Vulkan present + working is a bonus; CPU-only is supported. Never promise a speedup.
- Model: `base` default (~141 MiB download, ~388 MB RAM); `tiny` low-end; `small` optional on 8 GB; `medium`/`large-v3-turbo` catalog-only/experimental.

**Required docs (under `docs/`):**

1. `BUILD.md` — toolchain versions, env vars, `./gradlew` commands, offline cache, Docker/CI fallback.
2. `INSTALL.md` — download APK, enable unknown sources, install, first run, upgrade/uninstall (sideload only).
3. `PERMISSIONS.md` — mic, overlay/display-over-other-apps, accessibility, notification, battery optimisation; what each does and how to revoke.
4. `MODELS.md` — download/import, sizes, SHA-256, storage, idle unload, delete model, no cloud.
5. `PRIVACY.md` — local-only audio/transcripts, network only for explicit model download, no telemetry/accounts, what is stored and where.
6. `DEVICE-MATRIX.md` — supported/best-effort/unsupported table, tested devices + Android version + result (starts empty until H1).
7. `TROUBLESHOOTING.md` — mic/overlay/accessibility/permission failures, no text inserted, model missing/corrupt, OOM, engine unavailable; each with a concrete next step.
8. `DOCTOR.md` — what the in-app doctor shows and how to copy it into a bug report.

**Doctor output (pasteable, no secrets):** app version/versionCode, device model + Android/API, ABI, RAM, backend (VULKAN/CPU) + fallback reason, model id + SHA-256 prefix, permission states, free storage, idle-unload state, last error code. Never include transcript text or audio.

**Acceptance:** a new user can go from APK file to a successful dictation using `INSTALL` + `PERMISSIONS` alone; every failure path in `TROUBLESHOOTING` has a next action; `DEVICE-MATRIX` is updated after each H1 run.
