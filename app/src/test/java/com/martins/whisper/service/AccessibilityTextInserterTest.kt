package com.martins.whisper.service

import android.content.ClipboardManager
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class AccessibilityTextInserterTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun `falls back to the clipboard when the accessibility service is not connected`() {
        val result = AccessibilityTextInserter(context).insert("hello from whisper")

        assertTrue(result is InsertResult.Copied)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals("hello from whisper", clipboard.primaryClip?.getItemAt(0)?.text)
    }

    @Test
    fun `empty text is never written to the clipboard`() {
        val result = AccessibilityTextInserter(context).insert("")

        assertTrue(result is InsertResult.Failed)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(0, clipboard.primaryClip?.itemCount ?: 0)
    }
}
