# Release checklist - Martins Whisper 0.1.0

This is the operator checklist for cutting and distributing a Martins Whisper
build. v1 is **sideload only** and uses **debug signing**; there is no Play
Store release. Run the sections in order.

> **Verification labels used below:** **VERIFIED (host)** means the command or
> fact is recorded in this repo's build/install docs. **UNVERIFIED (host, not
> run)** means the procedure has not been executed in this sandbox.
> **UNVERIFIED (device, H1-blocked)** means it needs a physical arm64 phone and
> has not been run here. Do not turn any device item into a pass without
> evidence.

## 1. Versioning

`versionCode` and `versionName` live in `app/build.gradle.kts:17-18`:

```kotlin
versionCode = 1
versionName = "0.1.0"
```

- [ ] Bump **`versionCode`** by at least 1 for every APK that will be installed
      on a device. It must be a monotonically increasing integer; never reuse or
      decrease it.
- [ ] Bump **`versionName`** to the release label (SemVer is fine:
      `0.1.0` -> `0.1.1` or `0.2.0`). The tag, release title and this value
      should agree.
- [ ] Confirm the edited values:

      ```bash
      cd /work/martins-whisper-worktrees/d1-docs
      grep -nE 'versionCode|versionName' app/build.gradle.kts
      ```

