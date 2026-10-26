# MFVP-1 Wire AccessibilityInserter into DictationService (BLOCKING)
status: open
acceptance: `:app:testDebugUnitTest --offline` has a test that a FeedTextInserter reaches AccessibilityInserter when connected, clipboard fallback when not; code: DictationService.inserterFactory returns accessibility-backed inserter.
Evidence of gap (2026-10-26, MARA audit):
- DictationService.kt:514 default inserterFactory = ClipboardTextInserter -> text is only COPIED, never inserted into the focused field. MFVP definition of done requires insert.
- AccessibilityInserter (app/src/main/kotlin/com/martins/whisper/accessibility/DictationAccessibilityService.kt:267) exists, attach/detach on onServiceConnected/onUnbind, insert() is suspend returning InsertResult.
Task (<=15 min, one commit): create adapter `AccessibilityTextInserter(context)` : TextInserter using runBlocking on AccessibilityInserter.insert; set as DictationService.inserterFactory default with Imperative fallback to ClipboardTextInserter when AccessibilityInserter.connected==false; unit test both paths (inject a fake via existing test seams). Do not change AccessibilityInserter's public surface.
