# U1-overlay handoff

## Executive summary

U1 delivers the Android shell for Martins Whisper: a microphone-type foreground service with a `StateFlow` state machine, a non-focusable draggable overlay bubble (idle/loading/recording/processing/success/error, timer, RMS bars, haptics, gear/stop), and a programmatic onboarding/settings screen for mic, overlay, accessibility, notifications, model, language, GPU/CPU, 300 s idle unload and boot restore. A deterministic fake engine exercises the whole press -> record -> finalize -> clipboard path; E1 and I1 plug in through `EngineFactory` and `TextInserter`/`ACTION_TRANSCRIPT`. The debug APK builds successfully and 18 pure JVM tests pass. No device/runtime validation exists in this sandbox; Robolectric is blocked on aarch64 by a missing Conscrypt native library.

## Delivered / exact files

All paths are relative to the worktree root.

- `app/src/main/AndroidManifest.xml` - permissions, launcher activity, FGS service (`foregroundServiceType="microphone"`), boot receiver, I1 merge comment.
- `app/src/main/java/com/martins/whisper/MainActivity.kt` - onboarding/permissions, status, model/language/backend/idle pickers, privacy note, boot switch, start/stop overlay.
- `app/src/main/java/com/martins/whisper/BootReceiver.kt` - restores overlay on `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` when enabled; starts without microphone FGS type.
- `app/src/main/java/com/martins/whisper/service/DictationService.kt` - FGS lifecycle, channel/notification with Settings/Stop, overlay wiring, `START_STICKY`, `StateFlow`, press/release, 400 ms cancel, 10 min cap, timer, RMS level, idle unload, clean stop, transcript broadcast.
- `app/src/main/java/com/martins/whisper/service/DictationState.kt` - idle/loading/recording/processing/success/error.
- `app/src/main/java/com/martins/whisper/service/FakeSpeechEngine.kt` - deterministic `SpeechEngine` with load/transcribe delays.
- `app/src/main/java/com/martins/whisper/service/EngineFactory.kt` - single E1 replacement seam.
- `app/src/main/java/com/martins/whisper/service/TextInserter.kt` - `TextInserter` + clipboard implementation; I1 replacement seam.
- `app/src/main/java/com/martins/whisper/audio/AudioSource.kt`, `audio/AudioRecordSource.kt` - 16 kHz mono PCM16, 20 ms (320 sample) frames, RMS helper, permission/release handling.
- `app/src/main/java/com/martins/whisper/overlay/MicBubbleView.kt` - custom Canvas bubble/pill, states, timer/level bars, mic/check/cross/spinner, gear/stop hit targets, haptics, drag gestures.
- `app/src/main/java/com/martins/whisper/overlay/OverlayController.kt` - `TYPE_APPLICATION_OVERLAY`, `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_IN_SCREEN`, bottom-end default, drag clamp and persisted x/y.
- `app/src/main/java/com/martins/whisper/overlay/OverlayGeometry.kt`, `BubbleFormat.kt` - pure clamping/default-position/timer formatting.
- `app/src/main/java/com/martins/whisper/data/SettingsStore.kt` - SharedPreferences persistence and defensive backend parsing.
- `app/src/main/java/com/martins/whisper/ui/ModelOptions.kt` - local model catalog stub (base default, tiny/small/medium/large-v3-turbo).
- `app/src/main/java/com/martins/whisper/ui/PermissionGate.kt` - pure permission readiness/summary logic.
- `app/src/main/res/values/strings.xml`, `values/themes.xml`, `drawable/ic_mic_notification.xml`.
- Tests: `app/src/test/java/com/martins/whisper/{data,overlay,service,ui}/*.kt` (6 test classes, 18 tests).

## Build / test evidence

Environment (shared toolchain, no Nix installs):

```sh
export JAVA_HOME=/work/android-toolchain/jdk
export ANDROID_HOME=/work/android-toolchain/sdk
export ANDROID_SDK_ROOT=/work/android-toolchain/sdk
export GRADLE_USER_HOME=/work/android-toolchain/gradle-home
printf 'sdk.dir=/work/android-toolchain/sdk\n' > local.properties
```

```sh
/home/node/.local/bin/gradle -p /work/martins-whisper-worktrees/u1-overlay \
  :app:assembleDebug :app:testDebugUnitTest --no-daemon --offline
```

Result (2026-10-26):

```
BUILD SUCCESSFUL in 7s
43 actionable tasks: 8 executed, 35 up-to-date
```

- APK: `app/build/outputs/apk/debug/app-debug.apk`, 8.4 MB.
- Tests: 18 passed / 0 failed:
  - `SettingsDefaultsTest` 5
  - `BubbleFormatTest` 1
  - `OverlayGeometryTest` 3
  - `DictationStateTest` 4
  - `ServiceContractTest` 2
  - `PermissionGateTest` 3
