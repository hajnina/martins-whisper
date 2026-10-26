# U2b — Overlay feedback states, haptics, accessibility

Owner: U2B-CODER (branch `agent/u2-states`). One session, one commit. Base: current `master`
(G2/G3 merged).

## Goal

Give the overlay distinct, testable feedback for every dictation state and make it accessible.

1. **Pure presentation mapping** (new `app/src/main/java/com/martins/whisper/overlay/BubblePresentation.kt`,
   no Android types): `DictationState` -> `BubbleVisual(accent: Int, icon: BubbleIcon,
   contentDescription: String, label: String?)`.
   - idle: mic, teal, “Hold to dictate”
   - loading-model (`Loading`): spinner, amber, “Loading model…” + the state label
   - listening (`Recording`): mic + level bars, red, “Listening; release to transcribe”
   - transcribing (`Processing`): a **distinct** transcribing icon (e.g. three dots, not the same
     spinner as loading), blue, “Transcribing…”
   - success: check, green, the message
   - error: cross, dark red, “Error: <message>”
   Distinctness is the point: loading vs transcribing vs listening vs error must not look the same.
2. **MicBubbleView** uses the mapping for accent, icon drawing and `contentDescription`; keep the
   existing expanded layout, timer, level bars and gear/stop controls working.
3. **Haptics**: start (`ACTION_DOWN`) and stop (`ACTION_UP`) emit a short tick
   (`HapticFeedbackConstants.VIRTUAL_KEY`); success keeps `CONFIRM`, error keeps `REJECT`. Route all
   haptics through an internal injectable seam, e.g.
   `internal var hapticFeedback: (Int) -> Unit = { performHapticFeedback(it) }`, so tests can record
   calls without a device.
4. **Accessibility**: every state sets a non-blank, state-specific `contentDescription`; error and
   success include the message. The view stays usable with TalkBack (content description + existing
   touch handling).

## Tests

- Plain JVM `BubblePresentationTest`: all six states map to the expected accent/icon/content
  description; loading/transcribing/listening/error/idle are distinct; error/success include their
  messages.
- Robolectric `MicBubbleViewRobolectricTest`: setting a state updates `contentDescription`; the
  haptic seam records `CONFIRM`/`REJECT` for success/error and `VIRTUAL_KEY` on ACTION_DOWN/UP;
  loading/processing start the spinner without crashing.
- Existing `OverlayControllerRobolectricTest`, `OverlayGeometryTest`, `BubbleFormatTest` must stay
  green.

## Inputs

- `app/src/main/java/com/martins/whisper/overlay/MicBubbleView.kt`
- `app/src/main/java/com/martins/whisper/service/DictationState.kt`
- `app/src/test/java/com/martins/whisper/harness/OverlayControllerRobolectricTest.kt` (pattern)

## Acceptance

```
cd /work/martins-whisper-worktrees/u2-states
export JAVA_HOME=/work/android-toolchain/jdk
export ANDROID_HOME=/work/android-toolchain/sdk ANDROID_SDK_ROOT=/work/android-toolchain/sdk
export GRADLE_USER_HOME=/home/node/gh-u2b
./gradlew :app:testDebugUnitTest --offline
```

BUILD SUCCESSFUL; new tests counted in the XML totals; `git status` clean.

## Status

open

## Result

-
