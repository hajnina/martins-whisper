package com.martins.whisper.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.EditText
import android.widget.LinearLayout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.util.ReflectionHelpers

/**
 * On device the bubble tap delivers our own overlay's content-changed events and the foreign
 * field can stop reporting input focus while the overlay/IME owns the window. Neither may turn
 * a working insertion into a clipboard-only result.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class ClipboardFallbackRegressionRobolectricTest {

    private lateinit var activity: Activity
    private lateinit var field: EditText
    private lateinit var service: DictationAccessibilityService
    private val transcript = "hello whisper"

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val layout = LinearLayout(activity)
        field = EditText(activity)
        layout.addView(field)
        activity.setContentView(layout)
        service = Robolectric.buildService(DictationAccessibilityService::class.java).create()
            .also { ReflectionHelpers.callInstanceMethod<AccessibilityService>(it.get(), "onServiceConnected") }
            .get()
    }

    @After
    fun tearDown() {
        service.onDestroy()
        InsertionPolicy.allowOwnPackageForE2E = false
        InsertionPolicy.allowOwnPackageForOnboardingTest = false
    }

    @Test
    fun `overlay content events do not erase the remembered foreign field`() {
        plantField(viewId = "notes:id/body", fieldText = "draft", focused = true)
        rememberFocused()

        val ownEvent = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        ownEvent.packageName = service.packageName
        service.onAccessibilityEvent(ownEvent)
        ownEvent.recycle()

        assertNotNull(service.lastFocusedSnapshot())
        assertTrue(service.captureTargetAtStart())
    }

    @Test
    fun `a field that lost reported focus after the bubble tap is still inserted into`() {
        plantField(viewId = "notes:id/body", fieldText = "draft", focused = true)
        rememberFocused()

        val ownEvent = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        ownEvent.packageName = service.packageName
        service.onAccessibilityEvent(ownEvent)
        ownEvent.recycle()

        plantField(viewId = "notes:id/body", fieldText = "draft", focused = false)

        val result = service.insert(transcript)

        assertEquals(InsertResult.Inserted, result)
        assertEquals("draft hello whisper", field.text.toString())
        assertNull(clipText())
    }

    /** The service snapshots the field exactly as it does from a focused event. */
    private fun rememberFocused() {
        val node = planted!!
        val snapshot = ReflectionHelpers.callInstanceMethod<NodeSnapshot>(
            service,
            "toSnapshot",
            ReflectionHelpers.ClassParameter.from(AccessibilityNodeInfo::class.java, node),
        )
        ReflectionHelpers.setField(service, "lastFocused", snapshot)
    }

    private var planted: AccessibilityNodeInfo? = null

    private fun clipboard(): ClipboardManager =
        activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private fun clipText(): String? =
        clipboard().primaryClip?.getItemAt(0)?.text?.toString()

    private fun plantField(viewId: String, fieldText: String, focused: Boolean) {
        shadowOf(service).setWindows(emptyList())
        val node = AccessibilityNodeInfo.obtain(field)
        node.javaClass.getMethod("setClassName", CharSequence::class.java)
            .invoke(node, "android.widget.EditText")
        node.javaClass.getMethod("setPackageName", CharSequence::class.java)
            .invoke(node, "com.vendor.notes")
        node.javaClass.getMethod("setEditable", Boolean::class.javaPrimitiveType).invoke(node, true)
        node.javaClass.getMethod("setEnabled", Boolean::class.javaPrimitiveType).invoke(node, true)
        node.javaClass.getMethod("setVisibleToUser", Boolean::class.javaPrimitiveType).invoke(node, true)
        node.javaClass.getMethod("setFocused", Boolean::class.javaPrimitiveType).invoke(node, focused)
        node.javaClass.getMethod("setShowingHintText", Boolean::class.javaPrimitiveType).invoke(node, false)
        node.javaClass.getMethod("setPassword", Boolean::class.javaPrimitiveType).invoke(node, false)
        node.javaClass.getMethod("setViewIdResourceName", String::class.java).invoke(node, viewId)
        node.setText(fieldText)
        node.javaClass.getMethod("setTextSelection", Integer.TYPE, Integer.TYPE)
            .invoke(node, fieldText.length, fieldText.length)
        planted = node
        shadowOf(node).setOnPerformActionListener { action, arguments ->
            if (action != AccessibilityNodeInfo.ACTION_SET_TEXT) return@setOnPerformActionListener false
            val text = arguments.getCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            )
            field.setText(text)
            field.setSelection((text?.length ?: 0).coerceAtMost(field.length()))
            true
        }
        node.javaClass.getMethod("setSealed", Boolean::class.javaPrimitiveType).invoke(node, true)
        val window = AccessibilityWindowInfo.obtain()
        shadowOf(window).setRoot(node)
        shadowOf(window).setType(AccessibilityWindowInfo.TYPE_APPLICATION)
        shadowOf(window).setFocused(true)
        shadowOf(window).setActive(true)
        shadowOf(service).setWindows(listOf(window))
    }
}
