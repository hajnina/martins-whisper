package com.martins.whisper.debug

import android.content.Intent
import android.widget.EditText
import com.martins.whisper.R
import com.martins.whisper.accessibility.InsertionPolicy
import java.io.File
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class DebugTextActivityTest {

    @After
    fun tearDown() {
        InsertionPolicy.allowOwnPackageForE2E = false
    }

    @Test
    fun `target screen exposes text password and hint fields and allows its own package`() {
        val controller = Robolectric.buildActivity(DebugTextActivity::class.java).setup()
        val activity = controller.get()

        assertTrue(InsertionPolicy.allowOwnPackageForE2E)
        val text: EditText = activity.findViewById(R.id.e2e_text)
        val password: EditText = activity.findViewById(R.id.e2e_password)
        val hint: EditText = activity.findViewById(R.id.e2e_hint)
        assertEquals("", text.text.toString())
        assertEquals(DebugTextActivity.PASSWORD_SEED, password.text.toString())
        assertEquals(DebugTextActivity.HINT_TEXT, hint.hint?.toString())
        assertEquals("", hint.text.toString())

        val state = stateFile(activity).readText().let(::JSONObject)
        assertEquals("", state.getString("text"))
        assertEquals(DebugTextActivity.PASSWORD_SEED, state.getString("password"))

        text.setText("inserted words")
        val updated = stateFile(activity).readText().let(::JSONObject)
        assertEquals("inserted words", updated.getString("text"))

        controller.pause()
        assertFalse(InsertionPolicy.allowOwnPackageForE2E)
        controller.destroy()
    }

    @Test
    fun `script extras clear the fields and focus the requested one`() {
        val controller = Robolectric.buildActivity(DebugTextActivity::class.java).setup()
        val activity = controller.get()
        val text: EditText = activity.findViewById(R.id.e2e_text)
        val password: EditText = activity.findViewById(R.id.e2e_password)
        val hint: EditText = activity.findViewById(R.id.e2e_hint)
        text.setText("old words")
        hint.setText("old hint")

        controller.newIntent(
            Intent(activity, DebugTextActivity::class.java)
                .putExtra(DebugTextActivity.EXTRA_FOCUS, DebugTextActivity.FOCUS_PASSWORD)
                .putExtra(DebugTextActivity.EXTRA_CLEAR, true),
        )

        assertEquals("", text.text.toString())
        assertEquals(DebugTextActivity.PASSWORD_SEED, password.text.toString())
        assertEquals("", hint.text.toString())
        assertTrue(password.hasFocus())
        val state = stateFile(activity).readText().let(::JSONObject)
        assertEquals(DebugTextActivity.FOCUS_PASSWORD, state.getString("focused"))

        controller.newIntent(
            Intent(activity, DebugTextActivity::class.java)
                .putExtra(DebugTextActivity.EXTRA_FOCUS, DebugTextActivity.FOCUS_HINT),
        )
        assertTrue(hint.hasFocus())
        controller.destroy()
    }

    private fun stateFile(activity: DebugTextActivity): File = File(activity.filesDir, "e2e/state.json")
}
