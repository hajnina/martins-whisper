package com.martins.whisper.accessibility

sealed interface InsertResult {
    data object Inserted : InsertResult
    data class Copied(val reason: String) : InsertResult
    data class Failed(val reason: String) : InsertResult

    /** Deliberately inserted nowhere (wrong target, password field): no clipboard fallback. */
    data class Rejected(val reason: String) : InsertResult
}

fun interface TextInserter {
    fun insert(text: String): InsertResult
}
