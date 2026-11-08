package com.martins.whisper.service

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.EditText
import android.widget.LinearLayout
import com.martins.whisper.accessibility.AccessibilityInserter
import com.martins.whisper.accessibility.InsertionPolicy
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
 * Adapter contract: AccessibilityTextInserter must pass through accessibility outcomes and only
 * use the clipboard accessibility when it cannot reach a live target.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class AccessibilityTextInserterContractRobolectricTest {

    private lateinit var activity: Activity
    private lateinit var field: EditText
    private var service: AccessibilityService? = null
    private var planted: AccessibilityNodeInfo? = null
    private val transcript = "hello whisper"

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val layout = LinearLayout(activity)
        field = EditText(activity)
        layout.addView(field)
        activity.setContentView(layout)
    }

    @After
    fun tearDown() {
        planted?.recycle()
        planted = null
        service?.onDestroy()
        InsertionPolicy.allowOwnPackageForOnboardingTest = false
    }

    @Test
    fun `own package field is refused until the onboarding flag and then inserts`() {
        attachAccessibilityService()
        field.setText("draft")
        plantField(
            viewId = "practice:id/field",
            packageName = activity.packageName,
            fieldText = "draft",
            caret = 5,
        )
        assertTrue(AccessibilityInserter.captureTargetAtStart())

        val refused = AccessibilityTextInserter(activity).insert(transcript)

        assertEquals(InsertResult.Rejected("own package focused"), refused)
        assertEquals("draft", field.text.toString())
        assertNull(clipText())

        InsertionPolicy.allowOwnPackageForOnboardingTest = true
        assertTrue(AccessibilityInserter.captureTargetAtStart())
        val allowed = AccessibilityTextInserter(activity).insert(transcript)

        assertEquals(InsertResult.Inserted, allowed)
        assertEquals("draft hello whisper", field.text.toString())
        assertNull(clipText())
    }

    @Test
    fun `password field outcome is passed through without an extra clipboard write`() {
        attachAccessibilityService()
        field.setText("4242")
        plantField(viewId = "vault:id/pin", fieldText = "4242", password = true)
        assertTrue(AccessibilityInserter.captureTargetAtStart())

        val result = AccessibilityTextInserter(activity).insert(transcript)

        assertEquals(InsertResult.Copied("password field"), result)
        assertEquals("hello whisper", clipText())
        assertEquals("4242", field.text.toString())
    }

    @Test
    fun `without a connected service the transcript is saved on the clipboard`() {
        val standalone = Robolectric.buildService(
            com.martins.whisper.accessibility.DictationAccessibilityService::class.java,
        ).create()
        ReflectionHelpers.callInstanceMethod<AccessibilityService>(standalone.get(), "onServiceConnected")
        standalone.get().onDestroy()
        assertTrue(!AccessibilityInserter.connected.value)

        val result = AccessibilityTextInserter(activity).insert(transcript)

        assertEquals(InsertResult.Copied("clipboard"), result)
        assertEquals("hello whisper", clipText())
    }

    private fun attachAccessibilityService() {
        val created = Robolectric.buildService(
            com.martins.whisper.accessibility.DictationAccessibilityService::class.java,
        ).create()
        service = created.get()
        ReflectionHelpers.callInstanceMethod<AccessibilityService>(service, "onServiceConnected")
        assertTrue(AccessibilityInserter.connected.value)
    }
    private fun clipboard(): ClipboardManager =
        activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private fun clipText(): String? =
        clipboard().primaryClip?.getItemAt(0)?.text?.toString()

    private fun plantField(
        viewId: String,
        packageName: String = "com.vendor.notes",
        fieldText: String? = null,
        caret: Int? = null,
        password: Boolean = false,
    ) {
        shadowOf(service!!).setWindows(emptyList())
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
        if (fieldText != null) {
            node.setText(fieldText)
            node.javaClass.getMethod("setTextSelection", Integer.TYPE, Integer.TYPE)
                .invoke(node, caret ?: fieldText.length, caret ?: fieldText.length)
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
        shadowOf(service!!).setWindows(listOf(window))
    }
}
