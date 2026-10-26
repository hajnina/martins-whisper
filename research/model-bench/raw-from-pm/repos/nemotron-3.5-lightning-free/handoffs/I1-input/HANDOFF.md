# I1-input Handoff

## Executive summary
Implemented the Android accessibility insertion path: UTF-16/surrogate-safe `TextInsertionPlanner`, focus tracking, `ACTION_SET_TEXT` -> clipboard+`ACTION_PASTE` -> clipboard+toast/notification fallback, and a process-wide `AccessibilityInserter` bridge with `StateFlow<Boolean> connected` and `suspend insert(text)`. Added the accessibility service XML/config, minimal additive manifest entries, and pure policy tests. `:core:test` + `:app:testDebugUnitTest` pass (31 tests) and `:app:assembleDebug` succeeds. Robolectric cannot run in this aarch64 sandbox (bundled conscrypt has x86_64 native only); no device is available, so the on-device matrix is documented but unverified.

## Files changed / added
- `core/src/main/kotlin/com/martins/whisper/core/Contracts.kt` (surrogate-safe clamping/selection/caret implementation)
- `core/src/test/kotlin/com/martins/whisper/core/TextInsertionPlannerTest.kt` (20 tests)
- `app/src/main/kotlin/com/martins/whisper/accessibility/DictationAccessibilityService.kt`
- `app/src/main/kotlin/com/martins/whisper/accessibility/InsertionPolicy.kt`
- `app/src/main/kotlin/com/martins/whisper/accessibility/InsertResult.kt` (`InsertResult`, `TextInserter`)
- `app/src/test/kotlin/com/martins/whisper/accessibility/InsertionPolicyTest.kt` (11 tests)
- `app/src/main/res/xml/accessibility_service_config.xml`
- `app/src/main/res/values/strings.xml` (added service/fallback strings)
- `app/src/main/AndroidManifest.xml` (additive service + `POST_NOTIFICATIONS`)

## Build / test evidence
Commands (toolchain env is required; `GRADLE_USER_HOME` points at the shared cache):

```sh
export JAVA_HOME=/work/android-toolchain/jdk
export ANDROID_HOME=/work/android-toolchain/sdk ANDROID_SDK_ROOT=/work/android-toolchain/sdk
export GRADLE_USER_HOME=/work/android-toolchain/gradle-home
/home/node/.local/bin/gradle :core:test :app:testDebugUnitTest --no-daemon --console=plain
/home/node/.local/bin/gradle :app:assembleDebug --no-daemon --console=plain
```

Results:
- `:core:test` PASS, `TextInsertionPlannerTest`: 20 tests, 0 failures.
- `:app:testDebugUnitTest` PASS, `InsertionPolicyTest`: 11 tests, 0 failures.
- `:app:assembleDebug` BUILD SUCCESSFUL; APK produced at `app/build/outputs/apk/debug/app-debug.apk`.
- Kotlin only emits expected deprecation warnings for `AccessibilityNodeInfo.recycle()` on API 33+.

## Interfaces / invariants
- `TextInsertionPlanner.insert(current, selectionStart, selectionEnd, insert)`:
  - clamps both bounds into `0..current.length`; inverted ranges collapse at `selectionStart`;
  - never splits a UTF-16 surrogate pair at either selection boundary (expands to the full pair);
  - returns `selectionStart == selectionEnd == start + insert.length` (caret after inserted text);
  - handles `null`/empty current and empty insert.
- `InsertionPolicy.route(NodeSnapshot?, ownPackage, text): InsertionRoute`
  - empty text -> `Reject`;
  - null/disabled/invisible/password -> `ClipboardOnly`;
  - own package -> `Reject`;
  - editable with unreadable text (WebView/Compose) -> `Paste`;
  - otherwise -> `SetText`.
- `DictationAccessibilityService : TextInserter`
  - `insert(text)` is synchronous and must run on the main thread;
  - finds `FOCUS_INPUT` from `findFocus`, `rootInActiveWindow`, then interactive windows (focused/active first);
  - `refresh()` validation and one re-find retry for stale nodes; all obtained nodes/windows are recycled;
  - fallback order: `ACTION_SET_TEXT` (with selection args) -> clipboard + `ACTION_PASTE` -> clipboard + toast + notification (`Copied`);
  - own package is ignored; password/disabled/invisible fields go to clipboard.