- Robolectric was attempted first for `SettingsStore`/`MicBubbleView` and failed before any test body on this linux-aarch64 host:
  `UnsatisfiedLinkError: no conscrypt_openjdk_jni-linux-aarch_64` from `AndroidTestEnvironment.setUpApplicationState`. The Groovy-uber jar in the cache only contains `linux-x86_64`. Those tests were replaced with pure helper tests, per the U1 fallback instruction. The Robolectric dependency is left in `app/build.gradle.kts` for T1/x86 runners.
- No lint/typecheck task was run beyond Kotlin compilation; compilation completed with only deprecation warnings.

## Interfaces exposed

- Service actions: `com.martins.whisper.action.START`, `.STOP`, `.SETTINGS`, `.TRANSCRIPT`.
- Companion: `DictationService.start(context)`, `.stop(context)`, `STATE: StateFlow<DictationState>` (property name `state`), `inserterFactory`.
- Transcript hook for I1:
  - Explicit-package broadcast `com.martins.whisper.action.TRANSCRIPT` with string extra `transcript`.
  - Or replace `DictationService.inserterFactory` with an accessibility-backed `TextInserter`.
- Engine hook for E1: `EngineFactory.factory: (Context, SettingsStore) -> SpeechEngine`; the service calls `transcribe(AudioChunk(...FLUSH), language)` once per hold.
- Overlay callbacks: `MicBubbleView.Listener` (`onPress`, `onRelease`, `onCancel`, `onDrag`, `onDragEnd`, `onSettingsTap`, `onStopTap`).
- Settings keys/defaults in `SettingsStore` (`model_id=base`, `language=auto`, `backend=AUTO`, `idle_unload_seconds=300`, `overlay_enabled=false`, `boot_start=true`, bubble x/y).

## Contract changes

- No edits to `core/` and no signature changes to the shared contracts.
- `ModelOptions` is intentionally a local stub because `core.ModelCatalog` does not exist yet. E1/PM should replace it with `ModelCatalog`; ids match whisper.cpp GGML names.
- I1 manifest merge snippet (add to `app/src/main/AndroidManifest.xml`; adjust class/package to I1's final names):

```xml
<service
    android:name=".accessibility.DictationAccessibilityService"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="false">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>
```

If I1 uses a manifest receiver for transcripts instead of replacing `inserterFactory`:

```xml
<receiver
    android:name=".accessibility.TranscriptReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="com.martins.whisper.action.TRANSCRIPT" />
    </intent-filter>
</receiver>
```

## Known limitations / blockers

- Runtime validation is blocked: no Android device, no KVM emulator, no microphone, no display/GPU. Overlay drag/render, FGS microphone start, boot restore and clipboard behavior are unverified on hardware.
- Fake engine only. No native model, no real transcription, no chunker/VAD/merger use yet; each hold produces one `AudioChunk` with `ChunkReason.FLUSH`. C1/E1 should feed `AudioChunker` output and merge transcripts.
- Insertion currently always copies to clipboard (`InsertResult.Copied`). Accessibility insertion is not wired; `ACTION_TRANSCRIPT` is the handoff hook.
- Android 14+ restricts microphone FGS starts from the background. Boot restore deliberately starts with foreground type 0; a later press calls `startForeground` with microphone type and may require the app/overlay to be in a user-visible foreground state or an OEM exemption. Documented as a device bring-up item.
- Overlay expansion clamps to the right edge, so the collapsed bubble can appear shifted left after an expansion near the screen edge. Cosmetic; position is still persisted.
- `STATE` is process-global, not per-service-instance; fine for a single app process but not multi-process safe.
- Model/language/backend/idle settings are persisted but only the fake engine consumes language; model/backend/idle unload are staged for E1.
- Notification Settings/Stop actions use icon `0` and depend on notification permission on Android 13+; the FGS still runs without it.
- Robolectric/JVM Android tests cannot run on this aarch64 host due to missing Conscrypt native; use an x86_64 runner or device tests.

## Next steps

1. I1: add the accessibility service, wire it via `inserterFactory` or the transcript broadcast, verify focus retention (overlay flags are already non-focusable).
2. E1: implement `EngineFactory` with whisper.cpp and replace `ModelOptions` with `core.ModelCatalog`; feed real chunks and honor `idleUnloadSeconds`.
3. C1/PM: replace single-chunk finalize with `AudioChunker`/`Vad`/`TranscriptMerger` on the ASR dispatcher.
4. T1/PM: run Robolectric and instrumented tests on x86_64/device; device-validate overlay drag, FGS mic start, boot restore, clipboard, accessibility fallback.
5. G1: merge, then run `:app:lintDebug`, `:app:testDebugUnitTest`, `:app:assembleDebug` together.

## References

- `SPEC.md` sections 3.3, 3.7, 3.8; `DECISIONS.md` ADR-002/003/006; `KANBAN.md` U1.
- Android platform APIs used directly: `Service`, `NotificationCompat`, `WindowManager` overlay flags, `AudioRecord`, `AccessibilityManager`, `Settings.ACTION_*`.
- No external repo code was copied in this work package and no new dependency was added; the existing Robolectric/coroutines test dependencies came with the skeleton.

## Commits

- `2e8f20a` - feat(app): overlay bubble, dictation foreground service, onboarding settings
