package com.martins.whisper.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.EditText
import android.widget.LinearLayout
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.util.ReflectionHelpers

/**
 * Drives the real insertion path with field state delivered the way the system hands it to a
 * connected accessibility service: a node tree obtained from a real view, planted as the root of
 * the focused window. The view's field is updated by the ACTION_SET_TEXT/ACTION_PASTE callbacks
 * the way a real view hierarchy answers them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class InsertionIntoFieldRobolectricTest {

    private lateinit var activity: Activity
    private lateinit var field: EditText
    private lateinit var service: DictationAccessibilityService
    private val transcript = "hello whisper"
    private var planted: AccessibilityNodeInfo? = null

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
        assertTrue(AccessibilityInserter.connected.value)
    }

    @After
    fun tearDown() {
        service.onDestroy()
        InsertionPolicy.allowOwnPackageForE2E = false
        InsertionPolicy.allowOwnPackageForOnboardingTest = false
    }

    @Test
    fun `set text route writes into the editable field and preserves text around the caret`() {
        field.setText("pre")
        plantField(viewId = "target:id/input", fieldText = "pre", caret = 3)
        assertTrue(service.captureTargetAtStart())

        val result = service.insert(transcript)

        assertEquals(InsertResult.Inserted, result)
        assertEquals("pre hello whisper", field.text.toString())
        assertNull(clipText())
    }

    @Test
    fun `a blank field with a placeholder hint receives the capitalized transcript`() {
        plantField(viewId = "blank:id/input", hint = true)
        assertTrue(service.captureTargetAtStart())

        val result = service.insert(transcript)

        assertEquals(InsertResult.Inserted, result)
        assertEquals("Hello whisper", field.text.toString())
        assertNull(clipText())
    }

    @Test
    fun `a blank field without text and without a hint takes the paste route`() {
        plantField(viewId = "blank:id/body")
        assertTrue(service.captureTargetAtStart())

        val result = service.insert(transcript)

        assertEquals(InsertResult.Inserted, result)
        assertEquals("Hello whisper", field.text.toString())
        assertEquals("Hello whisper", clipText())
    }

    @Test
    fun `password field is never written to and lands on the clipboard unchanged`() {
        field.setText("1234")
        plantField(viewId = "vault:id/pin", fieldText = "1234", password = true)
        assertTrue(service.captureTargetAtStart())

        val result = service.insert(transcript)

        assertTrue(result is InsertResult.Copied)
        assertEquals("password field", (result as InsertResult.Copied).reason)
        assertEquals("hello whisper", clipText())
        assertEquals("1234", field.text.toString())
    }

    @Test
    fun `node without readable text takes the paste route and lands in the real field`() {
        plantField(viewId = "compose:id/input")
        assertTrue(service.captureTargetAtStart())
        val node = planted!!

        val result = service.insert(transcript)

        assertEquals(InsertResult.Inserted, result)
        assertEquals("Hello whisper", field.text.toString())
        assertEquals("Hello whisper", clipText())
        assertNull(node.text)
    }

    @Test
    fun `own package fields are refused until the onboarding session flag allows the practice field`() {
        field.setText("")
        plantField(viewId = "practice:id/field", packageName = activity.packageName)
        assertTrue(service.captureTargetAtStart())

        val refused = service.insert(transcript)

        assertTrue(refused is InsertResult.Rejected)
        assertEquals("own package focused", (refused as InsertResult.Rejected).reason)
        assertEquals("", field.text.toString())
        assertNull(clipText())

        InsertionPolicy.allowOwnPackageForOnboardingTest = true
        field.setText("")
        assertTrue(service.captureTargetAtStart())
        val allowed = service.insert(transcript)

        assertEquals(InsertResult.Inserted, allowed)
        assertEquals("Hello whisper", field.text.toString())
    }

    @Test
    fun `target that vanished between capture and insert is rejected without a clipboard fallback`() {
        field.setText("still here")
        plantField(viewId = "gone:id/input", fieldText = "still here")
        assertTrue(service.captureTargetAtStart())

        shadowOf(service).setWindows(emptyList())
        val result = service.insert(transcript)

        assertTrue(result is InsertResult.Rejected)
        assertEquals("target field disappeared", (result as InsertResult.Rejected).reason)
        assertEquals("still here", field.text.toString())
        assertNull(clipText())
    }

    @Test
    fun `field replaced by another view with a different id is rejected as a changed target`() {
        field.setText("beta text")
        plantField(viewId = "swap:id/alpha", fieldText = "alpha text")
        assertTrue(service.captureTargetAtStart())
        plantField(viewId = "swap:id/beta", fieldText = "beta text")

        val result = service.insert(transcript)

        assertTrue(result is InsertResult.Rejected)
        assertEquals("target changed", (result as InsertResult.Rejected).reason)
        assertEquals("beta text", field.text.toString())
        assertNull(clipText())
        assertTrue(AccessibilityInserter.lastTargetChanged == true)
    }

    @Test
    fun `no capture at start falls back to the clipboard`() {
        field.setText("late text")
        plantField(viewId = "late:id/input", fieldText = "late text")

        val result = service.insert(transcript)

        assertTrue(result is InsertResult.Copied)
        assertEquals("no target field at start", (result as InsertResult.Copied).reason)
        assertEquals("hello whisper", clipText())
        assertEquals("late text", field.text.toString())
    }

    private fun clipboard(): ClipboardManager =
        activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private fun clipText(): String? =
        clipboard().primaryClip?.getItemAt(0)?.text?.toString()

    private fun plantField(
        viewId: String,
        editable: Boolean = true,
        password: Boolean = false,
        packageName: String = "com.vendor.notes",
        fieldText: String? = null,
        caret: Int? = null,
        hint: Boolean = false,
    ) {
        shadowOf(service).setWindows(emptyList())
        val node = AccessibilityNodeInfo.obtain(field)
        node.javaClass.getMethod("setClassName", CharSequence::class.java)
            .invoke(node, "android.widget.EditText")
        node.javaClass.getMethod("setPackageName", CharSequence::class.java)
            .invoke(node, packageName)
        node.javaClass.getMethod("setEditable", Boolean::class.javaPrimitiveType)
            .invoke(node, editable)
        node.javaClass.getMethod("setEnabled", Boolean::class.javaPrimitiveType)
            .invoke(node, true)
        node.javaClass.getMethod("setVisibleToUser", Boolean::class.javaPrimitiveType)
            .invoke(node, true)
        node.javaClass.getMethod("setFocused", Boolean::class.javaPrimitiveType)
            .invoke(node, true)
        node.javaClass.getMethod("setShowingHintText", Boolean::class.javaPrimitiveType)
            .invoke(node, hint)
        node.javaClass.getMethod("setPassword", Boolean::class.javaPrimitiveType)
            .invoke(node, password)
        node.javaClass.getMethod("setViewIdResourceName", String::class.java)
            .invoke(node, viewId)
        if (fieldText != null) {
            node.setText(fieldText)
            val caretAt = caret ?: fieldText.length
            node.javaClass.getMethod("setTextSelection", Integer.TYPE, Integer.TYPE)
                .invoke(node, caretAt, caretAt)
        }
        planted = node
        shadowOf(node).setOnPerformActionListener { action, arguments ->
            when (action) {
                AccessibilityNodeInfo.ACTION_SET_TEXT -> {
                    val text = arguments.getCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    )
                    val start = arguments.getInt(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT,
                    )
                    val end = arguments.getInt(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT,
                    )
                    field.setText(text)
                    field.setSelection(
                        start.coerceIn(0, field.length()),
                        end.coerceIn(0, field.length()),
                    )
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
        shadowOf(service).setWindows(listOf(window))
    }
}
