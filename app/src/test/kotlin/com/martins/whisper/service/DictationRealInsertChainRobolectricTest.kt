package com.martins.whisper.service

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.EditText
import android.widget.LinearLayout
import com.martins.whisper.accessibility.AccessibilityInserter
import com.martins.whisper.accessibility.DictationAccessibilityService
import com.martins.whisper.accessibility.InsertionPolicy
import com.martins.whisper.audio.AudioSource
import com.martins.whisper.core.SpeechEngine
import com.martins.whisper.core.Transcript
import com.martins.whisper.core.asr.FakeSpeechEngine
import com.martins.whisper.data.SettingsStore
import com.martins.whisper.service.EngineFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.RuntimeEnvironment
import org.robolectric.util.ReflectionHelpers

/**
 * Whole chain: the real dictation service presses against the real accessibility insertion path
 * through a live attached accessibility service, no TextInserter seam override, so the transcript
 * reaches a real EditText view byte for byte.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class DictationRealInsertChainRobolectricTest {

    private lateinit var app: Activity
    private lateinit var field: EditText
    private var dictate: ServiceController<DictationService>? = null
    private var accessibility: AccessibilityService? = null
    private var planted: AccessibilityNodeInfo? = null
    private var engine: FakeSpeechEngine? = null
    private var originalEngineFactory: ((Context, SettingsStore) -> SpeechEngine)? = null
    private var originalAudioFactory: ((Context) -> AudioSource)? = null

    @Before
    fun setUp() {
        app = Robolectric.buildActivity(Activity::class.java).setup().get()
        val layout = LinearLayout(app)
        field = EditText(app)
        layout.addView(field)
        app.setContentView(layout)
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(
            Manifest.permission.RECORD_AUDIO,
        )
        engine = FakeSpeechEngine(
            backend = "fake-jvm",
            respond = { _, language ->
                Transcript(text = "hello from fake", language = language)
            },
        )
        originalEngineFactory = EngineFactory.factory
        originalAudioFactory = DictationService.audioSourceFactory
        EngineFactory.factory = { _, _ -> engine!! }
        DictationService.audioSourceFactory = { FakeAudioSource() }
    }

    @After
    fun tearDown() {
        dictate?.destroy()
        accessibility?.onDestroy()
        planted?.recycle()
        planted = null
        InsertionPolicy.allowOwnPackageForOnboardingTest = false
        originalEngineFactory?.let { EngineFactory.factory = it }
        originalAudioFactory?.let { DictationService.audioSourceFactory = it }
    }

    @Test
    fun `the whole chain inserts the transcript into the focused editable field`() {
        attachAccessibilityService()
        plantField(viewId = "notes:id/body")

        startAndFinishDictation()

        await("insertion into the field") { field.text.toString() == "Hello from fake" }
        assertEquals("Hello from fake", field.text.toString())
        assertTrue(DictationService.state.value is DictationState.Success)
        assertEquals("Hello from fake", clipText())
        assertEquals(1, engine!!.received.size)
    }

    @Test
    fun `an own package field without the onboarding session reports a refused insert`() {
        attachAccessibilityService()
        plantField(viewId = "practice:id/field", packageName = app.packageName)

        startAndFinishDictation()

        await("insert refusal to settle") {
            DictationService.state.value is DictationState.Error
        }
        val state = DictationService.state.value
        assertTrue(state is DictationState.Error)
        assertEquals("Nothing inserted: own package focused", (state as DictationState.Error).message)
        assertEquals("", field.text.toString())
        assertNull(clipText())
    }

    private fun startAndFinishDictation() {
        val controller = Robolectric.buildService(DictationService::class.java).create()
            .also { dictate = it }
        val service = controller.get()

        ReflectionHelpers.callInstanceMethod<Void>(service, "onPress")
        await("dictation recording") { DictationService.state.value is DictationState.Recording }
        ReflectionHelpers.callInstanceMethod<Void>(service, "onRelease")
    }

    private fun attachAccessibilityService() {
        val created = Robolectric.buildService(DictationAccessibilityService::class.java).create()
        accessibility = created.get()
        ReflectionHelpers.callInstanceMethod<AccessibilityService>(
            accessibility,
            "onServiceConnected",
        )
        assertTrue(AccessibilityInserter.connected.value)
    }

    private fun clipboard(): ClipboardManager =
        app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private fun clipText(): String? =
        clipboard().primaryClip?.getItemAt(0)?.text?.toString()

    private fun await(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) {
                shadowOf(Looper.getMainLooper()).idle()
                return
            }
            Thread.sleep(10)
        }
        throw AssertionError("timed out waiting for $what; state=${DictationService.state.value}")
    }

    private fun plantField(
        viewId: String,
        packageName: String = "com.vendor.notes",
        password: Boolean = false,
    ) {
        shadowOf(accessibility!!).setWindows(emptyList())
        val node = AccessibilityNodeInfo.obtain(field)
        node.javaClass.getMethod("setClassName", CharSequence::class.java)
            .invoke(node, "android.widget.EditText")
        node.javaClass.getMethod("setPackageName", CharSequence::class.java)
            .invoke(node, packageName)
        node.javaClass.getMethod("setEditable", Boolean::class.javaPrimitiveType)
            .invoke(node, true)
        node.javaClass.getMethod("setEnabled", Boolean::class.javaPrimitiveType)
            .invoke(node, true)
        node.javaClass.getMethod("setVisibleToUser", Boolean::class.javaPrimitiveType)
            .invoke(node, true)
        node.javaClass.getMethod("setFocused", Boolean::class.javaPrimitiveType)
            .invoke(node, true)
        node.javaClass.getMethod("setShowingHintText", Boolean::class.javaPrimitiveType)
            .invoke(node, false)
        node.javaClass.getMethod("setPassword", Boolean::class.javaPrimitiveType)
            .invoke(node, password)
        node.javaClass.getMethod("setViewIdResourceName", String::class.java)
            .invoke(node, viewId)
        planted = node
        shadowOf(node).setOnPerformActionListener { action, arguments ->
            when (action) {
                AccessibilityNodeInfo.ACTION_SET_TEXT -> {
                    val text = arguments.getCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    )
                    field.setText(text)
                    val start = arguments.getInt(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT,
                    )
                    field.setSelection(start.coerceIn(0, field.length()))
                    true
                }
                AccessibilityNodeInfo.ACTION_PASTE -> {
                    val clip = clipboard().primaryClip?.getItemAt(0)?.text
                        ?: return@setOnPerformActionListener false
                    val caretAt = field.selectionStart.coerceAtLeast(0)
                    field.editableText.replace(
                        caretAt.coerceAtMost(field.length()),
                        caretAt.coerceAtMost(field.length()),
                        clip,
                    )
                    true
                }
                else -> false
            }
        }
        node.javaClass.getMethod("setSealed", Boolean::class.javaPrimitiveType).invoke(node, true)
        val window = AccessibilityWindowInfo.obtain()
        shadowOf(window).setRoot(node)
        shadowOf(window).setType(AccessibilityWindowInfo.TYPE_APPLICATION)
        shadowOf(window).setFocused(true)
        shadowOf(window).setActive(true)
        shadowOf(accessibility!!).setWindows(listOf(window))
    }

    private class FakeAudioSource : AudioSource {
        var started = false
            private set

        override fun start(onFrame: (ShortArray, Long) -> Unit, onError: (Throwable) -> Unit) {
            started = true
            var time = 1_000L
            repeat(FRAMES) {
                onFrame(TONE, time)
                time += 20
            }
        }

        override fun stop() = Unit
        override fun release() = Unit

        companion object {
            const val FRAMES = 60
            const val FRAME_SAMPLES = 320
            val TONE = ShortArray(FRAME_SAMPLES) { 6_000.toShort() }
        }
    }
}
