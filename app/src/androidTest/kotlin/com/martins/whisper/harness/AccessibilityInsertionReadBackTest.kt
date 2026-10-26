package com.martins.whisper.harness

import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.martins.whisper.MainActivity
import com.martins.whisper.core.TextInsertionPlanner
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Layer 2 (device-only): insertion planned for a focused editable node must be readable back
 * through the same accessibility node, including the caret position.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class AccessibilityInsertionReadBackTest {

    @Test
    fun inserted_text_and_caret_are_visible_to_accessibility() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var edit: EditText
            scenario.onActivity { activity ->
                edit = EditText(activity).apply {
                    id = FIELD_ID
                    setText("hello world")
                    setSelection("hello world".length)
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
                edit.requestFocus()
            }
            instrumentation.waitForIdleSync()

            scenario.onActivity {
                val before = edit.createAccessibilityNodeInfo()
                assumeNotNull("view has no accessibility node", before)
                val current = before.text?.toString() ?: edit.text.toString()
                val plan = TextInsertionPlanner.insert(
                    current = current,
                    selectionStart = before.textSelectionStart,
                    selectionEnd = before.textSelectionEnd,
                    insert = " brave",
                )
                edit.setText(plan.text)
                edit.setSelection(plan.selectionStart)

                val after = edit.createAccessibilityNodeInfo()
                assumeNotNull("view has no accessibility node after insertion", after)
                assertEquals(plan.text, after.text?.toString())
                assertEquals(plan.selectionStart, after.textSelectionStart)
                assertEquals(plan.selectionEnd, after.textSelectionEnd)
            }
        }
    }

    private companion object {
        const val FIELD_ID = 0x7f0f0002
    }
}
