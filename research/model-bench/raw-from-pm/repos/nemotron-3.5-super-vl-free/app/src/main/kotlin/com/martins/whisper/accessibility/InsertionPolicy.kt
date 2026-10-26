package com.martins.whisper.accessibility

data class NodeSnapshot(
    val packageName: String?,
    val isEditable: Boolean,
    val isEnabled: Boolean,
    val isVisible: Boolean,
    val isPassword: Boolean,
    val hasReadableText: Boolean,
    val selectionStart: Int,
    val selectionEnd: Int,
)

sealed interface InsertionRoute {
    data object SetText : InsertionRoute
    data object Paste : InsertionRoute
    data class ClipboardOnly(val reason: String) : InsertionRoute
    data class Reject(val reason: String) : InsertionRoute
}

object InsertionPolicy {
    fun route(
        node: NodeSnapshot?,
        ownPackage: String,
        text: String,
    ): InsertionRoute {
        if (text.isEmpty()) return InsertionRoute.Reject("empty text")
        if (node == null) return InsertionRoute.ClipboardOnly("no focused editable field")
        if (node.packageName == ownPackage) return InsertionRoute.Reject("own package focused")
        if (!node.isEditable) return InsertionRoute.ClipboardOnly("focused field is not editable")
        if (!node.isEnabled) return InsertionRoute.ClipboardOnly("focused field is disabled")
        if (!node.isVisible) return InsertionRoute.ClipboardOnly("focused field is not visible")
        if (node.isPassword) return InsertionRoute.ClipboardOnly("password field")
        if (!node.hasReadableText) return InsertionRoute.Paste
        return InsertionRoute.SetText
    }
}
