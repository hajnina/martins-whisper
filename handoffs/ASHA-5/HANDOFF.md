# ASHA-5 — U2b trim (MFVP 4b) + missing E2E insert link — HANDOFF

**Owner:** ASHA · **Branch:** `agent/u2-states` · **Base:** `636b3f9` (master, merged into branch)

## Executive summary

- Merged current master into `agent/u2-states`; resolved the known T2/U2b test conflict by
  deliberately updating `OverlayStateMachineRobolectricTest` to the new state descriptions.
- MFVP 4b now complete on this branch: start/stop ticks (VIRTUAL_KEY), inserted tick (CONFIRM),
  **error = distinct double REJECT pulse ~90 ms apart**, state + content description applied
  synchronously (bubble redraws on the next frame, well under 100 ms), and a test proving the
  overlay window is `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL` so it cannot steal focus.
- Fixed the biggest missing E2E link: `DictationService` defaulted to a clipboard-only inserter,
  so transcripts never reached the focused field. The default is now
  `AccessibilityTextInserter`, which inserts through `DictationAccessibilityService` and falls
  back to the clipboard when the service is not enabled or rejects the target (e.g. password).
- Full local gate `:core:test :app:testDebugUnitTest :app:assembleDebug --offline`:
  **234 tests (137 core + 97 app), 0 failures/errors**; default debug APK 8,982,513 B.
- README replaced with the one short MFVP install/permissions/use page. APK evidence for the
  native recipe verified against NORA-1's arm64 artifact (see below).

## Files changed

- `app/src/main/java/com/martins/whisper/overlay/BubblePresentation.kt`
  - Loading description uses the state label when present (no duplicated "Loading model… …").
- `app/src/main/java/com/martins/whisper/overlay/MicBubbleView.kt`
  - `scheduleHaptic` seam; `ERROR_DOUBLE_TICK_MS = 90L`; error emits REJECT then a second REJECT.
  - Removed dead `AnimationUtils` import/unused helper.
- `app/src/main/java/com/martins/whisper/overlay/OverlayController.kt`
  - `internal val windowFlags` for the focus-safety test.
- `app/src/main/kotlin/com/martins/whisper/accessibility/DictationAccessibilityService.kt`
  - Added non-suspending `AccessibilityInserter.insertNow(text)` for main-thread callers.
- `app/src/main/java/com/martins/whisper/service/AccessibilityTextInserter.kt` (new)
  - Accessibility insert, clipboard fallback, empty text rejected.
- `app/src/main/java/com/martins/whisper/service/DictationService.kt`
  - `inserterFactory` default is now `AccessibilityTextInserter`.
- Tests:
  - `app/src/test/java/com/martins/whisper/overlay/MicBubbleViewRobolectricTest.kt`
    (double tick, synchronous state, existing descriptions)
  - `app/src/test/java/com/martins/whisper/overlay/BubblePresentationTest.kt`
  - `app/src/test/java/com/martins/whisper/harness/OverlayControllerRobolectricTest.kt` (focus)
  - `app/src/test/java/com/martins/whisper/harness/OverlayStateMachineRobolectricTest.kt`
  - `app/src/test/java/com/martins/whisper/service/AccessibilityTextInserterTest.kt` (new)

## MFVP 4b mapping

| Requirement | Implementation | Test |
|---|---|---|
| tick on start-recording | ACTION_DOWN → VIRTUAL_KEY | `press and release emit a virtual key tick` |
| tick on stop | ACTION_UP (non-drag) → VIRTUAL_KEY | same |
| tick on text inserted | Success → CONFIRM | `success ticks once and error ticks twice` |
| error = distinct double tick | Error → REJECT + REJECT after 90 ms | `error double tick schedules…` |
| visible state within ~100 ms | `state` setter updates contentDescription + invalidates synchronously; service publishes Loading before engine work | `state, content description and haptics apply synchronously` |
| never steals focus | overlay `FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL` | `bubble window is not focusable…` |

## E2E flow after this change

tap bubble → `DictationService.onPress` (publishes Loading immediately) → AudioRecord frames →
`DictationEngineHost` buffer → `finishSession`/`transcribe` on Default → `AccessibilityTextInserter`
→ focused editable node (`ACTION_SET_TEXT`, else `ACTION_PASTE`) → `Success` state + CONFIRM tick.
Clipboard remains the fallback and still produces the "Transcript copied to clipboard" message.

## Model + native APK verification

- Default model path reviewed: `SettingsStore.modelId = "base"` → `ModelCatalog.default` →
  `ModelRepositoryProvider.forContext(filesDir)` → `<filesDir>/models`; download/import use
  `ResumableModelDownloader` with Range resume + SHA-256 verify + atomic promote. Covered by
  `ModelDownloaderTest` (7) and `HttpModelDownloaderTest` (4). No gap found.
- Native APK (NORA-1 artifact, `/work/martins-whisper-worktrees/n1-native/.../app-debug.apk`):
  `native-code: 'arm64-v8a'`; `lib/arm64-v8a/{libwhisper,libggml,libggml-base,libggml-cpu,
  libmartins_whisper,libparakeet}.so`; debug-signed; sha256
  `c90e356f6e65e82722f6a29b35ae2d661244f68b53c806820b3c4d5c751b7476`, 12,902,712 B.
  The recipe is in `.github/workflows/native-build.yml` (on master, `-Pmartins.native=true`).
- **This host is aarch64**, so the NDK's `linux-x86_64` clang cannot run locally
  (`Exec format error`). Local native builds are impossible here; CI (ubuntu x86_64) or an x86
  host is required. Default `assembleDebug` and all JVM tests still run locally.

## Commands and evidence

```bash
cd /work/martins-whisper-worktrees/u2-states
export JAVA_HOME=/work/android-toolchain/jdk
export ANDROID_HOME=/work/android-toolchain/sdk ANDROID_SDK_ROOT=/work/android-toolchain/sdk
export GRADLE_USER_HOME=/home/node/gh-asha
./gradlew :app:testDebugUnitTest --offline
```

Full gate:

```bash
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --offline
```

Result: `BUILD SUCCESSFUL`; totals parsed from the XML result dirs:
**core 137 + app 97 = 234 tests, 0 failures/errors** (includes 2 new
`AccessibilityTextInserterTest`, 7 `MicBubbleViewRobolectricTest`, 3
`OverlayControllerRobolectricTest`). Default APK: 8,982,513 B.

## Commits

- `fbb94d7` Merge branch 'master' into agent/u2-states
- `63a3d9d` feat(u2b): distinct error double tick, focus-safe overlay test, sync state visibility
- `82b1a9e` feat(e2e): insert transcripts into the focused field via accessibility with clipboard fallback
- (handoff commit follows)

## Known limitations / next

- No device run: insertion is proven by unit tests + the existing accessibility node code; H1 on
  the phone is still required.
- Native CI run (Actions) still needs the owner to push; local native build is blocked by the
  aarch64 host (NDK host tools are x86_64).
- `:app:assembleDebugAndroidTest` was not rerun this session; no androidTest sources changed.
