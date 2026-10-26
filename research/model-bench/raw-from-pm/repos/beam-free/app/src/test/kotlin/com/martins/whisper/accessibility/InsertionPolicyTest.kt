package com.martins.whisper.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class InsertionPolicyTest {

    private val own = "com.martins.whisper"

    private fun node(
        packageName: String? = "com.example.chat",
        editable: Boolean = true,
        enabled: Boolean = true,
        visible: Boolean = true,
        password: Boolean = false,
        readableText: Boolean = true,
        selectionStart: Int = 3,
        selectionEnd: Int = 3,
    ) = NodeSnapshot(
        packageName = packageName,
        isEditable = editable,
        isEnabled = enabled,
        isVisible = visible,
        isPassword = password,
        hasReadableText = readableText,
        selectionStart = selectionStart,
        selectionEnd = selectionEnd,
    )

    @Test
    fun `empty text is rejected`() {
        assertEquals(InsertionRoute.Reject("empty text"), InsertionPolicy.route(node(), own, ""))
    }

    @Test
    fun `missing node falls back to clipboard`() {
        assertEquals(
            InsertionRoute.ClipboardOnly("no focused editable field"),
            InsertionPolicy.route(null, own, "hello"),
        )
    }

    @Test
    fun `own package is ignored`() {
        assertEquals(
            InsertionRoute.Reject("own package focused"),
            InsertionPolicy.route(node(packageName = own), own, "hello"),
        )
    }

    @Test
    fun `unknown package is allowed`() {
        assertEquals(InsertionRoute.SetText, InsertionPolicy.route(node(packageName = null), own, "hi"))
    }

    @Test
    fun `non editable focus falls back to clipboard`() {
        assertEquals(
            InsertionRoute.ClipboardOnly("focused field is not editable"),
            InsertionPolicy.route(node(editable = false), own, "hi"),
        )
    }

    @Test
    fun `disabled field falls back to clipboard`() {
        assertEquals(
            InsertionRoute.ClipboardOnly("focused field is disabled"),
            InsertionPolicy.route(node(enabled = false), own, "hi"),
        )
    }

    @Test
    fun `invisible field falls back to clipboard`() {
        assertEquals(
            InsertionRoute.ClipboardOnly("focused field is not visible"),
            InsertionPolicy.route(node(visible = false), own, "hi"),
        )
    }

    @Test
    fun `password field falls back to clipboard`() {
        assertEquals(
            InsertionRoute.ClipboardOnly("password field"),
            InsertionPolicy.route(node(password = true, readableText = false), own, "hi"),
        )
    }

    @Test
    fun `compose or webview node without text uses paste`() {
        assertEquals(
            InsertionRoute.Paste,
            InsertionPolicy.route(node(readableText = false), own, "hi"),
        )
    }

    @Test
    fun `readable editable field uses set text`() {
        assertEquals(InsertionRoute.SetText, InsertionPolicy.route(node(), own, "hi"))
    }

    @Test
    fun `invalid selection still uses set text because planner clamps`() {
        val result = InsertionPolicy.route(node(selectionStart = -1, selectionEnd = 999), own, "hi")
        assertEquals(InsertionRoute.SetText, result)
    }
}