- `AccessibilityInserter` (process-wide):
  - `val connected: StateFlow<Boolean>`;
  - `suspend fun insert(text): InsertResult`, dispatches to `Dispatchers.Main.immediate`;
  - service reference is cleared on unbind/destroy (no retained context leak).
- `lastFocusedSnapshot()` exposes the last validated editable focus for diagnostics.

## Contract changes
No signature changes to `Contracts.kt`. The prior `TextInsertionPlanner` body was replaced with the surrogate-safe implementation. New app-only types `InsertResult`, `TextInserter`, `NodeSnapshot`, `InsertionRoute` are additive.

## Integration notes / manifest merge snippet
The manifest edits are additive and minimal. If U1 already added a service/permission block, keep one copy (duplicate `<service>` with the same name will break manifest merge).

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<service
    android:name=".accessibility.DictationAccessibilityService"
    android:exported="true"
    android:label="@string/accessibility_service_label"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>
```

- `accessibility_service_config.xml` points `android:settingsActivity` at `com.martins.whisper.ui.MainActivity`; create that class in U1 or update the string.
- Callers should observe `AccessibilityInserter.connected` to show "enable accessibility" onboarding. If the service is not connected, `insert` returns `Failed("accessibility service not connected")`; the caller must do its own clipboard fallback (the bridge intentionally has no Application context).
- `InsertResult.Copied` means text is on the clipboard; `Failed` means nothing was inserted/copied.
- Do not clear the clipboard automatically (per SPEC 3.6).

## Known limitations / blockers
- No Android device/emulator: no real end-to-end insertion evidence. All behavior is code-reviewed + JVM-tested only.
- Robolectric smoke tests were attempted and removed: on this aarch64 host Robolectric 4.14.1 loads `libconscrypt_openjdk_jni-linux-aarch_64.so`, which is absent from `conscrypt-openjdk-uber-2.5.2` (x86_64 only). Unblock by running on x86_64 or supplying an aarch64 conscrypt native build; the pure decision tests already cover routing.
- `ACTION_SET_TEXT` may return `true` but ignore the selection bundle in some editors; caret placement is not verifiable without a device.
- Compose/WebView fields often expose editable nodes without readable text; these intentionally use paste. Some WebViews reject both `ACTION_SET_TEXT` and `ACTION_PASTE` and end at clipboard fallback.
- `ACTION_PASTE` needs the target field to retain input focus; the overlay must stay `FLAG_NOT_FOCUSABLE` (U1).
- Notification fallback is skipped when `POST_NOTIFICATIONS` is denied (Android 13+); the toast and clipboard still work.
- Reading `node.text` for non-password fields is required for `SET_TEXT`; spans are flattened to plain text.

## On-device test matrix (unverified in sandbox)
| Target | Expected route | Checks |
|---|---|---|
| AOSP Contacts/Messages `EditText` | SET_TEXT | text at cursor, replace selection, emoji, undo |
| WhatsApp / Signal / Telegram composer | SET_TEXT, else PASTE | cursor mid-text, selection replace, multiline |
| Chrome/WebView contenteditable | PASTE (no readable text) | paste at caret, no duplicate, no crash |
| Compose `BasicTextField` apps | PASTE or clipboard | focus retained while overlay used |
| Search fields / disabled fields | clipboard fallback | toast + notification; no focus theft |
| Password fields | clipboard fallback | no text leak into field |
| Split-screen / freeform multi-window | SET_TEXT in active window | active-window pick, stale-node retry |
| Own app text field | reject/ignore | nothing inserted; no self-paste loop |
| Accessibility turned off / revoked | `Failed(not connected)` | U1 shows clipboard fallback and onboarding |
| Emoji/surrogate text | SET_TEXT | full emoji preserved, caret after emoji |
| Notification permission denied (33+) | clipboard + toast | no crash, notification skipped |

## References used
- https://developer.android.com/reference/android/accessibilityservice/AccessibilityService - service lifecycle/focus APIs and XML config fields.
- https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo#ACTION_SET_TEXT - `ACTION_SET_TEXT` + selection bundle arguments.
- https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo#ACTION_PASTE - paste fallback and focus requirement.
- https://developer.android.com/develop/ui/views/notifications/notification-permission - Android 13 `POST_NOTIFICATIONS` runtime gate.
- https://developer.android.com/reference/android/content/ClipboardManager - clipboard fallback semantics.
- Local SPEC 3.6 / ADR-003 / ADR-006 for route order and non-focusable overlay constraint.

## Commit
`agent/i1-input` - see `git log -1` after this handoff commit.
