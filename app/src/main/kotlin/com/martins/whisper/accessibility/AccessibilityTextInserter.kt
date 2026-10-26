package com.martins.whisper.accessibility

import android.content.Context
import com.martins.whisper.service.ClipboardTextInserter
import com.martins.whisper.service.InsertResult as ServiceInsertResult
import com.martins.whisper.service.TextInserter
import kotlinx.coroutines.runBlocking

class AccessibilityTextInserter(
    context: Context,
    private val fallback: TextInserter = ClipboardTextInserter(context),
) : TextInserter {

    override fun insert(text: String): ServiceInsertResult {
        if (!AccessibilityInserter.connected.value) return fallback.insert(text)
        return runBlocking {
            try {
                when (val inserted = AccessibilityInserter.insert(text)) {
                    InsertResult.Inserted -> ServiceInsertResult.Inserted
                    is InsertResult.Copied -> ServiceInsertResult.Copied(inserted.reason)
                    is InsertResult.Failed -> ServiceInsertResult.Failed(inserted.reason)
                }
            } catch (t: Throwable) {
                ServiceInsertResult.Failed(t.message ?: "accessibility insertion failed")
            }
        }
    }
}
