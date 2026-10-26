package com.martins.whisper.accessibility

import android.app.Application
import android.content.Context
import com.martins.whisper.service.DictationService
import com.martins.whisper.service.InsertResult
import com.martins.whisper.service.TextInserter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.android.controller.ServiceController

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class AccessibilityTextInserterTest {

    private lateinit var app: Application
    private var accessibilityController: ServiceController<DictationAccessibilityService>? = null

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        detachAccessibility()
    }

    @After
    fun tearDown() {
        detachAccessibility()
    }

    private fun detachAccessibility() {
        accessibilityController.let { controller ->
            if (controller != null) {
                AccessibilityInserter.detach(controller.get())
                controller.destroy()
                accessibilityController = null
            }
        }
    }

    private fun connectAccessibility() {
        accessibilityController =
            Robolectric.buildService(DictationAccessibilityService::class.java).create()
        AccessibilityInserter.attach(accessibilityController!!.get())
    }

    private fun runDefaultFactory(text: String): InsertResult =
        DictationService.inserterFactory(app).insert(text)

    @Test
    fun `adapter routes insert through AccessibilityInserter when service is connected`() {
        connectAccessibility()
        assertTrue(AccessibilityInserter.connected.value)
        val result = AccessibilityTextInserter(app).insert("")
        assertEquals(InsertResult.Failed("empty text"), result)
    }

    @Test
    fun `adapter falls back to clipboard when service is not connected`() {
        assertEquals(false, AccessibilityInserter.connected.value)
        val result = AccessibilityTextInserter(app).insert("")
        assertEquals(InsertResult.Copied("clipboard"), result)
    }

    @Test
    fun `default DictationService factory uses accessibility inserter when connected`() {
        connectAccessibility()
        val result = runDefaultFactory("")
        assertEquals(InsertResult.Failed("empty text"), result)
    }

    @Test
    fun `default DictationService factory falls back to clipboard when disconnected`() {
        val result = runDefaultFactory("")
        assertEquals(InsertResult.Copied("clipboard"), result)
    }
}