- [ ] Confirm the values embedded in the built APK (host check):

      ```bash
      source scripts/env.sh
      aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk \
        | grep -E '^package:|versionCode'
      ```

      Expected for this release: package `com.martins.whisper`, versionName
      `0.1.0`, versionCode `1`. This check is **VERIFIED (host)** in
      [BUILD.md](BUILD.md) ("aapt2 dump badging reports ... versionName 0.1.0,
      versionCode 1").

- [ ] Read the version actually installed on the phone:

      ```bash
      adb shell dumpsys package com.martins.whisper | grep versionName
      adb shell dumpsys package com.martins.whisper | grep versionCode
      ```

      **UNVERIFIED (device, H1-blocked).** `scripts/doctor.sh` also prints
      `version_name` and `version_code` from the same package dump
      (`scripts/doctor.sh:131-132`).

## 2. Build the release APK

For v1 the release artifact **is the debug-signed arm64 APK**. Build it with
the native engine enabled; the default build has no native libraries and cannot
transcribe ([BUILD.md](BUILD.md) "Native NDK", [INSTALL.md](INSTALL.md)
"Operator path").

- [ ] Bootstrap once on a fresh host, then load the toolchain:

      ```bash
      cd /work/martins-whisper-worktrees/d1-docs
      ./scripts/bootstrap-build.sh
      source scripts/env.sh
      ```

- [ ] Build the arm64 native APK using the command from [BUILD.md](BUILD.md):

      ```bash
      ./gradlew :app:assembleDebug --offline -Pmartins.native=true
      ```

      Output: **`app/build/outputs/apk/debug/app-debug.apk`**.

      The native variant needs NDK `27.0.12077973`; install it with
      `./scripts/bootstrap-build.sh --with-ndk` or use the x86_64
      Docker/GitHub Actions path in [BUILD.md](BUILD.md). **UNVERIFIED (host,
      not run):** this sandbox has no NDK, so the native variant was not built
      here.

- [ ] Verify the native library is packaged:

      ```bash
      unzip -l app/build/outputs/apk/debug/app-debug.apk \
        | grep 'lib/arm64-v8a/libmartins_whisper.so'
      ```

      A matching line means the engine is present. No output means this is the
      non-native build; do not ship it as the release asset. **UNVERIFIED
      (host, not run)** for the native build; the default build was verified to
      have no `lib/` entries.

- [ ] Optional: build the Gradle `release` variant instead of `debug`:

      ```bash
      ./gradlew :app:assembleRelease --offline -Pmartins.native=true
      ```

      Without a signing config this lands at
      **`app/build/outputs/apk/release/app-release-unsigned.apk`**. After the
      signing config in section 3b it lands at
      **`app/build/outputs/apk/release/app-release.apk`**. **UNVERIFIED (host,
      not run).**

- [ ] Record the artifact hash for the release notes:

      ```bash
      sha256sum app/build/outputs/apk/debug/app-debug.apk
      ```

      The APK hash changes on every rebuild because of the debug-signing
      timestamp; publish the hash of the exact file you upload, never an older
      run's hash ([BUILD.md](BUILD.md)).

## 3. Signing options

Use option **(a) debug signing** for v1. Option **(b)** is the path only if a
real release keystore is ever required.

### 3a. Debug signing (recommended for v1)

The repo has **no `signingConfig`** in `app/build.gradle.kts`, so `assembleDebug`
uses the Android Gradle Plugin's default debug key. Debug signing is sufficient
for personal sideloading and upgrades on the same machine/key.

| Property | Debug default |
|---|---|
| Keystore | `$HOME/.android/debug.keystore` (created by AGP on first debug build) |
| Alias | `androiddebugkey` |
| Store password | `android` |
| Key password | `android` |
| Key validity | **365 days** |
| Signing schemes | v2 verified for the 0.1.0 APK (`apksigner`; [BUILD.md](BUILD.md)) |

- [ ] Confirm the debug APK is signed (host):

      ```bash
      source scripts/env.sh
      apksigner verify --print-certs app/build/outputs/apk/debug/app-debug.apk
      ```

      **VERIFIED (host)** for the 0.1.0 debug APK in [BUILD.md](BUILD.md).

- [ ] Watch the 365-day validity. After the debug key expires, AGP can create a
      new one; the key changes, so Android rejects an in-place update. Delete
      `$HOME/.android/debug.keystore` only when you accept a new signing
      identity, and expect `INSTALL_FAILED_UPDATE_INCOMPATIBLE` on the phone.
      Uninstall first; that deletes models and settings ([INSTALL.md](INSTALL.md)
      "Upgrade").

> **No Play release is planned.** v1 is sideload-only
> ([INSTALL.md](INSTALL.md), [PRIVACY.md](PRIVACY.md)); the accessibility
> overlay is also a known Play policy risk (`docs/RISK_REGISTER.md` R-09). Do
> not prepare an AAB, Play App Signing, or a store listing. Debug signing is
> the intended v1 signing method.

### 3b. Real keystore (only if needed)

> **UNVERIFIED (host, not run).** These commands were not executed in this
> sandbox. Keep the keystore and passwords out of the repository and out of
> shell history.

- [ ] Create the keystore outside the worktree. `keytool` comes from the JDK in
      `scripts/env.sh`:

      ```bash
      source scripts/env.sh
      mkdir -p "$HOME/.martins-whisper"
      keytool -genkeypair -v \
        -keystore "$HOME/.martins-whisper/martins-whisper-release.jks" \
        -storetype PKCS12 \
        -alias martins-whisper \
        -keyalg RSA \
        -keysize 4096 \
        -validity 10000 \
        -dname "CN=Martins Whisper, O=Martins Whisper, C=US"
      ```

- [ ] Export the passwords for the build (never write them to a tracked file):

      ```bash
      export MARTINS_KEYSTORE="$HOME/.martins-whisper/martins-whisper-release.jks"
      export MARTINS_KEY_ALIAS=martins-whisper
      read -rsp 'Keystore password: ' MARTINS_KEYSTORE_PASSWORD; echo
      read -rsp 'Key password: ' MARTINS_KEY_PASSWORD; echo
      export MARTINS_KEYSTORE_PASSWORD MARTINS_KEY_PASSWORD
      ```

- [ ] Add a signing config to `app/build.gradle.kts`. Add the
      `signingConfigs` block inside `android { }`, and add the
      `signingConfig` line to the existing `release { }` block:

      ```kotlin
      android {
          signingConfigs {
              create("release") {
                  storeFile = file(System.getenv("MARTINS_KEYSTORE"))
                  storePassword = System.getenv("MARTINS_KEYSTORE_PASSWORD")
                  keyAlias = System.getenv("MARTINS_KEY_ALIAS")
                  keyPassword = System.getenv("MARTINS_KEY_PASSWORD")
              }
          }

          buildTypes {
              release {
                  signingConfig = signingConfigs.getByName("release")
                  isMinifyEnabled = false
                  proguardFiles(
                      getDefaultProguardFile("proguard-android-optimize.txt"),
                      "proguard-rules.pro",
                  )
              }
          }
      }
      ```

      The current `.gitignore` does **not** ignore `*.jks` or `*.keystore`.
      Add both patterns before putting a keystore anywhere under the worktree,
      or keep the keystore in `$HOME` as shown. Back the keystore and passwords
      up offline: losing them makes in-place upgrades impossible.

- [ ] Build and verify the signed release APK:

      ```bash
      ./gradlew :app:assembleRelease --offline -Pmartins.native=true
      apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
      aapt2 dump badging app/build/outputs/apk/release/app-release.apk \
        | grep -E '^package:|versionCode'
      ```

      Expected output: `app/build/outputs/apk/release/app-release.apk`,
      signature verification success, and the version values from section 1.
      **UNVERIFIED (host, not run).**

## 4. Distribution

### 4a. GitHub release

Prerequisites: an authenticated `gh` (`gh auth status`) and a clean artifact
from section 2. The tag should match `versionName`.

- [ ] Copy the APK to a versioned filename and create its checksum:

      ```bash
      cd /work/martins-whisper-worktrees/d1-docs
      cp app/build/outputs/apk/debug/app-debug.apk \
        /tmp/martins-whisper-0.1.0-arm64.apk
      cd /tmp
      sha256sum martins-whisper-0.1.0-arm64.apk \
        > martins-whisper-0.1.0-arm64.apk.sha256
      cat martins-whisper-0.1.0-arm64.apk.sha256
      ```

- [ ] Write the release notes template to `/tmp/martins-whisper-0.1.0-notes.md`
      (fill in `<commit>` and `<sha256>`):

      ```markdown
      # Martins Whisper 0.1.0

      Sideload-only release for arm64 Android 10+ (API 29+). No Play Store
      release is planned.

      - Package: `com.martins.whisper`
      - versionName: `0.1.0`
      - versionCode: `1`
      - ABI: `arm64-v8a` only
      - Signing: debug key (personal sideload)
      - Commit: `<commit>`
      - SHA-256: `<sha256>`

      ## Install

      1. Download `martins-whisper-0.1.0-arm64.apk` on the phone.
      2. Allow "Install unknown apps" for the browser or file manager.
      3. Tap the APK, then Install.
      4. Grant microphone, display over other apps, and accessibility
         insertion. See `docs/PERMISSIONS.md`.
      5. Download or import a model in the app.

      This asset is the native arm64 build (`-Pmartins.native=true`). Without
      the native library the app can download models but cannot transcribe.

      Device verification status: **UNVERIFIED (device, H1-blocked)** unless
      this release notes say otherwise.
      ```

- [ ] Publish the release:

      ```bash
      gh release create v0.1.0 \
        /tmp/martins-whisper-0.1.0-arm64.apk \
        /tmp/martins-whisper-0.1.0-arm64.apk.sha256 \
        --title "Martins Whisper 0.1.0" \
        --notes-file /tmp/martins-whisper-0.1.0-notes.md \
        --latest
      ```

      **UNVERIFIED (host, not run).** No GitHub credentials are available in
      this sandbox.

- [ ] Verify a downloaded asset on any host:

      ```bash
      sha256sum -c martins-whisper-0.1.0-arm64.apk.sha256
      ```

- [ ] On the phone, download the APK from the release page, allow **Install
      unknown apps** for that source, tap the file, then **Install**. See
      [INSTALL.md](INSTALL.md) "User path". **UNVERIFIED (device, H1-blocked).**

### 4b. `adb install -r` with `scripts/install-apk.sh`

The script is an operator helper around `adb install -r`; it keeps app data on
reinstall. Read from `scripts/install-apk.sh`:

| Behavior | Source |
|---|---|
| Default APK: `app/build/outputs/apk/debug/app-debug.apk` | `scripts/install-apk.sh:9` |
| `--apk PATH` selects another APK | `scripts/install-apk.sh:21` |
| `--serial SERIAL` picks a device; defaults to `$ANDROID_SERIAL` or the only connected device | `scripts/install-apk.sh:22`, `:94-106` |
| `ADB` env var, then `adb` on `PATH`, then `$ANDROID_HOME`/`$ANDROID_SDK_ROOT`/toolchain `platform-tools/adb` | `scripts/install-apk.sh:40-61` |
| Fails if the APK is missing | `scripts/install-apk.sh:89` |
| Refuses zero or multiple devices unless `--serial` is given | `scripts/install-apk.sh:96-100` |
| Prints APK path, SHA-256 and target before installing | `scripts/install-apk.sh:111-115` |
| Runs `adb install -r "$APK"` | `scripts/install-apk.sh:117` |

- [ ] Install the default debug APK on the only connected device:

      ```bash
      cd /work/martins-whisper-worktrees/d1-docs
      source scripts/env.sh
      ./scripts/install-apk.sh
      ```

- [ ] Or point at a specific APK and device:

      ```bash
      ./scripts/install-apk.sh \
        --apk /tmp/martins-whisper-0.1.0-arm64.apk \
        --serial <device-serial>
      ```

      List serials with `adb devices`. The device state must be `device`, not
      `unauthorized` or `offline`.

- [ ] Check the script's help and error paths on the host:

      ```bash
      ./scripts/install-apk.sh --help
      bash -n scripts/install-apk.sh
      ```

      **VERIFIED (host):** `--help`, `bash -n`, missing-adb and no-device paths
      ([INSTALL.md](INSTALL.md)). **UNVERIFIED (device, H1-blocked):** the
      actual install on a phone.

- [ ] If Android reports `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, the signing key
      changed. Uninstall (`adb uninstall com.martins.whisper`) and install
      again; this deletes models and settings. Do not mix APKs built with
      different keys ([INSTALL.md](INSTALL.md) "Upgrade").

## 5. Host-run pre-release checks

Run these before publishing. None of them proves device behavior.

- [ ] Toolchain and script sanity:

      ```bash
      cd /work/martins-whisper-worktrees/d1-docs
      ./scripts/bootstrap-build.sh
      source scripts/env.sh
      for f in scripts/*.sh; do bash -n "$f" || exit 1; done
      ```

- [ ] Core test gate:

      ```bash
      ./gradlew :core:test --offline --no-daemon --console=plain
      ```

      The release gate is **zero failures**, not a fixed test count. The last
      recorded integrated run in [BUILD.md](BUILD.md) was 135 JVM tests
      (`:core:test` 85 + `:app:testDebugUnitTest` 50); a later integration note
      reports 141. Use the printed result from this run.

- [ ] Full host gate (tests + APK), as in [BUILD.md](BUILD.md):

      ```bash
      ./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug \
        --offline --no-daemon --console=plain
      ```

      Expected: `BUILD SUCCESSFUL`, zero test failures, and
      `app/build/outputs/apk/debug/app-debug.apk`. **VERIFIED (host)** as a
      procedure in [BUILD.md](BUILD.md); re-run it for the exact commit being
      released.

- [ ] Doctor script, because [DOCTOR.md](DOCTOR.md),
      [TROUBLESHOOTING.md](TROUBLESHOOTING.md), [INSTALL.md](INSTALL.md) and
      [PRIVACY.md](PRIVACY.md) reference it:

      ```bash
      ./scripts/doctor.sh --help
      ./scripts/doctor.sh > doctor.txt
      ```

      `--help` needs no device and is **VERIFIED (host)**. The full report
      needs a connected device and is **UNVERIFIED (device, H1-blocked)**; it
      prints `version_name`, `version_code`, permissions, models and engine
      state without transcripts or tokens (`scripts/doctor.sh:126-135`).

- [ ] Artifact checks for the exact file being published:

      ```bash
      aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk \
        | grep -E '^package:|versionCode'
      unzip -l app/build/outputs/apk/debug/app-debug.apk \
        | grep 'lib/arm64-v8a/libmartins_whisper.so'
      sha256sum app/build/outputs/apk/debug/app-debug.apk
      ```

## 6. Device-needed steps (all UNVERIFIED)

Every item in this section is **UNVERIFIED (device, H1-blocked)**. No phone was
available in this environment, and no device result is claimed.

- [ ] **Install:** `./scripts/install-apk.sh` completes and prints `Installed.`
      **UNVERIFIED (device, H1-blocked).**
- [ ] **Version:** `adb shell dumpsys package com.martins.whisper | grep
      versionName` returns `0.1.0`, and `grep versionCode` returns `1`.
      **UNVERIFIED (device, H1-blocked).**
- [ ] **Launch:** the app opens to the **Martins Whisper** screen and shows the
      permission summary. **UNVERIFIED (device, H1-blocked).**
- [ ] **Permissions:** microphone, display over other apps, and accessibility
      insertion show **Granted**; notifications are recommended. See
      [PERMISSIONS.md](PERMISSIONS.md). **UNVERIFIED (device, H1-blocked).**
- [ ] **Model:** a model is **Installed · selected** (in-app download/import or
      `./scripts/push-model.sh`). See [MODELS.md](MODELS.md). **UNVERIFIED
      (device, H1-blocked).**
- [ ] **Engine:** **Diagnostics** says **Native whisper.cpp: available**.
      **UNVERIFIED (device, H1-blocked).**
- [ ] **First dictation:** hold the bubble, release, and get **Inserted** (or
      the clipboard fallback) per [FIRST-DICTATION.md](FIRST-DICTATION.md).
      **UNVERIFIED (device, H1-blocked).**
- [ ] **Doctor report:** `./scripts/doctor.sh > doctor.txt` prints a complete
      report with `end_of_report`. **UNVERIFIED (device, H1-blocked).**
- [ ] **Upgrade:** reinstall over the old version with the same key keeps app
      data and models. **UNVERIFIED (device, H1-blocked).**
- [ ] **Uninstall:** `adb uninstall com.martins.whisper` removes the app, models
      and settings. **UNVERIFIED (device, H1-blocked).**

Record the device model, Android version/API, build fingerprint
(`adb shell getprop ro.build.fingerprint`), APK SHA-256 and pass/fail evidence
in the run handoff and [DEVICE-MATRIX.md](DEVICE-MATRIX.md). Do not mark H1
complete without that evidence.

## Related guides

[BUILD.md](BUILD.md) · [INSTALL.md](INSTALL.md) ·
[FIRST-DICTATION.md](FIRST-DICTATION.md) · [DOCTOR.md](DOCTOR.md) ·
[DEVICE-MATRIX.md](DEVICE-MATRIX.md) · [RISK_REGISTER.md](RISK_REGISTER.md)
