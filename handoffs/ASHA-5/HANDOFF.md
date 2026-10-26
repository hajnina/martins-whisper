# ASHA-5 — explicit language + bubble drag (owner tickets 1,2)

Branch `agent/ui-device2`, worktree `/work/martins-whisper-worktrees/ui-device2`.
**Stacked on `agent/ui-device1` (`8491c0f`): merge `agent/ui-device1` first, then this branch.**
Code commit `2757cc9`. No capture/gate/VAD/telemetry changes.

## Ticket 1 — explicit language (English default, Czech, auto opt-in)
- `SettingsStore`: default language is now `en` (`DEFAULT_LANGUAGE = LANGUAGE_ENGLISH`). Supported set is
  exactly `{auto, en, cs}` (`SUPPORTED_LANGUAGES`); any older/unsupported stored value falls back to
  English instead of leaking an unexpected code to whisper.
- Main screen spinner (Recognition section) now offers **Auto detect / English / Čeština**, preselected
  from settings. Selecting an item persists immediately.
- Engine path already existed and is now covered by tests: `DictationService` reads
  `settings.effectiveLanguage()` at finalize time and passes it to
  `DictationEngineHost.transcribe` → `SpeechEngine.transcribe(language)` → JNI
  (`params.language = "en"/"cs"`, `detect_language = false`). Auto is the only path that sends `null`
  (whisper detects; explicitly opt-in, since it flips on 2-5 s clips).
- Default model is untouched: still `base` and asserted multilingual.
- Files: `app/src/main/java/com/martins/whisper/data/SettingsStore.kt`,
  `app/src/main/java/com/martins/whisper/MainActivity.kt`,
  `app/src/main/res/values/strings.xml`.

## Ticket 2 — bubble drag drift + snap
- `MicBubbleView` records `rawX/rawY` at ACTION_DOWN and passes **finger travel since DOWN** to the
  listener; it never uses view-local coordinates for dragging (`onDragStart()` added as a default
  interface method, so existing listeners still compile).
- `OverlayController.beginDrag()` snapshots the window origin; `dragBy(dxFromDown,dyFromDown)` uses
  `OverlayGeometry.draggedPosition` = origin + finger travel (constant touch offset), clamped to the
  screen every event; `endDrag()` snaps to the nearest left/right edge, updates the window and
  persists the position.
- `DictationService` listener wires `onDragStart → beginDrag`, `onDrag → dragBy`, `onDragEnd → endDrag`.
- Files: `app/src/main/java/com/martins/whisper/overlay/{MicBubbleView,OverlayController,OverlayGeometry}.kt`,
  `app/src/main/java/com/martins/whisper/service/DictationService.kt`.

## Tests
- New `LanguageSettingsTest`: default English, Czech persists across instances, auto → null,
  unsupported stored value → English, default base model multilingual.
- New `BubbleDragRobolectricTest` (`@Config(qualifiers = "w1080dp-h1920dp-mdpi")`): synthetic
  MotionEvents through the real bubble view + real drag bookkeeping; asserts the window follows the
  finger from the DOWN point within **2 px** at four move points, then release snaps to the nearest
  edge and persists `bubbleX/bubbleY`.
- `DictationServiceRobolectricTest`: engine receives `"cs"` when Czech is selected and `null` for Auto.
- `MainActivityDeviceFixTest`: language spinner has exactly 3 options, defaults to English, selecting
  Czech persists `cs`.
- `OverlayGeometryTest`: offset math, clamping, snap choice + y clamp.

## Evidence (real commands/output)
Env: `JAVA_HOME=/work/android-toolchain/jdk ANDROID_HOME=/work/android-toolchain/sdk GRADLE_USER_HOME=/home/node/gh-asha-ui`.

```
./gradlew :app:testDebugUnitTest \
  --tests 'com.martins.whisper.data.LanguageSettingsTest' \
  --tests 'com.martins.whisper.ui.MainActivityDeviceFixTest' \
  --tests 'com.martins.whisper.harness.DictationServiceRobolectricTest' \
  --tests 'com.martins.whisper.overlay.OverlayGeometryTest' \
  --tests 'com.martins.whisper.overlay.BubbleDragRobolectricTest' --offline
BUILD SUCCESSFUL in 12s
```
```
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --offline
BUILD SUCCESSFUL in 8s
core tests 157 skipped 0 failures 0 errors 0   (FROM-CACHE, same tree as master gate)
app  tests 127 skipped 1 failures 0 errors 0
APK: app/build/outputs/apk/debug/app-debug.apk  9,012,853 bytes
SHA-256: ccfaa8190416cf9d07d2bc107571a3e7b6c770bb2d34b9b4e30f3a6bb138f7b0
```
APK is the default non-native debug build (UI/compile evidence only).

## Limitations / notes
- Branch is stacked; MARA must merge `agent/ui-device1` first (or merge this branch, which contains it).
- Auto-detect remains available but is opt-in; the default is explicit English as requested.
- Only English/Czech are offered for now; adding a language is a one-line entry in `SUPPORTED_LANGUAGES`
  + the spinner list.
- `:app:assembleDebugAndroidTest` still cannot run offline in this worktree (androidx.test deps absent
  from the private Gradle cache); no androidTest sources changed.
