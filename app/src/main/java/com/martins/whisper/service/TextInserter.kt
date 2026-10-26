package com.martins.whisper.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

sealed interface InsertResult {
    data object Inserted : InsertResult
    data class Copied(val reason: String) : InsertResult
    data class Failed(val reason: String) : InsertResult

    /** Deliberately inserted nowhere (wrong target, password field): no clipboard fallback. */
    data class Rejected(val reason: String) : InsertResult
}

interface TextInserter {
    fun insert(text: String): InsertResult
}

/**
 * Temporary U1 implementation. I1 should replace this with the accessibility-backed
 * inserter; the service also emits ACTION_TRANSCRIPT for that integration.
 */
class ClipboardTextInserter(context: Context) : TextInserter {
    private val clipboard =
        context.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    override fun insert(text: String): InsertResult = try {
        clipboard.setPrimaryClip(ClipData.newPlainText("Martins Whisper", text))
        InsertResult.Copied("clipboard")
    } catch (t: Throwable) {
        InsertResult.Failed(t.message ?: "clipboard unavailable")
    }
}
