package com.martins.whisper.debug

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.martins.whisper.R
import com.martins.whisper.accessibility.InsertionPolicy
import java.io.File
import org.json.JSONObject

/**
 * Debug-only insertion target for device E2E, so tests do not depend on Google Keep or any
 * third-party app. Three fields: normal text, password, and hint-only. Writes what the fields
 * really contain to files/e2e/state.json on every change; the script reads that back after
 * insertion. Never present in release builds (debug source set only).
 */
class DebugTextActivity : Activity() {

    private lateinit var textField: EditText
    private lateinit var passwordField: EditText
    private lateinit var hintField: EditText
    private lateinit var statusView: TextView
    private var suppress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
        applyIntent(intent)
        writeState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntent(intent)
        writeState()
    }

    private fun applyIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_CLEAR, false) == true) {
            suppress = true
            textField.setText("")
            passwordField.setText(PASSWORD_SEED)
            hintField.setText("")
            suppress = false
        }
        when (intent?.getStringExtra(EXTRA_FOCUS)) {
            FOCUS_PASSWORD -> passwordField.requestFocus()
            FOCUS_HINT -> hintField.requestFocus()
            else -> textField.requestFocus()
        }
    }

    override fun onResume() {
        super.onResume()
        InsertionPolicy.allowOwnPackageForE2E = true
        writeState()
    }

    override fun onPause() {
        InsertionPolicy.allowOwnPackageForE2E = false
        writeState()
        super.onPause()
    }

    private fun buildContent(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(40), dp(20), dp(20))
            setBackgroundColor(Color.WHITE)
        }
        root.addView(TextView(this).apply {
            text = "E2E insertion target"
            textSize = 20f
            setTextColor(Color.rgb(19, 93, 102))
        })
        root.addView(TextView(this).apply {
            text = "Focus a field, then tap the Martins Whisper bubble."
            textSize = 12f
            setTextColor(Color.rgb(90, 95, 100))
            setPadding(0, dp(4), 0, dp(12))
        })

        textField = field(R.id.e2e_text, "e2e text field", InputType.TYPE_CLASS_TEXT)
        root.addView(label("Normal field"))
        root.addView(textField, matchWidth())

        passwordField = field(
            R.id.e2e_password,
            "e2e password field",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
        ).apply { setText(PASSWORD_SEED) }
        root.addView(label("Password field (must refuse)"))
        root.addView(passwordField, matchWidth())

        hintField = field(R.id.e2e_hint, "e2e hint field", InputType.TYPE_CLASS_TEXT).apply {
            hint = HINT_TEXT
        }
        root.addView(label("Hint-only field"))
        root.addView(hintField, matchWidth())

        statusView = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.rgb(60, 70, 75))
            setPadding(0, dp(16), 0, 0)
        }
        root.addView(statusView)
        return root
    }

    private fun field(id: Int, description: String, inputType: Int): EditText =
        EditText(this).apply {
            this.id = id
            this.inputType = inputType
            contentDescription = description
            isSingleLine = true
            showSoftInputOnFocus = false
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    if (!suppress) writeState()
                }
            })
            setOnFocusChangeListener { _: View, _: Boolean -> if (!suppress) writeState() }
        }

    private fun writeState() {
        if (!::textField.isInitialized || !::passwordField.isInitialized ||
            !::hintField.isInitialized || !::statusView.isInitialized
        ) {
            return
        }
        val focused = when {
            passwordField.hasFocus() -> "e2e_password"
            hintField.hasFocus() -> "e2e_hint"
            else -> "e2e_text"
        }
        val json = JSONObject()
            .put("text", textField.text?.toString().orEmpty())
            .put("password", passwordField.text?.toString().orEmpty())
            .put("hint", hintField.text?.toString().orEmpty())
            .put("hint_text", HINT_TEXT)
            .put("focused", focused)
            .put("ts_utc", System.currentTimeMillis())
        File(stateDir(), "state.json").apply { parentFile?.mkdirs() }.writeText(json.toString())
        statusView.text = "text='${textField.text}' password='${passwordField.text}' hint='${hintField.text}'"
    }

    private fun stateDir(): File = File(filesDir, "e2e").apply { mkdirs() }

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(Color.rgb(40, 48, 52))
        setPadding(0, dp(10), 0, dp(2))
    }

    private fun matchWidth(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val PASSWORD_SEED = "SECRET"
        const val HINT_TEXT = "Type here"

        /** Script control: `am start ... --es focus e2e_password --ez clear true`. */
        const val EXTRA_FOCUS = "focus"
        const val EXTRA_CLEAR = "clear"
        const val FOCUS_TEXT = "e2e_text"
        const val FOCUS_PASSWORD = "e2e_password"
        const val FOCUS_HINT = "e2e_hint"
    }
}
