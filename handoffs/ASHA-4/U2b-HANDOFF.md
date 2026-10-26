# U2b — Overlay feedback states, haptics, accessibility — HANDOFF

**Owner:** U2B-CODER · **Branch:** `agent/u2-states` · **Base:** `c53b6ef` (master)

## Executive summary

- Added pure `BubblePresentation` mapping all six `DictationState`s to distinct
  `BubbleVisual(accent, icon, contentDescription, label)` values. Loading (amber spinner) and
  transcribing (blue three dots) are now visually distinct; listening has mic + level bars, error a
  dark-red cross, success a green check.
- `MicBubbleView` now drives accent, icon and `contentDescription` from the mapping, keeps the
  expanded layout/timer/level-bars/gear/stop behaviour, and routes every haptic through an
  injectable `internal var hapticFeedback` seam.
- Haptics: `ACTION_DOWN`/`ACTION_UP` emit `VIRTUAL_KEY`; success emits `CONFIRM`; error emits
  `REJECT`.
- Accessibility: every state has a non-blank, state-specific description; success/error include
  the message.
- Tests: `:app:testDebugUnitTest` BUILD SUCCESSFUL — 86 tests, 0 failures, 0 errors, 0 skipped
  (7 new `BubblePresentationTest`, 5 new `MicBubbleViewRobolectricTest`). Existing
  `OverlayControllerRobolectricTest`, `OverlayGeometryTest`, `BubbleFormatTest` still green.

## Files changed

- `app/src/main/java/com/martins/whisper/overlay/BubblePresentation.kt` (new)
  - `BubbleIcon` enum, `BubbleVisual` data class, `BubblePresentation.of(state)`.
  - No Android imports; colours are ARGB `Int` constants.
- `app/src/main/java/com/martins/whisper/overlay/MicBubbleView.kt`
  - State setter uses `BubblePresentation.of(value).contentDescription`.
  - `internal var hapticFeedback: (Int) -> Unit = { performHapticFeedback(it) }`; all haptic calls
    go through it.
  - `onDraw` switches on `BubbleVisual.icon`; new `drawTranscribing` (three dots).
  - Removed the local `accentFor` mapping (now in `BubblePresentation`).
- `app/src/test/java/com/martins/whisper/overlay/BubblePresentationTest.kt` (new, plain JVM)
- `app/src/test/java/com/martins/whisper/overlay/MicBubbleViewRobolectricTest.kt` (new)

## State mapping

| State | Accent | Icon | contentDescription | label |
|---|---|---|---|---|
| `Idle` | teal `#135D66` | `MIC` | `Hold to dictate` | `null` |
| `Loading(label)` | amber `#B26A00` | `SPINNER` | `Loading model…` (+ label if different) | `label` |
| `Recording` | red `#C62828` | `MIC_LEVELS` | `Listening; release to transcribe` | `null` |
| `Processing(label)` | blue `#1565C0` | `TRANSCRIBING` (3 dots) | `Transcribing…` | `label` |
| `Success(message)` | green `#2E7D32` | `CHECK` | `message` | `message` |
| `Error(message)` | dark red `#B00020` | `CROSS` | `Error: message` | `message` |

Loading vs processing use different accents *and* different icons; all six accents and all six
icons are distinct (asserted in tests).

## Haptics

- `ACTION_DOWN` on the bubble body → `HapticFeedbackConstants.VIRTUAL_KEY`.
- `ACTION_UP` (non-drag release) → `VIRTUAL_KEY`.
- `Success` state change → `CONFIRM`; `Error` state change → `REJECT`.
- All go through `MicBubbleView.hapticFeedback`; tests replace it with a recording lambda.

## Commands and evidence

```bash
cd /work/martins-whisper-worktrees/u2-states
export JAVA_HOME=/work/android-toolchain/jdk
export ANDROID_HOME=/work/android-toolchain/sdk ANDROID_SDK_ROOT=/work/android-toolchain/sdk
export GRADLE_USER_HOME=/home/node/gh-u2b
./gradlew :app:testDebugUnitTest --offline
```

Output (tail):

```
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 27s
30 actionable tasks: 23 executed, 7 from cache
```

Counts from `app/build/test-results/testDebugUnitTest/*.xml` (18 suites):

```
com.martins.whisper.overlay.BubblePresentationTest        tests=7 failures=0 errors=0 skipped=0
com.martins.whisper.overlay.MicBubbleViewRobolectricTest  tests=5 failures=0 errors=0 skipped=0
com.martins.whisper.overlay.OverlayGeometryTest           tests=3 failures=0 errors=0 skipped=0
com.martins.whisper.overlay.BubbleFormatTest              tests=1 failures=0 errors=0 skipped=0
com.martins.whisper.harness.OverlayControllerRobolectricTest tests=2 failures=0 errors=0 skipped=0
TOTAL                                                     tests=86 failures=0 errors=0 skipped=0
```

## Commits

- `4763078` feat(u2b): state-driven bubble presentation, haptics seam, accessibility
- (this handoff commit follows)

## Known limitations / notes for integrator

- Spinner animation still starts for both `Loading` and `Processing` (ticket asked that
  loading/processing start the spinner without crashing); `Processing` draws the distinct three-dot
  icon, so the animation is invisible there.
- Haptic ticks fire only for the main bubble press/release, not for the gear/stop control taps
  (preserved existing control behaviour).
- No device/emulator run in this session: verification is Robolectric + JVM only.
- No changes outside `app/src/main`/`app/src/test` overlay files; no shared Gradle home touched.

## References

- Existing `MicBubbleView.kt` / `DictationState.kt` patterns in this repo.
- Android `HapticFeedbackConstants` docs (VIRTUAL_KEY / CONFIRM / REJECT).
