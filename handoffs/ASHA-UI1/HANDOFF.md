# ASHA — UI device fixes (DEVICE-REPORT items 2,3,4,5)

Branch `agent/ui-device1`, worktree `/work/martins-whisper-worktrees/ui-device1`.
Base master `c62f533`. Code through `bb35f0a` (commits `bd26755`, `e7ad490`, `bb35f0a`).

## Merge
```
git merge --no-ff agent/ui-device1
```
Only `app/src/{main,test}`, `.gitignore` and `handoffs/ASHA-UI1/` changed; no core/capture/gate files.

## Overlap with `agent/mara-ui` (read before merging)
MARA's `agent/mara-ui` (`1835ae7`, authored concurrently at 19:45) covers the same items 2-4:
insets with `systemBars()` only, `AccessibilityManager` listener + `onWindowFocusChanged`, and
percent/MB in the **global** bar below all rows. It has no item 5.
This branch is the ticket superset: adds displayCutout, authoritative `Settings.Secure` read +
`ContentObserver`, per-row **inline** progress (fixes the reported off-screen bar), byte handling
for imports, and item 5 (bubble default + raw-delta drag). `recordings/` ignore from MARA's branch
is included here too.
Recommendation: merge `agent/ui-device1` and drop `agent/mara-ui` (merging both conflicts and
loses item 5). If MARA prefers its own branch, use `bb35f0a`'s `MainActivity`/`ui`/`overlay` code
as the conflict resolution.

## What changed
- **Item 2 edge-to-edge (Android 16):** `enableEdgeToEdge()` in `MainActivity.onCreate`; a `ViewCompat.setOnApplyWindowInsetsListener` on the root `ScrollView` applies `systemBars() | displayCutout()` insets as padding (`clipToPadding = true`), so the title clears the status bar and the last model buttons clear the nav bar.
  - `app/src/main/java/com/martins/whisper/MainActivity.kt`
- **Item 3 stale permissions:** accessibility is now read from the authoritative `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` value (`PermissionGate.accessibilityServiceEnabled`, supports `pkg/.Class` short form) with the `AccessibilityManager` list kept as fallback. A `ContentObserver` on `ENABLED_ACCESSIBILITY_SERVICES` + `ACCESSIBILITY_ENABLED` is registered in `onResume` and unregistered in `onPause`; `onResume` still refreshes. Status now updates while the app is visible and on return from system settings.
  - `MainActivity.kt`, `app/src/main/java/com/martins/whisper/ui/PermissionGate.kt`
- **Item 4 download progress:** the transferring row replaces its action buttons with a horizontal `ProgressBar` + label showing percent **and bytes**, e.g. `Downloading Base… 45% · 66 MB / 148 MB` (unknown total → bytes only; bytes propagate through `ModelBrowser`; UI updates throttled to 100 ms). Previously the bar was appended below all rows, off-screen where the user tapped.
  - `MainActivity.kt`, `app/src/main/java/com/martins/whisper/ui/ModelBrowser.kt`
- **Item 5 bubble default + drag:** `OverlayGeometry.defaultSide` puts a fresh bubble on the right edge, vertically centred — away from Gboard Enter and Messages mic/send (was bottom-right + 72 dp inset). Drag now uses `MotionEvent.rawX/rawY` deltas from ACTION_DOWN instead of view-local coordinates, removing the drift feedback loop.
  - `app/src/main/java/com/martins/whisper/overlay/{OverlayGeometry,OverlayController,MicBubbleView}.kt`

No capture / gate / VAD / telemetry / WAV code touched (MARA owns those).

## Tests (new/updated)
- New `app/src/test/java/com/martins/whisper/ui/MainActivityDeviceFixTest.kt`: dispatches system-bar insets and asserts ScrollView padding 120/180; accessibility row "Not granted" → "Granted" on `pause().resume()`; same via live `ContentObserver` notification.
- `PermissionGateTest`: setting-string parsing (exact, short form, other app, empty, null).
- `ModelBrowserTest`: bytes propagation and labels (`45% · 66 MB / 148 MB`, bytes-only).
- `OverlayGeometryTest`: new default position (right edge, vertically centred).

## Evidence (real commands/output)
Env: `JAVA_HOME=/work/android-toolchain/jdk ANDROID_HOME=/work/android-toolchain/sdk GRADLE_USER_HOME=/home/node/gh-asha-ui`.

```
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --offline
BUILD SUCCESSFUL in 8s
core tests 157 skipped 0 failures 0 errors 0   (FROM-CACHE, same tree as master gate)
app  tests 114 skipped 1 failures 0 errors 0
APK: app/build/outputs/apk/debug/app-debug.apk  9,109,293 bytes
SHA-256: 3c182b9b86741a36ba7be4f34f28be83ad70d40f9154ff7e0c58a6061c059bf1
```
Targeted run of the four touched test classes also green:
`./gradlew :app:testDebugUnitTest --tests 'com.martins.whisper.ui.*' --tests 'com.martins.whisper.overlay.OverlayGeometryTest' --offline` → BUILD SUCCESSFUL.

## Notes / limitations
- The built APK is the default non-native debug build (no `-Pmartins.native=true`); it is UI/compile evidence only. Device verification needs the CI native APK.
- `:app:assembleDebugAndroidTest` cannot run offline in this worktree: own Gradle cache lacks `androidx.test.ext:junit:1.2.1` and `androidx.test.espresso:espresso-core:3.6.1` (network not used, shared cache untouched). No androidTest sources changed.
- Behaviour change to flag to the owner: with edge-to-edge the status bar follows the light window background with dark icons instead of the old solid teal (#135D66).
