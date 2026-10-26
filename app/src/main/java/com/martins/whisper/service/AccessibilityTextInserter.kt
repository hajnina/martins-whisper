package com.martins.whisper.service

import android.content.Context
import com.martins.whisper.accessibility.AccessibilityInserter
import com.martins.whisper.accessibility.InsertResult as AccessibilityInsertResult

/**
 * Inserts into the focused field through the accessibility service. When that service is not
 * connected or refuses the target (for example a password field), falls back to the clipboard so
 * the transcript is never lost.
 */
class AccessibilityTextInserter(context: Context) : TextInserter {

    private val clipboard = ClipboardTextInserter(context)

    override fun insert(text: String): InsertResult {
        if (text.isEmpty()) return InsertResult.Failed("empty text")
        return when (val result = AccessibilityInserter.insertNow(text)) {
            AccessibilityInsertResult.Inserted -> InsertResult.Inserted
            is AccessibilityInsertResult.Copied -> InsertResult.Copied(result.reason)
            is AccessibilityInsertResult.Failed -> clipboard.insert(text)
        }
    }
}
