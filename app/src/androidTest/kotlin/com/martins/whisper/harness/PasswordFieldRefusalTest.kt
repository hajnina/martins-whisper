package com.martins.whisper.harness

import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.martins.whisper.MainActivity
import com.martins.whisper.accessibility.InsertionPolicy
import com.martins.whisper.accessibility.InsertionRoute
import com.martins.whisper.accessibility.NodeSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Layer 2 (device-only): a real password field must be recognised and refused, and its text
 * must never be mutated by the insertion planner.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class PasswordFieldRefusalTest {

    @Test
    fun password_field_is_refused_and_text_is_untouched() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var edit: EditText
            scenario.onActivity { activity ->
                edit = EditText(activity).apply {
                    id = FIELD_ID
                    inputType =
                        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    setText("correct horse battery staple")
                    requestFocus()
                }
                activity.setContentView(
                    LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(
                            edit,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                    },
                )
            }
            instrumentation.waitForIdleSync()

            scenario.onActivity {
                val node = edit.createAccessibilityNodeInfo()
                assumeTrue("device exposes no password accessibility node", node != null)
                assumeTrue("node is not marked as password", node.isPassword)
                val snapshot = NodeSnapshot(
                    packageName = edit.context.packageName,
                    isEditable = node.isEditable,
                    isEnabled = node.isEnabled,
                    isVisible = node.isVisibleToUser,
                    isPassword = node.isPassword,
                    hasReadableText = !node.isPassword && node.text != null,
                    selectionStart = node.textSelectionStart,
                    selectionEnd = node.textSelectionEnd,
                )
                assertEquals(
                    InsertionRoute.ClipboardOnly("password field"),
                    InsertionPolicy.route(snapshot, "com.example.other", "inserted text"),
                )
                assertEquals(
                    InsertionRoute.Reject("own package focused"),
                    InsertionPolicy.route(snapshot, edit.context.packageName, "inserted text"),
                )
                assertEquals("correct horse battery staple", edit.text.toString())
            }
        }
    }

    private companion object {
        const val FIELD_ID = 0x7f0f0003
    }
}
